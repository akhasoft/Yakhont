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
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CoreReflection;
import akha.yakhont.adapter.BaseCacheAdapter.BaseArrayAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.DataBinder;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;
import akha.yakhont.loader.BaseResponse;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
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
    protected final BaseCacheAdapter<T, R, E, D>    mBaseCacheAdapter;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected final DataBinder      <T>             mDataBinder;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected       ViewHolderCreator               mViewHolderCreator;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @LayoutRes
    protected final int                             mLayoutId;

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
        if (mDataBinder != null)
            mDataBinder.bind(position, mBaseCacheAdapter.getItem(position), holder.itemView);
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
     * @param <R>
     *        The type of network response
     *
     * @param <E>
     *        The type of error (if any)
     *
     * @param <D>
     *        The type of data in the adapter
     */
    public static class DataBindingRecyclerViewAdapter<R, E, D>
            extends BaseRecyclerViewAdapter<Object, R, E, D> {

        private final   int                                     mDataBindingId;

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
                                              @NonNull final BaseCacheAdapter<Object, R, E, D> baseCacheAdapter) {
            super(baseCacheAdapter, null, layoutId);
            CoreLogger.log("DataBindingRecyclerViewAdapter instantiated");

            mDataBindingId  = id;
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

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            onBindViewHolder(mBaseCacheAdapter, viewHolder, position);
        }

        @SuppressWarnings("UnusedReturnValue")
        private static <T, R, E, D> T onBindViewHolder(
                @NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter,
                @NonNull final ViewHolder viewHolder, final int position) {
            final T data = baseCacheAdapter.getArrayAdapter().getItem(position);
            DataBindingViewHolder.bind(viewHolder, data, null);
            return data;
        }
    }

    /**
     * Extends {@link ViewHolder ViewHolder} to support Data Binding Library.
     */
    public static class DataBindingViewHolder extends ViewHolder {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final ViewDataBinding                             mViewDataBinding;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final int                                         mDataBindingId;

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
        @SuppressWarnings("WeakerAccess")
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
            this(getViewDataBinding(parent, layoutId), id);
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
            super(getView(binding));

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
            if (data == null) {
                CoreLogger.logError("data for binding is null");
                return;
            }
//          CoreLogger.log("DataBindingViewHolder, Data Binding ID " + mDataBindingId);

            mViewDataBinding.setVariable(mDataBindingId, data);
            mViewDataBinding.executePendingBindings();
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
            if (data == null) {
                CoreLogger.logError("data for binding is null, position " + position);
                return;
            }
            if (CoreReflection.isNotSingle(data)) {
                final Object tmp = CoreReflection.getObject(data, position);
                if (tmp == null)
                    CoreLogger.logError("can't bind data, position " + position);
                else
                    bind(tmp);
            }
            else {
                CoreLogger.logWarning("single object " + data.getClass().getName() +
                        ", position " + position + " ignored");
                bind(data);
            }
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
                                          final BaseResponse<R, E, D> data,
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
        public static <      D> void bind(final ViewHolder              viewHolder,
                                          final D                       data,
                                          final Integer                 position) {
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
         * Returns the {@code ViewDataBinding} component.
         *
         * @param parent
         *        The ViewGroup into which the new View will be added
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         *
         * @return  The {@code ViewDataBinding} component
         */
        @SuppressWarnings("WeakerAccess")
        public static ViewDataBinding getViewDataBinding(@NonNull   final ViewGroup parent,
                                                         @LayoutRes final int       layoutId) {
            return DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()),
                    layoutId, parent, false);
        }

        /**
         * Returns the outermost {@code View} in the layout file associated with the Binding.
         *
         * @return  The {@code View}
         *
         * @see     ViewDataBinding#getRoot() ViewDataBinding.getRoot()
         */
        @SuppressWarnings("WeakerAccess")
        public View getView() {
            return getView(mViewDataBinding);
        }

        /**
         * Returns the outermost {@code View} in the layout file associated with the Binding.
         *
         * @param viewDataBinding
         *        The {@code ViewDataBinding}
         *
         * @return  The {@code View}
         *
         * @see     ViewDataBinding#getRoot() ViewDataBinding.getRoot()
         */
        @SuppressWarnings("WeakerAccess")
        public static View getView(final ViewDataBinding viewDataBinding) {
            return viewDataBinding == null ? null: viewDataBinding.getRoot();
        }

        /**
         * Returns the outermost {@code View} in the layout file associated with the Binding.
         *
         * @param parent
         *        The ViewGroup into which the new View will be added
         *
         * @param layoutId
         *        The resource identifier of a layout file that defines the views
         *
         * @return  The {@code View}
         *
         * @see     ViewDataBinding#getRoot() ViewDataBinding.getRoot()
         */
        @SuppressWarnings("unused")
        public static View getView(@NonNull   final ViewGroup parent,
                                   @LayoutRes final int       layoutId) {
            return getView(getViewDataBinding(parent, layoutId));
        }
    }

    /**
     * Extends {@code ArrayAdapter} to work with Data binding Library.
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
    public static class DataBindingArrayAdapter<R, E, D> extends BaseArrayAdapter<Object> {

        @LayoutRes
        private final           int                             mLayoutId;

        private final           int                             mDataBindingId;

        private                 BaseCacheAdapter<Object, R, E, D>
                                                                mBaseCacheAdapter;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
        protected DataBindingArrayAdapter(@NonNull   final Context       context,
                                          @LayoutRes final int           layoutId,
                                                     final int           dataBindingId) {
            super(context, layoutId);
            CoreLogger.log("DataBindingArrayAdapter instantiated");

            mLayoutId       = layoutId;
            mDataBindingId  = dataBindingId;
        }

        /**
         * Sets the {@code BaseCacheAdapter} component.
         *
         * @param adapter
         *        The {@code BaseCacheAdapter}
         */
        @SuppressWarnings("unused")
        public void setAdapter(final BaseCacheAdapter<Object, R, E, D> adapter) {
            mBaseCacheAdapter = adapter;
        }

        /**
         * Please refer to the base method description.
         */
        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (mBaseCacheAdapter == null) {
                CoreLogger.logWarning("mBaseCacheAdapter == null");
                return convertView;
            }
            final DataBindingViewHolder viewHolder = convertView == null ?
                    new DataBindingViewHolder(parent, mLayoutId, mDataBindingId):
                    new DataBindingViewHolder(convertView,       mDataBindingId);

            DataBindingRecyclerViewAdapter.onBindViewHolder(mBaseCacheAdapter, viewHolder, position);
            return viewHolder.getView();
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public ViewBinder getAdapterViewBinder() {
            return null;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void setAdapterViewBinder(final ViewBinder viewBinder) {
            if (viewBinder != null)
                CoreLogger.log(Level.ERROR, "ignored viewBinder: " + viewBinder, true);
        }
    }
}
