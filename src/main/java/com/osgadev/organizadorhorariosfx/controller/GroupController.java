package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.StudentDAO;
import com.osgadev.organizadorhorariosfx.dao.GroupDAO;
import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.model.Student;
import com.osgadev.organizadorhorariosfx.service.GroupService;
import com.osgadev.organizadorhorariosfx.util.ImportadorExcel;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class GroupController {

    // --- Contenedores Superiores (Estados) ---
    @FXML private HBox panelConsulta;
    @FXML private HBox panelCreacion;

    // --- Inputs Estado Consulta ---
    @FXML private ComboBox<String> cbAnioConsulta;
    @FXML private ComboBox<String> cbEtapaConsulta;

    // --- Inputs Estado Creación ---
    @FXML private TextField txtTotalAlumnos;
    @FXML private ComboBox<String> cbAnioCreacion;
    @FXML private ComboBox<String> cbEtapaCreacion;
    @FXML private Button btnGuardarBD;

    // --- Contenedores Centrales ---
    @FXML private TableView<Group> tablaGrupos;
    @FXML private VBox vistaVaciaVBox;
    @FXML private Label lblMensajeVacio;

    // --- Controles de Navegación y Estadísticas ---
    @FXML private Label lblCursoActual;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private Label lblTotalAlumnos;
    @FXML private Label lblTotalProfesores;
    @FXML private Label lblTotalCursos;
    @FXML private Label lblTotalGrupos;

    // --- Columnas ---
    @FXML private TableColumn<Group, Integer> colNumeroGrupo;
    @FXML private TableColumn<Group, String> colProfesor;
    @FXML private TableColumn<Group, String> colIdGrupo;
    @FXML private TableColumn<Group, Integer> colAlumnos;
    @FXML private TableColumn<Group, String> colRango;

    // --- Lógica de Negocio y Datos ---
    private GroupService groupService = new GroupService();
    private GroupDAO groupDAO = new GroupDAO();
    private StudentDAO studentDAO = new StudentDAO();
    private TeacherDAO teacherDAO = new TeacherDAO();

    private ObservableList<Group> listaBaseGrupos = FXCollections.observableArrayList();
    private FilteredList<Group> gruposFiltrados;
    private List<Course> cursosUnicos = new ArrayList<>();
    private int indiceCursoActual = 0;

    // --- NUEVO: Memoria temporal para los alumnos importados ---
    private List<Student> alumnosImportados = null;

    @FXML
    public void initialize() {
        configurarColumnas();
        llenarComboBoxes();

        // El FilteredList se inicializa envuelto en la lista base
        gruposFiltrados = new FilteredList<>(listaBaseGrupos, p -> true);
        tablaGrupos.setItems(gruposFiltrados);

        // Al abrir la ventana, iniciamos en Modo Consulta
        activarModoConsulta();
    }

    // ==========================================
    // CONFIGURACIÓN INICIAL
    // ==========================================

    private void configurarColumnas() {
        colNumeroGrupo.setCellValueFactory(celda -> {
            int indice = tablaGrupos.getItems().indexOf(celda.getValue());
            return new SimpleIntegerProperty(indice + 1).asObject();
        });

        colIdGrupo.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getIdGrupo()));
        colProfesor.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombreProfesor()));
        colAlumnos.setCellValueFactory(celda -> new SimpleIntegerProperty(celda.getValue().getTamanioGrupo()).asObject());
        colRango.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getRangoTexto()));
    }

    private void llenarComboBoxes() {
        int anioActual = Year.now().getValue();
        ObservableList<String> anios = FXCollections.observableArrayList();
        for (int i = anioActual - 2; i < anioActual + 5; i++) {
            anios.add(String.valueOf(i));
        }
        ObservableList<String> etapas = FXCollections.observableArrayList("1", "2", "3");

        cbAnioConsulta.setItems(anios); cbEtapaConsulta.setItems(etapas);
        cbAnioCreacion.setItems(anios); cbEtapaCreacion.setItems(etapas);

        cbAnioConsulta.getSelectionModel().select(String.valueOf(anioActual));
        cbEtapaConsulta.getSelectionModel().selectFirst();
        cbAnioCreacion.getSelectionModel().select(String.valueOf(anioActual));
        cbEtapaCreacion.getSelectionModel().selectFirst();
    }

    // ==========================================
    // MANEJO DE ESTADOS (VISTAS)
    // ==========================================

    private void activarModoConsulta() {
        panelConsulta.setVisible(true); panelConsulta.setManaged(true);
        panelCreacion.setVisible(false); panelCreacion.setManaged(false);

        listaBaseGrupos.clear();
        mostrarVistaVacia("Selecciona un Año y Etapa para consultar.");
        actualizarEstadisticas();
    }

    @FXML
    private void onActivarModoCreacionClick() {
        panelConsulta.setVisible(false); panelConsulta.setManaged(false);
        panelCreacion.setVisible(true); panelCreacion.setManaged(true);
        btnGuardarBD.setDisable(true);

        // Resetear estado manual o de Excel al entrar al modo creación
        alumnosImportados = null;
        txtTotalAlumnos.setDisable(false);
        txtTotalAlumnos.clear();

        listaBaseGrupos.clear();
        mostrarVistaVacia("Ingresa el número de alumnos o sube un Excel y calcula el borrador.");
        actualizarEstadisticas();
    }

    @FXML
    private void onCancelarCreacionClick() {
        alumnosImportados = null;
        txtTotalAlumnos.setDisable(false);
        activarModoConsulta();
    }

    private void mostrarVistaVacia(String mensaje) {
        vistaVaciaVBox.setVisible(true);
        tablaGrupos.setVisible(false);
        lblMensajeVacio.setText(mensaje);

        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);
        lblCursoActual.setText("Materia: -");
    }

    private void mostrarTabla() {
        vistaVaciaVBox.setVisible(false);
        tablaGrupos.setVisible(true);
    }

    // ==========================================
    // ACCIONES PRINCIPALES
    // ==========================================

    @FXML
    private void onBuscarBDClick() {
        String anio = cbAnioConsulta.getValue();
        String etapa = cbEtapaConsulta.getValue();

        List<Group> recuperados = groupDAO.obtenerPorAnioYEtapa(anio, etapa);

        if (recuperados.isEmpty()) {
            mostrarVistaVacia("No se encontraron grupos para " + anio + " - Etapa " + etapa);
        } else {
            cargarDatosEnTabla(recuperados);
        }
    }

    // --- NUEVO: Cargar lista desde Excel ---
    @FXML
    private void onCargarListaExcelClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Lista de Alumnos");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos Excel (*.xlsx)", "*.xlsx"));

        File archivo = fileChooser.showOpenDialog(tablaGrupos.getScene().getWindow());

        if (archivo != null) {
            try {
                alumnosImportados = ImportadorExcel.leerListaAlumnos(archivo);

                if (alumnosImportados.isEmpty()) {
                    mostrarAlerta("Archivo Vacío", "No se encontraron alumnos válidos en el archivo Excel.", Alert.AlertType.WARNING);
                } else {
                    txtTotalAlumnos.setText(String.valueOf(alumnosImportados.size()));
                    txtTotalAlumnos.setDisable(true); // Bloquear input para evitar inconsistencias
                    mostrarAlerta("Éxito", "Se detectaron y cargaron " + alumnosImportados.size() + " alumnos desde el Excel.", Alert.AlertType.INFORMATION);
                }
            } catch (Exception e) {
                e.printStackTrace();
                mostrarAlerta("Error de Lectura", "No se pudo leer el archivo: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void mostrarAlerta(String titulo, String contenido, Alert.AlertType tipo) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(contenido);
        alerta.showAndWait();
    }

    @FXML
    private void onGenerarBorradorClick() {
        try {
            String anio = cbAnioCreacion.getValue();
            String etapa = cbEtapaCreacion.getEditor().getText().trim();

            if(groupDAO.existenGruposParaCiclo(anio, etapa)){
                Alert alerta = new Alert(Alert.AlertType.WARNING);
                alerta.setTitle("Etapa duplicada");
                alerta.setHeaderText("Ya existen grupos para " + anio + " - Etapa " + etapa);
                alerta.setContentText("No puedes generar nuevos grupos para un ciclo que ya tiene grupos guardados. " +
                        "Por favor elimina los grupos actuales desde el panel de consulta primero");
                alerta.showAndWait();
                return;
            }

            int alumnos = Integer.parseInt(txtTotalAlumnos.getText());
            if (alumnos <= 0) throw new NumberFormatException();

            List<Teacher> profes = teacherDAO.obtenerProfesoresObservable();
            List<Group> borrador = groupService.generarGrupos(alumnos, profes, anio, etapa);

            if (borrador.isEmpty()) {
                mostrarVistaVacia("No hay profesores asignados a cursos.");
                return;
            }

            cargarDatosEnTabla(borrador);
            btnGuardarBD.setDisable(false);

        } catch (NumberFormatException e) {
            mostrarAlerta("Error", "Ingresa un número válido de alumnos o sube un Excel.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void onGuardarEnBDClick() {
        String anio = cbAnioCreacion.getValue();
        String etapa = cbEtapaCreacion.getValue();

        // 1. Guardar primero los grupos
        groupDAO.guardarGruposMasivo(new ArrayList<>(listaBaseGrupos), anio, etapa);

        // 2. Asociar los alumnos a los grupos en memoria y guardarlos
        if (alumnosImportados != null && !alumnosImportados.isEmpty()) {
            for (int i = 0; i < alumnosImportados.size(); i++) {
                int numeroLista = i + 1; // El primer alumno del Excel es el número 1
                Student al = alumnosImportados.get(i);
                al.setNumeroLista(numeroLista);
                al.getGruposAsignados().clear(); // Limpiar por si el usuario le dio click 2 veces

                // Asignarle TODAS las materias que le toquen según su número
                for (Group g : listaBaseGrupos) {
                    if (numeroLista >= g.getRangoInicial() && numeroLista <= g.getRangoFinal()) {
                        al.agregarGrupo(g);
                    }
                }
            }
            // Mandarlos a la BD (El dao guardará al alumno y sus relaciones M:M)
            StudentDAO studentDAO = new StudentDAO();
            studentDAO.guardarAlumnosYRelaciones(alumnosImportados, anio, etapa);
        }

        System.out.println("Grupos y Alumnos (Relacionales) guardados exitosamente en BD.");

        activarModoConsulta();
        cbAnioConsulta.setValue(anio);
        cbEtapaConsulta.setValue(etapa);
        onBuscarBDClick();
    }

    @FXML
    private void onEliminarGruposClick() {
        String anio = cbAnioConsulta.getValue();
        String etapa = cbEtapaConsulta.getValue();

        groupDAO.eliminarPorAnioYEtapa(anio, etapa);
        activarModoConsulta();
    }

    // ==========================================
    // NAVEGACIÓN Y FILTRADO DE TABLA
    // ==========================================

    private void cargarDatosEnTabla(List<Group> datos) {
        listaBaseGrupos.setAll(datos);

        cursosUnicos.clear();
        for (Group g : listaBaseGrupos) {
            if (!cursosUnicos.contains(g.getCurso())) {
                cursosUnicos.add(g.getCurso());
            }
        }

        indiceCursoActual = 0;
        mostrarTabla();
        actualizarFiltroMateria();
        actualizarEstadisticas();
    }

    @FXML
    private void onAnteriorClick() {
        if (indiceCursoActual > 0) {
            indiceCursoActual--;
            actualizarFiltroMateria();
        }
    }

    @FXML
    private void onSiguienteClick() {
        if (indiceCursoActual < cursosUnicos.size() - 1) {
            indiceCursoActual++;
            actualizarFiltroMateria();
        }
    }

    private void actualizarFiltroMateria() {
        if (cursosUnicos.isEmpty()) return;

        Course cursoActual = cursosUnicos.get(indiceCursoActual);

        lblCursoActual.setText("Materia: " + cursoActual.getNombre() +
                " (" + (indiceCursoActual + 1) + "/" + cursosUnicos.size() + ")");

        btnAnterior.setDisable(indiceCursoActual == 0);
        btnSiguiente.setDisable(indiceCursoActual == cursosUnicos.size() - 1);

        gruposFiltrados.setPredicate(grupo -> grupo.getCurso().getId() == cursoActual.getId());
    }

    private void actualizarEstadisticas() {
        if (listaBaseGrupos.isEmpty()) {
            lblTotalAlumnos.setText("0");
            lblTotalProfesores.setText("0");
            lblTotalCursos.setText("0");
            lblTotalGrupos.setText("0");
            return;
        }

        int totalAlumnos = 0;
        List<Integer> idsProfesoresUnicos = new ArrayList<>();

        for (Group g : listaBaseGrupos) {
            if (g.getCurso().getId() == cursosUnicos.get(0).getId()) {
                totalAlumnos += g.getTamanioGrupo();
            }

            if (!idsProfesoresUnicos.contains(g.getProfesor().getId())) {
                idsProfesoresUnicos.add(g.getProfesor().getId());
            }
        }

        lblTotalAlumnos.setText(String.valueOf(totalAlumnos));
        lblTotalProfesores.setText(String.valueOf(idsProfesoresUnicos.size()));
        lblTotalCursos.setText(String.valueOf(cursosUnicos.size()));
        lblTotalGrupos.setText(String.valueOf(listaBaseGrupos.size()));
    }
}