package pt.iscte.pcd.isctorrent.sync;

public class MyLock {
    private boolean locked = false;
    private Thread owner;

    public synchronized void lock() throws InterruptedException {
        while (locked) {
            wait();
        }
        locked = true;
        owner = Thread.currentThread();
    }

    public synchronized void unlock() {
        if (owner == Thread.currentThread()) {
            locked = false;
            owner = null;
            notifyAll();
        }
    }
}
