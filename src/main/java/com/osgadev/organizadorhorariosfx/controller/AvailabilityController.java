package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.DAO.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.DAO.CourseDAO;
import com.osgadev.organizadorhorariosfx.DAO.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class AvailabilityController implements Initializable {

    @FXML private ComboBox<Teacher> cmbProfesor;
    @FXML private ComboBox<Course> cmbCursoSugerido;
    @FXML private Label lblEstadoBD;
    @FXML private ComboBox<String> cmbDia;
    @FXML private ComboBox<String> cmbHoraInicio;
    @FXML private ComboBox<String> cmbMinutoInicio;
    @FXML private ComboBox<String> cmbHoraFin;
    @FXML private ComboBox<String> cmbMinutoFin;
    @FXML private Button btnAgregar;
    @FXML private Button btnBorrar;
    @FXML private Button btnGuardar;
    @FXML private Button btnEliminarTodo;
    @FXML private GridPane gridCalendario;

    private List<BloqueTiempo> listaBloques = new ArrayList<>();
    private BloqueTiempo bloqueSeleccionado = null;

    private TeacherDAO teacherDAO = new TeacherDAO();
    private AvailabilityDAO availabilityDAO = new AvailabilityDAO();
    private CourseDAO courseDAO = new CourseDAO();

    private final int HORA_INICIO_VISUAL = 7;
    private final int HORA_FIN_VISUAL = 22;
    // Mantenemos 4 filas por hora para la vista, para ser consistentes con el calendario de horarios
    private final int FILAS_VISUALES = (HORA_FIN_VISUAL - HORA_INICIO_VISUAL) * 4;

    private class BloqueTiempo {
        int colDia, slotInicioSemanal, slotFinSemanal;
        Course cursoSugerido;
        Pane uiNode;
        boolean superpuesto = false;

        public BloqueTiempo(int colDia, int slotInicioSemanal, int slotFinSemanal,Course cursoSugerido, Pane uiNode) {
            this.colDia = colDia;
            this.slotInicioSemanal = slotInicioSemanal;
            this.slotFinSemanal = slotFinSemanal;
            this.cursoSugerido = cursoSugerido;
            this.uiNode = uiNode;
        }

        public boolean seSuperpone(BloqueTiempo otro) {
            return (this.slotInicioSemanal < otro.slotFinSemanal && this.slotFinSemanal > otro.slotInicioSemanal);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configurarComboBoxes();
        configurarCuadricula();
        cargarProfesores();
        cargarCursos();

        btnAgregar.setOnAction(e -> agregarBloqueDesdeUI());
        btnBorrar.setOnAction(e -> borrarBloqueSeleccionado());
        btnGuardar.setOnAction(e -> guardarEnBD());
        btnEliminarTodo.setOnAction(e -> eliminarTodaDisponibilidad());

        deshabilitarControles(true);
    }

    private void cargarCursos() {
        // Agregamos un curso "Comodín" ficticio para que el profe pueda no elegir nada
        Course comodin = new Course();
        comodin.setId(-1);
        comodin.setNombre("-- Comodín (Cualquier curso) --");

        cmbCursoSugerido.getItems().add(comodin);
        cmbCursoSugerido.getItems().addAll(courseDAO.obtenerCursos()); // Asegúrate de tener este método en tu DAO

        cmbCursoSugerido.setConverter(new StringConverter<Course>() {
            @Override
            public String toString(Course c) { return c == null ? "" : c.getNombre(); }
            @Override
            public Course fromString(String s) { return null; }
        });

        cmbCursoSugerido.getSelectionModel().selectFirst();
    }

    private void deshabilitarControles(boolean deshabilitar) {
        btnAgregar.setDisable(deshabilitar);
        btnGuardar.setDisable(deshabilitar);
        btnEliminarTodo.setDisable(deshabilitar);
        btnBorrar.setDisable(true);
    }

    private void cargarProfesores() {
        cmbProfesor.getItems().addAll(teacherDAO.obtenerProfesoresObservable());

        cmbProfesor.setConverter(new StringConverter<Teacher>() {
            @Override
            public String toString(Teacher t) { return t == null ? "" : t.getNombre() + " " + t.getApellidoPaterno(); }
            @Override
            public Teacher fromString(String s) { return null; }
        });

        cmbProfesor.setOnAction(e -> {
            Teacher profe = cmbProfesor.getValue();
            if (profe != null) {
                deshabilitarControles(false);
                cargarDesdeBD(profe);
            }
        });
    }

    private void cargarDesdeBD(Teacher profe) {
        limpiarCuadricula();
        List<Availability> guardados = availabilityDAO.getByTeacher(profe);

        if (guardados.isEmpty()) {
            lblEstadoBD.setText("Sin datos guardados");
            lblEstadoBD.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        } else {
            lblEstadoBD.setText("Mostrando " + guardados.size() + " bloques");
            lblEstadoBD.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            for (Availability dbBlock : guardados) {
                int colDia = dbBlock.getColumnaDia();
                int hInicio = dbBlock.getHoraInicio();
                int mInicio = dbBlock.getMinutoInicio();
                int hFin = dbBlock.getHoraFin();
                int mFin = dbBlock.getMinutoFin();

                if (hInicio >= HORA_INICIO_VISUAL && hFin <= HORA_FIN_VISUAL) {
                    crearYPosicionarNodo(colDia, dbBlock.getStartSlot(), dbBlock.getEndSlot(), dbBlock.getCursoSugerido(), hInicio, mInicio, hFin, mFin);
                }
            }
            actualizarEstadoSuperposiciones();
        }
    }

    private void guardarEnBD() {
        Teacher profe = cmbProfesor.getValue();
        if (profe == null) return;

        List<Availability> nuevosBloques = new ArrayList<>();
        for (BloqueTiempo b : listaBloques) {
            nuevosBloques.add(new Availability(profe,b.cursoSugerido, b.slotInicioSemanal, b.slotFinSemanal));
        }

        availabilityDAO.saveAll(profe, nuevosBloques);
        lblEstadoBD.setText("Guardado exitosamente");
        lblEstadoBD.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
    }

    private void eliminarTodaDisponibilidad() {
        Teacher profe = cmbProfesor.getValue();
        if (profe == null) return;

        availabilityDAO.deleteAllByTeacher(profe);
        limpiarCuadricula();
        lblEstadoBD.setText("Disponibilidad eliminada");
        lblEstadoBD.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
    }

    private void limpiarCuadricula() {
        for (BloqueTiempo b : listaBloques) {
            gridCalendario.getChildren().remove(b.uiNode);
        }
        listaBloques.clear();
        bloqueSeleccionado = null;
        btnBorrar.setDisable(true);
    }

    private void configurarComboBoxes() {
        cmbDia.getItems().addAll("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");
        cmbDia.getSelectionModel().selectFirst();
        for (int i = HORA_INICIO_VISUAL; i <= HORA_FIN_VISUAL; i++) {
            String hora = String.format("%02d", i);
            cmbHoraInicio.getItems().add(hora);
            cmbHoraFin.getItems().add(hora);
        }
        // --- CAMBIO: Solo permitimos 00 y 30 para coincidir con el motor de Choco ---
        String[] minutos = {"00", "30"};
        cmbMinutoInicio.getItems().addAll(minutos);
        cmbMinutoFin.getItems().addAll(minutos);
        cmbMinutoInicio.getSelectionModel().selectFirst();
        cmbMinutoFin.getSelectionModel().selectFirst();
    }

    private void configurarCuadricula() {
        for (int i = 0; i < FILAS_VISUALES; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(20.0); rc.setPrefHeight(20.0);
            gridCalendario.getRowConstraints().add(rc);
        }
        for (int col = 1; col < 8; col++) {
            Pane sep = new Pane(); sep.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1;");
            gridCalendario.add(sep, col, 0, 1, FILAS_VISUALES);
        }
        int indexFila = 0;
        for (int i = HORA_INICIO_VISUAL; i < HORA_FIN_VISUAL; i++) {
            Pane sep = new Pane(); sep.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
            gridCalendario.add(sep, 0, indexFila, 8, 1);
            Label lbl = new Label(String.format("%02d:00", i));
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: gray; -fx-padding: 2;");
            gridCalendario.add(lbl, 0, indexFila);
            indexFila += 4;
        }
    }

    private void agregarBloqueDesdeUI() {
        if (cmbHoraInicio.getValue() == null || cmbHoraFin.getValue() == null) return;

        int indexDia = cmbDia.getSelectionModel().getSelectedIndex();
        int hInicio = Integer.parseInt(cmbHoraInicio.getValue());
        int mInicio = Integer.parseInt(cmbMinutoInicio.getValue());
        int hFin = Integer.parseInt(cmbHoraFin.getValue());
        int mFin = Integer.parseInt(cmbMinutoFin.getValue());

        Course cursoElegido = cmbCursoSugerido.getValue();
        if (cursoElegido != null && cursoElegido.getId() == -1) cursoElegido = null;

        if (hInicio < HORA_INICIO_VISUAL || hFin > HORA_FIN_VISUAL) return;

        // --- CAMBIO: Nueva matemática de resolución (48 bloques de 30 mins) ---
        // (indexDia * 48) + (hInicio * 2) + (mInicio / 30)
        int slotInicio = (indexDia * 48) + (hInicio * 2) + (mInicio / 30);
        int slotFin = (indexDia * 48) + (hFin * 2) + (mFin / 30);

        if (slotFin <= slotInicio) return;

        crearYPosicionarNodo(indexDia + 1, slotInicio, slotFin, cursoElegido, hInicio, mInicio, hFin, mFin);
        actualizarEstadoSuperposiciones();
    }

    private void crearYPosicionarNodo(int colDia, int slotInicio, int slotFin,Course curso, int h1, int m1, int h2, int m2) {
        // --- CAMBIO: La vista sigue usando la lógica de "4 filas por hora" para dibujar correctamente ---
        // Se divide entre 15 para saber cuántas filas visuales debe bajar, aunque el fondo sea de 30 mins
        int filaInicio = ((h1 - HORA_INICIO_VISUAL) * 4) + (m1 / 15);
        int filaFin = ((h2 - HORA_INICIO_VISUAL) * 4) + (m2 / 15);

        VBox nodo = new VBox();
        nodo.setPadding(new Insets(2));

        String bgColor = curso == null ? "#99ff99" : "#add8e6";
        String borderColor = curso == null ? "green" : "blue";
        nodo.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + borderColor + "; -fx-border-radius: 3;");

        Label textoHora = new Label(String.format("%02d:%02d - %02d:%02d", h1, m1, h2, m2));
        textoHora.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        nodo.getChildren().add(textoHora);

        if (curso != null) {
            Label textoCurso = new Label(curso.getNombre());
            textoCurso.setStyle("-fx-font-size: 9px; -fx-text-fill: #333333;");
            textoCurso.setWrapText(true);
            nodo.getChildren().add(textoCurso);
        }

        BloqueTiempo bloque = new BloqueTiempo(colDia, slotInicio, slotFin, curso, nodo);
        nodo.setOnMouseClicked(e -> seleccionarBloque(bloque));

        listaBloques.add(bloque);
        gridCalendario.add(nodo, colDia, filaInicio);
        GridPane.setRowSpan(nodo, filaFin - filaInicio);
    }

    private void seleccionarBloque(BloqueTiempo bloque) {
        if (bloqueSeleccionado != null) actualizarColorBloque(bloqueSeleccionado);
        bloqueSeleccionado = bloque;
        btnBorrar.setDisable(false);

        // Respetamos el color original azul si tiene curso, verde si es comodín, rojo si choca
        String bgColor = (bloque.cursoSugerido == null) ? "#99ff99" : "#add8e6";
        if (bloque.superpuesto) bgColor = "#ff9999";

        // Al seleccionar, el borde siempre se pone azul rey grueso
        bloque.uiNode.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: blue; -fx-border-width: 2px; -fx-border-radius: 3;");
    }

    private void borrarBloqueSeleccionado() {
        if (bloqueSeleccionado != null) {
            gridCalendario.getChildren().remove(bloqueSeleccionado.uiNode);
            listaBloques.remove(bloqueSeleccionado);
            bloqueSeleccionado = null;
            btnBorrar.setDisable(true);
            actualizarEstadoSuperposiciones();
        }
    }

    private void actualizarEstadoSuperposiciones() {
        boolean hayError = false;
        for (BloqueTiempo b : listaBloques) b.superpuesto = false;

        for (int i = 0; i < listaBloques.size(); i++) {
            for (int j = i + 1; j < listaBloques.size(); j++) {
                if (listaBloques.get(i).seSuperpone(listaBloques.get(j))) {
                    listaBloques.get(i).superpuesto = true;
                    listaBloques.get(j).superpuesto = true;
                    hayError = true;
                }
            }
        }
        for (BloqueTiempo b : listaBloques) if (b != bloqueSeleccionado) actualizarColorBloque(b);
        btnGuardar.setDisable(hayError || cmbProfesor.getValue() == null);
    }

    private void actualizarColorBloque(BloqueTiempo b) {
        if (b.superpuesto) {
            b.uiNode.setStyle("-fx-background-color: #ff9999; -fx-border-color: red; -fx-border-radius: 3;");
        } else {
            // Evaluamos nuevamente si tiene curso sugerido para devolverle su color original
            String bgColor = (b.cursoSugerido == null) ? "#99ff99" : "#add8e6";
            String borderColor = (b.cursoSugerido == null) ? "green" : "blue";
            b.uiNode.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + borderColor + "; -fx-border-radius: 3;");
        }
    }
}
