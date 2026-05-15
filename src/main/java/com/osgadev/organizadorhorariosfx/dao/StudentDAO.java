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

    private boolean isSQLite(Connection conn) throws SQLException {
        String dbName = conn.getMetaData().getDatabaseProductName();
        return dbName != null && dbName.toLowerCase().contains("sqlite");
    }

    private boolean isMySQL(Connection conn) throws SQLException {
        String dbName = conn.getMetaData().getDatabaseProductName();
        return dbName != null && dbName.toLowerCase().contains("mysql");
    }

    public void guardarAlumnosYRelaciones(List<Student> students, String anio, String etapa) {
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

            String sqlAlumno;
            String sqlRelacion;

            if (isSQLite(conn)) {
                sqlAlumno =
                        "INSERT INTO alumno (matricula, nombre_completo, correo_electronico, numero_lista, anio, etapa) " +
                                "VALUES (?, ?, ?, ?, ?, ?) " +
                                "ON CONFLICT(matricula) DO UPDATE SET " +
                                "nombre_completo = excluded.nombre_completo, " +
                                "correo_electronico = excluded.correo_electronico, " +
                                "numero_lista = excluded.numero_lista, " +
                                "anio = excluded.anio, " +
                                "etapa = excluded.etapa";

                sqlRelacion = "INSERT OR IGNORE INTO alumno_grupo (matricula, grupo_id) VALUES (?, ?)";

            } else if (isMySQL(conn)) {
                sqlAlumno =
                        "INSERT INTO alumno (matricula, nombre_completo, correo_electronico, numero_lista, anio, etapa) " +
                                "VALUES (?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "nombre_completo = VALUES(nombre_completo), " +
                                "correo_electronico = VALUES(correo_electronico), " +
                                "numero_lista = VALUES(numero_lista), " +
                                "anio = VALUES(anio), " +
                                "etapa = VALUES(etapa)";

                sqlRelacion = "INSERT IGNORE INTO alumno_grupo (matricula, grupo_id) VALUES (?, ?)";

            } else {
                throw new SQLException("Motor de base de datos no soportado.");
            }

            conn.setAutoCommit(false);

            try (PreparedStatement pstmtAlumno = conn.prepareStatement(sqlAlumno);
                 PreparedStatement pstmtRelacion = conn.prepareStatement(sqlRelacion)) {

                for (Student al : students) {
                    pstmtAlumno.setString(1, al.getMatricula());
                    pstmtAlumno.setString(2, al.getNombreCompleto());
                    pstmtAlumno.setString(3, al.getCorreo_electronico());
                    pstmtAlumno.setInt(4, al.getNumeroLista());
                    pstmtAlumno.setString(5, anio);
                    pstmtAlumno.setString(6, etapa);
                    pstmtAlumno.addBatch();

                    for (Group g : al.getGruposAsignados()) {
                        pstmtRelacion.setString(1, al.getMatricula());
                        pstmtRelacion.setString(2, g.getIdGrupo());
                        pstmtRelacion.addBatch();
                    }
                }

                pstmtAlumno.executeBatch();
                pstmtRelacion.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("Error al guardar students y relaciones: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

    public void eliminarAlumnosYRelacionesMasivo(String anio, String etapa) {
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

            String sqlRelaciones;

            if (isSQLite(conn)) {
                sqlRelaciones =
                        "DELETE FROM alumno_grupo " +
                                "WHERE matricula IN (" +
                                "SELECT matricula FROM alumno WHERE anio = ? AND etapa = ?" +
                                ")";
            } else if (isMySQL(conn)) {
                sqlRelaciones =
                        "DELETE ag FROM alumno_grupo ag " +
                                "INNER JOIN alumno a ON ag.matricula = a.matricula " +
                                "WHERE a.anio = ? AND a.etapa = ?";
            } else {
                throw new SQLException("Motor de base de datos no soportado.");
            }

            String sqlAlumnos = "DELETE FROM alumno WHERE anio = ? AND etapa = ?";

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
        String sql;
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (isSQLite(conn)) {
                sql = "INSERT OR IGNORE INTO alumno_grupo (matricula, grupo_id) VALUES (?, ?)";
            } else if (isMySQL(conn)) {
                sql = "INSERT IGNORE INTO alumno_grupo (matricula, grupo_id) VALUES (?, ?)";
            } else {
                throw new SQLException("Motor de base de datos no soportado.");
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, matricula);
                ps.setString(2, idGrupo);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Student al = new Student(rs.getString("matricula"), rs.getString("nombre_completo"), rs.getString("correo_electronico"));
                    al.setNumeroLista(rs.getInt("numero_lista"));
                    String idGrupo = rs.getString("grupo_id");

                    mapa.computeIfAbsent(idGrupo, k -> new ArrayList<>()).add(al);
                }
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