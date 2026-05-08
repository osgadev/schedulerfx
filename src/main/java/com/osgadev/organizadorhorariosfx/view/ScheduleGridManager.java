package com.osgadev.organizadorhorariosfx.view;

import com.osgadev.organizadorhorariosfx.dao.AvailabilityDAO;
import com.osgadev.organizadorhorariosfx.dto.AssignedSession;
import com.osgadev.organizadorhorariosfx.dto.GroupState;
import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.service.ManualAssignmentManager;
import com.osgadev.organizadorhorariosfx.service.OccupationMap;
import com.osgadev.organizadorhorariosfx.service.ScheduleService;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.function.Consumer;

public class ScheduleGridManager {

    private final GridPane gridCalendario;
    private final ManualAssignmentManager assignmentManager;
    private final ScheduleService scheduleService;
    private final OccupationMap occupationMap;
    private final AvailabilityDAO availabilityDAO;
    private final Label lblHorasRestantes;

    // Callbacks para comunicar al controlador
    private final Runnable onHorarioModificado;
    private final Consumer<GroupState> onGrupoExtraidoDelGrid;

    private List<AssignedSession> horarioGenerado;
    private Pane[][] matrizCeldasReceptoras;
    private VBox fantasmaDrag;
    private boolean esPosicionValida = false;

    private final int HORA_INICIO = 7;
    private final int HORA_FIN = 22;

    public ScheduleGridManager(GridPane gridCalendario,
                               ManualAssignmentManager assignmentManager,
                               ScheduleService scheduleService,
                               OccupationMap occupationMap,
                               AvailabilityDAO availabilityDAO,
                               Label lblHorasRestantes,
                               Runnable onHorarioModificado,
                               Consumer<GroupState> onGrupoExtraidoDelGrid) {
        this.gridCalendario = gridCalendario;
        this.assignmentManager = assignmentManager;
        this.scheduleService = scheduleService;
        this.occupationMap = occupationMap;
        this.availabilityDAO = availabilityDAO;
        this.lblHorasRestantes = lblHorasRestantes;
        this.onHorarioModificado = onHorarioModificado;
        this.onGrupoExtraidoDelGrid = onGrupoExtraidoDelGrid;
    }

    public void setHorarioGenerado(List<AssignedSession> horarioGenerado) {
        this.horarioGenerado = horarioGenerado;
    }

    public void construirTablaBase() {
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

                            if(lblHorasRestantes != null) lblHorasRestantes.setText(resultadoValidacion.getMensaje());
                        }
                    }
                    event.consume();
                });

                celda.setOnDragExited(event -> {
                    if (fantasmaDrag != null) fantasmaDrag.setVisible(false);
                    GroupState grupoSel = assignmentManager.getGrupoSeleccionado();
                    if (grupoSel != null && lblHorasRestantes != null) {
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

                        Platform.runLater(onHorarioModificado); // Avisa al controlador
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

    public void pintarBloques(List<AssignedSession> sesiones, boolean showCurso, boolean showProf, boolean showAlumnos, boolean showId) {
        Random rand = new Random();
        Map<Integer, String> coloresPorCurso = new HashMap<>();
        Map<AssignedSession, ScheduleLayoutHelper.PosicionVisual> layout = ScheduleLayoutHelper.calcularLayoutCartas(sesiones);

        for (AssignedSession s : sesiones) {
            Group g = s.getGrupo();
            int idCurso = g.getCurso().getId();
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

            VBox caja = ScheduleUIFactory.crearTarjetaSesionVisual(g, hex, textoHora, showCurso, showProf, showAlumnos, showId);

            // Layout (solapamientos)
            ScheduleLayoutHelper.PosicionVisual pos = layout.get(s);
            if(pos != null) {
                ColumnConstraints colObj = gridCalendario.getColumnConstraints().get(s.getColumnaDia());
                double anchoTotal = colObj.getMinWidth();
                double anchoCarta = anchoTotal / pos.totalColumnas;
                caja.setMinWidth(anchoCarta - 2);
                caja.setMaxWidth(anchoCarta - 2);
                caja.setTranslateX(pos.indiceColumna * anchoCarta);
            }

            // Click derecho: Eliminar
            ContextMenu menu = new ContextMenu();
            MenuItem itemEliminar = new MenuItem("Eliminar clase y reembolsar horas");
            itemEliminar.setOnAction(e -> {
                horarioGenerado.remove(s);
                int duracionBloques = s.getSpanFilas();
                occupationMap.eliminarClase(s.getSlotInicioSemanal(), duracionBloques, s.getGrupo());
                assignmentManager.reembolsarHorasAGrupo(s.getGrupo().getIdGrupo(), duracionBloques / 2.0);
                Platform.runLater(onHorarioModificado); // Avisa al controlador
            });
            menu.getItems().add(itemEliminar);
            caja.setOnContextMenuRequested(e -> menu.show(caja, e.getScreenX(), e.getScreenY()));

            // Arrastrar tarjeta para moverla (AQUÍ SE APLICÓ LA CORRECCIÓN)
            caja.setOnDragDetected(event -> {
                Dragboard db = caja.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent clipboardContent = new ClipboardContent();
                double horas = s.getSpanFilas() / 2.0;
                clipboardContent.putString(String.valueOf(horas));
                db.setContent(clipboardContent);

                // ACTUALIZACIÓN SÍNCRONA DEL ESTADO DE LOS DATOS
                GroupState eg = assignmentManager.buscarEstadoPorId(s.getGrupo().getIdGrupo());
                horarioGenerado.remove(s);
                int duracionBloques = s.getSpanFilas();
                occupationMap.eliminarClase(s.getSlotInicioSemanal(), duracionBloques, s.getGrupo());

                if (eg != null) {
                    eg.reembolsarHoras(horas);
                    assignmentManager.setGrupoSeleccionado(eg);
                    onGrupoExtraidoDelGrid.accept(eg); // Avisa al controlador para actualizar Comboboxes
                }

                // OCULTACIÓN DE UI DIFERIDA PARA EVITAR QUE SE CANCELE EL DRAG EVENT
                Platform.runLater(() -> {
                    caja.setVisible(false);
                    aplicarModoFantasmaATarjetas(true);
                });

                event.consume();
            });

            caja.setOnDragDone(event -> {
                if (event.getTransferMode() == null) {
                    double horas = s.getSpanFilas() / 2.0;
                    GroupState grupoSel = assignmentManager.getGrupoSeleccionado();
                    if (grupoSel != null) grupoSel.agregarHoras(horas);
                    horarioGenerado.add(s);
                    occupationMap.registrarClase(s.getSlotInicioSemanal(), s.getSpanFilas(), s.getGrupo());
                }
                Platform.runLater(onHorarioModificado);
            });

            gridCalendario.add(caja, s.getColumnaDia(), s.getFilaHora() + 1);
            GridPane.setRowSpan(caja, s.getSpanFilas());
        }
    }

    public void aplicarModoFantasmaATarjetas(boolean activar) {
        gridCalendario.getChildren().forEach(nodo -> {
            if (nodo instanceof VBox && nodo != fantasmaDrag && !nodo.getProperties().containsKey("esSugerencia")) {
                nodo.setMouseTransparent(activar);
                nodo.setOpacity(activar ? 0.15 : 1.0);
            }
        });
    }

    public void iluminarDisponibilidadProfesor(Teacher profe) {
        if (matrizCeldasReceptoras == null || profe == null) return;

        gridCalendario.getChildren().removeIf(nodo -> nodo instanceof VBox && nodo.getProperties().containsKey("esSugerencia"));

        for (int col = 1; col <= 7; col++) {
            for (int fila = 1; fila < matrizCeldasReceptoras[col].length; fila++) {
                Pane c = matrizCeldasReceptoras[col][fila];
                if (c != null) {
                    c.setStyle("-fx-background-color: transparent;");
                    c.getProperties().remove("estiloOriginal");
                    c.getProperties().remove("esValido");
                    c.setOpacity(1.0);
                }
            }
        }

        List<Availability> disponibilidad = availabilityDAO.getByTeacher(profe);
        for (Availability a : disponibilidad) {
            for (int slot = a.getStartSlot(); slot < a.getEndSlot(); slot++) {
                int col = (slot / 48) + 1;
                int hInicio = (slot % 48) / 2;
                int mInicio = ((slot % 48) % 2) * 30;
                int filaVisual = ((hInicio - HORA_INICIO) * 2) + (mInicio / 30) + 1;

                if (col >= 1 && col <= 7 && filaVisual >= 1 && filaVisual < matrizCeldasReceptoras[col].length) {
                    Pane celda = matrizCeldasReceptoras[col][filaVisual];
                    if (celda != null) ScheduleUIFactory.aplicarEfectoCristal(celda);
                }
            }
        }
    }

    public void pintarSugerencias(Teacher profe, boolean mostrarSugerencias) {
        if (!mostrarSugerencias || profe == null || matrizCeldasReceptoras == null) return;
        List<Availability> disponibilidad = availabilityDAO.getByTeacher(profe);

        for (Availability a : disponibilidad) {
            boolean esSugerenciaFija = (a.getCursoSugerido() != null);
            int startSlot = a.getStartSlot();
            int endSlot = a.getEndSlot();
            int duracionSlots = endSlot - startSlot;

            int col = (startSlot / 48) + 1;
            int hInicio = (startSlot % 48) / 2;
            int mInicio = ((startSlot % 48) % 2) * 30;
            int filaVisual = ((hInicio - HORA_INICIO) * 2) + (mInicio / 30) + 1;

            if (col < 1 || col > 7 || filaVisual < 1 || filaVisual >= matrizCeldasReceptoras[col].length) continue;

            if (esSugerenciaFija) {
                int hFin = ((startSlot % 48) + duracionSlots) / 2;
                int mFin = (((startSlot % 48) + duracionSlots) % 2) * 30;
                String textoHora = String.format("%02d:%02d - %02d:%02d", hInicio, mInicio, hFin, mFin);

                VBox sugerencia = ScheduleUIFactory.crearSugerenciaFijaVisual(a.getCursoSugerido().getNombre(), textoHora, duracionSlots);
                gridCalendario.add(sugerencia, col, filaVisual);
                GridPane.setRowSpan(sugerencia, duracionSlots);
                sugerencia.toBack();
            } else {
                for (int slot = startSlot; slot < endSlot; slot++) {
                    int fVis = (((slot % 48) / 2 - HORA_INICIO) * 2) + (((slot % 48) % 2) * 30 / 30) + 1;
                    if (fVis >= 1 && fVis < matrizCeldasReceptoras[col].length) {
                        Pane celda = matrizCeldasReceptoras[col][fVis];
                        if (celda != null && celda.getProperties().containsKey("esValido")) {
                            ScheduleUIFactory.aplicarEfectoSugerenciaLibre(celda);
                        }
                    }
                }
            }
        }
    }
}