package com.fiap.bank.atm.infrastructure.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fábrica de conexões com o arquivo SQLite. Centraliza a URL de conexão e o
 * fechamento, para que os repositórios não precisem conhecer detalhes do
 * driver JDBC.
 */
public final class DatabaseConnectionFactory {

    private static final String URL = "jdbc:sqlite:fiapbank.db";

    private DatabaseConnectionFactory() {
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL);
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
