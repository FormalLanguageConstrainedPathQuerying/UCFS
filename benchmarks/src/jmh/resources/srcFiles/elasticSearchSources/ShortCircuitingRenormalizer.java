/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.job.process.normalizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.util.CancellableThreads;
import org.elasticsearch.xpack.core.ml.job.process.autodetect.state.Quantiles;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Renormalizer for one job that discards outdated quantiles if even newer ones are received while waiting for a prior renormalization
 * to complete. Only one normalization must run at any time for any particular job. Quantiles documents can be large so it's important
 * that we don't retain them unnecessarily.
 */
public class ShortCircuitingRenormalizer implements Renormalizer {

    private static final Logger logger = LogManager.getLogger(ShortCircuitingRenormalizer.class);

    private final String jobId;
    private final ScoresUpdater scoresUpdater;
    private final ExecutorService executorService;
    /**
     * Each job may only have 1 normalization in progress at any time; the semaphore enforces this.
     * Modifications must be synchronized.
     */
    private final Semaphore semaphore = new Semaphore(1);
    private AugmentedQuantiles latestQuantilesHolder;
    private Future<?> latestTask;

    public ShortCircuitingRenormalizer(String jobId, ScoresUpdater scoresUpdater, ExecutorService executorService) {
        this.jobId = jobId;
        this.scoresUpdater = scoresUpdater;
        this.executorService = executorService;
    }

    @Override
    public boolean isEnabled() {
        return scoresUpdater.getNormalizationWindow() > 0;
    }

    @Override
    public void renormalize(Quantiles quantiles, Runnable setupStep) {
        if (isEnabled() == false) {
            return;
        }

        synchronized (this) {
            latestQuantilesHolder = (latestQuantilesHolder == null)
                ? new AugmentedQuantiles(quantiles, setupStep, null, new CountDownLatch(1))
                : new AugmentedQuantiles(
                    quantiles,
                    setupStep,
                    latestQuantilesHolder.getEvictedTimestamp(),
                    latestQuantilesHolder.getLatch()
                );
            tryStartWork();
        }
    }

    @Override
    public void waitUntilIdle() throws InterruptedException {
        CountDownLatch latch;
        Future<?> taskToWaitFor;
        do {
            synchronized (this) {
                latch = (latestQuantilesHolder != null) ? latestQuantilesHolder.getLatch() : null;
            }
            if (latch != null) {
                latch.await();
            }
            synchronized (this) {
                taskToWaitFor = latestTask;
            }
            if (taskToWaitFor != null) {
                try {
                    taskToWaitFor.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof CancellableThreads.ExecutionCancelledException) {
                        break;
                    }
                    logger.error("[" + jobId + "] Error propagated from normalization", e);
                    taskToWaitFor = null;
                } catch (CancellationException e) {
                    throw new InterruptedException("Normalization cancelled");
                }
            }
        } while (latch != null || taskToWaitFor != null);
    }

    @Override
    public void shutdown() throws InterruptedException {
        scoresUpdater.shutdown();
        waitUntilIdle();
    }

    private synchronized AugmentedQuantiles getLatestAugmentedQuantilesAndClear() {
        AugmentedQuantiles latest = latestQuantilesHolder;
        latestQuantilesHolder = null;
        return latest;
    }

    private synchronized boolean tryStartWork() {
        if (latestQuantilesHolder == null) {
            return false;
        }
        if (semaphore.tryAcquire()) {
            try {
                latestTask = executorService.submit(this::doRenormalizations);
            } catch (RejectedExecutionException e) {
                latestQuantilesHolder.getLatch().countDown();
                latestQuantilesHolder = null;
                latestTask = null;
                semaphore.release();
                logger.warn("[{}] Normalization discarded as threadpool is shutting down", jobId);
                return false;
            }
            return true;
        }
        return false;
    }

    private synchronized boolean tryFinishWork() {
        if (latestQuantilesHolder != null) {
            return false;
        }
        semaphore.release();
        latestTask = null;
        return true;
    }

    private void doRenormalizations() {
        do {
            AugmentedQuantiles latestAugmentedQuantiles = getLatestAugmentedQuantilesAndClear();
            assert latestAugmentedQuantiles != null;
            if (latestAugmentedQuantiles != null) { 
                latestAugmentedQuantiles.runSetupStep();
                Quantiles latestQuantiles = latestAugmentedQuantiles.getQuantiles();
                CountDownLatch latch = latestAugmentedQuantiles.getLatch();
                try {
                    scoresUpdater.update(
                        latestQuantiles.getQuantileState(),
                        latestQuantiles.getTimestamp().getTime(),
                        latestAugmentedQuantiles.getWindowExtensionMs()
                    );
                } catch (Exception e) {
                    logger.error("[" + jobId + "] Normalization failed", e);
                } finally {
                    latch.countDown();
                }
            } else {
                logger.warn("[{}] request to normalize null quantiles", jobId);
            }
        } while (tryFinishWork() == false);
    }

    /**
     * Grouping of a {@linkplain Quantiles} object with its corresponding {@linkplain CountDownLatch} object.
     * Also stores the earliest timestamp that any set of discarded quantiles held, to allow the normalization
     * window to be extended if multiple normalization requests are combined.
     */
    private class AugmentedQuantiles {
        private final Quantiles quantiles;
        private final Runnable setupStep;
        private final Date earliestEvictedTimestamp;
        private final CountDownLatch latch;

        AugmentedQuantiles(Quantiles quantiles, Runnable setupStep, Date earliestEvictedTimestamp, CountDownLatch latch) {
            this.quantiles = Objects.requireNonNull(quantiles);
            this.setupStep = Objects.requireNonNull(setupStep);
            this.earliestEvictedTimestamp = earliestEvictedTimestamp;
            this.latch = Objects.requireNonNull(latch);
        }

        Quantiles getQuantiles() {
            return quantiles;
        }

        void runSetupStep() {
            setupStep.run();
        }

        Date getEvictedTimestamp() {
            return (earliestEvictedTimestamp != null) ? earliestEvictedTimestamp : quantiles.getTimestamp();
        }

        long getWindowExtensionMs() {
            if (earliestEvictedTimestamp == null) {
                return 0;
            }
            long earliestTimeMs = earliestEvictedTimestamp.getTime();
            long latestTimeMs = quantiles.getTimestamp().getTime();
            long windowExtensionMs = latestTimeMs - earliestTimeMs;
            if (windowExtensionMs < 0) {
                logger.warn("[{}] Quantiles not supplied in time order - {} after {}", jobId, latestTimeMs, earliestTimeMs);
                return 0;
            }
            return windowExtensionMs;
        }

        CountDownLatch getLatch() {
            return latch;
        }
    }
}
