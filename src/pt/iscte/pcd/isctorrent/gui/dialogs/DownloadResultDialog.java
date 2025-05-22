package pt.iscte.pcd.isctorrent.gui.dialogs;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Map;

/**
 * Diálogo para exibir resultado do download
 * Mostra estatísticas de blocos por nó e tempo decorrido
 */
public class DownloadResultDialog extends JDialog {

    /**
     * Mostra o diálogo de resultado
     * @param parent Janela principal
     * @param fileName Nome do ficheiro
     * @param blocksPerNode Blocos descarregados por nó
     * @param timeElapsed Tempo decorrido em milissegundos
     */
    public static void showResult(Window parent, String fileName,
                                  Map<String, Integer> blocksPerNode, long timeElapsed) {
        // Criar diálogo
        JDialog dialog = new JDialog(parent, "Download Completo",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        // Painel principal
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Ícone e título
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.informationIcon"));
        headerPanel.add(iconLabel, BorderLayout.WEST);

        JLabel titleLabel = new JLabel("Descarga completa.");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        // Painel de conteúdo
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Nome do ficheiro
        JLabel fileLabel = new JLabel("Ficheiro: " + fileName);
        fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(fileLabel);
        contentPanel.add(Box.createVerticalStrut(10));

        // Estatísticas por nó
        contentPanel.add(new JLabel("Blocos descarregados:"));
        contentPanel.add(Box.createVerticalStrut(5));

        int totalBlocks = 0;
        for (Map.Entry<String, Integer> entry : blocksPerNode.entrySet()) {
            String nodeInfo = entry.getKey();
            int blocks = entry.getValue();
            totalBlocks += blocks;

            JLabel nodeLabel = new JLabel(String.format(
                    "  Fornecedor [endereco=%s]: %d",
                    nodeInfo, blocks
            ));
            nodeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(nodeLabel);
        }

        contentPanel.add(Box.createVerticalStrut(10));

        // Total de blocos
        JLabel totalLabel = new JLabel("Total de blocos: " + totalBlocks);
        totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(totalLabel);

        contentPanel.add(Box.createVerticalStrut(5));

        // Tempo decorrido
        double seconds = timeElapsed / 1000.0;
        JLabel timeLabel = new JLabel(String.format("Tempo decorrido: %.1fs", seconds));
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(timeLabel);

        // Velocidade média (se houver dados suficientes)
        if (totalBlocks > 0 && seconds > 0) {
            double blocksPerSecond = totalBlocks / seconds;
            JLabel speedLabel = new JLabel(String.format(
                    "Velocidade média: %.1f blocos/s", blocksPerSecond
            ));
            speedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(speedLabel);
        }

        // Painel de botões
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton okButton = new JButton("OK");
        okButton.setMnemonic('O');
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);

        // Montar diálogo
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(mainPanel);
        dialog.getRootPane().setDefaultButton(okButton);

        // Configurar ESC para fechar
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Exibir
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}