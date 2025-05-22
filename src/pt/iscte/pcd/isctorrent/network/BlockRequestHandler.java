package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.core.FileManager;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.sync.MyCondition;
import pt.iscte.pcd.isctorrent.sync.MyLock;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Thread única para processar pedidos de blocos
 * Evita sobrecarga do nó com múltiplos pedidos simultâneos
 */
public class BlockRequestHandler implements Runnable {
    private final FileManager fileManager;
    private final Queue<BlockRequest> requestQueue;
    private final MyLock lock;
    private final MyCondition hasRequests;
    private volatile boolean running;

    /**
     * Estrutura para armazenar um pedido pendente
     */
    private static class BlockRequest {
        final FileBlockRequestMessage request;
        final NodeConnection connection;

        BlockRequest(FileBlockRequestMessage request, NodeConnection connection) {
            this.request = request;
            this.connection = connection;
        }
    }

    /**
     * Construtor do handler
     * @param fileManager Gestor de ficheiros
     */
    public BlockRequestHandler(FileManager fileManager) {
        this.fileManager = fileManager;
        this.requestQueue = new LinkedList<>();
        this.lock = new MyLock();
        this.hasRequests = new MyCondition(lock);
        this.running = true;
    }

    /**
     * Adiciona um pedido à fila
     * @param request Pedido de bloco
     * @param connection Conexão que fez o pedido
     */
    public void addRequest(FileBlockRequestMessage request, NodeConnection connection) {
        try {
            lock.lock();
            try {
                requestQueue.offer(new BlockRequest(request, connection));
                hasRequests.signal(); // Acordar thread processadora
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        System.out.println("[BlockHandler] Thread iniciada");

        while (running) {
            try {
                BlockRequest blockRequest = null;

                // Obter próximo pedido
                lock.lock();
                try {
                    while (requestQueue.isEmpty() && running) {
                        hasRequests.await();
                    }

                    if (!running) {
                        break;
                    }

                    blockRequest = requestQueue.poll();
                } finally {
                    lock.unlock();
                }

                // Processar pedido fora do lock
                if (blockRequest != null) {
                    processRequest(blockRequest);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[BlockHandler] Thread terminada");
    }

    /**
     * Processa um pedido de bloco
     * @param blockRequest Pedido a processar
     */
    private void processRequest(BlockRequest blockRequest) {
        FileBlockRequestMessage request = blockRequest.request;
        NodeConnection connection = blockRequest.connection;

        try {
            System.out.println("[BlockHandler] A processar pedido - Ficheiro: " +
                    request.getFileName() + ", Offset: " + request.getOffset());

            // Ler bloco do ficheiro
            byte[] data = fileManager.readFileBlock(
                    request.getFileName(),
                    request.getOffset(),
                    request.getLength()
            );

            // Criar e enviar resposta
            FileBlockAnswerMessage answer = new FileBlockAnswerMessage(
                    data,
                    request.getOffset()
            );

            connection.sendMessage(answer);

            System.out.println("[BlockHandler] Bloco enviado - " +
                    data.length + " bytes");

        } catch (IOException e) {
            System.err.println("[BlockHandler] Erro ao processar pedido: " +
                    e.getMessage());
            // Poderia enviar uma mensagem de erro ao cliente
        }
    }

    /**
     * Para a thread de processamento
     */
    public void shutdown() {
        try {
            lock.lock();
            try {
                running = false;
                hasRequests.signal(); // Acordar thread para terminar
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Obtém o número de pedidos pendentes
     * @return Número de pedidos na fila
     */
    public int getPendingCount() {
        try {
            lock.lock();
            try {
                return requestQueue.size();
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}