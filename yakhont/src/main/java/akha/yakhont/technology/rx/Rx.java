/*
 * Copyright (C) 2015-2017 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont.technology.rx;

import akha.yakhont.CoreLogger;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import android.support.annotation.NonNull;

import rx.Completable;
import rx.Observable;
import rx.Producer;
import rx.Single;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.observers.SafeSubscriber;
import rx.plugins.RxJavaHooks;
import rx.subscriptions.CompositeSubscription;

/**
 * The component to work with {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}.
 *
 * @param <D>
 *        The data type
 *
 * @see CommonRx
 *
 * @author akha
 */
public class Rx<D> extends CommonRx<D> {

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final boolean                     mIsNullable;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final boolean                     mHasProducer;

    /**
     * Initialises a newly created {@code Rx} object.
     */
    public Rx() {
        this(false, false);
    }

    /**
     * Initialises a newly created {@code Rx} object.
     *
     * @param nullable
     *        {@code true} if given {@code Rx} object should support nulls, {@code false} otherwise
     *
     * @param hasProducer
     *        {@code true} if given {@code Rx} object has {@link Producer}, {@code false} otherwise
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public Rx(final boolean nullable, final boolean hasProducer) {
        mIsNullable             = nullable;
        mHasProducer            = hasProducer;

        CoreLogger.logWarning("please consider using RxJava 2");
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void setErrorHandlerEmpty() {
        setErrorHandlerHelper(false);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void setErrorHandlerJustLog() {
        setErrorHandlerHelper(true);
    }

    private static void setErrorHandlerHelper(final boolean isLog) {
        setErrorHandler(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (isLog) CoreLogger.log("Rx error handler", throwable);
            }
        });
    }

    /**
     * Sets Rx error handler. Please refer to {@link RxJavaHooks#setOnError RxJavaHooks.setOnError()} for more info.
     *
     * @param handler
     *        The Rx error handler to set
     */
    @SuppressWarnings("WeakerAccess")
    public static void setErrorHandler(final Action1<Throwable> handler) {
        RxJavaHooks.setOnError(handler);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected boolean isNullable() {
        return mIsNullable;
    }

    /**
     * Converts {@link SubscriberRx} to {@link Subscriber}.
     *
     * @param subscriber
     *        The {code SubscriberRx} to convert
     *
     * @return  The converted subscriber
     */
    @SuppressWarnings("WeakerAccess")
    public Subscriber<D> getSubscriber(final SubscriberRx<D> subscriber) {
        if (subscriber == null) {
            CoreLogger.logError("subscriber is null");
            return null;
        }
        final Subscriber<D> s = new Subscriber<D>() {
            @Override
            public void onNext(final D result) {
                subscriber.onNext(result);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onCompleted() {
                subscriber.onCompleted();
            }
        };
        if (mHasProducer) s.setProducer(new Producer() {
            @Override
            public void request(long n) {
                subscriber.request(n);
            }
        });
        return s;
    }

    /**
     * Subscribes {@link Subscriber} to receive Rx push-based notifications.
     *
     * @param subscriber
     *        The {@link Subscriber subscriber}
     *
     * @return  The {@link Subscription}
     */
    @SuppressWarnings("WeakerAccess")
    public Subscription subscribe(@NonNull final Subscriber<D> subscriber) {
        return createObservable().subscribe(subscriber);
    }

    /**
     * Subscribes {@link SubscriberRx} to receive Rx push-based notifications.
     *
     * @param subscriber
     *        The {@link SubscriberRx subscriber}
     *
     * @return  The {@link Subscription}
     */
    @SuppressWarnings("WeakerAccess")
    public Subscription subscribe(@NonNull final SubscriberRx<D> subscriber) {
        return subscribe(getSubscriber(subscriber));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected void subscribeRx(@NonNull final SubscriberRx<D> subscriber) {
        add(subscribe(subscriber));
    }

    /**
     * Adds {@link Subscription} to the registered subscriptions list.
     *
     * @param subscription
     *        The {@link Subscription} to add
     */
    @SuppressWarnings("WeakerAccess")
    public void add(final Subscription subscription) {
        mRxSubscription.add(subscription);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unchecked", "WeakerAccess"})
    public static <D> Subscription handle(final Object result, final CallbackRx<D> callback) {
        return result instanceof Observable ? handle((Observable<D>) result, callback):
               result instanceof Single     ? handle((Single<D>    ) result, callback): null;
    }

    /**
     * Handles the {@link Observable} provided.
     *
     * @param observable
     *        The {@link Observable}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Subscription}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Subscription handle(final Observable<D> observable, final CallbackRx<D> callback) {
        if (observable == null) CoreLogger.logError("observable == null");
        if (callback   == null) CoreLogger.logError("callback == null");

        return observable == null || callback == null ? null: observable
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        callback.onError(throwable);
                    }
                })
                .doOnNext(new Action1<D>() {
                    @Override
                    public void call(D data) {
                        callback.onResult(data);
                    }
                })
                .subscribe();
    }

    /**
     * Handles the {@link Single} provided.
     *
     * @param single
     *        The {@link Single}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Subscription}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Subscription handle(final Single<D> single, final CallbackRx<D> callback) {
        if (single   == null) CoreLogger.logError("single == null");
        if (callback == null) CoreLogger.logError("callback == null");

        return single == null || callback == null ? null: single
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        callback.onError(throwable);
                    }
                })
                .doOnSuccess(new Action1<D>() {
                    @Override
                    public void call(D data) {
                        callback.onResult(data);
                    }
                })
                .subscribe();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkNullSubscriber(final Subscriber<? super D> subscriber) {
        if (subscriber == null) CoreLogger.logError("subscriber is null");
        return subscriber != null;
    }

    /**
     * Creates the {@link Observable}.
     *
     * @return  The {@link Observable}
     */
    @NonNull
    @SuppressWarnings("WeakerAccess")
    public Observable<D> createObservable() {
        return Observable.unsafeCreate(new Observable.OnSubscribe<D>() {
            @Override
            public void call(final Subscriber<? super D> subscriber) {
                if (!checkNullSubscriber(subscriber)) return;

                register(new CallbackRx<D>() {
                    @Override
                    public void onResult(D result) {
                        Rx.this.onResult(subscriber, result);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Rx.this.onError(subscriber, throwable);
                    }
                });
            }
        });
    }

    /**
     * Converts {@link Subscriber} to {@link SafeSubscriber}.
     *
     * @param subscriber
     *        The {code Subscriber} to convert
     *
     * @return  The converted subscriber
     */
    @SuppressWarnings({"WeakerAccess", "unchecked"})
    public SafeSubscriber<? super D> getSafeSubscriber(final Subscriber<? super D> subscriber) {
        return subscriber instanceof SafeSubscriber ?
                (SafeSubscriber<D>) subscriber: new SafeSubscriber<>(subscriber);
    }

    /**
     * Implementation of {@link CallbackRx#onResult CallbackRx.onResult()}.
     *
     * @param subscriber
     *        The {@code Subscriber}
     *
     * @param result
     *        The item emitted by the {@code Observable}
     */
    @SuppressWarnings("WeakerAccess")
    protected void onResult(final Subscriber<? super D> subscriber, final D result) {
        if (!checkNullSubscriber(subscriber)) return;

        if (!mIsNullable && result == null) {
            onError(subscriber, new Exception("Rx: result is null"));
            return;
        }

        final SafeSubscriber<? super D> safeSubscriber = getSafeSubscriber(subscriber);
        safeSubscriber.onNext(result);

        if (!isSingle()) return;
        safeSubscriber.onCompleted();
    }

    /**
     * Implementation of {@link CallbackRx#onError CallbackRx.onError()}.
     *
     * @param subscriber
     *        The {@code Subscriber}
     *
     * @param throwable
     *        The exception encountered by the {@code Observable}
     */
    @SuppressWarnings("WeakerAccess")
    protected void onError(final Subscriber<? super D> subscriber, final Throwable throwable) {
        CoreLogger.log("Rx failed", throwable);

        if (!checkNullSubscriber(subscriber)) return;

        final SafeSubscriber<? super D> safeSubscriber = getSafeSubscriber(subscriber);
        safeSubscriber.onError(throwable);
    }

    /**
     * Creates the {@link Single}.
     *
     * @return  The {@link Single}
     */
    @NonNull
    @SuppressWarnings("unused")
    public Single<D> createSingle() {
        checkSingle();
        return createObservable().toSingle();
    }

    /**
     * Creates the {@link Completable}.
     *
     * @return  The {@link Completable}
     */
    @NonNull
    @SuppressWarnings("unused")
    public Completable createCompletable() {
        return createObservable().toCompletable();
    }

    /**
     * Represents a group of Subscriptions that are unsubscribed together.
     */
    public static class RxSubscription {

        private final CompositeSubscription     mCompositeSubscription = new CompositeSubscription();

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public void add(final Object result) {
            if (result instanceof Subscription)
                add((Subscription) result);
            else
                handleUnknownResult(result);
        }

        /**
         * Adds a new {@link Subscription}.
         *
         * @param subscription
         *        The {@link Subscription} to add
         */
        public void add(final Subscription subscription) {
            if (subscription == null)
                CoreLogger.logError("subscription is null");
            else
                mCompositeSubscription.add(subscription);
        }

        /**
         * Unsubscribes all added {@link Subscription Subscriptions}.
         */
        public void unsubscribe() {
            if (mCompositeSubscription.hasSubscriptions()) {
                CoreLogger.logWarning("Rx unsubscribe");
                mCompositeSubscription.unsubscribe();
            }
            else
                CoreLogger.log("CompositeSubscription.hasSubscriptions() returns false");
        }
    }
}
