package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

/**
 * Resultado de uma pesquisa de ficheiro
 * Contém informações sobre o ficheiro e o nó que o possui
 */
public class FileSearchResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final WordSearchMessage searchMessage;
    private final String fileName;
    private final long fileSize;
    private final String nodeAddress;
    private final int nodePort;

    /**
     * Construtor do resultado de pesquisa
     * @param searchMessage Mensagem de pesquisa original
     * @param fileName Nome do ficheiro
     * @param fileSize Tamanho do ficheiro em bytes
     * @param nodeAddress Endereço do nó que possui o ficheiro
     * @param nodePort Porta do nó que possui o ficheiro
     */
    public FileSearchResult(WordSearchMessage searchMessage, String fileName,
                            long fileSize, String nodeAddress, int nodePort) {
        this.searchMessage = searchMessage;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.nodeAddress = nodeAddress;
        this.nodePort = nodePort;
    }

    // Getters
    public WordSearchMessage getSearchMessage() { return searchMessage; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getNodeAddress() { return nodeAddress; }
    public int getNodePort() { return nodePort; }

    @Override
    public String toString() {
        return String.format("%s (%d bytes) - %s:%d",
                fileName,
                fileSize,
                nodeAddress,
                nodePort);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FileSearchResult other)) return false;
        return fileName.equals(other.fileName) &&
                nodeAddress.equals(other.nodeAddress) &&
                nodePort == other.nodePort;
    }

    @Override
    public int hashCode() {
        return fileName.hashCode() ^ nodeAddress.hashCode() ^ nodePort;
    }
}