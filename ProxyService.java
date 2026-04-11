import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ProxyService {

    private final ScoreClient upstream;
    private final BlockingQueue<ProxyRequest> queue;

    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong upstreamCalls = new AtomicLong();

    public ProxyService(ScoreClient upstream, int maxQueueSize) {
        this.upstream = upstream;
        this.queue = new ArrayBlockingQueue<>(maxQueueSize);
        startSchedulerThread();
    }

    public CompletableFuture<Double> requestScore(String cpf) {
        ProxyRequest req = new ProxyRequest(cpf);

        boolean ok = queue.offer(req);
        if (!ok) {
            dropped.incrementAndGet();
            req.future.completeExceptionally(
                    new RuntimeException("Fila cheia, requisição descartada"));
        } else {
            enqueued.incrementAndGet();
        }

        return req.future;
    }

    private void startSchedulerThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    ProxyRequest req = queue.take();

                    try {
                        upstreamCalls.incrementAndGet();
                        double score = upstream.score(req.cpf);
                        req.future.complete(score);
                    } catch (Exception e) {
                        req.future.completeExceptionally(e);
                    }

                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public long getEnqueued() {
        return enqueued.get();
    }

    public long getDropped() {
        return dropped.get();
    }

    public long getUpstreamCalls() {
        return upstreamCalls.get();
    }
}