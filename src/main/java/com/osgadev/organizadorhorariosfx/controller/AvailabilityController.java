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
import javafx.scene.input.MouseEvent;
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

    // CheckBoxes de los días enlazados desde el FXML
    @FXML private CheckBox chkLunes;
    @FXML private CheckBox chkMartes;
    @FXML private CheckBox chkMiercoles;
    @FXML private CheckBox chkJueves;
    @FXML private CheckBox chkViernes;
    @FXML private CheckBox chkSabado;
    @FXML private CheckBox chkDomingo;

    @FXML private ComboBox<String> cmbHoraInicio;
    @FXML private ComboBox<String> cmbMinutoInicio;
    @FXML private ComboBox<String> cmbHoraFin;
    @FXML private ComboBox<String> cmbMinutoFin;

    @FXML private Button btnAgregar;
    @FXML private Button btnCambiarCurso;
    @FXML private Button btnBorrar;
    @FXML private Button btnGuardar;
    @FXML private Button btnEliminarTodo;
    @FXML private GridPane gridCalendario;

    private CheckBox[] checkDias; // Arreglo para iterar fácilmente los días
    private List<BloqueTiempo> listaBloques = new ArrayList<>();
    private List<BloqueTiempo> bloquesSeleccionados = new ArrayList<>();

    private TeacherDAO teacherDAO = new TeacherDAO();
    private AvailabilityDAO availabilityDAO = new AvailabilityDAO();
    private CourseDAO courseDAO = new CourseDAO();

    private final int HORA_INICIO_VISUAL = 7;
    private final int HORA_FIN_VISUAL = 22;
    private final int FILAS_VISUALES = (HORA_FIN_VISUAL - HORA_INICIO_VISUAL) * 4;

    // Clase interna para manejar la lógica de cada bloque de tiempo
    private class BloqueTiempo {
        int colDia, slotInicioSemanal, slotFinSemanal;
        Course cursoSugerido;
        Pane uiNode;
        boolean superpuesto = false;

        public BloqueTiempo(int colDia, int slotInicioSemanal, int slotFinSemanal, Course cursoSugerido, Pane uiNode) {
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
        // Agrupar los CheckBoxes para facilitar la lectura
        checkDias = new CheckBox[]{chkLunes, chkMartes, chkMiercoles, chkJueves, chkViernes, chkSabado, chkDomingo};

        configurarComboBoxes();
        configurarCuadricula();
        cargarProfesores();
        cargarCursos();

        // Asignación de acciones a los botones
        btnAgregar.setOnAction(e -> agregarBloqueDesdeUI());
        btnBorrar.setOnAction(e -> borrarBloquesSeleccionados());
        btnGuardar.setOnAction(e -> guardarEnBD());
        btnEliminarTodo.setOnAction(e -> eliminarTodaDisponibilidad());
        btnCambiarCurso.setOnAction(e -> cambiarCursoDeSeleccionados());

        deshabilitarControles(true);
    }

    private void cargarCursos() {
        Course comodin = new Course();
        comodin.setId(-1);
        comodin.setNombre("-- Comodín (Cualquier curso) --");

        cmbCursoSugerido.getItems().add(comodin);
        cmbCursoSugerido.getItems().addAll(courseDAO.obtenerCursos());

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
        btnCambiarCurso.setDisable(true);
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
            nuevosBloques.add(new Availability(profe, b.cursoSugerido, b.slotInicioSemanal, b.slotFinSemanal));
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
        bloquesSeleccionados.clear();
        btnBorrar.setDisable(true);
        btnCambiarCurso.setDisable(true);
    }

    private void configurarComboBoxes() {
        for (int i = HORA_INICIO_VISUAL; i <= HORA_FIN_VISUAL; i++) {
            String hora = String.format("%02d", i);
            cmbHoraInicio.getItems().add(hora);
            cmbHoraFin.getItems().add(hora);
        }
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
            indexFila += 4; // 4 slots de 15 mins por hora
        }
    }

    // =====================================================================
    // CREACIÓN MASIVA Y ACTUALIZACIÓN VISUAL
    // =====================================================================
    private void agregarBloqueDesdeUI() {
        if (cmbHoraInicio.getValue() == null || cmbHoraFin.getValue() == null) return;

        int hInicio = Integer.parseInt(cmbHoraInicio.getValue());
        int mInicio = Integer.parseInt(cmbMinutoInicio.getValue());
        int hFin = Integer.parseInt(cmbHoraFin.getValue());
        int mFin = Integer.parseInt(cmbMinutoFin.getValue());

        Course cursoElegido = cmbCursoSugerido.getValue();
        if (cursoElegido != null && cursoElegido.getId() == -1) cursoElegido = null;

        if (hInicio < HORA_INICIO_VISUAL || hFin > HORA_FIN_VISUAL) return;

        boolean agregoAlMenosUno = false;

        // Iterar sobre los CheckBoxes para crear un bloque en cada día seleccionado
        for (int i = 0; i < checkDias.length; i++) {
            if (checkDias[i].isSelected()) {
                int slotInicio = (i * 48) + (hInicio * 2) + (mInicio / 30);
                int slotFin = (i * 48) + (hFin * 2) + (mFin / 30);

                if (slotFin > slotInicio) {
                    crearYPosicionarNodo(i + 1, slotInicio, slotFin, cursoElegido, hInicio, mInicio, hFin, mFin);
                    agregoAlMenosUno = true;
                }
            }
        }

        if (agregoAlMenosUno) {
            actualizarEstadoSuperposiciones();
        }
    }

    private void crearYPosicionarNodo(int colDia, int slotInicio, int slotFin, Course curso, int h1, int m1, int h2, int m2) {
        int filaInicio = ((h1 - HORA_INICIO_VISUAL) * 4) + (m1 / 15);
        int filaFin = ((h2 - HORA_INICIO_VISUAL) * 4) + (m2 / 15);

        VBox nodo = new VBox();
        nodo.setPadding(new Insets(2));

        BloqueTiempo bloque = new BloqueTiempo(colDia, slotInicio, slotFin, curso, nodo);

        actualizarContenidoVisualBloque(bloque);

        // SOPORTE PARA MULTI-SELECCIÓN CON CTRL O SHIFT
        nodo.setOnMouseClicked((MouseEvent e) -> seleccionarBloque(bloque, e));

        listaBloques.add(bloque);
        gridCalendario.add(nodo, colDia, filaInicio);
        GridPane.setRowSpan(nodo, filaFin - filaInicio);
    }

    private void actualizarContenidoVisualBloque(BloqueTiempo b) {
        b.uiNode.getChildren().clear();

        int h1 = (b.slotInicioSemanal % 48) / 2;
        int m1 = ((b.slotInicioSemanal % 48) % 2) * 30;
        int h2 = (b.slotFinSemanal % 48) / 2;
        int m2 = ((b.slotFinSemanal % 48) % 2) * 30;

        Label textoHora = new Label(String.format("%02d:%02d - %02d:%02d", h1, m1, h2, m2));
        textoHora.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        b.uiNode.getChildren().add(textoHora);

        if (b.cursoSugerido != null) {
            Label textoCurso = new Label(b.cursoSugerido.getNombre());
            textoCurso.setStyle("-fx-font-size: 9px; -fx-text-fill: #333333;");
            textoCurso.setWrapText(true);
            b.uiNode.getChildren().add(textoCurso);
        }
    }

    private void cambiarCursoDeSeleccionados() {
        Course cursoElegido = cmbCursoSugerido.getValue();
        if (cursoElegido != null && cursoElegido.getId() == -1) cursoElegido = null;

        for (BloqueTiempo b : bloquesSeleccionados) {
            b.cursoSugerido = cursoElegido;
            actualizarContenidoVisualBloque(b);
        }
        for (BloqueTiempo b : listaBloques) actualizarColorBloque(b);
    }

    private void seleccionarBloque(BloqueTiempo bloque, MouseEvent e) {
        if (e.isControlDown() || e.isShiftDown()) {
            if (bloquesSeleccionados.contains(bloque)) {
                bloquesSeleccionados.remove(bloque); // Deseleccionar si ya estaba seleccionado
            } else {
                bloquesSeleccionados.add(bloque);
            }
        } else {
            bloquesSeleccionados.clear();
            bloquesSeleccionados.add(bloque);
        }

        btnBorrar.setDisable(bloquesSeleccionados.isEmpty());
        btnCambiarCurso.setDisable(bloquesSeleccionados.isEmpty());

        for (BloqueTiempo b : listaBloques) actualizarColorBloque(b);
    }

    private void borrarBloquesSeleccionados() {
        if (!bloquesSeleccionados.isEmpty()) {
            for (BloqueTiempo b : bloquesSeleccionados) {
                gridCalendario.getChildren().remove(b.uiNode);
                listaBloques.remove(b);
            }
            bloquesSeleccionados.clear();
            btnBorrar.setDisable(true);
            btnCambiarCurso.setDisable(true);
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
        for (BloqueTiempo b : listaBloques) actualizarColorBloque(b);
        btnGuardar.setDisable(hayError || cmbProfesor.getValue() == null);
    }

    private void actualizarColorBloque(BloqueTiempo b) {
        boolean seleccionado = bloquesSeleccionados.contains(b);
        String bgColor = (b.cursoSugerido == null) ? "#99ff99" : "#add8e6"; // Verde (comodín) o Azul (curso específico)
        String borderColor = (b.cursoSugerido == null) ? "green" : "blue";
        int borderWidth = 1;

        if (b.superpuesto) {
            bgColor = "#ff9999"; // Rojo si chocan horarios
            borderColor = "red";
        }

        if (seleccionado) {
            borderColor = "#9C27B0"; // Borde morado para marcar selección
            borderWidth = 3;
        }

        b.uiNode.setStyle("-fx-background-color: " + bgColor +
                "; -fx-border-color: " + borderColor +
                "; -fx-border-width: " + borderWidth + "px;" +
                " -fx-border-radius: 3; -fx-cursor: hand;");
    }
}