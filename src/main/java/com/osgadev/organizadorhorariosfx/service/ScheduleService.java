package com.osgadev.organizadorhorariosfx.service;

import com.osgadev.organizadorhorariosfx.dao.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.dto.AssignedSession;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.Task;

import java.util.function.BiConsumer;
import java.util.*;

public class ScheduleService {

    private final AvailabilityDAO availabilityDAO;
    private static final int BLOQUES_POR_DIA = 48;

    // CACHÉ DE RENDIMIENTO: Evita consultar la BD decenas de veces por segundo en el Drag & Drop
    private final Map<Integer, List<Availability>> cacheDisponibilidad = new HashMap<>();

    public ScheduleService(AvailabilityDAO availabilityDAO) {
        this.availabilityDAO = availabilityDAO;
    }

    // Método para limpiar la caché cuando se cambia de semestre o se recarga todo
    public void limpiarCache() {
        cacheDisponibilidad.clear();
    }

    // Método centralizado que usa la caché
    private List<Availability> obtenerDisponibilidadProfe(Teacher profe) {
        if (profe == null) return new ArrayList<>();
        // Si el profe ya está en el mapa, lo devuelve al instante. Si no, va a la BD y lo guarda.
        return cacheDisponibilidad.computeIfAbsent(profe.getId(), id -> availabilityDAO.getByTeacher(profe));
    }

    public List<AssignedSession> generarHorario(List<Group> todosLosGrupos, BiConsumer<List<AssignedSession>, String> onProgressUpdate) {

        Map<Integer, Integer> espacioPorProfesor = new HashMap<>();
        for (Group g : todosLosGrupos) {
            int profeId = g.getProfesor().getId();
            if (!espacioPorProfesor.containsKey(profeId)) {
                int slotsLibres = 0;
                for (Availability a : obtenerDisponibilidadProfe(g.getProfesor())) {
                    slotsLibres += (a.getEndSlot() - a.getStartSlot());
                }
                espacioPorProfesor.put(profeId, slotsLibres);
            }
        }

        List<Group> gruposOrdenados = new ArrayList<>(todosLosGrupos);
        gruposOrdenados.sort((g1, g2) -> {
            int espacio1 = espacioPorProfesor.get(g1.getProfesor().getId());
            int espacio2 = espacioPorProfesor.get(g2.getProfesor().getId());
            if (espacio1 != espacio2) return Integer.compare(espacio1, espacio2);
            return Integer.compare(g2.getCurso().getMinHorasSemanales(), g1.getCurso().getMinHorasSemanales());
        });

        int[][] configuraciones = {
                {0, 1}, {0, 0}, {1, 1}, {1, 0}, {2, 0}, {3, 0}, {4, 0}
        };

        for (int intento = 0; intento < configuraciones.length; intento++) {
            int nivelRelajacionPlantilla = configuraciones[intento][0];
            boolean alinearHoras = (configuraciones[intento][1] == 1);

            if (onProgressUpdate != null) {
                String msj = (intento == 0) ? "🚀 Intento 1: Buscando estructura perfecta..."
                        : "⚠️ Intento " + (intento + 1) + ": Relajando restricciones...";
                onProgressUpdate.accept(new ArrayList<>(), msj);
            }

            Model model = new Model("Motor Scheduling - Intento " + intento);
            Map<Group, List<Task>> tareasPorGrupo = new HashMap<>();
            List<IntVar> todasLasVariablesDeInicio = new ArrayList<>();
            boolean viableInicialmente = true;

            for (Group grupo : gruposOrdenados) {
                List<Availability> disponibilidadTotal = obtenerDisponibilidadOrdenada(grupo);
                int horasSemanales = Math.max(1, grupo.getCurso().getMinHorasSemanales());
                int totalBloques = horasSemanales * 2;

                int[] particionElegida = elegirMejorPlantilla(grupo, totalBloques, disponibilidadTotal, nivelRelajacionPlantilla);

                if (particionElegida == null) {
                    viableInicialmente = false;
                    break;
                }

                List<Task> sesionesDelGrupo = new ArrayList<>();
                IntVar[] variablesInicioGrupo = new IntVar[particionElegida.length];

                for (int i = 0; i < particionElegida.length; i++) {
                    int duracion = particionElegida[i];
                    int[] dominio = obtenerDominioFiltrado(duracion, disponibilidadTotal);

                    if(dominio.length == 0) {
                        viableInicialmente = false;
                        break;
                    }

                    IntVar startVar = model.intVar(grupo.getIdGrupo() + "_S" + (i + 1), dominio);
                    IntVar durationVar = model.intVar("dur", duracion);
                    IntVar endVar = model.intOffsetView(startVar, duracion);

                    Task tarea = new Task(startVar, durationVar, endVar);
                    sesionesDelGrupo.add(tarea);
                    todasLasVariablesDeInicio.add(startVar);
                    variablesInicioGrupo[i] = startVar;
                }

                if (!viableInicialmente) break;

                tareasPorGrupo.put(grupo, sesionesDelGrupo);

                if (variablesInicioGrupo.length > 1) {
                    IntVar[] diasDeSesion = new IntVar[variablesInicioGrupo.length];
                    for (int i = 0; i < variablesInicioGrupo.length; i++) {
                        diasDeSesion[i] = variablesInicioGrupo[i].div(BLOQUES_POR_DIA).intVar();
                    }
                    model.allDifferent(diasDeSesion).post();

                    for (int i = 0; i < variablesInicioGrupo.length - 1; i++) {
                        if (particionElegida[i] == particionElegida[i + 1]) {
                            model.arithm(variablesInicioGrupo[i], "<", variablesInicioGrupo[i + 1]).post();
                        }
                    }

                    if (alinearHoras) {
                        for (int i = 0; i < variablesInicioGrupo.length - 1; i++) {
                            if (particionElegida[i] == particionElegida[i + 1]) {
                                IntVar hora1 = variablesInicioGrupo[i].mod(BLOQUES_POR_DIA).intVar();
                                IntVar hora2 = variablesInicioGrupo[i + 1].mod(BLOQUES_POR_DIA).intVar();
                                model.arithm(hora1, "=", hora2).post();
                            }
                        }
                    }
                }
            }

            if (!viableInicialmente) continue;

            for (int i = 0; i < gruposOrdenados.size(); i++) {
                for (int j = i + 1; j < gruposOrdenados.size(); j++) {
                    Group g1 = gruposOrdenados.get(i);
                    Group g2 = gruposOrdenados.get(j);

                    if (g1.getProfesor().getId() == g2.getProfesor().getId() || hayInterseccion(g1, g2)) {
                        for (Task t1 : tareasPorGrupo.get(g1)) {
                            for (Task t2 : tareasPorGrupo.get(g2)) {
                                Constraint t1Primero = model.arithm(t1.getEnd(), "<=", t2.getStart());
                                Constraint t2Primero = model.arithm(t2.getEnd(), "<=", t1.getStart());
                                model.or(t1Primero, t2Primero).post();
                            }
                        }
                    }
                }
            }

            IntVar[] variablesBuscar = todasLasVariablesDeInicio.toArray(new IntVar[0]);
            if (variablesBuscar.length == 0) continue;

            model.getSolver().setSearch(Search.domOverWDegSearch(variablesBuscar));
            model.getSolver().limitTime("5s");

            try {
                if (model.getSolver().solve()) {
                    List<AssignedSession> horarioFinal = extraerHorario(gruposOrdenados, tareasPorGrupo);
                    if (onProgressUpdate != null) onProgressUpdate.accept(horarioFinal, "✅ ¡Horario Generado con Éxito en el Intento " + (intento + 1) + "!");
                    return horarioFinal;
                } else {
                    System.out.println("Choco falló en intento " + (intento + 1) + ". Relajando restricciones...");
                }
            } catch (Exception e) {
                System.out.println("Error en el intento " + (intento + 1) + ": " + e.getMessage());
            }
        }

        if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(), "❌ Fracaso Total: Imposible generar horario.");
        return null;
    }

    private List<Availability> obtenerDisponibilidadOrdenada(Group grupo) {
        List<Availability> dispProfesor = obtenerDisponibilidadProfe(grupo.getProfesor());
        List<Availability> bloquesFijos = new ArrayList<>();
        List<Availability> bloquesGenericos = new ArrayList<>();

        for (Availability a : dispProfesor) {
            if (a.getCursoSugerido() != null && a.getCursoSugerido().getId() == grupo.getCurso().getId()) {
                bloquesFijos.add(a);
            } else if (a.getCursoSugerido() == null) {
                bloquesGenericos.add(a);
            }
        }
        List<Availability> disponibilidadTotal = new ArrayList<>(bloquesFijos);
        disponibilidadTotal.addAll(bloquesGenericos);
        return disponibilidadTotal;
    }

    private int[] elegirMejorPlantilla(Group grupo, int totalBloques, List<Availability> disponibilidades, int nivelRelajacion) {
        List<int[]> plantillas = obtenerPlantillasHumanas(totalBloques);
        List<int[]> plantillasValidasParaElProfesor = new ArrayList<>();

        for (int[] particionPrueba : plantillas) {
            boolean particionValida = true;
            Set<Integer> diasUnicosGlobales = new HashSet<>();

            for (int duracion : particionPrueba) {
                int[] dom = obtenerDominioFiltrado(duracion, disponibilidades);
                if (dom.length == 0) {
                    particionValida = false;
                    break;
                }
                for(int slot : dom) diasUnicosGlobales.add(slot / BLOQUES_POR_DIA);
            }

            if (particionValida && diasUnicosGlobales.size() >= particionPrueba.length) {
                plantillasValidasParaElProfesor.add(particionPrueba);
            }
        }

        if (plantillasValidasParaElProfesor.isEmpty()) return null;
        int indiceElegido = Math.min(nivelRelajacion, plantillasValidasParaElProfesor.size() - 1);
        return plantillasValidasParaElProfesor.get(indiceElegido);
    }

    private int[] obtenerDominioFiltrado(int duracionClase, List<Availability> disponibilidades) {
        Set<Integer> validos = new HashSet<>();
        for (Availability a : disponibilidades) {
            int maxInicioPosible = a.getEndSlot() - duracionClase;
            for (int i = a.getStartSlot(); i <= maxInicioPosible; i++) {
                validos.add(i);
            }
        }
        return validos.stream().mapToInt(i -> i).sorted().toArray();
    }

    private List<AssignedSession> extraerHorario(List<Group> gruposOrdenados, Map<Group, List<Task>> tareasPorGrupo) {
        List<AssignedSession> variantesResultantes = new ArrayList<>();
        for (Group g : gruposOrdenados) {
            for (Task tarea : tareasPorGrupo.get(g)) {
                int slotInicioSemanal = tarea.getStart().getValue();
                int duracionBloques = tarea.getDuration().getValue();

                int indexDia = slotInicioSemanal / BLOQUES_POR_DIA;
                int slotDelDia = slotInicioSemanal % BLOQUES_POR_DIA;
                int hInicio = slotDelDia / 2;
                int mInicio = (slotDelDia % 2) * 30;

                int columnaDia = indexDia + 1;
                int filaInicioVisual = ((hInicio - 7) * 2) + (mInicio / 30);

                AssignedSession nuevaSesion = new AssignedSession(g, columnaDia, filaInicioVisual, duracionBloques);
                nuevaSesion.setSlotInicioSemanal(slotInicioSemanal);
                variantesResultantes.add(nuevaSesion);
            }
        }
        return variantesResultantes;
    }

    private boolean hayInterseccion(Group g1, Group g2) {
        return Math.max(g1.getRangoInicial(), g2.getRangoInicial()) <= Math.min(g1.getRangoFinal(), g2.getRangoFinal());
    }

    private List<int[]> obtenerPlantillasHumanas(int totalBloquesRestantes) {
        List<int[]> plantillas = new ArrayList<>();
        switch (totalBloquesRestantes) {
            case 2: plantillas.add(new int[]{2}); break;
            case 3: plantillas.add(new int[]{3}); break;
            case 4: plantillas.add(new int[]{2, 2}); plantillas.add(new int[]{4}); break;
            case 5: plantillas.add(new int[]{3, 2}); plantillas.add(new int[]{5}); break;
            case 6: plantillas.add(new int[]{2, 2, 2}); plantillas.add(new int[]{3, 3}); plantillas.add(new int[]{4, 2}); break;
            case 8: plantillas.add(new int[]{2, 2, 2, 2}); plantillas.add(new int[]{4, 4}); plantillas.add(new int[]{3, 3, 2}); plantillas.add(new int[]{4, 2, 2}); break;
            case 10: plantillas.add(new int[]{2, 2, 2, 2, 2}); plantillas.add(new int[]{4, 4, 2}); plantillas.add(new int[]{3, 3, 2, 2}); plantillas.add(new int[]{4, 3, 3}); break;
            case 12: plantillas.add(new int[]{2, 2, 2, 2, 2, 2}); plantillas.add(new int[]{4, 4, 4}); plantillas.add(new int[]{3, 3, 3, 3}); plantillas.add(new int[]{4, 4, 2, 2}); break;
            default:
                int restantes = totalBloquesRestantes;
                List<Integer> generico = new ArrayList<>();
                while (restantes > 0) {
                    if (restantes >= 2) { generico.add(2); restantes -= 2; }
                    else { generico.add(1); restantes -= 1; }
                }
                plantillas.add(generico.stream().mapToInt(i -> i).toArray());
        }
        return plantillas;
    }

    public enum ValidacionManual {
        OK("Suelte para asignar..."),
        FUERA_DE_HORARIO("⚠️ Fuera de horario"),
        CHOQUE_MATERIAS("⚠️ Choque de materias"),
        MAX_UNO_POR_DIA("⚠️ 1 bloque por día máximo"),
        FUERA_DE_DISPONIBILIDAD("⚠️ Fuera de disponibilidad");

        private final String mensaje;
        ValidacionManual(String mensaje) { this.mensaje = mensaje; }
        public String getMensaje() { return mensaje; }
    }

    public int calcularSlotSemanal(int columnaDia, int filaVisual, int horaInicioDia) {
        int hInicioReal = horaInicioDia + ((filaVisual - 1) / 2);
        int slotDelDia = (hInicioReal * 2) + ((filaVisual - 1) % 2);
        return ((columnaDia - 1) * BLOQUES_POR_DIA) + slotDelDia;
    }

    public ValidacionManual validarPosicionManual(Group grupo, int columnaDia, int filaVisual,
                                                  int spanFilasVisuales, int numFilasTiempo,
                                                  List<AssignedSession> horarioActual,
                                                  OccupationMap occupationMap, int horaInicioDia) {
        if ((filaVisual - 1) + spanFilasVisuales > numFilasTiempo) return ValidacionManual.FUERA_DE_HORARIO;
        int slotSemanalBase = calcularSlotSemanal(columnaDia, filaVisual, horaInicioDia);

        // REGLA: Validar usando la CACHÉ, velocidad ultrarrápida
        List<Availability> disponibilidades = obtenerDisponibilidadProfe(grupo.getProfesor());
        for (int i = 0; i < spanFilasVisuales; i++) {
            int slotActual = slotSemanalBase + i;
            boolean slotCubierto = false;

            for (Availability a : disponibilidades) {
                if (slotActual >= a.getStartSlot() && slotActual < a.getEndSlot()) {
                    slotCubierto = true;
                    break;
                }
            }

            if (!slotCubierto) {
                return ValidacionManual.FUERA_DE_DISPONIBILIDAD;
            }
        }

        // Validar Choques
        for (int i = 0; i < spanFilasVisuales; i++) {
            if (occupationMap.profesorOcupadoEnSlot(grupo.getProfesor().getId(), slotSemanalBase + i) ||
                    occupationMap.rangoOcupadoEnSlot(grupo.getRangoInicial(), grupo.getRangoFinal(), slotSemanalBase + i)) {
                return ValidacionManual.CHOQUE_MATERIAS;
            }
        }

        // Validar Máximo 1 sesión por día
        for (AssignedSession s : horarioActual) {
            if (s.getGrupo().getIdGrupo().equals(grupo.getIdGrupo()) && s.getColumnaDia() == columnaDia) {
                return ValidacionManual.MAX_UNO_POR_DIA;
            }
        }

        return ValidacionManual.OK;
    }
}