package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.core.Constants;
import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.sync.*;

import java.util.*;

public class DownloadTasksManager {
    private static class DownloadContext {
        final Queue<FileBlockRequestMessage> pendingBlocks = new LinkedList<>();
        final byte[] fileData;
        final Map<String, Integer> blocksPerNode = new HashMap<>();
        final long startTime = System.currentTimeMillis();
        int receivedBlocks = 0;
        final int totalBlocks;
        FileWriterThread writer;

        public DownloadContext(FileSearchResult file) {
            this.fileData = new byte[(int) file.fileSize()];
            this.totalBlocks = (int)((file.fileSize() + Constants.BLOCK_SIZE - 1) / Constants.BLOCK_SIZE);
        }

        public boolean isComplete() {
            return receivedBlocks >= totalBlocks;
        }
    }

    private final MyThreadPool threadPool;
    private final Map<String, DownloadContext> activeDownloads;
    private final IscTorrent torrent;
    private final MyLock lock;
    private final MyCondition condition;

    public DownloadTasksManager(IscTorrent torrent) {
        this.torrent = torrent;
        this.threadPool = new MyThreadPool(Constants.MAX_CONCURRENT_DOWNLOADS);
        this.activeDownloads = new HashMap<>();
        this.lock = new MyLock();
        this.condition = new MyCondition(lock);
    }

    public void startDownload(FileSearchResult file, List<NodeConnection> sources, String workingDirectory) {
        try {
            lock.lock();

            String fileName = file.fileName();
            if (activeDownloads.containsKey(fileName)) return;

            DownloadContext context = new DownloadContext(file);
            activeDownloads.put(fileName, context);

            for (NodeConnection conn : sources) {
                String nodeKey = conn.getRemoteAddress() + ":" + conn.getRemotePort();
                context.blocksPerNode.put(nodeKey, 0);
            }

            for (long i = 0; i < context.totalBlocks; i++) {
                long offset = i * Constants.BLOCK_SIZE;
                int length = (int) Math.min(Constants.BLOCK_SIZE, file.fileSize() - offset);
                context.pendingBlocks.offer(new FileBlockRequestMessage(fileName, offset, length));
            }

            for (NodeConnection connection : sources) {
                threadPool.submit(new DownloadTask(file, connection, this));
            }

            FileWriterThread writer = new FileWriterThread(fileName, file.fileName(), workingDirectory, this);
            context.writer = writer;
            new Thread(writer).start();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public FileBlockRequestMessage getNextBlock(String fileName) throws InterruptedException {
        lock.lock();
        try {
            DownloadContext context = activeDownloads.get(fileName);
            if (context == null) return null;

            while (context.pendingBlocks.isEmpty() && !context.isComplete()) {
                condition.await();
            }

            return context.pendingBlocks.poll();
        } finally {
            lock.unlock();
        }
    }

    public void saveBlock(String fileName, FileBlockAnswerMessage answer, NodeConnection connection) {
        try {
            lock.lock();

            DownloadContext context = activeDownloads.get(fileName);
            if (context == null) return;

            String nodeKey = connection.getRemoteAddress() + ":" + connection.getRemotePort();
            context.blocksPerNode.put(nodeKey, context.blocksPerNode.getOrDefault(nodeKey, 0) + 1);

            System.arraycopy(answer.data(), 0, context.fileData, (int) answer.offset(), answer.data().length);
            context.receivedBlocks++;

            if (context.isComplete()) {
                long elapsedTime = System.currentTimeMillis() - context.startTime;
                if (context.writer != null) {
                    context.writer.notifyDownloadComplete(context.blocksPerNode, elapsedTime);
                }
            }

            condition.signalAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public void requeueBlock(FileBlockRequestMessage block) {
        try {
            lock.lock();

            DownloadContext context = activeDownloads.get(block.fileName());
            if (context != null) {
                context.pendingBlocks.offer(block);
                condition.signalAll();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public boolean isDownloadComplete(String fileName) {
        try {
            lock.lock();
            DownloadContext context = activeDownloads.get(fileName);
            return context != null && context.isComplete();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }

    public byte[] getFileData(String fileName) {
        try {
            lock.lock();
            DownloadContext context = activeDownloads.get(fileName);
            return context != null ? context.fileData : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    public void removeDownload(String fileName) {
        try {
            lock.lock();
            activeDownloads.remove(fileName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        threadPool.shutdown();
        try {
            lock.lock();
            activeDownloads.clear();
            condition.signalAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public IscTorrent getTorrent() {
        return torrent;
    }
}