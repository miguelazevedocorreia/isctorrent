package pt.iscte.pcd.isctorrent.sync;

public class MyThreadPool {
    private final Thread[] workers;
    private final java.util.Queue<Runnable> tasks = new java.util.LinkedList<>();
    private volatile boolean shutdown = false;

    public MyThreadPool(int size) {
        workers = new Thread[size];
        for (int i = 0; i < size; i++) {
            workers[i] = new Thread(this::workerLoop);
            workers[i].start();
        }
    }

    public synchronized void submit(Runnable task) {
        if (!shutdown) {
            tasks.offer(task);
            notifyAll();
        }
    }

    private void workerLoop() {
        while (!shutdown) {
            Runnable task = null;
            synchronized(this) {
                while (tasks.isEmpty() && !shutdown) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (!shutdown) {
                    task = tasks.poll();
                }
            }
            if (task != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    // Ignora exceções das tasks
                }
            }
        }
    }

    public synchronized void shutdown() {
        shutdown = true;
        notifyAll();
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }
}