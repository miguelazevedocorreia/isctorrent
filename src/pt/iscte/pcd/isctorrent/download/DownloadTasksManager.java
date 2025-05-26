package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.concurrency.MyCondition;
import pt.iscte.pcd.isctorrent.concurrency.MyLock;
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
    // SEPARAR FILAS POR FICHEIRO:
    private final Map<String, Queue<FileBlockRequestMessage>> pendingBlocksPerFile;
    private final Map<String, byte[]> fileData;
    private final Map<String, FileWriterThread> writers;
    private final Map<String, Integer> receivedBlocks;
    private final Map<String, Integer> totalBlocks;

    private final Map<String, Map<String, Integer>> blocksPerNode;
    private final Map<String, Long> downloadStartTime;

    private final IscTorrent torrent;

    private final MyLock lock = new MyLock();
    private final MyCondition blockAvailable = lock.newCondition();

    public DownloadTasksManager(IscTorrent torrent) {
        this.torrent = torrent;
        this.downloadExecutor = Executors.newFixedThreadPool(Constants.MAX_CONCURRENT_DOWNLOADS);
        this.pendingBlocksPerFile = new HashMap<>();
        this.fileData = new HashMap<>();
        this.writers = new HashMap<>();
        this.receivedBlocks = new HashMap<>();
        this.totalBlocks = new HashMap<>();
        this.blocksPerNode = new HashMap<>();
        this.downloadStartTime = new HashMap<>();
    }

    public void startDownload(FileSearchResult file, List<NodeConnection> sources, String workingDirectory) {
        String fileName = file.fileName();
        System.out.println("[Transfer] Starting transfer for: " + fileName);

        lock.lock();
        try {
            downloadStartTime.put(fileName, System.currentTimeMillis());

            Map<String, Integer> nodeCounter = new HashMap<>();
            for (NodeConnection conn : sources) {
                String nodeKey = conn.getRemoteAddress() + ":" + conn.getRemotePort();
                nodeCounter.put(nodeKey, 0);
            }
            blocksPerNode.put(fileName, nodeCounter);

            fileData.put(fileName, new byte[(int) file.fileSize()]);

            int totalBlocksCount = (int)((file.fileSize() + Constants.BLOCK_SIZE - 1) / Constants.BLOCK_SIZE);
            totalBlocks.put(fileName, totalBlocksCount);
            receivedBlocks.put(fileName, 0);

            System.out.println("[DEBUG] Total blocos a descarregar para " + fileName + ": " + totalBlocksCount);

            // CRIAR FILA ESPECÍFICA PARA ESTE FICHEIRO:
            Queue<FileBlockRequestMessage> fileQueue = new LinkedList<>();
            for (long i = 0; i < totalBlocksCount; i++) {
                long offset = i * Constants.BLOCK_SIZE;
                int length = (int) Math.min(Constants.BLOCK_SIZE, file.fileSize() - offset);
                fileQueue.offer(new FileBlockRequestMessage(fileName, offset, length));
            }
            pendingBlocksPerFile.put(fileName, fileQueue);

            blockAvailable.signalAll();
        } finally {
            lock.unlock();
        }

        for (NodeConnection connection : sources) {
            downloadExecutor.submit(new DownloadTask(file, connection, this));
        }

        FileWriterThread writer = new FileWriterThread(fileName, workingDirectory, this);
        writers.put(fileName, writer);
        new Thread(writer).start();
    }

    public FileBlockRequestMessage getNextBlock(String fileName) throws InterruptedException {
        lock.lock();
        try {
            Queue<FileBlockRequestMessage> fileQueue = pendingBlocksPerFile.get(fileName);
            if (fileQueue == null) {
                System.out.println("[DEBUG] Fila não encontrada para " + fileName);
                return null;
            }

            while (fileQueue.isEmpty() && !isDownloadComplete(fileName)) {
                System.out.println("[DEBUG] " + fileName + " - A aguardar blocos... Fila vazia, download completo: " + isDownloadComplete(fileName));
                blockAvailable.await();
            }

            if (isDownloadComplete(fileName)) {
                System.out.println("[DEBUG] " + fileName + " - Download completo, a sair");
                return null;
            }

            FileBlockRequestMessage block = fileQueue.poll();
            if (block != null) {
                System.out.println("[DEBUG] " + fileName + " - A pedir bloco offset: " + block.offset());
            }
            return block;
        } finally {
            lock.unlock();
        }
    }

    public void saveBlock(String fileName, FileBlockAnswerMessage answer, NodeConnection connection) {
        lock.lock();
        try {
            String nodeKey = connection.getRemoteAddress() + ":" + connection.getRemotePort();
            Map<String, Integer> nodeCounter = blocksPerNode.get(fileName);
            if (nodeCounter != null) {
                nodeCounter.put(nodeKey, nodeCounter.getOrDefault(nodeKey, 0) + 1);
            }

            byte[] file = fileData.get(fileName);
            if (file != null) {
                // VERIFICAR LIMITES DO ARRAY:
                int maxBytesToCopy = Math.min(answer.data().length, file.length - (int)answer.offset());
                if (maxBytesToCopy > 0) {
                    System.arraycopy(answer.data(), 0, file, (int) answer.offset(), maxBytesToCopy);
                    System.out.println("[DEBUG] " + fileName + " - Copiados " + maxBytesToCopy + " bytes no offset " + answer.offset());
                } else {
                    System.err.println("[ERRO] " + fileName + " - Tentativa de escrita fora dos limites: offset=" + answer.offset() + ", tamanho=" + file.length);
                }

                int received = receivedBlocks.get(fileName) + 1;
                receivedBlocks.put(fileName, received);

                // LOG DE DEBUG
                System.out.println("[DEBUG] " + fileName + " - Blocos recebidos: " + received + "/" + totalBlocks.get(fileName));

                if (isDownloadComplete(fileName)) {
                    System.out.println("[DEBUG] Download completo para " + fileName + "! A notificar FileWriterThread");
                    long elapsedTime = System.currentTimeMillis() - downloadStartTime.getOrDefault(fileName, 0L);

                    FileWriterThread writer = writers.get(fileName);
                    if (writer != null) {
                        writer.notifyDownloadComplete(nodeCounter, elapsedTime);
                    } else {
                        System.err.println("[ERRO] FileWriterThread não encontrada para " + fileName + "!");
                    }
                }
            }
            blockAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        System.out.println("[Manager] Shutting down download manager");
        downloadExecutor.shutdownNow();

        lock.lock();
        try {
            pendingBlocksPerFile.clear();
            blockAvailable.signalAll();

            fileData.clear();
            writers.clear();
            receivedBlocks.clear();
            totalBlocks.clear();
            blocksPerNode.clear();
            downloadStartTime.clear();
        } finally {
            lock.unlock();
        }
    }

    public boolean isDownloadComplete(String fileName) {
        lock.lock();
        try {
            return receivedBlocks.getOrDefault(fileName, 0) >= totalBlocks.getOrDefault(fileName, 0);
        } finally {
            lock.unlock();
        }
    }

    public byte[] getFileData(String fileName) {
        lock.lock();
        try {
            return fileData.get(fileName);
        } finally {
            lock.unlock();
        }
    }

    public void requeueBlock(FileBlockRequestMessage block) {
        lock.lock();
        try {
            Queue<FileBlockRequestMessage> fileQueue = pendingBlocksPerFile.get(block.fileName());
            if (fileQueue != null) {
                fileQueue.offer(block);
                blockAvailable.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public IscTorrent getTorrent() {
        return torrent;
    }
}