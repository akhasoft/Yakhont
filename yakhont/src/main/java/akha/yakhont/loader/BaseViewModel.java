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

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.PagingRecyclerViewAdapter;
import akha.yakhont.loader.BaseLiveData.CacheLiveData;
import akha.yakhont.loader.BaseLiveData.Requester;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoadExtendedBuilder;

import android.app.Activity;

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
import androidx.paging.PagedList.Config;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * {@link ViewModel} extender, adjusted to work with {@link BaseLiveData}.
 *
 * @param <D>
 *        The type of data
 *
 * @see BaseLiveData
 */
public class BaseViewModel<D> extends AndroidViewModel {

    private static final String                 DEFAULT_KEY         = "yakhont-default-viewmodel-key";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final BaseLiveData<D>        mData;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected      final Observer    <D>        mObserver;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected            List<CoreLoad<?, ?>>   mCoreLoads;

    /**
     * Initialises a newly created {@link BaseViewModel} object.
     *
     * @param data
     *        The {@link BaseLiveData}
     *
     * @param observer
     *        Please refer to {@link LiveData#observe}
     */
    @SuppressWarnings("WeakerAccess")
    protected BaseViewModel(@NonNull final BaseLiveData<D>     data,
                            @NonNull final Observer    <D>     observer) {
        super(Utils.getApplication());

        mData     = data;
        mObserver = observer;
    }

    /**
     * Returns the {@link BaseViewModel} for the current {@link Activity}.
     *
     * @param <S>
     *        The type of {@link BaseViewModel}
     *
     * @param <D>
     *        The type of data
     *
     * @return  The {@link BaseViewModel}
     */
    public static <S extends BaseViewModel<D>, D> S get() {
        return get((Activity) null, null);
    }

    /**
     * Returns the {@link BaseViewModel} for the given {@link Activity} and key (if any).
     *
     * @param activity
     *        The {@link Activity}
     *
     * @param key
     *        The {@link BaseViewModel} key or null for default value
     *        (please refer to {@link ViewModelProvider#get(String, Class)})
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
    public static <S extends BaseViewModel<D>, D> S get(Activity activity, final String key) {
        if (activity == null) activity = Utils.getCurrentActivity();

        final S result = get(getViewModelStore(activity), key);
        if (result == null)
            CoreLogger.logError("can't find ViewModel for Activity " + CoreLogger.getDescription(activity));

        return result;
    }

    /**
     * Returns the {@link BaseViewModel} for the given {@link Fragment} and key (if any).
     *
     * @param fragment
     *        The {@link Fragment}
     *
     * @param key
     *        The {@link BaseViewModel} key or null for default value
     *        (please refer to {@link ViewModelProvider#get(String, Class)})
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
     * Returns the {@link BaseViewModel} for the given {@link ViewModelStore} and key (if any).
     *
     * @param store
     *        The {@link ViewModelStore}
     *
     * @param key
     *        The {@link BaseViewModel} key or null for default value
     *        (please refer to {@link ViewModelProvider#get(String, Class)})
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
     * Returns the weak reference to {@link BaseViewModel} for the given {@link ViewModelStore}
     * and key (if any).
     *
     * @param store
     *        The {@link ViewModelStore}
     *
     * @param key
     *        The {@link BaseViewModel} key or null for default value
     *        (please refer to {@link ViewModelProvider#get(String, Class)})
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
    public static <S extends BaseViewModel<D>, D> WeakReference<S> getWeak(final ViewModelStore store,
                                                                           final String         key) {
        final boolean[] empty = new boolean[] {true};
        getWeak(store, empty);

        if (!empty[0]) {
            final Map<String, WeakReference<? extends BaseViewModel<?>>> map =
                    BaseViewModelProvider.getHelper(store);

            final WeakReference<S> ref;
            if (key != null)
                ref = (WeakReference<S>) map.get(key);
            else {
                if (map.size() > 1) {
                    final WeakReference<S> tmp = (WeakReference<S>) map.get(DEFAULT_KEY);
                    ref = tmp != null ? tmp: (WeakReference<S>) getHelper(map);
                    CoreLogger.logWarning("expected ViewModel collection size 1, but actually " +
                            map.size() + ", ViewModelStore " + CoreLogger.getDescription(store));
                }
                else
                    ref = (WeakReference<S>) getHelper(map);
            }
            if (ref != null) return ref;

            CoreLogger.logWarning("null WeakReference for key " + key + ", ViewModelStore " +
                    CoreLogger.getDescription(store));
        }

        CoreLogger.logWarning("can't find ViewModel for key " + key + ", ViewModelStore " +
                CoreLogger.getDescription(store));
        return null;
    }

    private static Collection<WeakReference<? extends BaseViewModel<?>>> get(
            final Map<String, WeakReference<? extends BaseViewModel<?>>> map) {
        final Collection<WeakReference<? extends BaseViewModel<?>>> result =
                map == null ? null: map.values();
        if (result == null)
            CoreLogger.logWarning("null map");
        return result;
    }

    private static WeakReference<? extends BaseViewModel<?>> get(
            final Collection<WeakReference<? extends BaseViewModel<?>>> values) {
        final Iterator<WeakReference<? extends BaseViewModel<?>>> iterator =
                values == null ? null: values.iterator();
        if (iterator == null)
            CoreLogger.logWarning("null values");
        return iterator == null ? null: iterator.next();
    }

    private static WeakReference<? extends BaseViewModel<?>> getHelper(
            final Map<String, WeakReference<? extends BaseViewModel<?>>> map) {
        return get(get(map));
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
     * Returns the {@link CoreLoad}'s collection kept in this {@link ViewModel}
     * (mostly for screen orientation changes handling).
     *
     * @return  The {@link CoreLoad}'s collection
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
     * Sets the {@link CoreLoad}'s collection to keep in this {@link ViewModel}
     * (mostly for screen orientation changes handling).
     *
     * @param coreLoads
     *        The {@link CoreLoad}'s collection
     */
    @SuppressWarnings("WeakerAccess")
    public void setCoreLoads(final List<CoreLoad<?, ?>> coreLoads) {
        if (coreLoads != null && coreLoads.size() == 0)
            CoreLogger.logWarning("about to save zero-size list of CoreLoads");
        mCoreLoads = coreLoads;
    }

    /**
     * Sets the {@link CoreLoad} to keep in this {@link ViewModel}
     * (mostly for screen orientation changes handling).
     *
     * @param coreLoad
     *        The {@link CoreLoad}
     */
    public void setCoreLoad(final CoreLoad<?, ?> coreLoad) {
        setCoreLoads(coreLoad == null ? null: Collections.singletonList(coreLoad));
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static <D> Observer<D> updateUi(final boolean              stop,
                                              final LifecycleOwner       lifecycleOwner,
                                              @NonNull final LiveData<D> liveData,
                                              @NonNull final Observer<D> observer) {
        try {
            if (lifecycleOwner == null) {
                if (stop)
                    liveData.removeObserver(observer);
                else
                    liveData.observeForever(observer);
                return observer;
            }
            else if (!stop) liveData.observe(lifecycleOwner, observer);
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
        return null;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected Observer<D> updateUi(final boolean stop, final LifecycleOwner owner, final Runnable callback) {
        final Observer<D> result = updateUi(stop, owner, mData, mObserver);
        try {
            if (callback != null) callback.run();
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "UnusedReturnValue"})
    protected Observer<D> updateUi(final boolean stop, final LifecycleOwner owner) {
        return updateUi(stop, owner, null);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void updateUiFragmentForWeaver(final boolean stop, @NonNull final Fragment fragment) {
        final Collection<BaseViewModel<?>> models = new ArrayList<>();

        BaseViewModelProvider.getViewModels(getViewModelStore(fragment), null,
                models, CoreLogger.getDefaultLevel());

        for (final BaseViewModel<?> model: models)
            model.updateUi(stop, fragment);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void updateUiActivityForWeaver(final boolean stop, @NonNull final Activity activity) {
        final Collection<BaseViewModel<?>> models = getViewModels(activity, false,
                CoreLogger.getDefaultLevel());

        for (final BaseViewModel<?> model: models)
            model.updateUi(stop, getLifecycleOwner(activity));
    }

    private static LifecycleOwner getLifecycleOwner(final Activity activity) {
        return activity instanceof LifecycleOwner ? (LifecycleOwner) activity: null;
    }

    /**
     * Gets ViewModelStore.
     *
     * @param activity
     *        The Activity
     *
     * @return  The ViewModelStore
     *
     * @throws  ViewModelException
     *          please refer to the exception description
     */
    public static ViewModelStore getViewModelStore(Activity activity) {
        if (activity == null) {
            CoreLogger.logWarning("getViewModelStore: about to use current Activity");
            activity = Utils.getCurrentActivity();
        }
        if (activity instanceof FragmentActivity)
            return ((FragmentActivity) activity).getViewModelStore();
        if (activity instanceof ViewModelStoreOwner)
            return ((ViewModelStoreOwner) activity).getViewModelStore();

        throw new ViewModelException("unexpected activity (should be ViewModelStoreOwner or " +
                "FragmentActivity): " + CoreLogger.getDescription(activity));
    }

    /**
     * Extends {@link RuntimeException} and will be thrown if given Activity can't provide
     * {@link ViewModelStore} instance.
     */
    @SuppressWarnings("WeakerAccess")
    public static class ViewModelException extends RuntimeException {

        /**
         * Please refer to the {@link RuntimeException#RuntimeException(String)}.
         */
        @SuppressWarnings("WeakerAccess")
        public ViewModelException(final String message) {
            super(message);
        }
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
        return isLoading(activity, null, true);
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
     * @param activity
     *        The Activity
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
    public static boolean isLoading(@NonNull final Activity activity, final String key,
                                    final boolean includeFragments) {
        final Level level = Level.ERROR;
        final boolean result = BaseViewModelProvider.isLoading(getViewModelStore(activity), key, level);
        return result || !includeFragments ? result: isLoadingFragment(activity, level);
    }

    private static boolean isLoadingFragment(@NonNull final Activity activity, @NonNull final Level level) {
        if (!(activity instanceof FragmentActivity)) {
            CoreLogger.logError("unexpected activity (should be FragmentActivity): "
                    + CoreLogger.getDescription(activity));
            return false;
        }
        for (final Fragment fragment: ((FragmentActivity) activity)
                .getSupportFragmentManager().getFragments())
            if (BaseViewModelProvider.isLoading(getViewModelStore(fragment), null, level))
                return true;
        return false;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean isLoadingForWeaver(@NonNull final Activity activity) {
        final Collection<BaseViewModel<?>> models = getViewModels(activity, true,
                CoreLogger.getDefaultLevel());

        for (final BaseViewModel<?> model: models) {
            final BaseLiveData<?> data = model.getData();
            if (data.isLoading() && data.confirm(activity, null)) return true;
        }
        return false;
    }

    /**
     * Returns collection of {@code BaseViewModel} associated with the current Activity.
     *
     * @param activity
     *        The Activity
     *
     * @param includeFragments
     *        {@code true} to include fragments data loading status, {@code false} otherwise
     *
     * @return  The {@code BaseViewModel} collection
     */
    @SuppressWarnings("unused")
    public static Collection<BaseViewModel<?>> getViewModels(
            final Activity activity, final boolean includeFragments) {
        return getViewModels(activity, includeFragments, Level.ERROR);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Collection<BaseViewModel<?>> getViewModels(
            Activity activity, final boolean includeFragments, @NonNull final Level level) {

        if (activity == null) activity = Utils.getCurrentActivity();

        final Collection<BaseViewModel<?>> list = new ArrayList<>();
        BaseViewModelProvider.getViewModels(getViewModelStore(activity), null, list, level);

        if (includeFragments)
            if (activity instanceof FragmentActivity)
                for (final Fragment fragment: ((FragmentActivity) activity)
                        .getSupportFragmentManager().getFragments())
                    BaseViewModelProvider.getViewModels(getViewModelStore(fragment), null,
                            list, level);
            else
                CoreLogger.log("unexpected activity (should be FragmentActivity): "
                        + CoreLogger.getDescription(activity));
        return list;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getViewModels(
            @NonNull final Activity activity) {
        return BaseViewModelProvider.getEntries(activity);
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

    private static class BaseViewModelFactory<D> implements Factory {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  WeakReference<ViewModelStore>
                                                mStore;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  String                 mKey;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  BaseLiveData<D>        mData;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final  Observer    <D>        mObserver;

        @SuppressWarnings("unused")
        private BaseViewModelFactory(@NonNull final ViewModelStore     store,
                                     @NonNull final BaseLiveData<D>    data,
                                     @NonNull final Observer    <D>    observer,
                                              final String             key) {
            mStore      = new WeakReference<>(store);
            mKey        = key;
            mData       = data;
            mObserver   = observer;
        }

        @SuppressWarnings("WeakerAccess")
        protected BaseViewModel<D> createViewModel() {
            return new BaseViewModel<>(mData, mObserver);
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> notUsed) {
            final BaseViewModel<D> baseViewModel = createViewModel();

            final String key = mKey != null ? mKey: DEFAULT_KEY;
            BaseViewModelProvider.put(mStore, key, new WeakReference<>(baseViewModel));

            @SuppressWarnings("unchecked")
            final T result = (T) baseViewModel;
            return result;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class BaseViewModelProvider<D> extends ViewModelProvider {

        // this is 'cause of ViewModelStore limitation (no official way to get list of keys)
        private final static
                         Map<ViewModelStore, Map<String, WeakReference<? extends BaseViewModel<?>>>>
                                                sMap            = Utils.newWeakMap();

        @SuppressWarnings("unused")
        private BaseViewModelProvider(@NonNull final ViewModelStore     store,
                                      @NonNull final BaseLiveData<D>    data,
                                      @NonNull final Observer    <D>    observer,
                                               final String             key) {
            this(store, new BaseViewModelFactory<>(store, data, observer, key));
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
                @NonNull final Activity activity) {
            final Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> result =
                    getEntries(getViewModelStore(activity));

            CoreLogger.log("entries for activity " + activity + " - " + result);
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
         */
        @SuppressWarnings("WeakerAccess")
        protected PagingViewModel(
                @NonNull final BaseLiveData                      <D>    data,
                @NonNull final Observer                          <D>    observer,
                @NonNull final Callable<? extends DataSource<Key, T>>   dataSourceProducer,
                @NonNull final Config                                   config,
                @NonNull final PagingRecyclerViewAdapter<T, R, E>       adapter) {

            super(data, observer);

            mDataSourceFactory  = new DataSourceFactory<>(dataSourceProducer);
            mDataPaged          = new LivePagedListBuilder<>(mDataSourceFactory, config).build();
            mAdapter            = adapter;

            //noinspection Convert2Lambda
            mObserverPaged      = new Observer<PagedList<T>>() {
                @Override
                public void onChanged(@Nullable PagedList<T> data) {
                    mAdapter.submitList(data);

                    if (mSwipeToRefresh != null) Utils.safeRunnableRun(mSwipeToRefresh);
                }
            };
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public void setOnSwipeToRefresh(final Runnable callback) {
            mSwipeToRefresh = callback;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @Override
        protected Observer<D> updateUi(final boolean stop, final LifecycleOwner owner) {
            //noinspection Convert2Lambda
            return super.updateUi(stop, owner, new Runnable() {
                @Override
                public void run() {
                    updateUi(stop, owner, mDataPaged, mObserverPaged);
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

            private ViewModelFactory(
                    @NonNull final ViewModelStore                           store,
                    @NonNull final BaseLiveData                      <D>    data,
                    @NonNull final Observer                          <D>    observer,
                             final String                                   key,
                    @NonNull final Callable<? extends DataSource<Key, T>>   dataSourceProducer,
                    @NonNull final Config                                   config,
                    @NonNull final PagingRecyclerViewAdapter<T, R, E>       adapter) {

                super(store, data, observer, key);

                mDataSourceProducer     = dataSourceProducer;
                mConfig                 = config;
                mAdapter                = adapter;
            }

            @Override
            protected BaseViewModel<D> createViewModel() {
                return new PagingViewModel<>(mData, mObserver, mDataSourceProducer, mConfig, mAdapter);
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
                    @NonNull final PagingRecyclerViewAdapter<T, R, E>       adapter) {

                super(store, new ViewModelFactory<>(store, data, observer, key, dataSourceProducer,
                        config, adapter));
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
                        public void loadAfter  (@NonNull LoadParams         <Key   > params,
                                                @NonNull LoadCallback       <Key, T> callback) {
                            handleStubCall();
                        }

                        @Override
                        public void loadBefore (@NonNull LoadParams         <Key   > params,
                                                @NonNull LoadCallback       <Key, T> callback) {
                            handleStubCall();
                        }

                        @Override
                        public void loadInitial(@NonNull LoadInitialParams  <Key   > params,
                                                @NonNull LoadInitialCallback<Key, T> callback) {
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
        protected     ViewModelStore                            mStore;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     LifecycleOwner                            mLifecycleOwner;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     BaseLiveData             <D>              mData;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     String                                    mKey;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     String                                    mTableName;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected     Requester                <D>              mRequester;
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
         * Sets the {@code ViewModelStore} component.
         *
         * @param viewModelStore
         *        The {@code ViewModelStore}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         */
        public Builder<S, Key, T, R, E, D> setViewModelStore(final ViewModelStore viewModelStore) {
            mStore                  = viewModelStore;
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
         * Sets the requester component.
         *
         * @param requester
         *        The {@code BaseResponseLoaderWrapper.Requester}
         *
         * @return  This {@code Builder} object to allow for chaining of calls to set methods
         *
         * @see     CoreLoadExtendedBuilder#setRequester(BaseResponseLoaderWrapper.Requester)
         */
        public Builder<S, Key, T, R, E, D> setRequester(final Requester<D> requester) {
            mRequester              = requester;
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
                mLifecycleOwner     = getLifecycleOwner(activity);
                if (mStore ==  null)
                    mStore          = getViewModelStore(activity);
                else
                    CoreLogger.logWarning("ViewModelStore already defined for activity " +
                            CoreLogger.getDescription(activity));
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
                    if (mStore ==  null)
                        mStore          = getViewModelStore(fragment);
                    else
                        CoreLogger.logWarning("ViewModelStore already defined for fragment " +
                                CoreLogger.getDescription(fragment));
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
            Activity activity = mActivity == null ? null: mActivity.get();
            if (activity == null) activity = Utils.getCurrentActivity();

            if (activity.getApplication() == null) {
                CoreLogger.log(new IllegalStateException(
                        "Your activity is not yet attached to "
                                + "Application. You can't request ViewModel before onCreate call."));
                return null;
            }
            CoreLogger.log("creating BaseViewModel, Activity " + CoreLogger.getDescription(activity));

            if (mStore == null) mStore = getViewModelStore(activity);

            if (mLifecycleOwner == null) mLifecycleOwner = getLifecycleOwner(activity);

            if (mData == null) {
                if (mRequester == null) {
                    CoreLogger.logError("requester == null, please set in BaseViewModelBuilder");
                    return null;
                }
                mData = mTableName == null ?
                        new BaseLiveData <>(mRequester, mBaseDialog):
                        new CacheLiveData<>(mRequester, mBaseDialog, mTableName, mUriResolver);
            }

            if (mKey == null) mKey = DEFAULT_KEY;

            final S result;
            if (mDataSourceProducer == null) {
                if (mClass == null) mClass = castBaseViewModelClass(false);

                result = new BaseViewModelProvider<>(mStore, mData, mObserver, mKey).get(mKey, mClass);
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

                result = new PagingViewModel.ViewModelProvider<>(mStore, mData, mObserver, mKey,
                        mDataSourceProducer, mConfig, mAdapter).get(mKey, mClass);
            }
            result.updateUi(false, mLifecycleOwner);

            CoreLogger.log("created BaseViewModel " + CoreLogger.getDescription(result));
            return result;
       }
    }
}
