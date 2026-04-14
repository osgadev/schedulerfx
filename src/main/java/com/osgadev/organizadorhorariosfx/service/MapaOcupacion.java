package com.osgadev.organizadorhorariosfx.service;

import com.osgadev.organizadorhorariosfx.model.Group;
import java.util.ArrayList;
import java.util.List;

public class MapaOcupacion {

    // 7 días * 48 bloques = 336 bloques de 30 minutos a la semana
    private static final int TOTAL_SLOTS = 336;

    // Cada posición del arreglo representa un bloque exacto de 30 minutos en la semana.
    // Guarda una lista de los grupos (y sus rangos de alumnos) que ya tienen clase en ese momento.
    private final List<Group>[] mapa;

    @SuppressWarnings("unchecked")
    public MapaOcupacion() {
        mapa = new ArrayList[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            mapa[i] = new ArrayList<>();
        }
    }

    /**
     * Registra que un grupo ya tiene su horario definido y ocupa estos slots.
     * @param slotInicio El slot donde empieza la clase (0 a 335).
     * @param duracionBloques La duración de la clase en bloques de 30 minutos.
     * @param grupo El grupo que ocupa este espacio.
     */
    public void registrarClase(int slotInicio, int duracionBloques, Group grupo) {
        for (int i = slotInicio; i < slotInicio + duracionBloques; i++) {
            if (i < TOTAL_SLOTS) {
                mapa[i].add(grupo);
            }
        }
    }

    /**
     * Limpia completamente el radar (útil para reiniciar el proceso si es necesario).
     */
    public void limpiar() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            mapa[i].clear();
        }
    }

    /**
     * Verifica si un rango de alumnos ya está ocupado tomando otra materia en un slot específico.
     */
    public boolean rangoOcupadoEnSlot(int rangoInicio, int rangoFin, int slot) {
        if (slot >= TOTAL_SLOTS) return true; // Fuera de rango = inválido

        for (Group gRegistrado : mapa[slot]) {
            if (hayInterseccion(rangoInicio, rangoFin, gRegistrado.getRangoInicial(), gRegistrado.getRangoFinal())) {
                return true; // Hay un choque de alumnos en este cuarto de hora
            }
        }
        return false; // Alumnos libres
    }

    /**
     * EL MÉTODO ESTRELLA:
     * Cruza la disponibilidad de un profesor contra el mapa, verificando que
     * ni los alumnos ni el profesor estén ocupados en ese momento.
     *
     * @param dominiosInicioProfesor Arreglo de slots de inicio válidos del profesor (obtenidos de la BD).
     * @param duracionRequerida Cuántos bloques de 30 mins dura esta sesión.
     * @param grupo El grupo que se intenta acomodar (para extraer alumnos y profesor).
     * @return true si hay al menos UN hueco donde la clase cabe perfecto sin chocar con nada.
     */
    public boolean tieneHuecoSeguro(int[] dominiosInicioProfesor, int duracionRequerida, Group grupo) {
        int rangoInicio = grupo.getRangoInicial();
        int rangoFin = grupo.getRangoFinal();
        int idProfesor = grupo.getProfesor().getId();

        // Evaluamos cada posible momento en el que el profesor puede iniciar su clase
        for (int startSlot : dominiosInicioProfesor) {
            boolean huecoValido = true;

            // Revisamos si los alumnos y el profesor están libres en TODA la duración de esa clase
            for (int i = startSlot; i < startSlot + duracionRequerida; i++) {

                // DOBLE VERIFICACIÓN: Ni alumnos ni profesor pueden estar en otra clase
                if (rangoOcupadoEnSlot(rangoInicio, rangoFin, i) || profesorOcupadoEnSlot(idProfesor, i)) {
                    huecoValido = false;
                    break; // Hay choque en algún punto de esta sesión, este inicio no sirve
                }
            }

            // Si recorrió toda la duración y nunca hubo choque, este profesor es un candidato válido
            if (huecoValido) {
                return true;
            }
        }

        // Si probamos todos los huecos del profesor y en todos hubo algún choque:
        return false;
    }


    /**
     * Evalúa si dos rangos numéricos se sobreponen.
     */
    private boolean hayInterseccion(int inicio1, int fin1, int inicio2, int fin2) {
        int maxInicio = Math.max(inicio1, inicio2);
        int minFin = Math.min(fin1, fin2);
        return maxInicio <= minFin;
    }

    public boolean profesorOcupadoEnSlot(int idProfesor, int slot) {
        if (slot >= TOTAL_SLOTS) return true;
        for (Group gRegistrado : mapa[slot]) {
            if (gRegistrado.getProfesor().getId() == idProfesor) {
                return true; // El profesor ya está dando otra clase a esta hora
            }
        }
        return false;
    }

    /**
     * Elimina un grupo del mapa (usado para hacer Backtracking).
     */
    public void eliminarClase(int slotInicio, int duracionBloques, Group grupo) {
        for (int i = slotInicio; i < slotInicio + duracionBloques; i++) {
            if (i < TOTAL_SLOTS) {
                mapa[i].remove(grupo);
            }
        }
    }


}
