package network;

import protocol.*;
import java.io.*;
import java.net.Socket;

/**
 * Handler para novas conexões de cliente
 */
public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private NetworkManager networkManager;

    public ClientHandler(Socket clientSocket, NetworkManager networkManager) {
        this.clientSocket = clientSocket;
        this.networkManager = networkManager;
    }

    @Override
    public void run() {
        try {
            // Criar conexão
            String nodeKey = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
            NodeConnection connection = new NodeConnection(clientSocket, nodeKey);

            // Adicionar à lista de conexões
            networkManager.getConnections().put(nodeKey, connection);

            // Escutar mensagens desta conexão
            ConnectionListener listener = new ConnectionListener(connection, networkManager);
            listener.run(); // Executar no mesmo thread

        } catch (IOException e) {
            System.err.println("Erro ao processar cliente: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (IOException closeError) {
                System.err.println("Erro ao fechar socket do cliente: " + closeError.getMessage());
            }
        }
    }
}