package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Mensagem de resposta com dados de um bloco de ficheiro
 * Enviada em resposta a um FileBlockRequestMessage
 */
public class FileBlockAnswerMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final byte[] data;
    private final long offset;
    private final int actualLength;
    private final boolean isLastBlock;

    /**
     * Construtor da resposta com bloco
     * @param data Dados do bloco
     * @param offset Posição do bloco no ficheiro
     */
    public FileBlockAnswerMessage(byte[] data, long offset) {
        this(data, offset, false);
    }

    /**
     * Construtor completo da resposta
     * @param data Dados do bloco
     * @param offset Posição do bloco no ficheiro
     * @param isLastBlock Indica se é o último bloco do ficheiro
     */
    public FileBlockAnswerMessage(byte[] data, long offset, boolean isLastBlock) {
        if (data == null) {
            throw new IllegalArgumentException("Dados não podem ser nulos");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset não pode ser negativo");
        }

        // Clonar dados para garantir imutabilidade
        this.data = data.clone();
        this.offset = offset;
        this.actualLength = data.length;
        this.isLastBlock = isLastBlock;
    }

    /**
     * Obtém os dados do bloco
     * @return Cópia dos dados
     */
    public byte[] data() {
        return data.clone();
    }

    /**
     * Obtém o offset do bloco
     * @return Posição no ficheiro
     */
    public long offset() {
        return offset;
    }

    /**
     * Obtém o tamanho real dos dados
     * @return Número de bytes válidos
     */
    public int actualLength() {
        return actualLength;
    }

    /**
     * Verifica se é o último bloco
     * @return true se for o último bloco
     */
    public boolean isLastBlock() {
        return isLastBlock;
    }

    /**
     * Calcula o checksum dos dados para verificação
     * @return Checksum simples dos dados
     */
    public long calculateChecksum() {
        long checksum = 0;
        for (byte b : data) {
            checksum = ((checksum << 1) | (checksum >>> 63)) ^ b;
        }
        return checksum;
    }

    @Override
    public String toString() {
        return String.format("FileBlockAnswerMessage[offset=%d, length=%d, last=%b]",
                offset, actualLength, isLastBlock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FileBlockAnswerMessage other)) return false;
        return offset == other.offset &&
                actualLength == other.actualLength &&
                isLastBlock == other.isLastBlock &&
                Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(offset) ^ actualLength ^ Arrays.hashCode(data);
    }
}