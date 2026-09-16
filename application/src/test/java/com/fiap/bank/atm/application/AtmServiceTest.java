package com.fiap.bank.atm.application;

import com.fiap.bank.atm.domain.exception.InvalidPinException;
import com.fiap.bank.atm.infrastructure.persistence.AccountRepositoryJdbcImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtmServiceTest {
    @TempDir
    Path tempDir;

    private String previousDatabasePath;

    @BeforeEach
    void useIsolatedDatabase() {
        previousDatabasePath = System.getProperty("fiapbank.db.path");
        System.setProperty("fiapbank.db.path", tempDir.resolve("login-test.db").toString());
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
    void successfulLoginPersistsResetOfFailedPinAttempts() {
        AccountRepositoryJdbcImpl repository = new AccountRepositoryJdbcImpl();
        AtmService service = new AtmService(repository);

        assertThrows(InvalidPinException.class, () -> service.authenticate("12345", "0000"));
        assertEquals(1, new AccountRepositoryJdbcImpl()
                .findByAccountNumber("12345").orElseThrow().getFailedAttempts());

        service.authenticate("12345", "1234");
        assertEquals(0, new AccountRepositoryJdbcImpl()
                .findByAccountNumber("12345").orElseThrow().getFailedAttempts());
    }
}
