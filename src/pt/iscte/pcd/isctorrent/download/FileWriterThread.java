package pt.iscte.pcd.isctorrent.download;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileWriterThread implements Runnable {
    private final String hash;
    private final String fileName;
    private final String workingDirectory;
    private final DownloadTasksManager manager;
    private volatile boolean downloadComplete = false;

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
                while (!downloadComplete) {
                    wait();
                }
            }

            byte[] fileData = manager.getFileData(hash);
            if (fileData == null) {
                throw new IOException("File data not found");
            }

            File newFile = new File(workingDirectory, fileName);
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(fileData);
                System.out.println("[Write] File saved successfully: " + newFile.getAbsolutePath());
            }

        } catch (InterruptedException | IOException e) {
            System.err.println("[Error] " + e.getMessage());
        }
    }

    public synchronized void notifyDownloadComplete() {
        downloadComplete = true;
        notify();
    }
}