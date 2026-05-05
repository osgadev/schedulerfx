package com.osgadev.organizadorhorariosfx.dao;

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
                    preparedStatement.setInt(5, grupo.getRangoInicial());
                    preparedStatement.setInt(6, grupo.getRangoFinal());
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

        // CORRECCIÓN: Agregado 'c.color_hex' y 'c.descripcion'
        String sql = "SELECT g.grupo_id, g.tamanio_grupo, g.rango_inicial, g.rango_final, " +
                "c.curso_id AS course_id, c.nombre AS course_nombre, c.min_horas_semanales AS course_horas, " +
                "c.color_hex AS course_color, c.descripcion AS course_descripcion, " +
                "t.profesor_id AS teacher_id, t.nombre AS teacher_nombre, t.apellido_paterno, t.apellido_materno, " +
                "t.correo_electronico, t.telefono " +
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
                curso.setMinHorasSemanales(rs.getInt("course_horas"));
                // NUEVO: Asignar los valores extraídos a la instancia del curso
                curso.setColorHex(rs.getString("course_color"));
                curso.setDescripcion(rs.getString("course_descripcion"));

                Teacher teacher = new Teacher();
                teacher.setId(rs.getInt("teacher_id"));
                teacher.setNombre(rs.getString("teacher_nombre"));
                teacher.setApellidoPaterno(rs.getString("apellido_paterno"));
                teacher.setApellidoMaterno(rs.getString("apellido_materno"));
                teacher.setCorreoElectronico(rs.getString("correo_electronico"));
                teacher.setTelefono(rs.getString("telefono"));

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
        String sql = "SELECT 1 FROM grupo WHERE anio = ? AND etapa = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.out.println("Error al comprobar existencia de grupos: " + e.getMessage());
            return false;
        }
    }

    public Group obtenerPorId(String idGrupo) {
        Group grupo = null;

        // CORRECCIÓN: Agregado 'c.color_hex' y 'c.descripcion' a la consulta por ID
        String sql = "SELECT g.grupo_id, g.tamanio_grupo, g.rango_inicial, g.rango_final, " +
                "c.curso_id, c.nombre AS nombre_curso, c.min_horas_semanales, c.color_hex, c.descripcion, " +
                "p.profesor_id, p.nombre AS nombre_profesor, p.apellido_paterno, p.apellido_materno, p.correo_electronico, p.telefono " +
                "FROM grupo g " +
                "INNER JOIN curso c ON g.curso_id = c.curso_id " +
                "INNER JOIN profesor p ON g.profesor_id = p.profesor_id " +
                "WHERE g.grupo_id = ?";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, idGrupo);

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Course curso = new Course();
                    curso.setId(rs.getInt("curso_id"));
                    curso.setNombre(rs.getString("nombre_curso"));
                    curso.setMinHorasSemanales(rs.getInt("min_horas_semanales"));
                    // NUEVO: Asignar color y descripción
                    curso.setColorHex(rs.getString("color_hex"));
                    curso.setDescripcion(rs.getString("descripcion"));

                    Teacher profesor = new Teacher(
                            rs.getInt("profesor_id"),
                            rs.getString("nombre_profesor"),
                            rs.getString("apellido_paterno"),
                            rs.getString("apellido_materno"),
                            rs.getString("correo_electronico"),
                            rs.getString("telefono"),
                            new java.util.ArrayList<>()
                    );

                    grupo = new Group(
                            rs.getString("grupo_id"),
                            curso,
                            profesor,
                            rs.getInt("tamanio_grupo"),
                            rs.getInt("rango_inicial"),
                            rs.getInt("rango_final")
                    );
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error al obtener el grupo con ID: " + idGrupo);
            e.printStackTrace();
        }

        return grupo;
    }

    public int contarGrupos(String anio, String etapa) {
        int total = 0;
        String sql = "SELECT COUNT(*) FROM grupo WHERE anio = ? AND etapa = ?";
        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) total = rs.getInt(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return total;
    }

    public double calcularHorasTotalesRequeridas(String anio, String etapa) {
        double totalHoras = 0;
        String sql = "SELECT SUM(c.min_horas_semanales) FROM grupo g " +
                "JOIN curso c ON g.curso_id = c.curso_id " +
                "WHERE g.anio = ? AND g.etapa = ?";
        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) totalHoras = rs.getDouble(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return totalHoras;
    }
}