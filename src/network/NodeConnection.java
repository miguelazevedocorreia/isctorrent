package network;

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
    // Adicionar variável para porta de escuta remota
    private int remoteListeningPort = -1;

    public NodeConnection(Socket socket, IscTorrent torrent) throws IOException {
        this.socket = socket;
        this.torrent = torrent;

        // Importante: primeiro criar output, fazer flush, depois criar input
        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.output.flush(); // Força envio do cabeçalho

        this.input = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        System.out.println("[Ligação] A iniciar processamento de mensagens para " +
                getRemoteAddress() + ":" + getRemotePort());

        while (running && !socket.isClosed()) {
            try {
                Object message = input.readObject();
                if (message != null) {
                    System.out.println("[Mensagem] Recebida mensagem do tipo: " +
                            message.getClass().getSimpleName() + " de " +
                            getRemoteAddress() + ":" + getRemotePort());
                    handleMessage(message);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[Erro] Falha na comunicação com " +
                            getRemoteAddress() + ":" + getRemotePort() + " - " + e.getMessage());
                    break;
                }
            } catch (ClassNotFoundException e) {
                System.err.println("[Erro] Tipo de mensagem desconhecido de " +
                        getRemoteAddress() + ":" + getRemotePort());
                break;
            }
        }
        close();
    }

    private void handleMessage(Object message) throws IOException {
        if (message instanceof NewConnectionRequest request) {
            // Guardar a porta de escuta do nó remoto
            remoteListeningPort = request.port();
            System.out.println("[Conexão] Recebido pedido de conexão de " +
                    getRemoteAddress() + " porta de escuta: " + remoteListeningPort);

            // Estabelecer uma conexão de retorno
            torrent.getConnectionManager().establishReturnConnection(getRemoteAddress(), remoteListeningPort);
        }
        else if (message instanceof WordSearchMessage) {
            System.out.println("[Pesquisa] A processar pedido de pesquisa de " +
                    getRemoteAddress() + ":" + getRemotePort());
            handleSearch((WordSearchMessage) message);
        }
        else if (message instanceof FileBlockRequestMessage request) {
            System.out.println("[Transferência] A processar pedido de bloco - Hash: " +
                    request.hash() + ", Offset: " + request.offset());
            handleBlockRequest(request);
        }
        else if (message instanceof FileBlockAnswerMessage) {
            System.out.println("[Transferência] Recebida resposta de bloco");
            synchronized(this) {
                lastResponse = message;
                notifyAll();
            }
        }
        else if (message instanceof List) {
            @SuppressWarnings("unchecked")
            List<FileSearchResult> results = (List<FileSearchResult>) message;
            System.out.println("[Pesquisa] Recebidos " + results.size() +
                    " resultados de " + getRemoteAddress() + ":" + getRemotePort());

            if (searchResultsCollector != null) {
                searchResultsCollector.addResults(results);
                searchResultsCollector = null; // Reset após uso
            } else {
                torrent.getGui().addSearchResults(results);
            }
        }
    }

    private void handleSearch(WordSearchMessage search) throws IOException {
        System.out.println("[Pesquisa] A procurar ficheiros com: '" + search.keyword() + "'");
        List<FileSearchResult> results = torrent.getFileManager()
                .searchFiles(search.keyword());
        System.out.println("[Pesquisa] Encontrados " + results.size() + " ficheiros");
        sendMessage(results);
    }

    private void handleBlockRequest(FileBlockRequestMessage request) throws IOException {
        try {
            System.out.println("[Transferência] A ler bloco do ficheiro - Hash: " +
                    request.hash() + ", Offset: " + request.offset());

            byte[] data = torrent.getFileManager().readFileBlock(
                    request.hash(),
                    request.offset(),
                    request.length()
            );

            FileBlockAnswerMessage response = new FileBlockAnswerMessage(
                    data,
                    request.offset()
            );

            System.out.println("[Transferência] A enviar bloco com " +
                    data.length + " bytes");

            output.writeObject(response);
            output.flush();

            System.out.println("[Transferência] Bloco enviado com sucesso");
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
        System.out.println("[Ligação] A encerrar ligação com " +
                getRemoteAddress() + ":" + getRemotePort());
        running = false;
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[Ligação] Ligação encerrada com sucesso");
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

    public int getRemoteListeningPort() {
        return remoteListeningPort;
    }

    public void setSearchResultsCollector(SearchResultsCollector collector) {
        this.searchResultsCollector = collector;
    }

}