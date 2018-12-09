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

import akha.yakhont.CoreLogger;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.Size;
import android.view.View;
import android.widget.SimpleCursorAdapter;

import java.lang.ref.WeakReference;

public class BaseSimpleCursorAdapter extends SimpleCursorAdapter implements BaseCursorAdapter {

    private final WeakReference<Context>    mContext;

    private BaseCacheAdapter.ViewBinder     mViewBinder;
    private BaseCacheAdapter.DataConverter  mDataConverter;

    @TargetApi  (      Build.VERSION_CODES.HONEYCOMB)
    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    public BaseSimpleCursorAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                   @NonNull @Size(min = 1) final String[] from,
                                   @NonNull @Size(min = 1) final    int[] to) {
        super(context, layoutId, null, from, to, 0);
        mContext = new WeakReference<>(context);
        init();
    }

    @SuppressWarnings({"deprecation", "unused", "UnusedParameters"})
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
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                return BaseSimpleCursorSupportAdapter.setViewValueHelper(
                        mViewBinder, mContext, view, cursor, columnIndex);
            }
        });
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean isSupport() {
        final boolean result = false;
        CoreLogger.log("BaseSimpleCursorAdapter.isSupport() == " + result);
        return result;
    }

    @SuppressWarnings("unused")
    public BaseCacheAdapter.ViewBinder getAdapterViewBinder() {
        return mViewBinder;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused", "UnusedReturnValue"})
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

    @Override
    @CallSuper
    public Cursor swapCursor(Cursor cursor) {
        return super.swapCursor(BaseSimpleCursorSupportAdapter.swapCursor(mDataConverter, cursor));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public Cursor getItemCursor(int position) {
        return BaseSimpleCursorSupportAdapter.getItemCursor(position, getCursor());
    }
}
