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
import akha.yakhont.adapter.BaseCacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCursorAdapter;
import akha.yakhont.loader.BaseResponse;

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
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;

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
     * Initialises a newly created {@code BaseCursorAdapter} object.
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
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    public BaseSimpleCursorAdapter(@NonNull final Context context, @LayoutRes final int layoutId,
                                   @NonNull @Size(min = 1) final String[] from,
                                   @NonNull @Size(min = 1) final    int[] to) {
        super(context, layoutId, null, from, to, 0);
        mContext = new WeakReference<>(context);
        init();
    }

    /**
     * Initialises a newly created {@code BaseCursorAdapter} object (for using {@link android.widget.SimpleCursorAdapter}
     * on devices with API version &lt; {@link android.os.Build.VERSION_CODES#HONEYCOMB HONEYCOMB}).
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
     * @param ignored
     *        ignored
     */
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
                final Object data    = BaseResponse.getData(cursor, columnIndex);
                final String strData = BaseCacheAdapter.getString(data);

                if (mViewBinder != null && mViewBinder.setViewValue(view,
                        data instanceof Exception ? null: data,
                        strData == null ? "": strData))
                    return true;

                if (view instanceof Checkable)
                    ((Checkable) view).setChecked(Boolean.parseBoolean(strData));

                else if (view instanceof ImageView)
                    BaseCacheAdapter.bindImageView(mContext.get(), (ImageView) view, data);

                else
                    return false;

                return true;
            }
        });
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean isSupport() {
        final boolean result = isSupportBody();
        CoreLogger.log("BaseSimpleCursorAdapter.isSupport() == " + result);
        return result;
    }

    private static boolean isSupportBody() {
        final String name = SimpleCursorAdapter.class.getName();
        final int pos = name.lastIndexOf('.');
        if (pos > 0) {
            final String pkg = name.substring(0, pos);
            CoreLogger.log("package for SimpleCursorAdapter is " + pkg);
            return "android.support.v4.widget".equals(pkg);
        }
        CoreLogger.logError("can not define class name for SimpleCursorAdapter, returned name is " + name);
        return true;
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

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused", "UnusedReturnValue"})
    @Override
    public void setAdapterViewBinder(final BaseCacheAdapter.ViewBinder viewBinder) {
        mViewBinder = viewBinder;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public void setDataConverter(final BaseCacheAdapter.DataConverter dataConverter) {
        if (dataConverter == null)
            CoreLogger.logWarning("about to set DataConverter to null");
        if (mDataConverter != null)
            CoreLogger.logError("DataConverter was already set to " + mDataConverter);
        mDataConverter = dataConverter;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    @CallSuper
    public Cursor swapCursor(Cursor cursor) {
        if (mDataConverter == null)
            CoreLogger.logError("mDataConverter == null");
        else
            cursor = mDataConverter.swapCursor(cursor);
        return super.swapCursor(cursor);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public Cursor getItemCursor(int position) {
        if (position < 0) {
            CoreLogger.logError("wrong cursor position " + position);
            return null;
        }
        final Cursor cursor = getCursor();
        if (cursor == null) return null;

        if (cursor.moveToPosition(position)) return cursor;

        CoreLogger.logError("can't move cursor to position " + position);
        return null;
    }
}
