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
import javafx.scene.paint.Color;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    // ==========================================
    // ETIQUETAS FXML (Vista)
    // ==========================================
    @FXML private Label lblTotalProfesores;
    @FXML private Label lblProfesoresAlerta;
    @FXML private Label lblTotalCursos;

    @FXML private Label lblTotalGrupos;
    @FXML private Label lblEstadoAlumnos;

    // Componentes del Gráfico de Dona
    @FXML private PieChart pieHorarios;
    @FXML private Label lblEstadoHorario;
    @FXML private Label lblDetalleHorario;

    @FXML private Label lblStatusDetallado;

    // Filtros FXML
    @FXML private ComboBox<String> cmbAnio;
    @FXML private ComboBox<String> cmbEtapa;

    // ==========================================
    // INSTANCIAS DE DAOs
    // ==========================================
    private final TeacherDAO teacherDAO = new TeacherDAO();
    private final CourseDAO courseDAO = new CourseDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();
    private final StudentDAO studentDAO = new StudentDAO();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Configurar ComboBoxes de Filtro de Ciclo
        int currentYear = LocalDate.now().getYear();
        cmbAnio.getItems().addAll(String.valueOf(currentYear - 1), String.valueOf(currentYear), String.valueOf(currentYear + 1));
        cmbEtapa.getItems().addAll("1", "2");

        // 2. Cargar los valores desde la Sesión Global
        cmbAnio.getSelectionModel().select(SessionGlobal.getAnioActual());
        cmbEtapa.getSelectionModel().select(SessionGlobal.getEtapaActual());

        // 3. Listeners: Guardar el ciclo en la variable global y recargar datos
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

        // 4. Primera carga diferida para no bloquear la interfaz
        Platform.runLater(this::cargarDatosDashboard);
    }

    private void cargarDatosDashboard() {
        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();

        if (anio == null || etapa == null) return;

        // ==========================================
        // 1. DATOS GLOBALES DEL CATÁLOGO
        // ==========================================
        int totalProfes = teacherDAO.contarProfesores();
        int profesSinDisp = teacherDAO.contarProfesoresSinDisponibilidad();
        int totalCursos = courseDAO.contarCursos();

        lblTotalProfesores.setText(String.valueOf(totalProfes));
        lblTotalCursos.setText(String.valueOf(totalCursos));

        if (profesSinDisp > 0) {
            lblProfesoresAlerta.setText("⚠️ " + profesSinDisp + " sin disponibilidad asignada");
            lblProfesoresAlerta.setTextFill(Color.web("#e74c3c")); // Rojo
        } else if (totalProfes > 0) {
            lblProfesoresAlerta.setText("✅ Todos con disponibilidad");
            lblProfesoresAlerta.setTextFill(Color.web("#27ae60")); // Verde
        } else {
            lblProfesoresAlerta.setText("Catálogo vacío");
            lblProfesoresAlerta.setTextFill(Color.web("#7f8c8d")); // Gris
        }

        // ==========================================
        // 2. DATOS DEL CICLO SELECCIONADO
        // ==========================================
        int totalGrupos = groupDAO.contarGrupos(anio, etapa);
        int totalAlumnos = studentDAO.contarAlumnos(anio, etapa);

        lblTotalGrupos.setText(String.valueOf(totalGrupos));

        if (totalAlumnos > 0) {
            lblEstadoAlumnos.setText(String.valueOf(totalAlumnos));
            lblEstadoAlumnos.setTextFill(Color.web("#27ae60"));
        } else {
            lblEstadoAlumnos.setText("0");
            lblEstadoAlumnos.setTextFill(Color.web("#e74c3c"));
        }

        // ==========================================
        // --- Cálculo de Porcentaje de Horarios con Gráfico de Dona ---
        // ==========================================
        double horasRequeridas = groupDAO.calcularHorasTotalesRequeridas(anio, etapa);
        double horasAsignadas = scheduleDAO.calcularHorasAsignadas(anio, etapa);

        if (horasRequeridas > 0) {
            double horasPendientes = horasRequeridas - horasAsignadas;
            if (horasPendientes < 0) horasPendientes = 0; // Seguridad por si se asignan de más

            double fraccion = horasAsignadas / horasRequeridas;
            if (fraccion > 1.0) fraccion = 1.0;

            int porcentajeTexto = (int) (fraccion * 100);

            // 1. Configurar los datos del PieChart (Dos rebanadas)
            PieChart.Data sliceAsignadas = new PieChart.Data("Asignadas", horasAsignadas);
            PieChart.Data slicePendientes = new PieChart.Data("Pendientes", horasPendientes);

            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(sliceAsignadas, slicePendientes);
            pieHorarios.setData(pieData);

            // 2. Colorear las rebanadas mediante código
            Platform.runLater(() -> {
                Node nodoAsignadas = sliceAsignadas.getNode();
                Node nodoPendientes = slicePendientes.getNode();

                if (nodoPendientes != null) {
                    nodoPendientes.setStyle("-fx-pie-color: #ecf0f1;"); // Gris claro para lo pendiente
                }

                if (nodoAsignadas != null) {
                    if (porcentajeTexto == 100) {
                        nodoAsignadas.setStyle("-fx-pie-color: #27ae60;"); // Verde
                        lblEstadoHorario.setTextFill(Color.web("#27ae60"));
                    } else if (porcentajeTexto > 0) {
                        nodoAsignadas.setStyle("-fx-pie-color: #f39c12;"); // Naranja
                        lblEstadoHorario.setTextFill(Color.web("#f39c12"));
                    } else {
                        nodoAsignadas.setStyle("-fx-pie-color: #e74c3c;"); // Rojo
                        lblEstadoHorario.setTextFill(Color.web("#e74c3c"));
                    }
                }
            });

            // 3. Actualizar Textos
            lblEstadoHorario.setText(porcentajeTexto + "%");
            lblDetalleHorario.setText(String.format("%.1f de %.1f hrs", horasAsignadas, horasRequeridas));

        } else {
            // Si no hay horas requeridas
            pieHorarios.setData(FXCollections.observableArrayList(new PieChart.Data("Vacío", 1)));
            Platform.runLater(() -> {
                if(pieHorarios.getData().size() > 0 && pieHorarios.getData().get(0).getNode() != null) {
                    pieHorarios.getData().get(0).getNode().setStyle("-fx-pie-color: #bdc3c7;"); // Gris oscuro
                }
            });
            lblEstadoHorario.setText("0%");
            lblEstadoHorario.setTextFill(Color.web("#7f8c8d"));
            lblDetalleHorario.setText("0 hrs requeridas");
        }

        // ==========================================
        // 3. CONSTRUCCIÓN DEL DIAGNÓSTICO
        // ==========================================
        StringBuilder diagnostico = new StringBuilder();

        if (totalProfes == 0) {
            diagnostico.append("❌ No hay profesores registrados. Ve a la pestaña 'Profesores'.\n");
        } else if (profesSinDisp > 0) {
            diagnostico.append("⚠️ Hay ").append(profesSinDisp).append(" profesor(es) que no tienen su disponibilidad configurada. Los horarios podrían quedar incompletos o fallar al generar.\n");
        }

        if (totalCursos == 0) {
            diagnostico.append("❌ No hay cursos registrados en el catálogo.\n");
        }

        if (totalGrupos == 0) {
            diagnostico.append("ℹ️ Aún no se han armado grupos para el ciclo ").append(anio).append("-").append(etapa).append(".\n");
        } else {
            if (totalAlumnos == 0) {
                diagnostico.append("⚠️ Falta importar la lista de alumnos (Excel) para verificar el tamaño de los grupos.\n");
            }
            if (horasAsignadas == 0) {
                diagnostico.append("ℹ️ Los grupos están listos. Ve a la pestaña 'Horarios' para comenzar la asignación.\n");
            } else if (horasAsignadas < horasRequeridas) {
                double faltan = horasRequeridas - horasAsignadas;
                diagnostico.append("⏳ Horarios en progreso. Faltan ").append(String.format("%.1f", faltan)).append(" horas por ubicar en el calendario.\n");
            } else {
                diagnostico.append("✅ ¡Todo excelente! El 100% de las horas requeridas ya están asignadas en el calendario.\n");
            }
        }

        if (diagnostico.length() == 0) {
            diagnostico.append("Todo el sistema está operando correctamente.");
        }

        lblStatusDetallado.setText(diagnostico.toString());
    }
}