package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.CourseDAO;
import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class CourseController implements Initializable {

    // ==========================================
    // LADO IZQUIERDO (MASTER)
    // ==========================================
    @FXML private HBox rootHBox;
    @FXML private TextField txtBuscarCurso;

    @FXML private TableView<Course> tablaCursos;
    @FXML private TableColumn<Course, Integer> colNumeroCurso;
    @FXML private TableColumn<Course, String> colNombre;
    @FXML private TableColumn<Course, Integer> colHoras;

    // ==========================================
    // LADO DERECHO (DETALLE Y ESTADO VACÍO)
    // ==========================================
    @FXML private VBox panelVacio;
    @FXML private VBox panelFormulario;

    @FXML private Label lblTituloDetalle;
    @FXML private Button btnEliminarCurso;

    @FXML private TextField txtNombre;
    @FXML private Spinner<Integer> spnHoras;
    @FXML private ColorPicker colorPicker;
    @FXML private TextArea txtDescripcion;

    // Tabla inversa de profesores
    @FXML private TableView<Teacher> tablaProfesoresAsignados;
    @FXML private TableColumn<Teacher, String> colNombreProfesorDetalle;

    // ==========================================
    // VARIABLES DE ESTADO Y DAOs
    // ==========================================
    private CourseDAO courseDAO = new CourseDAO();
    private TeacherDAO teacherDAO = new TeacherDAO(); // Para la tabla inversa

    private ObservableList<Course> listaCursosFx;
    private ObservableList<Teacher> profesoresAsignadosFx = FXCollections.observableArrayList();

    private Course cursoSeleccionado = null; // null significa "Modo Crear"

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Cargar hoja de estilos CSS
        try {
            String cssPath = getClass().getResource("/css/styles.css").toExternalForm();
            rootHBox.getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.err.println("No se pudo cargar el archivo CSS.");
        }

        configurarTablaMaestra();
        configurarTablaDetalle();
        configurarSpinner();
        cargarDatosMaestros();

        panelFormulario.setVisible(false);
        panelVacio.setVisible(true);

        // Listener: Detectar selección en la tabla
        tablaCursos.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                cargarDetalleCurso(newSelection);
            }
        });
    }

    // ==========================================
    // MÉTODOS DE CONFIGURACIÓN
    // ==========================================
    private void configurarTablaMaestra() {
        colNumeroCurso.setCellValueFactory(celda -> new ReadOnlyObjectWrapper<>(listaCursosFx.indexOf(celda.getValue()) + 1));
        colNombre.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombre()));
        colHoras.setCellValueFactory(celda -> new SimpleIntegerProperty(celda.getValue().getMinHorasSemanales()).asObject());
    }

    private void configurarTablaDetalle() {
        // Configuramos la tablita inversa que muestra a los profesores
        colNombreProfesorDetalle.setCellValueFactory(celda -> new SimpleStringProperty(
                celda.getValue().getNombre() + " " + celda.getValue().getApellidoPaterno()
        ));
        tablaProfesoresAsignados.setItems(profesoresAsignadosFx);
    }

    private void configurarSpinner() {
        // Spinner de 1 a 20 horas, con valor inicial 4
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 4);
        spnHoras.setValueFactory(valueFactory);
        spnHoras.setEditable(true);

        // Truco de UX: Forzar al Spinner a guardar el valor escrito cuando el usuario hace clic fuera de él
        spnHoras.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // Si perdió el foco
                spnHoras.increment(0); // Esto obliga a procesar el texto escrito
            }
        });
    }

    private void cargarDatosMaestros() {
        // Cargar cursos
        listaCursosFx = FXCollections.observableArrayList(courseDAO.obtenerCursos());

        FilteredList<Course> datosFiltrados = new FilteredList<>(listaCursosFx, p -> true);

        txtBuscarCurso.textProperty().addListener((observable, oldValue, newValue) -> {
            datosFiltrados.setPredicate(curso -> {
                if (newValue == null || newValue.isEmpty()) return true;
                return curso.getNombre().toLowerCase().contains(newValue.toLowerCase());
            });
        });

        SortedList<Course> datosOrdenados = new SortedList<>(datosFiltrados);
        datosOrdenados.comparatorProperty().bind(tablaCursos.comparatorProperty());
        tablaCursos.setItems(datosOrdenados);
    }

    // ==========================================
    // MÉTODOS DE ACCIÓN (INTERFAZ)
    // ==========================================
    @FXML
    protected void onNuevoCursoClick() {
        cursoSeleccionado = null;
        tablaCursos.getSelectionModel().clearSelection();

        lblTituloDetalle.setText("Nuevo Curso");
        btnEliminarCurso.setVisible(false);

        limpiarFormulario();

        panelVacio.setVisible(false);
        panelFormulario.setVisible(true);
    }

    private void cargarDetalleCurso(Course curso) {
        cursoSeleccionado = curso;

        lblTituloDetalle.setText("Editando: " + curso.getNombre());
        btnEliminarCurso.setVisible(true);

        // Llenar campos
        txtNombre.setText(curso.getNombre());
        spnHoras.getValueFactory().setValue(curso.getMinHorasSemanales());
        txtDescripcion.setText(curso.getDescripcion() != null ? curso.getDescripcion() : "");

        // Convertir de Hex a Objeto Color para el ColorPicker
        if (curso.getColorHex() != null && !curso.getColorHex().isEmpty()) {
            colorPicker.setValue(Color.web(curso.getColorHex()));
        } else {
            colorPicker.setValue(Color.WHITE);
        }

        // Llenar la tabla inversa: Buscar qué profesores dan este curso
        // Usamos TeacherDAO y filtramos en memoria
        profesoresAsignadosFx.clear();
        List<Teacher> todosLosProfes = teacherDAO.obtenerProfesoresObservable();
        List<Teacher> profesDeEsteCurso = todosLosProfes.stream()
                .filter(profe -> profe.getCursos().stream().anyMatch(c -> c.getId() == curso.getId()))
                .collect(Collectors.toList());
        profesoresAsignadosFx.addAll(profesDeEsteCurso);

        panelVacio.setVisible(false);
        panelFormulario.setVisible(true);
    }

    @FXML
    protected void onGuardarClick() {
        // Validaciones básicas
        if (txtNombre.getText().trim().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "El nombre del curso es obligatorio.").showAndWait();
            return;
        }

        // Extraer color en Hexadecimal
        Color color = colorPicker.getValue();
        String colorHex = String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));

        Course cursoAGuardar = new Course(
                txtNombre.getText(),
                spnHoras.getValue(),
                txtDescripcion.getText(),
                colorHex
        );

        if (cursoSeleccionado == null) {
            // INSERT
            // Asumo que tu courseDAO.insertar(curso) ha sido actualizado para guardar descripción y color
            if (courseDAO.insertar(cursoAGuardar)) {
                cargarDatosMaestros();
                onCancelarClick();
            }
        } else {
            // UPDATE
            cursoAGuardar.setId(cursoSeleccionado.getId());
            if (courseDAO.actualizar(cursoAGuardar)) {
                cargarDatosMaestros();
                onCancelarClick();
            }
        }
    }

    @FXML
    protected void onEliminarCursoClick() {
        if (cursoSeleccionado == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Eliminación");
        alert.setHeaderText("¿Eliminar curso: " + cursoSeleccionado.getNombre() + "?");

        // ADVERTENCIA si hay profesores
        if (!profesoresAsignadosFx.isEmpty()) {
            alert.setContentText("ATENCIÓN: Hay " + profesoresAsignadosFx.size() + " profesor(es) asignados a este curso.\nSe eliminará la relación si continúas.");
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (courseDAO.eliminar(cursoSeleccionado.getId())) {
                listaCursosFx.remove(cursoSeleccionado);
                onCancelarClick();
            } else {
                new Alert(Alert.AlertType.ERROR, "Error al eliminar el curso.").showAndWait();
            }
        }
    }

    @FXML
    protected void onCancelarClick() {
        tablaCursos.getSelectionModel().clearSelection();
        cursoSeleccionado = null;
        limpiarFormulario();

        panelFormulario.setVisible(false);
        panelVacio.setVisible(true);
    }

    private void limpiarFormulario() {
        txtNombre.clear();
        spnHoras.getValueFactory().setValue(4); // Default
        txtDescripcion.clear();
        colorPicker.setValue(Color.WHITE);
        profesoresAsignadosFx.clear();
    }
}