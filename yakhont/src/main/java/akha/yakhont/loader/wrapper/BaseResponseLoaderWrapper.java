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

import akha.yakhont.Core;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseCacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterWrapper;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.DataBindingCacheAdapterWrapper;
import akha.yakhont.adapter.BaseRecyclerViewAdapter;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.ViewHolderCreator;
import akha.yakhont.adapter.ValuesCacheAdapterWrapper;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog.Progress;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.LoadParameters;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.BaseViewModel;
import akha.yakhont.loader.BaseConverter;
import akha.yakhont.technology.retrofit.BaseRetrofit;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import android.app.Activity;
import android.content.res.Resources;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.GridView;
import android.widget.ListView;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelStore;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseResponseLoaderWrapper<C, R, E, D> extends BaseLoaderWrapper<BaseResponse<R, E, D>> {

    // just a placeholder for the moment
    private static final int                                    MAX_TABLE_NAME_LENGTH   = 1024;

    private   final     String                                  mTableName;
    private   final     String                                  mTableDescription;

    private             boolean                                 mNoCache;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final     Requester<C>                            mRequester;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final     Converter<D>                            mConverter;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
    protected final     UriResolver                             mUriResolver;

    private             CacheAdapter<R, E, D>                   mAdapter;

    private             LoaderRx<R, E, D>                       mRx;

    private             LoaderCallbacks<E, D>                   mLoaderCallbacks;

    /**
     * The API for data loading.
     *
     * @param <C>
     *        The type of callback (or Retrofit API)
     */
    @SuppressWarnings("unused")
    public interface Requester<C> {

        /**
         * Starts an asynchronous data loading.
         *
         * @param parameter
         *        The callback (or Retrofit API)
         *
         * @yakhont.see BaseResponseLoaderWrapper#CoreLoad CoreLoad
         * @yakhont.see BaseLoader#makeRequest(C) BaseLoader.makeRequest()
         */
        void makeRequest(C parameter);
    }

    // for lambda support
    @SuppressWarnings("WeakerAccess")
    public interface LoaderCallback<D> {
        void onLoadFinished(D data, Source source);
    }

    @SuppressWarnings("unused")
    public static abstract class LoaderCallbacks<E, D> implements LoaderCallback<D> {

        @Override
        public void onLoadFinished(final D data, final Source source) {
            CoreLogger.log("empty onLoadFinished in " + this);
        }

        @SuppressWarnings("WeakerAccess")
        public void onLoadError(@SuppressWarnings("unused") final E      error,
                                @SuppressWarnings("unused") final Source source) {
            CoreLogger.log("empty onLoadError in " + this);
        }
    }

    protected BaseResponseLoaderWrapper(@NonNull final ViewModelStore viewModelStore, final String loaderId,
                                        @NonNull final Requester<C> requester,
                                        @NonNull final String tableName, final String description,
                                        @NonNull final Converter<D> converter, @NonNull final UriResolver uriResolver) {
        this(viewModelStore, loaderId, requester, adjustTableName(tableName), description,
                converter, uriResolver, isLoaderIdAutoGenerated(loaderId));
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected BaseResponseLoaderWrapper(@NonNull final ViewModelStore viewModelStore, final String loaderId,
                                        @NonNull final Requester<C> requester,
                                        @NonNull final String tableName, final String description,
                                        @NonNull final Converter<D> converter, @NonNull final UriResolver uriResolver,
                                        final boolean loaderIdAutoGenerated) {
        super(viewModelStore, loaderIdAutoGenerated && tableName.length() > 0 ?
                generateLoaderId(tableName): loaderId, loaderIdAutoGenerated);

        mTableName              = tableName;
        mTableDescription       = description;
        CoreLogger.log("assigned table name: " + mTableName);

        mRequester              = requester;
        mConverter              = converter;
        mUriResolver            = uriResolver;
    }

    private static String generateLoaderId(@NonNull final String tableName) {
        return tableName;
    }

    @SuppressWarnings("unused")
    public static void clearCache(final CoreLoad coreLoad) {
        if (coreLoad == null) {
            CoreLogger.logError("coreLoad == null");
            return;
        }

        for (final BaseLoaderWrapper baseLoaderWrapper: coreLoad.getLoaders())
            if (baseLoaderWrapper instanceof BaseResponseLoaderWrapper)
                Utils.clearCache(baseLoaderWrapper.getTableName());
    }

    private static String adjustTableName(String tableName) {
        if (tableName == null || tableName.trim().length() == 0) {
            CoreLogger.logError("empty table name");
            return "";
        }
        CoreLogger.log("table name before adjusting: " + tableName);

        if (tableName.startsWith("[L") && tableName.endsWith(";"))
            tableName = tableName.substring(2, tableName.length() - 1);

        tableName = Utils.replaceSpecialChars(tableName);

        //noinspection ConstantConditions,ConstantConditions
        tableName = tableName.length() > MAX_TABLE_NAME_LENGTH ?
                tableName.substring(MAX_TABLE_NAME_LENGTH - tableName.length()): tableName;

        CoreLogger.log("table name after adjusting: " + tableName);
        return tableName;
    }

    @Override
    public BaseLoaderWrapper findLoader(final Collection<BaseLoaderWrapper<?>> loaders) {
        final BaseLoaderWrapper baseLoaderWrapper = super.findLoader(loaders);

        if (baseLoaderWrapper instanceof BaseResponseLoaderWrapper) {
            final String table = ((BaseResponseLoaderWrapper) baseLoaderWrapper).mTableName;
            if (!mTableName.equals(table))
                CoreLogger.logError("found loader with id " + getLoaderId() +
                        " but table is different: found " + table + ", should be " + mTableName);
        }

        return baseLoaderWrapper;
    }

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @NonNull
    public BaseResponseLoaderWrapper<C, R, E, D> setNoCache(final boolean noCache) {
        mNoCache            = noCache;
        return this;
    }

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @NonNull
    public BaseResponseLoaderWrapper<C, R, E, D> setLoaderCallbacks(final LoaderCallbacks<E, D> loaderCallbacks) {
        mLoaderCallbacks    = loaderCallbacks;
        return this;
    }

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @NonNull
    public BaseResponseLoaderWrapper<C, R, E, D> setAdapter(final CacheAdapter<R, E, D> adapter) {
        mAdapter            = adapter;
        return this;
    }

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    @NonNull
    public BaseResponseLoaderWrapper<C, R, E, D> setRx(final LoaderRx<R, E, D> rx) {
        mRx                 = rx;
        return this;
    }

    @SuppressWarnings("unused")
    public LoaderRx<R, E, D> getRx() {
        return mRx;
    }

    @SuppressWarnings("unused")
    public CacheAdapter<R, E, D> getAdapter() {
        return mAdapter;
    }

    @Override
    public String getTableName() {
        return mNoCache ? null: mTableName;
    }

    @Override
    protected String getTableDescription() {
        return mTableDescription;
    }

    @Override
    public Type getType() {
        final Type type = super.getType();
        return type != null ? type: mConverter.getType();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static <D> Converter<D> getDefaultConverter() {
        return new BaseConverter<>();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static UriResolver getDefaultUriResolver() {
        return Utils.getUriResolver();
    }

    @CallSuper
    @Override
    protected void onLoadFinished(final BaseResponse<R, E, D> data, final LoadParameters parameters) {
        super.onLoadFinished(data, parameters);

        if (mLoaderCallbacks != null) {
            if (data == null)
                mLoaderCallbacks.onLoadFinished(null, null);
            else {
                final E      error  = data.getError ();
                final Source source = data.getSource();

                if (error != null)
                    mLoaderCallbacks.onLoadError(error, source);
                else
                    mLoaderCallbacks.onLoadFinished(data.getResult(), source);
            }
        }

        updateAdapter(data, parameters != null && parameters.getMerge());

        if (mRx != null) mRx.onResult(data);
    }

    private void updateAdapter(final BaseResponse<R, E, D> data, final boolean isMerge) {
        if (mAdapter != null)
            mAdapter.update(data, isMerge, null);
        else
            CoreLogger.logWarning("adapter == null, table name: " + mTableName);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static abstract class BaseResponseLoaderBuilder<C, R, E, D> implements LoaderBuilder<C, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final ViewModelStore                          mViewModelStore;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Requester<C>                            mRequester;

        private         String                                  mTableName;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       String                                  mDescription;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Boolean                                 mNoCache;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       String                                  mLoaderId;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Type                                    mType;
        private         Converter<D>                            mConverter;
        private         UriResolver                             mUriResolver;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       LoaderCallbacks<E, D>                   mLoaderCallbacks;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Callable<Progress>                            mProgress;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Callable<BaseViewModel<BaseResponse<R, E, D>>>
                                                                mBaseViewModel;

        @SuppressWarnings("WeakerAccess")
        public BaseResponseLoaderBuilder() {
            mViewModelStore     = BaseViewModel.getViewModelStore((Activity) null);
        }

        @SuppressWarnings("WeakerAccess")
        public BaseResponseLoaderBuilder(@NonNull final Fragment fragment) {
            mViewModelStore     = BaseViewModel.getViewModelStore(fragment);
        }

        @SuppressWarnings("WeakerAccess")
        public BaseResponseLoaderBuilder(@NonNull final Activity activity) {
            mViewModelStore     = BaseViewModel.getViewModelStore(activity);
        }

        @SuppressWarnings("WeakerAccess")
        public BaseResponseLoaderBuilder(@NonNull final ViewModelStore viewModelStore) {
            mViewModelStore     = viewModelStore;
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setRequester(@NonNull final Requester<C> requester) {
            mRequester          = requester;
            return this;
        }

        @SuppressWarnings("unused")
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setTableName(@NonNull final Context context,
                                                                  @StringRes final int tableNameId) {
            return setTableName(context.getString(tableNameId));
        }

        @SuppressWarnings("WeakerAccess")
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setTableName(@NonNull final String tableName) {
            mTableName          = adjustTableName(tableName);
            return this;
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setLoaderId(final String loaderId) {
            mLoaderId           = loaderId;
            return this;
        }

        @SuppressWarnings("WeakerAccess")
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setDescription(@NonNull final String description) {
            mDescription        = description;
            return this;
        }

        @SuppressWarnings("unused")
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setDescription(@NonNull final Context context,
                                                                    @StringRes final int descriptionId) {
            return setDescription(context.getString(descriptionId));
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setConverter(@NonNull final Converter<D> converter) {
            mConverter          = converter;
            return this;
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setUriResolver(@NonNull final UriResolver uriResolver) {
            mUriResolver        = uriResolver;
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setType(@NonNull final Type type) {
            mType               = type;
            return this;
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setNoCache(final Boolean noCache) {
            mNoCache            = noCache;
            return this;
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setLoaderCallbacks(final LoaderCallbacks<E, D> loaderCallbacks) {
            mLoaderCallbacks    = loaderCallbacks;
            return this;
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setProgress(final Callable<Progress> progress) {
            mProgress           = progress;
            return this;
        }

        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setBaseViewModel(
                final Callable<BaseViewModel<BaseResponse<R, E, D>>> baseViewModel) {
            mBaseViewModel      = baseViewModel;
            return this;
        }

        @Override
        public final BaseResponseLoaderWrapper<C, R, E, D> createBaseResponseLoader() {
            final BaseResponseLoaderWrapper<C, R, E, D> loaderWrapper =
                    createBaseResponseLoaderWrapper(mViewModelStore);

            loaderWrapper.setLoaderCallbacks(mLoaderCallbacks);
            loaderWrapper.setNoCache(mNoCache != null ? mNoCache: false);

            // in such order
            loaderWrapper.setProgress(mProgress);
            loaderWrapper.setBaseViewModel(mBaseViewModel);

            return loaderWrapper;
        }

        @Override
        public final BaseLoaderWrapper<D> createBaseLoader() {
            return null;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected abstract BaseResponseLoaderWrapper<C, R, E, D> createBaseResponseLoaderWrapper(
                final ViewModelStore viewModelStore);

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected void setType(@NonNull final BaseResponseLoaderWrapper<C, R, E, D> wrapper,
                               final Type type) {
            if (mType != null) {
                CoreLogger.log("setType: mType " + mType);
                wrapper.setType(mType);

                if (!mType.equals(type))
                    CoreLogger.logWarning("setType: type " + type + " will be ignored");
                return;
            }

            CoreLogger.log("setType: type " + type);
            if (type != null) {
                wrapper.setType(type);
                mType = type;
            }

            if (wrapper.getType() == null)
                CoreLogger.logWarning("can not detect data type, please consider to set it " +
                        "via setType() method call");
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public Type getTypeRaw() {
            return mType;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public Boolean getNoCacheRaw() {
            return mNoCache;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public LoaderCallbacks<E, D> getLoaderCallbacksRaw() {
            return mLoaderCallbacks;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public Callable<Progress> getProgressRaw() {
            return mProgress;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public Callable<BaseViewModel<BaseResponse<R, E, D>>> getBaseViewModelRaw() {
            return mBaseViewModel;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public Converter<D> getConverterRaw() {
            return mConverter;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public String getTableNameRaw() {
            return mTableName;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public String getLoaderIdRaw() {
            return mLoaderId;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public UriResolver getUriResolverRaw() {
            return mUriResolver;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public String getDescriptionRaw() {
            return mDescription;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public Requester<C> getRequesterRaw() {
            return mRequester;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected Requester<C> getRequester() {
            return mRequester != null ? mRequester: getDefaultRequester();
        }

        /**
         * Please refer to the base method description.
         */
        public Requester<C> getDefaultRequester() {
            CoreLogger.logError("the default requester is not defined");
            return null;
        }

        @NonNull
        public Converter<D> getConverter() {
            if (mConverter == null) mConverter = new BaseConverter<>();
            return mConverter;
        }

        @NonNull
        protected UriResolver getUriResolver() {
            if (mUriResolver == null) mUriResolver = Utils.getUriResolver();
            return mUriResolver;
        }

        @SuppressWarnings("unused")
        protected boolean isLoaderIdAutoGenerated() {
            return mLoaderId == null;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected String getTableName(final Method method) {
            String tableName = getTableNameRaw();
            if (TextUtils.isEmpty(tableName)) tableName = createTableName(method);
            return tableName;
        }

        @SuppressWarnings("WeakerAccess")
        public String getTableName() {
            if (TextUtils.isEmpty(mTableName)) CoreLogger.logError("empty table name");
            return mTableName;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected String createTableName(final Method method) {
            if (!TextUtils.isEmpty(mTableName)) return mTableName;

            if (method != null) {
                String name = method.getDeclaringClass().getName() + "." + method.getName();

                final Class<?>[] params = method.getParameterTypes();
                if (params.length > 0) {
                    String tmp = Arrays.deepToString(params);

                    if (tmp.startsWith("[") && tmp.endsWith("]"))
                        tmp = tmp.substring(1, tmp.length() - 1);

                    name += "_" + tmp.replace(", ", " ");
                }
                setTableName(name);
            }
            else
                CoreLogger.logError("method == null");

            return getTableName();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static abstract class BaseResponseLoaderExtendedBuilder<C, R, E, D, T> extends BaseResponseLoaderBuilder<C, R, E, D> {

        @SuppressWarnings("unused")
        public BaseResponseLoaderExtendedBuilder() {
        }

        @SuppressWarnings("unused")
        public BaseResponseLoaderExtendedBuilder(final Fragment fragment) {
            super(fragment);
        }

        @SuppressWarnings("unused")
        public BaseResponseLoaderExtendedBuilder(final Activity activity) {
            super(activity);
        }

        @SuppressWarnings("unused")
        public BaseResponseLoaderExtendedBuilder(final ViewModelStore viewModelStore) {
            super(viewModelStore);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        protected static abstract class RequesterHelper<C, T> {

            protected       Method                              mMethod;
            private   final Type                                mTypeResponse;

            @SuppressWarnings("WeakerAccess")
            public RequesterHelper(final Type type) {
                mTypeResponse = type;
            }

            protected abstract void init();

            protected abstract void request(C callback) throws Exception;

            private void requestWrapper(C callback) throws Exception {
                logMethod();
                if (mMethod == null) throw new RuntimeException("method == null");
                request(callback);
            }

            private void logMethod() {
                CoreLogger.log(mMethod == null ? Level.ERROR: CoreLogger.getDefaultLevel(), "for type " +
                        (mTypeResponse == null ? "null": mTypeResponse instanceof Class ?
                        ((Class) mTypeResponse).getName(): mTypeResponse) + " method == " + mMethod);
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected Requester<C> getRequester(@NonNull final RequesterHelper<C, T> requesterHelper) {

            requesterHelper.init();

            //noinspection Convert2Lambda
            return new Requester<C>() {
                @SuppressWarnings("unused")
                @Override
                public void makeRequest(@NonNull final C callback) {
                    try {
                        requesterHelper.requestWrapper(callback);
                    }
                    catch (Throwable throwable) {
                        CoreLogger.log("makeRequest failed", throwable);
                        throw throwable instanceof RuntimeException ?
                                (RuntimeException) throwable: new RuntimeException(throwable);
                    }
                }
            };
        }
    }

    /**
     * The API to create new {@code BaseResponseLoaderWrapper} (or {@code BaseLoaderWrapper})
     * instances.
     * <p>First, {@link #createBaseResponseLoader} will be called. In case of returned null,
     * the result of calling {@link #createBaseLoader} will be used.
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
     *        The type of data to load
     */
    @SuppressWarnings("WeakerAccess")
    public interface LoaderBuilder<C, R, E, D> {

        /**
         * Returns a new {@code BaseLoaderWrapper} instance (or null). Called only if
         * call to {@link #createBaseResponseLoader} returns null.
         *
         * @return  The {@code BaseLoaderWrapper} instance
         */
        @SuppressWarnings("SameReturnValue")
        BaseLoaderWrapper<D> createBaseLoader();

        /**
         * Returns a new {@code BaseResponseLoaderWrapper} instance (or null).
         *
         * @return  The {@code BaseResponseLoaderWrapper} instance
         */
        BaseResponseLoaderWrapper<C, R, E, D> createBaseResponseLoader();

        /**
         * Returns default {@code Requester} (if any).
         *
         * @return  The default {@code Requester}
         */
        @SuppressWarnings("unused")
        Requester<C> getDefaultRequester();
    }

    /**
     * The <code>CoreLoad</code> component is responsible for data loading. Usage example:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import com.yourpackage.model.YourData;
     * import com.yourpackage.retrofit.YourRetrofit;
     *
     * import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
     * import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
     * import akha.yakhont.technology.retrofit.Retrofit2;
     * import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
     * import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
     *
     * public class YourFragment extends Fragment {
     *
     *     &#064;Override
     *     public void onActivityCreated(Bundle savedInstanceState) {
     *         super.onActivityCreated(savedInstanceState);
     *
     *         // SwipeRefreshLayout handling (optional)
     *         SwipeRefreshWrapper.register(this, R.id.swipeContainer);
     *
     *         // optional Rx component
     *         Retrofit2Rx&lt;YourData[]&gt; rx = new Retrofit2Rx&lt;&gt;();
     *
     *         CoreLoad coreLoad = new Retrofit2CoreLoadBuilder&lt;&gt;(this, getRetrofit()) {
     *
     *             .setRequester(YourRetrofit::yourMethod)
     *             .setDataBinding(BR.yourDataBindingId)    // use Data Binding Library
     *
     *             // or - reflection-based data binding
     *             //.setDataBinding(new String[] {"name",    "age"   },
     *             //                new int   [] {R.id.name, R.id.age})
     *
     *             // 3 methods below are optional too
     *             .setListView(R.id.list_view)       // recycler view / list / grid ID
     *             .setListItem(R.layout.list_item)   // view item layout
     *
     *             .setRx(rx)
     *
     *             .create();
     *
     *         coreLoad.load();
     *     }
     *
     *     private Retrofit2&lt;Retrofit2Api, YourData[]&gt; getRetrofit() {
     *         // something like this
     *         return new Retrofit2&lt;YourRetrofit, YourData[]&gt;().init(
     *             YourRetrofit.class, "http://...");
     *     }
     * }
     * </pre>
     *
     * Here the item layout XML (R.layout.list_item) may looks as follows:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * &lt;LinearLayout
     *     ... &gt;
     *
     *     &lt;TextView
     *         android:id="&#064;+id/name" ... /&gt;
     *
     *     &lt;TextView
     *         android:id="&#064;+id/age" ... /&gt;
     *
     * &lt;/LinearLayout&gt;
     * </pre>
     *
     * And the JSON:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * [
     *     {
     *         "name": "John",
     *         "age": 25
     *     },
     *     {
     *         "name": "Bob",
     *         "age": 32
     *     },
     *     ...
     * ]
     * </pre>
     *
     * @see BaseLoaderWrapper
     * @see akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper
     * @see akha.yakhont.technology.retrofit.Retrofit2
     * @see akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx
     */
    public interface CoreLoad {

        /**
         * Returns the collection of {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         *
         * @return  The loaders
         */
        Collection<BaseLoaderWrapper<?>> getLoaders();

        /**
         * Adds loader to the collection of {@link BaseLoaderWrapper loaders} associated with
         * the given {@code CoreLoad} component.
         *
         * @param loader
         *        The loader to add
         *
         * @param replace
         *        If {@code true}, forces to replace loader (if it already exists in the given collection)
         *
         * @return  {@code true} if the loader was successfully added, {@code false} otherwise
         */
        boolean addLoader(BaseLoaderWrapper<?> loader, boolean replace);

        /**
         * Starts all loaders associated with the given {@code CoreLoad} component.
         *
         * @return  {@code true} if data loading was successfully started, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        boolean load();

        /**
         * Starts all loaders associated with the given {@code CoreLoad} component.
         *
         * @param activity
         *        The Activity
         *
         * @param parameters
         *        The LoadParameters
         *
         * @return  {@code true} if data loading was successfully started, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        boolean load(Activity activity, LoadParameters parameters);

        /**
         * Cancels all {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         *
         * @param activity
         *        The Activity
         *
         * @return  This {@code CoreLoad} object to allow for chaining of calls
         */
        @SuppressWarnings({"UnusedReturnValue", "unused"})
        CoreLoad cancelLoading(Activity activity);

        /**
         * Indicates whether the back key press should be emulated if data loading was cancelled or not
         *
         * @param isGoBackOnCancelLoading
         *        The value to set
         *
         * @return  This {@code CoreLoad} object to allow for chaining of calls
         */
        @SuppressWarnings({"UnusedReturnValue", "unused"})
        CoreLoad setGoBackOnCancelLoading(final boolean isGoBackOnCancelLoading);
    }

    @SuppressWarnings("unused")
    public static class CoreLoader implements CoreLoad {

        private final   Collection<BaseLoaderWrapper<?>>        mLoaders                = Utils.newSet();

        private final   AtomicBoolean                           mGoBackOnCancelLoading  = new AtomicBoolean(true);

        @Override
        public Collection<BaseLoaderWrapper<?>> getLoaders() {
            return mLoaders;
        }

        @Override
        public boolean addLoader(final BaseLoaderWrapper<?> loader, final boolean replace) {
            if (loader == null) {
                CoreLogger.logWarning("loader == null");
                return false;
            }

            final BaseLoaderWrapper foundLoader = loader.findLoader(mLoaders);
            if (foundLoader != null)
                if (replace) {
                    CoreLogger.logWarning("existing loader will be replaced: " + foundLoader);
                    mLoaders.remove(foundLoader);
                }
                else {
                    CoreLogger.logError("loader already exist: " + foundLoader);
                    return false;
                }

            final boolean result = mLoaders.add(loader);
            CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.ERROR, "mLoaders.add result == " + result);
            return result;
        }

        @Override
        public CoreLoad cancelLoading(final Activity activity) {
            CoreLogger.logWarning("about to cancel loading");

            for (final BaseLoaderWrapper baseLoaderWrapper: mLoaders)
                baseLoaderWrapper.cancelRequest(Level.WARNING);

            if (!isGoBackOnCancelLoading()) return this;
            CoreLogger.logWarning("isGoBackOnLoadingCanceled: about to call Activity.onBackPressed()");

            //noinspection Convert2Lambda
            Utils.postToMainLoop(new Runnable() {
                    @Override
                    public void run() {
                        if (activity != null) activity.onBackPressed();
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "Activity.onBackPressed()";
                    }
                });
            return this;
        }

        private boolean isGoBackOnCancelLoading() {
            return mGoBackOnCancelLoading.get();
        }

        @Override
        public CoreLoad setGoBackOnCancelLoading(final boolean isGoBackOnCancelLoading) {
            mGoBackOnCancelLoading.set(isGoBackOnCancelLoading);
            return this;
        }

        @Override
        public boolean load() {
            return load(null, new LoadParameters());
        }

        @Override
        public boolean load(final Activity activity, final LoadParameters parameters) {
            return BaseLoaderWrapper.start(activity, getLoaders(), parameters);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link CoreLoad} objects.
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
     */
    public static class CoreLoadBuilder<C, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderRx<R, E, D>                     mRx;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderBuilder<C, R, E, D>             mLoaderBuilder;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected BaseCacheAdapterWrapper<?, R, E, D>   mAdapterWrapper;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ViewBinder                            mViewBinder;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected ViewHolderCreator                     mViewHolderCreator;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @IdRes
        protected int                                   mListViewId     = Core.NOT_VALID_VIEW_ID;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @LayoutRes
        protected int                                   mLayoutItemId   = Core.NOT_VALID_RES_ID;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected boolean                               mNoBinding;

        @SuppressWarnings("WeakerAccess")
        public CoreLoadBuilder() {
        }

        /**
         * Sets the Rx component.
         *
         * @param rx
         *        The Rx component
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setRx(final LoaderRx<R, E, D> rx) {
            checkData(mRx, rx, "Rx");
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
        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        public CoreLoadBuilder<C, R, E, D> setLoaderBuilder(final LoaderBuilder<C, R, E, D> loaderBuilder) {
            checkData(mLoaderBuilder, loaderBuilder, "loader builder");
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
        public <S> CoreLoadBuilder<C, R, E, D> setAdapterWrapper(
                final BaseCacheAdapter.BaseCacheAdapterWrapper<S, R, E, D> adapterWrapper) {
            checkData(mAdapterWrapper, adapterWrapper, "adapter wrapper");
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
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setViewBinder(final BaseCacheAdapter.ViewBinder viewBinder) {
            checkData(mViewBinder, viewBinder, "ViewBinder");
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
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setViewHolderCreator(final BaseRecyclerViewAdapter.ViewHolderCreator viewHolderCreator) {
            checkData(mViewHolderCreator, viewHolderCreator, "ViewHolder creator");
            mViewHolderCreator = viewHolderCreator;
            return this;
        }

        /**
         * Sets the {@link ListView}, {@link GridView} or {@link RecyclerView} ID (for data binding).
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
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setListView(@IdRes final int listViewId) {
            if (mListViewId != Core.NOT_VALID_VIEW_ID)
                CoreLogger.log(Level.ERROR, "The list view id is already set");
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
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setListItem(@LayoutRes final int layoutItemId) {
            if (mLayoutItemId != Core.NOT_VALID_RES_ID)
                CoreLogger.log(Level.ERROR, "The layout item id is already set");
            mLayoutItemId = layoutItemId;
            return this;
        }

        /**
         * Prevents component from binding loaded data (ignored in case of Data Binding Library).
         *
         * @param noBinding
         *        {@code true} to just load data, without any default binding (default value is {@code false})
         *
         * @return  This {@code CoreLoadBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public CoreLoadBuilder<C, R, E, D> setNoBinding(final boolean noBinding) {
            mNoBinding = noBinding;
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

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedParameters", "WeakerAccess"})
        protected void customizeAdapterWrapper(@NonNull final Activity activity,
                                               @NonNull final CoreLoad coreLoad, @NonNull final View root,
                                               @NonNull final View list, @LayoutRes final int item) {
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static Activity getActivity(final Activity activity) {
            return activity != null ? activity: Utils.getCurrentActivity();
        }

        @SuppressWarnings("WeakerAccess")
        protected CoreLoad create(Activity activity, final Fragment fragment) {

            activity = getActivity(activity);

            if (mLoaderBuilder == null) {
                CoreLogger.logError("The loader builder is null");
                return null;
            }

            final CoreLoad coreLoad = new BaseResponseLoaderWrapper.CoreLoader();

            if (mNoBinding) {
                final String errText = "'no default binding' mode set, so %s will be ignored";

                if (mListViewId     != Core.NOT_VALID_VIEW_ID)
                    CoreLogger.logWarning(String.format(errText, "list ID " +
                            CoreLogger.getResourceDescription(mListViewId)));

                if (mLayoutItemId   != Core.NOT_VALID_RES_ID)
                    CoreLogger.logWarning(String.format(errText, "list item layout ID " +
                            CoreLogger.getResourceDescription(mLayoutItemId)));

                if (mAdapterWrapper != null) {
                    CoreLogger.logWarning(String.format(errText, "adapter wrapper " +
                            mAdapterWrapper.getClass().getName()));

                    mAdapterWrapper = null;
                }
            }
            else
                if (!create(activity, fragment, coreLoad)) return null;

            final boolean result = coreLoad.addLoader(getLoader(), true);
            CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.ERROR, "add loader result == " + result);

            return coreLoad;
        }

        private BaseLoaderWrapper<?> getLoader() {
            final BaseResponseLoaderWrapper<C, R, E, D> loader = mLoaderBuilder.createBaseResponseLoader();
            if (loader == null) return mLoaderBuilder.createBaseLoader();

            if (mRx             != null) loader.setRx(mRx);
            if (mAdapterWrapper != null) loader.setAdapter(mAdapterWrapper);
            return loader;
        }

        private boolean create(@NonNull final Activity activity, final Fragment fragment,
                               @NonNull final CoreLoad coreLoad) {
            View list = null;

            View root = fragment == null ? null: fragment.getView();
            if (root == null) root = Utils.getDefaultView(activity);

            if (root == null)
                CoreLogger.logWarning("The fragment's root view is null");
            else
                list = mListViewId == Core.NOT_VALID_VIEW_ID ?
                        BaseCacheAdapter.findListView(root): root.findViewById(mListViewId);

            if (list == null)
                if (mListViewId != Core.NOT_VALID_VIEW_ID)
                    CoreLogger.logError("view with id " +
                            CoreLogger.getResourceDescription(mListViewId) + " was not found");
                else
                    CoreLogger.logWarning("no ListView, GridView or RecyclerView found for default binding");

            @LayoutRes int itemId = mLayoutItemId;
            if (itemId == Core.NOT_VALID_RES_ID && list != null)
                itemId = getItemLayout(activity.getResources(), list, "layout", activity.getPackageName());

            if (itemId == Core.NOT_VALID_RES_ID)
                CoreLogger.logWarning("no list item layout ID found for default binding");
            else
                CoreLogger.log("list item layout ID: " + CoreLogger.getResourceDescription(itemId));

            if (list != null && itemId != Core.NOT_VALID_RES_ID)
                customizeAdapterWrapper(activity, coreLoad, root, list, itemId);

            if (mAdapterWrapper == null)
                CoreLogger.logWarning("The adapter wrapper is null, so no data binding will be done");
            else {
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
                    CoreLogger.logError("view with id " + CoreLogger.getResourceDescription(mListViewId) +
                            " should be instance of ListView, GridView or RecyclerView");
                    return false;
                }
            }
            return true;
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
    public static abstract class CoreLoadExtendedBuilder<C, R, E, D, T>
            extends CoreLoadBuilder<C, R, E, D> implements Requester<C> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Type                                  mType;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderCallbacks<E, D>                 mLoaderCallbacks;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Callable<Progress>                    mProgress;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Callable<BaseViewModel<BaseResponse<R, E, D>>>
                                                        mBaseViewModel;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Integer                               mDataBindingId;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @Size(min = 1)
        protected String[]                              mFrom;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @Size(min = 1)
        protected int[]                                 mTo;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Boolean                               mNoCache;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected String                                mTableName;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected String                                mDescription;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @StringRes
        protected Integer                               mDescriptionId  = Core.NOT_VALID_RES_ID;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Requester<T>                          mRequester;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Requester<C>                          mDefaultRequester;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final Object                          mLockRequester  = new Object();

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Converter<D>                          mConverter;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected String                                mLoaderId;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Boolean                               mSupport;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected Boolean                               mSupportCursorAdapter;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected UriResolver                           mUriResolver;

        protected CoreLoadExtendedBuilder() {
        }

        @SuppressWarnings("unused")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setNoCache(final boolean noCache) {
            mNoCache         = noCache;
            return this;
        }

        @SuppressWarnings("WeakerAccess")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderCallbacks(final LoaderCallbacks<E, D> loaderCallbacks) {
            mLoaderCallbacks = loaderCallbacks;
            return this;
        }

        @SuppressWarnings("unused")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setProgress(final Progress progress) {
            //noinspection Convert2Lambda
            mProgress        = progress == null ? null: new Callable<Progress>() {
                @Override
                public Progress call() {
                    return progress;
                }
            };
            return this;
        }

        @SuppressWarnings("unused")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setProgress(final Callable<Progress> progress) {
            mProgress        = progress;
            return this;
        }

        @SuppressWarnings("unused")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setBaseViewModel(
                final Callable<BaseViewModel<BaseResponse<R, E, D>>> baseViewModel) {
            mBaseViewModel   = baseViewModel;
            return this;
        }

        @SuppressWarnings("unused")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderCallback(final LoaderCallback<D> loaderCallback) {
            return setLoaderCallbacks(loaderCallback == null ? null: new LoaderCallbacks<E, D>() {
                @Override
                public void onLoadFinished(final D data, final Source source) {
                    loaderCallback.onLoadFinished(data, source);
                }
            });
        }

        /**
         * Sets the data type; for collections ({@code List}, {@code Set}, {@code Queue})
         * please use {@code TypeToken} (e.g. from Gson). Usage examples:
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * import com.google.gson.reflect.TypeToken;
         *
         * setType(YourData[].class);
         *
         * setType(new TypeToken&lt;List&lt;YourData&gt;&gt;() {}.getType());
         * </pre>
         *
         * @param type
         *        The data type
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setType(final Type type) {
            checkData(mType, type, "type");
            mType = type;
            return this;
        }

        /**
         * Sets the data binding (for the Data Binding Library).
         *
         * @param id
         *        The BR id of the variable to be set
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         *
         * @see androidx.databinding.ViewDataBinding#setVariable ViewDataBinding.setVariable()
         */
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setDataBinding(@NonNull final Integer id) {
            checkData(mDataBindingId, id, "data binding id");
            mDataBindingId = id;
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
        @NonNull
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setDataBinding(@NonNull @Size(min = 1) final String[] from,
                                                                     @NonNull @Size(min = 1) final    int[] to) {
            checkData(mFrom, from, "from data binding");
            checkData(mTo  , to  , "to data binding");

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
            checkData(mDescription, description, "description");
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
            if (mDescriptionId != Core.NOT_VALID_RES_ID)
                CoreLogger.log(Level.ERROR, "The description id is already set");
            mDescriptionId = descriptionId;
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
            checkData(mTableName, tableName, "table name");
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
        public CoreLoadExtendedBuilder<C, R, E, D, T> setLoaderId(final String loaderId) {
            if (mLoaderId != null)
                CoreLogger.log(Level.ERROR, "The loader id is already set");
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
            checkData(mUriResolver, uriResolver, "URI resolver");
            mUriResolver = uriResolver;
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setRx(final LoaderRx<R, E, D> rx) {
            super.setRx(rx);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setViewBinder(final ViewBinder viewBinder) {
            super.setViewBinder(viewBinder);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setViewHolderCreator(final ViewHolderCreator viewHolderCreator) {
            super.setViewHolderCreator(viewHolderCreator);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setListView(final int listViewId) {
            super.setListView(listViewId);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setListItem(final int layoutItemId) {
            super.setListItem(layoutItemId);
            return this;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public CoreLoadExtendedBuilder<C, R, E, D, T> setNoBinding(final boolean noBinding) {
            super.setNoBinding(noBinding);
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
        public <S> CoreLoadExtendedBuilder<C, R, E, D, T> setAdapterWrapper(
                final BaseCacheAdapterWrapper<S, R, E, D> adapterWrapper) {
            super.setAdapterWrapper(adapterWrapper);
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
        public CoreLoadExtendedBuilder<C, R, E, D, T> setConverter(final Converter<D> converter) {
            checkData(mConverter, converter, "converter");
            mConverter = converter;
            return this;
        }

        @SuppressWarnings("unused")
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setSupportMode(final boolean support) {
            checkData(mSupport, support, "support");
            mSupport = support;
            return this;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        @NonNull
        public CoreLoadExtendedBuilder<C, R, E, D, T> setSupportCursorAdapterMode(final boolean support) {
            checkData(mSupportCursorAdapter, support, "support CursorAdapter");
            mSupportCursorAdapter = support;
            return this;
        }

        /**
         * Sets the requester (most of the time - just some method from your Retrofit API).
         * Usage examples:
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * import com.yourpackage.retrofit.YourRetrofit;
         *
         * import akha.yakhont.Core;
         *
         * // for methods without parameters
         * setRequester(YourRetrofit::yourMethod);
         *
         * // for methods with parameters
         * setRequester(yourRetrofit -&gt; yourRetrofit.yourMethod("your parameter"));
         *
         * // for methods with parameters (Java 7 style)
         * setRequester(new Core.Requester&lt;YourRetrofit&gt;() {
         *     &#064;Override
         *     public void makeRequest(YourRetrofit yourRetrofit) {
         *         yourRetrofit.yourMethod("your parameter");
         *     }
         * });
         * </pre>
         *
         * @param requester
         *        The requester
         *
         * @return  This {@code CoreLoadExtendedBuilder} object to allow for chaining of calls to set methods
         *
         * @see     #makeRequest(Object)
         */
        @SuppressWarnings("unused")
        public CoreLoadExtendedBuilder<C, R, E, D, T> setRequester(final Requester<T> requester) {
            checkData(mRequester, requester, "requester");
            mRequester = requester;
            return this;
        }

        /**
         * Starts an asynchronous data loading. Usage examples ('raw calls' means - without default
         * Yakhont pre- and postprocessing):
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * import com.yourpackage.model.YourData;
         * import com.yourpackage.retrofit.YourRetrofit;
         *
         * import akha.yakhont.technology.rx.Rx;
         * import akha.yakhont.technology.rx.Rx2;
         *
         * // for typical Retrofit2 (Rx2 / Rx / Call) - but for such simple Retrofit2 calls
         * //   it's better to use 'setRequester(YourRetrofit::getDataRx)'
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getApi(callback).getDataRx();
         * }
         *
         * // for raw Retrofit2 Call ('getApi()' takes null)
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getApi(null).getData("your parameter").enqueue(callback);
         * }
         *
         * // for raw Retrofit2 Rx2  ('getApi()' takes null)
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getRx2DisposableHandler().add(Rx2.handle(
         *         getApi(null).getDataRx(), getRxWrapper(callback)));
         * }
         *
         * // for raw Retrofit2 Rx   ('getApi()' takes null)
         * &#064;Override
         * public void makeRequest(&#064;NonNull Callback&lt;YourData[]&gt; callback) {
         *     getRxSubscriptionHandler().add(Rx.handle(
         *         getApi(null).getDataOldRx(), getRxWrapper(callback)));
         * }
         * </pre>
         *
         * Here the <code>YourRetrofit</code> may looks as follows:
         *
         * <p><pre style="background-color: silver; border: thin solid black;">
         * package com.yourpackage.retrofit;

         * import com.yourpackage.model.YourData;
         *
         * import io.reactivex.Observable;
         *
         * import retrofit2.Call;
         * import retrofit2.http.GET;
         * import retrofit2.http.Query;
         *
         * public interface YourRetrofit {
         *
         *     &#064;GET("/data")  // Flowable, Maybe and Single works too
         *     Observable&lt;YourData[]&gt; getDataRx();
         *
         *     &#064;GET("/data")  // Single works too
         *     rx.Observable&lt;YourData[]&gt; getDataOldRx();
         *
         *     &#064;GET("/data")
         *     Call&lt;YourData[]&gt; getData(&#064;Query("parameter") String parameter);
         * }
         * </pre>
         *
         * Note: for raw calls you should set cache table name and data type
         * (see {@link #setTableName} and {@link #setType}).
         *
         * @param callback
         *        The callback
         *
         * @see     #setRequester
         * @see     Requester#makeRequest(Object)
         */
        @Override
        public void makeRequest(@NonNull final C callback) {
            if (mRequester != null) {
                mRequester.makeRequest(getApi(callback));
                return;
            }
            synchronized (mLockRequester) {
                if (mDefaultRequester == null) mDefaultRequester = mLoaderBuilder.getDefaultRequester();
            }
            if (mDefaultRequester == null)
                CoreLogger.logError("The default requester is null, callback " + callback);
            else
                mDefaultRequester.makeRequest(callback);
        }

        /**
         * Returns the API defined by the service interface (e.g. the Retrofit API).
         *
         * <p>Note: 'raw calls' means - without default Yakhont pre- and postprocessing.
         *
         * @param callback
         *        The loader's callback (or null for raw Retrofit calls)
         *
         * @return  The API
         *
         * @see BaseRetrofit#getApi(Object) BaseRetrofit.getApi()
         */
        @SuppressWarnings("unused")
        public abstract T getApi(final C callback);

        private static String toString(final Object[] data) {
            return Arrays.deepToString(data);
        }

        private static String toString(final int[] data) {
            return Arrays.toString(data);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedParameters"})
        @Override
        protected void customizeAdapterWrapper(@NonNull final Activity activity,
                                               @NonNull final CoreLoad coreLoad, @NonNull final View root,
                                               @NonNull final View list, @LayoutRes final int item) {
            if (mFrom != null || mTo != null)
                if (mDataBindingId != null)
                    CoreLogger.logError("DataBinding id == " + mDataBindingId + ", 'from' " +
                            toString(mFrom) + " and 'to' " + toString(mTo) + " binding data will be ignored");
                else if (mFrom == null || mTo == null)
                    CoreLogger.logError("both 'from' " + toString(mFrom) +
                            " and 'to' " + toString(mTo) + " binding data should be defined");
                else if (mFrom.length != mTo.length)
                    CoreLogger.logError("both 'from' " + toString(mFrom) +
                            " and 'to' " + toString(mTo) + " binding data should has same size");

            final boolean support              = mSupport              == null ? false: mSupport;
            final boolean supportCursorAdapter = mSupportCursorAdapter == null ?  true: mSupportCursorAdapter;

            setAdapterWrapperHelper(mDataBindingId != null ?
                    new DataBindingCacheAdapterWrapper(mDataBindingId, activity, item, support):
                                    mFrom          == null ?
                    new ValuesCacheAdapterWrapper<>(activity, item,                    support, supportCursorAdapter) :
                    new ValuesCacheAdapterWrapper<>(activity, item, mFrom, mTo,        support, supportCursorAdapter));
        }

        // compiler issue
        private void setAdapterWrapperHelper(
                @NonNull final BaseCacheAdapterWrapper<?, R, E, D> adapterWrapper) {
            super.setAdapterWrapper(adapterWrapper);
        }

        @SuppressWarnings("unused")
        public CoreLoad create() {
            return super.create(null, null);
        }

        @SuppressWarnings("unused")
        public CoreLoad create(@NonNull final Activity activity) {
            return super.create(activity, null);
        }

        @SuppressWarnings("unused")
        public CoreLoad create(@NonNull final Fragment fragment) {
            return super.create(fragment.getActivity(), fragment);
        }

        protected CoreLoad create(BaseResponseLoaderBuilder<C, R, E, D> builder) {
            return create(null, null, builder);
        }

        @SuppressWarnings("unused")
        protected CoreLoad create(@NonNull final Activity activity, BaseResponseLoaderBuilder<C, R, E, D> builder) {
            return create(activity, null, builder);
        }

        @SuppressWarnings("unused")
        protected CoreLoad create(@NonNull final Fragment fragment, BaseResponseLoaderBuilder<C, R, E, D> builder) {
            return create(fragment.getActivity(), fragment, builder);
        }

        private CoreLoad create(Activity activity, final Fragment fragment,
                                BaseResponseLoaderBuilder<C, R, E, D> builder) {

            activity = getActivity(activity);

            if (mDataBindingId != null) {
                if (mNoBinding) {
                    CoreLogger.logError("The Data Binding Library will be used, " +
                            "'NoBinding' parameter will be ignored");
                    mNoBinding = false;
                }
                if (mViewBinder != null)
                    CoreLogger.logError("The Data Binding Library will be used, " +
                            "'ViewBinder' parameter will be ignored, please use @BindingAdapter");
            }

            if (builder == null) {
                if (mLoaderBuilder != null) {
                    if (mLoaderBuilder instanceof BaseResponseLoaderBuilder)
                        builder = (BaseResponseLoaderBuilder<C, R, E, D>) mLoaderBuilder;
                    else
                        return super.create(activity, fragment);
                }
                else {
                    CoreLogger.logError("The loader builder is not defined");
                    return null;
                }
            }
            else {
                if (mLoaderBuilder != null && !mLoaderBuilder.equals(builder))
                    CoreLogger.logWarning("The already set loader builder will be ignored: " + mLoaderBuilder);
                if (!builder.equals(mLoaderBuilder)) setLoaderBuilder(builder);
            }

            if (mDescriptionId != Core.NOT_VALID_RES_ID && mDescription != null)
                CoreLogger.logWarning("Both description and description ID were set; description ID will be ignored");

            if (builder.getRequesterRaw() == null)
                builder.setRequester(this);
            else
                CoreLogger.logWarning("The loader builder requester is already set, " +
                        "so overridden method (if any) 'makeRequest(callback)' will be ignored");

            if (check(mType           , builder.getTypeRaw()           , "Type"          ))
                builder.setType           (mType           );
            if (check(mNoCache        , builder.getNoCacheRaw()        , "NoCache"       ))
                builder.setNoCache        (mNoCache        );
            if (check(mLoaderCallbacks, builder.getLoaderCallbacksRaw(), "LoaderCallback"))
                builder.setLoaderCallbacks(mLoaderCallbacks);
            if (check(mProgress       , builder.getProgressRaw()       , "Progress"      ))
                builder.setProgress       (mProgress       );
            if (check(mBaseViewModel  , builder.getBaseViewModelRaw()  , "BaseViewModel" ))
                builder.setBaseViewModel  (mBaseViewModel  );
            if (check(mConverter      , builder.getConverterRaw()      , "Converter"     ))
                builder.setConverter      (mConverter      );
            if (check(mTableName      , builder.getTableNameRaw()      , "TableName"     ))
                builder.setTableName      (mTableName      );
            if (check(mLoaderId       , builder.getLoaderIdRaw()       , "LoaderId"      ))
                builder.setLoaderId       (mLoaderId       );
            if (check(mUriResolver    , builder.getUriResolverRaw()    , "UriResolver"   ))
                builder.setUriResolver    (mUriResolver    );

            if (mDescription != null) {
                if (check(mDescription, builder.getDescriptionRaw()    , "Description"   ))
                    builder.setDescription(mDescription    );
            }
            else if (mDescriptionId != Core.NOT_VALID_RES_ID) {
                final String description = Objects.requireNonNull(Utils.getApplication())
                        .getString(mDescriptionId);
                if (check(description,  builder.getDescriptionRaw()    , "Description"   ))
                    builder.setDescription(description     );
            }

            final CoreLoad coreLoad = super.create(activity, fragment);
            if (mAdapterWrapper != null) mAdapterWrapper.setConverter(builder.getConverterRaw());
            return coreLoad;
        }

        private static <S> boolean check(final S valueOwn, final S valueBuilder, @NonNull final String txt) {
            if (valueOwn == null) return false;
            if (valueBuilder != null && !valueOwn.equals(valueBuilder)) {
                CoreLogger.logError(String.format("two different %s in CoreLoadExtendedBuilder and " +
                        "BaseResponseLoaderBuilder, the first one will be ignored (value %s), " +
                        "accepted value: %s", txt, valueOwn.toString(), valueOwn.toString()));
                return false;
            }
            return true;
        }
    }

    private static <S> void checkData(final S valueOwn, final S valueBuilder,
                                      @NonNull final String txt) {
        if (valueOwn != null)
            CoreLogger.log(valueBuilder == null ? Level.WARNING: Level.ERROR,
                    String.format("The %s is already set", txt));
    }
}
