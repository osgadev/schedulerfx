package com.osgadev.organizadorhorariosfx.model;

import java.util.ArrayList;
import java.util.List;

public class Alumno {
    private String matricula;
    private String nombreCompleto;
    private String correo_electronico;
    private int numeroLista;
    private List<Group> gruposAsignados; // NUEVO: Relación con sus materias

    public Alumno(String matricula, String nombreCompleto, String correo_electronico) {
        this.matricula = matricula;
        this.nombreCompleto = nombreCompleto;
        this.correo_electronico = correo_electronico;
        this.gruposAsignados = new ArrayList<>();
    }

    public String getMatricula() { return matricula; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getCorreo_electronico() { return correo_electronico; }

    public int getNumeroLista() { return numeroLista; }
    public void setNumeroLista(int numeroLista) { this.numeroLista = numeroLista; }

    public List<Group> getGruposAsignados() { return gruposAsignados; }
    public void agregarGrupo(Group grupo) { this.gruposAsignados.add(grupo); }
}