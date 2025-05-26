package protocol;

import java.io.Serial;
import java.io.Serializable;

public record FileSearchResult(String hash, String fileName, long fileSize, String nodeAddress, int nodePort,
                               String workingDirectory) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return String.format("%s (%d bytes) - Porta: %d",
                fileName,
                fileSize,
                nodePort);
    }
}