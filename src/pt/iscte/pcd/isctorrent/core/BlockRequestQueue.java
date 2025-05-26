package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.concurrency.MyCondition;
import pt.iscte.pcd.isctorrent.concurrency.MyLock;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;

import java.util.LinkedList;
import java.util.Queue;

// Thread única para processar pedidos de blocos
public class BlockRequestQueue {
    private final Queue<BlockRequest> queue = new LinkedList<>();
    private final MyLock lock = new MyLock();
    private final MyCondition notEmpty = lock.newCondition();
    private volatile boolean running = true;
    private final FileManager fileManager;

    public BlockRequestQueue(FileManager fileManager) {
        this.fileManager = fileManager;
        new Thread(this::processRequests).start();
    }

    public void addRequest(FileBlockRequestMessage request, NodeConnection connection) {
        lock.lock();
        try {
            queue.offer(new BlockRequest(request, connection));
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    private void processRequests() {
        while (running) {
            BlockRequest request = null;
            lock.lock();
            try {
                while (queue.isEmpty() && running) {
                    notEmpty.await();
                }
                if (running) {
                    request = queue.poll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                lock.unlock();
            }

            if (request != null) {
                try {
                    request.connection.handleBlockRequestDirectly(request.request, fileManager);
                } catch (Exception e) {
                    System.err.println("[BlockQueue] Erro ao processar pedido: " + e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            running = false;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private static class BlockRequest {
        final FileBlockRequestMessage request;
        final NodeConnection connection;

        BlockRequest(FileBlockRequestMessage request, NodeConnection connection) {
            this.request = request;
            this.connection = connection;
        }
    }
}