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

package akha.yakhont.loader;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseCacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterWrapper;
import akha.yakhont.adapter.BaseCacheAdapter.DataBindingCacheAdapterWrapper;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.ViewHolderCreator;
import akha.yakhont.adapter.ValuesCacheAdapterWrapper;
import akha.yakhont.fragment.WorkerFragment;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.LoaderBuilder;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoaderFactory;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.BaseResponseLoaderBuilder;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.retrofit.BaseRetrofit;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.Size;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.GridView;
import android.widget.ListView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Provider;

/**
 * A class that performs asynchronous loading of data. After loading completion, the registered callback will be called.
 * See {@link #setCallback(Object) setCallback()}.
 *
 * @param <C>
 *        The type of callback
 *
 * @param <R>
 *        The type of network response
 *
 * @param <E>
 *        The type of error (if any)
 *
 * @param <D>
 *        The type of data in this loader
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public abstract class BaseLoader<C, R, E, D> extends Loader<BaseResponse<R, E, D>> {

    private final static    String                  FORMAT_ERROR                = "%s (%s)";
    private final static    String                  FORMAT_INFO                 = "id = %d (%s)";
    private final static    String                  FORMAT_ADD                  = "%s, %s";

    @StringRes
    private static final    int                     ERROR_RES_ID                = akha.yakhont.R.string.yakhont_loader_error;
    @StringRes
    private static final    int                     ERROR_NETWORK_RES_ID        = akha.yakhont.R.string.yakhont_loader_error_network;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final         String                  mNetworkMessage;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final         String                  mErrorMessage;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final         String                  mDescription;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final         String                  mLogDescription;

    private   final         boolean                 mSilent;

    private                 int                     mTimeout                    = -1;

    private                 C                       mCallback;

    private                 BaseResponse<R, E, D>   mResult;

    private                 ProgressWrapper         mProgress;

    /**
     * Initialises a newly created {@code BaseLoader} object.
     *
     * @param context
     *        The context
     */
    @SuppressWarnings("unused")
    public BaseLoader(@NonNull final Context context) {
        this(context, null);
    }

    /**
     * Initialises a newly created {@code BaseLoader} object.
     *
     * @param context
     *        The context
     *
     * @param description
     *        The data description (for GUI)
     */
    @SuppressWarnings("WeakerAccess")
    public BaseLoader(@NonNull final Context context,
                      @SuppressWarnings("SameParameterValue") final String description) {
        this(context, description, null);
    }

    /**
     * Initialises a newly created {@code BaseLoader} object.
     *
     * @param context
     *        The context
     *
     * @param description
     *        The data description (for GUI)
     *
     * @param logDescription
     *        The data description (for logging)
     */
    @SuppressWarnings("WeakerAccess")
    public BaseLoader(@NonNull final Context context,
                      final String description, final String logDescription) {
        this(context, description, logDescription, false);
    }

    /**
     * Initialises a newly created {@code BaseLoader} object.
     *
     * @param context
     *        The context
     *
     * @param resId
     *        The resource ID of the data description (for GUI)
     */
    @SuppressWarnings("unused")
    public BaseLoader(@NonNull final Context context,
                      @StringRes final int resId) {
        this(context, resId, null);
    }

    /**
     * Initialises a newly created {@code BaseLoader} object.
     *
     * @param context
     *        The context
     *
     * @param resId
     *        The resource ID of the data description (for GUI)
     *
     * @param logDescription
     *        The data description (for logging)
     */
    @SuppressWarnings("WeakerAccess")
    public BaseLoader(@NonNull final Context context,
                      @StringRes final int resId, @SuppressWarnings("SameParameterValue") final String logDescription) {
        this(context, context.getString(resId), logDescription, false);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public BaseLoader(@NonNull final Context context,
                      final String description, final String logDescription,
                      @SuppressWarnings("SameParameterValue") final boolean silent) {
        super(context);

        mSilent             = silent;

        mDescription        = description;
        mLogDescription     = logDescription;

        mNetworkMessage     = formatError(context, ERROR_NETWORK_RES_ID,     description);
        mErrorMessage       = formatError(context, ERROR_RES_ID,             description);
    }

    private String formatError(@NonNull final Context context,
                               @StringRes final int resId, final String description) {
        String error = context.getString(resId);
        return description == null ? error: String.format(FORMAT_ERROR, error, description);
    }

    /**
     * Sets component to display progress.
     *
     * @param progress
     *        The progress component
     *
     * @return  This {@code BaseLoader} object
     */
    @SuppressWarnings("UnusedReturnValue")
    public BaseLoader<C, R, E, D> setProgress(final ProgressWrapper progress) {
        mProgress = progress;
        return this;
    }

    /**
     * Sets callback component. For example:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * setCallback(new Callback&lt;D&gt;() {
     *
     *     &#064;Override
     *     public void success(D result, Response response) {
     *         loader.callbackHelper(true, new BaseResponse&lt;...&gt;(...));
     *     }
     *
     *     &#064;Override
     *     public void failure(RetrofitError error) {
     *         loader.callbackHelper(false, new BaseResponse&lt;...&gt;(...));
     *     }
     * });
     * </pre>
     *
     * @param callback
     *        The callback component
     *
     * @return  This {@code BaseLoader} object
     *
     * @see #callbackHelper callbackHelper
     * @see BaseResponse
     */
    @SuppressWarnings("UnusedReturnValue")
    public BaseLoader<C, R, E, D> setCallback(final C callback) {
        mCallback = callback;
        return this;
    }

    /**
     * Sets timeout (in seconds).
     *
     * @param timeout
     *        The timeout
     *
     * @return  This {@code BaseLoader} object
     */
    @SuppressWarnings("UnusedReturnValue")
    public BaseLoader<C, R, E, D> setTimeout(@IntRange(from = 1) final int timeout) {
        mTimeout = timeout;
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starts an asynchronous load.
     */
    @SuppressWarnings("WeakerAccess")
    protected void makeRequest() {
        if (mCallback == null) {
            CoreLogger.logError(addLoaderInfo("mCallback == null"));
            return;
        }
        doProgressSafe(true);

        //noinspection Convert2Lambda
        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    CoreLogger.log(addLoaderInfo("makeRequest"));
                    makeRequest(mCallback);
                }
                catch (Exception exception) {
                    CoreLogger.log(addLoaderInfo("makeRequest failed"), exception);
                    //noinspection Convert2Diamond
                    callbackHelper(false, new BaseResponse<R, E, D>(
                            null, null, null, null, Source.UNKNOWN, exception));
                }
            }

            @NonNull
            @Override
            public String toString() {
                return addLoaderInfo("BaseLoader.makeRequest()");
            }
        });
    }

    /**
     * Subject to call from the callback (see {@link #setCallback(Object) setCallback()}).
     *
     * @param success
     *        {@code true} if data loading completed successfully, {@code false} otherwise
     *
     * @param baseResponse
     *        The results of loading
     */
    public void callbackHelper(final boolean success, @NonNull final BaseResponse<R, E, D> baseResponse) {
        //noinspection Convert2Lambda
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                callback(success, baseResponse);
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseLoader.callback()";
            }
        });
    }

    private void callback(final boolean success, @NonNull final BaseResponse<R, E, D> baseResponse) {
        synchronized (mTimeoutLock) {
            final boolean waiting = mFuture != null;
            CoreLogger.log(waiting ? Level.DEBUG: Level.ERROR, addLoaderInfo(
                    "success " + success + ", waiting " + waiting));
            if (!waiting) {
                if (sRespectWaiting.get()) return;
                CoreLogger.logWarning(addLoaderInfo(
                        "Results were delivered while not waiting; anyway, results accepted. " +
                                "To change this behaviour, use setWaitingMode() method."));
            }
        }
        CoreLogger.log(addLoaderInfo("proceed"));

        doProgressSafe(false);

        if (success) {
            onSuccess(baseResponse);
            return;
        }

        final E error = baseResponse.getError();
        if (error != null) {
            if (error instanceof Throwable)
                logError((Throwable) error);
            else
                CoreLogger.logError(addLoaderInfo(error.toString()));
        }
        final Throwable throwable = baseResponse.getThrowable();
        if (throwable != null) logError(throwable);

        if (error == null && throwable == null)
            CoreLogger.logError(addLoaderInfo("error == null"));

        displayErrorSafe(makeErrorMessage(baseResponse));

        onFailure(baseResponse);
    }

    private void logError(@NonNull final Throwable throwable) {
        CoreLogger.logError(addLoaderInfo("loader failed"), throwable);
    }

    private void displayErrorSafe(@NonNull final String text) {
        try {
            if (!mSilent) displayError(text);
        }
        catch (Exception e) {
            CoreLogger.log(addLoaderInfo("displayErrorSafe failed"), e);
        }
    }

    /**
     * Callback which is called after successful data loading.
     *
     * @param baseResponse
     *        The results of loading
     */
    @MainThread
    @SuppressWarnings("WeakerAccess")
    protected void onSuccess(@NonNull final BaseResponse<R, E, D> baseResponse) {
        baseResponse.setValues(null);
        onCallback(baseResponse);
    }

    /**
     * Callback which is called after NOT successful data loading.
     *
     * @param baseResponse
     *        The results of loading
     */
    @MainThread
    @SuppressWarnings("WeakerAccess")
    protected void onFailure(@NonNull final BaseResponse<R, E, D> baseResponse) {
        onCallback(baseResponse);
    }

    @MainThread
    private void onCallback(@NonNull final BaseResponse<R, E, D> baseResponse) {
        deliver(baseResponse);
    }

    /**
     * Delivers the results of loading.
     *
     * @param baseResponse
     *        The results
     */
    @SuppressWarnings("WeakerAccess")
    protected void deliver(@NonNull final BaseResponse<R, E, D> baseResponse) {
        CoreLogger.log(addLoaderInfo("deliver"));

        postDeliverResult(baseResponse);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starts an asynchronous load.
     *
     * @param callback
     *        The callback
     */
    protected abstract void makeRequest(@NonNull final C callback);

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void displayError(@NonNull final String text) {
        if (mProgress != null) mProgress.displayError(text);
    }

    /** @exclude */
    @NonNull
    @SuppressWarnings({"JavaDoc", "UnusedParameters", "WeakerAccess"})
    protected String makeErrorMessage(@NonNull final BaseResponse<R, E, D> baseResponse) {
        return mNetworkMessage;
    }

    /** @exclude */
    @NonNull
    @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
    protected String makeErrorMessage() {
        return mErrorMessage;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void doProgress(final boolean show) {
        if (mProgress != null) mProgress.doProgress(show, mDescription);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void doProgressSafe(final boolean show, final boolean cancel) {
        try {
            doProgressTimer(show, cancel);
            if (!mSilent) doProgress(show);
        }
        catch (Exception e) {
            CoreLogger.log(addLoaderInfo("show = " + show), e);
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void doProgressSafe(final boolean show) {
        doProgressSafe(show, true);
    }

    private static final boolean        MAY_INTERRUPT_IF_RUNNING    = true;

    private final        Object         mTimeoutLock                = new Object();
    private              Future<?>      mFuture;
    private static final AtomicBoolean  sRespectWaiting             = new AtomicBoolean(true);

    /**
     * Sets waiting mode: {@code true} to ignore delivered results if not waiting for them;
     * {@code false} otherwise. The default value is {@code true}.
     *
     * @param value
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static boolean setWaitingMode(final boolean value) {
        return sRespectWaiting.getAndSet(value);
    }

    private void doProgressTimer(final boolean start, final boolean cancel) {
        synchronized (mTimeoutLock) {
            doProgressTimerAsync(start, cancel);
        }
    }

    private void doProgressTimerAsync(final boolean start, final boolean cancel) {
        if (mFuture != null) {
            final boolean result = !cancel || mFuture.cancel(MAY_INTERRUPT_IF_RUNNING);
            mFuture = null;

            if (start)   CoreLogger.logError(addLoaderInfo("previous timeout task is not null"));
            if (!result) CoreLogger.logError(addLoaderInfo("timeout task cancel problem"));
        }
        if (!start) return;

        if (mTimeout < 0) {
            CoreLogger.logError(addLoaderInfo("mTimeout < 0"));
            return;
        }

        // stop show loading progress after TIMEOUT_CONNECTION_TIMER seconds of connection timeout
        // (normally should never happen)
        final int timeout = (mTimeout + Core.TIMEOUT_CONNECTION_TIMER) * 1000;

        mFuture = Utils.runInBackground(timeout, new Runnable() {
            @Override
            public void run() {
                CoreLogger.log(Level.ERROR, addLoaderInfo("forced to stop"), false);
                doProgressSafe(false, false);

                //noinspection Convert2Lambda
                Utils.postToMainLoop(new Runnable() {
                    @Override
                    public void run() {
                        //noinspection Convert2Diamond
                        onFailure(new BaseResponse<R, E, D>(Source.TIMEOUT));
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "BaseLoader.onFailure()";
                    }
                });
            }

            @NonNull
            @Override
            public String toString() {
                return addLoaderInfo("loader timeout");
            }
        });
    }

    private void postDeliverResult(@NonNull final BaseResponse<R, E, D> result) {
        //noinspection Convert2Lambda
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                deliverResult(result);
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseLoader.deliverResult()";
            }
        });
    }

    /** @exclude */
    @NonNull
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected String addLoaderInfo(final String str) {
        final String info = toString();
        //noinspection ConstantConditions
        return str == null ? info: String.format(FORMAT_ADD, str, info);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public boolean isEmpty(final D result) {
        return result == null || (result.getClass().isArray() && ((Object[]) result).length == 0);
    }

    /**
     * Destroys loaders.
     *
     * @param loaderManager
     *        The loader manager
     *
     * @param ids
     *        The list of loader's IDs
     *
     * @param forceDestroy
     *        Force to destroy all (including already started) loaders
     *
     * @return  The quantity of destroyed loaders
     */
    @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
    public static int destroyLoaders(final LoaderManager loaderManager, final Collection<Integer> ids,
                                     final boolean forceDestroy) {
        if (ids == null) {
            CoreLogger.logError("loaderIds == null");
            return 0;
        }
        CoreLogger.log("loader ids qty " + ids.size());

        int qty = 0;
        for (final int i: ids)
            if (destroyLoader(loaderManager, i, forceDestroy)) qty++;

        CoreLogger.log(qty + " loader" + (qty != 1 ? "s": "") + " has been destroyed");
        return qty;
    }

    /**
     * Destroys loader with the given ID.
     *
     * @param loaderManager
     *        The loader manager
     *
     * @param id
     *        The loader ID
     *
     * @param forceDestroy
     *        Force to destroy loader even if it was started
     *
     * @return  {@code true} if loader was successfully destroyed, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean destroyLoader(final LoaderManager loaderManager, final int id,
                                        final boolean forceDestroy) {
        if (loaderManager == null) {
            CoreLogger.logError("loaderManager == null");
            return false;
        }

        final Loader loader = loaderManager.getLoader(id);
        if (loader == null) {
            CoreLogger.logError("can't find loader wth ID " + id);
            return false;
        }

        final boolean started = loader.isStarted();
        if (started && !forceDestroy) {
            CoreLogger.logError("can't destroy started loader " + loader);
            return false;
        }

        CoreLogger.log(started ? Level.WARNING: Level.DEBUG, "about to destroy " +
                (started ? "started ": "") + "loader " + loader);

        //noinspection Convert2Lambda
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                loaderManager.destroyLoader(id);
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseLoader.destroyLoader()";
            }
        });

        return true;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void enableLoaderManagerDebugLogging(final boolean enable) {
        LoaderManager.enableDebugLogging(enable);
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public String toString() {
        return String.format(Utils.getLocale(), FORMAT_INFO, getId(),
                !TextUtils.isEmpty(mLogDescription) ? mLogDescription:
                        !TextUtils.isEmpty(mDescription) ? mDescription: "description N/A");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // based on LoaderCustom from samples

    /**
     * Please refer to the base method description.
     */
    @MainThread
    @Override
    public void deliverResult(BaseResponse<R, E, D> data) {
        if (isReset())      // An async query came in while the loader is stopped. Result is not needed.
            releaseResources(data);

        final BaseResponse<R, E, D> prevData = mResult;
        mResult = data;

        if (isStarted())    // If the loader is currently started, we can immediately deliver its results.
            super.deliverResult(data);

        releaseResources(prevData);
    }

    // from docs: "Subclasses generally must implement at least onStartLoading(), onStopLoading(), onForceLoad(), and onReset()."

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void onStartLoading() {
        //noinspection Convert2Lambda
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                if (mResult != null)    // If we currently have a result available, deliver it immediately.
                    deliverResult(mResult);

                if (isReload() || mResult == null)
                    forceLoad();
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseLoader.onStartLoading()";
            }
        });
    }

    /**
     * Please refer to the base method description.
     */
    @MainThread
    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoadHelper();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void cancelLoadHelper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) cancelLoad();
    }

    /**
     * Please refer to the base method description.
     */
    @MainThread
    @Override
    protected void onForceLoad() {
        super.onForceLoad();
        cancelLoadHelper();

        makeRequest();
    }

    /**
     * Please refer to the base method description.
     */
    @MainThread
    @Override
    protected void onReset() {
        super.onReset();

        onStopLoading();

        releaseResources(mResult);
        mResult = null;

        doProgressSafe(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess", "SameReturnValue"})
    protected boolean isReload() {
        // e.g. if the data has changed since the last time it was loaded, start a load.
        return false;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess", "EmptyMethod", "UnusedParameters"})
    protected void releaseResources(final BaseResponse<R, E, D> data) {
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the {@code CoreLoad} component for the given fragment.
     *
     * @param fragment
     *        The fragment
     *
     * @return  The {@code CoreLoad}
     */
    public static CoreLoad getCoreLoad(final Fragment fragment) {
        return WorkerFragment.findInstance(fragment);
    }

    /**
     * Returns the {@code CoreLoad} component for the given activity.
     *
     * @param activity
     *        The activity
     *
     * @return  The {@code CoreLoad}
     */
    public static CoreLoad getCoreLoad(final Activity activity) {
        return WorkerFragment.findInstance(activity);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The <code>ProgressWrapper</code> class is intended to display a data loading progress to the user.
     */
    public static class ProgressWrapper {

        private final   WeakReference<Fragment>           mFragment;
        private final   boolean                           mNoProgress, mNoError;
        private final   AtomicBoolean                     mProgressShown        = new AtomicBoolean();
        private final   Provider<BaseDialog>              mToast;

        /**
         * Initialises a newly created {@code ProgressWrapper} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param noProgress
         *        {@code true} to NOT display progress, {@code false} otherwise
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        public ProgressWrapper(@NonNull final WeakReference<Fragment> fragment,
                               final boolean noProgress) {
            this(fragment, noProgress, false);
        }

        /**
         * Initialises a newly created {@code ProgressWrapper} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param noProgress
         *        {@code true} to not display progress, {@code false} otherwise
         *
         * @param noError
         *        {@code true} to not display errors, {@code false} otherwise
         */
        @SuppressWarnings("WeakerAccess")
        public ProgressWrapper(@NonNull final WeakReference<Fragment> fragment,
                               final boolean noProgress, final boolean noError) {
            mFragment       = fragment;

            mNoProgress     = noProgress;
            mNoError        = noError;

            mToast          = noError ? null: Core.getDagger().getToastLong();
        }

        /**
         * Displays or hides progress.
         *
         * @param show
         *        {@code true} to display progress, {@code false} to hide
         *
         * @param description
         *        The text to display
         */
        @SuppressWarnings("WeakerAccess")
        public void doProgress(final boolean show, final String description) {
            if (mNoProgress) return;

            final CoreLoad coreLoad = getCoreLoad(mFragment.get());
            if (coreLoad == null) return;

            if (show)
                coreLoad.showProgress(description);
            else
                if (mProgressShown.get()) coreLoad.hideProgress(false);

            mProgressShown.set(show);
        }

        /**
         * Displays error.
         *
         * @param text
         *        The text to display
         */
        @SuppressWarnings("WeakerAccess")
        public void displayError(@NonNull final String text) {
            if (mNoError) return;

            final Fragment fragment = mFragment.get();

            if (fragment == null)
                CoreLogger.logError("fragment == null");
            else
                mToast.get().start(fragment.getActivity(), text, null);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Callback for a client to interact with loader manager. Extended version of the
     * {@link android.app.LoaderManager.LoaderCallbacks}.
     *
     * @param <C>
     *        The type of callback
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data
     */
    @SuppressWarnings("unused")
    public static abstract class LoaderCallback<C, R, E, D>
            implements LoaderManager.LoaderCallbacks<BaseResponse<R, E, D>> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected BaseResponseLoaderWrapper<C, R, E, D> mLoaderWrapper;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public void setLoaderWrapper(final BaseResponseLoaderWrapper<C, R, E, D> loaderWrapper) {
            mLoaderWrapper = loaderWrapper;
        }

        /**
         * Returns the owner of the given object.
         *
         * @return  The BaseResponseLoaderWrapper container for this object
         */
        public BaseResponseLoaderWrapper<C, R, E, D> getLoaderWrapper() {
            return mLoaderWrapper;
        }

        /**
         * Provides the possibility to customize the loader.
         *
         * @param loader
         *        The loader to use
         *
         * @return  The customized loader (default implementation returns the original one)
         */
        public Loader<BaseResponse<R, E, D>> onCreateLoader(final Loader<BaseResponse<R, E, D>> loader) {
            return loader;
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("SameReturnValue")
        @Override
        public Loader<BaseResponse<R, E, D>> onCreateLoader(final int id, final Bundle args) {
            return null;
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("EmptyMethod")
        @Override
        public void onLoaderReset(final Loader<BaseResponse<R, E, D>> loader) {
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("EmptyMethod")
        @Override
        public void onLoadFinished(final Loader<BaseResponse<R, E, D>> loader,
                                   final BaseResponse<R, E, D> data) {
        }

        /**
         * Called when a loader finished its load. Please refer to
         * {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()} for more details.
         *
         * @param data
         *        The data generated by the Loader
         *
         * @param source
         *        The data source
         */
        @SuppressWarnings({"UnusedParameters", "EmptyMethod"})
        public void onLoadFinished(final D data, final Source source) {
        }

        /**
         * Called when a loader finished with error.
         *
         * @param error
         *        The error
         *
         * @param source
         *        The data source
         */
        @SuppressWarnings("EmptyMethod")
        public void onLoadError(final E error, final Source source) {
        }
    }

    /**
     * Simplified version of the {@link LoaderCallback}.
     *
     * @param <D>
     *        The type of data
     */
    @SuppressWarnings("WeakerAccess")
    public interface LoaderFinishedCallback<D> {

        /**
         * Called when a loader finished its load. Please refer to
         * {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()} for more details.
         *
         * @param data
         *        The data generated by the Loader
         *
         * @param source
         *        The data source
         */
        void onLoadFinished(final D data, final Source source);
    }

    private static <S> void checkData(final S valueOwn, final S valueBuilder,
                                      @NonNull final String txt) {
        if (valueOwn != null)
            CoreLogger.log(valueBuilder == null ? Level.WARNING: Level.ERROR,
                    String.format("The %s is already set", txt));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link CoreLoad} objects.
     *
     * @param <C>
     *        The type of callback
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data
     */
    public static class CoreLoadBuilder<C, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final WeakReference<Fragment>         mFragment;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderRx<R, E, D>                     mRx;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderBuilder<C, R, E, D>             mLoaderBuilder;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected BaseCacheAdapterWrapper<?, R, E, D>
                                                        mAdapterWrapper;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ViewBinder                            mViewBinder;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ViewHolderCreator                     mViewHolderCreator;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @IdRes
        protected int                                   mListViewId     = Core.NOT_VALID_VIEW_ID;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @LayoutRes
        protected int                                   mLayoutItemId   = Core.NOT_VALID_RES_ID;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected boolean                               mNoBinding;

        /**
         * Initialises a newly created {@code CoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         */
        @SuppressWarnings("WeakerAccess")
        public CoreLoadBuilder(@NonNull final Fragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        /**
         * Sets the Rx component.
         *
         * @param rx
         *        The Rx component
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setRx(final LoaderRx<R, E, D> rx) {
            checkData(mRx, rx, "Rx");
            mRx = rx;
            return this;
        }

        /**
         * Sets the loader builder component.
         *
         * @param loaderBuilder
         *        The loader builder component
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        public CoreLoadBuilder<C, R, E, D> setLoaderBuilder(final LoaderBuilder<C, R, E, D> loaderBuilder) {
            checkData(mLoaderBuilder, loaderBuilder, "loader builder");
            mLoaderBuilder = loaderBuilder;
            return this;
        }

        /**
         * Sets the adapter wrapper component.
         *
         * @param adapterWrapper
         *        The adapter wrapper component
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"UnusedReturnValue", "unused"})
        public <S> CoreLoadBuilder<C, R, E, D> setAdapterWrapper(
                final BaseCacheAdapterWrapper<S, R, E, D> adapterWrapper) {
            checkData(mAdapterWrapper, adapterWrapper, "adapter wrapper");
            mAdapterWrapper = adapterWrapper;
            return this;
        }

        /**
         * Sets the view binder component.
         *
         * @param viewBinder
         *        The view binder component
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setViewBinder(final ViewBinder viewBinder) {
            checkData(mViewBinder, viewBinder, "ViewBinder");
            mViewBinder = viewBinder;
            return this;
        }

        /**
         * Sets the ViewHolder creator component.
         *
         * @param viewHolderCreator
         *        The ViewHolder creator component
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setViewHolderCreator(final ViewHolderCreator viewHolderCreator) {
            checkData(mViewHolderCreator, viewHolderCreator, "ViewHolder creator");
            mViewHolderCreator = viewHolderCreator;
            return this;
        }

        /**
         * Sets the {@link ListView}, {@link GridView} or {@link RecyclerView} ID (for data binding).
         * <p>
         * If a view ID was not set, the implementation looks for the first {@link ListView}, {@link GridView} or
         * {@link RecyclerView} in the fragment's root view.
         *
         * @param listViewId
         *        The resource identifier of a {@link ListView}, {@link GridView} or {@link RecyclerView}
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setListView(@IdRes final int listViewId) {
            if (mListViewId != Core.NOT_VALID_VIEW_ID)
                CoreLogger.log(Level.ERROR, "The list view id is already set");
            mListViewId = listViewId;
            return this;
        }

        /**
         * Sets the resource identifier of a layout file that defines the views to bind.
         * <p>
         * If a layout ID was not set, the following algorithm will be applied:
         * <ul>
         *   <li>find name of the list, which is the string representation of the list ID;
         *     if the ID was not defined, "list", "grid" or "recycler" will be used
         *   </li>
         *   <li>look for the layout with ID == name + "_item";
         *     if not found, look for the layout with ID == name
         *   </li>
         * </ul>
         *
         * @param layoutItemId
         *        The resource identifier of a layout file that defines the views to bind
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setListItem(@LayoutRes final int layoutItemId) {
            if (mLayoutItemId != Core.NOT_VALID_RES_ID)
                CoreLogger.log(Level.ERROR, "The layout item id is already set");
            mLayoutItemId = layoutItemId;
            return this;
        }

        /**
         * Prevents component from binding loaded data (ignored in case of Data Binding Library).
         *
         * @param noBinding
         *        {@code true} to just load data, without any default binding (default value is {@code false})
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setNoBinding(final boolean noBinding) {
            mNoBinding = noBinding;
            return this;
        }

        @LayoutRes
        @SuppressWarnings("SameParameterValue")
        private int getItemLayout(@NonNull final Resources resources, @NonNull final View list,
                                  @NonNull final String defType, @NonNull final String defPackage) {

            final String name = list.getId() != Core.NOT_VALID_VIEW_ID ? resources.getResourceEntryName(list.getId()):
                    list instanceof RecyclerView ? "recycler": list instanceof GridView ? "grid": "list";

            @LayoutRes final int id = resources.getIdentifier(name + "_item", defType, defPackage);
            return id != Core.NOT_VALID_RES_ID ? id: resources.getIdentifier(name, defType, defPackage);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedParameters", "WeakerAccess"})
        protected void customizeAdapterWrapper(@NonNull final CoreLoad coreLoad, @NonNull final View root,
                                               @NonNull final View list, @LayoutRes final int item) {
        }

        /**
         * Returns the {@link CoreLoad} with the arguments supplied to this builder.
         *
         * @return  The {@code CoreLoad} object
         */
        public CoreLoad create() {
            if (mLoaderBuilder == null) {
                CoreLogger.logError("The loader builder is null");
                return null;
            }

            final Fragment fragment = mFragment.get();
            if (fragment == null) {
                CoreLogger.logError("The fragment is null");
                return null;
            }

            final CoreLoad coreLoad = getCoreLoad(fragment);
            if (coreLoad == null) return null;

            if (mNoBinding) {
                final String errText = "'no default binding' mode set, so %s will be ignored";

                if (mListViewId     != Core.NOT_VALID_VIEW_ID)
                    CoreLogger.logWarning(String.format(errText, "list ID " + mListViewId));

                if (mLayoutItemId   != Core.NOT_VALID_RES_ID)
                    CoreLogger.logWarning(String.format(errText, "list item layout ID " +
                            mLayoutItemId));

                if (mAdapterWrapper != null) {
                    CoreLogger.logWarning(String.format(errText, "adapter wrapper " +
                            mAdapterWrapper.getClass().getName()));

                    mAdapterWrapper = null;
                }
            }
            else
                if (!create(fragment, coreLoad)) return null;

            final boolean result = coreLoad.addLoader(getLoader(), true);
            CoreLogger.log(result ? Level.DEBUG: Level.ERROR, "add loader result == " + result);

            return coreLoad;
        }

        private BaseLoaderWrapper<?> getLoader() {
            final BaseResponseLoaderWrapper<C, R, E, D> loader = mLoaderBuilder.createBaseResponseLoader();
            if (loader == null) return mLoaderBuilder.createBaseLoader();

            if (mRx             != null) loader.setRx(mRx);
            if (mAdapterWrapper != null) loader.setAdapter(mAdapterWrapper);
            return loader;
        }

        private boolean create(final Fragment fragment, final CoreLoad coreLoad) {
            View list = null;

            final View root = fragment.getView();
            if (root == null)
                CoreLogger.logWarning("The fragment's root view is null");
            else
                list = mListViewId == Core.NOT_VALID_VIEW_ID ?
                        BaseCacheAdapter.findListView(root): root.findViewById(mListViewId);

            if (list == null) {
                if (mListViewId != Core.NOT_VALID_VIEW_ID)
                    CoreLogger.logError("view with id " + mListViewId + " was not found");
                else
                    CoreLogger.logWarning("no ListView, GridView or RecyclerView found for default binding");
            }

            @LayoutRes int itemId = mLayoutItemId;
            if (itemId == Core.NOT_VALID_RES_ID && list != null)
                itemId = getItemLayout(fragment.getResources(), list, "layout", fragment.getActivity().getPackageName());

            if (itemId == Core.NOT_VALID_RES_ID)
                CoreLogger.logWarning("no list item layout ID found for default binding");
            else
                CoreLogger.log("list item layout ID: " + itemId);

            if (list != null && itemId != Core.NOT_VALID_RES_ID)
                customizeAdapterWrapper(coreLoad, root, list, itemId);

            if (mAdapterWrapper == null)
                CoreLogger.logWarning("The adapter wrapper is null, so no data binding will be done");
            else {
                if (mViewBinder != null) mAdapterWrapper.setAdapterViewBinder(mViewBinder);

                if (mViewHolderCreator != null)
                    mAdapterWrapper.getRecyclerViewAdapter().setViewHolderCreator(mViewHolderCreator);

                if      (list instanceof ListView)
                    ((ListView) list).setAdapter(mAdapterWrapper.getAdapter());
                else if (list instanceof GridView)
                    ((GridView) list).setAdapter(mAdapterWrapper.getAdapter());
                else if (list instanceof RecyclerView)
                    ((RecyclerView) list).setAdapter(mAdapterWrapper.getRecyclerViewAdapter());
                else {
                    CoreLogger.logError("view with id " + mListViewId +
                            " should be instance of ListView, GridView or RecyclerView");
                    return false;
                }
            }

            return true;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link CoreLoad} objects. For the moment just contains some common code
     * for {@link BaseResponseLoaderBuilder} customization.
     *
     * @param <C>
     *        The type of callback
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
     * @param <T>
     *        The type of API
     */
    public static abstract class CoreLoadExtendedBuilder<C, R, E, D, T> extends CoreLoadBuilder<C, R, E, D>
            implements Requester<C> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Type                                  mType;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderCallback<C, R, E, D>            mLoaderCallback;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Integer                               mDataBindingId;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @Size(min = 1)
        protected String[]                              mFrom;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @Size(min = 1)
        protected int[]                                 mTo;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected String                                mDescription;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @StringRes
        protected Integer                               mDescriptionId  = Core.NOT_VALID_RES_ID;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Requester<T>                          mRequester;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Requester<C>                          mDefaultRequester;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final Object                          mLockRequester  = new Object();

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Converter<D>                          mConverter;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected String                                mTableName;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Integer                               mLoaderId;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected UriResolver                           mUriResolver;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderFactory<BaseResponse<R, E, D>>  mLoaderFactory;

        /**
         * Initialises a newly created {@code CoreLoadExtendedBuilder} object.
         *
         * @param fragment
         *        The fragment
         */
        @SuppressWarnings("unused")
        protected CoreLoadExtendedBuilder(@NonNull final Fragment fragment) {
            super(fragment);
        }

        /**
         * Sets the data type; for collections ({@code List}, {@code Set}, {@code Queue})
         * please use {@code TypeToken} (e.g. from Gson). Usage examples:
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * import com.google.gson.reflect.TypeToken;
         *
         * setType(YourData[].class);
         *
         * setType(new TypeToken&lt;List&lt;YourData&gt;&gt;() {}.getType());
         * </pre>
         *
         * @param type
         *        The data type
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setType(final Type type) {
            checkData(mType, type, "type");
            mType = type;
            return this;
        }

        /**
         * Sets the data binding (for the Data Binding Library).
         *
         * @param id
         *        The BR id of the variable to be set
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         *
         * @see android.databinding.ViewDataBinding#setVariable ViewDataBinding.setVariable()
         */
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setDataBinding(@NonNull final Integer id) {
            checkData(mDataBindingId, id, "data binding id");
            mDataBindingId = id;
            return this;
        }

        /**
         * Sets the data binding.
         *
         * @param from
         *        The list of names representing the data to bind to the UI
         *
         * @param to
         *        The views that should display data in the "from" parameter
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setDataBinding(@NonNull @Size(min = 1) final String[] from,
                                                                     @NonNull @Size(min = 1) final    int[] to) {
            checkData(mFrom, from, "from data binding");
            checkData(mTo  , to  , "to data binding");

            mFrom = from;
            mTo   = to;

            return this;
        }

        /**
         * Sets the data description.
         *
         * @param description
         *        The data description
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setDescription(final String description) {
            checkData(mDescription, description, "description");
            mDescription = description;
            return this;
        }

        /**
         * Sets the data description ID.
         *
         * @param descriptionId
         *        The data description ID
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setDescriptionId(@StringRes final int descriptionId) {
            if (mDescriptionId != Core.NOT_VALID_RES_ID)
                CoreLogger.log(Level.ERROR, "The description id is already set");
            mDescriptionId = descriptionId;
            return this;
        }

        /**
         * Sets the table name.
         *
         * @param tableName
         *        The name of the table in the database (to cache the loaded data)
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setTableName(@NonNull final String tableName) {
            checkData(mTableName, tableName, "table name");
            mTableName = tableName;
            return this;
        }

        /**
         * Sets the loader ID.
         *
         * @param loaderId
         *        The loader ID
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderId(final int loaderId) {
            if (mLoaderId != null)
                CoreLogger.log(Level.ERROR, "The loader id is already set");
            mLoaderId = loaderId;
            return this;
        }

        /**
         * Sets the URI resolver.
         *
         * @param uriResolver
         *        The URI resolver
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setUriResolver(@NonNull final UriResolver uriResolver) {
            checkData(mUriResolver, uriResolver, "URI resolver");
            mUriResolver = uriResolver;
            return this;
        }

        /**
         * Sets the loader factory.
         *
         * @param loaderFactory
         *        The loader factory
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderFactory(@NonNull final LoaderFactory<BaseResponse<R, E, D>> loaderFactory) {
            checkData(mLoaderFactory, loaderFactory, "loader factory");
            mLoaderFactory = loaderFactory;
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setRx(final LoaderRx<R, E, D> rx) {
            super.setRx(rx);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setViewBinder(final ViewBinder viewBinder) {
            super.setViewBinder(viewBinder);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setViewHolderCreator(final ViewHolderCreator viewHolderCreator) {
            super.setViewHolderCreator(viewHolderCreator);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setListView(final int listViewId) {
            super.setListView(listViewId);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setListItem(final int layoutItemId) {
            super.setListItem(layoutItemId);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setNoBinding(final boolean noBinding) {
            super.setNoBinding(noBinding);
            return this;
        }

        /**
         * Sets the adapter wrapper component.
         *
         * @param adapterWrapper
         *        The adapter wrapper component
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @Override
        public <S> CoreLoadExtendedBuilder<C, R, E, D, T> setAdapterWrapper(
                final BaseCacheAdapterWrapper<S, R, E, D> adapterWrapper) {
            super.setAdapterWrapper(adapterWrapper);
            return this;
        }

        /**
         * Sets the loader callback component.
         *
         * @param callback
         *        The loader callback component
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("WeakerAccess")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderCallback(final LoaderCallback<C, R, E, D> callback) {
            checkData(mLoaderCallback, callback, "loader callback");
            mLoaderCallback = callback;
            return this;
        }

        /**
         * Sets the loader callback component.
         *
         * @param callback
         *        The loader callback component
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderCallback(final LoaderFinishedCallback<D> callback) {
            return setLoaderCallback(new LoaderCallback<C, R, E, D>() {
                @SuppressWarnings("unused")
                @Override
                public void onLoadFinished(final D data, final Source source) {
                    callback.onLoadFinished(data, source);
                }
            });
        }

        /**
         * Sets the converter.
         *
         * @param converter
         *        The converter
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setConverter(final Converter<D> converter) {
            checkData(mConverter, converter, "converter");
            mConverter = converter;
            return this;
        }

        /**
         * Sets the requester (most of the time - just some method from your Retrofit API).
         * Usage examples:
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * import com.yourpackage.retrofit.YourRetrofit;
         *
         * import akha.yakhont.Core;
         *
         * // for methods without parameters
         * setRequester(YourRetrofit::yourMethod);
         *
         * // for methods with parameters
         * setRequester(yourRetrofit -&gt; yourRetrofit.yourMethod("your parameter"));
         *
         * // for methods with parameters (Java 7 style)
         * setRequester(new Core.Requester&lt;YourRetrofit&gt;() {
         *     &#064;Override
         *     public void makeRequest(YourRetrofit yourRetrofit) {
         *         yourRetrofit.yourMethod("your parameter");
         *     }
         * });
         * </pre>
         *
         * @param requester
         *        The requester
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         *
         * @see     #makeRequest(Object)
         */
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setRequester(final Requester<T> requester) {
            checkData(mRequester, requester, "requester");
            mRequester = requester;
            return this;
        }

        /**
         * Starts an asynchronous data loading. Usage examples ('raw calls' means - without default
         * Yakhont pre- and postprocessing):
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * import com.yourpackage.model.YourData;
         * import com.yourpackage.retrofit.YourRetrofit;
         *
         * import akha.yakhont.technology.rx.Rx;
         * import akha.yakhont.technology.rx.Rx2;
         *
         * // for typical Retrofit2 (Rx2 / Rx / Call) - but for such simple Retrofit2 calls
         * //   it's better to use 'setRequester(YourRetrofit::getDataRx)'
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getApi(callback).getDataRx();
         * }
         *
         * // for raw Retrofit2 Call ('getApi()' takes null)
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getApi(null).getData("your parameter").enqueue(callback);
         * }
         *
         * // for raw Retrofit2 Rx2  ('getApi()' takes null)
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getRx2DisposableHandler().add(Rx2.handle(
         *         getApi(null).getDataRx(), getRxWrapper(callback)));
         * }
         *
         * // for raw Retrofit2 Rx   ('getApi()' takes null)
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getRxSubscriptionHandler().add(Rx.handle(
         *         getApi(null).getDataOldRx(), getRxWrapper(callback)));
         * }
         * </pre>
         *
         * Here the <code>YourRetrofit</code> may looks as follows:
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * package com.yourpackage.retrofit;

         * import com.yourpackage.model.YourData;
         *
         * import io.reactivex.Observable;
         *
         * import retrofit2.Call;
         * import retrofit2.http.GET;
         * import retrofit2.http.Query;
         *
         * public interface YourRetrofit {
         *
         *     &#064;GET("/data")  // Flowable, Maybe and Single works too
         *     Observable&lt;YourData[]&gt; getDataRx();
         *
         *     &#064;GET("/data")  // Single works too
         *     rx.Observable&lt;YourData[]&gt; getDataOldRx();
         *
         *     &#064;GET("/data")
         *     Call&lt;YourData[]&gt; getData(&#064;Query("parameter") String parameter);
         * }
         * </pre>
         *
         * Note: for raw calls you should set cache table name and data type
         * (see {@link #setTableName} and {@link #setType}).
         *
         * @param callback
         *        The callback
         *
         * @see     #setRequester
         * @see     Requester#makeRequest(Object)
         */
        @Override
        public void makeRequest(@NonNull final C callback) {
            if (mRequester != null) {
                mRequester.makeRequest(getApi(callback));
                return;
            }
            synchronized (mLockRequester) {
                if (mDefaultRequester == null) mDefaultRequester = mLoaderBuilder.getDefaultRequester();
            }
            if (mDefaultRequester == null)
                CoreLogger.logError("The default requester is null, callback " + callback);
            else
                mDefaultRequester.makeRequest(callback);
        }

        /**
         * Returns the API defined by the service interface (e.g. the Retrofit API).
         *
         * <p>Note: 'raw calls' means - without default Yakhont pre- and postprocessing.
         *
         * @param callback
         *        The loader's callback (or null for raw Retrofit calls)
         *
         * @return  The API
         *
         * @see BaseRetrofit#getApi(Object) BaseRetrofit.getApi()
         */
        @SuppressWarnings("unused")
        public abstract T getApi(final C callback);

        private static String toString(final Object[] data) {
            return Arrays.deepToString(data);
        }

        private static String toString(final int[] data) {
            return Arrays.toString(data);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedParameters"})
        @Override
        protected void customizeAdapterWrapper(@NonNull final CoreLoad coreLoad, @NonNull final View root,
                                               @NonNull final View list, @LayoutRes final int item) {
            if (mFrom != null || mTo != null)
                if (mDataBindingId != null)
                    CoreLogger.logError("DataBinding id == " + mDataBindingId + ", 'from' " +
                            toString(mFrom) + " and 'to' " + toString(mTo) + " binding data will be ignored");
                else if (mFrom == null || mTo == null)
                    CoreLogger.logError("both 'from' " + toString(mFrom) +
                            " and 'to' " + toString(mTo) + " binding data should be defined");
                else if (mFrom.length != mTo.length)
                    CoreLogger.logError("both 'from' " + toString(mFrom) +
                            " and 'to' " + toString(mTo) + " binding data should has same size");

            final Activity activity = mFragment.get().getActivity();

            setAdapterWrapperHelper(mDataBindingId != null ?
                    new DataBindingCacheAdapterWrapper(mDataBindingId, item, activity): mFrom == null ?
                    new ValuesCacheAdapterWrapper<>(activity, item):
                    new ValuesCacheAdapterWrapper<>(activity, item, mFrom, mTo));
        }

        // compiler issue
        private void setAdapterWrapperHelper(
                @NonNull final BaseCacheAdapterWrapper<?, R, E, D> adapterWrapper) {
            super.setAdapterWrapper(adapterWrapper);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected CoreLoad create(BaseResponseLoaderBuilder<C, R, E, D> builder) {
            final Fragment fragment = mFragment.get();
            if (fragment == null) {
                CoreLogger.logError("The fragment is null");
                return null;
            }

            if (mDataBindingId != null) {
                if (mNoBinding) {
                    CoreLogger.logError("The Data Binding Library will be used, " +
                            "'NoBinding' parameter will be ignored");
                    mNoBinding = false;
                }
                if (mViewBinder != null)
                    CoreLogger.logError("The Data Binding Library will be used, " +
                            "'ViewBinder' parameter will be ignored, please use @BindingAdapter");
            }

            if (builder == null) {
                if (mLoaderBuilder != null) {
                    if (mLoaderBuilder instanceof BaseResponseLoaderBuilder)
                        builder = (BaseResponseLoaderBuilder<C, R, E, D>) mLoaderBuilder;
                    else
                        return super.create();
                }
                else {
                    CoreLogger.logError("The loader builder is not defined");
                    return null;
                }
            }
            else {
                if (mLoaderBuilder != null && !mLoaderBuilder.equals(builder))
                    CoreLogger.logWarning("The already set loader builder will be ignored: " + mLoaderBuilder);
                if (!builder.equals(mLoaderBuilder)) setLoaderBuilder(builder);
            }

            if (mDescriptionId != Core.NOT_VALID_RES_ID && mDescription != null)
                CoreLogger.logWarning("Both description and description ID were set; description ID will be ignored");

            if (builder.getRequesterRaw() == null)
                builder.setRequester(this);
            else
                CoreLogger.logWarning("The loader builder requester is already set, " +
                        "so overridden method (if any) 'makeRequest(callback)' will be ignored");

            if (check(mType           , builder.getTypeRaw()          , "Type"          ))
                builder.setType           (mType          );
            //noinspection SpellCheckingInspection
            if (check(mLoaderFactory  , builder.getLoaderFactoryRaw() , "LoaderFactorie"))
                builder.setLoaderFactory  (mLoaderFactory );
            if (check(mLoaderCallback , builder.getLoaderCallbackRaw(), "LoaderCallback"))
                builder.setLoaderCallback (mLoaderCallback);
            if (check(mConverter      , builder.getConverterRaw()     , "Converter"     ))
                builder.setConverter      (mConverter     );
            if (check(mTableName      , builder.getTableNameRaw()     , "TableName"     ))
                builder.setTableName      (mTableName     );
            if (check(mLoaderId       , builder.getLoaderIdRaw()      , "LoaderId"      ))
                builder.setLoaderId       (mLoaderId      );
            if (check(mUriResolver    , builder.getUriResolverRaw()   , "UriResolver"   ))
                builder.setUriResolver    (mUriResolver   );

            if (mDescription != null) {
                if (check(mDescription, builder.getDescriptionRaw()   , "Description"   ))
                    builder.setDescription(mDescription);
            }
            else if (mDescriptionId != Core.NOT_VALID_RES_ID) {
                final String description = fragment.getString(mDescriptionId);
                if (check(description,  builder.getDescriptionRaw()   , "Description"   ))
                    builder.setDescription(description);
            }

            final CoreLoad coreLoad = super.create();
            if (mAdapterWrapper != null) mAdapterWrapper.setConverter(builder.getConverterRaw());
            return coreLoad;
        }

        private static <S> boolean check(final S valueOwn, final S valueBuilder, @NonNull final String txt) {
            if (valueOwn == null) return false;
            if (valueBuilder != null && !valueOwn.equals(valueBuilder)) {
                CoreLogger.logError(String.format("two different %ss in CoreLoadExtendedBuilder and " +
                                "BaseResponseLoaderBuilder, the first one will be ignored (value %s), " +
                                "accepted value: %s", txt, valueOwn.toString(), valueOwn.toString()));
                return false;
            }
            return true;
        }
    }
}
