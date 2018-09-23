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

package akha.yakhont.loader.wrapper;

import akha.yakhont.Core;
import akha.yakhont.Core.Requester;
import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.Mergeable;
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.BaseLoader.LoaderCallback;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.LoadParameters;
import akha.yakhont.loader.BaseResponse.Source;
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
import android.support.annotation.RequiresApi;
import android.support.annotation.Size;
import android.support.annotation.StringRes;
import android.text.TextUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public class BaseResponseLoaderWrapper<C, R, E, D> extends BaseLoaderWrapper<BaseResponse<R, E, D>> {

    // just a placeholder for the moment
    private static final int                                    MAX_TABLE_NAME_LENGTH   = 1024;

    private   final String                                      mTableName;
    private   final String                                      mDescription;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final Requester<C>                                mRequester;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final Converter<D>                                mConverter;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final UriResolver                                 mUriResolver;

    private         CacheAdapter<R, E, D>                       mAdapter;
    private         Loader<BaseResponse<R, E, D>>               mLoader;

    private         LoaderRx<R, E, D>                           mRx;

    private   final LoaderFactoryWrapper                        mLoaderFactoryWrapper;

    /**
     * The API to create new {@yakhont.link BaseLoader} instances. To create {@link Loader} instances
     * please use {@yakhont.link BaseLoaderWrapper#LoaderFactory LoaderFactory}.
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
     * @yakhont.see BaseLoaderWrapper#LoaderFactory LoaderFactory
     */
    @SuppressWarnings("WeakerAccess")
    public interface BaseLoaderFactory<C, R, E, D> {

        /**
         * Returns a new {@code BaseLoader} instance.
         *
         * @param merge
         *        {@code true} to merge the newly loaded data with already existing, {@code false} otherwise
         *
         * @return  The {@code BaseLoader} instance
         */
        BaseLoader<C, R, E, D> getLoader(boolean merge);
    }

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
    public BaseResponseLoaderWrapper(@NonNull final Context context, @NonNull final Fragment fragment,
                                     final Integer loaderId, @NonNull final Requester<C> requester,
                                     @NonNull final String tableName, final String description,
                                     @NonNull final Converter<D> converter, @NonNull final UriResolver uriResolver) {
        this(context, fragment, loaderId, requester, adjustTableName(tableName), description,
                converter, uriResolver, loaderId == null);
    }

    private BaseResponseLoaderWrapper(@NonNull final Context context, @NonNull final Fragment fragment,
                                      final Integer loaderId, @NonNull final Requester<C> requester,
                                      @NonNull final String tableName, final String description,
                                      @NonNull final Converter<D> converter, @NonNull final UriResolver uriResolver,
                                      final boolean loaderIdAutoGenerated) {
        super(fragment, loaderIdAutoGenerated && tableName.length() > 0 ?
                generateLoaderId(tableName): loaderId, loaderIdAutoGenerated);

        mTableName              = tableName;
        mDescription            = description;
        CoreLogger.log("assigned table name: " + mTableName);

        mRequester              = requester;
        mConverter              = converter;
        mUriResolver            = uriResolver;

        mLoaderFactoryWrapper   = new LoaderFactoryWrapper();

        //noinspection Convert2Lambda
        setBaseLoaderFactory(new BaseLoaderFactory<C, R, E, D>() {
            @Override
            public BaseLoader<C, R, E, D> getLoader(boolean merge) {
                return new WrapperLoader<>(context, new WeakReference<>(getFragment()), getLoaderId(),
                        mTableName, mDescription, merge, mAdapter, mRequester, mUriResolver);
            }
        });
    }

    private static Integer generateLoaderId(@NonNull final String tableName) {
        return tableName.hashCode();
    }

    /**
     * Sets {@code BaseLoader} factory.
     *
     * @param baseLoaderFactory
     *        The factory
     */
    @SuppressWarnings("WeakerAccess")
    public void setBaseLoaderFactory(final BaseLoaderFactory<C, R, E, D> baseLoaderFactory) {
        mLoaderFactoryWrapper.mBaseLoaderFactory = baseLoaderFactory;
    }

    private class LoaderFactoryWrapper {

        private BaseLoaderFactory<C, R, E, D>                   mBaseLoaderFactory;

        private BaseLoader<C, R, E, D> setCallback(final boolean merge, final C callback) {
            final BaseLoader<C, R, E, D> loader = mBaseLoaderFactory.getLoader(merge);
            if (loader == null)
                CoreLogger.logWarning("loader == null");
            return loader == null ? null: loader.setCallback(callback);
        }
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
                BaseResponse.clearCache(
                        ((BaseResponseLoaderWrapper) baseLoaderWrapper).getTableName());
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
        tableName = tableName.length() > MAX_TABLE_NAME_LENGTH ?
                tableName.substring(MAX_TABLE_NAME_LENGTH - tableName.length(),
                        tableName.length()): tableName;

        CoreLogger.log("table name after adjusting: " + tableName);
        return tableName;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public BaseLoaderWrapper findLoader(final Collection<BaseLoaderWrapper> loaders) {
        final BaseLoaderWrapper baseLoaderWrapper = super.findLoader(loaders);
        if (baseLoaderWrapper instanceof BaseResponseLoaderWrapper) {
            final String table = ((BaseResponseLoaderWrapper) baseLoaderWrapper).mTableName;
            if (!mTableName.equals(table))
                CoreLogger.logError("found loader with id " + getLoaderId() +
                        " but table is different: found " + table + ", should be " + mTableName);
        }
        return baseLoaderWrapper;
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

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected void setLoaderParameters(@NonNull @Size(value = 0) final List<BaseLoader<C, R, E, D>> baseLoaders,
                                       final int timeout, @NonNull final C callback) {
        if (geLoaderFactory() != null) {
            CoreLogger.logWarning("can not set loader callback; user-defined loader factory");
            return;
        }
        //noinspection Convert2Lambda
        setLoaderFactory(new LoaderFactory<BaseResponse<R, E, D>>() {
            @Override
            public Loader<BaseResponse<R, E, D>> getLoader(final boolean merge) {
                final BaseLoader<C, R, E, D> loader = mLoaderFactoryWrapper.setCallback(merge, callback);
                if (loader != null) {
                    loader.setTimeout(timeout);
                    baseLoaders.add(loader);
                }
                else
                    CoreLogger.logWarning("BaseLoader == null; can not set timeout");
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
        if (mLoader instanceof WrapperLoader)
            ((WrapperLoader) mLoader).setAdapter(getLoaderAdapter(mAdapter));
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

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected void onLoadFinishedHelper(final Loader<BaseResponse<R, E, D>> loader, final BaseResponse<R, E, D> data) {
    }

    private void updateAdapter(final Loader<BaseResponse<R, E, D>> loader, final BaseResponse<R, E, D> data) {
        if (mAdapter != null)
            //noinspection Convert2Lambda
            mAdapter.update(data, loader instanceof Mergeable && ((Mergeable) loader).isMerge(),
                    new Runnable() {
                        @Override
                        public void run() {
                            BaseResponseLoaderWrapper.super.onLoadFinishedHelper(loader, data);
                        }
                    });
        else
            CoreLogger.logWarning("adapter == null, table name: " + mTableName);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @CallSuper
    @Override
    protected boolean onLoaderResetHelper(@NonNull final Loader<BaseResponse<R, E, D>> loader) {
        if (!super.onLoaderResetHelper(loader)) return false;

        if (mAdapter != null) mAdapter.resetArray();
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class WrapperLoader<C, R, E, D> extends CacheLoader<C, R, E, D> implements Mergeable {

        private final Requester<C>                              mRequester;
        private final boolean                                   mMerge;

        @SuppressWarnings("unused")
        private WrapperLoader(@NonNull final Context context, @NonNull final WeakReference<Fragment> fragment,
                              final int loaderId, @NonNull final String tableName, final String description,
                              final boolean merge, final CacheAdapter<R, E, D> adapter,
                              @NonNull final Requester<C> requester, @NonNull final UriResolver uriResolver) {
            super(context, fragment, loaderId, tableName, description, getLoaderAdapter(adapter), uriResolver);

            setMerge(merge);

            mRequester  = requester;
            mMerge      = merge;
        }

        @Override
        public boolean isMerge() {
            return mMerge;
        }

        @Override
        protected void makeRequest(@NonNull final C callback) {
            try {
                mRequester.makeRequest(callback);
            }
            // java.lang.ClassCastException: Couldn't convert result of type
            // io.reactivex.internal.observers.LambdaObserver to io.reactivex.Observable
            catch (ClassCastException exception) {
                Utils.check(exception, "io.reactivex.internal.");
                CoreLogger.log(Level.WARNING, "it seems an API bug", exception);
            }
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
    public static class BaseResponseLoaderBuilder<C, R, E, D> implements LoaderBuilder<C, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final WeakReference<Fragment>                 mUiFragment;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Requester<C>                            mRequester;

        private         String                                  mTableName;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       String                                  mDescription;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Integer                                 mLoaderId;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Type                                    mType;
        private         Converter<D>                            mConverter;
        private         UriResolver                             mUriResolver;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       LoaderCallback<C, R, E, D>              mLoaderCallbacks;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       LoaderFactory<BaseResponse<R, E, D>>    mLoaderFactory;

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected String getTableName(final Method method) {
            String tableName = getTableNameRaw();
            if (TextUtils.isEmpty(tableName)) tableName = createTableName(method);
            return tableName;
        }

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
         *
         * @yakhont.see BaseLoader.CoreLoadExtendedBuilder#setType CoreLoadExtendedBuilder.setType
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
         * Sets the loader callback. Extended version of the {@link #setLoaderCallbacks(LoaderManager.LoaderCallbacks)}.
         *
         * @param loaderCallbacks
         *        The loader callbacks
         *
         * @return  This {@code BaseResponseLoaderBuilder} object to allow for chaining of calls to set methods
         */
        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public BaseResponseLoaderBuilder<C, R, E, D> setLoaderCallback(final LoaderCallback<C, R, E, D> loaderCallbacks) {
            mLoaderCallbacks = loaderCallbacks;
            return this;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected LoaderManager.LoaderCallbacks<BaseResponse<R, E, D>> createLoaderCallbacks() {
            return mLoaderCallbacks == null ? null: new LoaderManager.LoaderCallbacks<BaseResponse<R, E, D>>() {

                @Override
                public Loader<BaseResponse<R, E, D>> onCreateLoader(int id, Bundle args) {
                    final Loader<BaseResponse<R, E, D>> loader = mLoaderCallbacks.onCreateLoader(id, args);
                    return loader != null ? loader: mLoaderCallbacks.onCreateLoader(
                            mLoaderCallbacks.getLoaderWrapper().createLoader(id, args));
                }

                @Override
                public void onLoaderReset(Loader<BaseResponse<R, E, D>> loader) {
                    mLoaderCallbacks.onLoaderReset(loader);
                }

                @Override
                public void onLoadFinished(Loader<BaseResponse<R, E, D>> loader, BaseResponse<R, E, D> data) {
                    mLoaderCallbacks.onLoadFinished(loader, data);

                    if (data == null) {
                        mLoaderCallbacks.onLoadFinished(null, (Source) null);
                        return;
                    }

                    final E      error  = data.getError();
                    final Source source = data.getSource();

                    if (error != null)
                        mLoaderCallbacks.onLoadError(error, source);
                    else
                        mLoaderCallbacks.onLoadFinished(data.getResult(), source);
                }
            };
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public final BaseResponseLoaderWrapper<C, R, E, D> createBaseResponseLoader() {
            final BaseResponseLoaderWrapper<C, R, E, D> loaderWrapper = createLoaderWrapper();

            if (mLoaderCallbacks != null) {
                mLoaderCallbacks.setLoaderWrapper(loaderWrapper);
                loaderWrapper.setLoaderCallbacks(createLoaderCallbacks());
            }
            if (mLoaderFactory   != null) loaderWrapper.setLoaderFactory(mLoaderFactory);

            return loaderWrapper;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public final BaseLoaderWrapper<D> createBaseLoader() {
            return null;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        protected BaseResponseLoaderWrapper<C, R, E, D> createLoaderWrapper() {
            return new BaseResponseLoaderWrapper<>(getContext(), getFragment(), mLoaderId,
                    getRequester(), getTableName(), mDescription, getConverter(), getUriResolver());
        }

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

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public Type getTypeRaw() {
            return mType;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public LoaderCallback<C, R, E, D> getLoaderCallbackRaw() {
            return mLoaderCallbacks;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public LoaderFactory<BaseResponse<R, E, D>> getLoaderFactoryRaw() {
            return mLoaderFactory;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public Converter<D> getConverterRaw() {
            return mConverter;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public String getTableNameRaw() {
            return mTableName;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public Integer getLoaderIdRaw() {
            return mLoaderId;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public UriResolver getUriResolverRaw() {
            return mUriResolver;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public String getDescriptionRaw() {
            return mDescription;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
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
        public Converter<D> getConverter() {
            if (mConverter == null) mConverter = new BaseConverter<>();
            return mConverter;
        }

        /**
         * Returns the URI resolver.
         *
         * @return  The URI resolver
         */
        @NonNull
        protected UriResolver getUriResolver() {
            if (mUriResolver == null) mUriResolver = Utils.getUriResolver();
            return mUriResolver;
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
            if (TextUtils.isEmpty(mTableName)) CoreLogger.logError("empty table name");
            return mTableName;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected String createTableName(final Method method) {
            if (!TextUtils.isEmpty(mTableName)) return mTableName;

            if (method != null) {
                String name = method.getDeclaringClass().getName() + "." + method.getName();

                final Class<?>[] params = method.getParameterTypes();
                if (params != null && params.length > 0) {
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
        protected       Integer                                 mTimeout;

        /**
         * Initialises a newly created {@code BaseResponseLoaderExtendedBuilder} object.
         *
         * @param fragment
         *        The fragment
         */
        @SuppressWarnings("unused")
        public BaseResponseLoaderExtendedBuilder(@NonNull final Fragment fragment) {
            super(fragment);
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
                CoreLogger.log(mMethod == null ? Level.ERROR: Level.DEBUG, "for type " +
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
                        throw throwable instanceof RuntimeException ? (RuntimeException) throwable: new RuntimeException(throwable);
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
        Collection<BaseLoaderWrapper> getLoaders();

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
         * @param parameters
         *        The LoadParameters
         *
         * @return  {@code true} if data loading was successfully started, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        boolean load(LoadParameters parameters);

        /**
         * Starts all loaders associated with the given {@code CoreLoad} component.
         *
         * @return  {@code true} if data loading was successfully started, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        boolean load();

        /**
         * Cancels all {@link BaseLoaderWrapper loaders} associated with the given {@code CoreLoad} component.
         *
         * @return  This {@code CoreLoad} object to allow for chaining of calls
         *
         * @see LoaderManager#destroyLoader
         */
        @SuppressWarnings("UnusedReturnValue")
        CoreLoad cancelLoading();

        /**
         * Indicates whether the back key press should be emulated if data loading was cancelled or not
         *
         * @param isGoBackOnLoadingCanceled
         *        The value to set
         *
         * @return  This {@code CoreLoad} object to allow for chaining of calls
         */
        @SuppressWarnings("UnusedReturnValue")
        CoreLoad setGoBackOnLoadingCanceled(boolean isGoBackOnLoadingCanceled);

        /**
         * Displays a data loading progress indicator.
         *
         * @param text
         *        The text to display
         *
         * @return  This {@code CoreLoad} object to allow for chaining of calls
         */
        @SuppressWarnings("UnusedReturnValue")
        CoreLoad showProgress(String text);

        /**
         * Hides data loading progress indicator.
         *
         * @param force
         *        Indicates whether the progress hiding should be forced (e.g as result of {@link #cancelLoading()}) or not
         *
         * @return  This {@code CoreLoad} object to allow for chaining of calls
         */
        @SuppressWarnings("UnusedReturnValue")
        CoreLoad hideProgress(boolean force);
    }
}
