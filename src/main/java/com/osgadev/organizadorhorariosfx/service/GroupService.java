package com.osgadev.organizadorhorariosfx.service;

import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupService {

    public List<Group> generarGrupos(int totalAlumnos, List<Teacher> listaProfesores, String anio, String etapa) {
        List<Group> gruposGenerados = new ArrayList<>();

        Map<Course, List<Teacher>> mapaCursos = new HashMap<>();

        for (Teacher teacher : listaProfesores) {
            List<Course> cursosDelProfesor = teacher.getCursos();

            if (cursosDelProfesor != null) {
                for (Course curso : cursosDelProfesor) {
                    mapaCursos.putIfAbsent(curso, new ArrayList<>());
                    mapaCursos.get(curso).add(teacher);
                }
            }
        }

        for (Map.Entry<Course, List<Teacher>> entry : mapaCursos.entrySet()) {
            Course curso = entry.getKey();
            List<Teacher> profesoresDelCurso = entry.getValue();

            int cantidadProfesores = profesoresDelCurso.size();
            if (cantidadProfesores == 0) continue;

            int alumnosPorGrupoBase = totalAlumnos / cantidadProfesores;
            int alumnosSobrantes = totalAlumnos % cantidadProfesores;

            // NUEVO: Variable para llevar el conteo del rango acumulado de ESTA materia
            int inicioRangoActual = 1;

            for (int i = 0; i < cantidadProfesores; i++) {
                Teacher teacher = profesoresDelCurso.get(i);

                int alumnosAsignados = alumnosPorGrupoBase + (i < alumnosSobrantes ? 1 : 0);

                // NUEVO: Cálculo del final del rango
                int finRangoActual = (inicioRangoActual + alumnosAsignados) - 1;

                String idGenerado = generarIdGrupo(
                        anio,
                        etapa,
                        curso.getNombre(),
                        teacher.getNombre(),
                        teacher.getApellidoPaterno(),
                        teacher.getApellidoMaterno(),
                        1
                );

                // Agregamos los rangos al constructor
                Group nuevoGrupo = new Group(idGenerado, curso, teacher, alumnosAsignados, inicioRangoActual, finRangoActual);
                gruposGenerados.add(nuevoGrupo);

                // NUEVO: El inicio del siguiente grupo será el final de este + 1
                inicioRangoActual = finRangoActual + 1;
            }
        }

        return gruposGenerados;
    }


    private String generarIdGrupo(String anio, String etapa, String curso, String nombre, String apPaterno, String apMaterno, int numero) {
        StringBuilder idGrupo = new StringBuilder();

        if(anio != null && anio.length() >= 2){
            idGrupo.append(anio.substring(anio.length()-2));
        }

        System.out.println("La etapa es diferente de null?: "+etapa != null);
        System.out.println("La etapa esta vacia?"+etapa.isEmpty());
        if (etapa != null && !etapa.isEmpty()){
            idGrupo.append(etapa.trim());
            System.out.println(etapa.trim());
        }

        if (curso != null && !curso.isEmpty()) idGrupo.append(curso.trim().toUpperCase().charAt(0));
        if (nombre != null && !nombre.isEmpty()) idGrupo.append(nombre.trim().toUpperCase().charAt(0));
        if (apPaterno != null && !apPaterno.isEmpty()) idGrupo.append(apPaterno.trim().toUpperCase().charAt(0));
        if (apMaterno != null && !apMaterno.isEmpty()) idGrupo.append(apMaterno.trim().toUpperCase().charAt(0));

        idGrupo.append(numero);

        return idGrupo.toString();
    }


}
