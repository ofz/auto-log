package io.github.ofz.autolog.autoconfigure.config;

import com.lmax.disruptor.dsl.ProducerType;
import io.github.ofz.autolog.aspect.AutoLogAspect;
import io.github.ofz.autolog.context.LogContextPool;
import io.github.ofz.autolog.disruptor.DisruptorConfig;
import io.github.ofz.autolog.disruptor.LogEventProducer;
import io.github.ofz.autolog.formatter.DefaultLogFormatter;
import io.github.ofz.autolog.formatter.LogFormatter;
import io.github.ofz.autolog.provider.AttributeProvider;
import io.github.ofz.autolog.provider.DefaultAttributeProvider;
import io.github.ofz.autolog.provider.DefaultOperatorProvider;
import io.github.ofz.autolog.provider.DefaultSensitiveDataFilter;
import io.github.ofz.autolog.provider.DefaultTraceIdProvider;
import io.github.ofz.autolog.provider.OperatorProvider;
import io.github.ofz.autolog.provider.SensitiveDataFilter;
import io.github.ofz.autolog.provider.TraceIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring Boot auto-configuration for the AutoLog framework.
 *
 * <p>Automatically sets up the Disruptor ring buffer, event producer, event handler,
 * default log formatter, LogContext object pool, and AOP aspect. All beans are
 * annotated with {@link ConditionalOnMissingBean} so users can provide custom
 * implementations.
 *
 * <p>Configuration is driven by {@link AutoLogProperties} (prefix {@code auto.log.*}).
 * The whole subsystem can be disabled by setting {@code auto.log.enabled=false}.
 *
 * @author ofz
 */
@Configuration
@ConditionalOnProperty(prefix = "auto.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AutoLogProperties.class)
public class AutoLogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AutoLogAutoConfiguration.class);

    /**
     * Creates the LogContext object pool. The pool size matches the ring buffer size
     * to ensure there are enough pre-allocated instances for full pipeline throughput.
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public LogContextPool logContextPool(AutoLogProperties properties) {
        int poolSize = DisruptorConfig.normalizeRingBufferSize(properties.getRingBufferSize());
        log.info("Initializing AutoLog LogContextPool with size={}", poolSize);
        return new LogContextPool(poolSize);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public DisruptorConfig disruptorConfig(AutoLogProperties properties, LogFormatter logFormatter,
                                           LogContextPool logContextPool) {
        int bufferSize = DisruptorConfig.normalizeRingBufferSize(properties.getRingBufferSize());
        ProducerType producerType = DisruptorConfig.parseProducerType(properties.getProducerType());
        com.lmax.disruptor.WaitStrategy waitStrategy = DisruptorConfig.parseWaitStrategy(properties.getWaitStrategy());

        log.info("Initializing AutoLog Disruptor with bufferSize={}, producerType={}, waitStrategy={}",
                bufferSize, producerType, waitStrategy.getClass().getSimpleName());

        return new DisruptorConfig(bufferSize, producerType, waitStrategy,
                properties.getThreadNamePrefix(), logFormatter, logContextPool);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DisruptorConfig.class)
    public LogEventProducer logEventProducer(DisruptorConfig disruptorConfig) {
        return disruptorConfig.getProducer();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperatorProvider operatorProvider() {
        return new DefaultOperatorProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceIdProvider traceIdProvider() {
        return new DefaultTraceIdProvider();
    }

    /**
     * Creates the default sensitive data filter, combining built-in keywords
     * with any additional keywords from {@code auto.log.sensitive-keywords}.
     * Skipped when a custom {@link SensitiveDataFilter} bean is registered.
     */
    @Bean
    @ConditionalOnMissingBean(SensitiveDataFilter.class)
    public DefaultSensitiveDataFilter sensitiveDataFilter(AutoLogProperties properties) {
        String raw = properties.getSensitiveKeywords();
        if (raw == null || raw.trim().isEmpty()) {
            return new DefaultSensitiveDataFilter();
        }
        Set<String> extra = new HashSet<>();
        for (String kw : raw.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) {
                extra.add(trimmed);
            }
        }
        log.info("AutoLog sensitive data filter initialised with {} extra keyword(s): {}",
                extra.size(), extra);
        return new DefaultSensitiveDataFilter(extra);
    }

    @Bean
    @ConditionalOnMissingBean
    public LogFormatter logFormatter(SensitiveDataFilter sensitiveDataFilter) {
        return new DefaultLogFormatter(sensitiveDataFilter);
    }

    /**
     * Default no-op attribute provider — created only when the user hasn't
     * registered any custom {@link AttributeProvider} beans.  When custom
     * providers exist this bean is skipped so only the user's providers
     * contribute attributes.
     */
    @Bean
    @ConditionalOnMissingBean(AttributeProvider.class)
    public DefaultAttributeProvider defaultAttributeProvider() {
        return new DefaultAttributeProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LogEventProducer.class)
    public AutoLogAspect autoLogAspect(LogEventProducer producer, LogFormatter formatter,
                                       OperatorProvider operatorProvider, TraceIdProvider traceIdProvider,
                                       LogContextPool logContextPool,
                                       List<AttributeProvider> attributeProviders) {
        return new AutoLogAspect(producer, formatter, operatorProvider, traceIdProvider,
                logContextPool, attributeProviders);
    }

}
