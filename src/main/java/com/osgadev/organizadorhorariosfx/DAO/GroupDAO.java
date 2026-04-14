package com.osgadev.organizadorhorariosfx.DAO;

import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {

    public void guardarGruposMasivo(List<Group> grupos, String anio, String etapa) {
        // CORREGIDO: El orden de las columnas en el SQL ahora coincide exactamente con los setString/setInt
        String sql = "INSERT INTO grupo (grupo_id, curso_id, profesor_id, tamanio_grupo, rango_inicial, rango_final, anio, etapa) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getInstance().getConnection()) {

            connection.setAutoCommit(false);

            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                for (Group grupo : grupos) {
                    preparedStatement.setString(1, grupo.getIdGrupo());
                    preparedStatement.setInt(2, grupo.getCurso().getId());
                    preparedStatement.setInt(3, grupo.getProfesor().getId());
                    preparedStatement.setInt(4, grupo.getTamanioGrupo());

                    // CORREGIDO: Rangos en posición 5 y 6
                    preparedStatement.setInt(5, grupo.getRangoInicial());
                    preparedStatement.setInt(6, grupo.getRangoFinal());

                    // CORREGIDO: Año y etapa en posición 7 y 8
                    preparedStatement.setString(7, anio);
                    preparedStatement.setString(8, etapa);

                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                System.out.println("Se deshizo la transacción debido a un error: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Group> obtenerPorAnioYEtapa(String anio, String etapa) {
        List<Group> lista = new ArrayList<>();

        // CORRECCIÓN: Agregado el alias AS course_horas
        String sql = "SELECT g.grupo_id, g.tamanio_grupo, g.rango_inicial, g.rango_final, " +
                "c.curso_id AS course_id, c.nombre AS course_nombre, c.min_horas_semanales AS course_horas, " +
                "t.profesor_id AS teacher_id, t.nombre AS teacher_nombre, t.apellido_paterno, t.apellido_materno " +
                "FROM grupo g " +
                "INNER JOIN curso c ON g.curso_id = c.curso_id " +
                "INNER JOIN profesor t ON g.profesor_id = t.profesor_id " +
                "WHERE g.anio = ? AND g.etapa = ? " +
                "ORDER BY teacher_id";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Course curso = new Course();
                curso.setId(rs.getInt("course_id"));
                curso.setNombre(rs.getString("course_nombre"));
                // CORRECCIÓN: Leer el alias course_horas
                curso.setMinHorasSemanales(rs.getInt("course_horas"));

                Teacher teacher = new Teacher();
                teacher.setId(rs.getInt("teacher_id"));
                teacher.setNombre(rs.getString("teacher_nombre"));
                teacher.setApellidoPaterno(rs.getString("apellido_paterno"));
                teacher.setApellidoMaterno(rs.getString("apellido_materno"));

                Group grupo = new Group(
                        rs.getString("grupo_id"),
                        curso,
                        teacher,
                        rs.getInt("tamanio_grupo"),
                        rs.getInt("rango_inicial"),
                        rs.getInt("rango_final")
                );

                lista.add(grupo);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }


    // AÑADIDO: Metodo necesario para que el botón "Eliminar Grupos" de tu vista funcione
    public void eliminarPorAnioYEtapa(String anio, String etapa) {
        String sql = "DELETE FROM grupo WHERE anio = ? AND etapa = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);

            int filasBorradas = pstmt.executeUpdate();
            System.out.println("Se eliminaron " + filasBorradas + " grupos correctamente.");

        } catch (SQLException e) {
            System.out.println("Error al eliminar grupos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean existenGruposParaCiclo(String anio, String etapa) {
        // Usamos un query ultra ligero: si encuentra al menos 1 fila, devuelve '1'
        String sql = "SELECT 1 FROM grupo WHERE anio = ? AND etapa = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);

            try (ResultSet rs = pstmt.executeQuery()) {
                // Si rs.next() es true, significa que encontró al menos un registro
                return rs.next();
            }

        } catch (SQLException e) {
            System.out.println("Error al comprobar existencia de grupos: " + e.getMessage());
            return false; // Por seguridad, en caso de error de BD asumimos que no hay
        }
    }
}
