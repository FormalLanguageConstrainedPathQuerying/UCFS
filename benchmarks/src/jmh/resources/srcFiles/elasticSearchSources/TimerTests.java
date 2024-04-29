/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.profile;

import org.elasticsearch.test.ESTestCase;

import java.util.concurrent.atomic.AtomicLong;

public class TimerTests extends ESTestCase {

    public void testTimingInterval() {
        final AtomicLong nanoTimeCallCounter = new AtomicLong();
        Timer t = new Timer() {
            long time = 50;

            @Override
            long nanoTime() {
                nanoTimeCallCounter.incrementAndGet();
                return time += 1;
            }
        };
        for (int i = 0; i < 100000; ++i) {
            t.start();
            t.stop();
            if (i < 256) {
                assertEquals((i + 1) * 2, nanoTimeCallCounter.get());
            }
        }
        assertEquals(3356L, nanoTimeCallCounter.get());
    }

    public void testExtrapolate() {
        Timer t = new Timer() {
            long time = 50;

            @Override
            long nanoTime() {
                return time += 42;
            }
        };
        for (int i = 1; i < 100000; ++i) {
            t.start();
            t.stop();
            assertEquals(i, t.getCount());
            assertEquals(i * 42L, t.getApproximateTiming());
        }
    }

}
