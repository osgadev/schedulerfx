package com.osgadev.organizadorhorariosfx.util;

import com.osgadev.organizadorhorariosfx.DTO.SesionAsignada;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class ExportadorExcel {

    public static void exportarHorarioPersonalizado(List<SesionAsignada> horarioGenerado, File archivoDestino) throws Exception {
        Workbook workbook = new XSSFWorkbook();

        // 1. Agrupar por Profesor y luego por Grupo
        Map<Teacher, Map<Group, List<SesionAsignada>>> datosAgrupados = new HashMap<>();
        for (SesionAsignada s : horarioGenerado) {
            Teacher profe = s.getGrupo().getProfesor();
            datosAgrupados.computeIfAbsent(profe, k -> new HashMap<>())
                    .computeIfAbsent(s.getGrupo(), k -> new ArrayList<>()).add(s);
        }

        // Estilos básicos
        CellStyle estiloNegrita = workbook.createCellStyle();
        Font fuenteNegrita = workbook.createFont();
        fuenteNegrita.setBold(true);
        estiloNegrita.setFont(fuenteNegrita);

        // Estilos para las celdas de horarios (Con bordes negros)
        CellStyle estiloCeldaHorario = workbook.createCellStyle();
        estiloCeldaHorario.setAlignment(HorizontalAlignment.CENTER);
        estiloCeldaHorario.setVerticalAlignment(VerticalAlignment.CENTER);
        estiloCeldaHorario.setBorderTop(BorderStyle.THIN);
        estiloCeldaHorario.setBorderBottom(BorderStyle.THIN);
        estiloCeldaHorario.setBorderLeft(BorderStyle.THIN);
        estiloCeldaHorario.setBorderRight(BorderStyle.THIN);
        estiloCeldaHorario.setWrapText(true);

        // Nombres de los días y colores (Lunes a Domingo)
        String[] diasSemana = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        IndexedColors[] coloresDias = {
                IndexedColors.LIGHT_GREEN, IndexedColors.LIGHT_TURQUOISE,
                IndexedColors.CORAL, IndexedColors.LIGHT_CORNFLOWER_BLUE,
                IndexedColors.ORCHID, IndexedColors.LEMON_CHIFFON, IndexedColors.TAN
        };

        // Crear estilos de cabecera pre-configurados para los 7 días
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
        for (Map.Entry<Teacher, Map<Group, List<SesionAsignada>>> entryProfe : datosAgrupados.entrySet()) {
            Teacher profe = entryProfe.getKey();

            // Construir nombre completo del profe real
            String apellidoMat = (profe.getApellidoMaterno() != null) ? profe.getApellidoMaterno() : "";
            String nombreCompleto = profe.getNombre() + " " + profe.getApellidoPaterno() + " " + apellidoMat;

            // Limpiar nombre de la hoja
            String nombreHoja = (profe.getNombre() + " " + profe.getApellidoPaterno()).replaceAll("[\\\\/?*:\\[\\]]", "");
            if (nombreHoja.length() > 30) nombreHoja = nombreHoja.substring(0, 30);

            Sheet sheet = workbook.createSheet(nombreHoja);
            int numFilaActual = 0;

            // 3. Crear las mini-tablas por cada Grupo de ese profesor
            for (Map.Entry<Group, List<SesionAsignada>> entryGrupo : entryProfe.getValue().entrySet()) {
                Group grupo = entryGrupo.getKey();
                List<SesionAsignada> sesiones = entryGrupo.getValue();

                // Fila: Nombre del Profesor
                Row filaNombre = sheet.createRow(numFilaActual++);
                crearCelda(filaNombre, 0, "Profesor:", estiloNegrita);
                crearCelda(filaNombre, 1, nombreCompleto, null);

                // Fila: Contacto (Datos Reales del DAO)
                String correo = (profe.getCorreoElectronico() != null) ? profe.getCorreoElectronico() : "No registrado";
                String telefono = (profe.getTelefono() != null) ? profe.getTelefono() : "No registrado";

                Row filaContacto = sheet.createRow(numFilaActual++);
                crearCelda(filaContacto, 0, "Contacto:", estiloNegrita);
                crearCelda(filaContacto, 1, "Correo: " + correo + " | Tel: " + telefono, null);

                // Fila: Datos del Grupo y Materia (Datos Reales)
                Row filaGrupo = sheet.createRow(numFilaActual++);
                crearCelda(filaGrupo, 0, "Materia / Grupo:", estiloNegrita);
                String infoGrupo = String.format("%s (ID: %s) | Alumnos: %d-%d",
                        grupo.getCurso().getNombre(),
                        grupo.getIdGrupo(),
                        grupo.getRangoInicial(),
                        grupo.getRangoFinal());
                crearCelda(filaGrupo, 1, infoGrupo, null);

                // Fila en blanco
                numFilaActual++;

                // Fila: Cabecera de los días (Lunes a Domingo con colores)
                Row filaDias = sheet.createRow(numFilaActual++);
                for (int i = 0; i < diasSemana.length; i++) {
                    crearCelda(filaDias, i, diasSemana[i], estilosDias[i]);
                    sheet.setColumnWidth(i, 4500); // Ajustar ancho visual de columna
                }

                // Fila: Horarios de las sesiones (Pintar solo donde hay clase)
                Row filaHorarios = sheet.createRow(numFilaActual++);
                filaHorarios.setHeightInPoints(30); // Fila más ancha para que se vea como bloque

                // Inicializamos todas las celdas de la semana en blanco con bordes
                for (int i = 0; i < 7; i++) {
                    crearCelda(filaHorarios, i, "", estiloCeldaHorario);
                }

                // Llenamos las celdas donde el grupo tiene clases
                for (SesionAsignada s : sesiones) {
                    int indexColumnaExcel = s.getColumnaDia() - 1;

                    int slotInicio = s.getSlotInicioSemanal() % 48;
                    int slotFin = slotInicio + s.getSpanFilas();

                    String textoHora = String.format("%02d:%02d -\n%02d:%02d",
                            slotInicio / 2, (slotInicio % 2) * 30,
                            slotFin / 2, (slotFin % 2) * 30);

                    // Obtenemos la celda existente y le seteamos la hora
                    Cell celdaDia = filaHorarios.getCell(indexColumnaExcel);
                    celdaDia.setCellValue(textoHora);
                }

                // Espacio reservado para la futura lista de alumnos
                Row filaAlumnos = sheet.createRow(numFilaActual++);
                crearCelda(filaAlumnos, 0, "[Espacio para lista de alumnos...]", null);

                // Dejar 2 filas en blanco antes de la tabla del siguiente grupo
                numFilaActual += 2;
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