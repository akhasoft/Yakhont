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

import akha.yakhont.Core;
import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.DataBinder;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;

import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.ViewGroup;

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
 * @param <VH>
 *        The type of ViewHolder in this adapter
 *
 * @author akha
 */
@SuppressWarnings("WeakerAccess")
public class BaseRecyclerViewAdapter<T, R, E, D, VH extends ViewHolder> extends Adapter<VH> {

    private final   BaseCacheAdapter<T, R, E, D>    mBaseCacheAdapter;
    private final   DataBinder      <T>             mDataBinder;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected       ViewHolderCreator<VH>           mViewHolderCreator;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @LayoutRes
    protected final int                             mLayoutId;

    /**
     * Called when RecyclerView needs a new RecyclerView.ViewHolder of the given type to represent an item.
     *
     * @see Adapter#onCreateViewHolder
     */
    @SuppressWarnings("unused")
    public interface ViewHolderCreator<VH extends ViewHolder> {

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
        VH onCreateViewHolder(ViewGroup parent, int viewType, @LayoutRes int layoutId);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public BaseRecyclerViewAdapter(@NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter,
                                   @NonNull final DataBinder<T> dataBinder) {
        this(baseCacheAdapter, dataBinder, Core.NOT_VALID_RES_ID);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public BaseRecyclerViewAdapter(@NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter,
                                   @NonNull final DataBinder<T> dataBinder,
                                   @LayoutRes final int layoutId) {
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
        return mDataBinder.getAdapterViewBinder();
    }

    /**
     * Registers the {@code ViewBinder}.
     * Most implementations should use {@link ValuesCacheAdapterWrapper#setAdapterViewBinder} instead.
     *
     * @param viewBinder
     *        The ViewBinder
     */
    public void setAdapterViewBinder(final ViewBinder viewBinder) {
        mDataBinder.setAdapterViewBinder(viewBinder);
    }

    /**
     * Gets the registered {@code ViewHolderCreator} (if any).
     *
     * @return  Thee {@code ViewHolderCreator} or null
     */
    @SuppressWarnings("unused")
    public ViewHolderCreator<VH> getViewHolderCreator() {
        return mViewHolderCreator;
    }

    /**
     * Registers the {@code ViewHolderCreator}.
     *
     * @param viewHolderCreator
     *        The ViewHolderCreator
     */
    public void setViewHolderCreator(@NonNull final ViewHolderCreator<VH> viewHolderCreator) {
        mViewHolderCreator = viewHolderCreator;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        mDataBinder.bind(position, mBaseCacheAdapter.getItem(position), holder.itemView);
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
}
