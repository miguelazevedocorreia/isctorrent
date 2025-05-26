package pt.iscte.pcd.isctorrent;

import pt.iscte.pcd.isctorrent.core.IscTorrent;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // verifica argumentos
        if (args.length != 2) {
            System.out.println("Uso: java IscTorrent <porta> <diretório_trabalho>");
            return;
        }

        SwingUtilities.invokeLater(() -> { // thread-safe para GUI
            try {
                int port = Integer.parseInt(args[0]); // primeiro argumento: porta
                String workingDirectory = args[1]; // segundo argumento: pasta de trabalho

                IscTorrent torrent = new IscTorrent(port, workingDirectory);

                // garante encerramento limpo quando aplicação termina
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