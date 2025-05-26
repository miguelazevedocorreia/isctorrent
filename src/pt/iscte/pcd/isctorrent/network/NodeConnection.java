package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

// representa uma ligação com outro nó, usando canais de objetos
public class NodeConnection implements Runnable {
    private final Socket socket;
    private final ObjectInputStream input; // canal de objetos entrada
    private final ObjectOutputStream output; // canal de objetos saída
    private final IscTorrent torrent;
    private SearchResultsCollector searchResultsCollector;
    private volatile boolean running = true;
    private Object lastResponse; // para coordenação de respostas
    private int remoteServerPort = -1; // porta do servidor remoto

    public NodeConnection(Socket socket, IscTorrent torrent) throws IOException {
        this.socket = socket;
        this.torrent = torrent;
        // ordem importante: output primeiro para evitar deadlock
        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.output.flush();
        this.input = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        while (running && !socket.isClosed()) {
            try {
                Object message = input.readObject(); // recebe mensagem do canal
                if (message != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Falha na comunicação: " + e.getMessage());
                    break;
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Tipo de mensagem desconhecido");
                break;
            }
        }
        close();
    }

    // processa diferentes tipos de mensagem
    private void handleMessage(Object message) throws IOException {
        if (message instanceof NewConnectionRequest request) {
            this.remoteServerPort = request.port(); // guarda porta do servidor remoto
            System.out.println("Aceite conexão de " + getRemoteAddress() + ":" + getRemotePort());
            torrent.getGui().updateConnectionsList();
        }
        else if (message instanceof WordSearchMessage search) {
            handleSearch(search); // processa pesquisa
        }
        else if (message instanceof FileBlockRequestMessage request) {
            handleBlockRequest(request); // processa pedido de bloco
        }
        else if (message instanceof FileBlockAnswerMessage) {
            // coordenação: notifica thread que espera resposta
            synchronized(this) {
                lastResponse = message;
                notifyAll(); // acorda thread que espera
            }
        }
        else if (message instanceof List) {
            @SuppressWarnings("unchecked")
            List<FileSearchResult> results = (List<FileSearchResult>) message;

            if (searchResultsCollector != null) {
                searchResultsCollector.addResults(results); // adiciona ao collector
                searchResultsCollector = null;
            } else {
                torrent.getGui().addSearchResults(results); // mostra na GUI
            }
        }
    }

    // responde a pesquisa de ficheiros
    private void handleSearch(WordSearchMessage search) throws IOException {
        List<FileSearchResult> results = torrent.getFileManager().searchFiles(search.keyword());
        sendMessage(results); // envia resultados da pesquisa
    }

    // responde a pedido de bloco de ficheiro
    private void handleBlockRequest(FileBlockRequestMessage request) throws IOException {
        try {
            byte[] data = torrent.getFileManager().readFileBlock(
                    request.fileName(), request.offset(), request.length());

            FileBlockAnswerMessage response = new FileBlockAnswerMessage(data, request.offset());
            output.writeObject(response);
            output.flush();
        } catch (IOException e) {
            System.err.println("Falha ao processar bloco: " + e.getMessage());
            throw new IOException("Erro ao processar bloco", e);
        }
    }

    // envia mensagem pelo canal de objetos
    public synchronized void sendMessage(Object message) throws IOException {
        if (socket.isClosed()) {
            throw new IOException("Socket fechado");
        }
        output.writeObject(message);
        output.flush();
    }

    // coordenação: espera por resposta usando wait/notify
    public synchronized Object receiveResponse() throws IOException {
        try {
            while(lastResponse == null) {
                wait(); // bloqueia até resposta chegar
            }
            Object response = lastResponse;
            lastResponse = null;
            return response;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    public void close() {
        running = false;
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Falha ao encerrar ligação: " + e.getMessage());
        }
    }

    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        // retorna porta do servidor se conhecida, senão porta do socket
        return remoteServerPort != -1 ? remoteServerPort : socket.getPort();
    }

    public void setSearchResultsCollector(SearchResultsCollector collector) {
        this.searchResultsCollector = collector;
    }
}