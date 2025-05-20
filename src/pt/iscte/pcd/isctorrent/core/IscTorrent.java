package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.download.DownloadTasksManager;
import pt.iscte.pcd.isctorrent.gui.GUI;
import pt.iscte.pcd.isctorrent.network.ConnectionManager;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;
import pt.iscte.pcd.isctorrent.network.NodeConnection;

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
        this.downloadManager = new DownloadTasksManager();
        this.connectionManager = new ConnectionManager(port, this);
        this.gui = new GUI(this, port);

        System.out.println("IscTorrent iniciado na porta: " + port);
    }

    public synchronized void searchFiles(String keyword) {
        // Busca local
        List<FileSearchResult> localResults = fileManager.searchFiles(keyword);
        gui.addSearchResults(localResults);

        // Busca remota
        try {
            WordSearchMessage searchMessage = new WordSearchMessage(
                    keyword,
                    InetAddress.getLocalHost().getHostAddress(),
                    port
            );
            connectionManager.broadcastSearch(searchMessage);
        } catch (UnknownHostException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                    "Erro ao obter endereço local: " + e.getMessage(),
                    "Erro de Rede",
                    JOptionPane.ERROR_MESSAGE));
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

    public void shutdown() {
        connectionManager.shutdown();
        downloadManager.shutdown();
    }
}