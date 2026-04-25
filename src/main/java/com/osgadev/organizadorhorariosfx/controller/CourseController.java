package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.CourseDAO;
import com.osgadev.organizadorhorariosfx.OrganizadorApplication;
import com.osgadev.organizadorhorariosfx.model.Course;
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

public class CourseController implements Initializable {

    @FXML
    private TableView<Course> tablaCursos;  //variable de la tabla
    @FXML
    private TableColumn<Course, Number> colNumeroCurso;  //variable de las columnas, con el tipo del objeto y el tipo de dato, usamos clases envoltorio por que trabajamos con objetos
    @FXML
    private TableColumn<Course, String> colNombre;
    @FXML
    private TableColumn<Course, Integer> colHoras;
    @FXML
    private TableColumn<Course, Void> colAcciones;
    @FXML
    private Button addNewCourseButton;

    private CourseDAO courseDAO = new CourseDAO();//declaramos e inicializamos el dao (es la clase que hace las consultas a la bd)
    private ObservableList<Course> listaCursosFx;// decalramos la lista de cursos, aqui vaciaremos el resultset


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {  //configurando celdas de la tabla, le decimos a java de donde obtener la informacion para la tabla
        colNumeroCurso.setCellValueFactory(celda -> {
            int indice = tablaCursos.getItems().indexOf(celda.getValue());
            return new SimpleIntegerProperty(indice + 1);
        });
//        colId.setCellValueFactory(celda -> new SimpleIntegerProperty(celda.getValue().getId()).asObject()); //el metodo setCellValueFactory espera un callback, la interfaz callback tiene solo un metodo call, requiere un parametro de entrada (CellDataFeatures = celda) y regresa un observable value (valor del objeto que obtenermos con el metodo get, lo obtenemos del POJO)
        colNombre.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombre()));   // leer la documentacion de la clase table column y callback
        colHoras.setCellValueFactory(celda -> new SimpleIntegerProperty(celda.getValue().getMinHorasSemanales()).asObject());

        agregarBotonesAccion();
        cargarDatosEnTabla();
    }

    public void onAddNewCourseButtonClick(){
        showCourseFormView(null); //para añadir un nuevo registro le pasamos null all metodo showCourseFormView
    }

    private void agregarBotonesAccion() {
        Callback<TableColumn<Course, Void>, TableCell<Course, Void>> cellFactory = new Callback<>() {
            @Override
            public TableCell<Course, Void> call(final TableColumn<Course, Void> param) {

                return new TableCell<>() {
                    // 1. Instanciar los componentes visuales
                    private final Button btnEditar = new Button("Editar");
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
                        // ACCIÓN: ELIMINAR CURSO
                        // ======================================
                        btnEliminar.setOnAction(event -> {

                            Course cursoSeleccionado = getTableView().getItems().get(getIndex());

                            Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
                            confirmationAlert.setTitle("Confirmar Eliminacion");
                            confirmationAlert.setHeaderText("¿Estas seguro de eliminar este curso?");
                            confirmationAlert.setContentText("Curso: " + cursoSeleccionado.getNombre() + "\nEsta accion no se puede deshacer.");

                            Optional<ButtonType> resultado = confirmationAlert.showAndWait();

                            if(resultado.isPresent() && resultado.get()==ButtonType.OK){
                                boolean borradoExitoso = courseDAO.eliminar(cursoSeleccionado.getId());

                                if (borradoExitoso) {
                                    // Si se borró en BD, lo borramos de la lista observable para actualizar la vista
                                    listaCursosFx.remove(cursoSeleccionado);

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
                        // ACCIÓN: EDITAR CURSO (ABRIR POPUP)
                        // ======================================
                        btnEditar.setOnAction(event -> {
                            Course cursoSeleccionado = getTableView().getItems().get(getIndex());
                            showCourseFormView(cursoSeleccionado);
//                            tablaCursos.refresh();
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

        // 5. Asignar la fábrica a la columna correspondiente
        colAcciones.setCellFactory(cellFactory);
    }

    public void showCourseFormView(Course cursoAEditar){ //esta vista nos ayuda a agregar un nuevo curso o editar uno ya existente
        try {
            FXMLLoader loader = new FXMLLoader(OrganizadorApplication.class.getResource("course-form-view.fxml")); // cargarmos el FXML
            Parent root = loader.load();

            CourseFormController courseFormController = loader.getController();  //obtenemos el controlador de la ventana form

            Stage stage = new Stage();   //preparamos el stage
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL); // APPLICATION_MODAL bloquea la ventana de atras

            if (cursoAEditar == null) {   // decidimos el modo en que se abre la ventana (CREAR O EDITAR)
                // CREAR
                stage.setTitle("Agregar Nuevo Curso");
                courseFormController.cargarCursoNuevo();
            } else {
                // EDITAR
                stage.setTitle("Editar Curso");
                courseFormController.cargarCursoExistente(cursoAEditar);
            }

            //mostrar la ventana y pausar el código hasta que se cierre
            stage.showAndWait();

            cargarDatosEnTabla(); // recargamos los datos desde MySQL para ver los cambios
            tablaCursos.refresh();// aseguramos que la vista se repinte

        } catch (IOException e) {
            System.err.println("Error al abrir el formulario de curso: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cargarDatosEnTabla() {
        //forma explicita
        List<Course> datosBD = courseDAO.obtenerCursos();  //metemos los datos que obtenemos de la lista que retorna el metodo del dao en una nuevaa lista

        listaCursosFx = FXCollections.observableArrayList(datosBD);  //convertimos la lista de cursos en una lista observable (puede notificar cambios en la ui)

        tablaCursos.setItems(listaCursosFx); //ahora la tabla esta observando nuestra lista de cursos, escuchara cualquier cambio

    }

}
