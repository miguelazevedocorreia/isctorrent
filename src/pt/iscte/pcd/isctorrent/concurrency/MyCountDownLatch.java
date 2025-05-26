package pt.iscte.pcd.isctorrent.concurrency;

public class MyCountDownLatch {
    private int count;

    public MyCountDownLatch(int count) {
        this.count = count;
    }

    public synchronized void countDown() {
        count--;
        if (count <= 0) {
            notifyAll();
        }
    }

    public synchronized boolean await(long timeoutSeconds) throws InterruptedException {
        long timeoutMs = timeoutSeconds * 1000;
        long startTime = System.currentTimeMillis();

        while (count > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeoutMs) {
                return false; // timeout
            }

            long remainingTime = timeoutMs - elapsed;
            wait(remainingTime);
        }
        return true; // completed
    }

    public synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }

    public synchronized int getCount() {
        return count;
    }
}