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

    @FXML private Button btnGenerar;
    @FXML private GridPane gridCalendario;
    @FXML private VBox fase1Vacia;
    @FXML private ScrollPane scrollCalendario;

    // --- NUEVOS CONTROLES DE FILTRADO ---
    @FXML private ComboBox<String> cmbFiltroCurso;
    @FXML private ComboBox<String> cmbFiltroProfesor;
    @FXML private Button btnLimpiarFiltros;

    // Variable global para almacenar el resultado exitoso de la IA y poder filtrarlo
    private List<SesionAsignada> horarioGenerado = new ArrayList<>();

    private GroupDAO groupDAO;
    private AvailabilityDAO availabilityDAO;
    private HorarioService horarioService;

    // Configuración visual
    private final int HORA_INICIO = 7;
    private final int HORA_FIN = 22;

    // --- CONTROLES DE DEBUG (HILOS Y CONCURRENCIA) ---
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

        // --- INICIALIZACIÓN DE FILTROS ---
        cmbFiltroCurso.setDisable(true);
        cmbFiltroProfesor.setDisable(true);
        btnLimpiarFiltros.setDisable(true);

        cmbFiltroCurso.setOnAction(e -> aplicarFiltros());
        cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
        btnLimpiarFiltros.setOnAction(e -> limpiarFiltros());

        // --- INYECTAR LABEL DE ESTADO ARRIBA DEL CALENDARIO ---
        lblEstadoIA = new Label("Estado de la IA: Esperando...");
        lblEstadoIA.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333333; -fx-padding: 5;");

        // Lo metemos en el StackPane (el padre de scrollCalendario) pero lo alineamos arriba
        StackPane contenedorCentral = (StackPane) scrollCalendario.getParent();
        StackPane.setAlignment(lblEstadoIA, javafx.geometry.Pos.TOP_LEFT);
        StackPane.setMargin(lblEstadoIA, new javafx.geometry.Insets(10, 0, 0, 10));
        contenedorCentral.getChildren().add(lblEstadoIA);

        Platform.runLater(this::inyectarControlesDeDebug);
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

        // LÓGICA DE LOS BOTONES DE DEBUG
        btnPausar.setOnAction(e -> {
            isPaused = !isPaused;
            if (isPaused) {
                btnPausar.setText("▶ Reanudar IA");
                btnPausar.setStyle("-fx-base: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
                btnSiguientePaso.setDisable(false);
            } else {
                btnPausar.setText("⏸ Pausar IA");
                btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
                btnSiguientePaso.setDisable(true);
                synchronized (pauseLock) { pauseLock.notify(); } // Despierta al hilo
            }
        });

        btnSiguientePaso.setOnAction(e -> {
            synchronized (pauseLock) { pauseLock.notify(); } // Libera solo un paso
        });

        panelDerecho.getChildren().addAll(btnPausar, btnSiguientePaso);
    }

    @FXML
    public void generarHorario() {
        // Bloquear filtros mientras la IA trabaja
        cmbFiltroCurso.setDisable(true);
        cmbFiltroProfesor.setDisable(true);
        btnLimpiarFiltros.setDisable(true);
        cmbFiltroCurso.getItems().clear();
        cmbFiltroProfesor.getItems().clear();

        btnGenerar.setText("Visualizando IA en vivo...");
        btnGenerar.setDisable(true);

        // Solo habilitamos el botón de pausa si ya se crearon los controles dinámicos
        if (btnPausar != null) btnPausar.setDisable(false);

        fase1Vacia.setVisible(false);
        scrollCalendario.setVisible(true);

        Task<List<SesionAsignada>> task = new Task<List<SesionAsignada>>() {
            @Override
            protected List<SesionAsignada> call() {
                // 1. Obtenemos los datos de la base de datos
                List<Group> grupos = groupDAO.obtenerPorAnioYEtapa("2026", "1");

                // 2. Llamamos al servicio y pasamos el BiConsumer (estadoParcial y mensajeDeLaIA)
                return horarioService.generarHorario(grupos, (estadoParcial, mensajeIA) -> {

                    // A) Actualizamos la Interfaz Gráfica (Siempre en el hilo de JavaFX con runLater)
                    Platform.runLater(() -> {
                        actualizarMensajeIA(mensajeIA);
                        construirTablaBase();
                        pintarBloques(estadoParcial);
                    });

                    // B) Gestión de Pausa y Velocidad de Reproducción (En el hilo de trabajo de la Task)
                    synchronized (pauseLock) {
                        if (isPaused) {
                            try {
                                // Si está en pausa, el hilo se congela aquí hasta que apretemos "Reanudar" o "Siguiente Paso"
                                pauseLock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            try {
                                // Si está en "Play", hace una pausa de 400ms para que podamos ver el movimiento
                                Thread.sleep(400);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        };

        // 3. Lo que sucede cuando la IA termina su trabajo (con éxito o fracaso natural)
        task.setOnSucceeded(e -> {
            List<SesionAsignada> resultado = task.getValue();
            restaurarBotones();

            if (resultado != null) {
                // 1. Guardar el horario globalmente para los filtros
                this.horarioGenerado = resultado;

                // 2. Extraer los datos y llenar los ComboBox
                popularFiltros();

                // 3. Terminó con éxito
                construirTablaBase();
                pintarBloques(resultado);
                imprimirSolucionConsola(resultado);
                mostrarAlerta("¡Éxito!", "El algoritmo logró resolver el tablero por completo.");
            } else {
                // Fracasó porque el tablero está demasiado lleno
                mostrarAlerta("Error", "El tablero está saturado. La IA probó todas las variantes posibles y fracasó.");
            }
        });

        // 4. Lo que sucede si el código crashea (Excepción de Java, NullPointer, etc.)
        task.setOnFailed(e -> {
            restaurarBotones();
            Throwable excepcion = task.getException();
            if (excepcion != null) {
                excepcion.printStackTrace();
                mostrarAlerta("Excepción", "Ocurrió un error fatal en el código: " + excepcion.getMessage());
            }
        });

        // 5. Encendemos el motor en un hilo secundario para no congelar la ventana
        new Thread(task).start();
    }


    private void restaurarBotones() {
        btnGenerar.setText("Generar Horarios");
        btnGenerar.setDisable(false);
        btnPausar.setDisable(true);
        btnSiguientePaso.setDisable(true);
        isPaused = false;
        btnPausar.setText("⏸ Pausar IA");
        btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
    }

    // =====================================================================
    // MÉTODOS VISUALES
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
            colDia.setHgrow(Priority.ALWAYS); colDia.setMinWidth(100); colDia.setFillWidth(true);
            gridCalendario.getColumnConstraints().add(colDia);
        }

        RowConstraints rowCabecera = new RowConstraints();
        rowCabecera.setMinHeight(30); rowCabecera.setPrefHeight(30); rowCabecera.setMaxHeight(30);
        gridCalendario.getRowConstraints().add(rowCabecera);

        String[] dias = {"Hora", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        for (int i = 0; i < dias.length; i++) {
            Label lbl = new Label(dias[i]);
            lbl.setStyle("-fx-font-weight: bold; -fx-padding: 5;");
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(javafx.geometry.Pos.CENTER);
            gridCalendario.add(lbl, i, 0);
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
        NumberBinding anchoDia = gridCalendario.widthProperty().subtract(60).divide(7);

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

            Label lblCurso = new Label(g.getCurso().getNombre());
            lblCurso.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
            lblCurso.setWrapText(true); lblCurso.setEllipsisString("..."); lblCurso.setMaxWidth(Double.MAX_VALUE);

            Label lblProf = new Label(g.getProfesor().getNombre());
            lblProf.setStyle("-fx-font-size: 10px;");
            lblProf.setWrapText(true); lblProf.setEllipsisString("..."); lblProf.setMaxWidth(Double.MAX_VALUE);

            caja.getChildren().addAll(lblCurso, lblProf);

            PosicionVisual pos = layout.get(s);

            caja.maxWidthProperty().bind(anchoDia.divide(pos.totalColumnas).subtract(2));
            caja.translateXProperty().bind(anchoDia.divide(pos.totalColumnas).multiply(pos.indiceColumna));

            GridPane.setHalignment(caja, HPos.LEFT);
            GridPane.setValignment(caja, VPos.TOP);

            DropShadow sombra = new DropShadow(); sombra.setRadius(2.0); sombra.setOffsetY(1.0); sombra.setColor(Color.color(0, 0, 0, 0.4));
            caja.setEffect(sombra);

            String infoTooltip = String.format("Materia: %s\nProfesor: %s\nAlumnos: %d al %d",
                    g.getCurso().getNombre(), g.getProfesor().getNombre(), g.getRangoInicial(), g.getRangoFinal());
            Tooltip tooltip = new Tooltip(infoTooltip);
            tooltip.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-color: rgba(0,0,0,0.8);");
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
        System.out.println("\n🗓️ REPORTE DE HORARIOS GENERADOS POR CHOCO SOLVER");
        Map<Group, List<SesionAsignada>> sesionesPorGrupo = new HashMap<>();
        for (SesionAsignada s : sesiones) sesionesPorGrupo.computeIfAbsent(s.getGrupo(), k -> new ArrayList<>()).add(s);
        String[] nombresDias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        for (Map.Entry<Group, List<SesionAsignada>> entry : sesionesPorGrupo.entrySet()) {
            Group g = entry.getKey();
            System.out.println("\n▶ GRUPO ID: " + g.getIdGrupo() + " | " + g.getCurso().getNombre() + " | " + g.getProfesor().getNombre());
            for (SesionAsignada s : entry.getValue()) {
                System.out.println("   - " + nombresDias[s.getColumnaDia() - 1] + ": " + formatearHora(s.getFilaHora()) + " a " + formatearHora(s.getFilaHora() + s.getSpanFilas()));
            }
        }
    }

    private String formatearHora(int filaVisual) {
        int hora = HORA_INICIO + (filaVisual / 4);
        int minutos = (filaVisual % 4) * 15;
        return String.format("%02d:%02d", hora, minutos);
    }

    private class PosicionVisual {
        int indiceColumna, totalColumnas;
        public PosicionVisual(int indice, int total) { this.indiceColumna = indice; this.totalColumnas = total; }
    }

    public void actualizarMensajeIA(String mensaje) {
        Platform.runLater(() -> lblEstadoIA.setText(mensaje));
    }

    // =====================================================================
    // MÉTODOS DE FILTRADO
    // =====================================================================

    private void popularFiltros() {
        Set<String> cursos = new HashSet<>();
        Set<String> profesores = new HashSet<>();

        // Extraer nombres únicos del horario generado
        for (SesionAsignada s : horarioGenerado) {
            cursos.add(s.getGrupo().getCurso().getNombre());
            profesores.add(s.getGrupo().getProfesor().getNombre());
        }

        // Ordenar alfabéticamente
        List<String> listaCursos = new ArrayList<>(cursos);
        Collections.sort(listaCursos);
        List<String> listaProfesores = new ArrayList<>(profesores);
        Collections.sort(listaProfesores);

        // Llenar los ComboBox
        cmbFiltroCurso.getItems().setAll(listaCursos);
        cmbFiltroProfesor.getItems().setAll(listaProfesores);

        // Habilitar controles
        cmbFiltroCurso.setDisable(false);
        cmbFiltroProfesor.setDisable(false);
        btnLimpiarFiltros.setDisable(false);
    }

    private void aplicarFiltros() {
        if (horarioGenerado == null || horarioGenerado.isEmpty()) return;

        String cursoSeleccionado = cmbFiltroCurso.getValue();
        String profesorSeleccionado = cmbFiltroProfesor.getValue();

        List<SesionAsignada> sesionesFiltradas = new ArrayList<>();

        for (SesionAsignada s : horarioGenerado) {
            boolean coincideCurso = (cursoSeleccionado == null || cursoSeleccionado.isEmpty() ||
                    s.getGrupo().getCurso().getNombre().equals(cursoSeleccionado));

            boolean coincideProfesor = (profesorSeleccionado == null || profesorSeleccionado.isEmpty() ||
                    s.getGrupo().getProfesor().getNombre().equals(profesorSeleccionado));

            // Si pasa ambos filtros (o si los filtros están vacíos), se agrega a la vista
            if (coincideCurso && coincideProfesor) {
                sesionesFiltradas.add(s);
            }
        }

        // Repintar el calendario solo con las cartas filtradas
        construirTablaBase();
        pintarBloques(sesionesFiltradas);
    }

    private void limpiarFiltros() {
        // Evitar que el evento setOnAction se dispare múltiples veces innecesariamente
        cmbFiltroCurso.setOnAction(null);
        cmbFiltroProfesor.setOnAction(null);

        cmbFiltroCurso.getSelectionModel().clearSelection();
        cmbFiltroProfesor.getSelectionModel().clearSelection();

        // Restaurar listeners
        cmbFiltroCurso.setOnAction(e -> aplicarFiltros());
        cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());

        // Forzar repintado con todos los datos
        aplicarFiltros();
    }
}