package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import java.io.IOException;

/**
 * Tarefa de download de um ficheiro
 * Executa numa thread dedicada para cada nó fonte
 */
public class DownloadTask implements Runnable {
    private final FileSearchResult fileInfo;
    private final NodeConnection connection;
    private final DownloadTasksManager manager;

    /**
     * Construtor da tarefa
     * @param fileInfo Informação do ficheiro
     * @param connection Conexão com o nó fonte
     * @param manager Gestor de downloads
     */
    public DownloadTask(FileSearchResult fileInfo, NodeConnection connection,
                        DownloadTasksManager manager) {
        this.fileInfo = fileInfo;
        this.connection = connection;
        this.manager = manager;
    }

    @Override
    public void run() {
        String fileName = fileInfo.getFileName();
        String nodeInfo = connection.getRemoteAddress() + ":" +
                connection.getRemotePort();

        System.out.println("[Download] Thread iniciada para " + fileName +
                " de " + nodeInfo);

        try {
            int blocksDownloaded = 0;

            // Continuar enquanto houver blocos
            while (!manager.isDownloadComplete(fileName)) {
                // Obter próximo bloco
                FileBlockRequestMessage request = manager.getNextBlock(fileName);

                if (request == null) {
                    // Não há mais blocos pendentes
                    break;
                }

                try {
                    // Enviar pedido
                    connection.sendMessage(request);

                    // Aguardar resposta
                    Object response = connection.receiveResponse();

                    if (response instanceof FileBlockAnswerMessage answer) {
                        // Guardar bloco recebido
                        manager.saveBlock(fileName, answer, connection);
                        blocksDownloaded++;

                        System.out.println("[Download] Bloco recebido de " + nodeInfo +
                                " - Offset: " + answer.offset());
                    } else {
                        System.err.println("[Download] Resposta inválida de " + nodeInfo);
                        // Recolocar bloco na fila
                        manager.requeueBlock(request);
                    }

                } catch (IOException e) {
                    System.err.println("[Download] Erro de comunicação com " + nodeInfo +
                            ": " + e.getMessage());
                    // Recolocar bloco na fila para outro nó tentar
                    manager.requeueBlock(request);

                    // Se perdeu conexão, terminar
                    if (!connection.isConnected()) {
                        break;
                    }
                }
            }

            System.out.println("[Download] Thread terminada - " + nodeInfo +
                    " forneceu " + blocksDownloaded + " blocos");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[Download] Thread interrompida para " + nodeInfo);
        } catch (Exception e) {
            System.err.println("[Download] Erro inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}