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

package akha.yakhont.loader;

import akha.yakhont.BaseCacheProvider;
import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.SnackbarBuilder;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CoreReflection;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.CacheHelper;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.Converter;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoadParameters;
import akha.yakhont.technology.Dagger2.UiModule;
import akha.yakhont.technology.Dagger2.UiModule.ViewHandler;
import akha.yakhont.technology.Dagger2.UiModule.ViewModifier;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper;
import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2LoaderBuilder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
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
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import java.util.Collection;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Provider;

import com.google.android.material.snackbar.Snackbar;

/**
 * {@link LiveData} extender, adjusted to work with {@link BaseViewModel}.
 *
 * @param <D>
 *        The type of data
 *
 * @see BaseViewModel
 *
 * @author akha
 */
public class BaseLiveData<D> extends MutableLiveData<D> {

    private static class LiveDataLoadParameters {

        private    final Future<?>                  mFuture;
        private    final LoadParameters             mParameters;

        private LiveDataLoadParameters(final Future<?> future, final LoadParameters parameters) {
            mFuture         = future;
            mParameters     = parameters != null ? parameters: new LoadParameters();

            if (parameters == null) CoreLogger.log("accepted default load parameters: " + mParameters);
        }
    }

    // may interrupt if running
    private static final boolean                    MAY_INTERRUPT           = true;

    // setProgressDelay description should be consistent
    private static final int                        DEFAULT_GUI_DELAY       = 700;      // ms

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final DataLoader<D>              mDataLoader;
    private        final BaseDialog                 mBaseDialog;
    private              int                        mProgressDelay          = DEFAULT_GUI_DELAY;

    private        final AtomicBoolean              mLoading                = new AtomicBoolean();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final AtomicBoolean              mSetValue               = new AtomicBoolean();

    private              LiveDataLoadParameters     mLoadParameters;
    private        final Object                     mLockLoading            = new Object();

    private        final Provider<BaseDialog>       mToast;

    private static       long                       sToastStartTime;
    private static final Object                     sLockToast;

    static {
        sLockToast                                                          = new Object();
        init();
    }

    /**
     * Cleanups static fields in BaseLiveData; called from {@link Core#cleanUpFinal()}.
     */
    public static void cleanUpFinal() {
        init();
    }

    private static void init() {
        synchronized (sLockToast) {
            sToastStartTime     = 0;
        }
    }

    /**
     * The data loading helper API.
     *
     * @param <D>
     *        The type of data
     */
    public interface DataLoader<D> extends Callable<D>, CacheHelper {

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
     * @param dataLoader
     *        The {@code DataLoader} component
     */
    @SuppressWarnings("unused")
    public BaseLiveData(@NonNull final DataLoader<D> dataLoader) {
        this(dataLoader, getDefaultBaseDialog());
    }

    /**
     * Initialises a newly created {@code BaseLiveData} object.
     *
     * @param dataLoader
     *        The {@code DataLoader} component
     *
     * @param dialog
     *        The data loading progress GUI
     */
    public BaseLiveData(@NonNull final DataLoader<D> dataLoader, final BaseDialog dialog) {

        mDataLoader             = dataLoader;
        mBaseDialog             = dialog != null ? dialog: getDefaultBaseDialog();
        // should be consistent with displayError
        mToast                  = Core.getDagger().getToastShort();

        mBaseDialog.setOnCancel(new Runnable() {
            @Override
            public void run() {
                CoreLogger.logWarning("cancelled by user " + mDataLoader);
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
        return Utils.getApplication();
    }

    private Boolean onCompleteAsync(final boolean cancel) {
        if (!mLoading.get()) return null;

        mLoading.set(false);

        if (cancel) {
            mSetValue.set(false);
            mDataLoader.cancel();
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
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                if (!noProgress) mBaseDialog.stop();
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseDialog.stop()";
            }
        });

        final boolean noErrors   = mLoadParameters.mParameters.getNoErrors();
        mLoadParameters = null;

        return noErrors;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected Boolean onComplete(final boolean cancel) {
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

        Utils.postToMainLoop(new Runnable() {
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
    @MainThread
    protected void onCompleteHelper(final boolean success, final D result, Boolean notDisplayErrors) {
        if (success) {
            if (result instanceof BaseResponse) ((BaseResponse<?, ?, ?>) result).setValues(null);

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
        if (notDisplayErrors) return;

        synchronized (sLockToast) {
            // again an ugly hack :-) (4000 is for Toast.LENGTH_SHORT - according to Toast sources)
            if (sToastStartTime + 4000 + DEFAULT_GUI_DELAY >= getCurrentTime()) return;

            mToast.get().start(makeErrorMessage(baseResponse), null);
            sToastStartTime = getCurrentTime();
        }
    }

    private static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected String makeErrorMessage(@SuppressWarnings("unused") final BaseResponse baseResponse) {
        return Objects.requireNonNull(Utils.getApplication()).getString(akha.yakhont.R.string.yakhont_loader_error);
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
        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                makeRequestHandler(activity, text, data, loadParameters);
            }

            @NonNull
            @Override
            public String toString() {
                return "BaseLiveData.makeRequestHandler()";
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

                //noinspection ConstantConditions
                mLoadParameters = new LiveDataLoadParameters(handleTimeout ?
                        Utils.runInBackground(loadParameters == null ? getDefaultTimeout():
                                loadParameters.getTimeout(), new Runnable() {
                            @Override
                            public void run() {
                                CoreLogger.logWarning("request timeout " + mDataLoader);
                                onComplete(false, getTimeoutStub(loadParameters), true);
                            }

                            @NonNull
                            @Override
                            public String toString() {
                                return "BaseLiveData.onComplete()";
                            }
                        }): null, loadParameters);
            }

            if (activity == null)   // e.g. service
                CoreLogger.logWarning("makeRequestHandler: activity == null");
            else {
                Utils.postToMainLoop(mProgressDelay, new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mLockLoading) {
                            if (mLoading.get() && !mLoadParameters.mParameters.getNoProgress())
                                mBaseDialog.start(text, data);
                        }
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "BaseDialog.start()";
                    }
                });
            }

            mSetValue.set(true);

            return mDataLoader.call();
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
                null, null, null, null, null, Source.UNKNOWN, exception));
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected D getTimeoutStub(final LoadParameters parameters) {
        return castBaseResponse(new BaseResponse<>(parameters, Source.TIMEOUT));
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

        private        final Uri                    mUri;
        private              Boolean                mMerge, mMergeFromParameters;
        private              Cursor                 mCursor;

        /**
         * Initialises a newly created {@code CacheLiveData} object.
         *
         * @param dataLoader
         *        The {@code DataLoader} component
         *
         * @param tableName
         *        The name of the table in the database (to cache the loaded data)
         *
         * @param uriResolver
         *        The URI resolver
         */
        @SuppressWarnings("unused")
        public CacheLiveData(@NonNull final DataLoader<D> dataLoader,
                             @NonNull final String tableName, final UriResolver uriResolver) {
            super(dataLoader);

            mUri = init(tableName, uriResolver);
        }

        /**
         * Initialises a newly created {@code CacheLiveData} object.
         *
         * @param dataLoader
         *        The {@code DataLoader} component
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
        public CacheLiveData(@NonNull final DataLoader<D> dataLoader, final BaseDialog dialog,
                             @NonNull final String tableName, final UriResolver uriResolver) {
            super(dataLoader, dialog);

            mUri = init(tableName, uriResolver);
        }

        private Uri init(@NonNull final String tableName, UriResolver uriResolver) {
            if (uriResolver == null) uriResolver = Utils.getUriResolver();
            return uriResolver.getUri(tableName);
        }

        /**
         * Sets the "merge data" flag.
         *
         * @param merge
         *        The "merge data" flag
         *
         * @return  The previous value of the "merge data" flag
         */
        @SuppressWarnings("UnusedReturnValue")
        public Boolean setMerge(final Boolean merge) {
            final Boolean previous = mMerge;
            mMerge = merge;
            return previous;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void makeRequest(final Activity activity, final String text, final Intent data,
                                final LoadParameters loadParameters) {
            mMergeFromParameters = loadParameters != null && loadParameters.getMerge();

            final boolean forceCache = loadParameters != null && loadParameters.getForceCache();
            if (forceCache || !Utils.isConnected()) {
                CoreLogger.log("request forced to cache, forceCache " + forceCache);

                mSetValue.set(true);
                onComplete(false, getForceStub(loadParameters));
            }
            else
                super.makeRequest(activity, text, data, loadParameters);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onComplete(final boolean success, final D result) {
            Utils.runInBackground(new Runnable() {
                @Override
                public void run() {
                    onCompleteHelper(success, result);
                }

                @NonNull
                @Override
                public String toString() {
                    return "CacheLiveData.onComplete()";
                }
            });

        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public Cursor getCursor() {
            return mCursor;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public void closeCursor() {
            Utils.close(mCursor);
        }

        private void onCompleteHelper(final boolean success, D result) {
            String selection = null;
            if (result instanceof BaseResponse) {
                final LoadParameters loadParameters = ((BaseResponse) result).getParameters();
                final long pageId =  loadParameters == null ? Converter.DEFAULT_PAGE_ID: loadParameters.getPageId();
                selection = pageId == Converter.DEFAULT_PAGE_ID ? null:
                        String.format(Utils.getLocale(), "%s = %d", BaseConverter.PAGE_ID, pageId);
                CoreLogger.log("query selection is '" + selection + "'");
            }
            else
                CoreLogger.logWarning("no query selection (for non-BaseResponse result)");

            if (success && result != null)
                storeResult(selection, getContentValues(result), result, mMerge != null ? mMerge:
                        mMergeFromParameters != null ? mMergeFromParameters: false);
            else {
                CoreLogger.log("about to load data from cache, previous success flag: " + success);
                final String table = Utils.getCacheTableName(mUri);

                try {
                    mCursor = mDataLoader.getCursor(table, result instanceof BaseResponse ?
                            ((BaseResponse) result).getParameters(): null);
                    CoreLogger.log("custom converter getCursor, table: " + table);
                }
                catch (UnsupportedOperationException exception) {
                    CoreLogger.log(CoreLogger.getDefaultLevel(),
                            "default converter getCursor, table: " + table, exception);
                    mCursor = query(selection, table);
                }
                catch (Exception exception) {
                    CoreLogger.log(exception);
                    onComplete(false);
                    return;
                }

                if (mCursor != null) result = handleCursor(result, mCursor);
            }
            super.onComplete(success, result);
        }

        private void handleCacheError(@NonNull final ContentResolver contentResolver,
                                      @NonNull final String table) {
            if (!mDataLoader.isInternalCache()) return;
            CoreLogger.logWarning("about to drop cache table " + table);

            contentResolver.call(mUri, BaseCacheProvider.CALL_EXEC_SQL,
                    String.format(BaseCacheProvider.DROP_TABLE, table), null);
        }

        private Cursor query(final String selection, final String table) {
            final ContentResolver contentResolver = getContext().getContentResolver();
            try {
                final Cursor cursor = contentResolver.query(mUri, null,
                        selection, null, null);
                if (cursor == null)
                    CoreLogger.logError("cache cursor == null, URI: " + mUri);
                return cursor;
            }
            catch (Exception exception) {
                CoreLogger.log("cache query failed, URI: " + mUri, exception);

                handleCacheError(contentResolver, table);
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
        protected D getForceStub(final LoadParameters parameters) {
            return castBaseResponse(new BaseResponse<>(parameters, Source.CACHE));
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected D handleCursor(final D result, @NonNull final Cursor cursor) {
            if (!(result instanceof BaseResponse)) return result;

            final BaseResponse baseResponse = (BaseResponse) result;
            return castBaseResponse(new BaseResponse<>(baseResponse.getParameters(),
                    baseResponse.getResult(), baseResponse.getResponse(), cursor,
                    baseResponse.getError(), Source.CACHE, baseResponse.getThrowable()));
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Collection<ContentValues> getContentValues(final D result) {
            return result instanceof BaseResponse ? ((BaseResponse<?, ?, ?>) result).getValues(): null;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected void storeResult(final String selection, final Collection<ContentValues> values,
                                   final D result, final boolean merge) {
            if (values == null || values.size() == 0) {
                CoreLogger.logError("nothing to store in cache, empty values");
                return;
            }
            if (result instanceof BaseResponse) {
                final Source source = ((BaseResponse) result).getSource();
                if (source != Source.NETWORK) {
                    CoreLogger.log("wrong data source to cache (should be NETWORK): " + source);
                    return;
                }
            }

            final String table = Utils.getCacheTableName(mUri);
            if (table == null) {
                CoreLogger.logError("can't store in cache, empty table name");
                return;
            }
            CoreLogger.log("about to store in cache, merge " + merge);

            Utils.runInBackground(new Runnable() {
                @Override
                public void run() {
                    final ContentResolver contentResolver = getContext().getContentResolver();
                    try {
                        Boolean result;
                        if (!merge) {
                            result = mDataLoader.clear(table);
                            if (result == null) {
                                CoreLogger.logWarning("unexpected error in DataLoader.clear(), table: " + table);
                                return;
                            }

                            if (result)
                                CoreLogger.log("custom converter delete, table: " + table);
                            else {
                                CoreLogger.log("default converter delete, table: " + table);
                                Utils.clearCache(mUri);
                            }
                        }
                        else if (selection != null && mDataLoader.isInternalCache()) {
                            final int rowsQty = contentResolver.delete(mUri, selection, null);
                            CoreLogger.log("paging delete, table: " + table + ", selection: " +
                                    selection + ", rows qty: " + rowsQty);
                        }

                        result = mDataLoader.store(values);
                        if (result == null) {
                            CoreLogger.logWarning("unexpected error in DataLoader.store(), table: " + table);
                            return;
                        }

                        if (result) {
                            CoreLogger.log("custom converter store, table: " + table);
                            return;
                        }
                        CoreLogger.log("default converter store, table: " + table);

                        try {
                            contentResolver.bulkInsert(mUri, values.toArray(new ContentValues[0]));
                        }
                        catch (Exception exception) {
                            handleCacheError(contentResolver, table);
                            throw exception;
                        }
                    }
                    catch (Exception exception) {
                        CoreLogger.log("can not store result, table: " + table, exception);
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
                                                                                                  /*
                             For Madmen Only
                               - Hermann Hesse, Steppenwolf
                                                                                                  */
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
             * Confirms data loading canceling.
             *
             * @param activity
             *        The {@link Activity}
             *
             * @param view
             *        The view for {@link Snackbar} (or null for default one)
             *
             * @return  {@code true} if data load canceling confirmation supported, {@code false} otherwise
             *
             * @see BaseDialog#confirm
             */
            boolean confirm(Activity activity, View view);
        }

        static {
            init();
        }

        /**
         * Cleanups static fields in LiveDataDialog; called from {@link Core#cleanUpFinal()}.
         */
        public static void cleanUpFinal() {
            init();
        }

        private static void init() {
            sInstance = new LiveDataDialog();
        }

        /**
         * The base (with {@link Snackbar} for confirmation) {@link Progress} implementation.
         */
        @SuppressWarnings("unused")
        public static abstract class ProgressDefault implements Progress {

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            protected     static Integer            sSnackbarDuration;
            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            protected     static SnackbarBuilder    sSnackbarBuilder;

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            protected     static ViewModifier       sToastViewModifier;

            private              Snackbar           mSnackbar;

            static {
                init();
            }

            /**
             * Cleanups static fields in ProgressDefault; called from {@link Core#cleanUpFinal()}.
             */
            public static void cleanUpFinal() {
                ProgressDefaultImp.init();
                init();
            }

            private static void init() {
                sSnackbarDuration   = null;
                sSnackbarBuilder    = null;
                sToastViewModifier  = null;
            }

            /**
             * Please refer to the base method description.
             */
            @Override
            public void setText(final String text) {
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
            public boolean confirm(final Activity activity, final View view) {
                if (UiModule.hasSnackbars()) {
                    CoreLogger.logWarning("data loading cancel confirmation Snackbar will not be shown " +
                            "'cause some Yakhont's Snackbar is already on screen (or in queue)");
                    return false;
                }

                if (sSnackbarBuilder != null && sSnackbarDuration != null)
                    CoreLogger.logError("Snackbar's duration " + sSnackbarDuration +
                            " will be ignored 'cause SnackbarBuilder is already set");

                //todo - add in javadoc about: setView() clears Snackbar queue
                final SnackbarBuilder snackbarBuilder = sSnackbarBuilder != null ? sSnackbarBuilder:
                        getDefaultSnackbarBuilder(activity);
                adjustSnackbarBuilder(snackbarBuilder, view);

                mSnackbar = snackbarBuilder.show();

                mSnackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(final Snackbar transientBottomBar, final int event) {
                        mSnackbar = null;
                    }
                });

                return true;
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            protected void adjustSnackbarBuilder(final SnackbarBuilder snackbarBuilder, final View view) {
                if (view != null) snackbarBuilder.setViewId(view.getId());
            }

            /**
             * Creates default SnackbarBuilder for data loading cancel dialog.
             *
             * @return  The default SnackbarBuilder
             */
            public static SnackbarBuilder getDefaultSnackbarBuilder() {
                return getDefaultSnackbarBuilder(null);
            }

            /**
             * Creates default SnackbarBuilder for data loading cancel dialog.
             *
             * @param activity
             *        The Activity
             *
             * @return  The default SnackbarBuilder
             */
            public static SnackbarBuilder getDefaultSnackbarBuilder(Activity activity) {
                final Activity activityToUse = activity != null ? activity: Utils.getCurrentActivity();
                //noinspection Convert2Lambda
                return new SnackbarBuilder()
                        .setTextId(akha.yakhont.R.string.yakhont_loader_alert)
                        .setDuration(sSnackbarDuration != null ? sSnackbarDuration: Snackbar.LENGTH_LONG)

                        .setViewHandlersChain(true)
                        .setViewHandler(Utils.getDefaultSnackbarViewModifier())

                        .setActionTextId(akha.yakhont.R.string.yakhont_alert_yes)
                        .setActionColor(Utils.getDefaultSnackbarActionColor())
                        .setAction(new View.OnClickListener() {
                            @Override
                            public void onClick(final View view) {
                                cancel(activityToUse);
                            }
                        });
            }

            /**
             * Sets the duration of {@code Snackbar} which confirms the data loading cancellation.
             *
             * @param duration
             *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Snackbar.LENGTH_INDEFINITE},
             *        {@code Snackbar.LENGTH_LONG} or {@code Snackbar.LENGTH_SHORT}, null for default value
             */
            public static void setConfirmDuration(final Integer duration) {
                sSnackbarDuration = duration;
            }

            /**
             * Sets the {@code Snackbar} builder (for creating {@code Snackbar} which will confirm
             * the data loading cancellation).
             *
             * @param snackbarBuilder
             *        The {@link SnackbarBuilder}
             *
             * @see #getDefaultSnackbarBuilder
             */
            public static void setSnackbarBuilder(final SnackbarBuilder snackbarBuilder) {
                sSnackbarBuilder = snackbarBuilder;
            }

            /**
             * Sets the {@code Toast} View handler (for customizing data loading progress
             * {@code Toast}). Usage example:
             *
             * <p><pre style="background-color: silver; border: thin solid black;">
             * setToastViewHandler((view, vh) -&gt; {
             *     view.setBackgroundColor(Color.GRAY);
             *     vh.getTextView().setTextColor(Color.YELLOW);
             * //  vh.getToastProgressView().set...;
             * });
             * </pre>
             *
             * @param viewModifier
             *        The Toast's view modifier
             */
            public static void setToastViewHandler(final ViewModifier viewModifier) {
                sToastViewModifier = viewModifier;
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

            /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
            @Override
            protected void adjustSnackbarBuilder(final SnackbarBuilder snackbarBuilder, final View view) {
                snackbarBuilder.setView(view);
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
            @SuppressWarnings("WeakerAccess")
            public static Dialog handle(@NonNull final Dialog dialog, final Callable<Boolean> callback) {
                //noinspection Convert2Lambda
                dialog.setOnKeyListener(new OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP)
                            if (callback != null) return Utils.safeRunBoolean(callback);
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
             * @param key
             *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
             *        for more info); could be null (for default value)

             * @return  The handled {@link Dialog}
             */
            @SuppressWarnings("unused")
            public static Dialog handle(@NonNull final Dialog dialog, final View view, final String key) {
                return handle(dialog, createCallable(BaseViewModel.get(key), null, view));
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
             *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
             *        for more info); could be null (for default value)
             *
             * @return  The handled {@link Dialog}
             */
            @SuppressWarnings("unused")
            public static Dialog handle(@NonNull final Dialog dialog, final View view,
                                        final Activity activity, final String key) {
                return handle(dialog, createCallable(BaseViewModel.get(
                        BaseViewModel.cast(activity, null), key), activity, view));
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
             *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
             *        for more info); could be null (for default value)
             *
             * @return  The handled {@link Dialog}
             */
            @SuppressWarnings("unused")
            public static Dialog handle(@NonNull final Dialog dialog, final View view,
                                        final Fragment fragment, final String key) {
                return handle(dialog, createCallable(BaseViewModel.get(fragment, key),
                        fragment.getActivity(), view));
            }

            private static Callable<Boolean> createCallable(final BaseViewModel baseViewModel,
                                                            final Activity activity, final View view) {
                if (baseViewModel == null)
                    CoreLogger.logWarning("can't create Callable dor Dialog handling");

                return baseViewModel == null ? null: new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        return baseViewModel.getData().confirm(activity, view);
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "BaseViewModel.getData().confirm()";
                    }
                };
            }
        }

        private static class ProgressDefaultImp extends ProgressDefault {

            private final static long               UPDATE_INTERVAL         = 300;

            private       static ProgressDefaultImp sInstance;

            private              Toast              mToast;
            private              CountDownTimer     mCountDownTimer;

            static {
                init();
            }

            private static void init() {
                sInstance = null;
            }

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
                View view = getView(context);

                final ViewHandler viewHandler = getToastViewHandler();

                if (viewHandler != null && !viewHandler.wrapper(view)) {
                    CoreLogger.logError("failed View customization for Toast: " + mToast);
                    view = getView(context);
                }

                mToast.setView(view);
                mToast.setGravity(Gravity.CENTER, 0, 0);
                mToast.setDuration(Toast.LENGTH_SHORT);
            }

            private static ViewHandler getToastViewHandler() {
                return sToastViewModifier == null ? null: new ViewHandler() {
                    @SuppressWarnings("unused")
                    @Override
                    public void modify(final View view, final ViewHandler viewHandler) {
                        sToastViewModifier.modify(view, viewHandler);
                    }
                };
            }

            @SuppressLint("InflateParams")
            private View getView(final Context context) {
                return LayoutInflater.from(context).inflate(
                        akha.yakhont.R.layout.yakhont_progress, null, false);
            }

            @SuppressWarnings("unused")
            @Override
            public void setText(@NonNull final String text) {
                ((TextView) mToast.getView().findViewById(akha.yakhont.R.id.yakhont_loader_text)).setText(text);
            }

            @Override
            public void show() {
                mCountDownTimer = new CountDownTimer(Long.MAX_VALUE, UPDATE_INTERVAL) {
                    @Override
                    public void onTick(final long millisUntilFinished) {
                        mToast.show();
                    }

                    @Override
                    public void onFinish() {
                    }
                }.start();
            }

            @SuppressWarnings("unused")
            @Override
            public void hide() {
                if (mCountDownTimer != null) {
                    mCountDownTimer.cancel();
                    mCountDownTimer = null;
                }
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

        private       static LiveDataDialog         sInstance;

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
         * Shows the data loading progress GUI (it always runs in UI thread).
         *
         * @param text
         *        The text to display in data loading progress GUI
         *
         * @see Progress#show
         */
        @SuppressWarnings("WeakerAccess")
        public void start(final String text) {
            Utils.postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    synchronized (mLock) {
                        startAsync(text);
                    }
                }

                @NonNull
                @Override
                public String toString() {
                    return "LiveDataDialog.startAsync()";
                }
            });
        }

        private void startAsync(final String text) {
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

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
        @Override
        public boolean start(final String text, final Intent data) {
            start(text);
            return true;
        }

        /**
         * Please refer to the base method description (it always runs in UI thread).
         */
        @SuppressWarnings("unused")
        @Override
        public void stop() {
            stop(false, Utils.getCurrentActivity());
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public void stop(final boolean force, final Activity activity) {
            Utils.postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    try {
                        final boolean result = stopNotSafe(force, activity);
                        CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.WARNING,
                                "stop progress result: " + result);
                    }
                    catch (Exception exception) {
                        CoreLogger.log(exception);
                    }
                }

                @NonNull
                @Override
                public String toString() {
                    return "LiveDataDialog.stopNotSafe()";
                }
            });
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
                        BaseViewModel.cast(activity, null), true, CoreLogger.getDefaultLevel());
                if (models.size() == 0) {
                    CoreLogger.logWarning("only current BaseLiveData loading will be " +
                            "stopped 'cause of unsupported Activity " + CoreLogger.getDescription(activity));
                    cancel();
                }
                else
                    for (final BaseViewModel<?> model : models)
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
                                Utils.safeRun(mOnCancel);
                            }
                            catch (Exception exception) {
                                CoreLogger.log(exception);
                            }
                        }
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "LiveDataDialog.cancel()";
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
            Utils.postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    if (!mProgress.confirm(activity, view)) cancel(activity);
                }

                @NonNull
                @Override
                public String toString() {
                    return "Progress.confirm()";
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
            CoreLogger.logWarning("about to cancel data loading");
            sInstance.stop(true, activity);
        }
    }
}
