package com.osgadev.organizadorhorariosfx.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    @FXML
    private BorderPane mainBorderPane;
    @FXML
    private StackPane contentArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cargar el Dashboard (Inicio) por defecto al iniciar la app
        onHomeButtonClick();
    }

    private void cargarVista(String vistaFxml) {
        try {
            Parent vista = FXMLLoader.load(getClass().getResource("/com/osgadev/organizadorhorariosfx/" + vistaFxml));
            // Limpiamos el StackPane antes de inyectar la nueva vista (opcional pero recomendado)
            contentArea.getChildren().clear();
            contentArea.getChildren().add(vista);
        } catch (IOException e) {
            System.out.println("Error al cargar la vista: " + vistaFxml);
            e.printStackTrace();
        }
    }

    // Nuevo método para el botón de Inicio
    @FXML
    protected void onHomeButtonClick() {
        cargarVista("home-view.fxml");
    }

    // Tus otros métodos se quedan igual...
    @FXML protected void onTeacherButtonClick() { cargarVista("teacher-view.fxml"); }
    @FXML protected void onCourseButtonClick() { cargarVista("course-view.fxml"); }
    @FXML protected void onGroupButtonClick() { cargarVista("group-view.fxml"); }
    @FXML protected void onAvailabilityButtonClick() { cargarVista("availability-view.fxml"); }
    @FXML protected void onScheduleButtonClick() { cargarVista("schedule-view.fxml"); }
}