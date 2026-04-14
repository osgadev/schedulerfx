package com.osgadev.organizadorhorariosfx.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String DATABASE_NAME = "organizador_db";
    private static final String URL = "jdbc:mysql://localhost/" + DATABASE_NAME;
    private static final String USER = "root";
    private static final String PASSWORD = "webmaster";

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    //hacemos uso de singleton
    private static DatabaseConnection instance; //<- variable que guarda la INSTANCIA

    private Connection connection;

    private DatabaseConnection(){ //<- constructor PRIVADO para singleton
        try{
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e){
            e.printStackTrace();
        }
    }

    public static synchronized DatabaseConnection getInstance(){ // <- metodo ESTATICO que usamos para obtener la instancia
        if(instance ==  null){
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException{   // metodo para obtener la conexion a la base de datos el driver
        if(connection == null || connection.isClosed()){
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        }
        return connection;
    }

    public void closeConnection(){
        if(connection != null){
            try {
                connection.close();
            } catch (SQLException e){
                e.printStackTrace();
            } finally {
                connection = null;
            }
        }
    }



}
