package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.*;
import com.osgadev.organizadorhorariosfx.util.GlobalSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    @FXML private VBox rootVBox;

    @FXML private Label lblTotalProfesores;
    @FXML private Label lblProfesoresAlerta;
    @FXML private Label lblTotalCursos;
    @FXML private Label lblTotalGrupos;
    @FXML private Label lblEstadoAlumnos;

    @FXML private PieChart pieHorarios;
    @FXML private Label lblEstadoHorario;
    @FXML private Label lblDetalleHorario;

    // Contenedor que reemplaza a lblStatusDetallado
    @FXML private VBox vboxDiagnostico;

    @FXML private ComboBox<String> cmbAnio;
    @FXML private ComboBox<String> cmbEtapa;

    private final TeacherDAO teacherDAO = new TeacherDAO();
    private final CourseDAO courseDAO = new CourseDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        int currentYear = LocalDate.now().getYear();
        cmbAnio.getItems().addAll(String.valueOf(currentYear - 1), String.valueOf(currentYear), String.valueOf(currentYear + 1));
        cmbEtapa.getItems().addAll("1", "2", "3");

        cmbAnio.getSelectionModel().select(GlobalSession.getAnioActual());
        cmbEtapa.getSelectionModel().select(GlobalSession.getEtapaActual());

        cmbAnio.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                GlobalSession.setAnioActual(newVal);
                cargarDatosDashboard();
            }
        });

        cmbEtapa.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                GlobalSession.setEtapaActual(newVal);
                cargarDatosDashboard();
            }
        });

        Platform.runLater(this::cargarDatosDashboard);
    }

    private void cargarDatosDashboard() {
        String anio = GlobalSession.getAnioActual();
        String etapa = GlobalSession.getEtapaActual();

        if (anio == null || etapa == null) return;

        int totalProfes = teacherDAO.contarProfesores();
        int profesSinDisp = teacherDAO.contarProfesoresSinDisponibilidad();
        int totalCursos = courseDAO.contarCursos();

        lblTotalProfesores.setText(String.valueOf(totalProfes));
        lblTotalCursos.setText(String.valueOf(totalCursos));

        if (profesSinDisp > 0) {
            lblProfesoresAlerta.setText(profesSinDisp + " sin disponibilidad");
            lblProfesoresAlerta.setGraphic(getIcon("/images/warning.png"));
            setSemanticClass(lblProfesoresAlerta, "warning");
        } else if (totalProfes > 0) {
            lblProfesoresAlerta.setText("Todos con disponibilidad");
            lblProfesoresAlerta.setGraphic(getIcon("/images/check.png"));
            setSemanticClass(lblProfesoresAlerta, "success");
        } else {
            lblProfesoresAlerta.setText("Catálogo vacío");
            lblProfesoresAlerta.setGraphic(null);
            setSemanticClass(lblProfesoresAlerta, "text-muted");
        }

        int totalGrupos = groupDAO.contarGrupos(anio, etapa);
        int totalAlumnos = studentDAO.contarAlumnos(anio, etapa);

        lblTotalGrupos.setText(String.valueOf(totalGrupos));

        if (totalAlumnos > 0) {
            lblEstadoAlumnos.setText(String.valueOf(totalAlumnos));
            setSemanticClass(lblEstadoAlumnos, "success");
        } else {
            lblEstadoAlumnos.setText("0");
            setSemanticClass(lblEstadoAlumnos, "danger");
        }

        double horasRequeridas = groupDAO.calcularHorasTotalesRequeridas(anio, etapa);
        double horasAsignadas = scheduleDAO.calcularHorasAsignadas(anio, etapa);

        if (horasRequeridas > 0) {
            double horasPendientes = Math.max(0, horasRequeridas - horasAsignadas);
            double fraccion = Math.min(1.0, horasAsignadas / horasRequeridas);
            int porcentajeTexto = (int) Math.round(fraccion * 100);

            PieChart.Data sliceAsignadas = new PieChart.Data("Asignadas", horasAsignadas);
            PieChart.Data slicePendientes = new PieChart.Data("Pendientes", horasPendientes);

            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(sliceAsignadas, slicePendientes);
            pieHorarios.setData(pieData);

            Platform.runLater(() -> {
                Node nodoAsignadas = sliceAsignadas.getNode();
                Node nodoPendientes = slicePendientes.getNode();

                if (nodoPendientes != null) {
                    nodoPendientes.setStyle("-fx-pie-color: -color-bg-subtle; -fx-border-width: 0;");
                }

                String colorHex;
                if (porcentajeTexto == 0) {
                    colorHex = "#e74c3c";
                } else if (porcentajeTexto < 25) {
                    colorHex = "#e67e22";
                } else if (porcentajeTexto < 50) {
                    colorHex = "#f1c40f";
                } else if (porcentajeTexto < 75) {
                    colorHex = "#3498db";
                } else if (porcentajeTexto < 100) {
                    colorHex = "#2ecc71";
                } else {
                    colorHex = "#1e8449";
                }

                if (nodoAsignadas != null) {
                    nodoAsignadas.setStyle("-fx-pie-color: " + colorHex + "; -fx-border-width: 0;");
                }

                lblEstadoHorario.getStyleClass().removeAll("success", "warning", "danger", "text-muted");
                lblEstadoHorario.setStyle("-fx-text-fill: " + colorHex + ";");
            });

            lblEstadoHorario.setText(porcentajeTexto + "%");
            lblDetalleHorario.setText(String.format("%.1f de %.1f hrs", horasAsignadas, horasRequeridas));

        } else {
            pieHorarios.setData(FXCollections.observableArrayList(new PieChart.Data("Vacío", 1)));
            Platform.runLater(() -> {
                if(!pieHorarios.getData().isEmpty() && pieHorarios.getData().get(0).getNode() != null) {
                    pieHorarios.getData().get(0).getNode().setStyle("-fx-pie-color: -color-border-default; -fx-border-width: 0;");
                }
            });
            lblEstadoHorario.setText("0%");
            lblEstadoHorario.setStyle("");
            setSemanticClass(lblEstadoHorario, "text-muted");
            lblDetalleHorario.setText("0 hrs requeridas");
        }

        // ==========================================
        // CONSTRUCCIÓN DEL DIAGNÓSTICO
        // ==========================================
        Platform.runLater(() -> {
            vboxDiagnostico.getChildren().clear();
            boolean hayErrores = false;

            if (totalProfes == 0) {
                // Usando danger.png para error grave
                agregarItemDiagnostico("No hay profesores registrados. Ve a la pestaña 'Profesores'.", "/images/danger.png", "danger");
                hayErrores = true;
            } else if (profesSinDisp > 0) {
                // Usando warning.png para advertencia
                agregarItemDiagnostico("Hay " + profesSinDisp + " profesor(es) sin disponibilidad configurada.", "/images/warning.png", "warning");
                // Usando danger.png para bloqueo de sistema
                agregarItemDiagnostico("Los horarios no pueden generarse hasta que la disponibilidad esté completa.", "/images/danger.png", "danger");
                hayErrores = true;
            }

            if (totalCursos == 0) {
                // Usando danger.png para error grave
                agregarItemDiagnostico("No hay cursos registrados en el catálogo. Ve a la pestaña 'Cursos'", "/images/danger.png", "danger");
                hayErrores = true;
            }

            if (totalGrupos == 0) {
                // Usando warning para información pendiente
                agregarItemDiagnostico("Aún no se han armado grupos para el ciclo " + anio + "-" + etapa + ".", "/images/warning.png", "text-muted");
                hayErrores = true;
            } else {
                if (totalAlumnos == 0) {
                    // Usando warning.png para advertencia
                    agregarItemDiagnostico("Falta importar la lista de alumnos.", "/images/warning.png", "warning");
                    hayErrores = true;
                }

                if (horasAsignadas == 0) {
                    if (profesSinDisp > 0 || totalProfes == 0) {
                        agregarItemDiagnostico("Los grupos están listos.", "/images/check.png", "text-muted");
                    } else {
                        agregarItemDiagnostico("Los grupos están listos. Ve a la pestaña 'Horarios' para comenzar.", "/images/check.png", "text-muted");
                    }
                    hayErrores = true;
                } else if (horasAsignadas < horasRequeridas) {
                    agregarItemDiagnostico("Horarios en progreso. Faltan " + String.format("%.1f", horasRequeridas - horasAsignadas) + " horas.", "/images/check.png", "text-muted");
                    hayErrores = true;
                } else {
                    agregarItemDiagnostico("¡Todo excelente! El 100% de las horas están asignadas.", "/images/check.png", "success");
                }
            }

            if (!hayErrores && horasRequeridas > 0 && horasAsignadas >= horasRequeridas) {
                vboxDiagnostico.getChildren().clear();
                agregarItemDiagnostico("Todo el sistema está operando correctamente.", "/images/check.png", "success");
            }
        });
    }

    private void agregarItemDiagnostico(String mensaje, String rutaIcono, String claseEstilo) {
        Label lblItem = new Label(mensaje);
        lblItem.setWrapText(true);
        lblItem.setGraphic(getIcon(rutaIcono));
        lblItem.setGraphicTextGap(10);
        lblItem.getStyleClass().add(claseEstilo);

        vboxDiagnostico.getChildren().add(lblItem);
    }

    private void setSemanticClass(Label label, String styleClass) {
        label.setStyle("");
        label.getStyleClass().removeAll("success", "danger", "warning", "accent", "text-muted");
        label.getStyleClass().add(styleClass);
    }

    private ImageView getIcon(String path) {
        try {
            URL resource = getClass().getResource(path);
            if (resource != null) {
                ImageView imageView = new ImageView(new Image(resource.toExternalForm()));
                imageView.setFitWidth(18);
                imageView.setFitHeight(18);
                imageView.setPreserveRatio(true);
                return imageView;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}