package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

/**
 * Mensagem de pesquisa de ficheiros por palavra-chave
 * Enviada para todos os nós conectados
 */
public class WordSearchMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String keyword;
    private final String sourceAddress;
    private final int sourcePort;

    /**
     * Construtor da mensagem de pesquisa
     * @param keyword Palavra-chave a pesquisar
     * @param sourceAddress Endereço do nó que originou a pesquisa
     * @param sourcePort Porta do nó que originou a pesquisa
     */
    public WordSearchMessage(String keyword, String sourceAddress, int sourcePort) {
        this.keyword = keyword;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
    }

    /**
     * Obtém a palavra-chave
     * @return Palavra-chave da pesquisa
     */
    public String keyword() {
        return keyword;
    }

    /**
     * Obtém o endereço de origem
     * @return Endereço do nó que originou a pesquisa
     */
    public String sourceAddress() {
        return sourceAddress;
    }

    /**
     * Obtém a porta de origem
     * @return Porta do nó que originou a pesquisa
     */
    public int sourcePort() {
        return sourcePort;
    }

    @Override
    public String toString() {
        return String.format("WordSearchMessage[keyword='%s', source=%s:%d]",
                keyword, sourceAddress, sourcePort);
    }
}