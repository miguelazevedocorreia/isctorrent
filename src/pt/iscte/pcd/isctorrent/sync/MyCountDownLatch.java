package pt.iscte.pcd.isctorrent.sync;

// implementação própria de CountDownLatch
public class MyCountDownLatch {
    private int count;

    public MyCountDownLatch(int count) {
        this.count = count;
    }

    // decrementa contador e notifica se chegou a zero
    public synchronized void countDown() {
        if (count > 0) {
            count--;
            if (count == 0) {
                notifyAll(); // acorda todas as threads em espera
            }
        }
    }

    // espera até contador chegar a zero
    public synchronized void await() throws InterruptedException {
        while (count > 0) { // while loop
            wait();
        }
    }

    // versão com timeout para coordenação de pesquisas
    public synchronized boolean await(long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while (count > 0) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false; // timeout expirou
            }
            wait(remaining); // espera pelo tempo restante
        }
        return true; // completou antes do timeout
    }
}