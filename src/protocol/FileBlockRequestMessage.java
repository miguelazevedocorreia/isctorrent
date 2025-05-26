package protocol;

import java.io.Serial;
import java.io.Serializable;

public record FileBlockRequestMessage(String hash, long offset, int length) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}