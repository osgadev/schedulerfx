package com.osgadev.organizadorhorariosfx.dao;

import com.osgadev.organizadorhorariosfx.model.Student;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentDAO {

    /**
     * Guarda alumnos y sus relaciones.
     * Optimizado con Transacciones y Batch processing.
     */
    public void guardarAlumnosYRelaciones(List<Student> students, String anio, String etapa) {
        String sqlAlumno = "INSERT INTO alumno (matricula, nombre_completo, correo_electronico, numero_lista, anio, etapa) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE nombre_completo=VALUES(nombre_completo), correo_electronico=VALUES(correo_electronico), " +
                "numero_lista=VALUES(numero_lista), anio=VALUES(anio), etapa=VALUES(etapa)";

        String sqlRelacion = "INSERT IGNORE INTO alumno_grupo (matricula, grupo_id) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false); // Iniciar transacción

            try (PreparedStatement pstmtAlumno = conn.prepareStatement(sqlAlumno);
                 PreparedStatement pstmtRelacion = conn.prepareStatement(sqlRelacion)) {

                for (Student al : students) {
                    // 1. Preparar alumno
                    pstmtAlumno.setString(1, al.getMatricula());
                    pstmtAlumno.setString(2, al.getNombreCompleto());
                    pstmtAlumno.setString(3, al.getCorreo_electronico());
                    pstmtAlumno.setInt(4, al.getNumeroLista());
                    pstmtAlumno.setString(5, anio);
                    pstmtAlumno.setString(6, etapa);
                    pstmtAlumno.addBatch();

                    // 2. Preparar relaciones
                    for (Group g : al.getGruposAsignados()) {
                        pstmtRelacion.setString(1, al.getMatricula());
                        pstmtRelacion.setString(2, g.getIdGrupo());
                        pstmtRelacion.addBatch();
                    }
                }

                // Ejecutar lotes
                pstmtAlumno.executeBatch();
                pstmtRelacion.executeBatch();

                conn.commit(); // Confirmar transacción

            } catch (SQLException e) {
                conn.rollback(); // Revertir en caso de error
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error al guardar students y relaciones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Recupera la lista plana de todos los alumnos de un ciclo.
     */
    public List<Student> obtenerPorAnioYEtapa(String anio, String etapa) {
        List<Student> listaAlumnos = new ArrayList<>();
        String sql = "SELECT matricula, nombre_completo, correo_electronico, numero_lista " +
                "FROM alumno WHERE anio = ? AND etapa = ? ORDER BY numero_lista ASC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, anio);
            ps.setString(2, etapa);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Student al = new Student(
                            rs.getString("matricula"),
                            rs.getString("nombre_completo"),
                            rs.getString("correo_electronico")
                    );
                    al.setNumeroLista(rs.getInt("numero_lista"));
                    listaAlumnos.add(al);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al recuperar los alumnos inscritos: " + e.getMessage());
            e.printStackTrace();
        }
        return listaAlumnos;
    }

    /**
     * Elimina a todos los alumnos y sus relaciones (alumno_grupo) de un ciclo y etapa específicos.
     */
    public void eliminarAlumnosYRelacionesMasivo(String anio, String etapa) {
        String sqlRelaciones = "DELETE ag FROM alumno_grupo ag " +
                "INNER JOIN alumno a ON ag.matricula = a.matricula " +
                "WHERE a.anio = ? AND a.etapa = ?";

        String sqlAlumnos = "DELETE FROM alumno WHERE anio = ? AND etapa = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psRelaciones = conn.prepareStatement(sqlRelaciones);
                 PreparedStatement psAlumnos = conn.prepareStatement(sqlAlumnos)) {

                psRelaciones.setString(1, anio);
                psRelaciones.setString(2, etapa);
                psRelaciones.executeUpdate();

                psAlumnos.setString(1, anio);
                psAlumnos.setString(2, etapa);
                psAlumnos.executeUpdate();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error al eliminar masivamente alumnos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ======================================================================
    // NUEVOS MÉTODOS PARA EL FLUJO INCREMENTAL (ALUMNOS EXTRA)
    // ======================================================================

    public int obtenerMaximoNumeroLista(String anio, String etapa) {
        String sql = "SELECT MAX(numero_lista) FROM alumno WHERE anio = ? AND etapa = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, anio);
            ps.setString(2, etapa);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void guardarAlumnoIndividual(Student alumno, String anio, String etapa) {
        String sql = "INSERT INTO alumno (matricula, nombre_completo, correo_electronico, numero_lista, anio, etapa) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alumno.getMatricula());
            ps.setString(2, alumno.getNombreCompleto());
            ps.setString(3, alumno.getCorreo_electronico());
            ps.setInt(4, alumno.getNumeroLista());
            ps.setString(5, anio);
            ps.setString(6, etapa);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void eliminarAlumnoIndividual(String matricula) {
        String sqlRelaciones = "DELETE FROM alumno_grupo WHERE matricula = ?";
        String sqlAlumno = "DELETE FROM alumno WHERE matricula = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psRel = conn.prepareStatement(sqlRelaciones);
                 PreparedStatement psAl = conn.prepareStatement(sqlAlumno)) {
                psRel.setString(1, matricula);
                psRel.executeUpdate();

                psAl.setString(1, matricula);
                psAl.executeUpdate();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Student> obtenerAlumnosPendientes(String anio, String etapa) {
        List<Student> pendientes = new ArrayList<>();
        String sql = "SELECT a.matricula, a.nombre_completo, a.correo_electronico, a.numero_lista " +
                "FROM alumno a " +
                "WHERE a.anio = ? AND a.etapa = ? AND a.matricula NOT IN (SELECT matricula FROM alumno_grupo) " +
                "ORDER BY a.numero_lista ASC";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, anio);
            ps.setString(2, etapa);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Student al = new Student(rs.getString("matricula"), rs.getString("nombre_completo"), rs.getString("correo_electronico"));
                    al.setNumeroLista(rs.getInt("numero_lista"));
                    pendientes.add(al);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pendientes;
    }

    public void asignarAlumnoAGrupo(String matricula, String idGrupo) {
        String sql = "INSERT IGNORE INTO alumno_grupo (matricula, grupo_id) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matricula);
            ps.setString(2, idGrupo);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ======================================================================
    // MÉTODOS ORIGINALES INTACTOS (Usados por otros controladores)
    // ======================================================================

    public Map<String, List<Student>> obtenerAlumnosAgrupadosPorBD(String anio, String etapa) {
        Map<String, List<Student>> mapa = new HashMap<>();

        String sql = "SELECT a.matricula, a.nombre_completo, a.correo_electronico, a.numero_lista, ag.grupo_id " +
                "FROM alumno a " +
                "INNER JOIN alumno_grupo ag ON a.matricula = ag.matricula " +
                "INNER JOIN grupo g ON ag.grupo_id = g.grupo_id " +
                "WHERE g.anio = ? AND g.etapa = ? " +
                "ORDER BY a.numero_lista ASC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Student al = new Student(rs.getString("matricula"), rs.getString("nombre_completo"), rs.getString("correo_electronico"));
                al.setNumeroLista(rs.getInt("numero_lista"));
                String idGrupo = rs.getString("grupo_id");

                mapa.computeIfAbsent(idGrupo, k -> new ArrayList<>()).add(al);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mapa;
    }

    public boolean existenAlumnos(String anio, String etapa) {
        String sql = "SELECT 1 FROM alumno WHERE anio = ? AND etapa = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public int contarAlumnos(String anio, String etapa) {
        int total = 0;
        String sql = "SELECT COUNT(*) FROM alumno WHERE anio = ? AND etapa = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) total = rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return total;
    }
}