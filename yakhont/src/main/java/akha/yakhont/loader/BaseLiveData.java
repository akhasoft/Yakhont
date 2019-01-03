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

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger;
// ProGuard issue
// import akha.yakhont.R;
import akha.yakhont.loader.BaseResponse.LoadParameters;
import akha.yakhont.loader.BaseResponse.Source;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.MutableLiveData;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Provider;

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

    private        final Requester<D>               mRequester;
    private        final BaseDialog                 mBaseDialog;
    private        final AtomicBoolean              mLoading                = new AtomicBoolean();
    private        final AtomicBoolean              mSetValue               = new AtomicBoolean();

    private              LiveDataLoadParameters     mLoadParameters;
    private        final Object                     mLockLoading            = new Object();

    private        final Provider<BaseDialog>       mToast;

    public interface Requester<D> extends Callable<D> {
        void cancel();
    }

    public BaseDialog getBaseDialog() {
        return mBaseDialog;
    }

    protected int getDefaultTimeout() {
        return Core.TIMEOUT_CONNECTION;
    }

    private static BaseDialog getDefaultBaseDialog() {
        return LiveDataDialog.getInstance();
    }

    public BaseLiveData(@NonNull final Requester<D> requester) {
        this(requester, getDefaultBaseDialog());
    }

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
                return "BaseDialog.setOnCancel()";
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

        //noinspection Convert2Lambda
        postToMainLoop(new Runnable() {
            @Override
            public void run() {
                if (!mLoadParameters.mParameters.getNoProgress()) mBaseDialog.stop();
            }
        });

        final boolean notDisplayErrors = mLoadParameters.mParameters.getNoErrors();
        mLoadParameters = null;

        return notDisplayErrors;
    }

    private Boolean onComplete(final boolean cancel) {
        synchronized (mLockLoading) {
            return onCompleteAsync(cancel);
        }
    }

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

        //noinspection Convert2Lambda
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

    protected void handleError(final D result, final boolean notDisplayErrors) {
        CoreLogger.logError(LOADING_FAILED);
        displayError(null, notDisplayErrors);
    }

    protected void displayError(final BaseResponse baseResponse, final boolean notDisplayErrors) {
        if (!notDisplayErrors)
            mToast.get().start(null, makeErrorMessage(baseResponse), null);
    }

    protected String makeErrorMessage(final BaseResponse baseResponse) {
        return Utils.getApplication().getString(akha.yakhont.R.string.yakhont_loader_error_network);
    }

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

                mLoadParameters = new LiveDataLoadParameters(handleTimeout ?
                        Utils.runInBackground( /* loadParameters == null ? getDefaultTimeout(): */
                                loadParameters.getTimeout(), new Runnable() {
                            @Override
                            public void run() {
                                CoreLogger.logWarning("request timeout " + mRequester);
                                onComplete(false, getTimeoutStub(), true);
                            }
                        }): null, loadParameters);
            }

            //noinspection Convert2Lambda
            postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    if (!mLoadParameters.mParameters.getNoProgress())
                        mBaseDialog.start(activity, text, data);
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

    @SuppressWarnings("unchecked")
    private static <D> D castBaseResponse(@NonNull final BaseResponse baseResponse) {
        return (D) baseResponse;
    }

    protected D getErrorStub(final Exception exception) {
        return castBaseResponse(new BaseResponse<>(
                null, null, null, null, Source.UNKNOWN, exception));
    }

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

    public boolean confirm(final Activity activity) {
        return mBaseDialog.confirm(activity);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class CacheLiveData<D> extends BaseLiveData<D> {

        private static final BaseResponse           FORCE_STUB              = new BaseResponse(Source.CACHE);

        private        final Uri                    mUri;

        private        final AtomicBoolean          mMerge                  = new AtomicBoolean();

        public CacheLiveData(@NonNull final Requester<D> requester,
                             @NonNull final String tableName, final UriResolver uriResolver) {
            super(requester);
            mUri = init(tableName, uriResolver);
        }

        public CacheLiveData(@NonNull final Requester<D> requester, final BaseDialog dialog,
                             @NonNull final String tableName, final UriResolver uriResolver) {
            super(requester, dialog);
            mUri = init(tableName, uriResolver);
        }

        private Uri init(@NonNull final String tableName, UriResolver uriResolver) {
            if (uriResolver == null) uriResolver = Utils.getUriResolver();
            return uriResolver.getUri(tableName);
        }

        @Override
        public void makeRequest(final Activity activity, final String text, final Intent data,
                                final LoadParameters loadParameters) {
            mMerge.set(loadParameters != null && loadParameters.getMerge());

            final boolean forceCache = loadParameters != null && loadParameters.getForceCache();
            if (forceCache || !Utils.isConnected()) {
                CoreLogger.log("request forced to cache, forceCache " + forceCache);

                onComplete(false, getForceStub());
            }
            else
                super.makeRequest(activity, text, data, loadParameters);
        }

        @Override
        public void onComplete(final boolean success, D result) {
            if (success && result != null)
                storeResult(getContentValues(result), result, mMerge.get());
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

        @Override
        protected void onCompleteHelper(final boolean success, final D result, final Boolean notDisplayErrors) {
            if (!success) setValue(result);
            super.onCompleteHelper(success, result, notDisplayErrors);
        }

        protected D getForceStub() {
            return castBaseResponse(FORCE_STUB);
        }

        protected D handleCursor(final D result, @NonNull final Cursor cursor) {
            if (!(result instanceof BaseResponse)) return result;

            final BaseResponse baseResponse = (BaseResponse) result;
            return castBaseResponse(new BaseResponse<>(null, null, cursor,
                    baseResponse.getError(), Source.CACHE, baseResponse.getThrowable()));
        }

        protected ContentValues[] getContentValues(final D result) {
            return result instanceof BaseResponse ? ((BaseResponse) result).getValues(): null;
        }

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

            //noinspection Convert2Lambda
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

    public static class LiveDataDialog implements BaseDialog {

        public interface Progress {
            void    setText(String text);
            void    show();
            void    hide();
            void    confirm(Activity activity);
        }

        public static abstract class ProgressDefault implements Progress {

            private              Snackbar           mSnackbar;
            private       static Integer            sSnackbarDuration;

            @CallSuper
            @Override
            public void hide() {
                if (mSnackbar == null) return;

                mSnackbar.dismiss();
                mSnackbar = null;
            }

            @Override
            public void confirm(final Activity activity) {
                mSnackbar = Snackbar.make(ViewHelper.getViewForSnackbar(activity, null),

                        akha.yakhont.R.string.yakhont_loader_alert,

                        sSnackbarDuration != null ? sSnackbarDuration: Snackbar.LENGTH_SHORT)

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

                mSnackbar.show();
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

            private void makeToast() {

                final Context context = getContext();

                mToast = new Toast(context);

                //noinspection InflateParams
                mToast.setView(LayoutInflater.from(context)
                        .inflate(akha.yakhont.R.layout.progress, null, false));

                mToast.setGravity(Gravity.CENTER, 0, 0);
                mToast.setDuration(Toast.LENGTH_SHORT);
            }

            @Override
            public void setText(@NonNull final String text) {
                ((TextView) mToast.getView().findViewById(akha.yakhont.R.id.yakhont_loader_text))
                        .setText(text);
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

        @NonNull
        public static LiveDataDialog getInstance() {
            return sInstance;
        }

        private LiveDataDialog() {
        }

        public Progress getProgress() {
            return mProgress;
        }

        public boolean setProgress(final Progress progress) {
            final boolean result = mProgress == null && progress != null;
            if (!result)
                CoreLogger.logWarning(String.format("unexpected progress: %s (mProgress: %s)" +
                         progress == null ? "null":  progress.toString(),
                        mProgress == null ? "null": mProgress.toString()));

            mProgress = progress;
            return result;
        }

        public boolean isLoading() {
            synchronized (mLock) {
                return mIsLoading;
            }
        }

        public static String getInfoText() {
            return getInfoText(null);
        }

        public static String getInfoText(@StringRes final int tableInfo) {
            return getInfoText(getContext().getResources().getString(tableInfo));
        }

        public static String getInfoText(final String tableInfo) {
            final Context context = getContext();
            return context.getString(akha.yakhont.R.string.yakhont_loader_progress, tableInfo != null ?
                    tableInfo: context.getString(akha.yakhont.R.string.yakhont_loader_progress_def_info));
        }

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

        @Override
        public boolean start(Activity context, String text, Intent data) {
            start(text);
            return true;
        }

        @Override
        public boolean stop() {
            return stop(false, null);
        }

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
                final Collection<BaseViewModel<?>> models =
                        BaseViewModel.getViewModels(activity, true);
                if (models == null) {
                    CoreLogger.logWarning("only current BaseLiveData loading will be " +
                            "stopped 'cause of unsupported Activity " + CoreLogger.getActivityName(activity));
                    cancel();
                }
                else
                    for (final BaseViewModel<?> model: models)
                        model.getData().mBaseDialog.cancel();
            }

            return true;
        }

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
                                mOnCancel.run();
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

        @Override
        public boolean setOnCancel(final Runnable runnable) {
            synchronized (mLockCancel) {
                mOnCancel = runnable;
            }
            return true;
        }

        public static void setConfirmDuration(final int duration) {
            if (sInstance.mProgress instanceof ProgressDefault)
                ((ProgressDefault) sInstance.mProgress).sSnackbarDuration = duration;
            else
                CoreLogger.logWarning("confirmation duration " + duration + " ignored for " +
                        sInstance.mProgress.getClass());
        }

        @Override
        public boolean confirm(final Activity activity) {
            postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    mProgress.confirm(activity);
                }
            });
            return true;
        }

        public static void cancel(final Activity activity) {
            sInstance.stop(true, activity);
        }
    }
}
