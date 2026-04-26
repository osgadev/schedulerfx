package com.osgadev.organizadorhorariosfx.util;

import java.time.LocalDate;

public class SessionGlobal {
    // Por defecto inicia con el año actual y etapa 1
    private static String anioActual = String.valueOf(LocalDate.now().getYear());
    private static String etapaActual = "1";

    // Navegación Contextual (Deep Linking)
    private static Integer idProfesorNavegacion = null;

    public static String getAnioActual() { return anioActual; }
    public static void setAnioActual(String anio) { anioActual = anio; }

    public static String getEtapaActual() { return etapaActual; }
    public static void setEtapaActual(String etapa) { etapaActual = etapa; }

    // Métodos para Navegación
    public static void setProfesorNavegacion(Integer id) { idProfesorNavegacion = id; }
    public static Integer getProfesorNavegacion() { return idProfesorNavegacion; }
    public static void limpiarProfesorNavegacion() { idProfesorNavegacion = null; }
}