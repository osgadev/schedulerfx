package com.osgadev.organizadorhorariosfx.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    // Uso de ambas bases de datos habilitadaa
    // Cambiar a "MYSQL" o "SQLITE",   SQLite embebido (automatico)     para MySQL cargar el script que se encuentra en resources
    private static final String DB_TIPO = "SQLITE";

    // Configuración MySQL
    private static final String MYSQL_DB_NAME = "organizador_db";
    private static final String MYSQL_URL = "jdbc:mysql://localhost/" + MYSQL_DB_NAME;
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "webmaster";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    // Configuración SQLite
    private static final String SQLITE_DB_NAME = "organizador.db";
    private static final String SQLITE_APP_FOLDER = ".OrganizadorApp";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String SQLITE_SCRIPT_PATH = "/db/organizador_sqlite3.sql";

    // Variables del Singleton
    private static DatabaseConnection instance;
    private Connection connection;

    // Constructor privado
    private DatabaseConnection() {
        System.out.println("=== SISTEMA DE BASE DE DATOS INICIADO EN MODO: " + DB_TIPO + " ===");
        try {
            if ("MYSQL".equals(DB_TIPO)) {
                Class.forName(MYSQL_DRIVER);
            } else if ("SQLITE".equals(DB_TIPO)) {
                Class.forName(SQLITE_DRIVER);
            } else {
                throw new IllegalStateException("Tipo de base de datos no soportado: " + DB_TIPO);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Error al cargar el driver de " + DB_TIPO + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Obtener la instancia singleton
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    // Obtener la conexion
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {

            if ("MYSQL".equals(DB_TIPO)) {
                connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
                System.out.println("[MYSQL] Conexión exitosa a la base de datos: " + MYSQL_DB_NAME);

            } else if ("SQLITE".equals(DB_TIPO)) {
                String userHome = System.getProperty("user.home");
                File appDirectorio = new File(userHome, SQLITE_APP_FOLDER);

                if (!appDirectorio.exists() && !appDirectorio.mkdirs()) {
                    throw new SQLException("No se pudo crear el directorio de la app: " + appDirectorio.getAbsolutePath());
                }

                File bdArchivo = new File(appDirectorio, SQLITE_DB_NAME);
                boolean esBaseDeDatosNueva = !bdArchivo.exists();

                String url = "jdbc:sqlite:" + bdArchivo.getAbsolutePath();
                connection = DriverManager.getConnection(url);

                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON");
                }

                if (esBaseDeDatosNueva) {
                    System.out.println("[SQLITE] Creando base de datos nueva e inicializando tablas en: " + bdArchivo.getAbsolutePath());
                    inicializarBaseDeDatosSQLite(connection);
                } else {
                    System.out.println("[SQLITE] Usando base de datos existente en: " + bdArchivo.getAbsolutePath());
                }
            }
        }
        return connection;
    }

    // Cerrar la conexion
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[" + DB_TIPO + "] Conexión cerrada correctamente.");
            } catch (SQLException e) {
                System.err.println("Error al cerrar la conexión: " + e.getMessage());
                e.printStackTrace();
            } finally {
                connection = null;
            }
        }
    }

    // Metodo para crear las tablas si la BD de SQLite es nueva
    private void inicializarBaseDeDatosSQLite(Connection conn) {
        boolean autoCommitAnterior = true;

        try (InputStream in = getClass().getResourceAsStream(SQLITE_SCRIPT_PATH)) {
            if (in == null) {
                System.err.println("[SQLITE - ERROR] No se encontró el archivo " + SQLITE_SCRIPT_PATH + " en resources.");
                return;
            }

            StringBuilder sqlCompleto = new StringBuilder();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String limpia = linea.trim();

                    if (limpia.isEmpty()) {
                        continue;
                    }

                    if (limpia.startsWith("--")) {
                        continue;
                    }

                    sqlCompleto.append(linea).append("\n");
                }
            }

            autoCommitAnterior = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");

                String[] sentencias = sqlCompleto.toString().split(";");

                for (String sentencia : sentencias) {
                    String sql = sentencia.trim();

                    if (sql.isEmpty()) {
                        continue;
                    }

                    stmt.execute(sql);
                }
            }

            conn.commit();
            System.out.println("[SQLITE] Todas las tablas fueron creadas exitosamente desde el script.");

        } catch (Exception e) {
            try {
                conn.rollback();
                System.err.println("[SQLITE] Se hizo rollback por error durante la inicialización.");
            } catch (SQLException rollbackEx) {
                System.err.println("[SQLITE - ERROR] Falló el rollback: " + rollbackEx.getMessage());
                rollbackEx.printStackTrace();
            }

            System.err.println("[SQLITE - ERROR] Fallo al ejecutar el script: " + e.getMessage());
            e.printStackTrace();

        } finally {
            try {
                conn.setAutoCommit(autoCommitAnterior);
            } catch (SQLException e) {
                System.err.println("[SQLITE - ERROR] No se pudo restaurar autoCommit: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}