package com.osgadev.organizadorhorariosfx.model;

import java.util.Objects;

public class Course {

    private int id;
    private String nombre;
    private int minHorasSemanales;

    // NUEVOS ATRIBUTOS
    private String descripcion;
    private String colorHex;

    // Constructor completo (Para Updates)
    public Course(int id, String nombre, int minHorasSemanales, String descripcion, String colorHex) {
        this.id = id;
        this.nombre = nombre;
        this.minHorasSemanales = minHorasSemanales;
        this.descripcion = descripcion;
        this.colorHex = colorHex;
    }

    // Constructor sin ID (Para Inserts)
    public Course(String nombre, int minHorasSemanales, String descripcion, String colorHex) {
        this.nombre = nombre;
        this.minHorasSemanales = minHorasSemanales;
        this.descripcion = descripcion;
        this.colorHex = colorHex;
    }

    // Constructores originales (mantienen compatibilidad)
    public Course(int id, String nombre, int diasMinimosSemanales) {
        this.id = id;
        this.nombre = nombre;
        this.minHorasSemanales = diasMinimosSemanales;
    }

    public Course(String nombre, int diasMinimosSemanales) {
        this.nombre = nombre;
        this.minHorasSemanales = diasMinimosSemanales;
    }

    public Course() {}

    // Getters y Setters originales
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getMinHorasSemanales() { return minHorasSemanales; }
    public void setMinHorasSemanales(int minHorasSemanales) { this.minHorasSemanales = minHorasSemanales; }

    // Nuevos Getters y Setters
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    @Override
    public String toString() {
        return nombre; // Simplificado para que los ComboBox se vean limpios
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