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
        String fileName = file.fileName();

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

        for (long i = 0; i < totalBlocksCount; i++) {
            long offset = i * Constants.BLOCK_SIZE;
            int length = (int) Math.min(Constants.BLOCK_SIZE, file.fileSize() - offset);
            pendingBlocks.offer(new FileBlockRequestMessage(fileName, offset, length));
        }

        for (NodeConnection connection : sources) {
            downloadExecutor.submit(new DownloadTask(file, connection, this));
        }

        FileWriterThread writer = new FileWriterThread(fileName, file.fileName(), workingDirectory, this);
        writers.put(fileName, writer);
        new Thread(writer).start();
    }

    public synchronized FileBlockRequestMessage getNextBlock(String fileName) throws InterruptedException {
        while (pendingBlocks.isEmpty() && !isDownloadComplete(fileName)) {
            wait();
        }
        return pendingBlocks.poll();
    }

    public synchronized void saveBlock(String fileName, FileBlockAnswerMessage answer, NodeConnection connection) {
        String nodeKey = connection.getRemoteAddress() + ":" + connection.getRemotePort();
        Map<String, Integer> nodeCounter = blocksPerNode.get(fileName);
        if (nodeCounter != null) {
            nodeCounter.put(nodeKey, nodeCounter.getOrDefault(nodeKey, 0) + 1);
        }

        byte[] file = fileData.get(fileName);
        if (file != null) {
            System.arraycopy(answer.data(), 0, file, (int) answer.offset(), answer.data().length);
            int received = receivedBlocks.get(fileName) + 1;
            receivedBlocks.put(fileName, received);

            if (isDownloadComplete(fileName)) {
                long elapsedTime = System.currentTimeMillis() - downloadStartTime.getOrDefault(fileName, 0L);
                FileWriterThread writer = writers.get(fileName);
                if (writer != null) {
                    writer.notifyDownloadComplete(nodeCounter, elapsedTime);
                }
            }
        }
        notifyAll();
    }

    public synchronized void shutdown() {
        downloadExecutor.shutdownNow();
        pendingBlocks.clear();
        notifyAll();
        fileData.clear();
        writers.clear();
        receivedBlocks.clear();
        totalBlocks.clear();
        blocksPerNode.clear();
        downloadStartTime.clear();
    }

    public synchronized boolean isDownloadComplete(String fileName) {
        return receivedBlocks.getOrDefault(fileName, 0) >= totalBlocks.getOrDefault(fileName, 0);
    }

    public byte[] getFileData(String fileName) {
        return fileData.get(fileName);
    }

    public synchronized void requeueBlock(FileBlockRequestMessage block) {
        pendingBlocks.offer(block);
        notifyAll();
    }

    public IscTorrent getTorrent() {
        return torrent;
    }
}