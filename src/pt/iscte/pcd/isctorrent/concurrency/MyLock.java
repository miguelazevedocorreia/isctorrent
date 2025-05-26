package pt.iscte.pcd.isctorrent.concurrency;

import java.util.concurrent.atomic.AtomicReference;

// Custom ReentrantLock implementation
public class MyLock {
    private final AtomicReference<Thread> owner = new AtomicReference<>(null);
    private int holdCount = 0;

    public void lock() {
        Thread current = Thread.currentThread();
        while (!owner.compareAndSet(null, current)) {
            if (owner.get() == current) {
                holdCount++;
                return;
            }
            Thread.yield();
        }
        holdCount = 1;
    }

    public void unlock() {
        Thread current = Thread.currentThread();
        if (owner.get() != current) {
            throw new IllegalMonitorStateException();
        }
        if (--holdCount == 0) {
            owner.set(null);
        }
    }

    public MyCondition newCondition() {
        return new MyCondition(this);
    }

    public boolean isHeldByCurrentThread() {
        return owner.get() == Thread.currentThread();
    }
}