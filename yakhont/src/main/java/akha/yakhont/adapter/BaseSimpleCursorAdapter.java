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

import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.view.View;
import android.widget.SimpleCursorAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.Size;

import java.lang.ref.WeakReference;

/**
 * Extends {@code SimpleCursorAdapter} to provide additional view binding possibilities.
 *
 * @author akha
 */
public class BaseSimpleCursorAdapter extends SimpleCursorAdapter implements BaseCursorAdapter {

    private final WeakReference<Context>    mContext;

    private BaseCacheAdapter.ViewBinder     mViewBinder;
    private BaseCacheAdapter.DataConverter  mDataConverter;

    /**
     * Initialises a newly created {@code BaseSimpleCursorAdapter} object.
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
    @TargetApi  (      Build.VERSION_CODES.HONEYCOMB)
    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    public BaseSimpleCursorAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                   @NonNull @Size(min = 1) final String[] from,
                                   @NonNull @Size(min = 1) final    int[] to) {
        super(context, layoutId, null, from, to, 0);
        mContext = new WeakReference<>(context);
        init();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "deprecation", "unused", "UnusedParameters", "RedundantSuppression"})
    public BaseSimpleCursorAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                   @NonNull @Size(min = 1) final String[] from,
                                   @NonNull @Size(min = 1) final    int[] to  , final int ignored) {
        super(context, layoutId, null, from, to);
        mContext = new WeakReference<>(context);
        init();
    }

    private void init() {
        //noinspection Convert2Lambda
        setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
                return BaseSimpleCursorSupportAdapter.setViewValueHelper(
                        mViewBinder, mContext, view, cursor, columnIndex);
            }
        });
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean isSupport() {
        final boolean result = false;
        CoreLogger.log("BaseSimpleCursorAdapter.isSupport() == " + result);
        return result;
    }

    /**
     * Gets the registered {@code ViewBinder} (if any).
     *
     * @return  The {@code ViewBinder} or null
     */
    @SuppressWarnings("unused")
    public BaseCacheAdapter.ViewBinder getAdapterViewBinder() {
        return mViewBinder;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused", "UnusedReturnValue"})
    @Override
    public void setAdapterViewBinder(final BaseCacheAdapter.ViewBinder viewBinder) {
        mViewBinder = viewBinder;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public void setDataConverter(final BaseCacheAdapter.DataConverter dataConverter) {
        BaseSimpleCursorSupportAdapter.checkDataConverter(dataConverter, mDataConverter);
        mDataConverter = dataConverter;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    @CallSuper
    public Cursor swapCursor(final Cursor cursor) {
        return super.swapCursor(BaseSimpleCursorSupportAdapter.swapCursor(mDataConverter, cursor));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public Cursor getItemCursor(final int position) {
        return BaseSimpleCursorSupportAdapter.getItemCursor(position, getCursor());
    }
}
