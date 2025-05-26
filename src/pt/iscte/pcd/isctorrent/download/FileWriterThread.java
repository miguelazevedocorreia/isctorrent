package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.gui.dialogs.DownloadResultDialog;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

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
            synchronized(this) {
                while (!downloadComplete) wait();
            }

            byte[] fileData = manager.getFileData(hash);
            if (fileData == null) throw new IOException("File data not found");

            File newFile = new File(workingDirectory, fileName);
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(fileData);

                // Limpar dados do manager após escrita
                manager.removeDownload(hash);

                SwingUtilities.invokeLater(() ->
                        DownloadResultDialog.showResult(
                                SwingUtilities.getWindowAncestor(manager.getTorrent().getGui()),
                                fileName, nodeCounter, elapsedTime));
            }
        } catch (InterruptedException | IOException e) {
            System.err.println("Erro na escrita: " + e.getMessage());
        }
    }

    public synchronized void notifyDownloadComplete(Map<String, Integer> nodeCounter, long elapsedTime) {
        this.nodeCounter = nodeCounter;
        this.elapsedTime = elapsedTime;
        this.downloadComplete = true;
        notify();
    }
}