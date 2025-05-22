package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.protocol.WordSearchMessage;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de ficheiros locais
 * Responsável por ler a pasta de trabalho e fornecer blocos
 */
public class FileManager {
    private final String workingDirectory;
    private final Map<String, File> availableFiles;
    private final int port;

    /**
     * Construtor do gestor de ficheiros
     * @param workingDirectory Diretório de trabalho
     * @param port Porta do nó local
     */
    public FileManager(String workingDirectory, int port) {
        this.workingDirectory = workingDirectory;
        this.availableFiles = new ConcurrentHashMap<>();
        this.port = port;
        loadFiles();
    }

    /**
     * Carrega os ficheiros disponíveis no diretório de trabalho
     */
    private void loadFiles() {
        File directory = new File(workingDirectory);

        // Criar diretório se não existir
        if (!directory.exists()) {
            directory.mkdirs();
            System.out.println("[FileManager] Diretório criado: " + workingDirectory);
            return;
        }

        // Ler ficheiros existentes
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    availableFiles.put(file.getName(), file);
                    System.out.println("[FileManager] Ficheiro disponível: " +
                            file.getName() + " (" + file.length() + " bytes)");
                }
            }
        }

        System.out.println("[FileManager] Total de ficheiros carregados: " +
                availableFiles.size());
    }

    /**
     * Lê um bloco de um ficheiro
     * @param fileName Nome do ficheiro
     * @param offset Posição inicial
     * @param length Tamanho do bloco
     * @return Dados do bloco
     */
    public byte[] readFileBlock(String fileName, long offset, int length)
            throws IOException {
        File file = availableFiles.get(fileName);
        if (file == null) {
            throw new IOException("Ficheiro não encontrado: " + fileName);
        }

        // Validar parâmetros
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("Offset ou length inválidos");
        }

        // Ajustar length se necessário
        long fileSize = file.length();
        if (offset >= fileSize) {
            return new byte[0]; // Offset além do fim do ficheiro
        }

        int actualLength = (int) Math.min(length, fileSize - offset);
        byte[] buffer = new byte[actualLength];

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            int bytesRead = raf.read(buffer);

            // Se leu menos bytes que o esperado
            if (bytesRead < actualLength) {
                byte[] result = new byte[bytesRead];
                System.arraycopy(buffer, 0, result, 0, bytesRead);
                return result;
            }

            return buffer;
        }
    }

    /**
     * Pesquisa ficheiros por palavra-chave
     * @param searchMessage Mensagem de pesquisa
     * @return Lista de resultados
     */
    public List<FileSearchResult> searchFiles(WordSearchMessage searchMessage) {
        List<FileSearchResult> results = new ArrayList<>();
        String keyword = searchMessage.keyword().toLowerCase();
        String localAddress = Constants.LOCAL_ADDRESS;

        for (Map.Entry<String, File> entry : availableFiles.entrySet()) {
            String fileName = entry.getKey();
            File file = entry.getValue();

            // Verificar se o nome contém a palavra-chave
            if (fileName.toLowerCase().contains(keyword)) {
                results.add(new FileSearchResult(
                        searchMessage,  // Incluir mensagem original
                        fileName,
                        file.length(),
                        localAddress,
                        port
                ));
            }
        }

        return results;
    }

    /**
     * Verifica se um ficheiro existe
     * @param fileName Nome do ficheiro
     * @return true se existe, false caso contrário
     */
    public boolean hasFile(String fileName) {
        return availableFiles.containsKey(fileName);
    }

    /**
     * Obtém o tamanho de um ficheiro
     * @param fileName Nome do ficheiro
     * @return Tamanho em bytes ou -1 se não existir
     */
    public long getFileSize(String fileName) {
        File file = availableFiles.get(fileName);
        return file != null ? file.length() : -1;
    }

    /**
     * Recarrega a lista de ficheiros disponíveis
     */
    public void refresh() {
        availableFiles.clear();
        loadFiles();
    }
}