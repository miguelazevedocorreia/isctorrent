package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.core.Constants;
import pt.iscte.pcd.isctorrent.core.IscTorrent;
import pt.iscte.pcd.isctorrent.network.NodeConnection;
import pt.iscte.pcd.isctorrent.protocol.FileBlockAnswerMessage;
import pt.iscte.pcd.isctorrent.protocol.FileBlockRequestMessage;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import java.util.*;

// coordena downloads de ficheiros conforme especificado no enunciado
public class DownloadTasksManager {

    // contexto de cada download em curso
    private static class DownloadContext {
        final Queue<FileBlockRequestMessage> pendingBlocks = new LinkedList<>(); // blocos por descarregar
        final byte[] fileData; // dados do ficheiro em memória
        final Map<String, Integer> blocksPerNode = new HashMap<>(); // contador por nó para estatísticas
        final long startTime = System.currentTimeMillis();
        int receivedBlocks = 0;
        final int totalBlocks;
        FileWriterThread writer; // thread dedicada à escrita

        public DownloadContext(FileSearchResult file) {
            this.fileData = new byte[(int) file.fileSize()];
            this.totalBlocks = (int)((file.fileSize() + Constants.BLOCK_SIZE - 1) / Constants.BLOCK_SIZE);
        }

        public boolean isComplete() {
            return receivedBlocks >= totalBlocks;
        }
    }

    private final Map<String, DownloadContext> activeDownloads; // downloads ativos
    private final IscTorrent torrent;

    public DownloadTasksManager(IscTorrent torrent) {
        this.torrent = torrent;
        this.activeDownloads = new HashMap<>();
    }

    // inicia download com uma thread por nó
    public synchronized void startDownload(FileSearchResult file, List<NodeConnection> sources, String workingDirectory) {
        String fileName = file.fileName();
        if (activeDownloads.containsKey(fileName)) return; // já está a descarregar

        DownloadContext context = new DownloadContext(file);
        activeDownloads.put(fileName, context);

        // inicializa contadores por nó
        for (NodeConnection conn : sources) {
            String nodeKey = conn.getRemoteAddress() + ":" + conn.getRemotePort();
            context.blocksPerNode.put(nodeKey, 0);
        }

        // cria lista de todos os blocos a descarregar
        for (long i = 0; i < context.totalBlocks; i++) {
            long offset = i * Constants.BLOCK_SIZE;
            int length = (int) Math.min(Constants.BLOCK_SIZE, file.fileSize() - offset);
            context.pendingBlocks.offer(new FileBlockRequestMessage(fileName, offset, length));
        }

        // lança uma thread por nó conforme especificado
        for (NodeConnection connection : sources) {
            new Thread(new DownloadTask(file, connection, this)).start();
        }

        // thread dedicada para escrita em disco quando completo
        FileWriterThread writer = new FileWriterThread(fileName, file.fileName(), workingDirectory, this);
        context.writer = writer;
        new Thread(writer).start();
    }

    // coordenação: obtém próximo bloco a descarregar
    public synchronized FileBlockRequestMessage getNextBlock(String fileName) {
        DownloadContext context = activeDownloads.get(fileName);
        if (context == null || context.isComplete()) return null;

        return context.pendingBlocks.poll(); // retorna próximo bloco ou null
    }

    // coordenação: guarda bloco recebido e atualiza estatísticas
    public synchronized void saveBlock(String fileName, FileBlockAnswerMessage answer, NodeConnection connection) {
        DownloadContext context = activeDownloads.get(fileName);
        if (context == null) return;

        String nodeKey = connection.getRemoteAddress() + ":" + connection.getRemotePort();
        context.blocksPerNode.put(nodeKey, context.blocksPerNode.getOrDefault(nodeKey, 0) + 1);

        // copia dados do bloco para posição correta
        System.arraycopy(answer.data(), 0, context.fileData, (int) answer.offset(), answer.data().length);
        context.receivedBlocks++;

        // notifica writer se download completo
        if (context.isComplete()) {
            long elapsedTime = System.currentTimeMillis() - context.startTime;
            if (context.writer != null) {
                context.writer.notifyDownloadComplete(context.blocksPerNode, elapsedTime);
            }
        }
    }

    // recoloca bloco na fila se houve erro
    public synchronized void requeueBlock(FileBlockRequestMessage block) {
        DownloadContext context = activeDownloads.get(block.fileName());
        if (context != null) {
            context.pendingBlocks.offer(block);
        }
    }

    public synchronized boolean isDownloadComplete(String fileName) {
        DownloadContext context = activeDownloads.get(fileName);
        return context != null && context.isComplete();
    }

    public synchronized byte[] getFileData(String fileName) {
        DownloadContext context = activeDownloads.get(fileName);
        return context != null ? context.fileData : null;
    }

    public synchronized void removeDownload(String fileName) {
        activeDownloads.remove(fileName);
    }

    public synchronized void shutdown() {
        activeDownloads.clear();
    }

    public IscTorrent getTorrent() {
        return torrent;
    }
}