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

package akha.yakhont;

import akha.yakhont.Core.UriResolver;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.CursorHandler;
import akha.yakhont.loader.BaseResponse;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The {@link ContentProvider} which does not use predefined database schema but creates tables
 * (or adds columns, if necessary) "on the fly". The supported data types are TEXT and BLOB.
 * <br>Example of declaration in the manifest file:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * &lt;manifest xmlns:android="http://schemas.android.com/apk/res/android"
 *     package="com.yourpackage" &gt;
 *
 *     &lt;application ... &gt;
 *     ...
 *
 *     &lt;provider
 *         android:authorities="com.yourpackage.provider"
 *         android:name="akha.yakhont.BaseCacheProvider"
 *         android:enabled="true"
 *         android:exported="false" /&gt;
 *
 *     &lt;/application&gt;
 * &lt;/manifest&gt;
 * </pre>
 *
 * If you prefer to design another authority (for example, not based on the name of the package),
 * please implement the {@link UriResolver} interface and register it via the
 * {@link Utils#setUriResolver setUriResolver()} method.
 *
 * @author akha
 */
@SuppressLint("Registered")
@SuppressWarnings("unused")
public class BaseCacheProvider extends ContentProvider {

    private static final String         DB_NAME           = "cache.db";
    private static final int            DB_VERSION        = 2;

    private static final String         MIME_DIR          = "vnd.android.cursor.dir";
    private static final String         MIME_ITEM         = "vnd.android.cursor.item";
    private static final String         MIME_SUBTYPE      = "/vnd.%s.%s";

    private static final String         SELECTION_ID      = BaseColumns._ID + " = ?";

    private static final String         CREATE_TABLE      = "CREATE TABLE IF NOT EXISTS %s (" + BaseColumns._ID +
                                                            " INTEGER PRIMARY KEY";     // alias for rowid
    private static final String         ALTER_TABLE       = "ALTER TABLE %s ADD COLUMN %s %s;";

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected DbHelper                  mDbHelper;

    // should be SQLite 3.5.9 compatible

    /**
     * Initialises a newly created {@code BaseCacheProvider} object.
     */
    public BaseCacheProvider() {
    }

    /**
     * Returns the database name.
     *
     * @return  The DB name
     */
    @SuppressWarnings({"WeakerAccess", "SameReturnValue"})
    public String getDbName() {
        return DB_NAME;
    }

    /**
     * Returns the database version.
     *
     * @return  The DB version
     */
    @SuppressWarnings({"WeakerAccess", "SameReturnValue"})
    public int getDbVersion() {
        return DB_VERSION;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean onCreate() {
        //noinspection ConstantConditions
        mDbHelper = new DbHelper(getContext(), DB_NAME, getDbVersion());
        return true;
    }

    /**
     * Please refer to the base method description.
     *
     * @param method
     *        The method name; supported are "getDbName", "getDbVersion", "close"
     *
     * @param arg
     *        always null
     *
     * @param extras
     *        always null
     *
     * @return  Bundle with key equals to method name - or null
     */
    @CallSuper
    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        final Bundle bundle;
        switch (method) {
            case "getDbName":
                bundle = new Bundle();
                bundle.putString(method, getDbName());
                return bundle;

            case "getDbVersion":
                bundle = new Bundle();
                bundle.putInt(method, getDbVersion());
                return bundle;

            case "close":
                close();
                break;

            default:
                CoreLogger.logError("unknown method " + method);
                break;
        }
        return null;
    }

    /**
     * @see SQLiteOpenHelper#close
     */
    public void close() {
        try {
            mDbHelper.close();
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        return insert(uri, values, false, null);
    }

    private Uri insert(@NonNull final Uri uri, @NonNull final ContentValues values, final boolean silent,
                       final ContentValues[] bulkValues) {
        final String tableName = Utils.getCacheTableName(uri);
        if (tableName == null) {
            CoreLogger.logError("insert failed");
            return null;
        }

        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = insert(db, tableName, values);

        if (!silent) CoreLogger.log(String.format(getLocale(), "table %s, id %d", tableName, id));

        if (id == -1 && isMissedColumnsOrTable(db, tableName,
                bulkValues == null ? new ContentValues[] {values}: bulkValues)) {
            id = insert(db, tableName, values);
            CoreLogger.log(String.format(getLocale(), "table %s, new id %d", tableName, id));
        }
        if (id == -1) CoreLogger.logError("table " + tableName + ": insert error");

        return id == -1 ? null: ContentUris.withAppendedId(uri, id);
    }

    private long insert(@NonNull final SQLiteDatabase db, @NonNull final String tableName,
                        @NonNull final ContentValues values) {
        return db.insert(tableName, null, values);
    }

    private boolean isMissedColumnsOrTable(@NonNull final SQLiteDatabase db, @NonNull final String tableName,
                                           @NonNull final ContentValues[] bulkValues) {
        final Map<String, CreateTableScriptBuilder.DataType> columns = getColumns(tableName, bulkValues);
        return isTableExist(tableName) ?
                addColumns(db, tableName, columns): createTable(db, tableName, columns);
    }

    /**
     * Adds column(s) to the existing table.
     *
     * @param db
     *        The database
     *
     * @param tableName
     *        The table name (in the database above)
     *
     * @param columns
     *        The column(s) to add
     *
     * @return  Whether the column(s) addition was successful
     */
    protected boolean addColumns(@NonNull final SQLiteDatabase db, @NonNull final String tableName,
                                 @NonNull final Map<String, CreateTableScriptBuilder.DataType> columns) {
        boolean columnsAdded = false;
        for (final String columnName: columns.keySet())
            if (!isColumnExist(tableName, columnName))
                columnsAdded = execSQL(db, String.format(ALTER_TABLE, tableName, columnName,
                        getDataType(columns, columnName).name()));
        return columnsAdded;
    }

    @SuppressLint("ObsoleteSdkInt")
    private Set<String> getKeySet(@NonNull final ContentValues values) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) return values.keySet();

        final Set<String> set = Utils.newSet();
        for (final Map.Entry<String, Object> entry: values.valueSet())
            set.add(entry.getKey());

        return set;
    }

    /**
     * Creates the list of columns to add.
     *
     * @param tableName
     *        The table name
     *
     * @param bulkValues
     *        The data to add to the table
     *
     * @return  The list of columns
     */
    protected Map<String, CreateTableScriptBuilder.DataType> getColumns(@NonNull final String tableName,
                                                                        @NonNull final ContentValues[] bulkValues) {
        final Map<String, CreateTableScriptBuilder.DataType> columns = Utils.newMap();

        for (final ContentValues values: bulkValues) {
            for (final String key: getKeySet(values))
                if (!columns.containsKey(key) && values.get(key) != null) columns.put(key,
                        values.getAsByteArray(key) == null ?
                                CreateTableScriptBuilder.DEFAULT_DATA_TYPE:
                                CreateTableScriptBuilder.DataType.BLOB);
            if (values.size() == columns.size()) return columns;
        }

        // set unknown columns to default type (TEXT)
        for (final String key: getKeySet(bulkValues[0]))
            if (!columns.containsKey(key)) {
                final CreateTableScriptBuilder.DataType type =
                        CreateTableScriptBuilder.DEFAULT_DATA_TYPE;
                CoreLogger.logError(String.format(
                        "table %s, column %s: no data found, column type forced to %s",
                        tableName, key, type.name()));
                columns.put(key, type);
            }

        return columns;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int bulkInsert(@NonNull final Uri uri, @NonNull final ContentValues[] bulkValues) {
        //noinspection ConstantConditions
        if (bulkValues == null || bulkValues.length == 0) return 0;

        final String tableName = Utils.getCacheTableName(uri);
        if (tableName == null) {
            CoreLogger.logError("bulkInsert failed");
            return 0;
        }
        CoreLogger.log(String.format(getLocale(), "table: %s, number of rows to add: %d",
                tableName, bulkValues.length));

        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (!isTableExist(tableName) &&
                !createTable(db, tableName, getColumns(tableName, bulkValues)))
            return 0;

        int result = 0;
        switch (Matcher.match(uri)) {
            case ALL:
                //noinspection Convert2Lambda
                runTransaction(db, new Runnable() {
                    @Override
                    public void run() {
                        for (final ContentValues values: bulkValues)
                            insert(uri, values, true, bulkValues);
                        CoreLogger.log("bulkInsert completed, number of added rows: " + bulkValues.length);
                    }
                });
                result = bulkValues.length;
                break;

            case ID:
                CoreLogger.logError("failed bulk insert with ID, uri " + uri);
                break;

            default:
                CoreLogger.logError("wrong uri " + uri);
                break;
        }

        return result;
    }

    private interface CallableHelper<V> {
        V call(String table, String condition, String[] args, String[] columns, String order, ContentValues data);
    }

    private static String[] getSelectionIdArgs(@NonNull final Uri uri) {
        return new String[] {uri.getLastPathSegment()};
    }

    private <V> V handle(@NonNull final CallableHelper<V> callable, final V defValue, @NonNull final Uri uri,
                         String condition, String[] args, final String[] columns, final String order,
                         final ContentValues data) {
        final String table = Utils.getCacheTableName(uri);
        if (table == null) {
            CoreLogger.logError("handle data failed");
            return defValue;
        }

        switch (Matcher.match(uri)) {
            case ID:                        // fall through
                if (!TextUtils.isEmpty(condition))
                    CoreLogger.logError(String.format("selection %s will be replaced with %s, uri %s",
                            condition, SELECTION_ID, uri.toString()));

                condition = SELECTION_ID;
                args      = getSelectionIdArgs(uri);

            case ALL:
                try {
                    return callable.call(table, condition, args, columns, order, data);
                }
                catch (Exception exception) {
                    CoreLogger.log(String.format("uri %s, selection %s, selection args %s",
                            uri.toString(), condition, Arrays.deepToString(args)), exception);
                    return defValue;
                }

            default:
                CoreLogger.logError("wrong uri " + uri);
                return defValue;
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        //noinspection Convert2Lambda
        return handle(new CallableHelper<Cursor>() {
            @Override
            public Cursor call(String table, String condition, String[] args, String[] columns,
                               String order, ContentValues data) {
                return mDbHelper.getReadableDatabase().query(table, columns, condition, args,
                        null, null, order);
            }
        }, BaseResponse.EMPTY_CURSOR, uri, selection, selectionArgs, projection, sortOrder, null);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        //noinspection Convert2Lambda
        return handle(new CallableHelper<Integer>() {
            @Override
            public Integer call(String table, String condition, String[] args, String[] columns,
                                String order, ContentValues data) {
                if (!isTableExist(table)) {
                    CoreLogger.logWarning("tried to remove rows from not existing table " + table);
                    return 0;
                }
                // from docs: To remove all rows and get a count pass "1" as the whereClause
                if (condition == null) condition = "1";
                final int rows = mDbHelper.getWritableDatabase().delete(table, condition, args);

                CoreLogger.log(String.format(getLocale(),
                        "table: %s, number of deleted rows: %d", table, rows));
                return rows;
            }
        }, 0, uri, selection, selectionArgs, null, null, null);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        //noinspection Convert2Lambda
        return handle(new CallableHelper<Integer>() {
            @Override
            public Integer call(String table, String condition, String[] args, String[] columns,
                                String order, ContentValues data) {
                final int rows = mDbHelper.getWritableDatabase().update(table, data, condition, args);

                CoreLogger.log(String.format(getLocale(),
                        "table: %s, number of updated rows: %d", table, rows));
                return rows;
            }
        }, 0, uri, selection, selectionArgs, null, null, values);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public String getType(@NonNull Uri uri) {
        final String param = Utils.getCacheTableName(uri);
        if (param == null) {
            CoreLogger.logError("getType failed");
            return null;
        }
        final String table = String.format(MIME_SUBTYPE, uri.getAuthority(), param);

        switch (Matcher.match(uri)) {
            case ALL:
                return MIME_DIR + table;
            case ID:
                return MIME_ITEM + table;
            default:
                CoreLogger.logError("wrong uri " + uri);
                return null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isTableExist(@NonNull final String table) {
        return isExist(getDbForIsExist(), table /*, BaseColumns._ID */ );
    }

    private boolean isColumnExist(@NonNull final String table, @NonNull final String column) {
        return isExist(getDbForIsExist(), table, column);
    }

    private SQLiteDatabase getDbForIsExist() {
        return mDbHelper.getWritableDatabase();     // writable to trigger onCreate
    }

    /**
     * Checks whether the given table exists or not.
     *
     * @param db
     *        The database
     *
     * @param table
     *        The table name
     *
     * @return  {@code true} if the table exists, {@code false} otherwise
     */
    public static boolean isExist(@NonNull final SQLiteDatabase db, @NonNull final String table) {
        final Cursor cursor = db.rawQuery(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[] {table});
        if (cursor == null) return false;

        final boolean result = cursor.getCount() > 0;
        cursor.close();
        return result;
    }

    /**
     * Checks whether the given column exists or not.
     *
     * @param db
     *        The database
     *
     * @param table
     *        The table name
     *
     * @param column
     *        The column name
     *
     * @return  {@code true} if the column exists, {@code false} otherwise
     */
    public static boolean isExist(@NonNull final SQLiteDatabase db,
                                  @NonNull final String table, @NonNull final String column) {
        if (!isExist(db, table)) {
            CoreLogger.logError("not existing table: " + table);
            return false;
        }
        return sUsePragma ? isExistPragma(db, table, column): isExistQuery(db, table, column);
    }

    private static boolean              sUsePragma;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void setPragmaUsage(final boolean value) {
        sUsePragma = value;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static boolean isExistQuery(@NonNull final SQLiteDatabase db,
                                       @NonNull final String table, @NonNull final String column) {
        boolean result = false;
        Cursor  cursor = null;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            cursor = db.query(table, new String[] {column}, null,
                    null, null, null, null, "1");
            result = true;
        }
        catch (SQLException exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), table, exception);
        }
        finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static boolean isExistPragma(@NonNull final SQLiteDatabase db,
                                        @NonNull final String table, @NonNull final String column) {
        final boolean[] result = new boolean[] {false};
        final Cursor cursor = db.rawQuery(String.format("PRAGMA table_info(%s)", table), null);
        final int idx = cursor.getColumnIndex("name");

        //noinspection Convert2Lambda
        if (Utils.cursorHelper(cursor, new Utils.CursorHandler() {
                    @Override
                    public boolean handle(Cursor cursor) {
                        if (column.equals(cursor.getString(idx))) result[0] = true;
                        return !result[0];
                    }
                }, true, false, null))
            return result[0];
        else {
            CoreLogger.logError(String.format("can't read columns list for %s", table));
            return false;
        }
    }

    /**
     * Creates table.
     *
     * @param db
     *        The database
     *
     * @param table
     *        The table name
     *
     * @param columns
     *        The list of columns
     *
     * @return  {@code true} if table was created successfully, {@code false} otherwise
     */
    protected boolean createTable(@NonNull final SQLiteDatabase db, @NonNull final String table,
                                  @NonNull @Size(min = 1) final Map<String, CreateTableScriptBuilder.DataType> columns) {
        CoreLogger.log(String.format("%s", table));

        final CreateTableScriptBuilder builder = new CreateTableScriptBuilder(table);
        for (final String columnName: columns.keySet())
            builder.addColumn(columnName, getDataType(columns, columnName));

        return execSQL(db, builder.create());
    }

    private CreateTableScriptBuilder.DataType getDataType(
            @NonNull @Size(min = 1) final Map<String, CreateTableScriptBuilder.DataType> columns,
            @NonNull final String columnName) {
        CreateTableScriptBuilder.DataType dataType = columns.get(columnName);
        if (dataType == null) {
            dataType = CreateTableScriptBuilder.DEFAULT_DATA_TYPE;
            CoreLogger.logError(String.format("can't find data type for column %s, " +
                    "default value %s will be used", columnName, dataType.name()));
        }
        return dataType;
    }

    /**
     * Simple class to generate typical CREATE TABLE script.
     * The {@link BaseColumns#_ID _ID} column is added by default.
     */
    public static class CreateTableScriptBuilder {

        /** The default data type (value is {@link DataType#TEXT}). */
        public static final DataType    DEFAULT_DATA_TYPE   = DataType.TEXT;

        /**
         * The SQL data types.
         */
        public enum DataType {
            /** The signed integer. */
            INTEGER,
            /** The floating point. */
            REAL,
            /** The text string. */
            TEXT,
            /** The blob (Binary Large Object). */
            BLOB
        }

        private final String            mTable;
        private final Set<String>       mColumns            = Utils.newSet();

        /**
         * Initialises a newly created {@code CreateTableScriptBuilder} object.
         *
         * @param table
         *        The name of SQL table to create
         */
        public CreateTableScriptBuilder(@NonNull final String table) {
            mTable = table;
        }

        /**
         * Adds column in the SQL table's columns list.
         * The {@link BaseColumns#_ID _ID} column is already in.
         *
         * @param name
         *        The SQL column name
         *
         * @param type
         *        The SQL column type
         *
         * @return  This {@code CreateTableScriptBuilder} object to allow for chaining of calls to add methods
         */
        @SuppressWarnings("UnusedReturnValue")
        public CreateTableScriptBuilder addColumn(@NonNull final String   name,
                                                  @NonNull final DataType type) {
            mColumns.add(String.format("%s %s", name, type.name()));
            return this;
        }

        /**
         * Creates a SQL script with the arguments supplied to this builder.
         *
         * @return  The newly created SQL script to execute
         */
        public String create() {
            final StringBuilder builder = new StringBuilder(String.format(CREATE_TABLE, mTable));
            for (final String column: mColumns)
                builder.append(String.format(", %s", column));
            return builder.append(");").toString();
        }
    }

    /**
     * Executes transaction.
     *
     * @param db
     *        The database
     *
     * @param runnable
     *        The transaction to execute
     *
     * @return  {@code true} if transaction completed successfully, {@code false} otherwise
     */
    @SuppressLint("ObsoleteSdkInt")
    public static boolean runTransaction(@NonNull final SQLiteDatabase db, @NonNull final Runnable runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            db.beginTransactionNonExclusive();
        else
            db.beginTransaction();

        try {
            runnable.run();
            db.setTransactionSuccessful();
            return true;
        }
        catch (Exception exception) {
            CoreLogger.log("failed runnable " + runnable, exception);
            return false;
        }
        finally {
            db.endTransaction();
        }
    }

    /**
     * Executes SQL.
     *
     * @param db
     *        The database
     *
     * @param sql
     *        The SQL statement(s) to execute
     *
     * @return  {@code true} if script was executed successfully, {@code false} otherwise
     */
    public static boolean execSQL(@NonNull final SQLiteDatabase db, @NonNull final String sql) {
        return execSQLWrapper(db, sql);
    }

    private static boolean execSQLWrapper(@NonNull final SQLiteDatabase db, @NonNull final String sql) {
        try {
            CoreLogger.log(sql);
            db.execSQL(sql);
            return true;
        }
        catch (Exception exception) {
            CoreLogger.log(sql, exception);
            return false;
        }
    }

    /**
     * Executes SQL from the Android's asset.
     *
     * @param context
     *        The context
     *
     * @param db
     *        The database
     *
     * @param scriptName
     *        The name of the asset
     *
     * @return  {@code true} if script was executed successfully, {@code false} otherwise
     */
    public static boolean executeSQLScript(@NonNull final Context context, @NonNull final SQLiteDatabase db,
                                           @NonNull final String scriptName) {
        try {
            final InputStream inputStream = context.getAssets().open(scriptName);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1)
                outputStream.write(buffer, 0, length);
            outputStream.close();
            inputStream.close();

            return executeSQLScript(db, outputStream.toString().split(";"));
        }
        catch (/*IO*/Exception exception) {
            CoreLogger.log("failed SQLScript " + scriptName, exception);
            return false;
        }
    }

    /**
     * Executes SQL provided.
     *
     * @param db
     *        The database
     *
     * @param script
     *        The script to execute
     *
     * @return  {@code true} if script was executed successfully, {@code false} otherwise
     */
    public static boolean executeSQLScript(@NonNull final SQLiteDatabase db, final String[] script) {
        if (script == null || script.length == 0) {
            CoreLogger.logWarning("nothing to do");
            return false;
        }
        try {
            boolean result = true;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < script.length; i++) {
                final String sqlStatement = script[i].trim();
                if (sqlStatement.length() > 0)
                    if (!execSQLWrapper(db, sqlStatement + ";")) result = false;
            }
            return result;
        }
        catch (Exception exception) {
            CoreLogger.log(Arrays.deepToString(script), exception);
            return false;
        }
    }

    /**
     * Executes SQL provided.
     *
     * @param db
     *        The database
     *
     * @param script
     *        The script to execute
     *
     * @return  {@code true} if script was executed successfully, {@code false} otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean executeSQLScript(@NonNull final SQLiteDatabase db, final String script) {
        return executeSQLScript(db, new String[] { script });
    }

    /**
     * Clears (drops) tables, virtual tables and views based on information from sqlite_master.
     *
     * @param db
     *        The database
     *
     * @return  {@code true} if tables and views removing was executed successfully, {@code false} otherwise
     */
    public static boolean clear(@NonNull final SQLiteDatabase db) {
        final Cursor cursor = db.rawQuery(
                "SELECT type, name, rootpage FROM sqlite_master", null);

        final CacheClearCursorHandler handler = new CacheClearCursorHandler();
        if (Utils.cursorHelper(cursor, handler, true, false, null)) {
            try {
                boolean result = true;

                if (handler.mTables.isEmpty())
                    CoreLogger.logWarning("no cache tables found");

                for (final String view: handler.mViews) {
                    CoreLogger.log("about to drop view " + view);
                    if (!executeSQLScript(db, "DROP VIEW IF EXISTS " + view))   result = false;
                }
                for (final String table: handler.mVTables) {
                    CoreLogger.log("about to drop virtual table " + table);
                    if (!executeSQLScript(db, "DROP TABLE IF EXISTS " + table)) result = false;
                }
                for (final String table: handler.mTables) {
                    CoreLogger.log("about to drop table " + table);
                    if (!executeSQLScript(db, "DROP TABLE IF EXISTS " + table)) result = false;
                }

                if (result)
                    CoreLogger.log("db clear completed successfully");
                else
                    CoreLogger.logWarning("there's some issues during db clear");

                return result;
            }
            catch (Exception exception) {
                CoreLogger.log("db clear failed", exception);
                return false;
            }
        }
        else {
            CoreLogger.logError("db clear failed");
            return false;
        }
    }

    private static class CacheClearCursorHandler implements CursorHandler {

        private final List<String> mTables = new ArrayList<>(),
                mVTables = new ArrayList<>(), mViews = new ArrayList<>();

        private Integer mIdxType, mIdxName, mIdxPage;

        @Override
        public boolean handle(Cursor cursor) {
            if (mIdxType == null) mIdxType = cursor.getColumnIndex("type");
            if (mIdxName == null) mIdxName = cursor.getColumnIndex("name");
            if (mIdxPage == null) mIdxPage = cursor.getColumnIndex("rootpage");

            final String name = cursor.getString(mIdxName);
            if (!name.toLowerCase(getLocale()).startsWith("sqlite_")) {

                final String type = cursor.getString(mIdxType).toLowerCase(getLocale());
                switch (type) {
                    case "table":
                        if (cursor.isNull(mIdxPage) || cursor.getInt(mIdxPage) == 0)
                            mVTables.add(name);
                        else
                            mTables.add(name);
                        break;

                    case "view":
                        mViews.add(name);
                        break;
                }
            }
            return true;
        }
    }

    /**
     * Gets the {@code SQLiteOpenHelper} object.
     *
     * @return  The {@code SQLiteOpenHelper}
     */
    public SQLiteOpenHelper getDbHelper() {
        return mDbHelper;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Called when database created.
     *
     * @param db
     *        The database
     */
    protected void onCreate(@SuppressWarnings("UnusedParameters") @NonNull final SQLiteDatabase db) {
        CoreLogger.log(String.format(getLocale(), "on create database %s, version %d",
                DB_NAME, getDbVersion()));
    }

    /**
     * Called when database upgraded.
     *
     * @param db
     *        The database
     *
     * @param oldVersion
     *        The current version of the DB
     *
     * @param newVersion
     *        The version of the DB after upgrade
     */
    protected void onUpgrade(@SuppressWarnings("UnusedParameters") @NonNull final SQLiteDatabase db,
                             final int oldVersion, final int newVersion) {
        CoreLogger.log(String.format(getLocale(),
                "on upgrade database %s from version %d to %d", DB_NAME, oldVersion, newVersion));
        if (newVersion == 2 && oldVersion == 1)
            if (!clear(mDbHelper.getWritableDatabase()))
                CoreLogger.logError(String.format(getLocale(), "error during clear database %s", DB_NAME));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class DbHelper extends SQLiteOpenHelper {

        private DbHelper(@NonNull Context context, @NonNull final String name, final int version) {
            super(context, name, null, version);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onCreate(final SQLiteDatabase db) {
            //noinspection Convert2Lambda
            runTransaction(db, new Runnable() {
                @Override
                public void run() {
                    BaseCacheProvider.this.onCreate(db);
                }
            });
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            //noinspection Convert2Lambda
            runTransaction(db, new Runnable() {
                @Override
                public void run() {
                    BaseCacheProvider.this.onUpgrade(db, oldVersion, newVersion);
                }
            });
        }
    }

    @SuppressWarnings("unused")
    private static class Matcher {

        private enum Match {
            NO,
            ALL,
            ID
        }

        @NonNull
        private static Match match(@NonNull final Uri uri) {
            final List<String> pathSegments     = uri.getPathSegments();
            final int pathSegmentsSize          = pathSegments.size();

            if (pathSegmentsSize == 1)
                return Match.ALL;
            else if (pathSegmentsSize == 2 && TextUtils.isDigitsOnly(pathSegments.get(1)))
                return Match.ID;
            else
                return Match.NO;
        }
    }

    private static Locale getLocale() {
        return Utils.getLocale();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // copyDbs below are for debug only

    /**
     * Copies the current database to the default backup directory (in debug builds only,
     * see {@link Utils#isDebugMode}).
     *
     * @param context
     *        The context
     */
    @SuppressWarnings("WeakerAccess")
    public static void copyDb(@NonNull final Context context) {
        copyDb(context, null, null);
    }

    /**
     * Copies the database to the file specified (in debug builds only,
     * see {@link Utils#isDebugMode}).
     *
     * @param context
     *        The context
     *
     * @param srcDb
     *        The database to copy, or null (means default one: {@code cache.db})
     *
     * @param dstDb
     *        The file to copy database to, or null (means default backup directory and file name)
     */
    public static void copyDb(@NonNull final Context context, final File srcDb, final File dstDb) {
        if (!Utils.isDebugMode(context.getPackageName())) {
            CoreLogger.logWarning("db copying is available in debug builds only; " +
                    "please consider to use CoreLogger.registerShakeDataSender()");
            return;
        }
        copyFile(context, getSrcDb(context, srcDb), dstDb);
    }

    private static void copyFile(@NonNull final Context context, @NonNull final File srcFile,
                                 final File dstFileOrg) {
        //noinspection Convert2Lambda
        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                copyFileSync(context, srcFile, dstFileOrg, null);
            }
        });
    }

    private static File getSrcDb(@NonNull final Context context, final File srcDb) {
        return srcDb != null ? srcDb: context.getDatabasePath(DB_NAME);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static File copyDbSync(@NonNull final Context context, final File srcDb, final File dstDb,
                                  final Map<String, Exception> errors) {
        return copyFileSync(context, getSrcDb(context, srcDb), dstDb, errors);
    }

    private static File copyFileSync(@NonNull final Context context, @NonNull final File srcFile,
                                     final File dstFileOrg, final Map<String, Exception> errors) {
        try {
            if (!srcFile.exists()) {
                handleError("file doesn't exist: " + srcFile, errors);
                return null;
            }

            File dstFile = dstFileOrg;
            if (dstFile == null) dstFile = Utils.getTmpDir(context);
            if (dstFile == null) return null;

            if (dstFile.isDirectory()) {
                if (!dstFile.canWrite()) {
                    handleError("directory is read-only: " + dstFile, errors);
                    return null;
                }
                dstFile = new File(dstFile, getDstFileName(srcFile.getName()));
            }

            final FileChannel src = new FileInputStream (srcFile).getChannel();
            final FileChannel dst = new FileOutputStream(dstFile).getChannel();
            dst.transferFrom(src, 0, src.size());
            src.close();
            dst.close();

            CoreLogger.log(srcFile + " copied to " + dstFile);
            return dstFile;
        }
        catch (Exception exception) {
            final String text = "copying failed: " + srcFile;
            CoreLogger.log(text, exception);
            if (errors != null) errors.put(text, exception);
            return null;
        }
    }

    private static void handleError(final String text, final Map<String, Exception> map) {
        CoreLogger.logError(text);
        if (map != null) map.put(text, new RuntimeException("copy file stack trace"));
    }

    private static String getDstFileName(@NonNull final String name) {
        final String suffix = Utils.getTmpFileSuffix();
        final int idx = name.lastIndexOf('.');
        return idx < 0 ? name + suffix: name.substring(0, idx) + suffix + name.substring(idx);
    }
}
