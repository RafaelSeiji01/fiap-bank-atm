package com.fiap.bank.atm.domain.model;

public enum TransactionType {
    WITHDRAWAL("Saque"),
    DEPOSIT("Depósito"),
    TRANSFER_OUT("Transf. Enviada"),
    TRANSFER_IN("Transf. Recebida");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
