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
import com.osgadev.organizadorhorariosfx.util.ExcelImporter;
import com.osgadev.organizadorhorariosfx.util.GlobalSession;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    private final CourseDAO courseDAO = new CourseDAO();

    private ObservableList<Group> listaBaseGrupos = FXCollections.observableArrayList();
    private FilteredList<Group> gruposFiltrados;
    private List<Course> cursosUnicos = new ArrayList<>();
    private int indiceCursoActual = 0;

    private List<Student> alumnosImportados = null;

    private ObservableList<Student> listaAlumnosActual = FXCollections.observableArrayList();
    private FilteredList<Student> alumnosFiltrados;

    private int totalAlumnosEstructura = 0;

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
        configurarMenuContextualTabla();

        cargarContextoGlobal();
    }

    public void cargarContextoGlobal() {
        String anio = GlobalSession.getAnioActual();
        String etapa = GlobalSession.getEtapaActual();
        lblCicloGlobal.setText("Año: " + anio + " | Etapa: " + etapa);

        List<Group> recuperados = groupDAO.obtenerPorAnioYEtapa(anio, etapa);

        if (recuperados.isEmpty()) {
            totalAlumnosEstructura = 0;
            activarModoVacio();
        } else {
            totalAlumnosEstructura = calcularTotalAlumnosDesdeGrupos(recuperados);
            cargarDatosLista(recuperados);
            activarModoTabla();
        }
    }

    private int calcularTotalAlumnosDesdeGrupos(List<Group> grupos) {
        if (grupos == null || grupos.isEmpty()) return 0;

        int cursoIdBase = grupos.get(0).getCurso().getId();

        return grupos.stream()
                .filter(g -> g.getCurso().getId() == cursoIdBase)
                .mapToInt(Group::getTamanioGrupo)
                .sum();
    }

    private void configurarMenuContextualTabla() {
        tablaGrupos.setRowFactory(tv -> {
            TableRow<Group> row = new TableRow<>();

            ContextMenu contextMenu = new ContextMenu();

            MenuItem itemAgregarExtra = new MenuItem("Añadir grupo extra");
            itemAgregarExtra.setOnAction(e -> {
                Group grupoSeleccionado = row.getItem();
                if (grupoSeleccionado != null) {
                    onAgregarGrupoExtra(grupoSeleccionado);
                }
            });

            MenuItem itemEliminarGrupo = new MenuItem("Eliminar grupo");
            itemEliminarGrupo.setOnAction(e -> {
                Group grupoSeleccionado = row.getItem();
                if (grupoSeleccionado != null) {
                    onEliminarGrupo(grupoSeleccionado);
                }
            });

            contextMenu.getItems().addAll(itemAgregarExtra, itemEliminarGrupo);

            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            return row;
        });
    }

    private void onAgregarGrupoExtra(Group grupoBase) {
        String anio = GlobalSession.getAnioActual();
        String etapa = GlobalSession.getEtapaActual();

        int totalAlumnos = totalAlumnosEstructura;

        if (totalAlumnos <= 0) {
            List<Group> gruposActualesTmp = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
            totalAlumnos = calcularTotalAlumnosDesdeGrupos(gruposActualesTmp);
            totalAlumnosEstructura = totalAlumnos;
        }

        if (totalAlumnos <= 0) {
            mostrarAlerta("Error", "No hay total de alumnos disponible para recalcular la estructura.", Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar");
        confirm.setHeaderText("Se añadirá un grupo extra para la materia seleccionada.");
        confirm.setContentText(
                "Profesor: " + grupoBase.getNombreProfesor() +
                        "\nMateria: " + grupoBase.getCurso().getNombre() +
                        "\n\nSe regenerará la estructura completa de grupos para recalcular tamaños y rangos.\n¿Deseas continuar?"
        );

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            List<Teacher> profesores = teacherDAO.obtenerProfesoresObservable();
            List<Group> gruposActuales = groupDAO.obtenerPorAnioYEtapa(anio, etapa);

            List<Group> gruposRegenerados = groupService.generarGruposConExtras(
                    totalAlumnos,
                    profesores,
                    gruposActuales,
                    grupoBase,
                    anio,
                    etapa
            );

            if (gruposRegenerados.isEmpty()) {
                mostrarAlerta("Error", "No se pudo regenerar la estructura de grupos.", Alert.AlertType.ERROR);
                return;
            }

            groupDAO.eliminarPorAnioYEtapa(anio, etapa);
            groupDAO.guardarGruposMasivo(new ArrayList<>(gruposRegenerados), anio, etapa);
            totalAlumnosEstructura = totalAlumnos;

            List<Student> alumnosBD = studentDAO.obtenerPorAnioYEtapa(anio, etapa);
            List<Student> alumnosParaAsignar = (alumnosImportados != null && !alumnosImportados.isEmpty())
                    ? alumnosImportados
                    : alumnosBD;

            if (alumnosParaAsignar != null && !alumnosParaAsignar.isEmpty()) {
                for (Student al : alumnosParaAsignar) {
                    al.getGruposAsignados().clear();
                    for (Group g : gruposRegenerados) {
                        if (al.getNumeroLista() >= g.getRangoInicial() && al.getNumeroLista() <= g.getRangoFinal()) {
                            al.agregarGrupo(g);
                        }
                    }
                }
                studentDAO.guardarAlumnosYRelaciones(alumnosParaAsignar, anio, etapa);
            }

            mostrarAlerta("Éxito", "Se añadió el grupo extra y se regeneró la estructura correctamente.", Alert.AlertType.INFORMATION);
            cargarContextoGlobal();

        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "Ocurrió un problema al añadir el grupo extra.", Alert.AlertType.ERROR);
        }
    }

    private void onEliminarGrupo(Group grupoBase) {
        String anio = GlobalSession.getAnioActual();
        String etapa = GlobalSession.getEtapaActual();

        int totalAlumnos = totalAlumnosEstructura;

        if (totalAlumnos <= 0) {
            List<Group> gruposActualesTmp = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
            totalAlumnos = calcularTotalAlumnosDesdeGrupos(gruposActualesTmp);
            totalAlumnosEstructura = totalAlumnos;
        }

        if (totalAlumnos <= 0) {
            mostrarAlerta("Error", "No hay total de alumnos disponible para recalcular la estructura.", Alert.AlertType.WARNING);
            return;
        }

        List<Group> gruposActuales = groupDAO.obtenerPorAnioYEtapa(anio, etapa);
        long gruposMismaCombinacion = gruposActuales.stream()
                .filter(g -> g.getCurso().getId() == grupoBase.getCurso().getId()
                        && g.getProfesor().getId() == grupoBase.getProfesor().getId())
                .count();

        if (gruposMismaCombinacion <= 1) {
            mostrarAlerta("Aviso",
                    "No puedes eliminar este grupo porque es el único grupo de esa materia para ese profesor.",
                    Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar eliminación");
        confirm.setHeaderText("Se eliminará un grupo de la materia seleccionada.");
        confirm.setContentText(
                "Grupo: " + grupoBase.getIdGrupo() +
                        "\nProfesor: " + grupoBase.getNombreProfesor() +
                        "\nMateria: " + grupoBase.getCurso().getNombre() +
                        "\n\nSe regenerará la estructura completa de grupos para recalcular tamaños y rangos.\n¿Deseas continuar?"
        );

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            List<Teacher> profesores = teacherDAO.obtenerProfesoresObservable();

            List<Group> gruposRegenerados = groupService.generarGruposQuitandoGrupo(
                    totalAlumnos,
                    profesores,
                    gruposActuales,
                    grupoBase,
                    anio,
                    etapa
            );

            if (gruposRegenerados.isEmpty()) {
                mostrarAlerta("Error", "La estructura resultante quedó vacía. Operación cancelada.", Alert.AlertType.ERROR);
                return;
            }

            groupDAO.eliminarPorAnioYEtapa(anio, etapa);
            groupDAO.guardarGruposMasivo(new ArrayList<>(gruposRegenerados), anio, etapa);
            totalAlumnosEstructura = totalAlumnos;

            List<Student> alumnosBD = studentDAO.obtenerPorAnioYEtapa(anio, etapa);
            List<Student> alumnosParaAsignar = (alumnosImportados != null && !alumnosImportados.isEmpty())
                    ? alumnosImportados
                    : alumnosBD;

            if (alumnosParaAsignar != null && !alumnosParaAsignar.isEmpty()) {
                for (Student al : alumnosParaAsignar) {
                    al.getGruposAsignados().clear();
                    for (Group g : gruposRegenerados) {
                        if (al.getNumeroLista() >= g.getRangoInicial() && al.getNumeroLista() <= g.getRangoFinal()) {
                            al.agregarGrupo(g);
                        }
                    }
                }
                studentDAO.guardarAlumnosYRelaciones(alumnosParaAsignar, anio, etapa);
            }

            mostrarAlerta("Éxito", "El grupo se eliminó y la estructura fue recalculada correctamente.", Alert.AlertType.INFORMATION);
            cargarContextoGlobal();

        } catch (Exception e) {
            e.printStackTrace();
            mostrarAlerta("Error", "Ocurrió un problema al eliminar el grupo.", Alert.AlertType.ERROR);
        }
    }

    private void configurarBuscadoresYOrdenamiento() {
        gruposFiltrados = new FilteredList<>(listaBaseGrupos, p -> true);
        SortedList<Group> gruposOrdenados = new SortedList<>(gruposFiltrados);
        gruposOrdenados.comparatorProperty().bind(tablaGrupos.comparatorProperty());
        tablaGrupos.setItems(gruposOrdenados);

        txtBuscarGrupo.textProperty().addListener((obs, oldV, newV) -> actualizarFiltroMateria());

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
        colRango.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRangoTexto()));

        colAcciones.setCellFactory(param -> new TableCell<>() {
            private final Button btnVer = new Button("Ver Detalles");
            {
                btnVer.getStyleClass().addAll("button-outlined", "accent");
                btnVer.setOnAction(e -> mostrarAlumnosDeGrupo(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnVer);
                if (!empty) {
                    int total = studentDAO.contarAlumnos(GlobalSession.getAnioActual(), GlobalSession.getEtapaActual());
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

    private void mostrarEnPanelDetalle(List<Student> alumnos, String titulo, Group grupoInfo) {
        lblTituloDetalle.setText(titulo);
        lblSubtituloDetalle.setText("Mostrando: " + alumnos.size() + " estudiantes");
        txtBuscarAlumno.clear();

        if (grupoInfo != null) {
            boxInfoProfesor.setVisible(true);
            boxInfoProfesor.setManaged(true);
            lblNombreProfDetalle.setText(grupoInfo.getNombreProfesor());
        } else {
            boxInfoProfesor.setVisible(false);
            boxInfoProfesor.setManaged(false);
        }

        listaAlumnosActual.setAll(alumnos);
        activarModoDetalleAlumnos();
    }

    private void mostrarAlumnosDeGrupo(Group grupo) {
        String anio = GlobalSession.getAnioActual();
        String etapa = GlobalSession.getEtapaActual();
        Map<String, List<Student>> alumnosEnGrupos = studentDAO.obtenerAlumnosAgrupadosPorBD(anio, etapa);
        List<Student> alumnosMostrar = alumnosEnGrupos.getOrDefault(grupo.getIdGrupo(), new ArrayList<>());
        mostrarEnPanelDetalle(alumnosMostrar, "Alumnos - Grupo " + grupo.getIdGrupo() + " (" + grupo.getCurso().getNombre() + ")", grupo);
    }

    @FXML
    private void onVerListaCompletaClick() {
        if (alumnosImportados != null && !alumnosImportados.isEmpty() && listaBaseGrupos.isEmpty()) {
            mostrarEnPanelDetalle(alumnosImportados, "Vista Previa: Memoria Excel", null);
        } else {
            String anio = GlobalSession.getAnioActual();
            String etapa = GlobalSession.getEtapaActual();
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

    @FXML
    private void onGenerarYGuardarClick() {
        try {
            String anio = GlobalSession.getAnioActual();
            String etapa = GlobalSession.getEtapaActual();
            int alumnos = 0;

            if (rbManual.isSelected()) {
                alumnos = Integer.parseInt(txtTotalAlumnos.getText());
            } else if (rbExcel.isSelected()) {
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
            totalAlumnosEstructura = alumnos;

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
        if (new Alert(Alert.AlertType.CONFIRMATION, "CUIDADO: ¿Eliminar TODO EL CICLO (Alumnos y Grupos)?\nEsta acción es irreversible.")
                .showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            String a = GlobalSession.getAnioActual();
            String e = GlobalSession.getEtapaActual();
            studentDAO.eliminarAlumnosYRelacionesMasivo(a, e);
            groupDAO.eliminarPorAnioYEtapa(a, e);
            totalAlumnosEstructura = 0;
            limpiarFormularioCreacion();
            cargarContextoGlobal();
        }
    }

    @FXML
    private void onCargarExcelBannerClick() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File archivo = fc.showOpenDialog(rootHBox.getScene().getWindow());

        if (archivo != null) {
            try {
                alumnosImportados = ExcelImporter.leerListaAlumnos(archivo);
                if (alumnosImportados.isEmpty()) {
                    mostrarAlerta("Error", "El archivo Excel está vacío o no es válido.", Alert.AlertType.WARNING);
                    return;
                }

                for (int i = 0; i < alumnosImportados.size(); i++) {
                    alumnosImportados.get(i).setNumeroLista(i + 1);
                }

                btnCargarExcelRegenerar.setText("Memoria Cargada");
                try {
                    ImageView loadedIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/check.png")));
                    loadedIcon.setFitHeight(16);
                    loadedIcon.setFitWidth(16);
                    btnCargarExcelRegenerar.setGraphic(loadedIcon);
                } catch(Exception e){}

                btnCargarExcelRegenerar.setDisable(true);
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
            String anio = GlobalSession.getAnioActual();
            String etapa = GlobalSession.getEtapaActual();

            List<Teacher> profes = teacherDAO.obtenerProfesoresObservable();
            List<Group> gruposGenerados = groupService.generarGrupos(alumnosImportados.size(), profes, anio, etapa);

            if (gruposGenerados.isEmpty()) {
                mostrarAlerta("Error", "Catálogo de profesores incompleto.", Alert.AlertType.WARNING);
                return;
            }

            groupDAO.eliminarPorAnioYEtapa(anio, etapa);
            groupDAO.guardarGruposMasivo(new ArrayList<>(gruposGenerados), anio, etapa);
            totalAlumnosEstructura = alumnosImportados.size();

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

    private void limpiarFormularioCreacion() {
        alumnosImportados = null;
        txtTotalAlumnos.clear();
        btnSubirExcel.setText("Seleccionar Archivo");

        try {
            ImageView defaultIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/folder.png")));
            defaultIcon.setFitHeight(16);
            defaultIcon.setFitWidth(16);
            btnSubirExcel.setGraphic(defaultIcon);
        } catch(Exception e){}

        btnVerExcelLocal.setVisible(false);
        btnVerExcelLocal.setManaged(false);
        rbManual.setSelected(true);
        txtTotalAlumnos.setDisable(false);
        btnSubirExcel.setDisable(true);

        if (btnCargarExcelRegenerar != null) {
            btnCargarExcelRegenerar.setText("Cargar Archivo Excel");
            try {
                ImageView btnIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/folder.png")));
                btnIcon.setFitHeight(16);
                btnIcon.setFitWidth(16);
                btnCargarExcelRegenerar.setGraphic(btnIcon);
            } catch(Exception e){}
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

    @FXML
    private void onSeleccionarCardManual() {
        rbManual.setSelected(true);
        txtTotalAlumnos.setDisable(false);
        btnSubirExcel.setDisable(true);
    }

    @FXML
    private void onSeleccionarCardExcel() {
        rbExcel.setSelected(true);
        txtTotalAlumnos.setDisable(true);
        txtTotalAlumnos.clear();
        btnSubirExcel.setDisable(false);
    }

    @FXML
    private void onCargarListaExcelClick() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File archivo = fc.showOpenDialog(rootHBox.getScene().getWindow());

        if (archivo != null) {
            try {
                alumnosImportados = ExcelImporter.leerListaAlumnos(archivo);
                if (alumnosImportados.isEmpty()) return;

                for (int i = 0; i < alumnosImportados.size(); i++) {
                    alumnosImportados.get(i).setNumeroLista(i + 1);
                }

                btnSubirExcel.setText("Archivo Cargado");
                try {
                    ImageView loadedIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/check.png")));
                    loadedIcon.setFitHeight(16);
                    loadedIcon.setFitWidth(16);
                    btnSubirExcel.setGraphic(loadedIcon);
                } catch(Exception e){}

                btnVerExcelLocal.setVisible(true);
                btnVerExcelLocal.setManaged(true);
            } catch (Exception e) {}
        }
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

        Map<Integer, Course> mapaCursos = datos.stream()
                .collect(Collectors.toMap(
                        g -> g.getCurso().getId(),
                        Group::getCurso,
                        (a, b) -> a
                ));

        cursosUnicos = mapaCursos.values().stream()
                .sorted(Comparator.comparingInt(Course::getId))
                .collect(Collectors.toList());

        indiceCursoActual = 0;
        txtBuscarGrupo.clear();
        actualizarFiltroMateria();
        actualizarEstadisticas();
    }

    private void actualizarEstadisticas() {
        int tAlumnos = 0;

        if (totalAlumnosEstructura > 0) {
            tAlumnos = totalAlumnosEstructura;
        } else if (alumnosImportados != null) {
            tAlumnos = alumnosImportados.size();
        } else {
            tAlumnos = studentDAO.contarAlumnos(GlobalSession.getAnioActual(), GlobalSession.getEtapaActual());
        }

        lblTotalAlumnos.setText(String.valueOf(tAlumnos));
        lblTotalGrupos.setText(String.valueOf(listaBaseGrupos.size()));

        try {
            lblTotalProfesores.setText(String.valueOf(teacherDAO.obtenerProfesoresObservable().size()));
            lblTotalCursos.setText(String.valueOf(courseDAO.contarCursos()));
        } catch (Exception e) {
            System.err.println("No se pudieron cargar métricas estáticas del catálogo.");
        }
    }

    private void activarModoVacio() {
        panelVacio.setVisible(true);
        panelFormulario.setVisible(false);
        panelTabla.setVisible(false);
        panelDetalleAlumnos.setVisible(false);
        btnNuevoCiclo.setVisible(true);
        btnEliminarCiclo.setVisible(false);
        btnVerAlumnosGlobal.setVisible(false);
        btnVerAlumnosGlobal.setManaged(false);

        if(panelAlertaManual != null) {
            panelAlertaManual.setVisible(false);
            panelAlertaManual.setManaged(false);
        }

        listaBaseGrupos.clear();
        cursosUnicos.clear();
        actualizarEstadisticas();
    }

    private void activarModoFormulario() {
        panelVacio.setVisible(false);
        panelFormulario.setVisible(true);
        panelTabla.setVisible(false);
        panelDetalleAlumnos.setVisible(false);
        btnNuevoCiclo.setVisible(false);
        btnEliminarCiclo.setVisible(false);
        btnVerAlumnosGlobal.setVisible(false);
        btnVerAlumnosGlobal.setManaged(false);

        if(panelAlertaManual != null) {
            panelAlertaManual.setVisible(false);
            panelAlertaManual.setManaged(false);
        }

        txtTotalAlumnos.clear();
        listaBaseGrupos.clear();
        cursosUnicos.clear();
        actualizarEstadisticas();
    }

    private void activarModoTabla() {
        panelVacio.setVisible(false);
        panelFormulario.setVisible(false);
        panelTabla.setVisible(true);
        panelDetalleAlumnos.setVisible(false);
        btnNuevoCiclo.setVisible(false);
        btnEliminarCiclo.setVisible(true);

        int totalAlumnosReales = studentDAO.contarAlumnos(GlobalSession.getAnioActual(), GlobalSession.getEtapaActual());

        if (totalAlumnosReales == 0) {
            panelAlertaManual.setVisible(true);
            panelAlertaManual.setManaged(true);
            btnCargarExcelRegenerar.setText("Cargar Archivo Excel");
            try {
                ImageView defaultIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/folder.png")));
                defaultIcon.setFitHeight(16);
                defaultIcon.setFitWidth(16);
                btnCargarExcelRegenerar.setGraphic(defaultIcon);
            } catch(Exception e){}

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
        panelVacio.setVisible(false);
        panelFormulario.setVisible(false);
        panelTabla.setVisible(false);
        panelDetalleAlumnos.setVisible(true);
    }

    private void mostrarAlerta(String titulo, String contenido, Alert.AlertType tipo) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(contenido);
        alerta.showAndWait();
    }
}