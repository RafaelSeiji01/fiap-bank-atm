package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import com.fiap.bank.atm.infrastructure.database.DatabaseConnectionFactory;
import com.fiap.bank.atm.infrastructure.database.DatabaseSetup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JDBC (SQLite puro, via PreparedStatement/ResultSet) do
 * contrato AccountRepository ditado pelo domínio.
 */
public class AccountRepositoryJdbcImpl implements AccountRepository {

    private static final String SELECT_ACCOUNT_COLUMNS = "id, number, pin, balance, daily_withdrawal_limit, "
            + "total_withdrawn_today, failed_attempts, status";

    public AccountRepositoryJdbcImpl() {
        DatabaseSetup.criarTabelas();
        seedContasDeTesteSeVazio();
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT " + SELECT_ACCOUNT_COLUMNS + " FROM tb_account WHERE number = ?";
        Connection conn = DatabaseConnectionFactory.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapAccount(rs, conn)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar conta pelo número.", e);
        } finally {
            DatabaseConnectionFactory.closeConnection(conn);
        }
    }

    @Override
    public Optional<Account> buscarPorId(UUID id) {
        String sql = "SELECT " + SELECT_ACCOUNT_COLUMNS + " FROM tb_account WHERE id = ?";
        Connection conn = DatabaseConnectionFactory.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapAccount(rs, conn)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar conta pelo id.", e);
        } finally {
            DatabaseConnectionFactory.closeConnection(conn);
        }
    }

    @Override
    public List<Account> buscarTodos() {
        String sql = "SELECT " + SELECT_ACCOUNT_COLUMNS + " FROM tb_account";
        List<Account> contas = new ArrayList<>();
        Connection conn = DatabaseConnectionFactory.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                contas.add(mapAccount(rs, conn));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar todas as contas.", e);
        } finally {
            DatabaseConnectionFactory.closeConnection(conn);
        }
        return contas;
    }

    @Override
    public void salvar(Account entidade) {
        Connection conn = DatabaseConnectionFactory.getConnection();
        try {
            upsertAccount(conn, entidade);
            reescreverTransacoes(conn, entidade);
        } finally {
            DatabaseConnectionFactory.closeConnection(conn);
        }
    }

    @Override
    public void remover(UUID id) {
        String sql = "DELETE FROM tb_account WHERE id = ?";
        Connection conn = DatabaseConnectionFactory.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover conta.", e);
        } finally {
            DatabaseConnectionFactory.closeConnection(conn);
        }
    }

    private void upsertAccount(Connection conn, Account account) {
        String sql = "INSERT OR REPLACE INTO tb_account "
                + "(id, number, pin, balance, daily_withdrawal_limit, total_withdrawn_today, failed_attempts, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, account.getId().toString());
            stmt.setString(2, account.getAccountNumber());
            stmt.setString(3, account.getPin());
            stmt.setBigDecimal(4, account.getBalance().getAmount());
            stmt.setBigDecimal(5, account.getDailyWithdrawalLimit().getAmount());
            stmt.setBigDecimal(6, account.getTotalWithdrawnToday().getAmount());
            stmt.setInt(7, account.getFailedAttempts());
            stmt.setString(8, account.isBlocked() ? "BLOCKED" : "ACTIVE");
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar conta.", e);
        }
    }

    // Regrava o extrato inteiro da conta a cada salvamento: mais simples e
    // seguro do que rastrear individualmente quais transações já foram
    // persistidas, e o volume de dados aqui não justifica a otimização.
    private void reescreverTransacoes(Connection conn, Account account) {
        String delete = "DELETE FROM tb_transaction WHERE account_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(delete)) {
            stmt.setString(1, account.getId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao limpar o extrato antigo da conta.", e);
        }

        String insert = "INSERT INTO tb_transaction (id, account_id, type, amount, description, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insert)) {
            for (Transaction tx : account.getTransactions()) {
                stmt.setString(1, tx.getId().toString());
                stmt.setString(2, account.getId().toString());
                stmt.setString(3, tx.getType().name());
                stmt.setBigDecimal(4, tx.getAmount().getAmount());
                stmt.setString(5, tx.getDescription());
                stmt.setTimestamp(6, Timestamp.valueOf(tx.getTimestamp()));
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar o extrato da conta.", e);
        }
    }

    private Account mapAccount(ResultSet rs, Connection conn) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        Account account = new Account(
                id,
                rs.getString("number"),
                rs.getString("pin"),
                Money.of(rs.getBigDecimal("balance")),
                Money.of(rs.getBigDecimal("daily_withdrawal_limit")),
                Money.of(rs.getBigDecimal("total_withdrawn_today")),
                "BLOCKED".equals(rs.getString("status")),
                rs.getInt("failed_attempts"));

        for (Transaction tx : buscarTransacoes(conn, id)) {
            account.seedTransaction(tx);
        }
        return account;
    }

    private List<Transaction> buscarTransacoes(Connection conn, UUID accountId) throws SQLException {
        String sql = "SELECT id, type, amount, description, created_at FROM tb_transaction "
                + "WHERE account_id = ? ORDER BY created_at ASC";
        List<Transaction> transacoes = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime timestamp = rs.getTimestamp("created_at").toLocalDateTime();
                    transacoes.add(new Transaction(
                            UUID.fromString(rs.getString("id")),
                            timestamp,
                            TransactionType.valueOf(rs.getString("type")),
                            Money.of(rs.getBigDecimal("amount")),
                            rs.getString("description")));
                }
            }
        }
        return transacoes;
    }

    private void seedContasDeTesteSeVazio() {
        if (!buscarTodos().isEmpty()) {
            return;
        }

        Account acc1 = new Account(UUID.randomUUID(), "12345", "1234", Money.of(5000.00), Money.of(1500.00));
        acc1.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(3),
                TransactionType.DEPOSIT, Money.of(2000.00), "Depósito em dinheiro"));
        acc1.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(2),
                TransactionType.TRANSFER_IN, Money.of(500.00), "Transf. de Conta 67890"));
        acc1.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(1),
                TransactionType.WITHDRAWAL, Money.of(100.00), "Saque eletrônico"));
        salvar(acc1);

        Account acc2 = new Account(UUID.randomUUID(), "67890", "5678", Money.of(1200.00), Money.of(1000.00));
        acc2.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(5),
                TransactionType.DEPOSIT, Money.of(1500.00), "Depósito inicial"));
        acc2.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(2),
                TransactionType.TRANSFER_OUT, Money.of(500.00), "Transf. para Conta 12345"));
        salvar(acc2);

        Account acc3 = new Account(UUID.randomUUID(), "99999", "9999", Money.of(50.00), Money.of(500.00));
        acc3.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(10),
                TransactionType.DEPOSIT, Money.of(50.00), "Abertura de conta"));
        salvar(acc3);
    }
}
