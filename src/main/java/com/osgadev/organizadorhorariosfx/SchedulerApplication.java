package com.osgadev.organizadorhorariosfx;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SchedulerApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 1. APLICAR TEMA ATLANTAFX
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        FXMLLoader fxmlLoader = new FXMLLoader(SchedulerApplication.class.getResource("main-view.fxml"));

        // Puedes dejar 1024x700, la vista ahora será 100% responsiva
        Scene scene = new Scene(fxmlLoader.load(), 1024, 700);

        stage.setTitle("Organizador de Horarios");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}