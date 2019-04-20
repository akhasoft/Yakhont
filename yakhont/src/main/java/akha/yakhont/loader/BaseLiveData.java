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

package akha.yakhont.loader;

import akha.yakhont.BaseCacheProvider;
import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger;
// ProGuard issue
// import akha.yakhont.R;
import akha.yakhont.CoreReflection;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoadParameters;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2LoaderBuilder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Provider;

/**
 * {@link LiveData} extender, adjusted to work with {@link BaseViewModel}.
 *
 * @param <D>
 *        The type of data
 *
 * @see BaseViewModel
 */
@SuppressWarnings("JavadocReference")
public class BaseLiveData<D> extends MutableLiveData<D> {

    private static class LiveDataLoadParameters {

        private    final Future<?>                  mFuture;
        private    final LoadParameters             mParameters;

        private LiveDataLoadParameters(final Future<?> future, final LoadParameters parameters) {
            mFuture                 = future;
            mParameters             = parameters != null ? parameters: new LoadParameters();

            if (parameters == null) CoreLogger.log("accepted default load parameters: " + mParameters);
        }
    }

    private static final BaseResponse               TIMEOUT_STUB            = new BaseResponse<>(Source.TIMEOUT);

    // may interrupt if running
    private static final boolean                    MAY_INTERRUPT           = true;

    // setProgressDelay description should be consistent
    private static final int                        DEFAULT_PROGRESS_DELAY  = 700;      // ms

    private        final Requester<D>               mRequester;
    private        final BaseDialog                 mBaseDialog;
    private        final AtomicBoolean              mLoading                = new AtomicBoolean();
    private        final AtomicBoolean              mSetValue               = new AtomicBoolean();
    private              int                        mProgressDelay          = DEFAULT_PROGRESS_DELAY;

    private              LiveDataLoadParameters     mLoadParameters;
    private        final Object                     mLockLoading            = new Object();

    private        final Provider<BaseDialog>       mToast;

    /**
     * The API to make data loading requests.
     *
     * @param <D>
     *        The type of data
     */
    public interface Requester<D> extends Callable<D> {

        /**
         * Cancels the current data loading request (if any).
         */
        void cancel();
    }

    /**
     * Returns {@code BaseDialog} which shows data loading progress.
     *
     * @return  The {@code BaseDialog}
     */
    public BaseDialog getBaseDialog() {
        return mBaseDialog;
    }

    /**
     * Returns the default data loading timeout.
     *
     * @return  The timeout
     */
    @SuppressWarnings({"SameReturnValue", "unused"})
    protected int getDefaultTimeout() {
        return Core.TIMEOUT_CONNECTION;
    }

    private static BaseDialog getDefaultBaseDialog() {
        return LiveDataDialog.getInstance();
    }

    /**
     * Initialises a newly created {@code BaseLiveData} object.
     *
     * @param requester
     *        The data loading requester
     */
    @SuppressWarnings("unused")
    public BaseLiveData(@NonNull final Requester<D> requester) {
        this(requester, getDefaultBaseDialog());
    }

    /**
     * Initialises a newly created {@code BaseLiveData} object.
     *
     * @param requester
     *        The data loading requester
     *
     * @param dialog
     *        The data loading progress GUI
     */
    public BaseLiveData(@NonNull final Requester<D> requester, final BaseDialog dialog) {

        mRequester              = requester;
        mBaseDialog             = dialog != null ? dialog: getDefaultBaseDialog();
        mToast                  = Core.getDagger().getToastLong();

        mBaseDialog.setOnCancel(new Runnable() {
            @Override
            public void run() {
                CoreLogger.logWarning("cancelled by user " + mRequester);
                onComplete(true);
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseDialog.onCancel()";
            }
        });
    }

    private static Context getContext() {
        return Utils.getApplication().getApplicationContext();
    }

    private Boolean onCompleteAsync(final boolean cancel) {
        if (!mLoading.get()) return null;

        mLoading.set(false);

        if (cancel) {
            mSetValue.set(false);
            mRequester.cancel();
        }
        else {
            if (mLoadParameters.mFuture != null) {
                final boolean result = mLoadParameters.mFuture.cancel(MAY_INTERRUPT);
                if (!result)
                    CoreLogger.logError("can't cancel future " + mLoadParameters.mFuture);
            }
            else
                CoreLogger.log("mLoadParameters.mFuture == null");
        }

        final boolean noProgress = mLoadParameters.mParameters.getNoProgress();
        //noinspection Convert2Lambda
        postToMainLoop(new Runnable() {
            @Override
            public void run() {
                if (!noProgress) mBaseDialog.stop();
            }
        });

        final boolean noErrors   = mLoadParameters.mParameters.getNoErrors();
        mLoadParameters = null;

        return noErrors;
    }

    private Boolean onComplete(final boolean cancel) {
        synchronized (mLockLoading) {
            return onCompleteAsync(cancel);
        }
    }

    /**
     * Called when data loading process completes.
     *
     * @param success
     *        Indicates whether the data loading process was successful or not
     *
     * @param result
     *        The data loading process result
     */
    public void onComplete(final boolean success, final D result) {
        onComplete(success, result, false);
    }

    private void onComplete(final boolean success, final D result, final boolean cancel) {
        final Boolean notDisplayErrors = onComplete(cancel);

        if (!mSetValue.get()) {
            if (success)
                CoreLogger.logWarning("Results were delivered after timeout - not accepted: " + result);
            else
                CoreLogger.log("onComplete (not successful): " + result);
            return;
        }

        if (result == null)
            CoreLogger.logWarning("result == null");

        postToMainLoop(new Runnable() {
            @Override
            public void run() {
                onCompleteHelper(success, result, notDisplayErrors);
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseLiveData.onComplete()";
            }
        });
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void onCompleteHelper(final boolean success, final D result, Boolean notDisplayErrors) {
        if (success) {
            if (result instanceof BaseResponse) ((BaseResponse) result).setValues(null);

            setValue(result);
            return;
        }

        notDisplayErrors = notDisplayErrors == null ? false: notDisplayErrors;

        if (!(result instanceof BaseResponse)) {
            handleError(result, notDisplayErrors);
            return;
        }

        final BaseResponse baseResponse = (BaseResponse) result;

        final Object error = baseResponse.getError();
        if (error != null) {
            if (error instanceof Throwable)
                log(error);
            else
                CoreLogger.logError(LOADING_FAILED + ": " + error);
        }
        final Throwable throwable = baseResponse.getThrowable();
        if (throwable != null) log(throwable);

        if (error == null && throwable == null)
            CoreLogger.logError(LOADING_FAILED + ": error == null");

        displayError(baseResponse, notDisplayErrors);
    }

    private static final String                     LOADING_FAILED          = "loading failed";

    private void log(@NonNull final Object error) {
        CoreLogger.log(LOADING_FAILED, (Throwable) error);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void handleError(@SuppressWarnings("unused") final D result, final boolean notDisplayErrors) {
        CoreLogger.logError(LOADING_FAILED);
        displayError(null, notDisplayErrors);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void displayError(final BaseResponse baseResponse, final boolean notDisplayErrors) {
        if (!notDisplayErrors)
            mToast.get().start(null, makeErrorMessage(baseResponse), null);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected String makeErrorMessage(@SuppressWarnings("unused") final BaseResponse baseResponse) {
        return Utils.getApplication().getString(akha.yakhont.R.string.yakhont_loader_error);
    }

    /**
     * Makes data loading request.
     *
     * @param activity
     *        The Activity
     *
     * @param text
     *        The text to display in data loading progress GUI
     *
     * @param data
     *        The additional data (if any) to pass to data loading progress GUI
     *
     * @param loadParameters
     *        The LoadParameters
     */
    public void makeRequest(final Activity activity, final String text, final Intent data,
                            final LoadParameters loadParameters) {
        //noinspection Convert2Lambda
        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                makeRequestHandler(activity, text, data, loadParameters);
            }
        });
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedReturnValue", "WeakerAccess"})
    protected D makeRequestHandler(final Activity activity, final String text, final Intent data,
                                   final LoadParameters loadParameters) {
        try {
            synchronized (mLockLoading) {
                if (mLoadParameters != null) {
                    CoreLogger.logError("previous request is not yet completed");
                    return null;
                }
                if (loadParameters == null) CoreLogger.logWarning("loadParameters == null");

                mLoading.set(true);

                final boolean handleTimeout = loadParameters != null && loadParameters.getHandleTimeout();
                if (handleTimeout)
                    CoreLogger.log("handleTimeout == true");
                else
                    CoreLogger.logWarning("Yakhont delegates timeout handling to loader; " +
                            "if you're not agree just set 'handleTimeout' in LoadParameters to true");

                //noinspection Convert2Lambda,ConstantConditions
                mLoadParameters = new LiveDataLoadParameters(handleTimeout ?
                        Utils.runInBackground(loadParameters == null ? getDefaultTimeout():
                                loadParameters.getTimeout(), new Runnable() {
                            @Override
                            public void run() {
                                CoreLogger.logWarning("request timeout " + mRequester);
                                onComplete(false, getTimeoutStub(), true);
                            }
                        }): null, loadParameters);
            }

            //noinspection Convert2Lambda
            Utils.postToMainLoop(mProgressDelay, new Runnable() {
                @Override
                public void run() {
                    synchronized (mLockLoading) {
                        if (mLoading.get() && !mLoadParameters.mParameters.getNoProgress())
                            mBaseDialog.start(activity, text, data);
                    }
                }
            });

            mSetValue.set(true);

            return mRequester.call();
        }
        catch (Exception exception) {
            onComplete(false, getErrorStub(exception), false);

            CoreLogger.log("makeRequestHelper failed", exception);
            return null;
        }
    }

    /**
     * Sets delay (in milliseconds) after which the data loading progress will be shown.
     * The default value is 700 ms.
     *
     * @param progressDelay
     *        The progress delay
     */
    @SuppressWarnings("unused")
    public void setProgressDelay(final int progressDelay) {
        if (progressDelay > 0)
            mProgressDelay = progressDelay;
        else
            CoreLogger.logError("wrong progress delay " + progressDelay + ", should be > 0");
    }

    @SuppressWarnings("unchecked")
    private static <D> D castBaseResponse(@NonNull final BaseResponse baseResponse) {
        return (D) baseResponse;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected D getErrorStub(final Exception exception) {
        return castBaseResponse(new BaseResponse<>(
                null, null, null, null, Source.UNKNOWN, exception));
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected D getTimeoutStub() {
        return castBaseResponse(TIMEOUT_STUB);
    }

    private static void postToMainLoop(@NonNull final Runnable runnable) {
        Utils.postToMainLoop(runnable);
    }

    private boolean isLoadingSync() {
        synchronized (mLockLoading) {
            return mLoading.get();
        }
    }

    /**
     * Gets the data loading status.
     *
     * @return  {@code true} if data loading is in progress, {@code false} otherwise
     */
    public boolean isLoading() {
        final boolean loading = isLoadingSync();

        if (mBaseDialog instanceof LiveDataDialog) {
            final boolean liveDataLoading = ((LiveDataDialog) mBaseDialog).isLoading();

            if (loading != liveDataLoading)     // should never happen
                CoreLogger.logWarning("isLoading() problem: mLoading.get() == " + loading +
                        ", LiveDataDialog.isLoading() == " + liveDataLoading);
        }

        return loading;
    }

    /**
     * Confirms data load canceling.
     *
     * @param activity
     *        The @link Activity}
     *
     * @param view
     *        The View for {@link Snackbar}
     *
     * @return  {@code true} if confirmation supported, {@code false} otherwise
     *
     * @see BaseDialog#confirm
     */
    public boolean confirm(final Activity activity, final View view) {
        return mBaseDialog.confirm(activity, view);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A class that performs loading of data. If network is not available, loading goes from cache
     * (which is updated after every successful loading from network). Most implementations should not
     * use <code>CacheLiveData</code> directly, but instead utilise {@link Retrofit2LoaderWrapper}
     * or {@link Retrofit2LoaderBuilder}.
     *
     * @param <D>
     *        The type of data in this loader
     *
     * @see BaseCacheProvider
     */
    public static class CacheLiveData<D> extends BaseLiveData<D> {

        private static final BaseResponse           FORCE_STUB              = new BaseResponse(Source.CACHE);

        private        final Uri                    mUri;

        private        final Boolean                mMerge;
        private              boolean                mMergeFromParameters;

        /**
         * Initialises a newly created {@code CacheLiveData} object.
         *
         * @param requester
         *        The data loading requester
         *
         * @param merge
         *        The "merge data" flag
         *
         * @param tableName
         *        The name of the table in the database (to cache the loaded data)
         *
         * @param uriResolver
         *        The URI resolver
         */
        @SuppressWarnings("unused")
        public CacheLiveData(@NonNull final Requester<D> requester, final Boolean merge,
                             @NonNull final String tableName, final UriResolver uriResolver) {
            super(requester);

            mUri   = init(tableName, uriResolver);
            mMerge = merge;
        }

        /**
         * Initialises a newly created {@code CacheLiveData} object.
         *
         * @param requester
         *        The data loading requester
         *
         * @param merge
         *        The "merge data" flag
         *
         * @param dialog
         *        The data loading progress GUI
         *
         * @param tableName
         *        The name of the table in the database (to cache the loaded data)
         *
         * @param uriResolver
         *        The URI resolver
         */
        public CacheLiveData(@NonNull final Requester<D> requester, final Boolean merge, final BaseDialog dialog,
                             @NonNull final String tableName, final UriResolver uriResolver) {
            super(requester, dialog);

            mUri   = init(tableName, uriResolver);
            mMerge = merge;
        }

        private Uri init(@NonNull final String tableName, UriResolver uriResolver) {
            if (uriResolver == null) uriResolver = Utils.getUriResolver();
            return uriResolver.getUri(tableName);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void makeRequest(final Activity activity, final String text, final Intent data,
                                final LoadParameters loadParameters) {
            if (mMerge == null) mMergeFromParameters = loadParameters != null && loadParameters.getMerge();

            final boolean forceCache = loadParameters != null && loadParameters.getForceCache();
            if (forceCache || !Utils.isConnected()) {
                CoreLogger.log("request forced to cache, forceCache " + forceCache);

                onComplete(false, getForceStub());
            }
            else
                super.makeRequest(activity, text, data, loadParameters);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onComplete(final boolean success, D result) {
            if (success && result != null)
                storeResult(getContentValues(result), result, mMerge != null ? mMerge: mMergeFromParameters);
            else {
                CoreLogger.log("about to load data from cache, previous success flag: " + success);
                final Cursor cursor = query();
                if (cursor != null) result = handleCursor(result, cursor);
            }
            super.onComplete(success, result);
        }

        private Cursor query() {
            try {
                final Cursor cursor = getContext().getContentResolver().query(mUri, null,
                        null, null, null);
                if (cursor == null)
                    CoreLogger.logError("cache cursor == null, URI: " + mUri);
                return cursor;
            }
            catch (Exception exception) {
                CoreLogger.log("cache query failed, URI: " + mUri, exception);
                return null;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        protected void onCompleteHelper(final boolean success, final D result, final Boolean notDisplayErrors) {
            if (!success) setValue(result);
            super.onCompleteHelper(success, result, notDisplayErrors);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected D getForceStub() {
            return castBaseResponse(FORCE_STUB);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected D handleCursor(final D result, @NonNull final Cursor cursor) {
            if (!(result instanceof BaseResponse)) return result;

            final BaseResponse baseResponse = (BaseResponse) result;
            return castBaseResponse(new BaseResponse<>(null, null, cursor,
                    baseResponse.getError(), Source.CACHE, baseResponse.getThrowable()));
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ContentValues[] getContentValues(final D result) {
            return result instanceof BaseResponse ? ((BaseResponse) result).getValues(): null;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected void storeResult(final ContentValues[] values, final D result, final boolean merge) {
            if (result == null) {
                CoreLogger.logError("nothing to store in cache, empty result");
                return;
            }

            if (result instanceof BaseResponse) {
                final Source source = ((BaseResponse) result).getSource();
                if (source != Source.NETWORK) {
                    CoreLogger.log("wrong data source to cache (should be NETWORK): " + source);
                    return;
                }
            }

            if (Utils.getCacheTableName(mUri) == null) {
                CoreLogger.logError("can't store in cache, empty table name");
                return;
            }
            if (values == null || values.length == 0) {
                CoreLogger.logError("nothing to store in cache, empty values");
                return;
            }
            CoreLogger.log("about to store in cache, merge " + merge);

            Utils.runInBackground(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!merge) Utils.clearCache(mUri);

                        getContext().getContentResolver().bulkInsert(mUri, values);
                    }
                    catch (Exception exception) {
                        CoreLogger.log("can not store result", exception);
                    }
                }

                @NonNull
                @Override
                public String toString() {
                    return "CacheLiveData.storeResult()";
                }
            });
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The GUI which shows data loading progress.
     */
    public static class LiveDataDialog implements BaseDialog {

        /**
         * The API to show the data loading progress.
         */
        public interface Progress {

            /**
             * Sets text to show in the data loading progress GUI.
             *
             * @param text
             *        The text to show
             */
            void setText(String text);

            /**
             * Shows the data loading progress GUI.
             */
            void show();

            /**
             * Hides the data loading progress GUI.
             */
            void hide();

            /**
             * Confirms data load canceling.
             *
             * @param activity
             *        The {@link Activity}
             *
             * @param view
             *        The {@link Dialog}'s view (or null if you're not going to use {@link Snackbar})
             *
             * @see BaseDialog#confirm
             */
            void confirm(Activity activity, View view);
        }

        /**
         * The base (with {@link Snackbar} for confirmation) {@link Progress} implementation.
         */
        @SuppressWarnings("unused")
        public static abstract class ProgressDefault implements Progress {

            private              Snackbar           mSnackbar;
            private       static Integer            sSnackbarDuration, sSnackbarColor;
            private       static ColorStateList     sSnackbarColors;

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            protected     static Integer            sProgressColor;

            /**
             * Please refer to the base method description.
             */
            @Override
            public void setText(String text) {
            }

            /**
             * Please refer to the base method description.
             */
            @CallSuper
            @Override
            public void hide() {
                if (mSnackbar == null) return;

                mSnackbar.dismiss();
                mSnackbar = null;
            }

            /**
             * Please refer to the base method description.
             */
            @Override
            public void confirm(final Activity activity, final View view) {
                //noinspection Convert2Lambda
                mSnackbar = Snackbar.make(view != null ? view: ViewHelper.getViewForSnackbar(activity, null),

                        akha.yakhont.R.string.yakhont_loader_alert,

                        sSnackbarDuration != null ? sSnackbarDuration: Snackbar.LENGTH_LONG)

                        .setAction(akha.yakhont.R.string.yakhont_alert_yes, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                cancel(activity);
                            }
                        })

                        .addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                            @Override
                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                mSnackbar = null;
                                super.onDismissed(transientBottomBar, event);
                            }
                        });

                if (sSnackbarColors != null) {
                    if (sSnackbarColor != null)
                        CoreLogger.logWarning("ColorStateList != null so Snackbar color " +
                                "will be ignored: " + sSnackbarColor);
                    mSnackbar.setActionTextColor(sSnackbarColors);
                }
                else if (sSnackbarColor != null) mSnackbar.setActionTextColor(sSnackbarColor);

                mSnackbar.show();
            }

            /**
             * Please refer to {@link Snackbar#setDuration}.
             */
            public static void setConfirmDuration(final Integer duration) {
                sSnackbarDuration = duration;
            }

            /**
             * Please refer to {@link Snackbar#setActionTextColor}.
             */
            public static void setConfirmTextColor(final Integer color) {
                sSnackbarColor = color;
            }

            /**
             * Please refer to {@link Snackbar#setActionTextColor(ColorStateList)}.
             */
            public static void setConfirmTextColor(final ColorStateList colors) {
                sSnackbarColors = colors;
            }

            /**
             * Sets progress text color (please refer to {@link TextView#setTextColor}).
             */
            public static void setProgressTextColor(final Integer color) {
                sProgressColor = color;
            }

            /**
             * Adjusts {@link Dialog} to properly work with {@link Snackbar} (the {@link Callable#call}
             * should return value for the {@link OnKeyListener#onKey} - normally {@code true}).
             *
             * @param dialog
             *        The {@link Dialog}
             *
             * @param callback
             *        The callback (if  null - the {@link Callable#call} will return {@code false})
             *
             * @return  The handled {@link Dialog}
             */
            public static Dialog handle(@NonNull final Dialog dialog, final Callable<Boolean> callback) {
                //noinspection Convert2Lambda
                dialog.setOnKeyListener(new OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP)
                            try {
                                if (callback != null) return callback.call();
                            }
                            catch (Exception exception) {
                                CoreLogger.log(exception);
                            }
                        return false;
                    }
                });
                return dialog;
            }

            /**
             * Adjusts {@link Dialog} to properly work with {@link Snackbar}.
             *
             * @param dialog
             *        The {@link Dialog}
             *
             * @param view
             *        The {@link Dialog}'s view (or null if you're not going to use {@link Snackbar})
             *
             * @return  The handled {@link Dialog}
             */
            public static Dialog handle(@NonNull final Dialog dialog, final View view) {
                return handle(dialog, createCallable(BaseViewModel.get(), null, view));
            }

            /**
             * Adjusts {@link Dialog} to properly work with {@link Snackbar}.
             *
             * @param dialog
             *        The {@link Dialog}
             *
             * @param view
             *        The {@link Dialog}'s view (or null if you're not going to use {@link Snackbar})
             *
             * @param activity
             *        The {@link Activity}
             *
             * @param key
             *        The {@link BaseViewModel} key or null for default value
             *        (please refer to {@link ViewModelProvider#get(String, Class)})
             *
             * @return  The handled {@link Dialog}
             */
            public static Dialog handle(@NonNull final Dialog dialog, final View view,
                                        final Activity activity, final String key) {
                return handle(dialog, createCallable(BaseViewModel.get(activity, key), activity, view));
            }

            /**
             * Adjusts {@link Dialog} to properly work with {@link Snackbar}.
             *
             * @param dialog
             *        The {@link Dialog}
             *
             * @param view
             *        The {@link Dialog}'s view (or null if you're not going to use {@link Snackbar})
             *
             * @param fragment
             *        The {@link Fragment}
             *
             * @param key
             *        The {@link BaseViewModel} key or null for default value
             *        (please refer to {@link ViewModelProvider#get(String, Class)})
             *
             * @return  The handled {@link Dialog}
             */
            public static Dialog handle(@NonNull final Dialog dialog, final View view,
                                        final Fragment fragment, final String key) {
                return handle(dialog, createCallable(BaseViewModel.get(fragment, key),
                        fragment.getActivity(), view));
            }

            private static Callable<Boolean> createCallable(final BaseViewModel baseViewModel,
                                                            final Activity activity, final View view) {
                if (baseViewModel == null)
                    CoreLogger.logWarning("can't create Callable dor Dialog handling");

                //noinspection Convert2Lambda
                return baseViewModel == null ? null: new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return baseViewModel.getData().confirm(activity, view);
                    }
                };
            }
        }

        /**
         * The helper class for showing custom confirmation dialogs.
         */
        public static class ProgressDefaultDialog extends ProgressDefault {

            private static final Random             RANDOM                  = new Random();

            private final        Class              mClass;
            private              DialogFragment     mProgress;
            private              String             mTag;

            /**
             * Initialises a newly created {@code ProgressDefaultDialog} object.
             *
             * @param cls
             *        The {@link DialogFragment} class
             */
            @SuppressWarnings("unused")
            public ProgressDefaultDialog(@NonNull final Class cls) {
                mClass = cls;
            }

            /**
             * Please refer to the base method description.
             */
            @SuppressWarnings("unused")
            @Override
            public void show() {
                mProgress = CoreReflection.createSafe(mClass);
                if (mProgress == null) return;

                final Activity activity = Utils.getCurrentActivity();
                if (activity instanceof FragmentActivity) {
                    mTag = "yakhont_progress_" + RANDOM.nextInt(Integer.MAX_VALUE);
                    mProgress.show(((FragmentActivity) activity).getSupportFragmentManager(), mTag);
                }
                else
                    CoreLogger.logError("unexpected Activity (should be FragmentActivity) " +
                            CoreLogger.getDescription(activity));
            }

            /**
             * Please refer to the base method description.
             */
            @SuppressWarnings("unused")
            @Override
            public void hide() {
                super.hide();
                if (mProgress == null) return;

                mProgress.dismiss();
                mProgress = null;
            }

            /**
             * Returns custom {@link DialogFragment}.
             *
             * @return  The {@link DialogFragment}
             */
            @SuppressWarnings("unused")
            public DialogFragment getDialog() {
                return mProgress;
            }

            /**
             * Returns tag which was used to show the {@link DialogFragment}.
             *
             * @return  The tag
             *
             * @see     DialogFragment#show(FragmentManager, String)
             */
            @SuppressWarnings("unused")
            public String getTag() {
                return mTag;
            }
        }

        private static class ProgressDefaultImp extends ProgressDefault {

            private final static long               UPDATE_INTERVAL         = 300;

            private       static ProgressDefaultImp sInstance;

            private              Toast              mToast;
            private              CountDownTimer     mCountDownTimer;

            // lazy init and no sync ('cause it's implemented in parent class)
            @NonNull
            private static ProgressDefaultImp getInstance() {
                if (sInstance == null) sInstance = new ProgressDefaultImp();
                return sInstance;
            }

            private ProgressDefaultImp() {
                makeToast();
            }

            @SuppressLint("InflateParams")
            private void makeToast() {
                final Context context = getContext();

                mToast = new Toast(context);

                mToast.setView(LayoutInflater.from(context)
                        .inflate(akha.yakhont.R.layout.yakhont_progress, null, false));

                mToast.setGravity(Gravity.CENTER, 0, 0);
                mToast.setDuration(Toast.LENGTH_SHORT);
            }

            @Override
            public void setText(@NonNull final String text) {
                final TextView view = mToast.getView().findViewById(akha.yakhont.R.id.yakhont_loader_text);
                if (sProgressColor != null) view.setTextColor(sProgressColor);
                view.setText(text);
            }

            @Override
            public void show() {
                mCountDownTimer = new CountDownTimer(Long.MAX_VALUE, UPDATE_INTERVAL) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        mToast.show();
                    }

                    @Override
                    public void onFinish() {
                    }
                }.start();
            }

            @Override
            public void hide() {
                mCountDownTimer.cancel();
                mCountDownTimer = null;

                super.hide();

                mToast.cancel();
                makeToast();
            }
        }

        private              Progress               mProgress;
        private              boolean                mIsLoading;

        private final        Object                 mLock                   = new Object();
        private              int                    mCounter;

        private              Runnable               mOnCancel;
        private final        Object                 mLockCancel             = new Object();

        private final static LiveDataDialog         sInstance               = new LiveDataDialog();

        /**
         * Gets instance of LiveDataDialog (yes, it's Singleton).
         *
         * @return  The LiveDataDialog instance
         */
        @NonNull
        public static LiveDataDialog getInstance() {
            return sInstance;
        }

        private LiveDataDialog() {
        }

        /**
         * Gets component to show the data loading progress.
         *
         * @return  The Progress
         */
        @SuppressWarnings("unused")
        public Progress getProgress() {
            return mProgress;
        }

        /**
         * Sets component to show the data loading progress.
         *
         * @param progress
         *        The Progress component
         *
         * @return  {@code true} if component set was OK, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        public boolean setProgress(final Progress progress) {
            final boolean result = mProgress == null && progress != null;
            if (!result)
                //noinspection ConstantConditions
                CoreLogger.logWarning(String.format("unexpected progress: %s (mProgress: %s)" +
                         progress == null ? "null":  progress.toString(),
                        mProgress == null ? "null": mProgress.toString()));

            mProgress = progress;
            return result;
        }

        /**
         * Gets the data loading status.
         *
         * @return  {@code true} if data loading is in progress, {@code false} otherwise
         */
        @SuppressWarnings("WeakerAccess")
        public boolean isLoading() {
            synchronized (mLock) {
                return mIsLoading;
            }
        }

        /**
         * Gets default text for data loading GUI.
         *
         * @return  The text for data loading GUI
         */
        @SuppressWarnings("unused")
        public static String getInfoText() {
            return getInfoText(null);
        }

        /**
         * Gets customized text for data loading GUI.
         *
         * @param info
         *        The additional info describing data to load
         *
         * @return  The text for data loading GUI
         */
        @SuppressWarnings("unused")
        public static String getInfoText(@StringRes final int info) {
            return getInfoText(getContext().getResources().getString(info));
        }

        /**
         * Gets customized text for data loading GUI.
         *
         * @param info
         *        The additional info describing data to load
         *
         * @return  The text for data loading GUI
         */
        public static String getInfoText(final String info) {
            final Context context = getContext();
            return context.getString(akha.yakhont.R.string.yakhont_loader_progress, info != null ?
                    info: context.getString(akha.yakhont.R.string.yakhont_loader_progress_def_info));
        }

        /**
         * Shows the data loading progress GUI.
         *
         * @param text
         *        The text to display in data loading progress GUI
         *
         * @see Progress#show
         */
        @SuppressWarnings("WeakerAccess")
        public void start(final String text) {
            synchronized (mLock) {
                if (mProgress == null) mProgress = ProgressDefaultImp.getInstance();

                mProgress.setText(text != null ? text: getInfoText(null));

                if (mIsLoading) {
                    mCounter++;
                    CoreLogger.log("progress counter incremented to " + mCounter);
                    return;
                }
                mCounter   = 1;
                mIsLoading = true;

                mProgress.show();
            }
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
        @Override
        public boolean start(Activity context, String text, Intent data) {
            start(text);
            return true;
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
        @Override
        public boolean stop() {
            return stop(false, null);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public boolean stop(final boolean force, final Activity activity) {
            try {
                return stopNotSafe(force, activity);
            }
            catch (Exception exception) {
                CoreLogger.log(exception);
                return false;
            }
        }

        private boolean stopNotSafe(final boolean force, final Activity activity) {
            synchronized (mLock) {
                if (!mIsLoading) return false;

                if (--mCounter > 0 && !force) return false;

                CoreLogger.log("about to stop progress, force " + force);

                mCounter   = 0;
                mIsLoading = false;

                if (mProgress != null)
                    mProgress.hide();
                else
                    CoreLogger.logWarning("mProgress == null");     // should never happen
            }

            if (force) {
                final Collection<BaseViewModel<?>> models = BaseViewModel.getViewModels(
                        activity, true, CoreLogger.getDefaultLevel());
                if (models == null) {
                    CoreLogger.logWarning("only current BaseLiveData loading will be " +
                            "stopped 'cause of unsupported Activity " + CoreLogger.getDescription(activity));
                    cancel();
                }
                else
                    for (final BaseViewModel<?> model: models)
                        model.getData().mBaseDialog.cancel();
            }

            return true;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public boolean cancel() {
            synchronized (mLockCancel) {
                if (mOnCancel == null) {
                    CoreLogger.log("data load canceling handler == null, object " + this);
                    return false;
                }
                Utils.runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mLockCancel) {
                            try {
                                CoreLogger.log("about to cancel data loading, object " + this);
                                Utils.safeRunnableRun(mOnCancel);
                            }
                            catch (Exception exception) {
                                CoreLogger.log(exception);
                            }
                        }
                    }
                });
                return true;
            }
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public boolean setOnCancel(final Runnable handler) {
            synchronized (mLockCancel) {
                mOnCancel = handler;
            }
            return true;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public boolean confirm(final Activity activity, final View view) {
            //noinspection Convert2Lambda
            postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    mProgress.confirm(activity, view);
                }
            });
            return true;
        }

        /**
         * Cancels the current data loading request (if any).
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings("WeakerAccess")
        public static void cancel(final Activity activity) {
            sInstance.stop(true, activity);
        }
    }
}
