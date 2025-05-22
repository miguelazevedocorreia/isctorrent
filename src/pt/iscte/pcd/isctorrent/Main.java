package pt.iscte.pcd.isctorrent;

import pt.iscte.pcd.isctorrent.core.IscTorrent;

import javax.swing.*;
import java.io.File;

/**
 * Classe principal do IscTorrent
 * Ponto de entrada da aplicação
 */
public class Main {

    /**
     * Método principal
     * @param args Argumentos: porta e diretório de trabalho
     */
    public static void main(String[] args) {
        // Validar argumentos
        if (args.length != 2) {
            System.err.println("Uso: java IscTorrent <porta> <diretório_trabalho>");
            System.err.println("Exemplo: java IscTorrent 8081 dl1");
            System.exit(1);
        }

        // Configurar look and feel nativo
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Usar look and feel padrão se falhar
            System.err.println("Aviso: Não foi possível usar o look and feel do sistema");
        }

        // Executar na Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Processar argumentos
                int port = parsePort(args[0]);
                String workingDirectory = validateDirectory(args[1]);

                // Criar instância do IscTorrent
                IscTorrent torrent = new IscTorrent(port, workingDirectory);

                // Registar shutdown hook para limpeza
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("\n[Main] A encerrar aplicação...");
                    torrent.shutdown();
                }, "ShutdownHook"));

                System.out.println("[Main] IscTorrent iniciado com sucesso");

            } catch (IllegalArgumentException e) {
                // Erro nos argumentos
                System.err.println("Erro: " + e.getMessage());
                JOptionPane.showMessageDialog(null,
                        e.getMessage(),
                        "Erro de Inicialização",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);

            } catch (Exception e) {
                // Erro inesperado
                System.err.println("Erro fatal: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Erro ao iniciar aplicação: " + e.getMessage(),
                        "Erro Fatal",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /**
     * Valida e converte a porta
     * @param portStr String com o número da porta
     * @return Número da porta válido
     * @throws IllegalArgumentException se a porta for inválida
     */
    private static int parsePort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);

            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(
                        "A porta deve estar entre 1 e 65535"
                );
            }

            // Avisar se usar porta privilegiada
            if (port < 1024) {
                System.out.println("[Main] Aviso: Porta " + port +
                        " é privilegiada e pode requerer permissões especiais");
            }

            return port;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "A porta deve ser um número válido: " + portStr
            );
        }
    }

    /**
     * Valida o diretório de trabalho
     * @param dirPath Caminho do diretório
     * @return Caminho absoluto do diretório
     * @throws IllegalArgumentException se o diretório for inválido
     */
    private static String validateDirectory(String dirPath) {
        File dir = new File(dirPath);

        // Criar diretório se não existir
        if (!dir.exists()) {
            System.out.println("[Main] Diretório não existe, a criar: " + dirPath);

            if (!dir.mkdirs()) {
                throw new IllegalArgumentException(
                        "Não foi possível criar o diretório: " + dirPath
                );
            }
        }

        // Verificar se é um diretório
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(
                    "O caminho especificado não é um diretório: " + dirPath
            );
        }

        // Verificar permissões
        if (!dir.canRead() || !dir.canWrite()) {
            throw new IllegalArgumentException(
                    "Sem permissões de leitura/escrita no diretório: " + dirPath
            );
        }

        return dir.getAbsolutePath();
    }
}