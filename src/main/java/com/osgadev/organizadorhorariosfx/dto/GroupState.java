package com.osgadev.organizadorhorariosfx.dto;

import com.osgadev.organizadorhorariosfx.model.Group;

public class GroupState {
    private final Group grupo;
    private final double horasTotales;
    private double horasColocadas;

    public GroupState(Group grupo) {
        this.grupo = grupo;
        // Asumimos que getMinHorasSemanales() devuelve un entero, lo pasamos a double
        this.horasTotales = Math.max(1.0, grupo.getCurso().getMinHorasSemanales());
        this.horasColocadas = 0.0;
    }

    public Group getGrupo() { return grupo; }
    public double getHorasTotales() { return horasTotales; }
    public double getHorasColocadas() { return horasColocadas; }

    public double getHorasRestantes() {
        return horasTotales - horasColocadas;
    }

    public boolean isCompleto() {
        return getHorasRestantes() <= 0;
    }

    public void agregarHoras(double horas) {
        if (horasColocadas + horas <= horasTotales) {
            horasColocadas += horas;
        }
    }

    public void reembolsarHoras(double horas) {
        if (horasColocadas - horas >= 0) {
            horasColocadas -= horas;
        }
    }

    @Override
    public String toString() {
        // Esto es lo que se mostrará en el ListView
        String status = isCompleto() ? "✅ " : "⏳ ";
        return status + grupo.getCurso().getNombre() +
                " (" + grupo.getRangoInicial() + "-" + grupo.getRangoFinal() + ") " +
                "[" + getHorasRestantes() + "h]";
    }
}