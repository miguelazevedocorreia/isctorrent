package pt.iscte.pcd.isctorrent.core;

import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileManager {
    private final String workingDirectory;
    private final Map<String, File> availableFiles;
    private final int port;

    public FileManager(String workingDirectory, int port) {
        this.workingDirectory = workingDirectory;
        this.availableFiles = new ConcurrentHashMap<>();
        this.port = port;
        loadFiles();
    }

    private void loadFiles() {
        File directory = new File(workingDirectory);

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    try {
                        String hash = calculateHash(file);
                        availableFiles.put(hash, file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private String calculateHash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to calculate hash", e);
        }
    }

    public synchronized byte[] readFileBlock(String hash, long offset, int length)
            throws IOException {
        File file = availableFiles.get(hash);
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

    public List<FileSearchResult> searchFiles(String keyword) {
        List<FileSearchResult> results = new ArrayList<>();
        String localAddress = Constants.LOCAL_ADDRESS;

        for (String hash : availableFiles.keySet()) {
            File file = availableFiles.get(hash);
            if (file.getName().toLowerCase().contains(keyword.toLowerCase())) {
                results.add(new FileSearchResult(
                        hash,
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