package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.protocol.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

/**
 * Conexão com um nó remoto
 * Gere a comunicação bidirecional através de sockets
 */
public class NodeConnection implements Runnable {
    private final Socket socket;
    private final ObjectInputStream input;
    private final ObjectOutputStream output;
    private final IscTorrent torrent;

    private SearchResultsCollector searchResultsCollector;
    private volatile boolean running = true;
    private Object lastResponse;

    // Porta de escuta do nó remoto
    private int remoteListeningPort = -1;

    /**
     * Construtor da conexão
     * @param socket Socket da conexão
     * @param torrent Instância principal do IscTorrent
     */
    public NodeConnection(Socket socket, IscTorrent torrent) throws IOException {
        this.socket = socket;
        this.torrent = torrent;

        // Criar streams (output primeiro, depois input)
        this.output = new ObjectOutputStream(socket.getOutputStream());
        this.output.flush();
        this.input = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        System.out.println("[Conexão] Iniciada com " + getRemoteAddress() +
                ":" + getRemotePort());

        while (running && !socket.isClosed()) {
            try {
                Object message = input.readObject();
                if (message != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[Conexão] Erro de comunicação: " +
                            e.getMessage());
                }
                break;
            } catch (ClassNotFoundException e) {
                System.err.println("[Conexão] Tipo de mensagem desconhecido");
            }
        }

        close();
    }

    /**
     * Processa mensagens recebidas
     * @param message Mensagem a processar
     */
    private void handleMessage(Object message) throws IOException {
        // Pedido de nova conexão
        if (message instanceof NewConnectionRequest request) {
            handleNewConnection(request);
        }
        // Pedido de pesquisa
        else if (message instanceof WordSearchMessage search) {
            handleSearch(search);
        }
        // Pedido de bloco - delegar ao BlockHandler
        else if (message instanceof FileBlockRequestMessage request) {
            handleBlockRequest(request);
        }
        // Resposta de bloco
        else if (message instanceof FileBlockAnswerMessage answer) {
            handleBlockAnswer(answer);
        }
        // Lista de resultados de pesquisa
        else if (message instanceof List<?>) {
            handleSearchResults(message);
        }
    }

    /**
     * Processa pedido de nova conexão
     * @param request Pedido de conexão
     */
    private void handleNewConnection(NewConnectionRequest request) {
        remoteListeningPort = request.port();
        System.out.println("[Conexão] Pedido recebido de " + request.address() +
                ":" + request.port());

        // Estabelecer conexão de retorno se necessário
        torrent.getConnectionManager().establishReturnConnection(
                request.address(),
                request.port()
        );
    }

    /**
     * Processa pedido de pesquisa
     * @param search Mensagem de pesquisa
     */
    private void handleSearch(WordSearchMessage search) throws IOException {
        System.out.println("[Pesquisa] Recebida: '" + search.keyword() + "'");

        // Pesquisar ficheiros locais
        List<FileSearchResult> results = torrent.getFileManager().searchFiles(search);

        // Enviar resultados
        sendMessage(results);

        System.out.println("[Pesquisa] Enviados " + results.size() + " resultados");
    }

    /**
     * Processa pedido de bloco
     * @param request Pedido de bloco
     */
    private void handleBlockRequest(FileBlockRequestMessage request) {
        // Delegar ao BlockHandler para evitar sobrecarga
        torrent.getBlockHandler().addRequest(request, this);
    }

    /**
     * Processa resposta de bloco
     * @param answer Resposta com dados do bloco
     */
    private void handleBlockAnswer(FileBlockAnswerMessage answer) {
        synchronized(this) {
            lastResponse = answer;
            notifyAll();
        }
    }

    /**
     * Processa resultados de pesquisa
     * @param message Lista de resultados
     */
    @SuppressWarnings("unchecked")
    private void handleSearchResults(Object message) {
        List<FileSearchResult> results = (List<FileSearchResult>) message;

        System.out.println("[Pesquisa] Recebidos " + results.size() +
                " resultados de " + getRemoteAddress());

        if (searchResultsCollector != null) {
            searchResultsCollector.addResults(results);
            searchResultsCollector = null; // Limpar após uso
        } else {
            // Se não há coletor, adicionar diretamente à GUI
            torrent.getGui().addSearchResults(results);
        }
    }

    /**
     * Envia uma mensagem
     * @param message Mensagem a enviar
     */
    public synchronized void sendMessage(Object message) throws IOException {
        if (socket.isClosed()) {
            throw new IOException("Socket fechado");
        }

        output.writeObject(message);
        output.flush();
    }

    /**
     * Recebe uma resposta (bloqueia até receber)
     * @return Resposta recebida
     */
    public synchronized Object receiveResponse() throws IOException {
        try {
            while (lastResponse == null && running) {
                wait(10000); // Timeout de 10 segundos

                if (lastResponse == null) {
                    throw new IOException("Timeout ao aguardar resposta");
                }
            }

            Object response = lastResponse;
            lastResponse = null;
            return response;

        } catch (InterruptedException e) {
            throw new IOException("Interrompido ao aguardar resposta", e);
        }
    }

    /**
     * Fecha a conexão
     */
    public void close() {
        System.out.println("[Conexão] A fechar conexão com " + getRemoteAddress());
        running = false;

        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // Ignorar erros ao fechar
        }

        // Acordar threads em espera
        synchronized(this) {
            notifyAll();
        }
    }

    /**
     * Define o coletor de resultados de pesquisa
     * @param collector Coletor a usar
     */
    public void setSearchResultsCollector(SearchResultsCollector collector) {
        this.searchResultsCollector = collector;
    }

    // Getters
    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        return socket.getPort();
    }

    public int getRemoteListeningPort() {
        return remoteListeningPort;
    }

    public boolean isConnected() {
        return !socket.isClosed() && running;
    }
}