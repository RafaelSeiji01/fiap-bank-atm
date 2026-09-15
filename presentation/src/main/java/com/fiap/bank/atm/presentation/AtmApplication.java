package com.fiap.bank.atm.presentation;

import com.fiap.bank.atm.application.AtmService;
import com.fiap.bank.atm.application.AtmServiceFactory;
import javax.swing.SwingUtilities;

public class AtmApplication {
    public static void main(String[] args) {
        // A camada de apresentação só enxerga a camada de aplicação; a fábrica
        // é quem sabe montar a implementação concreta do repositório.
        AtmService atmService = AtmServiceFactory.createDefault();
        // Inicializa a camada de Apresentação de forma segura na Event Dispatch Thread
        // (EDT)
        SwingUtilities.invokeLater(() -> {
            AtmFrame mainFrame = new AtmFrame(atmService);
            mainFrame.setVisible(true);
        });
    }
}
