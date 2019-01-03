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

package akha.yakhont.loader.wrapper;

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
// ProGuard issue
// import akha.yakhont.R;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog.Progress;
import akha.yakhont.loader.BaseLiveData.Requester;
import akha.yakhont.loader.BaseResponse.LoadParameters;
import akha.yakhont.loader.BaseViewModel;

import android.app.Activity;
import android.view.View;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelStore;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public abstract class BaseLoaderWrapper<D> {

    private static final String                             FORMAT_INFO                 = "BaseLoaderWrapper: %s";

    private final ViewModelStore                            mViewModelStore;

    private final String                                    mLoaderId;
    private final boolean                                   mLoaderIdAutoGenerated;

    private SwipeRefreshWrapper                             mSwipeRefreshWrapper;

    private CountDownLatch                                  mCountDownLatch;
    private D                                               mData;

    private static final Random                             RANDOM                      = new Random();

    private Type                                            mType;

    private Callable<Progress>                              mProgress;
    private Callable<BaseViewModel<D>>                      mBaseViewModel;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static boolean isLoaderIdAutoGenerated(final String loaderId) {
        return loaderId == null;
    }

    protected BaseLoaderWrapper(@NonNull final Activity activity, final String loaderId) {
        this(activity, loaderId, isLoaderIdAutoGenerated(loaderId));
    }

    protected BaseLoaderWrapper(@NonNull final Fragment fragment, final String loaderId) {
        this(fragment, loaderId, isLoaderIdAutoGenerated(loaderId));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected BaseLoaderWrapper(@NonNull final Activity activity, final String loaderId,
                                         final boolean  loaderIdAutoGenerated) {
        this(BaseViewModel.getViewModelStore(activity), loaderId, loaderIdAutoGenerated);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected BaseLoaderWrapper(@NonNull final Fragment fragment, final String loaderId,
                                         final boolean  loaderIdAutoGenerated) {
        this(BaseViewModel.getViewModelStore(fragment), loaderId, loaderIdAutoGenerated);
    }

    protected BaseLoaderWrapper(final String loaderId) {
        this(loaderId, isLoaderIdAutoGenerated(loaderId));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected BaseLoaderWrapper(final String loaderId, final boolean loaderIdAutoGenerated) {
        this(BaseViewModel.getViewModelStore((Activity) null), loaderId, loaderIdAutoGenerated);
    }

    protected BaseLoaderWrapper(@NonNull final ViewModelStore viewModelStore, final String loaderId) {
        this(viewModelStore, loaderId, isLoaderIdAutoGenerated(loaderId));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected BaseLoaderWrapper(@NonNull final ViewModelStore viewModelStore,
                                final String loaderId, final boolean loaderIdAutoGenerated) {

        mViewModelStore             = viewModelStore;

        mLoaderIdAutoGenerated      = loaderId == null || loaderIdAutoGenerated;
        mLoaderId                   = loaderId != null ? loaderId: generateLoaderId();

        if (mLoaderIdAutoGenerated) CoreLogger.log("auto generated id " + loaderId);
    }

    public void setProgress(final Callable<Progress> progress) {
        mProgress       = progress;
    }

    public void setBaseViewModel(final Callable<BaseViewModel<D>> baseViewModel) {
        if (baseViewModel != null && mProgress != null)
            CoreLogger.logWarning("you set BaseViewModel; already set Callable<Progress> " +
                    "(via setProgress() call) will be ignored");
        mBaseViewModel  = baseViewModel;
    }

    public void setType(final Type type) {
        mType           = type;
    }

    public Type getType() {
        return mType;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected void setTypeIfNotSet(@NonNull final Type type) {
        if (mType == null)
            mType = type;
        else if (!mType.equals(type))
            CoreLogger.logWarning("setTypeIfNotSet: type " + type + " will be ignored");
    }

    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    public BaseLoaderWrapper<D> setSwipeRefreshWrapper(final SwipeRefreshWrapper swipeRefreshWrapper) {
        mSwipeRefreshWrapper        = swipeRefreshWrapper;
        return this;
    }

    private static String generateLoaderId() {
        return String.valueOf(RANDOM.nextInt(Integer.MAX_VALUE));
    }

    public String getLoaderId() {
        return mLoaderId;
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public boolean isAutoGeneratedId() {
        return mLoaderIdAutoGenerated;
    }

    public BaseLoaderWrapper findLoader(final Collection<BaseLoaderWrapper<?>> loaders) {

        for (final BaseLoaderWrapper baseLoaderWrapper: loaders)
            if (baseLoaderWrapper.mLoaderId.equals(mLoaderId)) {
                CoreLogger.log("found loader with id " + mLoaderId);
                return baseLoaderWrapper;
            }

        CoreLogger.log("can't find loader with id " + mLoaderId);
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Utils.getLocale(), FORMAT_INFO, mLoaderId);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean start(final Collection<BaseLoaderWrapper<?>> loaders, final LoadParameters parameters) {
        return start(null, loaders, parameters);
    }

    public static boolean start(final Activity activity, final Collection<BaseLoaderWrapper<?>> loaders,
                                final LoadParameters parameters) {
        if (!checkEmpty(Level.ERROR, loaders)) return false;

        if (parameters == null || parameters.getLoaderId() == null) {
            boolean result = true;
            for (final BaseLoaderWrapper loader: loaders)
                if (!loader.start(activity, parameters)) result = false;
            return result;
        }
        else {
            final String id = parameters.getLoaderId();
            for (final BaseLoaderWrapper loader: loaders)
                if (loader.getLoaderId().equals(id))
                    return loader.start(activity, parameters);
            CoreLogger.logError("invalid loader ID: " + id);
            return false;
        }
    }

    public boolean start() {
        return start(new LoadParameters());
    }

    public boolean start(final LoadParameters parameters) {
        return start((Activity) null, parameters);
    }

    public boolean start(final Activity activity, final LoadParameters parameters) {
        final boolean result = startHelper(activity, parameters);
        CoreLogger.log(result ? Level.DEBUG: Level.ERROR, "start loading result: " + result);
        return result;
    }

    private boolean startHelper(final Activity activity, final LoadParameters parameters) {

        if (parameters == null)
            return startLoading(activity, null);

        if (parameters.getSync())
            return startSync(activity, parameters) != null;

        if (!parameters.checkArguments()) return false;

        final String id = parameters.getLoaderId();
        if (id != null && !mLoaderId.equals(id)) {
            CoreLogger.logError("invalid loader ID: " + id + " (required " + mLoaderId + " or null)");
            return false;
        }

        return startLoading(activity, parameters);
    }

    private BaseViewModel<?> findViewModel(@Size(min = 1, max = 1) final boolean[] create,
            final Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> viewModels) {

        if (viewModels == null) {
            CoreLogger.log("ViewModels == null");
            return null;
        }

        for (final Map.Entry<String, WeakReference<? extends BaseViewModel<?>>> entry: viewModels)
            if (entry.getKey().equals(mLoaderId)) {
                final BaseViewModel<?> model = entry.getValue().get();
                if (model == null) CoreLogger.logWarning("model == null, id: " + mLoaderId);
                return model;
            }

        if (create == null)
            CoreLogger.log("no ViewModels found");
        else
            create[0] = true;

        return null;
    }

    protected String getTableName() {
        return null;
    }

    protected String getTableDescription() {
        return null;
    }

    protected Progress getProgress() {
        try {
            return mProgress == null ? null: mProgress.call();
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
            return null;
        }
    }

    protected BaseViewModel<D> getBaseViewModel(final Activity activity, final LoadParameters parameters) {

        try {
            if (mBaseViewModel != null) return mBaseViewModel.call();
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
            return null;
        }

        final RequesterHelper requesterHelper = new RequesterHelper();

        final BaseViewModel<D> baseViewModel = BaseViewModel.getInstance(activity, mViewModelStore,
                mLoaderId, getTableName(), requesterHelper, new Observer<D>() {
                    @Override
                    public void onChanged(@Nullable D data) {
                        onLoadFinished(data, parameters);
                    }
                });

        requesterHelper.mBaseViewModel = new WeakReference<>(baseViewModel);

        final Progress progress = getProgress();
        if (progress != null) {
            final BaseDialog baseDialog = baseViewModel.getData().getBaseDialog();

            if (baseDialog instanceof LiveDataDialog)
                ((LiveDataDialog) baseDialog).setProgress(progress);
            else
                CoreLogger.logError("can't set progress for baseDialog " +
                        "'cause it's not an instance of LiveDataDialog but " + baseDialog.getClass().getName());
        }

        return baseViewModel;
    }

    private BaseViewModel<?> getViewModel(final Activity activity, final LoadParameters parameters,
                                          final Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> viewModels) {
        final boolean[] create = new boolean[1];
        final BaseViewModel<?> model = findViewModel(create, viewModels);

        return create[0] ? getBaseViewModel(activity, parameters): model;
    }

    private class RequesterHelper implements Requester<D> {

        private                                             WeakReference<BaseViewModel<D>>
                                                                                        mBaseViewModel;
        @Override
        public void cancel() {
            cancelRequest(Level.DEBUG);
        }

        @Override
        public D call() {
            D result = null;
            try {
                final BaseViewModel<D> baseViewModel = getBaseViewModel(true);
                if (baseViewModel == null)
                    CoreLogger.logError("can't make request 'cause baseViewModel == null, loader ID: " + mLoaderId);
                else
                    result = makeRequest(baseViewModel);
            }
            catch (Exception exception) {
                CoreLogger.log("loader ID: " + mLoaderId, exception);
            }
            return result;
        }

        private BaseViewModel<D> getBaseViewModel(final boolean isError) {
            BaseViewModel<D> result = null;
            if (mBaseViewModel == null)     // should never happen
                CoreLogger.log(isError ? Level.ERROR: Level.DEBUG, "mBaseViewModel == null, loader ID: " + mLoaderId);
            else {
                result = mBaseViewModel.get();
                if (result == null)         // should never happen
                    CoreLogger.log(isError ? Level.ERROR: Level.DEBUG, "baseViewModel == null, loader ID: " + mLoaderId);
            }
            return result;
        }
    }

    protected void onLoadFinished(final D data, final LoadParameters parameters) {

        if (CoreLogger.isFullInfo())
            CoreLogger.log("loader ID: " + mLoaderId + ", data: " + data);

        mData = data;

        handleSync();
        setRefreshing();
    }

    protected abstract D makeRequest(@NonNull final BaseViewModel<D> baseViewModel);

    protected boolean cancelRequest(@NonNull final Level level) {
        CoreLogger.log(level, "request cancelling is not supported");
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLoading() {
        final BaseViewModel<?> model = findViewModel(null, BaseViewModel.getViewModels(mViewModelStore));
        return model != null && model.getData().isLoading();
    }

    private boolean startLoading(Activity activity, final LoadParameters parameters) {

        mData = null;

        if (activity == null) activity = Utils.getCurrentActivity();

        final BaseViewModel<?> model = getViewModel(activity, parameters,
                BaseViewModel.getViewModels(mViewModelStore));

        if (model != null) {
            final String tableDescription = getTableDescription();
            model.getData().makeRequest(activity, LiveDataDialog.getInfoText(
                    tableDescription != null ? tableDescription: mLoaderId), null, parameters);
            return true;
        }

        // should never happen
        CoreLogger.logError("there's no model with id " + mLoaderId);
        return false;
    }

    private void setRefreshing() {
        if (mSwipeRefreshWrapper != null) mSwipeRefreshWrapper.setRefreshing();
    }

    private void handleSync() {
        if (mCountDownLatch == null) return;

        mCountDownLatch.countDown();
        mCountDownLatch = null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public D startSync() {
        return startSync(new LoadParameters());
    }

    @SuppressWarnings("WeakerAccess")
    @WorkerThread
    public D startSync(final LoadParameters parameters) {
        return startSync((Activity) null, parameters);
    }

    @SuppressWarnings("WeakerAccess")
    @WorkerThread
    public D startSync(final Activity activity, final LoadParameters parameters) {
        final boolean result = startSync(activity, Arrays.asList(new BaseLoaderWrapper[] {this}), parameters);
        return result ? mData: null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean checkEmpty(@NonNull final Level level, final Collection<BaseLoaderWrapper<?>> loaders) {
        final boolean empty = loaders == null || loaders.isEmpty();
        if (empty)
            CoreLogger.log(level, "no loaders in collection");
        else
            CoreLogger.log("loaders list size: " + loaders.size());
        return !empty;
    }

    @SuppressWarnings("WeakerAccess")
    @WorkerThread
    public static boolean startSync(final Collection<BaseLoaderWrapper<?>> loaders,
                                    final LoadParameters                   parameters) {
        return startSync(null, loaders, parameters);
    }

    @SuppressWarnings("WeakerAccess")
    @WorkerThread
    public static boolean startSync(final Activity activity, final Collection<BaseLoaderWrapper<?>> loaders,
                                    final LoadParameters parameters) {
        if (parameters != null && !parameters.checkArguments()) return false;

        if (parameters != null && !parameters.getSync()) {
            CoreLogger.logError("invalid parameter sync: false");
            return false;
        }
        if (Utils.isCurrentThreadMain()) {
            CoreLogger.logError("not allowed to run from the main thread");
            return false;
        }

        if (!checkEmpty(Level.ERROR, loaders)) return false;

        if (parameters != null && parameters.getLoaderId() != null) {
            final String id = parameters.getLoaderId();
            boolean found = false;
            for (final BaseLoaderWrapper loader: loaders)
                if (loader.mLoaderId.equals(id)) {
                    found = true;
                    break;
                }
            if (!found) {
                CoreLogger.logError("no such loader ID: " + id);
                return false;
            }
        }

        final CountDownLatch countDownLatch = new CountDownLatch(
                parameters != null && parameters.getLoaderId() != null ? 1: loaders.size());

        for (final BaseLoaderWrapper loader: loaders)
            if (checkId(parameters, loader.mLoaderId))
                loader.setCountDownLatch(countDownLatch);

        final boolean[] result = new boolean[] {true};
        //noinspection Convert2Lambda
        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                for (final BaseLoaderWrapper loader: loaders)
                    if (checkId(parameters, loader.mLoaderId))
                        if (!loader.start(activity, parameters)) result[0] = false;
            }
        });

        await(countDownLatch);

        for (final BaseLoaderWrapper loader: loaders)
            if (checkId(parameters, loader.mLoaderId))
                loader.setCountDownLatch(null);

        CoreLogger.log("completed");
        return result[0];
    }

    private static boolean checkId(final LoadParameters parameters, final String id) {
        if (parameters == null) return true;
        final String idParam = parameters.getLoaderId();
        return idParam == null || id.equals(idParam);
    }

    private static void await(final CountDownLatch countDownLatch) {
        if (countDownLatch == null) return;
        try {
            countDownLatch.await();
        }
        catch (InterruptedException e) {
            CoreLogger.log("interrupted", e);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private BaseLoaderWrapper<D> setCountDownLatch(final CountDownLatch countDownLatch) {
        mCountDownLatch = countDownLatch;
        return this;
    }

    @SuppressWarnings("unused")
    public D getResult() {
        return mData;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    public static class SwipeRefreshWrapper<D> {

        @IdRes
        private static final int                            ID_SWIPE_LAYOUT             =
                akha.yakhont.R.id.yakhont_swipe_refresh_layout;

        private final OnRefreshListenerHelper               mOnRefreshListener;

        private final WeakReference<SwipeRefreshLayout>     mSwipeRefreshLayout;

        private SwipeRefreshWrapper(@NonNull final OnRefreshListenerHelper           onRefreshListener,
                                    @NonNull final WeakReference<SwipeRefreshLayout> swipeRefreshLayout) {
            mOnRefreshListener  = onRefreshListener;
            mSwipeRefreshLayout = swipeRefreshLayout;
        }

        public static boolean register(Activity activity, @IdRes final int    resId,
                                       final Collection<BaseLoaderWrapper<?>> baseLoaderWrappers,
                                       final LoadParameters                   parameters) {
            final int size = baseLoaderWrappers.size();
            if (size != 1) {
                CoreLogger.logError("wrong BaseLoaderWrapper collection size " + size);
                return false;
            }
            return register(activity, resId, baseLoaderWrappers.iterator().next(), parameters);
        }

        public static <D> boolean register(Activity activity, @IdRes final int resId,
                                           final BaseLoaderWrapper<D> baseLoaderWrapper,
                                           final LoadParameters       parameters) {
            return register(activity, resId, baseLoaderWrapper,
                    parameters == null ? null: new Callable<LoadParameters>() {
                        @Override
                        public LoadParameters call() {
                            return parameters;
                        }
                    });
        }

        public static <D> boolean register(Activity activity, @IdRes final int  resId,
                                           final BaseLoaderWrapper<D>           baseLoaderWrapper,
                                           final Callable<LoadParameters>       parameters) {
            if (activity == null) {
                CoreLogger.logWarning("activity == null, current one will be used for SwipeRefresh");
                activity = Utils.getCurrentActivity();
            }

            if (baseLoaderWrapper == null)
                CoreLogger.logError("baseLoaderWrapper == null");
            else {
                final View swipeRefreshLayout = activity.findViewById(resId);
                if (swipeRefreshLayout == null)
                    CoreLogger.logError("can't find SwipeRefreshLayout with id " +
                            CoreLogger.getResourceDescription(resId));
                else {
                    if (!(swipeRefreshLayout instanceof SwipeRefreshLayout))
                        CoreLogger.logError("view with id " + CoreLogger.getResourceDescription(resId) +
                                " is not SwipeRefreshLayout but " + swipeRefreshLayout.getClass().getName());
                    else {
                        final OnRefreshListenerHelper<D> onRefreshListener = new OnRefreshListenerHelper<>(
                                baseLoaderWrapper, parameters);
                        final SwipeRefreshWrapper<D> swipeRefreshWrapper = new SwipeRefreshWrapper<>(
                                onRefreshListener, new WeakReference<>((SwipeRefreshLayout) swipeRefreshLayout)
                        );
                        onRefreshListener.mSwipeRefreshWrapper = swipeRefreshWrapper;

                        ViewHelper.setTag(swipeRefreshLayout, ID_SWIPE_LAYOUT,
                                new WeakReference<SwipeRefreshWrapper>(swipeRefreshWrapper));
                        return true;
                    }
                }
            }
            CoreLogger.logError("can't register");
            return false;
        }

        private static class OnRefreshListenerHelper<D> implements OnRefreshListener {

            private final Callable<LoadParameters>          mParameters;
            private final BaseLoaderWrapper  <D>            mBaseLoaderWrapper;
            private       SwipeRefreshWrapper<D>            mSwipeRefreshWrapper;

            private OnRefreshListenerHelper(@NonNull final BaseLoaderWrapper<D>     baseLoaderWrapper,
                                                     final Callable<LoadParameters> parameters) {
                mBaseLoaderWrapper      = baseLoaderWrapper;
                mParameters             = parameters;
            }

            @Override
            public void onRefresh() {
                LoadParameters loadParameters = null;

                try {
                    if (mParameters != null) loadParameters = mParameters.call();
                    if (loadParameters != null)
                        CoreLogger.log("SwipeRefresh - accepted values for LoadParameters: " + loadParameters);
                }
                catch (Exception exception) {
                    CoreLogger.log(exception);
                }

                if (loadParameters == null) {
                    loadParameters = new LoadParameters();
                    CoreLogger.log("SwipeRefresh - accepted default values for LoadParameters: " + loadParameters);
                }

                final SwipeRefreshLayout swipeRefreshLayout = mSwipeRefreshWrapper.get();
                if (swipeRefreshLayout == null) return;

                if (!loadParameters.getNoProgress()) swipeRefreshLayout.setRefreshing(false);

                mBaseLoaderWrapper.setSwipeRefreshWrapper(mSwipeRefreshWrapper);
                mBaseLoaderWrapper.start(loadParameters);
            }
        }

        private static Collection<View> findViews(final Activity activity) {
            final ArrayList<View> result = new ArrayList<>();

            //noinspection Convert2Lambda
            ViewHelper.findView(result, ViewHelper.getView(activity), new ViewHelper.ViewVisitor() {
                @SuppressWarnings("unused")
                @Override
                public boolean handle(final View view) {
                    return view instanceof SwipeRefreshLayout;
                }
            });

            return result;
        }

        // subject to call from weaver
        public static void onPauseOrResume(@NonNull final Activity activity, final boolean resume) {
            for (final View view: findViews(activity)) {
                @SuppressWarnings("unchecked")
                final WeakReference<SwipeRefreshWrapper> weakReference =
                        (WeakReference<SwipeRefreshWrapper>) view.getTag(ID_SWIPE_LAYOUT);
                if (weakReference == null) return;

                final SwipeRefreshWrapper wrapper = weakReference.get();
                if (wrapper == null)
                    CoreLogger.logError("wrapper == null for Activity " +
                            CoreLogger.getActivityName(activity));
                else
                    ((SwipeRefreshLayout) view).setOnRefreshListener(
                            resume ? wrapper.mOnRefreshListener: null);
            }
        }

        private SwipeRefreshLayout get() {
            final SwipeRefreshLayout swipeRefreshLayout = mSwipeRefreshLayout.get();
            CoreLogger.log(swipeRefreshLayout == null ? Level.ERROR: Level.DEBUG,
                    "mSwipeRefreshLayout.get(): " + swipeRefreshLayout);
            return swipeRefreshLayout;
        }

        private void setRefreshing() {
            final SwipeRefreshLayout swipeRefreshLayout = get();
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        }
    }
}
