package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.download.DownloadTasksManager;
import pt.iscte.pcd.isctorrent.gui.GUI;
import pt.iscte.pcd.isctorrent.network.ConnectionManager;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.network.SearchResultsCollector;

import javax.swing.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        System.out.println("IscTorrent iniciado na porta: " + port);
    }

    public synchronized void searchFiles(String keyword) {
        // Busca local
        List<FileSearchResult> localResults = fileManager.searchFiles(keyword);

        // Iniciar CountDownLatch para esperar respostas remotas
        int activeConnections = connectionManager.getActiveConnectionsCount();
        if (activeConnections == 0) {
            // Se não há conexões ativas, apenas mostre os resultados locais
            gui.addSearchResults(localResults);
            return;
        }

        CountDownLatch latch = new CountDownLatch(activeConnections);
        SearchResultsCollector collector = new SearchResultsCollector(latch, localResults);

        // Busca remota
        try {
            WordSearchMessage searchMessage = new WordSearchMessage(
                    keyword,
                    InetAddress.getLocalHost().getHostAddress(),
                    port
            );
            connectionManager.broadcastSearch(searchMessage, collector);

            // Esperar (com timeout) pelas respostas ou até o latch chegar a zero
            boolean allResponded = latch.await(5, TimeUnit.SECONDS);

            // Exibir resultados coletados
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
    }

    public synchronized void startDownload(FileSearchResult result) {
        List<NodeConnection> sources = connectionManager.getConnectionsForNode(
                result.nodeAddress(),
                result.nodePort()
        );

        if (!sources.isEmpty()) {
            downloadManager.startDownload(result, sources, this.workingDirectory);
        } else {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                    "Não foi possível encontrar conexão com o nó remoto",
                    "Erro de Download",
                    JOptionPane.ERROR_MESSAGE));
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