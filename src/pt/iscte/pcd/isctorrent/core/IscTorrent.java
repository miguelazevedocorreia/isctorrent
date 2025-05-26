package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.concurrency.MyLock;
import pt.iscte.pcd.isctorrent.download.DownloadTasksManager;
import pt.iscte.pcd.isctorrent.gui.GUI;
import pt.iscte.pcd.isctorrent.network.ConnectionManager;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.network.SearchResultsCollector;
import pt.iscte.pcd.isctorrent.concurrency.MyCountDownLatch;

import javax.swing.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IscTorrent {
    private final int port;
    private final String workingDirectory;
    private final GUI gui;
    private final ConnectionManager connectionManager;
    private final FileManager fileManager;
    private final Map<String, DownloadTasksManager> downloadManagers;
    private final MyLock downloadLock = new MyLock();

    public IscTorrent(int port, String workingDirectory) {
        this.port = port;
        this.workingDirectory = workingDirectory;

        this.fileManager = new FileManager(workingDirectory, port);
        this.downloadManagers = new HashMap<>();
        this.connectionManager = new ConnectionManager(port, this);
        this.gui = new GUI(this, port);

        System.out.println("IscTorrent iniciado na porta: " + port);
    }

    public void searchFiles(String keyword) {
        downloadLock.lock();
        try {
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

                boolean allResponded = latch.await(5);

                gui.addSearchResults(collector.getAllResults());

                if (!allResponded) {
                    System.out.println("[Pesquisa] Timeout ao aguardar respostas de todos os nós");
                }
            } catch (UnknownHostException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                        "Erro ao obter endereço local: " + e.getMessage(),
                        "Erro de Rede",
                        JOptionPane.ERROR_MESSAGE));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[Pesquisa] Interrompido ao aguardar respostas: " + e.getMessage());
            }
        } finally {
            downloadLock.unlock();
        }
    }

    public void startDownload(FileSearchResult result) {
        downloadLock.lock();
        try {
            List<NodeConnection> sources = connectionManager.getConnectionsForNode(
                    result.nodeAddress(),
                    result.nodePort()
            );

            if (!sources.isEmpty()) {
                // Criar um novo DownloadTasksManager para cada download
                DownloadTasksManager manager = new DownloadTasksManager(this);
                downloadManagers.put(result.fileName(), manager);
                manager.startDownload(result, sources, this.workingDirectory);
            } else {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                        "Não foi possível encontrar conexão com o nó remoto",
                        "Erro de Download",
                        JOptionPane.ERROR_MESSAGE));
            }
        } finally {
            downloadLock.unlock();
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
        fileManager.shutdown();

        downloadLock.lock();
        try {
            for (DownloadTasksManager manager : downloadManagers.values()) {
                manager.shutdown();
            }
            downloadManagers.clear();
        } finally {
            downloadLock.unlock();
        }
    }
}