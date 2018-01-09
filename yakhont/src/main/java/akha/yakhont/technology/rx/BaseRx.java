/*
 * Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov
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
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.technology.rx.Rx.RxSubscription;
import akha.yakhont.technology.rx.Rx2.Rx2Disposable;

import android.app.Activity;
import android.location.Location;

import java.lang.ref.WeakReference;
import java.util.Set;

/**
 * The base component to work with {@link <a href="http://reactivex.io/">Rx</a>}.
 * <p>
 * Supports both {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
 * and {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>}.
 *
 * @param <D>
 *        The data type
 *
 * @see Rx
 * @see Rx2
 * @see LoaderRx
 * @see LocationRx
 *
 * @author akha
 */
public abstract class BaseRx<D> {

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final boolean                     mIsSingle;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final CommonRx<D>                 mCommonRx;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final Set<CallbackRx<D>>          mCallbacks          = Utils.newSet();

    /**
     * Initialises a newly created {@code BaseRx} object.
     *
     * @param commonRx
     *        The {@link CommonRx} to use
     *
     * @param isSingle
     *        {@code true} if {@link CommonRx} either emits one value only or an error notification, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    protected BaseRx(final CommonRx<D> commonRx, final boolean isSingle) {
        if (commonRx == null)
            throw new RuntimeException("CommonRx is null");

        mCommonRx = commonRx;
        mIsSingle = isSingle;

        if (mCommonRx.mBaseRx == null)
            mCommonRx.mBaseRx = this;
        else
            throw new IllegalStateException("this instance of CommonRx is already in use");
    }

    /**
     * Calls {@link #unsubscribe} and removes all registered {@link CallbackRx callbacks}.
     */
    public void cleanup() {
        unsubscribe();

        final int size = mCallbacks.size();
        if (size == 0) return;

        CoreLogger.logWarning(getClass().getName() + " Rx cleanup, size " + size);
        mCallbacks.clear();
    }

    /**
     * Please refer to {@link CommonRx#unsubscribe CommonRx.unsubscribe()} description.
     */
    @SuppressWarnings("WeakerAccess")
    public void unsubscribe() {
        mCommonRx.unsubscribe();
    }

    /**
     * Checks given Rx component for the registered observers.
     *
     * @return  {@code true} if given Rx component has some registered observers, {@code false} otherwise
     */
    public boolean hasObservers() {
        return !mCallbacks.isEmpty();
    }

    /**
     * Please refer to {@link CommonRx#setErrorHandlerEmpty CommonRx.setErrorHandlerEmpty()} description.
     */
    @SuppressWarnings("unused")
    public void setErrorHandlerEmpty() {
        mCommonRx.setErrorHandlerEmpty();
    }

    /**
     * Please refer to {@link CommonRx#setErrorHandlerJustLog CommonRx.setErrorHandlerJustLog()} description.
     */
    @SuppressWarnings("unused")
    public void setErrorHandlerJustLog() {
        mCommonRx.setErrorHandlerJustLog();
    }

    /**
     * Returns the last observed item (if any), or null.
     *
     * @return  The last observed item
     */
    protected abstract D getResult();

    /**
     * Please refer to {@link CommonRx#isNullable CommonRx.isNullable()} description.
     */
    @SuppressWarnings("WeakerAccess")
    protected boolean isNullable() {
        return mCommonRx.isNullable();
    }

    /**
     * Returns {@code true} if {@link CommonRx} either emits one value only or an error notification, {@code false} otherwise.
     *
     * @return  {@code true} if {@link CommonRx} either emits one value only or an error notification, {@code false} otherwise
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    protected boolean isSingle() {
        return mIsSingle;
    }

    /**
     * Returns the {@link CommonRx} in use.
     *
     * @return  The {@link CommonRx} in use
     */
    @SuppressWarnings("unused")
    public CommonRx<D> getRx(){
        return mCommonRx;
    }

    /**
     * Called by external components to provide observed items.
     *
     * @param result
     *        The observed item
     */
    public void onResult(final D result) {
        onResult(result, null);
    }

    /**
     * Called by external components to indicate errors.
     *
     * @param throwable
     *        The error
     */
    @SuppressWarnings({"WeakerAccess", "ThrowableResultOfMethodCallIgnored"})
    public void onError(final Throwable throwable) {
        CoreLogger.log(throwable == null ? Level.ERROR: Level.DEBUG, "Rx failed", throwable);
        onResult(null, throwable == null ? getDefaultException(null): throwable);
    }

    /**
     * Called by external components to indicate errors.
     *
     * @param error
     *        The error
     */
    @SuppressWarnings("WeakerAccess")
    public void onError(final String error) {
        onError(getDefaultException(error));
    }

    private Exception getDefaultException(final String error) {
        return new Exception(error == null ? "unknown error": error);
    }

    private void onResult(final D result, final Throwable throwable) {
        for (final CallbackRx<D> callback: mCallbacks) {
            if (callback == null) {
                CoreLogger.logError("callback is null");
                continue;
            }
            if (throwable != null)
                callback.onError(throwable);
            else
                callback.onResult(result);
        }
        if (mIsSingle) mCallbacks.clear();
    }

    /**
     * Subscribes common subscriber (suitable for both RxJava and RxJava 2) to receive Rx push-based notifications.
     *
     * @param subscriber
     *        The {@link SubscriberRx subscriber}
     *
     * @return  This {@code BaseRx} object to allow for chaining of calls to methods
     */
    @SuppressWarnings("WeakerAccess")
    public BaseRx<D> subscribe(final SubscriberRx<D> subscriber) {
        if (subscriber == null)
            CoreLogger.logError("subscriber is null");
        else
            mCommonRx.subscribeRx(subscriber);
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Provides a common async callback mechanism (for both RxJava and RxJava 2) to receive Rx push-based notifications.
     *
     * @param <D>
     *        The type of item the {@code CallbackRx} expects to observe
     */
    public interface CallbackRx<D> {

        /**
         * Provides the {@code CallbackRx} with a new item to observe.
         *
         * @param result
         *        The item emitted by the {@code Observable}
         */
        void onResult(D result);

        /**
         * Notifies the {@code CallbackRx} that the {@code Observable} has experienced an error condition.
         *
         * @param throwable
         *        The exception encountered by the {@code Observable}
         */
        void onError(Throwable throwable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Provides a common subscriber (for both RxJava and RxJava 2) to receive Rx push-based notifications.
     *
     * @param <D>
     *        The type of item the {@code SubscriberRx} expects to observe
     */
    public static class SubscriberRx<D> {

        /**
         * Initialises a newly created {@code SubscriberRx} object.
         */
        public SubscriberRx() {
        }

        /**
         * Request a certain maximum number of emitted items from the {@code Observable}
         * this {@code SubscriberRx} is subscribed to.
         * <p>
         * For the moment it's supported for RxJava only (not for RxJava 2).
         *
         * @param n
         *        The maximum number of items you want the {@code Observable} to emit to the {@code SubscriberRx} at this time
         */
        public void request(final long n) {
            CoreLogger.log("Rx: request; n == " + n);
        }

        /**
         * Provides the {@code SubscriberRx} with a new item to observe.
         *
         * @param result
         *        The item emitted by the {@code Observable}
         */
        public void onNext(final D result) {
            CoreLogger.log("Rx: onNext; result == " + result);
        }

        /**
         * Notifies the {@code SubscriberRx} that the {@code Observable} has experienced an error condition.
         *
         * @param throwable
         *        The exception encountered by the {@code Observable}
         */
        public void onError(final Throwable throwable) {
            CoreLogger.log("Rx: onError", throwable);
        }

        /**
         * Notifies the {@code SubscriberRx} that the {@code Observable} has finished sending push-based notifications.
         */
        public void onCompleted() {
            CoreLogger.log("Rx: onCompleted");
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The base component to implement both {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>} and
     * {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>} support.
     *
     * @param <D>
     *        The data type
     *
     * @see Rx
     * @see Rx2
     */
    public static abstract class CommonRx<D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected BaseRx<D>                     mBaseRx;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final RxSubscription          mRxSubscription     = new RxSubscription();
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final Rx2Disposable           mRx2Disposable      = new Rx2Disposable();

        /**
         * Initialises a newly created {@code CommonRx} object.
         */
        @SuppressWarnings("WeakerAccess")
        protected CommonRx() {
        }

        /**
         * Returns the {@link RxSubscription} component.
         *
         * @return  The {@link RxSubscription}
         */
        public RxSubscription getRxSubscriptionHandler() {
            return mRxSubscription;
        }

        /**
         * Returns the {@link Rx2Disposable} component.
         *
         * @return  The {@link Rx2Disposable}
         */
        public Rx2Disposable getRx2DisposableHandler() {
            return mRx2Disposable;
        }

        /**
         * Sets Rx error handler to the empty one. Not advisable at all.
         */
        public abstract void setErrorHandlerEmpty();

        /**
         * Sets Rx error handler to the one which does logging only.
         * For more info please refer to {@link <a href="https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling">Rx error handling</a>}.
         */
        public abstract void setErrorHandlerJustLog();

        /**
         * Stops the receipt of notifications on the registered subscribers (and disposables).
         */
        public void unsubscribe() {
            mRx2Disposable .unsubscribe();
            mRxSubscription.unsubscribe();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected abstract void subscribeRx(final SubscriberRx<D> subscriber);

        /**
         * Returns {@code true} if observed items can be nulls, {@code false} otherwise.
         *
         * @return  {@code true} if given Rx component supports nulls, {@code false} otherwise
         */
        protected abstract boolean isNullable();

        /**
         * Please refer to {@link BaseRx#isSingle BaseRx.isSingle()} description.
         */
        @SuppressWarnings("WeakerAccess")
        protected boolean isSingle() {
            return mBaseRx.mIsSingle;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected void checkSingle() {
            if (!isSingle()) throw new RuntimeException(
                    "given object can not be treated as Rx Single / Maybe; please fix constructor call");
        }

        /**
         * Please refer to {@link BaseRx#getResult BaseRx.getResult()} description.
         */
        @SuppressWarnings("unused")
        protected D getResult() {
            return mBaseRx.getResult();
        }

        /**
         * Registers the {@link CallbackRx callback}.
         *
         * @param callback
         *        The callback to register
         */
        @SuppressWarnings("WeakerAccess")
        public void register(final CallbackRx<D> callback) {
            if (callback == null) {
                CoreLogger.logError("callback is null");
                return;
            }
            if (!mBaseRx.mCallbacks.add(callback)) {
                CoreLogger.logWarning("the callback is already registered: " + callback);
            }
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void handleUnknownResult(final Object result) {
            CoreLogger.logError("unknown Rx " + (result == null ? null: result.getClass()));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseRx} class to provide {@link Location} support. For example, in Activity:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.callback.annotation.CallbacksInherited;
     * import akha.yakhont.location.LocationCallbacks;
     * import akha.yakhont.technology.rx.BaseRx.LocationRx;
     * import akha.yakhont.technology.rx.BaseRx.SubscriberRx;
     *
     * import android.location.Location;
     *
     * &#064;CallbacksInherited(LocationCallbacks.class)
     * public class MyActivity extends Activity {
     *
     *     private LocationCallbacks mLocationCallbacks;
     *     private LocationRx        mRx;
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *         super.onCreate(savedInstanceState);
     *         ...
     *
     *         mLocationCallbacks = LocationCallbacks.getLocationCallbacks(this);
     *
     *         boolean useRxJava2 = true;
     *         mRx = new LocationRx(useRxJava2, this).subscribe(new SubscriberRx&lt;Location&gt;() {
     *
     *             &#064;Override
     *             public void onNext(final Location location) {
     *                 // your code here
     *             }
     *         });
     *
     *         // avoids terminating the application
     *         //   by calls to the Rx uncaught exception handler
     *         // actually it's the final application only
     *         //   that should set (or not) such kind of handlers:
     *         // it's not advised for intermediate libraries
     *         //   to change the global handlers behavior
     *         mRx.setErrorHandlerJustLog();
     *
     *         mLocationCallbacks.register(mRx);
     *     }
     *
     *     &#064;Override
     *     protected void onDestroy() {
     *         mRx.cleanup();
     *         mLocationCallbacks.unregister(mRx);
     *
     *         super.onDestroy();
     *     }
     * }
     * </pre>
     *
     * @see LocationCallbacks
     */
    public static class LocationRx extends BaseRx<Location> {

        private final WeakReference<Activity>   mActivity;

        /**
         * Initialises a newly created {@code LocationRx} object.
         * <p>
         * Note: experimental.
         */
        @SuppressWarnings("unused")
        public LocationRx() {
            this(true, null);
        }

        /**
         * Initialises a newly created {@code LocationRx} object.
         *
         * @param isRx2
         *        {@code true} for using {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>},
         *        {@code false} for {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings("SameParameterValue")
        public LocationRx(final boolean isRx2, final Activity activity) {
            this(isRx2 ? new Rx2<Location>(): new Rx<Location>(), activity);
        }

        /**
         * Initialises a newly created {@code LocationRx} object.
         * <p>
         * Note: experimental.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         */
        @SuppressWarnings("unused")
        public LocationRx(final CommonRx<Location> commonRx) {
            this(commonRx, null);
        }

        /**
         * Initialises a newly created {@code LocationRx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         *
         * @param activity
         *        The Activity
         */
        public LocationRx(final CommonRx<Location> commonRx, final Activity activity) {
            super(commonRx, false);
            mActivity = (activity == null) ? null: new WeakReference<>(activity);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public LocationRx subscribe(final SubscriberRx<Location> subscriber) {
            return (LocationRx) super.subscribe(subscriber);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public Location getResult() {
            Activity activity = null;
            if (mActivity != null) {
                activity = mActivity.get();
                if (activity == null)
                    CoreLogger.logWarning("activity is null, let's try the current one");
            }
            return LocationCallbacks.getCurrentLocation(
                    activity != null ? activity: LocationCallbacks.getActivity());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseRx} class to provide {@link BaseResponse} support. For example, in Fragment:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.technology.retrofit.Retrofit2;
     * import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
     * import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
     * import akha.yakhont.technology.rx.BaseRx.SubscriberRx;
     *
     * import com.mypackage.model.MyData;
     * import com.mypackage.retrofit.Retrofit2Api;
     *
     * public class MyFragment extends Fragment {
     *
     *     private Retrofit2Rx&lt;MyData[]&gt; mRx;
     *
     *     &#064;Override
     *     public void onActivityCreated(Bundle savedInstanceState) {
     *         super.onActivityCreated(savedInstanceState);
     *         ...
     *
     *         boolean useRxJava2 = true;
     *         mRx = new Retrofit2Rx&lt;&gt;(useRxJava2);
     *
     *         mRx.subscribeSimple(new SubscriberRx&lt;MyData[]&gt;() {
     *
     *             &#064;Override
     *             public void onNext(final MyData[] data) {
     *                 // your code here
     *             }
     *         });
     *
     *         new Retrofit2CoreLoadBuilder&lt;&gt;(this, MyData[].class, getRetrofitApi())
     *             .setRx(mRx).create().startLoading();
     *     }
     *
     *     private Retrofit2&lt;Retrofit2Api&gt; getRetrofitApi() {
     *         // something like below but not exactly -
     *         //   Retrofit2 object should be cached somewhere
     *         // and don't forget to call Retrofit2.init()
     *         return new Retrofit2&lt;&gt;();
     *     }
     *
     *     &#064;Override
     *     public void onDestroy() {
     *         mRx.cleanup();
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
    public static class LoaderRx<R, E, D> extends BaseRx<BaseResponse<R, E, D>> {

        private static final boolean            DEFAULT_MODE_SINGLE = true;

        private BaseResponse<R, E, D>           mCached;

        /**
         * Initialises a newly created {@code LoaderRx} object.
         */
        public LoaderRx() {
            this(true);
        }

        /**
         * Initialises a newly created {@code LoaderRx} object.
         *
         * @param isRx2
         *        {@code true} for using {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>},
         *        {@code false} for {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
         */
        public LoaderRx(final boolean isRx2) {
            this(isRx2, DEFAULT_MODE_SINGLE);
        }

        /**
         * Initialises a newly created {@code LoaderRx} object.
         *
         * @param isRx2
         *        {@code true} for using {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 2</a>},
         *        {@code false} for {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}
         *
         * @param isSingle
         *        {@code true} if {@link CommonRx} either emits one value only or an error notification, {@code false} otherwise
         */
        public LoaderRx(final boolean isRx2, final boolean isSingle) {
            this(isRx2 ? new Rx2<BaseResponse<R, E, D>>(): new Rx<BaseResponse<R, E, D>>(), isSingle);
        }

        /**
         * Initialises a newly created {@code LoaderRx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         */
        public LoaderRx(final CommonRx<BaseResponse<R, E, D>> commonRx) {
            this(commonRx, DEFAULT_MODE_SINGLE);
        }

        /**
         * Initialises a newly created {@code LoaderRx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         *
         * @param isSingle
         *        {@code true} if {@link CommonRx} either emits one value only or an error notification, {@code false} otherwise
         */
        public LoaderRx(final CommonRx<BaseResponse<R, E, D>> commonRx, final boolean isSingle) {
            super(commonRx, isSingle);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public LoaderRx<R, E, D> subscribe(final SubscriberRx<BaseResponse<R, E, D>> subscriber) {
            return (LoaderRx<R, E, D>) super.subscribe(subscriber);
        }

        /**
         * Subscribes common subscriber (suitable for both RxJava and RxJava 2) to receive Rx push-based notifications.
         * For simplification automatically extracts result from {@link BaseResponse}.
         *
         * @param subscriber
         *        The {@link SubscriberRx subscriber}
         *
         * @return  This {@code LoaderRx} object to allow for chaining of calls to methods
         */
        public LoaderRx<R, E, D> subscribeSimple(final SubscriberRx<D> subscriber) {
            if (subscriber == null) {
                CoreLogger.logError("subscriber is null");
                return null;
            }
            return subscribe(new SubscriberRx<BaseResponse<R, E, D>>() {
                @Override
                public void request(final long n) {
                    subscriber.request(n);
                }

                @Override
                public void onNext(final BaseResponse<R, E, D> result) {
                    subscriber.onNext(result == null ? null: result.getResult());
                }

                @Override
                public void onError(final Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    subscriber.onCompleted();
                }
            });
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public BaseResponse<R, E, D> getResult() {
            return mCached;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onResult(final BaseResponse<R, E, D> baseResponse) {
            CoreLogger.log("LoaderRx.onResult()");

            mCached = baseResponse;

            if (baseResponse == null) {     // should never happen
                super.onResult(null);
                return;
            }

            final E error = baseResponse.getError();
            if (error != null) {
                onError(error instanceof Throwable ? (Throwable) error: new Exception(error.toString()));
                return;
            }

            final D result = baseResponse.getResult();
            if (result == null && !isNullable()) {
                CoreLogger.logError("BaseResponse.getResult() is null");

                super.onResult(null);
                return;
            }

            super.onResult(baseResponse);
        }
    }
}
