package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

public record FileBlockAnswerMessage(byte[] data, long offset) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public FileBlockAnswerMessage(byte[] data, long offset) {
        this.data = data.clone();
        this.offset = offset;
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}