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

package akha.yakhont.adapter;

import akha.yakhont.Core;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CoreReflection;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.DataBindingArrayAdapter;
import akha.yakhont.adapter.BaseRecyclerViewAdapter.DataBindingRecyclerViewAdapter;
import akha.yakhont.loader.BaseConverter;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.Converter;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.ThemedSpinnerAdapter;
import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.Size;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  The {@link android.widget.Adapter Adapter} which was designed for {@yakhont.link CacheLoader}.
 *  Intended to use with {@yakhont.link BaseResponseLoaderWrapper}.
 *
 * @param <T>
 *        The type of {@code BaseResponse} values
 *
 * @param <R>
 *        The type of network response
 *
 * @param <E>
 *        The type of error (if any)
 *
 * @param <D>
 *        The type of data in this adapter
 *
 * @yakhont.see CacheLoader
 *
 * @author akha
 */
public class BaseCacheAdapter<T, R, E, D> implements ListAdapter, SpinnerAdapter, Filterable {
                                                                                                  /*
                             For Madmen Only
                               - Hermann Hesse, Steppenwolf
                                                                                                  */
    private             BaseAdapter                         mBaseAdapter;
    private final       BaseArrayAdapter<T>                 mArrayAdapter;
    private final       BaseCursorAdapter                   mCursorAdapter;

    private final       DataConverter<T, R, E, D>           mConverter;

    /**
     * The API to convert a {@code BaseResponse} to collection.
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
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
    @SuppressWarnings("WeakerAccess")
    public interface DataConverter<T, R, E, D> {

        /**
         * Converts {@code BaseResponse} to collection.
         *
         * @param baseResponse
         *        The {@code BaseResponse}
         *
         * @return  The Collection
         */
        Collection<T> convert(BaseResponse<R, E, D> baseResponse);

        /**
         * Converts {@code Cursor} to data.
         *
         * @param cursor
         *        The {@code Cursor}
         *
         * @param position
         *        The position
         *
         * @return  The Collection
         */
        T convert(Cursor cursor, int position);

        /**
         * Swaps cursor.
         *
         * @param cursor
         *        The {@code Cursor}
         *
         * @return  The {@code Cursor}
         */
        @SuppressWarnings("unused")
        Cursor swapCursor(Cursor cursor);

        /**
         * Returns {@code Converter} for data.
         *
         * @return  The Converter
         */
        Converter<D> getConverter();
    }
    
    /**
     * For internal use only.
     *
     * <p>As Martin Fowler pointed out, {@link <a href="http://martinfowler.com/ieeeSoftware/published.pdf">"Yet there’s something
     * to be said for the public–published distinction being more important than the more common public–private distinction."</a>}
     *
     * @yakhont.see BaseSimpleCursorAdapter
     */
    public interface BaseCursorAdapter extends ListAdapter, SpinnerAdapter, Filterable {
        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedReturnValue"})
        Cursor swapCursor(Cursor cursor);

        /** @exclude */ @SuppressWarnings("JavaDoc")
        void setAdapterViewBinder(ViewBinder viewBinder);

        /** @exclude */ @SuppressWarnings("JavaDoc")
        void setDataConverter(DataConverter dataConverter);

        /** @exclude */ @SuppressWarnings("JavaDoc")
        Cursor getItemCursor(int position);
    }

    /**
     * The API to work with adapters (for example, {@link ValuesCacheAdapterWrapper}).
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
    public interface CacheAdapter<R, E, D> {

        /**
         * Updates the adapter with a new data.
         *
         * @param data
         *        The new data
         *
         * @param isMerge
         *        {@code true} if new data should be merged with existing ones, {@code false} otherwise
         *
         * @param onLoadFinished
         *        The loaded data adjustment
         */
        void update(BaseResponse<R, E, D> data, boolean isMerge, final Runnable onLoadFinished);

        /**
         * Resets the array adapter.
         */
        @SuppressWarnings("unused")
        void resetArray();

        /**
         * Returns the cursor adapter component.
         *
         * @return  The BaseCursorAdapter
         */
        @SuppressWarnings("unused")
        BaseCursorAdapter getCursorAdapter();
    }

    /**
     * This class can be used by external clients of {@code BaseCacheAdapter} to bind values to views.
     */
    @SuppressWarnings("unused")
    public interface ViewBinder {

        /** To use as a {@link #setViewValue} return type (the value is {@value}). */
        boolean VIEW_BOUND = true;

        /**
         * Binds the value to the specified view.
         * When binding is handled by this {@code ViewBinder}, this method must return {@link #VIEW_BOUND};
         * otherwise, {@code BaseCacheAdapter} will attempts to handle the binding on its own.
         *
         * @param view
         *        The view to bind the data to
         *
         * @param data
         *        The data to bind to the view
         *
         * @param textRepresentation
         *        The String representation of the supplied data: it is either the result of data.toString() or an empty String but it is never null
         *
         * @return  {@code true} if the value was bound to the view, {@code false} otherwise
         */
        boolean setViewValue(View view, Object data, String textRepresentation);
    }

    @SuppressWarnings("WeakerAccess")
    public BaseCacheAdapter(@NonNull                final Activity          context,
                            @LayoutRes              final    int            layoutId,
                            @NonNull @Size(min = 1) final String[]          from,
                            @NonNull @Size(min = 1) final    int[]          to,
                            @NonNull final BaseArrayAdapter <T>             arrayAdapter,
                            @NonNull final DataConverter    <T, R, E, D>    converter,
                            final boolean                          support) {
        this(support ? new BaseSimpleCursorSupportAdapter(context, layoutId, from, to):
                       new BaseSimpleCursorAdapter       (context, layoutId, from, to),
                arrayAdapter, converter);
    }

    /**
     * Initialises a newly created {@code BaseCacheAdapter} object.
     *
     * @param baseCacheAdapter
     *        The BaseCacheAdapter
     */
    @SuppressWarnings({"unused", "WeakerAccess", "CopyConstructorMissesField"})
    public BaseCacheAdapter(@NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter) {
        this(baseCacheAdapter.mCursorAdapter, baseCacheAdapter.mArrayAdapter, baseCacheAdapter.mConverter);
    }

    /**
     * Initialises a newly created {@code BaseCacheAdapter} object.
     *
     * @param cursorAdapter
     *        The {@code BaseCursorAdapter}
     *
     * @param arrayAdapter
     *        The {@code BaseArrayAdapter}
     *
     * @param converter
     *        The {@code DataConverter}
     */
    @SuppressWarnings("WeakerAccess")
    public BaseCacheAdapter(@NonNull final BaseCursorAdapter                   cursorAdapter,
                            @NonNull final BaseArrayAdapter<T>                 arrayAdapter,
                            @NonNull final DataConverter   <T, R, E, D>        converter) {

        mArrayAdapter   = arrayAdapter;
        mCursorAdapter  = cursorAdapter;
        mConverter      = converter;

        mCursorAdapter.setDataConverter(converter);

        setCurrentAdapter(false);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public DataConverter<T, R, E, D> getConverter() {
        return mConverter;
    }

    /**
     * Returns the cursor adapter component.
     *
     * @return  The BaseCursorAdapter
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public BaseCursorAdapter getCursorAdapter() {
        return mCursorAdapter;
    }

    /**
     * Returns the array adapter component.
     *
     * @return  The BaseArrayAdapter
     */
    @NonNull
    @SuppressWarnings("WeakerAccess")
    public BaseArrayAdapter<T> getArrayAdapter() {
        return mArrayAdapter;
    }

    /**
     * Updates the adapter with a new data.
     *  Most implementations should use {@link BaseCacheAdapterWrapper#update} instead.
     *
     * @param data
     *        The new data
     *
     * @param isMerge
     *        {@code true} if new data should be merged with existing ones, {@code false} otherwise
     *
     * @param onLoadFinished
     *        The loaded data adjustment
     */
    @SuppressWarnings("WeakerAccess")
    public void update(@NonNull final BaseResponse<R, E, D> data, boolean isMerge,
                       final Runnable onLoadFinished) {
        final Source source = data.getSource();
        switch (source) {

            case CACHE:
                final Cursor cursor = data.getCursor();
                D            result = data.getResult();

                if (result == null) result = mConverter.getConverter().getData(cursor);
                if (result == null) {
                    customize(onLoadFinished);
                    updateCursor(cursor);
                    break;
                }
                else {                          // fall through
                    data.setResult(result);
                    isMerge = false;
                }

            case NETWORK:
                customize(onLoadFinished);
                updateArray(mConverter.convert(data), isMerge);
                break;

            case TIMEOUT:
            case UNKNOWN:
                CoreLogger.logWarning("nothing to update 'cause of " + source);
                break;

            default:
                CoreLogger.logError("unknown source " + source);
                break;
        }
    }

    private void customize(final Runnable onLoadFinished) {
        if (onLoadFinished != null) onLoadFinished.run();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void addAll(final Collection<? extends T> collection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            mArrayAdapter.addAll(collection);
        else
            for (final T object: collection)
                mArrayAdapter.add(object);
    }

    /**
     * Updates the array adapter with a new data.
     *
     * @param collection
     *        The new data
     *
     * @param isMerge
     *        {@code true} if new data should be merged with existing ones, {@code false} otherwise
     */
    @MainThread
    @SuppressWarnings("WeakerAccess")
    public void updateArray(final Collection<? extends T> collection, final boolean isMerge) {
        setCurrentAdapter(true);

        if (!isMerge)           mArrayAdapter.clear();
        if (collection != null) addAll(collection);
    }

    /**
     * Resets the array adapter.
     *  Most implementations should use {@link ValuesCacheAdapterWrapper#resetArray} instead.
     */
    @SuppressWarnings("WeakerAccess")
    public void resetArray() {
        mArrayAdapter.clear();
    }

    /**
     * Updates the cursor adapter with a new data.
     *
     * @param cursor
     *        The new data
     */
    @MainThread
    @SuppressWarnings("WeakerAccess")
    public void updateCursor(final Cursor cursor) {
        setCurrentAdapter(false);

        mCursorAdapter.swapCursor(cursor != null && cursor.getCount() > 0 ? cursor: null);
    }

    /**
     * Sets adapter to use.
     *
     * @param isArray
     *        {@code true} means the array adapter, {@code false} - the cursor one
     */
    @SuppressWarnings("WeakerAccess")
    public void setCurrentAdapter(final boolean isArray) {
        mBaseAdapter = isArray ? mArrayAdapter: (BaseAdapter) mCursorAdapter;
    }

    /**
     * Returns {@code true} if current adapter in use is the cursor one, {@code false} otherwise.
     *
     * @return  The current adapter flag
     */
    @SuppressWarnings("WeakerAccess")
    protected boolean isCursorAdapter() {
        return mBaseAdapter == mCursorAdapter;
    }

    /**
     * Registers the {@code ViewBinder} to use.
     * Most implementations should use {@link ValuesCacheAdapterWrapper#setAdapterViewBinder} instead.
     *
     * @param viewBinder
     *        The ViewBinder
     *
     * @return  This {@code BaseCacheAdapter} object
     *
     * @see BaseArrayAdapter#setAdapterViewBinder
     * @see BaseCursorAdapter#setAdapterViewBinder
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public BaseCacheAdapter<T, R, E, D> setAdapterViewBinder(final ViewBinder viewBinder) {
        mArrayAdapter .setAdapterViewBinder(viewBinder);
        mCursorAdapter.setAdapterViewBinder(viewBinder);
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static View findListView(@NonNull final View view) {
        //noinspection Convert2Lambda
        return ViewHelper.findView(view, new ViewHelper.ViewVisitor() {
            @SuppressWarnings("unused")
            @Override
            public boolean handle(final View listView) {
                return listView instanceof AbsListView || listView instanceof RecyclerView;
            }
        });
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static int[] getViewsBinding(@NonNull final Context context, @LayoutRes final int layoutId,
                                        @NonNull final Set<String> set) {
        final Map<Integer, String>  map             = Core.Utils.newMap();
        final Resources             resources       = context.getResources();
        final ViewInflater          viewInflater    = new ViewInflater(context, layoutId);

        //noinspection Convert2Lambda
        ViewHelper.visitView(viewInflater.inflate(null), new ViewHelper.ViewVisitor() {
            @SuppressWarnings("unused")
            @Override
            public boolean handle(final View view) {
                final int id = view.getId();
                if (id != Core.NOT_VALID_VIEW_ID && view.getVisibility() == View.VISIBLE
                        && (view instanceof TextView || view instanceof ImageView))
                    map.put(id, resources.getResourceEntryName(id));

                // force to handle all views
                return !ViewHelper.VIEW_FOUND;
            }
        });
        CoreLogger.log(map.size() > 0 ? CoreLogger.getDefaultLevel(): Level.ERROR, "views binding: totally " + map.size());

        final int[] ids = new int[map.size()];
        set.clear();

        for (final Map.Entry<Integer, String> entry: map.entrySet()) {
            ids[set.size()]    = entry.getKey();
            final String value = entry.getValue();
            set.add(value);
            CoreLogger.log("views binding: added " + value);
        }
        return ids;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Please refer to the base method description.
     */
    @Override
    public Filter getFilter() {
        return isCursorAdapter() ? mCursorAdapter.getFilter(): mArrayAdapter.getFilter();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean areAllItemsEnabled() {
        return mBaseAdapter.areAllItemsEnabled();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean isEnabled(int position) {
        return mBaseAdapter.isEnabled(position);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return mBaseAdapter.getDropDownView(position, convertView, parent);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int getCount() {
        return mBaseAdapter.getCount();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public T getItem(int position) {
        return isCursorAdapter() ? getItemCursor(position): getItemArray(position);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected T getItemArray(int position) {
        return mArrayAdapter.getItem(position);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected T getItemCursor(int position) {
        final Cursor cursor = mCursorAdapter.getItemCursor(position);
        if (cursor == null) {
            CoreLogger.logError("can't retrieve item for cursor position " + position);
            return null;
        }
        return mConverter.convert(cursor, position);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public long getItemId(int position) {
        return mBaseAdapter.getItemId(position);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int getItemViewType(int position) {
        return mBaseAdapter.getItemViewType(position);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return mBaseAdapter.getView(position, convertView, parent);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int getViewTypeCount() {
        return mBaseAdapter.getViewTypeCount();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean hasStableIds() {
        return mBaseAdapter.hasStableIds();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean isEmpty() {
        return mBaseAdapter.isEmpty();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mCursorAdapter.registerDataSetObserver(observer);
        mArrayAdapter .registerDataSetObserver(observer);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mCursorAdapter.unregisterDataSetObserver(observer);
        mArrayAdapter .unregisterDataSetObserver(observer);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     *  The adapter intended to use on modern devices (API version &gt;= {@link android.os.Build.VERSION_CODES#M M}).
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data in this adapter
     */
    @TargetApi  (      Build.VERSION_CODES.M)
    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressWarnings("WeakerAccess")
    public static class ApiMCacheAdapter<T, R, E, D> extends BaseCacheAdapter<T, R, E, D> implements ThemedSpinnerAdapter {

        /**
         * Initialises a newly created {@code ApiMCacheAdapter} object.
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
         *
         * @param arrayAdapter
         *        The {@code BaseArrayAdapter}
         *
         * @param converter
         *        The {@code DataConverter}
         */
        @SuppressWarnings("WeakerAccess")
        public ApiMCacheAdapter(@NonNull                final Activity          context,
                                @LayoutRes              final    int            layoutId,
                                @NonNull @Size(min = 1) final String[]          from,
                                @NonNull @Size(min = 1) final    int[]          to,
                                @NonNull final BaseArrayAdapter<T>              arrayAdapter,
                                @NonNull final DataConverter   <T, R, E, D>     converter,
                                         final boolean                          support) {
            super(context, layoutId, from, to, arrayAdapter, converter, support);
        }

        private ApiMCacheAdapter(@SuppressWarnings("SameParameterValue")
                                 @NonNull   final BaseCursorAdapter             stub,
                                 @NonNull   final BaseArrayAdapter<T>           arrayAdapter,
                                 @NonNull   final DataConverter   <T, R, E, D>  converter) {
            super(stub, arrayAdapter, converter);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public Resources.Theme getDropDownViewTheme() {
            return (isCursorAdapter() ? (ThemedSpinnerAdapter) getCursorAdapter(): getArrayAdapter())
                    .getDropDownViewTheme();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void setDropDownViewTheme(Resources.Theme theme) {
            if (isCursorAdapter())
                ((ThemedSpinnerAdapter) getCursorAdapter()).setDropDownViewTheme(theme);
            else
                getArrayAdapter().setDropDownViewTheme(theme);
        }
    }

    @TargetApi  (      Build.VERSION_CODES.M)
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static class ApiMDataBindingCacheAdapter<R, E, D> extends ApiMCacheAdapter<Object, R, E, D> {

        @SuppressWarnings("WeakerAccess")
        public ApiMDataBindingCacheAdapter(@NonNull   final BaseArrayAdapter<Object>           arrayAdapter,
                                           @NonNull   final DataConverter   <Object, R, E, D>  converter) {
            super(DataBindingCacheAdapter.STUB, arrayAdapter, converter);
        }

        @Override
        public void setCurrentAdapter(final boolean isArray) {
            super.setCurrentAdapter(DataBindingCacheAdapter.getCurrentAdapterData());
            DataBindingCacheAdapter.checkArray(isArray);
        }
    }

    /**
     *  The adapter intended to use on devices with API version &lt; {@link android.os.Build.VERSION_CODES#M M}.
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data in this adapter
     */
    public static class SupportCacheAdapter<T, R, E, D> extends BaseCacheAdapter<T, R, E, D>
            implements androidx.appcompat.widget.ThemedSpinnerAdapter {

        private final androidx.appcompat.widget.ThemedSpinnerAdapter.Helper     mDropDownHelper;

        @LayoutRes
        private final int                                                       mLayoutId;

        /**
         * Initialises a newly created {@code SupportCacheAdapter} object.
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
         *
         * @param arrayAdapter
         *        The {@code BaseArrayAdapter}
         *
         * @param converter
         *        The {@code DataConverter}
         */
        @SuppressWarnings("WeakerAccess")
        public SupportCacheAdapter(@NonNull                final Activity       context,
                                   @LayoutRes              final    int         layoutId,
                                   @NonNull @Size(min = 1) final String[]       from,
                                   @NonNull @Size(min = 1) final    int[]       to,
                                   @NonNull final BaseArrayAdapter<T>           arrayAdapter,
                                   @NonNull final DataConverter   <T, R, E, D>  converter,
                                            final boolean                       support) {
            super(context, layoutId, from, to, arrayAdapter, converter, support);

            mDropDownHelper = init(context);
            mLayoutId       = layoutId;
        }

        private SupportCacheAdapter(@NonNull   final Activity                      context,
                                    @LayoutRes final int                           layoutId,
                                    @SuppressWarnings("SameParameterValue")
                                    @NonNull   final BaseCursorAdapter             stub,
                                    @NonNull   final BaseArrayAdapter<T>           arrayAdapter,
                                    @NonNull   final DataConverter   <T, R, E, D>  converter) {
            super(stub, arrayAdapter, converter);

            mDropDownHelper = init(context);
            mLayoutId       = layoutId;
        }

        private static androidx.appcompat.widget.ThemedSpinnerAdapter.Helper init(
                @NonNull final Activity context) {
            return new androidx.appcompat.widget.ThemedSpinnerAdapter.Helper(context);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public Resources.Theme getDropDownViewTheme() {
            return mDropDownHelper.getDropDownViewTheme();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void setDropDownViewTheme(Resources.Theme theme) {
            mDropDownHelper.setDropDownViewTheme(theme);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = mDropDownHelper.getDropDownViewInflater()
                    .inflate(mLayoutId, parent, false);
            return super.getDropDownView(position, convertView, parent);
        }
    }

    public static class SupportDataBindingCacheAdapter<R, E, D> extends SupportCacheAdapter<Object, R, E, D> {

        @SuppressWarnings("WeakerAccess")
        public SupportDataBindingCacheAdapter(@NonNull   final Activity                           context,
                                              @LayoutRes final int                                layoutId,
                                              @NonNull   final BaseArrayAdapter<Object>           arrayAdapter,
                                              @NonNull   final DataConverter   <Object, R, E, D>  converter) {
            super(context, layoutId, DataBindingCacheAdapter.STUB, arrayAdapter, converter);
        }

        @Override
        public void setCurrentAdapter(final boolean isArray) {
            super.setCurrentAdapter(DataBindingCacheAdapter.getCurrentAdapterData());
            DataBindingCacheAdapter.checkArray(isArray);
        }
    }

    /**
     *  The adapter intended to use with Data Binding Library.
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data in this adapter
     */
    public static class DataBindingCacheAdapter<R, E, D> extends BaseCacheAdapter<Object, R, E, D> {

        private static final BaseCursorAdapter                                  STUB = new BaseCursorAdapter() {
            @Override public boolean areAllItemsEnabled()                         { return true; }
            @Override public int     getCount()                                   { return    0; }
            @Override public View    getDropDownView(int p, View c, ViewGroup g)  { return null; }
            @Override public Filter  getFilter()                                  { return null; }
            @Override public Object  getItem(int p)                               { return null; }
            @Override public Cursor  getItemCursor(int p)                         { return null; }
            @Override public long    getItemId(int p)                             { return    0; }
            @Override public int     getItemViewType(int p)                       { return    0; }
            @Override public View    getView(int p, View c, ViewGroup g)          { return null; }
            @Override public int     getViewTypeCount()                           { return    0; }
            @Override public boolean hasStableIds()                               { return true; }
            @Override public boolean isEmpty()                                    { return true; }
            @Override public boolean isEnabled(int p)                             { return true; }
            @Override public void    registerDataSetObserver(DataSetObserver o)   {              }
            @Override public void    setAdapterViewBinder(ViewBinder v)           {              }
            @Override public void    setDataConverter(DataConverter d)            {              }
            @Override public Cursor  swapCursor(Cursor c)                         { return    c; }
            @Override public void    unregisterDataSetObserver(DataSetObserver o) {              }
        };

        /**
         * Initialises a newly created {@code DataBindingCacheAdapter} object.
         *
         * @param arrayAdapter
         *        The {@code BaseArrayAdapter}
         *
         * @param converter
         *        The {@code DataConverter}
         */
        @SuppressWarnings("WeakerAccess")
        public DataBindingCacheAdapter(@NonNull final BaseArrayAdapter<Object>           arrayAdapter,
                                       @NonNull final DataConverter   <Object, R, E, D>  converter) {
            super(STUB, arrayAdapter, converter);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void setCurrentAdapter(final boolean isArray) {
            super.setCurrentAdapter(getCurrentAdapterData());
            checkArray(isArray);
        }

        @SuppressWarnings("SameReturnValue")
        private static boolean getCurrentAdapterData() {
            return true;
        }

        private static void checkArray(final boolean isArray) {
            if (!isArray)
                CoreLogger.log(Level.WARNING, "BaseCursorAdapter ignored", true);
        }
    }

    /**
     * Factory used to create new {@code BaseCacheAdapter} instances.
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data in the adapter created
     */
    @SuppressWarnings("unused")
    public static class BaseCacheAdapterFactory<T, R, E, D> {

        /**
         * Initialises a newly created {@code BaseCacheAdapterFactory} object.
         */
        public BaseCacheAdapterFactory() {
        }

        @SuppressLint("ObsoleteSdkInt")
        public BaseCacheAdapter<T, R, E, D> getAdapter(
                @NonNull                final Activity                          context,
                @LayoutRes              final    int                            layoutId,
                @NonNull @Size(min = 1) final String[]                          from,
                @NonNull @Size(min = 1) final    int[]                          to,
                @NonNull                final BaseArrayAdapter<T>               arrayAdapter,
                @NonNull                final DataConverter   <T, R, E, D>      converter,
                                        final boolean                           support,
                                        final boolean                           supportCursorAdapter) {
            CoreLogger.log("supportCursorAdapter is " + supportCursorAdapter);

            if (support) {
                if (!supportCursorAdapter)
                    CoreLogger.logWarning("support mode is true, supportCursorAdapter is false");
                return new SupportCacheAdapter<>(context, layoutId, from, to, arrayAdapter,
                        converter, supportCursorAdapter);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return new ApiMCacheAdapter   <>(context, layoutId, from, to, arrayAdapter,
                        converter, supportCursorAdapter);
            else
                return new BaseCacheAdapter   <>(context, layoutId, from, to, arrayAdapter,
                        converter, supportCursorAdapter);
        }

        public BaseCacheAdapter<Object, R, E, D> getAdapter(
                @NonNull                final Activity                          context,
                @LayoutRes              final int                               layoutId,
                @NonNull                final BaseArrayAdapter<Object>          arrayAdapter,
                @NonNull                final DataConverter   <Object, R, E, D> converter,
                                        final boolean                           support) {
            if (support)
                return new SupportDataBindingCacheAdapter<>(context, layoutId, arrayAdapter, converter);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return new ApiMDataBindingCacheAdapter<>(arrayAdapter, converter);
            else
                return new DataBindingCacheAdapter    <>(arrayAdapter, converter);
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void bindImageView(final Context context, @NonNull final ImageView imageView, final Object value) {
        final String strValue = getString(value);
        try {
            imageView.setImageResource(Integer.parseInt(strValue));
        }
        catch (NumberFormatException e) {
            if (context == null)
                CoreLogger.logError("context for Picasso == null");
            else
                Picasso.with(context).load(strValue).into(imageView);
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static String getString(final Object value) {
        return value == null ? null: value instanceof Exception ? null:
               value instanceof byte[] ? "": value.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends {@code ArrayAdapter} to provide additional view binding possibilities.
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
     */
    public static abstract class BaseArrayAdapter<T> extends ArrayAdapter<T> {

        /**
         * Initialises a newly created {@code BaseArrayAdapter} object.
         *
         * @param context
         *        The {@link Context}
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         */
        @SuppressWarnings("WeakerAccess")
        protected BaseArrayAdapter(@NonNull   final Context context,
                                   @LayoutRes final int     layoutId) {
            super(context, layoutId);
        }

        /**
         * Gets the registered {@code ViewBinder} (if any).
         *
         * @return  The {@code ViewBinder} or null
         */
        @SuppressWarnings("unused")
        public abstract ViewBinder getAdapterViewBinder();

        /**
         * Registers the {@code ViewBinder}.
         * Most implementations should use {@link BaseCacheAdapterWrapper#setAdapterViewBinder} instead.
         *
         * @param viewBinder
         *        The ViewBinder
         */
        public abstract void setAdapterViewBinder(final ViewBinder viewBinder);
    }

    /**
     * Extends {@code BaseArrayAdapter} to provide default view binding possibilities
     * (the same way as in {@link SimpleCursorAdapter}).
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
     */
    @SuppressWarnings("WeakerAccess")
    public static class DefaultArrayAdapter<T> extends BaseArrayAdapter<T> {

        private final           DataBinder<T>                   mDataBinder;
        private final           ViewInflater                    mViewInflater;

        /**
         * Initialises a newly created {@code DefaultArrayAdapter} object.
         *
         * @param context
         *        The {@link Context}
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         *
         * @param dataBinder
         *        The {@link DataBinder}
         */
        protected DefaultArrayAdapter(@NonNull   final Context       context,
                                      @LayoutRes final int           layoutId,
                                      @NonNull   final DataBinder<T> dataBinder) {
            super(context, layoutId);

            mDataBinder     = dataBinder;
            mViewInflater   = new ViewInflater(context, layoutId);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public ViewBinder getAdapterViewBinder() {
            return mDataBinder.getAdapterViewBinder();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void setAdapterViewBinder(final ViewBinder viewBinder) {
            mDataBinder.setAdapterViewBinder(viewBinder);
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            return mDataBinder.bind(position, getItem(position),
                    convertView == null ? mViewInflater.inflate(parent): convertView);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public String[] getFrom() {
            return mDataBinder.mFrom;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public int[] getTo() {
            return mDataBinder.mTo;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class ViewInflater {

        private final           LayoutInflater                  mInflater;
        @LayoutRes
        private final           int                             mLayoutId;

        public ViewInflater(@NonNull final Context context, @LayoutRes final int layoutId) {
            mLayoutId   = layoutId;
            mInflater   = LayoutInflater.from(context);

            if (mLayoutId == Core.NOT_VALID_RES_ID)
                CoreLogger.logError("item layout ID is not defined");
        }

        public View inflate(final ViewGroup parent) {
            return mLayoutId == Core.NOT_VALID_RES_ID ? null:
                    mInflater.inflate(mLayoutId, parent, false);
        }

        @SuppressWarnings("unused")
        @LayoutRes
        public int getLayoutId() {
            return mLayoutId;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static abstract class DataBinder<T> {

        private   final         Context                         mContext;
        private                 ViewBinder                      mViewBinder;

        @SuppressWarnings("WeakerAccess")
        protected final         String[]                        mFrom;
        @SuppressWarnings("WeakerAccess")
        protected final         int   []                        mTo;

        public DataBinder(@NonNull final Context context,
                          @NonNull @Size(min = 1) final String[] from,
                          @NonNull @Size(min = 1) final int   [] to) {
            mContext    = context;

            mFrom       = from;
            mTo         = to;
        }

        public ViewBinder getAdapterViewBinder() {
            return mViewBinder;
        }

        public void setAdapterViewBinder(final ViewBinder viewBinder) {
            mViewBinder = viewBinder;
        }

        public View bind(final int position, final T item, final View mainView) {
            if (item == null) {
                CoreLogger.logError("item is null, position = " + position);
                return mainView;
            }

            for (int i = 0; i < mTo.length; i++) {
                final View view = mainView.findViewById(mTo[i]);
                if (view == null) {
                    CoreLogger.logError("view not found, index = " + i);
                    continue;
                }

                try {
                    setViewValue(view, item, i);
                }
                catch (Exception exception) {
                    CoreLogger.log("setViewValue failed", exception);
                }
            }

            return mainView;
        }

        private void setViewValue(@NonNull final View view, @NonNull final T item, final int index) {
            final Object    value = getValue(item, index);
            final String strValue = getString(value);

            if (mViewBinder != null && mViewBinder.setViewValue(view, value,
                    strValue == null ? "": strValue)) return;

            if (view instanceof Checkable)
                ((Checkable) view).setChecked(Boolean.parseBoolean(strValue));

            else if (view instanceof TextView)
                ((TextView) view).setText(strValue);

            else if (view instanceof ImageView)
                bindImageView(mContext, (ImageView) view, value);

            else
                CoreLogger.logError("not supported view type, index = " + index);
        }

        protected abstract Object getValue(@NonNull final T item, final int index);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The base abstract data converter for {@link BaseCacheAdapter}.
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
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
    public static abstract class BaseDataConverter<T, R, E, D> implements DataConverter<T, R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Converter<D>                                mConverter;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       List<T>                                     mCursorCache;

        /**
         * Sets the {@code Converter} component.
         *
         * @param converter
         *        The {@code Converter}
         *
         * @return  {@code true} if {@code Converter} set successfully, {@code false} otherwise
         */
        @SuppressWarnings("WeakerAccess")
        public boolean setConverter(final Converter<D> converter) {
            if (converter == null) {
                CoreLogger.logError("converter == null");
                return false;
            }
            if (mConverter != null)
                CoreLogger.logWarning("converter already set");

            mConverter = converter;
            return true;
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
        @Override
        public Converter<D> getConverter() {
            return mConverter;
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
        @Override
        public Cursor swapCursor(Cursor cursor) {
            mCursorCache = null;
            return cursor;
        }

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("unused")
        @Override
        public T convert(Cursor cursor, int position) {
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

        /**
         * Please refer to the base method description.
         */
        @SuppressWarnings("WeakerAccess")
        @NonNull
        protected List<T> convert(@NonNull final Object data) {
            final List<Object> objects = CoreReflection.getObjects(data);
            if (objects == null) return Collections.singletonList(convertSingle(data));

            final ArrayList<T> list = new ArrayList<>();
            for (final Object object: objects)
                if (!CoreReflection.isNotSingle(object))
                    list.add(convertSingle(object));

            return list;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected abstract T convertSingle(@NonNull final Object data);
    }

    /**
     * The base abstract wrapper for {@link BaseCacheAdapter}.
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
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
    public static abstract class BaseCacheAdapterWrapper<T, R, E, D>
            implements CacheAdapter<R, E, D> {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final BaseCacheAdapter       <T, R, E, D>         mBaseCacheAdapter;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final BaseRecyclerViewAdapter<T, R, E, D>         mBaseRecyclerViewAdapter;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final BaseDataConverter      <T, R, E, D>         mBaseDataConverter;

        /**
         * Initialises a newly created {@code BaseCacheAdapterWrapper} object.
         *
         * @param baseCacheAdapter
         *        The {@code BaseCacheAdapter}
         *
         * @param baseRecyclerViewAdapter
         *        The {@code BaseRecyclerViewAdapter}
         *
         * @param baseDataConverter
         *        The {@code BaseDataConverter}
         */
        @SuppressWarnings("WeakerAccess")
        protected BaseCacheAdapterWrapper(@NonNull final BaseCacheAdapter       <T, R, E, D> baseCacheAdapter,
                                          @NonNull final BaseRecyclerViewAdapter<T, R, E, D> baseRecyclerViewAdapter,
                                          @NonNull final BaseDataConverter      <T, R, E, D> baseDataConverter) {
            mBaseCacheAdapter           = baseCacheAdapter;
            mBaseRecyclerViewAdapter    = baseRecyclerViewAdapter;
            mBaseDataConverter          = baseDataConverter;
        }

        /**
         * Returns the {@code BaseCacheAdapter} wrapped by this object.
         *
         * @return  The BaseCacheAdapter
         */
        @NonNull
        @SuppressWarnings("unused")
        public BaseCacheAdapter<T, R, E, D> getAdapter() {
            return mBaseCacheAdapter;
        }

        /**
         * Returns the {@code ContentValuesRecyclerViewAdapter} wrapped by this object.
         *
         * @return  The ContentValuesRecyclerViewAdapter
         */
        @NonNull
        @SuppressWarnings("unused")
        public BaseRecyclerViewAdapter<T, R, E, D> getRecyclerViewAdapter() {
            return mBaseRecyclerViewAdapter;
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
            return mBaseDataConverter.setConverter(converter);
        }

        /**
         * Registers the {@code ViewBinder} to use.
         *
         * @param viewBinder
         *        The ViewBinder
         */
        public void setAdapterViewBinder(final ViewBinder viewBinder) {
            mBaseCacheAdapter       .setAdapterViewBinder(viewBinder);
            mBaseRecyclerViewAdapter.setAdapterViewBinder(viewBinder);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void update(@NonNull final BaseResponse<R, E, D> data, final boolean isMerge,
                           final Runnable onLoadFinished) {
            mBaseCacheAdapter.update(data, isMerge, onLoadFinished);
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
                    mBaseCacheAdapter.resetArray();
                    mBaseRecyclerViewAdapter.notifyDataSetChanged();
                }

                @NonNull
                @Override
                public String toString() {
                    return "BaseCacheAdapterWrapper.resetArray()";
                }
            });
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public BaseCursorAdapter getCursorAdapter() {
            return mBaseCacheAdapter.getCursorAdapter();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class DataBindingDataConverter<R, E, D> extends BaseDataConverter<Object, R, E, D> {

        @SuppressWarnings("unused")
        @Override
        public Collection<Object> convert(BaseResponse<R, E, D> baseResponse) {
            if (baseResponse == null) {
                CoreLogger.logError("baseResponse == null");
                return null;
            }
            final D data = baseResponse.getResult();

            if (CoreReflection.isNotSingle(data))
                return CoreReflection.getObjects(data);

            CoreLogger.log("single object " + data.getClass().getName());
            return Collections.singletonList(data);
        }

        @SuppressWarnings("unused")
        @Override
        protected Object convertSingle(@NonNull final Object data) {
            return data;
        }
    }

    /**
     * The wrapper for {@link BaseCacheAdapter} to use with Data Binding Library.
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
    public static class DataBindingCacheAdapterWrapper<R, E, D>
            extends BaseCacheAdapterWrapper<Object, R, E, D> {

        public DataBindingCacheAdapterWrapper(           final int       id,
                                              @NonNull   final Activity  context,
                                              @LayoutRes final int       layoutId,
                                                         final boolean   support) {
            this(id, context, layoutId, new BaseCacheAdapterFactory<>(), support);
        }

        @SuppressWarnings("WeakerAccess")
        public DataBindingCacheAdapterWrapper(
                           final int                                      id,
                @NonNull   final Activity                                 context,
                @LayoutRes final int                                      layoutId,
                @NonNull   final BaseCacheAdapterFactory<Object, R, E, D> factory,
                           final boolean                                  support) {
            this(id, layoutId, init(id, context, layoutId, factory, support));
        }

        private static <R, E, D> BaseCacheAdapter<Object, R, E, D> init(
                           final int                                      id,
                @NonNull   final Activity                                 context,
                @LayoutRes final int                                      layoutId,
                @NonNull   final BaseCacheAdapterFactory<Object, R, E, D> factory,
                           final boolean                                  support) {
            CoreLogger.log("DataBindingCacheAdapterWrapper instantiated");

            final DataBindingArrayAdapter< R, E, D> arrayAdapter =
                    new DataBindingArrayAdapter<>(context, layoutId, id);
            final BaseCacheAdapter<Object, R, E, D> result = factory.getAdapter(context, layoutId,
                    arrayAdapter, new DataBindingDataConverter<>(), support);

            arrayAdapter.setAdapter(result);
            return result;
        }

        private DataBindingCacheAdapterWrapper(
                           final int                               id,
                @LayoutRes final int                               layoutId,
                @NonNull   final BaseCacheAdapter<Object, R, E, D> baseCacheAdapter) {
            super(baseCacheAdapter, new DataBindingRecyclerViewAdapter<>(id, layoutId, baseCacheAdapter),
                    (DataBindingDataConverter<R, E, D>) baseCacheAdapter.getConverter());
        }
    }
}
