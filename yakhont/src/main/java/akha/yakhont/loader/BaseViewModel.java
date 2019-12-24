/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
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

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.DataStore;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.PagingRecyclerViewAdapter;
import akha.yakhont.loader.BaseLiveData.CacheLiveData;
import akha.yakhont.loader.BaseLiveData.DataLoader;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoader;

import android.app.Activity;
import android.app.Service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProvider.Factory;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PageKeyedDataSource;
import androidx.paging.PagedList;
import androidx.paging.PagedList.BoundaryCallback;
import androidx.paging.PagedList.Config;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * {@link ViewModel} extender, adjusted to work with {@link BaseLiveData}.
 *
 * @param <D>
 *        The type of data
 *
 * @see BaseLiveData
 *
 * @author akha
 */
public class BaseViewModel<D> extends AndroidViewModel {

    /**
     * The {@link ViewModel} default key (use it only if you have exactly one {@link ViewModel}
     * in your {@link ViewModelStore}).
     * */
    public static  final String                 DEFAULT_KEY         = "";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final BaseLiveData<D>        mData;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final Observer    <D>        mObserver;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected            List<CoreLoad<?, ?>>   mCoreLoads;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final DataStore              mDataStore          = new DataStore();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final Runnable               mOnClearedCallback;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final boolean                mNoLifecycleOwner;

    /**
     * Initialises a newly created {@link BaseViewModel} object.
     *
     * @param data
     *        The {@link BaseLiveData}
     *
     * @param observer
     *        Please refer to {@link LiveData#observe}
     *
     * @param onClearedCallback
     *        The callback to call from the {@link #onCleared} method
     *
     * @param noLifecycleOwner
     *        {@code true} if given {@code BaseViewModel} has no associated {@link LifecycleOwner}, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    protected BaseViewModel(@NonNull final BaseLiveData<D>     data,
                            @NonNull final Observer    <D>     observer,
                                     final Runnable            onClearedCallback,
                                     final boolean             noLifecycleOwner) {
        super(Utils.getApplication());

        mData                   = data;
        mObserver               = observer;
        mOnClearedCallback      = onClearedCallback;
        mNoLifecycleOwner       = noLifecycleOwner;
    }

    /**
     * Checks whether the {@code BaseViewModel} has associated {@link LifecycleOwner} or not.
     *
     * @return  {@code true} if given {@code BaseViewModel} has no associated {@link LifecycleOwner}, {@code false} otherwise
     */
    public boolean isNoLifecycleOwner() {
        return mNoLifecycleOwner;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static ViewModelStoreOwner cast(Activity activity, final Level level) {
        if (activity == null) {
            activity = Utils.getCurrentActivity();
            CoreLogger.log(level != null ? level: Level.WARNING, "Activity == null, " +
                    "the current one will be used: " + CoreLogger.getDescription(activity));
        }
        if (activity instanceof ViewModelStoreOwner) return (ViewModelStoreOwner) activity;

        CoreLogger.log(level != null ? level: Level.ERROR, "Activity should be instance of " +
                "ViewModelStoreOwner, e.g. FragmentActivity; the given Activity is " +
                CoreLogger.getDescription(activity));
        return null;
    }

    /**
     * Returns the {@link BaseViewModel} for the current {@link Activity} and key.
     *
     * @param key
     *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
     *        for more info); could be null (for default value)
     *
     * @param <S>
     *        The type of {@link BaseViewModel}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link BaseViewModel}
     */
    public static <S extends BaseViewModel<D>, D> S get(final String key) {
        return get(cast(Utils.getCurrentActivity(), null), key);
    }

    /**
     * Returns the {@link BaseViewModel} for the given {@link ViewModelStoreOwner} and key.
     *
     * @param viewModelStoreOwner
     *        The {@link ViewModelStoreOwner}
     *
     * @param key
     *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
     *        for more info); could be null (for default value)
     *
     * @param <S>
     *        The type of {@link BaseViewModel}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link BaseViewModel}
     */
    @SuppressWarnings("WeakerAccess")
    public static <S extends BaseViewModel<D>, D> S get(final ViewModelStoreOwner viewModelStoreOwner,
                                                        final String              key) {
        final S result = viewModelStoreOwner == null ? null: get(getViewModelStore(viewModelStoreOwner), key);
        if (result == null) CoreLogger.logError("can't find ViewModel for ViewModelStoreOwner " +
                CoreLogger.getDescription(viewModelStoreOwner));
        return result;
    }

    /**
     * Returns the {@link BaseViewModel} for the given {@link Fragment} and key.
     *
     * @param fragment
     *        The {@link Fragment}
     *
     * @param key
     *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
     *        for more info); could be null (for default value)
     *
     * @param <S>
     *        The type of {@link BaseViewModel}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link BaseViewModel}
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static <S extends BaseViewModel<D>, D> S get(final Fragment fragment, final String key) {
        if (fragment == null) {
            CoreLogger.logError("fragment == null");
            return null;
        }

        final S result = get(getViewModelStore(fragment), key);
        if (result == null)
            CoreLogger.logError("can't find ViewModel for Fragment " + CoreLogger.getDescription(fragment));

        return result;
    }

    /**
     * Returns the {@link BaseViewModel} for the given {@link ViewModelStore} and key.
     *
     * @param store
     *        The {@link ViewModelStore}
     *
     * @param key
     *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
     *        for more info); could be null (for default value)
     *
     * @param <S>
     *        The type of {@link BaseViewModel}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link BaseViewModel}
     */
    @SuppressWarnings("WeakerAccess")
    public static <S extends BaseViewModel<D>, D> S get(final ViewModelStore store, final String key) {
        final WeakReference<S> weak = getWeak(store, key);
        if (weak != null) {
            final S result = weak.get();
            if (result == null)
                CoreLogger.logWarning("null WeakReference.get() for key " + key + ", ViewModelStore " +
                        CoreLogger.getDescription(store));
            return result;
        }
        return null;
    }

    /**
     * Returns the weak reference to {@link BaseViewModel} for the given {@link ViewModelStore} and key.
     *
     * @param store
     *        The {@link ViewModelStore}
     *
     * @param key
     *        The {@link BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)}
     *        for more info); could be null (for default value)
     *
     * @param <S>
     *        The type of {@link BaseViewModel}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link BaseViewModel}
     *
     * @see     WeakReference
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public static <S extends BaseViewModel<D>, D> WeakReference<S> getWeak(
            final ViewModelStore store, String key) {
        if (key == null) {
            CoreLogger.logWarning("BaseViewModel.getWeak(): key == null, default one will be used");
            key = DEFAULT_KEY;
        }
        final boolean[] empty = new boolean[] {true};
        getWeak(store, empty);

        if (!empty[0]) {
            final Map<String, WeakReference<? extends BaseViewModel<?>>> map =
                    BaseViewModelProvider.getHelper(store);
            if (key.length() == 0) {
                final Set<String> keys = map.keySet();
                if (keys.size() == 1)
                    key = keys.iterator().next();
                else {
                    CoreLogger.logError("BaseViewModel.getWeak(): default key allows only if " +
                            "BaseViewModel is one and only");
                    return null;
                }
            }
            final WeakReference<S> ref = (WeakReference<S>) map.get(key);
            if (ref != null) return ref;

            CoreLogger.logWarning("null WeakReference for key " + key + ", ViewModelStore " +
                    CoreLogger.getDescription(store));
        }
        CoreLogger.logWarning("can't find ViewModel for key " + key + ", ViewModelStore " +
                CoreLogger.getDescription(store));
        return null;
    }

    /**
     * Returns the {@link BaseViewModel}'s weak references collection kept in this {@link ViewModel}.
     *
     * @param store
     *        The {@link ViewModelStore}
     *
     * @return  The {@link BaseViewModel}
     *
     * @see     WeakReference
     */
    @SuppressWarnings("unused")
    public static Collection<WeakReference<? extends BaseViewModel<?>>> getWeak(final ViewModelStore store) {
        return getWeak(store, (boolean[]) null);
    }

    private static Collection<WeakReference<? extends BaseViewModel<?>>> getWeak(
            final ViewModelStore store, @Size(min = 1, max = 1) final boolean[] empty) {
        if (store == null) {
            CoreLogger.logError("ViewModelStore == null");
            return null;
        }
        final Map<String, WeakReference<? extends BaseViewModel<?>>> map =
                BaseViewModelProvider.sMap.get(store);
        if (map != null) {
            if (empty == null) {
                final Collection<WeakReference<? extends BaseViewModel<?>>> models = map.values();
                if (models.size() != 0) return models;
            }
            else
                empty[0] = map.size() == 0;

            if (map.size() == 0)
                CoreLogger.logWarning("empty collection of ViewModels for ViewModelStore " +
                        CoreLogger.getDescription(store));
        }
        else
            CoreLogger.logWarning("no ViewModels found for ViewModelStore " +
                    CoreLogger.getDescription(store));
        return null;
    }

    /**
     * Returns the CoreLoads collection kept in this {@link ViewModel}
     * (mostly for screen orientation changes handling).
     *
     * @return  The CoreLoads collection
     */
    public List<CoreLoad<?, ?>> getCoreLoads() {
        if (mCoreLoads == null)
            CoreLogger.logWarning("null collection of CoreLoads");
        if (mCoreLoads != null && mCoreLoads.size() == 0)
            CoreLogger.logWarning("empty collection of CoreLoads");
        return mCoreLoads;
    }

    /**
     * Returns the {@link CoreLoad} kept in this {@link ViewModel}
     * (mostly for screen orientation changes handling).
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data to load
     *
     * @return  The {@link CoreLoad}
     */
    @SuppressWarnings({"unchecked", "TypeParameterHidesVisibleType", "unused"})
    public <E, D> CoreLoad<E, D> getCoreLoad() {
        final List<CoreLoad<?, ?>> list = getCoreLoads();
        if (list != null && list.size() > 1)
            CoreLogger.logWarning("expected 1 loader, but actual loaders qty is " + list.size());
        return list == null || list.size() == 0 ? null: (CoreLoad<E, D>) list.get(0);
    }

    /**
     * Sets the CoreLoads collection to keep in this {@link ViewModel}
     * (mostly for screen orientation changes handling).
     *
     * @param coreLoads
     *        The CoreLoads collection
     */
    @SuppressWarnings("WeakerAccess")
    public void setCoreLoads(final List<CoreLoad<?, ?>> coreLoads) {
        if (coreLoads != null && coreLoads.size() == 0)
            CoreLogger.logWarning("about to save zero-size list of CoreLoads");
        mCoreLoads = coreLoads;
    }

    /**
     * Sets the CoreLoads to keep in this {@link ViewModel}
     * (mostly for screen orientation changes handling).
     *
     * @param coreLoads
     *        The CoreLoads
     */
    public void setCoreLoads(final CoreLoad<?, ?>... coreLoads) {
        setCoreLoads(coreLoads == null ? null: Arrays.asList(coreLoads));
    }

    /**
     * Sets some data to keep in this {@link ViewModel}.
     *
     * @param key
     *        The key
     *
     * @param value
     *        The data
     *
     * @param <V>
     *        The type of data
     *
     * @return  The previous data for the given key (or null)
     *
     * @see #getData(String)
     */
    @SuppressWarnings("unused")
    public <V> V setData(final String key, final V value) {
        return mDataStore.setData(key, value);
    }

    /**
     * Returns the data (associated with the given key) kept in this {@link ViewModel}.
     *
     * @param key
     *        The key
     *
     * @param <V>
     *        The type of data
     *
     * @return  The data for the given key (or null)
     *
     * @see #setData
     */
    @SuppressWarnings({"unchecked", "unused"})
    public <V> V getData(final String key) {
        return (V) mDataStore.getData(key);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void onCleared() {
        if (mData instanceof CacheLiveData) ((CacheLiveData) mData).closeCursor();
        if (mOnClearedCallback != null) Utils.safeRun(mOnClearedCallback);
        super.onCleared();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static <D> Observer<D> updateUi(         final boolean        stop,
                                                    final LifecycleOwner lifecycleOwner,
                                           @NonNull final LiveData<D>    liveData,
                                           @NonNull final Observer<D>    observer) {
        if (lifecycleOwner == null) {
            Utils.postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    if (stop)
                        liveData.removeObserver(observer);
                    else
                        liveData.observeForever(observer);
                }

                @NonNull
                @Override
                public String toString() {
                    return "LiveData." + (stop ? "removeObserver": "observeForever");
                }
            });
            return observer;
        }
        else if (!stop) Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                liveData.observe(lifecycleOwner, observer);
            }

            @NonNull
            @Override
            public String toString() {
                return "LiveData.observe";
            }
        });

        return null;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public Observer<D> updateUi(final boolean stop, final LifecycleOwner owner, final Runnable callback) {
        final Observer<D> result = updateUi(stop, owner, mData, mObserver);
        Utils.safeRun(callback);
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "UnusedReturnValue"})
    public Observer<D> updateUi(final boolean stop, final LifecycleOwner owner) {
        return updateUi(stop, owner, null);
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void updateUiFragmentForWeaver(final boolean stop, @NonNull final Fragment fragment) {
        final Collection<BaseViewModel<?>> models = new ArrayList<>();

        BaseViewModelProvider.getViewModels(getViewModelStore(fragment), null,
                models, CoreLogger.getDefaultLevel());

        for (final BaseViewModel<?> model: models)
            if (!model.isNoLifecycleOwner()) model.updateUi(stop, fragment);
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void updateUiActivityForWeaver(final boolean stop, @NonNull final Activity activity) {
        final Level level = CoreLogger.getDefaultLevel();
        if (!(activity instanceof ViewModelStoreOwner)) {
            CoreLogger.log(level, "not ViewModelStoreOwner Activity: " + CoreLogger.getDescription(activity));
            return;
        }
        final Collection<BaseViewModel<?>> models = getViewModels(cast(activity, level),
                false, level);

        final LifecycleOwner lifecycleOwner = getLifecycleOwner(activity, CoreLogger.getDefaultLevel());

        for (final BaseViewModel<?> model: models)
            if (stop) {
                if (model.isNoLifecycleOwner())
                    modelUpdateUi(model);
                else
                    modelUpdateUi(model, lifecycleOwner, true, activity);
            }
            else {
                if (model.isNoLifecycleOwner()) {
                    if (lifecycleOwner != null)     // should never happen
                        CoreLogger.logError("unexpected LifecycleOwner Activity: " + CoreLogger.getDescription(activity));
                    model.updateUi(false, null);
                }
                else
                    modelUpdateUi(model, lifecycleOwner, false, activity);
            }
    }

    private static void modelUpdateUi(final BaseViewModel<?> model, final LifecycleOwner lifecycleOwner,
                                      final boolean          stop , final Activity       activity) {
        if (lifecycleOwner == null)                 // should never happen
            CoreLogger.logError("not LifecycleOwner Activity: " + CoreLogger.getDescription(activity));
        model.updateUi(stop, lifecycleOwner);
    }

    private static void modelUpdateUi(final BaseViewModel<?> model) {
        model.onCleared();
        model.updateUi(true, null);
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void updateUiServiceForWeaver(final int timeout, final Service service) {
        final ViewModelStoreOwner viewModelStoreOwner = service instanceof ViewModelStoreOwner ?
                (ViewModelStoreOwner) service: CoreLoader.INSTANCE;

        final Level level = CoreLogger.getDefaultLevel();
        final Collection<BaseViewModel<?>> models = getViewModels(viewModelStoreOwner,
                false, level);

        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (final BaseViewModel<?> model: models)
                    if (model.isNoLifecycleOwner()) modelUpdateUi(model);
            }

            @NonNull
            @Override
            public String toString() {
                return "updateUiServiceForWeaver";
            }
        };

        if (timeout <= 0)
            Utils.postToMainLoop(runnable);
        else
            Utils.postToMainLoop(timeout, runnable);
    }

    private static LifecycleOwner getLifecycleOwner(final Activity activity, final Level level) {
        if (activity instanceof LifecycleOwner) return (LifecycleOwner) activity;

        CoreLogger.log(level, "can't get LifecycleOwner for Activity: " + CoreLogger.getDescription(activity));
        return null;
    }

    private static LifecycleOwner getLifecycleOwner(final Activity activity) {
        return getLifecycleOwner(activity, Level.ERROR);
    }

    /**
     * Gets ViewModelStore.
     *
     * @param fragment
     *        The Fragment
     *
     * @return  The ViewModelStore
     */
    public static ViewModelStore getViewModelStore(@NonNull final Fragment fragment) {
        return fragment.getViewModelStore();
    }

    /**
     * Gets ViewModelStore.
     *
     * @param viewModelStoreOwner
     *        The {@code ViewModelStoreOwner}
     *
     * @return  The ViewModelStore
     *
     * @throws  BaseViewModelException
     *          please refer to the exception description
     */
    public static ViewModelStore getViewModelStore(final ViewModelStoreOwner viewModelStoreOwner) {
        final ViewModelStore result;
        if (viewModelStoreOwner == null) {
            CoreLogger.logError("getViewModelStore: viewModelStoreOwner == null");
            result = null;
        }
        else
            result = viewModelStoreOwner.getViewModelStore();

        if (result != null) return result;

        //noinspection ConstantConditions
        throw new BaseViewModelException("getViewModelStore() failed, ViewModelStoreOwner: " +
                CoreLogger.getDescription(viewModelStoreOwner));
    }

    /**
     * Will be thrown if given {@link ViewModelStoreOwner} (e.g. {@link FragmentActivity})
     * can't provide {@link ViewModelStore} instance.
     */
    @SuppressWarnings("WeakerAccess")
    public static class BaseViewModelException extends RuntimeException {

        /**
         * Please refer to the {@link RuntimeException#RuntimeException(String)}.
         */
        @SuppressWarnings("WeakerAccess")
        public BaseViewModelException(final String message) {
            super(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends ViewModel> Class<T> castBaseViewModelClass(final boolean paging) {
        return (Class<T>) (paging ? PagingViewModel.class: BaseViewModel.class);
    }

    /**
     * Gets the data loading status.
     *
     * @param activity
     *        The Activity
     *
     * @return  {@code true} if data loading is in progress, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean isLoading(@NonNull final Activity activity) {
        return isLoading(cast(activity, null), null, true);
    }

    /**
     * Gets the data loading status.
     *
     * @param activity
     *        The Activity
     *
     * @param key
     *        The key (if any), for more info please refer to {@link ViewModelProvider#get(String, Class)}
     *
     * @return  {@code true} if data loading is in progress, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean isLoading(@NonNull final Activity activity, final String key) {
        return isLoading(cast(activity, null), key, true);
    }

    /**
     * Gets the data loading status.
     *
     * @param fragment
     *        The Fragment
     *
     * @return  {@code true} if data loading is in progress, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean isLoading(@NonNull final Fragment fragment) {
        return isLoading(fragment, null);
    }

    /**
     * Gets the data loading status.
     *
     * @param fragment
     *        The Fragment
     *
     * @param key
     *        The key (if any), for more info please refer to {@link ViewModelProvider#get(String, Class)}
     *
     * @return  {@code true} if data loading is in progress, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean isLoading(@NonNull final Fragment fragment, final String key) {
        return BaseViewModelProvider.isLoading(getViewModelStore(fragment), key, Level.ERROR);
    }

    /**
     * Gets the data loading status.
     *
     * @param viewModelStoreOwner
     *        The ViewModelStoreOwner
     *
     * @param key
     *        The key (if any), for more info please refer to {@link ViewModelProvider#get(String, Class)}
     *
     * @param includeFragments
     *        {@code true} to include fragments data loading status, {@code false} otherwise
     *
     * @return  {@code true} if data loading is in progress, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean isLoading(final ViewModelStoreOwner viewModelStoreOwner,
                                    final String key, final boolean includeFragments) {
        if (viewModelStoreOwner == null) {
            CoreLogger.logError("isLoading: ViewModelStoreOwner == null");
            return false;
        }
        final Level level = Level.ERROR;
        final boolean result = BaseViewModelProvider.isLoading(
                getViewModelStore(viewModelStoreOwner), key, level);
        return result || !includeFragments ? result: isLoadingFragment(viewModelStoreOwner, level);
    }

    private static boolean isLoadingFragment(@NonNull final ViewModelStoreOwner viewModelStoreOwner,
                                             @NonNull final Level level) {
        if (!(viewModelStoreOwner instanceof FragmentActivity)) {
            CoreLogger.logError("unexpected ViewModelStoreOwner (should be FragmentActivity): "
                    + CoreLogger.getDescription(viewModelStoreOwner));
            return false;
        }
        for (final Fragment fragment: ((FragmentActivity) viewModelStoreOwner)
                .getSupportFragmentManager().getFragments())
            if (BaseViewModelProvider.isLoading(getViewModelStore(fragment), null, level))
                return true;
        return false;
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean isLoadingForWeaver(@NonNull final Activity activity) {
        if (!(activity instanceof ViewModelStoreOwner)) return false;

        final Level level = CoreLogger.getDefaultLevel();
        final Collection<BaseViewModel<?>> models = getViewModels(
                cast(activity, level), true, level);

        for (final BaseViewModel<?> model: models) {
            final BaseLiveData<?> data = model.getData();
            if (data.isLoading() && data.confirm(activity, null)) return true;
        }
        return false;
    }

    /**
     * Returns collection of {@code BaseViewModel} associated with the given ViewModelStoreOwner.
     *
     * @param viewModelStoreOwner
     *        The ViewModelStoreOwner
     *
     * @param includeFragments
     *        {@code true} to include fragments data loading status, {@code false} otherwise
     *
     * @return  The {@code BaseViewModel} collection
     */
    @SuppressWarnings("unused")
    public static Collection<BaseViewModel<?>> getViewModels(
            final ViewModelStoreOwner viewModelStoreOwner, final boolean includeFragments) {
        return getViewModels(viewModelStoreOwner, includeFragments, Level.ERROR);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Collection<BaseViewModel<?>> getViewModels(
            final ViewModelStoreOwner viewModelStoreOwner, final boolean includeFragments, @NonNull final Level level) {

        final Collection<BaseViewModel<?>> list = new ArrayList<>();
        BaseViewModelProvider.getViewModels(getViewModelStore(viewModelStoreOwner), null, list, level);

        if (includeFragments)
            if (viewModelStoreOwner instanceof FragmentActivity)
                for (final Fragment fragment: ((FragmentActivity) viewModelStoreOwner)
                        .getSupportFragmentManager().getFragments())
                    BaseViewModelProvider.getViewModels(getViewModelStore(fragment), null,
                            list, level);
            else
                CoreLogger.log("unexpected ViewModelStoreOwner (should be FragmentActivity): "
                        + CoreLogger.getDescription(viewModelStoreOwner));
        return list;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getViewModels(
            @NonNull final ViewModelStoreOwner viewModelStoreOwner) {
        return BaseViewModelProvider.getEntries(viewModelStoreOwner);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getViewModels(
            @NonNull final Fragment fragment) {
        return BaseViewModelProvider.getEntries(fragment);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getViewModels(
            @NonNull final ViewModelStore store) {
        return BaseViewModelProvider.getEntries(store);
    }

    /**
     * Returns loaded data (if any).
     *
     * @param <S>
     *        The {@code BaseLiveData} customization
     *
     * @return  The {@code BaseLiveData}
     */
    @SuppressWarnings("unchecked")
    public <S extends BaseLiveData<D>> S getData() {
        return (S) mData;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Implementation of {@code Factory} interface which responsible to instantiate BaseViewModels.
     *
     * @param <D>
     *        The type of data
     */
    @SuppressWarnings("WeakerAccess")
    public static class BaseViewModelFactory<D> implements Factory {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  WeakReference<ViewModelStore>
                                                mViewModelStore;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  String                 mKey;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  BaseLiveData<D>        mData;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  Observer    <D>        mObserver;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  Runnable               mOnClearedCallback;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  boolean                mNoLifecycleOwner;

        /**
         * Initialises a newly created {@link BaseViewModelFactory} object.
         *
         * @param viewModelStore
         *        The {@link ViewModelStore}
         *
         * @param data
         *        The {@link BaseLiveData}
         *
         * @param observer
         *        Please refer to {@link LiveData#observe}
         *
         * @param key
         *        The key, for more info please refer to {@link ViewModelProvider#get(String, Class)}
         *
         * @param onClearedCallback
         *        The callback to call from the {@link #onCleared} method
         *
         * @param noLifecycleOwner
         *        {@code true} if given {@code BaseViewModel} has no associated {@link LifecycleOwner}, {@code false} otherwise
         */
        public BaseViewModelFactory(         final ViewModelStore     viewModelStore,
                                    @NonNull final BaseLiveData<D>    data,
                                    @NonNull final Observer    <D>    observer,
                                             final String             key,
                                             final Runnable           onClearedCallback,
                                             final boolean            noLifecycleOwner) {

            mViewModelStore    = viewModelStore == null ? null: new WeakReference<>(viewModelStore);
            mKey               = key;
            mData              = data;
            mObserver          = observer;
            mOnClearedCallback = onClearedCallback;
            mNoLifecycleOwner  = noLifecycleOwner;
        }

        /**
         * Creates new instance of {@link BaseViewModel}.
         *
         * @return  The {@link BaseViewModel}
         */
        @SuppressWarnings("WeakerAccess")
        protected BaseViewModel<D> createViewModel() {
            return new BaseViewModel<>(mData, mObserver, mOnClearedCallback, mNoLifecycleOwner);
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> notUsed) {
            if (mKey == null) {
                CoreLogger.logError("BaseViewModelFactory: key == null");
                throw new BaseViewModelFactoryException("key == null");
            }
            final BaseViewModel<D> baseViewModel = createViewModel();

            if (mViewModelStore != null)
                BaseViewModelProvider.put(mViewModelStore, mKey, new WeakReference<>(baseViewModel));

            @SuppressWarnings("unchecked")
            final T result = (T) baseViewModel;
            return result;
        }

        /**
         * Will be thrown if key is null for given {@link ViewModelStore}.
         */
        public static class BaseViewModelFactoryException extends RuntimeException {

            /**
             * Please refer to the {@link RuntimeException#RuntimeException(String)}.
             */
            public BaseViewModelFactoryException(final String message) {
                super(message);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class BaseViewModelProvider<D> extends ViewModelProvider {

        // this is 'cause of ViewModelStore limitation (no official way to get list of keys)
        private final static
                         Map<ViewModelStore, Map<String, WeakReference<? extends BaseViewModel<?>>>>
                                                sMap            = Utils.newWeakMap();

        @SuppressWarnings("unused")
        private BaseViewModelProvider(@NonNull final ViewModelStore     store,
                                      @NonNull final BaseLiveData<D>    data,
                                      @NonNull final Observer    <D>    observer,
                                               final String             key,
                                               final Runnable           onClearedCallback,
                                               final boolean            noLifecycleOwner) {

            this(store, new BaseViewModelFactory<>(store, data, observer, key, onClearedCallback, noLifecycleOwner));
        }

        private BaseViewModelProvider(@NonNull final ViewModelStore             store,
                                      @NonNull final BaseViewModelFactory<D>    factory) {
            super(store, factory);
        }

        private static <D> void put(@NonNull final WeakReference<ViewModelStore>             store,
                                    @NonNull final String                                    key,
                                    @NonNull final WeakReference<? extends BaseViewModel<D>> model) {
            final ViewModelStore viewModelStore = store.get();
            if (viewModelStore == null) {   // should never happen
                CoreLogger.logError("viewModelStore == null");
                return;
            }

            Map<String, WeakReference<? extends BaseViewModel<?>>> models = sMap.get(viewModelStore);
            if (models == null) {
                models = Utils.newWeakMap();
                sMap.put(viewModelStore, models);
            }

            CoreLogger.log("about to put in models key: " + key + ", model: " + model);
            final WeakReference<? extends BaseViewModel<?>> prev = models.put(key, model);

            if (prev != null)
                CoreLogger.logError("models already contained key: " + key + ", prev model: " + prev);
        }

        private static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getEntries(
                @NonNull final ViewModelStore store) {
            final Map<String, WeakReference<? extends BaseViewModel<?>>> models = sMap.get(store);

            final Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> result =
                    models == null ? null: models.entrySet();

            CoreLogger.log("entries for ViewModelStore " + store + " - " + result);
            return result;
        }

        private static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getEntries(
                @NonNull final ViewModelStoreOwner viewModelStoreOwner) {
            final Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> result =
                    getEntries(getViewModelStore(viewModelStoreOwner));

            CoreLogger.log("entries for ViewModelStoreOwner " + viewModelStoreOwner + " - " + result);
            return result == null ? Collections.emptySet(): result;
        }

        private static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getEntries(
                @NonNull final Fragment fragment) {
            final Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> result =
                    getEntries(getViewModelStore(fragment));

            CoreLogger.log("entries for fragment " + fragment + " - " + result);
            return result == null ? Collections.emptySet(): result;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private static boolean checkModels(@NonNull final Level level,
                final Map<String, WeakReference<? extends BaseViewModel<?>>> models) {
            boolean result = false;
            if (models == null)       // happens at app starting (called via Weaver)
                CoreLogger.log(level, "models == null");
            else if (models.isEmpty())
                CoreLogger.log(level, "models is empty");
            else
                result = true;
            return result;
        }

        private static BaseViewModel<?> getModel(final WeakReference<? extends BaseViewModel<?>> model) {
            BaseViewModel<?> result = null;
            if (model == null)
                CoreLogger.logError("model == null");
            else {
                result = model.get();
                if (result == null)
                    CoreLogger.logError("viewModel == null");
            }
            return result;
        }

        private static boolean isLoading(@NonNull final ViewModelStore store, final String key,
                                         @NonNull final Level level) {
            final Map<String, WeakReference<? extends BaseViewModel<?>>> models = getHelper(store);
            if (!checkModels(level, models)) return false;

            if (key != null) return isLoading(models.get(key));

            for (final WeakReference<? extends BaseViewModel<?>> model: models.values())
                if (isLoading(model)) return true;

            CoreLogger.log("final no key loading: false");
            return false;
        }

        private static boolean isLoading(final WeakReference<? extends BaseViewModel<?>> baseViewModel) {
            final BaseViewModel<?> model = getModel(baseViewModel);
            if (model == null) return false;

            final BaseLiveData<?> data = model.getData();
            final boolean loading = data.isLoading();

            CoreLogger.log("loading: " + loading + ", BaseLiveData: " + data);
            return loading;
        }

        private static void getViewModels(@NonNull final ViewModelStore store,
                                          @SuppressWarnings("SameParameterValue") final String key,
                                          @NonNull final Collection<BaseViewModel<?>> baseViewModels,
                                          @NonNull final Level level) {
            final Map<String, WeakReference<? extends BaseViewModel<?>>> models = getHelper(store);
            if (!checkModels(level, models)) return;

            if (key != null)
                getViewModel(models.get(key), baseViewModels);
            else
                for (final WeakReference<? extends BaseViewModel<?>> model: models.values())
                    getViewModel(model, baseViewModels);
        }

        private static Map<String, WeakReference<? extends BaseViewModel<?>>> getHelper(
                @NonNull final ViewModelStore store) {
            return sMap.get(store);
        }

        private static void getViewModel(final WeakReference<? extends BaseViewModel<?>> baseViewModel,
                                         @NonNull final Collection<BaseViewModel<?>> baseViewModels) {
            final BaseViewModel<?> model = getModel(baseViewModel);
            if (model == null) return;

            try {
                final boolean result = baseViewModels.add(model);
                CoreLogger.log("baseViewModels.add(viewModel): " + result);
            }
            catch (Exception exception) {
                CoreLogger.log(exception);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * {@link BaseViewModel} extender, adjusted to work with Google paging library.
     *
     * @param <Key>
     *        The {@link DataSource}'s key
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
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
    public static class PagingViewModel<Key, T, R, E, D> extends BaseViewModel<D> {

        /** The default page size (value is {@value}). */
        @SuppressWarnings("WeakerAccess")
        public static final int                                             DEFAULT_PAGE_SIZE   = 20;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  LiveData<PagedList<                         T>>    mDataPaged;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  Observer          <PagedList<               T>>    mObserverPaged;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  PagingRecyclerViewAdapter<T, R, E>                 mAdapter;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  DataSourceFactory<? extends DataSource<Key, T>>    mDataSourceFactory;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected        Runnable                                           mSwipeToRefresh;

        /**
         * Initialises a newly created {@code PagingViewModel} object.
         *
         * @param data
         *        The {@link BaseLiveData}
         *
         * @param observer
         *        Please refer to {@link LiveData#observe}
         *
         * @param dataSourceProducer
         *        The {@link DataSource} producer
         *
         * @param config
         *        The {@link Config} for {@link PagedList}
         *
         * @param adapter
         *        The {@link PagingRecyclerViewAdapter}
         *
         * @param boundaryCallback
         *        The {@link BoundaryCallback} (please refer to {@link LivePagedListBuilder} for details)
         *
         * @param fetchExecutor
         *        The fetch {@link Executor} (please refer to {@link LivePagedListBuilder} for details)
         *
         * @param initialLoadKey
         *        The initial load key (please refer to {@link LivePagedListBuilder} for details)
         *
         * @param onClearedCallback
         *        The callback to call from the {@link #onCleared} method
         *
         * @param noLifecycleOwner
         *        {@code true} if given {@code PagingViewModel} has no associated {@link LifecycleOwner}, {@code false} otherwise
         */
        @SuppressWarnings("WeakerAccess")
        protected PagingViewModel(
                @NonNull final BaseLiveData                      <D>    data,
                @NonNull final Observer                          <D>    observer,
                @NonNull final Callable<? extends DataSource<Key, T>>   dataSourceProducer,
                @NonNull final Config                                   config,
                @NonNull final PagingRecyclerViewAdapter<T, R, E>       adapter,
                         final BoundaryCallback         <T      >       boundaryCallback,
                         final Executor                                 fetchExecutor,
                         final Key                                      initialLoadKey,
                         final Runnable                                 onClearedCallback,
                         final boolean                                  noLifecycleOwner) {

            super(data, observer, onClearedCallback, noLifecycleOwner);

            mAdapter              = adapter;
            mDataSourceFactory    = new DataSourceFactory<>(dataSourceProducer);

            final LivePagedListBuilder<Key, T> builder =
                    new LivePagedListBuilder<>(mDataSourceFactory, config);

            if (boundaryCallback != null) builder.setBoundaryCallback(boundaryCallback);
            if (fetchExecutor    != null) builder.setFetchExecutor   (fetchExecutor   );
            if (initialLoadKey   != null) builder.setInitialLoadKey  (initialLoadKey  );

            mDataPaged            = builder.build();

            //noinspection Convert2Lambda
            mObserverPaged        = new Observer<PagedList<T>>() {
                @Override
                public void onChanged(@Nullable PagedList<T> data) {
                    mAdapter.submitList(data);

                    if (mSwipeToRefresh != null) Utils.safeRun(mSwipeToRefresh);
                }
            };
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public void setOnSwipeToRefresh(final Runnable callback) {
            mSwipeToRefresh = callback;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @Override
        public Observer<D> updateUi(final boolean stop, final LifecycleOwner owner) {
            return super.updateUi(stop, owner, new Runnable() {
                @Override
                public void run() {
                    updateUi(stop, owner, mDataPaged, mObserverPaged);
                }

                @NonNull
                @Override
                public String toString() {
                    return "PagingViewModel.updateUi()";
                }
            });
        }

        /**
         * Invalidates the current {@link DataSource} (if any).
         *
         * @return  {@code true} if the {@link DataSource} was invalidated, {@code false} otherwise
         */
        public boolean invalidateDataSource() {
            return mDataSourceFactory.invalidate();
        }

        private static class ViewModelFactory<Key, T, R, E, D> extends BaseViewModelFactory<D> {

            private final   Callable<? extends DataSource<Key,  T>>     mDataSourceProducer;
            private final   Config                                      mConfig;
            private final   PagingRecyclerViewAdapter<T, R, E>          mAdapter;

            private final   BoundaryCallback         <T      >          mBoundaryCallback;
            private final   Executor                                    mFetchExecutor;
            private final   Key                                         mInitialLoadKey;

            private final   Runnable                                    mOnClearedCallback;
            private final   boolean                                     mNoLifecycleOwner;

            private ViewModelFactory(
                    @NonNull final ViewModelStore                           store,
                    @NonNull final BaseLiveData                      <D>    data,
                    @NonNull final Observer                          <D>    observer,
                             final String                                   key,
                    @NonNull final Callable<? extends DataSource<Key, T>>   dataSourceProducer,
                    @NonNull final Config                                   config,
                    @NonNull final PagingRecyclerViewAdapter<T, R, E>       adapter,
                             final BoundaryCallback         <T      >       boundaryCallback,
                             final Executor                                 fetchExecutor,
                             final Key                                      initialLoadKey,
                             final Runnable                                 onClearedCallback,
                             final boolean                                  noLifecycleOwner) {

                super(store, data, observer, key, onClearedCallback, noLifecycleOwner);

                mDataSourceProducer     = dataSourceProducer;
                mConfig                 = config;
                mAdapter                = adapter;

                mBoundaryCallback       = boundaryCallback;
                mFetchExecutor          = fetchExecutor;
                mInitialLoadKey         = initialLoadKey;

                mOnClearedCallback      = onClearedCallback;
                mNoLifecycleOwner       = noLifecycleOwner;
            }

            @Override
            protected BaseViewModel<D> createViewModel() {
                return new PagingViewModel<>(mData, mObserver, mDataSourceProducer, mConfig, mAdapter,
                        mBoundaryCallback, mFetchExecutor, mInitialLoadKey, mOnClearedCallback, mNoLifecycleOwner);
            }
        }

        private static class ViewModelProvider<Key, T, R, E, D> extends BaseViewModelProvider<D> {

            private ViewModelProvider(
                    @NonNull final ViewModelStore                           store,
                    @NonNull final BaseLiveData                      <D>    data,
                    @NonNull final Observer                          <D>    observer,
                             final String                                   key,
                    @NonNull final Callable<? extends DataSource<Key, T>>   dataSourceProducer,
                    @NonNull final Config                                   config,
                    @NonNull final PagingRecyclerViewAdapter<T, R, E>       adapter,
                             final BoundaryCallback         <T      >       boundaryCallback,
                             final Executor                                 fetchExecutor,
                             final Key                                      initialLoadKey,
                             final Runnable                                 onClearedCallback,
                             final boolean                                  noLifecycleOwner) {
                super(store, new ViewModelFactory<>(store, data, observer, key, dataSourceProducer,
                        config, adapter, boundaryCallback, fetchExecutor, initialLoadKey,
                        onClearedCallback, noLifecycleOwner));
            }
        }

        private class DataSourceFactory<S extends DataSource<Key, T>> extends DataSource.Factory<Key, T> {

            private         MutableLiveData<S>                          mDataSource;
            private final   Callable       <S>                          mProducer;

            private DataSourceFactory(@NonNull final Callable<S> producer) {
                mProducer = producer;
            }

            @NonNull
            @Override
            public DataSource<Key, T> create() {
                final S dataSource;

                try {
                    dataSource = mProducer.call();
                }
                catch (Exception exception) {   // should never happen
                    CoreLogger.log(exception);

                    return new PageKeyedDataSource<Key, T>() {
                        @Override
                        public void loadAfter  (@NonNull final LoadParams         <Key   > params,
                                                @NonNull final LoadCallback       <Key, T> callback) {
                            handleStubCall();
                        }

                        @Override
                        public void loadBefore (@NonNull final LoadParams         <Key   > params,
                                                @NonNull final LoadCallback       <Key, T> callback) {
                            handleStubCall();
                        }

                        @Override
                        public void loadInitial(@NonNull final LoadInitialParams  <Key   > params,
                                                @NonNull final LoadInitialCallback<Key, T> callback) {
                            handleStubCall();
                        }
                    };
                }

                mDataSource = new MutableLiveData<>();
                mDataSource.postValue(dataSource);

                return dataSource;
            }

            private void handleStubCall() {
                CoreLogger.logError("invalid paging DataSource");
            }

            @SuppressWarnings("WeakerAccess")
            public boolean invalidate() {
                CoreLogger.log("about to invalidate DataSource");
                if (mDataSource != null) {
                    final DataSource<Key, T> dataSource = mDataSource.getValue();
                    if (dataSource != null) {
                        dataSource.invalidate();
                        return true;
                    }
                }
                CoreLogger.logWarning("no DataSource to invalidate");
                return false;
            }
        }

        @SuppressWarnings("WeakerAccess")
        public static Config getDefaultConfig() {
            return getDefaultConfig(DEFAULT_PAGE_SIZE);
        }

        @SuppressWarnings("WeakerAccess")
        public static Config getDefaultConfig(int pageSize) {
            final Config.Builder builder = new Config.Builder();
            if (pageSize < 1) {
                CoreLogger.logError("wrong page size " + pageSize +
                        ", default one will be used: " + DEFAULT_PAGE_SIZE);
                pageSize = DEFAULT_PAGE_SIZE;
            }
            builder.setPageSize(pageSize);
            return builder.build();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link BaseViewModel} instances.
     *
     * @param <Key>
     *        The {@link DataSource}'s key
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
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
    public static class Builder<S extends BaseViewModel<D>, Key, T, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     WeakReference<Activity>                   mActivity;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     ViewModelStore                            mViewModelStore;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     LifecycleOwner                            mLifecycleOwner;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     BaseLiveData             <D>              mData;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     String                                    mKey;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     String                                    mTableName;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     DataLoader               <D>              mDataLoader;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Class                    <S>              mClass;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     BaseDialog                                mBaseDialog;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     UriResolver                               mUriResolver;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Config                                    mConfig;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Integer                                   mPageSize;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Callable<? extends DataSource<Key,  T>>   mDataSourceProducer;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     PagingRecyclerViewAdapter<T, R, E>        mAdapter;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Runnable                                  mOnCleared;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     BoundaryCallback         <T      >        mBoundaryCallback;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Executor                                  mFetchExecutor;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Key                                       mInitialLoadKey;

        private final Observer                 <D>              mObserver;

        /**
         * Initialises a newly created {@code Builder} object.
         *
         * @param observer
         *        The {@code Observer}
         */
        public Builder(@NonNull final Observer <D> observer) {
            mObserver               = observer;
        }

        /**
         * Sets the {@code BoundaryCallback} component.
         *
         * @param boundaryCallback
         *        The {@code BoundaryCallback} (please refer to {@link LivePagedListBuilder} for details)
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setBoundaryCallback(final BoundaryCallback<T> boundaryCallback) {
            mBoundaryCallback       = boundaryCallback;
            return this;
        }

        /**
         * Sets the fetch {@code Executor} component.
         *
         * @param fetchExecutor
         *        The {@code Executor} (please refer to {@link LivePagedListBuilder} for details)
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setFetchExecutor(final Executor fetchExecutor) {
            mFetchExecutor          = fetchExecutor;
            return this;
        }

        /**
         * Sets the initial load key.
         *
         * @param initialLoadKey
         *        The initial load key (please refer to {@link LivePagedListBuilder} for details)
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setInitialLoadKey(final Key initialLoadKey) {
            mInitialLoadKey         = initialLoadKey;
            return this;
        }

        /**
         * Sets the {@code ViewModelStore} component.
         *
         * @param viewModelStore
         *        The {@code ViewModelStore}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setViewModelStore(final ViewModelStore viewModelStore) {
            mViewModelStore         = viewModelStore;
            return this;
        }

        /**
         * Sets the {@code BaseLiveData} component.
         *
         * @param baseLiveData
         *        The {@code BaseLiveData}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public Builder<S, Key, T, R, E, D> setBaseLiveData(final BaseLiveData<D> baseLiveData) {
            mData                   = baseLiveData;
            return this;
        }

        /**
         * Sets the {@code BaseViewModel} key.
         *
         * @param key
         *        The {@code BaseViewModel} key (please refer to {@link ViewModelProvider#get(String, Class)})
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setKey(final String key) {
            mKey                    = key;
            return this;
        }

        /**
         * Sets the database cache table name.
         *
         * @param tableName
         *        The table name
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setTableName(final String tableName) {
            mTableName              = tableName;
            return this;
        }

        /**
         * Sets the DataLoader component.
         *
         * @param dataLoader
         *        The {@code DataLoader}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setDataLoader(final DataLoader<D> dataLoader) {
            mDataLoader             = dataLoader;
            return this;
        }

        /**
         * Sets the BaseViewModel's class.
         *
         * @param cls
         *        The BaseViewModel's class
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public Builder<S, Key, T, R, E, D> setClass(final Class<S> cls) {
            mClass                  = cls;
            return this;
        }

        /**
         * Sets the BaseDialog component.
         *
         * @param baseDialog
         *        The {@code BaseDialog}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public Builder<S, Key, T, R, E, D> setBaseDialog(final BaseDialog baseDialog) {
            mBaseDialog             = baseDialog;
            return this;
        }

        /**
         * Sets the UriResolver component.
         *
         * @param uriResolver
         *        The {@code UriResolver}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public Builder<S, Key, T, R, E, D> setUriResolver(final UriResolver uriResolver) {
            mUriResolver            = uriResolver;
            return this;
        }

        /**
         * Sets the Config component (for paging configuration).
         *
         * @param config
         *        The {@code Config}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setConfig(final Config config) {
            mConfig                 = config;
            return this;
        }

        /**
         * Sets the page size (for paging configuration).
         *
         * @param pageSize
         *        The {@code pageSize}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setPageSize(final Integer pageSize) {
            mPageSize               = pageSize;
            return this;
        }

        /**
         * Sets DataSource producer for paging library.
         *
         * @param dataSourceProducer
         *        The {@code DataSource} producer
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setDataSourceProducer(
                final Callable<? extends DataSource<Key, T>> dataSourceProducer) {
            mDataSourceProducer     = dataSourceProducer;
            return this;
        }

        /**
         * Sets the {@code PagingRecyclerViewAdapter} component.
         *
         * @param adapter
         *        The {@link PagingRecyclerViewAdapter}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setAdapter(final PagingRecyclerViewAdapter<T, R, E> adapter) {
            mAdapter                = adapter;
            return this;
        }

        /**
         * Sets the {@code Runnable} component to run when {@link ViewModel#onCleared()} will be called.
         *
         * @param onCleared
         *        The {@link Runnable}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setOnCleared(final Runnable onCleared) {
            mOnCleared              = onCleared;
            return this;
        }

        /**
         * Sets the {@link Activity} to use as a current Activity, a {@link LifecycleOwner}
         * (and a {@link ViewModelStore} - if not yet defined).
         *
         * @param activity
         *        The {@link Activity}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setActivity(final Activity activity) {
            if (activity != null) {
                mActivity           = new WeakReference<>(activity);
                mLifecycleOwner     = getLifecycleOwner(activity, CoreLogger.getDefaultLevel());

                if (mViewModelStore == null)
                    mViewModelStore = getViewModelStore(cast(activity, null));

                else if (activity instanceof ViewModelStoreOwner &&
                        mViewModelStore != getViewModelStore(cast(activity, null)))
                    CoreLogger.logError("ViewModelStore (" + CoreLogger.getDescription(mViewModelStore) +
                            ") already defined for Activity: " + CoreLogger.getDescription(activity));
            }
            return this;
        }

        /**
         * Sets the {@link Fragment} to use as a {@link LifecycleOwner}
         * (and a {@link ViewModelStore} - if not yet defined).
         *
         * @param fragment
         *        The {@link Fragment}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public Builder<S, Key, T, R, E, D> setFragment(final Fragment fragment) {
            if (fragment != null) {
                final Activity activity = fragment.getActivity();
                if (activity == null)
                    CoreLogger.log(new IllegalStateException(
                            "Can't create ViewModelProvider for detached fragment"));
                else {
                    mActivity           = new WeakReference<>(activity);
                    mLifecycleOwner     = fragment;

                    final ViewModelStore viewModelStore = getViewModelStore(fragment);
                    if (mViewModelStore == null)
                        mViewModelStore = viewModelStore;

                    else if (mViewModelStore != viewModelStore)
                        CoreLogger.logError("ViewModelStore (" + CoreLogger.getDescription(mViewModelStore) +
                                ") already defined for Fragment: " + CoreLogger.getDescription(fragment));
                }
            }
            return this;
        }

        /**
         * Returns the {@link BaseViewModel} with the arguments supplied to this builder.
         *
         * @return  The {@code BaseViewModel} instance
         */
        public S create() {
            if (mKey == null) {
                CoreLogger.logError("BaseViewModel.Builder: key == null");
                return null;
            }

            final Activity activity = mActivity == null ? null: mActivity.get();
            if (activity != null && activity.getApplication() == null) {    // from Google sources
                CoreLogger.log(new IllegalStateException("Your Activity is not yet attached to "
                        + "Application. You can't request ViewModel before 'onCreate()' call."));
                return null;
            }
            CoreLogger.log("creating BaseViewModel, Activity: " + CoreLogger.getDescription(activity));

            if (mViewModelStore == null && activity != null)
                mViewModelStore =  getViewModelStore(BaseViewModel.cast(activity, null));
            if (mViewModelStore == null) {
                CoreLogger.logError("BaseViewModel.Builder: ViewModelStore == null");
                return null;
            }

            if (mLifecycleOwner == null && activity instanceof LifecycleOwner)
                mLifecycleOwner =  getLifecycleOwner(activity);
            if (mLifecycleOwner == null)
                CoreLogger.logWarning("LifecycleOwner == null, Activity: " + CoreLogger.getDescription(activity));

            if (mData == null) {
                if (mDataLoader == null) {
                    CoreLogger.logError("DataLoader == null, please set in BaseViewModelBuilder");
                    return null;
                }
                mData = mTableName == null ?
                        new BaseLiveData <>(mDataLoader, mBaseDialog):
                        new CacheLiveData<>(mDataLoader, mBaseDialog, mTableName, mUriResolver);
                if (mData instanceof CacheLiveData && mDataSourceProducer != null)
                    ((CacheLiveData) mData).setMerge(true);
            }

            final boolean noLifecycleOwner = mLifecycleOwner == null;

            final S result;
            if (mDataSourceProducer == null) {
                if (mClass == null) mClass = castBaseViewModelClass(false);

                result = new BaseViewModelProvider<>(mViewModelStore, mData, mObserver, mKey,
                        mOnCleared, noLifecycleOwner).get(mKey, mClass);
            }
            else {
                if (mAdapter == null) {
                    CoreLogger.logError("paging adapter == null, please set in BaseViewModelBuilder");
                    return null;
                }
                if (mConfig == null)
                    mConfig = mPageSize == null ? PagingViewModel.getDefaultConfig():
                            PagingViewModel.getDefaultConfig(mPageSize);
                else if (mPageSize != null)
                    CoreLogger.logWarning("for PagedList both Config and page size are defined " +
                            "in BaseViewModelBuilder, the page size " + mPageSize + " will be ignored");

                if (mClass == null) mClass = castBaseViewModelClass(true);

                result = new PagingViewModel.ViewModelProvider<>(mViewModelStore, mData, mObserver,
                        mKey, mDataSourceProducer, mConfig, mAdapter, mBoundaryCallback,
                        mFetchExecutor, mInitialLoadKey, mOnCleared, noLifecycleOwner).get(mKey, mClass);
            }
            result.updateUi(false, mLifecycleOwner);

            CoreLogger.log("created BaseViewModel: " + CoreLogger.getDescription(result));
            return result;
       }
    }
}
