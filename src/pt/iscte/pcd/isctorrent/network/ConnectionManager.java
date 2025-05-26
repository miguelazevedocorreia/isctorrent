package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.NewConnectionRequest;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;

import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.*;

// gere ligações entre nós
public class ConnectionManager {
    private final IscTorrent torrent;
    private final int port;
    private final List<NodeConnection> connections; // lista de conexões ativas
    private final ServerSocket serverSocket;
    private volatile boolean running = true;

    public ConnectionManager(int port, IscTorrent torrent) {
        this.port = port;
        this.torrent = torrent;
        this.connections = new ArrayList<>(); // sincronizada com synchronized

        try {
            this.serverSocket = new ServerSocket(port);
            new Thread(this::acceptConnections).start(); // thread para aceitar ligações
        } catch (IOException e) {
            throw new RuntimeException("Falha ao iniciar servidor", e);
        }
    }

    // liga ativamente a outro nó
    public void connectToNode(String address, int port) {
        try {
            Socket socket = new Socket(address, port);
            NodeConnection connection = new NodeConnection(socket, torrent);

            // envia pedido de ligação
            NewConnectionRequest request = new NewConnectionRequest(
                    InetAddress.getLocalHost().getHostAddress(), this.port);
            connection.sendMessage(request);

            synchronized(connections) { // protege lista partilhada
                connections.add(connection);
            }
            new Thread(connection).start(); // thread para gerir esta conexão
            torrent.getGui().updateConnectionsList();

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Falha ao ligar a " + address + ":" + port,
                    "Erro de Ligação", JOptionPane.ERROR_MESSAGE));
        }
    }

    // aceita ligações passivamente
    private void acceptConnections() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept(); // bloqueia até nova ligação
                NodeConnection connection = new NodeConnection(socket, torrent);

                synchronized(connections) { // protege secção crítica
                    connections.add(connection);
                }
                new Thread(connection).start(); // nova thread por conexão

            } catch (IOException e) {
                if (running && !serverSocket.isClosed()) {
                    System.err.println("Erro ao aceitar ligação: " + e.getMessage());
                }
            }
        }
    }

    // envia pesquisa para todos os nós ligados
    public void broadcastSearch(WordSearchMessage search, SearchResultsCollector collector) {
        List<NodeConnection> connectionsCopy;
        synchronized(connections) { // copia para evitar modificação concorrente
            connectionsCopy = new ArrayList<>(connections);
        }

        for (NodeConnection connection : connectionsCopy) {
            try {
                if (collector != null) {
                    connection.setSearchResultsCollector(collector);
                }
                connection.sendMessage(search); // envia para cada nó
            } catch (IOException e) {
                synchronized(connections) {
                    connections.remove(connection); // remove se falhou
                }
                if (collector != null) {
                    collector.addResults(Collections.emptyList()); // conta como resposta vazia
                }
            }
        }
    }

    // encontra conexões para um nó específico
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

    // lista de conexões para GUI
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

    // termina todas as conexões
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