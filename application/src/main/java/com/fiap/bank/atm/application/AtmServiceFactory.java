package com.fiap.bank.atm.application;

import com.fiap.bank.atm.domain.repository.AccountRepository;
import com.fiap.bank.atm.infrastructure.persistence.AccountRepositoryJdbcImpl;

/**
 * Fábrica de composição: monta o AtmService com sua implementação concreta de
 * repositório. Mantém a camada de apresentação cega a domain/infrastructure,
 * já que ela só enxerga este módulo (application).
 */
public final class AtmServiceFactory {

    private AtmServiceFactory() {
    }

    public static AtmService createDefault() {
        AccountRepository accountRepository = new AccountRepositoryJdbcImpl();
        return new AtmService(accountRepository);
    }
}
