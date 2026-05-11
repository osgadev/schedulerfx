package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.*;
import com.osgadev.organizadorhorariosfx.util.SessionGlobal;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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
    @FXML private Label lblStatusDetallado;

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

        cmbAnio.getSelectionModel().select(SessionGlobal.getAnioActual());
        cmbEtapa.getSelectionModel().select(SessionGlobal.getEtapaActual());

        cmbAnio.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                SessionGlobal.setAnioActual(newVal);
                cargarDatosDashboard();
            }
        });

        cmbEtapa.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                SessionGlobal.setEtapaActual(newVal);
                cargarDatosDashboard();
            }
        });

        Platform.runLater(this::cargarDatosDashboard);
//        cargarDatosDashboard();
    }

    private void cargarDatosDashboard() {
        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();

        if (anio == null || etapa == null) return;

        // PROFESORES Y CURSOS
        int totalProfes = teacherDAO.contarProfesores();
        int profesSinDisp = teacherDAO.contarProfesoresSinDisponibilidad();
        int totalCursos = courseDAO.contarCursos();

        lblTotalProfesores.setText(String.valueOf(totalProfes));
        lblTotalCursos.setText(String.valueOf(totalCursos));

        if (profesSinDisp > 0) {
            lblProfesoresAlerta.setText("⚠️ " + profesSinDisp + " sin disponibilidad");
            setSemanticClass(lblProfesoresAlerta, "danger");
        } else if (totalProfes > 0) {
            lblProfesoresAlerta.setText("✅ Todos con disponibilidad");
            setSemanticClass(lblProfesoresAlerta, "success");
        } else {
            lblProfesoresAlerta.setText("Catálogo vacío");
            setSemanticClass(lblProfesoresAlerta, "text-muted");
        }

        // INFORMACION GRUPOS Y ALUMNOS
        int totalGrupos = groupDAO.contarGrupos(anio, etapa);
        int totalAlumnos = studentDAO.contarAlumnos(anio, etapa);

        lblTotalGrupos.setText(String.valueOf(totalGrupos));

        //Validar si hay alumnos cargados en bd
        if (totalAlumnos > 0) {
            lblEstadoAlumnos.setText(String.valueOf(totalAlumnos));
            setSemanticClass(lblEstadoAlumnos, "success");
        } else {
            lblEstadoAlumnos.setText("0");
            setSemanticClass(lblEstadoAlumnos, "danger");
        }

        // Grafico de dona
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

                // Usamos la variable del tema para la parte pendiente, así se adapta al modo oscuro
                if (nodoPendientes != null) {
                    nodoPendientes.setStyle("-fx-pie-color: -color-bg-subtle; -fx-border-width: 0;");
                }

                String colorHex;
                if (porcentajeTexto == 0) {
                    colorHex = "#e74c3c"; // Rojo
                } else if (porcentajeTexto < 25) {
                    colorHex = "#e67e22"; // Naranja
                } else if (porcentajeTexto < 50) {
                    colorHex = "#f1c40f"; // Amarillo
                } else if (porcentajeTexto < 75) {
                    colorHex = "#3498db"; // Azul
                } else if (porcentajeTexto < 100) {
                    colorHex = "#2ecc71"; // Verde claro
                } else {
                    colorHex = "#1e8449"; // Verde oscuro
                }

                // Aplicar el color dinámico a la rebanada asignada
                if (nodoAsignadas != null) {
                    nodoAsignadas.setStyle("-fx-pie-color: " + colorHex + "; -fx-border-width: 0;");
                }

                // Limpiamos las clases semánticas previas y aplicamos el color al texto
                lblEstadoHorario.getStyleClass().removeAll("success", "warning", "danger", "text-muted");
                lblEstadoHorario.setStyle("-fx-text-fill: " + colorHex + ";");
            });

            lblEstadoHorario.setText(porcentajeTexto + "%");
            lblDetalleHorario.setText(String.format("%.1f de %.1f hrs", horasAsignadas, horasRequeridas));

        } else {
            // Caso donde no hay horas requeridas
            pieHorarios.setData(FXCollections.observableArrayList(new PieChart.Data("Vacío", 1)));
            Platform.runLater(() -> {
                if(!pieHorarios.getData().isEmpty() && pieHorarios.getData().get(0).getNode() != null) {
                    // Usamos una variable del tema para cuando esté vacío
                    pieHorarios.getData().get(0).getNode().setStyle("-fx-pie-color: -color-border-default; -fx-border-width: 0;");
                }
            });
            lblEstadoHorario.setText("0%");
            lblEstadoHorario.setStyle(""); // Limpia colores estáticos manuales
            setSemanticClass(lblEstadoHorario, "text-muted");
            lblDetalleHorario.setText("0 hrs requeridas");
        }

        // ==========================================
        // 4. CONSTRUCCIÓN DEL DIAGNÓSTICO
        // ==========================================
        StringBuilder diagnostico = new StringBuilder();

        if (totalProfes == 0) diagnostico.append("❌ No hay profesores registrados. Ve a la pestaña 'Profesores'.\n");
        else if (profesSinDisp > 0) diagnostico.append("⚠️ Hay ").append(profesSinDisp).append(" profesor(es) sin disponibilidad configurada.\n");

        if (totalCursos == 0) diagnostico.append("❌ No hay cursos registrados en el catálogo.\n");

        if (totalGrupos == 0) diagnostico.append("ℹ️ Aún no se han armado grupos para el ciclo ").append(anio).append("-").append(etapa).append(".\n");
        else {
            if (totalAlumnos == 0) diagnostico.append("⚠️ Falta importar la lista de alumnos.\n");

            if (horasAsignadas == 0) diagnostico.append("ℹ️ Los grupos están listos. Ve a la pestaña 'Horarios' para comenzar.\n");
            else if (horasAsignadas < horasRequeridas) diagnostico.append("⏳ Horarios en progreso. Faltan ").append(String.format("%.1f", horasRequeridas - horasAsignadas)).append(" horas.\n");
            else diagnostico.append("✅ ¡Todo excelente! El 100% de las horas están asignadas.\n");
        }

        if (diagnostico.length() == 0) diagnostico.append("Todo el sistema está operando correctamente.");

        lblStatusDetallado.setText(diagnostico.toString());
    }

    /**
     * Método ayudante para aplicar clases semánticas de AtlantaFX.
     * Limpia los colores dinámicos directos y las clases anteriores.
     */
    private void setSemanticClass(Label label, String styleClass) {
        label.setStyle(""); // Limpia cualquier estilo inline (como colores directos de la dona)
        label.getStyleClass().removeAll("success", "danger", "warning", "accent", "text-muted");
        label.getStyleClass().add(styleClass);
    }
}