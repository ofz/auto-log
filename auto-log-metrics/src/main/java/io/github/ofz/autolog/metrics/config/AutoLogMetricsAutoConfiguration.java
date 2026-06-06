package io.github.ofz.autolog.metrics.config;

import io.github.ofz.autolog.context.LogContextPool;
import io.github.ofz.autolog.disruptor.DisruptorConfig;
import io.github.ofz.autolog.metrics.collector.MicrometerCollector;
import io.github.ofz.autolog.metrics.collector.Slf4jCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for AutoLog metrics.
 *
 * <p>Two complementary collectors:
 * <ul>
 *   <li>{@link MicrometerCollector} — activated when Micrometer is on the
 *       classpath (i.e. any Spring Boot app with Actuator). Registers
 *       gauges and counters for Prometheus / Grafana.</li>
 *   <li>{@link Slf4jCollector} — always active. Periodically logs a
 *       human-readable summary for quick diagnosis without a dashboard.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(prefix = "auto.log.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AutoLogMetricsProperties.class)
@ConditionalOnBean({LogContextPool.class, DisruptorConfig.class})
public class AutoLogMetricsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AutoLogMetricsAutoConfiguration.class);

    // ---- Micrometer (when Actuator is on classpath) ----

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public MicrometerCollector micrometerCollector(LogContextPool pool, DisruptorConfig config) {
        log.info("AutoLog metrics: Micrometer collector activated");
        return new MicrometerCollector(pool, config);
    }

    // ---- SLF4J periodic reporter (always) ----

    @Bean
    @ConditionalOnMissingBean
    public Slf4jCollector slf4jCollector(LogContextPool pool, DisruptorConfig config,
                                          AutoLogMetricsProperties props) {
        if (props.getReportIntervalMs() > 0) {
            log.info("AutoLog metrics: SLF4J reporter activated (interval={}ms)", props.getReportIntervalMs());
        }
        return new Slf4jCollector(pool, config, props.getReportIntervalMs());
    }
}
