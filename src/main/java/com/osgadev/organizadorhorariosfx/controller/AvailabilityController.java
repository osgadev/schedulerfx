package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.util.GlobalSession;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class AvailabilityController implements Initializable {

    @FXML private ComboBox<Teacher> cmbProfesor;
    @FXML private Label lblEstadoBD;
    @FXML private VBox panelControles;
    @FXML private FlowPane paletaCursos;
    @FXML private VBox panelDeudaHoras; // <-- NUEVO COMPONENTE
    @FXML private Button btnGuardar, btnEliminarTodo;
    @FXML private GridPane gridCalendario;
    @FXML private ScrollPane scrollCalendario;

    private List<BloqueTiempo> listaBloques = new ArrayList<>();

    // VARIABLES PARA LA SELECCIÓN MÚLTIPLE Y ARRASTRE
    private List<BloqueTiempo> bloquesSeleccionados = new ArrayList<>();
    private BloqueTiempo bloqueArrastrado = null;

    // VARIABLES PARA ENLAZAR LA PALETA DE CURSOS
    private Map<String, ToggleButton> mapaBotonesCursos = new HashMap<>();
    private ToggleGroup grupoCursos = new ToggleGroup();

    // SNAPSHOTS PARA EL ARRASTRE EN GRUPO
    private class BloqueSnapshot {
        BloqueTiempo bloque;
        int colDia, slotInicio, slotFin;
        public BloqueSnapshot(BloqueTiempo b) {
            this.bloque = b;
            this.colDia = b.colDia;
            this.slotInicio = b.slotInicioSemanal;
            this.slotFin = b.slotFinSemanal;
        }
    }
    private List<BloqueSnapshot> dragSnapshots = new ArrayList<>();
    private BloqueSnapshot arrastradoSnapshot = null;

    private TeacherDAO teacherDAO = new TeacherDAO();
    private AvailabilityDAO availabilityDAO = new AvailabilityDAO();

    private final int HORA_INICIO_VISUAL = 7;
    private final int HORA_FIN_VISUAL = 24;
    private final int FILAS_VISUALES = (HORA_FIN_VISUAL - HORA_INICIO_VISUAL) * 4;

    private boolean isDragging = false;
    private boolean isResizing = false;
    private double startMouseY = 0;
    private int initialStartSlot = 0;
    private int initialEndSlot = 0;
    private int dragOffsetSlots = 0;

    private class BloqueTiempo {
        int colDia, slotInicioSemanal, slotFinSemanal;
        Course cursoSugerido;
        StackPane uiNode;
        VBox contentNode;
        boolean superpuesto = false;

        public BloqueTiempo(int colDia, int slotInicioSemanal, int slotFinSemanal, Course cursoSugerido, StackPane uiNode, VBox contentNode) {
            this.colDia = colDia;
            this.slotInicioSemanal = slotInicioSemanal;
            this.slotFinSemanal = slotFinSemanal;
            this.cursoSugerido = cursoSugerido;
            this.uiNode = uiNode;
            this.contentNode = contentNode;
        }

        public boolean seSuperpone(BloqueTiempo otro) {
            return (this.slotInicioSemanal < otro.slotFinSemanal && this.slotFinSemanal > otro.slotInicioSemanal)
                    && this.colDia == otro.colDia;
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        aplicarDisenoEmpresarial();
        configurarCuadricula();
        cargarProfesores();

        btnGuardar.setOnAction(e -> guardarEnBD());
        btnEliminarTodo.setOnAction(e -> eliminarTodaDisponibilidad());

        // Enfoque por defecto si aún no se selecciona ningún profesor al abrir la pantalla
        enfocarHora(8);

        Integer idPendiente = GlobalSession.getProfesorNavegacion();
        if (idPendiente != null) {
            seleccionarProfesorEnComboPorId(idPendiente);
            GlobalSession.limpiarProfesorNavegacion();
        }
    }

    private void enfocarHora(int horaObjetivo) {
        Platform.runLater(() -> {
            double alturaFila = 14.0;
            int filasPorHora = 4;

            double targetY = (horaObjetivo - HORA_INICIO_VISUAL) * filasPorHora * alturaFila;

            double alturaTotalGrid = gridCalendario.getBoundsInLocal().getHeight();
            double alturaVisible = scrollCalendario.getViewportBounds().getHeight();

            if (alturaTotalGrid > alturaVisible) {
                double maxScrollPosible = alturaTotalGrid - alturaVisible;
                double proporcionCalculada = targetY / maxScrollPosible;

                scrollCalendario.setVvalue(Math.max(0.0, Math.min(proporcionCalculada, 1.0)));
            }
        });
    }

    private void aplicarDisenoEmpresarial() {
        panelControles.setSpacing(15);

        paletaCursos.setHgap(8);
        paletaCursos.setVgap(8);
        paletaCursos.setAlignment(Pos.CENTER_LEFT);

        btnGuardar.setText("Guardar Calendario");
        btnGuardar.setStyle("-fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 16;");
        btnGuardar.setMaxWidth(Double.MAX_VALUE);

        btnEliminarTodo.setText("Limpiar Todo");
        btnEliminarTodo.setStyle("-fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 16;");
        btnEliminarTodo.setMaxWidth(Double.MAX_VALUE);

        lblEstadoBD.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 5 0;");
    }

    private void actualizarHorasDeuda() {
        Teacher profe = cmbProfesor.getValue();
        if (profe == null || profe.getCursos() == null) {
            panelDeudaHoras.getChildren().clear();
            return;
        }

        Map<String, Double> horasEspecificasPorCurso = new HashMap<>();
        double horasComodin = 0.0;
        double horasMinimasTotales = 0.0;

        for (Course c : profe.getCursos()) {
            horasMinimasTotales += c.getMinHorasSemanales();
        }

        for (BloqueTiempo b : listaBloques) {
            double horasBloque = (b.slotFinSemanal - b.slotInicioSemanal) * 0.5;

            if (b.cursoSugerido == null) {
                horasComodin += horasBloque;
            } else {
                String nombreCurso = b.cursoSugerido.getNombre();
                horasEspecificasPorCurso.put(
                        nombreCurso,
                        horasEspecificasPorCurso.getOrDefault(nombreCurso, 0.0) + horasBloque
                );
            }
        }

        double horasEspecificasTotales = 0.0;
        for (double horas : horasEspecificasPorCurso.values()) {
            horasEspecificasTotales += horas;
        }

        double horasDisponiblesTotales = horasEspecificasTotales + horasComodin;
        double deudaTotal = Math.max(0.0, horasMinimasTotales - horasDisponiblesTotales);

        panelDeudaHoras.getChildren().clear();

        Label lblResumen = new Label(
                String.format("Total disponible: %.1f / %.1f h  |  Deuda global: %.1f h",
                        horasDisponiblesTotales, horasMinimasTotales, deudaTotal)
        );
        lblResumen.setWrapText(true);
        lblResumen.setStyle(
                deudaTotal == 0
                        ? "-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-font-size: 12px;"
                        : "-fx-text-fill: #dc3545; -fx-font-weight: bold; -fx-font-size: 12px;"
        );
        panelDeudaHoras.getChildren().add(lblResumen);

        if (horasComodin > 0) {
            Label lblComodin = new Label(String.format("Bolsa comodín disponible: %.1f h", horasComodin));
            lblComodin.setWrapText(true);
            lblComodin.setStyle("-fx-text-fill: #b26a00; -fx-font-weight: bold; -fx-font-size: 12px;");
            panelDeudaHoras.getChildren().add(lblComodin);
        }

        for (Course c : profe.getCursos()) {
            double horasObligatorias = c.getMinHorasSemanales();
            double horasEspecificas = horasEspecificasPorCurso.getOrDefault(c.getNombre(), 0.0);

            String texto;
            String estilo;

            if (horasEspecificas >= horasObligatorias) {
                texto = String.format("• %s: %.1f / %.1f h (Completado)",
                        c.getNombre(), horasEspecificas, horasObligatorias);
                estilo = "-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-font-size: 12px;";
            } else if ((horasEspecificas + horasComodin) >= horasObligatorias) {
                double faltanteEspecifico = horasObligatorias - horasEspecificas;
                texto = String.format("• %s: %.1f / %.1f h (Asignable con %.1f h comodín)",
                        c.getNombre(), horasEspecificas, horasObligatorias, faltanteEspecifico);
                estilo = "-fx-text-fill: #b26a00; -fx-font-weight: bold; -fx-font-size: 12px;";
            } else {
                double faltan = horasObligatorias - (horasEspecificas + horasComodin);
                texto = String.format("• %s: %.1f / %.1f h (Faltan %.1f h reales)",
                        c.getNombre(), horasEspecificas, horasObligatorias, faltan);
                estilo = "-fx-text-fill: #dc3545; -fx-font-weight: normal; -fx-font-size: 12px;";
            }

            Label lblCurso = new Label(texto);
            lblCurso.setWrapText(true);
            lblCurso.setStyle(estilo);
            panelDeudaHoras.getChildren().add(lblCurso);
        }
    }

    private void configurarCuadricula() {
        gridCalendario.getChildren().clear();

        for (int i = 0; i < FILAS_VISUALES; i++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(14.0); rc.setPrefHeight(14.0);
            gridCalendario.getRowConstraints().add(rc);
        }

        int indexFila = 0;
        for (int i = HORA_INICIO_VISUAL; i < HORA_FIN_VISUAL; i++) {
            Pane sepHora = new Pane(); sepHora.setStyle("-fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");
            gridCalendario.add(sepHora, 1, indexFila, 7, 1);

            Pane sepMedia = new Pane(); sepMedia.setStyle("-fx-border-color: -color-border-subtle; -fx-border-style: dashed; -fx-border-width: 1 0 0 0;");
            gridCalendario.add(sepMedia, 1, indexFila + 2, 7, 1);

            Label lbl = new Label(String.format("%02d:00", i));
            lbl.getStyleClass().addAll("text-muted", "text-small");
            GridPane.setMargin(lbl, new Insets(0, 5, 0, 0));
            GridPane.setHalignment(lbl, javafx.geometry.HPos.RIGHT);
            gridCalendario.add(lbl, 0, indexFila);

            indexFila += 4;
        }

        for (int col = 1; col <= 7; col++) {
            Pane sepVertical = new Pane();
            sepVertical.setStyle("-fx-border-color: -color-border-subtle; -fx-border-width: 0 1 0 0;");
            gridCalendario.add(sepVertical, col, 0, 1, FILAS_VISUALES);

            for (int row = 0; row < FILAS_VISUALES; row++) {
                Pane celdaInvisible = new Pane();
                final int finalCol = col;
                final int finalRow = row;

                celdaInvisible.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 1) {
                        bloquesSeleccionados.clear();
                        actualizarEstadoGlobal();
                    } else if (e.getClickCount() == 2) {
                        if (cmbProfesor.getValue() != null && !isDragging && !isResizing) {
                            crearBloquePredeterminado(finalCol, finalRow);
                        }
                    }
                });

                celdaInvisible.setOnMouseDragEntered(e -> {
                    if (bloqueArrastrado != null && isDragging && arrastradoSnapshot != null) {
                        int targetRow = finalRow - dragOffsetSlots;
                        if(targetRow < 0) targetRow = 0;
                        moverSeleccionADestino(finalCol, targetRow);
                    }
                });

                gridCalendario.add(celdaInvisible, col, row);
            }
        }
    }

    private void crearBloquePredeterminado(int colDia, int filaInicio) {
        int h1 = HORA_INICIO_VISUAL + (filaInicio / 4);
        int m1 = (filaInicio % 4) * 15;

        int h2 = h1 + 1;
        int m2 = m1;
        if(h2 >= HORA_FIN_VISUAL) { h2 = 24; m2 = 0; }

        int slotInicio = ((colDia - 1) * 48) + (h1 * 2) + (m1 / 30);
        int slotFin = ((colDia - 1) * 48) + (h2 * 2) + (m2 / 30);

        BloqueTiempo temp = new BloqueTiempo(colDia, slotInicio, slotFin, null, null, null);
        for (BloqueTiempo b : listaBloques) {
            if (b.seSuperpone(temp)) return;
        }

        BloqueTiempo nuevo = crearYPosicionarNodo(colDia, slotInicio, slotFin, null, h1, m1, h2, m2);

        bloquesSeleccionados.clear();
        bloquesSeleccionados.add(nuevo);
        actualizarEstadoGlobal();

        actualizarHorasDeuda(); // <-- SE ACTUALIZAN LAS HORAS AQUÍ
    }

    private void seleccionarProfesorEnComboPorId(int idBuscado) {
        for (Teacher t : cmbProfesor.getItems()) {
            if (t != null && t.getId() == idBuscado) {
                cmbProfesor.getSelectionModel().select(t);
                procesarSeleccionProfesor(t);
                break;
            }
        }
    }

    private void procesarSeleccionProfesor(Teacher profe) {
        if (profe != null) {
            panelControles.setDisable(false);
            cargarPaletaCursos(profe);
            cargarDesdeBD(profe);
            bloquesSeleccionados.clear();
            bloqueArrastrado = null;
            actualizarEstadoGlobal();
            lblEstadoBD.setText("Profesor seleccionado. Listo para asignar bloques.");
        }
    }

    private void cargarPaletaCursos(Teacher profe) {
        paletaCursos.getChildren().clear();
        mapaBotonesCursos.clear();

        grupoCursos = new ToggleGroup();

        ToggleButton btnComodin = new ToggleButton("Comodín");
        btnComodin.getStyleClass().addAll("button", "outlined");
        btnComodin.setStyle("-fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px;");
        btnComodin.setToggleGroup(grupoCursos);
        btnComodin.setOnAction(e -> aplicarCursoSeleccionado(null));
        paletaCursos.getChildren().add(btnComodin);

        mapaBotonesCursos.put("COMODIN_NULL", btnComodin);

        if (profe.getCursos() != null) {
            for (Course c : profe.getCursos()) {
                ToggleButton btnCurso = new ToggleButton(c.getNombre());
                btnCurso.getStyleClass().addAll("button", "outlined");
                btnCurso.setStyle("-fx-cursor: hand; -fx-padding: 6 12; -fx-font-size: 12px;");
                btnCurso.setToggleGroup(grupoCursos);
                btnCurso.setOnAction(e -> aplicarCursoSeleccionado(c));
                paletaCursos.getChildren().add(btnCurso);

                mapaBotonesCursos.put(c.getNombre(), btnCurso);
            }
        }
    }

    private void actualizarSeleccionPaleta() {
        if (bloquesSeleccionados.isEmpty()) {
            if (grupoCursos.getSelectedToggle() != null) {
                grupoCursos.getSelectedToggle().setSelected(false);
            }
            return;
        }

        Course tipoBase = bloquesSeleccionados.get(0).cursoSugerido;
        String nombreBase = (tipoBase == null) ? "COMODIN_NULL" : tipoBase.getNombre();

        boolean todosIguales = true;

        for (BloqueTiempo b : bloquesSeleccionados) {
            String nombreActual = (b.cursoSugerido == null) ? "COMODIN_NULL" : b.cursoSugerido.getNombre();
            if (!nombreBase.equals(nombreActual)) {
                todosIguales = false;
                break;
            }
        }

        if (todosIguales) {
            ToggleButton btn = mapaBotonesCursos.get(nombreBase);
            if (btn != null) {
                btn.setSelected(true);
            }
        } else {
            if (grupoCursos.getSelectedToggle() != null) {
                grupoCursos.getSelectedToggle().setSelected(false);
            }
        }
    }

    private void aplicarCursoSeleccionado(Course c) {
        if (bloquesSeleccionados.isEmpty()) {
            lblEstadoBD.setText("Selecciona al menos un bloque primero.");
            lblEstadoBD.getStyleClass().setAll("label", "warning");

            if (grupoCursos.getSelectedToggle() != null) {
                grupoCursos.getSelectedToggle().setSelected(false);
            }
            return;
        }

        for (BloqueTiempo b : bloquesSeleccionados) {
            b.cursoSugerido = c;
            actualizarContenidoVisualBloque(b);
        }

        actualizarEstadoGlobal();
        actualizarHorasDeuda(); // <-- SE ACTUALIZAN LAS HORAS AQUÍ

        lblEstadoBD.setText("Curso aplicado a " + bloquesSeleccionados.size() + " bloque(s).");
        lblEstadoBD.getStyleClass().setAll("label", "success");
    }

    private void cargarDesdeBD(Teacher profe) {
        limpiarCuadriculaBloques();
        List<Availability> guardados = availabilityDAO.getByTeacher(profe);

        if (guardados.isEmpty()) {
            lblEstadoBD.setText("Lienzo en blanco. Comienza a crear bloques.");
            lblEstadoBD.getStyleClass().setAll("label", "text-muted");
            enfocarHora(8);
        } else {
            lblEstadoBD.setText("Mostrando " + guardados.size() + " bloques registrados.");
            lblEstadoBD.getStyleClass().setAll("label", "success");

            int primeraHora = 24;

            for (Availability dbBlock : guardados) {
                int colDia = dbBlock.getColumnaDia();
                int hInicio = dbBlock.getHoraInicio();
                int mInicio = dbBlock.getMinutoInicio();
                int hFin = dbBlock.getHoraFin();
                int mFin = dbBlock.getMinutoFin();

                // validacion para que no se rompa el programa cuando es multiplo exacto
                if (hFin == 0 && mFin == 0 && hInicio > 0) hFin = 24;

                if (hInicio < primeraHora) {
                    primeraHora = hInicio;
                }

                if (hInicio >= HORA_INICIO_VISUAL && hFin <= HORA_FIN_VISUAL) {
                    crearYPosicionarNodo(colDia, dbBlock.getStartSlot(), dbBlock.getEndSlot(), dbBlock.getCursoSugerido(), hInicio, mInicio, hFin, mFin);
                }
            }
            actualizarEstadoSuperposiciones();
            enfocarHora(primeraHora);
        }

        // <-- SE CALCULA LA DEUDA INICIAL AQUÍ UNA VEZ CARGADA LA DB
        Platform.runLater(this::actualizarHorasDeuda);
    }

    private void guardarEnBD() {
        Teacher profe = cmbProfesor.getValue();
        if (profe == null) return;

        List<Availability> nuevosBloques = new ArrayList<>();
        for (BloqueTiempo b : listaBloques) {
            nuevosBloques.add(new Availability(profe, b.cursoSugerido, b.slotInicioSemanal, b.slotFinSemanal));
        }

        availabilityDAO.saveAll(profe, nuevosBloques);
        lblEstadoBD.setText("¡Calendario guardado exitosamente!");
        lblEstadoBD.getStyleClass().setAll("label", "success");

        bloquesSeleccionados.clear();
        bloqueArrastrado = null;
        actualizarEstadoGlobal();
    }

    private void eliminarTodaDisponibilidad() {
        Teacher profe = cmbProfesor.getValue();
        if (profe == null) return;
        availabilityDAO.deleteAllByTeacher(profe);
        limpiarCuadriculaBloques();
        lblEstadoBD.setText("Calendario limpiado por completo.");
        lblEstadoBD.getStyleClass().setAll("label", "danger");

        actualizarHorasDeuda(); // <-- SE ACTUALIZAN LAS HORAS AL LIMPIAR TODO
    }

    private void limpiarCuadriculaBloques() {
        for (BloqueTiempo b : listaBloques) gridCalendario.getChildren().remove(b.uiNode);
        listaBloques.clear();
        bloquesSeleccionados.clear();
        bloqueArrastrado = null;
        actualizarEstadoGlobal();
    }

    private BloqueTiempo crearYPosicionarNodo(int colDia, int slotInicio, int slotFin, Course curso, int h1, int m1, int h2, int m2) {
        int filaInicio = ((h1 - HORA_INICIO_VISUAL) * 4) + (m1 / 15);
        int filaFin = ((h2 - HORA_INICIO_VISUAL) * 4) + (m2 / 15);

        StackPane rootNode = new StackPane();
        rootNode.setPadding(new Insets(2));
        GridPane.setMargin(rootNode, new Insets(1, 2, 1, 2));

        VBox contentNode = new VBox();
        contentNode.setAlignment(Pos.TOP_LEFT);

        rootNode.getChildren().add(contentNode);

        BloqueTiempo bloque = new BloqueTiempo(colDia, slotInicio, slotFin, curso, rootNode, contentNode);

        actualizarContenidoVisualBloque(bloque);
        configurarInteractividadDragResize(bloque);

        listaBloques.add(bloque);
        gridCalendario.add(rootNode, colDia, filaInicio);
        GridPane.setRowSpan(rootNode, filaFin - filaInicio);

        return bloque;
    }

    private void configurarInteractividadDragResize(BloqueTiempo bloque) {
        StackPane nodo = bloque.uiNode;

        nodo.setOnMouseMoved(e -> {
            double y = e.getY();
            double height = nodo.getHeight();
            if (y > height - 10) {
                nodo.setCursor(Cursor.V_RESIZE);
            } else {
                nodo.setCursor(Cursor.HAND);
            }
        });

        nodo.setOnMousePressed(e -> {
            bloqueArrastrado = bloque;

            if (e.isControlDown()) {
                if (bloquesSeleccionados.contains(bloque)) {
                    bloquesSeleccionados.remove(bloque);
                } else {
                    bloquesSeleccionados.add(bloque);
                }
            } else {
                if (!bloquesSeleccionados.contains(bloque)) {
                    bloquesSeleccionados.clear();
                    bloquesSeleccionados.add(bloque);
                }
            }
            actualizarEstadoGlobal();

            startMouseY = e.getScreenY();
            initialStartSlot = bloque.slotInicioSemanal;
            initialEndSlot = bloque.slotFinSemanal;
            dragOffsetSlots = (int) (e.getY() / 14.0);

            if (nodo.getCursor() == Cursor.V_RESIZE) {
                isResizing = true;
                isDragging = false;
            } else {
                isResizing = false;
                isDragging = false;
                e.setDragDetect(true);
            }
            e.consume();
        });

        nodo.setOnDragDetected(e -> {
            if (!isResizing) {
                isDragging = true;

                dragSnapshots.clear();
                for (BloqueTiempo b : bloquesSeleccionados) {
                    dragSnapshots.add(new BloqueSnapshot(b));
                    b.uiNode.setMouseTransparent(true);
                    if (b == bloque) {
                        arrastradoSnapshot = new BloqueSnapshot(b);
                    }
                }

                nodo.startFullDrag();
            }
            e.consume();
        });

        nodo.setOnMouseDragged(e -> {
            if (isResizing) {
                double deltaY = e.getScreenY() - startMouseY;
                int slotsAAnadir = (int) (deltaY / 28.0);
                int nuevoEndSlot = initialEndSlot + slotsAAnadir;

                if (nuevoEndSlot - bloque.slotInicioSemanal < 2) {
                    nuevoEndSlot = bloque.slotInicioSemanal + 2;
                }

                // Calcular el slot maximo permitido para este dia (las 24:00)
                int maxSlotDia = bloque.slotInicioSemanal - (bloque.slotInicioSemanal % 48) + 48;

                // Si el usuario arrastra mas alla de las 24:00, lo topamos en el límite del día
                if (nuevoEndSlot > maxSlotDia) {
                    nuevoEndSlot = maxSlotDia;
                }

                if (nuevoEndSlot != bloque.slotFinSemanal) {
                    BloqueTiempo temp = new BloqueTiempo(bloque.colDia, bloque.slotInicioSemanal, nuevoEndSlot, null, null, null);
                    boolean overlaps = false;
                    for (BloqueTiempo b : listaBloques) {
                        if (b != bloque && b.seSuperpone(temp)) {
                            overlaps = true;
                            break;
                        }
                    }

                    if (!overlaps) {
                        actualizarLimitesBloque(bloque, bloque.colDia, bloque.slotInicioSemanal, nuevoEndSlot);
                        actualizarEstadoSuperposiciones();
                    }
                }
            }
            e.consume();
        });

        nodo.setOnMouseReleased(e -> {
            for (BloqueTiempo b : bloquesSeleccionados) {
                b.uiNode.setMouseTransparent(false);
            }
            nodo.setMouseTransparent(false);

            if (isDragging || isResizing) {
                isDragging = false;
                isResizing = false;
                bloqueArrastrado = null;
                arrastradoSnapshot = null;
                dragSnapshots.clear();
                nodo.setCursor(Cursor.HAND);
                actualizarEstadoSuperposiciones();
                actualizarHorasDeuda(); // <-- SE ACTUALIZAN LAS HORAS AL SOLTAR EL BLOQUE REDIMENSIONADO/ARRASTRADO
            } else if (e.getButton() == MouseButton.PRIMARY) {
                if (!e.isControlDown()) {
                    bloquesSeleccionados.clear();
                    bloquesSeleccionados.add(bloque);
                    actualizarEstadoGlobal();
                }
            }
            e.consume();
        });

        ContextMenu menu = new ContextMenu();
        MenuItem itemBorrar = new MenuItem("🗑 Eliminar Bloque(s)");
        itemBorrar.setOnAction(e -> {
            if (bloquesSeleccionados.contains(bloque)) {
                for (BloqueTiempo b : new ArrayList<>(bloquesSeleccionados)) {
                    gridCalendario.getChildren().remove(b.uiNode);
                    listaBloques.remove(b);
                }
                bloquesSeleccionados.clear();
            } else {
                gridCalendario.getChildren().remove(bloque.uiNode);
                listaBloques.remove(bloque);
            }
            actualizarEstadoGlobal();
            actualizarHorasDeuda(); // <-- SE ACTUALIZA AL ELIMINAR UN BLOQUE
        });
        menu.getItems().add(itemBorrar);

        nodo.setOnContextMenuRequested(e -> {
            if (!bloquesSeleccionados.contains(bloque)) {
                bloquesSeleccionados.clear();
                bloquesSeleccionados.add(bloque);
            }
            actualizarEstadoGlobal();
            menu.show(nodo, e.getScreenX(), e.getScreenY());
        });
    }

    private void moverSeleccionADestino(int targetColDia, int targetFilaInicio) {
        int h1 = HORA_INICIO_VISUAL + (targetFilaInicio / 4);
        int m1 = (targetFilaInicio % 4) * 15;

        int nuevoSlotInicioArrastrado = ((targetColDia - 1) * 48) + (h1 * 2) + (m1 / 30);
        int deltaAbsoluto = nuevoSlotInicioArrastrado - arrastradoSnapshot.slotInicio;

        for (BloqueSnapshot snap : dragSnapshots) {
            int nuevoInicio = snap.slotInicio + deltaAbsoluto;
            int nuevoFin = snap.slotFin + deltaAbsoluto;

            if (nuevoInicio < 0) return;

            int nuevaCol = (nuevoInicio / 48) + 1;
            int nuevaColFin = ((nuevoFin - 1) / 48) + 1;

            if (nuevaCol < 1 || nuevaCol > 7 || nuevaColFin != nuevaCol) return;

            BloqueTiempo temp = new BloqueTiempo(nuevaCol, nuevoInicio, nuevoFin, null, null, null);
            for (BloqueTiempo b : listaBloques) {
                if (!bloquesSeleccionados.contains(b) && b.seSuperpone(temp)) {
                    return;
                }
            }
        }

        for (BloqueSnapshot snap : dragSnapshots) {
            int nuevoInicio = snap.slotInicio + deltaAbsoluto;
            int nuevoFin = snap.slotFin + deltaAbsoluto;
            int nuevaCol = (nuevoInicio / 48) + 1;

            actualizarLimitesBloque(snap.bloque, nuevaCol, nuevoInicio, nuevoFin);
        }
    }

    private void actualizarLimitesBloque(BloqueTiempo bloque, int colDia, int slotInicio, int slotFin) {
        bloque.colDia = colDia;
        bloque.slotInicioSemanal = slotInicio;
        bloque.slotFinSemanal = slotFin;

        int h1 = (slotInicio % 48) / 2;
        int m1 = ((slotInicio % 48) % 2) * 30;
        int h2 = (slotFin % 48) / 2;
        int m2 = ((slotFin % 48) % 2) * 30;
        if (slotFin % 48 == 0) { h2 = 24; m2 = 0; }

        int filaInicio = ((h1 - HORA_INICIO_VISUAL) * 4) + (m1 / 15);
        int filaFin = ((h2 - HORA_INICIO_VISUAL) * 4) + (m2 / 15);

        GridPane.setColumnIndex(bloque.uiNode, colDia);
        GridPane.setRowIndex(bloque.uiNode, filaInicio);
        GridPane.setRowSpan(bloque.uiNode, filaFin - filaInicio);

        actualizarContenidoVisualBloque(bloque);
    }

    private void actualizarContenidoVisualBloque(BloqueTiempo b) {
        b.contentNode.getChildren().clear();

        int h1 = (b.slotInicioSemanal % 48) / 2;
        int m1 = ((b.slotInicioSemanal % 48) % 2) * 30;
        int h2 = (b.slotFinSemanal % 48) / 2;
        int m2 = ((b.slotFinSemanal % 48) % 2) * 30;
        if (b.slotFinSemanal % 48 == 0) { h2 = 24; m2 = 0; }

        String horaTexto = String.format("%02d:%02d - %02d:%02d", h1, m1, h2, m2);
        String cursoNombre = (b.cursoSugerido != null) ? b.cursoSugerido.getNombre() : "Comodín";

        Tooltip infoTooltip = new Tooltip("Hora: " + horaTexto + "\nCurso: " + cursoNombre);
        infoTooltip.setShowDelay(Duration.millis(300));
        Tooltip.install(b.uiNode, infoTooltip);

        Label textoHora = new Label(horaTexto);
        textoHora.setWrapText(true);
        textoHora.getStyleClass().addAll("text-bold", "text-small");
        b.contentNode.getChildren().add(textoHora);

        Label textoCurso = new Label(cursoNombre);
        textoCurso.setWrapText(true);
        textoCurso.getStyleClass().add("text-small");
        if(b.cursoSugerido == null) textoCurso.setStyle("-fx-font-style: italic;");

        b.contentNode.getChildren().add(textoCurso);
    }

    private void actualizarEstadoGlobal() {
        actualizarEstadoSuperposiciones();
        actualizarSeleccionPaleta();
    }

    private void actualizarEstadoSuperposiciones() {
        boolean hayError = false;
        for (BloqueTiempo b : listaBloques) b.superpuesto = false;

        for (int i = 0; i < listaBloques.size(); i++) {
            for (int j = i + 1; j < listaBloques.size(); j++) {
                if (listaBloques.get(i).seSuperpone(listaBloques.get(j))) {
                    listaBloques.get(i).superpuesto = true;
                    listaBloques.get(j).superpuesto = true;
                    hayError = true;
                }
            }
        }
        for (BloqueTiempo b : listaBloques) actualizarColorBloque(b);

        btnGuardar.setDisable(hayError || cmbProfesor.getValue() == null);
        if (hayError) {
            lblEstadoBD.setText("Error: Horarios superpuestos.");
            lblEstadoBD.getStyleClass().setAll("label", "danger");
        }
    }

    private void actualizarColorBloque(BloqueTiempo b) {
        boolean seleccionado = bloquesSeleccionados.contains(b);

        String bgColor = (b.cursoSugerido == null) ? "-color-success-subtle" : "-color-accent-subtle";
        String borderColor = (b.cursoSugerido == null) ? "-color-success-emphasis" : "-color-accent-emphasis";

        if (b.superpuesto) {
            bgColor = "-color-danger-subtle";
            borderColor = "-color-danger-emphasis";
        }

        int borderWidth = 1;
        String extraEffect = "";

        if (seleccionado) {
            borderColor = "-color-fg-default";
            borderWidth = 2;
            extraEffect = "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 8, 0, 0, 0);";
        }

        b.uiNode.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-width: " + borderWidth + "px;" +
                        "-fx-border-radius: 4px;" +
                        "-fx-background-radius: 4px;" +
                        extraEffect
        );

        if (!b.contentNode.getChildren().isEmpty()) {
            Label lblHora = (Label) b.contentNode.getChildren().get(0);
            String textColor = (b.superpuesto) ? "-color-danger-fg" : ((b.cursoSugerido == null) ? "-color-success-fg" : "-color-accent-fg");
            lblHora.setStyle("-fx-text-fill: " + textColor + ";");

            if (b.contentNode.getChildren().size() > 1) {
                Label lblCurso = (Label) b.contentNode.getChildren().get(1);
                if (b.cursoSugerido == null) {
                    lblCurso.setStyle("-fx-text-fill: " + textColor + "; -fx-font-style: italic;");
                } else {
                    lblCurso.setStyle("-fx-text-fill: " + textColor + ";");
                }
            }
        }
    }

    private void cargarProfesores() {
        cmbProfesor.getItems().addAll(teacherDAO.obtenerProfesoresObservable());
        cmbProfesor.setConverter(new StringConverter<Teacher>() {
            @Override public String toString(Teacher t) { return t == null ? "" : t.getNombre() + " " + t.getApellidoPaterno(); }
            @Override public Teacher fromString(String s) { return null; }
        });
        cmbProfesor.setOnAction(e -> procesarSeleccionProfesor(cmbProfesor.getValue()));
    }
}