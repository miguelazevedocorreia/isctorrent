package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.core.Constants;
import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.sync.MyCondition;
import pt.iscte.pcd.isctorrent.sync.MyLock;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de tarefas de download
 * Coordena o download de ficheiros entre múltiplas threads
 * Usa locks explícitos e variáveis condicionais próprias
 */
public class DownloadTasksManager {
    // Lock principal para sincronização
    private final MyLock lock = new MyLock();
    private final MyCondition blocksAvailable = new MyCondition(lock);
    private final MyCondition downloadComplete = new MyCondition(lock);

    // Estruturas de dados
    private final Map<String, Queue<FileBlockRequestMessage>> pendingBlocks = new HashMap<>();
    private final Map<String, byte[]> fileData = new ConcurrentHashMap<>();
    private final Map<String, FileWriterThread> writers = new HashMap<>();
    private final Map<String, Integer> receivedBlocks = new HashMap<>();
    private final Map<String, Integer> totalBlocks = new HashMap<>();

    // Rastreamento de blocos por nó
    private final Map<String, Map<String, Integer>> blocksPerNode = new HashMap<>();
    private final Map<String, Long> downloadStartTime = new HashMap<>();

    // Lista de threads de download ativas
    private final List<Thread> downloadThreads = new ArrayList<>();

    private final IscTorrent torrent;
    private volatile boolean shuttingDown = false;

    /**
     * Construtor do gestor
     * @param torrent Instância principal do IscTorrent
     */
    public DownloadTasksManager(IscTorrent torrent) {
        this.torrent = torrent;
    }

    /**
     * Inicia o download de um ficheiro
     * @param file Informação do ficheiro a descarregar
     * @param sources Conexões disponíveis para download
     * @param workingDirectory Diretório de trabalho
     */
    public void startDownload(FileSearchResult file, List<NodeConnection> sources,
                              String workingDirectory) throws InterruptedException {
        String fileName = file.getFileName();
        System.out.println("[Download] A iniciar download de: " + fileName);

        lock.lock();
        try {
            // Inicializar estruturas
            downloadStartTime.put(fileName, System.currentTimeMillis());
            fileData.put(fileName, new byte[(int) file.getFileSize()]);

            // Calcular blocos
            int totalBlocksCount = (int)((file.getFileSize() + Constants.BLOCK_SIZE - 1)
                    / Constants.BLOCK_SIZE);
            totalBlocks.put(fileName, totalBlocksCount);
            receivedBlocks.put(fileName, 0);

            // Criar fila de blocos
            Queue<FileBlockRequestMessage> blocks = new LinkedList<>();
            for (long i = 0; i < totalBlocksCount; i++) {
                long offset = i * Constants.BLOCK_SIZE;
                int length = (int) Math.min(Constants.BLOCK_SIZE,
                        file.getFileSize() - offset);
                blocks.offer(new FileBlockRequestMessage(fileName, offset, length));
            }
            pendingBlocks.put(fileName, blocks);

            // Inicializar contador de blocos por nó
            Map<String, Integer> nodeCounter = new HashMap<>();
            for (NodeConnection conn : sources) {
                String nodeKey = conn.getRemoteAddress() + ":" + conn.getRemotePort();
                nodeCounter.put(nodeKey, 0);
            }
            blocksPerNode.put(fileName, nodeCounter);

            // Criar thread de escrita
            FileWriterThread writer = new FileWriterThread(fileName, fileName,
                    workingDirectory, this);
            writers.put(fileName, writer);
            Thread writerThread = new Thread(writer);
            writerThread.start();

            // Criar threads de download
            for (NodeConnection connection : sources) {
                Thread downloadThread = new Thread(
                        new DownloadTask(file, connection, this)
                );
                downloadThreads.add(downloadThread);
                downloadThread.start();
            }

            // Sinalizar que há blocos disponíveis
            blocksAvailable.signalAll();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Obtém o próximo bloco a descarregar
     * @param fileName Nome do ficheiro
     * @return Próximo bloco ou null se não houver mais
     */
    public FileBlockRequestMessage getNextBlock(String fileName)
            throws InterruptedException {
        lock.lock();
        try {
            Queue<FileBlockRequestMessage> blocks = pendingBlocks.get(fileName);

            // Esperar se não há blocos e download não está completo
            while (blocks != null && blocks.isEmpty() &&
                    !isDownloadComplete(fileName) && !shuttingDown) {
                blocksAvailable.await();
            }

            if (shuttingDown || blocks == null) {
                return null;
            }

            return blocks.poll();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Guarda um bloco recebido
     * @param fileName Nome do ficheiro
     * @param answer Resposta com os dados do bloco
     * @param connection Conexão que forneceu o bloco
     */
    public void saveBlock(String fileName, FileBlockAnswerMessage answer,
                          NodeConnection connection) throws InterruptedException {
        lock.lock();
        try {
            // Atualizar contador de blocos por nó
            String nodeKey = connection.getRemoteAddress() + ":" +
                    connection.getRemotePort();
            Map<String, Integer> nodeCounter = blocksPerNode.get(fileName);
            if (nodeCounter != null) {
                nodeCounter.put(nodeKey, nodeCounter.getOrDefault(nodeKey, 0) + 1);
            }

            // Copiar dados para o array do ficheiro
            byte[] file = fileData.get(fileName);
            if (file != null) {
                System.arraycopy(answer.data(), 0, file,
                        (int) answer.offset(), answer.data().length);

                // Incrementar contador de blocos recebidos
                int received = receivedBlocks.get(fileName) + 1;
                receivedBlocks.put(fileName, received);

                System.out.println("[Download] Bloco recebido: " + received + "/" +
                        totalBlocks.get(fileName));

                // Verificar se download está completo
                if (received >= totalBlocks.get(fileName)) {
                    long elapsedTime = System.currentTimeMillis() -
                            downloadStartTime.get(fileName);

                    FileWriterThread writer = writers.get(fileName);
                    if (writer != null) {
                        writer.notifyDownloadComplete(nodeCounter, elapsedTime);
                    }

                    // Sinalizar conclusão
                    downloadComplete.signalAll();
                }
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Recoloca um bloco na fila (em caso de erro)
     * @param block Bloco a recolocar
     */
    public void requeueBlock(FileBlockRequestMessage block) throws InterruptedException {
        lock.lock();
        try {
            Queue<FileBlockRequestMessage> blocks = pendingBlocks.get(block.getFileName());
            if (blocks != null) {
                blocks.offer(block);
                blocksAvailable.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Verifica se o download está completo
     * @param fileName Nome do ficheiro
     * @return true se completo, false caso contrário
     */
    public boolean isDownloadComplete(String fileName) throws InterruptedException {
        lock.lock();
        try {
            return receivedBlocks.getOrDefault(fileName, 0) >=
                    totalBlocks.getOrDefault(fileName, Integer.MAX_VALUE);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Obtém os dados completos do ficheiro
     * @param fileName Nome do ficheiro
     * @return Array de bytes com o ficheiro completo
     */
    public byte[] getFileData(String fileName) {
        return fileData.get(fileName);
    }

    /**
     * Encerra o gestor de downloads
     */
    public void shutdown() {
        System.out.println("[Manager] A encerrar gestor de downloads");

        try {
            lock.lock();
            try {
                shuttingDown = true;

                // Acordar todas as threads em espera
                blocksAvailable.signalAll();
                downloadComplete.signalAll();

            } finally {
                lock.unlock();
            }

            // Interromper todas as threads de download
            for (Thread thread : downloadThreads) {
                thread.interrupt();
            }

            // Limpar estruturas
            pendingBlocks.clear();
            fileData.clear();
            writers.clear();
            receivedBlocks.clear();
            totalBlocks.clear();
            blocksPerNode.clear();
            downloadStartTime.clear();
            downloadThreads.clear();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Obtém a instância do IscTorrent
     * @return Instância principal
     */
    public IscTorrent getTorrent() {
        return torrent;
    }
}