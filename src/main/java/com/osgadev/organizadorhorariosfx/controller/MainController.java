package com.osgadev.organizadorhorariosfx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

import java.io.IOException;

public class MainController {
    @FXML
    private BorderPane mainBorderPane;

    private void cargarVista(String vistaFxml) {
        try{
            Parent vista = FXMLLoader.load(getClass().getResource("/com/osgadev/organizadorhorariosfx/" + vistaFxml));
            mainBorderPane.setCenter(vista);
        } catch (IOException e){
            System.out.println("Error al cargar la vista");
            e.printStackTrace();
            e.getCause();
        }
    }

    @FXML
    protected void onTeacherButtonClick() {
        cargarVista("teacher-view.fxml");
    }

    @FXML
    protected void onCourseButtonClick() {
        cargarVista("course-view.fxml");
    }

    @FXML
    protected void onGroupButtonClick(){cargarVista("group-view.fxml");}

    @FXML
    protected void onAvailabilityButtonClick(){cargarVista("availability-view.fxml");}

    @FXML
    protected void onScheduleButtonClick(){cargarVista("schedule-view.fxml");}
}
