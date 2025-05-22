package pt.iscte.pcd.isctorrent.sync;

/**
 * Implementação própria de um CountDownLatch
 * Permite que threads esperem até que um contador chegue a zero
 */
public class MyCountDownLatch {
    private int count;

    /**
     * Construtor do latch
     * @param count Contador inicial (deve ser >= 0)
     */
    public MyCountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count não pode ser negativo");
        }
        this.count = count;
    }

    /**
     * Decrementa o contador
     * Se chegar a zero, acorda todas as threads em espera
     */
    public synchronized void countDown() {
        if (count > 0) {
            count--;
            if (count == 0) {
                notifyAll(); // Acorda todas as threads em espera
            }
        }
    }

    /**
     * Espera até o contador chegar a zero
     * @throws InterruptedException se a thread for interrompida
     */
    public synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }

    /**
     * Espera até o contador chegar a zero ou timeout
     * @param timeout Tempo máximo de espera em milissegundos
     * @return true se o contador chegou a zero, false se timeout
     */
    public synchronized boolean await(long timeout) throws InterruptedException {
        if (count == 0) {
            return true;
        }

        long deadline = System.currentTimeMillis() + timeout;
        long remaining = timeout;

        while (count > 0 && remaining > 0) {
            wait(remaining);
            remaining = deadline - System.currentTimeMillis();
        }

        return count == 0;
    }

    /**
     * Obtém o valor atual do contador
     * @return Valor do contador
     */
    public synchronized int getCount() {
        return count;
    }
}