package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.CourseDAO;
import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class TeacherFormController implements Initializable {

    @FXML
    private ComboBox<Course> cursosCombo;
    @FXML
    private TableView<Course> tableCursos;
    @FXML
    private TableColumn<Course, String> colNombreCurso;
    @FXML
    private TableColumn<Course, Void> colAcciones;
    @FXML
    private Button btnAgregarCurso;
    @FXML
    private Button cancelButton;

    @FXML
    private TextField nombreProfesorTextField;
    @FXML
    private TextField apellidoPProfesorTextField;
    @FXML
    private TextField apellidoMProfesorTextField;
    @FXML
    private TextField correoEProfesorTextField;
    @FXML
    private TextField telefonoProfesorTextField;

    private CourseDAO courseDAO = new CourseDAO();
    private TeacherDAO teacherDAO = new TeacherDAO();
    private ObservableList<Course> cursosObservable = FXCollections.observableArrayList();
    private Teacher profesorActual;
    private boolean esModoEdicion = true;

    public void onSaveButtonClick(){
//        if(!validarCamposProfesor()){
//            return;
//        }
        Teacher nuevoProfesor = new Teacher(
                nombreProfesorTextField.getText(),
                apellidoPProfesorTextField.getText(),
                apellidoMProfesorTextField.getText(),
                correoEProfesorTextField.getText(),
                telefonoProfesorTextField.getText(),
                new ArrayList<>(cursosObservable)
        );

        if (!esModoEdicion){
            boolean guardado = teacherDAO.insertar(nuevoProfesor);
            if(guardado){
                System.out.println("Exito, profesor guardado correctamente");
                onCancelButtonClick();
            } else {
                System.out.println("Error, No se pudo guardar el profesor");
            }
        } else {
            nuevoProfesor.setId(profesorActual.getId());
            boolean actualizado = teacherDAO.actualizar(nuevoProfesor);
            if(actualizado){
                System.out.println("Exito, informacion del profesor actualizada correctamente");
                onCancelButtonClick();
            } else {
                System.out.println("Error, No se pudo guardar la informacion del profesor");
            }
        }
    }

    public void cargarProfesorNuevo(){
        this.profesorActual = new Teacher();
        nombreProfesorTextField.clear();
        apellidoPProfesorTextField.clear();
        apellidoMProfesorTextField.clear();
        correoEProfesorTextField.clear();
        telefonoProfesorTextField.clear();
        esModoEdicion = false;
    }

    public void cargarProfesorExistente(Teacher profesor){
        this.profesorActual = profesor;
        nombreProfesorTextField.setText(profesor.getNombre());
        apellidoPProfesorTextField.setText(profesor.getApellidoPaterno());
        apellidoMProfesorTextField.setText(profesor.getApellidoMaterno());
        correoEProfesorTextField.setText(profesor.getCorreoElectronico());
        telefonoProfesorTextField.setText(profesor.getTelefono());

        if(profesor.getCursos() != null && !profesor.getCursos().isEmpty()){
            cursosObservable.addAll(profesor.getCursos());
            for (Course curso: profesor.getCursos()){
                System.out.println(curso.getId());  //AQUI HACEMOS LAS PRUEBAS DE CURSOS DUPLICADOS DEBUG
            }
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        cargarComboCursos();
        configurarTablaCursos();
        agregarBotonEliminar();
//        btnAgregarCurso.setOnAction(this::agregarCursoATabla);

        System.out.println("Tabla configurada con lista: " + cursosObservable);
    }

    public void agregarCursoATabla() {
        Course cursoSeleccionado = cursosCombo.getSelectionModel().getSelectedItem();

        if (cursoSeleccionado == null) {
            System.out.println("Se debe seleccionar un curso");
            return;
        }

        // Verificar si ya está en la tabla para no duplicar
        if (cursosObservable.contains(cursoSeleccionado)) {
            System.out.println("Información," + "El curso ya está agregado");
            return;
        }

        // Agregar a la tabla
        cursosObservable.add(cursoSeleccionado);
        System.out.println("El id del curso agregado es: " + cursoSeleccionado.getId());  //DEBUG DEL DUPLICADO DE CURSO

        // Opcional: limpiar selección del combo
//        cursosCombo.getSelectionModel().clearSelection();

    }


    private void configurarTablaCursos() {
        colNombreCurso.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombre()));
        tableCursos.setItems(cursosObservable);
    }

    public void cargarComboCursos(){

        List<Course> listaCursos = courseDAO.obtenerCursos();
        ObservableList<Course> cursos =FXCollections.observableArrayList(listaCursos);

        cursosCombo.setItems(cursos);
        configurarVisualizacionCombo();

    }

    private void configurarVisualizacionCombo() {
        // Personalizar cómo se ve cada item en el ComboBox
        cursosCombo.setCellFactory(param -> new ListCell<Course>() {
            @Override
            protected void updateItem(Course curso, boolean empty) {
                super.updateItem(curso, empty);
                if (empty || curso == null) {
                    setText(null);
                } else {
                    setText(curso.getNombre()); // Muestra solo el nombre
                }
            }
        });

        // Personalizar cómo se ve el item seleccionado
        cursosCombo.setButtonCell(new ListCell<Course>() {
            @Override
            protected void updateItem(Course curso, boolean empty) {
                super.updateItem(curso, empty);
                if (empty || curso == null) {
                    setText(null);
                } else {
                    setText(curso.getNombre());
                }
            }
        });
    }

    private void agregarBotonEliminar() {

        Callback<TableColumn<Course, Void>, TableCell<Course, Void>> cellFactory = param -> {

            return new TableCell<>() {

                private final Button btnEliminar = new Button();
                private final HBox panelBoton = new HBox();

                {
                    Image iconoEliminar = new Image(getClass().getResourceAsStream("/images/remove.png"));
                    ImageView iconoView = new ImageView(iconoEliminar);
                    iconoView.setFitWidth(20);
                    iconoView.setFitHeight(20);

                    btnEliminar.setGraphic(iconoView);
                    btnEliminar.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

                    Tooltip tooltipEliminar = new Tooltip("Eliminar curso");
                    btnEliminar.setTooltip(tooltipEliminar);

                    btnEliminar.setOnAction(event -> {

                        Course cursoSeleccionado = getTableView().getItems().get(getIndex());

                        // eliminar solo de la tabla
                        cursosObservable.remove(getIndex());

                    });

                    // agregar botón al contenedor
                    panelBoton.setAlignment(Pos.CENTER);
                    panelBoton.getChildren().add(btnEliminar);
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty) {
                        setGraphic(null);
                    } else {
                        setGraphic(panelBoton);
                    }
                }
            };
        };

        colAcciones.setCellFactory(cellFactory);
    }

    public void onCancelButtonClick(){
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}
