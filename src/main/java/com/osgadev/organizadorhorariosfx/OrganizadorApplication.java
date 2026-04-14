package com.osgadev.organizadorhorariosfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class OrganizadorApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(OrganizadorApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 950, 600);
//        stage.initStyle(StageStyle.UNDECORATED);
//        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }
}
