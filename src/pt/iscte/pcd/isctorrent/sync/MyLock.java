package pt.iscte.pcd.isctorrent.sync;

/**
 * Implementação própria de um lock reentrante
 * Requisito obrigatório do projeto - não usar bibliotecas Java
 */
public class MyLock {
    private boolean locked = false;
    private Thread owner = null;
    private int lockCount = 0;

    /**
     * Adquire o lock
     * Suporta reentrância - a mesma thread pode adquirir múltiplas vezes
     */
    public synchronized void lock() throws InterruptedException {
        Thread current = Thread.currentThread();

        // Esperar enquanto locked por outra thread
        while (locked && owner != current) {
            wait();
        }

        locked = true;
        owner = current;
        lockCount++;
    }

    /**
     * Liberta o lock
     * Deve ser chamado o mesmo número de vezes que lock()
     */
    public synchronized void unlock() {
        Thread current = Thread.currentThread();

        if (owner != current) {
            throw new IllegalMonitorStateException("Thread não possui o lock");
        }

        lockCount--;

        if (lockCount == 0) {
            locked = false;
            owner = null;
            notify(); // Acorda uma thread em espera
        }
    }

    /**
     * Verifica se o lock está adquirido
     * @return true se locked, false caso contrário
     */
    public synchronized boolean isLocked() {
        return locked;
    }

    /**
     * Verifica se a thread atual possui o lock
     * @return true se a thread atual é owner
     */
    public synchronized boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }
}