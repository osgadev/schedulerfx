package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.DAO.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.DAO.GroupDAO;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.service.HorarioService;
import com.osgadev.organizadorhorariosfx.DTO.SesionAsignada;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.beans.binding.NumberBinding;
import javafx.geometry.HPos;
import javafx.geometry.VPos;

import java.util.*;

public class ScheduleController {

    // Contenedores Principales
    @FXML private GridPane gridCalendario;
    @FXML private VBox fase1Vacia;
    @FXML private ScrollPane scrollCalendario;

    // Zona Superior (Contexto)
    @FXML private ComboBox<String> cmbAnio;
    @FXML private ComboBox<String> cmbEtapa;
    @FXML private Button btnCargar;

    // Zona de Acciones BD y Motor
    @FXML private Button btnGenerar;
    @FXML private Button btnGuardarBD;
    @FXML private Button btnBorrarBD;

    // Zona Filtros Visuales
    @FXML private ComboBox<String> cmbFiltroCurso;
    @FXML private ComboBox<String> cmbFiltroProfesor;
    @FXML private Button btnLimpiarFiltros;

    // Zona Personalización de Tarjetas
    @FXML private CheckBox chkRangoAlumnos;
    @FXML private CheckBox chkProfesor;
    @FXML private CheckBox chkIdGrupo;
    @FXML private CheckBox chkNombreCurso;

    // Variables de Estado
    private List<SesionAsignada> horarioGenerado = new ArrayList<>();
    private GroupDAO groupDAO;
    private AvailabilityDAO availabilityDAO;
    private HorarioService horarioService;
    // private ScheduleDAO scheduleDAO; // <- Lo descomentaremos cuando se cree

    // Configuración visual
    private final int HORA_INICIO = 7;
    private final int HORA_FIN = 22;

    // Controles de Debug
    private final Object pauseLock = new Object();
    private volatile boolean isPaused = false;
    private Button btnPausar;
    private Button btnSiguientePaso;
    private Label lblEstadoIA;

    @FXML
    public void initialize() {
        groupDAO = new GroupDAO();
        availabilityDAO = new AvailabilityDAO();
        horarioService = new HorarioService(availabilityDAO);

        fase1Vacia.setVisible(true);
        scrollCalendario.setVisible(false);

        // Llenar Combos de Contexto
        cmbAnio.getItems().addAll("2025", "2026", "2027");
        cmbEtapa.getItems().addAll("1", "2");
        cmbAnio.getSelectionModel().select("2026");
        cmbEtapa.getSelectionModel().select("1");

        // Inicialización de Filtros
        cmbFiltroCurso.setDisable(true);
        cmbFiltroProfesor.setDisable(true);
        btnLimpiarFiltros.setDisable(true);

        cmbFiltroCurso.setOnAction(e -> aplicarFiltros());
        cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
        btnLimpiarFiltros.setOnAction(e -> limpiarFiltros());

        // Listeners para los CheckBox
        chkRangoAlumnos.setOnAction(e -> aplicarFiltros());
        chkProfesor.setOnAction(e -> aplicarFiltros());
        chkIdGrupo.setOnAction(e -> aplicarFiltros());
        chkNombreCurso.setOnAction(e -> aplicarFiltros());

        // Preparar Label de IA para la barra lateral
        lblEstadoIA = new Label("Estado de la IA:\nEsperando...");
        lblEstadoIA.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1565C0;");
        lblEstadoIA.setWrapText(true); // Permite que el texto largo baje a la siguiente línea

        Platform.runLater(() -> {
            // Lo inyectamos al final de la barra lateral derecha
            VBox panelDerecho = (VBox) btnGenerar.getParent();
            Separator separadorFinal = new Separator();

            // Agregamos el separador y el label al fondo de los controles
            panelDerecho.getChildren().addAll(separadorFinal, lblEstadoIA);

            // Llamamos a los botones de debug
            inyectarControlesDeDebug();
        });
    }

    private void inyectarControlesDeDebug() {
        VBox panelDerecho = (VBox) btnGenerar.getParent();
        btnPausar = new Button("⏸ Pausar IA");
        btnPausar.setPrefWidth(200.0);
        btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        btnPausar.setDisable(true);

        btnSiguientePaso = new Button("⏭ Siguiente Paso");
        btnSiguientePaso.setPrefWidth(200.0);
        btnSiguientePaso.setStyle("-fx-base: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSiguientePaso.setDisable(true);

        btnPausar.setOnAction(e -> {
            isPaused = !isPaused;
            if (isPaused) {
                btnPausar.setText("▶ Reanudar IA");
                btnPausar.setStyle("-fx-base: #4CAF50; -fx-text-fill: white;");
                btnSiguientePaso.setDisable(false);
            } else {
                btnPausar.setText("⏸ Pausar IA");
                btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white;");
                btnSiguientePaso.setDisable(true);
                synchronized (pauseLock) { pauseLock.notify(); }
            }
        });

        btnSiguientePaso.setOnAction(e -> {
            synchronized (pauseLock) { pauseLock.notify(); }
        });

        int index = panelDerecho.getChildren().indexOf(btnGenerar);
        panelDerecho.getChildren().add(index + 1, btnPausar);
        panelDerecho.getChildren().add(index + 2, btnSiguientePaso);
    }

    // =====================================================================
    // FLUJO DE BASE DE DATOS Y MOTOR
    // =====================================================================

    @FXML
    public void cargarHorario() {
        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();

        if (anio == null || etapa == null) {
            mostrarAlerta("Atención", "Seleccione el año y la etapa.");
            return;
        }

        // Simulación: No existe en BD aún
        boolean existeEnBD = false;

        if (existeEnBD) {
            btnGenerar.setDisable(true);
            btnGuardarBD.setDisable(true);
            btnBorrarBD.setDisable(false);

            popularFiltros();
            fase1Vacia.setVisible(false);
            scrollCalendario.setVisible(true);
            aplicarFiltros();
            actualizarMensajeIA("Horario cargado desde la Base de Datos.");
        } else {
            this.horarioGenerado.clear();
            gridCalendario.getChildren().clear();
            fase1Vacia.setVisible(true);
            scrollCalendario.setVisible(false);

            btnGenerar.setDisable(false);
            btnGuardarBD.setDisable(true);
            btnBorrarBD.setDisable(true);
            actualizarMensajeIA("Listo para generar un nuevo horario.");
        }
    }

    @FXML
    public void generarHorario() {
        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();

        btnGenerar.setText("Visualizando IA...");
        btnGenerar.setDisable(true);
        btnBorrarBD.setDisable(true);
        if (btnPausar != null) btnPausar.setDisable(false);

        cmbFiltroCurso.setDisable(true);
        cmbFiltroProfesor.setDisable(true);
        btnLimpiarFiltros.setDisable(true);

        fase1Vacia.setVisible(false);
        scrollCalendario.setVisible(true);

        Task<List<SesionAsignada>> task = new Task<List<SesionAsignada>>() {
            @Override
            protected List<SesionAsignada> call() {
                List<Group> grupos = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
                return horarioService.generarHorario(grupos, (estadoParcial, mensajeIA) -> {
                    Platform.runLater(() -> {
                        actualizarMensajeIA(mensajeIA);
                        construirTablaBase();
                        pintarBloques(estadoParcial);
                    });

                    synchronized (pauseLock) {
                        if (isPaused) {
                            try { pauseLock.wait(); } catch (InterruptedException e) {}
                        } else {
                            try { Thread.sleep(400); } catch (InterruptedException e) {}
                        }
                    }
                });
            }
        };

        task.setOnSucceeded(e -> {
            List<SesionAsignada> resultado = task.getValue();
            restaurarBotonesIA();

            if (resultado != null) {
                this.horarioGenerado = resultado;
                popularFiltros();
                aplicarFiltros();

                btnGenerar.setDisable(true);
                btnGuardarBD.setDisable(false);
                mostrarAlerta("¡Éxito!", "La IA resolvió el horario.");
            } else {
                btnGenerar.setDisable(false);
                mostrarAlerta("Error", "Tablero saturado.");
            }
        });

        task.setOnFailed(e -> {
            restaurarBotonesIA();
            btnGenerar.setDisable(false);
            if (task.getException() != null) task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    @FXML
    public void guardarEnBD() {
        mostrarAlerta("Guardado", "El horario se ha guardado exitosamente en la base de datos.");
        btnGuardarBD.setDisable(true);
        btnBorrarBD.setDisable(false);
    }

    @FXML
    public void borrarDeBD() {
        mostrarAlerta("Borrado", "El horario fue eliminado. Puede volver a generarlo.");
        this.horarioGenerado.clear();
        cargarHorario();
    }

    private void restaurarBotonesIA() {
        btnGenerar.setText("Generar Horarios (IA)");
        btnPausar.setDisable(true);
        btnSiguientePaso.setDisable(true);
        isPaused = false;
        btnPausar.setText("⏸ Pausar IA");
        btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
    }

    // =====================================================================
    // MÉTODOS VISUALES Y GRID
    // =====================================================================

    private void construirTablaBase() {
        gridCalendario.getChildren().clear();
        gridCalendario.getColumnConstraints().clear();
        gridCalendario.getRowConstraints().clear();

        ColumnConstraints colHora = new ColumnConstraints();
        colHora.setMinWidth(60); colHora.setPrefWidth(60); colHora.setMaxWidth(60);
        gridCalendario.getColumnConstraints().add(colHora);

        for (int i = 0; i < 7; i++) {
            ColumnConstraints colDia = new ColumnConstraints();
            colDia.setHgrow(Priority.ALWAYS);
            colDia.setMinWidth(180); // Mínimo de 180px para forzar el scroll horizontal si se empalman
            colDia.setFillWidth(true);
            gridCalendario.getColumnConstraints().add(colDia);
        }

        RowConstraints rowCabecera = new RowConstraints();
        rowCabecera.setMinHeight(30); rowCabecera.setPrefHeight(30); rowCabecera.setMaxHeight(30);
        gridCalendario.getRowConstraints().add(rowCabecera);

        Label lblTituloHora = new Label("Hora");
        lblTituloHora.setStyle("-fx-font-weight: bold; -fx-padding: 5;");
        lblTituloHora.setMaxWidth(Double.MAX_VALUE);
        lblTituloHora.setAlignment(javafx.geometry.Pos.CENTER);
        gridCalendario.add(lblTituloHora, 0, 0);

        String[] nombresDias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

        for (int i = 0; i < nombresDias.length; i++) {
            HBox headerBox = new HBox(5);
            headerBox.setAlignment(javafx.geometry.Pos.CENTER);
            headerBox.setMaxWidth(Double.MAX_VALUE);

            Label lblDia = new Label(nombresDias[i]);
            lblDia.setStyle("-fx-font-weight: bold;");

            // Botón de expansión (La lógica se inyecta en actualizarBotonesDeExpansion)
            Button btnExpandir = new Button("⛶");
            btnExpandir.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2;");
            btnExpandir.setDisable(true); // Inicia deshabilitado hasta calcular empalmes
            Tooltip.install(btnExpandir, new Tooltip("Expandir/Contraer Columna"));

            headerBox.getChildren().addAll(lblDia, btnExpandir);
            gridCalendario.add(headerBox, i + 1, 0);
        }

        int numFilasTiempo = (HORA_FIN - HORA_INICIO) * 4;
        for (int i = 0; i < numFilasTiempo; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(20); rc.setPrefHeight(20); rc.setMaxHeight(20);
            gridCalendario.getRowConstraints().add(rc);
        }

        for (int col = 1; col <= 7; col++) {
            Pane sepCol = new Pane();
            sepCol.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1;");
            sepCol.setMouseTransparent(true);
            gridCalendario.add(sepCol, col, 1, 1, numFilasTiempo);
        }

        int filaActual = 1;
        for (int hora = HORA_INICIO; hora < HORA_FIN; hora++) {
            Pane sepRow = new Pane();
            sepRow.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
            sepRow.setMouseTransparent(true);
            gridCalendario.add(sepRow, 0, filaActual, 8, 1);

            Label lblHora = new Label(hora + ":00");
            lblHora.setStyle("-fx-font-size: 11px; -fx-padding: 2;");
            gridCalendario.add(lblHora, 0, filaActual);

            filaActual += 4;
        }
    }

    private void pintarBloques(List<SesionAsignada> sesiones) {
        Random rand = new Random();
        Map<Integer, String> coloresPorCurso = new HashMap<>();
        Map<SesionAsignada, PosicionVisual> layout = calcularLayoutCartas(sesiones);

        // 1. CÁLCULO DE EMPALMES PARA EXPANSIÓN DINÁMICA
        int[] maxEmpalmesPorDia = new int[8];
        Arrays.fill(maxEmpalmesPorDia, 1);

        for (SesionAsignada s : sesiones) {
            PosicionVisual pos = layout.get(s);
            int dia = s.getColumnaDia();
            if (pos.totalColumnas > maxEmpalmesPorDia[dia]) {
                maxEmpalmesPorDia[dia] = pos.totalColumnas;
            }
        }
        actualizarBotonesDeExpansion(maxEmpalmesPorDia);

        // 2. CREACIÓN DE TARJETAS (VBox)
        for (SesionAsignada s : sesiones) {
            Group g = s.getGrupo();
            int idCurso = g.getCurso().getId();

            if (!coloresPorCurso.containsKey(idCurso)) {
                Color color = Color.color(rand.nextDouble() * 0.5 + 0.5, rand.nextDouble() * 0.5 + 0.5, rand.nextDouble() * 0.5 + 0.5);
                String hex = String.format("#%02X%02X%02X", (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255));
                coloresPorCurso.put(idCurso, hex);
            }
            String hexColor = coloresPorCurso.get(idCurso);

            VBox caja = new VBox();
            caja.setStyle("-fx-background-color: " + hexColor + "; -fx-border-color: black; -fx-padding: 3; -fx-border-radius: 3; -fx-background-radius: 3;");

            if (chkNombreCurso.isSelected()) {
                Label lblCurso = new Label(g.getCurso().getNombre());
                lblCurso.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                lblCurso.setWrapText(true); lblCurso.setMaxWidth(Double.MAX_VALUE);
                caja.getChildren().add(lblCurso);
            }

            if (chkProfesor.isSelected()) {
                Label lblProf = new Label(g.getProfesor().getNombre());
                lblProf.setStyle("-fx-font-size: 10px;");
                lblProf.setWrapText(true); lblProf.setMaxWidth(Double.MAX_VALUE);
                caja.getChildren().add(lblProf);
            }

            if (chkRangoAlumnos.isSelected()) {
                Label lblAlumnos = new Label("Alumnos: " + g.getRangoInicial() + "-" + g.getRangoFinal());
                lblAlumnos.setStyle("-fx-font-size: 10px; -fx-font-style: italic;");
                caja.getChildren().add(lblAlumnos);
            }

            if (chkIdGrupo.isSelected()) {
                Label lblId = new Label("ID: " + g.getIdGrupo());
                lblId.setStyle("-fx-font-size: 10px;");
                caja.getChildren().add(lblId);
            }

            // --- NUEVA LÓGICA DE DIMENSIONAMIENTO DINÁMICO ---
            PosicionVisual pos = layout.get(s);

            // Obtenemos la columna exacta donde está ubicada esta tarjeta
            ColumnConstraints columnaExacta = gridCalendario.getColumnConstraints().get(s.getColumnaDia());

            // VINCULAMOS el ancho de la tarjeta al ancho "minWidth" real de la columna
            javafx.beans.property.DoubleProperty anchoDinamicoColumna = columnaExacta.minWidthProperty();

            // Dividimos el ancho de la columna entre la cantidad de empalmes
            caja.maxWidthProperty().bind(anchoDinamicoColumna.divide(pos.totalColumnas).subtract(4));

            // Movemos la tarjeta horizontalmente si está empalmada, basándonos en el mismo ancho dinámico
            caja.translateXProperty().bind(anchoDinamicoColumna.divide(pos.totalColumnas).multiply(pos.indiceColumna).add(2));

            // Evitamos que el texto desaparezca por completo si se empalman muchas clases
            caja.setMinWidth(45);

            GridPane.setHalignment(caja, HPos.LEFT);
            GridPane.setValignment(caja, VPos.TOP);

            DropShadow sombra = new DropShadow(); sombra.setRadius(2.0); sombra.setOffsetY(1.0); sombra.setColor(Color.color(0, 0, 0, 0.4));
            caja.setEffect(sombra);

            String infoTooltip = String.format("Materia: %s\nProfesor: %s\nAlumnos: %d al %d",
                    g.getCurso().getNombre(), g.getProfesor().getNombre(), g.getRangoInicial(), g.getRangoFinal());
            Tooltip tooltip = new Tooltip(infoTooltip);
            Tooltip.install(caja, tooltip);

            caja.setOnMouseEntered((MouseEvent e) -> {
                caja.toFront();
                caja.setStyle("-fx-background-color: " + hexColor + "; -fx-border-color: white; -fx-border-width: 2; -fx-padding: 2; -fx-border-radius: 3; -fx-background-radius: 3;");
            });

            caja.setOnMouseExited((MouseEvent e) -> {
                caja.setStyle("-fx-background-color: " + hexColor + "; -fx-border-color: black; -fx-border-width: 1; -fx-padding: 3; -fx-border-radius: 3; -fx-background-radius: 3;");
            });

            int filaReal = s.getFilaHora() + 1;
            gridCalendario.add(caja, s.getColumnaDia(), filaReal);
            GridPane.setRowSpan(caja, s.getSpanFilas());
        }
    }

    private void actualizarBotonesDeExpansion(int[] maxEmpalmesPorDia) {
        for (int i = 1; i <= 7; i++) {
            final int columnaDia = i;

            HBox headerBox = (HBox) gridCalendario.getChildren().stream()
                    .filter(node -> GridPane.getRowIndex(node) != null && GridPane.getRowIndex(node) == 0)
                    .filter(node -> GridPane.getColumnIndex(node) != null && GridPane.getColumnIndex(node) == columnaDia)
                    .filter(node -> node instanceof HBox)
                    .findFirst()
                    .orElse(null);

            if (headerBox != null) {
                Button btnExpandir = (Button) headerBox.getChildren().get(1);
                ColumnConstraints colAfectada = gridCalendario.getColumnConstraints().get(columnaDia);

                int empalmes = maxEmpalmesPorDia[columnaDia];
                final double anchoDinamico = Math.max(180.0, empalmes * 100.0);

                if (empalmes <= 1) {
                    btnExpandir.setDisable(true);
                    btnExpandir.setOpacity(0.3);
                } else {
                    btnExpandir.setDisable(false);
                    btnExpandir.setOpacity(1.0);

                    btnExpandir.setOnAction(e -> {
                        if (colAfectada.getMinWidth() == 180.0) {
                            colAfectada.setMinWidth(anchoDinamico);
                            btnExpandir.setStyle("-fx-background-color: #e0e0e0; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2; -fx-border-color: gray; -fx-border-radius: 3;");
                        } else {
                            colAfectada.setMinWidth(180.0);
                            btnExpandir.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2;");
                        }
                    });
                }
            }
        }
    }

    private Map<SesionAsignada, PosicionVisual> calcularLayoutCartas(List<SesionAsignada> sesiones) {
        Map<SesionAsignada, PosicionVisual> layout = new HashMap<>();
        Map<Integer, List<SesionAsignada>> porDia = new HashMap<>();
        for (SesionAsignada s : sesiones) porDia.computeIfAbsent(s.getColumnaDia(), k -> new ArrayList<>()).add(s);

        for (List<SesionAsignada> diaSesiones : porDia.values()) {
            diaSesiones.sort(Comparator.comparingInt(SesionAsignada::getFilaHora).thenComparingInt(SesionAsignada::getSpanFilas));
            List<List<SesionAsignada>> bloques = new ArrayList<>();
            List<SesionAsignada> bloqueActual = new ArrayList<>();
            int maxFilaFin = -1;

            for (SesionAsignada s : diaSesiones) {
                int inicio = s.getFilaHora();
                int fin = inicio + s.getSpanFilas();
                if (bloqueActual.isEmpty() || inicio < maxFilaFin) {
                    bloqueActual.add(s);
                    maxFilaFin = Math.max(maxFilaFin, fin);
                } else {
                    bloques.add(new ArrayList<>(bloqueActual));
                    bloqueActual.clear();
                    bloqueActual.add(s);
                    maxFilaFin = fin;
                }
            }
            if (!bloqueActual.isEmpty()) bloques.add(bloqueActual);

            for (List<SesionAsignada> bloque : bloques) {
                List<List<SesionAsignada>> columnasVisuales = new ArrayList<>();
                for (SesionAsignada s : bloque) {
                    boolean colocada = false;
                    for (List<SesionAsignada> col : columnasVisuales) {
                        SesionAsignada ultimaEnCol = col.get(col.size() - 1);
                        if (s.getFilaHora() >= ultimaEnCol.getFilaHora() + ultimaEnCol.getSpanFilas()) {
                            col.add(s); colocada = true; break;
                        }
                    }
                    if (!colocada) {
                        List<SesionAsignada> nuevaCol = new ArrayList<>();
                        nuevaCol.add(s); columnasVisuales.add(nuevaCol);
                    }
                }
                int totalColumnasBloque = columnasVisuales.size();
                for (int i = 0; i < totalColumnasBloque; i++) {
                    for (SesionAsignada s : columnasVisuales.get(i)) layout.put(s, new PosicionVisual(i, totalColumnasBloque));
                }
            }
        }
        return layout;
    }

    private void mostrarAlerta(String titulo, String contenido) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(titulo); a.setContentText(contenido); a.showAndWait();
        });
    }

    private void imprimirSolucionConsola(List<SesionAsignada> sesiones) {
        System.out.println("\n🗓️ REPORTE DE HORARIOS GENERADOS");
    }

    public void actualizarMensajeIA(String mensaje) {
        Platform.runLater(() -> lblEstadoIA.setText(mensaje));
    }

    private void popularFiltros() {
        Set<String> cursos = new HashSet<>();
        Set<String> profesores = new HashSet<>();

        for (SesionAsignada s : horarioGenerado) {
            cursos.add(s.getGrupo().getCurso().getNombre());
            profesores.add(s.getGrupo().getProfesor().getNombre());
        }

        List<String> listaCursos = new ArrayList<>(cursos); Collections.sort(listaCursos);
        List<String> listaProfesores = new ArrayList<>(profesores); Collections.sort(listaProfesores);

        cmbFiltroCurso.getItems().setAll(listaCursos);
        cmbFiltroProfesor.getItems().setAll(listaProfesores);

        cmbFiltroCurso.setDisable(false);
        cmbFiltroProfesor.setDisable(false);
        btnLimpiarFiltros.setDisable(false);
    }

    private void aplicarFiltros() {
        if (horarioGenerado == null || horarioGenerado.isEmpty()) return;

        String cursoSel = cmbFiltroCurso.getValue();
        String profSel = cmbFiltroProfesor.getValue();

        List<SesionAsignada> filtradas = new ArrayList<>();
        for (SesionAsignada s : horarioGenerado) {
            boolean matchC = (cursoSel == null || cursoSel.isEmpty() || s.getGrupo().getCurso().getNombre().equals(cursoSel));
            boolean matchP = (profSel == null || profSel.isEmpty() || s.getGrupo().getProfesor().getNombre().equals(profSel));
            if (matchC && matchP) filtradas.add(s);
        }

        construirTablaBase();
        pintarBloques(filtradas);
    }

    private void limpiarFiltros() {
        cmbFiltroCurso.setOnAction(null); cmbFiltroProfesor.setOnAction(null);
        cmbFiltroCurso.getSelectionModel().clearSelection();
        cmbFiltroCurso.setValue(null);
        cmbFiltroProfesor.getSelectionModel().clearSelection();
        cmbFiltroProfesor.setValue(null);
        cmbFiltroCurso.setOnAction(e -> aplicarFiltros()); cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
        aplicarFiltros();
    }

    private class PosicionVisual {
        int indiceColumna, totalColumnas;
        public PosicionVisual(int i, int t) { this.indiceColumna = i; this.totalColumnas = t; }
    }
}