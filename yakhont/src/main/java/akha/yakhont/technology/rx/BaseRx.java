/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont.technology.rx;

import akha.yakhont.Core;               // for javadoc
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.FlavorHelper;
import akha.yakhont.FlavorHelper.FlavorCommonRx;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
import akha.yakhont.technology.rx.Rx3.Rx3Disposable;

import android.app.Activity;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * The base component to work with {@link <a href="https://reactivex.io/">Rx</a>}.
 * <p>
 * Supports all {@link <a href="https://github.com/ReactiveX/RxJava/">RxJava</a>} versions.
 *
 * @param <D>
 *        The data type
 *
 * @see Rx3
 * @see LoaderRx
 * @see LocationRx
 *
 * @author akha
 */
public abstract class BaseRx<D> {

    /**
     * The supported RxJava versions.
     */
    public enum RxVersions {
        /** Please refer to {@link <a href="https://github.com/ReactiveX/RxJava/tree/1.x">RxJava</a>}.   */
        @SuppressWarnings("unused")
        VERSION_1,
        /** Please refer to {@link <a href="https://github.com/ReactiveX/RxJava/tree/2.x">RxJava 2</a>}. */
        @SuppressWarnings("unused")
        VERSION_2,
        /** Please refer to {@link <a href="https://github.com/ReactiveX/RxJava">RxJava 3</a>}.          */
        VERSION_3
    }

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

        CoreLogger.log(mIsSingle ? Level.WARNING: CoreLogger.getDefaultLevel(), "Rx single is " + mIsSingle);
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
    @SuppressWarnings("unused")
    public boolean hasObservers() {
        return !mCallbacks.isEmpty();
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
    @SuppressWarnings({"WeakerAccess"})
    public void onError(final Throwable throwable) {
        CoreLogger.log(throwable == null ? Level.ERROR: CoreLogger.getDefaultLevel(), "Rx failed", throwable);
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
            Utils.safeRun(new Runnable() {
                @Override
                public void run() {
                    if (throwable != null)
                        callback.onError(throwable);
                    else
                        callback.onResult(result);
                }

                @NonNull
                @Override
                public String toString() {
                    return "BaseRx - callback onError / onResult";
                }
            });
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
    @SuppressWarnings({"unused", "WeakerAccess"})
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
        @SuppressWarnings("WeakerAccess")
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
        @SuppressWarnings("WeakerAccess")
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
     * The base component to implement {@link <a href="https://github.com/ReactiveX/RxJava">RxJava</a>} support.
     *
     * @param <D>
     *        The data type
     *
     * @see Rx3
     */
    public static abstract class CommonRx<D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final FlavorCommonRx          mFlavorCommonRx     = new FlavorCommonRx();

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       BaseRx<D>               mBaseRx;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final Rx3Disposable           mRxDisposable       = new Rx3Disposable();

        // for anonymous Rx
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static Rx3Disposable          sRxDisposable;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static boolean                sSafe;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static boolean                sIsErrorHandlerDefined;

        private   static RxVersions             sVersion;

        static {
            init();
        }

        /**
         * Cleanups static fields in CommonRx; called from {@link Core#cleanUpFinal()}.
         */
        public static void cleanUpFinal() {
            init();
        }

        private static void init() {
            sRxDisposable           = new Rx3Disposable();
            sIsErrorHandlerDefined  = false;
            sVersion                = null;
            sSafe                   = true;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void cleanUpFinal(final Runnable runnable) {
            Utils.safeRun(runnable);
            sIsErrorHandlerDefined = false;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void setErrorHandler(final Runnable runnable) {
            sIsErrorHandlerDefined = Utils.safeRun(runnable);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void setErrorHandlerDefaultBase() {
            sIsErrorHandlerDefined = true;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static boolean isErrorHandlerDefinedBase() {
            return sIsErrorHandlerDefined;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void checkNull(final Object result, final String msg) throws Exception {
            if (result == null) throw new Exception(msg);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static Class<?> getHandleReturnType(final Object handler, final Method method) {
            if (handler == null) {
                CoreLogger.logError("handler == null");
                return null;
            }
            if (method == null) {
                CoreLogger.logError("method == null");
                return null;
            }
            return method.getReturnType();
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static <D> Runnable getHandlerRunnable(final boolean ok, @NonNull final CallbackRx<D> callback,
                                                         final D data, final Throwable throwable,
                                                         @SuppressWarnings("SameParameterValue") final String prefix) {
            return new Runnable() {
                @Override
                public void run() {
                    if (ok)
                        callback.onResult(data);
                    else
                        callback.onError(throwable);
                }

                @NonNull
                @Override
                public String toString() {
                    return prefix + " - CallbackRx.on" + (ok ? "Result": "Error") + "()";
                }
            };
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static <D> void handleCheck(final Object object, final CallbackRx<D> callback, final String name) {
            if (object   == null) CoreLogger.logError(name +  " == null");
            if (callback == null) CoreLogger.logError("callback == null");
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "BooleanMethodIsAlwaysInverted", "WeakerAccess"})
        protected boolean checkNullObserver(final Object observer) {
            if (observer == null) CoreLogger.logError("observer is null");
            return observer != null;
        }

        /**
         * Gets the Rx version in use.
         *
         * @return  The Rx version
         */
        @SuppressWarnings("unused")
        public static RxVersions getVersion() {
            if (sVersion == null) CoreLogger.logError("unknown Rx version");    // should never happen
            return sVersion;
        }

        /**
         * Initialises a newly created {@code CommonRx} object.
         *
         * @param errMgsParam
         *        The error message parameter
         *
         * @param nOk
         *        The error message trigger
         *
         * @param version
         *        The Rx version
         */
        @SuppressWarnings("WeakerAccess")
        @RestrictTo(Scope.LIBRARY)
        protected CommonRx(@SuppressWarnings("SameParameterValue") @NonNull final String errMgsParam,
                           final boolean nOk,
                           @SuppressWarnings("SameParameterValue") final RxVersions version) {
            sVersion = version;
            if (nOk) CoreLogger.logError(String.format(
                    "in your application initialization code please call "                   +
                    "'Core.setRxUncaughtExceptionBehavior()' (or any of '%s.setErrorHandler*()'" +
                    " methods); ignore this information only if you know what you're doing", errMgsParam));
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public FlavorCommonRx getFlavorCommonRx() {
            return mFlavorCommonRx;
        }

        /**
         * Returns the {@link Rx3Disposable} component.
         *
         * @return  The {@link Rx3Disposable}
         *
         * @see #unsubscribe()
         */
        public Rx3Disposable getRxDisposableHandler() {
            return mRxDisposable;
        }

        /**
         * Returns the anonymous {@link Rx3Disposable} component.
         *
         * @return  The {@link Rx3Disposable}
         *
         * @see #unsubscribeAnonymous()
         */
        public static Rx3Disposable getRxDisposableHandlerAnonymous() {
            return sRxDisposable;
        }

        /**
         * Stops the receipt of notifications on the registered subscribers (and disposables).
         */
        @SuppressWarnings("WeakerAccess")
        public void unsubscribe() {
            mRxDisposable  .unsubscribe();
            mFlavorCommonRx.unsubscribe();          // RxJava 1 / 2 support is in full version
        }

        /**
         * Stops the receipt of notifications on the anonymous subscribers (and disposables).
         * e.g. if Rx is in Retrofit API only.
         */
        @SuppressWarnings("unused")
        public static void unsubscribeAnonymous() {
            final String msg ="about to unregister anonymous %s";

            if (sRxDisposable.notEmpty()) {
                CoreLogger.logWarning(String.format(msg, "disposables"));
                sRxDisposable.unsubscribe();
            }

            FlavorCommonRx.unsubscribeAnonymous(msg);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected abstract void subscribeRx(final SubscriberRx<D> subscriber);

        /**
         * Returns {@code true} if observed items can be nulls, {@code false} otherwise.
         *
         * @return  {@code true} if given Rx component supports nulls, {@code false} otherwise
         */
        @SuppressWarnings({"SameReturnValue", "WeakerAccess"})
        protected boolean isNullable() {
            return false;
        }

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

        /**
         * Returns the 'safe' flag value. Please refer to {@link #setSafeFlag} for more information.
         *
         * @return  The 'safe' flag value
         */
        @SuppressWarnings("WeakerAccess")
        public static boolean getSafeFlag() {
            return sSafe;
        }

        /**
         * The 'safe' flag affects subscription behaviour. E.g. for {@code Observable}, if 'safe' flag is {@code true},
         * executes {@code 'observable.subscribe(dataHandler, errorHandler)'}, otherwise executes
         * {@code 'observable.doOnError(errorHandler).doOnNext(dataHandler).subscribe()'}.
         * <p>
         * So, if 'safe' flag is {@code false} and some error happens, the {@code OnErrorNotImplementedException}
         * will be thrown.
         *
         * @param value
         *        The new value for the 'safe' flag
         */
        public static void setSafeFlag(final boolean value) {
            sSafe = value;
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
     * public class YourActivity extends Activity {
     *
     *     private LocationCallbacks mLocationCallbacks;
     *     private LocationRx        mRx;
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *         super.onCreate(savedInstanceState);
     *
     *         // your code here: setContentView(...), etc.
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
         */
        @SuppressWarnings("unused")
        public LocationRx() {
            this(RxVersions.VERSION_3, null);
        }

        /**
         * Initialises a newly created {@code LocationRx} object.
         *
         * @param version
         *        The one of supported RxJava versions
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
        public LocationRx(final RxVersions version, final Activity activity) {
            this(FlavorHelper.getCommonRx(version), activity);
        }

        /**
         * Initialises a newly created {@code LocationRx} object.
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
        @SuppressWarnings("WeakerAccess")
        public LocationRx(final CommonRx<Location> commonRx, final Activity activity) {
            super(commonRx, false);
            mActivity = activity == null ? null: new WeakReference<>(activity);
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
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
            if (mActivity != null) activity = mActivity.get();
            if (activity == null)
                CoreLogger.logWarning("activity is null, let's try to use the current one");
            return LocationCallbacks.getCurrentLocation(
                    activity != null ? activity: LocationCallbacks.getActivity());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseRx} class to provide {@link BaseResponse} support.
     * For example, in Fragment:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import com.yourpackage.model.YourData;
     * import com.yourpackage.retrofit.YourRetrofit;
     *
     * import akha.yakhont.technology.retrofit.Retrofit2;
     * import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
     * import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
     * import akha.yakhont.technology.rx.BaseRx.SubscriberRx;
     *
     * public class YourFragment extends Fragment {
     *
     *     private Retrofit2Rx&lt;YourData[]&gt; mRx;
     *
     *     &#064;Override
     *     public void onActivityCreated(Bundle savedInstanceState) {
     *         super.onActivityCreated(savedInstanceState);
     *
     *         boolean useRxJava2 = true;
     *         mRx = new Retrofit2Rx&lt;&gt;(useRxJava2);
     *
     *         mRx.subscribeSimple(new SubscriberRx&lt;YourData[]&gt;() {
     *
     *             &#064;Override
     *             public void onNext(final YourData[] data) {
     *                 // your code here
     *             }
     *         });
     *
     *         new Retrofit2CoreLoadBuilder&lt;&gt;(getRetrofit())
     *             .setRequester(YourRetrofit::yourMethod).setDataBinding(BR.yourDataBindingId)
     *             .setRx(mRx).create().load();
     *     }
     *
     *     private Retrofit2&lt;YourRetrofit, YourData[]&gt; getRetrofit() {
     *         // something like this
     *         return new Retrofit2&lt;YourRetrofit, YourData[]&gt;().init(
     *             YourRetrofit.class, "https://...");
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
     * @see CoreLoad
     * @see Retrofit2Rx
     */
    public static class LoaderRx<R, E, D> extends BaseRx<BaseResponse<R, E, D>> {

        private static final boolean            DEFAULT_MODE_SINGLE = false;

        private BaseResponse<R, E, D>           mCached;

        /**
         * Initialises a newly created {@code LoaderRx} object.
         */
        @SuppressWarnings("WeakerAccess")
        public LoaderRx() {
            this(RxVersions.VERSION_3);
        }

        /**
         * Initialises a newly created {@code LoaderRx} object.
         *
         * @param version
         *        The one of supported RxJava versions
         */
        @SuppressWarnings("WeakerAccess")
        public LoaderRx(final RxVersions version) {
            this(version, DEFAULT_MODE_SINGLE);
        }

        /**
         * Initialises a newly created {@code LoaderRx} object.
         *
         * @param version
         *        The one of supported RxJava versions
         *
         * @param isSingle
         *        {@code true} if {@link CommonRx} either emits one value only or an error notification, {@code false} otherwise
         */
        @SuppressWarnings({"WeakerAccess"})
        public LoaderRx(final RxVersions version, final boolean isSingle) {
            this(FlavorHelper.getCommonRx(version), isSingle);
        }

        /**
         * Initialises a newly created {@code LoaderRx} object.
         *
         * @param commonRx
         *        The {@link CommonRx} to use
         */
        @SuppressWarnings("WeakerAccess")
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
        @SuppressWarnings("WeakerAccess")
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
         * Subscribes common subscriber (suitable for both RxJava and RxJava 2) to receive Rx
         * push-based notifications.
         * For simplification automatically extracts result from {@link BaseResponse}.
         *
         * @param subscriber
         *        The {@link SubscriberRx subscriber}
         *
         * @return  This {@code LoaderRx} object to allow for chaining of calls to methods
         */
        @SuppressWarnings("unused")
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

                @SuppressWarnings("unused")
                @Override
                public void onNext(final BaseResponse<R, E, D> result) {
                    subscriber.onNext(result == null ? null: result.getResult());
                }

                @SuppressWarnings("unused")
                @Override
                public void onError(final Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @SuppressWarnings("unused")
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

            if (baseResponse == null)       // should never happen
                super.onResult(null);
            else {
                final Throwable error = baseResponse.getErrorOrThrowable();
                if (error != null)
                    onError(error);
                else {
                    final D result = baseResponse.getResult();
                    if (result == null && !isNullable()) {
                        CoreLogger.logError("BaseResponse.getResult() is null");
                        super.onResult(null);
                    }
                    else
                        super.onResult(baseResponse);
                }
            }
        }
    }
}
