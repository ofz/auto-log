package io.github.ofz.autolog.autoconfigure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AutoLog, prefixed with {@code auto.log}.
 *
 * <p>Example application.yml:
 * <pre>{@code
 * auto:
 *   log:
 *     enabled: true
 *     ring-buffer-size: 2048
 *     wait-strategy: BLOCKING
 *     producer-type: MULTI
 *     thread-name-prefix: autolog-disruptor-
 *     level: INFO
 * }</pre>
 *
 * @author ofz
 */
@ConfigurationProperties(prefix = "auto.log")
public class AutoLogProperties {

    /** Whether to enable auto-logging. Default: true. */
    private boolean enabled = true;

    /**
     * Ring buffer size, must be a power of 2.
     * Default: 1024. If not a power of 2, it is rounded up to the next power of 2.
     */
    private int ringBufferSize = 1024;

    /**
     * Disruptor wait strategy.
     * Supported values: BLOCKING, SLEEPING, YIELDING, BUSY_SPIN.
     * Default: BLOCKING.
     */
    private String waitStrategy = "BLOCKING";

    /**
     * Disruptor producer type.
     * Supported values: SINGLE, MULTI.
     * Default: MULTI (required for multi-threaded method invocation).
     */
    private String producerType = "MULTI";

    /**
     * Thread name prefix for Disruptor worker threads.
     * Default: "autolog-disruptor-".
     */
    private String threadNamePrefix = "autolog-disruptor-";

    /**
     * Default log level when not specified on the annotation.
     * Supported values: TRACE, DEBUG, INFO, WARN, ERROR.
     * Default: INFO.
     */
    private String level = "INFO";

    // ---- Getters and Setters ----

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    public String getWaitStrategy() {
        return waitStrategy;
    }

    public void setWaitStrategy(String waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    public String getProducerType() {
        return producerType;
    }

    public void setProducerType(String producerType) {
        this.producerType = producerType;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}
