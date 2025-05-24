package pt.iscte.pcd.isctorrent;

import pt.iscte.pcd.isctorrent.core.IscTorrent;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Uso: java IscTorrent <porta> <diretório_trabalho>");
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            try {
                int port = Integer.parseInt(args[0]);
                String workingDirectory = args[1];

                IscTorrent torrent = new IscTorrent(port, workingDirectory);
                Runtime.getRuntime().addShutdownHook(new Thread(torrent::shutdown));

            } catch (NumberFormatException e) {
                System.err.println("Erro: A porta deve ser um número válido");
                JOptionPane.showMessageDialog(null,
                        "A porta deve ser um número válido",
                        "Erro de Inicialização",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}