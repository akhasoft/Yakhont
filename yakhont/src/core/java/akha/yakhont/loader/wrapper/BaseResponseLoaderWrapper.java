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

package akha.yakhont.loader.wrapper;

import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.Mergeable;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.LoaderCallback;
import akha.yakhont.loader.CacheLoader;
import akha.yakhont.loader.BaseConverter;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;

/**
 * Extends the {@link BaseLoaderWrapper} class to provide {@link CacheLoader} support. Most implementations should not use
 * <code>BaseResponseLoaderWrapper</code> directly, but instead utilise {@link akha.yakhont.technology.retrofit.RetrofitLoaderWrapper}
 * or {@link akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitLoaderBuilder}.
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
 *
 * @see CacheLoader
 * @see CoreLoad
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
public class BaseResponseLoaderWrapper<C, R, E, D> extends BaseLoaderWrapper<BaseResponse<R, E, D>> {

    // just a placeholder for the moment
    private static final int                                MAX_TABLE_NAME_LENGTH     = 1024;

    private final String                                    mTableName;
    private final String                                    mDescription;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final Requester<C>                            mRequester;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final Converter<D>                            mConverter;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final UriResolver                             mUriResolver;

    private       CacheAdapter<R, E, D>                     mAdapter;
    private       Loader<BaseResponse<R, E, D>>             mLoader;

    private       LoaderRx<R, E, D>                         mRx;

    /**
     * Initialises a newly created {@code BaseResponseLoaderWrapper} object.
     *
     * @param context
     *        The context
     *
     * @param fragment
     *        The fragment
     *
     * @param loaderId
     *        The loader ID
     *
     * @param requester
     *        The requester
     *
     * @param tableName
     *        The name of the table in the database (to cache the loaded data)
     *
     * @param description
     *        The data description
     *
     * @param converter
     *        The converter
     *
     * @param uriResolver
     *        The URI resolver
     */
    @SuppressWarnings("WeakerAccess")
    public BaseResponseLoaderWrapper(@NonNull final Context context,
                                     @NonNull final Fragment fragment, final Integer loaderId, @NonNull final Requester<C> requester,
                                     @NonNull final String tableName, final String description, @NonNull final Converter<D> converter,
                                     @NonNull final UriResolver uriResolver) {
        super(fragment, loaderId);

        mTableName      = adjustTableName(tableName);
        mDescription    = description;
        CoreLogger.log("assigned table name: " + mTableName);

        mRequester      = requester;
        mConverter      = converter;
        mUriResolver    = uriResolver;

        setLoaderFactory(new LoaderFactory<BaseResponse<R, E, D>>() {
            @NonNull
            @Override
            public Loader<BaseResponse<R, E, D>> getLoader(final boolean merge) {
                return new WrapperLoader<>(context, new WeakReference<>(getFragment()), mConverter, getLoaderId(),
                        mTableName, mDescription, merge, mAdapter, mRequester, mUriResolver);
            }
        });
    }

    private static String adjustTableName(@NonNull final String tableName) {
        if (tableName.trim().length() == 0) {
            CoreLogger.logError("empty table name");
            return "";
        }
        final String name = Utils.replaceSpecialChars(tableName);
        return name.length() > MAX_TABLE_NAME_LENGTH ? name.substring(MAX_TABLE_NAME_LENGTH - name.length(), name.length()): name;
    }

    /**
     * Returns the type of data to load.
     *
     * @return  The data type
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public Type getType() {
        return mConverter.getType();
    }

    /**
     * Sets adapter.
     *
     * @param adapter
     *        The adapter
     *
     * @return  This {@code BaseResponseLoaderWrapper} object
     */
    @SuppressWarnings("UnusedReturnValue")
    public BaseResponseLoaderWrapper<C, R, E, D> setAdapter(final CacheAdapter<R, E, D> adapter) {       // TODO: 22.11.2015 make sync
        mAdapter        = adapter;

        if (mLoader instanceof WrapperLoader) ((WrapperLoader) mLoader).setAdapter(getLoaderAdapter(adapter));
        if (mLoader != null && getResult() != null) updateAdapter(mLoader, getResult());

        return this;
    }

    /**
     * Sets Rx component.
     *
     * @param rx
     *        The Rx component
     *
     * @return  This {@code BaseLoaderWrapper} object
     */
    @SuppressWarnings("UnusedReturnValue")
    public BaseResponseLoaderWrapper<C, R, E, D> setRx(final LoaderRx<R, E, D> rx) {
        mRx             = rx;
        return this;
    }

    /**
     * Returns the Rx component.
     *
     * @return  The Rx component
     */
    public LoaderRx<R, E, D> getRx() {
        return mRx;
    }

    private static BaseCursorAdapter getLoaderAdapter(final CacheAdapter adapter) {
        return adapter == null ? null: adapter.getCursorAdapter();
    }

    /**
     * Returns the adapter.
     *
     * @return  The adapter
     */
    @SuppressWarnings("unused")
    public CacheAdapter<R, E, D> getAdapter() {
        return mAdapter;
    }

    /**
     * Returns the table name.
     *
     * @return  The table name
     */
    @SuppressWarnings("unused")
    public String getTableName() {
        return mTableName;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public BaseLoaderWrapper findLoader(final Collection<BaseLoaderWrapper> loaders) {
        if (loaders == null) return null;

        if (!isLoaderIdAutoGenerated()) return super.findLoader(loaders);

        BaseLoaderWrapper foundLoader = null;
        for (final BaseLoaderWrapper loader: loaders)
            if (loader instanceof BaseResponseLoaderWrapper && mTableName.equals(((BaseResponseLoaderWrapper) loader).mTableName)) {
                foundLoader = loader;
                break;
            }

        CoreLogger.log((foundLoader == null ? "not ": "") + "found loader, table name: " + mTableName);
        return foundLoader;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public Loader<BaseResponse<R, E, D>> onCreateLoader(int id, Bundle args) {
        mLoader = super.onCreateLoader(id, args);
        if (mLoader instanceof WrapperLoader) ((WrapperLoader) mLoader).setAdapter(getLoaderAdapter(mAdapter));
        return mLoader;
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onLoadFinished(Loader<BaseResponse<R, E, D>> loader, BaseResponse<R, E, D> data) {
        super.onLoadFinished(loader, data);

        updateAdapter(loader, data);

        if (mRx != null) mRx.onResult(data);
    }

    private void updateAdapter(final Loader<BaseResponse<R, E, D>> loader, final BaseResponse<R, E, D> data) {
        if (mAdapter != null)
            mAdapter.update(data, loader instanceof Mergeable && ((Mergeable) loader).isMerge());
        else
            CoreLogger.logWarning("adapter == null, table name: " + mTableName);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onLoaderReset(Loader<BaseResponse<R, E, D>> loader) {
        super.onLoaderReset(loader);

        if (mAdapter != null) mAdapter.resetArray();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class WrapperLoader<C, R, E, D> extends CacheLoader<C, R, E, D> implements Mergeable {

        private final Requester<C>                          mRequester;
        private final boolean                               mMerge;

        @SuppressWarnings("unused")
        private WrapperLoader(@NonNull final Context context,
                              @NonNull final WeakReference<Fragment> fragment, @NonNull final Converter<D> converter,
                              final int loaderId, @NonNull final String tableName, final String description, final boolean merge,
                              final CacheAdapter<R, E, D> adapter, @NonNull final Requester<C> requester,
                              @NonNull final UriResolver uriResolver) {
            super(context, fragment, converter, loaderId, tableName, description, getLoaderAdapter(adapter), uriResolver);

            setMerge(merge);

            mRequester  = requester;
            mMerge      = merge;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public boolean isMerge() {
            return mMerge;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        protected void makeRequest(@NonNull final C callback) {
            mRequester.makeRequest(callback);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link BaseResponseLoaderWrapper} objects. Provides a convenient way to set the various fields of
     * a {@link BaseResponseLoaderWrapper}.
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
    public static class BaseResponseLoaderBuilder<C, R, E, D> implements LoaderBuilder<BaseResponse<R, E, D>> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final WeakReference<Fragment>                                   mUiFragment;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final Requester<C>                                              mRequester;

        private         String                                                    mTableName;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       String                                                    mDescription;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Integer                                                   mLoaderId;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Type                                                      mType;
        private         Converter<D>                                              mConverter;
        private         UriResolver                                               mUriResolver;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       LoaderManager.LoaderCallbacks<BaseResponse<R, E, D>>      mLoaderCallbacks;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       LoaderFactory<BaseResponse                <R, E, D>>      mLoaderFactory;

        /**
         * Initialises a newly created {@code BaseResponseLoaderBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param requester
         *        The requester
         */
        public BaseResponseLoaderBuilder(@NonNull final Fragment fragment, @NonNull final Requester<C> requester) {
            mUiFragment         = new WeakReference<>(fragment);
            mRequester          = requester;
        }

        /**
         * Sets the table name.
         *
         * @param context
         *        The context
         *
         * @param tableNameId
         *        The resource ID of the table name in the database
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public BaseResponseLoaderBuilder<C, R, E, D> setTableName(@NonNull final Context context,
                                                                  @StringRes final int tableNameId) {
            return setTableName(context.getString(tableNameId));
        }

        /**
         * Sets the table name.
         *
         * @param tableName
         *        The name of the table in the database (to cache the loaded data)
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setTableName(@NonNull final String tableName) {
            mTableName          = adjustTableName(tableName);
            return this;
        }

        /**
         * Sets the loader ID.
         *
         * @param loaderId
         *        The loader ID
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public BaseResponseLoaderBuilder<C, R, E, D> setLoaderId(final int loaderId) {
            mLoaderId           = loaderId;
            return this;
        }

        /**
         * Sets the data description.
         *
         * @param description
         *        The data description
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setDescription(@NonNull final String description) {
            mDescription        = description;
            return this;
        }

        /**
         * Sets the data description.
         *
         * @param context
         *        The context
         *
         * @param descriptionId
         *        The resource ID of the data description
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public BaseResponseLoaderBuilder<C, R, E, D> setDescription(@NonNull final Context context,
                                                                    @StringRes final int descriptionId) {
            return setDescription(context.getString(descriptionId));
        }

        /**
         * Sets the converter.
         *
         * @param converter
         *        The converter
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public BaseResponseLoaderBuilder<C, R, E, D> setConverter(@NonNull final Converter<D> converter) {
            mConverter          = converter;
            return this;
        }

        /**
         * Sets the URI resolver.
         *
         * @param uriResolver
         *        The URI resolver
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public BaseResponseLoaderBuilder<C, R, E, D> setUriResolver(@NonNull final UriResolver uriResolver) {
            mUriResolver        = uriResolver;
            return this;
        }

        /**
         * Sets the data type.
         *
         * @param type
         *        The type of data
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public BaseResponseLoaderBuilder<C, R, E, D> setType(@NonNull final Type type) {
            mType               = type;
            return this;
        }

        /**
         * Sets the loader factory.
         *
         * @param loaderFactory
         *        The loader factory
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public BaseResponseLoaderBuilder<C, R, E, D> setLoaderFactory(@NonNull final LoaderFactory<BaseResponse<R, E, D>> loaderFactory) {
            mLoaderFactory      = loaderFactory;
            return this;
        }

        /**
         * Sets the loader callbacks.
         *
         * @param loaderCallbacks
         *        The loader callbacks
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        public BaseResponseLoaderBuilder<C, R, E, D> setLoaderCallbacks(final LoaderManager.LoaderCallbacks<BaseResponse<R, E, D>> loaderCallbacks) {
            mLoaderCallbacks    = loaderCallbacks;
            return this;
        }

        /**
         * Sets the loader callback. Simplified version of the {@link #setLoaderCallbacks(LoaderManager.LoaderCallbacks)}.
         *
         * @param loaderCallback
         *        The loader callback (see {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished LoaderCallbacks.onLoadFinished()})
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public BaseResponseLoaderBuilder<C, R, E, D> setLoaderCallback(final LoaderCallback<D> loaderCallback) {
            return setLoaderCallbacks(loaderCallback == null ? null: new LoaderManager.LoaderCallbacks<BaseResponse<R, E, D>>() {
                @Override
                public Loader<BaseResponse<R, E, D>> onCreateLoader(int id, Bundle args) {
                    return null;
                }
                @Override
                public void onLoadFinished(Loader<BaseResponse<R, E, D>> loader, BaseResponse<R, E, D> data) {
                    if (data == null)
                        loaderCallback.onLoadFinished(null, null);
                    else
                        loaderCallback.onLoadFinished(data.getResult(), data.getSource());
                }
                @Override
                public void onLoaderReset(Loader<BaseResponse<R, E, D>> loader) {
                }
            });
        }

        /**
         * Creates a {@link BaseResponseLoaderWrapper} with the arguments supplied to this builder.
         *
         * @return  The newly created {@code BaseResponseLoaderWrapper} object
         */
        @NonNull
        @Override
        public final BaseResponseLoaderWrapper<C, R, E, D> create() {
            if (mType != null) setConverter(getConverter().setType(mType));
            if (getConverter().getType() == null) setConverter(getConverter().setType(getType()));

            final BaseResponseLoaderWrapper<C, R, E, D> loaderWrapper = createLoaderWrapper();

            if (mLoaderCallbacks != null) loaderWrapper.setLoaderCallbacks(mLoaderCallbacks);
            if (mLoaderFactory   != null) loaderWrapper.setLoaderFactory  (mLoaderFactory  );

            return loaderWrapper;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        protected BaseResponseLoaderWrapper<C, R, E, D> createLoaderWrapper() {
            return new BaseResponseLoaderWrapper<>(getContext(), getFragment(), mLoaderId,
                    mRequester, getTableName(), mDescription, getConverter(), getUriResolver());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected Context getContext() {
            final Fragment fragment = getFragment();
            return fragment == null ? null: fragment.getActivity();
        }

        /**
         * Returns the fragment.
         *
         * @return  The fragment
         */
        protected Fragment getFragment() {
            final Fragment fragment = mUiFragment.get();
            if (fragment == null)
                CoreLogger.logError("UI fragment == null");
            return fragment;
        }

        /**
         * Returns the converter.
         *
         * @return  The converter
         */
        @NonNull
        protected Converter<D> getConverter() {
            return mConverter != null ? mConverter: new BaseConverter<D>();
        }

        /**
         * Returns the URI resolver.
         *
         * @return  The URI resolver
         */
        @NonNull
        protected UriResolver getUriResolver() {
            return mUriResolver != null ? mUriResolver: Utils.getUriResolver();
        }

        /**
         * Indicates whether the loader ID was auto generated or not.
         *
         * @return  {@code true} if the loader ID was auto generated, {@code false} otherwise
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        protected boolean isLoaderIdAutoGenerated() {
            return mLoaderId == null;
        }

        /**
         * Returns the table name.
         *
         * @return  The table name
         */
        @SuppressWarnings("unused")
        public String getTableName() {
            if (TextUtils.isEmpty(mTableName)) setTableName(getTypeName());
            if (TextUtils.isEmpty(mTableName)) CoreLogger.logError("empty table name");
            return mTableName;
        }

        /**
         * Returns the type of data to load.
         *
         * @return  The data type
         */
        public Type getType() {
            return mType != null ? mType: mConverter != null ? mConverter.getType(): null;
        }

        private String getTypeName() {
            final Type type = getType();
            return type == null ? "": type instanceof Class ? ((Class) type).getName(): type.toString();
        }
    }

    /**
     * The <code>CoreLoad</code> component is responsible for data loading. Usage example:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.loader.BaseLoader;
     * import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
     * import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.Requester;
     * import akha.yakhont.technology.retrofit.Retrofit.RetrofitAdapterWrapper;
     * import akha.yakhont.technology.retrofit.Retrofit.RetrofitRx;
     * import akha.yakhont.technology.retrofit.RetrofitLoaderWrapper.RetrofitLoaderBuilder;
     *
     * import com.mypackage.model.MyData;
     *
     * import retrofit.Callback;
     *
     * public class MyFragment extends Fragment {
     *
     *     &#064;Override
     *     public void onActivityCreated(Bundle savedInstanceState) {
     *         super.onActivityCreated(savedInstanceState);
     *         ...
     *
     *         // SwipeRefreshLayout handling (optional)
     *         SwipeRefreshWrapper.register(this, R.id.swipeContainer);
     *
     *         // String[] and int[] has the same meaning as in SimpleCursorAdapter constructor:
     *         // data column names and view IDs
     *         RetrofitAdapterWrapper&lt;MyData[]&gt; adapter = new RetrofitAdapterWrapper&lt;&gt;(getActivity(), 
     *                 R.layout.list_item,
     *                 new String[] {"name",    "age"},
     *                 new int   [] {R.id.name, R.id.age});
     *
     *         ((ListView) getView().findViewById(R.id.list_view)).setAdapter(adapter.getAdapter());
     *
     *         RetrofitRx&lt;MyData[]&gt; rx = null;      // optional Rx component
     *
     *         CoreLoad coreLoad = BaseLoader.getCoreLoad(this);
     *
     *         coreLoad.addLoader(adapter, rx, new RetrofitLoaderBuilder&lt;&gt;(this,
     *                 new Requester&lt;Callback&lt;MyData[]&gt;&gt;() {
     *                     &#064;Override                                             // data loading request
     *                     public void makeRequest(Callback&lt;MyData[]&gt; callback) {
     *                         MyActivity.getRetrofit().getRetrofitApi().data(callback); // or something
     *                     }
     *                 }));
     *
     *         coreLoad.startLoading();
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
     * @see akha.yakhont.technology.retrofit.Retrofit
     * @see akha.yakhont.technology.retrofit.Retrofit.RetrofitRx
     */
    public interface CoreLoad {

        /**
         * Returns the collection of {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         *
         * @return  The loaders
         */
        Set<BaseLoaderWrapper> getLoaders();

        /**
         * Clears the collection of {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         */
        @SuppressWarnings("unused")
        void clearLoaders();

        /**
         * Destroys all {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         */
        @SuppressWarnings("unused")
        void destroyLoaders();

        /**
         * Cancels all {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         * @see LoaderManager#destroyLoader
         */
        void cancelLoaders();

        /**
         * Indicates whether the back key press should be emulated if data loading was cancelled or not
         *
         * @param isGoBackOnLoadingCanceled
         *        The value to set
         */
        @SuppressWarnings("SameParameterValue")
        void setGoBackOnLoadingCanceled(boolean isGoBackOnLoadingCanceled);

        /**
         * Adds loader to the collection of {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         * If such loader already exists, it replaced by the new one (if not busy with loading data - otherwise, the new loader ignored
         * and the existing one updated by the new adapter).
         *
         * @param adapter
         *        The adapter which added loader should use
         *
         * @param builder
         *        The loader builder (to build the loader to add)
         *
         * @return  The loader (added or modified)
         */
        @SuppressWarnings({"UnusedReturnValue", "unused"})
        BaseLoaderWrapper addLoader(CacheAdapter adapter, LoaderBuilder builder);

        /**
         * Adds loader to the collection of {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         * If such loader already exists, it replaced by the new one (if not busy with loading data - otherwise, the new loader ignored
         * and the existing one updated by the new adapter and Rx component).
         *
         * @param adapter
         *        The adapter which added loader should use
         *
         * @param rx
         *        The Rx component which added loader should use
         *
         * @param builder
         *        The loader builder (to build the loader to add)
         *
         * @return  The loader (added or modified)
         */
        @SuppressWarnings({"SameParameterValue", "unused"})
        BaseLoaderWrapper addLoader(CacheAdapter adapter, LoaderRx rx, LoaderBuilder builder);

        /**
         * Adds loader to the collection of {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         * If such loader already exists, it replaced by the new one (if not busy with loading data - otherwise, the new loader ignored
         * and the existing one updated by the new adapter).
         *
         * @param adapter
         *        The adapter which added loader should use
         *
         * @param loader
         *        The loader to add
         *
         * @return  The loader (added or modified)
         */
        @SuppressWarnings("unused")
        BaseLoaderWrapper addLoader(CacheAdapter adapter, BaseLoaderWrapper loader);

        /**
         * Adds loader to the collection of {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         * If such loader already exists, it replaced by the new one (if not busy with loading data - otherwise, the new loader ignored
         * and the existing one updated by the new adapter and Rx component).
         *
         * @param adapter
         *        The adapter which added loader should use
         *
         * @param rx
         *        The Rx component which added loader should use
         *
         * @param loader
         *        The loader to add
         *
         * @return  The loader (added or modified)
         */
        @SuppressWarnings("unused")
        BaseLoaderWrapper addLoader(CacheAdapter adapter, LoaderRx rx, BaseLoaderWrapper loader);

        /**
         * Starts all loaders associated with the given {@code CoreLoad} component.
         */
        @SuppressWarnings("unused")
        void startLoading();

        /**
         * Starts all loaders associated with the given {@code CoreLoad} component.
         *
         * @param forceCache
         *        {@code true} to force loading data from cache, {@code false} otherwise
         *
         * @param noProgress
         *        {@code true} to not display loading progress, {@code false} otherwise
         *
         * @param merge
         *        {@code true} to merge the newly loaded data with already existing, {@code false} otherwise
         *
         * @param sync
         *        {@code true} to load data synchronously, {@code false} otherwise
         */
        @SuppressWarnings({"SameParameterValue", "unused"})
        void startLoading(boolean forceCache, boolean noProgress, boolean merge, boolean sync);

        /**
         * Starts loader with the given ID.
         *
         * @param loaderId
         *        The loader's ID
         *
         * @param forceCache
         *        {@code true} to force loading data from cache, {@code false} otherwise
         *
         * @param noProgress
         *        {@code true} to not display loading progress, {@code false} otherwise
         *
         * @param merge
         *        {@code true} to merge the newly loaded data with already existing, {@code false} otherwise
         *
         * @param sync
         *        {@code true} to load data synchronously, {@code false} otherwise
         *
         * @return  {@code true} if loader was started successfully, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        BaseLoaderWrapper startLoading(int loaderId, boolean forceCache, boolean noProgress, boolean merge, boolean sync);

        /**
         * Displays a data loading progress indicator.
         *
         * @param text
         *        The text to display
         */
        void showProgress(String text);

        /**
         * Hides data loading progress indicator.
         *
         * @param force
         *        Indicates whether the progress hiding should be forced (e.g as result of {@link #cancelLoaders()}) or not
         */
        void hideProgress(boolean force);
    }
}
