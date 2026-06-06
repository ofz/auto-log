package io.github.ofz.autolog.metrics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AutoLog metrics, prefixed with {@code auto.log.metrics}.
 */
@ConfigurationProperties(prefix = "auto.log.metrics")
public class AutoLogMetricsProperties {

    /** Enable metrics collection. Default: true. */
    private boolean enabled = true;

    /** SLF4J report interval in milliseconds. Set to 0 to disable. Default: 60000. */
    private long reportIntervalMs = 60_000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getReportIntervalMs() { return reportIntervalMs; }
    public void setReportIntervalMs(long reportIntervalMs) { this.reportIntervalMs = reportIntervalMs; }
}
