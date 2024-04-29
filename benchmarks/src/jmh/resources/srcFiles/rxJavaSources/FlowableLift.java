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

import org.reactivestreams.Subscriber;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;

/**
 * Allows lifting operators into a chain of Publishers.
 *
 * <p>By having a concrete Publisher as lift, operator fusing can now identify
 * both the source and the operation inside it via casting, unlike the lambda version of this.
 *
 * @param <T> the upstream value type
 * @param <R> the downstream parameter type
 */
public final class FlowableLift<R, T> extends AbstractFlowableWithUpstream<T, R> {
    /** The actual operator. */
    final FlowableOperator<? extends R, ? super T> operator;

    public FlowableLift(Flowable<T> source, FlowableOperator<? extends R, ? super T> operator) {
        super(source);
        this.operator = operator;
    }

    @Override
    public void subscribeActual(Subscriber<? super R> s) {
        try {
            Subscriber<? super T> st = operator.apply(s);

            if (st == null) {
                throw new NullPointerException("Operator " + operator + " returned a null Subscriber");
            }

            source.subscribe(st);
        } catch (NullPointerException e) { 
            throw e;
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            RxJavaPlugins.onError(e);

            NullPointerException npe = new NullPointerException("Actually not, but can't throw other exceptions due to RS");
            npe.initCause(e);
            throw npe;
        }
    }
}
