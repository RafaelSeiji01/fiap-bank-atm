package com.fiap.bank.atm.infrastructure.database;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Cria o schema do banco (tabelas tb_account e tb_transaction) caso ainda
 * não exista. Rodado uma vez na inicialização da aplicação.
 */
public final class DatabaseSetup {

    private DatabaseSetup() {
    }

    public static void criarTabelas() {
        String sqlAccount = """
                CREATE TABLE IF NOT EXISTS tb_account (
                    id VARCHAR(36) PRIMARY KEY,
                    agency VARCHAR(10) NOT NULL,
                    number VARCHAR(20) NOT NULL,
                    balance DECIMAL(15, 2) NOT NULL,
                    status VARCHAR(20) NOT NULL
                );""";

        String sqlTransaction = """
                CREATE TABLE IF NOT EXISTS tb_transaction (
                    id VARCHAR(36) PRIMARY KEY,
                    account_id VARCHAR(36) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES tb_account(id)
                );""";

        Connection conn = DatabaseConnectionFactory.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sqlAccount);
            stmt.execute(sqlTransaction);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar as tabelas do banco de dados.", e);
        } finally {
            DatabaseConnectionFactory.closeConnection(conn);
        }
    }
}
