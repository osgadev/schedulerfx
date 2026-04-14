package com.osgadev.organizadorhorariosfx.DAO;

import com.osgadev.organizadorhorariosfx.model.Availability;
import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AvailabilityDAO {

    // Instanciamos el DAO de cursos para poder reconstruir el objeto Course al leer la BD
    private CourseDAO courseDAO = new CourseDAO();

    public void saveAll(Teacher profesor, List<Availability> bloquesNuevos) {
        String deleteSql = "DELETE FROM disponibilidad WHERE profesor_id = ?";
        // AÑADIDO: curso_sugerido_id
        String insertSql = "INSERT INTO disponibilidad (profesor_id, curso_sugerido, bloque_inicial, bloque_final) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            // 1. Borrar lo anterior
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, profesor.getId());
                deleteStmt.executeUpdate();
            }

            // 2. Insertar lo nuevo masivamente
            if (bloquesNuevos != null && !bloquesNuevos.isEmpty()) {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    for (Availability bloque : bloquesNuevos) {
                        insertStmt.setInt(1, profesor.getId());

                        // Validamos si es un curso sugerido o un bloque comodín (null)
                        if (bloque.getCursoSugerido() != null && bloque.getCursoSugerido().getId() > 0) {
                            insertStmt.setInt(2, bloque.getCursoSugerido().getId());
                        } else {
                            insertStmt.setNull(2, java.sql.Types.INTEGER);
                        }

                        insertStmt.setInt(3, bloque.getStartSlot());
                        insertStmt.setInt(4, bloque.getEndSlot());
                        insertStmt.addBatch();
                    }
                    insertStmt.executeBatch();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Availability> getByTeacher(Teacher profesor) {
        List<Availability> lista = new ArrayList<>();
        String sql = "SELECT disponibilidad_id, curso_sugerido, bloque_inicial, bloque_final FROM disponibilidad WHERE profesor_id = ? ORDER BY bloque_inicial ASC";

        // Creamos una clase temporal rápida para guardar los datos puros de la BD
        class DatosBD {
            int idDisp, cursoId, inicio, fin;
            boolean tieneCurso;
            public DatosBD(int idDisp, int cursoId, boolean tieneCurso, int inicio, int fin) {
                this.idDisp = idDisp; this.cursoId = cursoId; this.tieneCurso = tieneCurso;
                this.inicio = inicio; this.fin = fin;
            }
        }
        List<DatosBD> datosTemporales = new ArrayList<>();

        // PASO 1: Leemos la base de datos rápidamente y guardamos en la lista temporal
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profesor.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int idDisp = rs.getInt("disponibilidad_id");
                    int cursoId = rs.getInt("curso_sugerido");
                    boolean tieneCurso = !rs.wasNull(); // Si no es nulo, sí hay curso
                    int bInicio = rs.getInt("bloque_inicial");
                    int bFin = rs.getInt("bloque_final");

                    // Guardamos todo en memoria sin llamar a ningún otro DAO todavía
                    datosTemporales.add(new DatosBD(idDisp, cursoId, tieneCurso, bInicio, bFin));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // PASO 2: Ahora que el ResultSet y la conexión anterior ya se cerraron de forma segura,
        // podemos llamar a CourseDAO sin que haya conflictos en la conexión.
        for (DatosBD fila : datosTemporales) {
            Course cursoSugerido = null;
            if (fila.tieneCurso) {
                // Al llamar esto aquí, ya no interrumpimos la lectura anterior
                cursoSugerido = courseDAO.obtenerPorId(fila.cursoId);
            }

            lista.add(new Availability(
                    fila.idDisp,
                    profesor,
                    cursoSugerido,
                    fila.inicio,
                    fila.fin
            ));
        }

        return lista;
    }


    public void deleteAllByTeacher(Teacher profesor) {
        String sql = "DELETE FROM disponibilidad WHERE profesor_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, profesor.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
