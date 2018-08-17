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

package akha.yakhont.loader;

import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.CursorHandler;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.Type;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * @yakhont.see BaseLoader
 
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

    private static final String                   sNewLine                = System.getProperty("line.separator");

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public  static final    String[]              MIN_COLUMNS             = new String[] {BaseColumns._ID};
    
    /** @exclude */ @SuppressWarnings("JavaDoc")
    public  static final    Cursor                EMPTY_CURSOR            = new MatrixCursor(MIN_COLUMNS, 0);

    private final           R                     mResponse;
    private final           E                     mError;
    private                 D                     mData;
    private final           Cursor                mCursor;
    private                 ContentValues[]       mContentValues;
    private final           Source                mSource;
    private final           Throwable             mThrowable;

    /**
     * The API for data conversion.
     *
     * @param <D>
     *        The type of data
     */
    public interface ConverterHelper<D> {

        /**
         * Converts serialized data to object.
         *
         * @param string
         *        The additional information
         *
         * @param data
         *        The serialized data
         *
         * @return  The data object
         */
        D get(String string, byte[] data);
    }

    /**
     * Allows to get instance of {@link ConverterHelper}.
     *
     * @param <D>
     *        The type of data
     */
    public interface ConverterGetter<D> {

        /**
         * Returns instance of {@link ConverterHelper}.
         *
         * @param type
         *        The data type
         *
         * @return  The ConverterHelper
         */
        ConverterHelper<D> get(Type type);
    }

    /**
     * The API to convert data.
     *
     * @param <D>
     *        The type of data
     *
     * @see akha.yakhont.loader.BaseConverter
     */
    public interface Converter<D> {

        /**
         * Sets {@code ConverterGetter}.
         *
         * @param getter
         *        The {@code ConverterGetter}
         */
        void setConverterGetter(ConverterGetter<D> getter);

        /**
         * Returns the data to store in cache.
         *
         * @param string
         *        The string data
         *
         * @param bytes
         *        The bytes data
         *
         * @param cls
         *        The type of data
         *
         * @return  The ContentValues
         */
        ContentValues getValues(String string, byte[] bytes, Class cls);

        /**
         * Converts cursor to data.
         *
         * @param cursor
         *        The Cursor
         *
         * @return  The data
         */
        D getData(Cursor cursor);

        /**
         * Returns the type of last converted data.
         *
         * @return  The type of data (or null)
         */
        Type getType();
    }

    /**
     * Initialises a newly created {@code BaseResponse} object.
     *
     * @param source
     *        The source of data
     */
    public BaseResponse(@NonNull final Source source) {
        this(null, null, null, null, source, null);
    }

    /**
     * Initialises a newly created {@code BaseResponse} object.
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
    public BaseResponse(final D data, final R response, final Cursor cursor, final E error,
                        @NonNull final Source source, final Throwable throwable) {
        mData               = data;
        mResponse           = response;
        mCursor             = cursor;
        mSource             = source;
        mError              = error;
        mThrowable          = throwable;
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
    public void setValues(final ContentValues[] values) {
        mContentValues = values;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public ContentValues[] getValues() {
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
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        final Locale locale = Utils.getLocale();

        builder.append(String.format(locale, "%s, class: %s, error: %s%s",
                mSource.name(), mData == null ? null: mData.getClass().getName(),
                mError, sNewLine));
        builder.append(String.format(locale, "more error info: %s%s", mThrowable, sNewLine));

        if (mData == null)
            builder.append("no data").append(sNewLine);
        else {
            if (mData.getClass().isArray()) {
                final Object[] array = (Object[]) mData;
                builder.append(String.format(locale, "data: length %d%s", array.length, sNewLine));

                for (int i = 0; i < array.length; i++)
                    builder.append(String.format(locale, "[%d] %s%s", i, array[i], sNewLine));
            }
            else
                builder.append("data ").append(mData).append(sNewLine);
        }

        if (mCursor == null)
            builder.append("no cursor");
        else if (Utils.cursorHelper(mCursor, new LogCursorHandler(builder, locale),
                true, false, true))
            builder.delete(builder.length() - 1, builder.length());
        else
            builder.append("can't log cursor");

        return builder.toString();
    }

    private static class LogCursorHandler implements CursorHandler {

        private final StringBuilder         mBuilder;
        private final Locale                mLocale;

        private LogCursorHandler(@NonNull final StringBuilder builder, @NonNull final Locale locale) {
            mLocale     = locale;
            mBuilder    = builder;
            mBuilder.append("cursor:").append(sNewLine);
        }

        @Override
        public boolean handle(Cursor cursor) {
            for (int i = 0; i < cursor.getColumnCount(); i++)
                mBuilder.append(String.format(mLocale, "%s%s == %s", i == 0 ? "": ", ",
                        cursor.getColumnName(i), getString(cursor, i, mLocale)));
            mBuilder.append(sNewLine);
            return true;
        }

        private static String getString(@NonNull final Cursor cursor, final int columnIndex,
                                        @NonNull final Locale locale) {
            final Object data = getData(cursor, columnIndex);
            return data == null ? null: data instanceof byte[] ? String.format(locale, "[%s]",
                    Utils.toHex((byte[]) data, 8, false, locale, null)):
                    data instanceof Throwable ? "error": String.valueOf(data);
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
        catch (Exception e) {
            CoreLogger.log(Level.ERROR, "getData failed", e);
            return e;
        }
    }

    /**
     * Clears cache table.
     *
     * @param tableName
     *        The table name
     */
    public static void clearCache(final String tableName) {
        if (tableName == null) {
            CoreLogger.logError("tableName == null");
            return;
        }
        clearCache(Utils.getUri(tableName));
    }

    /**
     * Clears cache table.
     *
     * @param uri
     *        The URI
     */
    public static void clearCache(final Uri uri) {
        final String table = Utils.getLoaderTableName(uri);
        if (table == null) {
            CoreLogger.logWarning("not defined cache table name for clearing");
            return;
        }
        CoreLogger.logWarning("about to clear cache table " + table);
        //noinspection ConstantConditions
        Utils.getApplication().getContentResolver().delete(uri, null, null);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The class representing data loading parameters.
     */
    public static class LoadParameters {

        private final Integer               mLoaderId;
        private final boolean               mForceCache, mNoProgress, mMerge, mNoErrors, mSync;
        private final String                mError;

        private final static AtomicBoolean sSafe   = new AtomicBoolean(true);

        /**
         * Initialises a newly created {@code LoadParameters} object.
         */
        public LoadParameters() {
            this(null, false, false, false, false, false);
        }

        /**
         * Initialises a newly created {@code LoadParameters} object.
         *
         * @param loaderId
         *        The loader ID (or null)
         *
         * @param forceCache
         *        {@code true} to force loading data from cache, {@code false} otherwise
         *
         * @param noProgress
         *        {@code true} to not display loading progress, {@code false} otherwise
         *
         * @param merge
         *        {@code true} to merge the newly loaded data with already existing, {@code false} otherwise
         *
         * @param noErrors
         *        {@code true} to not display loading errors, {@code false} otherwise
         *
         * @param sync
         *        {@code true} to load data synchronously, {@code false} otherwise
         */
        public LoadParameters(final Integer loaderId,
                              final boolean forceCache, final boolean noProgress, final boolean merge,
                              final boolean noErrors,   final boolean sync) {
            mLoaderId       = loaderId;
            mForceCache     = forceCache;
            mNoProgress     = noProgress;
            mMerge          = merge;
            mNoErrors       = noErrors;
            mSync           = sync;

            CoreLogger.log("loader ID: " + loaderId + ", force cache: " + forceCache +
                    ", no progress: " + noProgress + ", merge: " + merge + ", no errors: " +
                    noErrors + ", sync: " + sync);

            if (mForceCache && mMerge) {
                mError = "wrong combination: force cache and merge";
                reportNotSafe();
                CoreLogger.logError(mError);
            }
            else
                mError = null;
        }

        private void reportNotSafe() {
            if (!sSafe.get()) throw new LoadParametersException(mError);
        }

        /**
         * Validates the data loading parameters provided.
         *
         * @return  {@code true} if the parameters are consistent, {@code false} otherwise
         */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean checkArguments() {
            reportNotSafe();
            return mError == null;
        }

        /**
         * Gets the loader ID.
         *
         * @return  The loader ID
         */
        public Integer getLoaderId() {
            return mLoaderId;
        }

        /**
         * Gets the "force cache" flag.
         *
         * @return  The "force cache" flag
         */
        public boolean getForceCache() {
            return mForceCache;
        }

        /**
         * Gets the "no progress" flag.
         *
         * @return  The "no progress" flag
         */
        public boolean getNoProgress() {
            return mNoProgress;
        }

        /**
         * Gets the "merge" flag.
         *
         * @return  The "merge" flag
         */
        public boolean getMerge() {
            return mMerge;
        }

        /**
         * Gets the "no errors" flag.
         *
         * @return  The "no errors" flag
         */
        public boolean getNoErrors() {
            return mNoErrors;
        }

        /**
         * Gets the "sync" flag.
         *
         * @return  The "sync" flag
         */
        public boolean getSync() {
            return mSync;
        }

        /**
         * Sets safe mode: if {@code false}, the {@link LoadParametersException} will be thrown
         * in case of not consistent parameters. The default value is {@code true}.
         *
         * @param value
         *        The value to set
         *
         * @return  The previous value
         */
        @SuppressWarnings("unused")
        public static boolean setSafeMode(final boolean value) {
            return sSafe.getAndSet(value);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public String toString() {
            return String.format(CoreLogger.getLocale(),
                    "force cache %b, no progress %b, no errors %b, merge %b, sync %b, params error %s, loader id %s",
                    mForceCache, mNoProgress, mNoErrors, mMerge, mSync, mError,
                    mLoaderId == null ? "null": mLoaderId.toString());
        }

        /**
         * The exception which indicates not consistent loading parameters.
         */
        @SuppressWarnings("WeakerAccess")
        public static class LoadParametersException extends RuntimeException {

            /**
             * Initialises a newly created {@code LoadParametersException} object.
             *
             * @param msg
             *        The message
             */
            public LoadParametersException(@NonNull final String msg) {
                super(msg);
            }
        }
    }
}
