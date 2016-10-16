/*
 * Copyright (C) 2016 akha, a.k.a. Alexander Kharitonov
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
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseCacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.fragment.WorkerFragment;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.LoaderCallback;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.Rx.RxLoader;
import akha.yakhont.technology.retrofit.Retrofit.RetrofitAdapterWrapper;
import akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitLoaderBuilder;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.IdRes;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.view.View;
import android.widget.GridView;
import android.widget.ListView;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
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

    private   final         String                  mLogDescription;

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

    private final AtomicBoolean     mWaitForResponse    = new AtomicBoolean();
    private final Object            mWaitLock           = new Object();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isWaiting() {
        synchronized (mWaitLock) {
            if (!mWaitForResponse.get()) return false;
            mWaitForResponse.set(false);
        }
        return true;
    }

    /**
     * Starts an asynchronous load.
     */
    @SuppressWarnings("WeakerAccess")
    protected void makeRequest() {
        if (mCallback == null) {
            CoreLogger.logError(addLoaderInfo("mCallback == null"));
            return;
        }

        synchronized (mWaitLock) {
            mWaitForResponse.set(true);
        }

        try {
            CoreLogger.log(addLoaderInfo("makeRequest"));
            makeRequest(mCallback);
        }
        catch (Exception e) {
            CoreLogger.log(addLoaderInfo("failed"), e);

            synchronized (mWaitLock) {
                mWaitForResponse.set(true);
            }
            return;
        }

        doProgressSafe(true);
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
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                final boolean waiting = isWaiting();
                CoreLogger.log(waiting ? Level.DEBUG: Level.ERROR, addLoaderInfo("success " + success));

                if (!waiting) return;
                CoreLogger.log(addLoaderInfo("proceed"));

                doProgressSafe(false);

                if (success) {
                    onSuccess(baseResponse);
                    return;
                }

                logError(baseResponse.getError());
                displayErrorSafe(makeErrorMessage(baseResponse.getError()));

                onFailure(baseResponse);
            }
        });
    }

    private void logError(final E error) {
        if (error == null) {
            CoreLogger.logError(addLoaderInfo("error == null"));
            return;
        }
        if (error instanceof Throwable)
            CoreLogger.logError(addLoaderInfo(error.toString()), (Throwable) error);
        else
            CoreLogger.logError(addLoaderInfo(error.toString()));
    }

    private void displayErrorSafe(@NonNull final String text) {
        try {
            if (!mSilent) displayError(text);
        }
        catch (Exception e) {
            CoreLogger.log(addLoaderInfo("failed"), e);
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
    protected String makeErrorMessage(final E error) {
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

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void doProgressSafe(final boolean show) {
        try {
            doProgressTimer(show);
            if (!mSilent) doProgress(show);
        }
        catch (Exception e) {
            CoreLogger.log(addLoaderInfo("show = " + show), e);
        }
    }

    private Timer               mTimer;
    private final Object        mTimerLock       = new Object();

    private void doProgressTimer(final boolean show) {
        synchronized (mTimerLock) {
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
            if (!show) return;

            if (mTimeout < 0) {
                CoreLogger.logError(addLoaderInfo("mTimeout < 0"));
                return;
            }

            // stop show loading progress after TIMEOUT_CONNECTION_TIMER seconds of connection timeout
            // (normally should never happen)
            final int timeout = (mTimeout + Core.TIMEOUT_CONNECTION_TIMER) * 1000;

            mTimer = new Timer(addLoaderInfo("timer for loading progress"));
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    CoreLogger.log(Level.ERROR, addLoaderInfo("timer forced to stop display loading progress"), false);
                    doProgressSafe(false);

                    if (!isWaiting()) return;
                    CoreLogger.log(addLoaderInfo("timer proceed"));

                    Utils.postToMainLoop(new Runnable() {
                        @Override
                        public void run() {
                            onFailure(new BaseResponse<R, E, D>(Source.TIMEOUT));
                        }
                    });
                }
            }, timeout);
        }
    }

    private void postDeliverResult(@NonNull final BaseResponse<R, E, D> result) {
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                deliverResult(result);
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
     * @param loaderIds
     *        The list of loaders to destroy
     */
    @SuppressWarnings("WeakerAccess")
    public static void destroyLoaders(final LoaderManager loaderManager, final Collection<Integer> loaderIds) {
        if (loaderManager == null) {
            CoreLogger.logError("loaderManager == null");
            return;
        }
        if (loaderIds == null) {
            CoreLogger.logError("loaderIds == null");
            return;
        }

        CoreLogger.log("loader ids qty " + loaderIds.size());

        int qty = 0;
        for (final int i: loaderIds)
            if (destroyLoader(loaderManager, i)) qty++;

        CoreLogger.log(qty + " loader" + (qty != 1 ? "s": "") + " destroyed");
    }

    /**
     * Destroys loader with the given ID.
     *
     * @param loaderManager
     *        The loader manager
     *
     * @param loaderId
     *        The loader ID
     *
     * @return  {@code true} if loader was successfully destroyed, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean destroyLoader(final LoaderManager loaderManager, final int loaderId) {
        final Loader loader = loaderManager.getLoader(loaderId);

        if (loader != null && loader.isStarted()) {
            CoreLogger.log("about to destroy loader with id " + loader.getId());
            loaderManager.destroyLoader(loaderId);
            return true;
        }

        return false;
    }
    
    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void enableLoaderManagerDebugLogging(final boolean enable) {
        LoaderManager.enableDebugLogging(enable);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public String toString() {
        return String.format(CoreLogger.getLocale(), FORMAT_INFO, getId(), mLogDescription);
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
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                if (mResult != null)    // If we currently have a result available, deliver it immediately.
                    deliverResult(mResult);

                if (isReload() || mResult == null)
                    forceLoad();
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
     * The <code>ProgressWrapper</code> class is intended to display a data loading progress to the user.
     */
    public static class ProgressWrapper {

        private final   WeakReference<Fragment>           mFragment;
        private final   boolean                           mNoProgress;
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
        @SuppressWarnings("WeakerAccess")
        public ProgressWrapper(@NonNull final WeakReference<Fragment> fragment, final boolean noProgress) {
            mFragment       = fragment;
            mNoProgress     = noProgress;
            
            mToast          = Core.getDagger().getToastLong();
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
            final Fragment fragment = mFragment.get();
            if (fragment == null)
                CoreLogger.logError("fragment == null");
            else
                mToast.get().start(fragment.getActivity(), text);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, Class)
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final Class<D> type) {
        return simpleInit(fragment, type, null, null, null, null);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param rx
     *        The {@code RxLoader} component
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, Class)
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final Class<D> type, final RxLoader rx) {
        return simpleInit(fragment, type, rx, null, null, null);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, Class)
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final Class<D> type, final ViewBinder viewBinder) {
        return simpleInit(fragment, type, null, null, null, viewBinder);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param description
     *        The data description
     *
     * @param loaderCallback
     *        The loader callback (see {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()})
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, Class)
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final Class<D> type, final String description,
                                          final LoaderCallback<D> loaderCallback) {
        return simpleInit(fragment, type, null, description, loaderCallback, null);
    }
    
    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param descriptionId
     *        The resource ID of the data description
     *
     * @param loaderCallback
     *        The loader callback (see {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()})
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, Class)
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final Class<D> type, @StringRes final int descriptionId,
                                          final LoaderCallback<D> loaderCallback) {
        return simpleInit(fragment, type, null, descriptionId, loaderCallback, null);
    }
    
    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param rx
     *        The {@code RxLoader} component
     *
     * @param description
     *        The data description
     *
     * @param loaderCallback
     *        The loader callback (see {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()})
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, Class)
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final Class<D> type,
                                          final RxLoader rx, final String description,
                                          final LoaderCallback<D> loaderCallback, final ViewBinder viewBinder) {
        return simpleInit(fragment, rx, getBuilder(fragment, type, Utils.NOT_VALID_RES_ID, description, loaderCallback), viewBinder);
    }
    
    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param rx
     *        The {@code RxLoader} component
     *
     * @param descriptionId
     *        The resource ID of the data description
     *
     * @param loaderCallback
     *        The loader callback (see {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()})
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, Class)
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final Class<D> type,
                                          final RxLoader rx, @StringRes final int descriptionId,
                                          final LoaderCallback<D> loaderCallback, final ViewBinder viewBinder) {
        return simpleInit(fragment, rx, getBuilder(fragment, type, descriptionId, null, loaderCallback), viewBinder);
    }
    
    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param listView
     *        The resource identifier of a {@link ListView} or {@link GridView}; if {@link View#NO_ID},
     *        implementation looks for the first {@code ListView} (or {@code GridView}) in the fragment's root view
     *
     * @param listItem
     *        The resource identifier of a layout file that defines the views to bind; if {@link akha.yakhont.Core.Utils#NOT_VALID_RES_ID},
     *        implementation looks for the layout with the same name as listView's ID (if no ID, "grid" or "list" will be used as name),
     *        e.g. in case of R.id.my_list it will look for R.layout.my_list (if not found, look for R.layout.my_list_item)
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment,
                                          @SuppressWarnings("SameParameterValue") @IdRes     final int listView,
                                          @SuppressWarnings("SameParameterValue") @LayoutRes final int listItem,
                                          @NonNull final Class<D> type) {
        return simpleInit(fragment, listView, listItem, getBuilder(fragment, type, Utils.NOT_VALID_RES_ID, null, null), null);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param listView
     *        The resource identifier of a {@link ListView} or {@link GridView}; if {@link View#NO_ID},
     *        implementation looks for the first {@code ListView} (or {@code GridView}) in the fragment's root view
     *
     * @param listItem
     *        The resource identifier of a layout file that defines the views to bind; if {@link akha.yakhont.Core.Utils#NOT_VALID_RES_ID},
     *        implementation looks for the layout with the same name as listView's ID (if no ID, "grid" or "list" will be used as name),
     *        e.g. in case of R.id.my_list it will look for R.layout.my_list (if not found, look for R.layout.my_list_item)
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param descriptionId
     *        The resource ID of the data description
     *
     * @param loaderCallback
     *        The loader callback (see {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()})
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment,
                                          @SuppressWarnings("SameParameterValue") @IdRes     final int listView,
                                          @SuppressWarnings("SameParameterValue") @LayoutRes final int listItem,
                                          @NonNull final Class<D> type, @StringRes final int descriptionId,
                                          final LoaderCallback<D> loaderCallback, final ViewBinder viewBinder) {
        return simpleInit(fragment, listView, listItem, 
                          getBuilder(fragment, type, descriptionId, null, loaderCallback), viewBinder);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param listView
     *        The resource identifier of a {@link ListView} or {@link GridView}; if {@link View#NO_ID},
     *        implementation looks for the first {@code ListView} (or {@code GridView}) in the fragment's root view
     *
     * @param listItem
     *        The resource identifier of a layout file that defines the views to bind; if {@link akha.yakhont.Core.Utils#NOT_VALID_RES_ID},
     *        implementation looks for the layout with the same name as listView's ID (if no ID, "grid" or "list" will be used as name),
     *        e.g. in case of R.id.my_list it will look for R.layout.my_list (if not found, look for R.layout.my_list_item)
     *
     * @param type
     *        The data type; will be used to build {@link akha.yakhont.Core.Requester}
     *        which calls the first method (from the API defined by the service interface,
     *        see {@link akha.yakhont.technology.retrofit.Retrofit#getRetrofitApi}) which handles that type
     *
     * @param description
     *        The data description
     *
     * @param loaderCallback
     *        The loader callback (see {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()})
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see RetrofitLoaderBuilder
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment,
                                          @SuppressWarnings("SameParameterValue") @IdRes     final int listView,
                                          @SuppressWarnings("SameParameterValue") @LayoutRes final int listItem,
                                          @NonNull final Class<D> type, final String description,
                                          final LoaderCallback<D> loaderCallback, final ViewBinder viewBinder) {
        return simpleInit(fragment, listView, listItem, 
                          getBuilder(fragment, type, Utils.NOT_VALID_RES_ID, description, loaderCallback), viewBinder);
    }

    private static <D> RetrofitLoaderBuilder<D> getBuilder(@NonNull final Fragment fragment, @NonNull final Class<D> type, 
                                                           @StringRes final int descriptionId, final String description, 
                                                           final LoaderCallback<D> loaderCallback) {
        final RetrofitLoaderBuilder<D>                  builder = new RetrofitLoaderBuilder<>(fragment, type);
        if (loaderCallback != null)                     builder.setLoaderCallback(loaderCallback);
        if (descriptionId  != Utils.NOT_VALID_RES_ID)   builder.setDescription   (fragment.getString(descriptionId));
        if (description    != null)                     builder.setDescription   (description);
        return                                          builder;
    }
    
    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param loaderBuilder
     *        The {@code RetrofitLoaderBuilder}
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitLoaderBuilder,
     * akha.yakhont.adapter.BaseCacheAdapter.ViewBinder)
     */
    @SuppressWarnings("unused")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, @NonNull final RetrofitLoaderBuilder<D> loaderBuilder) {
        return simpleInit(fragment, null, loaderBuilder, null);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param rx
     *        The {@code RxLoader} component
     *
     * @param loaderBuilder
     *        The {@code RetrofitLoaderBuilder}
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     *
     * @see #simpleInit(Fragment, int, int, akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitLoaderBuilder,
     * akha.yakhont.adapter.BaseCacheAdapter.ViewBinder)
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, final RxLoader rx,
                                          @NonNull final RetrofitLoaderBuilder<D> loaderBuilder, final ViewBinder viewBinder) {
        return simpleInit(fragment, rx, View.NO_ID, Utils.NOT_VALID_RES_ID, loaderBuilder, viewBinder);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param listView
     *        The resource identifier of a {@link ListView} or {@link GridView}; if {@link View#NO_ID},
     *        implementation looks for the first {@code ListView} (or {@code GridView}) in the fragment's root view
     *
     * @param listItem
     *        The resource identifier of a layout file that defines the views to bind; if {@link akha.yakhont.Core.Utils#NOT_VALID_RES_ID},
     *        implementation looks for the layout with the same name as listView's ID (if no ID, "grid" or "list" will be used as name),
     *        e.g. in case of R.id.my_list it will look for R.layout.my_list (if not found, look for R.layout.my_list_item)
     *
     * @param loaderBuilder
     *        The {@code RetrofitLoaderBuilder}
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment,
                                          @SuppressWarnings("SameParameterValue") @IdRes     final int listView,
                                          @SuppressWarnings("SameParameterValue") @LayoutRes final int listItem,
                                          @NonNull final RetrofitLoaderBuilder<D> loaderBuilder, final ViewBinder viewBinder) {
        return simpleInit(fragment, null, listView, listItem, loaderBuilder, viewBinder);
    }

    /**
     * The helper method to initialize simple loading: one loader with auto generated ID, default SQL table name (for cache) etc.
     *
     * @param fragment
     *        The fragment
     *
     * @param rx
     *        The {@code RxLoader} component
     *
     * @param listView
     *        The resource identifier of a {@link ListView} or {@link GridView}; if {@link View#NO_ID},
     *        implementation looks for the first {@code ListView} (or {@code GridView}) in the fragment's root view
     *
     * @param listItem
     *        The resource identifier of a layout file that defines the views to bind; if {@link akha.yakhont.Core.Utils#NOT_VALID_RES_ID},
     *        implementation looks for the layout with the same name as listView's ID (if no ID, "grid" or "list" will be used as name),
     *        e.g. in case of R.id.my_list it will look for R.layout.my_list (if not found, look for R.layout.my_list_item)
     *
     * @param loaderBuilder
     *        The {@code RetrofitLoaderBuilder}
     *
     * @param viewBinder
     *        The {@code ViewBinder} for custom data binding
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@code CoreLoad}
     */
    @SuppressWarnings("WeakerAccess")
    public static <D> CoreLoad simpleInit(@NonNull final Fragment fragment, final RxLoader rx,
                                          @SuppressWarnings("SameParameterValue") @IdRes     final int listView,
                                          @SuppressWarnings("SameParameterValue") @LayoutRes final int listItem,
                                          @NonNull final RetrofitLoaderBuilder<D> loaderBuilder, final ViewBinder viewBinder) {
        final CoreLoad coreLoad = getCoreLoad(fragment);
        if (coreLoad == null) return null;

        final View root = fragment.getView();
        if (root == null) {
            CoreLogger.log("The fragment's root view is null");
            return null;
        }

        final View list = listView == View.NO_ID ? BaseCacheAdapter.findListView(root): root.findViewById(listView);
        if (list == null) {
            CoreLogger.log("view with id " + listView + " was not found");
            return null;
        }

        int item = listItem;
        if (item == Utils.NOT_VALID_RES_ID)
            item = getItemLayout(fragment.getResources(), list, "layout", fragment.getActivity().getPackageName());

        CoreLogger.log(item == Utils.NOT_VALID_RES_ID ? Level.ERROR: Level.DEBUG, "list view item: " + item);
        if (item == Utils.NOT_VALID_RES_ID) return null;

        final RetrofitAdapterWrapper<D> adapterWrapper = new RetrofitAdapterWrapper<>(fragment.getActivity(), item);
        if (viewBinder != null) adapterWrapper.getAdapter().setAdapterViewBinder(viewBinder);

        if      (list instanceof ListView)
            ((ListView) list).setAdapter(adapterWrapper.getAdapter());
        else if (list instanceof GridView)
            ((GridView) list).setAdapter(adapterWrapper.getAdapter());
        else {
            CoreLogger.log("view with id " + listView + " should be instance of ListView or GridView");
            return null;
        }

        coreLoad.addLoader(adapterWrapper, rx, loaderBuilder);
        return coreLoad;
    }

    @SuppressWarnings("SameParameterValue")
    private static int getItemLayout(@NonNull final Resources resources,
                                     @NonNull final View list, @NonNull final String defType, @NonNull final String defPackage) {
        final String name = list.getId() != View.NO_ID ? resources.getResourceEntryName(list.getId()):
                list instanceof GridView ? "grid": "list";
        final int id = resources.getIdentifier(name, defType, defPackage);
        return id != Utils.NOT_VALID_RES_ID ? id: resources.getIdentifier(name + "_item", defType, defPackage);
    }

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
}
