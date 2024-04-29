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

package io.reactivex.rxjava3.internal.operators.flowable;

import org.reactivestreams.Publisher;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.internal.operators.flowable.FlowableElementAtMaybe.ElementAtSubscriber;

/**
 * Emits the indexth element from a Publisher as a Maybe.
 *
 * @param <T> the element type of the source
 * @since 3.0.0
 */
public final class FlowableElementAtMaybePublisher<T> extends Maybe<T> {

    final Publisher<T> source;

    final long index;

    public FlowableElementAtMaybePublisher(Publisher<T> source, long index) {
        this.source = source;
        this.index = index;
    }

    @Override
    protected void subscribeActual(MaybeObserver<? super T> observer) {
        source.subscribe(new ElementAtSubscriber<>(observer, index));
    }
}
