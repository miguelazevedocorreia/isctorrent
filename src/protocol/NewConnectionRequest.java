package protocol;

import java.io.Serial;
import java.io.Serializable;

public record NewConnectionRequest(String address, int port) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}