package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.DAO.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.DAO.GroupDAO;
import com.osgadev.organizadorhorariosfx.DAO.ScheduleDAO;
import com.osgadev.organizadorhorariosfx.model.Alumno;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.service.HorarioService;
import com.osgadev.organizadorhorariosfx.service.MapaOcupacion;
import com.osgadev.organizadorhorariosfx.DTO.SesionAsignada;
import com.osgadev.organizadorhorariosfx.DTO.EstadoGrupo;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
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

    @FXML private ComboBox<String> cmbFiltroCurso;
    @FXML private ComboBox<String> cmbFiltroProfesor;
    @FXML private Button btnLimpiarFiltros;

    @FXML private CheckBox chkRangoAlumnos;
    @FXML private CheckBox chkProfesor;
    @FXML private CheckBox chkIdGrupo;
    @FXML private CheckBox chkNombreCurso;

    // --- ALMACÉN MANUAL ---
    @FXML private ComboBox<Teacher> cmbProfesorManual;
    @FXML private ListView<EstadoGrupo> listGruposPendientes;
    @FXML private Label lblMateriaSeleccionada;
    @FXML private Label lblHorasRestantes;
    @FXML private HBox cajaBloquesGeneradores;

    // =====================================================================
    // VARIABLES DE ESTADO
    // =====================================================================

    private List<SesionAsignada> horarioGenerado = new ArrayList<>();
    private GroupDAO groupDAO;
    private AvailabilityDAO availabilityDAO;
    private HorarioService horarioService;
    private ScheduleDAO scheduleDAO;

    // Estado Manual
    private ObservableList<EstadoGrupo> listaEstados;
    private List<EstadoGrupo> todosLosEstados = new ArrayList<>();
    private EstadoGrupo grupoSeleccionado;
    private MapaOcupacion mapaOcupacion;
    private Pane[][] matrizCeldasReceptoras;
    private final double[] TAMANOS_BLOQUES = {1.0, 1.5, 2.0, 2.5};

    // --- FANTASMA PARA DRAG & DROP ---
    private VBox fantasmaDrag;
    private boolean esPosicionValida = false;

    // --- SUGERENCIAS DEL PROFESOR ---
    private boolean mostrarSugerencias = true;
    private Button btnToggleSugerencias;

    private final int HORA_INICIO = 7;
    private final int HORA_FIN = 22;

    // Debug IA
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
        scheduleDAO = new ScheduleDAO();
        mapaOcupacion = new MapaOcupacion();

        fase1Vacia.setVisible(true);
        scrollCalendario.setVisible(false);

        // --- NUEVO: CÁLCULO DINÁMICO DE AÑO Y CARGA AUTOMÁTICA ---
        int currentYear = java.time.LocalDate.now().getYear();
        cmbAnio.getItems().addAll(String.valueOf(currentYear - 1), String.valueOf(currentYear), String.valueOf(currentYear + 1));
        cmbEtapa.getItems().addAll("1", "2");

        cmbAnio.getSelectionModel().select(String.valueOf(currentYear));
        cmbEtapa.getSelectionModel().select("1");

        // Eventos para auto-cargar si el usuario cambia el año o etapa
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

            // PERSONALIZACIÓN VISUAL DE LA LISTA DE MATERIAS
            listGruposPendientes.setCellFactory(lv -> new ListCell<EstadoGrupo>() {
                @Override
                protected void updateItem(EstadoGrupo eg, boolean empty) {
                    super.updateItem(eg, empty);
                    if (empty || eg == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        double rest = eg.getHorasRestantes();
                        setText(eg.getGrupo().getCurso().getNombre() + " (" + rest + "h rest)");
                        if (rest <= 0) {
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;"); // Ya terminó
                        } else {
                            setStyle("-fx-text-fill: black;"); // Aún le falta
                        }
                    }
                }
            });

            listGruposPendientes.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
                if (newSel != null) seleccionarGrupoManual(newSel);
            });
        }

        lblEstadoIA = new Label("Estado de la IA:\nEsperando...");
        lblEstadoIA.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1565C0;");
        lblEstadoIA.setWrapText(true);

        Platform.runLater(() -> {
            if (btnGenerar.getParent() instanceof VBox) {
                VBox panelDerecho = (VBox) btnGenerar.getParent();
                panelDerecho.getChildren().addAll(new Separator(), lblEstadoIA);
                inyectarControlesDeDebug();
            }
            // Disparar la carga inicial automática
            cargarHorario();
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

        btnToggleSugerencias = new Button("💡 Ocultar Sugerencias");
        btnToggleSugerencias.setPrefWidth(200.0);
        btnToggleSugerencias.setStyle("-fx-base: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold;");
        btnToggleSugerencias.setOnAction(e -> {
            mostrarSugerencias = !mostrarSugerencias;
            if (mostrarSugerencias) {
                btnToggleSugerencias.setText("💡 Ocultar Sugerencias");
                btnToggleSugerencias.setStyle("-fx-base: #9C27B0; -fx-text-fill: white; -fx-font-weight: bold;");
            } else {
                btnToggleSugerencias.setText("💡 Mostrar Sugerencias");
                btnToggleSugerencias.setStyle("-fx-base: #757575; -fx-text-fill: white; -fx-font-weight: bold;");
            }
            aplicarFiltros();
        });

        // --- NUEVO BOTÓN: EXPORTAR A EXCEL ---
        Button btnExportarExcel = new Button("📥 Exportar a Excel");
        btnExportarExcel.setPrefWidth(200.0);
        btnExportarExcel.setStyle("-fx-base: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnExportarExcel.setOnAction(e -> exportarAExcel());

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
        // Insertamos el botón de exportar justo debajo del botón "Generar"
        panelDerecho.getChildren().add(index + 1, btnExportarExcel);
        panelDerecho.getChildren().add(index + 2, btnPausar);
        panelDerecho.getChildren().add(index + 3, btnSiguientePaso);
        panelDerecho.getChildren().add(index + 4, btnToggleSugerencias);
    }

    @FXML
    public void exportarAExcel() {
        if (horarioGenerado == null || horarioGenerado.isEmpty()) {
            mostrarAlerta("Sin datos", "No hay horario generado para exportar.");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Exportar Horarios de Profesores");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivos Excel (*.xlsx)", "*.xlsx"));

        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();
        fileChooser.setInitialFileName("Horarios_Profesores_" + anio + "_" + etapa + ".xlsx");

        File archivoDestino = fileChooser.showSaveDialog(gridCalendario.getScene().getWindow());

        if (archivoDestino != null) {
            try {
                // Instanciamos el DAO y sacamos el Mapa relacional
                com.osgadev.organizadorhorariosfx.DAO.AlumnoDAO alumnoDAO = new com.osgadev.organizadorhorariosfx.DAO.AlumnoDAO();
                Map<String, List<Alumno>> alumnosPorGrupo = alumnoDAO.obtenerAlumnosAgrupadosPorBD(anio, etapa);

                // Exportamos pasando el mapa
                com.osgadev.organizadorhorariosfx.util.ExportadorExcel.exportarHorarioPersonalizado(horarioGenerado, alumnosPorGrupo, archivoDestino);

                mostrarAlerta("Exportación Exitosa", "El archivo Excel se ha guardado correctamente.");
            } catch (Exception e) {
                e.printStackTrace();
                mostrarAlerta("Error de Exportación", "Ocurrió un error al exportar el archivo: " + e.getMessage());
            }
        }
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
        cargarGruposEnAlmacen(gruposDelSemestre); // Reinicia estados a max horas

        if (scheduleDAO.existsSchedule(anio, etapa)) {
            this.horarioGenerado = scheduleDAO.loadSchedule(anio, etapa);
            mapaOcupacion.limpiar();

            for (SesionAsignada s : horarioGenerado) {
                mapaOcupacion.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas(), s.getGrupo());
                // SINCRONIZAR HORAS CON EL ALMACÉN MANUAL
                for (EstadoGrupo eg : todosLosEstados) {
                    if (eg.getGrupo().getIdGrupo().equals(s.getGrupo().getIdGrupo())) {
                        eg.agregarHoras(s.getSpanFilas() / 2.0); // Deduce horas
                        break;
                    }
                }
            }

            btnGenerar.setDisable(true);
            btnGuardarBD.setDisable(true); // Ya no se necesita porque hay autoguardado, pero lo mantenemos visual
            btnBorrarBD.setDisable(false);

            popularFiltros();
            fase1Vacia.setVisible(false);
            scrollCalendario.setVisible(true);
            aplicarFiltros();
            actualizarMensajeIA("Horario cargado desde la Base de Datos.");
        } else {
            this.horarioGenerado.clear();
            mapaOcupacion.limpiar();

            fase1Vacia.setVisible(false);
            scrollCalendario.setVisible(true);
            aplicarFiltros();

            btnGenerar.setDisable(false);
            btnGuardarBD.setDisable(true);
            btnBorrarBD.setDisable(true);
            actualizarMensajeIA("Listo para armar manual o generar IA.");
        }
        actualizarFabricaDeBloques(); // Fuerza refresco de combos
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

        Task<List<SesionAsignada>> task = new Task<>() {
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
                            try { Thread.sleep(300); } catch (InterruptedException e) {}
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
                mapaOcupacion.limpiar();

                // Refrescar almacén para sincronizar con la IA
                List<Group> grupos = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
                cargarGruposEnAlmacen(grupos);

                for (SesionAsignada s : resultado) {
                    mapaOcupacion.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas(), s.getGrupo());
                    for (EstadoGrupo eg : todosLosEstados) {
                        if (eg.getGrupo().getIdGrupo().equals(s.getGrupo().getIdGrupo())) {
                            eg.agregarHoras(s.getSpanFilas() / 2.0);
                            break;
                        }
                    }
                }

                popularFiltros();
                aplicarFiltros();
                actualizarFabricaDeBloques();
                autoGuardar(); // Auto-guardamos el resultado de la IA

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

    // --- NUEVO: AUTOGUARDADO SILENCIOSO ---
    private void autoGuardar() {
        String anio = cmbAnio.getValue();
        String etapa = cmbEtapa.getValue();
        if (anio != null && etapa != null) {
            if (!horarioGenerado.isEmpty()) {
                scheduleDAO.saveSchedule(this.horarioGenerado, anio, etapa);
            } else {
                scheduleDAO.deleteSchedule(anio, etapa); // Si se eliminan todas las clases, borra el registro
            }
        }
    }

    private void restaurarBotonesIA() {
        btnGenerar.setText("Autocompletar (IA)");
        btnPausar.setDisable(true);
        btnSiguientePaso.setDisable(true);
        isPaused = false;
        btnPausar.setText("⏸ Pausar IA");
        btnPausar.setStyle("-fx-base: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
    }

    // =====================================================================
    // LÓGICA MANUAL (ALMACÉN Y PROFESORES)
    // =====================================================================

    // Calcula si un profesor ya no tiene horas por asignar en toda su carga
    private double calcularHorasPendientes(Teacher t) {
        double total = 0;
        for (EstadoGrupo eg : todosLosEstados) {
            if (eg.getGrupo().getProfesor().getId() == t.getId()) {
                total += eg.getHorasRestantes();
            }
        }
        return total;
    }

    private String formatearProfesor(Teacher t) {
        if (t == null) return "";
        double pendientes = calcularHorasPendientes(t);
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
                    if (calcularHorasPendientes(t) <= 0) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: black;");
                    }
                }
            }
        };
    }

    private void cargarGruposEnAlmacen(List<Group> grupos) {
        todosLosEstados.clear();
        listaEstados.clear(); // NUEVO: Limpia el panel izquierdo de materias pendientes
        grupoSeleccionado = null; // NUEVO: Limpia la materia seleccionada en memoria

        Map<Integer, Teacher> mapaProfesoresUnicos = new HashMap<>();

        for (Group g : grupos) {
            todosLosEstados.add(new EstadoGrupo(g));
            if (g.getProfesor() != null) {
                mapaProfesoresUnicos.put(g.getProfesor().getId(), g.getProfesor());
            }
        }

        List<Teacher> listaProfes = new ArrayList<>(mapaProfesoresUnicos.values());
        listaProfes.sort(Comparator.comparing(Teacher::getNombre));

        if (cmbProfesorManual != null) {
            cmbProfesorManual.getItems().setAll(listaProfes);
            cmbProfesorManual.setValue(null); // NUEVO: Deselecciona el profesor anterior

            // Asignar el nuevo visualizador inteligente
            cmbProfesorManual.setCellFactory(lv -> crearCeldaProfesor());
            cmbProfesorManual.setButtonCell(crearCeldaProfesor());

            cmbProfesorManual.setOnAction(e -> {
                Teacher profeElegido = cmbProfesorManual.getValue();
                if (profeElegido != null) {
                    List<EstadoGrupo> filtrados = new ArrayList<>();
                    for (EstadoGrupo eg : todosLosEstados) {
                        if (eg.getGrupo().getProfesor().getId() == profeElegido.getId()) {
                            filtrados.add(eg);
                        }
                    }
                    listaEstados.setAll(filtrados);

                    fase1Vacia.setVisible(false);
                    scrollCalendario.setVisible(true);

                    aplicarFiltros();
                    grupoSeleccionado = null;
                    actualizarFabricaDeBloques();
                }
            });
        }
        if (cajaBloquesGeneradores != null) cajaBloquesGeneradores.getChildren().clear();
        if (lblMateriaSeleccionada != null) lblMateriaSeleccionada.setText("Selecciona una materia");
        if (lblHorasRestantes != null) lblHorasRestantes.setText("---");
    }

    private void seleccionarGrupoManual(EstadoGrupo estado) {
        this.grupoSeleccionado = estado;
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
        // Forzar actualización visual del ComboBox y Lista al modificar bloques
        if (cmbProfesorManual != null) cmbProfesorManual.setButtonCell(crearCeldaProfesor());
        if (listGruposPendientes != null) listGruposPendientes.refresh();

        if (cajaBloquesGeneradores == null) return;
        cajaBloquesGeneradores.getChildren().clear();
        if (grupoSeleccionado == null) return;

        double restantes = grupoSeleccionado.getHorasRestantes();
        if (lblHorasRestantes != null) lblHorasRestantes.setText(restantes + " hrs restantes");

        for (double tamano : TAMANOS_BLOQUES) {
            StackPane bloqueVisual = crearBloqueVisual(tamano);

            if (tamano > restantes) {
                bloqueVisual.setDisable(true);
                bloqueVisual.setOpacity(0.4);
            } else {
                // SOLUCIÓN AL BUG: Forzar el foco y la detección de arrastre al primer clic
                bloqueVisual.setOnMousePressed(event -> {
                    bloqueVisual.requestFocus();
                    event.setDragDetect(true);
                });

                bloqueVisual.setOnDragDetected(event -> {
                    Dragboard db = bloqueVisual.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(String.valueOf(tamano));
                    db.setContent(content);

                    // ACTIVAR MODO FANTASMA
                    aplicarModoFantasmaATarjetas(true);

                    event.consume();
                });

                bloqueVisual.setOnDragDone(event -> {
                    // DESACTIVAR MODO FANTASMA
                    aplicarModoFantasmaATarjetas(false);
                    event.consume();
                });
            }
            cajaBloquesGeneradores.getChildren().add(bloqueVisual);
        }
    }

    private StackPane crearBloqueVisual(double horas) {
        StackPane panel = new StackPane();
        Rectangle fondo = new Rectangle(45, 30);
        fondo.setArcWidth(8); fondo.setArcHeight(8);
        fondo.setFill(Color.web("#4A90E2"));
        fondo.setStroke(Color.web("#003366"));

        Text texto = new Text(horas + "h");
        texto.setFill(Color.WHITE);
        texto.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        panel.getChildren().addAll(fondo, texto);
        panel.setOnMouseEntered(e -> fondo.setFill(Color.web("#5DADE2")));
        panel.setOnMouseExited(e -> fondo.setFill(Color.web("#4A90E2")));
        panel.setStyle("-fx-cursor: hand;");

        return panel;
    }

    // =====================================================================
    // MÉTODOS VISUALES Y GRID (EFECTO CRISTAL Y FANTASMA DRAG&DROP)
    // =====================================================================

    // --- MÉTODO MODO FANTASMA ---
    private void aplicarModoFantasmaATarjetas(boolean activar) {
        gridCalendario.getChildren().forEach(nodo -> {
            // Ignorar fantasmaDrag, contenedor vacío, y sugerencias generadas
            if (nodo instanceof VBox && nodo != fantasmaDrag && nodo != fase1Vacia && !nodo.getProperties().containsKey("esSugerencia")) {
                if (activar) {
                    nodo.setMouseTransparent(true);
                    nodo.setOpacity(0.15); // Transparencia alta para ver a través de las tarjetas
                } else {
                    nodo.setMouseTransparent(false);
                    nodo.setOpacity(1.0);
                }
            }
        });
    }

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
            colDia.setMinWidth(180);
            colDia.setFillWidth(true);
            gridCalendario.getColumnConstraints().add(colDia);
        }

        RowConstraints rowCabecera = new RowConstraints();
        rowCabecera.setMinHeight(30); rowCabecera.setPrefHeight(30);
        gridCalendario.getRowConstraints().add(rowCabecera);

        Label lblTituloHora = new Label("Hora");
        lblTituloHora.setStyle("-fx-font-weight: bold; -fx-padding: 5;");
        gridCalendario.add(lblTituloHora, 0, 0);

        String[] nombresDias = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        for (int i = 0; i < nombresDias.length; i++) {
            HBox headerBox = new HBox(5);
            headerBox.setAlignment(javafx.geometry.Pos.CENTER);
            Label lblDia = new Label(nombresDias[i]);
            lblDia.setStyle("-fx-font-weight: bold;");

            Button btnExpandir = new Button("⛶");
            btnExpandir.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 10px; -fx-padding: 2;");
            btnExpandir.setDisable(true);
            headerBox.getChildren().addAll(lblDia, btnExpandir);
            gridCalendario.add(headerBox, i + 1, 0);
        }

        int numFilasTiempo = (HORA_FIN - HORA_INICIO) * 2;
        matrizCeldasReceptoras = new Pane[8][numFilasTiempo + 1];

        for (int i = 0; i < numFilasTiempo; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(40); rc.setPrefHeight(40);
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
            filaActual += 2;
        }

        // ====================================================================
        // ZONAS DE ATERRIZAJE (DRAG & DROP Y FANTASMA CON TEXTO)
        // ====================================================================
        for (int col = 1; col <= 7; col++) {
            for (int fila = 1; fila <= numFilasTiempo; fila++) {
                Pane celda = new Pane();
                celda.setStyle("-fx-background-color: transparent;");

                final int colActual = col;
                final int filaActualDrop = fila;

                celda.setOnDragOver(event -> {
                    if (event.getDragboard().hasString() && grupoSeleccionado != null) {
                        double horas = Double.parseDouble(event.getDragboard().getString());
                        int spanFilasVisuales = (int)(horas * 2);
                        int duracionBloques = spanFilasVisuales;

                        int hInicioReal = HORA_INICIO + ((filaActualDrop - 1) / 2);
                        int mInicioReal = ((filaActualDrop - 1) % 2) * 30;
                        int slotDelDia = (hInicioReal * 2) + ((filaActualDrop - 1) % 2);
                        int slotSemanal = ((colActual - 1) * 48) + slotDelDia;

                        boolean ocupado = false;
                        for (int i = 0; i < duracionBloques; i++) {
                            if (mapaOcupacion.profesorOcupadoEnSlot(grupoSeleccionado.getGrupo().getProfesor().getId(), slotSemanal + i) ||
                                    mapaOcupacion.rangoOcupadoEnSlot(grupoSeleccionado.getGrupo().getRangoInicial(), grupoSeleccionado.getGrupo().getRangoFinal(), slotSemanal + i)) {
                                ocupado = true; break;
                            }
                        }

                        boolean fueraDeHorario = false;
                        for (int i = 0; i < spanFilasVisuales; i++) {
                            int filaRevisada = filaActualDrop + i;
                            if (filaRevisada >= matrizCeldasReceptoras[colActual].length ||
                                    matrizCeldasReceptoras[colActual][filaRevisada] == null ||
                                    !matrizCeldasReceptoras[colActual][filaRevisada].getProperties().containsKey("esValido")) {
                                fueraDeHorario = true; break;
                            }
                        }

                        // --- NUEVO: VALIDACIÓN DE 1 SESIÓN POR DÍA PARA EL MISMO GRUPO ---
                        boolean mismoGrupoMismoDia = false;
                        for (SesionAsignada s : horarioGenerado) {
                            if (s.getGrupo().getIdGrupo().equals(grupoSeleccionado.getGrupo().getIdGrupo()) && s.getColumnaDia() == colActual) {
                                mismoGrupoMismoDia = true;
                                break;
                            }
                        }

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

                            Group g = grupoSeleccionado.getGrupo();
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

                            // SE AÑADIÓ mismoGrupoMismoDia A LA CONDICIÓN DE RECHAZO
                            if (fueraDeHorario || ocupado || mismoGrupoMismoDia) {
                                fantasmaDrag.setStyle("-fx-background-color: rgba(255, 0, 0, 0.7); -fx-border-color: darkred; -fx-border-width: 2; -fx-padding: 3; -fx-border-radius: 3;");
                                esPosicionValida = false;
                                if (fueraDeHorario) lblHorasRestantes.setText("⚠️ Fuera de horario");
                                else if (ocupado) lblHorasRestantes.setText("⚠️ Choque de materias");
                                else lblHorasRestantes.setText("⚠️ 1 bloque por día máximo"); // AVISO PARA EL USUARIO
                            } else {
                                event.acceptTransferModes(TransferMode.MOVE);
                                fantasmaDrag.setStyle("-fx-background-color: rgba(0, 255, 0, 0.7); -fx-border-color: darkgreen; -fx-border-width: 2; -fx-padding: 3; -fx-border-radius: 3;");
                                esPosicionValida = true;
                                lblHorasRestantes.setText("Suelte para asignar...");
                            }
                        }
                    }
                    event.consume();
                });

                celda.setOnDragExited(event -> {
                    if (fantasmaDrag != null) fantasmaDrag.setVisible(false);
                    if (grupoSeleccionado != null) {
                        lblHorasRestantes.setText(grupoSeleccionado.getHorasRestantes() + " hrs restantes");
                    }
                    event.consume();
                });

                celda.setOnDragDropped(event -> {
                    if (event.getDragboard().hasString() && grupoSeleccionado != null && esPosicionValida) {
                        double horas = Double.parseDouble(event.getDragboard().getString());
                        int spanFilasVisuales = (int)(horas * 2);
                        int duracionBloques = spanFilasVisuales;

                        int hInicioReal = HORA_INICIO + ((filaActualDrop - 1) / 2);
                        int mInicioReal = ((filaActualDrop - 1) % 2) * 30;
                        int slotDelDia = (hInicioReal * 2) + ((filaActualDrop - 1) % 2);
                        int slotSemanal = ((colActual - 1) * 48) + slotDelDia;

                        grupoSeleccionado.agregarHoras(horas);
                        SesionAsignada nuevaSesion = new SesionAsignada(grupoSeleccionado.getGrupo(), colActual, filaActualDrop - 1, spanFilasVisuales);
                        nuevaSesion.setSlotInicioSemanal(slotSemanal);
                        horarioGenerado.add(nuevaSesion);

                        mapaOcupacion.registrarClase(slotSemanal, duracionBloques, grupoSeleccionado.getGrupo());

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
            fantasmaDrag = new VBox();
            fantasmaDrag.setMouseTransparent(true);
        }
        fantasmaDrag.setVisible(false);
        if (!gridCalendario.getChildren().contains(fantasmaDrag)) {
            gridCalendario.add(fantasmaDrag, 1, 1);
        }
    }

    private void iluminarDisponibilidadProfesor(Teacher profe) {
        if (matrizCeldasReceptoras == null) return;

        // ==========================================================
        // NUEVO: ELIMINAR SUGERENCIAS FIJAS (VBox) DEL GRID
        // ==========================================================
        gridCalendario.getChildren().removeIf(nodo ->
                nodo instanceof VBox && nodo.getProperties().containsKey("esSugerencia")
        );
        // ==========================================================


        for (int col = 1; col <= 7; col++) {
            for (int fila = 1; fila < matrizCeldasReceptoras[col].length; fila++) {
                Pane c = matrizCeldasReceptoras[col][fila];
                if (c != null) {
                    c.setStyle("-fx-background-color: transparent;");
                    c.getProperties().remove("estiloOriginal");
                    c.getProperties().remove("esValido");

                    // Detener animaciones previas para no gastar recursos
                    if (c.getProperties().containsKey("animacionSug")) {
                        ((javafx.animation.FadeTransition) c.getProperties().get("animacionSug")).stop();
                        c.getProperties().remove("animacionSug");
                        c.setOpacity(1.0);
                    }
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

                    String estiloCristal = "-fx-background-color: rgba(76, 175, 80, 0.25); -fx-border-color: rgba(255, 255, 255, 0.5);";
                    if (celda1 != null) {
                        celda1.setStyle(estiloCristal);
                        celda1.getProperties().put("estiloOriginal", estiloCristal);
                        celda1.getProperties().put("esValido", true);
                    }
                }
            }
        }
    }

    // =====================================================================
    // PINTAR SUGERENCIAS INTELIGENTES DEL PROFESOR
    // =====================================================================
    private void pintarSugerencias(Teacher profe) {
        if (!mostrarSugerencias || profe == null || matrizCeldasReceptoras == null) return;

        List<Availability> disponibilidad = availabilityDAO.getByTeacher(profe);

        for (Availability a : disponibilidad) {
            boolean esSugerenciaFija = a.getCursoSugerido() != null;
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
                // TIPO 1: Sugerencia Fija (Tarjeta Rojo Carmesí Punteada)
                VBox sugerencia = new VBox();
                sugerencia.setMouseTransparent(true);
                sugerencia.getProperties().put("esSugerencia", true); // Proteger de la transparencia de arrastre

                sugerencia.setStyle(
                        "-fx-background-color: rgba(211, 47, 47, 0.15);" +
                                "-fx-border-color: rgba(211, 47, 47, 0.8);" +
                                "-fx-border-width: 2;" +
                                "-fx-border-style: dashed;" +
                                "-fx-border-radius: 4;" +
                                "-fx-padding: 3;"
                );
                sugerencia.setMinHeight(duracionSlots * 40);
                sugerencia.setMaxHeight(duracionSlots * 40);

                Label lblIcono = new Label("💡 " + a.getCursoSugerido().getNombre());
                lblIcono.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(183, 28, 28, 1.0); -fx-font-weight: bold;");
                lblIcono.setWrapText(true);

                int hFin = (slotDelDia + duracionSlots) / 2;
                int mFin = ((slotDelDia + duracionSlots) % 2) * 30;
                Label lblHoraSug = new Label(String.format("%02d:%02d - %02d:%02d", hInicio, mInicio, hFin, mFin));
                lblHoraSug.setStyle("-fx-font-size: 9px; -fx-text-fill: rgba(183, 28, 28, 0.85); -fx-font-weight: bold;");

                sugerencia.getChildren().addAll(lblIcono, lblHoraSug);

                ColumnConstraints colObj = gridCalendario.getColumnConstraints().get(col);
                sugerencia.setMaxWidth(colObj.getMinWidth() - 8);

                gridCalendario.add(sugerencia, col, filaVisual);
                GridPane.setRowSpan(sugerencia, duracionSlots);
                sugerencia.toBack();

                // Animación de respiración (Fade)
                javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(800), sugerencia);
                ft.setFromValue(0.3);
                ft.setToValue(1.0);
                ft.setCycleCount(javafx.animation.Animation.INDEFINITE);
                ft.setAutoReverse(true);
                ft.play();
            }

            // TIPO 2: Sugerencia Libre (Borde punteado Rojo Carmesí en el cristal)
            for (int slot = startSlot; slot < endSlot; slot++) {
                int sDia = slot % 48;
                int h = sDia / 2;
                int m = (sDia % 2) * 30;
                int fVis = ((h - HORA_INICIO) * 2) + (m / 30) + 1;

                if (col >= 1 && col <= 7 && fVis >= 1 && fVis < matrizCeldasReceptoras[col].length) {
                    Pane celda = matrizCeldasReceptoras[col][fVis];
                    if (celda != null && celda.getProperties().containsKey("esValido")) {
                        if (!esSugerenciaFija) {
                            String estiloActual = celda.getStyle();
                            if (!estiloActual.contains("dashed")) {
                                celda.setStyle(estiloActual
                                        .replace("-fx-border-color: rgba(255, 255, 255, 0.5);", "")
                                        + "-fx-border-color: rgba(211, 47, 47, 0.8);"
                                        + "-fx-border-style: dashed;"
                                        + "-fx-border-width: 1;"
                                );

                                // Animación de respiración (Fade)
                                javafx.animation.FadeTransition ftCelda = new javafx.animation.FadeTransition(javafx.util.Duration.millis(800), celda);
                                ftCelda.setFromValue(0.4);
                                ftCelda.setToValue(1.0);
                                ftCelda.setCycleCount(javafx.animation.Animation.INDEFINITE);
                                ftCelda.setAutoReverse(true);
                                ftCelda.play();

                                celda.getProperties().put("animacionSug", ftCelda);
                            }
                        }
                    }
                }
            }
        }
    }

    private void pintarBloques(List<SesionAsignada> sesiones) {
        Random rand = new Random();
        Map<Integer, String> coloresPorCurso = new HashMap<>();
        Map<SesionAsignada, PosicionVisual> layout = calcularLayoutCartas(sesiones);

        int[] maxEmpalmesPorDia = new int[8];
        Arrays.fill(maxEmpalmesPorDia, 1);

        for (SesionAsignada s : sesiones) {
            PosicionVisual pos = layout.get(s);
            int dia = s.getColumnaDia();
            if (pos.totalColumnas > maxEmpalmesPorDia[dia]) maxEmpalmesPorDia[dia] = pos.totalColumnas;
        }
        actualizarBotonesDeExpansion(maxEmpalmesPorDia);

        List<SesionAsignada> sesionesParaPintar = new ArrayList<>(sesiones);

        for (SesionAsignada s : sesionesParaPintar) {
            Group g = s.getGrupo();
            int idCurso = g.getCurso().getId();

            if (!coloresPorCurso.containsKey(idCurso)) {
                Color color = Color.color(rand.nextDouble() * 0.5 + 0.5, rand.nextDouble() * 0.5 + 0.5, rand.nextDouble() * 0.5 + 0.5);
                String hex = String.format("#%02X%02X%02X", (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255));
                coloresPorCurso.put(idCurso, hex);
            }

            VBox caja = new VBox();
            caja.setStyle("-fx-background-color: " + coloresPorCurso.get(idCurso) + "; -fx-border-color: black; -fx-padding: 3; -fx-border-radius: 3;");

            int fInicio = s.getFilaHora();
            int fFin = fInicio + s.getSpanFilas();
            String textoHora = String.format("%02d:%02d - %02d:%02d",
                    HORA_INICIO + (fInicio / 2), (fInicio % 2) * 30,
                    HORA_INICIO + (fFin / 2), (fFin % 2) * 30);

            Label lblHoraVista = new Label(textoHora);
            lblHoraVista.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #222222;");
            caja.getChildren().add(lblHoraVista);

            if (chkNombreCurso != null && chkNombreCurso.isSelected()) {
                Label lblCurso = new Label(g.getCurso().getNombre());
                lblCurso.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
                lblCurso.setWrapText(true);
                caja.getChildren().add(lblCurso);
            }
            if (chkProfesor != null && chkProfesor.isSelected()) {
                Label lblProf = new Label(g.getProfesor().getNombre());
                lblProf.setStyle("-fx-font-size: 10px;");
                lblProf.setWrapText(true);
                caja.getChildren().add(lblProf);
            }
            if (chkRangoAlumnos != null && chkRangoAlumnos.isSelected()) {
                Label lblAlumnos = new Label("Alumnos: " + g.getRangoInicial() + "-" + g.getRangoFinal());
                lblAlumnos.setStyle("-fx-font-size: 10px; -fx-font-style: italic;");
                caja.getChildren().add(lblAlumnos);
            }
            if (chkIdGrupo != null && chkIdGrupo.isSelected()) {
                Label lblId = new Label("ID: " + g.getIdGrupo());
                lblId.setStyle("-fx-font-size: 10px;");
                caja.getChildren().add(lblId);
            }

            String infoTooltip = String.format("Horario: %s\nMateria: %s\nProfesor: %s\nAlumnos: %d al %d\nID Grupo: %s",
                    textoHora, g.getCurso().getNombre(), g.getProfesor().getNombre() + " " + g.getProfesor().getApellidoPaterno(),
                    g.getRangoInicial(), g.getRangoFinal(), g.getIdGrupo());
            Tooltip tooltip = new Tooltip(infoTooltip);
            Tooltip.install(caja, tooltip);

            ContextMenu menu = new ContextMenu();
            MenuItem itemEliminar = new MenuItem("🗑️ Eliminar clase y reembolsar horas");
            itemEliminar.setOnAction(e -> {
                horarioGenerado.remove(s);
                int duracionBloques = s.getSpanFilas();
                mapaOcupacion.eliminarClase(s.getSlotInicioSemanal(), duracionBloques, s.getGrupo());

                for (EstadoGrupo eg : todosLosEstados) {
                    if (eg.getGrupo().getIdGrupo().equals(s.getGrupo().getIdGrupo())) {
                        eg.reembolsarHoras(duracionBloques / 2.0);
                        break;
                    }
                }

                // NUEVO: Sincronizar todos los paneles y Guardar al eliminar
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
                // 1. INICIAMOS EL DRAG & DROP INMEDIATAMENTE (Sin tocar la UI todavía)
                Dragboard db = caja.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent clipboardContent = new ClipboardContent();
                double horas = s.getSpanFilas() / 2.0;
                clipboardContent.putString(String.valueOf(horas));
                db.setContent(clipboardContent);

                event.consume(); // Confirmamos el inicio del arrastre

                // 2. EN EL SIGUIENTE FOTOGRAMA, HACEMOS LOS CAMBIOS VISUALES
                Platform.runLater(() -> {
                    for (EstadoGrupo eg : todosLosEstados) {
                        if (eg.getGrupo().getIdGrupo().equals(s.getGrupo().getIdGrupo())) {
                            grupoSeleccionado = eg;

                            // Cambio silencioso del ComboBox
                            if (cmbProfesorManual != null) {
                                javafx.event.EventHandler<javafx.event.ActionEvent> handler = cmbProfesorManual.getOnAction();
                                cmbProfesorManual.setOnAction(null);
                                cmbProfesorManual.setValue(eg.getGrupo().getProfesor());
                                cmbProfesorManual.setOnAction(handler);

                                // Actualizar panel lateral
                                List<EstadoGrupo> filtrados = new ArrayList<>();
                                for (EstadoGrupo e : todosLosEstados) {
                                    if (e.getGrupo().getProfesor().getId() == eg.getGrupo().getProfesor().getId()) {
                                        filtrados.add(e);
                                    }
                                }
                                listaEstados.setAll(filtrados);
                                actualizarFabricaDeBloques();
                            }
                            break;
                        }
                    }

                    // Remover lógicamente la clase
                    horarioGenerado.remove(s);
                    int duracionBloques = s.getSpanFilas();
                    mapaOcupacion.eliminarClase(s.getSlotInicioSemanal(), duracionBloques, s.getGrupo());
                    if (grupoSeleccionado != null) grupoSeleccionado.reembolsarHoras(horas);

                    // Ocultar el bloque que tienes en el cursor
                    caja.setVisible(false);

                    // PINTAR NUEVAS DISPONIBILIDADES (limpiando las anteriores)
                    if (grupoSeleccionado != null) {
                        iluminarDisponibilidadProfesor(grupoSeleccionado.getGrupo().getProfesor());
                        pintarSugerencias(grupoSeleccionado.getGrupo().getProfesor());
                    }

                    // ACTIVAR MODO FANTASMA
                    aplicarModoFantasmaATarjetas(true);
                });
            });

            caja.setOnDragDone(event -> {
                if (event.getTransferMode() == null) {
                    double horas = s.getSpanFilas() / 2.0;
                    if (grupoSeleccionado != null) grupoSeleccionado.agregarHoras(horas);
                    horarioGenerado.add(s);
                    mapaOcupacion.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas(), s.getGrupo());

                    // Sincronizar también si la reubicación se cancela y regresa al mismo lugar
                    Platform.runLater(() -> {
                        if (listGruposPendientes != null) listGruposPendientes.refresh();
                        popularFiltros();
                        aplicarFiltros();
                        autoGuardar();
                    });
                }

                // DESACTIVAR MODO FANTASMA
                aplicarModoFantasmaATarjetas(false);

                event.consume();
            });

            PosicionVisual pos = layout.get(s);
            ColumnConstraints columnaExacta = gridCalendario.getColumnConstraints().get(s.getColumnaDia());
            javafx.beans.property.DoubleProperty anchoDinamico = columnaExacta.minWidthProperty();
            caja.maxWidthProperty().bind(anchoDinamico.divide(pos.totalColumnas).subtract(4));
            caja.translateXProperty().bind(anchoDinamico.divide(pos.totalColumnas).multiply(pos.indiceColumna).add(2));
            caja.setMinWidth(45);
            caja.setMouseTransparent(false);
            caja.setStyle(caja.getStyle() + "-fx-cursor: hand;");

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
                    btnExpandir.setDisable(true); btnExpandir.setOpacity(0.3);
                } else {
                    btnExpandir.setDisable(false); btnExpandir.setOpacity(1.0);
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
                    bloqueActual.add(s); maxFilaFin = Math.max(maxFilaFin, fin);
                } else {
                    bloques.add(new ArrayList<>(bloqueActual));
                    bloqueActual.clear(); bloqueActual.add(s);
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

    public void actualizarMensajeIA(String mensaje) {
        Platform.runLater(() -> lblEstadoIA.setText(mensaje));
    }

    // --- NUEVO: REFRESCADO DINÁMICO DE FILTROS ---
    private void popularFiltros() {
        String cursoSel = cmbFiltroCurso.getValue();
        String profSel = cmbFiltroProfesor.getValue();

        Set<String> cursos = new HashSet<>();
        Set<String> profesores = new HashSet<>();
        for (SesionAsignada s : horarioGenerado) {
            cursos.add(s.getGrupo().getCurso().getNombre());
            profesores.add(s.getGrupo().getProfesor().getNombre());
        }
        List<String> listaCursos = new ArrayList<>(cursos); Collections.sort(listaCursos);
        List<String> listaProfesores = new ArrayList<>(profesores); Collections.sort(listaProfesores);

        // Desactivamos temporalmente los eventos para que no causen un bucle visual al cambiar los items
        cmbFiltroCurso.setOnAction(null);
        cmbFiltroProfesor.setOnAction(null);

        cmbFiltroCurso.getItems().setAll(listaCursos);
        cmbFiltroProfesor.getItems().setAll(listaProfesores);

        // Restaurar la selección si el profesor/curso aún existe en el tablero
        if (listaCursos.contains(cursoSel)) cmbFiltroCurso.setValue(cursoSel);
        if (listaProfesores.contains(profSel)) cmbFiltroProfesor.setValue(profSel);

        cmbFiltroCurso.setDisable(listaCursos.isEmpty());
        cmbFiltroProfesor.setDisable(listaProfesores.isEmpty());
        btnLimpiarFiltros.setDisable(listaCursos.isEmpty() && listaProfesores.isEmpty());

        // Reactivar los eventos
        cmbFiltroCurso.setOnAction(e -> aplicarFiltros());
        cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
    }

    private void aplicarFiltros() {
        if (horarioGenerado == null || horarioGenerado.isEmpty()) {
            construirTablaBase();
        } else {
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

        if (cmbProfesorManual != null && cmbProfesorManual.getValue() != null) {
            iluminarDisponibilidadProfesor(cmbProfesorManual.getValue());
            pintarSugerencias(cmbProfesorManual.getValue());
        }
    }

    private void limpiarFiltros() {
        cmbFiltroCurso.setOnAction(null); cmbFiltroProfesor.setOnAction(null);
        cmbFiltroCurso.getSelectionModel().clearSelection(); cmbFiltroCurso.setValue(null);
        cmbFiltroProfesor.getSelectionModel().clearSelection(); cmbFiltroProfesor.setValue(null);
        cmbFiltroCurso.setOnAction(e -> aplicarFiltros()); cmbFiltroProfesor.setOnAction(e -> aplicarFiltros());
        aplicarFiltros();
    }

    private class PosicionVisual {
        int indiceColumna, totalColumnas;
        public PosicionVisual(int i, int t) { this.indiceColumna = i; this.totalColumnas = t; }
    }
}