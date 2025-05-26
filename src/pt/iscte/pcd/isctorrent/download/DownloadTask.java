package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

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
            System.out.println("[DownloadTask] Iniciado para " + fileInfo.fileName());
            while (!manager.isDownloadComplete(fileInfo.fileName())) {
                FileBlockRequestMessage request = manager.getNextBlock(fileInfo.fileName());
                if (request == null) {
                    System.out.println("[DownloadTask] getNextBlock retornou null para " + fileInfo.fileName());
                    break;
                }

                System.out.println("[DownloadTask] A enviar pedido para " + fileInfo.fileName() + " offset: " + request.offset());
                connection.sendMessage(request);
                Object response = connection.receiveResponse();

                if (response instanceof FileBlockAnswerMessage answer) {
                    manager.saveBlock(fileInfo.fileName(), answer, connection);
                } else {
                    System.out.println("[DownloadTask] Resposta inválida, a recolocar bloco na fila");
                    manager.requeueBlock(request);
                }
            }
            System.out.println("[DownloadTask] Terminado para " + fileInfo.fileName());
        } catch (Exception e) {
            System.err.println("[DownloadTask] Erro: " + e.getMessage());
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
}