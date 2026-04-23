package com.osgadev.organizadorhorariosfx.util;

import com.osgadev.organizadorhorariosfx.model.Alumno;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ImportadorExcel {

    // El dominio de tu escuela para auto-generar correos si el usuario no los incluye
    private static final String DOMINIO_ESCUELA = "@tuescuela.edu.mx";

    public static List<Alumno> leerListaAlumnos(File archivoExcel) throws Exception {
        List<Alumno> alumnos = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(archivoExcel);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) return alumnos; // Archivo vacío

            // Saltamos la fila 1 (Cabeceras) porque no nos importa qué texto digan
            Row filaCabecera = rowIterator.next();

            // Determinamos el formato contando el número de columnas utilizadas en la cabecera
            int numColumnas = filaCabecera.getLastCellNum();
            boolean esNombreSeparado = (numColumnas >= 4); // Si tiene 4 o más columnas, asumimos formato separado

            // Iterar sobre las filas reales de datos (Alumnos)
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // Evitar filas vacías (Si no hay matrícula en la col 0, ignoramos la fila)
                Cell celdaMatricula = row.getCell(0);
                if (celdaMatricula == null || celdaMatricula.getCellType() == CellType.BLANK) {
                    continue;
                }

                String matricula = extraerValorString(celdaMatricula).trim();
                String nombreCompleto = "";
                String correo = "";

                if (esNombreSeparado) {
                    // FORMATO B (Separado): 0=Matricula, 1=Paterno, 2=Materno, 3=Nombres, 4=Correo(Opcional)
                    String paterno = extraerValorString(row.getCell(1)).trim();
                    String materno = extraerValorString(row.getCell(2)).trim();
                    String nombres = extraerValorString(row.getCell(3)).trim();

                    // Unir nombres limpiamente (manejando casos donde no tengan apellido materno)
                    nombreCompleto = (nombres + " " + paterno + " " + materno).trim().replaceAll(" +", " ");

                    // Leer correo (si existe en la columna 4)
                    if (numColumnas > 4) {
                        correo = extraerValorString(row.getCell(4)).trim();
                    }
                } else {
                    // FORMATO A (Junto): 0=Matricula, 1=NombreCompleto, 2=Correo(Opcional)
                    nombreCompleto = extraerValorString(row.getCell(1)).trim();

                    // Leer correo (si existe en la columna 2)
                    if (numColumnas > 2) {
                        correo = extraerValorString(row.getCell(2)).trim();
                    }
                }

                // Generar el correo institucional si la celda venía vacía
                if (correo.isEmpty()) {
                    correo = matricula.toLowerCase() + DOMINIO_ESCUELA;
                }

                alumnos.add(new Alumno(matricula, nombreCompleto, correo.toLowerCase()));
            }
        }
        return alumnos;
    }

    // Método de soporte para leer celdas (números o texto) sin que POI lance excepciones
    private static String extraerValorString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // Evitar que la matrícula '12345' se lea como '12345.0'
                long valorNumerico = (long) cell.getNumericCellValue();
                return String.valueOf(valorNumerico);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}