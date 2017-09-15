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
        mDisposable = disposable;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void setErrorHandlerEmpty() {
        setErrorHandler(Functions.<Throwable>emptyConsumer());
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void setErrorHandlerJustLog() {
        setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(@io.reactivex.annotations.NonNull Throwable throwable) throws Exception {
                CoreLogger.log("Rx2 error handler", throwable);
            }
        });
    }

    /**
     * Sets Rx error handler. Please refer to {@link RxJavaPlugins#setErrorHandler RxJavaPlugins.setErrorHandler()} for more info.
     *
     * @param handler
     *        The Rx error handler to set
     */
    @SuppressWarnings("WeakerAccess")
    public static void setErrorHandler(final Consumer<? super Throwable> handler) {
        RxJavaPlugins.setErrorHandler(handler);
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
     */
    @SuppressWarnings("WeakerAccess")
    public void add(final Disposable disposable) {
        mRx2Disposable.add(disposable);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unchecked", "WeakerAccess"})
    public static <D> Disposable handle(final Object result, final CallbackRx<D> callback) {
        return result instanceof Observable ? handle((Observable<D>) result, callback):
               result instanceof Flowable   ? handle((Flowable<D> )  result, callback):
               result instanceof Single     ? handle((Single<D>    ) result, callback):
               result instanceof Maybe      ? handle((Maybe<D>     ) result, callback): null;
    }

    /**
     * Handles the {@link Flowable} provided.
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
        if (flowable == null) CoreLogger.logError("flowable == null");
        if (callback == null) CoreLogger.logError("callback == null");

        return flowable == null || callback == null ? null: flowable
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull Throwable throwable) throws Exception {
                        callback.onError(throwable);
                    }
                })
                .doOnNext(new Consumer<D>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull D data) throws Exception {
                        callback.onResult(data);
                    }
                })
                .subscribe();
    }

    /**
     * Handles the {@link Maybe} provided.
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
        if (maybe    == null) CoreLogger.logError("maybe == null");
        if (callback == null) CoreLogger.logError("callback == null");

        return maybe == null || callback == null ? null: maybe
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull Throwable throwable) throws Exception {
                        callback.onError(throwable);
                    }
                })
                .doOnSuccess(new Consumer<D>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull D data) throws Exception {
                        callback.onResult(data);
                    }
                })
                .subscribe();
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
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Observable<D> observable, final CallbackRx<D> callback) {
        if (observable == null) CoreLogger.logError("observable == null");
        if (callback   == null) CoreLogger.logError("callback == null");

        return observable == null || callback == null ? null: observable
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull Throwable throwable) throws Exception {
                        callback.onError(throwable);
                    }
                })
                .doOnNext(new Consumer<D>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull D data) throws Exception {
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
     * @return  The {@link Disposable}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> Disposable handle(final Single<D> single, final CallbackRx<D> callback) {
        if (single   == null) CoreLogger.logError("single == null");
        if (callback == null) CoreLogger.logError("callback == null");

        return single == null || callback == null ? null: single
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull Throwable throwable) throws Exception {
                        callback.onError(throwable);
                    }
                })
                .doOnSuccess(new Consumer<D>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull D data) throws Exception {
                        callback.onResult(data);
                    }
                })
                .subscribe();
    }

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

    /**
     * A disposable container that can hold onto multiple other disposables.
     */
    public static class Rx2Disposable {

        private final CompositeDisposable       mCompositeDisposable    = new CompositeDisposable();

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public void add(final Object result) {
            if (result instanceof Disposable)
                add((Disposable) result);
            else
                handleUnknownResult(result);
        }

        /**
         * Adds a new {@link Disposable}.
         *
         * @param disposable
         *        The {@link Disposable} to add
         */
        public void add(final Disposable disposable) {
            if (disposable == null) {
                CoreLogger.logError("disposable is null");
                return;
            }
            mCompositeDisposable.add(disposable);
        }

        /**
         * Disposes all added {@link Disposable Disposables}.
         */
        public void unsubscribe() {
            if (mCompositeDisposable.isDisposed())
                CoreLogger.log("CompositeDisposable.isDisposed() returns true");
            else {
                CoreLogger.logWarning("Rx2 dispose, size == " + mCompositeDisposable.size());
                mCompositeDisposable.dispose();
            }
        }
    }
}
