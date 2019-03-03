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
import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.DataBinder;
import akha.yakhont.adapter.BaseCacheAdapter.DataBindingCacheAdapterWrapper;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.loader.BaseResponse;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil.ItemCallback;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import java.util.Collection;

/**
 * This adapter is just a wrapper for the {@link BaseCacheAdapter}.
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
 * @author akha
 */
@SuppressWarnings("WeakerAccess")
public class BaseRecyclerViewAdapter<T, R, E, D> extends Adapter<ViewHolder> {

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected final BaseCacheAdapter<T, R, E, D>                mBaseCacheAdapter;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected final DataBinder      <T>                         mDataBinder;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected       ViewHolderCreator                           mViewHolderCreator;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @LayoutRes
    protected final int                                         mLayoutId;

    /**
     * Called when RecyclerView needs a new RecyclerView.ViewHolder of the given type to represent an item.
     *
     * @see Adapter#onCreateViewHolder
     */
    @SuppressWarnings("unused")
    public interface ViewHolderCreator {

        /**
         * Called when RecyclerView needs a new RecyclerView.ViewHolder of the given type
         * to represent an item.
         *
         * @param parent
         *        The ViewGroup into which the new View will be added
         *
         * @param viewType
         *        The view type of the new View
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         *
         * @return  A new ViewHolder that holds a View of the given view type
         */
        ViewHolder onCreateViewHolder(ViewGroup parent, int viewType, @LayoutRes int layoutId);
    }

    /**
     * Initialises a newly created {@code BaseRecyclerViewAdapter} object.
     *
     * @param baseCacheAdapter
     *        The {@link BaseCacheAdapter}
     *
     * @param dataBinder
     *        The {@link DataBinder}
     */
    @SuppressWarnings("unused")
    public BaseRecyclerViewAdapter(@NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter,
                                            final DataBinder<T>                dataBinder) {
        this(baseCacheAdapter, dataBinder, Core.NOT_VALID_RES_ID);
    }

    /**
     * Initialises a newly created {@code BaseRecyclerViewAdapter} object.
     *
     * @param baseCacheAdapter
     *        The {@link BaseCacheAdapter}
     *
     * @param dataBinder
     *        The {@link DataBinder}
     *
     * @param layoutId
     *        The resource identifier of a layout file that defines the views
     */
    public BaseRecyclerViewAdapter(@NonNull   final BaseCacheAdapter<T, R, E, D> baseCacheAdapter,
                                              final DataBinder<T>                dataBinder,
                                   @LayoutRes final int                          layoutId) {
        mBaseCacheAdapter = baseCacheAdapter;
        mDataBinder       = dataBinder;
        mLayoutId         = layoutId;
    }

    /**
     * Gets the registered {@code ViewBinder} (if any).
     *
     * @return  Thee {@code ViewBinder} or null
     */
    @SuppressWarnings("unused")
    public ViewBinder getAdapterViewBinder() {
        return mDataBinder == null ? null: mDataBinder.getAdapterViewBinder();
    }

    /**
     * Registers the {@code ViewBinder}.
     * Most implementations should use {@link ValuesCacheAdapterWrapper#setAdapterViewBinder} instead.
     *
     * @param viewBinder
     *        The ViewBinder
     */
    public void setAdapterViewBinder(final ViewBinder viewBinder) {
        if (mDataBinder != null)
            mDataBinder.setAdapterViewBinder(viewBinder);
        else
            CoreLogger.logError("mDataBinder == null");
    }

    /**
     * Gets the registered {@code ViewHolderCreator} (if any).
     *
     * @return  Thee {@code ViewHolderCreator} or null
     */
    @SuppressWarnings("unused")
    public ViewHolderCreator getViewHolderCreator() {
        return mViewHolderCreator;
    }

    /**
     * Registers the {@code ViewHolderCreator}.
     *
     * @param viewHolderCreator
     *        The ViewHolderCreator
     */
    public void setViewHolderCreator(@NonNull final ViewHolderCreator viewHolderCreator) {
        mViewHolderCreator = viewHolderCreator;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        onBindViewHolderItem(holder, position, null, false);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void onBindViewHolderItem(@NonNull ViewHolder holder, int position,
                                     final T item, final boolean useItem) {
        if (mDataBinder != null)
            mDataBinder.bind(position, useItem ? item: mBaseCacheAdapter.getItem(position), holder.itemView);
        else
            CoreLogger.logError("mDataBinder == null");
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (mViewHolderCreator == null)
            throw new RuntimeException("please set ViewHolder creator via call to setViewHolderCreator()");

        if (mLayoutId == Core.NOT_VALID_RES_ID)
            CoreLogger.logWarning("item layout ID is not defined");

        return mViewHolderCreator.onCreateViewHolder(parent, viewType, mLayoutId);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int getItemCount() {
        return mBaseCacheAdapter.getCount();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The implementation of the {@link BaseRecyclerViewAdapter} to use with Data Binding Library.
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
    public static class DataBindingRecyclerViewAdapter<T, R, E, D>
            extends BaseRecyclerViewAdapter<T, R, E, D> {

        private final int                                       mDataBindingId;

        /**
         * Initialises a newly created {@code DataBindingRecyclerViewAdapter} object.
         *
         * @param id
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         *
         * @param baseCacheAdapter
         *        The {@link BaseCacheAdapter} to wrap
         */
        @SuppressWarnings("WeakerAccess")
        public DataBindingRecyclerViewAdapter(final int id, @LayoutRes final int layoutId,
                                              @NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter) {
            super(baseCacheAdapter, null, layoutId);
            CoreLogger.log("DataBindingRecyclerViewAdapter instantiated");

            mDataBindingId = id;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void setAdapterViewBinder(final ViewBinder viewBinder) {
            if (viewBinder != null) CoreLogger.logError("ViewBinder ignored: " + viewBinder);
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return mViewHolderCreator != null ? super.onCreateViewHolder(parent, viewType):
                    new DataBindingViewHolder(parent, mLayoutId, mDataBindingId);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @Override
        public void onBindViewHolderItem(@NonNull ViewHolder holder, int position,
                                         final T item, final boolean useItem) {
            DataBindingViewHolder.bind(holder, useItem ? item:
                    DataBindingCacheAdapterWrapper.getData(mBaseCacheAdapter, position), null);
        }
    }

    /**
     * Extends {@link ViewHolder ViewHolder} to support Data Binding Library.
     */
    public static class DataBindingViewHolder extends ViewHolder {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final ViewDataBinding                         mViewDataBinding;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final int                                     mDataBindingId;

        /**
         * Initialises a newly created {@code DataBindingViewHolder} object.
         *
         * @param view
         *        The View
         *
         * @param id
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        public DataBindingViewHolder(@NonNull final View view, final int id) {
            this(DataBindingUtil.bind(view), id);
        }

        /**
         * Initialises a newly created {@code DataBindingViewHolder} object.
         *
         * @param parent
         *        The ViewGroup into which the new View will be added
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         *
         * @param id
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         */
        @SuppressWarnings("WeakerAccess")
        public DataBindingViewHolder(@NonNull   final ViewGroup parent,
                                     @LayoutRes final int       layoutId, final int id) {
            this(DataBindingCacheAdapterWrapper.getViewDataBinding(parent, layoutId), id);
        }

        /**
         * Initialises a newly created {@code DataBindingViewHolder} object.
         *
         * @param binding
         *        The {@code ViewDataBinding} component
         *
         * @param id
         *        The BR id of the variable to be set (please refer to
         *        {@link ViewDataBinding#setVariable} for more info)
         */
        @SuppressWarnings("WeakerAccess")
        public DataBindingViewHolder(final ViewDataBinding binding, final int id) {
            super(DataBindingCacheAdapterWrapper.getView(binding));

            mViewDataBinding    = binding;
            mDataBindingId      = id;
        }

        /**
         * Performs data binding to the given view.
         *
         * @param data
         *        The data to bind
         */
        @SuppressWarnings("WeakerAccess")
        public void bind(final Object data) {
            DataBindingCacheAdapterWrapper.bind(data, mDataBindingId, mViewDataBinding);
        }

        /**
         * Performs data binding to the given view.
         *
         * @param data
         *        The data ({@link Collection} or array) to bind
         *
         * @param position
         *        The position in given {@link Collection} or array
         */
        @SuppressWarnings("WeakerAccess")
        public void bind(final Object data, final int position) {
            DataBindingCacheAdapterWrapper.bind(data, position, mDataBindingId, mViewDataBinding);
        }

        /**
         * Performs data binding to the given view.
         *
         * @param viewHolder
         *        The {@link ViewHolder} to bind
         *
         * @param data
         *        The data to bind
         *
         * @param position
         *        The position in given data
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
        @SuppressWarnings("unused")
        public static <R, E, D> void bind(final ViewHolder              viewHolder,
                                          final BaseResponse<R, E, D>   data,
                                          final Integer                 position) {
            if (data == null)
                CoreLogger.logError("BaseResponse for binding == null");
            else
                bind(viewHolder, data.getResult(), position);
        }

        /**
         * Performs data binding to the given view.
         *
         * @param viewHolder
         *        The {@link ViewHolder} to bind
         *
         * @param data
         *        The data to bind
         *
         * @param position
         *        The position in given data
         *
         * @param <D>
         *        The type of data
         */
        @SuppressWarnings("WeakerAccess")
        public static <D> void bind(final ViewHolder viewHolder,
                                    final D          data,
                                    final Integer    position) {
            if (data == null)
                CoreLogger.logError("data for binding == null");

            else if (viewHolder instanceof DataBindingViewHolder) {
                final DataBindingViewHolder tmp = (DataBindingViewHolder) viewHolder;
                if (position == null)
                    tmp.bind(data);
                else
                    tmp.bind(data, position);
            }

            else if (viewHolder != null)
                CoreLogger.logError("unsupported holder " + viewHolder.getClass().getName());
            else
                CoreLogger.logError("viewHolder == null");
        }

        /**
         * Returns the outermost {@code View} in the layout file associated with the Binding.
         *
         * @return  The {@code View}
         *
         * @see     ViewDataBinding#getRoot() ViewDataBinding.getRoot()
         */
        @SuppressWarnings({"unused", "WeakerAccess"})
        public View getView() {
            return DataBindingCacheAdapterWrapper.getView(mViewDataBinding);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final ItemCallback<T>   mDefaultDiffCallback    = new ItemCallback<T>() {
        @Override
        public boolean areItemsTheSame(@NonNull final T oldItem, @NonNull final T newItem) {
            return areContentsTheSame(oldItem, newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull final T oldItem, @NonNull final T newItem) {
            return oldItem.equals(newItem);
        }
    };

    /**
     * Gets the default diff callback to use with {@link PagedListAdapter};
     * to compare items uses {@link Object#equals(Object)}.
     *
     * @return  The {@code ItemCallback}
     */
    public ItemCallback<T> getDefaultDiffCallback() {
        return mDefaultDiffCallback;
    }

    /**
     * The implementation of the {@link PagedListAdapter} to use with Paging Library.
     *
     * @param <T>
     *        The type of {@code BaseResponse} values
     *
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     */
    public static class PagingRecyclerViewAdapter<T, R, E> extends PagedListAdapter<T, ViewHolder> {

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected final BaseRecyclerViewAdapter<T, R, E, T>     mRecyclerViewAdapter;

        /**
         * Initialises a newly created {@code PagingRecyclerViewAdapter} object.
         *
         * @param callback
         *        The {@link ItemCallback}
         *
         * @param adapter
         *        The {@link BaseRecyclerViewAdapter}
         *
         * @see     #getDefaultDiffCallback()
         */
        public PagingRecyclerViewAdapter(@NonNull final ItemCallback           <T         > callback,
                                         @NonNull final BaseRecyclerViewAdapter<T, R, E, T> adapter) {
            super(callback);

            mRecyclerViewAdapter = adapter;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return mRecyclerViewAdapter.onCreateViewHolder(parent, viewType);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            final T item = getItem(position);
            if (item == null) return;

            mRecyclerViewAdapter.onBindViewHolderItem(viewHolder, position, item, true);
        }
    }
}
