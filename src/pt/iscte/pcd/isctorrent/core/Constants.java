package pt.iscte.pcd.isctorrent.core;

/**
 * Constantes globais do sistema IscTorrent
 */
public class Constants {

    /**
     * Tamanho do bloco para transferência de ficheiros
     * Valor sugerido no enunciado: 10240 bytes (10 KB)
     */
    public static final int BLOCK_SIZE = 10240;

    /**
     * Endereço local padrão
     * Usado quando não é possível determinar o endereço real
     */
    public static final String LOCAL_ADDRESS = "127.0.0.1";

    /**
     * Número máximo de downloads simultâneos
     * Limita o uso de recursos do sistema
     */
    public static final int MAX_CONCURRENT_DOWNLOADS = 5;

    /**
     * Timeout para operações de rede (em milissegundos)
     */
    public static final int NETWORK_TIMEOUT = 10000; // 10 segundos

    /**
     * Timeout para aguardar respostas de pesquisa (em milissegundos)
     */
    public static final int SEARCH_TIMEOUT = 5000; // 5 segundos

    /**
     * Tamanho do buffer para leitura de ficheiros
     */
    public static final int FILE_BUFFER_SIZE = 8192; // 8 KB

    /**
     * Versão do protocolo
     * Pode ser usado para compatibilidade futura
     */
    public static final int PROTOCOL_VERSION = 1;

    // Construtor privado para evitar instanciação
    private Constants() {
        throw new AssertionError("Constants não deve ser instanciada");
    }
}