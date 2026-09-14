package com.fiap.bank.atm.presentation;

import com.fiap.bank.atm.application.AtmService;
import javax.swing.SwingUtilities;

public class AtmApplication {
    public static void main(String[] args) {
        // Inicializa as camadas de Infraestrutura e Aplicação (DDD)
        //AccountRepository accountRepository = new InMemoryAccountRepository();
        //AtmService atmService = new AtmService(accountRepository);
        AtmService atmService = null;
        // Inicializa a camada de Apresentação de forma segura na Event Dispatch Thread
        // (EDT)
        SwingUtilities.invokeLater(() -> {
            AtmFrame mainFrame = new AtmFrame(atmService);
            mainFrame.setVisible(true);
        });
    }
}
