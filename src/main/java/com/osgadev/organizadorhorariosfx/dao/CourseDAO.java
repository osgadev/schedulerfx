package com.osgadev.organizadorhorariosfx.dao;

import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseDAO {

    //====== metodo para obtener los cursos (rellenamos la lista con el RESULT SET de esta consulta)========
    public List<Course> obtenerCursos(){

        List<Course> listaCursos = new ArrayList<>();
        String query = "SELECT * FROM curso";

        try(Connection connection = DatabaseConnection.getInstance().getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            ResultSet rs = preparedStatement.executeQuery()){

            while (rs.next()){
                int id = rs.getInt("curso_id");
                String nombre = rs.getString("nombre");
                int horas = rs.getInt("min_horas_semanales");

                // NUEVOS CAMPOS: Leemos la descripción y el color desde la base de datos
                String descripcion = rs.getString("descripcion");
                String colorHex = rs.getString("color_hex");

                // Usamos el nuevo constructor del modelo Course
                Course curso = new Course(id, nombre, horas, descripcion, colorHex);
                listaCursos.add(curso);
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener los cursos: " + e.getMessage());
        }
        return listaCursos;
    }

    //====== metodo para insertar cursos========
    public boolean insertar(Course curso){
        // ACTUALIZADO: Añadimos las nuevas columnas al INSERT
        String query = "INSERT INTO curso (nombre, min_horas_semanales, descripcion, color_hex) VALUES (?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getInstance().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
        ) {
            preparedStatement.setString(1, curso.getNombre());
            preparedStatement.setInt(2, curso.getMinHorasSemanales());

            // Manejo de valores nulos para los nuevos campos
            if (curso.getDescripcion() != null && !curso.getDescripcion().trim().isEmpty()) {
                preparedStatement.setString(3, curso.getDescripcion());
            } else {
                preparedStatement.setNull(3, Types.VARCHAR); // O Types.CLOB / Types.LONGVARCHAR dependiendo del driver
            }

            if (curso.getColorHex() != null && !curso.getColorHex().isEmpty()) {
                preparedStatement.setString(4, curso.getColorHex());
            } else {
                preparedStatement.setNull(4, Types.VARCHAR);
            }

            int filasAfectadas = preparedStatement.executeUpdate();

            if(filasAfectadas > 0){
                try(ResultSet rsKeys = preparedStatement.getGeneratedKeys()){
                    if(rsKeys.next()){
                        curso.setId(rsKeys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e){
            System.err.println("Error al insertar el curso: " + e.getMessage());
        }
        return false;
    }

    //====== metodo para actualizar los cursos========
    public boolean actualizar(Course curso){
        // ACTUALIZADO: Añadimos las nuevas columnas al UPDATE
        String query = "UPDATE curso SET nombre = ?, min_horas_semanales = ?, descripcion = ?, color_hex = ? WHERE curso_id = ?";

        try(Connection connection = DatabaseConnection.getInstance().getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query)){

            preparedStatement.setString(1, curso.getNombre());
            preparedStatement.setInt(2, curso.getMinHorasSemanales());

            // Manejo de valores nulos para los nuevos campos
            if (curso.getDescripcion() != null && !curso.getDescripcion().trim().isEmpty()) {
                preparedStatement.setString(3, curso.getDescripcion());
            } else {
                preparedStatement.setNull(3, Types.VARCHAR);
            }

            if (curso.getColorHex() != null && !curso.getColorHex().isEmpty()) {
                preparedStatement.setString(4, curso.getColorHex());
            } else {
                preparedStatement.setNull(4, Types.VARCHAR);
            }

            preparedStatement.setInt(5, curso.getId());

            int filasAfectadas = preparedStatement.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e){
            System.err.println("Error al actualizar el curso: " + e.getMessage());
        }
        return false;
    }

    //====== metodo para borrar los cursos========
    public boolean eliminar(int idCurso){
        String query = "DELETE FROM curso WHERE curso_id = ?";

        try(Connection connection = DatabaseConnection.getInstance().getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query)){

            preparedStatement.setInt(1, idCurso);

            int filasAfectadas = preparedStatement.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e){
            System.err.println("Error al eliminar el curso: " + e.getMessage());
        }
        return false;
    }

    public Course obtenerPorId(int id) {
        Course curso = null;
        String sql = "SELECT * FROM curso WHERE curso_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    curso = new Course();
                    curso.setId(rs.getInt("curso_id"));
                    curso.setNombre(rs.getString("nombre"));
                    curso.setMinHorasSemanales(rs.getInt("min_horas_semanales"));

                    // NUEVOS CAMPOS
                    curso.setDescripcion(rs.getString("descripcion"));
                    curso.setColorHex(rs.getString("color_hex"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return curso;
    }

    public int contarCursos() {
        int total = 0;
        String sql = "SELECT COUNT(*) FROM curso";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) total = rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return total;
    }
}