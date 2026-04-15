package com.osgadev.organizadorhorariosfx.service;

import com.osgadev.organizadorhorariosfx.DAO.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.DTO.SesionAsignada;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.IntVar;

import java.util.function.BiConsumer;
import java.util.*;

public class HorarioService {

    private final AvailabilityDAO availabilityDAO;
    private MapaOcupacion mapa;
    private OptimizadorGrupos optimizador;

    private static final int BLOQUES_POR_DIA = 48;

    public HorarioService(AvailabilityDAO availabilityDAO) {
        this.availabilityDAO = availabilityDAO;
    }

    public List<SesionAsignada> generarHorario(List<Group> todosLosGrupos, BiConsumer<List<SesionAsignada>, String> onProgressUpdate) {
        this.mapa = new MapaOcupacion();
        this.optimizador = new OptimizadorGrupos(availabilityDAO, mapa);
        List<SesionAsignada> horarioFinal = new ArrayList<>();

        Map<Integer, List<Group>> gruposPorCurso = new HashMap<>();
        for (Group g : todosLosGrupos) gruposPorCurso.computeIfAbsent(g.getCurso().getId(), k -> new ArrayList<>()).add(g);

        List<List<Group>> listaDeCapas = new ArrayList<>(gruposPorCurso.values());
        listaDeCapas.sort(Comparator.comparingInt(List::size));

        System.out.println("\n================ INICIANDO VISUALIZADOR =================\n");
        if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(), "🚀 IA Iniciada: Ordenando " + listaDeCapas.size() + " materias...");

        boolean exito = resolverConBacktracking(0, listaDeCapas, horarioFinal, onProgressUpdate);

        if (exito) {
            if (onProgressUpdate != null) onProgressUpdate.accept(horarioFinal, "✅ ¡Horario Completo Generado con Éxito!");
            return horarioFinal;
        } else {
            if (onProgressUpdate != null) onProgressUpdate.accept(horarioFinal, "❌ Fracaso Total: Tablero demasiado saturado.");
            return null;
        }
    }

    private boolean resolverConBacktracking(int indiceCapa, List<List<Group>> listaDeCapas, List<SesionAsignada> horarioFinal, BiConsumer<List<SesionAsignada>, String> onProgressUpdate) {
        if (indiceCapa >= listaDeCapas.size()) return true;

        List<Group> capaActual = listaDeCapas.get(indiceCapa);
        String nombreCurso = capaActual.get(0).getCurso().getNombre();
        String msjProcesando = "▶ Procesando Capa " + (indiceCapa + 1) + "/" + listaDeCapas.size() + ": " + nombreCurso;
        System.out.println("\n" + msjProcesando);

        if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(horarioFinal), "⏳ Analizando posibles profesores para " + nombreCurso + "...");

        if (!optimizador.optimizarCapa(capaActual)) {
            String error = "❌ Optimizador falló para " + nombreCurso;
            System.out.println("  " + error);
            if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(horarioFinal), error);
            return false;
        }

        List<List<SesionAsignada>> variantesPosibles = resolverCapaConChocoMult(capaActual);
        if (variantesPosibles == null || variantesPosibles.isEmpty()) {
            String error = "❌ Choco no encontró combinaciones para " + nombreCurso;
            System.out.println("  " + error);
            if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(horarioFinal), error);
            return false;
        }

        for (int i = 0; i < variantesPosibles.size(); i++) {
            List<SesionAsignada> variante = variantesPosibles.get(i);
            String msjVariante = "✅ Aplicando Variante " + (i + 1) + "/" + variantesPosibles.size() + " de " + nombreCurso;
            System.out.println("  " + msjVariante);

            for (SesionAsignada s : variante) {
                mapa.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas() / 2, s.getGrupo());
                horarioFinal.add(s);
            }

            if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(horarioFinal), msjProcesando + " | " + msjVariante);

            if (resolverConBacktracking(indiceCapa + 1, listaDeCapas, horarioFinal, onProgressUpdate)) return true;

            String msjRetroceso = "⏪ Retrocediendo: Deshaciendo variante " + (i + 1) + " de " + nombreCurso;
            System.out.println("  " + msjRetroceso);
            if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(horarioFinal), msjRetroceso);

            for (SesionAsignada s : variante) {
                mapa.eliminarClase(s.getSlotInicioSemanal(), s.getSpanFilas() / 2, s.getGrupo());
                horarioFinal.remove(s);
            }

            if (onProgressUpdate != null) onProgressUpdate.accept(new ArrayList<>(horarioFinal), "Limpiando tablero...");
        }
        return false;
    }

    private List<List<SesionAsignada>> resolverCapaConChocoMult(List<Group> gruposCapa) {
        Model model = new Model("Motor CSP - Anclaje Inteligente y Humano");
        Map<Group, IntVar[]> mapaVariables = new HashMap<>();
        Map<IntVar, Integer> mapaDuracionesVariable = new HashMap<>();

        for (Group grupo : gruposCapa) {
            // 1. OBTENEMOS LAS DISPONIBILIDADES Y SEPARAMOS AZULES DE VERDES
            List<Availability> dispProfesor = availabilityDAO.getByTeacher(grupo.getProfesor());
            List<Availability> bloquesFijos = new ArrayList<>();
            List<Availability> bloquesGenericos = new ArrayList<>();

            for (Availability a : dispProfesor) {
                if (a.getCursoSugerido() != null && a.getCursoSugerido().getId() == grupo.getCurso().getId()) {
                    bloquesFijos.add(a); // Es un Bloque Azul asignado a este curso
                } else if (a.getCursoSugerido() == null) {
                    bloquesGenericos.add(a); // Es un Bloque Verde genérico
                }
            }

            // UNIFICAMOS LA DISPONIBILIDAD (Dando prioridad a los bloques Azules poniéndolos primero)
            List<Availability> disponibilidadTotal = new ArrayList<>();
            disponibilidadTotal.addAll(bloquesFijos);
            disponibilidadTotal.addAll(bloquesGenericos);

            int horasSemanales = Math.max(1, grupo.getCurso().getMinHorasSemanales());
            int totalBloques = horasSemanales * 2;
            List<IntVar> sesionesDelGrupo = new ArrayList<>();

            // 2. EL MOTOR DE PLANTILLAS GOBIERNA TODO EL TIEMPO
            int[] particionElegida = null;
            List<int[]> dominiosElegidos = new ArrayList<>();
            List<int[]> plantillas = obtenerPlantillasHumanas(totalBloques);

            for (int[] particionPrueba : plantillas) {
                boolean particionValida = true;
                List<int[]> doms = new ArrayList<>();
                Set<Integer> diasUnicosGlobales = new HashSet<>();

                for (int duracionPedazo : particionPrueba) {
                    // Choco buscará primero en los bloques azules, y luego en los verdes
                    int[] dom = obtenerDominioFiltrado(grupo, duracionPedazo, disponibilidadTotal);

                    if (dom.length == 0) {
                        particionValida = false;
                        break;
                    }
                    doms.add(dom);
                    for(int slot : dom) diasUnicosGlobales.add(slot / BLOQUES_POR_DIA);
                }

                // Validamos que todas las piezas de la plantilla caigan en días distintos
                if (particionValida && diasUnicosGlobales.size() >= particionPrueba.length) {
                    particionElegida = particionPrueba;
                    dominiosElegidos = doms;
                    break;
                }
            }

            if (particionElegida == null) return null; // Falló el acomodo de todas las plantillas

            // 3. CREAMOS LAS VARIABLES PARA CHOCO SOLVER
            for (int i = 0; i < particionElegida.length; i++) {
                IntVar varDinamica = model.intVar(grupo.getIdGrupo() + "_S" + (i + 1), dominiosElegidos.get(i));
                sesionesDelGrupo.add(varDinamica);
                mapaDuracionesVariable.put(varDinamica, particionElegida[i]);
            }

            IntVar[] arregloSesiones = sesionesDelGrupo.toArray(new IntVar[0]);
            mapaVariables.put(grupo, arregloSesiones);

            // 4. RESTRICCIONES DE SIMETRÍA Y DÍAS DISTINTOS
            if (arregloSesiones.length > 1) {
                IntVar[] diasDeSesion = new IntVar[arregloSesiones.length];
                for (int i = 0; i < arregloSesiones.length; i++) {
                    diasDeSesion[i] = arregloSesiones[i].div(BLOQUES_POR_DIA).intVar();
                }
                model.allDifferent(diasDeSesion).post();

                // Romper simetría
                for (int i = 0; i < sesionesDelGrupo.size() - 1; i++) {
                    int dur1 = mapaDuracionesVariable.get(sesionesDelGrupo.get(i));
                    int dur2 = mapaDuracionesVariable.get(sesionesDelGrupo.get(i+1));
                    if (dur1 == dur2) {
                        model.arithm(sesionesDelGrupo.get(i), "<", sesionesDelGrupo.get(i + 1)).post();
                    }
                }
            }
        }

        // 5. RESTRICCIONES DE TRASLAPE GLOBAL
        for (int i = 0; i < gruposCapa.size(); i++) {
            for (int j = i + 1; j < gruposCapa.size(); j++) {
                Group g1 = gruposCapa.get(i);
                Group g2 = gruposCapa.get(j);

                if (g1.getProfesor().getId() == g2.getProfesor().getId() || hayInterseccion(g1, g2)) {
                    for (IntVar s1 : mapaVariables.get(g1)) {
                        for (IntVar s2 : mapaVariables.get(g2)) {
                            int durG1 = mapaDuracionesVariable.get(s1);
                            int durG2 = mapaDuracionesVariable.get(s2);

                            Constraint s1Primero = model.arithm(s1.add(durG1).intVar(), "<=", s2);
                            Constraint s2Primero = model.arithm(s2.add(durG2).intVar(), "<=", s1);
                            model.or(s1Primero, s2Primero).post();
                        }
                    }
                }
            }
        }

        List<List<SesionAsignada>> todasLasSoluciones = new ArrayList<>();
        int maxVariantes = 3;

        while (model.getSolver().solve() && todasLasSoluciones.size() < maxVariantes) {
            List<SesionAsignada> varianteActual = new ArrayList<>();
            for (Group g : gruposCapa) {
                for (IntVar varSesion : mapaVariables.get(g)) {
                    int slotInicioSemanal = varSesion.getValue();
                    int duracionBloques = mapaDuracionesVariable.get(varSesion);

                    int indexDia = slotInicioSemanal / BLOQUES_POR_DIA;
                    int slotDelDia = slotInicioSemanal % BLOQUES_POR_DIA;
                    int hInicio = slotDelDia / 2;
                    int mInicio = (slotDelDia % 2) * 30;

                    int columnaDia = indexDia + 1;
                    int filaInicioVisual = ((hInicio - 7) * 4) + (mInicio / 15);
                    int spanFilasVisuales = duracionBloques * 2;

                    SesionAsignada nuevaSesion = new SesionAsignada(g, columnaDia, filaInicioVisual, spanFilasVisuales);
                    nuevaSesion.setSlotInicioSemanal(slotInicioSemanal);
                    varianteActual.add(nuevaSesion);
                }
            }
            todasLasSoluciones.add(varianteActual);
        }
        return todasLasSoluciones;
    }

    private int[] obtenerDominioFiltrado(Group grupo, int duracionClase, List<Availability> disponibilidades) {
        List<Integer> validos = new ArrayList<>();
        for (Availability a : disponibilidades) {
            int maxInicioPosible = a.getEndSlot() - duracionClase;
            for (int i = a.getStartSlot(); i <= maxInicioPosible; i++) {
                boolean huecoLimpio = true;
                for (int j = i; j < i + duracionClase; j++) {
                    if (mapa.rangoOcupadoEnSlot(grupo.getRangoInicial(), grupo.getRangoFinal(), j) ||
                            mapa.profesorOcupadoEnSlot(grupo.getProfesor().getId(), j)) {
                        huecoLimpio = false; break;
                    }
                }
                if (huecoLimpio) validos.add(i);
            }
        }
        return validos.stream().mapToInt(i -> i).toArray();
    }

    private boolean hayInterseccion(Group g1, Group g2) {
        return Math.max(g1.getRangoInicial(), g2.getRangoInicial()) <= Math.min(g1.getRangoFinal(), g2.getRangoFinal());
    }

    private List<int[]> obtenerPlantillasHumanas(int totalBloquesRestantes) {
        List<int[]> plantillas = new ArrayList<>();

        // Catálogo de plantillas priorizadas: Preferencia por sesiones cortas en MÁS días.
        switch (totalBloquesRestantes) {
            case 2: // 1 hora
                plantillas.add(new int[]{2});
                break;
            case 3: // 1.5 horas
                plantillas.add(new int[]{3});
                break;
            case 4: // 2 horas
                plantillas.add(new int[]{2, 2});       // Ideal: 2 días de 1h
                plantillas.add(new int[]{4});          // Respaldo: 1 día de 2h
                break;
            case 5: // 2.5 horas
                plantillas.add(new int[]{3, 2});       // 1 día de 1.5h y 1 día de 1h
                break;
            case 6: // 3 horas
                plantillas.add(new int[]{2, 2, 2});    // Ideal: 3 días de 1h
                plantillas.add(new int[]{3, 3});       // Respaldo 1: 2 días de 1.5h
                plantillas.add(new int[]{4, 2});       // Respaldo 2: 1 día de 2h y 1 día de 1h
                break;
            case 8: // 4 horas
                plantillas.add(new int[]{2, 2, 2, 2}); // Ideal: 4 días de 1h
                plantillas.add(new int[]{3, 3, 2});    // Respaldo 1: 2 días de 1.5h, 1 día de 1h
                plantillas.add(new int[]{4, 2, 2});    // Respaldo 2: 1 día de 2h, 2 días de 1h
                plantillas.add(new int[]{4, 4});       // Respaldo 3: 2 días de 2h
                break;
            case 10: // 5 horas
                plantillas.add(new int[]{2, 2, 2, 2, 2});
                plantillas.add(new int[]{3, 3, 2, 2});
                plantillas.add(new int[]{4, 3, 3});
                plantillas.add(new int[]{4, 4, 2});
                break;
            case 12: // 6 horas
                plantillas.add(new int[]{2, 2, 2, 2, 2, 2});
                plantillas.add(new int[]{3, 3, 2, 2, 2});
                plantillas.add(new int[]{3, 3, 3, 3});
                plantillas.add(new int[]{4, 4, 2, 2});
                plantillas.add(new int[]{4, 4, 4});
                break;
            default:
                // Generador fallback invertido (prefiere bloques de 2 slots)
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
}