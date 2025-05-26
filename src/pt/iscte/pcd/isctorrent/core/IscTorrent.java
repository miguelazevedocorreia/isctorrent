package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.download.DownloadTasksManager;
import pt.iscte.pcd.isctorrent.gui.GUI;
import pt.iscte.pcd.isctorrent.network.ConnectionManager;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.network.SearchResultsCollector;
import pt.iscte.pcd.isctorrent.sync.MyCountDownLatch;

import javax.swing.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class IscTorrent {
    private final int port;
    private final String workingDirectory;
    private final GUI gui;
    private final ConnectionManager connectionManager;
    private final FileManager fileManager;
    private final DownloadTasksManager downloadManager;

    public IscTorrent(int port, String workingDirectory) {
        this.port = port;
        this.workingDirectory = workingDirectory;

        this.fileManager = new FileManager(workingDirectory, port);
        this.downloadManager = new DownloadTasksManager(this);
        this.connectionManager = new ConnectionManager(port, this);
        this.gui = new GUI(this, port);
    }

    public synchronized void searchFiles(String keyword) {
        List<FileSearchResult> localResults = fileManager.searchFiles(keyword);

        int activeConnections = connectionManager.getActiveConnectionsCount();
        if (activeConnections == 0) {
            gui.addSearchResults(localResults);
            return;
        }

        MyCountDownLatch latch = new MyCountDownLatch(activeConnections);
        SearchResultsCollector collector = new SearchResultsCollector(latch, localResults);

        try {
            WordSearchMessage searchMessage = new WordSearchMessage(
                    keyword,
                    InetAddress.getLocalHost().getHostAddress(),
                    port
            );
            connectionManager.broadcastSearch(searchMessage, collector);

            boolean allResponded = latch.await(Constants.SEARCH_TIMEOUT_MS);
            gui.addSearchResults(collector.getAllResults());

        } catch (UnknownHostException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                    "Erro ao obter endereço local: " + e.getMessage(),
                    "Erro de Rede",
                    JOptionPane.ERROR_MESSAGE));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void startDownload(FileSearchResult result) {
        System.out.println("Download iniciado: " + result.fileName());

        List<NodeConnection> fileConnections = connectionManager
                .getConnectionsForNode(result.nodeAddress(), result.nodePort());

        if (!fileConnections.isEmpty()) {
            downloadManager.startDownload(result, fileConnections, this.workingDirectory);
        } else {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                    "Nenhuma conexão ativa tem este ficheiro",
                    "Erro de Download", JOptionPane.ERROR_MESSAGE));
        }
    }

    public void connectToNode(String address, int port) {
        connectionManager.connectToNode(address, port);
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public GUI getGui() {
        return gui;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void shutdown() {
        connectionManager.shutdown();
        downloadManager.shutdown();
    }
}