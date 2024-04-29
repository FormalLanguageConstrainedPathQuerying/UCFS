/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.bulk;

import org.elasticsearch.core.TimeValue;
import org.elasticsearch.test.ESTestCase;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.elasticsearch.core.TimeValue.timeValueMillis;

public class BackoffPolicyTests extends ESTestCase {
    public void testWrapBackoffPolicy() {
        TimeValue timeValue = timeValueMillis(between(0, Integer.MAX_VALUE));
        int maxNumberOfRetries = between(1, 1000);
        BackoffPolicy policy = BackoffPolicy.constantBackoff(timeValue, maxNumberOfRetries);
        AtomicInteger retries = new AtomicInteger();
        policy = BackoffPolicy.wrap(policy, retries::getAndIncrement);

        int expectedRetries = 0;
        {
            Iterator<TimeValue> itr = policy.iterator();
            assertEquals(expectedRetries, retries.get());

            while (itr.hasNext()) {
                assertEquals(expectedRetries, retries.get());
                itr.next();
                expectedRetries += 1;
                assertEquals(expectedRetries, retries.get());
            }
            expectThrows(NoSuchElementException.class, () -> itr.next());
            assertEquals(expectedRetries, retries.get());
        }
        {
            Iterator<TimeValue> itr = policy.iterator();
            itr.next();
            expectedRetries += 1;
            assertEquals(expectedRetries, retries.get());
        }
    }
}
