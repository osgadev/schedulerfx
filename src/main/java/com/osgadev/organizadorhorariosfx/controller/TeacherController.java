package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.dao.CourseDAO;
import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.util.SessionGlobal;

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

    // ==========================================
    // LADO IZQUIERDO (MASTER)
    // ==========================================
    @FXML private HBox rootHBox;

    @FXML private TextField txtBuscarProfesor; // Barra de Búsqueda

    @FXML private TableView<Teacher> tablaProfesores;
    @FXML private TableColumn<Teacher, Integer> colNumeroProfesor;
    @FXML private TableColumn<Teacher, String> colEstado;
    @FXML private TableColumn<Teacher, String> colNombre;

    // ==========================================
    // LADO DERECHO (DETALLE Y ESTADO VACÍO)
    // ==========================================
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

    // ==========================================
    // VARIABLES DE ESTADO Y DAOs
    // ==========================================
    private TeacherDAO teacherDAO = new TeacherDAO();
    private CourseDAO courseDAO = new CourseDAO();
    private AvailabilityDAO availabilityDAO = new AvailabilityDAO();

    private ObservableList<Teacher> listaProfesoresFx;
    private ObservableList<Course> cursosAsignadosFx = FXCollections.observableArrayList();

    private Teacher profesorSeleccionado = null; // null significa "Modo Crear"

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        // --- CARGAR LA HOJA DE ESTILOS CSS ---
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

        // Asegurarnos de que inicie en Estado Vacío
        panelFormulario.setVisible(false);
        panelVacio.setVisible(true);

        // Listener: Detectar cuando el usuario hace clic en un profesor de la tabla
        tablaProfesores.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                cargarDetalleProfesor(newSelection);
            }
        });
    }

    // ==========================================
    // MÉTODOS DE CONFIGURACIÓN
    // ==========================================
    private void configurarTablaMaestra() {
        // En lugar de usar el índice de la tabla (que cambia al filtrar), mostramos el ID o generamos un índice basado en la lista original
        colNumeroProfesor.setCellValueFactory(celda -> new ReadOnlyObjectWrapper<>(listaProfesoresFx.indexOf(celda.getValue()) + 1));

        colNombre.setCellValueFactory(celda -> new SimpleStringProperty(
                celda.getValue().getNombre() + " " +
                        celda.getValue().getApellidoPaterno() + " " +
                        (celda.getValue().getApellidoMaterno() != null ? celda.getValue().getApellidoMaterno() : "")
        ));

        // Columna de Estado (✅ o ⚠️) leyendo directamente del modelo optimizado
        colEstado.setCellValueFactory(celda -> {
            Teacher profe = celda.getValue();
            // Ya no consultamos a la BD, usamos el atributo booleano
            return new SimpleStringProperty(profe.isTieneDisponibilidad() ? "✅" : "⚠️");
        });

        // Darle color y Tooltip a la columna de estado
        colEstado.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    if (item.equals("⚠️")) {
                        setStyle("-fx-text-fill: #e67e22; -fx-alignment: CENTER; -fx-font-size: 14px;");
                        setTooltip(new Tooltip("Sin disponibilidad asignada"));
                    } else {
                        setStyle("-fx-text-fill: #27ae60; -fx-alignment: CENTER; -fx-font-size: 14px;");
                        setTooltip(new Tooltip("Disponibilidad OK"));
                    }
                }
            }
        });
    }

    private void configurarTablaDetalle() {
        colNombreCursoDetalle.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombre()));
        tablaCursosAsignados.setItems(cursosAsignadosFx);

        // Botón Eliminar Curso de la tabla de detalle (del formulario derecho)
        Callback<TableColumn<Course, Void>, TableCell<Course, Void>> cellFactory = param -> new TableCell<>() {
            private final Button btnEliminar = new Button();
            {
                Image iconoEliminar = new Image(getClass().getResourceAsStream("/images/remove.png"));
                ImageView iconoView = new ImageView(iconoEliminar);
                iconoView.setFitWidth(15); iconoView.setFitHeight(15);
                btnEliminar.setGraphic(iconoView);
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

        // Personalizar ComboBox para que muestre solo el nombre
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
        // Cargar datos originales
        listaProfesoresFx = FXCollections.observableArrayList(teacherDAO.obtenerProfesoresObservable());

        // Envolver la lista para aplicar el filtro
        FilteredList<Teacher> datosFiltrados = new FilteredList<>(listaProfesoresFx, p -> true);

        // Configurar listener para el campo de búsqueda
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

        // Envolver el FilteredList en un SortedList para soportar ordenamiento de columnas
        SortedList<Teacher> datosOrdenados = new SortedList<>(datosFiltrados);
        datosOrdenados.comparatorProperty().bind(tablaProfesores.comparatorProperty());

        // Establecer la lista ordenada y filtrada en la tabla
        tablaProfesores.setItems(datosOrdenados);
    }

    // ==========================================
    // MÉTODOS DE ACCIÓN (INTERFAZ)
    // ==========================================
    @FXML
    protected void onNuevoProfesorClick() {
        profesorSeleccionado = null; // Modo Creación
        tablaProfesores.getSelectionModel().clearSelection();

        lblTituloDetalle.setText("Nuevo Profesor");
        btnEliminarProfesor.setVisible(false);
        btnIrDisponibilidad.setVisible(false);

        // Ocultar alerta de disponibilidad en modo nuevo
        boxAlertaDisponibilidad.setVisible(false);
        boxAlertaDisponibilidad.setManaged(false);

        limpiarFormulario();

        // Alternar vistas al modo Formulario
        panelVacio.setVisible(false);
        panelFormulario.setVisible(true);
    }

    private void cargarDetalleProfesor(Teacher profe) {
        profesorSeleccionado = profe; // Modo Edición

        lblTituloDetalle.setText("Editando a: " + profe.getNombre());
        btnEliminarProfesor.setVisible(true);
        btnIrDisponibilidad.setVisible(true);

        // Lógica de validación de Disponibilidad para la alerta, leyendo del modelo
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

        // Alternar vistas al modo Formulario
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
            // INSERT
            if (teacherDAO.insertar(profeAGuardar)) {
                cargarDatosMaestros(); // Recargamos lista completa
                onCancelarClick(); // Limpiamos pantalla
            }
        } else {
            // UPDATE
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
                listaProfesoresFx.remove(profesorSeleccionado); // La lista se actualiza y la UI refleja el cambio
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

        // Regresar al estado vacío (Empty State)
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
            SessionGlobal.setProfesorNavegacion(profesorSeleccionado.getId());

            // Llama a la instancia global de MainController para cambiar la vista
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