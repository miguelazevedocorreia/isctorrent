package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.download.DownloadTasksManager;
import pt.iscte.pcd.isctorrent.gui.GUI;
import pt.iscte.pcd.isctorrent.network.BlockRequestHandler;
import pt.iscte.pcd.isctorrent.network.ConnectionManager;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.network.SearchResultsCollector;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;

import javax.swing.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Classe principal do IscTorrent
 * Coordena todos os componentes do sistema P2P
 */
public class IscTorrent {
    private final int port;
    private final String workingDirectory;
    private final GUI gui;
    private final ConnectionManager connectionManager;
    private final FileManager fileManager;
    private final DownloadTasksManager downloadManager;
    private final BlockRequestHandler blockHandler;
    private final Thread blockHandlerThread;

    /**
     * Construtor do IscTorrent
     * @param port Porta para receber conexões
     * @param workingDirectory Diretório de trabalho
     */
    public IscTorrent(int port, String workingDirectory) {
        this.port = port;
        this.workingDirectory = workingDirectory;

        // Inicializar componentes
        this.fileManager = new FileManager(workingDirectory, port);
        this.downloadManager = new DownloadTasksManager(this);

        // Inicializar handler de blocos
        this.blockHandler = new BlockRequestHandler(fileManager);
        this.blockHandlerThread = new Thread(blockHandler, "BlockHandler");
        this.blockHandlerThread.start();

        this.connectionManager = new ConnectionManager(port, this);
        this.gui = new GUI(this, port);

        System.out.println("[IscTorrent] Sistema iniciado na porta " + port);
        System.out.println("[IscTorrent] Diretório de trabalho: " + workingDirectory);
    }

    /**
     * Pesquisa ficheiros na rede
     * @param keyword Palavra-chave a pesquisar
     */
    public void searchFiles(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            JOptionPane.showMessageDialog(gui,
                    "Por favor insira uma palavra-chave",
                    "Pesquisa Inválida",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Criar mensagem de pesquisa
            WordSearchMessage searchMessage = new WordSearchMessage(
                    keyword,
                    InetAddress.getLocalHost().getHostAddress(),
                    port
            );

            // Pesquisa local
            List<FileSearchResult> localResults = fileManager.searchFiles(searchMessage);
            System.out.println("[Pesquisa] Resultados locais: " + localResults.size());

            // Verificar conexões ativas
            int activeConnections = connectionManager.getActiveConnectionsCount();
            if (activeConnections == 0) {
                // Apenas resultados locais
                gui.displaySearchResults(localResults);
                return;
            }

            // Criar coletor para resultados remotos
            SearchResultsCollector collector = new SearchResultsCollector(
                    activeConnections,
                    localResults
            );

            // Difundir pesquisa
            connectionManager.broadcastSearch(searchMessage, collector);

            // Esperar respostas (com timeout de 5 segundos)
            boolean allResponded = collector.waitForResults(5000);

            if (!allResponded) {
                System.out.println("[Pesquisa] Timeout - respostas pendentes: " +
                        collector.getPendingResponses());
            }

            // Exibir todos os resultados
            gui.displaySearchResults(collector.getAllResults());

        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(gui,
                    "Erro ao obter endereço local: " + e.getMessage(),
                    "Erro de Rede",
                    JOptionPane.ERROR_MESSAGE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Pesquisa] Interrompida: " + e.getMessage());
        }
    }

    /**
     * Inicia o download de um ficheiro
     * @param result Resultado da pesquisa com informação do ficheiro
     */
    public void startDownload(FileSearchResult result) {
        try {
            // Obter conexões para o nó que tem o ficheiro
            List<NodeConnection> sources = connectionManager.getConnectionsForNode(
                    result.getNodeAddress(),
                    result.getNodePort()
            );

            if (sources.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui,
                        "Não há conexão com o nó " + result.getNodeAddress() +
                                ":" + result.getNodePort(),
                        "Erro de Download",
                        JOptionPane.ERROR_MESSAGE));
                return;
            }

            // Iniciar download
            downloadManager.startDownload(result, sources, workingDirectory);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Download] Erro ao iniciar: " + e.getMessage());
        }
    }

    /**
     * Conecta a um novo nó
     * @param address Endereço do nó
     * @param port Porta do nó
     */
    public void connectToNode(String address, int port) {
        if (address == null || address.trim().isEmpty()) {
            JOptionPane.showMessageDialog(gui,
                    "Endereço inválido",
                    "Erro de Conexão",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (port <= 0 || port > 65535) {
            JOptionPane.showMessageDialog(gui,
                    "Porta inválida",
                    "Erro de Conexão",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectionManager.connectToNode(address, port);
    }

    /**
     * Encerra o sistema
     */
    public void shutdown() {
        System.out.println("[IscTorrent] A encerrar sistema...");

        // Parar componentes
        connectionManager.shutdown();
        downloadManager.shutdown();
        blockHandler.shutdown();

        // Aguardar thread do handler
        try {
            blockHandlerThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[IscTorrent] Sistema encerrado");
    }

    // Getters
    public FileManager getFileManager() { return fileManager; }
    public GUI getGui() { return gui; }
    public ConnectionManager getConnectionManager() { return connectionManager; }
    public BlockRequestHandler getBlockHandler() { return blockHandler; }
    public int getPort() { return port; }
    public String getWorkingDirectory() { return workingDirectory; }
}