package com.lefilou.jedis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static java.util.Arrays.fill;
import static org.apache.logging.log4j.LogManager.getLogger;

class RedisWorker {

    private static final Logger logger = getLogger(RedisWorker.class);

    private static final String LARGE_VALUE = "This is a large value used to simulate a large JSON object. ".repeat(10);
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 7010;
    public static final int DEFAULT_TTL = 15 * 60;

    private static final Random random = new Random();

    private final JedisCluster jedisCluster;

    public RedisWorker() {
        jedisCluster = buildJedisCluster();
    }

    public void process(int threadId) {
        int numberOfOperationsPerThread = 10000;

        for (int i = 0; i < numberOfOperationsPerThread; i++) {
            try {
                runSomeLoad(threadId, i);
            } catch (Exception e) {
                if (e instanceof IllegalStateException) {
                    throw e;
                }
                logger.error("Error in thread {} at iteration {}: {}", threadId, i, e.getMessage());
            }
        }
        logger.info("Thread {} completed all operations", threadId);
    }


    public void stop() {
        try {
            if (jedisCluster != null) {
                jedisCluster.close();
            }
        } catch (Exception e) {
            logger.error("Error closing JedisCluster: {}", e.getMessage());
        }
    }

    private JedisCluster buildJedisCluster() {
        return new JedisCluster(
                Set.of(new HostAndPort(DEFAULT_HOST, DEFAULT_PORT)),
                2000,
                2000,
                10,
                null,
                null,
                new GenericObjectPoolConfig(),
                false
        );
    }

    /**
     * Simulate situation where we have a key pointing to a pointer, which points to a large value:
     * key -> pointer -> large value
     */
    private void runSomeLoad(int threadId, int i) {
        final String threadPrefix = "thread-" + threadId + "-";
        final String key = threadPrefix + "key-" + i;
        final String pointer = threadPrefix + "pointer-" + i;
        final String value = threadPrefix + "large-value-" + i + "-" + generateLargeJson(random.nextInt(2, 20));

        simulateRealisticOutOfMemoryError(Integer.parseInt(threadPrefix.split("-")[1]), i);

        jedisCluster.setex(key, DEFAULT_TTL, pointer);
        jedisCluster.setex(pointer, DEFAULT_TTL, value);

        String readPointer = jedisCluster.get(key);
        if (readPointer == null || !readPointer.equals(pointer)) {
            logger.info("Issue found on the pointer! readPointer={} expected={}", readPointer, pointer);
            throw new IllegalStateException("Fount error reading pointer for key: " + key);
        }
        String readValue = jedisCluster.get(readPointer);
        if (readValue == null || !readValue.equals(value)) {
            logger.info("Issue found on the value! readValue={} expected={}", readValue, value);
            throw new IllegalStateException("Fount error reading value for pointer: " + readPointer);
        }
    }

    /**
     * Simulates a realistic OutOfMemoryError by allocating large objects in a controlled manner.
     * This method tries to avoid immediate JVM crash by allocating memory in chunks and allowing
     * other threads to continue running, making it more likely to encounter memory pressure situations.
     */
    private void simulateRealisticOutOfMemoryError(int threadId, int iteration) {
        // Only trigger OOM for certain combinations to make it realistic
        if ((threadId + iteration) % 17 == 0) {
            try {
                // Create a large list that consumes significant memory
                List<byte[]> memoryHog = new ArrayList<>();

                // Allocate memory in chunks to avoid immediate crash
                for (int i = 0; i < 1000; i++) {
                    // Each chunk is 1MB
                    byte[] chunk = new byte[1024 * 1024];
                    // Fill with data to prevent JIT optimization
                    fill(chunk, (byte) (i % 256));
                    memoryHog.add(chunk);

                    // Small delay to let other threads continue and GC to struggle
                    if (i % 50 == 0) {
                        Thread.sleep(1);
                    }
                }

                // Keep reference alive briefly
                logger.info("Allocated {} MB in thread {}", memoryHog.size(), threadId);

            } catch (OutOfMemoryError e) {
                logger.error("OutOfMemoryError triggered in thread {} at iteration {}", threadId, iteration);
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    private static String generateLargeJson(int numberOfEntries) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        for (int i = 1; i <= numberOfEntries; i++) {
            jsonBuilder
                    .append("  \"key").append(i).append("\": ").append(LARGE_VALUE).append("\"").append(i).append("\"");
            if (i < numberOfEntries) {
                jsonBuilder.append(",\n");
            } else {
                jsonBuilder.append("\n");
            }
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

}
