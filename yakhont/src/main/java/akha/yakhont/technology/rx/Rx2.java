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

import akha.yakhont.CoreLogger;
import akha.yakhont.CoreReflection;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.BaseRx.SubscriberRx;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.functions.Functions;
import io.reactivex.internal.subscriptions.EmptySubscription;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subscribers.DisposableSubscriber;
import io.reactivex.subscribers.SafeSubscriber;

/**
 * The component to work with {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>}.
 *
 * @param <D>
 *        The data type
 *
 * @see CommonRx
 *
 * @author akha
 */
public class Rx2<D> extends CommonRx<D> {

    private final Disposable                    mDisposable;

    private static boolean                      sIsErrorHandlerDefined;

    /**
     * Initialises a newly created {@code Rx2} object.
     */
    public Rx2() {
        this(null);
    }

    /**
     * Initialises a newly created {@code Rx2} object.
     *
     * @param disposable
     *        The optional {@link Disposable}
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public Rx2(final Disposable disposable) {
        super("Rx2", !isErrorHandlerDefined());

        mDisposable = disposable;
    }

    /**
     * Sets Rx error handler to the empty one. Not advisable at all.
     *
     * @see RxJavaPlugins#setErrorHandler
     */
    @SuppressWarnings("unused")
    public static void setErrorHandlerEmpty() {
        setErrorHandler(Functions.emptyConsumer());
    }

    /**
     * Sets Rx error handler to the one which does logging only. For more info please refer to
     * {@link <a href="https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling">Rx error handling</a>}.
     *
     * @see RxJavaPlugins#setErrorHandler
     */
    public static void setErrorHandlerJustLog() {
        //noinspection Convert2Lambda
        setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(@io.reactivex.annotations.NonNull Throwable throwable) {
                CoreLogger.log("RxJavaPlugins Error handler", throwable);
            }
        });
    }

    /**
     * Sets Rx error handler. Please refer to
     * {@link RxJavaPlugins#setErrorHandler RxJavaPlugins.setErrorHandler()} for more info.
     *
     * @param handler
     *        The Rx error handler to set
     *
     * @see RxJavaPlugins#setErrorHandler
     */
    @SuppressWarnings("WeakerAccess")
    public static void setErrorHandler(final Consumer<? super Throwable> handler) {
        RxJavaPlugins.setErrorHandler(handler);
        sIsErrorHandlerDefined = true;
    }

    /**
     * Keeps using default Rx error handler.
     *
     * @see RxJavaPlugins#setErrorHandler
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
     * @see RxJavaPlugins#setErrorHandler
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
        return false;
    }

    /**
     * Subscribes {@link SubscriberRx} to receive Rx push-based notifications.
     *
     * @param subscriber
     *        The {@link SubscriberRx subscriber}
     *
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public Disposable subscribe(@NonNull final SubscriberRx<D> subscriber) {
        return isSingle() ? createSingle().subscribeWith(new DisposableSingleObserver<D>() {
            @Override
            public void onSuccess(final D result) {
                subscriber.onNext(result);
            }

            @Override
            public void onError(final Throwable throwable) {
                subscriber.onError(throwable);
            }
        }): createObservable().subscribeWith(new DisposableObserver<D>() {
            @Override
            public void onNext(final D result) {
                subscriber.onNext(result);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onCompleted();
            }
        });
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected void subscribeRx(@NonNull final SubscriberRx<D> subscriber) {
        add(subscribe(subscriber));
    }

    /**
     * Adds {@link Disposable} to the registered disposables list.
     *
     * @param disposable
     *        The {@link Disposable} to add
     *
     * @return  {@code true} if Disposable was successfully added, {@code false} otherwise
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    public boolean add(final Disposable disposable) {
        return mRx2Disposable.add(disposable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void cancel(final Object disposable) {
        if (disposable instanceof Disposable) ((Disposable) disposable).dispose();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static <D> Disposable handle(final Object handler, final Method method, final Object[] args,
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

        if (Flowable.class.isAssignableFrom(returnType)) {
            final Flowable<D> result = CoreReflection.invokeSafe(handler, method, args);
            checkNull(result, "Flowable == null");
            return handle(result, callback);
        }
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
        if (Maybe.class.isAssignableFrom(returnType)) {
            final Maybe<D> result = CoreReflection.invokeSafe(handler, method, args);
            checkNull(result, "Maybe == null");
            return handle(result, callback);
        }
        return null;
    }

    private static void checkNull(final Object result, final String msg) throws Exception {
        if (result == null) throw new Exception(msg);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static <D> Consumer<D> getHandlerData(@NonNull final CallbackRx<D> callback) {
        //noinspection Anonymous2MethodRef,Convert2Lambda
        return new Consumer<D>() {
            @Override
            public void accept(@io.reactivex.annotations.NonNull D data) {
                callback.onResult(data);
            }
        };
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static <D> Consumer<Throwable> getHandlerError(@NonNull final CallbackRx<D> callback) {
        //noinspection Anonymous2MethodRef,Convert2Lambda
        return new Consumer<Throwable>() {
            @Override
            public void accept(@io.reactivex.annotations.NonNull Throwable throwable) {
                callback.onError(throwable);
            }
        };
    }

    /**
     * Handles (subscribes) the {@link Flowable} provided.
     *
     * @param flowable
     *        The {@link Flowable}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param isSafe
     *        please refer to {@link #setSafeFlag} for more information
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Flowable<D> flowable, final CallbackRx<D> callback,
                                        final boolean isSafe) {
        if (flowable == null) CoreLogger.logError("flowable == null");
        if (callback == null) CoreLogger.logError("callback == null");

        return flowable == null || callback == null ? null: isSafe ?
                flowable.subscribe(getHandlerData(callback), getHandlerError(callback)):
                flowable.doOnError(getHandlerError(callback)).doOnNext(getHandlerData(callback)).subscribe();
    }

    /**
     * Handles (subscribes) the {@link Flowable} provided.
     * The subscription behaviour defines by the {@link #getSafeFlag} method.
     *
     * @param flowable
     *        The {@link Flowable}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Flowable<D> flowable, final CallbackRx<D> callback) {
        return handle(flowable, callback, getSafeFlag());
    }

    /**
     * Handles (subscribes) the {@link Maybe} provided.
     *
     * @param maybe
     *        The {@link Maybe}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param isSafe
     *        please refer to {@link #setSafeFlag} for more information
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Maybe<D> maybe, final CallbackRx<D> callback,
                                        final boolean isSafe) {
        if (maybe    == null) CoreLogger.logError("maybe == null");
        if (callback == null) CoreLogger.logError("callback == null");

        return maybe == null || callback == null ? null: isSafe ?
                maybe.subscribe(getHandlerData(callback), getHandlerError(callback)):
                maybe.doOnError(getHandlerError(callback)).doOnSuccess(getHandlerData(callback)).subscribe();
    }

    /**
     * Handles (subscribes) the {@link Maybe} provided.
     * The subscription behaviour defines by the {@link #getSafeFlag} method.
     *
     * @param maybe
     *        The {@link Maybe}
     *
     * @param callback
     *        The {@link CallbackRx}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Maybe<D> maybe, final CallbackRx<D> callback) {
        return handle(maybe, callback, getSafeFlag());
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
     *        please refer to {@link #setSafeFlag} for more information
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Observable<D> observable, final CallbackRx<D> callback,
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
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Observable<D> observable, final CallbackRx<D> callback) {
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
     *        please refer to {@link #setSafeFlag} for more information
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Single<D> single, final CallbackRx<D> callback,
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
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Single<D> single, final CallbackRx<D> callback) {
        return handle(single, callback, getSafeFlag());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkNullObserver(final Observer<? super D> observer) {
        if (observer == null) CoreLogger.logError("observer is null");
        return observer != null;
    }

    /**
     * Creates the {@link Observable}.
     *
     * @return  The {@link Observable}
     */
    @NonNull
    @SuppressWarnings("WeakerAccess")
    public Observable<D> createObservable() {
        return new Observable<D>() {
            @Override
            protected void subscribeActual(final Observer<? super D> observer) {
                if (!checkNullObserver(observer)) return;

                if (mDisposable != null) observer.onSubscribe(mDisposable);

                register(new CallbackRx<D>() {
                    @Override
                    public void onResult(final D result) {
                        Rx2.this.onResult(observer, result);
                    }

                    @Override
                    public void onError(final Throwable throwable) {
                        Rx2.this.onError(observer, throwable);
                    }
                });
            }
        };
    }

    /**
     * Converts {@link Observer} to {@link SafeSubscriber}.
     *
     * @param observer
     *        The {code Observer} to convert
     *
     * @return  The converted observer
     */
    @SuppressWarnings("WeakerAccess")
    public SafeSubscriber<? super D> getSafeSubscriber(final Observer<? super D> observer) {
        if (!checkNullObserver(observer)) return null;

        final SafeSubscriber<? super D> safeSubscriber = new SafeSubscriber<>(new DisposableSubscriber<D>() {
            @Override
            public void onNext(final D result) {
                observer.onNext(result);
            }

            @Override
            public void onError(final Throwable throwable) {
                observer.onError(throwable);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        });

        // well, it's a hack, but it seems it's better than copy / paste logic from SafeSubscriber to here
        safeSubscriber.onSubscribe(EmptySubscription.INSTANCE);

        return safeSubscriber;
    }

    /**
     * Implementation of {@link CallbackRx#onResult CallbackRx.onResult()}.
     *
     * @param observer
     *        The {@code Observer}
     *
     * @param result
     *        The item emitted by the {@code Observable}
     */
    @SuppressWarnings("WeakerAccess")
    protected void onResult(final Observer<? super D> observer, final D result) {
        if (!checkNullObserver(observer)) return;

        if (result == null) {
            onError(observer, new Exception("Rx2: result is null"));
            return;
        }

        final SafeSubscriber<? super D> safeSubscriber = getSafeSubscriber(observer);
        safeSubscriber.onNext(result);

        if (!isSingle()) return;
        safeSubscriber.onComplete();
    }

    /**
     * Implementation of {@link CallbackRx#onError CallbackRx.onError()}.
     *
     * @param observer
     *        The {@code Observer}
     *
     * @param throwable
     *        The exception encountered by the {@code Observable}
     */
    @SuppressWarnings("WeakerAccess")
    protected void onError(final Observer<? super D> observer, final Throwable throwable) {
        CoreLogger.log("Rx2 failed", throwable);

        if (!checkNullObserver(observer)) return;

        final SafeSubscriber<? super D> safeSubscriber = getSafeSubscriber(observer);
        safeSubscriber.onError(throwable);
    }

    /**
     * Creates the {@link Flowable}.
     *
     * @return  The {@link Flowable}
     */
    @NonNull
    @SuppressWarnings("unused")
    public Flowable<D> createFlowable() {
        return createFlowable(BackpressureStrategy.LATEST);
    }

    /**
     * Creates the {@link Flowable}.
     *
     * @param strategy
     *        The {@link BackpressureStrategy strategy}
     *
     * @return  The {@link Flowable}
     */
    @NonNull
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public Flowable<D> createFlowable(@NonNull final BackpressureStrategy strategy) {
        return createObservable().toFlowable(strategy);
    }

    /**
     * Creates the {@link Single}.
     *
     * @return  The {@link Single}
     */
    @NonNull
    @SuppressWarnings("WeakerAccess")
    public Single<D> createSingle() {
        checkSingle();
        return createObservable().singleOrError();
    }

    /**
     * Creates the {@link Maybe}.
     *
     * @return  The {@link Maybe}
     */
    @NonNull
    @SuppressWarnings("unused")
    public Maybe<D> createMaybe() {
        checkSingle();
        return createObservable().singleElement();
    }

    /**
     * Creates the {@link Completable}.
     *
     * @return  The {@link Completable}
     */
    @NonNull
    @SuppressWarnings("unused")
    public Completable createCompletable() {
        return createObservable().ignoreElements();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A disposable container that can hold onto multiple other disposables.
     * Unlike {@link CompositeDisposable}, it's reusable.
     */
    public static class Rx2Disposable {

        private CompositeDisposable             mCompositeDisposable    = createContainer();
        private final Object                    mLock                   = new Object();

        private static CompositeDisposable createContainer() {
            return new CompositeDisposable();
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "UnusedReturnValue"})
        public boolean add(final Object result) {
            if (result instanceof Disposable)
                return add((Disposable) result);
            else {
                handleUnknownResult(result);
                return false;
            }
        }

        /**
         * Adds a new {@link Disposable}.
         *
         * @param disposable
         *        The {@link Disposable} to add
         *
         * @return  {@code true} if Disposable was successfully added, {@code false} otherwise
         */
        @SuppressWarnings("WeakerAccess")
        public boolean add(final Disposable disposable) {
            if (disposable == null) {
                CoreLogger.logError("Disposable is null");
                return false;
            }
            synchronized (mLock) {
                boolean result = mCompositeDisposable.add(disposable);
                if (!result) {
                    if (notEmpty())
                        CoreLogger.logError("can not add Disposable to not empty container");
                    else {
                        mCompositeDisposable = createContainer();
                        result = mCompositeDisposable.add(disposable);
                        if (!result) CoreLogger.logError("can not add Disposable to container");
                    }
                }
                return result;
            }
        }

        /**
         * Checks whether the given container is empty or not.
         *
         * @return  {@code true} if container is not empty, {@code false} otherwise
         */
        public boolean notEmpty() {
            synchronized (mLock) {
                return mCompositeDisposable.size() > 0;
            }
        }

        /**
         * Disposes all added {@link Disposable Disposables}.
         */
        public void unsubscribe() {
            synchronized (mLock) {
                if (!notEmpty())
                    CoreLogger.log("CompositeDisposable.size() returns 0");
                else if (mCompositeDisposable.isDisposed())
                    CoreLogger.log("CompositeDisposable.isDisposed() returns true");
                else {
                    CoreLogger.logWarning("Rx2 dispose, size == " + mCompositeDisposable.size());
                    mCompositeDisposable.dispose();

                    // not usable after disposing, so creating the new one
                    mCompositeDisposable = createContainer();
                }
            }
        }
    }
}
