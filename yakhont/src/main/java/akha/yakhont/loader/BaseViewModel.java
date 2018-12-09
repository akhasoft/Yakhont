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

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.loader.BaseLiveData.CacheLiveData;
import akha.yakhont.loader.BaseLiveData.Requester;

import android.app.Activity;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProvider.Factory;
import android.arch.lifecycle.ViewModelStore;
import android.arch.lifecycle.ViewModelStoreOwner;
import android.arch.lifecycle.ViewModelStores;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class BaseViewModel<D> extends AndroidViewModel {

    private static final String                 DEFAULT_KEY     = "cf2a52ae-6f9f-4800-8f14-bc8a2794de8e";

    private final static ViewModelStore         sStubStore      = new ViewModelStore();

    private        final BaseLiveData<D>        mData;
    private        final Observer    <D>        mObserver;

    private static <D> BaseLiveData<D> getDefaultLiveData(@NonNull final Requester<D> requester,
                                                                   final String tableName) {
        return tableName == null ?
                new BaseLiveData<>(requester): new CacheLiveData<>(requester, tableName, null);
    }

    private static String getKey(final String key) {
        return key == null ? DEFAULT_KEY: key;
    }

    public static <D> BaseViewModel<D> getInstance(               Activity         activity,
                                                   @NonNull final ViewModelStore   store,
                                                            final String           key,
                                                            final String           tableName,
                                                   @NonNull final Requester<D>     requester,
                                                   @NonNull final Observer <D>     observer) {
        if (activity == null) activity = Utils.getCurrentActivity();

        return getInstance(activity, store, getLifecycleOwner(activity),
                getDefaultLiveData(requester, tableName), observer, getKey(key), castBaseViewModelClass());
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Activity         activity,
                                                            final String           key,
                                                            final String           tableName,
                                                   @NonNull final Requester<D>     requester,
                                                   @NonNull final Observer <D>     observer) {
        return getInstance(activity, getDefaultLiveData(requester, tableName),
                observer, getKey(key), castBaseViewModelClass());
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Fragment         fragment,
                                                            final String           key,
                                                            final String           tableName,
                                                   @NonNull final Requester<D>     requester,
                                                   @NonNull final Observer <D>     observer) {
        return getInstance(fragment, getDefaultLiveData(requester, tableName),
                observer, getKey(key), castBaseViewModelClass());
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Activity         activity,
                                                   @NonNull final String           key,
                                                   @NonNull final Class<? extends BaseViewModel<D>>
                                                                                   cls,
                                                            final BaseDialog       baseDialog,
                                                            final String           tableName,
                                                   @NonNull final Requester<D>     requester,
                                                   @NonNull final Observer <D>     observer) {
        return getInstance(activity, key, cls, tableName == null ?
                new BaseLiveData <>(requester, baseDialog):
                new CacheLiveData<>(requester, baseDialog, tableName, null), observer);
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Fragment         fragment,
                                                   @NonNull final String           key,
                                                   @NonNull final Class<? extends BaseViewModel<D>>
                                                                                   cls,
                                                            final BaseDialog       baseDialog,
                                                            final String           tableName,
                                                   @NonNull final Requester<D>     requester,
                                                   @NonNull final Observer <D>     observer) {
        return getInstance(fragment, key, cls, tableName == null ?
                new BaseLiveData <>(requester, baseDialog):
                new CacheLiveData<>(requester, baseDialog, tableName, null), observer);
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Activity         activity,
                                                   @NonNull final BaseLiveData<D>  data,
                                                   @NonNull final Observer    <D>  observer) {
        return getInstance(activity, data, observer, DEFAULT_KEY, castBaseViewModelClass());
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Fragment         fragment,
                                                   @NonNull final BaseLiveData<D>  data,
                                                   @NonNull final Observer    <D>  observer) {
        return getInstance(fragment, data, observer, DEFAULT_KEY, castBaseViewModelClass());
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Activity         activity,
                                                   @NonNull final String           key,
                                                   @NonNull final Class<? extends BaseViewModel<D>>
                                                                                   cls,
                                                   @NonNull final BaseLiveData<D>  data,
                                                   @NonNull final Observer    <D>  observer) {
        return getInstance(activity, data, observer, key, cls);
    }

    public static <D> BaseViewModel<D> getInstance(@NonNull final Fragment         fragment,
                                                   @NonNull final String           key,
                                                   @NonNull final Class<? extends BaseViewModel<D>>
                                                                                   cls,
                                                   @NonNull final BaseLiveData<D>  data,
                                                   @NonNull final Observer    <D>  observer) {
        return getInstance(fragment, data, observer, key, cls);
    }

    private static <T extends ViewModel, D> T getInstance(@NonNull final Activity          activity,
                                                          @NonNull final BaseLiveData<D>   data,
                                                          @NonNull final Observer    <D>   observer,
                                                          @NonNull final String            key,
                                                          @NonNull final Class       <T>   cls) {
//      return ViewModelProviders.of(activity).get(key, cls);

        return getInstance(activity, getViewModelStore(activity), getLifecycleOwner(activity),
                data, observer, key, cls);
    }

    private static <T extends ViewModel, D> T getInstance(@NonNull final Fragment          fragment,
                                                          @NonNull final BaseLiveData<D>   data,
                                                          @NonNull final Observer    <D>   observer,
                                                          @NonNull final String            key,
                                                          @NonNull final Class       <T>   cls) {
//      return ViewModelProviders.of(fragment).get(key, cls);

        final Activity activity = fragment.getActivity();
        if (activity == null) {
            CoreLogger.log(new IllegalStateException(
                    "Can't create ViewModelProvider for detached fragment"));
            return null;
        }

        return getInstance(activity, getViewModelStore(fragment), fragment, data, observer, key, cls);
    }

    private static <T extends ViewModel, D> T getInstance(@NonNull final Activity          activity,
                                                          @NonNull final ViewModelStore    store,
                                                          @NonNull final LifecycleOwner    lifecycleOwner,
                                                          @NonNull final BaseLiveData<D>   data,
                                                          @NonNull final Observer    <D>   observer,
                                                          @NonNull final String            key,
                                                          @NonNull final Class       <T>   cls) {
        if (activity.getApplication() == null) {
            CoreLogger.log(new IllegalStateException(
                    "Your activity is not yet attached to "
                            + "Application. You can't request ViewModel before onCreate call."));
            return null;
        }

        final T result = new BaseViewModelProvider<>(store, data, observer, key).get(key, cls);

        updateUi(lifecycleOwner, data, observer);

        return result;
    }

    private static <D> void updateUi(@NonNull final LifecycleOwner   lifecycleOwner,
                                     @NonNull final LiveData<D>      liveData,
                                     @NonNull final Observer<D>      observer) {
        try {
            liveData.observe(lifecycleOwner, observer);
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
    }

    public void updateUi(@NonNull final LifecycleOwner lifecycleOwner) {
        updateUi(lifecycleOwner, mData, mObserver);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void updateUiFragmentForWeaver(@NonNull final Fragment fragment) {
        final Collection<BaseViewModel<?>> models = new ArrayList<>();

        BaseViewModelProvider.getViewModels(getViewModelStore(fragment), null, models);

        for (final BaseViewModel<?> model: models)
            model.updateUi(fragment);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void updateUiActivityForWeaver(@NonNull final Activity activity) {
        final Collection<BaseViewModel<?>> models = getViewModels(activity, false);

        for (final BaseViewModel<?> model: models)
            model.updateUi(getLifecycleOwner(activity));
    }

    private static LifecycleOwner getLifecycleOwner(final Activity activity) {
        if (activity instanceof LifecycleOwner)
            return (LifecycleOwner) activity;

        CoreLogger.logWarning("about to use ProcessLifecycleOwner for " +
                Utils.getActivityName(activity));
        return ProcessLifecycleOwner.get();
    }

    public static ViewModelStore getViewModelStore(Activity activity) {

        if (activity == null) {
            CoreLogger.logWarning("getViewModelStore: about to use current Activity");
            activity = Utils.getCurrentActivity();
        }

        if (activity instanceof FragmentActivity)
            return ViewModelStores.of((FragmentActivity) activity);
        if (activity instanceof ViewModelStoreOwner)
            return ((ViewModelStoreOwner) activity).getViewModelStore();

        logUnexpectedActivity(activity);
        return sStubStore;
    }

    private static void logUnexpectedActivity(final Activity activity) {
        CoreLogger.logError("unexpected activity (should be ViewModelStoreOwner or FragmentActivity): "
                + Utils.getActivityName(activity));
    }

    public static ViewModelStore getViewModelStore(@NonNull final Fragment fragment) {
        return ViewModelStores.of(fragment);
    }

    @SuppressWarnings("unchecked")
    private static <T extends ViewModel> Class<T> castBaseViewModelClass() {
        return (Class<T>) BaseViewModel.class;
    }

    public static boolean isLoading(@NonNull final Activity activity) {
        return isLoading(activity, null, true);
    }

    public static boolean isLoading(@NonNull final Fragment fragment) {
        return isLoading(fragment, null);
    }

    public static boolean isLoading(@NonNull final Fragment fragment, final String key) {
        return BaseViewModelProvider.isLoading(getViewModelStore(fragment), key);
    }

    public static boolean isLoading(@NonNull final Activity activity, final String key,
                                    final boolean includeFragments) {
        final boolean result = BaseViewModelProvider.isLoading(getViewModelStore(activity), key);
        return result || !includeFragments ? result: isLoadingFragment(activity);
    }

    private static boolean isLoadingFragment(@NonNull final Activity activity) {
        if (!(activity instanceof FragmentActivity)) {
            CoreLogger.logError("unexpected activity (should be FragmentActivity): "
                    + Utils.getActivityName(activity));
            return false;
        }
        for (final Fragment fragment: ((FragmentActivity) activity)
                .getSupportFragmentManager().getFragments())
            if (BaseViewModelProvider.isLoading(getViewModelStore(fragment), null))
                return true;
        return false;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static boolean isLoadingForWeaver(@NonNull final Activity activity) {
        final Collection<BaseViewModel<?>> models = getViewModels(activity, true);

        for (final BaseViewModel<?> model: models) {
            final BaseLiveData<?> data = model.getData();
            if (data.isLoading() && data.confirm(activity)) return true;
        }
        return false;
    }

    public static Collection<BaseViewModel<?>> getViewModels(Activity activity, final boolean includeFragments) {

        if (activity == null) activity = Utils.getCurrentActivity();
/*
        if (!(activity instanceof FragmentActivity)) {
            CoreLogger.logWarning("getViewModels: expected FragmentActivity but actually " +
                    Utils.getActivityName(activity));
            return null;
        }
        final FragmentActivity fragmentActivity = (FragmentActivity) activity;
*/
        final Collection<BaseViewModel<?>> list = new ArrayList<>();
        BaseViewModelProvider.getViewModels(getViewModelStore(activity), null, list);

        if (includeFragments)
            if (activity instanceof FragmentActivity)
                for (final Fragment fragment: ((FragmentActivity) activity)
                        .getSupportFragmentManager().getFragments())
                    BaseViewModelProvider.getViewModels(getViewModelStore(fragment), null, list);
            else
                CoreLogger.log("unexpected activity (should be FragmentActivity): "
                        + Utils.getActivityName(activity));
        return list;
    }

    public static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getViewModels(
            @NonNull final Activity activity) {
        return BaseViewModelProvider.getEntries(activity);
    }

    public static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getViewModels(
            @NonNull final Fragment fragment) {
        return BaseViewModelProvider.getEntries(fragment);
    }

    public static Set<Map.Entry<String, WeakReference<? extends BaseViewModel<?>>>> getViewModels(
            @NonNull final ViewModelStore store) {
        return BaseViewModelProvider.getEntries(store);
    }

    protected BaseViewModel(@NonNull final BaseLiveData<D>     data,
                            @NonNull final Observer    <D>     observer) {
        super(Utils.getApplication());

        mData     = data;
        mObserver = observer;
    }

    @SuppressWarnings("unchecked")
    public <S extends BaseLiveData<D>> S getData() {
        return (S) mData;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class BaseViewModelFactory<D> implements Factory {

        private final    WeakReference<ViewModelStore>
                                                mStore;
        private final    String                 mKey;
        private final    BaseLiveData<D>        mData;
        private final    Observer    <D>        mObserver;

        private BaseViewModelFactory(@NonNull final ViewModelStore     store,
                                     @NonNull final BaseLiveData<D>    data,
                                     @NonNull final Observer    <D>    observer,
                                              final String             key) {
            mStore      = new WeakReference<>(store);
            mKey        = key;
            mData       = data;
            mObserver   = observer;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> notUsed) {
            final BaseViewModel<D> baseViewModel = new BaseViewModel<>(mData, mObserver);

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

        private BaseViewModelProvider(@NonNull final ViewModelStore     store,
                                      @NonNull final BaseLiveData<D>    data,
                                      @NonNull final Observer    <D>    observer,
                                               final String             key) {
            super(store, new BaseViewModelFactory<>(store, data, observer, key));
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

        private static boolean checkModels(
                final Map<String, WeakReference<? extends BaseViewModel<?>>> models) {
            boolean result = false;
            if (models == null)       // should never happen
                CoreLogger.logError("models == null");
            else if (models.isEmpty())
                CoreLogger.logError("models is empty");
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

        private static boolean isLoading(@NonNull final ViewModelStore store, final String key) {
            final Map<String, WeakReference<? extends BaseViewModel<?>>> models = sMap.get(store);
            if (!checkModels(models)) return false;

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

        private static void getViewModels(@NonNull final ViewModelStore store, final String key,
                                          @NonNull final Collection<BaseViewModel<?>> baseViewModels) {
            final Map<String, WeakReference<? extends BaseViewModel<?>>> models = sMap.get(store);
            if (!checkModels(models)) return;

            if (key != null)
                getViewModel(models.get(key), baseViewModels);
            else
                for (final WeakReference<? extends BaseViewModel<?>> model: models.values())
                    getViewModel(model, baseViewModels);
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
}
