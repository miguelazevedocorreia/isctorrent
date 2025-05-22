package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.NewConnectionRequest;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;

import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestor de conexões de rede
 * Responsável por aceitar conexões e gerir nós conectados
 */
public class ConnectionManager {
    private final IscTorrent torrent;
    private final int port;
    private final List<NodeConnection> connections;
    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private volatile boolean running = true;

    // Controlo de conexões duplicadas
    private final Set<String> connectedNodes = Collections.synchronizedSet(new HashSet<>());

    /**
     * Construtor do gestor
     * @param port Porta para aceitar conexões
     * @param torrent Instância principal do IscTorrent
     */
    public ConnectionManager(int port, IscTorrent torrent) {
        this.port = port;
        this.torrent = torrent;
        this.connections = new CopyOnWriteArrayList<>();

        try {
            this.serverSocket = new ServerSocket(port);
            System.out.println("[Servidor] Iniciado na porta " + port);

            // Thread para aceitar conexões
            this.acceptThread = new Thread(this::acceptConnections, "AcceptThread");
            this.acceptThread.start();

        } catch (IOException e) {
            System.err.println("[Servidor] Erro ao iniciar na porta " + port);
            throw new RuntimeException("Falha ao iniciar servidor", e);
        }
    }

    /**
     * Aceita conexões de entrada
     */
    private void acceptConnections() {
        System.out.println("[Servidor] A aguardar conexões...");

        while (running && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                String clientInfo = socket.getInetAddress().getHostAddress() +
                        ":" + socket.getPort();

                System.out.println("[Servidor] Nova conexão de " + clientInfo);

                // Criar e iniciar conexão
                NodeConnection connection = new NodeConnection(socket, torrent);
                connections.add(connection);
                new Thread(connection, "NodeConn-" + clientInfo).start();

            } catch (IOException e) {
                if (running) {
                    System.err.println("[Servidor] Erro ao aceitar conexão: " +
                            e.getMessage());
                }
            }
        }

        System.out.println("[Servidor] Parou de aceitar conexões");
    }

    /**
     * Conecta a um nó remoto
     * @param address Endereço do nó
     * @param port Porta do nó
     */
    public void connectToNode(String address, int port) {
        connectToNode(address, port, false);
    }

    /**
     * Estabelece conexão de retorno
     * @param address Endereço do nó
     * @param port Porta de escuta do nó
     */
    public void establishReturnConnection(String address, int port) {
        String nodeKey = address + ":" + port;

        if (connectedNodes.contains(nodeKey)) {
            System.out.println("[Conexão] Já existe conexão com " + nodeKey);
            return;
        }

        System.out.println("[Conexão] A estabelecer conexão de retorno para " + nodeKey);
        connectToNode(address, port, true);
    }

    /**
     * Conecta a um nó remoto
     * @param address Endereço do nó
     * @param port Porta do nó
     * @param isReturnConnection Se é uma conexão de retorno
     */
    private void connectToNode(String address, int port, boolean isReturnConnection) {
        String nodeKey = address + ":" + port;

        // Verificar conexão duplicada
        if (connectedNodes.contains(nodeKey)) {
            if (!isReturnConnection) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Já existe uma conexão com " + nodeKey,
                                "Conexão Duplicada",
                                JOptionPane.WARNING_MESSAGE)
                );
            }
            return;
        }

        System.out.println("[Cliente] A conectar a " + nodeKey);

        try {
            Socket socket = new Socket(address, port);
            NodeConnection connection = new NodeConnection(socket, torrent);

            // Enviar pedido de conexão se não for retorno
            if (!isReturnConnection) {
                NewConnectionRequest request = new NewConnectionRequest(
                        InetAddress.getLocalHost().getHostAddress(),
                        this.port
                );
                connection.sendMessage(request);
            }

            // Adicionar conexão
            connections.add(connection);
            connectedNodes.add(nodeKey);
            new Thread(connection, "NodeConn-" + nodeKey).start();

            System.out.println("[Cliente] Conectado com sucesso a " + nodeKey);

            if (!isReturnConnection) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "Conectado com sucesso a " + nodeKey,
                                "Conexão Estabelecida",
                                JOptionPane.INFORMATION_MESSAGE)
                );
            }

        } catch (IOException e) {
            String errorMsg = "Falha ao conectar a " + nodeKey + ": " + e.getMessage();
            System.err.println("[Cliente] " + errorMsg);

            if (!isReturnConnection) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                errorMsg,
                                "Erro de Conexão",
                                JOptionPane.ERROR_MESSAGE)
                );
            }
        }
    }

    /**
     * Difunde uma pesquisa para todos os nós
     * @param search Mensagem de pesquisa
     * @param collector Coletor de resultados
     */
    public void broadcastSearch(WordSearchMessage search, SearchResultsCollector collector) {
        System.out.println("[Pesquisa] A difundir para " + connections.size() +
                " conexões");

        List<NodeConnection> failedConnections = new ArrayList<>();

        for (NodeConnection connection : connections) {
            try {
                // Associar coletor se fornecido
                if (collector != null) {
                    connection.setSearchResultsCollector(collector);
                }

                connection.sendMessage(search);

                System.out.println("[Pesquisa] Enviada para " +
                        connection.getRemoteAddress() + ":" +
                        connection.getRemotePort());

            } catch (IOException e) {
                System.err.println("[Pesquisa] Falha ao enviar para " +
                        connection.getRemoteAddress());
                failedConnections.add(connection);

                // Notificar coletor da falha
                if (collector != null) {
                    collector.addResults(Collections.emptyList());
                }
            }
        }

        // Remover conexões falhadas
        for (NodeConnection failed : failedConnections) {
            removeConnection(failed);
        }
    }

    /**
     * Obtém conexões para um nó específico
     * @param address Endereço do nó
     * @param port Porta do nó
     * @return Lista de conexões disponíveis
     */
    public List<NodeConnection> getConnectionsForNode(String address, int port) {
        List<NodeConnection> result = new ArrayList<>();

        for (NodeConnection conn : connections) {
            // Verificar por endereço e porta de escuta
            if (conn.getRemoteAddress().equals(address) &&
                    (conn.getRemoteListeningPort() == port ||
                            conn.getRemotePort() == port)) {

                if (conn.isConnected()) {
                    result.add(conn);
                }
            }
        }

        System.out.println("[Conexão] Encontradas " + result.size() +
                " conexões para " + address + ":" + port);
        return result;
    }

    /**
     * Remove uma conexão
     * @param connection Conexão a remover
     */
    private void removeConnection(NodeConnection connection) {
        connections.remove(connection);

        // Remover do conjunto de nós conectados
        String nodeKey = connection.getRemoteAddress() + ":" +
                connection.getRemoteListeningPort();
        connectedNodes.remove(nodeKey);

        connection.close();
    }

    /**
     * Obtém o número de conexões ativas
     * @return Número de conexões
     */
    public int getActiveConnectionsCount() {
        return connections.size();
    }

    /**
     * Obtém lista de conexões ativas
     * @return Lista de conexões
     */
    public List<NodeConnection> getActiveConnections() {
        return new ArrayList<>(connections);
    }

    /**
     * Encerra o gestor de conexões
     */
    public void shutdown() {
        System.out.println("[Servidor] A encerrar gestor de conexões...");
        running = false;

        // Fechar todas as conexões
        for (NodeConnection connection : connections) {
            connection.close();
        }
        connections.clear();
        connectedNodes.clear();

        // Fechar servidor
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Servidor] Erro ao fechar socket: " + e.getMessage());
        }

        // Aguardar thread de aceitação
        try {
            acceptThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[Servidor] Gestor de conexões encerrado");
    }
}