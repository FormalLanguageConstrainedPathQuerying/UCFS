/*
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.rxjava3.functions;

/**
 * A functional interface (callback) that consumes a primitive long value.
 */
@FunctionalInterface
public interface LongConsumer {
    /**
     * Consume a primitive long input.
     * @param t the primitive long value
     * @throws Throwable if the implementation wishes to throw any type of exception
     */
    void accept(long t) throws Throwable;
}
