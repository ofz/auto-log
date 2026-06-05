package io.github.ofz.autolog.autoconfigure;

import io.github.ofz.autolog.autoconfigure.config.AutoLogProperties;
import io.github.ofz.autolog.disruptor.DisruptorConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for starter auto-configuration utilities.
 */
class StarterConfigTest {

    @Test
    void testAutoLogPropertiesDefaults() {
        AutoLogProperties props = new AutoLogProperties();
        assertTrue(props.isEnabled());
        assertEquals(1024, props.getRingBufferSize());
        assertEquals("BLOCKING", props.getWaitStrategy());
        assertEquals("MULTI", props.getProducerType());
        assertEquals("autolog-disruptor-", props.getThreadNamePrefix());
        assertEquals("INFO", props.getLevel());
    }

    @Test
    void testRoundUpToPowerOfTwo() {
        assertEquals(64, DisruptorConfig.normalizeRingBufferSize(1));
        assertEquals(64, DisruptorConfig.normalizeRingBufferSize(63));
        assertEquals(64, DisruptorConfig.normalizeRingBufferSize(64));
        assertEquals(128, DisruptorConfig.normalizeRingBufferSize(65));
        assertEquals(128, DisruptorConfig.normalizeRingBufferSize(100));
        assertEquals(256, DisruptorConfig.normalizeRingBufferSize(200));
        assertEquals(1024, DisruptorConfig.normalizeRingBufferSize(1024));
        assertEquals(2048, DisruptorConfig.normalizeRingBufferSize(1025));
    }

    @Test
    void testNormalizeRingBufferSizeClamping() {
        assertEquals(64, DisruptorConfig.normalizeRingBufferSize(-1));
        assertEquals(64, DisruptorConfig.normalizeRingBufferSize(0));
        assertEquals(64, DisruptorConfig.normalizeRingBufferSize(32));
        assertEquals(65536, DisruptorConfig.normalizeRingBufferSize(100000));
    }

    @Test
    void testParseWaitStrategy() {
        assertNotNull(DisruptorConfig.parseWaitStrategy("BLOCKING"));
        assertNotNull(DisruptorConfig.parseWaitStrategy("SLEEPING"));
        assertNotNull(DisruptorConfig.parseWaitStrategy("YIELDING"));
        assertNotNull(DisruptorConfig.parseWaitStrategy("BUSY_SPIN"));
        // Unknown falls back to BLOCKING
        assertNotNull(DisruptorConfig.parseWaitStrategy("INVALID"));
        assertNotNull(DisruptorConfig.parseWaitStrategy(null));
        assertNotNull(DisruptorConfig.parseWaitStrategy(""));
    }

    @Test
    void testParseProducerType() {
        assertNotNull(DisruptorConfig.parseProducerType("SINGLE"));
        assertNotNull(DisruptorConfig.parseProducerType("MULTI"));
        assertNotNull(DisruptorConfig.parseProducerType("INVALID"));
        assertNotNull(DisruptorConfig.parseProducerType(null));
    }
}
