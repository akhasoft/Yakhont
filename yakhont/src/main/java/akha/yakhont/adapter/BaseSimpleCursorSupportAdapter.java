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
import akha.yakhont.loader.BaseResponse;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.cursoradapter.widget.SimpleCursorAdapter;

import java.lang.ref.WeakReference;

/**
 * Extends {@code SimpleCursorAdapter} to provide additional view binding possibilities.
 *
 * @author akha
 */
public class BaseSimpleCursorSupportAdapter extends SimpleCursorAdapter implements BaseCursorAdapter {

    private final WeakReference<Context>    mContext;

    private BaseCacheAdapter.ViewBinder     mViewBinder;
    private BaseCacheAdapter.DataConverter  mDataConverter;

    /**
     * Initialises a newly created {@code BaseSimpleCursorSupportAdapter} object.
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
    public BaseSimpleCursorSupportAdapter(@NonNull                final Context  context,
                                          @LayoutRes              final    int   layoutId,
                                          @NonNull @Size(min = 1) final String[] from,
                                          @NonNull @Size(min = 1) final    int[] to) {
        super(context, layoutId, null, from, to, 0);
        mContext = new WeakReference<>(context);
        init();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "deprecation", "unused"})
    public BaseSimpleCursorSupportAdapter(@NonNull                    final Context  context ,
                                          @LayoutRes                  final    int   layoutId,
                                          @NonNull @Size(min = 1)     final String[] from    ,
                                          @NonNull @Size(min = 1)     final    int[] to      ,
                                          @SuppressWarnings("unused") final    int   ignored) {
        super(context, layoutId, null, from, to);
        mContext = new WeakReference<>(context);
        init();
    }

    private void init() {
        //noinspection Convert2Lambda
        setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                return setViewValueHelper(mViewBinder, mContext, view, cursor, columnIndex);
            }
        });
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean isSupport() {
        final boolean result = true;
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
        checkDataConverter(dataConverter, mDataConverter);
        mDataConverter = dataConverter;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    @CallSuper
    public Cursor swapCursor(Cursor cursor) {
        return super.swapCursor(swapCursor(mDataConverter, cursor));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public Cursor getItemCursor(int position) {
        return getItemCursor(position, getCursor());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static boolean setViewValueHelper(final BaseCacheAdapter.ViewBinder viewBinder,
                                             final WeakReference<Context>      context,
                                             final View view, final Cursor cursor, final int columnIndex) {
        final Object data    = BaseResponse.getData(cursor, columnIndex);
        final String strData = BaseCacheAdapter.getString(data);

        if (viewBinder != null && viewBinder.setViewValue(view,
                data instanceof Exception ? null: data,
                strData == null ? "": strData))
            return true;

        if (view instanceof Checkable)
            ((Checkable) view).setChecked(Boolean.parseBoolean(strData));

        else if (view instanceof ImageView)
            BaseCacheAdapter.bindImageView(context.get(), (ImageView) view, data);

        else
            return false;

        return true;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void checkDataConverter(final BaseCacheAdapter.DataConverter newDataConverter,
                                          final BaseCacheAdapter.DataConverter oldDataConverter) {
        if (newDataConverter == null)
            CoreLogger.logWarning("about to set DataConverter to null");
        if (oldDataConverter != null)
            CoreLogger.logError("DataConverter was already set to " + oldDataConverter);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Cursor swapCursor(final BaseCacheAdapter.DataConverter dataConverter, Cursor cursor) {
        if (dataConverter == null)
            CoreLogger.logError("mDataConverter == null");
        else
            cursor = dataConverter.swapCursor(cursor);
        return cursor;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Cursor getItemCursor(final int position, final Cursor cursor) {
        if (position < 0) {
            CoreLogger.logError("wrong cursor position " + position);
            return null;
        }
        if (cursor == null) {
            CoreLogger.logWarning("getCursor() returns null");
            return null;
        }

        if (cursor.moveToPosition(position)) return cursor;

        CoreLogger.logError("can't move cursor to position " + position);
        return null;
    }
}
