package pt.iscte.pcd.isctorrent.concurrency;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

// Custom Condition implementation
public class MyCondition {
    private final MyReentrantLock lock;
    private final ConcurrentLinkedQueue<Thread> waitQueue = new ConcurrentLinkedQueue<>();

    public MyCondition(MyReentrantLock lock) {
        this.lock = lock;
    }

    public void await() throws InterruptedException {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException();
        }

        Thread current = Thread.currentThread();
        waitQueue.add(current);

        lock.unlock();

        while (waitQueue.contains(current)) {
            LockSupport.park();
            if (Thread.interrupted()) {
                waitQueue.remove(current);
                throw new InterruptedException();
            }
        }

        lock.lock();
    }

    public void signal() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException();
        }

        Thread thread = waitQueue.poll();
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }

    public void signalAll() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException();
        }

        Thread thread;
        while ((thread = waitQueue.poll()) != null) {
            LockSupport.unpark(thread);
        }
    }
}