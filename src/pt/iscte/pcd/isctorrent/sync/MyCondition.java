package pt.iscte.pcd.isctorrent.sync;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Implementação própria de uma variável condicional
 * Trabalha em conjunto com MyLock
 */
public class MyCondition {
    private final MyLock lock;
    private final Queue<Thread> waitingThreads = new LinkedList<>();

    /**
     * Construtor da variável condicional
     * @param lock Lock associado a esta condição
     */
    public MyCondition(MyLock lock) {
        this.lock = lock;
    }

    /**
     * Espera até ser sinalizado
     * Liberta o lock enquanto espera e readquire quando acordado
     */
    public void await() throws InterruptedException {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("Thread não possui o lock");
        }

        Thread current = Thread.currentThread();

        synchronized (this) {
            waitingThreads.add(current);

            // Libertar o lock antes de esperar
            lock.unlock();

            try {
                // Esperar até ser notificado
                while (waitingThreads.contains(current)) {
                    wait();
                }
            } finally {
                // Readquirir o lock após acordar
                lock.lock();
            }
        }
    }

    /**
     * Acorda uma thread em espera
     */
    public synchronized void signal() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("Thread não possui o lock");
        }

        Thread thread = waitingThreads.poll();
        if (thread != null) {
            notifyAll(); // Acorda todas para verificar se é a sua vez
        }
    }

    /**
     * Acorda todas as threads em espera
     */
    public synchronized void signalAll() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("Thread não possui o lock");
        }

        waitingThreads.clear();
        notifyAll();
    }
}