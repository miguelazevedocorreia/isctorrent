package download;

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
            while (!manager.isDownloadComplete(fileInfo.hash())) {
                FileBlockRequestMessage request = manager.getNextBlock(fileInfo.hash());
                if (request == null) {
                    break;
                }

                connection.sendMessage(request);
                Object response = connection.receiveResponse();

                if (response instanceof FileBlockAnswerMessage answer) {
                    manager.saveBlock(fileInfo.hash(), answer, connection);
                } else {
                    manager.requeueBlock(request);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }
}