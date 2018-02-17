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

import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;
import akha.yakhont.loader.BaseResponse.Source;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
//import android.provider.BaseColumns;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
//import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class that performs asynchronous loading of data. If network is not available, loading goes from cache
 * (which is updated after every successful loading from network). Most implementations should not
 * use <code>CacheLoader</code> directly, but instead utilise {@link akha.yakhont.technology.retrofit.RetrofitLoaderWrapper}
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
 *        The type of data in this loader
 *
 * @see akha.yakhont.BaseCacheProvider
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
public abstract class CacheLoader<C, R, E, D> extends BaseLoader<C, R, E, D> {

    private   final     WeakReference<Fragment>       mFragment;
    private   final     Uri                           mUri;
    private   final     int                           mLoaderId;
    private             BaseCursorAdapter             mAdapter;

    private   final     AtomicBoolean                 mForceCache               = new AtomicBoolean();
    private   final     AtomicBoolean                 mMerge                    = new AtomicBoolean();

    private   final     ExecutorService               mExecutor                 = Executors.newSingleThreadExecutor();

    private   final     Converter<D>                  mConverter;

    /**
     * Initialises a newly created {@code CacheLoader} object.
     *
     * @param context
     *        The context
     *
     * @param fragment
     *        The fragment
     *
     * @param converter
     *        The converter
     *
     * @param loaderId
     *        The loader ID
     *
     * @param tableName
     *        The name of the table in the database (to cache the loaded data)
     *
     * @param description
     *        The data description
     *
     * @param adapter
     *        The adapter to use
     *
     * @param uriResolver
     *        The URI resolver
     */
    public CacheLoader(@NonNull final Context context,
                       @NonNull final WeakReference<Fragment> fragment, @NonNull final Converter<D> converter,
                       final int loaderId, @NonNull final String tableName, final String description, final BaseCursorAdapter adapter,
                       @NonNull final UriResolver uriResolver) {
        super(context, description, tableName);

        mFragment           = fragment;
        mUri                = uriResolver.getUri(tableName);
        mLoaderId           = loaderId;
        mAdapter            = adapter;
        mConverter          = converter;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    protected Converter<D> getConverter() {
        return mConverter;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int getId() {
        final int id = super.getId();
        if (id == 0) return mLoaderId;

        if (id != mLoaderId)
            CoreLogger.logError("getId() == " + id + " but mLoaderId == " + mLoaderId);    // should never happen

        return id;
    }

    /**
     * Sets the adapter to use.
     *
     * @param adapter
     *        The adapter
     */
    public void setAdapter(final BaseCursorAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Sets the "force cache" flag. Setting to {@code true} forces loading data from cache.
     * <br>The default value is {@code false}.
     *
     * @param forceCache
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean setForceCache(final boolean forceCache) {
        CoreLogger.log(addLoaderInfo("" + forceCache));

        return mForceCache.getAndSet(forceCache);
    }

    /**
     * Sets the "merge" flag. If set to {@code true} the loaded data will be merged with already existing.
     * <br>The default value is {@code false}.
     *
     * @param merge
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean setMerge(final boolean merge) {
        CoreLogger.log(addLoaderInfo("" + merge));

        return mMerge.getAndSet(merge);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void makeRequest() {
        if (mForceCache.get() || !Utils.isConnected()) {
            CoreLogger.log(addLoaderInfo("request forced to cache, forceCache " + mForceCache.get()));

            onFailure(new BaseResponse<>(Source.CACHE));
        }
        else
            super.makeRequest();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onSuccess(@NonNull final BaseResponse<R, E, D> baseResponse) {
        storeResult(baseResponse);

        super.onSuccess(baseResponse);
    }

    private void storeResult(@NonNull final BaseResponse<R, E, D> baseResponse) {
        switch (baseResponse.getSource()) {
            case NETWORK:
                break;
            default:
                return;
        }

        final D result = baseResponse.getResult();

        CoreLogger.logWarning(addLoaderInfo("about to store in cache"));

        final ContentValues[] values = mConverter.get(result);
        baseResponse.setContentValues(values);

        //noinspection Convert2Lambda
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    storeResult(values);
                }
                catch (Exception e) {
                    CoreLogger.log(addLoaderInfo("can not store result"), e);
                }
            }
        });
    }

    private void storeResult(final ContentValues[] values) {
        final ContentResolver contentResolver = getContext().getContentResolver();

        if (!mMerge.get()) contentResolver.delete(mUri, null, null);

        if (values == null || values.length == 0) return;

        contentResolver.bulkInsert(mUri, values);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onFailure(@NonNull final BaseResponse<R, E, D> baseResponse) {
        CoreLogger.log(addLoaderInfo("about to load from cache"));

        final Fragment fragment = mFragment.get();
        if (fragment == null) {
            CoreLogger.logError("fragment == null");
            return;
        }

        final LoaderManager loaderManager = fragment.getLoaderManager();
        if (loaderManager == null)
            CoreLogger.logError("loaderManager == null");
        else
            loaderManager.restartLoader(-mLoaderId, null, new CursorLoaderWrapper(baseResponse.getError()));
    }

    private class CursorLoaderWrapper implements LoaderManager.LoaderCallbacks<Cursor> {

        private final E         mError;

        private CursorLoaderWrapper(final E error) {
            mError = error;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getContext(), mUri, null, null, null, null);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            CoreLogger.log(addLoaderInfo("from cache"));

            deliver(new BaseResponse<>(mConverter.get(cursor), null, cursor, mError, Source.CACHE, null));
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            CoreLogger.log(addLoaderInfo(null));

            if (mAdapter != null) mAdapter.swapCursor(null);
        }
    }
}
