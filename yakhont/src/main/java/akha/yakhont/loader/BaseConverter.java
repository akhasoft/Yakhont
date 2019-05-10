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

package akha.yakhont.loader;

import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.CursorHandler;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CoreReflection;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.Converter;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.ConverterGetter;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper.ConverterHelper;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;

/**
 * The data converter.
 *
 * @param <D>
 *        The type of data
 *
 * @author akha
 */
public class BaseConverter<D> implements Converter<D> {

    private static final String                     CACHE_STRING              = "data_string";
    private static final String                     CACHE_BYTES               = "data_bytes";

    private static final String                     CLASS_STRING              = "class_string";
    private static final String                     CLASS_BYTES               = "class_bytes";

    private static final byte[]                     EMPTY_BYTES               = new byte[0];

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected               Class                   mClass;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected               ConverterGetter<D>      mBaseConverterGetter;

    /**
     * Initialises a newly created {@code BaseConverter} object.
     */
    public BaseConverter() {
    }

    private static byte[] getClassBytes(@NonNull final Class cls) {
        try {
            final ByteArrayOutputStream bytes  = new ByteArrayOutputStream();
            final ObjectOutputStream    stream = new ObjectOutputStream(bytes);
            stream.writeObject(cls);
            stream.close();
            return bytes.toByteArray();
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
        return EMPTY_BYTES;
    }

    private static Class getClass(final byte[] data) {
        if (data == null) {
            CoreLogger.logWarning("bytes for class == null");
            return null;
        }
        try {
            final ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(data));
            final Class cls = (Class) stream.readObject();
            stream.close();
            return cls;
        }
        catch (Exception exception) {
            CoreLogger.log(Level.WARNING, exception);
        }
        return null;
    }

    private static String getClassString(@NonNull final Class cls) {
        return cls.getName();
    }

    private static Class getClass(final String data) {
        if (data == null) {
            CoreLogger.logWarning("string for class == null");
            return null;
        }
        try {
            return Class.forName(data);
        }
        catch (/*ClassNotFound*/Exception exception) {
            CoreLogger.log(Level.WARNING, exception);
        }
        return null;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void setConverterGetter(final ConverterGetter<D> getter) {
        if (getter == null) CoreLogger.logWarning("getter == null");
        mBaseConverterGetter = getter;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Type getType() {
        return mClass;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Collection<ContentValues> getValues(String string, byte[] bytes, Class cls) {
        if (cls == null) {
            CoreLogger.logError("data class == null");
            return null;
        }
        final ContentValues contentValues = new ContentValues();

        contentValues.put(CACHE_BYTES , bytes  != null ? bytes : EMPTY_BYTES);
        contentValues.put(CACHE_STRING, string != null ? string:          "");

        contentValues.put(CLASS_BYTES , getClassBytes (cls));
        contentValues.put(CLASS_STRING, getClassString(cls));

        return Collections.singleton(contentValues);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public D getData(final Cursor cursor) {
        mClass = null;

        if (mBaseConverterGetter == null) {
            CoreLogger.logError("mBaseConverterGetter == null");
            return null;
        }

        final ConverterCursorHandler handler = new ConverterCursorHandler();
        if (Utils.cursorHelper(cursor, handler, true, false, null)) {
            if (handler.mData == null)
                CoreLogger.logError("can't convert cursor to the data object");
            else
                mClass = handler.mData.getClass();

            return handler.mData;
        }
        else {
            CoreLogger.logError("error during converting cursor to the data object");
            return null;
        }
    }

    private static byte[] getBytes(@NonNull final Cursor cursor, @NonNull final String column) {
        final int idx = cursor.getColumnIndex(column);
        return cursor.isNull(idx) ? null: cursor.getBlob(idx);
    }

    private static String getString(@NonNull final Cursor cursor, @NonNull final String column) {
        final int idx = cursor.getColumnIndex(column);
        return cursor.isNull(idx) ? null: cursor.getString(idx);
    }

    private class ConverterCursorHandler implements CursorHandler {

        private D                                   mData;

        @Override
        public boolean handle(Cursor cursor) {

            ConverterHelper<D> converterHelper = mBaseConverterGetter.get(null);
            if (converterHelper == null) {
                CoreLogger.log("default type not defined, the one from cache will be used");

                Type type = BaseConverter.getClass(getBytes(cursor , CLASS_BYTES ));
                if (type == null)
                    type  = BaseConverter.getClass(getString(cursor, CLASS_STRING));

                if (type == null)
                    CoreLogger.logError("type == null");
                else
                    converterHelper = mBaseConverterGetter.get(type);
            }

            if (converterHelper == null)
                CoreLogger.logError("converter == null");
            else {
                final D data = converterHelper.get(
                        getString(cursor, CACHE_STRING), getBytes(cursor, CACHE_BYTES));
                if (data == null)
                    CoreLogger.logError("converter returned null");
                else {

                    if (mData == null)
                        mData = data;
                    else if (CoreReflection.isNotSingle(mData)) {
                        @SuppressWarnings("unchecked")
                        final D tmp = (D) CoreReflection.mergeObjects(mData, data);
                        mData = tmp;
                    }
                    else {
                        CoreLogger.logError("not mergeable objects: " + mData.getClass() +
                                ", " + data.getClass());
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
