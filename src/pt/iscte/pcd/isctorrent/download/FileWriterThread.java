package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.gui.dialogs.DownloadResultDialog;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

// thread dedicada à escrita em disco quando download completo
public class FileWriterThread implements Runnable {
    private final String hash;
    private final String fileName;
    private final String workingDirectory;
    private final DownloadTasksManager manager;
    private volatile boolean downloadComplete = false;
    private Map<String, Integer> nodeCounter;
    private long elapsedTime;

    public FileWriterThread(String hash, String fileName, String workingDirectory, DownloadTasksManager manager) {
        this.hash = hash;
        this.fileName = fileName;
        this.workingDirectory = workingDirectory;
        this.manager = manager;
    }

    @Override
    public void run() {
        try {
            // espera até download estar completo usando wait/notify
            synchronized(this) {
                while (!downloadComplete) {
                    wait(); // bloqueia até ser notificada
                }
            }

            byte[] fileData = manager.getFileData(hash);
            if (fileData == null) throw new IOException("File data not found");

            // escreve ficheiro completo em disco
            File newFile = new File(workingDirectory, fileName);
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(fileData);

                manager.removeDownload(hash); // limpa dados após escrita

                // mostra resultado
                SwingUtilities.invokeLater(() ->
                        DownloadResultDialog.showResult(
                                SwingUtilities.getWindowAncestor(manager.getTorrent().getGui()),
                                fileName, nodeCounter, elapsedTime));
            }
        } catch (InterruptedException | IOException e) {
            System.err.println("Erro na escrita: " + e.getMessage());
        }
    }

    // notificação de download completo
    public synchronized void notifyDownloadComplete(Map<String, Integer> nodeCounter, long elapsedTime) {
        this.nodeCounter = nodeCounter;
        this.elapsedTime = elapsedTime;
        this.downloadComplete = true;
        notify(); // acorda thread de escrita
    }
}