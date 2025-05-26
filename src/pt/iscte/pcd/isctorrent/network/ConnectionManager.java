package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.NewConnectionRequest;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;

import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ConnectionManager {
    private final IscTorrent torrent;
    private final int port;
    private final List<NodeConnection> connections;
    private final ServerSocket serverSocket;
    private volatile boolean running = true;

    public ConnectionManager(int port, IscTorrent torrent) {
        this.port = port;
        this.torrent = torrent;
        this.connections = new CopyOnWriteArrayList<>();

        try {
            this.serverSocket = new ServerSocket(port);
            System.out.println("[Servidor] Iniciado na porta " + port);
            new Thread(this::acceptConnections).start();
        } catch (IOException e) {
            System.err.println("[Servidor] Falha ao iniciar servidor na porta " + port);
            throw new RuntimeException("Falha ao iniciar servidor", e);
        }
    }

    public void connectToNode(String address, int port) {
        try {
            Socket socket = new Socket(address, port);
            NodeConnection connection = new NodeConnection(socket, torrent);

            // Enviar apenas identificação
            NewConnectionRequest request = new NewConnectionRequest(
                    InetAddress.getLocalHost().getHostAddress(), this.port);
            connection.sendMessage(request);

            connections.add(connection);
            new Thread(connection).start();

            // Notificar GUI da nova conexão
            torrent.getGui().updateConnectionsList();

        } catch (IOException e) {
            String errorMsg = "Falha ao ligar a " + address + ":" + port + " - " + e.getMessage();
            System.err.println("[Cliente] " + errorMsg);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, errorMsg,
                    "Erro de Ligação", JOptionPane.ERROR_MESSAGE));
        }
    }

    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                String clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                System.out.println("[Servidor] Nova ligação aceite de " + clientAddress);

                NodeConnection connection = new NodeConnection(socket, torrent);
                connections.add(connection);
                new Thread(connection).start();

            } catch (IOException e) {
                if (running && !serverSocket.isClosed()) {
                    System.err.println("[Servidor] Erro ao aceitar ligação: " + e.getMessage());
                }
            }
        }
    }

    public void broadcastSearch(WordSearchMessage search) {
        broadcastSearch(search, null);
    }

    public void broadcastSearch(WordSearchMessage search, SearchResultsCollector collector) {
        for (NodeConnection connection : connections) {
            try {
                if (collector != null) {
                    connection.setSearchResultsCollector(collector);
                }
                connection.sendMessage(search);
            } catch (IOException e) {
                System.err.println("[Pesquisa] Erro ao enviar pesquisa");
                connections.remove(connection);
                if (collector != null) {
                    collector.addResults(Collections.emptyList());
                }
            }
        }
    }

    public List<NodeConnection> getConnectionsForNode(String address, int port) {
        List<NodeConnection> result = new ArrayList<>();
        for (NodeConnection conn : connections) {
            if (conn.getRemoteAddress().equals(address)) {
                result.add(conn);
            }
        }
        return result;
    }

    public List<String> getConnectionsList() {
        return connections.stream()
                .map(conn -> conn.getRemoteAddress() + ":" + conn.getRemotePort())
                .collect(Collectors.toList());
    }

    public int getActiveConnectionsCount() {
        return connections.size();
    }

    public void shutdown() {
        running = false;
        for (NodeConnection connection : connections) {
            connection.close();
        }
        connections.clear();
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("[Servidor] Erro ao encerrar servidor: " + e.getMessage());
        }
    }
}