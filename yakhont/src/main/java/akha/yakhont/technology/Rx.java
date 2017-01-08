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

package akha.yakhont.technology;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.location.LocationCallbacks;

import android.app.Activity;
import android.location.Location;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Set;

import rx.Observable;
import rx.Producer;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.Exceptions;

/**
 * The base component to work with {@link <a href="http://reactivex.io/">Rx</a>}.
 *
 * @param <D>
 *        The data type
 *
 * @see RxLoader
 * @see RxLocation
 *
 * @author akha
 */
public abstract class Rx<D> {

    private   final Set<Subscriber<? super D>>  mSubscribers                = Utils.newWeakSet();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final boolean                     mIsSingle;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final boolean                     mIsNullable;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final boolean                     mHasProducer;

    /**
     * Initialises a newly created {@code Rx} object.
     *
     * @param single
     *        {@code true} if {@link Observable} either emits one value only or an error notification, {@code false} otherwise
     *
     * @param nullable
     *        {@code true} if emitted data can be null, {@code false} otherwise
     *
     * @param hasProducer
     *        {@code true} if the given component should provide {@link Producer}, {@code false} otherwise
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    protected Rx(final boolean single, final boolean nullable, final boolean hasProducer) {
        mIsSingle       = single;
        mIsNullable     = nullable;
        mHasProducer    = hasProducer;
    }

    /**
     * Returns the list of {@link Subscriber Subscribers}.
     *
     * @return  The subscribers list
     */
    public Set<Subscriber<? super D>> getSubscribers() {
        return mSubscribers;
    }

    /**
     * Stops the receipt of notifications on the registered {@link Subscriber Subscribers}.
     */
    public void unsubscribe() {
        final int size = mSubscribers.size();
        if (size == 0) return;

        CoreLogger.logWarning("RX unsubscribe, size " + size);

        for (final Subscriber<? super D> subscriber: mSubscribers)
            if (!subscriber.isUnsubscribed()) subscriber.unsubscribe();

        mSubscribers.clear();
    }

    /**
     * Returns the {@link Observable}.
     *
     * @return  The {@code Observable}
     */
    @NonNull
    @SuppressWarnings("unused")
    public Observable<D> createObservable() {
        return Observable.create(new Observable.OnSubscribe<D>() {
            @Override
            public void call(final Subscriber<? super D> subscriber) {
                if (subscriber == null) {
                    CoreLogger.logWarning("subscriber is null");
                    return;
                }

                subscriber.add(new Subscription() {
                    @Override
                    public void unsubscribe() {
                        mSubscribers.remove(subscriber);
                    }
                    @Override
                    public boolean isUnsubscribed() {
                        return mSubscribers.contains(subscriber);
                    }
                });

                if (mHasProducer) subscriber.setProducer(new Producer() {
                    @Override
                    public void request(long n) {
                        if (n <= 0)
                            CoreLogger.logWarning("not valid n " + n);
                        else
                            onResult(getResult());
                    }
                });

                mSubscribers.add(subscriber);
            }
        });
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected D getResult() {
        return null;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void onResult(final D result) {
        for (final Subscriber<? super D> subscriber: mSubscribers) {
            if (subscriber.isUnsubscribed()) {
                CoreLogger.log("unsubscribed: " + subscriber);
                continue;
            }

            if (!mIsNullable && result == null) {
                subscriber.onError(new Exception("result is null"));
                continue;
            }

            try {
                if (!onNext(subscriber, result)) continue;
            }
            catch (Throwable t) {
                Exceptions.throwIfFatal(t);
                subscriber.onError(t);
                continue;
            }

            if (mIsSingle) subscriber.onCompleted();
        }

        if (mIsSingle) mSubscribers.clear();
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "BooleanMethodIsAlwaysInverted", "WeakerAccess"})
    protected boolean onNext(@NonNull final Subscriber<? super D> subscriber, final D result) {
        subscriber.onNext(result);
        return true;
    }

    /**
     * Extends the {@link Rx} class to provide {@link BaseResponse} support. For example, in Fragment:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.loader.BaseLoader;
     * import akha.yakhont.loader.BaseResponse;
     * import akha.yakhont.technology.retrofit.Retrofit.RetrofitRx;
     *
     * import com.mypackage.model.MyData;
     *
     * import retrofit.client.Response;
     *
     * import rx.Subscriber;
     * import rx.Subscription;
     *
     * public class MyFragment extends Fragment {
     *
     *     private Subscription mRxSubscription;
     *
     *     &#064;Override
     *     public void onActivityCreated(Bundle savedInstanceState) {
     *         super.onActivityCreated(savedInstanceState);
     *         ...
     *
     *         RetrofitRx&lt;MyData[]&gt; rx = new RetrofitRx&lt;&gt;();
     *
     *         mRxSubscription = rx.createObservable().subscribe(
     *                 new Subscriber&lt;BaseResponse&lt;Response, Exception, MyData[]&gt;&gt;() {
     *
     *             &#064;Override
     *             public void onNext(BaseResponse&lt;Response, Exception, MyData[]&gt; baseResponse) {
     *                 MyData[] data = baseResponse.getResult();
     *                 // your code here
     *             }
     *
     *             &#064;Override
     *             public void onCompleted() {
     *                 // your code here
     *             }
     *
     *             &#064;Override
     *             public void onError(Throwable e) {
     *                 // your code here
     *             }
     *         });
     *
     *         BaseLoader.simpleInit(this, MyData[].class, rx).startLoading();
     *     }
     *
     *     &#064;Override
     *     public void onDestroy() {
     *         mRxSubscription.unsubscribe();
     *
     *         super.onDestroy();
     *     }
     * }
     * </pre>
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data
     *
     * @yakhont.see BaseResponseLoaderWrapper.CoreLoad
     * @yakhont.see Retrofit.RetrofitRx
     */
    public static class RxLoader<R, E, D> extends Rx<BaseResponse<R, E, D>> {

        /**
         * Initialises a newly created {@code RxLoader} object.
         */
        public RxLoader() {
            this(true, true);
        }

        /**
         * Initialises a newly created {@code RxLoader} object.
         *
         * @param single
         *        {@code true} if {@link Observable} either emits one value only or an error notification, {@code false} otherwise
         *
         * @param nullable
         *        {@code true} if emitted data can be null, {@code false} otherwise
         */
        @SuppressWarnings("SameParameterValue")
        public RxLoader(final boolean single, final boolean nullable) {
            super(single, nullable, false);
        }

        /** @exclude */
        @Override
        @SuppressWarnings("JavaDoc")
        protected boolean onNext(@NonNull final Subscriber<? super BaseResponse<R, E, D>> subscriber,
                                 final BaseResponse<R, E, D> baseResponse) {
            if (baseResponse == null) {     // should never happen
                subscriber.onError(new Exception("BaseResponse is null"));
                return false;
            }

            if (baseResponse.getResult() != null) return super.onNext(subscriber, baseResponse);

            final E error = baseResponse.getError();
            if (error != null) {
                if (error instanceof Throwable) Exceptions.throwIfFatal((Throwable) error);
                subscriber.onError(error instanceof Throwable ? (Throwable) error: new Exception(error.toString()));
                return false;
            }

            if (mIsNullable) return super.onNext(subscriber, baseResponse);

            subscriber.onError(new Exception("BaseResponse.getResult() is null"));
            return false;
        }
    }

    /**
     * Extends the {@link Rx} class to provide {@link Location} support. For example, in Activity:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.callback.annotation.CallbacksInherited;
     * import akha.yakhont.location.LocationCallbacks;
     *
     * import rx.Subscription;
     * import rx.functions.Action1;
     *
     * &#064;CallbacksInherited(LocationCallbacks.class)
     * public class MyActivity extends Activity {
     *
     *     private LocationCallbacks mLocationCallbacks;
     *     private RxLocation        mRx;
     *     private Subscription      mRxSubscription;
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *         super.onCreate(savedInstanceState);
     *         ...
     *
     *         mLocationCallbacks = LocationCallbacks.getLocationCallbacks(this);
     *
     *         mRx = new RxLocation(this);
     *
     *         mRxSubscription = mRx.createObservable().subscribe(new Action1&lt;Location&gt;() {
     *
     *             &#064;Override
     *             public void call(Location location) {
     *                 // your code here
     *             }
     *         });
     *
     *         mLocationCallbacks.register(mRx);
     *     }
     *
     *     &#064;Override
     *     protected void onDestroy() {
     *         mLocationCallbacks.unregister(mRx);
     *         mRxSubscription.unsubscribe();
     *
     *         super.onDestroy();
     *     }
     * }
     * </pre>
     *
     * @see LocationCallbacks
     */
    public static class RxLocation extends Rx<Location> {

        private final WeakReference<Activity>   mActivity;

        /**
         * Initialises a newly created {@code RxLocation} object.
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings("unused")
        public RxLocation(@NonNull final Activity activity) {
            this(activity, false);
        }

        /**
         * Initialises a newly created {@code RxLocation} object.
         *
         * @param activity
         *        The Activity
         *
         * @param single
         *        {@code true} if {@link Observable} either emits one value only or an error notification, {@code false} otherwise
         */
        @SuppressWarnings("SameParameterValue")
        public RxLocation(@NonNull final Activity activity, final boolean single) {
            super(single, true, true);
            mActivity = new WeakReference<>(activity);
        }

        /** @exclude */
        @Override
        @SuppressWarnings("JavaDoc")
        protected Location getResult() {
            return LocationCallbacks.getCurrentLocation(mActivity.get());
        }
    }
}
