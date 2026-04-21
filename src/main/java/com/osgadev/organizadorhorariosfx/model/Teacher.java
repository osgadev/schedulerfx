package com.osgadev.organizadorhorariosfx.model;

import java.util.List;
import java.util.Objects;

public class Teacher {

    private int id;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String correoElectronico;
    private String telefono;
    private List<Course> cursos;
    private List<Availability> disponibilidades;

    public Teacher(int id, String nombre, String apellidoPaterno, String apellidoMaterno, String correoElectronico, String telefono, List<Course> cursos) {
        this.id = id;
        this.nombre = nombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.correoElectronico = correoElectronico;
        this.telefono = telefono;
        this.cursos = cursos;
    }

    public Teacher(String nombre, String apellidoPaterno, String apellidoMaterno, String correoElectronico, String telefono, List<Course> cursos) {
        this.nombre = nombre;
        this.apellidoPaterno = apellidoPaterno;
        this.apellidoMaterno = apellidoMaterno;
        this.correoElectronico = correoElectronico;
        this.telefono = telefono;
        this.cursos = cursos;
    }

    public Teacher(){}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellidoPaterno() {
        return apellidoPaterno;
    }

    public void setApellidoPaterno(String apellidoPaterno) {
        this.apellidoPaterno = apellidoPaterno;
    }

    public String getApellidoMaterno() {
        return apellidoMaterno;
    }

    public void setApellidoMaterno(String apellidoMaterno) {
        this.apellidoMaterno = apellidoMaterno;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public void setCorreoElectronico(String correoElectronico) {
        this.correoElectronico = correoElectronico;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public List<Course> getCursos() {
        return cursos;
    }

    public void setCursos(List<Course> cursos) {
        this.cursos = cursos;
    }

    public List<Availability> getDisponibilidades() {
        return disponibilidades;
    }

    public void setDisponibilidades(List<Availability> disponibilidades) {
        this.disponibilidades = disponibilidades;
    }

    @Override
    public String toString() {
        return "Teacher{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", apellidoPaterno='" + apellidoPaterno + '\'' +
                ", apellidoMaterno='" + apellidoMaterno + '\'' +
                ", correoElectronico='" + correoElectronico + '\'' +
                ", telefono='" + telefono + '\'' +
                ", cursos=" + cursos +
                ", disponibilidades=" + disponibilidades +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Teacher teacher = (Teacher) o;
        return id == teacher.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
