package com.osgadev.organizadorhorariosfx.DAO;

import com.osgadev.organizadorhorariosfx.model.Course;
import com.osgadev.organizadorhorariosfx.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CourseDAO {

    //====== metodo para obtener los cursos (rellenamos la lista con el RESULT SET de esta consulta)========
    public List<Course> obtenerCursos(){

        List<Course> listaCursos = new ArrayList<>();  //creamos e inicializamos la lista aqui mismo, po lo tanto es una variable del metodo
        String query = "SELECT * FROM curso"; //esto es la query que usaremos para obtener la informacion de la bd

        try(Connection connection = DatabaseConnection.getInstance().getConnection(); //usamos un try with resources para cerrar automaticamente la conexion
            PreparedStatement preparedStatement = connection.prepareStatement(query); //el try with  resources tambien cierra el prepared statement
            ResultSet rs = preparedStatement.executeQuery()){  //el try with  resources tambien cierra el result set

            while (rs.next()){         //recorremos el resultset guardando la informacion en variables que despues usamos para construir el objeto
                int id = rs.getInt("curso_id");
                String nombre = rs.getString("nombre");
                int horas = rs.getInt("min_horas_semanales");

                Course curso = new Course(id, nombre, horas);
                listaCursos.add(curso);  //cuando el objeto esta hecho lo añadimos a la lista de cursos
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener los cursos: " + e.getMessage());  //este error salta si hay algun error al obtener los datos de la bd
        }
        return listaCursos;  //regresamos la lista de cursos, la usaremos despues para cargar la tabla en java
    }

    //====== metodo para insertar cursos========
    public boolean insertar(Course curso){           // metodo boolean para retornar si fue o no exitosa la insercion del registro
        String query = "INSERT INTO curso (nombre, min_horas_semanales) VALUES (?,?)";    //prepared statement para insertar, el id se genera automaticamente

        try (Connection connection = DatabaseConnection.getInstance().getConnection();  //try with resources para cerrar conexion y el prepared statement
             PreparedStatement preparedStatement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)  // usamos RETURN_GENERATED_KEYS para devolver el id generado automaticamente despues de hacer la insercion
        ) {
            preparedStatement.setString(1, curso.getNombre());     //con esto colocamos los valores de los "?" en el prepared statement
            preparedStatement.setInt(2, curso.getMinHorasSemanales());

            int filasAfectadas = preparedStatement.executeUpdate();       //el metodo excecuteUpdate devuelve los registros insertados, si es exitoso devolvera uno o varios

            if(filasAfectadas > 0){
                try(ResultSet rsKeys = preparedStatement.getGeneratedKeys()){  //aqui obtenemos las keys autogeneradas (de id en este caso), try with resources por el rs
                    if(rsKeys.next()){
                        curso.setId(rsKeys.getInt(1));    //recorremos las keys autogeneradas (solo una columna, "id" en este caso y las asignamos a los objetos, en este caso solo 1 (curso)
                    }
                }
                return true;  //retornamos verdadero por que la insercion fue exitosa (filasAfectadas > 1)
            }
        } catch (SQLException e){
            System.err.println("Error al insertar el curso: " + e.getMessage());
        }
        return false;
    }

    //====== metodo para actualizar los cursos========
    public boolean actualizar(Course curso){
        String query = "UPDATE curso SET nombre = ?, min_horas_semanales = ? WHERE curso_id = ?";  //actualizar dato, se necesita el id para saber que dato se actualiza

        try(Connection connection = DatabaseConnection.getInstance().getConnection();  //try with resources para cerrar conexion y prepared statement
            PreparedStatement preparedStatement = connection.prepareStatement(query)){

            preparedStatement.setString(1, curso.getNombre());      //le pasamos la informacion al query
            preparedStatement.setInt(2, curso.getMinHorasSemanales());
            preparedStatement.setInt(3, curso.getId());

            int filasAfectadas = preparedStatement.executeUpdate();     // regresa las filas actualizadas, si tiene exito devolvera uno o mas
            return filasAfectadas > 0;        //regresamos verdadero si la actualizacion fue exitosa

        } catch (SQLException e){
            System.err.println("Error al actualizar el curso: " + e.getMessage());
        }
        return false;  //regresamos falso si no se actualizo ningun dato
    }

    //====== metodo para borrar los cursos========
    public boolean eliminar(int idCurso){   //necesitamos un id para eliminar
        String query = "DELETE FROM curso WHERE curso_id = ?";

        try(Connection connection = DatabaseConnection.getInstance().getConnection(); // try with resources
            PreparedStatement preparedStatement = connection.prepareStatement(query)){

            preparedStatement.setInt(1, idCurso);   //le pasamos el indice

            int filasAfectadas = preparedStatement.executeUpdate();  //obtenemos las filas afectadas/eliminadas
            return filasAfectadas > 0; //aqui retornamos verdadero si hubo filas afectadas

        } catch (SQLException e){
            System.err.println("Error al eliminar el curso: " + e.getMessage());
        }
        return false;
    }

    public Course obtenerPorId(int id) {
        Course curso = null;
        String sql = "SELECT * FROM curso WHERE curso_id = ?"; // Asegúrate de usar tus nombres de tabla/columnas reales
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    curso = new Course();
                    curso.setId(rs.getInt("curso_id"));
                    curso.setNombre(rs.getString("nombre"));
                    // curso.setMinHorasSemanales(rs.getInt("min_horas_semanales"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return curso;
    }


}
