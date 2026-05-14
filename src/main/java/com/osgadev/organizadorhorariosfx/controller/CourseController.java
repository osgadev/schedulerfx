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

    @FXML private HBox rootHBox;
    @FXML private TextField txtBuscarCurso;

    @FXML private TableView<Course> tablaCursos;
    @FXML private TableColumn<Course, Integer> colNumeroCurso;
    @FXML private TableColumn<Course, String> colNombre;
    @FXML private TableColumn<Course, Integer> colHoras;

    @FXML private VBox panelVacio;
    @FXML private VBox panelFormulario;

    @FXML private Label lblTituloDetalle;
    @FXML private Button btnEliminarCurso;

    @FXML private TextField txtNombre;
    @FXML private Spinner<Integer> spnHoras;
    @FXML private ColorPicker colorPicker;
    @FXML private TextArea txtDescripcion;

    @FXML private TableView<Teacher> tablaProfesoresAsignados;
    @FXML private TableColumn<Teacher, String> colNombreProfesorDetalle;

    private CourseDAO courseDAO = new CourseDAO();
    private TeacherDAO teacherDAO = new TeacherDAO();

    private ObservableList<Course> listaCursosFx;
    private ObservableList<Teacher> profesoresAsignadosFx = FXCollections.observableArrayList();

    private Course cursoSeleccionado = null;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

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

        tablaCursos.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                cargarDetalleCurso(newSelection);
            }
        });
    }

    private void configurarTablaMaestra() {
        colNumeroCurso.setCellValueFactory(celda -> new ReadOnlyObjectWrapper<>(listaCursosFx.indexOf(celda.getValue()) + 1));
        colNombre.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombre()));
        colHoras.setCellValueFactory(celda -> new SimpleIntegerProperty(celda.getValue().getMinHorasSemanales()).asObject());
    }

    private void configurarTablaDetalle() {
        colNombreProfesorDetalle.setCellValueFactory(celda -> new SimpleStringProperty(
                celda.getValue().getNombre() + " " +
                        celda.getValue().getApellidoPaterno() + " " +
                        (celda.getValue().getApellidoMaterno() != null ? celda.getValue().getApellidoMaterno() : "")
        ));
        tablaProfesoresAsignados.setItems(profesoresAsignadosFx);
    }

    private void configurarSpinner() {
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 4);
        spnHoras.setValueFactory(valueFactory);
        spnHoras.setEditable(true);

        spnHoras.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                spnHoras.increment(0);
            }
        });
    }

    private void cargarDatosMaestros() {
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

        txtNombre.setText(curso.getNombre());
        spnHoras.getValueFactory().setValue(curso.getMinHorasSemanales());
        txtDescripcion.setText(curso.getDescripcion() != null ? curso.getDescripcion() : "");

        if (curso.getColorHex() != null && !curso.getColorHex().isEmpty()) {
            try {
                colorPicker.setValue(Color.web(curso.getColorHex()));
            } catch (Exception e) {
                colorPicker.setValue(Color.WHITE);
            }
        } else {
            colorPicker.setValue(Color.WHITE);
        }

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
        if (txtNombre.getText().trim().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "El nombre del curso es obligatorio.").showAndWait();
            return;
        }

        Color color = colorPicker.getValue();
        if (color == null) color = Color.WHITE;

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
            if (courseDAO.insertar(cursoAGuardar)) {
                cargarDatosMaestros();
                onCancelarClick();
            } else {
                new Alert(Alert.AlertType.ERROR, "No se pudo guardar el curso. Revisa que el nombre no esté duplicado.").showAndWait();
            }
        } else {
            cursoAGuardar.setId(cursoSeleccionado.getId());

            if (courseDAO.actualizar(cursoAGuardar)) {
                cursoSeleccionado.setNombre(cursoAGuardar.getNombre());
                cursoSeleccionado.setMinHorasSemanales(cursoAGuardar.getMinHorasSemanales());
                cursoSeleccionado.setDescripcion(cursoAGuardar.getDescripcion());
                cursoSeleccionado.setColorHex(cursoAGuardar.getColorHex());

                tablaCursos.refresh();

                onCancelarClick();
            } else {
                new Alert(Alert.AlertType.ERROR, "Error al actualizar el curso.").showAndWait();
            }
        }
    }

    @FXML
    protected void onEliminarCursoClick() {
        if (cursoSeleccionado == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Eliminación");
        alert.setHeaderText("¿Eliminar curso: " + cursoSeleccionado.getNombre() + "?");

        if (!profesoresAsignadosFx.isEmpty()) {
            alert.setContentText("ATENCIÓN: Hay " + profesoresAsignadosFx.size() + " profesor(es) capacitado(s) para esta materia.\nSe eliminará la relación si continúas.");
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (courseDAO.eliminar(cursoSeleccionado.getId())) {
                listaCursosFx.remove(cursoSeleccionado);
                onCancelarClick();
            } else {
                new Alert(Alert.AlertType.ERROR, "No se pudo eliminar el curso. Es posible que esté asignado a grupos existentes.").showAndWait();
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
        spnHoras.getValueFactory().setValue(4);
        txtDescripcion.clear();
        colorPicker.setValue(Color.WHITE);
        profesoresAsignadosFx.clear();
    }
}