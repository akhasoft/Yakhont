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

import akha.yakhont.adapter.BaseCacheAdapter.DataBinder;
import akha.yakhont.adapter.BaseCacheAdapter.ViewBinder;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView.Adapter;
import android.support.v7.widget.RecyclerView.ViewHolder;

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
public abstract class BaseRecyclerViewAdapter<T, R, E, D, VH extends ViewHolder> extends Adapter<VH> {

    private final BaseCacheAdapter<T, R, E, D>      mBaseCacheAdapter;
    private final DataBinder      <T>               mDataBinder;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public BaseRecyclerViewAdapter(@NonNull final BaseCacheAdapter<T, R, E, D> baseCacheAdapter,
                                   @NonNull final DataBinder<T> dataBinder) {
        mBaseCacheAdapter = baseCacheAdapter;
        mDataBinder       = dataBinder;
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
     * Registers the {@code ViewBinder}. Most implementations should use {@link ValuesCacheAdapterWrapper#setAdapterViewBinder} instead.
     *
     * @param viewBinder
     *        The ViewBinder
     */
    public void setAdapterViewBinder(final ViewBinder viewBinder) {
        mDataBinder.setAdapterViewBinder(viewBinder);
    }

    /**
     * Please refer to the base method description.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(VH holder, int position) {
        mDataBinder.bind(position, (T) mBaseCacheAdapter.getItem(position), holder.itemView);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int getItemCount() {
        return mBaseCacheAdapter.getCount();
    }
}
