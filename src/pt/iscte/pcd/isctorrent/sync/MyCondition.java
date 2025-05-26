package pt.iscte.pcd.isctorrent.sync;

public class MyCondition {
    private final MyLock lock;
    private final Object monitor = new Object();

    public MyCondition(MyLock lock) {
        this.lock = lock;
    }

    public void await() throws InterruptedException {
        lock.unlock();
        synchronized(monitor) {
            monitor.wait();
        }
        lock.lock();
    }

    public void signalAll() {
        synchronized(monitor) {
            monitor.notifyAll();
        }
    }

    public void signal() {
        synchronized(monitor) {
            monitor.notify();
        }
    }
}
