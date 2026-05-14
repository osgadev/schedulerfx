package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.dao.CourseDAO;
import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.util.GlobalSession;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class TeacherController implements Initializable {

    @FXML private HBox rootHBox;

    @FXML private TextField txtBuscarProfesor;

    @FXML private TableView<Teacher> tablaProfesores;
    @FXML private TableColumn<Teacher, Integer> colNumeroProfesor;
    @FXML private TableColumn<Teacher, String> colEstado;
    @FXML private TableColumn<Teacher, String> colNombre;

    @FXML private VBox panelVacio;
    @FXML private VBox panelFormulario;
    @FXML private HBox boxAlertaDisponibilidad;

    @FXML private Label lblTituloDetalle;
    @FXML private Button btnIrDisponibilidad;
    @FXML private Button btnEliminarProfesor;

    @FXML private TextField txtNombre;
    @FXML private TextField txtApellidoP;
    @FXML private TextField txtApellidoM;
    @FXML private TextField txtCorreo;
    @FXML private TextField txtTelefono;

    @FXML private ComboBox<Course> cmbCursos;
    @FXML private TableView<Course> tablaCursosAsignados;
    @FXML private TableColumn<Course, String> colNombreCursoDetalle;
    @FXML private TableColumn<Course, Void> colAccionesCursoDetalle;

    private TeacherDAO teacherDAO = new TeacherDAO();
    private CourseDAO courseDAO = new CourseDAO();
    private AvailabilityDAO availabilityDAO = new AvailabilityDAO();

    private ObservableList<Teacher> listaProfesoresFx;
    private ObservableList<Course> cursosAsignadosFx = FXCollections.observableArrayList();

    private Teacher profesorSeleccionado = null;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        try {
            String cssPath = getClass().getResource("/css/styles.css").toExternalForm();
            rootHBox.getStylesheets().add(cssPath);
        } catch (NullPointerException e) {
            System.err.println("No se pudo cargar el archivo CSS. Verifica que esté en src/main/resources/css/styles.css");
        }

        configurarTablaMaestra();
        configurarTablaDetalle();
        cargarComboCursos();
        cargarDatosMaestros();

        panelFormulario.setVisible(false);
        panelVacio.setVisible(true);

        tablaProfesores.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                cargarDetalleProfesor(newSelection);
            }
        });
    }

    private void configurarTablaMaestra() {
        colNumeroProfesor.setCellValueFactory(celda -> new ReadOnlyObjectWrapper<>(listaProfesoresFx.indexOf(celda.getValue()) + 1));

        colNombre.setCellValueFactory(celda -> new SimpleStringProperty(
                celda.getValue().getNombre() + " " +
                        celda.getValue().getApellidoPaterno() + " " +
                        (celda.getValue().getApellidoMaterno() != null ? celda.getValue().getApellidoMaterno() : "")
        ));

        colEstado.setCellValueFactory(celda -> {
            Teacher profe = celda.getValue();
            return new SimpleStringProperty(profe.isTieneDisponibilidad() ? "true" : "false");
        });

        colEstado.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    ImageView imageView = new ImageView();
                    imageView.setFitWidth(18);
                    imageView.setFitHeight(18);

                    if (item.equals("false")) {
                        try {
                            imageView.setImage(new Image(getClass().getResourceAsStream("/images/warning.png")));
                        } catch(Exception e){}
                        setTooltip(new Tooltip("Sin disponibilidad asignada"));
                    } else {
                        try {
                            imageView.setImage(new Image(getClass().getResourceAsStream("/images/check.png")));
                        } catch(Exception e){}
                        setTooltip(new Tooltip("Disponibilidad OK"));
                    }
                    setGraphic(imageView);
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });
    }

    private void configurarTablaDetalle() {
        colNombreCursoDetalle.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombre()));
        tablaCursosAsignados.setItems(cursosAsignadosFx);

        Callback<TableColumn<Course, Void>, TableCell<Course, Void>> cellFactory = param -> new TableCell<>() {
            private final Button btnEliminar = new Button();
            {
                try {
                    Image iconoEliminar = new Image(getClass().getResourceAsStream("/images/remove.png"));
                    ImageView iconoView = new ImageView(iconoEliminar);
                    iconoView.setFitWidth(15); iconoView.setFitHeight(15);
                    btnEliminar.setGraphic(iconoView);
                } catch(Exception e){}
                btnEliminar.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                btnEliminar.setOnAction(e -> cursosAsignadosFx.remove(getIndex()));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnEliminar);
            }
        };
        colAccionesCursoDetalle.setCellFactory(cellFactory);
    }

    private void cargarComboCursos() {
        List<Course> listaCursos = courseDAO.obtenerCursos();
        cmbCursos.setItems(FXCollections.observableArrayList(listaCursos));

        cmbCursos.setCellFactory(param -> new ListCell<Course>() {
            @Override protected void updateItem(Course c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getNombre());
            }
        });
        cmbCursos.setButtonCell(new ListCell<Course>() {
            @Override protected void updateItem(Course c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.getNombre());
            }
        });
    }

    private void cargarDatosMaestros() {
        listaProfesoresFx = FXCollections.observableArrayList(teacherDAO.obtenerProfesoresObservable());

        FilteredList<Teacher> datosFiltrados = new FilteredList<>(listaProfesoresFx, p -> true);

        txtBuscarProfesor.textProperty().addListener((observable, oldValue, newValue) -> {
            datosFiltrados.setPredicate(profesor -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String filtro = newValue.toLowerCase();

                if (profesor.getNombre().toLowerCase().contains(filtro)) {
                    return true;
                } else if (profesor.getApellidoPaterno().toLowerCase().contains(filtro)) {
                    return true;
                } else if (profesor.getApellidoMaterno() != null && profesor.getApellidoMaterno().toLowerCase().contains(filtro)) {
                    return true;
                }
                return false;
            });
        });

        SortedList<Teacher> datosOrdenados = new SortedList<>(datosFiltrados);
        datosOrdenados.comparatorProperty().bind(tablaProfesores.comparatorProperty());

        tablaProfesores.setItems(datosOrdenados);
    }

    @FXML
    protected void onNuevoProfesorClick() {
        profesorSeleccionado = null;
        tablaProfesores.getSelectionModel().clearSelection();

        lblTituloDetalle.setText("Nuevo Profesor");
        btnEliminarProfesor.setVisible(false);
        btnIrDisponibilidad.setVisible(false);

        boxAlertaDisponibilidad.setVisible(false);
        boxAlertaDisponibilidad.setManaged(false);

        limpiarFormulario();

        panelVacio.setVisible(false);
        panelFormulario.setVisible(true);
    }

    private void cargarDetalleProfesor(Teacher profe) {
        profesorSeleccionado = profe;

        lblTituloDetalle.setText("Editando a: " + profe.getNombre());
        btnEliminarProfesor.setVisible(true);
        btnIrDisponibilidad.setVisible(true);

        boolean tieneDisp = profe.isTieneDisponibilidad();
        if (!tieneDisp) {
            boxAlertaDisponibilidad.setVisible(true);
            boxAlertaDisponibilidad.setManaged(true);
        } else {
            boxAlertaDisponibilidad.setVisible(false);
            boxAlertaDisponibilidad.setManaged(false);
        }

        txtNombre.setText(profe.getNombre());
        txtApellidoP.setText(profe.getApellidoPaterno());
        txtApellidoM.setText(profe.getApellidoMaterno() != null ? profe.getApellidoMaterno() : "");
        txtCorreo.setText(profe.getCorreoElectronico());
        txtTelefono.setText(profe.getTelefono());

        cursosAsignadosFx.clear();
        if (profe.getCursos() != null) {
            cursosAsignadosFx.addAll(profe.getCursos());
        }

        panelVacio.setVisible(false);
        panelFormulario.setVisible(true);
    }

    @FXML
    protected void onGuardarClick() {
        Teacher profeAGuardar = new Teacher(
                txtNombre.getText(), txtApellidoP.getText(), txtApellidoM.getText(),
                txtCorreo.getText(), txtTelefono.getText(), new ArrayList<>(cursosAsignadosFx)
        );

        if (profesorSeleccionado == null) {
            if (teacherDAO.insertar(profeAGuardar)) {
                cargarDatosMaestros();
                onCancelarClick();
            }
        } else {
            profeAGuardar.setId(profesorSeleccionado.getId());
            if (teacherDAO.actualizar(profeAGuardar)) {
                cargarDatosMaestros();
                onCancelarClick();
            }
        }
    }

    @FXML
    protected void onEliminarProfesorClick() {
        if (profesorSeleccionado == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Eliminación");
        alert.setHeaderText("¿Eliminar al profesor " + profesorSeleccionado.getNombre() + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (teacherDAO.eliminar(profesorSeleccionado.getId())) {
                listaProfesoresFx.remove(profesorSeleccionado);
                onCancelarClick();
            } else {
                new Alert(Alert.AlertType.ERROR, "No se pudo eliminar, el profesor está en uso.").showAndWait();
            }
        }
    }

    @FXML
    protected void onCancelarClick() {
        tablaProfesores.getSelectionModel().clearSelection();
        profesorSeleccionado = null;
        limpiarFormulario();

        panelFormulario.setVisible(false);
        panelVacio.setVisible(true);
    }

    @FXML
    protected void onAgregarCursoClick() {
        Course cursoSel = cmbCursos.getSelectionModel().getSelectedItem();
        if (cursoSel != null && !cursosAsignadosFx.contains(cursoSel)) {
            cursosAsignadosFx.add(cursoSel);
        }
    }

    @FXML
    protected void onIrDisponibilidadClick() {
        if (profesorSeleccionado != null) {
            GlobalSession.setProfesorNavegacion(profesorSeleccionado.getId());

            if (MainController.getInstance() != null) {
                MainController.getInstance().onAvailabilityButtonClick();
            }
        }
    }

    private void limpiarFormulario() {
        txtNombre.clear(); txtApellidoP.clear(); txtApellidoM.clear();
        txtCorreo.clear(); txtTelefono.clear();
        cursosAsignadosFx.clear();
        cmbCursos.getSelectionModel().clearSelection();
    }
}