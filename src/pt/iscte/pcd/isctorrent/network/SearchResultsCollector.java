package pt.iscte.pcd.isctorrent.network;

import pt.iscte.pcd.isctorrent.protocol.FileSearchResult;
import pt.iscte.pcd.isctorrent.sync.MyCountDownLatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Coletor de resultados de pesquisa
 * Agrega resultados de múltiplos nós usando sincronização própria
 */
public class SearchResultsCollector {
    private final MyCountDownLatch latch;
    private final List<FileSearchResult> results;

    /**
     * Construtor do coletor
     * @param expectedResponses Número de respostas esperadas
     * @param initialResults Resultados iniciais (locais)
     */
    public SearchResultsCollector(int expectedResponses, List<FileSearchResult> initialResults) {
        this.latch = new MyCountDownLatch(expectedResponses);
        this.results = new ArrayList<>(initialResults);
    }

    /**
     * Adiciona resultados de um nó
     * @param newResults Novos resultados a adicionar
     */
    public synchronized void addResults(List<FileSearchResult> newResults) {
        if (newResults != null) {
            results.addAll(newResults);
        }
        latch.countDown();
    }

    /**
     * Espera por todas as respostas
     * @param timeoutMillis Timeout em milissegundos
     * @return true se todas as respostas foram recebidas, false se timeout
     */
    public boolean waitForResults(long timeoutMillis) throws InterruptedException {
        return latch.await(timeoutMillis);
    }

    /**
     * Obtém todos os resultados coletados
     * @return Lista com todos os resultados
     */
    public synchronized List<FileSearchResult> getAllResults() {
        return new ArrayList<>(results);
    }

    /**
     * Obtém o número de respostas pendentes
     * @return Número de respostas ainda esperadas
     */
    public int getPendingResponses() {
        return latch.getCount();
    }
}