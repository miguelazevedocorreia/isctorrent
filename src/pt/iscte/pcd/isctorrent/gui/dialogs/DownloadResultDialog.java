package pt.iscte.pcd.isctorrent.gui.dialogs;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class DownloadResultDialog extends JDialog {

    // mostra estatísticas do download
    public static void showResult(Window parent, String fileName, Map<String, Integer> blocksPerNode, long timeElapsed) {
        JDialog dialog = new JDialog(parent, "Download Completo", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Descarga completa.");
        contentPanel.add(titleLabel);

        // mostra quantos blocos foram descarregados de cada nó
        for (Map.Entry<String, Integer> entry : blocksPerNode.entrySet()) {
            JLabel nodeLabel = new JLabel("Fornecedor [endereco=" + entry.getKey() + "]: " + entry.getValue());
            contentPanel.add(nodeLabel);
        }

        // tempo total decorrido
        JLabel timeLabel = new JLabel("Tempo decorrido: " + (timeElapsed/1000) + "s");
        contentPanel.add(timeLabel);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dialog.dispose());

        panel.add(contentPanel, BorderLayout.CENTER);
        panel.add(okButton, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}