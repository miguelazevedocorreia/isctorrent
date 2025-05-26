package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.sync.MyCountDownLatch;

import java.util.ArrayList;
import java.util.List;

// recolhe resultados de pesquisa usando CountDownLatch
public class SearchResultsCollector {
    private final MyCountDownLatch latch; // coordenação para esperar por todas as respostas
    private final List<FileSearchResult> results; // todos os resultados recolhidos

    public SearchResultsCollector(MyCountDownLatch latch, List<FileSearchResult> initialResults) {
        this.latch = latch;
        this.results = new ArrayList<>(initialResults); // inclui resultados locais
    }

    // adiciona resultados de um nó e decrementa contador
    public synchronized void addResults(List<FileSearchResult> newResults) {
        results.addAll(newResults);
        latch.countDown(); // sinaliza que este nó respondeu
    }

    // obtém todos os resultados recolhidos
    public synchronized List<FileSearchResult> getAllResults() {
        return new ArrayList<>(results);
    }
}