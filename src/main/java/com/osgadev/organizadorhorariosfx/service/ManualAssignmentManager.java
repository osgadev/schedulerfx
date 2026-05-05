package com.osgadev.organizadorhorariosfx.service;

import com.osgadev.organizadorhorariosfx.dto.GroupState;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;

import java.util.*;
import java.util.stream.Collectors;

public class ManualAssignmentManager {

    private final List<GroupState> todosLosEstados;
    private GroupState grupoSeleccionado;

    public ManualAssignmentManager() {
        this.todosLosEstados = new ArrayList<>();
    }

    // Inicializa el almacén de materias
    public void cargarGrupos(List<Group> grupos) {
        todosLosEstados.clear();
        grupoSeleccionado = null;
        for (Group g : grupos) {
            todosLosEstados.add(new GroupState(g));
        }
    }

    // Obtiene una lista de profesores únicos a partir de los grupos cargados
    public List<Teacher> obtenerProfesoresUnicos() {
        Map<Integer, Teacher> mapaProfesores = new HashMap<>();
        for (GroupState estado : todosLosEstados) {
            Teacher t = estado.getGrupo().getProfesor();
            if (t != null) {
                mapaProfesores.put(t.getId(), t);
            }
        }
        List<Teacher> listaProfes = new ArrayList<>(mapaProfesores.values());
        listaProfes.sort(Comparator.comparing(Teacher::getNombre));
        return listaProfes;
    }

    // Obtiene los grupos pendientes filtrados por un profesor
    public List<GroupState> obtenerGruposPorProfesor(Teacher profesor) {
        if (profesor == null) return new ArrayList<>();
        return todosLosEstados.stream()
                .filter(eg -> eg.getGrupo().getProfesor().getId() == profesor.getId())
                .collect(Collectors.toList());
    }

    // Calcula cuántas horas le faltan por acomodar a un profesor en toda su carga
    public double calcularHorasPendientesPorProfesor(Teacher profesor) {
        if (profesor == null) return 0;
        return todosLosEstados.stream()
                .filter(eg -> eg.getGrupo().getProfesor().getId() == profesor.getId())
                .mapToDouble(GroupState::getHorasRestantes)
                .sum();
    }

    // Deduce horas a un grupo específico (se usa al cargar de BD o tras la IA)
    public void deducirHorasAGrupo(String idGrupo, double horasRestar) {
        for (GroupState eg : todosLosEstados) {
            if (eg.getGrupo().getIdGrupo().equals(idGrupo)) {
                eg.agregarHoras(horasRestar); // Asumiendo que tu método "agregarHoras" deduce horas
                break;
            }
        }
    }

    // Reembolsa horas a un grupo específico (se usa al eliminar bloques)
    public void reembolsarHorasAGrupo(String idGrupo, double horasSumar) {
        for (GroupState eg : todosLosEstados) {
            if (eg.getGrupo().getIdGrupo().equals(idGrupo)) {
                eg.reembolsarHoras(horasSumar);
                break;
            }
        }
    }

    // Getter y Setter del grupo activo para el Drag & Drop
    public GroupState getGrupoSeleccionado() {
        return grupoSeleccionado;
    }

    public void setGrupoSeleccionado(GroupState estado) {
        this.grupoSeleccionado = estado;
    }

    // Busca un GroupState específico por el ID del grupo
    public GroupState buscarEstadoPorId(String idGrupo) {
        return todosLosEstados.stream()
                .filter(eg -> eg.getGrupo().getIdGrupo().equals(idGrupo))
                .findFirst()
                .orElse(null);
    }
}