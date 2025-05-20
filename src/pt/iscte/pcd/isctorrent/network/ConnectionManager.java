package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.NewConnectionRequest;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;

import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

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
        System.out.println("[Cliente] A tentar estabelecer ligação a " + address + ":" + port);

        try {
            Socket socket = new Socket(address, port);
            System.out.println("[Cliente] Ligação estabelecida com " + address + ":" + port);

            NodeConnection connection = new NodeConnection(socket, torrent);

            NewConnectionRequest request = new NewConnectionRequest(
                    InetAddress.getLocalHost().getHostAddress(),
                    this.port
            );
            connection.sendMessage(request);
            System.out.println("[Cliente] Enviado pedido de ligação para " + address + ":" + port);

            connections.add(connection);
            new Thread(connection).start();

        } catch (IOException e) {
            String errorMsg = "Falha ao ligar a " + address + ":" + port + " - " + e.getMessage();
            System.err.println("[Cliente] " + errorMsg);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, errorMsg, "Erro de Ligação",
                    JOptionPane.ERROR_MESSAGE));
        }
    }

    private void acceptConnections() {
        System.out.println("[Servidor] A iniciar aceitação de ligações");
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
        System.out.println("[Pesquisa] A iniciar difusão de pesquisa por '" + search.keyword() +
                "' para " + connections.size() + " ligações");

        for (NodeConnection connection : connections) {
            try {
                connection.sendMessage(search);
                System.out.println("[Pesquisa] Pesquisa enviada para " +
                        connection.getRemoteAddress() + ":" + connection.getRemotePort());
            } catch (IOException e) {
                System.err.println("[Pesquisa] Erro ao enviar pesquisa para " +
                        connection.getRemoteAddress() + ":" + connection.getRemotePort());
                connections.remove(connection);
            }
        }
    }

    public List<NodeConnection> getConnectionsForNode(String address, int port) {
        System.out.println("[Ligação] A procurar ligações para " + address + ":" + port);
        List<NodeConnection> result = new ArrayList<>();
        for (NodeConnection conn : connections) {
            if (conn.getRemoteAddress().equals(address) && conn.getRemotePort() == port) {
                result.add(conn);
            }
        }
        System.out.println("[Ligação] Encontradas " + result.size() + " ligações");
        return result;
    }

    public void shutdown() {
        System.out.println("[Servidor] A iniciar encerramento do servidor");
        running = false;
        for (NodeConnection connection : connections) {
            connection.close();
        }
        connections.clear();
        try {
            serverSocket.close();
            System.out.println("[Servidor] Servidor encerrado com sucesso");
        } catch (IOException e) {
            System.err.println("[Servidor] Erro ao encerrar servidor: " + e.getMessage());
        }
    }
}