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
//import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoaderFactory;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.BaseResponseLoaderBuilder;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.retrofit.BaseRetrofit;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.support.v4.app.Fragment;
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
        CoreLogger.log(addLoaderInfo("loader failed"), throwable);
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
        final int timeout = (mTimeout /*+ Core.TIMEOUT_CONNECTION_TIMER*/) * 1000;

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
        return null; //WorkerFragment.findInstance(fragment);
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
        return null; //WorkerFragment.findInstance(activity);
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
/*
            if (show)
                coreLoad.showProgress(description);
            else
                if (mProgressShown.get()) coreLoad.hideProgress(false);
*/
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
/*
    @SuppressWarnings("unused")
    public static abstract class LoaderCallback<C, R, E, D>
            implements LoaderManager.LoaderCallbacks<BaseResponse<R, E, D>> {

        protected BaseResponseLoaderWrapper<C, R, E, D> mLoaderWrapper;

        public void setLoaderWrapper(final BaseResponseLoaderWrapper<C, R, E, D> loaderWrapper) {
            mLoaderWrapper = loaderWrapper;
        }

        public BaseResponseLoaderWrapper<C, R, E, D> getLoaderWrapper() {
            return mLoaderWrapper;
        }

        public Loader<BaseResponse<R, E, D>> onCreateLoader(final Loader<BaseResponse<R, E, D>> loader) {
            return loader;
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public Loader<BaseResponse<R, E, D>> onCreateLoader(final int id, final Bundle args) {
            return null;
        }

        @SuppressWarnings("EmptyMethod")
        @Override
        public void onLoaderReset(final Loader<BaseResponse<R, E, D>> loader) {
        }

        @SuppressWarnings("EmptyMethod")
        @Override
        public void onLoadFinished(final Loader<BaseResponse<R, E, D>> loader,
                                   final BaseResponse<R, E, D> data) {
        }

        @SuppressWarnings({"UnusedParameters", "EmptyMethod"})
        public void onLoadFinished(final D data, final Source source) {
        }

        @SuppressWarnings("EmptyMethod")
        public void onLoadError(final E error, final Source source) {
        }
    }

    @SuppressWarnings("WeakerAccess")
    public interface LoaderFinishedCallback<D> {

        void onLoadFinished(final D data, final Source source);
    }
*/
}
