package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

/**
 * Mensagem de pedido de bloco de ficheiro
 * Usa o nome do ficheiro como identificador (conforme enunciado)
 */
public class FileBlockRequestMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final long offset;
    private final int length;

    /**
     * Construtor do pedido de bloco
     * @param fileName Nome do ficheiro
     * @param offset Posição inicial do bloco
     * @param length Tamanho do bloco em bytes
     */
    public FileBlockRequestMessage(String fileName, long offset, int length) {
        this.fileName = fileName;
        this.offset = offset;
        this.length = length;
    }

    // Getters
    public String getFileName() { return fileName; }
    public long getOffset() { return offset; }
    public int getLength() { return length; }
}