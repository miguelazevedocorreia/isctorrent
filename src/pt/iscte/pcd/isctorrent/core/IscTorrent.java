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
import java.util.ArrayList;
import java.util.List;

public class IscTorrent {
    private final int port;
    private final String workingDirectory;
    private final GUI gui;
    private final ConnectionManager connectionManager;
    private final FileManager fileManager;
    private final DownloadTasksManager downloadManager;

    // classe principal que coordena todos os componentes
    public IscTorrent(int port, String workingDirectory) {
        this.port = port;
        this.workingDirectory = workingDirectory;

        this.fileManager = new FileManager(workingDirectory, port);
        this.downloadManager = new DownloadTasksManager(this);
        this.connectionManager = new ConnectionManager(port, this);
        this.gui = new GUI(this, port);
    }

    // coordena pesquisa usando CountDownLatch
    public void searchFiles(String keyword) {
        List<FileSearchResult> localResults = fileManager.searchFiles(keyword);

        int activeConnections = connectionManager.getActiveConnectionsCount();
        if (activeConnections == 0) {
            gui.addSearchResults(localResults); // só resultados locais
            return;
        }

        // usa CountDownLatch para esperar por todas as respostas
        MyCountDownLatch latch = new MyCountDownLatch(activeConnections);
        SearchResultsCollector collector = new SearchResultsCollector(latch, localResults);

        try {
            WordSearchMessage searchMessage = new WordSearchMessage(
                    keyword,
                    InetAddress.getLocalHost().getHostAddress(),
                    port
            );
            connectionManager.broadcastSearch(searchMessage, collector);

            // espera por todas as respostas ou timeout
            latch.await(Constants.SEARCH_TIMEOUT_MS);
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

    // inicia download com múltiplas threads, uma por nó
    public void startDownloadFromMultipleNodes(List<FileSearchResult> results) {
        if (results.isEmpty()) return;

        String fileName = results.get(0).fileName();
        System.out.println("Download iniciado: " + fileName);

        List<NodeConnection> allConnections = new ArrayList<>();

        // recolhe todas as conexões disponíveis para o ficheiro
        for (FileSearchResult result : results) {
            List<NodeConnection> nodeConnections = connectionManager
                    .getConnectionsForNode(result.nodeAddress(), result.nodePort());
            allConnections.addAll(nodeConnections);
        }

        if (!allConnections.isEmpty()) {
            downloadManager.startDownload(results.get(0), allConnections, this.workingDirectory);
        } else {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                    "Nenhuma conexão ativa tem este ficheiro disponível",
                    "Erro de Download", JOptionPane.ERROR_MESSAGE));
        }
    }

    // estabelece ligação a outro nó
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

    // termina todas as operações em curso
    public void shutdown() {
        connectionManager.shutdown();
        downloadManager.shutdown();
    }
}