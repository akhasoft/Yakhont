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

package akha.yakhont.fragment;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.ConfigurationChangedListener;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.SupportHelper;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.debug.BaseFragment;
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoaderBuilder;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Loader;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Provider;

/**
 * The <code>WorkerFragment</code> class is responsible for data loading.
 *
 * @see CoreLoad
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
public class WorkerFragment extends BaseFragment implements ConfigurationChangedListener, CoreLoad {

    /** The tag for this fragment. */
    @SuppressWarnings("WeakerAccess")
    public static final String                              TAG                         = Utils.getTag(WorkerFragment.class);

    private       final Set<BaseLoaderWrapper>              mLoaders                    = Utils.newSet();
    private             Provider<BaseDialog>                mProgressProvider;

    /**
     * Initialises a newly created {@code WorkerFragment} object.
     */
    public WorkerFragment() {
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        Core.register(this);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mProgressProvider == null) mProgressProvider = Core.getDagger().getProgress();

        if (mLoadersCounterSave.get() <= 0) return;

        final LoaderManager loaderManager = getLoaderManager();
        if (loaderManager == null)
            CoreLogger.logError("loaderManager == null");
        
        else {
            CoreLogger.log("restore loaders callbacks");

            for (final BaseLoaderWrapper baseLoaderWrapper: getLoaders()) {
                if (!baseLoaderWrapper.isLoading()) return;

                final Loader loader = loaderManager.getLoader(baseLoaderWrapper.getLoaderId());
                if (loader != null)
                    restoreCallbacks(loaderManager, loader.getId(), baseLoaderWrapper);
                else
                    CoreLogger.logError("can not find loader, id " + baseLoaderWrapper.getLoaderId());
             }
        }

        showProgress(mTextSave.get());

        mProgress.mLoadersCounter.set(mLoadersCounterSave.get());
        mLoadersCounterSave.set(0);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onChangedConfiguration(Configuration newConfig) {
        if (mProgress.mProgressDialog == null) return;

        mLoadersCounterSave.set(mProgress.mLoadersCounter.get());
        hideProgress(true);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onDetach() {
        for (final BaseLoaderWrapper loader: getLoaders()) {
            loader.setSwipeRefreshWrapper(null);
            loader.setProgress(null);
            setAdapter(loader, null);
        }

        super.onDetach();
    }

    private void restoreCallbacks(@NonNull final LoaderManager loaderManager, final int id,
                                  @NonNull final BaseLoaderWrapper<?> callback) {
        loaderManager.initLoader(id, null, callback);
    }

    @SuppressWarnings("unchecked")
    private void setAdapter(@NonNull final BaseLoaderWrapper loader, final CacheAdapter adapter) {
        if (loader instanceof BaseResponseLoaderWrapper)
            ((BaseResponseLoaderWrapper) loader).setAdapter(adapter);
        else
            CoreLogger.logError("can not set adapter, class " + loader.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private void setRxUnchecked(@NonNull final BaseResponseLoaderWrapper baseResponseLoaderWrapper,
                                final LoaderRx rx) {
        baseResponseLoaderWrapper.setRx(rx);
    }

    private void setRx(@NonNull final BaseLoaderWrapper loader, final LoaderRx rx) {
        if (!(loader instanceof BaseResponseLoaderWrapper)) return;

        final BaseResponseLoaderWrapper baseResponseLoaderWrapper = (BaseResponseLoaderWrapper) loader;

        final LoaderRx prevRx = baseResponseLoaderWrapper.getRx();
        if (prevRx == rx) return;

        if (prevRx != null && rx != null && !rx.hasObservers()) {
            CoreLogger.logWarning("no observers in Rx passed; Rx ignored");
            return;
        }
        if (prevRx != null) prevRx.cleanup();
        CommonRx.unsubscribeAnonymous();

        setRxUnchecked(baseResponseLoaderWrapper, rx);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onDestroy() {
        for (final BaseLoaderWrapper loader: mLoaders)
            setRx(loader, null);

        destroyLoaders();

        Core.unregister(this);

        super.onDestroy();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Finds instance of {@code WorkerFragment}.
     *
     * @param fragment
     *        The fragment (to get the associated activity in which to find)
     *
     * @return  The {@code WorkerFragment} object (or null)
     */
    public static WorkerFragment findInstance(final Fragment fragment) {
        if (fragment == null) {
            CoreLogger.logError("fragment == null");
            return null;
        }
        return (fragment instanceof WorkerFragment) ? (WorkerFragment) fragment: findInstance(fragment.getActivity());
    }

    /**
     * Finds instance of {@code WorkerFragment}.
     *
     * @param activity
     *        The activity in which to find
     *
     * @return  The {@code WorkerFragment} object (or null)
     */
    public static WorkerFragment findInstance(final Activity activity) {
        if (activity == null) {
            CoreLogger.logError("activity == null");
            return null;
        }

        final FragmentManager fragmentManager = SupportHelper.getFragmentManager(activity, new Fragment());
        if (fragmentManager == null) {
            CoreLogger.logError("fragmentManager == null");
            return null;
        }

        final Fragment fragment = fragmentManager.findFragmentByTag(WorkerFragment.TAG);
        if (fragment == null) {
            CoreLogger.logError("fragment == null");
            return null;
        }

        final WorkerFragment workerFragment = (isWorkerFragment(Level.ERROR, fragment)) ? (WorkerFragment) fragment: null;
        if (workerFragment == null) {
            CoreLogger.logError("workerFragment == null");
            return null;
        }

        return workerFragment;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Set<BaseLoaderWrapper> getLoaders() {
        return mLoaders;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void cancelLoaders() {
        CoreLogger.logWarning("cancelled");

        hideProgress(true);

        destroyLoaders();

        if (!isGoBackOnLoadingCanceled()) return;

        CoreLogger.logWarning("isGoBackOnLoadingCanceled: about to call Activity.onBackPressed()");

        //noinspection Convert2Lambda
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getActivity();
                if (activity != null) activity.onBackPressed();
            }
        });
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void destroyLoaders() {
        CoreLogger.logWarning("destroyed");

        final Collection<Integer> loadersIds = new HashSet<>();

        for (final BaseLoaderWrapper loader: mLoaders)
            loadersIds.add(loader.getLoaderId());

        BaseLoader.destroyLoaders(getLoaderManager(), loadersIds);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void clearLoaders() {
        mLoaders.clear();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public BaseLoaderWrapper addLoader(final CacheAdapter adapter, @NonNull final LoaderBuilder builder) {
        return addLoader(adapter, null, builder);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public BaseLoaderWrapper addLoader(final CacheAdapter adapter, final LoaderRx rx, @NonNull final LoaderBuilder builder) {
        return addLoader(adapter, rx, builder.create());
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public BaseLoaderWrapper addLoader(final CacheAdapter adapter, @NonNull final BaseLoaderWrapper loader) {
        return addLoader(adapter, null, loader);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public BaseLoaderWrapper addLoader(final CacheAdapter adapter, final LoaderRx rx,
                                       @NonNull final BaseLoaderWrapper loader) {
        @SuppressWarnings("unchecked")
        BaseLoaderWrapper foundLoader = loader.findLoader(mLoaders), loaderToSet = loader;

        if (foundLoader == null)
            mLoaders.add(loader);
        else {
            if (!foundLoader.isLoading()) {
                mLoaders.remove(foundLoader);
                mLoaders.add(loader);

                foundLoader = null;
                CoreLogger.log("loader replaced (" + loader + ")");
            }
            else {
                loaderToSet = foundLoader;
                CoreLogger.logWarning("loader already exist and busy with data loading, new loader ignored (" + loader + ")");
            }
        }

        setAdapter(loaderToSet, adapter);
        setRx     (loaderToSet, rx     );

        return foundLoader;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void startLoading() {
        startLoading(false, false, false, false, false);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void startLoading(final boolean forceCache, final boolean noProgress,
                             final boolean merge, final boolean sync, final boolean noErrors) {
        final Collection<BaseLoaderWrapper> loaders = mLoaders;
        if (sync)
            //noinspection Convert2Lambda
            Utils.runInBackground(new Runnable() {
                @Override
                public void run() {
                    BaseLoaderWrapper.startSync(loaders, forceCache, noProgress, merge, noErrors);
                }
            });
        else
            for (final BaseLoaderWrapper loader: loaders)
                loader.start(forceCache, noProgress, merge, noErrors);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public BaseLoaderWrapper startLoading(final int loaderId, final boolean forceCache, final boolean noProgress,
                                          final boolean merge, final boolean sync, final boolean noErrors) {
        setGoBackOnLoadingCanceled(false);

        for (final BaseLoaderWrapper loader: mLoaders)
            if (loader.getLoaderId() == loaderId) {
                if (sync)
                    //noinspection Convert2Lambda
                    Utils.runInBackground(new Runnable() {
                        @Override
                        public void run() {
                            loader.startSync(forceCache, noProgress, merge);
                        }
                    });
                else
                    loader.start(forceCache, noProgress, merge, noErrors);

                return loader;
            }

        CoreLogger.logError("not found loader with id " + loaderId);
        return null;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static boolean isWorkerFragment(Level level, @NonNull final Fragment fragment) {
        final boolean result = fragment instanceof WorkerFragment;
        if (!result)
            CoreLogger.log(level, "fragment is instance of " + fragment.getClass().getName() +
                    "; should be WorkerFragment, looks like wrong usage of tag " + WorkerFragment.TAG);
        return result;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The <code>WorkerFragmentCallbacks</code> class is responsible for initialisation of {@link WorkerFragment}.
     */
    public static class WorkerFragmentCallbacks extends BaseActivityCallbacks {     // should be sync with SupportHelper (full)

        /**
         * Initialises a newly created {@code WorkerFragmentCallbacks} object.
         */
        public WorkerFragmentCallbacks() {
        }

        /**
         * Creates new instance of {@code WorkerFragment} object.
         *
         * @return  The newly created {@code WorkerFragment} object
         */
        @NonNull
        @SuppressWarnings("WeakerAccess")
        protected WorkerFragment createWorkerFragment() {
            return new WorkerFragment();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityCreated(@NonNull final Activity activity, final Bundle savedInstanceState) {
            
            final FragmentManager fragmentManager = SupportHelper.getFragmentManager(activity, new Fragment());
            if (fragmentManager == null) {
                CoreLogger.logError("fragmentManager == null");
                return;
            }
            
            final String   tag              = WorkerFragment.TAG;
            final Fragment workerFragment   = fragmentManager.findFragmentByTag(tag);
            
            if (savedInstanceState != null) {
                if (workerFragment != null)
                    CoreLogger.log("reuse existing WorkerFragment, tag " + tag);
                else
                    CoreLogger.logWarning("WorkerFragment not found, tag " + tag);
                return;
            }

            if (workerFragment == null) {
                CoreLogger.log("about to add WorkerFragment, tag " + tag);

                fragmentManager.beginTransaction().add(createWorkerFragment(), tag).commit();
                fragmentManager.executePendingTransactions();
            }
            else if (isWorkerFragment(Level.WARNING, workerFragment))
                CoreLogger.logWarning("strange: WorkerFragment is already in, tag " + tag);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private   final AtomicBoolean                           mGoBackOnLoadingCanceled    = new AtomicBoolean(true);

    /**
     * Please refer to the base method description.
     */
    @Override
    public CoreLoad setGoBackOnLoadingCanceled(final boolean isGoBackOnLoadingCanceled) {
        mGoBackOnLoadingCanceled.set(isGoBackOnLoadingCanceled);
        return this;
    }

    // normally loading starts 'cause of initialization of a new fragment
    // if loading starts after fragment initialized successfully, say, 'cause of swipe-to-refresh - return false
    private boolean isGoBackOnLoadingCanceled() {
        return mGoBackOnLoadingCanceled.get();
    }

    private   final Progress                                mProgress                   = new Progress();

    private   final AtomicInteger                           mLoadersCounterSave         = new AtomicInteger();
    private   final AtomicReference<String>                 mTextSave                   = new AtomicReference<>();

    /**
     * Please refer to the base method description.
     */
    @Override
    public void showProgress(final String text) {
        mProgress.show(text);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void hideProgress(final boolean force) {
        mProgress.hide(force);
    }

    private class Progress {

        private       BaseDialog                            mProgressDialog;

        private final AtomicInteger                         mLoadersCounter             = new AtomicInteger();

        private final Object                                mLock                       = new Object();

        private void show(final String text) {
            synchronized (mLock) {
                showAsync(text);
            }
        }

        private void hide(final boolean force) {
            synchronized (mLock) {
                hideAsync(force);
            }
        }

        private void showAsync(final String text) {
            final int counter = mLoadersCounter.incrementAndGet();
            CoreLogger.log("loaders counter " + counter);

            if (counter > 1 && mProgressDialog != null) return;

            if (mProgressDialog != null)
                CoreLogger.logError("mProgressDialog != null");

            mProgressDialog = mProgressProvider.get();

            if (!mProgressDialog.start(WorkerFragment.this.getActivity(), text, null)) {
                CoreLogger.logError("can not start progress dialog");
                mProgressDialog = null;
            }

            mTextSave.set(text);
        }

        private void hideAsync(final boolean force) {
            int counter = mLoadersCounter.get();
            CoreLogger.log("loaders counter " + counter + ", force " + force);

            if (force && counter > 1) mLoadersCounter.set(counter = 0);

            if (counter > 0) counter = mLoadersCounter.decrementAndGet();
            if (counter > 0) return;

            if (mProgressDialog == null) {
                CoreLogger.log(force ? Level.WARNING: Level.ERROR, "mProgressDialog == null");
                return;
            }

            if (!mProgressDialog.stop())
                CoreLogger.logError("can not stop progress dialog");

            mProgressDialog = null;
        }
    }
}
