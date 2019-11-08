/*
 * Copyright (C) 2015-2019 akha, a.k.a. Alexander Kharitonov
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

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreReflection;
import akha.yakhont.FlavorHelper.FlavorCommonRx;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import rx.Completable;
import rx.exceptions.OnErrorNotImplementedException;
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

    private static boolean                      sIsErrorHandlerDefined;

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
    @SuppressLint("RestrictedApi")
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public Rx(final boolean nullable, final boolean hasProducer) {
        super("Rx", !isErrorHandlerDefined());

        mIsNullable             = nullable;
        mHasProducer            = hasProducer;

        CoreLogger.logWarning("please consider using RxJava 2");
    }

    public static <D> RxSubscription getRxSubscriptionHandler(final BaseRx<D> rx) {
        return FlavorCommonRx.getRxSubscriptionHandler(rx);
    }

    /**
     * Sets Rx error handler to the empty one. Not advisable at all.
     *
     * @see RxJavaHooks#setOnError
     */
    @SuppressWarnings("unused")
    public static void setErrorHandlerEmpty() {
        setErrorHandlerHelper(false);
    }

    /**
     * Sets Rx error handler to the one which does logging only.
     *
     * @see RxJavaHooks#setOnError
     */
    public static void setErrorHandlerJustLog() {
        setErrorHandlerHelper(true);
    }

    private static void setErrorHandlerHelper(final boolean isLog) {
        //noinspection Convert2Lambda
        setErrorHandler(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (isLog) CoreLogger.log("RxJavaHooks OnError handler", throwable);
            }
        });
    }

    /**
     * Sets Rx error handler. Please refer to {@link RxJavaHooks#setOnError RxJavaHooks.setOnError()}
     * for more info.
     *
     * @param handler
     *        The Rx error handler to set
     *
     * @see RxJavaHooks#setOnError
     */
    @SuppressWarnings("WeakerAccess")
    public static void setErrorHandler(final Action1<Throwable> handler) {
        RxJavaHooks.setOnError(handler);
        sIsErrorHandlerDefined = true;
    }

    /**
     * Keeps using default Rx error handler.
     *
     * @see RxJavaHooks#setOnError
     */
    @SuppressWarnings("WeakerAccess")
    public static void setErrorHandlerDefault() {
        sIsErrorHandlerDefined = true;
    }

    /**
     * Checks whether the Rx error handler was set or not.
     *
     * @return  {@code true} if the Rx error handler was set, {@code false} otherwise
     *
     * @see RxJavaHooks#setOnError
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean isErrorHandlerDefined() {
        return sIsErrorHandlerDefined;
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
        final Subscriber<D> subscriberWrapper = new Subscriber<D>() {
            @Override
            public void onNext(final D result) {
                subscriber.onNext(result);
            }

            @Override
            public void onError(final Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onCompleted() {
                subscriber.onCompleted();
            }
        };
        if (mHasProducer) //noinspection Anonymous2MethodRef,Convert2Lambda
            subscriberWrapper.setProducer(new Producer() {
                @Override
                public void request(final long n) {
                    subscriber.request(n);
                }
            });
        return subscriberWrapper;
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
        mFlavorCommonRx.getRxSubscription().add(subscription);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void cancel(final Object subscription) {
        if (subscription instanceof Subscription) ((Subscription) subscription).unsubscribe();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static <D> Subscription handle(final Object handler, final Method method, final Object[] args,
                                          final CallbackRx<D> callback) throws Exception {
        if (handler == null) {
            CoreLogger.logError("handler == null");
            return null;
        }
        if (method == null) {
            CoreLogger.logError("method == null");
            return null;
        }
        final Class<?> returnType = method.getReturnType();

        if (Observable.class.isAssignableFrom(returnType)) {
            final Observable<D> result = CoreReflection.invokeSafe(handler, method, args);
            checkNull(result, "Observable == null");
            return handle(result, callback);
        }
        if (Single.class.isAssignableFrom(returnType)) {
            final Single<D> result = CoreReflection.invokeSafe(handler, method, args);
            checkNull(result, "Single == null");
            return handle(result, callback);
        }
        return null;
    }

    private static void checkNull(final Object result, final String msg) throws Exception {
        if (result == null) throw new Exception(msg);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static <D> Action1<D> getHandlerData(@NonNull final CallbackRx<D> callback) {
        //noinspection Anonymous2MethodRef,Convert2Lambda
        return new Action1<D>() {
            @Override
            public void call(final D data) {
                Utils.safeRun(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(data);
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "Rx - CallbackRx.onResult()";
                    }
                });
            }
        };
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static <D> Action1<Throwable> getHandlerError(@NonNull final CallbackRx<D> callback) {
        //noinspection Anonymous2MethodRef,Convert2Lambda
        return new Action1<Throwable>() {
            @Override
            public void call(final Throwable throwable) {
                Utils.safeRun(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(throwable);
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "Rx - CallbackRx.onError()";
                    }
                });
            }
        };
    }

    /**
     * Handles (subscribes) the {@link Observable} provided.
     *
     * @param observable
     *        The {@link Observable}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param isSafe
     *        {@code false} to throw {@link OnErrorNotImplementedException} in case of error,
     *        {@code true} otherwise;
     *        please refer to {@link #setSafeFlag} for more information
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Subscription}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Subscription handle(final Observable<D> observable, final CallbackRx<D> callback,
                                          final boolean isSafe) {
        if (observable == null) CoreLogger.logError("observable == null");
        if (callback   == null) CoreLogger.logError("callback == null");

        return observable == null || callback == null ? null: isSafe ?
                observable.subscribe(getHandlerData(callback), getHandlerError(callback)):
                observable.doOnError(getHandlerError(callback)).doOnNext(getHandlerData(callback)).subscribe();
    }

    /**
     * Handles (subscribes) the {@link Observable} provided.
     * The subscription behaviour defines by the {@link #getSafeFlag} method.
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
        return handle(observable, callback, getSafeFlag());
    }

    /**
     * Handles (subscribes) the {@link Single} provided.
     *
     * @param single
     *        The {@link Single}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param isSafe
     *        {@code false} to throw {@link OnErrorNotImplementedException} in case of error,
     *        {@code true} otherwise;
     *        please refer to {@link #setSafeFlag} for more information
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Subscription}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Subscription handle(final Single<D> single, final CallbackRx<D> callback,
                                          final boolean isSafe) {
        if (single   == null) CoreLogger.logError("single == null");
        if (callback == null) CoreLogger.logError("callback == null");

        return single == null || callback == null ? null: isSafe ?
                single.subscribe(getHandlerData(callback), getHandlerError(callback)):
                single.doOnError(getHandlerError(callback)).doOnSuccess(getHandlerData(callback)).subscribe();
    }

    /**
     * Handles (subscribes) the {@link Single} provided.
     * The subscription behaviour defines by the {@link #getSafeFlag} method.
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
        return handle(single, callback, getSafeFlag());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

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
        //noinspection Convert2Lambda
        return Observable.unsafeCreate(new Observable.OnSubscribe<D>() {
            @Override
            public void call(final Subscriber<? super D> subscriber) {
                if (!checkNullSubscriber(subscriber)) return;

                register(new CallbackRx<D>() {
                    @Override
                    public void onResult(final D result) {
                        Rx.this.onResult(subscriber, result);
                    }

                    @Override
                    public void onError(final Throwable throwable) {
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
     * @param <D>
     *        The data type
     *
     * @return  The converted subscriber
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> SafeSubscriber<D> getSafeSubscriber(@NonNull final Subscriber<D> subscriber) {
        return subscriber instanceof SafeSubscriber<?> ?
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

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Represents a group of Subscriptions that are unsubscribed together.
     * Unlike {@link CompositeSubscription}, it's reusable.
     */
    public static class RxSubscription {

        private CompositeSubscription           mCompositeSubscription  = createContainer();
        private final Object                    mLock                   = new Object();

        private static CompositeSubscription createContainer() {
            return new CompositeSubscription();
        }

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
        @SuppressWarnings("WeakerAccess")
        public void add(final Subscription subscription) {
            if (subscription == null)
                CoreLogger.logError("subscription is null");
            else {
                synchronized (mLock) {
                    mCompositeSubscription.add(subscription);
                }
            }
        }

        /**
         * Checks whether the given container is empty or not.
         *
         * @return  {@code true} if container is not empty, {@code false} otherwise
         */
        public boolean notEmpty() {
            synchronized (mLock) {
                return mCompositeSubscription.hasSubscriptions();
            }
        }

        /**
         * Unsubscribes all added {@link Subscription Subscriptions}.
         */
        public void unsubscribe() {
            synchronized (mLock) {
                if (notEmpty()) {
                    CoreLogger.logWarning("Rx unsubscribe");
                    mCompositeSubscription.unsubscribe();

                    // not usable after unsubscribe, so creating the new one
                    mCompositeSubscription = createContainer();
                }
                else
                    CoreLogger.log("CompositeSubscription.hasSubscriptions() returns false");
            }
        }
    }
}
