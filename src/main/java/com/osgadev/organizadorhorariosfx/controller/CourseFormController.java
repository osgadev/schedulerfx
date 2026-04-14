package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.DAO.CourseDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CourseFormController {
    @FXML
    private TextField nombreCursoTextField;
    @FXML
    private TextField horasSemanaTextField;
    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;
    @FXML
    private Label mainMessageLabel;
    @FXML
    private Label secondaryMessageLabel;

    private boolean esModoEdicion = true;
    private Course cursoActual;
    private CourseDAO courseDAO = new CourseDAO();

    public void cargarCursoNuevo(){
        this.cursoActual = new Course();
        nombreCursoTextField.clear();
        horasSemanaTextField.clear();
//        nombreCursoTextField.setText(cursoActual.getNombre());
//        horasSemanaTextField.setText(String.valueOf(cursoActual.getMinHorasSemanales()));
        this.esModoEdicion = false;
    }

    public void cargarCursoExistente(Course curso){
        this.cursoActual = curso;
        nombreCursoTextField.setText(curso.getNombre());
        horasSemanaTextField.setText(String.valueOf(curso.getMinHorasSemanales()));

    }

    @FXML
    private void onSaveButtonClick(){
        boolean exito;
        if(!nombreCursoTextField.getText().isEmpty() && !horasSemanaTextField.getText().isEmpty()){
            if(!nombreCursoTextField.getText().matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s.,]{3,50}$")){
                mainMessageLabel.setText("Error, el nombre debe tener entre 3 y 50 caracteres\n");
                secondaryMessageLabel.setText("Solo letras, espacios, puntos y comas");
                return;
            }
            if (!horasSemanaTextField.getText().matches("^([1-9]|[1-3][0-9]|40)$")) {
                mainMessageLabel.setText("Error");
                secondaryMessageLabel.setText("Las horas deben ser un número entre 1 y 40");
                return;
            }
            cursoActual.setNombre(nombreCursoTextField.getText());
            cursoActual.setMinHorasSemanales(Integer.valueOf(horasSemanaTextField.getText()));

            if(esModoEdicion){
                exito = courseDAO.actualizar(cursoActual);
            } else {
                exito = courseDAO.insertar(cursoActual);
            }

            if(exito){
                cerrarVentana();
            } else {
                System.err.println("Ocurrio un error al guardar en la base de datos");
            }

        } else {
            mainMessageLabel.setText("Debes rellenar todos los campos");
        }



    }

    public void onCancelButtonClick(){
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
//        cerrarVentana();
    }

    private void cerrarVentana() {
        Stage stage = (Stage) nombreCursoTextField.getScene().getWindow();
        stage.close();
    }


}
