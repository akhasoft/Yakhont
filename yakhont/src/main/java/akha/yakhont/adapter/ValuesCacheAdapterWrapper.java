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
import akha.yakhont.CoreReflection;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private final DataConverterContentValues<R, E, D>                   mDataConverterContentValues;

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
        this(context, layoutId, init(context, layoutId));
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
        this(context, layoutId, init(context, layoutId, from, to));
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
        this(context, layoutId, init(factory, compatible, context, layoutId, from, to));
    }

    private ValuesCacheAdapterWrapper(@NonNull final Context context, @LayoutRes final int layoutId,
                                      @NonNull final BaseCacheAdapter<ContentValues, R, E, D> baseCacheAdapter) {
        mBaseCacheAdapter           = baseCacheAdapter;
        mBaseRecyclerViewAdapter    = new ContentValuesRecyclerViewAdapter<>(context, layoutId,
                baseCacheAdapter.getArrayAdapter().getFrom(),
                baseCacheAdapter.getArrayAdapter().getTo(), baseCacheAdapter);
        mDataConverterContentValues =
                (DataConverterContentValues<R, E, D>) baseCacheAdapter.getConverter();
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
        return init(new BaseCacheAdapterFactory<>(),
                SupportHelper.isSupportMode(context), context, layout, from, to);
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
                new DataConverterContentValues<>(from), compatible);

        CoreLogger.log(Level.INFO, "instantiated " + adapter.getClass().getName());
        return adapter;
    }

    /**
     * Sets the {@code Converter}.
     *
     * @param converter
     *        The {@code Converter}
     *
     * @return  {@code true} if converter successfully set, {@code false} otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean setConverter(@NonNull final Converter<D> converter) {
        return mDataConverterContentValues.setConverter(converter);
    }

    private static DataBinder<ContentValues> getDataBinder(@NonNull final Context context,
                                                           @NonNull @Size(min = 1) final String[] from,
                                                           @NonNull @Size(min = 1) final int   [] to) {
        return new DataBinder<ContentValues>(context, from, to) {
            @Override
            protected Object getValue(@NonNull ContentValues item, int index) {
                return ValuesCacheAdapterWrapper.getValue(item, index, mFrom, true);
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
                                   @NonNull @Size(min = 1) final String[] from, final boolean notSilent) {
        if (index >= from.length) {
            CoreLogger.logError(String.format(Utils.getLocale(),
                    "invalid index %d (length %d)", index, from.length));
            return null;
        }

        String name = from[index];
        if (check(name, item)) return item.get(name);
/*
        final char[] tmp = name.toCharArray();
        for (int i = 0; i < tmp.length; i++)
            if (tmp[i] == '_' && i < tmp.length - 1) tmp[i + 1] = Character.toUpperCase(tmp[i + 1]);

        name = new String(tmp).replace("_", "");
*/
        name = name.replace("_", "");
        if (check(name, item)) return item.get(name);

        if (notSilent) CoreLogger.log(from[index].equals(BaseColumns._ID) ? Level.INFO: Level.WARNING,
                from[index] + ": no such key", false);
        return null;
    }

    private static boolean check(@NonNull final String name, @NonNull final ContentValues item) {
        return item.containsKey(name.toLowerCase(Utils.getLocale()));
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
        public ContentValuesRecyclerViewAdapter(@NonNull                final Context  context,
                                                @LayoutRes              final int      layoutId,
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
            return mViewHolderCreator != null ? super.onCreateViewHolder(parent, viewType):
                    new ViewHolder(mViewInflater.inflate(parent)) {};
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class DataConverterContentValues<R, E, D> implements DataConverter<ContentValues, R, E, D> {

        private Converter<D>                                            mConverter;
        private List<ContentValues>                                     mCursorCache;

        private final String[]                                          mFrom;

        private DataConverterContentValues(@NonNull @Size(min = 1) final String[] from) {
            mFrom = from;
        }

        private boolean setConverter(final Converter<D> converter) {
            if (converter == null) {
                CoreLogger.logError("converter == null");
                return false;
            }
            if (mConverter != null)
                CoreLogger.logWarning("converter already set");

            mConverter = converter;
            return true;
        }

        @Override
        public Converter<D> getConverter() {
            return mConverter;
        }

        @Override
        public Collection<ContentValues> convert(@NonNull final BaseResponse<R, E, D> baseResponse) {
            List<ContentValues> result = null;
            final Cursor        cursor = baseResponse.getCursor();

            try {
                D data = baseResponse.getResult();
                if (data == null) data = mConverter.getData(cursor);
                if (data == null)
                    CoreLogger.logError("can't retrieve result from BaseResponse");
                else {
                    int idx = -1;
                    for (int i = 0; i < mFrom.length; i++)
                        if (!mFrom[i].equals(BaseColumns._ID)) {
                            idx = i;
                            break;
                        }
                    result = convert(data);

                    if (result.size() > 0 && idx >= 0 &&
                            getValue(result.get(0), idx, mFrom, false) == null) {
                        CoreLogger.logWarning("about to get ContentValues from Map");

                        final List<ContentValues> tmp = getFromMap(data);
                        if (tmp != null) result = tmp;
                    }
                }
            }
            finally {
                if (cursor != null && !cursor.isClosed()) cursor.close();
            }
            return result;
        }

        private List<ContentValues> getFromMap(@NonNull final D data) {
            if (!Collection.class.isAssignableFrom(data.getClass())) {
                CoreLogger.logWarning("converted data are not Collection");
                return null;
            }
            final List<ContentValues> result = new ArrayList<>();

            final Iterator iterator = ((Collection) data).iterator();
            //noinspection WhileLoopReplaceableByForEach
            while (iterator.hasNext()) {
                final Object tmp = iterator.next();

                if (tmp instanceof Map) {
                    final ContentValues values = new ContentValues();
                    for (final Map.Entry<?, ?> entry: ((Map<?, ?>) tmp).entrySet())
                        values.put(entry.getKey().toString(), entry.getValue().toString());

                    result.add(values);
                }
                else {
                    CoreLogger.logWarning("converted data are not Map");
                    return null;
                }
            }
            return result;
        }

        @Override
        public Cursor swapCursor(Cursor cursor) {
            mCursorCache = null;
            return cursor;
        }

        @Override
        public ContentValues convert(@NonNull final Cursor cursor, final int position) {
            if (mConverter == null) {       // should never happen
                CoreLogger.logWarning("converter is null");
                mConverter = new BaseConverter<>();
            }

            if (mCursorCache == null) {
                final D data;
                try {
                    data = mConverter.getData(cursor);
                }
                finally {
                    if (!cursor.isClosed()) cursor.close();
                }
                if (data == null) {
                    CoreLogger.logError("data is null");
                    return null;
                }

                if (CoreReflection.isNotSingle(data))
                    mCursorCache = convert(data);
                else {
                    if (cursor.getPosition() == position) return convertSingle(data);

                    CoreLogger.logError("can't retrieve ContentValues for position " + position);
                    return null;
                }
            }
            if (position < mCursorCache.size()) return mCursorCache.get(position);

            CoreLogger.logError("can't retrieve ContentValues for position " + position +
                    ", data size == " + mCursorCache.size());
            return null;
        }

        @NonNull
        private List<ContentValues> convert(@NonNull final Object data) {
            final List<Object> objects = CoreReflection.getObjects(data);
            if (objects == null) return Collections.singletonList(convertSingle(data));

            final ArrayList<ContentValues> list = new ArrayList<>();
            for (final Object object: objects)
                if (!CoreReflection.isNotSingle(object))
                    list.add(convertSingle(object));

            return list;
        }

        private ContentValues convertSingle(@NonNull final Object data) {
            final Map<String, Object> map = CoreReflection.getFields(data,null,
                    false, false,
                    true, true, true, true,
                    false, false);

            final ContentValues values = new ContentValues();
            for (final Map.Entry<String, Object> entry: map.entrySet()) {
                final String key   = adjust(entry.getKey()).toLowerCase(Utils.getLocale());
                final Object value = entry.getValue();

                if (value == null)
                    values.putNull(key);
                else
                    values.put(key, value.toString());
            }
            return values;
        }

        private String adjust(@NonNull final String key) {
            return (key.length() > 1 && key.charAt(0) == 'm' &&
                    Character.isUpperCase(key.charAt(1))) ? key.substring(1): key;
        }
    }
}
