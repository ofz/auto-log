package io.github.ofz.autolog.metrics.collector;

import io.github.ofz.autolog.context.LogContextPool;
import io.github.ofz.autolog.disruptor.DisruptorConfig;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Registers AutoLog metrics with Micrometer so they appear in
 * {@code /actuator/prometheus}, Grafana, Datadog, etc.
 * Activated automatically when Micrometer is on the classpath.
 */
public class MicrometerCollector implements MeterBinder {

    private final LogContextPool pool;
    private final DisruptorConfig config;

    public MicrometerCollector(LogContextPool pool, DisruptorConfig config) {
        this.pool = pool;
        this.config = config;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // ---- Pool gauges ----
        Gauge.builder("autolog.pool.available", pool, LogContextPool::available)
                .description("Currently available LogContext instances in the pool")
                .baseUnit("objects")
                .register(registry);

        // ---- Pool cumulative counters ----
        FunctionCounter.builder("autolog.pool.borrow", pool, LogContextPool::getBorrowCount)
                .description("Total borrow operations")
                .register(registry);

        FunctionCounter.builder("autolog.pool.miss", pool, LogContextPool::getMissCount)
                .description("Pool misses (new allocations due to exhaustion)")
                .register(registry);

        FunctionCounter.builder("autolog.pool.release", pool, LogContextPool::getReleaseCount)
                .description("Successful returns to the pool")
                .register(registry);

        FunctionCounter.builder("autolog.pool.drop", pool, LogContextPool::getDropCount)
                .description("Returns dropped because the pool was full")
                .register(registry);

        FunctionCounter.builder("autolog.slow", pool, LogContextPool::getSlowCount)
                .description("Slow invocations exceeding slowThresholdMs")
                .register(registry);

        // ---- RingBuffer gauges ----
        Gauge.builder("autolog.ringbuffer.utilization", config, c ->
                        1.0 - (double) c.remainingCapacity() / c.getRingBufferSize())
                .description("RingBuffer utilization (0.0–1.0)")
                .baseUnit("fraction")
                .register(registry);

        Gauge.builder("autolog.ringbuffer.remaining", config, DisruptorConfig::remainingCapacity)
                .description("Remaining slots in the ring buffer")
                .baseUnit("slots")
                .register(registry);
    }
}
