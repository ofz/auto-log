package io.github.ofz.autolog.context;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free object pool for {@link LogContext} instances.
 *
 * <p>Pre-allocates a fixed number of LogContext objects and reuses them across
 * method invocations to reduce allocation pressure and Young GC frequency.
 * When the pool is exhausted, new instances are created on-the-fly and
 * returned to the pool after use (up to the configured maximum).
 *
 * <p>The pool is safe for concurrent borrow/release from multiple application
 * threads (via {@link #borrow()}) and the single Disruptor consumer thread
 * (via {@link #release(LogContext)}).
 *
 * @author ofz
 */
public class LogContextPool {

    private final ConcurrentLinkedQueue<LogContext> pool;
    private final int maxSize;

    /**
     * Approximate count of available instances. Maintained via atomic
     * increment/decrement on borrow/release so that {@link #available()}
     * and the capacity check in {@link #release(LogContext)} are O(1)
     * instead of the O(n) {@code ConcurrentLinkedQueue.size()}.
     */
    private final AtomicInteger poolSize = new AtomicInteger(0);

    /** Number of borrows since pool creation (for monitoring). */
    private final AtomicLong borrowCount = new AtomicLong(0);

    /** Number of new allocations due to pool exhaustion. */
    private final AtomicLong missCount = new AtomicLong(0);

    /** Number of successful releases back to the pool. */
    private final AtomicLong releaseCount = new AtomicLong(0);

    /** Number of releases dropped because the pool was full. */
    private final AtomicLong dropCount = new AtomicLong(0);

    /**
     * Creates a pool pre-allocated with {@code maxSize} empty LogContext instances.
     *
     * @param maxSize the maximum number of pooled instances
     */
    public LogContextPool(int maxSize) {
        this.maxSize = maxSize;
        this.pool = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < maxSize; i++) {
            pool.offer(new LogContext());
        }
        poolSize.set(maxSize);
    }

    /**
     * Borrows a LogContext from the pool. If the pool is empty, creates a new one.
     *
     * <p>The returned context has been cleared and is ready to be filled via
     * {@link LogContext#reset(String, String, java.lang.reflect.Method, Object[],
     * boolean, boolean, boolean, boolean, String, String, Class[], String, String)}.
     *
     * @return a cleared LogContext ready for reuse
     */
    public LogContext borrow() {
        borrowCount.incrementAndGet();
        LogContext ctx = pool.poll();
        if (ctx != null) {
            poolSize.decrementAndGet();
            return ctx;
        }
        missCount.incrementAndGet();
        return new LogContext();
    }

    /**
     * Returns a LogContext to the pool after it has been consumed.
     * If the pool is full, the context is dropped and left for GC.
     *
     * @param ctx the context to return (may be null, in which case this is a no-op)
     */
    public void release(LogContext ctx) {
        if (ctx == null) {
            return;
        }
        ctx.clearReferences();
        if (poolSize.get() < maxSize) {
            pool.offer(ctx);
            poolSize.incrementAndGet();
            releaseCount.incrementAndGet();
        } else {
            dropCount.incrementAndGet();
        }
    }

    // ---- Monitoring getters ----

    /** Total number of borrow operations. */
    public long getBorrowCount() {
        return borrowCount.get();
    }

    /** Number of times the pool was empty and a new instance was created. */
    public long getMissCount() {
        return missCount.get();
    }

    /** Number of successful returns to the pool. */
    public long getReleaseCount() {
        return releaseCount.get();
    }

    /** Number of contexts dropped because the pool was full. */
    public long getDropCount() {
        return dropCount.get();
    }

    /** Current number of available instances in the pool (approximate, O(1)). */
    public int available() {
        return poolSize.get();
    }

    /** Maximum pool size. */
    public int getMaxSize() {
        return maxSize;
    }
}
