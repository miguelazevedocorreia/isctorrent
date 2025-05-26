package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class NodeConnection implements Runnable {
    private final Socket socket;
    private final ObjectInputStream input;
    private final ObjectOutputStream output;
    private final IscTorrent torrent;
    private SearchResultsCollector searchResultsCollector;
    private volatile boolean running = true;
    private Object lastResponse;

    public NodeConnection(Socket socket, IscTorrent torrent) throws IOException {
        this.socket = socket;
        this.torrent = torrent;
        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.output.flush();
        this.input = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        while (running && !socket.isClosed()) {
            try {
                Object message = input.readObject();
                if (message != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[Erro] Falha na comunicação: " + e.getMessage());
                    break;
                }
            } catch (ClassNotFoundException e) {
                System.err.println("[Erro] Tipo de mensagem desconhecido");
                break;
            }
        }
        close();
    }

    private void handleMessage(Object message) throws IOException {
        if (message instanceof NewConnectionRequest request) {
            System.out.println("[Conexão] Aceite conexão de " + getRemoteAddress() + ":" + request.port());
            // Notificar GUI da nova conexão
            torrent.getGui().updateConnectionsList();
        }
        else if (message instanceof WordSearchMessage search) {
            handleSearch(search);
        }
        else if (message instanceof FileBlockRequestMessage request) {
            handleBlockRequest(request);
        }
        else if (message instanceof FileBlockAnswerMessage) {
            synchronized(this) {
                lastResponse = message;
                notifyAll();
            }
        }
        else if (message instanceof List) {
            @SuppressWarnings("unchecked")
            List<FileSearchResult> results = (List<FileSearchResult>) message;

            if (searchResultsCollector != null) {
                searchResultsCollector.addResults(results);
                searchResultsCollector = null;
            } else {
                torrent.getGui().addSearchResults(results);
            }
        }
    }

    private void handleSearch(WordSearchMessage search) throws IOException {
        List<FileSearchResult> results = torrent.getFileManager().searchFiles(search.keyword());
        sendMessage(results);
    }

    private void handleBlockRequest(FileBlockRequestMessage request) throws IOException {
        try {
            byte[] data = torrent.getFileManager().readFileBlock(
                    request.fileName(), request.offset(), request.length());

            FileBlockAnswerMessage response = new FileBlockAnswerMessage(data, request.offset());
            output.writeObject(response);
            output.flush();
        } catch (IOException e) {
            System.err.println("[Erro] Falha ao processar bloco: " + e.getMessage());
            throw new IOException("Erro ao processar bloco", e);
        }
    }

    public synchronized void sendMessage(Object message) throws IOException {
        if (socket.isClosed()) {
            throw new IOException("Socket fechado");
        }
        output.writeObject(message);
        output.flush();
    }

    public synchronized Object receiveResponse() throws IOException {
        try {
            while(lastResponse == null) {
                wait();
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
            System.err.println("[Erro] Falha ao encerrar ligação: " + e.getMessage());
        }
    }

    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        return socket.getPort();
    }

    public void setSearchResultsCollector(SearchResultsCollector collector) {
        this.searchResultsCollector = collector;
    }
}