package pt.iscte.pcd.isctorrent.sync;

public class MyCountDownLatch {
    private int count;

    public MyCountDownLatch(int count) {
        this.count = count;
    }

    public synchronized void countDown() {
        if (count > 0) {
            count--;
            if (count == 0) {
                notifyAll();
            }
        }
    }

    public synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }

    public synchronized boolean await(long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while (count > 0) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            wait(remaining);
        }
        return true;
    }
}