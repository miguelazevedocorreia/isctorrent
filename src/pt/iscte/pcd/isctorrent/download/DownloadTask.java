package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

// thread worker que descarrega blocos de um nó específico
public class DownloadTask implements Runnable {
    private final FileSearchResult fileInfo;
    private final NodeConnection connection;
    private final DownloadTasksManager manager;

    public DownloadTask(FileSearchResult fileInfo, NodeConnection connection, DownloadTasksManager manager) {
        this.fileInfo = fileInfo;
        this.connection = connection;
        this.manager = manager;
    }

    @Override
    public void run() {
        try {
            // ciclo de download: pede blocos até ficheiro estar completo
            while (!manager.isDownloadComplete(fileInfo.fileName())) {
                FileBlockRequestMessage request = manager.getNextBlock(fileInfo.fileName());
                if (request == null) { // não há mais blocos
                    break;
                }

                connection.sendMessage(request); // envia pedido do bloco
                Object response = connection.receiveResponse();

                if (response instanceof FileBlockAnswerMessage answer) {
                    manager.saveBlock(fileInfo.fileName(), answer, connection); // guarda bloco recebido
                } else {
                    manager.requeueBlock(request); // recoloca bloco na fila se erro
                }
            }
        } catch (Exception e) {
            System.err.println("Erro no download de " + fileInfo.fileName() + ": " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}