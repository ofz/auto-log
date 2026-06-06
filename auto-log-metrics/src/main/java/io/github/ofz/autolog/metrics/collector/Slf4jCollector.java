package io.github.ofz.autolog.metrics.collector;

import io.github.ofz.autolog.context.LogContextPool;
import io.github.ofz.autolog.disruptor.DisruptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically logs AutoLog metrics at INFO level.
 * Always active; interval is configurable via {@code auto.log.metrics.report-interval-ms}.
 */
public class Slf4jCollector {

    private static final Logger log = LoggerFactory.getLogger(Slf4jCollector.class);

    private final ScheduledExecutorService scheduler;
    private final LogContextPool pool;
    private final DisruptorConfig config;

    public Slf4jCollector(LogContextPool pool, DisruptorConfig config, long intervalMs) {
        this.pool = pool;
        this.config = config;

        if (intervalMs <= 0) {
            this.scheduler = null;
            return;
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "autolog-metrics-reporter");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(this::report, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void report() {
        try {
            long size = config.getRingBufferSize();
            long remaining = config.remainingCapacity();
            double utilization = size > 0 ? 100.0 * (size - remaining) / size : 0;

            log.info("[AutoLog Metrics] pool: {}/{} | borrow: {} miss: {} release: {} drop: {} | "
                     + "ringbuf: {}/{} ({}%) | slow: {}",
                    pool.available(), pool.getMaxSize(),
                    pool.getBorrowCount(), pool.getMissCount(),
                    pool.getReleaseCount(), pool.getDropCount(),
                    size - remaining, size,
                    String.format("%.1f", utilization),
                    pool.getSlowCount());
        } catch (Exception e) {
            // Metrics reporting must never throw
        }
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
