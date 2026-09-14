package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import java.util.HashMap;
import java.util.Map;

public class ContaCacheRepository {

    // K = String (Ex: "0001-12345"), V = Conta
    private Map<String, Account> cacheContas = new HashMap<>();

    // Helper para padronizar a chave composta
    private String gerarChave(String agencia, String numero) {
        return agencia + "-" + numero;
    }

    public void adicionar(Account entidade) {
        //String chave = gerarChave(entidade.getAgencia(), entidade.getAccountNumber());
        //cacheContas.put(chave, entidade);

        // retirando o getAgencia por nao ter na classe account
        String chave = entidade.getAccountNumber();
        cacheContas.put(chave, entidade);
    }

    // Busca O(1) de altíssima performance (mas ainda retornando null se não achar)
    public Account validarContaNoAtm(String agencia, String numero) {
        return cacheContas.get(gerarChave(agencia, numero));
    }
}