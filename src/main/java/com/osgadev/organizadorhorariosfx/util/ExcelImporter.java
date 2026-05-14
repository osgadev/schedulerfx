package com.osgadev.organizadorhorariosfx.util;

import com.osgadev.organizadorhorariosfx.model.Student;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExcelImporter {

    private static final String DOMINIO_ESCUELA = "@correo.ler.uam.mx";

    public static List<Student> leerListaAlumnos(File archivoExcel) throws Exception {
        List<Student> students = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(archivoExcel);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) return students;

            Row filaCabecera = rowIterator.next();

            int numColumnas = filaCabecera.getLastCellNum();
            boolean esNombreSeparado = (numColumnas >= 4);

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                Cell celdaMatricula = row.getCell(0);
                if (celdaMatricula == null || celdaMatricula.getCellType() == CellType.BLANK) {
                    continue;
                }

                String matricula = extraerValorString(celdaMatricula).trim();
                String nombreCompleto = "";
                String correo = "";

                if (esNombreSeparado) {
                    String paterno = extraerValorString(row.getCell(1)).trim();
                    String materno = extraerValorString(row.getCell(2)).trim();
                    String nombres = extraerValorString(row.getCell(3)).trim();

                    nombreCompleto = (nombres + " " + paterno + " " + materno).trim().replaceAll(" +", " ");

                    if (numColumnas > 4) {
                        correo = extraerValorString(row.getCell(4)).trim();
                    }
                } else {
                    nombreCompleto = extraerValorString(row.getCell(1)).trim();

                    if (numColumnas > 2) {
                        correo = extraerValorString(row.getCell(2)).trim();
                    }
                }

                if (correo.isEmpty()) {
                    correo = matricula.toLowerCase() + DOMINIO_ESCUELA;
                }

                students.add(new Student(matricula, nombreCompleto, correo.toLowerCase()));
            }
        }
        return students;
    }

    private static String extraerValorString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                long valorNumerico = (long) cell.getNumericCellValue();
                return String.valueOf(valorNumerico);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}