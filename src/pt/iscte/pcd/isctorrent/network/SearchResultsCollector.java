package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.concurrency.MyReentrantLock;
import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SearchResultsCollector {
    private final CountDownLatch latch;
    private final List<FileSearchResult> results;
    private final MyReentrantLock lock = new MyReentrantLock();

    public SearchResultsCollector(CountDownLatch latch, List<FileSearchResult> initialResults) {
        this.latch = latch;
        this.results = new ArrayList<>(initialResults);
    }

    public void addResults(List<FileSearchResult> newResults) {
        lock.lock();
        try {
            results.addAll(newResults);
            latch.countDown();
        } finally {
            lock.unlock();
        }
    }

    public List<FileSearchResult> getAllResults() {
        lock.lock();
        try {
            return new ArrayList<>(results);
        } finally {
            lock.unlock();
        }
    }
}