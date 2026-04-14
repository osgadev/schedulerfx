package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.DAO.GroupDAO;
import com.osgadev.organizadorhorariosfx.DAO.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.service.GroupService;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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
    private TeacherDAO teacherDAO = new TeacherDAO(); // DAO de ejemplo para obtener todos los profes

    private ObservableList<Group> listaBaseGrupos = FXCollections.observableArrayList();
    private FilteredList<Group> gruposFiltrados;
    private List<Course> cursosUnicos = new ArrayList<>();
    private int indiceCursoActual = 0;

    @FXML
    public void initialize() {
        configurarColumnas();
        llenarComboBoxes();

        // El FilteredList se inicializa envuelto en la lista base
        gruposFiltrados = new FilteredList<>(listaBaseGrupos, p -> true); // [web:489]
        tablaGrupos.setItems(gruposFiltrados);

        // Al abrir la ventana, iniciamos en Modo Consulta
        activarModoConsulta();
    }

    // ==========================================
    // CONFIGURACIÓN INICIAL
    // ==========================================

    private void configurarColumnas() {
        // El "No." de fila lo calculamos con el índice dinámico del FilteredList
        colNumeroGrupo.setCellValueFactory(celda -> {
            int indice = tablaGrupos.getItems().indexOf(celda.getValue());
            return new SimpleIntegerProperty(indice + 1).asObject();
        });

        colIdGrupo.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getIdGrupo()));
        colProfesor.setCellValueFactory(celda -> new SimpleStringProperty(celda.getValue().getNombreProfesor()));
        colAlumnos.setCellValueFactory(celda -> new SimpleIntegerProperty(celda.getValue().getTamanioGrupo()).asObject());

        // Ahora el rango viene directo del modelo
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

        // Seleccionar valores por defecto [web:491]
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

        listaBaseGrupos.clear(); // Limpiamos la tabla
        mostrarVistaVacia("Selecciona un Año y Etapa para consultar.");
        actualizarEstadisticas();
    }

    @FXML
    private void onActivarModoCreacionClick() {
        panelConsulta.setVisible(false); panelConsulta.setManaged(false);
        panelCreacion.setVisible(true); panelCreacion.setManaged(true);
        btnGuardarBD.setDisable(true); // Se activa hasta que generen el borrador

        listaBaseGrupos.clear();
        mostrarVistaVacia("Ingresa el número de alumnos y calcula el borrador.");
        actualizarEstadisticas();
    }

    @FXML
    private void onCancelarCreacionClick() {
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

            // Obtenemos los profesores desde la BD
            List<Teacher> profes = teacherDAO.obtenerProfesoresObservable();

            // Generamos en memoria
            List<Group> borrador = groupService.generarGrupos(alumnos, profes, anio, etapa);

            if (borrador.isEmpty()) {
                mostrarVistaVacia("No hay profesores asignados a cursos.");
                return;
            }

            cargarDatosEnTabla(borrador);
            btnGuardarBD.setDisable(false); // Ya pueden guardar

        } catch (NumberFormatException e) {
            System.out.println("Error: Ingresa un número válido de alumnos.");
        }
    }

    @FXML
    private void onGuardarEnBDClick() {
        String anio = cbAnioCreacion.getValue();
        String etapa = cbEtapaCreacion.getValue();

        // Convertimos la ObservableList a una ArrayList normal y la enviamos al DAO
        groupDAO.guardarGruposMasivo(new ArrayList<>(listaBaseGrupos), anio, etapa);
        System.out.println("Borrador guardado exitosamente en BD.");

        // Regresamos al modo normal y consultamos lo que acabamos de guardar
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
        activarModoConsulta(); // Limpia la pantalla
    }

    // ==========================================
    // NAVEGACIÓN Y FILTRADO DE TABLA
    // ==========================================

    private void cargarDatosEnTabla(List<Group> datos) {
        listaBaseGrupos.setAll(datos);

        // Extraer qué materias únicas hay en esta lista
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

        // Refrescamos el Predicate para que la tabla solo muestre los grupos del curso actual [web:489]
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
            // El total de alumnos es la suma de los alumnos asignados al primer curso
            // Para no contar doble, solo sumamos los del primer curso de la lista
            if (g.getCurso().getId() == cursosUnicos.get(0).getId()) {
                totalAlumnos += g.getTamanioGrupo();
            }

            // Contar profesores únicos
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
