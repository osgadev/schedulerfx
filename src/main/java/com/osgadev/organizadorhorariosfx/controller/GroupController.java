package com.osgadev.organizadorhorariosfx.controller;

import com.osgadev.organizadorhorariosfx.dao.CourseDAO;
import com.osgadev.organizadorhorariosfx.dao.StudentDAO;
import com.osgadev.organizadorhorariosfx.dao.GroupDAO;
import com.osgadev.organizadorhorariosfx.dao.TeacherDAO;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.model.Student;
import com.osgadev.organizadorhorariosfx.service.GroupService;
import com.osgadev.organizadorhorariosfx.util.ImportadorExcel;
import com.osgadev.organizadorhorariosfx.util.SessionGlobal;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupController {

    @FXML private HBox rootHBox;

    @FXML private Label lblCicloGlobal;
    @FXML private Label lblTotalAlumnos;
    @FXML private Label lblTotalProfesores;
    @FXML private Label lblTotalCursos;
    @FXML private Label lblTotalGrupos;
    @FXML private Button btnNuevoCiclo;
    @FXML private Button btnEliminarCiclo;
    @FXML private Button btnVerAlumnosGlobal;

    @FXML private VBox panelVacio;
    @FXML private VBox panelFormulario;
    @FXML private VBox panelTabla;

    @FXML private VBox cardManual;
    @FXML private VBox cardExcel;
    @FXML private RadioButton rbManual;
    @FXML private RadioButton rbExcel;
    @FXML private TextField txtTotalAlumnos;
    @FXML private Button btnSubirExcel;
    @FXML private Button btnVerExcelLocal;
    private ToggleGroup toggleGroupModo;

    // Componentes del Panel Lateral de Estado
    @FXML private VBox panelAlertaManual;
    @FXML private Button btnCargarExcelRegenerar;
    @FXML private VBox boxAccionesRegenerar;

    @FXML private TableView<Group> tablaGrupos;
    @FXML private TableColumn<Group, Integer> colNumeroGrupo;
    @FXML private TableColumn<Group, String> colProfesor;
    @FXML private TableColumn<Group, String> colIdGrupo;
    @FXML private TableColumn<Group, Integer> colAlumnos;
    @FXML private TableColumn<Group, String> colRango;
    @FXML private TableColumn<Group, Void> colAcciones;

    @FXML private Label lblCursoActual;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private TextField txtBuscarGrupo;

    @FXML private VBox panelDetalleAlumnos;
    @FXML private Label lblTituloDetalle;
    @FXML private Label lblSubtituloDetalle;
    @FXML private VBox boxInfoProfesor;
    @FXML private Label lblNombreProfDetalle;
    @FXML private TextField txtBuscarAlumno;

    @FXML private TableView<Student> tablaAlumnos;
    @FXML private TableColumn<Student, Integer> colNoLista;
    @FXML private TableColumn<Student, String> colMatricula;
    @FXML private TableColumn<Student, String> colNombreAlumno;

    private final GroupService groupService = new GroupService();
    private final GroupDAO groupDAO = new GroupDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final TeacherDAO teacherDAO = new TeacherDAO();
    private final CourseDAO courseDAO = new CourseDAO(); // DAOs de catálogo

    private ObservableList<Group> listaBaseGrupos = FXCollections.observableArrayList();
    private FilteredList<Group> gruposFiltrados;
    private List<Course> cursosUnicos = new ArrayList<>();
    private int indiceCursoActual = 0;

    private List<Student> alumnosImportados = null;

    private ObservableList<Student> listaAlumnosActual = FXCollections.observableArrayList();
    private FilteredList<Student> alumnosFiltrados;

    @FXML
    public void initialize() {
        try {
            String cssPath = getClass().getResource("/css/styles.css").toExternalForm();
            rootHBox.getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.err.println("Error al cargar styles.css");
        }

        toggleGroupModo = new ToggleGroup();
        rbManual.setToggleGroup(toggleGroupModo);
        rbExcel.setToggleGroup(toggleGroupModo);
        rbManual.setSelected(true);

        configurarBuscadoresYOrdenamiento();
        configurarColumnasGrupos();
        configurarColumnasAlumnos();

        cargarContextoGlobal();
    }

    public void cargarContextoGlobal() {
        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();
        lblCicloGlobal.setText("Año: " + anio + " | Etapa: " + etapa);

        List<Group> recuperados = groupDAO.obtenerPorAnioYEtapa(anio, etapa);

        if (recuperados.isEmpty()) {
            activarModoVacio();
        } else {
            cargarDatosLista(recuperados);
            activarModoTabla();
        }
    }

    private void configurarBuscadoresYOrdenamiento() {
        gruposFiltrados = new FilteredList<>(listaBaseGrupos, p -> true);
        SortedList<Group> gruposOrdenados = new SortedList<>(gruposFiltrados);
        gruposOrdenados.comparatorProperty().bind(tablaGrupos.comparatorProperty());
        tablaGrupos.setItems(gruposOrdenados);

        txtBuscarGrupo.textProperty().addListener((obs, oldV, newV) -> {
            actualizarFiltroMateria();
        });

        alumnosFiltrados = new FilteredList<>(listaAlumnosActual, p -> true);
        SortedList<Student> alumnosOrdenados = new SortedList<>(alumnosFiltrados);
        alumnosOrdenados.comparatorProperty().bind(tablaAlumnos.comparatorProperty());
        tablaAlumnos.setItems(alumnosOrdenados);

        txtBuscarAlumno.textProperty().addListener((obs, oldV, newV) -> {
            alumnosFiltrados.setPredicate(alumno -> {
                if (newV == null || newV.trim().isEmpty()) return true;
                String filtro = newV.toLowerCase();
                return alumno.getNombreCompleto().toLowerCase().contains(filtro) ||
                        alumno.getMatricula().toLowerCase().contains(filtro);
            });
        });
    }

    private void configurarColumnasGrupos() {
        colNumeroGrupo.setCellValueFactory(c -> new SimpleIntegerProperty(0).asObject());
        colNumeroGrupo.setCellFactory(col -> new TableCell<Group, Integer>() {
            @Override
            public void updateIndex(int index) {
                super.updateIndex(index);
                if (isEmpty() || index < 0) {
                    setText(null);
                } else {
                    setText(String.valueOf(index + 1));
                }
            }
        });

        colIdGrupo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIdGrupo()));
        colProfesor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreProfesor()));
        colAlumnos.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getTamanioGrupo()).asObject());

        colRango.setCellValueFactory(c -> {
            Group g = c.getValue();
            int baseSize = g.getRangoFinal() - g.getRangoInicial() + 1;
            int currentSize = g.getTamanioGrupo();
            if (currentSize > baseSize) {
                return new SimpleStringProperty(g.getRangoTexto() + " (+" + (currentSize - baseSize) + " extra)");
            }
            return new SimpleStringProperty(g.getRangoTexto());
        });

        colAcciones.setCellFactory(param -> new TableCell<>() {
            private final Button btnVer = new Button("Ver Detalles");
            {
                btnVer.getStyleClass().add("btn-outline-primary");
                btnVer.setStyle("-fx-padding: 3 8 3 8; -fx-font-size: 11px;");
                btnVer.setOnAction(e -> mostrarAlumnosDeGrupo(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnVer);
                if (!empty) {
                    int total = studentDAO.contarAlumnos(SessionGlobal.getAnioActual(), SessionGlobal.getEtapaActual());
                    btnVer.setDisable(total == 0);
                }
            }
        });
    }

    private void configurarColumnasAlumnos() {
        colNoLista.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getNumeroLista()));
        colMatricula.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMatricula()));
        colNombreAlumno.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCompleto()));
    }

    // ==========================================
    // VISTAS Y NAVEGACIÓN
    // ==========================================
    private void mostrarEnPanelDetalle(List<Student> alumnos, String titulo, Group grupoInfo) {
        lblTituloDetalle.setText(titulo);
        lblSubtituloDetalle.setText("Mostrando: " + alumnos.size() + " estudiantes");
        txtBuscarAlumno.clear();

        if (grupoInfo != null) {
            boxInfoProfesor.setVisible(true); boxInfoProfesor.setManaged(true);
            lblNombreProfDetalle.setText(grupoInfo.getNombreProfesor());
        } else {
            boxInfoProfesor.setVisible(false); boxInfoProfesor.setManaged(false);
        }

        listaAlumnosActual.setAll(alumnos);
        activarModoDetalleAlumnos();
    }

    private void mostrarAlumnosDeGrupo(Group grupo) {
        String anio = SessionGlobal.getAnioActual();
        String etapa = SessionGlobal.getEtapaActual();
        Map<String, List<Student>> alumnosEnGrupos = studentDAO.obtenerAlumnosAgrupadosPorBD(anio, etapa);
        List<Student> alumnosMostrar = alumnosEnGrupos.getOrDefault(grupo.getIdGrupo(), new ArrayList<>());
        mostrarEnPanelDetalle(alumnosMostrar, "Alumnos - Grupo " + grupo.getIdGrupo() + " (" + grupo.getCurso().getNombre() + ")", grupo);
    }

    @FXML
    private void onVerListaCompletaClick() {
        if (alumnosImportados != null && !alumnosImportados.isEmpty() && listaBaseGrupos.isEmpty()) {
            mostrarEnPanelDetalle(alumnosImportados, "Vista Previa: Memoria Excel", null);
        } else {
            String anio = SessionGlobal.getAnioActual();
            String etapa = SessionGlobal.getEtapaActual();
            List<Student> alumnosBD = studentDAO.obtenerPorAnioYEtapa(anio, etapa);
            mostrarEnPanelDetalle(alumnosBD, "Lista de alumnos", null);
        }
    }

    @FXML
    private void onVolverDeDetalleClick() {
        panelDetalleAlumnos.setVisible(false);
        if (listaBaseGrupos.isEmpty() && (alumnosImportados == null || alumnosImportados.isEmpty())) {
            activarModoVacio();
        } else if (listaBaseGrupos.isEmpty()) {
            panelFormulario.setVisible(true);
        } else {
            panelTabla.setVisible(true);
        }
    }

    // ==========================================
    // GENERACIÓN Y ELIMINACIÓN DE CICLOS (TODO O NADA)
    // ==========================================
    @FXML
    private void onGenerarYGuardarClick() {
        try {
            String anio = SessionGlobal.getAnioActual();
            String etapa = SessionGlobal.getEtapaActual();
            int alumnos = 0;

            if (rbManual.isSelected()) alumnos = Integer.parseInt(txtTotalAlumnos.getText());
            else if (rbExcel.isSelected()) {
                if (alumnosImportados == null || alumnosImportados.isEmpty()) {
                    mostrarAlerta("Error", "Carga un archivo Excel primero.", Alert.AlertType.WARNING);
                    return;
                }
                alumnos = alumnosImportados.size();
            }

            if (alumnos <= 0) throw new NumberFormatException();

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar");
            confirm.setHeaderText("¿Generar la estructura de grupos para este ciclo?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            List<Teacher> profes = teacherDAO.obtenerProfesoresObservable();
            List<Group> gruposGenerados = groupService.generarGrupos(alumnos, profes, anio, etapa);

            if (gruposGenerados.isEmpty()) {
                mostrarAlerta("Error", "Catálogo de profesores incompleto.", Alert.AlertType.WARNING);
                return;
            }

            groupDAO.guardarGruposMasivo(new ArrayList<>(gruposGenerados), anio, etapa);

            if (alumnosImportados != null && !alumnosImportados.isEmpty()) {
                for (Student al : alumnosImportados) {
                    al.getGruposAsignados().clear();
                    for (Group g : gruposGenerados) {
                        if (al.getNumeroLista() >= g.getRangoInicial() && al.getNumeroLista() <= g.getRangoFinal()) {
                            al.agregarGrupo(g);
                        }
                    }
                }
                studentDAO.guardarAlumnosYRelaciones(alumnosImportados, anio, etapa);
            }

            limpiarFormularioCreacion();
            mostrarAlerta("Éxito", "Estructura Base generada correctamente.", Alert.AlertType.INFORMATION);
            cargarContextoGlobal();

        } catch (NumberFormatException e) {
            mostrarAlerta("Error", "Número inválido.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void onEliminarCicloClick() {
        if (new Alert(Alert.AlertType.CONFIRMATION, "CUIDADO: ¿Eliminar TODO EL CICLO (Alumnos y Grupos)?\nEsta acción es irreversible.").showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            String a = SessionGlobal.getAnioActual(), e = SessionGlobal.getEtapaActual();
            studentDAO.eliminarAlumnosYRelacionesMasivo(a, e);
            groupDAO.eliminarPorAnioYEtapa(a, e);
            limpiarFormularioCreacion();
            cargarContextoGlobal();
        }
    }

    // ==========================================
    // LÓGICA DE REGENERACIÓN (TARJETA ESTADO)
    // ==========================================

    @FXML
    private void onCargarExcelBannerClick() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File archivo = fc.showOpenDialog(rootHBox.getScene().getWindow());

        if (archivo != null) {
            try {
                alumnosImportados = ImportadorExcel.leerListaAlumnos(archivo);
                if (alumnosImportados.isEmpty()) {
                    mostrarAlerta("Error", "El archivo Excel está vacío o no es válido.", Alert.AlertType.WARNING);
                    return;
                }

                for (int i = 0; i < alumnosImportados.size(); i++) alumnosImportados.get(i).setNumeroLista(i + 1);

                btnCargarExcelRegenerar.setText("✅ Memoria Cargada");
                btnCargarExcelRegenerar.setDisable(true); // Bloquear botón principal
                boxAccionesRegenerar.setVisible(true);
                boxAccionesRegenerar.setManaged(true);
            } catch (Exception e) {
                mostrarAlerta("Error", "Ocurrió un error al leer el Excel.", Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    private void onVerListaBannerClick() {
        if (alumnosImportados != null && !alumnosImportados.isEmpty()) {
            mostrarEnPanelDetalle(alumnosImportados, "Vista Previa: Memoria Excel", null);
        }
    }

    @FXML
    private void onGenerarBannerClick() {
        if (alumnosImportados == null || alumnosImportados.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Regenerar Ciclo");
        confirm.setHeaderText("Se regenerarán los grupos basándose en los " + alumnosImportados.size() + " alumnos del Excel.");
        confirm.setContentText("Esto reemplazará la estructura actual. ¿Deseas continuar?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            String anio = SessionGlobal.getAnioActual();
            String etapa = SessionGlobal.getEtapaActual();

            List<Teacher> profes = teacherDAO.obtenerProfesoresObservable();
            List<Group> gruposGenerados = groupService.generarGrupos(alumnosImportados.size(), profes, anio, etapa);

            if (gruposGenerados.isEmpty()) {
                mostrarAlerta("Error", "Catálogo de profesores incompleto.", Alert.AlertType.WARNING);
                return;
            }

            groupDAO.eliminarPorAnioYEtapa(anio, etapa);
            groupDAO.guardarGruposMasivo(new ArrayList<>(gruposGenerados), anio, etapa);

            for (Student al : alumnosImportados) {
                al.getGruposAsignados().clear();
                for (Group g : gruposGenerados) {
                    if (al.getNumeroLista() >= g.getRangoInicial() && al.getNumeroLista() <= g.getRangoFinal()) {
                        al.agregarGrupo(g);
                    }
                }
            }
            studentDAO.guardarAlumnosYRelaciones(alumnosImportados, anio, etapa);

            mostrarAlerta("Éxito", "La estructura se ha regenerado correctamente con la lista de alumnos.", Alert.AlertType.INFORMATION);
            limpiarFormularioCreacion();
            cargarContextoGlobal();
        }
    }

    // ==========================================
    // CARGA DE ARCHIVOS Y COMPORTAMIENTO UI
    // ==========================================

    private void limpiarFormularioCreacion() {
        alumnosImportados = null;
        txtTotalAlumnos.clear();
        btnSubirExcel.setText("📁 Seleccionar Archivo");
        btnVerExcelLocal.setVisible(false);
        btnVerExcelLocal.setManaged(false);
        rbManual.setSelected(true);
        txtTotalAlumnos.setDisable(false);
        btnSubirExcel.setDisable(true);
        // Reset Banner Estado
        if (btnCargarExcelRegenerar != null) {
            btnCargarExcelRegenerar.setText("📁 Cargar Archivo Excel");
            btnCargarExcelRegenerar.setDisable(false);
        }
        if (boxAccionesRegenerar != null) {
            boxAccionesRegenerar.setVisible(false);
            boxAccionesRegenerar.setManaged(false);
        }
    }

    @FXML
    private void onNuevoCicloClick() {
        limpiarFormularioCreacion();
        activarModoFormulario();
    }

    @FXML
    private void onCancelarCreacionClick() {
        limpiarFormularioCreacion();
        cargarContextoGlobal();
    }

    @FXML private void onSeleccionarCardManual() { rbManual.setSelected(true); txtTotalAlumnos.setDisable(false); btnSubirExcel.setDisable(true); }
    @FXML private void onSeleccionarCardExcel() { rbExcel.setSelected(true); txtTotalAlumnos.setDisable(true); txtTotalAlumnos.clear(); btnSubirExcel.setDisable(false); }

    @FXML
    private void onCargarListaExcelClick() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File archivo = fc.showOpenDialog(rootHBox.getScene().getWindow());

        if (archivo != null) {
            try {
                alumnosImportados = ImportadorExcel.leerListaAlumnos(archivo);
                if (alumnosImportados.isEmpty()) return;
                for (int i = 0; i < alumnosImportados.size(); i++) alumnosImportados.get(i).setNumeroLista(i + 1);

                btnSubirExcel.setText("✅ Archivo Cargado");
                btnVerExcelLocal.setVisible(true);
                btnVerExcelLocal.setManaged(true);
            } catch (Exception e) {}
        }
    }

    // ==========================================
    // REFRESCADO Y FILTRADO VISUAL
    // ==========================================
    @FXML private void onAnteriorClick() { if (indiceCursoActual > 0) { indiceCursoActual--; actualizarFiltroMateria(); } }
    @FXML private void onSiguienteClick() { if (indiceCursoActual < cursosUnicos.size() - 1) { indiceCursoActual++; actualizarFiltroMateria(); } }

    private void actualizarFiltroMateria() {
        if (cursosUnicos.isEmpty()) return;
        Course c = cursosUnicos.get(indiceCursoActual);

        lblCursoActual.setText(c.getNombre() + " (" + (indiceCursoActual + 1) + "/" + cursosUnicos.size() + ")");

        btnAnterior.setDisable(indiceCursoActual == 0);
        btnSiguiente.setDisable(indiceCursoActual == cursosUnicos.size() - 1);

        String textoBuscar = txtBuscarGrupo.getText() != null ? txtBuscarGrupo.getText().toLowerCase() : "";

        gruposFiltrados.setPredicate(g -> {
            if (g.getCurso().getId() != c.getId()) return false;
            if (textoBuscar.isEmpty()) return true;
            return g.getNombreProfesor().toLowerCase().contains(textoBuscar) ||
                    g.getIdGrupo().toLowerCase().contains(textoBuscar);
        });
        tablaGrupos.refresh();
    }

    private void cargarDatosLista(List<Group> datos) {
        listaBaseGrupos.setAll(datos);
        cursosUnicos = listaBaseGrupos.stream()
                .map(Group::getCurso)
                .distinct()
                .sorted(Comparator.comparingInt(Course::getId))
                .collect(Collectors.toList());

        indiceCursoActual = 0; txtBuscarGrupo.clear(); actualizarFiltroMateria(); actualizarEstadisticas();
    }

    private void actualizarEstadisticas() {
        // Valores dinámicos del ciclo actual (Alumnos y Grupos)
        int tAlumnos = 0;
        if (!listaBaseGrupos.isEmpty() && !cursosUnicos.isEmpty()) {
            tAlumnos = listaBaseGrupos.stream()
                    .filter(g -> g.getCurso().getId() == cursosUnicos.get(0).getId())
                    .mapToInt(Group::getTamanioGrupo).sum();
        } else if (alumnosImportados != null) {
            tAlumnos = alumnosImportados.size();
        } else {
            tAlumnos = studentDAO.contarAlumnos(SessionGlobal.getAnioActual(), SessionGlobal.getEtapaActual());
        }

        lblTotalAlumnos.setText(String.valueOf(tAlumnos));
        lblTotalGrupos.setText(String.valueOf(listaBaseGrupos.size()));

        // Valores estáticos del catálogo maestro (Profesores y Materias siempre se muestran correctos usando la BD)
        try {
            lblTotalProfesores.setText(String.valueOf(teacherDAO.obtenerProfesoresObservable().size()));
            // Aquí usamos el método optimizado del CourseDAO
            lblTotalCursos.setText(String.valueOf(courseDAO.contarCursos()));
        } catch (Exception e) {
            System.err.println("No se pudieron cargar métricas estáticas del catálogo.");
        }
    }

    private void activarModoVacio() {
        panelVacio.setVisible(true); panelFormulario.setVisible(false); panelTabla.setVisible(false); panelDetalleAlumnos.setVisible(false);
        btnNuevoCiclo.setVisible(true); btnEliminarCiclo.setVisible(false);
        btnVerAlumnosGlobal.setVisible(false); btnVerAlumnosGlobal.setManaged(false);
        if(panelAlertaManual != null) { panelAlertaManual.setVisible(false); panelAlertaManual.setManaged(false); }

        listaBaseGrupos.clear();
        cursosUnicos.clear(); // Limpiamos caché interno
        actualizarEstadisticas();
    }

    private void activarModoFormulario() {
        panelVacio.setVisible(false); panelFormulario.setVisible(true); panelTabla.setVisible(false); panelDetalleAlumnos.setVisible(false);
        btnNuevoCiclo.setVisible(false); btnEliminarCiclo.setVisible(false);
        btnVerAlumnosGlobal.setVisible(false); btnVerAlumnosGlobal.setManaged(false);
        if(panelAlertaManual != null) { panelAlertaManual.setVisible(false); panelAlertaManual.setManaged(false); }

        txtTotalAlumnos.clear();
        listaBaseGrupos.clear();
        cursosUnicos.clear();
        actualizarEstadisticas();
    }

    private void activarModoTabla() {
        panelVacio.setVisible(false); panelFormulario.setVisible(false); panelTabla.setVisible(true); panelDetalleAlumnos.setVisible(false);
        btnNuevoCiclo.setVisible(false); btnEliminarCiclo.setVisible(true);

        int totalAlumnos = studentDAO.contarAlumnos(SessionGlobal.getAnioActual(), SessionGlobal.getEtapaActual());

        if (totalAlumnos == 0) {
            panelAlertaManual.setVisible(true);
            panelAlertaManual.setManaged(true);
            btnCargarExcelRegenerar.setText("📁 Cargar Archivo Excel");
            btnCargarExcelRegenerar.setDisable(false);
            boxAccionesRegenerar.setVisible(false);
            boxAccionesRegenerar.setManaged(false);

            btnVerAlumnosGlobal.setVisible(false);
            btnVerAlumnosGlobal.setManaged(false);
        } else {
            panelAlertaManual.setVisible(false);
            panelAlertaManual.setManaged(false);

            btnVerAlumnosGlobal.setVisible(true);
            btnVerAlumnosGlobal.setManaged(true);
            btnVerAlumnosGlobal.setDisable(false);
        }
    }

    private void activarModoDetalleAlumnos() {
        panelVacio.setVisible(false); panelFormulario.setVisible(false); panelTabla.setVisible(false); panelDetalleAlumnos.setVisible(true);
    }

    private void mostrarAlerta(String titulo, String contenido, Alert.AlertType tipo) {
        Alert alerta = new Alert(tipo); alerta.setTitle(titulo); alerta.setHeaderText(null); alerta.setContentText(contenido); alerta.showAndWait();
    }
}