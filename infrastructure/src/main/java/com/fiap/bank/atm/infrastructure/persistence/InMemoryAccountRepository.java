package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryAccountRepository implements AccountRepository {
        private final Map<String, Account> accounts = new HashMap<>();

        public InMemoryAccountRepository() {
                seedData();
        }

        private void seedData() {
                // Conta 1
                Account acc1 = new Account(UUID.randomUUID(), "12345", "1234", Money.of(5000.00), Money.of(1500.00));
                acc1.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(3),
                                TransactionType.DEPOSIT,
                                Money.of(2000.00), "Depósito em dinheiro"));
                acc1.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(2),
                                TransactionType.TRANSFER_IN,
                                Money.of(500.00), "Transf. de Conta 67890"));
                acc1.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(1),
                                TransactionType.WITHDRAWAL,
                                Money.of(100.00), "Saque eletrônico"));
                accounts.put(acc1.getAccountNumber(), acc1);

                // Conta 2
                Account acc2 = new Account(UUID.randomUUID(), "67890", "5678", Money.of(1200.00), Money.of(1000.00));
                acc2.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(5),
                                TransactionType.DEPOSIT,
                                Money.of(1500.00), "Depósito inicial"));
                acc2.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(2),
                                TransactionType.TRANSFER_OUT,
                                Money.of(500.00), "Transf. para Conta 12345"));
                accounts.put(acc2.getAccountNumber(), acc2);

                // Conta 3
                Account acc3 = new Account(UUID.randomUUID(), "99999", "9999", Money.of(50.00), Money.of(500.00));
                acc3.seedTransaction(new Transaction(UUID.randomUUID(), LocalDateTime.now().minusDays(10),
                                TransactionType.DEPOSIT,
                                Money.of(50.00), "Abertura de conta"));
                accounts.put(acc3.getAccountNumber(), acc3);
        }

        @Override
        public Account findByAccountNumber(String accountNumber) {
                // Return a reference (or clone, but reference works for state updates in memory
                // repository)
                return accounts.get(accountNumber);
        }

        @Override
        public void save(Account account) {
                accounts.put(account.getAccountNumber(), account);
        }
}
