package com.osgadev.organizadorhorariosfx.model;

public class Availability {

    private int id;
    private Teacher profesor;
    private Course cursoSugerido;

    // Rango: 0 a 335 (Representan el inicio y fin absoluto en la semana, 48 bloques x 7 días)
    private int startSlot;
    private int endSlot;

    // Constante auxiliar para cálculos matemáticos (48 bloques de 30 mins al día)
    public static final int SLOTS_POR_DIA = 48;

    // --- Constructores ---
    public Availability(int id, Teacher profesor, Course cursoSugerido, int startSlot, int endSlot) {
        this.id = id;
        this.profesor = profesor;
        this.cursoSugerido = cursoSugerido;
        this.startSlot = startSlot;
        this.endSlot = endSlot;
    }

    public Availability(Teacher profesor, Course cursoSugerido, int startSlot, int endSlot) {
        this.profesor = profesor;
        this.cursoSugerido = cursoSugerido;
        this.startSlot = startSlot;
        this.endSlot = endSlot;
    }

    public Availability() {}

    // --- Getters y Setters Básicos ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Teacher getProfesor() { return profesor; }
    public void setProfesor(Teacher profesor) { this.profesor = profesor; }

    public Course getCursoSugerido() {
        return cursoSugerido;
    }

    public void setCursoSugerido(Course cursoSugerido) {
        this.cursoSugerido = cursoSugerido;
    }

    public int getStartSlot() { return startSlot; }
    public void setStartSlot(int startSlot) { this.startSlot = startSlot; }

    public int getEndSlot() { return endSlot; }
    public void setEndSlot(int endSlot) { this.endSlot = endSlot; }

    // --- Métodos de Utilidad (Matemática Modular) ---

    /**
     * Calcula la duración total del bloque en intervalos de 30 minutos.
     */
    public int getDuracionEnBloques() {
        return this.endSlot - this.startSlot;
    }

    /**
     * Retorna el índice del día para la UI.
     * Útil para pintar en el GridPane de JavaFX.
     * @return 1 para Lunes, 2 para Martes ... 7 para Domingo.
     */
    public int getColumnaDia() {
        // Ejemplo: slot 70 / 48 = 1 (Es el día 1, que es Martes).
        // Le sumamos 1 porque las columnas en tu GridPane empiezan en 1.
        return (this.startSlot / SLOTS_POR_DIA) + 1;
    }

    /**
     * Extrae el texto del día basado en el Slot, útil para mostrar reportes o etiquetas.
     */
    public String getNombreDia() {
        int indexDia = this.startSlot / SLOTS_POR_DIA;
        switch (indexDia) {
            case 0: return "Lunes";
            case 1: return "Martes";
            case 2: return "Miércoles";
            case 3: return "Jueves";
            case 4: return "Viernes";
            case 5: return "Sábado";
            case 6: return "Domingo";
            default: return "Desconocido";
        }
    }

    /**
     * Obtiene la hora de inicio en formato 24 hrs.
     * Ejemplo: Slot 16 -> Retorna 8 (8:00 AM).
     */
    public int getHoraInicio() {
        int slotsDelDia = this.startSlot % SLOTS_POR_DIA;
        return slotsDelDia / 2; // Dividimos entre 2 porque hay 2 bloques por hora
    }

    /**
     * Obtiene el minuto de inicio (0 o 30).
     * Ejemplo: Slot 17 -> Retorna 30.
     */
    public int getMinutoInicio() {
        int slotsDelDia = this.startSlot % SLOTS_POR_DIA;
        return (slotsDelDia % 2) * 30; // El residuo nos dice si es media hora
    }

    /**
     * Obtiene la hora de fin en formato 24 hrs.
     */
    public int getHoraFin() {
        int slotsDelDia = this.endSlot % SLOTS_POR_DIA;
        return slotsDelDia / 2;
    }

    /**
     * Obtiene el minuto de fin (0 o 30).
     */
    public int getMinutoFin() {
        int slotsDelDia = this.endSlot % SLOTS_POR_DIA;
        return (slotsDelDia % 2) * 30;
    }

    /**
     * Genera una etiqueta visual limpia del horario.
     * @return Ej: "08:00 - 09:30"
     */
    public String getEtiquetaHorario() {
        return String.format("%02d:%02d - %02d:%02d",
                getHoraInicio(), getMinutoInicio(),
                getHoraFin(), getMinutoFin());
    }
}
