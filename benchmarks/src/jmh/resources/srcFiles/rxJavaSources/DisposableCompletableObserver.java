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

package io.reactivex.rxjava3.observers;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.internal.disposables.DisposableHelper;
import io.reactivex.rxjava3.internal.util.EndConsumerHelper;

/**
 * An abstract {@link CompletableObserver} that allows asynchronous cancellation by implementing Disposable.
 *
 * <p>All pre-implemented final methods are thread-safe.
 *
 * <p>Like all other consumers, {@code DisposableCompletableObserver} can be subscribed only once.
 * Any subsequent attempt to subscribe it to a new source will yield an
 * {@link IllegalStateException} with message {@code "It is not allowed to subscribe with a(n) <class name> multiple times."}.
 *
 * <p>Implementation of {@link #onStart()}, {@link #onError(Throwable)} and
 * {@link #onComplete()} are not allowed to throw any unchecked exceptions.
 *
 * <p>Example<pre><code>
 * Disposable d =
 *     Completable.complete().delay(1, TimeUnit.SECONDS)
 *     .subscribeWith(new DisposableMaybeObserver&lt;Integer&gt;() {
 *         &#64;Override public void onStart() {
 *             System.out.println("Start!");
 *         }
 *         &#64;Override public void onError(Throwable t) {
 *             t.printStackTrace();
 *         }
 *         &#64;Override public void onComplete() {
 *             System.out.println("Done!");
 *         }
 *     });
 * 
 * d.dispose();
 * </code></pre>
 */
public abstract class DisposableCompletableObserver implements CompletableObserver, Disposable {

    final AtomicReference<Disposable> upstream = new AtomicReference<>();

    @Override
    public final void onSubscribe(@NonNull Disposable d) {
        if (EndConsumerHelper.setOnce(this.upstream, d, getClass())) {
            onStart();
        }
    }

    /**
     * Called once the single upstream {@link Disposable} is set via {@link #onSubscribe(Disposable)}.
     */
    protected void onStart() {
    }

    @Override
    public final boolean isDisposed() {
        return upstream.get() == DisposableHelper.DISPOSED;
    }

    @Override
    public final void dispose() {
        DisposableHelper.dispose(upstream);
    }
}
