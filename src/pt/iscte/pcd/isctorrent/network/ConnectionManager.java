package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.NewConnectionRequest;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;

import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class ConnectionManager {
    private final IscTorrent torrent;
    private final int port;
    private final List<NodeConnection> connections;
    private final ServerSocket serverSocket;
    private volatile boolean running = true;

    public ConnectionManager(int port, IscTorrent torrent) {
        this.port = port;
        this.torrent = torrent;
        // CORREÇÃO: Usar ArrayList normal com synchronized
        this.connections = new ArrayList<>();

        try {
            this.serverSocket = new ServerSocket(port);
            new Thread(this::acceptConnections).start();
        } catch (IOException e) {
            throw new RuntimeException("Falha ao iniciar servidor", e);
        }
    }

    public void connectToNode(String address, int port) {
        try {
            Socket socket = new Socket(address, port);
            NodeConnection connection = new NodeConnection(socket, torrent);

            NewConnectionRequest request = new NewConnectionRequest(
                    InetAddress.getLocalHost().getHostAddress(), this.port);
            connection.sendMessage(request);

            synchronized(connections) {
                connections.add(connection);
            }
            new Thread(connection).start();
            torrent.getGui().updateConnectionsList();

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Falha ao ligar a " + address + ":" + port,
                    "Erro de Ligação", JOptionPane.ERROR_MESSAGE));
        }
    }

    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                NodeConnection connection = new NodeConnection(socket, torrent);

                synchronized(connections) {
                    connections.add(connection);
                }
                new Thread(connection).start();

            } catch (IOException e) {
                if (running && !serverSocket.isClosed()) {
                    System.err.println("Erro ao aceitar ligação: " + e.getMessage());
                }
            }
        }
    }

    public void broadcastSearch(WordSearchMessage search, SearchResultsCollector collector) {
        List<NodeConnection> connectionsCopy;
        synchronized(connections) {
            connectionsCopy = new ArrayList<>(connections);
        }

        for (NodeConnection connection : connectionsCopy) {
            try {
                if (collector != null) {
                    connection.setSearchResultsCollector(collector);
                }
                connection.sendMessage(search);
            } catch (IOException e) {
                synchronized(connections) {
                    connections.remove(connection);
                }
                if (collector != null) {
                    collector.addResults(Collections.emptyList());
                }
            }
        }
    }

    public List<NodeConnection> getConnectionsForNode(String address, int port) {
        List<NodeConnection> result = new ArrayList<>();
        synchronized(connections) {
            for (NodeConnection conn : connections) {
                if (conn.getRemoteAddress().equals(address) &&
                        conn.getRemotePort() == port) {
                    result.add(conn);
                }
            }
        }
        return result;
    }

    public List<String> getConnectionsList() {
        synchronized(connections) {
            List<String> result = new ArrayList<>();
            for (NodeConnection conn : connections) {
                result.add(conn.getRemoteAddress() + ":" + conn.getRemotePort());
            }
            return result;
        }
    }

    public int getActiveConnectionsCount() {
        synchronized(connections) {
            return connections.size();
        }
    }

    public void shutdown() {
        running = false;
        synchronized(connections) {
            for (NodeConnection connection : connections) {
                connection.close();
            }
            connections.clear();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Erro ao encerrar servidor: " + e.getMessage());
        }
    }
}