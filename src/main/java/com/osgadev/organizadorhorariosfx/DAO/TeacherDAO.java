package com.osgadev.organizadorhorariosfx.DAO;

import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.model.Teacher;
import com.osgadev.organizadorhorariosfx.util.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TeacherDAO {

    public boolean actualizar(Teacher profesor){

        //actualizamos los datos de la tabla profesor
        String queryUpdate = "UPDATE profesor SET nombre=?, apellido_paterno=?, apellido_materno=?, correo_electronico=?, telefono=? " +
                "WHERE profesor_id=?";
        try(Connection connection = DatabaseConnection.getInstance().getConnection()){
            connection.setAutoCommit(false);

            try(PreparedStatement preparedStatement = connection.prepareStatement(queryUpdate)){
                preparedStatement.setString(1, profesor.getNombre());
                preparedStatement.setString(2, profesor.getApellidoPaterno());
                preparedStatement.setString(3, profesor.getApellidoMaterno());
                preparedStatement.setString(4, profesor.getCorreoElectronico());
                preparedStatement.setString(5, profesor.getTelefono());
                preparedStatement.setInt(6, profesor.getId());

                int filasAfectadas = preparedStatement.executeUpdate();
                if(filasAfectadas == 0){
                    connection.rollback();
                    return false;
                }
            }

            //borramos las relaciones de la tabla profesor_curso
            String queryDelete = "DELETE from profesor_curso WHERE profesor_id = ?";
            try(PreparedStatement preparedStatement = connection.prepareStatement(queryDelete)){
                preparedStatement.setInt(1, profesor.getId());
                preparedStatement.executeUpdate();
            }

            // insertamos las nuevas relaciones en la tabla profesor_curso
            if(profesor.getCursos() != null && !profesor.getCursos().isEmpty()){
                String queryInsert = "INSERT INTO profesor_curso (profesor_id, curso_id) VALUES (?, ?)";
                try(PreparedStatement preparedStatement = connection.prepareStatement(queryInsert)){
                    for(Course curso : profesor.getCursos()){
                        preparedStatement.setInt(1, profesor.getId());
                        preparedStatement.setInt(2, curso.getId());
                        preparedStatement.addBatch();
                    }
                    preparedStatement.executeBatch();
                }
            }
            connection.commit();
            return true;

        } catch (SQLException e){
            System.err.println("Error al actualizar la inforamcion del profesor" + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public ObservableList<Teacher> obtenerProfesoresObservable() {
        List<Teacher> profesores = obtenerProfesoresConCursos();
        return FXCollections.observableArrayList(profesores);
    }

    private List<Teacher> obtenerProfesoresConCursos() {
        Map<Integer, Teacher> profesorMap = new LinkedHashMap<>();
        String query = "SELECT " +
                "p.profesor_id, p.nombre, p.apellido_paterno, p.apellido_materno, " +
                "p.correo_electronico, p.telefono, " +
                "c.curso_id, c.nombre as curso_nombre, c.min_horas_semanales " +
                "FROM profesor p " +
                "LEFT JOIN profesor_curso pc ON p.profesor_id = pc.profesor_id " +
                "LEFT JOIN curso c ON pc.curso_id = c.curso_id " +
                "ORDER BY p.profesor_id";

        try(Connection connection = DatabaseConnection.getInstance().getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query);
            ResultSet rs = preparedStatement.executeQuery()){

            while (rs.next()){
                int profesorId = rs.getInt(1);

                Teacher profesor = profesorMap.get(profesorId);
                if(profesor == null){
                    profesor = new Teacher(
                            profesorId,
                            rs.getString("nombre"),   //tabien se puede usar en número de las columnas
                            rs.getString("apellido_paterno"),
                            rs.getString("apellido_materno"),
                            rs.getString("correo_electronico"),
                            rs.getString("telefono"),
                            new ArrayList<>()
                    );
                    profesorMap.put(profesorId, profesor);
                }

                int cursoId = rs.getInt("curso_id");
                if(cursoId != 0){
                    Course curso = new Course(
                            cursoId,
                            rs.getString("curso_nombre"),
                            rs.getInt("min_horas_semanales")
                    );
                    profesor.getCursos().add(curso);
                }
            }

        } catch (SQLException e){
            System.err.println("Error en la consulta" + e.getMessage());
            e.printStackTrace();
        }

        return new ArrayList<>(profesorMap.values());
    }

    public boolean eliminar(int profesor_id){
        String query = "DELETE FROM profesor WHERE profesor_id = ?";

        try(Connection connection = DatabaseConnection.getInstance().getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement(query)){
            preparedStatement.setInt(1, profesor_id);

            int filasAfectadas = preparedStatement.executeUpdate();
            return filasAfectadas > 0;
        } catch (SQLException e) {
            System.err.println("Error al eliminar la informacion del profesor" + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean insertar(Teacher profesor){
        String query1 = "INSERT INTO profesor (nombre, apellido_paterno, apellido_materno, correo_electronico, telefono) " +
                       "VALUES (?,?,?,?,?)";

        try(Connection connection = DatabaseConnection.getInstance().getConnection();
            PreparedStatement preparedStatement1 = connection.prepareStatement(query1, Statement.RETURN_GENERATED_KEYS)){

            connection.setAutoCommit(false); //desactivamos autocommit para manejar la transaccion

            preparedStatement1.setString(1, profesor.getNombre());
            preparedStatement1.setString(2, profesor.getApellidoPaterno());
            preparedStatement1.setString(3, profesor.getApellidoMaterno());
            preparedStatement1.setString(4, profesor.getCorreoElectronico());
            preparedStatement1.setString(5, profesor.getTelefono());

            int filasAfectadas = preparedStatement1.executeUpdate();
            if(filasAfectadas == 0){
                connection.rollback();
                return false;
            }

            int profesorId;
            try(ResultSet rsk = preparedStatement1.getGeneratedKeys()){
                if(!rsk.next()){
                    connection.rollback();
                    return false;
                }
                profesorId = rsk.getInt(1);
            }

            String query2 = "INSERT INTO profesor_curso (profesor_id, curso_id) VALUES (?,?)";
            try(PreparedStatement preparedStatement2 = connection.prepareStatement(query2)){
                for(Course curso : profesor.getCursos()){
                    preparedStatement2.setInt(1, profesorId);
                    preparedStatement2.setInt(2,curso.getId());
                    preparedStatement2.addBatch();
                }
                int[] resultados = preparedStatement2.executeBatch();

                for(int resultado : resultados){
                    if (resultado == PreparedStatement.EXECUTE_FAILED){
                        connection.rollback();
                        return false;
                    }
                }
            }
            connection.commit();
            return true;

        } catch (SQLException e){
            System.err.println("Error al guardar la informacion de l profesor" + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
}
