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

package akha.yakhont.loader;

import akha.yakhont.CoreLogger;
import akha.yakhont.loader.BaseResponse.Converter;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Build;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * The data converter.
 *
 * @param <D>
 *        The type of data
 *
 * @author akha
 */
public class BaseConverter<D> implements Converter<D> {

    private static final    Gson                sGson           = new GsonBuilder().serializeNulls().create();
    private static final    Object              sGsonLock       = new Object();

    private        final    JsonParser          mJsonParser     = new JsonParser();
    private        final    Object              mParserLock     = new Object();

    private                 Type                mType;

    private interface Visitor {
        @SuppressWarnings("UnusedParameters")
        void init(JsonObject jsonObject);
        void add (String key, String value);
    }

    /**
     * Initialises a newly created {@code JsonConverter} object.
     */
    public BaseConverter() {
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Converter<D> setType(Type type) {
        CoreLogger.log("set type to " + type);
        mType = type;

        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Type getType() {
        return mType;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public ContentValues[] get(final D src) {
        return src == null ? null: getContentValues(getJsonElement(src));
    }

    /**
     * Converts data to {@code JsonElement}.
     *
     * @param src
     *        The data to convert
     *
     * @return  The {@code JsonElement}
     */
    @SuppressWarnings("WeakerAccess")
    protected JsonElement getJsonElement(@NonNull final D src) {
        if (mType == null) setType(src.getClass());

        synchronized (sGsonLock) {
            //noinspection ConstantConditions
            return sGson.toJsonTree(src, getType());
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public D get(final Cursor cursor) {
        D result                = null;
        JsonElement jsonElement = null;

        try {
            jsonElement = getJsonElement(cursor);
            if (jsonElement == null) //noinspection ConstantConditions
                return result;

            synchronized (sGsonLock) {
                result = sGson.fromJson(jsonElement, getType());
            }
        }
        catch (Exception e) {
            CoreLogger.log("failed, jsonElement = " + jsonElement, e);
        }

        return result;
    }

    private JsonElement getJsonElement(final Cursor cursor) {
        if (cursor == null)           return null;
        if (!cursor.moveToFirst())    return null;

        final Type type = getType();
        final boolean isArray = type instanceof GenericArrayType || (type instanceof Class && ((Class) type).isArray());

        final JsonArray jsonArray = new JsonArray();
        for (;;) {
            final JsonObject jsonObject = new JsonObject();
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                String value = cursor.getString(i), name = cursor.getColumnName(i);
                if (value != null) {
                    value = value.trim();
                    if (value.startsWith("[") || value.startsWith("{")) {
                        synchronized (mParserLock) {
                            jsonObject.add(name, mJsonParser.parse(value));
                        }
                        continue;
                    }
                }
                jsonObject.addProperty(name, value);
            }
            jsonArray.add(jsonObject);

            if (!cursor.moveToNext()) break;
        }

        if (!isArray && jsonArray.size() > 1)
            CoreLogger.logError("class is not array but jsonArray.size() = " + jsonArray.size());

        return isArray ? jsonArray: jsonArray.size() > 0 ? jsonArray.get(0): null;
    }

    /** @exclude */
    @NonNull
    @SuppressWarnings({"JavaDoc", "unchecked", "WeakerAccess"})
    protected Cursor getCursor(final JsonElement jsonElement) {
        return ((CursorVisitor) accept(new CursorVisitor(), jsonElement)).getResult();
    }

    /** @exclude */
    @NonNull
    @SuppressWarnings({"JavaDoc", "unused"})
    public Cursor getCursor(@NonNull final Reader reader) {
        JsonElement jsonElement = null;
        try {
            synchronized (mParserLock) {
                jsonElement = mJsonParser.parse(reader);
            }
        }
        catch (Exception e) {
            CoreLogger.log("failed", e);
        }
        return getCursor(jsonElement);
    }

    /** @exclude */
    @NonNull
    @SuppressWarnings({"JavaDoc", "unchecked", "WeakerAccess"})
    protected ContentValues[] getContentValues(final JsonElement jsonElement) {
        return ((ContentValuesVisitor) accept(new ContentValuesVisitor(), jsonElement)).getResult();
    }

    private Visitor accept(@NonNull final Visitor visitor, final JsonElement jsonElement) {
        if (jsonElement == null) return visitor;

        if (jsonElement.isJsonObject())
            return accept(visitor, jsonElement.getAsJsonObject());

        if (jsonElement.isJsonArray())
            return accept(visitor, jsonElement.getAsJsonArray());

        CoreLogger.logError("unknown json element " + jsonElement);
        return visitor;
    }

    private Visitor accept(@NonNull final Visitor visitor, @NonNull final JsonObject jsonObject) {
        visitor.init(jsonObject);
        add(visitor, jsonObject);
        return visitor;
    }

    private Visitor accept(@NonNull final Visitor visitor, @NonNull final JsonArray jsonArray) {
        for (int i = 0; i < jsonArray.size(); i++) {
            if (jsonArray.get(i).isJsonNull())
                continue;
            else if (!jsonArray.get(i).isJsonObject()) {  // should never happen
                CoreLogger.logError("unknown json array element type " + jsonArray.get(i));
                continue;
            }
            final JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();

            visitor.init(jsonObject);
            add(visitor, jsonObject);
        }
        return visitor;
    }

    private void add(@NonNull final Visitor visitor, @NonNull final JsonObject jsonObject) {
        for (final Map.Entry<String, JsonElement> entry: jsonObject.entrySet()) {
            final String      key         = entry.getKey();
            final JsonElement jsonElement = entry.getValue();

            if (jsonElement.isJsonPrimitive())
                visitor.add(key, jsonElement.getAsString());

            else if (jsonElement.isJsonNull())
                visitor.add(key, null);

            else if (jsonElement.isJsonArray())
                visitor.add(key, jsonElement.getAsJsonArray().toString());

            else if (jsonElement.isJsonObject())
                visitor.add(key, jsonElement.getAsJsonObject().toString());

            else {  // should never happen
                CoreLogger.logError("unknown json element type " + jsonElement);
                visitor.add(key, null);
            }
        }
    }

    private class ContentValuesVisitor implements Visitor {

        private final       LinkedHashSet<ContentValues>    mResult             = new LinkedHashSet<>();
        private             ContentValues                   mContentValues;

        @NonNull
        private ContentValues[] getResult() {
            store();
            return mResult.toArray(new ContentValues[mResult.size()]);
        }

        @Override
        public void init(@NonNull final JsonObject jsonObject) {
            store();
            mContentValues = new ContentValues();
        }

        @Override
        public void add(@NonNull final String key, final String value) {
            mContentValues.put(key, value);
        }

        private void store() {
            if (mContentValues != null && mContentValues.size() > 0) mResult.add(mContentValues);
        }
    }

    @SuppressWarnings("unused")
    private class CursorVisitor implements Visitor {

        private             MatrixCursor                    mCursor;
        private             MatrixCursor.RowBuilder         mBuilder;
        private             int                             mIndex;

        @NonNull
        private Cursor getResult() {
            return mCursor == null ? BaseResponse.EMPTY_CURSOR: mCursor;
        }

        @Override
        public void init(@NonNull final JsonObject jsonObject) {
            if (mCursor == null) mCursor = getCursor(jsonObject);

            mBuilder = mCursor.newRow();
            addColumn(BaseColumns._ID, String.valueOf(++mIndex));
        }

        @Override
        public void add(@NonNull final String key, final String value) {
            addColumn(key, value);
        }

        @NonNull
        private MatrixCursor getCursor(@NonNull final JsonObject jsonObject) {
            final LinkedHashSet<String> columns = new LinkedHashSet<>();
            columns.add(BaseColumns._ID);

            for (final Map.Entry<String, JsonElement> entry: jsonObject.entrySet())
                columns.add(entry.getKey());

            return new MatrixCursor(columns.toArray(new String[columns.size()]));
        }

        private void addColumn(@NonNull final String name, final String value) {
            if (Build.VERSION.SDK_INT >= 19)
                mBuilder.add(name, value);
            else
                mBuilder.add(value);
        }
    }
}
