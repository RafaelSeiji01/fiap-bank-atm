package com.fiap.bank.atm.application.dto;

public record TransactionDTO(String date,
                             String description,
                             String formattedAmount
) {
}
