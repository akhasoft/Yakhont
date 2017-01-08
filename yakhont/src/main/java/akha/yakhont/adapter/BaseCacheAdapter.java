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

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.SupportHelper;
import akha.yakhont.loader.BaseResponse;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
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

import com.squareup.picasso.Picasso;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

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

    private final       ArrayConverter<T, R, E, D>          mConverter;

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
    public interface ArrayConverter<T, R, E, D> {

        /**
         * Converts data in {@code BaseResponse} to collection.
         *
         * @param baseResponse
         *        The {@code BaseResponse}
         *
         * @return  The Collection
         */
        Collection<? extends T> convert(BaseResponse<R, E, D> baseResponse);
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
        @SuppressWarnings("UnusedReturnValue")
        Cursor swapCursor(Cursor cursor);
        void setAdapterViewBinder(ViewBinder viewBinder);
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
         */
        void update(BaseResponse<R, E, D> data, boolean isMerge);

        /**
         * Resets the array adapter.
         */
        void resetArray();

        /**
         * Returns the cursor adapter component.
         *
         * @return  The BaseCursorAdapter
         */
        BaseCursorAdapter getCursorAdapter();
    }

    /**
     * The API related to data merging.
     */
    public interface Mergeable {

        /**
         * Returns the "merge" flag.
         *
         * @return  {@code true} to merge the newly loaded data with already existing, {@code false} otherwise
         */
        boolean isMerge();
    }

    /**
     * This class can be used by external clients of {@code BaseCacheAdapter} to bind values to views.
     */
    @SuppressWarnings("unused")
    public interface ViewBinder {

        /**
         * Binds the value to the specified view.
         * When binding is handled by this {@code ViewBinder}, this method must return {@code true}.
         * If this method returns {@code false}, {@code BaseCacheAdapter} will attempts to handle the binding on its own.
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

    /**
     * Initialises a newly created {@code BaseCacheAdapter} object.
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
     * @param arrayAdapter
     *        The BaseArrayAdapter
     *
     * @param converter
     *        The ArrayConverter
     */
    @SuppressWarnings("WeakerAccess")
    public BaseCacheAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                            @NonNull @Size(min = 1) final String[]          from,
                            @NonNull @Size(min = 1) final    int[]          to,
                            @NonNull final BaseArrayAdapter<T>              arrayAdapter,
                            @NonNull final ArrayConverter  <T, R, E, D>     converter) {
        this(SupportHelper.getBaseCursorAdapter(context, layoutId, from, to), arrayAdapter, converter);
    }

    /**
     * Initialises a newly created {@code BaseCacheAdapter} object.
     *
     * @param baseCacheAdapter
     *        The BaseCacheAdapter
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public BaseCacheAdapter(@NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter) {
        this(baseCacheAdapter.mCursorAdapter, baseCacheAdapter.mArrayAdapter, baseCacheAdapter.mConverter);
    }

    /**
     * Initialises a newly created {@code BaseCacheAdapter} object.
     *
     * @param cursorAdapter
     *        The BaseCursorAdapter
     *
     * @param arrayAdapter
     *        The BaseArrayAdapter
     *
     * @param converter
     *        The ArrayConverter
     */
    @SuppressWarnings("WeakerAccess")
    public BaseCacheAdapter(@NonNull final BaseCursorAdapter                   cursorAdapter,
                            @NonNull final BaseArrayAdapter<T>                 arrayAdapter,
                            @NonNull final ArrayConverter  <T, R, E, D>        converter) {

        mArrayAdapter   = arrayAdapter;
        mConverter      = converter;
        mCursorAdapter  = cursorAdapter;

        setCurrentAdapter(false);
    }

    /**
     * Returns the cursor adapter component.
     *
     * @return  The BaseCursorAdapter
     */
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
     *
     * @param baseResponse
     *        The new data
     */
    @SuppressWarnings("unused")
    public void update(@NonNull final BaseResponse<R, E, D> baseResponse) {
        update(baseResponse, false);
    }

    /**
     * Updates the adapter with a new data.
     *
     * @param data
     *        The new data
     *
     * @param isMerge
     *        {@code true} if new data should be merged with existing ones, {@code false} otherwise
     */
    public void update(@NonNull final BaseResponse<R, E, D> data, final boolean isMerge) {
        switch (data.getSource()) {
            case NETWORK:
                updateArray(mConverter.convert(data), isMerge);
                break;

            case CACHE:
            case TIMEOUT:
                updateCursor(data.getCursor());
                break;

            default:
                CoreLogger.logError("unknown source " + data.getSource());
                break;
        }
    }

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
     */
    public void resetArray() {
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                mArrayAdapter.clear();
            }
        });
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
        final View[] viewHelper = new View[1];

        visitView(view, new ViewVisitor() {
            @Override
            public boolean handle(final View view) {
                if (!(view instanceof AbsListView)) return false;

                if (viewHelper[0] == null) viewHelper[0] = view;
                return true;
            }
        });

        CoreLogger.log(viewHelper[0] == null ? Level.ERROR: Level.DEBUG, "list view: " + viewHelper[0]);
        return viewHelper[0];
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static int[] getViewsBinding(@NonNull final Context context, @LayoutRes final int layoutId,
                                        @NonNull final LinkedHashSet<String> set) {
        final Map<Integer, String>  map             = new LinkedHashMap<>();
        final Resources             resources       = context.getResources();

        visitView(LayoutInflater.from(context).inflate(layoutId, null, false), new ViewVisitor() {
            @Override
            public boolean handle(final View view) {
                final int id = view.getId();
                if (id != View.NO_ID && view.getVisibility() == View.VISIBLE
                        && (view instanceof TextView || view instanceof ImageView))
                    map.put(id, resources.getResourceEntryName(id));
                return false;
            }
        });

        CoreLogger.log(map.size() > 0 ? Level.DEBUG: Level.ERROR, "views binding: totally " + map.size());
        final int[] ids = new int[map.size()];
        set.clear();
        for (final Map.Entry<Integer, String> entry: map.entrySet()) {
            ids[set.size()] = entry.getKey();
            set.add(entry.getValue());
            CoreLogger.log("views binding: added " + entry.getValue());
        }
        return ids;
    }

    private static boolean visitView(@NonNull final View view, @NonNull final ViewVisitor visitor) {
        if (visitor.handle(view))
            return true;
        if (view instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++)
                if (visitView(viewGroup.getChildAt(i), visitor)) return true;
        }
        return false;
    }

    private interface ViewVisitor {
        boolean handle(View view);
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
    public Object getItem(int position) {
        return mBaseAdapter.getItem(position);
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
    @TargetApi(Build.VERSION_CODES.M)
    public static class ApiMCacheAdapter<T, R, E, D> extends BaseCacheAdapter<T, R, E, D> implements ThemedSpinnerAdapter {

        /**
         * Initialises a newly created {@code ApiMCacheAdapter} object.
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
         * @param arrayAdapter
         *        The BaseArrayAdapter
         *
         * @param converter
         *        The ArrayConverter
         */
        public ApiMCacheAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                @NonNull @Size(min = 1) final String[]          from,
                                @NonNull @Size(min = 1) final    int[]          to,
                                @NonNull final BaseArrayAdapter<T>              arrayAdapter,
                                @NonNull final ArrayConverter  <T, R, E, D>     converter) {
            super(context, layoutId, from, to, arrayAdapter, converter);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        @SuppressWarnings("unchecked")
        public Resources.Theme getDropDownViewTheme() {
            return ((ThemedSpinnerAdapter) (isCursorAdapter() ? getCursorAdapter(): getArrayAdapter())).getDropDownViewTheme();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void setDropDownViewTheme(Resources.Theme theme) {
            if (isCursorAdapter())
                ((ThemedSpinnerAdapter) getCursorAdapter()).setDropDownViewTheme(theme);
            else
                getArrayAdapter() .setDropDownViewTheme(theme);
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
            implements android.support.v7.widget.ThemedSpinnerAdapter {

        private final android.support.v7.widget.ThemedSpinnerAdapter.Helper     mDropDownHelper;

        @LayoutRes
        private final int                                                       mLayoutId;

        /**
         * Initialises a newly created {@code SupportCacheAdapter} object.
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
         * @param arrayAdapter
         *        The BaseArrayAdapter
         *
         * @param converter
         *        The ArrayConverter
         */
        public SupportCacheAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                   @NonNull @Size(min = 1) final String[]        from,
                                   @NonNull @Size(min = 1) final    int[]        to,
                                   @NonNull final BaseArrayAdapter<T>            arrayAdapter,
                                   @NonNull final ArrayConverter  <T, R, E, D>   converter) {
            super(context, layoutId, from, to, arrayAdapter, converter);

            mDropDownHelper = new android.support.v7.widget.ThemedSpinnerAdapter.Helper(context);
            mLayoutId       = layoutId;
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
            if (convertView == null) convertView = mDropDownHelper.getDropDownViewInflater().inflate(mLayoutId, parent, false);

            return super.getDropDownView(position, convertView, parent);
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

        /**
         * Returns a new {@code BaseCacheAdapter} instance.
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
         * @param arrayAdapter
         *        The BaseArrayAdapter
         *
         * @param converter
         *        The ArrayConverter
         *
         * @param support
         *        {@code true} to return the {@code SupportCacheAdapter} instance, {@code false} otherwise
         *
         * @return  The {@code BaseCacheAdapter} instance
         */
        public BaseCacheAdapter<T, R, E, D> getAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                                       @NonNull @Size(min = 1) final String[]         from,
                                                       @NonNull @Size(min = 1) final    int[]         to,
                                                       @NonNull final BaseArrayAdapter<T>             arrayAdapter,
                                                       @NonNull final ArrayConverter  <T, R, E, D>    converter,
                                                       final boolean support) {
            if (support)
                return new SupportCacheAdapter<>(context, layoutId, from, to, arrayAdapter, converter);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return new ApiMCacheAdapter   <>(context, layoutId, from, to, arrayAdapter, converter);
            else
                return new BaseCacheAdapter   <>(context, layoutId, from, to, arrayAdapter, converter);
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void bindImageView(@NonNull final Context context,
                                     @NonNull final ImageView imageView, final Object value) {
        final String strValue = getString(value);
        try {
            imageView.setImageResource(Integer.parseInt(strValue));
        }
        catch (NumberFormatException e) {
            Picasso.with(context).load(getString(value)).into(imageView);
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static String getString(final Object value) {
        return value == null ? null: value instanceof Exception ? null:
                value instanceof byte[] ? "": value.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends {@code ArrayAdapter} to provide additional view binding possibilities
     * (the same way as in {@link android.widget.SimpleCursorAdapter}).
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
     */
    @SuppressWarnings("WeakerAccess")
    public static abstract class BaseArrayAdapter<T> extends ArrayAdapter<T> {

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final         String[]                        mFrom;
        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final         int   []                        mTo;

        private final           LayoutInflater                  mInflater;

        @LayoutRes
        private final           int                             mLayoutId;

        private                 ViewBinder                      mViewBinder;

        /**
         * Initialises a newly created {@code BaseArrayAdapter} object.
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
         */
        @SuppressWarnings("WeakerAccess")
        protected BaseArrayAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                   @NonNull @Size(min = 1) final String[] from,
                                   @NonNull @Size(min = 1) final int   [] to) {
            super(context, layoutId);

            mFrom       = from;
            mTo         = to;

            mLayoutId   = layoutId;
            mInflater   = LayoutInflater.from(context);
        }

        /**
         * Gets the registered {@code ViewBinder} (if any).
         *
         * @return  Thee {@code ViewBinder} or null
         */
        @SuppressWarnings("unused")
        public ViewBinder getAdapterViewBinder() {
            return mViewBinder;
        }

        /**
         * Registers the {@code ViewBinder}. Most implementations should use {@link BaseCacheAdapter#setAdapterViewBinder} instead.
         *
         * @param viewBinder
         *        The ViewBinder
         */
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public void setAdapterViewBinder(final ViewBinder viewBinder) {
            mViewBinder = viewBinder;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            final View mainView = convertView == null ? mInflater.inflate(mLayoutId, parent, false): convertView;

            final T item = getItem(position);
            if (item == null) {
                CoreLogger.logError("item is null, position = " + position);
                return mainView;
            }

            for (int i = 0; i < mTo.length; i++) {
                final View view = mainView.findViewById(mTo[i]);
                if (view != null)
                    setViewValue(view, item, i);
                else
                    CoreLogger.logError("view not found, index = " + i);
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
                bindImageView(getContext(), (ImageView) view, value);

            else
                CoreLogger.logError("not supported view type, index = " + index);
        }

        /**
         * Gets value from the given item.
         *
         * @param item
         *        The item
         *
         * @param index
         *        The value's index
         *
         * @return  The item's value at the specified index
         */
        protected abstract Object getValue(@NonNull final T item, final int index);
    }
}
