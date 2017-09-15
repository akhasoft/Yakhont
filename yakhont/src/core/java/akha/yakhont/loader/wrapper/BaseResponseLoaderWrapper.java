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

import akha.yakhont.Core;
import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.TypeHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.Mergeable;
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.LoaderCallback;
import akha.yakhont.loader.CacheLoader;
import akha.yakhont.loader.BaseConverter;
import akha.yakhont.technology.rx.BaseRx.LoaderRx;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.support.annotation.StringRes;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
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

    /**
     * Clears all cache associated with given {@code CoreLoad} component.
     *
     * @param coreLoad
     *        The {@code CoreLoad} component
     */
    @SuppressWarnings("unused")
    public static void clearCache(final CoreLoad coreLoad) {
        if (coreLoad == null) {
            CoreLogger.logError("coreLoad == null");
            return;
        }
        final Application application = Utils.getApplication();

        for (final BaseLoaderWrapper baseLoaderWrapper: coreLoad.getLoaders())
            if (baseLoaderWrapper instanceof BaseResponseLoaderWrapper)
                BaseResponse.clearCache(application,
                        ((BaseResponseLoaderWrapper) baseLoaderWrapper).getTableName());
    }

    private static String adjustTableName(@NonNull String tableName) {
        if (tableName.trim().length() == 0) {
            CoreLogger.logError("empty table name");
            return "";
        }

        if (tableName.startsWith("[L") && tableName.endsWith(";"))
            tableName = tableName.substring(2, tableName.length() - 1);

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
    public BaseResponseLoaderWrapper<C, R, E, D> setAdapter(final CacheAdapter<R, E, D> adapter) {
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

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static <D> Converter<D> getDefaultConverter() {
        return new BaseConverter<>();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected static UriResolver getDefaultUriResolver() {
        return Utils.getUriResolver();
    }

    @SuppressWarnings("unchecked")
    private BaseLoader<C, R, E, D> getBaseLoader(@NonNull final Loader<BaseResponse<R, E, D>> loader) {
        return (BaseLoader<C, R, E, D>) loader;
    }

    // should be called from ctor only
    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected void setLoaderParameters(@NonNull @Size(value = 1) final BaseLoader<C, R, E, D>[] baseLoader,
                                       final int timeout, @NonNull final C callback) {
        final LoaderFactory<BaseResponse<R, E, D>> loaderFactory = geLoaderFactory();

        setLoaderFactory(new LoaderFactory<BaseResponse<R, E, D>>() {
            @NonNull
            @Override
            public Loader<BaseResponse<R, E, D>> getLoader(final boolean merge) {
                final Loader<BaseResponse<R, E, D>> loader = loaderFactory.getLoader(merge);

                if (loader instanceof BaseLoader) {
                    baseLoader[0] = getBaseLoader(loader);
                    baseLoader[0].setTimeout(timeout).setCallback(callback);
                }
                else
                    CoreLogger.logWarning("callback not set");

                return loader;
            }
        });
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
        protected       Requester<C>                                              mRequester;

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
         */
        @SuppressWarnings("unused")
        public BaseResponseLoaderBuilder(@NonNull final Fragment fragment) {
            mUiFragment         = new WeakReference<>(fragment);
        }

        /**
         * Sets the requester.
         *
         * @param requester
         *        The requester
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("UnusedReturnValue")
        public BaseResponseLoaderBuilder<C, R, E, D> setRequester(@NonNull final Requester<C> requester) {
            mRequester          = requester;
            return this;
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
        @SuppressWarnings({"unused", "UnusedReturnValue"})
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
        @SuppressWarnings({"unused", "UnusedReturnValue"})
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
        @SuppressWarnings({"unused", "UnusedReturnValue"})
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
                    getRequester(), getTableName(), mDescription, getConverter(), getUriResolver());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public Requester<C> getDefaultRequester() {
            CoreLogger.logError("the default requester is not defined");
            return null;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected Requester<C> getRequester() {
            return mRequester != null ? mRequester: getDefaultRequester();
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
            if (TextUtils.isEmpty(mTableName)) {
                final Type type = TypeHelper.getParameterizedOrGenericComponentType(getType());
                setTableName(type == null ? "": type instanceof Class ? ((Class) type).getName(): type.toString());
            }
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
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for {@link BaseResponseLoaderWrapper} objects. Provides a convenient way to set the various fields of
     * a {@link BaseResponseLoaderWrapper}. For the moment just contains some common code for Retrofit loaders builders.
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
    public static abstract class BaseResponseLoaderExtendedBuilder<C, R, E, D, T> extends BaseResponseLoaderBuilder<C, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Integer                                                   mTimeout;

        /**
         * Initialises a newly created {@code BaseResponseLoaderExtendedBuilder} object.
         *
         * @param fragment
         *        The fragment
         *
         * @param type
         *        The type of data
         */
        @SuppressWarnings("unused")
        public BaseResponseLoaderExtendedBuilder(@NonNull final Fragment fragment,
                                                 @NonNull final Type type) {
            super(fragment);
            setType(type);
        }

        /**
         * Sets the request timeout (in seconds).
         *
         * @param timeout
         *        The timeout
         *
         * @return  This {@code BaseResponseLoaderExtendedBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings("unused")
        public BaseResponseLoaderExtendedBuilder<C, R, E, D, T> setTimeout(@IntRange(from = 1) final int timeout) {
            mTimeout = timeout;
            return this;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected int getTimeout() {
            return mTimeout == null ? Core.TIMEOUT_CONNECTION: mTimeout;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected abstract Type getTypeHelper();

        @Override
        public Type getType() {
            if (mType == null) {
                final Type type = super.getType();
                if (type == null)
                    setType(getTypeHelper());
                else
                    mType = type;
            }
            return mType;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        protected static abstract class RequesterHelper<C, T> {

            protected       Method                                                mMethod;
            protected       T                                                     mHandler;
            private   final Type                                                  mTypeResponse;

            @SuppressWarnings({"unchecked", "WeakerAccess"})
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
                CoreLogger.log(mMethod == null ? Level.ERROR: Level.DEBUG, "for type " + (mTypeResponse == null ? "null":
                        mTypeResponse instanceof Class ? ((Class) mTypeResponse).getName(): mTypeResponse) + " method == " + mMethod);
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected Requester<C> getRequester(@NonNull final RequesterHelper<C, T> requesterHelper) {
            requesterHelper.init();

            return new Requester<C>() {
                @Override
                public void makeRequest(@NonNull final C callback) {
                    try {
                        requesterHelper.requestWrapper(callback);
                    }
                    catch (Throwable throwable) {
                        CoreLogger.log("makeRequest failed", throwable);
                        throw throwable instanceof RuntimeException ? (RuntimeException) throwable: new RuntimeException(throwable);
                    }
                }
            };
        }
    }

    /**
     * Extends the {@link BaseResponseLoaderWrapper} class. For the moment just contains some common code for Retrofit wrappers.
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
    public static abstract class BaseResponseLoaderExtendedWrapper<C, R, E, D> extends BaseResponseLoaderWrapper<C, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Type                                                      mType;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final Object                                                    mTypeLock   = new Object();

        /**
         * Initialises a newly created {@code BaseResponseLoaderExtendedWrapper} object.
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
        public BaseResponseLoaderExtendedWrapper(@NonNull final Context context,
                                                 @NonNull final Fragment fragment, final Integer loaderId, @NonNull final Requester<C> requester,
                                                 @NonNull final String tableName, final String description, @NonNull final Converter<D> converter,
                                                 @NonNull final UriResolver uriResolver) {
            super(context, fragment, loaderId, requester, tableName, description, converter, uriResolver);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected void onSetType(final Type type) {
            CoreLogger.log("set type to " + type);
            mType = type;
            if (mConverter.getType() == null) mConverter.setType(mType);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public Type getType() {
            synchronized (mTypeLock) {
                if (mType == null) onSetType(super.getType());
                if (mType == null) onSetType(getTypeHelper());
                return mType;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected abstract Type getTypeHelper();
    }

    /**
     * The <code>CoreLoad</code> component is responsible for data loading. Usage example:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.loader.wrapper.BaseLoaderWrapper.SwipeRefreshWrapper;
     * import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
     * import akha.yakhont.technology.retrofit.Retrofit2;
     * import akha.yakhont.technology.retrofit.Retrofit2.Retrofit2Rx;
     * import akha.yakhont.technology.retrofit.Retrofit2LoaderWrapper.Retrofit2CoreLoadBuilder;
     * import akha.yakhont.technology.rx.Rx;
     * import akha.yakhont.technology.rx.Rx2;
     *
     * import com.mypackage.model.MyData;
     * import com.mypackage.retrofit.Retrofit2Api;
     *
     * import retrofit2.Callback;
     * import android.support.annotation.NonNull;
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
     *         // optional Rx component
     *         Retrofit2Rx&lt;MyData[]&gt; rx = new Retrofit2Rx&lt;&gt;();
     *
     *         CoreLoad coreLoad = new Retrofit2CoreLoadBuilder&lt;MyData[], Retrofit2Api&gt;(
     *                 this, MyData[].class, getRetrofitApi()) {
     *
     *                     // optional: override makeRequest only if
     *                     //   the default one doesn't work well
     *                     &#064;Override
     *                     public void makeRequest(&#064;NonNull Callback&lt;MyData[]&gt; callback) {
     *                         // something like this (e.g. for Retrofit2 Call):
     *                         //     getApi().data().enqueue(callback);
     *                         // or like this (e.g. for Rx2 Flowable):
     *                         //     getRx2DisposableHandler().add(Rx2.handle(
     *                         //         getApi().data(), getRxWrapper(callback)));
     *                         // or like this (e.g. for Rx Observable):
     *                         //     getRxSubscriptionHandler().add(Rx.handle(
     *                         //         getApi().data(), getRxWrapper(callback)));
     *                         // or ...
     *                     }
     *                 }
     *
     *             // optional
     *             .setDataBinding(new String[] {"name",    "age"   },
     *                             new int   [] {R.id.name, R.id.age})
     *
     *             // all 3 "set" methods below are optional too
     *             .setListView(R.id.list_view)       // list / grid / recycler view ID
     *             .setListItem(R.layout.list_item)   // view item layout
     *             .setRx(rx)
     *
     *             .create();
     *
     *         coreLoad.startLoading();
     *     }
     *
     *     private Retrofit2&lt;Retrofit2Api&gt; getRetrofitApi() {
     *         // something like below but not exactly -
     *         //   Retrofit2 object should be cached somewhere
     *         // and don't forget to call Retrofit2.init()
     *         return new Retrofit2&lt;&gt;();
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
         *
         * @return  This {@code CoreLoad} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
        CoreLoad setGoBackOnLoadingCanceled(boolean isGoBackOnLoadingCanceled);

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
