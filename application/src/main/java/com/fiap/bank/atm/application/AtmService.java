package com.fiap.bank.atm.application;

import com.fiap.bank.atm.application.dto.AccountInfoDTO;
import com.fiap.bank.atm.application.dto.TransactionDTO;
import com.fiap.bank.atm.domain.exception.InvalidPinException;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.util.List;

public class AtmService {
    private final AccountRepository accountRepository;
    private Account currentAccount;

    public AtmService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountInfoDTO authenticate(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new InvalidPinException("Conta não encontrada."));

        try {
            account.authenticate(pin);
        } finally {
            // Tanto o bloqueio quanto o reset de tentativas devem sobreviver ao restart.
            accountRepository.salvar(account);
        }
        currentAccount = account;

        String balance = account.getBalance().format();
        String limit = account.getDailyWithdrawalLimit().minus(account.getTotalWithdrawnToday()).format();
        List<TransactionDTO> statement = account.getTransactions().stream()
                .map(tx -> new TransactionDTO(
                        tx.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                        tx.getType().getDescription(),
                        tx.getAmount().format()))
                .toList();

        return new AccountInfoDTO(account.getAccountNumber(), balance, limit, statement);
    }

    public void withdraw(double amount) {
        ensureAuthenticated();
        currentAccount.withdraw(Money.of(amount));
        accountRepository.salvar(currentAccount);
    }

    public void deposit(double amount) {
        ensureAuthenticated();
        currentAccount.deposit(Money.of(amount));
        accountRepository.salvar(currentAccount);
    }

    public void transfer(String targetAccountNumber, double amount) {
        ensureAuthenticated();

        Account targetAccount = accountRepository.findByAccountNumber(targetAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada."));

        currentAccount.transfer(targetAccount, Money.of(amount));

        accountRepository.salvarTransferencia(currentAccount, targetAccount);
    }

    public void logout() {
        currentAccount = null;
    }

    public AccountInfoDTO getCurrentAccount() {
        if (currentAccount == null) return null;

        String balance = currentAccount.getBalance().format();
        String limit = currentAccount.getDailyWithdrawalLimit().minus(currentAccount.getTotalWithdrawnToday()).format();

        List<TransactionDTO> statement = currentAccount.getTransactions().stream()
                .map(tx -> new TransactionDTO(
                        tx.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                        tx.getType().getDescription(),
                        tx.getAmount().format()
                ))
                .collect(java.util.stream.Collectors.toList());

        return new AccountInfoDTO(currentAccount.getAccountNumber(), balance, limit, statement);
    }

    public boolean isAuthenticated() {
        return currentAccount != null;
    }

    private void ensureAuthenticated() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("Nenhum usuário está autenticado no momento.");
        }
    }
}
