package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountRepositoryJdbcImplTest {
    @TempDir
    Path tempDir;

    private String previousDatabasePath;

    @BeforeEach
    void useIsolatedDatabase() {
        previousDatabasePath = System.getProperty("fiapbank.db.path");
        System.setProperty("fiapbank.db.path", tempDir.resolve("atm-test.db").toString());
    }

    @AfterEach
    void restoreDatabasePath() {
        if (previousDatabasePath == null) {
            System.clearProperty("fiapbank.db.path");
        } else {
            System.setProperty("fiapbank.db.path", previousDatabasePath);
        }
    }

    @Test
    void savedDepositSurvivesRestartWithoutDuplicatingTransactions() {
        AccountRepositoryJdbcImpl repository = new AccountRepositoryJdbcImpl();
        Account account = repository.findByAccountNumber("12345").orElseThrow();

        account.deposit(Money.of(12.34));
        repository.salvar(account);
        repository.salvar(account);

        Account reloaded = new AccountRepositoryJdbcImpl()
                .findByAccountNumber("12345").orElseThrow();
        assertEquals(Money.of(5012.34), reloaded.getBalance());
        assertEquals(4, reloaded.getTransactions().size());
    }

    @Test
    void transferPersistsBothAccountsTogether() {
        AccountRepositoryJdbcImpl repository = new AccountRepositoryJdbcImpl();
        Account source = repository.findByAccountNumber("12345").orElseThrow();
        Account target = repository.findByAccountNumber("67890").orElseThrow();

        source.transfer(target, Money.of(100));
        repository.salvarTransferencia(source, target);

        AccountRepositoryJdbcImpl restarted = new AccountRepositoryJdbcImpl();
        Account savedSource = restarted.findByAccountNumber("12345").orElseThrow();
        Account savedTarget = restarted.findByAccountNumber("67890").orElseThrow();
        assertEquals(Money.of(4900), savedSource.getBalance());
        assertEquals(Money.of(1300), savedTarget.getBalance());
        assertEquals(4, savedSource.getTransactions().size());
        assertEquals(3, savedTarget.getTransactions().size());
    }

    @Test
    void failedSecondAccountRollsBackFirstAccount() {
        AccountRepositoryJdbcImpl repository = new AccountRepositoryJdbcImpl();
        Account source = repository.findByAccountNumber("12345").orElseThrow();
        source.deposit(Money.of(20));
        Account duplicateNumber = new Account(UUID.randomUUID(), "12345", "0000",
                Money.of(1), Money.of(100));

        assertThrows(RuntimeException.class,
                () -> repository.salvarTransferencia(source, duplicateNumber));

        Account reloaded = repository.findByAccountNumber("12345").orElseThrow();
        assertEquals(Money.of(5000), reloaded.getBalance());
        assertEquals(3, reloaded.getTransactions().size());
    }
    @Test
    void controleDeSaqueDiarioSobrevivaAoRestartEZeraNoDiaSeguinte() {
        AccountRepositoryJdbcImpl repository = new AccountRepositoryJdbcImpl();
        Account conta = repository.findByAccountNumber("12345").orElseThrow(); // limite diário: 1500,00

        conta.withdraw(Money.of(300)); // saque de hoje
        repository.salvar(conta);

        AccountRepositoryJdbcImpl restarted = new AccountRepositoryJdbcImpl();
        Account recarregada = restarted.findByAccountNumber("12345").orElseThrow();

        assertEquals(Money.of(300), recarregada.getTotalWithdrawnToday());

        Account contaDeOntem = new Account(recarregada.getId(), recarregada.getAccountNumber(), recarregada.getPin(),
                recarregada.getBalance(), recarregada.getDailyWithdrawalLimit(), Money.of(300), false, 0,
                LocalDate.now().minusDays(1));

        assertEquals(Money.ZERO, contaDeOntem.getTotalWithdrawnToday());
        contaDeOntem.withdraw(Money.of(1500));
        assertEquals(Money.of(1500), contaDeOntem.getTotalWithdrawnToday());
    }
}
