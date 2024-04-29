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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.functions.Action;
import org.junit.*;
import org.mockito.InOrder;
import org.reactivestreams.*;

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.exceptions.*;
import io.reactivex.rxjava3.internal.subscriptions.BooleanSubscription;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.testsupport.TestHelper;

public class FlowableThrottleFirstTest extends RxJavaTest {

    private TestScheduler scheduler;
    private Scheduler.Worker innerScheduler;
    private Subscriber<String> subscriber;

    @Before
    public void before() {
        scheduler = new TestScheduler();
        innerScheduler = scheduler.createWorker();
        subscriber = TestHelper.mockSubscriber();
    }

    @Test
    public void throttlingWithDropCallbackCrashes() throws Throwable {
        Flowable<String> source = Flowable.unsafeCreate(new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new BooleanSubscription());
                publishNext(subscriber, 100, "one");    
                publishNext(subscriber, 300, "two");    
                publishNext(subscriber, 900, "three");   
                publishNext(subscriber, 905, "four");   
                publishCompleted(subscriber, 1000);     
            }
        });

        Action whenDisposed = mock(Action.class);

        Flowable<String> sampled = source
                .doOnCancel(whenDisposed)
                .throttleFirst(400, TimeUnit.MILLISECONDS, scheduler, e ->  {
            if ("two".equals(e)) {
                throw new TestException("forced");
            }
        });
        sampled.subscribe(subscriber);

        InOrder inOrder = inOrder(subscriber);

        scheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
        inOrder.verify(subscriber, times(1)).onNext("one");
        inOrder.verify(subscriber, times(1)).onError(any(TestException.class));
        inOrder.verify(subscriber, times(0)).onNext("two");
        inOrder.verify(subscriber, times(0)).onNext("three");
        inOrder.verify(subscriber, times(0)).onNext("four");
        inOrder.verify(subscriber, times(0)).onComplete();
        inOrder.verifyNoMoreInteractions();
        verify(whenDisposed).run();
    }

    @Test
    public void throttlingWithDropCallback() {
        Flowable<String> source = Flowable.unsafeCreate(new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new BooleanSubscription());
                publishNext(subscriber, 100, "one");    
                publishNext(subscriber, 300, "two");    
                publishNext(subscriber, 900, "three");   
                publishNext(subscriber, 905, "four");   
                publishCompleted(subscriber, 1000);     
            }
        });

        Observer<Object> dropCallbackObserver = TestHelper.mockObserver();
        Flowable<String> sampled = source.throttleFirst(400, TimeUnit.MILLISECONDS, scheduler, dropCallbackObserver::onNext);
        sampled.subscribe(subscriber);

        InOrder inOrder = inOrder(subscriber);
        InOrder dropCallbackOrder = inOrder(dropCallbackObserver);

        scheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
        inOrder.verify(subscriber, times(1)).onNext("one");
        inOrder.verify(subscriber, times(0)).onNext("two");
        dropCallbackOrder.verify(dropCallbackObserver, times(1)).onNext("two");
        inOrder.verify(subscriber, times(1)).onNext("three");
        inOrder.verify(subscriber, times(0)).onNext("four");
        dropCallbackOrder.verify(dropCallbackObserver, times(1)).onNext("four");
        inOrder.verify(subscriber, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
        dropCallbackOrder.verifyNoMoreInteractions();
    }

    @Test
    public void throttlingWithCompleted() {
        Flowable<String> source = Flowable.unsafeCreate(new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new BooleanSubscription());
                publishNext(subscriber, 100, "one");    
                publishNext(subscriber, 300, "two");    
                publishNext(subscriber, 900, "three");   
                publishNext(subscriber, 905, "four");   
                publishCompleted(subscriber, 1000);     
            }
        });

        Flowable<String> sampled = source.throttleFirst(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(subscriber);

        InOrder inOrder = inOrder(subscriber);

        scheduler.advanceTimeTo(1000, TimeUnit.MILLISECONDS);
        inOrder.verify(subscriber, times(1)).onNext("one");
        inOrder.verify(subscriber, times(0)).onNext("two");
        inOrder.verify(subscriber, times(1)).onNext("three");
        inOrder.verify(subscriber, times(0)).onNext("four");
        inOrder.verify(subscriber, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void throttlingWithError() {
        Flowable<String> source = Flowable.unsafeCreate(new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new BooleanSubscription());
                Exception error = new TestException();
                publishNext(subscriber, 100, "one");    
                publishNext(subscriber, 200, "two");    
                publishError(subscriber, 300, error);   
            }
        });

        Flowable<String> sampled = source.throttleFirst(400, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(subscriber);

        InOrder inOrder = inOrder(subscriber);

        scheduler.advanceTimeTo(400, TimeUnit.MILLISECONDS);
        inOrder.verify(subscriber).onNext("one");
        inOrder.verify(subscriber).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
    }

    private <T> void publishCompleted(final Subscriber<T> subscriber, long delay) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                subscriber.onComplete();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private <T> void publishError(final Subscriber<T> subscriber, long delay, final Exception error) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                subscriber.onError(error);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private <T> void publishNext(final Subscriber<T> subscriber, long delay, final T value) {
        innerScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                subscriber.onNext(value);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Test
    public void throttle() {
        Subscriber<Integer> subscriber = TestHelper.mockSubscriber();
        TestScheduler s = new TestScheduler();
        PublishProcessor<Integer> o = PublishProcessor.create();
        o.throttleFirst(500, TimeUnit.MILLISECONDS, s).subscribe(subscriber);

        s.advanceTimeTo(0, TimeUnit.MILLISECONDS);
        o.onNext(1); 
        o.onNext(2); 
        s.advanceTimeTo(501, TimeUnit.MILLISECONDS);
        o.onNext(3); 
        s.advanceTimeTo(600, TimeUnit.MILLISECONDS);
        o.onNext(4); 
        s.advanceTimeTo(700, TimeUnit.MILLISECONDS);
        o.onNext(5); 
        o.onNext(6); 
        s.advanceTimeTo(1001, TimeUnit.MILLISECONDS);
        o.onNext(7); 
        s.advanceTimeTo(1501, TimeUnit.MILLISECONDS);
        o.onComplete();

        InOrder inOrder = inOrder(subscriber);
        inOrder.verify(subscriber).onNext(1);
        inOrder.verify(subscriber).onNext(3);
        inOrder.verify(subscriber).onNext(7);
        inOrder.verify(subscriber).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void throttleFirstDefaultScheduler() {
        Flowable.just(1).throttleFirst(100, TimeUnit.MILLISECONDS)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(1);
    }

    @Test
    public void dispose() {
        TestHelper.checkDisposed(Flowable.just(1).throttleFirst(1, TimeUnit.DAYS));
    }

    @Test
    public void badSource() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            new Flowable<Integer>() {
                @Override
                protected void subscribeActual(Subscriber<? super Integer> subscriber) {
                    subscriber.onSubscribe(new BooleanSubscription());
                    subscriber.onNext(1);
                    subscriber.onNext(2);
                    subscriber.onComplete();
                    subscriber.onNext(3);
                    subscriber.onError(new TestException());
                    subscriber.onComplete();
                }
            }
            .throttleFirst(1, TimeUnit.DAYS)
            .test()
            .assertResult(1);

            TestHelper.assertUndeliverable(errors, 0, TestException.class);
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    public void backpressureNoRequest() {
        Flowable.range(1, 3)
        .throttleFirst(1, TimeUnit.MINUTES)
        .test(0L)
        .assertFailure(MissingBackpressureException.class);
    }

    @Test
    public void badRequest() {
        TestHelper.assertBadRequestReported(Flowable.never().throttleFirst(1, TimeUnit.MINUTES));
    }

    @Test
    public void doubleOnSubscribe() {
        TestHelper.checkDoubleOnSubscribeFlowable(f -> f.throttleFirst(1, TimeUnit.MINUTES));
    }
}
