package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

// mensagem de pesquisa por palavra-chave
public record WordSearchMessage(String keyword, String sourceAddress, int sourcePort) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}