package com.fiap.bank.atm.infrastructure.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Cria o schema do banco (tabelas tb_account e tb_transaction) caso ainda
 * não exista. Rodado uma vez na inicialização da aplicação.
 */
public final class DatabaseSetup {

    private DatabaseSetup() {
    }

    public static void criarTabelas() {
        // Estende o schema sugerido no Anexo 7.2 do checkpoint (que só previa
        // id/agency/number/balance/status) com as colunas que a entidade
        // Account de fato usa: pin, limite diário, total sacado hoje e
        // tentativas falhas. Sem elas, autenticação e limite diário não
        // sobreviveriam a um restart.
        String sqlAccount = """
                CREATE TABLE IF NOT EXISTS tb_account (
                    id VARCHAR(36) PRIMARY KEY,
                    number VARCHAR(20) NOT NULL UNIQUE,
                    pin VARCHAR(4) NOT NULL,
                    balance DECIMAL(15, 2) NOT NULL,
                    daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
                    total_withdrawn_today DECIMAL(15, 2) NOT NULL,
                    failed_attempts INTEGER NOT NULL,
                    status VARCHAR(20) NOT NULL,
                     last_withdrawal_date VARCHAR(10)
                );""";

        String sqlTransaction = """
                CREATE TABLE IF NOT EXISTS tb_transaction (
                    id VARCHAR(36) PRIMARY KEY,
                    account_id VARCHAR(36) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    description VARCHAR(255),
                    created_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES tb_account(id)
                );""";

        Connection conn = DatabaseConnectionFactory.getConnection();
        try (PreparedStatement accounts = conn.prepareStatement(sqlAccount);
                PreparedStatement transactions = conn.prepareStatement(sqlTransaction)) {
            accounts.execute();
            transactions.execute();
            migrarColunaDataControleSaque(conn);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar as tabelas do banco de dados.", e);
        } finally {
            DatabaseConnectionFactory.closeConnection(conn);
        }
    }
    private static void migrarColunaDataControleSaque(Connection conn) throws java.sql.SQLException {
        if (colunaExiste(conn, "tb_account", "last_withdrawal_date")) {
            return;
        }
        try (PreparedStatement alter = conn
                .prepareStatement("ALTER TABLE tb_account ADD COLUMN last_withdrawal_date VARCHAR(10)")) {
            alter.execute();
        }
    }

    private static boolean colunaExiste(Connection conn, String tabela, String coluna) throws java.sql.SQLException {
        try (PreparedStatement pragma = conn.prepareStatement("PRAGMA table_info(" + tabela + ")");
             ResultSet rs = pragma.executeQuery()) {
            while (rs.next()) {
                if (coluna.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
