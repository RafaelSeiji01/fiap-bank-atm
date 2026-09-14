package fiap.bank.atm.application.service;

import com.fiap.bank.atm.domain.exception.InvalidPinException;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.util.List;

public class AtmService {
    private final AccountRepository accountRepository;
    private Account currentAccount;

    public AtmService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account authenticate(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber);

        if (account == null) {
            throw new InvalidPinException("Conta não encontrada.");
        }

        try {
            account.authenticate(pin);
            currentAccount = account;
            return account;
        } catch (RuntimeException e) {
            accountRepository.save(account); // Save to persist failed attempts / blocked state
            throw e;
        }
    }

    public void withdraw(double amount) {
        ensureAuthenticated();
        currentAccount.withdraw(Money.of(amount));
        accountRepository.save(currentAccount);
    }

    public void deposit(double amount) {
        ensureAuthenticated();
        currentAccount.deposit(Money.of(amount));
        accountRepository.save(currentAccount);
    }

    public void transfer(String targetAccountNumber, double amount) {
        ensureAuthenticated();

        Account targetAccount = accountRepository.findByAccountNumber(targetAccountNumber);
        if (targetAccount == null) {
            throw new IllegalArgumentException("Conta de destino não encontrada.");
        }
        currentAccount.transfer(targetAccount, Money.of(amount));

        accountRepository.save(currentAccount);
        accountRepository.save(targetAccount);
    }

    public Money getBalance() {
        ensureAuthenticated();
        return currentAccount.getBalance();
    }

    public List<Transaction> getStatement() {
        ensureAuthenticated();
        return currentAccount.getTransactions();
    }

    public void logout() {
        currentAccount = null;
    }

    public Account getCurrentAccount() {
        return currentAccount;
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
