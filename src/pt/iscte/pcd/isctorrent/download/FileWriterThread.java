package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.gui.dialogs.DownloadResultDialog;
import pt.iscte.pcd.isctorrent.sync.MyCondition;
import pt.iscte.pcd.isctorrent.sync.MyLock;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Thread dedicada para escrita de ficheiros em disco
 * Espera até o download estar completo antes de escrever
 */
public class FileWriterThread implements Runnable {
    private final String fileName;
    private final String workingDirectory;
    private final DownloadTasksManager manager;

    // Sincronização própria
    private final MyLock lock = new MyLock();
    private final MyCondition downloadCompleted = new MyCondition(lock);

    private volatile boolean downloadComplete = false;
    private Map<String, Integer> nodeCounter;
    private long elapsedTime;

    /**
     * Construtor da thread de escrita
     * @param fileName Nome do ficheiro (usado como identificador)
     * @param displayName Nome para exibir
     * @param workingDirectory Diretório de trabalho
     * @param manager Gestor de downloads
     */
    public FileWriterThread(String fileName, String displayName,
                            String workingDirectory, DownloadTasksManager manager) {
        this.fileName = fileName;
        this.workingDirectory = workingDirectory;
        this.manager = manager;
    }

    @Override
    public void run() {
        System.out.println("[Escrita] Thread iniciada para " + fileName);

        try {
            // Aguardar conclusão do download
            lock.lock();
            try {
                while (!downloadComplete) {
                    downloadCompleted.await();
                }
            } finally {
                lock.unlock();
            }

            // Obter dados do ficheiro
            byte[] fileData = manager.getFileData(fileName);
            if (fileData == null) {
                throw new IOException("Dados do ficheiro não encontrados");
            }

            // Criar ficheiro no disco
            File outputFile = new File(workingDirectory, fileName);

            // Verificar se já existe
            if (outputFile.exists()) {
                System.out.println("[Escrita] Ficheiro já existe, a substituir: " +
                        outputFile.getName());
            }

            // Escrever dados
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileData);
                fos.flush();

                System.out.println("[Escrita] Ficheiro guardado com sucesso: " +
                        outputFile.getAbsolutePath() +
                        " (" + fileData.length + " bytes)");
            }

            // Exibir diálogo de resultado
            SwingUtilities.invokeLater(() -> {
                JFrame mainWindow = (JFrame) SwingUtilities.getWindowAncestor(
                        manager.getTorrent().getGui()
                );

                DownloadResultDialog.showResult(
                        mainWindow,
                        fileName,
                        nodeCounter,
                        elapsedTime
                );
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Escrita] Thread interrompida");
        } catch (IOException e) {
            System.err.println("[Escrita] Erro ao escrever ficheiro: " + e.getMessage());

            // Notificar utilizador do erro
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "Erro ao guardar ficheiro: " + e.getMessage(),
                            "Erro de Escrita",
                            JOptionPane.ERROR_MESSAGE)
            );
        }

        System.out.println("[Escrita] Thread terminada para " + fileName);
    }

    /**
     * Notifica que o download está completo
     * @param nodeCounter Contador de blocos por nó
     * @param elapsedTime Tempo decorrido em milissegundos
     */
    public void notifyDownloadComplete(Map<String, Integer> nodeCounter,
                                       long elapsedTime) {
        try {
            lock.lock();
            try {
                this.nodeCounter = nodeCounter;
                this.elapsedTime = elapsedTime;
                this.downloadComplete = true;
                downloadCompleted.signal();

                System.out.println("[Escrita] Notificação de conclusão recebida para " +
                        fileName);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}