module com.osgadev.organizadorhorariosfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires java.sql;
    requires mysql.connector.j;
    requires java.desktop;
    requires org.chocosolver.solver;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;

    opens com.osgadev.organizadorhorariosfx to javafx.fxml;
    exports com.osgadev.organizadorhorariosfx;
    exports com.osgadev.organizadorhorariosfx.util;
    opens com.osgadev.organizadorhorariosfx.util to javafx.fxml;
    exports com.osgadev.organizadorhorariosfx.controller;
    opens com.osgadev.organizadorhorariosfx.controller to javafx.fxml;
}