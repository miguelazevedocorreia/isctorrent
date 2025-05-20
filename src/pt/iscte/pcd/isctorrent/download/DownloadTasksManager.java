package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.core.Constants;
import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadTasksManager {

    private final ExecutorService downloadExecutor;
    private final Queue<FileBlockRequestMessage> pendingBlocks;
    private final Map<String, byte[]> fileData;
    private final Map<String, FileWriterThread> writers;
    private final Map<String, Integer> receivedBlocks;
    private final Map<String, Integer> totalBlocks;

    // Rastreamento de blocos por nó e tempo
    private final Map<String, Map<String, Integer>> blocksPerNode;
    private final Map<String, Long> downloadStartTime;

    private final IscTorrent torrent;

    public DownloadTasksManager(IscTorrent torrent) {
        this.torrent = torrent;
        this.downloadExecutor = Executors.newFixedThreadPool(Constants.MAX_CONCURRENT_DOWNLOADS);
        this.pendingBlocks = new LinkedList<>();
        this.fileData = new HashMap<>();
        this.writers = new HashMap<>();
        this.receivedBlocks = new HashMap<>();
        this.totalBlocks = new HashMap<>();
        this.blocksPerNode = new HashMap<>();
        this.downloadStartTime = new HashMap<>();
    }

    public synchronized void startDownload(FileSearchResult file, List<NodeConnection> sources, String workingDirectory) {
        String hash = file.hash();
        System.out.println("[Transfer] Starting transfer for: " + file.fileName());

        // Iniciar contador de tempo
        downloadStartTime.put(hash, System.currentTimeMillis());

        // Inicializar o contador de blocos por nó
        Map<String, Integer> nodeCounter = new HashMap<>();
        for (NodeConnection conn : sources) {
            String nodeKey = conn.getRemoteAddress() + ":" + conn.getRemotePort();
            nodeCounter.put(nodeKey, 0);
        }
        blocksPerNode.put(hash, nodeCounter);

        // Initialize file data array
        fileData.put(hash, new byte[(int) file.fileSize()]);

        // Calculate total blocks
        int totalBlocksCount = (int)((file.fileSize() + Constants.BLOCK_SIZE - 1) / Constants.BLOCK_SIZE);
        totalBlocks.put(hash, totalBlocksCount);
        receivedBlocks.put(hash, 0);

        // Create block requests
        for (long i = 0; i < totalBlocksCount; i++) {
            long offset = i * Constants.BLOCK_SIZE;
            int length = (int) Math.min(Constants.BLOCK_SIZE, file.fileSize() - offset);
            pendingBlocks.offer(new FileBlockRequestMessage(hash, offset, length));
        }

        // Submit download tasks to the thread pool
        for (NodeConnection connection : sources) {
            downloadExecutor.submit(new DownloadTask(file, connection, this));
        }

        // Start writer thread (keeping this in a separate thread as it's not part of the download pool)
        FileWriterThread writer = new FileWriterThread(hash, file.fileName(), workingDirectory, this);
        writers.put(hash, writer);
        new Thread(writer).start();
    }

    public synchronized FileBlockRequestMessage getNextBlock(String hash) throws InterruptedException {
        while (pendingBlocks.isEmpty() && !isDownloadComplete(hash)) {
            wait();
        }
        return pendingBlocks.poll();
    }

    public synchronized void saveBlock(String hash, FileBlockAnswerMessage answer, NodeConnection connection) {
        // Contabilizar o bloco recebido para este nó
        String nodeKey = connection.getRemoteAddress() + ":" + connection.getRemotePort();
        Map<String, Integer> nodeCounter = blocksPerNode.get(hash);
        if (nodeCounter != null) {
            nodeCounter.put(nodeKey, nodeCounter.getOrDefault(nodeKey, 0) + 1);
        }

        byte[] file = fileData.get(hash);
        if (file != null) {
            System.arraycopy(answer.data(), 0, file, (int) answer.offset(), answer.data().length);
            int received = receivedBlocks.get(hash) + 1;
            receivedBlocks.put(hash, received);

            if (isDownloadComplete(hash)) {
                // Calcular tempo decorrido
                long elapsedTime = System.currentTimeMillis() - downloadStartTime.getOrDefault(hash, 0L);

                FileWriterThread writer = writers.get(hash);
                if (writer != null) {
                    writer.notifyDownloadComplete(nodeCounter, elapsedTime);
                }
            }
        }
        notifyAll();
    }

    public synchronized void shutdown() {
        System.out.println("[Manager] Shutting down download manager");
        downloadExecutor.shutdownNow();

        // Clear all pending blocks
        pendingBlocks.clear();

        // Wake up any waiting threads so they can terminate
        notifyAll();

        // Clear all data structures
        fileData.clear();
        writers.clear();
        receivedBlocks.clear();
        totalBlocks.clear();
        blocksPerNode.clear();
        downloadStartTime.clear();
    }

    public synchronized boolean isDownloadComplete(String hash) {
        return receivedBlocks.getOrDefault(hash, 0) >= totalBlocks.getOrDefault(hash, 0);
    }

    public byte[] getFileData(String hash) {
        return fileData.get(hash);
    }

    public synchronized void requeueBlock(FileBlockRequestMessage block) {
        pendingBlocks.offer(block);
        notifyAll();
    }

    public IscTorrent getTorrent() {
        return torrent;
    }
}