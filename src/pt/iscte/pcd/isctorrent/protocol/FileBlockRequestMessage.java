package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

// pedido de bloco de ficheiro
public record FileBlockRequestMessage(String fileName, long offset, int length) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}