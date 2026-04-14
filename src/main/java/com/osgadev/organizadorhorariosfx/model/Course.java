package com.osgadev.organizadorhorariosfx.model;

import java.util.Objects;

public class Course {

    private int id;
    private String nombre;
    private int minHorasSemanales;

    public Course(int id, String nombre, int diasMinimosSemanales) {  //constructor que usamos para poder hacer updates
        this.id = id;
        this.nombre = nombre;
        this.minHorasSemanales = diasMinimosSemanales;
    }

    public Course(String nombre, int diasMinimosSemanales) {
        this.nombre = nombre;
        this.minHorasSemanales = diasMinimosSemanales;
    }

    public Course(){ //constructor que usamos para hacer los inserts, debido a que reutilizamos la vista update
    }

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

    public int getMinHorasSemanales() {
        return minHorasSemanales;
    }

    public void setMinHorasSemanales(int minHorasSemanales) {
        this.minHorasSemanales = minHorasSemanales;
    }

    @Override
    public String toString() {
        return "Course{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", diasMinimosSemanales=" + minHorasSemanales +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return id == course.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
