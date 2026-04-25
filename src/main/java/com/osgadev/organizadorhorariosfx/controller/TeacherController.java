package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.OrganizadorApplication;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class TeacherController implements Initializable {
    @FXML
    private TableView<Teacher> tablaProfesores;
    @FXML
    private TableColumn<Teacher, Number> colNumeroProfesor;
    @FXML
    private TableColumn<Teacher, String> colNombre;
    @FXML
    private TableColumn<Teacher, String> colCursos;
    @FXML
    private TableColumn<Teacher, Void> colAcciones;
    @FXML
    private Button guardarButton;

    private TeacherDAO teacherDAO = new TeacherDAO();
    private ObservableList<Teacher> listaProfesoresFx;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        configurarColumnas();
        cargarDatosEnTabla();
        agregarBotonesAccion();
    }

    public void onAddNewTeacherButtonClick(){
        showTeacherFormView(null);
    }

    public void showTeacherFormView(Teacher profesorAEditar){ //esta vista nos ayuda a agregar un nuevo curso o editar uno ya existente
        try {
            FXMLLoader loader = new FXMLLoader(OrganizadorApplication.class.getResource("teacher-form-view.fxml")); // cargarmos el FXML
            Parent root = loader.load();

            TeacherFormController teacherFormController = loader.getController();  //obtenemos el controlador de la ventana form

            Stage stage = new Stage();   //preparamos el stage
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL); // APPLICATION_MODAL bloquea la ventana de atras

            if (profesorAEditar == null) {   // decidimos el modo en que se abre la ventana (CREAR O EDITAR)
                // CREAR
                stage.setTitle("Agregar Nuevo Profesor");
                teacherFormController.cargarProfesorNuevo();
            } else {
                // EDITAR
                stage.setTitle("Informacion Profesor");
                teacherFormController.cargarProfesorExistente(profesorAEditar);
            }

            //mostrar la ventana y pausar el código hasta que se cierre
            stage.showAndWait();

            cargarDatosEnTabla(); // recargamos los datos desde MySQL para ver los cambios
//            tablaCursos.refresh();// aseguramos que la vista se repinte

        } catch (IOException e) {
            System.err.println("Error al abrir el formulario de curso: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void configurarColumnas(){
        colNumeroProfesor.setCellValueFactory(celda -> {
            int indice = tablaProfesores.getItems().indexOf(celda.getValue());
            return new SimpleIntegerProperty(indice + 1);
        });
//        colId.setCellValueFactory(celda -> new SimpleIntegerProperty(celda.getValue().getId()).asObject());
        colNombre.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombre() +" "+ celda.getValue().getApellidoPaterno() +" "+ celda.getValue().getApellidoMaterno()));
        colCursos.setCellValueFactory(celda -> {
            List<Course> listaCursos = celda.getValue().getCursos();
            if (listaCursos == null || listaCursos.isEmpty()){
                return new SimpleStringProperty("Sin cursos asignados");
            }
            String nombresCursos = listaCursos.stream().map(Course::getNombre).collect(Collectors.joining(", "));
            return new SimpleStringProperty(nombresCursos);
        });
    }

    private void cargarDatosEnTabla() {
        System.out.println("Cargando datos en tabla...");

        listaProfesoresFx = FXCollections.observableArrayList(teacherDAO.obtenerProfesoresObservable());
        tablaProfesores.setItems(listaProfesoresFx);

        System.out.println("Total profesores cargados: " + listaProfesoresFx.size());

    }

    private void agregarBotonesAccion() {
        Callback<TableColumn<Teacher, Void>, TableCell<Teacher, Void>> cellFactory = new Callback<>() {
            @Override
            public TableCell<Teacher, Void> call(final TableColumn<Teacher, Void> param) {

                return new TableCell<>() {
                    // 1. Instanciar los componentes visuales
                    private final Button btnEditar = new Button("Ver información");
                    private final Button btnEliminar = new Button("Eliminar");
                    private final HBox panelBotones = new HBox(10); // Espacio de 10px entre botones

                    {
                        // 2. Aplicar estilos básicos
                        btnEditar.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
                        btnEliminar.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand;");

                        // 3. Configurar el contenedor
                        panelBotones.setAlignment(Pos.CENTER);
                        panelBotones.getChildren().addAll(btnEditar, btnEliminar);

                        // ======================================
                        // ACCIÓN: ELIMINAR INFORMACION DEL PROFESOR
                        // ======================================
                        btnEliminar.setOnAction(event -> {

                            Teacher profesorSeleccionado = getTableView().getItems().get(getIndex());

                            Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
                            confirmationAlert.setTitle("Confirmar Eliminación");
                            confirmationAlert.setHeaderText("¿Estas seguro de borrar al profesor de la lista?");
                            confirmationAlert.setContentText("Profesor: " + profesorSeleccionado.getNombre() + "\nEsta accion no se puede deshacer.");

                            Optional<ButtonType> resultado = confirmationAlert.showAndWait();

                            if(resultado.isPresent() && resultado.get()==ButtonType.OK){
                                boolean borradoExitoso = teacherDAO.eliminar(profesorSeleccionado.getId());

                                if (borradoExitoso) {
                                    // Si se borró en BD, lo borramos de la lista observable para actualizar la vista
                                    listaProfesoresFx.remove(profesorSeleccionado);

                                    Alert succesAlert = new Alert(Alert.AlertType.INFORMATION);
                                    succesAlert.setTitle("Exito");
                                    succesAlert.setHeaderText(null);
                                    succesAlert.setContentText("El curso se ha eliminado correctamente");
                                    succesAlert.showAndWait();

                                } else {
                                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                                    errorAlert.setTitle("Error");
                                    errorAlert.setHeaderText("No se pudo eliminar el curso");
                                    errorAlert.setHeaderText("Ocurrió un error en la base de datos o el curso esta siendo usado por un profesor.");
                                    errorAlert.showAndWait();
                                }
                            }


                        });

                        // ======================================
                        // ACCIÓN: VER INFORMACION DEL PROFESOR (ABRIR POPUP)
                        // ======================================
                        btnEditar.setOnAction(event -> {
                            Teacher profesorSeleccionado = getTableView().getItems().get(getIndex());
                            showTeacherFormView(profesorSeleccionado);

                        });
                    }

                    // 4. Metodo que dibuja los botones en la tabla
                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);

                        // Solo dibujamos los botones si la fila actual pertenece a un curso válido
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(panelBotones);
                        }
                    }
                };
            }
        };
        colAcciones.setCellFactory(cellFactory);
    }
}
