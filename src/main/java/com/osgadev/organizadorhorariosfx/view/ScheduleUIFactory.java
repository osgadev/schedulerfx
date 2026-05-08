package com.osgadev.organizadorhorariosfx.view;

import com.osgadev.organizadorhorariosfx.model.Group;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class ScheduleUIFactory {

    // 1. CONFIGURACIÓN BASE DEL GRID
    public static void configurarEstructuraGrid(GridPane gridCalendario, int horaInicio, int horaFin) {
        gridCalendario.getColumnConstraints().clear();
        gridCalendario.getRowConstraints().clear();

        ColumnConstraints colHora = new ColumnConstraints();
        colHora.setMinWidth(60);
        colHora.setPrefWidth(60);
        colHora.setMaxWidth(60);
        gridCalendario.getColumnConstraints().add(colHora);

        String[] nombresDias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

        for (int i = 0; i < 7; i++) {
            ColumnConstraints colDia = new ColumnConstraints();
            colDia.prefWidthProperty().bind(gridCalendario.widthProperty().subtract(60).divide(7));
            colDia.setMinWidth(80);
            colDia.setMaxWidth(Double.MAX_VALUE);
            colDia.setHgrow(Priority.ALWAYS);
            colDia.setFillWidth(true);
            gridCalendario.getColumnConstraints().add(colDia);
        }

        RowConstraints rowCabecera = new RowConstraints();
        rowCabecera.setMinHeight(30);
        rowCabecera.setPrefHeight(30);
        gridCalendario.getRowConstraints().add(rowCabecera);

        Label lblTituloHora = new Label("Hora");
        lblTituloHora.setStyle("-fx-font-weight: bold; -fx-padding: 5; -fx-text-fill: #70757a;");
        gridCalendario.add(lblTituloHora, 0, 0);

        for (int i = 0; i < nombresDias.length; i++) {
            HBox headerBox = new HBox(5);
            headerBox.setAlignment(Pos.CENTER);
            headerBox.setMaxWidth(Double.MAX_VALUE);

            Label lblDia = new Label(nombresDias[i]);
            lblDia.setStyle("-fx-font-weight: bold; -fx-text-fill: #70757a;");

            Button btnExpandir = new Button("↔");
            btnExpandir.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2;");
            btnExpandir.setDisable(true);

            headerBox.getChildren().addAll(lblDia, btnExpandir);
            gridCalendario.add(headerBox, i + 1, 0);
        }

        int numFilasTiempo = (horaFin - horaInicio) * 2;
        for (int i = 0; i < numFilasTiempo; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(40);
            rc.setPrefHeight(40);
            gridCalendario.getRowConstraints().add(rc);
        }

        for (int col = 1; col <= 7; col++) {
            Pane sepCol = new Pane();
            sepCol.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(sepCol, Priority.ALWAYS);
            sepCol.setStyle("-fx-border-color: #dadce0; -fx-border-width: 0 0 0 1;");
            sepCol.setMouseTransparent(true);
            gridCalendario.add(sepCol, col, 1, 1, numFilasTiempo);
        }

        int filaActual = 1;
        for (int hora = horaInicio; hora < horaFin; hora++) {
            Pane sepRow = new Pane();
            sepRow.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(sepRow, Priority.ALWAYS);
            sepRow.setStyle("-fx-border-color: #dadce0; -fx-border-width: 1 0 0 0;");
            sepRow.setMouseTransparent(true);
            gridCalendario.add(sepRow, 0, filaActual, 8, 1);

            Label lblHora = new Label(hora + ":00");
            lblHora.setStyle("-fx-font-size: 11px; -fx-padding: 2; -fx-text-fill: #70757a;");
            gridCalendario.add(lblHora, 0, filaActual);

            filaActual += 2;
        }
    }

    // 2. BLOQUES DE ALMACÉN MANUAL
    public static StackPane crearBloqueGeneradorVisual(double horas, String hexColor) {
        StackPane panel = new StackPane();

        Rectangle fondo = new Rectangle(45, 30);
        fondo.setArcWidth(6);
        fondo.setArcHeight(6);

        Color baseColor = parseColorSafely(hexColor, "#4A90E2");

        if (isTooWhite(baseColor)) {
            baseColor = Color.web("#E8EAED");
        }

        fondo.setFill(baseColor);
        fondo.setStroke(baseColor.deriveColor(0, 1.0, 0.8, 1.0));
        fondo.setStrokeWidth(1.0);

        String textColorStr = obtenerColorTextoContraste(baseColor);
        Text texto = new Text(horas + "h");
        texto.setFill(Color.web(textColorStr));
        texto.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        panel.getChildren().addAll(fondo, texto);

        Color colorHover = baseColor.deriveColor(0, 1.0, 0.9, 1.0);
        Color finalColorBase = baseColor;
        panel.setOnMouseEntered(e -> fondo.setFill(colorHover));
        panel.setOnMouseExited(e -> fondo.setFill(finalColorBase));
        panel.setStyle("-fx-cursor: hand;");

        return panel;
    }

    // 3. FANTASMA DE ARRASTRE
    public static VBox crearContenedorFantasma() {
        VBox fantasma = new VBox();
        fantasma.setMouseTransparent(true);

        // LIMPIEZA: Eliminado el setPrefWidth(9999)
        fantasma.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(fantasma, Priority.ALWAYS);
        GridPane.setFillWidth(fantasma, true);

        return fantasma;
    }

    // 4. TARJETAS DE SESIÓN EN CALENDARIO
    public static VBox crearTarjetaSesionVisual(Group g, String hexColor, String textoHora,
                                                boolean showCurso, boolean showProfesor,
                                                boolean showAlumnos, boolean showId) {

        Color baseColor = parseColorSafely(hexColor, "#4285F4");

        if (isTooWhite(baseColor)) {
            baseColor = Color.web("#E8EAED");
        }

        Color borderColor = baseColor.deriveColor(0, 1.0, 0.8, 1.0);
        String borderHex = toHexString(borderColor);
        String backgroundHex = toHexString(baseColor);

        VBox caja = new VBox();

        // LIMPIEZA: Eliminado el setPrefWidth(9999) y el Listener al parentProperty
        caja.setMaxWidth(Double.MAX_VALUE);
        caja.setMaxHeight(Double.MAX_VALUE);
        GridPane.setHgrow(caja, Priority.ALWAYS);
        GridPane.setFillWidth(caja, true);

        caja.setStyle(
                "-fx-background-color: " + backgroundHex + ";" +
                        "-fx-border-color: " + borderHex + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-background-radius: 4;" +
                        "-fx-border-radius: 4;" +
                        "-fx-padding: 4 4 4 6;"
        );

        GridPane.setMargin(caja, new javafx.geometry.Insets(1, 1, 1, 1));

        String textoColor = obtenerColorTextoContraste(baseColor);
        String mainText = textoColor.equals("#FFFFFF") ? "#FFFFFF" : "#202124";
        String subText = textoColor.equals("#FFFFFF") ? "#FFFFFF" : "#5f6368";

        Label lblHoraVista = new Label(textoHora);
        lblHoraVista.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: " + mainText + ";");
        caja.getChildren().add(lblHoraVista);

        if (showCurso) {
            Label lblCurso = new Label(g.getCurso().getNombre());
            lblCurso.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + mainText + ";");
            lblCurso.setWrapText(true);
            caja.getChildren().add(lblCurso);
        }
        if (showProfesor) {
            Label lblProf = new Label(g.getProfesor().getNombre());
            lblProf.setStyle("-fx-font-size: 10px; -fx-text-fill: " + subText + ";");
            lblProf.setWrapText(true);
            caja.getChildren().add(lblProf);
        }
        if (showAlumnos) {
            Label lblAlumnos = new Label("Alumnos: " + g.getRangoInicial() + "-" + g.getRangoFinal());
            lblAlumnos.setStyle("-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: " + subText + ";");
            caja.getChildren().add(lblAlumnos);
        }
        if (showId) {
            Label lblId = new Label("ID: " + g.getIdGrupo());
            lblId.setStyle("-fx-font-size: 9px; -fx-text-fill: " + subText + ";");
            caja.getChildren().add(lblId);
        }

        String infoTooltip = String.format("Horario: %s\n%s\nProf: %s %s\nAlumnos: %d al %d\nGrupo ID: %s",
                textoHora, g.getCurso().getNombre(),
                g.getProfesor().getNombre(), g.getProfesor().getApellidoPaterno(),
                g.getRangoInicial(), g.getRangoFinal(), g.getIdGrupo());

        Tooltip tooltip = new Tooltip(infoTooltip);
        Tooltip.install(caja, tooltip);

        return caja;
    }

    // --- MÉTODOS DE UTILIDAD PARA COLORES ---
    private static Color parseColorSafely(String hex, String defaultHex) {
        try {
            return Color.web(hex != null && !hex.isEmpty() ? hex : defaultHex);
        } catch (Exception e) {
            return Color.web(defaultHex);
        }
    }

    private static boolean isTooWhite(Color color) {
        return color.getRed() > 0.95 && color.getGreen() > 0.95 && color.getBlue() > 0.95;
    }

    private static String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private static String obtenerColorTextoContraste(Color color) {
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
        return luminance > 0.5 ? "#000000" : "#FFFFFF";
    }

    // 5. EFECTO CRISTAL VERDE (Disponibilidad)
    public static void aplicarEfectoCristal(Pane celda) {
        String estiloCristal = "-fx-background-color: rgba(76, 175, 80, 0.25); -fx-border-color: rgba(255, 255, 255, 0.5);";
        celda.setStyle(estiloCristal);
        // Mantenemos la lógica de Properties para no requerir refactorización externa
        celda.getProperties().put("estiloOriginal", estiloCristal);
        celda.getProperties().put("esValido", true);
    }

    // 6. FABRICADOR DE SUGERENCIA FIJA
    public static VBox crearSugerenciaFijaVisual(String nombreCurso, String textoHora, int duracionFilas) {
        VBox sugerencia = new VBox();
        sugerencia.setMouseTransparent(true);
        sugerencia.getProperties().put("esSugerencia", true);

        // LIMPIEZA: Eliminado el setPrefWidth(9999)
        sugerencia.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(sugerencia, Priority.ALWAYS);
        GridPane.setFillWidth(sugerencia, true);

        sugerencia.setStyle(
                "-fx-background-color: rgba(211, 47, 47, 0.15);" +
                        "-fx-border-color: rgba(211, 47, 47, 0.8);" +
                        "-fx-border-width: 2; -fx-border-style: dashed; -fx-border-radius: 4; -fx-padding: 3;"
        );
        sugerencia.setMinHeight(duracionFilas * 40);
        sugerencia.setMaxHeight(duracionFilas * 40);

        Label lblIcono = new Label("⭐ " + nombreCurso);
        lblIcono.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(183, 28, 28, 1.0); -fx-font-weight: bold;");
        lblIcono.setWrapText(true);

        Label lblHoraSug = new Label(textoHora);
        lblHoraSug.setStyle("-fx-font-size: 9px; -fx-text-fill: rgba(183, 28, 28, 0.85); -fx-font-weight: bold;");

        sugerencia.getChildren().addAll(lblIcono, lblHoraSug);

        FadeTransition ft = new FadeTransition(Duration.millis(800), sugerencia);
        ft.setFromValue(0.3);
        ft.setToValue(1.0);
        ft.setCycleCount(Animation.INDEFINITE);
        ft.setAutoReverse(true);
        ft.play();

        return sugerencia;
    }

    // 7. EFECTO SUGERENCIA LIBRE
    public static void aplicarEfectoSugerenciaLibre(Pane celda) {
        // LIMPIEZA: Reemplazado el frágil "String.replace" por una asignación directa de estilo
        celda.setStyle("-fx-background-color: rgba(76, 175, 80, 0.25); -fx-border-color: rgba(211, 47, 47, 0.8); -fx-border-style: dashed; -fx-border-width: 1;");

        // LIMPIEZA: Verificación para prevenir fugas de memoria (apilar animaciones infinitas)
        if (!celda.getProperties().containsKey("animacionSug")) {
            FadeTransition ftCelda = new FadeTransition(Duration.millis(800), celda);
            ftCelda.setFromValue(0.4);
            ftCelda.setToValue(1.0);
            ftCelda.setCycleCount(Animation.INDEFINITE);
            ftCelda.setAutoReverse(true);
            ftCelda.play();

            celda.getProperties().put("animacionSug", ftCelda);
        }
    }
}