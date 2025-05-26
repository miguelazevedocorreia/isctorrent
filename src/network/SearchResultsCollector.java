package network;

import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SearchResultsCollector {
    private final CountDownLatch latch;
    private final List<FileSearchResult> results;

    public SearchResultsCollector(CountDownLatch latch, List<FileSearchResult> initialResults) {
        this.latch = latch;
        this.results = new ArrayList<>(initialResults);
    }

    public synchronized void addResults(List<FileSearchResult> newResults) {
        results.addAll(newResults);
        latch.countDown();
    }

    public synchronized List<FileSearchResult> getAllResults() {
        return new ArrayList<>(results);
    }
}