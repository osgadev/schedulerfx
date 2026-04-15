package com.osgadev.organizadorhorariosfx.service;

import com.osgadev.organizadorhorariosfx.DAO.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OptimizadorGrupos {

    private final AvailabilityDAO availabilityDAO;
    private final MapaOcupacion mapa;
    private static final int BLOQUES_POR_DIA = 48;

    public OptimizadorGrupos(AvailabilityDAO availabilityDAO, MapaOcupacion mapa) {
        this.availabilityDAO = availabilityDAO;
        this.mapa = mapa;
    }

    public boolean optimizarCapa(List<Group> gruposDelCurso) {
        for (int i = 0; i < gruposDelCurso.size(); i++) {
            Group grupoActual = gruposDelCurso.get(i);

            if (!profesorEsValido(grupoActual)) {
                System.out.println("⚠️ Peligro detectado: El profesor " + grupoActual.getProfesor().getNombre() +
                        " choca. Buscando permutación...");

                boolean swapExitoso = intentarPermutacion(gruposDelCurso, i);

                if (!swapExitoso) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean intentarPermutacion(List<Group> grupos, int indexFalla) {
        Group grupoFalla = grupos.get(indexFalla);
        Teacher profeOriginal = grupoFalla.getProfesor();

        for (int j = 0; j < grupos.size(); j++) {
            if (j == indexFalla) continue;

            Group grupoCandidato = grupos.get(j);
            Teacher profeCandidato = grupoCandidato.getProfesor();

            grupoFalla.setProfesor(profeCandidato);
            grupoCandidato.setProfesor(profeOriginal);

            if (profesorEsValido(grupoFalla) && profesorEsValido(grupoCandidato)) {
                System.out.println("🔄 PERMUTACIÓN EXITOSA con " + profeCandidato.getNombre());
                return true;
            }

            grupoFalla.setProfesor(profeOriginal);
            grupoCandidato.setProfesor(profeCandidato);
        }
        return false;
    }

    /**
     * Valida al profesor usando el mismo algoritmo de Particionamiento Asimétrico.
     */
    /**
     * Valida al profesor usando Plantillas Humanas.
     */
    private boolean profesorEsValido(Group g) {
        int horasSemanales = Math.max(1, g.getCurso().getMinHorasSemanales());
        int totalBloques = horasSemanales * 2;

        List<int[]> plantillas = obtenerPlantillasHumanas(totalBloques);

        for (int[] particionPrueba : plantillas) {
            boolean particionValida = true;
            Set<Integer> diasUnicosGlobales = new HashSet<>();

            for (int duracionPedazo : particionPrueba) {
                int[] dominiosCrudos = obtenerDominioValido(g.getProfesor(), duracionPedazo);
                boolean encontroHuecoParaEstePedazo = false;

                for (int slot : dominiosCrudos) {
                    boolean huecoLimpio = true;
                    for (int i = slot; i < slot + duracionPedazo; i++) {
                        if (mapa.rangoOcupadoEnSlot(g.getRangoInicial(), g.getRangoFinal(), i) ||
                                mapa.profesorOcupadoEnSlot(g.getProfesor().getId(), i)) {
                            huecoLimpio = false;
                            break;
                        }
                    }
                    if (huecoLimpio) {
                        diasUnicosGlobales.add(slot / BLOQUES_POR_DIA);
                        encontroHuecoParaEstePedazo = true;
                    }
                }
                if (!encontroHuecoParaEstePedazo) {
                    particionValida = false;
                    break;
                }
            }

            // Exigimos que todas las particiones caigan en días distintos
            if (particionValida && diasUnicosGlobales.size() >= particionPrueba.length) {
                return true;
            }
        }
        return false;
    }

    private int[] obtenerDominioValido(Teacher profesor, int duracionClase) {
        List<Availability> disp = availabilityDAO.getByTeacher(profesor);
        List<Integer> validos = new ArrayList<>();
        for (Availability a : disp) {
            int maxInicioPosible = a.getEndSlot() - duracionClase;
            for (int i = a.getStartSlot(); i <= maxInicioPosible; i++) validos.add(i);
        }
        return validos.stream().mapToInt(i -> i).toArray();
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
