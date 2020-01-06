/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.loader;

import akha.yakhont.CoreLogger;
import akha.yakhont.Core;               // for javadoc
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.CursorHandler;
import akha.yakhont.Core.Utils.DataStore;
import akha.yakhont.CoreReflection;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.LoadParameters;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Build;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The <code>BaseResponse</code> class represents the results of loading data.
 *
 * @param <R>
 *        The type of network response
 *
 * @param <E>
 *        The type of error (if any)
 *
 * @param <D>
 *        The type of data
 *
 * @see BaseLiveData
 * @see BaseViewModel

 * @author akha
 */
public class BaseResponse<R, E, D> {

    /**
     * The source of data.
     */
    public enum Source {
        /** The cache. */
        CACHE,
        /** The network. */
        NETWORK,
        /** The loading process was cancelled because of timeout. */
        TIMEOUT,
        /** The loading process was cancelled because of some unknown reason. */
        UNKNOWN
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public  static final    String[]                    MIN_COLUMNS       = new String[] {BaseColumns._ID};
    /** @exclude */ @SuppressWarnings("JavaDoc")
    public  static final    Cursor                      EMPTY_CURSOR      = new MatrixCursor(MIN_COLUMNS, 0);

    private static          String                      sNewLine;
    private static          int                         sBytesQtyForArray;

    private final           LoadParameters              mParameters;
    private final           R                           mResponse;
    private final           E                           mError;
    private                 D                           mData;
    private final           Cursor                      mCursor;
    private                 Collection<ContentValues>   mContentValues;
    private final           Source                      mSource;
    private final           Throwable                   mThrowable;

    private final           DataStore                   mStore            = new DataStore();

    static {
        init();
    }

    /**
     * Cleanups static fields in BaseResponse; normally called from {@link Core#cleanUp()}.
     */
    public static void cleanUp() {
        init();
    }

    private static void init() {
        sNewLine            = System.getProperty("line.separator");
        sBytesQtyForArray   = 64;
    }

    /**
     * Initialises a newly created {@code BaseResponse} object.
     *
     * @param parameters
     *        The data loading parameters
     *
     * @param source
     *        The source of data
     */
    public BaseResponse(final LoadParameters parameters, @NonNull final Source source) {
        this(parameters, null, null, null, null, source, null);
    }

    /**
     * Initialises a newly created {@code BaseResponse} object.
     *
     * @param parameters
     *        The data loading parameters
     *
     * @param data
     *        The data
     *
     * @param response
     *        The network response
     *
     * @param cursor
     *        The cursor
     *
     * @param error
     *        The error
     *
     * @param source
     *        The source of data
     *
     * @param throwable
     *        The additional error info (normally if error is not an instance of Throwable)
     */
    public BaseResponse(final LoadParameters parameters, final D data, final R response, final Cursor cursor,
                        final E error, @NonNull final Source source, final Throwable throwable) {
        mParameters         = parameters;
        mData               = data;
        mResponse           = response;
        mCursor             = cursor;
        mSource             = source;
        mError              = error;
        mThrowable          = throwable;
    }

    /**
     * Sets some data to keep in this {@link BaseResponse}.
     *
     * @param key
     *        The key
     *
     * @param value
     *        The data
     *
     * @param <V>
     *        The type of data
     *
     * @return  The previous data for the given key (or null)
     *
     * @see #getUserData
     */
    @SuppressWarnings("unused")
    public <V> V setUserData(final String key, final V value) {
        return mStore.setData(key, value);
    }

    /**
     * Returns the data (associated with the given key) kept in this {@link BaseResponse}.
     *
     * @param key
     *        The key
     *
     * @param <V>
     *        The type of data
     *
     * @return  The data for the given key (or null)
     *
     * @see #setUserData
     */
    @SuppressWarnings({"unchecked", "unused"})
    public <V> V getUserData(final String key) {
        return (V) mStore.getData(key);
    }

    /**
     * Returns the data loading parameters.
     *
     * @return  The data loading parameters
     */
    public LoadParameters getParameters() {
        return mParameters;
    }

    /**
     * Returns the network response.
     *
     * @return  The network response
     */
    @SuppressWarnings("unused")
    public R getResponse() {
        return mResponse;
    }

    /**
     * Returns the cursor.
     *
     * @return  The cursor
     */
    public Cursor getCursor() {
        return mCursor;
    }

    /**
     * Returns the loaded data.
     *
     * @return  The loaded data
     */
    public D getResult() {
        return mData;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void setResult(final D data) {
        mData = data;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void setValues(final Collection<ContentValues> values) {
        mContentValues = values;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public Collection<ContentValues> getValues() {
        return mContentValues;
    }

    /**
     * Returns the error.
     *
     * @return  The error
     */
    @SuppressWarnings("unused")
    public E getError() {
        return mError;
    }

    /**
     * If error is an instance of {@link Throwable}, returns the error itself;
     * otherwise, returns {@link Exception} wrapper for error.
     *
     * @return  The error as {@link Throwable}
     */
    @SuppressWarnings("WeakerAccess")
    public Throwable getErrorAsThrowable() {
        return mError == null ? null: mError instanceof Throwable ? (Throwable) mError:
                new Exception(mError.toString());
    }

    /**
     * If error is not null returns error as {@link Throwable}; otherwise, returns throwable.
     *
     * @return  The error or throwable
     *
     * @see #getErrorAsThrowable
     */
    public Throwable getErrorOrThrowable() {
        return mError == null ? mThrowable: getErrorAsThrowable();
    }

    /**
     * Returns the he additional error info (if any).
     *
     * @return  The additional error info
     */
    @SuppressWarnings("unused")
    public Throwable getThrowable() {
        return mThrowable;
    }

    /**
     * Returns the source of data.
     *
     * @return  The source of data
     */
    @NonNull
    public Source getSource() {
        return mSource;
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        final Locale locale = Utils.getLocale();

        builder.append(String.format(locale, "source: %s, data class: %s, error: %s%s",
                mSource == null ? null: mSource.name(), mData == null ? null: mData.getClass().getName(),
                mError, sNewLine));
        builder.append(String.format(locale, "more error info: %s%s", mThrowable, sNewLine));

        builder.append(String.format(locale, "data loading parameters: %s%s",
                mParameters == null ? "null": mParameters, sNewLine));

        if (mData == null)
            builder.append("no data").append(sNewLine);
        else {
            if (!(mData instanceof byte[]) && CoreReflection.isNotSingle(mData)) {
                final List<Object> data = CoreReflection.getObjects(mData, true);
                if (data == null)
                    builder.append(String.format(locale, "data: null%s", sNewLine));
                else {
                    builder.append(String.format(locale, "data: size %d%s", data.size(), sNewLine));

                    for (int i = 0; i < data.size(); i++)
                        builder.append(String.format(locale, "[%d] %s%s", i,
                                handleData(data.get(i), locale), sNewLine));
                }
            }
            else
                builder.append("data: ").append(handleData(mData, locale)).append(sNewLine);
        }

        if (mCursor == null)
            builder.append("no cursor");
        else if (Utils.cursorHelper(mCursor, new LogCursorHandler(builder, locale),
                0, false, true))
            builder.delete(builder.length() - 1, builder.length());
        else
            builder.append("can't log cursor");

        return builder.toString();
    }

    /**
     * Defines how many bytes should be printed in log for byte arrays (the default value is 64).
     *
     * @param bytesQtyForArray
     *        The quantity of bytes
     *
     * @see CoreLogger#toHex(byte[], int, int, Locale, Charset)
     */
    @SuppressWarnings("unused")
    public static void setBytesQtyForArray(final int bytesQtyForArray) {
        if (bytesQtyForArray > 0) sBytesQtyForArray = bytesQtyForArray;
    }

    private static String handleData(final Object data, @NonNull final Locale locale) {
        return data == null ? null: data instanceof byte[] ? (((byte[]) data).length == 0 ?
                sNewLine + "empty byte[]": CoreLogger.toHex((byte[]) data, 0,
                sBytesQtyForArray, locale, null)): data.toString();
    }

    private static class LogCursorHandler implements CursorHandler {

        private final StringBuilder                     mBuilder;
        private final Locale                            mLocale;

        private LogCursorHandler(@NonNull final StringBuilder builder, @NonNull final Locale locale) {
            mLocale     = locale;
            mBuilder    = builder;
            mBuilder.append("cursor:").append(sNewLine);
        }

        @Override
        public boolean handle(final Cursor cursor) {
            for (int i = 0; i < cursor.getColumnCount(); i++)
                mBuilder.append(String.format(mLocale, "%s%s == %s", i == 0 ? "": ", ",
                        cursor.getColumnName(i), getString(cursor, i, mLocale)));
            mBuilder.append(sNewLine);
            return true;
        }

        private static String getString(@NonNull final Cursor cursor, final int columnIndex,
                                        @NonNull final Locale locale) {
            final Object data = getData(cursor, columnIndex);
            return data == null ? null: String.format(locale, "[%s]", data instanceof byte[] ?
                    handleData(data, locale) + sNewLine: data instanceof Throwable ? "error": data.toString());
        }
    }

    /**
     * Returns the value of the requested column (from the result set returned by a database query) as an Object.
     *
     * @param cursor
     *        The cursor
     *
     * @param columnIndex
     *        The zero-based index of the target column
     *
     * @return  The value of the requested column (or Exception)
     */
    @Nullable
    @SuppressLint("ObsoleteSdkInt")
    public static Object getData(@NonNull final Cursor cursor, final int columnIndex) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                final int type = cursor.getType(columnIndex);
                switch (type) {
                    case Cursor.FIELD_TYPE_NULL:
                        return null;
                    case Cursor.FIELD_TYPE_BLOB:
                        return cursor.getBlob(columnIndex);
                    case Cursor.FIELD_TYPE_INTEGER:
                        return cursor.getLong(columnIndex);
                    case Cursor.FIELD_TYPE_FLOAT:
                        return cursor.getDouble(columnIndex);
                    default:
                        break;
                }
            }
            return cursor.getString(columnIndex);
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
            return exception;
        }
    }
}
