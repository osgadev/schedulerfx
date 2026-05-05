package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.dao.GroupDAO;
import com.osgadev.organizadorhorariosfx.dao.ScheduleDAO;
import com.osgadev.organizadorhorariosfx.dao.StudentDAO;
import com.osgadev.organizadorhorariosfx.model.Student;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.service.ManualAssignmentManager;
import com.osgadev.organizadorhorariosfx.service.ScheduleService;
import com.osgadev.organizadorhorariosfx.service.OccupationMap;
import com.osgadev.organizadorhorariosfx.dto.AssignedSession;
import com.osgadev.organizadorhorariosfx.dto.GroupState;
import com.osgadev.organizadorhorariosfx.view.ScheduleLayoutHelper;
import com.osgadev.organizadorhorariosfx.view.ScheduleUIFactory;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.util.*;

public class ScheduleController {

    // =====================================================================
    // CONTROLES FXML
    // =====================================================================
    @FXML private GridPane gridCalendario;
    @FXML private VBox fase1Vacia;
    @FXML private ScrollPane scrollCalendario;

    @FXML private ComboBox<String> cmbAnio;
    @FXML private ComboBox<String> cmbEtapa;
    @FXML private Button btnCargar;
    @FXML private Button btnGenerar;
    @FXML private Button btnGuardarBD;
    @FXML private Button btnBorrarBD;

    @FXML private Button btnExportarExcel;
    @FXML private Button btnPausar;
    @FXML private Button btnSiguientePaso;
    @FXML private Button btnToggleSugerencias;
    @FXML private Label lblEstadoIA;

    @FXML private ComboBox<String> cmbFiltroCurso;
    @FXML private ComboBox<String> cmbFiltroProfesor;
    @FXML private Button btnLimpiarFiltros;

    @FXML private CheckBox chkRangoAlumnos;
    @FXML private CheckBox chkProfesor;
    @FXML private CheckBox chkIdGrupo;
    @FXML private CheckBox chkNombreCurso;

    @FXML private ComboBox<Teacher> cmbProfesorManual;
    @FXML private ListView<GroupState> listGruposPendientes;
    @FXML private Label lblMateriaSeleccionada;
    @FXML private Label lblHorasRestantes;
    @FXML private HBox cajaBloquesGeneradores;

    // =====================================================================
    // VARIABLES DE ESTADO
    // =====================================================================
    private List<AssignedSession> horarioGenerado = new ArrayList<>();
    private GroupDAO groupDAO;
    private AvailabilityDAO availabilityDAO;
    private ScheduleService scheduleService;
    private ScheduleDAO scheduleDAO;

    private ObservableList<GroupState> listaEstados;
    private ManualAssignmentManager assignmentManager;
    private OccupationMap occupationMap;

    private Pane[][] matrizCeldasReceptoras;
    private final double[] TAMANOS_BLOQUES = {1.0, 1.5, 2.0, 2.5};

    private VBox fantasmaDrag;
    private boolean esPosicionValida = false;
    private boolean mostrarSugerencias = true;

    private final int HORA_INICIO = 7;
    private final int HORA_FIN = 22;

    private final Object pauseLock = new Object();
    private volatile boolean isPaused = false;

    // =====================================================================
    // INICIALIZACIÓN
    // =====================================================================
    @FXML
    public void initialize() {
        groupDAO = new GroupDAO();
        availabilityDAO = new AvailabilityDAO();
        scheduleService = new ScheduleService(availabilityDAO);
        scheduleDAO = new ScheduleDAO();
        occupationMap = new OccupationMap();
        assignmentManager = new ManualAssignmentManager();

        fase1Vacia.setVisible(true);
        scrollCalendario.setVisible(false);

        int currentYear = java.time.LocalDate.now().getYear();
        cmbAnio.getItems().addAll(String.valueOf(currentYear - 1), String.valueOf(currentYear), String.valueOf(currentYear + 1));
        cmbEtapa.getItems().addAll("1", "2");

        cmbAnio.getSelectionModel().select(String.valueOf(currentYear));
        cmbEtapa.getSelectionModel().select("1");

        cmbAnio.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && cmbEtapa.getValue() != null) cargarHorario();
        });
        cmbEtapa.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && cmbAnio.getValue() != null) cargarHorario();
        });

        cmbFiltroCurso.setDisable(true);
        cmbFiltroProfesor.setDisable(true);
        btnLimpiarFiltros.setDisable(true);

        cmbFiltroCurso.setOnAction(e -> aplicarFiltros());
        cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
        btnLimpiarFiltros.setOnAction(e -> limpiarFiltros());

        chkRangoAlumnos.setOnAction(e -> aplicarFiltros());
        chkProfesor.setOnAction(e -> aplicarFiltros());
        chkIdGrupo.setOnAction(e -> aplicarFiltros());
        chkNombreCurso.setOnAction(e -> aplicarFiltros());

        listaEstados = FXCollections.observableArrayList();
        if (listGruposPendientes != null) {
            listGruposPendientes.setItems(listaEstados);

            listGruposPendientes.setCellFactory(lv -> new ListCell<GroupState>() {
                @Override
                protected void updateItem(GroupState eg, boolean empty) {
                    super.updateItem(eg, empty);
                    if (empty || eg == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        double rest = eg.getHorasRestantes();
                        setText(eg.getGrupo().getCurso().getNombre() + " (" + rest + "h)");
                        if (rest <= 0) {
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: black;");
                        }
                    }
                }
            });

            listGruposPendientes.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
                if (newSel != null) seleccionarGrupoManual(newSel);
            });
        }

        Platform.runLater(this::cargarHorario);
    }

    // =====================================================================
    // EVENTOS UI (BOTONES Y DEBUG IA)
    // =====================================================================

    @FXML
    public void exportarAExcel() {
        if (horarioGenerado == null || horarioGenerado.isEmpty()) {
            mostrarAlerta("Sin datos", "No hay horario generado para exportar.");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Exportar Horarios de Profesores");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivos Excel", "*.xlsx", "*.xls"));

        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();
        fileChooser.setInitialFileName("HorariosProfesores_" + anio + "_" + etapa + ".xlsx");

        File archivoDestino = fileChooser.showSaveDialog(gridCalendario.getScene().getWindow());

        if (archivoDestino != null) {
            try {
                StudentDAO studentDAO = new StudentDAO();
                Map<String, List<Student>> alumnosPorGrupo = studentDAO.obtenerAlumnosAgrupadosPorBD(anio, etapa);
                com.osgadev.organizadorhorariosfx.util.ExportadorExcel.exportarHorarioPersonalizado(horarioGenerado, alumnosPorGrupo, archivoDestino);
                mostrarAlerta("Exportación Exitosa", "El archivo Excel se ha guardado correctamente.");
            } catch (Exception e) {
                e.printStackTrace();
                mostrarAlerta("Error de Exportación", "Ocurrió un error al exportar el archivo:\n" + e.getMessage());
            }
        }
    }

    @FXML
    public void togglePausaIA() {
        isPaused = !isPaused;
        if (isPaused) {
            btnPausar.setText("Reanudar IA");
            btnPausar.setStyle("-fx-base: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
            btnSiguientePaso.setDisable(false);
        } else {
            btnPausar.setText("Pausar IA");
            btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
            btnSiguientePaso.setDisable(true);
            synchronized (pauseLock) {
                pauseLock.notify();
            }
        }
    }

    @FXML
    public void siguientePasoIA() {
        synchronized (pauseLock) {
            pauseLock.notify();
        }
    }

    @FXML
    public void toggleSugerencias() {
        mostrarSugerencias = !mostrarSugerencias;
        if (mostrarSugerencias) {
            btnToggleSugerencias.setText("Ocultar Sugerencias");
            btnToggleSugerencias.setStyle("-fx-base: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            btnToggleSugerencias.setText("Mostrar Sugerencias");
            btnToggleSugerencias.setStyle("-fx-base: #757575; -fx-text-fill: white; -fx-font-weight: bold;");
        }
        aplicarFiltros();
    }

    // =====================================================================
    // FLUJO DE BASE DE DATOS Y MOTOR IA
    // =====================================================================
    @FXML
    public void cargarHorario() {
        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();

        if (anio == null || etapa == null) {
            mostrarAlerta("Atención", "Seleccione el año y la etapa.");
            return;
        }

        List<Group> gruposDelSemestre = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
        cargarGruposEnAlmacen(gruposDelSemestre);

        if (scheduleDAO.existsSchedule(anio, etapa)) {
            this.horarioGenerado = scheduleDAO.loadSchedule(anio, etapa);
            occupationMap.limpiar();

            for (AssignedSession s : horarioGenerado) {
                occupationMap.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas(), s.getGrupo());
                assignmentManager.deducirHorasAGrupo(s.getGrupo().getIdGrupo(), s.getSpanFilas() / 2.0);
            }

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
            occupationMap.limpiar();

            fase1Vacia.setVisible(false);
            scrollCalendario.setVisible(true);
            aplicarFiltros();

            btnGenerar.setDisable(false);
            btnGuardarBD.setDisable(true);
            btnBorrarBD.setDisable(true);
            actualizarMensajeIA("Listo para armar manual o generar IA.");
        }
        actualizarFabricaDeBloques();
    }

    @FXML
    public void generarHorario() {
        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();

        btnGenerar.setText("Calculando IA...");
        btnGenerar.setDisable(true);
        btnBorrarBD.setDisable(true);
        if (btnPausar != null) btnPausar.setDisable(false);

        fase1Vacia.setVisible(false);
        scrollCalendario.setVisible(true);

        Task<List<AssignedSession>> task = new Task<>() {
            @Override
            protected List<AssignedSession> call() {
                List<Group> grupos = groupDAO.obtenerPorAnioYEtapa(anio, etapa);

                return scheduleService.generarHorario(grupos, (estadoParcial, mensajeIA) -> {
                    Platform.runLater(() -> {
                        actualizarMensajeIA(mensajeIA);
                        construirTablaBase();
                        pintarBloques(estadoParcial);
                    });

                    synchronized (pauseLock) {
                        if (isPaused) {
                            try { pauseLock.wait(); } catch (InterruptedException e) {}
                        } else {
                            try { Thread.sleep(300); } catch (InterruptedException e) {}
                        }
                    }
                });
            }
        };

        task.setOnSucceeded(e -> {
            List<AssignedSession> resultado = task.getValue();
            restaurarBotonesIA();

            if (resultado != null) {
                this.horarioGenerado = resultado;
                occupationMap.limpiar();

                List<Group> grupos = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
                cargarGruposEnAlmacen(grupos);

                for (AssignedSession s : resultado) {
                    occupationMap.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas(), s.getGrupo());
                    assignmentManager.deducirHorasAGrupo(s.getGrupo().getIdGrupo(), s.getSpanFilas() / 2.0);
                }

                popularFiltros();
                aplicarFiltros();
                actualizarFabricaDeBloques();
                autoGuardar();

                btnGenerar.setDisable(true);
                btnGuardarBD.setDisable(false);
                mostrarAlerta("¡Éxito!", "La IA completó el horario.");
            } else {
                btnGenerar.setDisable(false);
                mostrarAlerta("Error", "Tablero saturado. Intenta colocar materias críticas a mano primero.");
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
        boolean exito = scheduleDAO.saveSchedule(this.horarioGenerado, cmbAnio.getValue(), cmbEtapa.getValue());
        if (exito) {
            mostrarAlerta("Guardado", "El horario se ha guardado exitosamente.");
            btnGuardarBD.setDisable(true);
            btnBorrarBD.setDisable(false);
        }
    }

    @FXML
    public void borrarDeBD() {
        if (scheduleDAO.deleteSchedule(cmbAnio.getValue(), cmbEtapa.getValue())) {
            this.horarioGenerado.clear();
            cargarHorario();
        }
    }

    private void autoGuardar() {
        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();
        if (anio != null && etapa != null) {
            if (!horarioGenerado.isEmpty()) {
                scheduleDAO.saveSchedule(this.horarioGenerado, anio, etapa);
            } else {
                scheduleDAO.deleteSchedule(anio, etapa);
            }
        }
    }

    private void restaurarBotonesIA() {
        btnGenerar.setText("Autocompletar (IA)");
        btnPausar.setDisable(true);
        btnSiguientePaso.setDisable(true);
        isPaused = false;
        btnPausar.setText("Pausar IA");
        btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
    }

    // =====================================================================
    // LÓGICA MANUAL (ALMACÉN Y PROFESORES)
    // =====================================================================

    private String formatearProfesor(Teacher t) {
        if (t == null) return "";
        double pendientes = assignmentManager.calcularHorasPendientesPorProfesor(t);
        String nombre = t.getNombre() + " " + t.getApellidoPaterno();
        if (pendientes <= 0) return "✅ " + nombre + " (0.0h)";
        return "⏳ " + nombre + " (" + pendientes + "h)";
    }

    private ListCell<Teacher> crearCeldaProfesor() {
        return new ListCell<Teacher>() {
            @Override
            protected void updateItem(Teacher t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(formatearProfesor(t));
                    if (assignmentManager.calcularHorasPendientesPorProfesor(t) <= 0) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: black;");
                    }
                }
            }
        };
    }

    private void cargarGruposEnAlmacen(List<Group> grupos) {
        assignmentManager.cargarGrupos(grupos);
        listaEstados.clear();

        List<Teacher> listaProfes = assignmentManager.obtenerProfesoresUnicos();

        if (cmbProfesorManual != null) {
            cmbProfesorManual.getItems().setAll(listaProfes);
            cmbProfesorManual.setValue(null);

            cmbProfesorManual.setCellFactory(lv -> crearCeldaProfesor());
            cmbProfesorManual.setButtonCell(crearCeldaProfesor());

            cmbProfesorManual.setOnAction(e -> {
                Teacher profeElegido = cmbProfesorManual.getValue();
                if (profeElegido != null) {
                    List<GroupState> filtrados = assignmentManager.obtenerGruposPorProfesor(profeElegido);
                    listaEstados.setAll(filtrados);

                    fase1Vacia.setVisible(false);
                    scrollCalendario.setVisible(true);

                    aplicarFiltros();
                    assignmentManager.setGrupoSeleccionado(null);
                    actualizarFabricaDeBloques();
                }
            });
        }
        if (cajaBloquesGeneradores != null) cajaBloquesGeneradores.getChildren().clear();
        if (lblMateriaSeleccionada != null) lblMateriaSeleccionada.setText("Selecciona una materia");
        if (lblHorasRestantes != null) lblHorasRestantes.setText("---");
    }

    private void seleccionarGrupoManual(GroupState estado) {
        assignmentManager.setGrupoSeleccionado(estado);
        if (lblMateriaSeleccionada != null) {
            lblMateriaSeleccionada.setText(
                    estado.getGrupo().getCurso().getNombre() + " | " +
                            "Alumnos: " + estado.getGrupo().getRangoInicial() + "-" + estado.getGrupo().getRangoFinal() + "\n" +
                            "Prof: " + estado.getGrupo().getProfesor().getNombre()
            );
        }
        actualizarFabricaDeBloques();
    }

    private void actualizarFabricaDeBloques() {
        if (cmbProfesorManual != null) cmbProfesorManual.setButtonCell(crearCeldaProfesor());
        if (listGruposPendientes != null) listGruposPendientes.refresh();

        if (cajaBloquesGeneradores == null) return;
        cajaBloquesGeneradores.getChildren().clear();

        GroupState grupoSeleccionado = assignmentManager.getGrupoSeleccionado();
        if (grupoSeleccionado == null) return;

        double restantes = grupoSeleccionado.getHorasRestantes();
        if (lblHorasRestantes != null) lblHorasRestantes.setText(restantes + " hrs restantes");

        // 1. OBTENEMOS EL COLOR DEL CURSO SELECCIONADO
        String hexColor = grupoSeleccionado.getGrupo().getCurso().getColorHex();

        for (double tamano : TAMANOS_BLOQUES) {
            // 2. PASAMOS LOS DOS PARÁMETROS A LA FÁBRICA
            StackPane bloqueVisual = ScheduleUIFactory.crearBloqueGeneradorVisual(tamano, hexColor);

            if (tamano > restantes) {
                bloqueVisual.setDisable(true);
                bloqueVisual.setOpacity(0.4);
            } else {
                bloqueVisual.setOnMousePressed(event -> {
                    bloqueVisual.requestFocus();
                    event.setDragDetect(true);
                });

                bloqueVisual.setOnDragDetected(event -> {
                    Dragboard db = bloqueVisual.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(String.valueOf(tamano));
                    db.setContent(content);

                    aplicarModoFantasmaATarjetas(true);
                    event.consume();
                });

                bloqueVisual.setOnDragDone(event -> {
                    aplicarModoFantasmaATarjetas(false);
                    event.consume();
                });
            }
            cajaBloquesGeneradores.getChildren().add(bloqueVisual);
        }
    }

    // =====================================================================
    // MÉTODOS VISUALES Y GRID (EFECTO CRISTAL Y FANTASMA DRAG&DROP)
    // =====================================================================

    private void aplicarModoFantasmaATarjetas(boolean activar) {
        gridCalendario.getChildren().forEach(nodo -> {
            if (nodo instanceof VBox && nodo != fantasmaDrag && nodo != fase1Vacia && !nodo.getProperties().containsKey("esSugerencia")) {
                if (activar) {
                    nodo.setMouseTransparent(true);
                    nodo.setOpacity(0.15);
                } else {
                    nodo.setMouseTransparent(false);
                    nodo.setOpacity(1.0);
                }
            }
        });
    }

    private void construirTablaBase() {
        gridCalendario.getChildren().clear();

        ScheduleUIFactory.configurarEstructuraGrid(gridCalendario, HORA_INICIO, HORA_FIN);

        int numFilasTiempo = (HORA_FIN - HORA_INICIO) * 2;
        matrizCeldasReceptoras = new Pane[8][numFilasTiempo + 1];

        for (int col = 1; col <= 7; col++) {
            for (int fila = 1; fila <= numFilasTiempo; fila++) {
                Pane celda = new Pane();
                celda.setStyle("-fx-background-color: transparent;");

                final int colActual = col;
                final int filaActualDrop = fila;

                celda.setOnDragOver(event -> {
                    GroupState grupoSel = assignmentManager.getGrupoSeleccionado();
                    if (event.getDragboard().hasString() && grupoSel != null) {
                        double horas = Double.parseDouble(event.getDragboard().getString());
                        int spanFilasVisuales = (int) (horas * 2);

                        ScheduleService.ValidacionManual resultadoValidacion = scheduleService.validarPosicionManual(
                                grupoSel.getGrupo(), colActual, filaActualDrop, spanFilasVisuales,
                                numFilasTiempo, horarioGenerado, occupationMap, HORA_INICIO
                        );

                        if (fantasmaDrag != null) {
                            fantasmaDrag.setVisible(true);
                            fantasmaDrag.getChildren().clear();

                            ColumnConstraints colObj = gridCalendario.getColumnConstraints().get(colActual);
                            fantasmaDrag.setMinWidth(colObj.getMinWidth() - 4);
                            fantasmaDrag.setMinHeight(spanFilasVisuales * 40);

                            int fInicio = filaActualDrop - 1;
                            int fFin = fInicio + spanFilasVisuales;
                            String textoHora = String.format("%02d:%02d - %02d:%02d",
                                    HORA_INICIO + (fInicio / 2), (fInicio % 2) * 30,
                                    HORA_INICIO + (fFin / 2), (fFin % 2) * 30);

                            Label lblHoraVista = new Label(textoHora);
                            lblHoraVista.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #222222;");
                            fantasmaDrag.getChildren().add(lblHoraVista);

                            Group g = grupoSel.getGrupo();
                            if (chkNombreCurso != null && chkNombreCurso.isSelected()) {
                                Label lbl1 = new Label(g.getCurso().getNombre());
                                lbl1.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                                fantasmaDrag.getChildren().add(lbl1);
                            }
                            if (chkProfesor != null && chkProfesor.isSelected()) {
                                Label lbl2 = new Label(g.getProfesor().getNombre());
                                lbl2.setStyle("-fx-font-size: 10px;");
                                fantasmaDrag.getChildren().add(lbl2);
                            }
                            if (chkRangoAlumnos != null && chkRangoAlumnos.isSelected()) {
                                Label lbl3 = new Label("Alumnos: " + g.getRangoInicial() + "-" + g.getRangoFinal());
                                lbl3.setStyle("-fx-font-size: 10px; -fx-font-style: italic;");
                                fantasmaDrag.getChildren().add(lbl3);
                            }

                            GridPane.setColumnIndex(fantasmaDrag, colActual);
                            GridPane.setRowIndex(fantasmaDrag, filaActualDrop);
                            GridPane.setRowSpan(fantasmaDrag, spanFilasVisuales);
                            fantasmaDrag.toFront();

                            if (resultadoValidacion != ScheduleService.ValidacionManual.OK) {
                                fantasmaDrag.setStyle("-fx-background-color: rgba(255, 0, 0, 0.7); -fx-border-color: darkred; -fx-border-width: 2; -fx-padding: 3; -fx-border-radius: 3;");
                                esPosicionValida = false;
                            } else {
                                event.acceptTransferModes(TransferMode.MOVE);
                                fantasmaDrag.setStyle("-fx-background-color: rgba(0, 255, 0, 0.7); -fx-border-color: darkgreen; -fx-border-width: 2; -fx-padding: 3; -fx-border-radius: 3;");
                                esPosicionValida = true;
                            }

                            lblHorasRestantes.setText(resultadoValidacion.getMensaje());
                        }
                    }
                    event.consume();
                });

                celda.setOnDragExited(event -> {
                    if (fantasmaDrag != null) fantasmaDrag.setVisible(false);
                    GroupState grupoSel = assignmentManager.getGrupoSeleccionado();
                    if (grupoSel != null) {
                        lblHorasRestantes.setText(grupoSel.getHorasRestantes() + " hrs restantes");
                    }
                    event.consume();
                });

                celda.setOnDragDropped(event -> {
                    GroupState grupoSel = assignmentManager.getGrupoSeleccionado();
                    if (event.getDragboard().hasString() && grupoSel != null && esPosicionValida) {
                        double horas = Double.parseDouble(event.getDragboard().getString());
                        int spanFilasVisuales = (int) (horas * 2);
                        int slotSemanal = scheduleService.calcularSlotSemanal(colActual, filaActualDrop, HORA_INICIO);

                        grupoSel.agregarHoras(horas);
                        AssignedSession nuevaSesion = new AssignedSession(grupoSel.getGrupo(), colActual, filaActualDrop - 1, spanFilasVisuales);
                        nuevaSesion.setSlotInicioSemanal(slotSemanal);
                        horarioGenerado.add(nuevaSesion);

                        occupationMap.registrarClase(slotSemanal, spanFilasVisuales, grupoSel.getGrupo());

                        if (fantasmaDrag != null) fantasmaDrag.setVisible(false);

                        Platform.runLater(() -> {
                            btnGuardarBD.setDisable(false);
                            actualizarFabricaDeBloques();
                            if (listGruposPendientes != null) listGruposPendientes.refresh();
                            popularFiltros();
                            aplicarFiltros();
                            autoGuardar();
                        });
                    }
                    event.setDropCompleted(true);
                    event.consume();
                });

                gridCalendario.add(celda, col, fila);
                matrizCeldasReceptoras[col][fila] = celda;
            }
        }

        if (fantasmaDrag == null) {
            fantasmaDrag = ScheduleUIFactory.crearContenedorFantasma();
        }
        fantasmaDrag.setVisible(false);
        if (!gridCalendario.getChildren().contains(fantasmaDrag)) {
            gridCalendario.add(fantasmaDrag, 1, 1);
        }
    }

    private void iluminarDisponibilidadProfesor(Teacher profe) {
        if (matrizCeldasReceptoras == null) return;

        gridCalendario.getChildren().removeIf(nodo ->
                nodo instanceof VBox && nodo.getProperties().containsKey("esSugerencia")
        );

        for (int col = 1; col <= 7; col++) {
            for (int fila = 1; fila < matrizCeldasReceptoras[col].length; fila++) {
                Pane c = matrizCeldasReceptoras[col][fila];
                if (c != null) {
                    c.setStyle("-fx-background-color: transparent;");
                    c.getProperties().remove("estiloOriginal");
                    c.getProperties().remove("esValido");

                    if (c.getProperties().containsKey("animacionSug")) {
                        javafx.animation.FadeTransition ft = (javafx.animation.FadeTransition) c.getProperties().get("animacionSug");
                        ft.stop();
                        c.getProperties().remove("animacionSug");
                    }
                    c.setOpacity(1.0);
                }
            }
        }

        List<Availability> disponibilidad = availabilityDAO.getByTeacher(profe);

        for (Availability a : disponibilidad) {
            for (int slot = a.getStartSlot(); slot < a.getEndSlot(); slot++) {
                int indexDia = slot / 48;
                int slotDelDia = slot % 48;
                int hInicio = slotDelDia / 2;
                int mInicio = (slotDelDia % 2) * 30;

                int col = indexDia + 1;
                int filaVisual = ((hInicio - HORA_INICIO) * 2) + (mInicio / 30) + 1;

                if (col >= 1 && col <= 7 && filaVisual >= 1 && filaVisual < matrizCeldasReceptoras[col].length) {
                    Pane celda1 = matrizCeldasReceptoras[col][filaVisual];
                    if (celda1 != null) {
                        ScheduleUIFactory.aplicarEfectoCristal(celda1);
                    }
                }
            }
        }
    }

    private void pintarSugerencias(Teacher profe) {
        if (!mostrarSugerencias || profe == null || matrizCeldasReceptoras == null) return;

        List<Availability> disponibilidad = availabilityDAO.getByTeacher(profe);

        for (Availability a : disponibilidad) {
            boolean esSugerenciaFija = (a.getCursoSugerido() != null);
            int startSlot = a.getStartSlot();
            int endSlot = a.getEndSlot();
            int duracionSlots = endSlot - startSlot;

            int indexDia = startSlot / 48;
            int col = indexDia + 1;
            int slotDelDia = startSlot % 48;
            int hInicio = slotDelDia / 2;
            int mInicio = (slotDelDia % 2) * 30;
            int filaVisual = ((hInicio - HORA_INICIO) * 2) + (mInicio / 30) + 1;

            if (col < 1 || col > 7 || filaVisual < 1 || filaVisual >= matrizCeldasReceptoras[col].length) continue;

            if (esSugerenciaFija) {
                int hFin = (slotDelDia + duracionSlots) / 2;
                int mFin = ((slotDelDia + duracionSlots) % 2) * 30;
                String textoHora = String.format("%02d:%02d - %02d:%02d", hInicio, mInicio, hFin, mFin);

                VBox sugerencia = ScheduleUIFactory.crearSugerenciaFijaVisual(
                        a.getCursoSugerido().getNombre(), textoHora, duracionSlots);

                ColumnConstraints colObj = gridCalendario.getColumnConstraints().get(col);
                sugerencia.setMaxWidth(colObj.getMinWidth() - 8);

                gridCalendario.add(sugerencia, col, filaVisual);
                GridPane.setRowSpan(sugerencia, duracionSlots);
                sugerencia.toBack();
            } else {
                for (int slot = startSlot; slot < endSlot; slot++) {
                    int sDia = slot % 48;
                    int h = sDia / 2;
                    int m = (sDia % 2) * 30;
                    int fVis = ((h - HORA_INICIO) * 2) + (m / 30) + 1;

                    if (col >= 1 && col <= 7 && fVis >= 1 && fVis < matrizCeldasReceptoras[col].length) {
                        Pane celda = matrizCeldasReceptoras[col][fVis];
                        if (celda != null && celda.getProperties().containsKey("esValido")) {
                            ScheduleUIFactory.aplicarEfectoSugerenciaLibre(celda);
                        }
                    }
                }
            }
        }
    }

    private void pintarBloques(List<AssignedSession> sesiones) {
        Random rand = new Random();
        Map<Integer, String> coloresPorCurso = new HashMap<>();

        Map<AssignedSession, ScheduleLayoutHelper.PosicionVisual> layout = ScheduleLayoutHelper.calcularLayoutCartas(sesiones);

        int[] maxEmpalmesPorDia = new int[8];
        Arrays.fill(maxEmpalmesPorDia, 1);
        for (AssignedSession s : sesiones) {
            ScheduleLayoutHelper.PosicionVisual pos = layout.get(s);
            int dia = s.getColumnaDia();
            if (pos.totalColumnas > maxEmpalmesPorDia[dia]) {
                maxEmpalmesPorDia[dia] = pos.totalColumnas;
            }
        }
        actualizarBotonesDeExpansion(maxEmpalmesPorDia);

        List<AssignedSession> sesionesParaPintar = new ArrayList<>(sesiones);

        for (AssignedSession s : sesionesParaPintar) {
            Group g = s.getGrupo();
            int idCurso = g.getCurso().getId();

            // CÓDIGO NUEVO: Toma el color desde la base de datos (modelo Curso)
            String hex = g.getCurso().getColorHex();

            if (hex == null || hex.isEmpty()) {
                if (!coloresPorCurso.containsKey(idCurso)) {
                    Color color = Color.color(rand.nextDouble() * 0.5 + 0.5, rand.nextDouble() * 0.5 + 0.5, rand.nextDouble() * 0.5 + 0.5);
                    hex = String.format("#%02X%02X%02X", (int) (color.getRed() * 255), (int) (color.getGreen() * 255), (int) (color.getBlue() * 255));
                    coloresPorCurso.put(idCurso, hex);
                } else {
                    hex = coloresPorCurso.get(idCurso);
                }
            }

            int fInicio = s.getFilaHora();
            int fFin = fInicio + s.getSpanFilas();
            String textoHora = String.format("%02d:%02d - %02d:%02d",
                    HORA_INICIO + (fInicio / 2), (fInicio % 2) * 30,
                    HORA_INICIO + (fFin / 2), (fFin % 2) * 30);

            VBox caja = ScheduleUIFactory.crearTarjetaSesionVisual(
                    g, hex, textoHora,
                    chkNombreCurso != null && chkNombreCurso.isSelected(),
                    chkProfesor != null && chkProfesor.isSelected(),
                    chkRangoAlumnos != null && chkRangoAlumnos.isSelected(),
                    chkIdGrupo != null && chkIdGrupo.isSelected()
            );

            ContextMenu menu = new ContextMenu();
            MenuItem itemEliminar = new MenuItem("Eliminar clase y reembolsar horas");
            itemEliminar.setOnAction(e -> {
                horarioGenerado.remove(s);
                int duracionBloques = s.getSpanFilas();
                occupationMap.eliminarClase(s.getSlotInicioSemanal(), duracionBloques, s.getGrupo());

                assignmentManager.reembolsarHorasAGrupo(s.getGrupo().getIdGrupo(), duracionBloques / 2.0);

                Platform.runLater(() -> {
                    actualizarFabricaDeBloques();
                    if (listGruposPendientes != null) listGruposPendientes.refresh();
                    popularFiltros();
                    aplicarFiltros();
                    autoGuardar();
                });
            });
            menu.getItems().add(itemEliminar);

            caja.setOnContextMenuRequested(e -> menu.show(caja, e.getScreenX(), e.getScreenY()));

            caja.setOnDragDetected(event -> {
                Dragboard db = caja.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent clipboardContent = new ClipboardContent();
                double horas = s.getSpanFilas() / 2.0;
                clipboardContent.putString(String.valueOf(horas));
                db.setContent(clipboardContent);
                event.consume();

                Platform.runLater(() -> {
                    GroupState eg = assignmentManager.buscarEstadoPorId(s.getGrupo().getIdGrupo());

                    if (eg != null) {
                        assignmentManager.setGrupoSeleccionado(eg);

                        if (cmbProfesorManual != null) {
                            javafx.event.EventHandler<javafx.event.ActionEvent> handler = cmbProfesorManual.getOnAction();
                            cmbProfesorManual.setOnAction(null);
                            cmbProfesorManual.setValue(eg.getGrupo().getProfesor());
                            cmbProfesorManual.setOnAction(handler);
                        }

                        List<GroupState> filtrados = assignmentManager.obtenerGruposPorProfesor(eg.getGrupo().getProfesor());
                        listaEstados.setAll(filtrados);
                        actualizarFabricaDeBloques();
                    }

                    horarioGenerado.remove(s);
                    int duracionBloques = s.getSpanFilas();
                    occupationMap.eliminarClase(s.getSlotInicioSemanal(), duracionBloques, s.getGrupo());

                    GroupState grupoSel = assignmentManager.getGrupoSeleccionado();
                    if (grupoSel != null) {
                        grupoSel.reembolsarHoras(horas);
                    }

                    caja.setVisible(false);

                    if (grupoSel != null) {
                        iluminarDisponibilidadProfesor(grupoSel.getGrupo().getProfesor());
                        pintarSugerencias(grupoSel.getGrupo().getProfesor());
                    }
                    aplicarModoFantasmaATarjetas(true);
                });
            });

            caja.setOnDragDone(event -> {
                if (event.getTransferMode() == null) {
                    double horas = s.getSpanFilas() / 2.0;
                    GroupState grupoSel = assignmentManager.getGrupoSeleccionado();
                    if (grupoSel != null) {
                        grupoSel.agregarHoras(horas);
                    }
                    horarioGenerado.add(s);
                    occupationMap.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas(), s.getGrupo());

                    Platform.runLater(() -> {
                        if (listGruposPendientes != null) listGruposPendientes.refresh();
                        popularFiltros();
                        aplicarFiltros();
                        autoGuardar();
                    });
                }
                aplicarModoFantasmaATarjetas(false);
                event.consume();
            });

            ScheduleLayoutHelper.PosicionVisual pos = layout.get(s);
            ColumnConstraints columnaExacta = gridCalendario.getColumnConstraints().get(s.getColumnaDia());
            javafx.beans.property.DoubleProperty anchoDinamico = columnaExacta.minWidthProperty();

            caja.maxWidthProperty().bind(anchoDinamico.divide(pos.totalColumnas).subtract(4));
            caja.translateXProperty().bind(anchoDinamico.divide(pos.totalColumnas).multiply(pos.indiceColumna).add(2));

            caja.setMinWidth(45);
            caja.setMouseTransparent(false);
            caja.setStyle(caja.getStyle() + "; -fx-cursor: hand;");

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
                    .findFirst().orElse(null);

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

    private void mostrarAlerta(String titulo, String contenido) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(titulo);
            a.setContentText(contenido);
            a.showAndWait();
        });
    }

    public void actualizarMensajeIA(String mensaje) {
        Platform.runLater(() -> lblEstadoIA.setText(mensaje));
    }

    // =====================================================================
    // REFRESCADO DINÁMICO DE FILTROS
    // =====================================================================
    private void popularFiltros() {
        String cursoSel = cmbFiltroCurso.getValue();
        String profSel = cmbFiltroProfesor.getValue();

        Set<String> cursos = new HashSet<>();
        Set<String> profesores = new HashSet<>();

        for (AssignedSession s : horarioGenerado) {
            cursos.add(s.getGrupo().getCurso().getNombre());
            profesores.add(s.getGrupo().getProfesor().getNombre());
        }

        List<String> listaCursos = new ArrayList<>(cursos);
        Collections.sort(listaCursos);
        List<String> listaProfesores = new ArrayList<>(profesores);
        Collections.sort(listaProfesores);

        cmbFiltroCurso.setOnAction(null);
        cmbFiltroProfesor.setOnAction(null);

        cmbFiltroCurso.getItems().setAll(listaCursos);
        cmbFiltroProfesor.getItems().setAll(listaProfesores);

        if (listaCursos.contains(cursoSel)) cmbFiltroCurso.setValue(cursoSel);
        if (listaProfesores.contains(profSel)) cmbFiltroProfesor.setValue(profSel);

        cmbFiltroCurso.setDisable(listaCursos.isEmpty());
        cmbFiltroProfesor.setDisable(listaProfesores.isEmpty());
        btnLimpiarFiltros.setDisable(listaCursos.isEmpty() && listaProfesores.isEmpty());

        cmbFiltroCurso.setOnAction(e -> aplicarFiltros());
        cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
    }

    private void aplicarFiltros() {
        if (horarioGenerado == null || horarioGenerado.isEmpty()) {
            construirTablaBase();
        } else {
            String cursoSel = cmbFiltroCurso.getValue();
            String profSel = cmbFiltroProfesor.getValue();

            List<AssignedSession> filtradas = new ArrayList<>();
            for (AssignedSession s : horarioGenerado) {
                boolean matchC = (cursoSel == null || cursoSel.isEmpty() || s.getGrupo().getCurso().getNombre().equals(cursoSel));
                boolean matchP = (profSel == null || profSel.isEmpty() || s.getGrupo().getProfesor().getNombre().equals(profSel));

                if (matchC && matchP) {
                    filtradas.add(s);
                }
            }

            construirTablaBase();
            pintarBloques(filtradas);

            if (cmbProfesorManual != null && cmbProfesorManual.getValue() != null) {
                iluminarDisponibilidadProfesor(cmbProfesorManual.getValue());
                pintarSugerencias(cmbProfesorManual.getValue());
            }
        }
    }

    private void limpiarFiltros() {
        cmbFiltroCurso.setOnAction(null);
        cmbFiltroProfesor.setOnAction(null);

        cmbFiltroCurso.getSelectionModel().clearSelection();
        cmbFiltroCurso.setValue(null);
        cmbFiltroProfesor.getSelectionModel().clearSelection();
        cmbFiltroProfesor.setValue(null);

        cmbFiltroCurso.setOnAction(e -> aplicarFiltros());
        cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
        aplicarFiltros();
    }
}