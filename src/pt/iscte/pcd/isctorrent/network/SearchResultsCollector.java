package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.sync.MyCountDownLatch;

import java.util.ArrayList;
import java.util.List;

public class SearchResultsCollector {
    private final MyCountDownLatch latch;
    private final List<FileSearchResult> results;

    public SearchResultsCollector(MyCountDownLatch latch, List<FileSearchResult> initialResults) {
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