package com.osgadev.organizadorhorariosfx.util;

import com.osgadev.organizadorhorariosfx.dto.AssignedSession;
import com.osgadev.organizadorhorariosfx.model.Student;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class ExportadorExcel {

    public static void exportarHorarioPersonalizado(List<AssignedSession> horarioGenerado,
                                                    Map<String, List<Student>> alumnosPorGrupo,
                                                    File archivoDestino) throws Exception {
        Workbook workbook = new XSSFWorkbook();

        // 1. Agrupar por Profesor y luego por Grupo
        Map<Teacher, Map<Group, List<AssignedSession>>> datosAgrupados = new HashMap<>();
        for (AssignedSession s : horarioGenerado) {
            Teacher profe = s.getGrupo().getProfesor();
            datosAgrupados.computeIfAbsent(profe, k -> new HashMap<>())
                    .computeIfAbsent(s.getGrupo(), k -> new ArrayList<>()).add(s);
        }

        // Estilos básicos
        CellStyle estiloNegrita = workbook.createCellStyle();
        Font fuenteNegrita = workbook.createFont();
        fuenteNegrita.setBold(true);
        estiloNegrita.setFont(fuenteNegrita);

        // Estilos para la tabla de horarios
        CellStyle estiloCeldaHorario = workbook.createCellStyle();
        estiloCeldaHorario.setAlignment(HorizontalAlignment.CENTER);
        estiloCeldaHorario.setVerticalAlignment(VerticalAlignment.CENTER);
        estiloCeldaHorario.setBorderTop(BorderStyle.THIN);
        estiloCeldaHorario.setBorderBottom(BorderStyle.THIN);
        estiloCeldaHorario.setBorderLeft(BorderStyle.THIN);
        estiloCeldaHorario.setBorderRight(BorderStyle.THIN);
        estiloCeldaHorario.setWrapText(true);

        // Estilos para la tabla de ALUMNOS
        CellStyle estiloCabeceraTabla = workbook.createCellStyle();
        estiloCabeceraTabla.setFont(fuenteNegrita);
        estiloCabeceraTabla.setAlignment(HorizontalAlignment.CENTER);
        estiloCabeceraTabla.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estiloCabeceraTabla.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estiloCabeceraTabla.setBorderTop(BorderStyle.THIN);
        estiloCabeceraTabla.setBorderBottom(BorderStyle.THIN);
        estiloCabeceraTabla.setBorderLeft(BorderStyle.THIN);
        estiloCabeceraTabla.setBorderRight(BorderStyle.THIN);

        CellStyle estiloCeldaAlumno = workbook.createCellStyle();
        estiloCeldaAlumno.setBorderTop(BorderStyle.THIN);
        estiloCeldaAlumno.setBorderBottom(BorderStyle.THIN);
        estiloCeldaAlumno.setBorderLeft(BorderStyle.THIN);
        estiloCeldaAlumno.setBorderRight(BorderStyle.THIN);

        // Nombres de los días y colores (Lunes a Domingo)
        String[] diasSemana = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        IndexedColors[] coloresDias = {
                IndexedColors.LIGHT_GREEN, IndexedColors.LIGHT_TURQUOISE,
                IndexedColors.CORAL, IndexedColors.LIGHT_CORNFLOWER_BLUE,
                IndexedColors.ORCHID, IndexedColors.LEMON_CHIFFON, IndexedColors.TAN
        };

        CellStyle[] estilosDias = new CellStyle[7];
        for (int i = 0; i < 7; i++) {
            estilosDias[i] = workbook.createCellStyle();
            estilosDias[i].setFont(fuenteNegrita);
            estilosDias[i].setAlignment(HorizontalAlignment.CENTER);
            estilosDias[i].setFillForegroundColor(coloresDias[i].getIndex());
            estilosDias[i].setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estilosDias[i].setBorderTop(BorderStyle.THIN);
            estilosDias[i].setBorderBottom(BorderStyle.THIN);
            estilosDias[i].setBorderLeft(BorderStyle.THIN);
            estilosDias[i].setBorderRight(BorderStyle.THIN);
        }

        // 2. Crear una hoja por cada Profesor
        for (Map.Entry<Teacher, Map<Group, List<AssignedSession>>> entryProfe : datosAgrupados.entrySet()) {
            Teacher profe = entryProfe.getKey();

            String apellidoMat = (profe.getApellidoMaterno() != null) ? profe.getApellidoMaterno() : "";
            String nombreCompleto = profe.getNombre() + " " + profe.getApellidoPaterno() + " " + apellidoMat;

            // Limpiar nombre de la hoja
            String nombreHoja = (profe.getNombre() + " " + profe.getApellidoPaterno()).replaceAll("[\\\\/?*:\\[\\]]", "");
            if (nombreHoja.length() > 30) nombreHoja = nombreHoja.substring(0, 30);

            Sheet sheet = workbook.createSheet(nombreHoja);
            int numFilaActual = 0;

            // 3. Crear las mini-tablas por cada Grupo
            for (Map.Entry<Group, List<AssignedSession>> entryGrupo : entryProfe.getValue().entrySet()) {
                Group grupo = entryGrupo.getKey();
                List<AssignedSession> sesiones = entryGrupo.getValue();

                // Datos del Profesor y Grupo
                Row filaNombre = sheet.createRow(numFilaActual++);
                crearCelda(filaNombre, 0, "Profesor:", estiloNegrita);
                crearCelda(filaNombre, 1, nombreCompleto, null);

                String correo = (profe.getCorreoElectronico() != null) ? profe.getCorreoElectronico() : "No registrado";
                String telefono = (profe.getTelefono() != null) ? profe.getTelefono() : "No registrado";
                Row filaContacto = sheet.createRow(numFilaActual++);
                crearCelda(filaContacto, 0, "Contacto:", estiloNegrita);
                crearCelda(filaContacto, 1, "Correo: " + correo + " | Tel: " + telefono, null);

                Row filaGrupo = sheet.createRow(numFilaActual++);
                crearCelda(filaGrupo, 0, "Materia / Grupo:", estiloNegrita);
                String infoGrupo = String.format("%s (ID: %s)", grupo.getCurso().getNombre(), grupo.getIdGrupo());
                crearCelda(filaGrupo, 1, infoGrupo, null);

                numFilaActual++; // Espacio en blanco

                // --- TABLA DE HORARIOS ---
                Row filaDias = sheet.createRow(numFilaActual++);
                for (int i = 0; i < diasSemana.length; i++) {
                    crearCelda(filaDias, i, diasSemana[i], estilosDias[i]);
                    sheet.setColumnWidth(i, 4500);
                }

                Row filaHorarios = sheet.createRow(numFilaActual++);
                filaHorarios.setHeightInPoints(30);
                for (int i = 0; i < 7; i++) crearCelda(filaHorarios, i, "", estiloCeldaHorario);

                for (AssignedSession s : sesiones) {
                    int indexColumnaExcel = s.getColumnaDia() - 1;
                    int slotInicio = s.getSlotInicioSemanal() % 48;
                    int slotFin = slotInicio + s.getSpanFilas();
                    String textoHora = String.format("%02d:%02d -\n%02d:%02d", slotInicio / 2, (slotInicio % 2) * 30, slotFin / 2, (slotFin % 2) * 30);
                    filaHorarios.getCell(indexColumnaExcel).setCellValue(textoHora);
                }

                numFilaActual += 2; // Espacio antes de los alumnos

                // --- TABLA DE ALUMNOS ---
                Row filaTituloAlumnos = sheet.createRow(numFilaActual++);
                crearCelda(filaTituloAlumnos, 0, "Lista de Alumnos Asignados:", estiloNegrita);

                Row filaCabeceraAlumnos = sheet.createRow(numFilaActual++);
                crearCelda(filaCabeceraAlumnos, 0, "No.", estiloCabeceraTabla);
                crearCelda(filaCabeceraAlumnos, 1, "Matrícula", estiloCabeceraTabla);
                crearCelda(filaCabeceraAlumnos, 2, "Nombre Completo", estiloCabeceraTabla);
                crearCelda(filaCabeceraAlumnos, 3, "Correo Electrónico", estiloCabeceraTabla);

                // Obtener los alumnos extraídos de la Base de Datos para este grupo en específico
                List<Student> alumnosDelGrupo = (alumnosPorGrupo != null) ? alumnosPorGrupo.get(grupo.getIdGrupo()) : null;

                if (alumnosDelGrupo != null && !alumnosDelGrupo.isEmpty()) {
                    for (Student al : alumnosDelGrupo) {
                        Row filaAl = sheet.createRow(numFilaActual++);
                        // IMPRIME EL NÚMERO DE LISTA REAL DEL ALUMNO
                        crearCelda(filaAl, 0, String.valueOf(al.getNumeroLista()), estiloCeldaAlumno);
                        crearCelda(filaAl, 1, al.getMatricula(), estiloCeldaAlumno);
                        crearCelda(filaAl, 2, al.getNombreCompleto(), estiloCeldaAlumno);
                        crearCelda(filaAl, 3, al.getCorreo_electronico(), estiloCeldaAlumno);
                    }
                } else {
                    Row filaVacia = sheet.createRow(numFilaActual++);
                    crearCelda(filaVacia, 0, "Sin alumnos asignados aún.", null);
                }

                numFilaActual += 3; // Espacio antes de la siguiente materia
            }
        }

        // 4. Guardar archivo
        try (FileOutputStream outputStream = new FileOutputStream(archivoDestino)) {
            workbook.write(outputStream);
        }
        workbook.close();
    }

    private static void crearCelda(Row fila, int columna, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }
}