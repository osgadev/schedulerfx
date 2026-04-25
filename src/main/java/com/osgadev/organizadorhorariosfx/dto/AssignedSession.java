package com.osgadev.organizadorhorariosfx.dto; // O model

import com.osgadev.organizadorhorariosfx.model.Group;

public class AssignedSession {
    private Group grupo;
    private int columnaDia;
    private int filaHora;
    private int spanFilas;
    private int slotInicioSemanal;

    public AssignedSession(Group grupo, int columnaDia, int filaHora, int spanFilas) {
        this.grupo = grupo;
        this.columnaDia = columnaDia;
        this.filaHora = filaHora;
        this.spanFilas = spanFilas;
    }

    // Getters para grupo, columnaDia, filaHora y spanFilas...
    public Group getGrupo() { return grupo; }
    public int getColumnaDia() { return columnaDia; }
    public int getFilaHora() { return filaHora; }
    public int getSpanFilas() { return spanFilas; }

    public int getSlotInicioSemanal() {
        return slotInicioSemanal;
    }

    public void setSlotInicioSemanal(int slotInicioSemanal) {
        this.slotInicioSemanal = slotInicioSemanal;
    }
}
