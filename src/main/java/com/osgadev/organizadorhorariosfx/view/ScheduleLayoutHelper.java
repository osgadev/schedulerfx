package com.osgadev.organizadorhorariosfx.view;

import com.osgadev.organizadorhorariosfx.dto.AssignedSession;
import java.util.*;

public class ScheduleLayoutHelper {

    // Clase interna para almacenar las coordenadas calculadas
    public static class PosicionVisual {
        public final int indiceColumna;
        public final int totalColumnas;

        public PosicionVisual(int indiceColumna, int totalColumnas) {
            this.indiceColumna = indiceColumna;
            this.totalColumnas = totalColumnas;
        }
    }

    // El algoritmo puro extraído del controlador
    public static Map<AssignedSession, PosicionVisual> calcularLayoutCartas(List<AssignedSession> sesiones) {
        Map<AssignedSession, PosicionVisual> layout = new HashMap<>();
        Map<Integer, List<AssignedSession>> porDia = new HashMap<>();

        for (AssignedSession s : sesiones) {
            porDia.computeIfAbsent(s.getColumnaDia(), k -> new ArrayList<>()).add(s);
        }

        for (List<AssignedSession> diaSesiones : porDia.values()) {
            diaSesiones.sort(Comparator.comparingInt(AssignedSession::getFilaHora)
                    .thenComparingInt(AssignedSession::getSpanFilas));

            List<List<AssignedSession>> bloques = new ArrayList<>();
            List<AssignedSession> bloqueActual = new ArrayList<>();
            int maxFilaFin = -1;

            for (AssignedSession s : diaSesiones) {
                int inicio = s.getFilaHora();
                int fin = inicio + s.getSpanFilas();

                if (bloqueActual.isEmpty() || inicio < maxFilaFin) {
                    bloqueActual.add(s);
                    maxFilaFin = Math.max(maxFilaFin, fin);
                } else {
                    bloques.add(new ArrayList<>(bloqueActual));
                    bloqueActual.clear();
                    bloqueActual.add(s);
                    maxFilaFin = fin;
                }
            }
            if (!bloqueActual.isEmpty()) {
                bloques.add(bloqueActual);
            }

            for (List<AssignedSession> bloque : bloques) {
                List<List<AssignedSession>> columnasVisuales = new ArrayList<>();

                for (AssignedSession s : bloque) {
                    boolean colocada = false;
                    for (List<AssignedSession> col : columnasVisuales) {
                        AssignedSession ultimaEnCol = col.get(col.size() - 1);
                        if (s.getFilaHora() >= ultimaEnCol.getFilaHora() + ultimaEnCol.getSpanFilas()) {
                            col.add(s);
                            colocada = true;
                            break;
                        }
                    }
                    if (!colocada) {
                        List<AssignedSession> nuevaCol = new ArrayList<>();
                        nuevaCol.add(s);
                        columnasVisuales.add(nuevaCol);
                    }
                }

                int totalColumnasBloque = columnasVisuales.size();
                for (int i = 0; i < totalColumnasBloque; i++) {
                    for (AssignedSession s : columnasVisuales.get(i)) {
                        layout.put(s, new PosicionVisual(i, totalColumnasBloque));
                    }
                }
            }
        }
        return layout;
    }
}