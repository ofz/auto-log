package io.github.ofz.autolog.autoconfigure.config;

import com.lmax.disruptor.dsl.ProducerType;
import io.github.ofz.autolog.aspect.AutoLogAspect;
import io.github.ofz.autolog.disruptor.DisruptorConfig;
import io.github.ofz.autolog.disruptor.LogEventProducer;
import io.github.ofz.autolog.formatter.DefaultLogFormatter;
import io.github.ofz.autolog.formatter.LogFormatter;
import io.github.ofz.autolog.provider.DefaultOperatorProvider;
import io.github.ofz.autolog.provider.DefaultTraceIdProvider;
import io.github.ofz.autolog.provider.OperatorProvider;
import io.github.ofz.autolog.provider.TraceIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for the AutoLog framework.
 *
 * <p>Automatically sets up the Disruptor ring buffer, event producer, event handler,
 * default log formatter, and AOP aspect. All beans are annotated with
 * {@link ConditionalOnMissingBean} so users can provide custom implementations.
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

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public DisruptorConfig disruptorConfig(AutoLogProperties properties, LogFormatter logFormatter) {
        int bufferSize = DisruptorConfig.normalizeRingBufferSize(properties.getRingBufferSize());
        ProducerType producerType = DisruptorConfig.parseProducerType(properties.getProducerType());
        com.lmax.disruptor.WaitStrategy waitStrategy = DisruptorConfig.parseWaitStrategy(properties.getWaitStrategy());

        log.info("Initializing AutoLog Disruptor with bufferSize={}, producerType={}, waitStrategy={}",
                bufferSize, producerType, waitStrategy.getClass().getSimpleName());

        return new DisruptorConfig(bufferSize, producerType, waitStrategy,
                properties.getThreadNamePrefix(), logFormatter);
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

    @Bean
    @ConditionalOnMissingBean
    public LogFormatter logFormatter() {
        return new DefaultLogFormatter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LogEventProducer.class)
    public AutoLogAspect autoLogAspect(LogEventProducer producer, LogFormatter formatter,
                                       OperatorProvider operatorProvider, TraceIdProvider traceIdProvider) {
        return new AutoLogAspect(producer, formatter, operatorProvider, traceIdProvider);
    }

}
