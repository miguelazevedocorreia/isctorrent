package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import java.io.*;
import java.util.*;

public class FileManager {
    private final String workingDirectory;
    private final Map<String, File> availableFiles;
    private final int port;

    public FileManager(String workingDirectory, int port) {
        this.workingDirectory = workingDirectory;
        this.availableFiles = new HashMap<>();
        this.port = port;
        loadFiles();
    }

    private synchronized void loadFiles() {
        File directory = new File(workingDirectory);
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    availableFiles.put(file.getName(), file);
                }
            }
        }
    }

    public synchronized byte[] readFileBlock(String fileName, long offset, int length)
            throws IOException {
        File file = availableFiles.get(fileName);
        if (file == null) {
            throw new IOException("File not found");
        }

        byte[] buffer = new byte[length];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.skip(offset);
            int read = fis.read(buffer);
            if (read < length) {
                byte[] actual = new byte[read];
                System.arraycopy(buffer, 0, actual, 0, read);
                return actual;
            }
            return buffer;
        }
    }

    public synchronized List<FileSearchResult> searchFiles(String keyword) {
        List<FileSearchResult> results = new ArrayList<>();
        String localAddress = Constants.LOCAL_ADDRESS;

        for (String fileName : availableFiles.keySet()) {
            File file = availableFiles.get(fileName);
            if (file.getName().toLowerCase().contains(keyword.toLowerCase())) {
                results.add(new FileSearchResult(
                        file.getName(),
                        file.length(),
                        localAddress,
                        port,
                        workingDirectory
                ));
            }
        }
        return results;
    }
}