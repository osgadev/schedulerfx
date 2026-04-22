package com.osgadev.organizadorhorariosfx.model;

import java.util.Objects;

public class Group {

    private String idGrupo;
    private Course curso;
    private Teacher profesor;
    private int tamanioGrupo;
    private int rangoInicial;
    private int rangoFinal;

    public Group(String idGrupo, Course curso, Teacher profesor, int tamanioGrupo, int rangoInicial, int rangoFinal) {
        this.idGrupo = idGrupo;
        this.curso = curso;
        this.profesor = profesor;
        this.tamanioGrupo = tamanioGrupo;
        this.rangoInicial = rangoInicial;
        this.rangoFinal = rangoFinal;
    }

    public Group() {
    }

    public String getIdGrupo() {
        return idGrupo;
    }

    public void setIdGrupo(String idGrupo) {
        this.idGrupo = idGrupo;
    }

    public Course getCurso() {
        return curso;
    }

    public void setCurso(Course curso) {
        this.curso = curso;
    }

    public Teacher getProfesor() {
        return profesor;
    }

    public void setProfesor(Teacher profesor) {
        this.profesor = profesor;
    }

    public int getTamanioGrupo() {
        return tamanioGrupo;
    }

    public void setTamanioGrupo(int tamanioGrupo) {
        this.tamanioGrupo = tamanioGrupo;
    }

    public int getRangoInicial() {
        return rangoInicial;
    }

    public void setRangoInicial(int rangoInicial) {
        this.rangoInicial = rangoInicial;
    }

    public int getRangoFinal() {
        return rangoFinal;
    }

    public void setRangoFinal(int rangoFinal) {
        this.rangoFinal = rangoFinal;
    }

    // Métodos útiles para tu TableView más adelante
    public String getNombreCurso() {
        return curso != null ? curso.getNombre() : "N/A";
    }

    public String getNombreProfesor() {
        return profesor != null ? profesor.getNombre() + " " + profesor.getApellidoPaterno() : "N/A";
    }

    public String getRangoTexto(){
        return rangoInicial + " - " + getRangoFinal();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return Objects.equals(idGrupo, group.idGrupo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(idGrupo);
    }
}
