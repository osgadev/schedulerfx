package com.osgadev.organizadorhorariosfx.DAO;

import com.osgadev.organizadorhorariosfx.DTO.SesionAsignada;
import com.osgadev.organizadorhorariosfx.model.Group;
import com.osgadev.organizadorhorariosfx.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ScheduleDAO {

    private final GroupDAO groupDAO;

    public ScheduleDAO() {
        this.groupDAO = new GroupDAO();
    }

    /**
     * Guarda la lista completa de sesiones generadas por la IA en la base de datos.
     */
    public boolean saveSchedule(List<SesionAsignada> sesiones, String anio, String etapa) {
        String sqlDelete = "DELETE FROM horario WHERE anio = ? AND etapa = ?";

        String sqlInsert = "INSERT INTO horario (grupo_id, anio, etapa, columna_dia, fila_visual_inicio, span_visual_filas, bloque_logico_inicio) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {

            conn.setAutoCommit(false);

            try (PreparedStatement pstmtDelete = conn.prepareStatement(sqlDelete)) {
                pstmtDelete.setString(1, anio);
                pstmtDelete.setString(2, etapa);
                pstmtDelete.executeUpdate();
            }

            try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert)) {
                for (SesionAsignada s : sesiones) {
                    pstmtInsert.setString(1, s.getGrupo().getIdGrupo());
                    pstmtInsert.setString(2, anio);
                    pstmtInsert.setString(3, etapa);
                    pstmtInsert.setInt(4, s.getColumnaDia());
                    pstmtInsert.setInt(5, s.getFilaHora());
                    pstmtInsert.setInt(6, s.getSpanFilas());
                    pstmtInsert.setInt(7, s.getSlotInicioSemanal());

                    pstmtInsert.addBatch();
                }
                pstmtInsert.executeBatch();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Carga el horario guardado en la base de datos y reconstruye la vista.
     */
    public List<SesionAsignada> loadSchedule(String anio, String etapa) {
        List<SesionAsignada> horarioCargado = new ArrayList<>();
        String sql = "SELECT * FROM horario WHERE anio = ? AND etapa = ?";

        System.out.println("Iniciando carga de horario para " + anio + " - Etapa " + etapa);

        // 1. Clase temporal interna para guardar los datos en crudo y soltar la BD rápido
        class DatosFila {
            String idGrupo;
            int colDia, filaVisual, spanFilas, slotInicio;

            public DatosFila(String idGrupo, int colDia, int filaVisual, int spanFilas, int slotInicio) {
                this.idGrupo = idGrupo;
                this.colDia = colDia;
                this.filaVisual = filaVisual;
                this.spanFilas = spanFilas;
                this.slotInicio = slotInicio;
            }
        }

        List<DatosFila> filasRecuperadas = new ArrayList<>();

        // 2. Extraemos TODO rápidamente del ResultSet y soltamos la conexión
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    filasRecuperadas.add(new DatosFila(
                            rs.getString("grupo_id"),
                            rs.getInt("columna_dia"),
                            rs.getInt("fila_visual_inicio"),
                            rs.getInt("span_visual_filas"),
                            rs.getInt("bloque_logico_inicio")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error leyendo la tabla horario:");
            e.printStackTrace();
            return horarioCargado; // Retornamos vacío si falla
        }

        // 3. Ya sin mantener el ResultSet abierto, buscamos los grupos.
        // Esto evita que GroupDAO cierre o interfiera con la conexión/cursor actual.
        for (DatosFila fila : filasRecuperadas) {
            Group grupo = groupDAO.obtenerPorId(fila.idGrupo);

            if (grupo != null) {
                SesionAsignada sesion = new SesionAsignada(grupo, fila.colDia, fila.filaVisual, fila.spanFilas);
                sesion.setSlotInicioSemanal(fila.slotInicio);
                horarioCargado.add(sesion);
            } else {
                System.err.println("Advertencia DAO: No se pudo encontrar el grupo_id: " + fila.idGrupo);
            }
        }

        System.out.println("Éxito: Se reconstruyeron " + horarioCargado.size() + " tarjetas.");
        return horarioCargado;
    }

    /**
     * Borra permanentemente el horario de un ciclo específico.
     */
    public boolean deleteSchedule(String anio, String etapa) {
        String sql = "DELETE FROM horario WHERE anio = ? AND etapa = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);

            int filasAfectadas = pstmt.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verifica si ya existe un horario calculado.
     */
    public boolean existsSchedule(String anio, String etapa) {
        String sql = "SELECT 1 FROM horario WHERE anio = ? AND etapa = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, anio);
            pstmt.setString(2, etapa);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}