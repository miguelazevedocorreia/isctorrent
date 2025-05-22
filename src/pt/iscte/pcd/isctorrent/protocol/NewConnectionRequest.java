package pt.iscte.pcd.isctorrent.protocol;

import java.io.Serial;
import java.io.Serializable;

/**
 * Mensagem de pedido de nova conexão
 * Enviada quando um nó se conecta a outro para estabelecer comunicação bidirecional
 */
public class NewConnectionRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String address;
    private final int port;
    private final long timestamp;

    /**
     * Construtor do pedido de conexão
     * @param address Endereço do nó que está a pedir conexão
     * @param port Porta de escuta do nó que está a pedir conexão
     */
    public NewConnectionRequest(String address, int port) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Endereço não pode ser nulo ou vazio");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Porta deve estar entre 1 e 65535");
        }

        this.address = address;
        this.port = port;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Obtém o endereço do nó
     * @return Endereço IP ou hostname
     */
    public String address() {
        return address;
    }

    /**
     * Obtém a porta de escuta do nó
     * @return Número da porta
     */
    public int port() {
        return port;
    }

    /**
     * Obtém o timestamp da criação do pedido
     * @return Timestamp em milissegundos
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Verifica se o pedido ainda é válido (não expirou)
     * @param timeoutMillis Timeout em milissegundos
     * @return true se ainda válido, false se expirou
     */
    public boolean isValid(long timeoutMillis) {
        return (System.currentTimeMillis() - timestamp) < timeoutMillis;
    }

    @Override
    public String toString() {
        return String.format("NewConnectionRequest[address=%s, port=%d, age=%dms]",
                address, port, System.currentTimeMillis() - timestamp);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NewConnectionRequest other)) return false;
        return port == other.port && address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode() ^ port;
    }
}