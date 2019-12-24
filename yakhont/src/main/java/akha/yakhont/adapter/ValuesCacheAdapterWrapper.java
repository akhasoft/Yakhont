/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
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
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterFactory;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterWrapper;
import akha.yakhont.adapter.BaseCacheAdapter.BaseDataConverter;
import akha.yakhont.adapter.BaseCacheAdapter.DataBinder;
import akha.yakhont.adapter.BaseCacheAdapter.DefaultArrayAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.ViewInflater;
import akha.yakhont.loader.BaseResponse;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ContentValues-based wrapper for {@code BaseCacheAdapter}.
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
public class ValuesCacheAdapterWrapper<R, E, D> extends BaseCacheAdapterWrapper<ContentValues, R, E, D> {

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object. The data binding goes by default:
     * cursor's column {@link BaseColumns#_ID _ID} binds to view with R.id._id, column "title" - to R.id.title etc.
     *
     * @param context
     *        The {@code Activity}
     *
     * @param layoutId
     *        The resource identifier of a layout file that defines the views
     *
     * @param support
     *        {@code true} to use the {@code SupportCacheAdapter} instance, {@code false} otherwise
     *
     * @param supportCursorAdapter
     *        {@code true} if SimpleCursorAdapter from support library should be used, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public ValuesCacheAdapterWrapper(@NonNull   final Activity context,
                                     @LayoutRes final int      layoutId,
                                                final boolean  support,
                                                final boolean  supportCursorAdapter) {
        this(context, layoutId, init(context, layoutId, support, supportCursorAdapter));
    }

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object.
     *
     * @param context
     *        The {@code Activity}
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
     * @param support
     *        {@code true} to use the {@code SupportCacheAdapter} instance, {@code false} otherwise
     *
     * @param supportCursorAdapter
     *        {@code true} if SimpleCursorAdapter from support library should be used, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public ValuesCacheAdapterWrapper(@NonNull                final Activity context,
                                     @LayoutRes              final int      layoutId,
                                     @NonNull @Size(min = 1) final String[] from,
                                     @NonNull @Size(min = 1) final    int[] to,
                                                             final boolean  support,
                                                             final boolean  supportCursorAdapter) {
        this(context, layoutId, init(support, supportCursorAdapter, context, layoutId, from, to));
    }

    /**
     * Initialises a newly created {@code ValuesCacheAdapterWrapper} object.
     *
     * @param factory
     *        The {@code BaseCacheAdapterFactory}
     *
     * @param support
     *        {@code true} to use the {@code SupportCacheAdapter} instance, {@code false} otherwise
     *
     * @param supportCursorAdapter
     *        {@code true} if SimpleCursorAdapter from support library should be used, {@code false} otherwise
     *
     * @param context
     *        The {@code Activity}
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
    @SuppressWarnings({"WeakerAccess", "unused"})
    public ValuesCacheAdapterWrapper(@NonNull final BaseCacheAdapterFactory<ContentValues, R, E, D> factory,
                                                             final boolean  support,
                                                             final boolean  supportCursorAdapter,
                                     @NonNull                final Activity context,
                                     @LayoutRes              final int      layoutId,
                                     @NonNull @Size(min = 1) final String[] from,
                                     @NonNull @Size(min = 1) final    int[] to) {
        this(context, layoutId, init(factory, support, supportCursorAdapter, context, layoutId, from, to));
    }

    private ValuesCacheAdapterWrapper(@NonNull   final Context context,
                                      @LayoutRes final int     layoutId,
                                      @NonNull   final BaseCacheAdapter<ContentValues, R, E, D> baseCacheAdapter) {
        super(baseCacheAdapter, new ContentValuesRecyclerViewAdapter<>(context, layoutId,
                        ((DefaultArrayAdapter<ContentValues>) baseCacheAdapter.getArrayAdapter()).getFrom(),
                        ((DefaultArrayAdapter<ContentValues>) baseCacheAdapter.getArrayAdapter()).getTo(),
                        baseCacheAdapter),
                (ContentValuesDataConverter<R, E, D>) baseCacheAdapter.getConverter());
    }

    private static <R, E, D> BaseCacheAdapter<ContentValues, R, E, D> init(
            @NonNull   final Activity context,
            @LayoutRes final int      layoutId,
                       final boolean  support,
                       final boolean  supportCursorAdapter) {
        final Set<String>           fromSet = Utils.newSet();
        final int[]                 to      = BaseCacheAdapter.getViewsBinding(context, layoutId, fromSet);
        final String[]              from    = fromSet.toArray(new String[0]);
        return init(support, supportCursorAdapter, context, layoutId, from, to);
    }

    private static <R, E, D> BaseCacheAdapter<ContentValues, R, E, D> init(
                                    final boolean  support,
                                    final boolean  supportCursorAdapter,
            @NonNull                final Activity context,
            @LayoutRes              final int      layout,
            @NonNull @Size(min = 1) final String[] from,
            @NonNull @Size(min = 1) final    int[] to) {
        return init(new BaseCacheAdapterFactory<>(), support, supportCursorAdapter,
                context, layout, from, to);
    }

    private static <R, E, D> BaseCacheAdapter<ContentValues, R, E, D> init(
            @NonNull                final BaseCacheAdapterFactory<ContentValues, R, E, D> factory,
                                    final boolean                                         support,
                                    final boolean                                         supportCursorAdapter,
            @NonNull                final Activity                                        context,
            @LayoutRes              final int                                             layout,
            @NonNull @Size(min = 1) final String[]                                        from,
            @NonNull @Size(min = 1) final    int[]                                        to) {

        final BaseCacheAdapter<ContentValues, R, E, D> adapter =
                factory.getAdapter(context, layout, from, to,
                    new DefaultArrayAdapter<>(context, layout, getDataBinder(context, from, to)),
                    new ContentValuesDataConverter<>(from), support, supportCursorAdapter);

        CoreLogger.log(Level.INFO, "instantiated " + adapter.getClass().getName());
        return adapter;
    }

    private static DataBinder<ContentValues> getDataBinder(@NonNull                final Context  context,
                                                           @NonNull @Size(min = 1) final String[] from,
                                                           @NonNull @Size(min = 1) final int   [] to) {
        return new DataBinder<ContentValues>(context, from, to) {
            @SuppressWarnings("unused")
            @Override
            protected Object getValue(@NonNull ContentValues item, int index) {
                return ValuesCacheAdapterWrapper.getValue(item, index, mFrom, true);
            }
        };
    }

    private static Object getValue(@NonNull                final ContentValues item,
                                                           final int           index,
                                   @NonNull @Size(min = 1) final String[]      from,
                                                           final boolean       notSilent) {
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
     * The {@link ContentValues}-based implementation of the {@code BaseRecyclerViewAdapter}.
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
            extends BaseRecyclerViewAdapter<ContentValues, R, E, D> {

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
         *        The {@code BaseCacheAdapter} to wrap
         */
        @SuppressWarnings("WeakerAccess")
        public ContentValuesRecyclerViewAdapter(@NonNull                final Context  context,
                                                @LayoutRes              final int      layoutId,
                                                @NonNull @Size(min = 1) final String[] from,
                                                @NonNull @Size(min = 1) final int   [] to,
                                                @NonNull final BaseCacheAdapter<ContentValues, R, E, D> baseCacheAdapter) {
            super(baseCacheAdapter, getDataBinder(context, from, to), layoutId);
            CoreLogger.log("ContentValuesRecyclerViewAdapter instantiated");

            mViewInflater = new ViewInflater(context, layoutId);
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, final int viewType) {
            return mViewHolderCreator != null ? super.onCreateViewHolder(parent, viewType):
                   new ViewHolder(mViewInflater.inflate(parent)) {};
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class ContentValuesDataConverter<R, E, D> extends BaseDataConverter<ContentValues, R, E, D> {

        private final String[]                                          mFrom;

        @SuppressWarnings("unused")
        private ContentValuesDataConverter(@NonNull @Size(min = 1) final String[] from) {
            mFrom = from;
        }

        @SuppressWarnings("unchecked")
        private D getData(final Cursor cursor) {
            return (D) mConverter.getData(cursor, null);
        }
        
        @SuppressWarnings("unused")
        @Override
        public Collection<ContentValues> convert(@NonNull final BaseResponse<R, E, D> baseResponse) {
            List<ContentValues> result = null;
            final Cursor        cursor = baseResponse.getCursor();

            try {
                D data = baseResponse.getResult();

                if (data == null) data = getData(cursor);
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
                Utils.close(cursor);
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
        protected ContentValues convertSingle(@NonNull final Object data) {
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
