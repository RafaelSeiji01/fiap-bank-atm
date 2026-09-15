package com.fiap.bank.atm.application.dto;

import java.util.List;

public record AccountInfoDTO(
        String accountNumber,
        String balance,
        String dailyLimitRemaining,
        List<TransactionDTO> statement) {
}