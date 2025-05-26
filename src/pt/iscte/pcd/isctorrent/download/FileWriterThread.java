package pt.iscte.pcd.isctorrent.download;

import pt.iscte.pcd.isctorrent.concurrency.MyCondition;
import pt.iscte.pcd.isctorrent.concurrency.MyLock;
import pt.iscte.pcd.isctorrent.gui.dialogs.DownloadResultDialog;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public class FileWriterThread implements Runnable {
    private final String fileName;
    private final String workingDirectory;
    private final DownloadTasksManager manager;
    private volatile boolean downloadComplete = false;

    private final MyLock lock = new MyLock();
    private final MyCondition downloadCompleted = lock.newCondition();

    private Map<String, Integer> nodeCounter;
    private long elapsedTime;

    public FileWriterThread(String fileName, String workingDirectory, DownloadTasksManager manager) {
        this.fileName = fileName;
        this.workingDirectory = workingDirectory;
        this.manager = manager;
    }

    @Override
    public void run() {
        try {
            System.out.println("[Writer] A aguardar conclusão do download para: " + fileName);
            lock.lock();
            try {
                while (!downloadComplete) {
                    downloadCompleted.await();
                }
            } finally {
                lock.unlock();
            }

            System.out.println("[Writer] Download concluído, a escrever ficheiro: " + fileName);
            byte[] fileData = manager.getFileData(fileName);
            if (fileData == null) {
                throw new IOException("File data not found");
            }

            File newFile = new File(workingDirectory, fileName);
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(fileData);
                System.out.println("[Write] File saved successfully: " + newFile.getAbsolutePath());

                SwingUtilities.invokeLater(() ->
                        DownloadResultDialog.showResult(
                                SwingUtilities.getWindowAncestor(manager.getTorrent().getGui()),
                                fileName,
                                nodeCounter,
                                elapsedTime
                        )
                );
            }

        } catch (InterruptedException | IOException e) {
            System.err.println("[Writer Error] " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void notifyDownloadComplete(Map<String, Integer> nodeCounter, long elapsedTime) {
        System.out.println("[DEBUG] FileWriterThread.notifyDownloadComplete() chamado para: " + fileName);
        lock.lock();
        try {
            this.nodeCounter = nodeCounter;
            this.elapsedTime = elapsedTime;
            this.downloadComplete = true;
            downloadCompleted.signal();
            System.out.println("[DEBUG] Signal enviado para FileWriterThread");
        } finally {
            lock.unlock();
        }
    }
}