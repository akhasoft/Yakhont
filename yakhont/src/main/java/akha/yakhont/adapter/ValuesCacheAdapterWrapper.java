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

package akha.yakhont.adapter;

import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.SupportHelper;
import akha.yakhont.adapter.BaseCacheAdapter.ArrayConverter;
import akha.yakhont.adapter.BaseCacheAdapter.BaseArrayAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterFactory;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.loader.BaseResponse;

import android.content.ContentValues;
import android.content.Context;
import android.provider.BaseColumns;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Size;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

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

    private final BaseCacheAdapter<ContentValues, R, E, D>      mBaseCacheAdapter;

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object. The data binding goes by default:
     * cursor's column {@link BaseColumns#_ID _ID} binds to view with R.id._id, column "title" - to R.id.title etc.
     *
     * @param context
     *        The Context
     *
     * @param layout
     *        The resource identifier of a layout file that defines the views
     */
    @SuppressWarnings("WeakerAccess")
    public ValuesCacheAdapterWrapper(@NonNull final Context context, @LayoutRes final int layout) {
        final LinkedHashSet<String> from = new LinkedHashSet<>();
        final int[]                 to   = BaseCacheAdapter.getViewsBinding(context, layout, from);
        mBaseCacheAdapter = to.length == 0 ? null: init(context, layout, from.toArray(new String[from.size()]), to);
    }

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object.
     *
     * @param context
     *        The Context
     *
     * @param layout
     *        The resource identifier of a layout file that defines the views
     *
     * @param from
     *        The list of names representing the data to bind to the UI
     *
     * @param to
     *        The views that should display data in the "from" parameter
     */
    @SuppressWarnings("WeakerAccess")
    public ValuesCacheAdapterWrapper(@NonNull final Context context, @LayoutRes final int layout,
                                     @NonNull @Size(min = 1) final String[] from,
                                     @NonNull @Size(min = 1) final    int[] to) {
        mBaseCacheAdapter = init(context, layout, from, to);
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
     *        The Context
     *
     * @param layout
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
                                     @NonNull final Context context, @LayoutRes final int layout,
                                     @NonNull @Size(min = 1) final String[] from,
                                     @NonNull @Size(min = 1) final    int[] to) {
        mBaseCacheAdapter = init(factory, compatible, context, layout, from, to);
    }

    private BaseCacheAdapter<ContentValues, R, E, D> init(@NonNull final Context context, @LayoutRes final int layout,
                                                          @NonNull @Size(min = 1) final String[] from,
                                                          @NonNull @Size(min = 1) final    int[] to) {
        return init(new BaseCacheAdapterFactory<ContentValues, R, E, D>(), SupportHelper.isSupportMode(context), context, layout, from, to);
    }

    private BaseCacheAdapter<ContentValues, R, E, D> init(@NonNull final BaseCacheAdapterFactory<ContentValues, R, E, D> factory,
                                                          @SuppressWarnings("SameParameterValue") final boolean compatible,
                                                          @NonNull final Context context, @LayoutRes final int layout,
                                                          @NonNull @Size(min = 1) final String[] from,
                                                          @NonNull @Size(min = 1) final    int[] to) {
        final BaseCacheAdapter<ContentValues, R, E, D> adapter = factory.getAdapter(context, layout, from, to,
                new ValuesArrayAdapter(context, layout, from, to),
                new ArrayConverter<ContentValues, R, E, D>() {
                    @Override
                    public Collection<ContentValues> convert(@NonNull final BaseResponse<R, E, D> baseResponse) {
                        final ContentValues[] values = baseResponse.getValues();
                        return values != null ? Arrays.asList(values): null;
                    }
                },
                compatible);

        CoreLogger.log(Level.INFO, "instantiated " + adapter.getClass().getName());
        return adapter;
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
     * Please refer to the base method description.
     */
    @Override
    public void update(@NonNull final BaseResponse<R, E, D> data, final boolean isMerge) {
        getAdapter().update(data, isMerge);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void resetArray() {
        getAdapter().resetArray();
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public BaseCursorAdapter getCursorAdapter() {
        return getAdapter().getCursorAdapter();
    }

    @SuppressWarnings("unused")
    private static class ValuesArrayAdapter extends BaseArrayAdapter<ContentValues> {

        private ValuesArrayAdapter(@NonNull final Context context, @LayoutRes final int layout,
                                   @NonNull @Size(min = 1) final String[] from,
                                   @NonNull @Size(min = 1) final    int[] to) {
            super(context, layout, from, to);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        protected Object getValue(@NonNull final ContentValues item, final int index) {
            if (index >= mFrom.length)          return null;
            if (item.containsKey(mFrom[index])) return item.get(mFrom[index]);

            CoreLogger.log(mFrom[index].equals(BaseColumns._ID) ? Level.INFO: Level.WARNING,
                    mFrom[index] + ": no such key", false);
            return null;
        }
    }
}
