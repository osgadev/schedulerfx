package com.osgadev.organizadorhorariosfx.service;

import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupService {

    public List<Group> generarGrupos(int totalAlumnos, List<Teacher> listaProfesores, String anio, String etapa) {
        List<Group> gruposGenerados = new ArrayList<>();

        if (totalAlumnos <= 0 || listaProfesores == null || listaProfesores.isEmpty()) {
            return gruposGenerados;
        }

        Map<Integer, Course> mapaCursoEntidad = new HashMap<>();
        Map<Integer, List<Teacher>> mapaCursos = new HashMap<>();

        for (Teacher teacher : listaProfesores) {
            List<Course> cursosDelProfesor = teacher.getCursos();

            if (cursosDelProfesor != null) {
                for (Course curso : cursosDelProfesor) {
                    if (curso == null) continue;

                    mapaCursoEntidad.putIfAbsent(curso.getId(), curso);
                    mapaCursos.putIfAbsent(curso.getId(), new ArrayList<>());
                    mapaCursos.get(curso.getId()).add(teacher);
                }
            }
        }

        List<Integer> cursosOrdenados = mapaCursos.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (Integer cursoId : cursosOrdenados) {
            Course curso = mapaCursoEntidad.get(cursoId);
            List<Teacher> profesoresDelCurso = mapaCursos.get(cursoId);

            if (curso == null || profesoresDelCurso == null || profesoresDelCurso.isEmpty()) continue;

            profesoresDelCurso.sort(Comparator.comparingInt(Teacher::getId));

            int cantidadProfesores = profesoresDelCurso.size();
            int alumnosPorGrupoBase = totalAlumnos / cantidadProfesores;
            int alumnosSobrantes = totalAlumnos % cantidadProfesores;

            int inicioRangoActual = 1;

            Map<Integer, Integer> contadorPorProfesor = new HashMap<>();

            for (int i = 0; i < cantidadProfesores; i++) {
                Teacher teacher = profesoresDelCurso.get(i);

                int alumnosAsignados = alumnosPorGrupoBase + (i < alumnosSobrantes ? 1 : 0);
                int finRangoActual = inicioRangoActual + alumnosAsignados - 1;

                int numeroGrupo = contadorPorProfesor.getOrDefault(teacher.getId(), 0) + 1;
                contadorPorProfesor.put(teacher.getId(), numeroGrupo);

                String idGenerado = generarIdGrupo(
                        anio,
                        etapa,
                        curso.getNombre(),
                        teacher.getNombre(),
                        teacher.getApellidoPaterno(),
                        teacher.getApellidoMaterno(),
                        teacher.getId(),
                        numeroGrupo
                );

                Group nuevoGrupo = new Group(
                        idGenerado,
                        curso,
                        teacher,
                        alumnosAsignados,
                        inicioRangoActual,
                        finRangoActual
                );

                gruposGenerados.add(nuevoGrupo);
                inicioRangoActual = finRangoActual + 1;
            }
        }

        gruposGenerados.sort(
                Comparator.comparing((Group g) -> g.getCurso().getId())
                        .thenComparing(g -> g.getProfesor().getId())
                        .thenComparing(g -> extraerNumeroFinal(g.getIdGrupo()))
        );

        return gruposGenerados;
    }

    public List<Group> generarGruposConExtras(
            int totalAlumnos,
            List<Teacher> listaProfesoresBase,
            List<Group> gruposActuales,
            Group grupoBaseSeleccionado,
            String anio,
            String etapa
    ) {
        if (listaProfesoresBase == null || listaProfesoresBase.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Integer> conteoCursoProfesor = new HashMap<>();

        for (Group grupo : gruposActuales) {
            String key = construirClaveCursoProfesor(grupo.getCurso().getId(), grupo.getProfesor().getId());
            conteoCursoProfesor.put(key, conteoCursoProfesor.getOrDefault(key, 0) + 1);
        }

        String keyGrupoNuevo = construirClaveCursoProfesor(
                grupoBaseSeleccionado.getCurso().getId(),
                grupoBaseSeleccionado.getProfesor().getId()
        );
        conteoCursoProfesor.put(keyGrupoNuevo, conteoCursoProfesor.getOrDefault(keyGrupoNuevo, 0) + 1);

        List<Teacher> listaProfesoresExpandida = new ArrayList<>();

        for (Teacher teacher : listaProfesoresBase) {
            if (teacher.getCursos() == null || teacher.getCursos().isEmpty()) continue;

            List<Course> cursosExpandido = new ArrayList<>();

            for (Course curso : teacher.getCursos()) {
                if (curso == null) continue;

                String key = construirClaveCursoProfesor(curso.getId(), teacher.getId());
                int repeticiones = conteoCursoProfesor.getOrDefault(key, 1);

                for (int i = 0; i < repeticiones; i++) {
                    cursosExpandido.add(curso);
                }
            }

            Teacher clon = clonarTeacherConCursos(teacher, cursosExpandido);
            listaProfesoresExpandida.add(clon);
        }

        return generarGrupos(totalAlumnos, listaProfesoresExpandida, anio, etapa);
    }

    public List<Group> generarGruposQuitandoGrupo(
            int totalAlumnos,
            List<Teacher> listaProfesoresBase,
            List<Group> gruposActuales,
            Group grupoAEliminar,
            String anio,
            String etapa
    ) {
        if (listaProfesoresBase == null || listaProfesoresBase.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Integer> conteoCursoProfesor = new HashMap<>();

        for (Group grupo : gruposActuales) {
            String key = construirClaveCursoProfesor(grupo.getCurso().getId(), grupo.getProfesor().getId());
            conteoCursoProfesor.put(key, conteoCursoProfesor.getOrDefault(key, 0) + 1);
        }

        String keyEliminar = construirClaveCursoProfesor(
                grupoAEliminar.getCurso().getId(),
                grupoAEliminar.getProfesor().getId()
        );

        int cantidadActual = conteoCursoProfesor.getOrDefault(keyEliminar, 0);
        if (cantidadActual <= 1) {
            return new ArrayList<>();
        }

        conteoCursoProfesor.put(keyEliminar, cantidadActual - 1);

        List<Teacher> listaProfesoresExpandida = new ArrayList<>();

        for (Teacher teacher : listaProfesoresBase) {
            if (teacher.getCursos() == null || teacher.getCursos().isEmpty()) continue;

            List<Course> cursosExpandido = new ArrayList<>();

            for (Course curso : teacher.getCursos()) {
                if (curso == null) continue;

                String key = construirClaveCursoProfesor(curso.getId(), teacher.getId());
                int repeticiones = conteoCursoProfesor.getOrDefault(key, 1);

                for (int i = 0; i < repeticiones; i++) {
                    cursosExpandido.add(curso);
                }
            }

            Teacher clon = clonarTeacherConCursos(teacher, cursosExpandido);
            listaProfesoresExpandida.add(clon);
        }

        return generarGrupos(totalAlumnos, listaProfesoresExpandida, anio, etapa);
    }

    private Teacher clonarTeacherConCursos(Teacher teacher, List<Course> cursosExpandido) {
        Teacher clon = new Teacher();
        clon.setId(teacher.getId());
        clon.setNombre(teacher.getNombre());
        clon.setApellidoPaterno(teacher.getApellidoPaterno());
        clon.setApellidoMaterno(teacher.getApellidoMaterno());
        clon.setCorreoElectronico(teacher.getCorreoElectronico());
        clon.setTelefono(teacher.getTelefono());
        clon.setCursos(cursosExpandido);
        return clon;
    }

    private String construirClaveCursoProfesor(int cursoId, int profesorId) {
        return cursoId + "-" + profesorId;
    }

    private int extraerNumeroFinal(String idGrupo) {
        if (idGrupo == null || idGrupo.isBlank()) return 0;

        StringBuilder numerosFinales = new StringBuilder();

        for (int i = idGrupo.length() - 1; i >= 0; i--) {
            char c = idGrupo.charAt(i);
            if (Character.isDigit(c)) {
                numerosFinales.insert(0, c);
            } else {
                break;
            }
        }

        if (numerosFinales.isEmpty()) return 0;

        try {
            return Integer.parseInt(numerosFinales.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String generarIdGrupo(String anio, String etapa, String curso,
                                  String nombre, String apPaterno, String apMaterno,
                                  int profesorId, int numero) {
        StringBuilder idGrupo = new StringBuilder();

        if (anio != null && anio.length() >= 2) {
            idGrupo.append(anio.substring(anio.length() - 2));
        }

        if (etapa != null && !etapa.isEmpty()) {
            idGrupo.append(etapa.trim());
        }

        if (curso != null && !curso.isEmpty()) {
            idGrupo.append(curso.trim().toUpperCase().charAt(0));
        }
        if (nombre != null && !nombre.isEmpty()) {
            idGrupo.append(nombre.trim().toUpperCase().charAt(0));
        }
        if (apPaterno != null && !apPaterno.isEmpty()) {
            idGrupo.append(apPaterno.trim().toUpperCase().charAt(0));
        }
        if (apMaterno != null && !apMaterno.isEmpty()) {
            idGrupo.append(apMaterno.trim().toUpperCase().charAt(0));
        }

        idGrupo.append(profesorId);
        idGrupo.append(numero);

        return idGrupo.toString();
    }
}