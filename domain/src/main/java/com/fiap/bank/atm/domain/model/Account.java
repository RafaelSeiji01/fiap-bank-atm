package com.fiap.bank.atm.domain.model;

import com.fiap.bank.atm.domain.exception.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Account extends BaseEntity {
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final String accountNumber;
    private final String pin;
    private Money balance;
    private final Money dailyWithdrawalLimit;
    private Money totalWithdrawnToday;
    private boolean blocked;
    private int failedAttempts;
    private final List<Transaction> transactions;

    public Account(UUID id, String accountNumber, String pin, Money initialBalance, Money dailyWithdrawalLimit) {
        super(id);
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        this.pin = Objects.requireNonNull(pin, "PIN cannot be null");
        this.balance = Objects.requireNonNull(initialBalance, "Initial balance cannot be null");
        this.dailyWithdrawalLimit = Objects.requireNonNull(dailyWithdrawalLimit, "Daily limit cannot be null");
        this.totalWithdrawnToday = Money.ZERO;
        this.blocked = false;
        this.failedAttempts = 0;
        this.transactions = new ArrayList<>();
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Money getBalance() {
        return balance;
    }

    public Money getDailyWithdrawalLimit() {
        return dailyWithdrawalLimit;
    }

    public Money getTotalWithdrawnToday() {
        return totalWithdrawnToday;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public void authenticate(String pinAttempt) {
        if (blocked) {
            throw new AccountBlockedException("Esta conta está bloqueada por excesso de tentativas de senha.");
        }

        if (!this.pin.equals(pinAttempt)) {
            failedAttempts++;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                blocked = true;
                throw new AccountBlockedException(
                        "Conta bloqueada após " + MAX_FAILED_ATTEMPTS + " tentativas incorretas.");
            }
            throw new InvalidPinException(
                    "Senha incorreta. Tentativa " + failedAttempts + " de " + MAX_FAILED_ATTEMPTS + ".");
        }

        failedAttempts = 0; // Reset attempts on successful login
    }

    public void withdraw(Money amount) {
        if (blocked) {
            throw new AccountBlockedException("Operação não permitida: conta bloqueada.");
        }

        if (amount.isLessThan(Money.of(0.01))) {
            throw new IllegalArgumentException("O valor do saque deve ser maior que zero.");
        }

        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException(
                    "Saldo insuficiente para realizar o saque. Saldo disponível: " + balance);
        }

        Money projectedWithdrawal = totalWithdrawnToday.plus(amount);
        if (projectedWithdrawal.isGreaterThan(dailyWithdrawalLimit)) {
            throw new DailyLimitExceededException("Limite diário de saque excedido. Limite restante hoje: "
                    + dailyWithdrawalLimit.minus(totalWithdrawnToday));
        }

        balance = balance.minus(amount);
        totalWithdrawnToday = totalWithdrawnToday.plus(amount);

        transactions.add(new Transaction(UUID.randomUUID(), TransactionType.WITHDRAWAL, amount, "Saque eletrônico"));
    }

    public void deposit(Money amount) {
        if (blocked) {
            throw new AccountBlockedException("Operação não permitida: conta bloqueada.");
        }

        if (amount.isLessThan(Money.of(0.01))) {
            throw new IllegalArgumentException("O valor do depósito deve ser maior que zero.");
        }

        balance = balance.plus(amount);
        transactions.add(new Transaction(UUID.randomUUID(), TransactionType.DEPOSIT, amount, "Depósito em dinheiro"));
    }

    public void transfer(Account targetAccount, Money amount) {
        if (blocked) {
            throw new AccountBlockedException("Operação não permitida: conta de origem bloqueada.");
        }

        if (targetAccount.isBlocked()) {
            throw new AccountBlockedException("Operação não permitida: conta de destino está bloqueada.");
        }

        if (amount.isLessThan(Money.of(0.01))) {
            throw new IllegalArgumentException("O valor da transferência deve ser maior que zero.");
        }

        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException("Saldo insuficiente para transferência. Saldo disponível: " + balance);
        }

        if (this.accountNumber.equals(targetAccount.getAccountNumber())) {
            throw new IllegalArgumentException("Não é possível realizar transferência para a mesma conta.");
        }

        // Debita a conta de origem
        this.balance = this.balance.minus(amount);
        this.transactions.add(new Transaction(
                UUID.randomUUID(),
                TransactionType.TRANSFER_OUT,
                amount,
                "Transf. para Conta " + targetAccount.getAccountNumber()));

        // Credita a conta de destino
        targetAccount.receiveTransfer(this, amount);
    }

    private void receiveTransfer(Account sourceAccount, Money amount) {
        this.balance = this.balance.plus(amount);
        this.transactions.add(new Transaction(
                UUID.randomUUID(),
                TransactionType.TRANSFER_IN,
                amount,
                "Transf. de Conta " + sourceAccount.getAccountNumber()));
    }

    // Helper for seeding transactions
    public void seedTransaction(Transaction transaction) {
        this.transactions.add(transaction);
    }
}
