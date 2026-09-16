# FIAP Bank - Emulador de Caixa Eletrônico (ATM)

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Maven](https://img.shields.io/badge/Maven-3.x-blue?style=for-the-badge&logo=apache-maven)
![FlatLaf](https://img.shields.io/badge/UI-FlatLaf_Dark-darkgreen?style=for-the-badge)
![Architecture](https://img.shields.io/badge/Architecture-Domain--Driven_Design_(DDD)-purple?style=for-the-badge)

> **FIAP - Engenharia de Software (2026)**  
> **Checkpoint 4 (CP4)** — Aplicação de Emulação de Caixa Eletrônico (ATM) construída em **Java 21**, **Swing (FlatLaf)** e orientada aos princípios de **Domain-Driven Design (DDD)**.

---

## 📌 Visão Geral

O **FIAP Bank ATM** é um emulador interativo de Caixa Eletrônico de alta fidelidade visual e comportamental. Desenvolvido para simular a experiência real de operação de um terminal bancário autoatendimento, o sistema oferece desde a validação de segurança de cartões/PIN até a dispensação simulada de cédulas e impressão de extrato térmico em popup.

A aplicação foi projetada com arquitetura limpa em camadas baseada em **DDD (Domain-Driven Design)**, garantindo desacoplamento entre regras de negócio, persistência de dados e a camada de apresentação visual.

---

## 🎯 Principais Funcionalidades

- **🔐 Autenticação Segura & Gestão de PIN**:
  - Leitura e validação de conta bancária e senha numérica de 4 dígitos.
  - Bloqueio automático de segurança da conta após **3 tentativas incorretas consecutivas**.
  - Reset de tentativas falhas ao autenticar com sucesso.
- **💵 Saque Eletrônico (Withdrawal)**:
  - Valores rápidos pré-definidos via botões físicos laterais (R\$ 20, R\$ 50, R\$ 100, R\$ 200, R\$ 500) e opção de valor personalizado.
  - Validação estrita de saldo disponível (`InsufficientFundsException`).
  - Controle e validação de **limite diário de saque** (`DailyLimitExceededException`).
  - Animação visual no compartimento de notas (efeito luminoso verde e alerta de retirada de cédulas).
- **📥 Depósito em Dinheiro (Deposit)**:
  - Entrada de valores numéricos via teclado.
  - Validação de valor mínimo positivo.
  - Animação visual de processamento de depósitos e envelopes.
- **💸 Transferência entre Contas (Transfer)**:
  - Transferência em tempo real para qualquer outra conta existente no sistema.
  - Validação da existência da conta de destino e verificação de conta bloqueada.
  - Proibição de transferências para a própria conta de origem.
  - Lançamento automático de movimentação de saída (`TRANSFER_OUT`) e entrada (`TRANSFER_IN`).
- **📊 Consulta de Saldo e Limite Diário**:
  - Exibição de saldo em moeda nacional formatada (`R$ X.XXX,XX`).
  - Exibição em tempo real do limite diário de saque restante.
- **🧾 Impressão de Extrato Térmico Virtual**:
  - Simulação de impressora lateral com aviso luminoso.
  - Emissão de extrato em janela pop-up estilizada como papel térmico (fonte monospaced), contendo as últimas transações e botão de destacar comprovante.
- **⌨️ Dupla Forma de Interação (Teclado Físico + Botões Virtuais)**:
  - Interface com botões laterais (L1, L2, L3 e R1, R2, R3) e teclado numérico virtual.
  - Suporte completo ao **teclado do computador** via interceptação global de eventos (`0-9`, `Enter` para confirmar, `Backspace` / `Esc` para apagar ou cancelar).
- **💡 Indicadores de Periféricos & LED Animated States**:
  - LED indicador de leitor de cartão piscando no estado de boas-vindas.
  - Slots de dispensador de dinheiro e impressora com feedback de cor e estado.

---

## 🏗️ Arquitetura do Sistema (DDD)

A aplicação segue uma divisão clara de responsabilidades estruturada nos padrões do **Domain-Driven Design (DDD)**:

```
com.fiap.bank.atm
├── domain                          # Camada de Domínio (Regras de Negócio Puras)
│   ├── exception                   # Exceções de negócio customizadas
│   │   ├── AccountBlockedException.java
│   │   ├── DailyLimitExceededException.java
│   │   ├── InsufficientFundsException.java
│   │   └── InvalidPinException.java
│   ├── model                       # Entidades e Objetos de Valor (Value Objects)
│   │   ├── Account.java            # Entidade Principal da Conta Bancária
│   │   ├── BaseEntity.java         # Classe base com ID (UUID) e datas de criação/atualização
│   │   ├── Money.java              # Value Object imutável para operações monetárias (BigDecimal)
│   │   ├── Transaction.java        # Entidade de Registro de Transações
│   │   └── TransactionType.java    # Enum dos tipos de transação (Saque, Depósito, Transferências)
│   └── repository                  # Interfaces de Repositório
│       └── AccountRepository.java
│
├── application                     # Camada de Aplicação (Casos de Uso & Orquestração)
│   └── service
│       └── AtmService.java         # Orquestra autenticação, transações e estado da sessão
│
├── infrastructure                  # Camada de Infraestrutura (Persistência e Recursos Externos)
│   └── persistence
│       ├── AccountRepositoryJdbcImpl.java # Persistência SQLite via JDBC
│       └── InMemoryAccountRepository.java # Implementação alternativa em memória
│
└── presentation                    # Camada de Apresentação (UI / Swing)
    ├── AtmFrame.java               # Janela principal do ATM com FlatLaf Dark Theme
    ├── AtmFrame.form               # Arquivo de layout visual do Swing Form
    └── ScreenState.java            # Enum da Máquina de Estados da Tela
```

---

## ⚙️ Detalhamento dos Componentes de Domínio

### 1. `Money` (Value Object)
- Classe imutável responsável por manipular valores monetários garantindo precisão decimal com `BigDecimal` (escala 2).
- Evita erros de arredondamento de ponto flutuante (`double`).
- Formatação automática no padrão brasileiro `pt-BR` (ex: `R$ 5.000,00`).
- Métodos utilitários de comparação (`isGreaterThan`, `isLessThan`, `isGreaterThanOrEqual`) e aritmética (`plus`, `minus`).

### 2. `Account` (Aggregate Root / Entity)
- Contém o número da conta, PIN criptografado/armazenado, saldo (`Money`), limite diário de saque, total sacado no dia, status de bloqueio, contador de falhas de autenticação e histórico de transações.
- Encapsula todas as regras de negócio de saque, depósito, transferência e autenticação.

### 3. `AtmService` (Application Service)
- Atua como a fachada da camada de aplicação.
- Gerencia o estado da conta atualmente autenticada (`currentAccount`).
- Garante a execução transacional salvando alterações no `AccountRepository`.

### 4. `ScreenState` & `AtmFrame` (Máquina de Estados de Tela)
A interface gráfica opera sobre uma **Máquina de Estados Finitos (FSM)** representada pelo enum `ScreenState`:

| Estado (`ScreenState`) | Descrição |
| :--- | :--- |
| `WELCOME` | Tela inicial aguardando a digitação do número da conta. LED do cartão pisca verde. |
| `ENTER_PIN` | Solicitação da senha de 4 dígitos (exibida com asteriscos `****`). |
| `MAIN_MENU` | Menu principal com opções operacionais associadas aos botões laterais. |
| `WITHDRAW_SELECT` | Seleção de valores pré-definidos de saque (R\$ 20 a R\$ 500) ou valor customizado. |
| `WITHDRAW_CUSTOM` | Campo para digitação de valor específico de saque. |
| `DEPOSIT_INPUT` | Campo para digitação de valor de depósito em dinheiro. |
| `TRANSFER_ACCOUNT` | Entrada do número da conta de destino para transferência. |
| `TRANSFER_VALUE` | Entrada do valor da transferência. |
| `SHOW_BALANCE` | Exibição do saldo disponível e limite diário restante. |
| `SHOW_STATEMENT` | Disparo da impressão do extrato. |
| `ANIMATION_*` | Estados temporários de animação (dispensador de cédulas, impressora e depósito). |
| `SUCCESS` / `ERROR` | Mensagens de confirmação de sucesso ou erros tratados do sistema. |

---

## 🔑 Contas Pré-cadastradas para Teste (Seed Data)

Na primeira execução, o `AccountRepositoryJdbcImpl` grava as contas de teste no arquivo SQLite `fiapbank.db`. Saldos, tentativas de PIN e extratos permanecem disponíveis após reiniciar o programa:

| Número da Conta | PIN (Senha) | Saldo Inicial | Limite Diário Saque | Histórico Inicial |
| :---: | :---: | :---: | :---: | :--- |
| **`12345`** | `1234` | **R\$ 5.000,00** | R\$ 1.500,00 | Depósito R\$ 2.000, Transf. Recebida R\$ 500, Saque R\$ 100 |
| **`67890`** | `5678` | **R\$ 1.200,00** | R\$ 1.000,00 | Depósito R\$ 1.500, Transf. Enviada R\$ 500 |
| **`99999`** | `9999` | **R\$ 50,00** | R\$ 500,00 | Depósito R\$ 50,00 (Abertura de conta) |

---

## 🛠️ Tecnologias e Bibliotecas Utilizadas

- **Java 21**: Linguagem principal de programação (LTS).
- **Swing (Java GUI)**: Framework nativo de interface gráfica.
- **FlatLaf 3.5.1 (`com.formdev:flatlaf`)**: Look & Feel moderno e escuro para interfaces Swing.
- **JUnit 5 (5.10.2)**: Framework de testes unitários.
- **Apache Maven**: Gerenciamento de dependências e build.
- **SQLite JDBC**: Persistência local com `PreparedStatement` e transações atômicas.

---

## 🚀 Como Executar o Projeto

### Pré-requisitos
- **JDK 21** ou superior instalado e configurado nas variáveis de ambiente (`JAVA_HOME`).
- **Apache Maven 3.8+** instalado (ou via integração da IDE NetBeans / IntelliJ / Eclipse / VS Code).

---

### Opção 1: Linha de Comando (Terminal / Prompt)

1. Clone o repositório ou navegue até a pasta raiz do projeto:
   ```bash
   cd fiap-bank-atm
   ```

2. Compile, rode os testes e instale os módulos Maven; depois execute a apresentação:
   ```bash
   mvn -pl presentation -am install
   mvn -pl presentation exec:java -Dexec.mainClass=com.fiap.bank.atm.presentation.AtmApplication
   ```

---

### Opção 2: Executar via Script Batch (Windows)

No Windows, você pode executar diretamente o script configurado `run.bat`:
```cmd
run.bat
```
*O script localiza automaticamente o Maven do Apache NetBeans ou o `mvn` global e inicializa a aplicação.*

---

### Opção 3: Apache NetBeans / IntelliJ IDEA / Eclipse

1. Abra a IDE e selecione **Open Project** apontando para a pasta raiz do projeto (onde se encontra o `pom.xml`).
2. Aguarde a sincronização das dependências Maven (`flatlaf`, `junit-jupiter`).
3. Localize e execute a classe principal: `com.fiap.bank.atm.presentation.AtmApplication`, no módulo `presentation`.

---

## 🧪 Rodando os Testes

Para executar a suíte de testes unitários com o Maven Surefire Plugin:
```bash
mvn test
```

---

## 🎨 Destaques de Design e Usabilidade

- **Tema Escuro de Alta Performance (Slate & Neon)**: Tela em estilo monitor bancário CRT/LCD moderno com texto ciano/amarelo para facilitar a leitura.
- **Teclado Numérico & Teclas de Atalho**:
  - Tecla `1` a `0`: Digitação de valores e PIN.
  - Tecla `Confirmar` / `Enter`: Submete a ação atual.
  - Tecla `C` (Vermelha) / `Backspace` / `Esc`: Limpa a digitação ou cancela/volta de tela.
- **Janela de Extrato Destacável**:
  - Ao solicitar o extrato no menu principal, um diálogo em formato de **comprovante impresso térmico** é aberto ao lado do terminal com as movimentações recentes e saldo atualizado.

---

## 📝 Licença e Créditos

Desenvolvido para fins acadêmicos como parte do curso de **Engenharia de Software (2026)** da **FIAP**.  
Prof. Eduardo Ramos.

Participante: Cesar Aaron Herrera — RM565398.
