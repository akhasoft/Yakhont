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
import akha.yakhont.loader.BaseConverter;
import akha.yakhont.loader.BaseResponse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import java.lang.Runnable;      // lint inspection bug workaround
import java.lang.String;        // same as above
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * The {@link ContentProvider} which does not use predefined database schema but creates tables
 * (or adds columns, if necessary) "on the fly".
 *
 * <p>Some of available features are:
 * <ul>
 *   <li>Checking for table or column existence
 *     <ul>
 *       <li>Checks if table exists: {@link #isExist(SQLiteDatabase, String)}</li>
 *       <li>Checks if column exists: {@link #isExist(SQLiteDatabase, String, String)}</li>
 *     </ul>
 *   </li>
 *   <li>Database copying
 *     <ul>
 *       <li>Copy database: {@link #copyDb(Context)}</li>
 *       <li>Copy database: {@link #copyDb(Context, File, File)}</li>
 *     </ul>
 *   </li>
 *   <li>SQL script(s) executing
 *     <ul>
 *       <li>Runs SQL script(s): {@link #execSql(Context, SQLiteDatabase, String)}</li>
 *       <li>Runs SQL script(s): {@link #execSql(SQLiteDatabase, String...)}</li>
 *     </ul>
 *   </li>
 *   <li>Transactions support
 *     <ul>
 *       <li>Runs transaction: {@link #runTransaction(SQLiteDatabase, Callable)}</li>
 *       <li>Runs transaction: {@link #runTransaction(SQLiteDatabase, Runnable)}</li>
 *     </ul>
 *   </li>
 *   <li>Database clearing
 *     <ul>
 *       <li>Clears database: {@link #clear(SQLiteDatabase)}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Example of declaration in the manifest file:
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
public class BaseCacheProvider extends ContentProvider {    // should be SQLite 3.5.9 compatible

    private static final String         DB_NAME           = "cache.db";
    private static final int            DB_VERSION        = 3;

    private static final String         MIME_DIR          = "vnd.android.cursor.dir";
    private static final String         MIME_ITEM         = "vnd.android.cursor.item";
    private static final String         MIME_SUBTYPE      = "/vnd.%s.%s";

    private static final String         SELECTION_ID      = BaseColumns._ID + " = ?";

    private static final String         CREATE_INDEX      = "CREATE INDEX IF NOT EXISTS %s ON %s (%s);";
    private static final String         NAME_INDEX        = "idx_%s_%s";

    private static final String         CREATE_TABLE      = "CREATE TABLE IF NOT EXISTS %s (" + BaseColumns._ID +
                                                            " INTEGER PRIMARY KEY";     // alias for the rowid

    private static final String         ALTER_TABLE       = "ALTER TABLE %s ADD COLUMN %s %s;";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public  static final String         DROP_TABLE        = "DROP TABLE IF EXISTS %s;";
    private static final String         DROP_VIEW         = "DROP VIEW  IF EXISTS %s;";

    // the action's names below should be consistent with 'call' javadoc
    /** The method name for DB clear (used by {@link #call}), value is {@value}. */
    public  static final String         CALL_CLEAR        = "clear";
    /** The method name for DB close (used by {@link #call}), value is {@value}. */
    public  static final String         CALL_CLOSE        = "close";
    /** The method name for DB copy (used by {@link #call}), value is {@value}. */
    public  static final String         CALL_COPY         = "copy";
    /** The method name for executing SQL script (used by {@link #call}), value is {@value}. */
    public  static final String         CALL_EXEC_SQL     = "execSql";
    /** The method name for get DB file one (used by {@link #call}), value is {@value}. */
    public  static final String         CALL_GET_DB_NAME  = "getDbName";
    /** The method name for get DB file version (used by {@link #call}), value is {@value}. */
    public  static final String         CALL_GET_DB_VER   = "getDbVersion";
    /** The method name for checking table or column existence in DB (used by {@link #call}), value is {@value}. */
    public  static final String         CALL_IS_EXIST     = "isExist";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected DbHelper                  mDbHelper;

    /**
     * Initialises a newly created {@code BaseCacheProvider} object.
     */
    public BaseCacheProvider() {
    }

    // the action's names description below should be consistent with CALL_* constants above
    /**
     * Please refer to the base method description.
     *
     * @param action
     *        The action's name, could be {@link #clear "clear"}, {@link #close "close"},
     *        {@link #copyDb(Context, File, File) "copy"}, {@link #execSql(SQLiteDatabase, String[]) "execSql"},
     *        {@link #getDbName "getDbName"}, {@link #getDbVersion "getDbVersion"} or
     *        {@link #isExist(SQLiteDatabase, String) "isExist"} (please use CALL_* definitions provided)
     *
     * @param arg
     *        Please refer to supported methods descriptions
     *
     * @param extras
     *        Column name for {@link #isExist(SQLiteDatabase, String, String) "isExist"} (or null),
     *        use the action name as a key
     *
     * @return  Bundle with key equals to the action name (or null)
     */
    @CallSuper
    @Nullable
    @Override
    public Bundle call(@NonNull final String action, @Nullable final String arg, @Nullable final Bundle extras) {
        switch (action) {
            case CALL_CLEAR:
                return callHelper(action, clear(mDbHelper.getWritableDatabase()), null, null);

            case CALL_CLOSE:
                close();
                break;

            case CALL_COPY:
                if (arg == null)
                    CoreLogger.logError("destination for DB copy == null");
                else
                    copyDb(null, getSrcDb(null, null), new File(arg));
                break;

            case CALL_EXEC_SQL:
                if (arg == null)
                    CoreLogger.logError("SQL script for executing == null");
                else
                    return callHelper(action, execSql(mDbHelper.getWritableDatabase(), arg), null, null);
                break;

            case CALL_GET_DB_NAME:
                return callHelper(action, null, null, getDbName());

            case CALL_GET_DB_VER:
                return callHelper(action, null, getDbVersion(), null);

            case CALL_IS_EXIST:
                if (arg == null)
                    CoreLogger.logError("table name for isExist == null");
                else if (extras == null)
                    return callHelper(action, isExist(mDbHelper.getWritableDatabase(), arg), null, null);
                else {
                    final String column = extras.getString(action);
                    if (column == null)
                        CoreLogger.logError("column name for isExist == null, table name " + arg);
                    else
                        return callHelper(action, isExist(mDbHelper.getWritableDatabase(), arg, column), null, null);
                }
                break;

            default:
                CoreLogger.logError("unknown action: " + action);
                break;
        }
        return null;
    }

    private Bundle callHelper(@NonNull final String method, final Boolean b, final Integer i, final String s) {
        final Bundle bundle = new Bundle();
        if (b != null) bundle.putBoolean(method, b);
        if (i != null) bundle.putInt    (method, i);
        if (s != null) bundle.putString (method, s);
        return bundle;
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
    public Uri insert(@NonNull final Uri uri, final ContentValues values) {
        return insert(getDbForIsExist(), uri, values, false, null);
    }

    private Uri insert(@NonNull final SQLiteDatabase db, @NonNull final Uri uri,
                       @NonNull final ContentValues values, final boolean silent,
                       final ContentValues[] bulkValues) {
        final String tableName = Utils.getCacheTableName(uri);
        if (tableName == null) {
            CoreLogger.logError("insert failed");
            return null;
        }
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
        return isExist(db, tableName) ?
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
            if (!isExist(db, tableName, columnName))
                columnsAdded = execSql(db, String.format(ALTER_TABLE, tableName, columnName,
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

        final SQLiteDatabase db = getDbForIsExist();
        if (!isExist(db, tableName) && !createTable(db, tableName, getColumns(tableName, bulkValues)))
            return 0;

        int result = 0;
        switch (Matcher.match(uri)) {
            case ALL:
                //noinspection Convert2Lambda
                runTransaction(db, new Runnable() {
                    @Override
                    public void run() {
                        for (final ContentValues values: bulkValues)
                            insert(db, uri, values, true, bulkValues);
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
        V call(SQLiteDatabase db, String table, String condition, String[] args, String[] columns, String order, ContentValues data);
    }

    private static String[] getSelectionIdArgs(@NonNull final Uri uri) {
        return new String[] {uri.getLastPathSegment()};
    }

    private <V> V handle(@NonNull final CallableHelper<V> callable, @NonNull final SQLiteDatabase db,
                         final V defValue, @NonNull final Uri uri, String condition, String[] args,
                         final String[] columns, final String order, final ContentValues data) {
        final String table = Utils.getCacheTableName(uri);
        if (table == null) {
            CoreLogger.logError("table == null");
            return defValue;
        }
        if (!isExist(db, table)) {
            CoreLogger.logWarning("no such table " + table);
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
                    return callable.call(db, table, condition, args, columns, order, data);
                }
                catch (Exception exception) {
                    CoreLogger.log(String.format("uri %s, selection %s, selection args %s",
                            uri.toString(), condition, Arrays.toString(args)), exception);
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
    public Cursor query(@NonNull final Uri uri, final String[] projection, final String selection,
                        final String[] args, final String sortOrder) {
        //noinspection Convert2Lambda
        return handle(new CallableHelper<Cursor>() {
            @Override
            public Cursor call(final SQLiteDatabase db, final String table, final String condition,
                               final String[] args, final String[] columns, final String order,
                               final ContentValues data) {
                return db.query(table, columns, condition, args, null, null, order);
            }
        }, getDbForIsExist(), BaseResponse.EMPTY_CURSOR, uri, selection, args, projection, sortOrder, null);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int delete(@NonNull final Uri uri, final String selection, final String[] selectionArgs) {
        //noinspection Convert2Lambda
        return handle(new CallableHelper<Integer>() {
            @Override
            public Integer call(final SQLiteDatabase db, final String table, String condition,
                                final String[] args, final String[] columns, final String order,
                                final ContentValues data) {
                // from docs: To remove all rows and get a count pass "1" as the whereClause
                if (condition == null) condition = "1";
                final int rows = db.delete(table, condition, args);

                CoreLogger.log(String.format(getLocale(),
                        "table: %s, number of deleted rows: %d", table, rows));
                return rows;
            }
        }, getDbForIsExist(), 0, uri, selection, selectionArgs, null, null, null);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int update(@NonNull final Uri uri, final ContentValues values,
                      final String selection, final String[] selectionArgs) {
        //noinspection Convert2Lambda
        return handle(new CallableHelper<Integer>() {
            @Override
            public Integer call(final SQLiteDatabase db, final String table, final String condition,
                                final String[] args, final String[] columns, final String order,
                                final ContentValues data) {
                final int rows = db.update(table, data, condition, args);

                CoreLogger.log(String.format(getLocale(),
                        "table: %s, number of updated rows: %d", table, rows));
                return rows;
            }
        }, getDbForIsExist(), 0, uri, selection, selectionArgs, null, null, values);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public String getType(@NonNull final Uri uri) {
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
                    public boolean handle(final Cursor cursor) {
                        if (column.equals(cursor.getString(idx))) result[0] = true;
                        return !result[0];
                    }
                }, 0, false, null))
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
        boolean addPageIndex = false;
        for (final String columnName: columns.keySet()) {
            builder.addColumn(columnName, getDataType(columns, columnName));
            if (columnName.equals(BaseConverter.PAGE_ID)) addPageIndex = true;
        }
        final boolean addPageIndexCallable = addPageIndex;

        //noinspection Convert2Lambda
        return runTransaction(db, new Callable<Boolean>() {
            @Override
            public Boolean call() {
                boolean result = execSql(db, builder.create());
                if (addPageIndexCallable)                                   // paging support
                    result = execSql(db, String.format(CREATE_INDEX, String.format(NAME_INDEX,
                            table, BaseConverter.PAGE_ID), table, BaseConverter.PAGE_ID));
                return result;
            }
        });
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
     * @param callable
     *        The transaction to execute, should return {@code true} if successful
     *
     * @return  {@code true} if transaction completed successfully, {@code false} otherwise
     */
    @SuppressLint("ObsoleteSdkInt")
    public static boolean runTransaction(@NonNull final SQLiteDatabase    db,
                                         @NonNull final Callable<Boolean> callable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            db.beginTransactionNonExclusive();
        else
            db.beginTransaction();

        try {
            final Boolean result = Utils.safeRun(callable);
            if (result != null && result) {
                db.setTransactionSuccessful();
                return true;
            }
        }
        catch (Exception exception) {
            CoreLogger.log("failed transaction, callable " + callable, exception);
        }
        finally {
            db.endTransaction();
        }

        CoreLogger.logError("failed transaction, callable " + callable);
        return false;
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
    public static boolean runTransaction(@NonNull final SQLiteDatabase db,
                                         @NonNull final Runnable       runnable) {
        //noinspection Convert2Lambda
        return runTransaction(db, new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return Utils.safeRun(runnable);
            }
        });
    }

    private static boolean execSqlWrapper(@NonNull final SQLiteDatabase db, @NonNull String sql) {
        try {
            if (!sql.endsWith(";")) sql += ";";
            CoreLogger.log("about to execute '" + sql + "'");
            db.execSQL(sql);
            return true;
        }
        catch (Exception exception) {
            CoreLogger.log(sql, exception);
            return false;
        }
    }

    private static Context fixContext(final Context context) {
        return context != null ? context: Utils.getApplication().getApplicationContext();
    }

    private static final int        BUFFER_SIZE         = 1024;

    /**
     * Executes SQL from the Android's asset.
     *
     * @param context
     *        The context (or null for default one)
     *
     * @param db
     *        The database
     *
     * @param sql
     *        The name of the asset containing SQL statement(s) to execute
     *
     * @return  {@code true} if script was executed successfully, {@code false} otherwise
     */
    public static boolean execSql(Context context, @NonNull final SQLiteDatabase db,
                                  @NonNull final String sql) {
        context = fixContext(context);
        try {
            final InputStream inputStream = context.getAssets().open(sql);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = inputStream.read(buffer)) != -1)
                outputStream.write(buffer, 0, length);
            outputStream.close();
            inputStream.close();

            return execSql(db, outputStream.toString().split(";"));
        }
        catch (/*IO*/Exception exception) {
            CoreLogger.log("failed SQL script " + sql, exception);
            return false;
        }
    }

    /**
     * Executes SQL provided.
     *
     * @param db
     *        The database
     *
     * @param sql
     *        The script(s) to execute
     *
     * @return  {@code true} if script was executed successfully, {@code false} otherwise
     */
    public static boolean execSql(@NonNull final SQLiteDatabase db, final String... sql) {
        if (sql == null || sql.length == 0) {
            CoreLogger.logWarning("nothing to do");
            return false;
        }
        try {
            boolean result = true;
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < sql.length; i++) {
                final String sqlStatement = sql[i].trim();
                if (sqlStatement.length() > 0)
                    if (!execSqlWrapper(db, sqlStatement)) result = false;
            }
            return result;
        }
        catch (Exception exception) {
            CoreLogger.log(Arrays.toString(sql), exception);
            return false;
        }
    }

    /**
     * Clears (drops) tables, virtual tables and views based on information from the 'sqlite_master'.
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
        if (Utils.cursorHelper(cursor, handler, 0, false, null)) {
            try {
                boolean result = true;

                if (handler.mTables.isEmpty())
                    CoreLogger.logWarning("no cache tables found");

                for (final String view: handler.mViews) {
                    CoreLogger.log("about to drop view " + view);
                    if (!execSql(db, String.format(DROP_VIEW,  view ))) result = false;
                }
                for (final String table: handler.mVTables) {
                    CoreLogger.log("about to drop virtual table " + table);
                    if (!execSql(db, String.format(DROP_TABLE, table))) result = false;
                }
                for (final String table: handler.mTables) {
                    CoreLogger.log("about to drop table " + table);
                    if (!execSql(db, String.format(DROP_TABLE, table))) result = false;
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
        public boolean handle(final Cursor cursor) {
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
        if (newVersion > oldVersion)
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

            if      (pathSegmentsSize == 1)
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
     *        The context (or null for default one)
     */
    @SuppressWarnings("WeakerAccess")
    public static void copyDb(final Context context) {
        copyDb(context, null, null);
    }

    /**
     * Copies the database to the file specified (in debug builds only,
     * see {@link Utils#isDebugMode}).
     *
     * @param context
     *        The context (or null for default one)
     *
     * @param srcDb
     *        The database to copy, or null (for default one: {@code cache.db})
     *
     * @param dstDb
     *        The file to copy database to, or null (for default backup directory and file name)
     */
    public static void copyDb(Context context, final File srcDb, final File dstDb) {
        context = fixContext(context);

        if (Utils.isDebugMode(context.getPackageName()))
            copyFile(context, getSrcDb(context, srcDb), dstDb);
        else
            CoreLogger.logWarning("db copying is available in debug builds only; " +
                    "please consider to use CoreLogger.registerShakeDataSender()");
    }

    private static void copyFile(@NonNull final Context context, @NonNull final File srcFile,
                                 final File dstFile) {
        final String  permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        //noinspection Convert2Lambda
        final boolean result     = new CorePermissions.RequestBuilder(
                    context instanceof Activity ? (Activity) context: null)
                .addOnGranted(permission, new Runnable() {
                    @Override
                    public void run() {
                        copyFileSync(context, srcFile, dstFile, null);
                    }
                })
                .setRationale(R.string.yakhont_permission_storage)
                .request();
        CoreLogger.log(permission + " request result: " + (result ? "already granted": "not granted yet"));
    }

    private static File getSrcDb(Context context, final File srcDb) {
        context = fixContext(context);
        return srcDb != null ? srcDb: context.getDatabasePath(DB_NAME);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static File copyDbSync(Context context, final File srcDb, final File dstDb,
                                  final Map<String, Exception> errors) {
        context = fixContext(context);
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
