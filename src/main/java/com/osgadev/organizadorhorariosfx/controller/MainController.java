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

    // ==========================================
    // PATRÓN SINGLETON PARA NAVEGACIÓN GLOBAL
    // ==========================================
    private static MainController instance;

    public static MainController getInstance() {
        return instance;
    }

    // ==========================================
    // ETIQUETAS FXML
    // ==========================================
    @FXML private BorderPane mainBorderPane;
    @FXML private StackPane contentArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Guardar la instancia activa para que otros controladores puedan llamarla
        instance = this;

        // 2. Cargar el Dashboard (Inicio) por defecto al iniciar la app
        onHomeButtonClick();
    }

    // ==========================================
    // NÚCLEO DE NAVEGACIÓN
    // ==========================================
    private void cargarVista(String vistaFxml) {
        try {
            Parent vista = FXMLLoader.load(getClass().getResource("/com/osgadev/organizadorhorariosfx/" + vistaFxml));

            // Limpiamos el StackPane antes de inyectar la nueva vista para liberar memoria
            contentArea.getChildren().clear();
            contentArea.getChildren().add(vista);

        } catch (IOException e) {
            System.err.println("Error crítico al cargar la vista: " + vistaFxml);
            e.printStackTrace();
        }
    }

    // ==========================================
    // BOTONES DEL SIDEBAR
    // ==========================================
    @FXML
    protected void onHomeButtonClick() {
        cargarVista("home-view.fxml");
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
    protected void onGroupButtonClick() {
        cargarVista("group-view.fxml");
    }

    @FXML
    public void onAvailabilityButtonClick() {
        // Nota: Este método es 'public' para que TeacherController pueda llamarlo desde afuera
        cargarVista("availability-view.fxml");
    }

    @FXML
    protected void onScheduleButtonClick() {
        cargarVista("schedule-view.fxml");
    }
}