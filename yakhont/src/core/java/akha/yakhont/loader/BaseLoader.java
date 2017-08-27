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

package akha.yakhont.loader;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseCacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.ViewHolderCreator;
import akha.yakhont.adapter.ValuesCacheAdapterWrapper;
import akha.yakhont.fragment.WorkerFragment;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.LoaderCallback;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoaderBuilder;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoaderFactory;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.BaseResponseLoaderBuilder;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
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
import android.support.annotation.IdRes;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.GridView;
import android.widget.ListView;

import com.google.gson.reflect.TypeToken;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
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

        doProgressSafe(true);

        Utils.runInBackground(true, new Runnable() {
            @Override
            public void run() {
                try {
                    CoreLogger.log(addLoaderInfo("makeRequest"));
                    makeRequest(mCallback);
                }
                catch (Exception exception) {
                    CoreLogger.log(addLoaderInfo("makeRequest failed"), exception);
                    callbackHelper(false, wrapException(exception));
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private BaseResponse<R, E, D> wrapException(@NonNull final Exception exception) {
        try {
            return new BaseResponse<>(null, null, null, (E) exception, Source.UNKNOWN, null);
        }
        catch (Exception internalException) {
            CoreLogger.log(addLoaderInfo("BaseResponse creation failed"), internalException);
            return new BaseResponse<>(null, null, null, null, Source.UNKNOWN, exception);
        }
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
                mToast.get().start(fragment.getActivity(), text, null);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link CoreLoad} objects.
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
    public static class CoreLoadBuilder<R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final WeakReference<Fragment>         mFragment;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderRx<R, E, D>                     mRx;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderBuilder<BaseResponse<R, E, D>>  mLoaderBuilder;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ValuesCacheAdapterWrapper<R, E, D>    mAdapterWrapper;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ViewBinder                            mViewBinder;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ViewHolderCreator<ViewHolder>         mViewHolderCreator;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @IdRes
        protected int                                   mListViewId     = Core.NOT_VALID_VIEW_ID;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @LayoutRes
        protected int                                   mLayoutItemId   = Core.NOT_VALID_RES_ID;

        /**
         * Initialises a newly created {@code CoreLoadBuilder} object.
         *
         * @param fragment
         *        The fragment
         */
        @SuppressWarnings("unused")
        public CoreLoadBuilder(@NonNull final Fragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }
/*
        / @exclude / @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected CoreLoadBuilder(@NonNull final CoreLoadBuilder<R, E, D> src) {
            mFragment           = src.mFragment;
            mRx                 = src.mRx;
            mLoaderBuilder      = src.mLoaderBuilder;
            mAdapterWrapper     = src.mAdapterWrapper;
            mViewHolderCreator  = src.mViewHolderCreator;
            mViewBinder         = src.mViewBinder;
            mListViewId         = src.mListViewId;
            mLayoutItemId       = src.mLayoutItemId;
        }
*/
        /**
         * Sets the Rx component.
         *
         * @param rx
         *        The Rx component
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadBuilder<R, E, D> setRx(final LoaderRx<R, E, D> rx) {
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
        @SuppressWarnings("UnusedReturnValue")
        public CoreLoadBuilder<R, E, D> setLoaderBuilder(final LoaderBuilder<BaseResponse<R, E, D>> loaderBuilder) {
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
        public CoreLoadBuilder<R, E, D> setAdapterWrapper(final ValuesCacheAdapterWrapper<R, E, D> adapterWrapper) {
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
        @SuppressWarnings("unused")
        public CoreLoadBuilder<R, E, D> setViewBinder(final ViewBinder viewBinder) {
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
        @SuppressWarnings("unused")
        public CoreLoadBuilder<R, E, D> setViewHolderCreator(final ViewHolderCreator<ViewHolder> viewHolderCreator) {
            mViewHolderCreator = viewHolderCreator;
            return this;
        }

        /**
         * Sets the {@link ListView}, {@link GridView} or {@link RecyclerView} ID.
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
        @SuppressWarnings("unused")
        public CoreLoadBuilder<R, E, D> setListView(@IdRes final int listViewId) {
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
        @SuppressWarnings("unused")
        public CoreLoadBuilder<R, E, D> setListItem(@LayoutRes final int layoutItemId) {
            mLayoutItemId = layoutItemId;
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

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedParameters"})
        protected void customizeAdapterWrapper(@NonNull final CoreLoad coreLoad, @NonNull final View root,
                                               @NonNull final View list, @LayoutRes final int item) {
        }

        /**
         * Creates a {@link CoreLoad} with the arguments supplied to this builder.
         *
         * @return  The newly created {@code CoreLoad} object
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

            final View root = fragment.getView();
            if (root == null) {
                CoreLogger.logError("The fragment's root view is null");
                return null;
            }

            final View list = mListViewId == Core.NOT_VALID_VIEW_ID ?
                    BaseCacheAdapter.findListView(root): root.findViewById(mListViewId);
            if (list == null) {
                CoreLogger.logError("view with id " + mListViewId + " was not found");
                return null;
            }

            @LayoutRes
            int itemId = mLayoutItemId;
            if (itemId == Core.NOT_VALID_RES_ID)
                itemId = getItemLayout(fragment.getResources(), list, "layout", fragment.getActivity().getPackageName());

            CoreLogger.log(itemId == Core.NOT_VALID_RES_ID ? Level.ERROR: Level.DEBUG, "list item ID: " + itemId);
            if (itemId == Core.NOT_VALID_RES_ID) return null;

            customizeAdapterWrapper(coreLoad, root, list, itemId);
            if (mAdapterWrapper == null) {
                CoreLogger.logError("The adapter wrapper is null");
                return null;
            }

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
                return null;
            }

            coreLoad.addLoader(mAdapterWrapper, mRx, mLoaderBuilder);

            return coreLoad;
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
    public static abstract class CoreLoadExtendedBuilder<C, R, E, D, T> extends CoreLoadBuilder<R, E, D>
            implements Requester<C>{

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final Type                            mType;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderCallback<D>                     mLoaderCallback;

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
        protected int                                   mDescriptionId  = Core.NOT_VALID_RES_ID;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Requester<C>                          mDefaultRequester;

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
         *        The type of data; for generic {@link java.util.Collection} types please use {@link TypeToken}
         *
         * @param type
         *        The type of data
         */
        @SuppressWarnings("unused")
        protected CoreLoadExtendedBuilder(@NonNull final Fragment fragment, @NonNull final Type type) {
            super(fragment);
            mType = type;
        }

        /**
         * Initialises a newly created {@code CoreLoadExtendedBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data; intended to use with generic {@link java.util.Collection} types,
         *        e.g. {@code new com.google.gson.reflect.TypeToken<List<MyData>>() {}}
         */
        @SuppressWarnings("unused")
        protected CoreLoadExtendedBuilder(@NonNull final Fragment fragment, @NonNull final TypeToken type) {
            this(fragment, type.getType());
        }
/*
        / @exclude / @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected CoreLoadExtendedBuilder(@NonNull final CoreLoadExtendedBuilder<C, R, E, D, T> src) {
            super(src);
            mType               = src.mType;
            mLoaderCallback     = src.mLoaderCallback;
            mFrom               = src.mFrom;
            mTo                 = src.mTo;
            mDescription        = src.mDescription;
            mDescriptionId      = src.mDescriptionId;
            mDefaultRequester   = src.mDefaultRequester
            mConverter          = src.mConverter;
            mTableName          = src.mTableName;
            mLoaderId           = src.mLoaderId;
            mUriResolver        = src.mUriResolver;
            mLoaderFactory      = src.mLoaderFactory;
        }
*/
        /**
         * Sets the loader callback component.
         *
         * @param loaderCallback
         *        The loader callback component
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderCallback(final LoaderCallback<D> loaderCallback) {
            mLoaderCallback = loaderCallback;
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
        public CoreLoadExtendedBuilder<C, R, E, D, T> setAdapterWrapper(final ValuesCacheAdapterWrapper<R, E, D> adapterWrapper) {
            if (mAdapterWrapper != null)
                CoreLogger.logWarning("The adapter wrapper is already set");
            else
                super.setAdapterWrapper(adapterWrapper);
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
            mDescriptionId = descriptionId;
            return this;
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
        public CoreLoadExtendedBuilder<C, R, E, D, T> setConverter(@NonNull final Converter<D> converter) {
            mConverter = converter;
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
            mLoaderFactory = loaderFactory;
            return this;
        }

        /**
         * Returns the API defined by the service interface (e.g. the Retrofit API).
         *
         * @return  The API
         */
        @SuppressWarnings("unused")
        public abstract T getApi();

        /**
         * Please refer to the base method description.
         */
        @Override
        public void makeRequest(@NonNull final C callback) {
            mDefaultRequester.makeRequest(callback);
        }

        @SuppressWarnings("unchecked")
        private BaseResponseLoaderBuilder<C, R, E, D> getBuilder() {
            if (mLoaderBuilder instanceof BaseResponseLoaderBuilder)
                return (BaseResponseLoaderBuilder<C, R, E, D>) mLoaderBuilder;

            CoreLogger.logWarning("The loader builder is not an instance of BaseResponseLoaderBuilder");
            return null;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected CoreLoad create(BaseResponseLoaderBuilder<C, R, E, D> builder) {
            final Fragment fragment = mFragment.get();
            if (fragment == null) {
                CoreLogger.logError("The fragment is null");
                return null;
            }

            if (mLoaderBuilder != null) {
                CoreLogger.logWarning("The loader builder is already set");
                builder = getBuilder();
            }
            else
                setLoaderBuilder(builder);

            if (builder == null) return super.create();

            if (mDescriptionId != Core.NOT_VALID_RES_ID && mDescription != null)
                CoreLogger.logWarning("Both description and description ID were set; description ID will be ignored");

            mDefaultRequester = builder.getDefaultRequester();

            builder.setRequester(this);

            if (mLoaderCallback != null)                        builder.setLoaderCallback(mLoaderCallback                   );
            if (mDescription    != null)                        builder.setDescription   (mDescription                      );
            else if (mDescriptionId != Core.NOT_VALID_RES_ID)   builder.setDescription   (fragment.getString(mDescriptionId));

            if (mConverter      != null)                        builder.setConverter     (mConverter                        );
            if (mTableName      != null)                        builder.setTableName     (mTableName                        );
            if (mLoaderId       != null)                        builder.setLoaderId      (mLoaderId                         );
            if (mUriResolver    != null)                        builder.setUriResolver   (mUriResolver                      );
            if (mLoaderFactory  != null)                        builder.setLoaderFactory (mLoaderFactory                    );

            return super.create();
        }
    }
}
