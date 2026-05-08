package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.dao.GroupDAO;
import com.osgadev.organizadorhorariosfx.dao.ScheduleDAO;
import com.osgadev.organizadorhorariosfx.dao.StudentDAO;
import com.osgadev.organizadorhorariosfx.model.Student;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.service.ManualAssignmentManager;
import com.osgadev.organizadorhorariosfx.service.ScheduleService;
import com.osgadev.organizadorhorariosfx.service.OccupationMap;
import com.osgadev.organizadorhorariosfx.dto.AssignedSession;
import com.osgadev.organizadorhorariosfx.dto.GroupState;
import com.osgadev.organizadorhorariosfx.util.SessionGlobal;
import com.osgadev.organizadorhorariosfx.view.ScheduleGridManager;
import com.osgadev.organizadorhorariosfx.view.ScheduleUIFactory;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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

    @FXML private Label lblAnioActivo;
    @FXML private Label lblEtapaActiva;
    @FXML private Label lblEstadoIA;

    @FXML private Button btnGenerar;
    @FXML private Button btnGuardarBD;
    @FXML private Button btnBorrarBD;
    @FXML private Button btnExportarExcel;
    @FXML private Button btnToggleSugerencias;

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

    // CAMBIO: Ahora es un GridPane para la matriz 2x2
    @FXML private GridPane cajaBloquesGeneradores;

    // =====================================================================
    // VARIABLES DE ESTADO Y SERVICIOS
    // =====================================================================
    private List<AssignedSession> horarioGenerado = new ArrayList<>();
    private GroupDAO groupDAO;
    private AvailabilityDAO availabilityDAO;
    private ScheduleService scheduleService;
    private ScheduleDAO scheduleDAO;

    private ObservableList<GroupState> listaEstados;
    private ManualAssignmentManager assignmentManager;
    private OccupationMap occupationMap;

    private ScheduleGridManager gridManager;

    private final double[] TAMANOS_BLOQUES = {1.0, 1.5, 2.0, 2.5};
    private boolean mostrarSugerencias = true;

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

        gridManager = new ScheduleGridManager(
                gridCalendario, assignmentManager, scheduleService, occupationMap, availabilityDAO, lblHorasRestantes,
                this::onGridModificadoManual,
                this::onGrupoExtraidoParaMover
        );
        gridManager.setHorarioGenerado(this.horarioGenerado);

        lblAnioActivo.setText("Año: " + SessionGlobal.getAnioActual());
        lblEtapaActiva.setText("Etapa: " + SessionGlobal.getEtapaActual());

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
    // CALLBACKS DEL GRID MANAGER
    // =====================================================================

    private void onGridModificadoManual() {
        btnGuardarBD.setDisable(false);
        actualizarFabricaDeBloques();
        if (listGruposPendientes != null) listGruposPendientes.refresh();
        popularFiltros();
        aplicarFiltros();
        autoGuardar();
    }

    private void onGrupoExtraidoParaMover(GroupState eg) {
        if (cmbProfesorManual != null) {
            javafx.event.EventHandler<javafx.event.ActionEvent> handler = cmbProfesorManual.getOnAction();
            cmbProfesorManual.setOnAction(null);
            cmbProfesorManual.setValue(eg.getGrupo().getProfesor());
            cmbProfesorManual.setOnAction(handler);
        }

        List<GroupState> filtrados = assignmentManager.obtenerGruposPorProfesor(eg.getGrupo().getProfesor());
        listaEstados.setAll(filtrados);
        actualizarFabricaDeBloques();

        gridManager.iluminarDisponibilidadProfesor(eg.getGrupo().getProfesor());
        gridManager.pintarSugerencias(eg.getGrupo().getProfesor(), mostrarSugerencias);
    }

    // =====================================================================
    // EVENTOS UI Y ACCIONES PRINCIPALES
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

        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();
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
    public void cargarHorario() {
        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();

        if (anio == null || etapa == null) {
            mostrarAlerta("Atención", "Error al obtener el año y la etapa de la sesión.");
            return;
        }

        scheduleService.limpiarCache();

        List<Group> gruposDelSemestre = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
        cargarGruposEnAlmacen(gruposDelSemestre);

        if (scheduleDAO.existsSchedule(anio, etapa)) {
            this.horarioGenerado.clear();
            this.horarioGenerado.addAll(scheduleDAO.loadSchedule(anio, etapa));
            gridManager.setHorarioGenerado(this.horarioGenerado);
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
        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();

        btnGenerar.setText("Calculando IA...");
        btnGenerar.setDisable(true);
        btnBorrarBD.setDisable(true);

        fase1Vacia.setVisible(false);
        scrollCalendario.setVisible(true);

        Task<List<AssignedSession>> task = new Task<>() {
            @Override
            protected List<AssignedSession> call() {
                List<Group> grupos = groupDAO.obtenerPorAnioYEtapa(anio, etapa);

                return scheduleService.generarHorario(grupos, (estadoParcial, mensajeIA) -> {
                    Platform.runLater(() -> {
                        actualizarMensajeIA(mensajeIA);
                        gridManager.construirTablaBase();
                        gridManager.pintarBloques(estadoParcial, true, false, false, false);
                    });
                });
            }
        };

        task.setOnSucceeded(e -> {
            List<AssignedSession> resultado = task.getValue();
            restaurarBotonesIA();

            if (resultado != null) {
                this.horarioGenerado.clear();
                this.horarioGenerado.addAll(resultado);
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
        boolean exito = scheduleDAO.saveSchedule(this.horarioGenerado, SessionGlobal.getAnioActual(), SessionGlobal.getEtapaActual());
        if (exito) {
            mostrarAlerta("Guardado", "El horario se ha guardado exitosamente.");
            btnGuardarBD.setDisable(true);
            btnBorrarBD.setDisable(false);
        }
    }

    @FXML
    public void borrarDeBD() {
        if (scheduleDAO.deleteSchedule(SessionGlobal.getAnioActual(), SessionGlobal.getEtapaActual())) {
            this.horarioGenerado.clear();
            cargarHorario();
            lblEstadoIA.setText("");
        }
    }

    private void autoGuardar() {
        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();
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
        // CAMBIO: Al forzar la reasignación de CellFactory, garantizamos que
        // la lista oculta del ComboBox también refresque sus celdas.
        if (cmbProfesorManual != null) {
            cmbProfesorManual.setCellFactory(lv -> crearCeldaProfesor());
            cmbProfesorManual.setButtonCell(crearCeldaProfesor());
        }
        if (listGruposPendientes != null) listGruposPendientes.refresh();

        if (cajaBloquesGeneradores == null) return;
        cajaBloquesGeneradores.getChildren().clear();

        GroupState grupoSeleccionado = assignmentManager.getGrupoSeleccionado();
        if (grupoSeleccionado == null) return;

        double restantes = grupoSeleccionado.getHorasRestantes();
        if (lblHorasRestantes != null) lblHorasRestantes.setText(restantes + " hrs restantes");

        String hexColor = grupoSeleccionado.getGrupo().getCurso().getColorHex();

        int i = 0;
        for (double tamano : TAMANOS_BLOQUES) {
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

                    gridManager.aplicarModoFantasmaATarjetas(true);
                    event.consume();
                });

                bloqueVisual.setOnDragDone(event -> {
                    gridManager.aplicarModoFantasmaATarjetas(false);
                    event.consume();
                });
            }

            // CAMBIO: Añadimos al GridPane especificando columna y fila (Matriz 2x2)
            int columna = i % 2;
            int fila = i / 2;
            cajaBloquesGeneradores.add(bloqueVisual, columna, fila);
            i++;
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
            gridManager.construirTablaBase();
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

            gridManager.construirTablaBase();
            gridManager.pintarBloques(
                    filtradas,
                    chkNombreCurso != null && chkNombreCurso.isSelected(),
                    chkProfesor != null && chkProfesor.isSelected(),
                    chkRangoAlumnos != null && chkRangoAlumnos.isSelected(),
                    chkIdGrupo != null && chkIdGrupo.isSelected()
            );
        }

        if (cmbProfesorManual != null && cmbProfesorManual.getValue() != null) {
            gridManager.iluminarDisponibilidadProfesor(cmbProfesorManual.getValue());
            gridManager.pintarSugerencias(cmbProfesorManual.getValue(), mostrarSugerencias);
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