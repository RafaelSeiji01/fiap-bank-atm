package com.fiap.bank.atm.infrastructure.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;

/**
 * Fábrica de conexões com o arquivo SQLite. Centraliza a URL de conexão e o
 * fechamento, para que os repositórios não precisem conhecer detalhes do
 * driver JDBC.
 */
public final class DatabaseConnectionFactory {

    private static final String DEFAULT_DATABASE = "fiapbank.db";

    private DatabaseConnectionFactory() {
    }

    public static Connection getConnection() {
        try {
            String path = System.getProperty("fiapbank.db.path", DEFAULT_DATABASE);
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (PreparedStatement pragma = connection.prepareStatement("PRAGMA foreign_keys = ON")) {
                pragma.execute();
            } catch (SQLException e) {
                connection.close();
                throw e;
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao conectar com o banco de dados SQLite.", e);
        }
    }

    public static void closeConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao encerrar a conexão com o banco de dados SQLite.", e);
        }
    }
}
