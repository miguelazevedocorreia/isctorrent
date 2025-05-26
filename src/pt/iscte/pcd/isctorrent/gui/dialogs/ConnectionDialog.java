package pt.iscte.pcd.isctorrent.gui.dialogs;

import javax.swing.*;
import java.awt.*;

public class ConnectionDialog extends JDialog {
    private final JTextField addressField;
    private final JTextField portField;
    private String address;
    private int port = -1;

    public ConnectionDialog(JFrame parent) {
        super(parent, "Conectar a nó", true);
        setLayout(new BorderLayout(5, 5));

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        addressField = new JTextField();
        addressField.setText("127.0.0.1");
        portField = new JTextField();
        portField.setText("8081");
        inputPanel.add(new JLabel("Endereço:"));
        inputPanel.add(addressField);
        inputPanel.add(new JLabel("Porta:"));
        inputPanel.add(portField);

        JButton connectButton = new JButton("Conectar");
        connectButton.addActionListener(e -> {
            try {
                address = addressField.getText();
                port = Integer.parseInt(portField.getText());
                dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "Porta inválida", "Erro",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(connectButton);

        add(inputPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public static ConnectionResult showDialog(JFrame parent) {
        ConnectionDialog dialog = new ConnectionDialog(parent);
        dialog.setVisible(true);

        if (dialog.getPort() == -1) {
            return null;
        }

        return new ConnectionResult(dialog.getAddress(), dialog.getPort());
    }

    public record ConnectionResult(String address, int port) {}
}