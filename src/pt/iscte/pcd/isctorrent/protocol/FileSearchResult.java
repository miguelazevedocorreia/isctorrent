package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

// resultado de pesquisa
public record FileSearchResult(String fileName, long fileSize, String nodeAddress, int nodePort,
                               String workingDirectory) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return String.format("%s (%d bytes)", // formato básico para debug
                fileName,
                fileSize);
    }
}