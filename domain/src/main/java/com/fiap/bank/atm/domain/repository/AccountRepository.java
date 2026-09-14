package com.fiap.bank.atm.domain.repository;

import com.fiap.bank.atm.domain.model.Account;

public interface AccountRepository {
    Account findByAccountNumber(String accountNumber);

    void save(Account account);
}
