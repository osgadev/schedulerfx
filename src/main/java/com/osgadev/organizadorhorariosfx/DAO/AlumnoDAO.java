package com.osgadev.organizadorhorariosfx.DAO;

import com.osgadev.organizadorhorariosfx.model.Alumno;
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

public class AlumnoDAO {

    public void guardarAlumnosYRelaciones(List<Alumno> alumnos, String anio, String etapa) {
        String sqlAlumno = "INSERT INTO alumno (matricula, nombre_completo, correo_electronico, numero_lista, anio, etapa) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE nombre_completo=VALUES(nombre_completo), correo_electronico=VALUES(correo_electronico), " +
                "numero_lista=VALUES(numero_lista), anio=VALUES(anio), etapa=VALUES(etapa)";

        String sqlRelacion = "INSERT IGNORE INTO alumno_grupo (matricula, grupo_id) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            // 1. Guardar o actualizar datos del alumno
            try (PreparedStatement pstmtAlumno = conn.prepareStatement(sqlAlumno)) {
                for (Alumno al : alumnos) {
                    pstmtAlumno.setString(1, al.getMatricula());
                    pstmtAlumno.setString(2, al.getNombreCompleto());
                    pstmtAlumno.setString(3, al.getCorreo_electronico());
                    pstmtAlumno.setInt(4, al.getNumeroLista());
                    pstmtAlumno.setString(5, anio);
                    pstmtAlumno.setString(6, etapa);
                    pstmtAlumno.addBatch();
                }
                pstmtAlumno.executeBatch();
            }

            // 2. Guardar las relaciones en la tabla puente
            try (PreparedStatement pstmtRelacion = conn.prepareStatement(sqlRelacion)) {
                for (Alumno al : alumnos) {
                    for (Group g : al.getGruposAsignados()) {
                        pstmtRelacion.setString(1, al.getMatricula());
                        pstmtRelacion.setString(2, g.getIdGrupo());
                        pstmtRelacion.addBatch();
                    }
                }
                pstmtRelacion.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error al guardar alumnos y relaciones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Devuelve un mapa con la lista de alumnos exacta para cada ID de grupo
    public Map<String, List<Alumno>> obtenerAlumnosAgrupadosPorBD(String anio, String etapa) {
        Map<String, List<Alumno>> mapa = new HashMap<>();

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
                Alumno al = new Alumno(rs.getString("matricula"), rs.getString("nombre_completo"), rs.getString("correo_electronico"));
                al.setNumeroLista(rs.getInt("numero_lista"));
                String idGrupo = rs.getString("grupo_id");

                mapa.computeIfAbsent(idGrupo, k -> new ArrayList<>()).add(al);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mapa;
    }
}