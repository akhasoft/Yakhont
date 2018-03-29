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

package akha.yakhont.adapter;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.SupportHelper;
import akha.yakhont.adapter.BaseCacheAdapter.BaseArrayAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterFactory;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.DataBinder;
import akha.yakhont.adapter.BaseCacheAdapter.DataConverter;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.adapter.BaseCacheAdapter.ViewInflater;
import akha.yakhont.loader.BaseConverter;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Converter;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * The wrapper for {@link BaseCacheAdapter}.
 *
 * @param <R>
 *        The type of network response
 *
 * @param <E>
 *        The type of error (if any)
 *
 * @param <D>
 *        The type of data in the adapter
 *
 * @author akha
 */
public class ValuesCacheAdapterWrapper<R, E, D> implements CacheAdapter<R, E, D> {

    private final BaseCacheAdapter <ContentValues, R, E, D>             mBaseCacheAdapter;
    private final ContentValuesRecyclerViewAdapter<R, E, D>             mBaseRecyclerViewAdapter;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected Converter<D>                                              mConverter;

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object. The data binding goes by default:
     * cursor's column {@link BaseColumns#_ID _ID} binds to view with R.id._id, column "title" - to R.id.title etc.
     *
     * @param context
     *        The Activity
     *
     * @param layoutId
     *        The resource identifier of a layout file that defines the views
     */
    @SuppressWarnings("WeakerAccess")
    public ValuesCacheAdapterWrapper(@NonNull final Activity context, @LayoutRes final int layoutId) {
        //noinspection RedundantTypeArguments
        this(ValuesCacheAdapterWrapper.<R, E, D>init(context, layoutId), context, layoutId);
    }

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object.
     *
     * @param context
     *        The Activity
     *
     * @param layoutId
     *        The resource identifier of a layout file that defines the views
     *
     * @param from
     *        The list of names representing the data to bind to the UI
     *
     * @param to
     *        The views that should display data in the "from" parameter
     */
    @SuppressWarnings("WeakerAccess")
    public ValuesCacheAdapterWrapper(@NonNull final Activity context, @LayoutRes final int layoutId,
                                     @NonNull @Size(min = 1) final String[] from,
                                     @NonNull @Size(min = 1) final    int[] to) {
        //noinspection RedundantTypeArguments
        this(ValuesCacheAdapterWrapper.<R, E, D>init(context, layoutId, from, to), context, layoutId);
    }

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object.
     *
     * @param factory
     *        The BaseCacheAdapterFactory
     *
     * @param compatible
     *        The support flag for the BaseCacheAdapterFactory
     *
     * @param context
     *        The Activity
     *
     * @param layoutId
     *        The resource identifier of a layout file that defines the views
     *
     * @param from
     *        The list of names representing the data to bind to the UI
     *
     * @param to
     *        The views that should display data in the "from" parameter
     */
    @SuppressWarnings("WeakerAccess")
    public ValuesCacheAdapterWrapper(@NonNull final BaseCacheAdapterFactory<ContentValues, R, E, D> factory,
                                     @SuppressWarnings("SameParameterValue") final boolean compatible,
                                     @NonNull final Activity context, @LayoutRes final int layoutId,
                                     @NonNull @Size(min = 1) final String[] from,
                                     @NonNull @Size(min = 1) final    int[] to) {
        this(init(factory, compatible, context, layoutId, from, to), context, layoutId);
    }

    private ValuesCacheAdapterWrapper(@NonNull final BaseCacheAdapter<ContentValues, R, E, D> baseCacheAdapter,
                                      @NonNull final Context context, @LayoutRes final int layoutId) {
        mBaseCacheAdapter        = baseCacheAdapter;
        mBaseCacheAdapter.setConverter(new DataConverterContentValues());

        mBaseRecyclerViewAdapter = new ContentValuesRecyclerViewAdapter<>(context, layoutId,
                baseCacheAdapter.getArrayAdapter().getFrom(),
                baseCacheAdapter.getArrayAdapter().getTo(), baseCacheAdapter);
    }

    private static <R, E, D> BaseCacheAdapter<ContentValues, R, E, D> init(
            @NonNull final Activity context, @LayoutRes final int layoutId) {
        final Set<String>           fromSet = Utils.newSet();
        final int[]                 to      = BaseCacheAdapter.getViewsBinding(context, layoutId, fromSet);
        final String[]              from    = fromSet.toArray(new String[fromSet.size()]);
        return init(context, layoutId, from, to);
    }

    private static <R, E, D> BaseCacheAdapter<ContentValues, R, E, D> init(
            @NonNull final Activity context, @LayoutRes final int layout,
            @NonNull @Size(min = 1) final String[] from,
            @NonNull @Size(min = 1) final    int[] to) {
        return init(new BaseCacheAdapterFactory<ContentValues, R, E, D>(), SupportHelper.isSupportMode(context), context, layout, from, to);
    }

    private static <R, E, D> BaseCacheAdapter<ContentValues, R, E, D> init(
            @NonNull final BaseCacheAdapterFactory<ContentValues, R, E, D> factory,
            @SuppressWarnings("SameParameterValue") final boolean compatible,
            @NonNull final Activity context, @LayoutRes final int layout,
            @NonNull @Size(min = 1) final String[] from,
            @NonNull @Size(min = 1) final    int[] to) {

        @SuppressWarnings("Convert2Lambda")
        final BaseCacheAdapter<ContentValues, R, E, D> adapter = factory.getAdapter(context, layout, from, to,
                new BaseArrayAdapter<>(context, layout, getDataBinder(context, from, to)),
                compatible);

        CoreLogger.log(Level.INFO, "instantiated " + adapter.getClass().getName());
        return adapter;
    }

    /**
     * Sets the {@code Converter}.
     *
     * @param converter
     *        The {@code Converter}
     */
    public void setConverter(@NonNull final Converter<D> converter) {
        if (mConverter != null)
            CoreLogger.logWarning("converter already set");
        mConverter = converter;
    }

    private class DataConverterContentValues implements DataConverter<ContentValues, R, E, D> {
        @Override
        public Collection<ContentValues> convert(@NonNull final BaseResponse<R, E, D> baseResponse) {
            final ContentValues[] values = baseResponse.getValues();
            return values != null ? Arrays.asList(values): null;
        }

        @Override
        public ContentValues convert(@NonNull final Cursor cursor) {
            if (mConverter == null) {       // should never happen
                CoreLogger.logWarning("converter is null");
                mConverter = new BaseConverter<>();     // type not set, but here it's not needed
            }
            return mConverter.getContentValues(cursor);
        }
    }

    private static DataBinder<ContentValues> getDataBinder(@NonNull final Context context,
                                                           @NonNull @Size(min = 1) final String[] from,
                                                           @NonNull @Size(min = 1) final int   [] to) {
        return new DataBinder<ContentValues>(context, from, to) {
            @Override
            protected Object getValue(@NonNull ContentValues item, int index) {
                return ValuesCacheAdapterWrapper.getValue(item, index, mFrom);
            }
        };
    }

    /**
     * Registers the {@code ViewBinder} to use.
     *
     * @param viewBinder
     *        The ViewBinder
     */
    public void setAdapterViewBinder(final ViewBinder viewBinder) {
        getAdapter()            .setAdapterViewBinder(viewBinder);
        getRecyclerViewAdapter().setAdapterViewBinder(viewBinder);
    }

    /**
     * Returns the {@code BaseCacheAdapter} wrapped by this object.
     *
     * @return  The BaseCacheAdapter
     */
    @NonNull
    @SuppressWarnings("unused")
    public BaseCacheAdapter<ContentValues, R, E, D> getAdapter() {
        return mBaseCacheAdapter;
    }

    /**
     * Returns the {@code ContentValuesRecyclerViewAdapter} wrapped by this object.
     *
     * @return  The ContentValuesRecyclerViewAdapter
     */
    @NonNull
    @SuppressWarnings("unused")
    public ContentValuesRecyclerViewAdapter<R, E, D> getRecyclerViewAdapter() {
        return mBaseRecyclerViewAdapter;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void update(@NonNull final BaseResponse<R, E, D> data, final boolean isMerge) {
        getAdapter().update(data, isMerge);
        mBaseRecyclerViewAdapter.notifyDataSetChanged();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void resetArray() {
        //noinspection Convert2Lambda
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                getAdapter().resetArray();
                mBaseRecyclerViewAdapter.notifyDataSetChanged();
            }

            @Override
            public String toString() {
                return "ValuesCacheAdapterWrapper.resetArray()";
            }
        });
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public BaseCursorAdapter getCursorAdapter() {
        return getAdapter().getCursorAdapter();
    }

    private static Object getValue(@NonNull final ContentValues item, final int index,
                                   @NonNull @Size(min = 1) final String[] from) {
        if (index >= from.length)          return null;
        if (item.containsKey(from[index])) return item.get(from[index]);

        CoreLogger.log(from[index].equals(BaseColumns._ID) ? Level.INFO: Level.WARNING,
                from[index] + ": no such key", false);
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The {@link ContentValues}-based implementation of the {@link BaseRecyclerViewAdapter}.
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data in the adapter
     */
    public static class ContentValuesRecyclerViewAdapter<R, E, D>
            extends BaseRecyclerViewAdapter<ContentValues, R, E, D, ViewHolder> {

        private final           ViewInflater                    mViewInflater;

        /**
         * Initialises a newly created {@code ContentValuesRecyclerViewAdapter} object.
         *
         * @param context
         *        The Context
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         *
         * @param from
         *        The list of names representing the data to bind to the UI
         *
         * @param to
         *        The views that should display data in the "from" parameter
         *
         * @param baseCacheAdapter
         *        The {@link BaseCacheAdapter} to wrap
         */
        @SuppressWarnings("WeakerAccess")
        public ContentValuesRecyclerViewAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                                @NonNull @Size(min = 1) final String[] from,
                                                @NonNull @Size(min = 1) final int   [] to,
                                                @NonNull final BaseCacheAdapter<ContentValues, R, E, D> baseCacheAdapter) {
            super(baseCacheAdapter, getDataBinder(context, from, to), layoutId);

            mViewInflater = new ViewInflater(context, layoutId);
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return mViewHolderCreator != null ?
                    super.onCreateViewHolder(parent, viewType):
                    new ViewHolder(mViewInflater.inflate(parent)) {};
        }
    }
}
