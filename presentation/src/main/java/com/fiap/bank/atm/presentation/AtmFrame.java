package com.fiap.bank.atm.presentation;

import com.formdev.flatlaf.FlatDarkLaf;
import com.fiap.bank.atm.application.AtmService;
import com.fiap.bank.atm.domain.exception.AccountBlockedException;
import com.fiap.bank.atm.domain.exception.DailyLimitExceededException;
import com.fiap.bank.atm.domain.exception.InsufficientFundsException;
import com.fiap.bank.atm.domain.exception.InvalidPinException;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Transaction;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AtmFrame extends JFrame {

    private final AtmService atmService;
    private ScreenState currentState;
    private final StringBuilder inputBuffer;
    private String targetAccountNumber;
    private String errorMessage;

    // Animation timers & state
    private Timer cardLedTimer;
    private boolean cardLedOn = true;
    private Timer cashAnimationTimer;
    private Timer printAnimationTimer;
    private Timer successTimer;

    // Virtual receipt paper component
    private JDialog receiptDialog;
    private JTextArea txtReceiptPaper;

    public AtmFrame(AtmService atmService) {
        this.atmService = atmService;
        this.inputBuffer = new StringBuilder();
        this.currentState = ScreenState.WELCOME;

        // Configura Look and Feel FlatLaf
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Falha ao inicializar o FlatLaf Look and Feel");
        }

        initComponents();
        setupCustomStyles();
        setupListeners();
        setupTimers();
        setupKeyboardInterception();

        updateScreen();
    }

    private void setupCustomStyles() {
        // Estilização premium da tela digital (CRT/LCD look)
        jPanelScreen.setBackground(new Color(11, 18, 28)); // Slate azul muito escuro
        jPanelScreenHeader.setBackground(new Color(11, 18, 28));
        jPanelScreenCenter.setBackground(new Color(11, 18, 28));
        jPanelScreenLeftLabels.setBackground(new Color(11, 18, 28));
        jPanelScreenRightLabels.setBackground(new Color(11, 18, 28));

        lblScreenHeader.setForeground(new Color(254, 240, 138)); // Amarelo néon suave
        lblScreenStatus.setForeground(new Color(241, 245, 249)); // Branco suave
        lblScreenInput.setForeground(new Color(56, 189, 248)); // Ciano brilhante
        lblScreenMessage.setForeground(new Color(234, 113, 113)); // Vermelho claro para alertas

        // Estilizar os botões físicos laterais
        JButton[] sideButtons = { btnLeft1, btnLeft2, btnLeft3, btnRight1, btnRight2, btnRight3 };
        for (JButton btn : sideButtons) {
            btn.setBackground(new Color(51, 65, 85)); // Aço cinza escuro
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            btn.setBorder(new LineBorder(new Color(71, 85, 105), 2));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        // Estilizar teclado numérico
        JButton[] numericButtons = { btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9, btn0, btnBlank, btnC };
        for (JButton btn : numericButtons) {
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            if (btn == btnC) {
                btn.setBackground(new Color(189, 58, 58)); // Vermelho cancelamento
                btn.setForeground(Color.WHITE);
                btn.setBorder(new LineBorder(new Color(220, 80, 80), 2));
            } else if (btn == btnBlank) {
                btn.setBackground(new Color(30, 41, 59));
                btn.setForeground(new Color(148, 163, 184));
                btn.setBorder(new LineBorder(new Color(71, 85, 105), 1));
            } else {
                btn.setBackground(new Color(30, 41, 59)); // Slate
                btn.setForeground(new Color(241, 245, 249));
                btn.setBorder(new LineBorder(new Color(71, 85, 105), 2));
            }
        }

        // Estilizar contêineres de periféricos
        cardSlotContainer.setBackground(new Color(18, 27, 38));
        receiptPrinterContainer.setBackground(new Color(18, 27, 38));
        cashDispenserContainer.setBackground(new Color(18, 27, 38));
    }

    private void setupTimers() {
        // LED de Cartão piscando no WELCOME
        cardLedTimer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentState == ScreenState.WELCOME) {
                    cardLedOn = !cardLedOn;
                    if (cardLedOn) {
                        lblCardIndicatorLed.setForeground(new Color(80, 200, 80));
                        lblCardIndicatorLed.setText("● INSERIR CARTÃO");
                    } else {
                        lblCardIndicatorLed.setForeground(new Color(30, 70, 30));
                        lblCardIndicatorLed.setText("  INSERIR CARTÃO");
                    }
                }
            }
        });
        cardLedTimer.start();

        // Temporizador para dispensar dinheiro
        cashAnimationTimer = new Timer(3000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cashAnimationTimer.stop();
                lblCashDispenserStatus.setText("FECHADO");
                lblCashDispenserStatus.setForeground(Color.GRAY);
                cashDispenserContainer.setBackground(new Color(18, 27, 38));

                // Transiciona para tela final
                currentState = ScreenState.SUCCESS;
                updateScreen();
                successTimer.start();
            }
        });

        // Temporizador para simulação da impressão
        printAnimationTimer = new Timer(3000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                printAnimationTimer.stop();
                lblPrinterStatus.setText("PRONTA");
                lblPrinterStatus.setForeground(Color.LIGHT_GRAY);
                receiptPrinterContainer.setBackground(new Color(18, 27, 38));

                // Mostrar o extrato impresso na tela
                showVirtualReceipt();

                currentState = ScreenState.SUCCESS;
                updateScreen();
                successTimer.start();
            }
        });

        // Temporizador de tela de sucesso (4s e depois volta para o menu ou tela
        // inicial)
        successTimer = new Timer(4000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                successTimer.stop();
                if (atmService.isAuthenticated()) {
                    currentState = ScreenState.MAIN_MENU;
                } else {
                    currentState = ScreenState.WELCOME;
                }
                inputBuffer.setLength(0);
                updateScreen();
            }
        });
    }

    private void setupKeyboardInterception() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                char keyChar = e.getKeyChar();
                int keyCode = e.getKeyCode();

                if (Character.isDigit(keyChar)) {
                    handleKeyInput(String.valueOf(keyChar));
                    return true;
                } else if (keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_ESCAPE) {
                    handleClearOrCancel();
                    return true;
                } else if (keyCode == KeyEvent.VK_ENTER) {
                    handleConfirm();
                    return true;
                }
            }
            return false;
        });
    }

    private void setupListeners() {
        // Configura cliques nos botões numéricos
        JButton[] numButtons = { btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9, btn0 };
        for (JButton btn : numButtons) {
            btn.addActionListener(e -> handleKeyInput(btn.getText()));
        }

        btnC.addActionListener(e -> handleClearOrCancel());
        btnBlank.addActionListener(e -> handleConfirm());

        // Botões físicos laterais
        btnLeft1.addActionListener(e -> handleSideButton("L1"));
        btnLeft2.addActionListener(e -> handleSideButton("L2"));
        btnLeft3.addActionListener(e -> handleSideButton("L3"));
        btnRight1.addActionListener(e -> handleSideButton("R1"));
        btnRight2.addActionListener(e -> handleSideButton("R2"));
        btnRight3.addActionListener(e -> handleSideButton("R3"));
    }

    private void handleKeyInput(String text) {
        if (isAnimationState())
            return;

        // Limita tamanho do input de acordo com o estado
        if (currentState == ScreenState.WELCOME) {
            if (inputBuffer.length() < 10) {
                inputBuffer.append(text);
            }
        } else if (currentState == ScreenState.ENTER_PIN) {
            if (inputBuffer.length() < 4) {
                inputBuffer.append(text);
            }
        } else if (currentState == ScreenState.WITHDRAW_CUSTOM || currentState == ScreenState.DEPOSIT_INPUT
                || currentState == ScreenState.TRANSFER_VALUE) {
            if (inputBuffer.length() < 7) { // Permite até R$ 9.999,99
                inputBuffer.append(text);
            }
        } else if (currentState == ScreenState.TRANSFER_ACCOUNT) {
            if (inputBuffer.length() < 10) {
                inputBuffer.append(text);
            }
        }
        updateScreen();
    }

    private void handleClearOrCancel() {
        if (isAnimationState())
            return;

        if (inputBuffer.length() > 0) {
            inputBuffer.setLength(inputBuffer.length() - 1);
            updateScreen();
        } else {
            // Se buffer vazio, o botão C cancela a operação ou volta de tela
            switch (currentState) {
                case ENTER_PIN:
                    atmService.logout();
                    currentState = ScreenState.WELCOME;
                    break;
                case WITHDRAW_SELECT:
                case DEPOSIT_INPUT:
                case TRANSFER_ACCOUNT:
                case SHOW_BALANCE:
                case SHOW_STATEMENT:
                    currentState = ScreenState.MAIN_MENU;
                    break;
                case WITHDRAW_CUSTOM:
                    currentState = ScreenState.WITHDRAW_SELECT;
                    break;
                case TRANSFER_VALUE:
                    currentState = ScreenState.TRANSFER_ACCOUNT;
                    break;
                case MAIN_MENU:
                    atmService.logout();
                    currentState = ScreenState.WELCOME;
                    break;
                case ERROR:
                    if (atmService.isAuthenticated()) {
                        currentState = ScreenState.MAIN_MENU;
                    } else {
                        currentState = ScreenState.WELCOME;
                    }
                    break;
                default:
                    // Sem ação
                    break;
            }
            inputBuffer.setLength(0);
            updateScreen();
        }
    }

    private void handleConfirm() {
        if (isAnimationState())
            return;

        try {
            switch (currentState) {
                case WELCOME:
                    if (inputBuffer.length() > 0) {
                        targetAccountNumber = inputBuffer.toString();
                        inputBuffer.setLength(0);
                        currentState = ScreenState.ENTER_PIN;
                    } else {
                        errorMessage = "DIGITE O NÚMERO DA CONTA";
                        currentState = ScreenState.ERROR;
                    }
                    break;

                case ENTER_PIN:
                    if (inputBuffer.length() == 4) {
                        String pin = inputBuffer.toString();
                        inputBuffer.setLength(0);
                        atmService.authenticate(targetAccountNumber, pin);
                        currentState = ScreenState.MAIN_MENU;
                    } else {
                        errorMessage = "DIGITE A SENHA DE 4 DÍGITOS";
                        currentState = ScreenState.ERROR;
                    }
                    break;

                case WITHDRAW_CUSTOM:
                    if (inputBuffer.length() > 0) {
                        double val = Double.parseDouble(inputBuffer.toString());
                        inputBuffer.setLength(0);
                        triggerCashWithdrawal(val);
                    } else {
                        errorMessage = "DIGITE UM VALOR VÁLIDO";
                        currentState = ScreenState.ERROR;
                    }
                    break;

                case DEPOSIT_INPUT:
                    if (inputBuffer.length() > 0) {
                        double val = Double.parseDouble(inputBuffer.toString());
                        inputBuffer.setLength(0);
                        triggerDeposit(val);
                    } else {
                        errorMessage = "DIGITE UM VALOR VÁLIDO";
                        currentState = ScreenState.ERROR;
                    }
                    break;

                case TRANSFER_ACCOUNT:
                    if (inputBuffer.length() > 0) {
                        targetAccountNumber = inputBuffer.toString();
                        inputBuffer.setLength(0);
                        currentState = ScreenState.TRANSFER_VALUE;
                    } else {
                        errorMessage = "INSIRA A CONTA DESTINO";
                        currentState = ScreenState.ERROR;
                    }
                    break;

                case TRANSFER_VALUE:
                    if (inputBuffer.length() > 0) {
                        double val = Double.parseDouble(inputBuffer.toString());
                        inputBuffer.setLength(0);
                        atmService.transfer(targetAccountNumber, val);
                        currentState = ScreenState.SUCCESS;
                    } else {
                        errorMessage = "DIGITE UM VALOR VÁLIDO";
                        currentState = ScreenState.ERROR;
                    }
                    break;

                default:
                    // Sem ação no confirm para outros estados
                    break;
            }
        } catch (AccountBlockedException ex) {
            errorMessage = "CONTA BLOQUEADA!";
            currentState = ScreenState.ERROR;
        } catch (InvalidPinException ex) {
            errorMessage = "SENHA INCORRETA!";
            currentState = ScreenState.ERROR;
        } catch (InsufficientFundsException ex) {
            errorMessage = "SALDO INSUFICIENTE!";
            currentState = ScreenState.ERROR;
        } catch (DailyLimitExceededException ex) {
            errorMessage = "LIMITE DIÁRIO EXCEDIDO!";
            currentState = ScreenState.ERROR;
        } catch (IllegalArgumentException ex) {
            errorMessage = ex.getMessage().toUpperCase();
            currentState = ScreenState.ERROR;
        } catch (Exception ex) {
            errorMessage = "ERRO NO SISTEMA";
            currentState = ScreenState.ERROR;
        }
        updateScreen();
    }

    private void handleSideButton(String btnId) {
        if (isAnimationState())
            return;

        switch (currentState) {
            case MAIN_MENU:
                if (btnId.equals("L1")) { // Saque
                    currentState = ScreenState.WITHDRAW_SELECT;
                } else if (btnId.equals("L2")) { // Depósito
                    currentState = ScreenState.DEPOSIT_INPUT;
                } else if (btnId.equals("L3")) { // Transferência
                    currentState = ScreenState.TRANSFER_ACCOUNT;
                } else if (btnId.equals("R1")) { // Saldo
                    currentState = ScreenState.SHOW_BALANCE;
                } else if (btnId.equals("R2")) { // Extrato
                    triggerPrintStatement();
                } else if (btnId.equals("R3")) { // Sair
                    atmService.logout();
                    currentState = ScreenState.WELCOME;
                }
                break;

            case WITHDRAW_SELECT:
                if (btnId.equals("L1")) {
                    triggerCashWithdrawal(20);
                } else if (btnId.equals("L2")) {
                    triggerCashWithdrawal(50);
                } else if (btnId.equals("L3")) {
                    triggerCashWithdrawal(100);
                } else if (btnId.equals("R1")) {
                    triggerCashWithdrawal(200);
                } else if (btnId.equals("R2")) {
                    triggerCashWithdrawal(500);
                } else if (btnId.equals("R3")) {
                    currentState = ScreenState.WITHDRAW_CUSTOM;
                }
                break;

            case SHOW_BALANCE:
            case SHOW_STATEMENT:
                if (btnId.equals("R3")) {
                    currentState = ScreenState.MAIN_MENU;
                }
                break;

            default:
                // Botões laterais desativados em outros estados
                break;
        }
        inputBuffer.setLength(0);
        updateScreen();
    }

    private boolean isAnimationState() {
        return currentState == ScreenState.ANIMATION_CASH ||
                currentState == ScreenState.ANIMATION_PRINT ||
                currentState == ScreenState.ANIMATION_DEPOSIT;
    }

    private void triggerCashWithdrawal(double val) {
        try {
            atmService.withdraw(val);
            currentState = ScreenState.ANIMATION_CASH;
            updateScreen();

            // Ativa slot físico com luz e indicação de dinheiro
            lblCashDispenserStatus.setText("RETIRE SUAS CÉDULAS");
            lblCashDispenserStatus.setForeground(new Color(50, 255, 50));
            cashDispenserContainer.setBackground(new Color(20, 80, 20)); // Fundo verde iluminado

            cashAnimationTimer.start();
        } catch (Exception ex) {
            errorMessage = ex.getMessage().toUpperCase();
            currentState = ScreenState.ERROR;
            updateScreen();
        }
    }

    private void triggerDeposit(double val) {
        try {
            atmService.deposit(val);
            currentState = ScreenState.ANIMATION_DEPOSIT;
            updateScreen();

            // Simula processando depósito
            Timer depTimer = new Timer(2000, e -> {
                currentState = ScreenState.SUCCESS;
                updateScreen();
                successTimer.start();
            });
            depTimer.setRepeats(false);
            depTimer.start();
        } catch (Exception ex) {
            errorMessage = ex.getMessage().toUpperCase();
            currentState = ScreenState.ERROR;
            updateScreen();
        }
    }

    private void triggerPrintStatement() {
        currentState = ScreenState.ANIMATION_PRINT;
        updateScreen();

        lblPrinterStatus.setText("IMPRIMINDO...");
        lblPrinterStatus.setForeground(Color.YELLOW);
        receiptPrinterContainer.setBackground(new Color(80, 80, 20)); // Fundo amarelado iluminado

        printAnimationTimer.start();
    }

    private void showVirtualReceipt() {
        Account acc = atmService.getCurrentAccount();
        if (acc == null)
            return;

        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("               FIAP BANK                \n");
        sb.append("        COMPROVANTE DE EXTRATO          \n");
        sb.append("========================================\n");
        sb.append("CONTA: ").append(acc.getAccountNumber()).append("\n");
        sb.append("DATA: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                .append("\n");
        sb.append("----------------------------------------\n");

        List<Transaction> txs = acc.getTransactions();
        int count = 0;
        // Pega as últimas 5 transações
        for (int i = txs.size() - 1; i >= 0 && count < 5; i--) {
            Transaction tx = txs.get(i);
            sb.append(String.format("%-12s %-14s %12s\n",
                    tx.getTimestamp().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                    tx.getType().getDescription(),
                    tx.getAmount().format()));
            count++;
        }

        sb.append("----------------------------------------\n");
        sb.append("SALDO ATUAL: ").append(acc.getBalance().format()).append("\n");
        sb.append("========================================\n");
        sb.append("        OBRIGADO POR UTILIZAR           \n");
        sb.append("             FIAP BANK                  \n");
        sb.append("========================================\n");

        if (receiptDialog != null) {
            receiptDialog.dispose();
        }

        receiptDialog = new JDialog(this, "Extrato Impresso", false);
        receiptDialog.setSize(320, 450);
        receiptDialog.setResizable(false);

        txtReceiptPaper = new JTextArea();
        txtReceiptPaper.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtReceiptPaper.setBackground(new Color(250, 250, 245)); // Papel térmico esbranquiçado
        txtReceiptPaper.setForeground(Color.BLACK);
        txtReceiptPaper.setText(sb.toString());
        txtReceiptPaper.setEditable(false);
        txtReceiptPaper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnTearOff = new JButton("Destacar Comprovante");
        btnTearOff.addActionListener(e -> receiptDialog.dispose());

        receiptDialog.setLayout(new BorderLayout());
        receiptDialog.add(new JScrollPane(txtReceiptPaper), BorderLayout.CENTER);
        receiptDialog.add(btnTearOff, BorderLayout.SOUTH);

        // Posição no lado direito da janela principal
        Point atmPos = this.getLocation();
        receiptDialog.setLocation(atmPos.x + this.getWidth() + 10, atmPos.y + 100);
        receiptDialog.setVisible(true);
    }

    private void updateScreen() {
        // Resetar opções por padrão
        lblLeftOpt1.setText(" ");
        lblLeftOpt2.setText(" ");
        lblLeftOpt3.setText(" ");
        lblRightOpt1.setText(" ");
        lblRightOpt2.setText(" ");
        lblRightOpt3.setText(" ");
        lblScreenMessage.setText(" ");

        // LED de cartão baseado na autenticação
        if (atmService.isAuthenticated()) {
            lblCardIndicatorLed.setForeground(new Color(80, 80, 250)); // Azul estático - Cartão lido
            lblCardIndicatorLed.setText("● CARTÃO VALIDADO");
        } else if (currentState == ScreenState.WELCOME) {
            // Controlado pelo cardLedTimer (piscando verde)
        } else if (currentState == ScreenState.ENTER_PIN) {
            lblCardIndicatorLed.setForeground(Color.ORANGE);
            lblCardIndicatorLed.setText("● LENDO SENHA...");
        } else if (currentState == ScreenState.ERROR) {
            lblCardIndicatorLed.setForeground(Color.RED);
            lblCardIndicatorLed.setText("● ERRO NO CARTÃO");
        }

        switch (currentState) {
            case WELCOME:
                lblScreenHeader.setText("--- ATM FIAP BANK ---");
                lblScreenStatus.setText("DIGITE O NÚMERO DA CONTA");
                lblScreenInput.setText(inputBuffer.length() > 0 ? inputBuffer.toString() + "_" : "[CONTA]_");
                lblScreenMessage.setText("Use o teclado físico ou numérico abaixo e clique Confirmar.");
                btnBlank.setText("Confirmar");
                break;

            case ENTER_PIN:
                lblScreenHeader.setText("--- ATM FIAP BANK ---");
                lblScreenStatus.setText("INSIRA A SENHA DE 4 DÍGITOS");

                StringBuilder stars = new StringBuilder();
                for (int i = 0; i < inputBuffer.length(); i++) {
                    stars.append("*");
                }
                lblScreenInput.setText(stars.length() > 0 ? stars.toString() : "[SENHA]");
                lblScreenMessage.setText("Acesso de Segurança. Pressione 'Confirmar' ao finalizar.");
                btnBlank.setText("Confirmar");
                break;

            case MAIN_MENU:
                Account currentAcc = atmService.getCurrentAccount();
                lblScreenHeader.setText("--- MENU PRINCIPAL ---");
                lblScreenStatus.setText("CONTA ATIVA: " + (currentAcc != null ? currentAcc.getAccountNumber() : ""));
                lblScreenInput.setText("SELECIONE A OPERAÇÃO");

                lblLeftOpt1.setText("> SACAR");
                lblLeftOpt2.setText("> DEPOSITAR");
                lblLeftOpt3.setText("> TRANSFERIR");

                lblRightOpt1.setText("SALDO <");
                lblRightOpt2.setText("EXTRATO <");
                lblRightOpt3.setText("SAIR <");
                btnBlank.setText("");
                break;

            case WITHDRAW_SELECT:
                lblScreenHeader.setText("--- REALIZAR SAQUE ---");
                lblScreenStatus.setText("ESCOLHA O VALOR DO SAQUE");
                lblScreenInput.setText("");

                lblLeftOpt1.setText("> R$ 20,00");
                lblLeftOpt2.setText("> R$ 50,00");
                lblLeftOpt3.setText("> R$ 100,00");

                lblRightOpt1.setText("R$ 200,00 <");
                lblRightOpt2.setText("R$ 500,00 <");
                lblRightOpt3.setText("OUTRO VALOR <");
                btnBlank.setText("");
                lblScreenMessage.setText("Pressione 'C' no teclado para voltar ao Menu.");
                break;

            case WITHDRAW_CUSTOM:
                lblScreenHeader.setText("--- VALOR PERSONALIZADO ---");
                lblScreenStatus.setText("DIGITE O VALOR PARA SAQUE");
                lblScreenInput.setText(inputBuffer.length() > 0 ? "R$ " + inputBuffer.toString() + ",00" : "R$ 0,00");
                lblScreenMessage.setText("Pressione 'Confirmar' para realizar o saque.");
                btnBlank.setText("Confirmar");
                break;

            case DEPOSIT_INPUT:
                lblScreenHeader.setText("--- REALIZAR DEPÓSITO ---");
                lblScreenStatus.setText("INSIRA O VALOR DE DEPÓSITO");
                lblScreenInput.setText(inputBuffer.length() > 0 ? "R$ " + inputBuffer.toString() + ",00" : "R$ 0,00");
                lblScreenMessage.setText("Digite e clique em 'Confirmar' para validar.");
                btnBlank.setText("Confirmar");
                break;

            case TRANSFER_ACCOUNT:
                lblScreenHeader.setText("--- TRANSFERÊNCIA BANCÁRIA ---");
                lblScreenStatus.setText("DIGITE A CONTA DE DESTINO");
                lblScreenInput.setText(inputBuffer.length() > 0 ? inputBuffer.toString() + "_" : "[CONTA DESTINO]_");
                lblScreenMessage.setText("Digite o número e clique 'Confirmar'.");
                btnBlank.setText("Confirmar");
                break;

            case TRANSFER_VALUE:
                lblScreenHeader.setText("--- VALOR DA TRANSFERÊNCIA ---");
                lblScreenStatus.setText("DESTINO: CONTA " + targetAccountNumber);
                lblScreenInput.setText(inputBuffer.length() > 0 ? "R$ " + inputBuffer.toString() + ",00" : "R$ 0,00");
                lblScreenMessage.setText("Pressione 'Confirmar' para efetuar.");
                btnBlank.setText("Confirmar");
                break;

            case SHOW_BALANCE:
                Account balanceAcc = atmService.getCurrentAccount();
                lblScreenHeader.setText("--- CONSULTA DE SALDO ---");
                lblScreenStatus.setText("SALDO DISPONÍVEL");
                lblScreenInput.setText(balanceAcc != null ? balanceAcc.getBalance().format() : "R$ 0,00");
                lblScreenMessage.setText("Limite Diário Restante: " +
                        (balanceAcc != null
                                ? balanceAcc.getDailyWithdrawalLimit().minus(balanceAcc.getTotalWithdrawnToday())
                                        .format()
                                : "R$ 0,00"));
                lblRightOpt3.setText("VOLTAR <");
                btnBlank.setText("");
                break;

            case SHOW_STATEMENT:
                lblScreenHeader.setText("--- EXTRATO IMPRESSO ---");
                lblScreenStatus.setText("EXTRATO GERADO");
                lblScreenInput.setText("VERIFIQUE A LATERAL");
                lblScreenMessage.setText("O extrato físico foi impresso na impressora lateral.");
                lblRightOpt3.setText("VOLTAR <");
                btnBlank.setText("");
                break;

            case ANIMATION_CASH:
                lblScreenHeader.setText("--- AGUARDE ---");
                lblScreenStatus.setText("CONSTANDO CÉDULAS...");
                lblScreenInput.setText("$$$$$$$$$$$$$");
                lblScreenMessage.setText("Retire as cédulas no dispensador abaixo.");
                btnBlank.setText("");
                break;

            case ANIMATION_PRINT:
                lblScreenHeader.setText("--- AGUARDE ---");
                lblScreenStatus.setText("IMPRIMINDO COMPROVANTE...");
                lblScreenInput.setText("■■■■■■■■■■■■■");
                lblScreenMessage.setText("Retire o papel térmico impresso ao lado.");
                btnBlank.setText("");
                break;

            case ANIMATION_DEPOSIT:
                lblScreenHeader.setText("--- AGUARDE ---");
                lblScreenStatus.setText("PROCESSANDO DEPÓSITO...");
                lblScreenInput.setText("•••••••••••••");
                lblScreenMessage.setText("Processando envelopes e autenticação...");
                btnBlank.setText("");
                break;

            case SUCCESS:
                lblScreenHeader.setText("--- OPERAÇÃO CONCLUÍDA ---");
                lblScreenStatus.setText("TRANSAÇÃO COM SUCESSO!");
                lblScreenInput.setText("OBRIGADO");
                lblScreenMessage.setText("Retornando em instantes...");
                btnBlank.setText("");
                break;

            case ERROR:
                lblScreenHeader.setText("--- ATENÇÃO ---");
                lblScreenStatus.setText("FALHA NA OPERAÇÃO");
                lblScreenInput.setText("ERRO");
                lblScreenMessage.setText(errorMessage != null ? errorMessage : "TENTE NOVAMENTE.");
                btnBlank.setText("");
                break;
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Generated
    // Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        GridBagConstraints gridBagConstraints;

        jPanelMain = new JPanel();
        jPanelHeader = new JPanel();
        lblHeaderTitle = new JLabel();
        jPanelCenterConsole = new JPanel();
        jPanelLeftButtons = new JPanel();
        btnLeft1 = new JButton();
        btnLeft2 = new JButton();
        btnLeft3 = new JButton();
        jPanelRightButtons = new JPanel();
        btnRight1 = new JButton();
        btnRight2 = new JButton();
        btnRight3 = new JButton();
        jPanelScreen = new JPanel();
        jPanelScreenHeader = new JPanel();
        lblScreenHeader = new JLabel();
        jPanelScreenCenter = new JPanel();
        lblScreenStatus = new JLabel();
        lblScreenInput = new JLabel();
        lblScreenMessage = new JLabel();
        jPanelScreenLeftLabels = new JPanel();
        lblLeftOpt1 = new JLabel();
        lblLeftOpt2 = new JLabel();
        lblLeftOpt3 = new JLabel();
        jPanelScreenRightLabels = new JPanel();
        lblRightOpt1 = new JLabel();
        lblRightOpt2 = new JLabel();
        lblRightOpt3 = new JLabel();
        jPanelBottomConsole = new JPanel();
        jPanelKeypad = new JPanel();
        btn1 = new JButton();
        btn2 = new JButton();
        btn3 = new JButton();
        btn4 = new JButton();
        btn5 = new JButton();
        btn6 = new JButton();
        btn7 = new JButton();
        btn8 = new JButton();
        btn9 = new JButton();
        btnBlank = new JButton();
        btn0 = new JButton();
        btnC = new JButton();
        jPanelPeripherals = new JPanel();
        cardSlotContainer = new JPanel();
        lblCardIndicatorLed = new JLabel();
        receiptPrinterContainer = new JPanel();
        lblPrinterStatus = new JLabel();
        cashDispenserContainer = new JPanel();
        lblCashDispenserStatus = new JLabel();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("FIAP Bank - Caixa Eletrônico (ATM)");
        setResizable(false);

        jPanelMain.setBackground(new Color(19, 30, 43));
        jPanelMain.setBorder(BorderFactory.createLineBorder(new Color(51, 51, 51), 8));
        jPanelMain.setLayout(new BorderLayout());

        jPanelHeader.setBackground(new Color(13, 20, 31));
        jPanelHeader.setPreferredSize(new Dimension(800, 80));
        jPanelHeader.setLayout(new GridBagLayout());

        lblHeaderTitle.setFont(new Font("Segoe UI", 1, 28)); // NOI18N
        lblHeaderTitle.setForeground(new Color(241, 248, 252));
        lblHeaderTitle.setText("FIAP BANK");
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = -1;
        gridBagConstraints.gridy = -1;
        jPanelHeader.add(lblHeaderTitle, gridBagConstraints);

        jPanelMain.add(jPanelHeader, BorderLayout.NORTH);

        jPanelCenterConsole.setBackground(new Color(19, 30, 43));
        jPanelCenterConsole.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        jPanelCenterConsole.setLayout(new BorderLayout());

        jPanelLeftButtons.setBackground(new Color(19, 30, 43));
        jPanelLeftButtons.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 15));
        jPanelLeftButtons.setPreferredSize(new Dimension(100, 300));
        jPanelLeftButtons.setLayout(new GridLayout(3, 1, 0, 35));

        btnLeft1.setFont(new Font("Segoe UI", 1, 14)); // NOI18N
        btnLeft1.setText("[ ]");
        jPanelLeftButtons.add(btnLeft1);

        btnLeft2.setFont(new Font("Segoe UI", 1, 14)); // NOI18N
        btnLeft2.setText("[ ]");
        jPanelLeftButtons.add(btnLeft2);

        btnLeft3.setFont(new Font("Segoe UI", 1, 14)); // NOI18N
        btnLeft3.setText("[ ]");
        jPanelLeftButtons.add(btnLeft3);

        jPanelCenterConsole.add(jPanelLeftButtons, BorderLayout.WEST);

        jPanelRightButtons.setBackground(new Color(19, 30, 43));
        jPanelRightButtons.setBorder(BorderFactory.createEmptyBorder(20, 15, 0, 0));
        jPanelRightButtons.setPreferredSize(new Dimension(100, 300));
        jPanelRightButtons.setLayout(new GridLayout(3, 1, 0, 35));

        btnRight1.setFont(new Font("Segoe UI", 1, 14)); // NOI18N
        btnRight1.setText("[ ]");
        jPanelRightButtons.add(btnRight1);

        btnRight2.setFont(new Font("Segoe UI", 1, 14)); // NOI18N
        btnRight2.setText("[ ]");
        jPanelRightButtons.add(btnRight2);

        btnRight3.setFont(new Font("Segoe UI", 1, 14)); // NOI18N
        btnRight3.setText("[ ]");
        jPanelRightButtons.add(btnRight3);

        jPanelCenterConsole.add(jPanelRightButtons, BorderLayout.EAST);

        jPanelScreen.setBackground(new Color(11, 18, 28));
        jPanelScreen.setBorder(new LineBorder(new Color(43, 56, 77), 4, true));
        jPanelScreen.setLayout(new BorderLayout());

        jPanelScreenHeader.setBackground(new Color(11, 18, 28));
        jPanelScreenHeader.setPreferredSize(new Dimension(500, 45));

        lblScreenHeader.setFont(new Font("Monospaced", 1, 18)); // NOI18N
        lblScreenHeader.setForeground(new Color(254, 240, 138));
        lblScreenHeader.setText("--- ATM FIAP BANK ---");
        jPanelScreenHeader.add(lblScreenHeader);

        jPanelScreen.add(jPanelScreenHeader, BorderLayout.NORTH);

        jPanelScreenCenter.setBackground(new Color(11, 18, 28));
        jPanelScreenCenter.setLayout(new GridLayout(3, 1));

        lblScreenStatus.setFont(new Font("Monospaced", 1, 14)); // NOI18N
        lblScreenStatus.setForeground(new Color(241, 245, 249));
        lblScreenStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblScreenStatus.setText("INSIRA SEU CARTÃO OU CONTA");
        jPanelScreenCenter.add(lblScreenStatus);

        lblScreenInput.setFont(new Font("Monospaced", 1, 24)); // NOI18N
        lblScreenInput.setForeground(new Color(56, 189, 248));
        lblScreenInput.setHorizontalAlignment(SwingConstants.CENTER);
        lblScreenInput.setText("_");
        jPanelScreenCenter.add(lblScreenInput);

        lblScreenMessage.setFont(new Font("Monospaced", 0, 12)); // NOI18N
        lblScreenMessage.setForeground(new Color(234, 113, 113));
        lblScreenMessage.setHorizontalAlignment(SwingConstants.CENTER);
        lblScreenMessage.setText(" ");
        jPanelScreenCenter.add(lblScreenMessage);

        jPanelScreen.add(jPanelScreenCenter, BorderLayout.CENTER);

        jPanelScreenLeftLabels.setBackground(new Color(11, 18, 28));
        jPanelScreenLeftLabels.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 0));
        jPanelScreenLeftLabels.setPreferredSize(new Dimension(130, 200));
        jPanelScreenLeftLabels.setLayout(new GridLayout(3, 1, 0, 35));

        lblLeftOpt1.setFont(new Font("Monospaced", 1, 14)); // NOI18N
        lblLeftOpt1.setForeground(new Color(56, 189, 248));
        lblLeftOpt1.setText(" ");
        jPanelScreenLeftLabels.add(lblLeftOpt1);

        lblLeftOpt2.setFont(new Font("Monospaced", 1, 14)); // NOI18N
        lblLeftOpt2.setForeground(new Color(56, 189, 248));
        lblLeftOpt2.setText(" ");
        jPanelScreenLeftLabels.add(lblLeftOpt2);

        lblLeftOpt3.setFont(new Font("Monospaced", 1, 14)); // NOI18N
        lblLeftOpt3.setForeground(new Color(56, 189, 248));
        lblLeftOpt3.setText(" ");
        jPanelScreenLeftLabels.add(lblLeftOpt3);

        jPanelScreen.add(jPanelScreenLeftLabels, BorderLayout.WEST);

        jPanelScreenRightLabels.setBackground(new Color(11, 18, 28));
        jPanelScreenRightLabels.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 10));
        jPanelScreenRightLabels.setPreferredSize(new Dimension(130, 200));
        jPanelScreenRightLabels.setLayout(new GridLayout(3, 1, 0, 35));

        lblRightOpt1.setFont(new Font("Monospaced", 1, 14)); // NOI18N
        lblRightOpt1.setForeground(new Color(56, 189, 248));
        lblRightOpt1.setHorizontalAlignment(SwingConstants.RIGHT);
        lblRightOpt1.setText(" ");
        jPanelScreenRightLabels.add(lblRightOpt1);

        lblRightOpt2.setFont(new Font("Monospaced", 1, 14)); // NOI18N
        lblRightOpt2.setForeground(new Color(56, 189, 248));
        lblRightOpt2.setHorizontalAlignment(SwingConstants.RIGHT);
        lblRightOpt2.setText(" ");
        jPanelScreenRightLabels.add(lblRightOpt2);

        lblRightOpt3.setFont(new Font("Monospaced", 1, 14)); // NOI18N
        lblRightOpt3.setForeground(new Color(56, 189, 248));
        lblRightOpt3.setHorizontalAlignment(SwingConstants.RIGHT);
        lblRightOpt3.setText(" ");
        jPanelScreenRightLabels.add(lblRightOpt3);

        jPanelScreen.add(jPanelScreenRightLabels, BorderLayout.EAST);

        jPanelCenterConsole.add(jPanelScreen, BorderLayout.CENTER);

        jPanelMain.add(jPanelCenterConsole, BorderLayout.CENTER);

        jPanelBottomConsole.setBackground(new Color(13, 20, 31));
        jPanelBottomConsole.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(85, 85, 85), 2), "CONSOLE DO OPERADOR",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", 1, 12), new Color(170, 170, 170))); // NOI18N
        jPanelBottomConsole.setPreferredSize(new Dimension(800, 360));
        jPanelBottomConsole.setLayout(new GridLayout(1, 2, 30, 0));

        jPanelKeypad.setBackground(new Color(13, 20, 31));
        jPanelKeypad.setBorder(BorderFactory.createEmptyBorder(15, 30, 15, 15));
        jPanelKeypad.setLayout(new GridLayout(4, 3, 10, 10));

        btn1.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn1.setText("1");
        jPanelKeypad.add(btn1);

        btn2.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn2.setText("2");
        jPanelKeypad.add(btn2);

        btn3.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn3.setText("3");
        jPanelKeypad.add(btn3);

        btn4.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn4.setText("4");
        jPanelKeypad.add(btn4);

        btn5.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn5.setText("5");
        jPanelKeypad.add(btn5);

        btn6.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn6.setText("6");
        jPanelKeypad.add(btn6);

        btn7.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn7.setText("7");
        jPanelKeypad.add(btn7);

        btn8.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn8.setText("8");
        jPanelKeypad.add(btn8);

        btn9.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn9.setText("9");
        jPanelKeypad.add(btn9);

        btnBlank.setFont(new Font("Segoe UI", 0, 14)); // NOI18N
        btnBlank.setText("Cartão");
        jPanelKeypad.add(btnBlank);

        btn0.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btn0.setText("0");
        jPanelKeypad.add(btn0);

        btnC.setBackground(new Color(189, 58, 58));
        btnC.setFont(new Font("Segoe UI", 1, 20)); // NOI18N
        btnC.setForeground(new Color(255, 255, 255));
        btnC.setText("C");
        jPanelKeypad.add(btnC);

        jPanelBottomConsole.add(jPanelKeypad);

        jPanelPeripherals.setBackground(new Color(13, 20, 31));
        jPanelPeripherals.setBorder(BorderFactory.createEmptyBorder(15, 15, 30, 30));
        jPanelPeripherals.setLayout(new GridLayout(3, 1, 0, 15));

        cardSlotContainer.setBackground(new Color(18, 27, 38));
        cardSlotContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(38, 52, 71), 2), "ENTRADA DE CARTÃO",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", 1, 10), new Color(153, 153, 153))); // NOI18N
        cardSlotContainer.setLayout(new BorderLayout());

        lblCardIndicatorLed.setFont(new Font("Segoe UI", 1, 12)); // NOI18N
        lblCardIndicatorLed.setForeground(new Color(80, 200, 80));
        lblCardIndicatorLed.setHorizontalAlignment(SwingConstants.CENTER);
        lblCardIndicatorLed.setText("● AGUARDANDO CARTÃO");
        cardSlotContainer.add(lblCardIndicatorLed, BorderLayout.CENTER);

        jPanelPeripherals.add(cardSlotContainer);

        receiptPrinterContainer.setBackground(new Color(18, 27, 38));
        receiptPrinterContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(38, 52, 71), 2), "IMPRESSORA DE EXTRATO",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", 1, 10), new Color(153, 153, 153))); // NOI18N
        receiptPrinterContainer.setLayout(new BorderLayout());

        lblPrinterStatus.setFont(new Font("Segoe UI", 1, 12)); // NOI18N
        lblPrinterStatus.setForeground(new Color(204, 204, 204));
        lblPrinterStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblPrinterStatus.setText("PRONTA");
        receiptPrinterContainer.add(lblPrinterStatus, BorderLayout.CENTER);

        jPanelPeripherals.add(receiptPrinterContainer);

        cashDispenserContainer.setBackground(new Color(18, 27, 38));
        cashDispenserContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(38, 52, 71), 2), "DISPENSADOR DE CÉDULAS",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", 1, 10), new Color(153, 153, 153))); // NOI18N
        cashDispenserContainer.setLayout(new BorderLayout());

        lblCashDispenserStatus.setFont(new Font("Segoe UI", 1, 12)); // NOI18N
        lblCashDispenserStatus.setForeground(new Color(153, 153, 153));
        lblCashDispenserStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblCashDispenserStatus.setText("FECHADO");
        cashDispenserContainer.add(lblCashDispenserStatus, BorderLayout.CENTER);

        jPanelPeripherals.add(cashDispenserContainer);

        jPanelBottomConsole.add(jPanelPeripherals);

        jPanelMain.add(jPanelBottomConsole, BorderLayout.SOUTH);

        getContentPane().add(jPanelMain, BorderLayout.CENTER);

        setSize(new Dimension(816, 839));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JButton btn0;
    private JButton btn1;
    private JButton btn2;
    private JButton btn3;
    private JButton btn4;
    private JButton btn5;
    private JButton btn6;
    private JButton btn7;
    private JButton btn8;
    private JButton btn9;
    private JButton btnBlank;
    private JButton btnC;
    private JButton btnLeft1;
    private JButton btnLeft2;
    private JButton btnLeft3;
    private JButton btnRight1;
    private JButton btnRight2;
    private JButton btnRight3;
    private JPanel cardSlotContainer;
    private JPanel cashDispenserContainer;
    private JPanel jPanelBottomConsole;
    private JPanel jPanelCenterConsole;
    private JPanel jPanelHeader;
    private JPanel jPanelKeypad;
    private JPanel jPanelLeftButtons;
    private JPanel jPanelMain;
    private JPanel jPanelPeripherals;
    private JPanel jPanelRightButtons;
    private JPanel jPanelScreen;
    private JPanel jPanelScreenCenter;
    private JPanel jPanelScreenHeader;
    private JPanel jPanelScreenLeftLabels;
    private JPanel jPanelScreenRightLabels;
    private JLabel lblCardIndicatorLed;
    private JLabel lblCashDispenserStatus;
    private JLabel lblHeaderTitle;
    private JLabel lblLeftOpt1;
    private JLabel lblLeftOpt2;
    private JLabel lblLeftOpt3;
    private JLabel lblPrinterStatus;
    private JLabel lblRightOpt1;
    private JLabel lblRightOpt2;
    private JLabel lblRightOpt3;
    private JLabel lblScreenHeader;
    private JLabel lblScreenInput;
    private JLabel lblScreenMessage;
    private JLabel lblScreenStatus;
    private JPanel receiptPrinterContainer;
    // End of variables declaration//GEN-END:variables
}
