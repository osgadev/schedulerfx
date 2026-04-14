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
    private boolean profesorEsValido(Group g) {
        int horasSemanales = Math.max(1, g.getCurso().getMinHorasSemanales());
        int totalBloques = horasSemanales * 2;

        for (int s = 5; s >= 1; s--) {
            int base = totalBloques / s;
            if (base < 2) continue;

            int residuo = totalBloques % s;
            int[] particionPrueba = new int[s];
            for (int i = 0; i < s; i++) particionPrueba[i] = base + (i < residuo ? 1 : 0);

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

            if (particionValida && diasUnicosGlobales.size() >= s) {
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
}
