package com.lefilou.jedis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) throws InterruptedException {

        int numThreads = args.length > 0 ? Integer.parseInt(args[0]) : 700;

        RedisWorker worker = new RedisWorker();

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {

            CountDownLatch latch = new CountDownLatch(numThreads);

            logger.info("Starting {} worker threads...", numThreads);

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.execute(() -> {
                    try {
                        worker.process(threadId);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            if (!latch.await(120, TimeUnit.SECONDS)) {
                logger.warn("Timeout reached before all threads completed.");
            }

            executor.shutdown();
        }
        worker.stop();
        logger.info("All threads completed!");

    }

}