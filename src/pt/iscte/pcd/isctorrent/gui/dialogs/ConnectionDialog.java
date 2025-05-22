package pt.iscte.pcd.isctorrent.gui.dialogs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Diálogo para estabelecer conexão com um nó remoto
 * Permite inserir endereço e porta do nó
 */
public class ConnectionDialog extends JDialog {
    private final JTextField addressField;
    private final JTextField portField;
    private String address;
    private int port = -1;

    /**
     * Resultado da conexão
     */
    public record ConnectionResult(String address, int port) {}

    /**
     * Construtor do diálogo
     * @param parent Janela principal
     */
    public ConnectionDialog(JFrame parent) {
        super(parent, "Conectar a nó", true);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Painel principal
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Painel de entrada
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Endereço
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Endereço:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        addressField = new JTextField(15);
        addressField.setText("127.0.0.1");
        addressField.setToolTipText("Endereço IP do nó remoto");
        inputPanel.add(addressField, gbc);

        // Porta
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        inputPanel.add(new JLabel("Porta:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        portField = new JTextField(15);
        portField.setText("8081");
        portField.setToolTipText("Porta do nó remoto (1-65535)");
        inputPanel.add(portField, gbc);

        // Painel de botões
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton connectButton = new JButton("Conectar");
        connectButton.setMnemonic(KeyEvent.VK_C);
        connectButton.addActionListener(e -> validateAndConnect());

        JButton cancelButton = new JButton("Cancelar");
        cancelButton.setMnemonic(KeyEvent.VK_ESCAPE);
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(connectButton);
        buttonPanel.add(cancelButton);

        // Adicionar painéis
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Configurar Enter para conectar
        getRootPane().setDefaultButton(connectButton);

        // Configurar ESC para cancelar
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        pack();
        setLocationRelativeTo(parent);

        // Focar campo de endereço
        addressField.selectAll();
        addressField.requestFocusInWindow();
    }

    /**
     * Valida os campos e conecta
     */
    private void validateAndConnect() {
        String addressText = addressField.getText().trim();
        String portText = portField.getText().trim();

        // Validar endereço
        if (addressText.isEmpty()) {
            showError("Por favor insira um endereço");
            addressField.requestFocusInWindow();
            return;
        }

        // Validar formato básico do endereço
        if (!isValidAddress(addressText)) {
            showError("Endereço inválido");
            addressField.selectAll();
            addressField.requestFocusInWindow();
            return;
        }

        // Validar porta
        if (portText.isEmpty()) {
            showError("Por favor insira uma porta");
            portField.requestFocusInWindow();
            return;
        }

        try {
            int portNumber = Integer.parseInt(portText);

            if (portNumber < 1 || portNumber > 65535) {
                showError("A porta deve estar entre 1 e 65535");
                portField.selectAll();
                portField.requestFocusInWindow();
                return;
            }

            // Tudo válido
            this.address = addressText;
            this.port = portNumber;
            dispose();

        } catch (NumberFormatException e) {
            showError("Porta inválida - deve ser um número");
            portField.selectAll();
            portField.requestFocusInWindow();
        }
    }

    /**
     * Valida formato básico do endereço
     * @param address Endereço a validar
     * @return true se válido
     */
    private boolean isValidAddress(String address) {
        // Validação básica - pode ser IP ou hostname
        if (address.equals("localhost")) {
            return true;
        }

        // Verificar se é um IP válido (básico)
        String[] parts = address.split("\\.");
        if (parts.length == 4) {
            try {
                for (String part : parts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        return false;
                    }
                }
                return true;
            } catch (NumberFormatException e) {
                // Não é IP, pode ser hostname
            }
        }

        // Aceitar como hostname se tiver formato razoável
        return address.matches("[a-zA-Z0-9.-]+");
    }

    /**
     * Mostra mensagem de erro
     * @param message Mensagem a exibir
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this,
                message,
                "Erro",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Obtém o endereço inserido
     * @return Endereço ou null se cancelado
     */
    public String getAddress() {
        return address;
    }

    /**
     * Obtém a porta inserida
     * @return Porta ou -1 se cancelado
     */
    public int getPort() {
        return port;
    }

    /**
     * Mostra o diálogo e retorna o resultado
     * @param parent Janela principal
     * @return Resultado da conexão ou null se cancelado
     */
    public static ConnectionResult showDialog(JFrame parent) {
        ConnectionDialog dialog = new ConnectionDialog(parent);
        dialog.setVisible(true);

        if (dialog.getPort() == -1) {
            return null;
        }

        return new ConnectionResult(dialog.getAddress(), dialog.getPort());
    }
}