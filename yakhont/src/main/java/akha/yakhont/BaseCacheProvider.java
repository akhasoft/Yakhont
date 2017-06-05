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

package akha.yakhont;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger.Level;
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
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 *     package="com.mypackage" &gt;
 *
 *     &lt;application ... &gt;
 *     ...
 *
 *     &lt;provider
 *         android:authorities="com.mypackage.provider"
 *         android:name="akha.yakhont.BaseCacheProvider"
 *         android:enabled="true"
 *         android:exported="false" /&gt;
 *
 *     &lt;/application&gt;
 * &lt;/manifest&gt;
 * </pre>
 *
 * If you prefer to design another authority (for example, not based on the name of the package), please implement the
 * {@link akha.yakhont.Core.UriResolver} interface and register it via the
 * {@link akha.yakhont.Core.Utils#setUriResolver setUriResolver()} method.
 *
 * @author akha
 */
@SuppressLint("Registered")
@SuppressWarnings("unused")
public class BaseCacheProvider extends ContentProvider {

    private static final String         DB_NAME           = "cache.db";
    private static final int            DB_VERSION        = 1;

    private static final String         MIME_DIR          = "vnd.android.cursor.dir";
    private static final String         MIME_ITEM         = "vnd.android.cursor.item";
    private static final String         MIME_SUBTYPE      = "/vnd.%s.%s";

    private static final String         SELECTION_ID      = BaseColumns._ID + "=?";

    private static final String         CREATE_INDEX      = "CREATE INDEX IF NOT EXISTS idx_%s_id ON %s (" +
                                                            BaseColumns._ID + " ASC);";
    private static final String         CREATE_TABLE      = "CREATE TABLE IF NOT EXISTS %s (" + BaseColumns._ID +
                                                            " INTEGER PRIMARY KEY AUTOINCREMENT";
    private static final String         ALTER_TABLE       = "ALTER TABLE %s ADD COLUMN %s %s;";

    private final Matcher               mUriMatcher       = new Matcher();

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

    private static String[] getSelectionIdArgs(@NonNull final Uri uri) {
        return new String[] {uri.getLastPathSegment()};
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
        final String tableName = Utils.getLoaderTableName(uri);

        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = insert(db, tableName, values);

        if (!silent) CoreLogger.log(String.format(getLocale(), "table %s, id %d", tableName, id));

        if (id == -1 && isMissedColumnsOrTable(db, tableName, bulkValues == null ? new ContentValues[] {values}: bulkValues)) {
            id = insert(db, tableName, values);
            CoreLogger.log(String.format(getLocale(), "table %s, new id %d", tableName, id));
        }
        if (id == -1) CoreLogger.logError("table " + tableName + ": insert error");

        return id == -1 ? null: ContentUris.withAppendedId(uri, id);
    }

    private long insert(@NonNull final SQLiteDatabase db, @NonNull final String tableName, @NonNull final ContentValues values) {
        return db.insert(tableName, null, values);
    }

    private boolean isMissedColumnsOrTable(@NonNull final SQLiteDatabase db, @NonNull final String tableName,
                                           @NonNull final ContentValues[] bulkValues) {
        final Map<String, CreateTableScriptBuilder.DataType> columns = getColumns(tableName, bulkValues);

        if (!isTableExist(tableName)) {
            createTable(db, tableName, columns);
            return true;
        }

        return addColumns(db, tableName, columns);
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
            if (!isColumnExist(tableName, columnName)) {
                execSQL(db, String.format(ALTER_TABLE, tableName, columnName,
                        columns.get(columnName).name()));
                columnsAdded = true;
            }
        return columnsAdded;
    }

    @SuppressLint("ObsoleteSdkInt")
    private Set<String> getKeySet(@NonNull final ContentValues values) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) return values.keySet();

        final Set<String> set = new LinkedHashSet<>();
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
        final Map<String, CreateTableScriptBuilder.DataType> columns = new LinkedHashMap<>();

        for (final ContentValues values: bulkValues) {
            for (final String key: getKeySet(values))
                if (!columns.containsKey(key) && values.get(key) != null) columns.put(key,
                        values.getAsByteArray(key) == null ?
                                CreateTableScriptBuilder.DataType.TEXT:
                                CreateTableScriptBuilder.DataType.BLOB);
            if (values.size() == columns.size()) return columns;
        }

        for (final String key: getKeySet(bulkValues[0]))
            if (!columns.containsKey(key)) {
                final CreateTableScriptBuilder.DataType type = CreateTableScriptBuilder.DataType.TEXT;
                CoreLogger.logWarning(String.format("table %s, column %s: no data found, column type forced to %s",
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

        final String tableName = Utils.getLoaderTableName(uri);
        CoreLogger.log(String.format(getLocale(), "table %s, %d rows", tableName, bulkValues.length));

        switch (mUriMatcher.match(uri)) {
            case ALL:
                runTransaction(mDbHelper.getWritableDatabase(), new Runnable() {
                    @Override
                    public void run() {
                        for (final ContentValues values: bulkValues)
                            insert(uri, values, true, bulkValues);
                    }
                });
                return bulkValues.length;

            case ID:        // fall through
            default:
                CoreLogger.logError("uri " + uri);
                return 0;
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final String tableName = Utils.getLoaderTableName(uri);

        switch (mUriMatcher.match(uri)) {       // fall through
            case ID:
                selection       = SELECTION_ID;
                selectionArgs   = getSelectionIdArgs(uri);

            case ALL:
                try {
                    return mDbHelper.getReadableDatabase().query(tableName, projection, selection, selectionArgs, null, null, sortOrder);
                }
                catch (Exception e) {
                    CoreLogger.log(Level.WARNING, String.format("table %s, selection %s, selection args %s",
                            tableName, selection, Arrays.deepToString(selectionArgs)), e);
                    return BaseResponse.EMPTY_CURSOR;
                }

            default:
                CoreLogger.logError("unknown uri " + uri);
                return BaseResponse.EMPTY_CURSOR;
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        final String tableName = Utils.getLoaderTableName(uri);

        if (!isTableExist(tableName)) return 0;

        switch (mUriMatcher.match(uri)) {       // fall through
            case ID:
                selection       = SELECTION_ID;
                selectionArgs   = getSelectionIdArgs(uri);

            case ALL:
                // from docs: To remove all rows and get a count pass "1" as the whereClause.
                if (selection == null) selection = "1";
                final int rows = mDbHelper.getWritableDatabase().delete(tableName, selection, selectionArgs);

                CoreLogger.log(String.format(getLocale(), "table %s, %d rows", tableName, rows));
                return rows;

            default:
                CoreLogger.logError("unknown uri " + uri);
                return 0;
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final String tableName = Utils.getLoaderTableName(uri);

        if (!isTableExist(tableName)) return 0;

        switch (mUriMatcher.match(uri)) {       // fall through
            case ID:
                selection       = SELECTION_ID;
                selectionArgs   = getSelectionIdArgs(uri);

            case ALL:
                final int rows = mDbHelper.getWritableDatabase().update(tableName, values, selection, selectionArgs);

                CoreLogger.log(String.format(getLocale(), "table %s, %d rows", tableName, rows));
                return rows;

            default:
                CoreLogger.logError("unknown uri " + uri);
                return 0;
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public String getType(@NonNull Uri uri) {
        final String table = String.format(MIME_SUBTYPE, uri.getAuthority(), Utils.getLoaderTableName(uri));

        switch (mUriMatcher.match(uri)) {
            case ALL:
                return MIME_DIR + table;
            case ID:
                return MIME_ITEM + table;
            default:
                CoreLogger.logError("unknown uri " + uri);
                return null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isTableExist(@NonNull final String tableName) {
        return isExist(tableName, BaseColumns._ID);
    }

    private boolean isColumnExist(@NonNull final String tableName, @NonNull final String columnName) {
        return isExist(tableName, columnName);
    }

    /**
     * Checks whether the given column exists.
     *
     * @param tableName
     *        The table name
     *
     * @param columnName
     *        The column name
     *
     * @return  {@code true} if the column exists, {@code false} otherwise
     */
    protected boolean isExist(@NonNull final String tableName, @NonNull final String columnName) {
        try {
            // writable to trigger onCreate
            mDbHelper.getWritableDatabase().query(tableName, new String[] {columnName}, null, null, null, null, null, "1");
            return true;
        }
        catch (SQLException e) {
            CoreLogger.log(Level.DEBUG, tableName, e);
            return false;
        }
    }

    /**
     * Creates table.
     *
     * @param db
     *        The database
     *
     * @param tableName
     *        The table name
     *
     * @param columns
     *        The list of columns
     */
    protected void createTable(@NonNull final SQLiteDatabase db, @NonNull final String tableName,
                               @NonNull @Size(min = 1) final Map<String, CreateTableScriptBuilder.DataType> columns) {
        CoreLogger.log(String.format("%s", tableName));

        final CreateTableScriptBuilder builder = new CreateTableScriptBuilder(tableName);
        for (final String columnName: columns.keySet())
            builder.addColumn(columnName, columns.get(columnName));
        execSQL(db, builder.create());

        execSQL(db, String.format(CREATE_INDEX, tableName, tableName));
    }

    /**
     * Simple class to generate typical CREATE TABLE script.
     * The {@link BaseColumns#_ID _ID} column is added by default.
     */
    public static class CreateTableScriptBuilder {

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

        private final String      mTable;
        private final Set<String> mColumns = Utils.newSet();

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
     */
    @SuppressLint("ObsoleteSdkInt")
    protected void runTransaction(@NonNull final SQLiteDatabase db, @NonNull final Runnable runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            db.beginTransactionNonExclusive();
        else
            db.beginTransaction();

        try {
            runnable.run();
            db.setTransactionSuccessful();
        }
        catch (Exception e) {
            CoreLogger.log("transaction failed", e);
            throw e;
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
     */
    protected void execSQL(@NonNull final SQLiteDatabase db, @NonNull final String sql) {
        CoreLogger.log(sql);
        db.execSQL(sql);
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
    protected boolean executeSQLScript(@NonNull final Context context, @NonNull final SQLiteDatabase db, @NonNull final String scriptName) {
        try {
            final InputStream inputStream = context.getAssets().open(scriptName);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1)
                outputStream.write(buffer, 0, length);
            outputStream.close();
            inputStream.close();

            final String[] script = outputStream.toString().split(";");
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < script.length; i++) {
                final String sqlStatement = script[i].trim();
                if (sqlStatement.length() > 0)
                    execSQL(db, sqlStatement + ";");
            }
            return true;
        }
        catch (IOException | SQLException e) {
            CoreLogger.log(scriptName, e);
            return false;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Called when database created.
     *
     * @param db
     *        The database
     */
    protected void onCreate(@SuppressWarnings("UnusedParameters") @NonNull final SQLiteDatabase db) {
        CoreLogger.log(String.format(getLocale(), "on create database %s, version %d", DB_NAME, getDbVersion()));
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
    protected void onUpgrade(@SuppressWarnings("UnusedParameters") @NonNull final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        CoreLogger.log(String.format(getLocale(), "on upgrade database %s from version %d to %d", DB_NAME, oldVersion, newVersion));
    /*
        if (newVersion > oldVersion) {  // handles upgrade
            switch (oldVersion) {
                case 1:        // fall through
                    executeSQLScript(database, "update_v2.sql");
                case 2:
                    executeSQLScript(database, "update_v3.sql");
                ...
            }
        }
        else ...                        // handles downgrade
     */
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
        private Match match(@NonNull final Uri uri) {
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
        return CoreLogger.getLocale();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // copyDbs below are for debug only

    /**
     * Copies the current database to the default backup directory (in debug builds only, see {@link Utils#isDebugMode Utils.isDebugMode()}).
     *
     * @param context
     *        The context
     */
    @SuppressWarnings("WeakerAccess")
    public static void copyDb(@NonNull final Context context) {
        copyDb(context, null, null);
    }

    /**
     * Copies the database to the file specified (in debug builds only, see {@link Utils#isDebugMode Utils.isDebugMode()}).
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
            CoreLogger.logWarning(
                    "db copying is available in debug builds only; please consider to use CoreLogger.registerShakeDataSender()");
            return;
        }
        copyFile(context, getSrcDb(context, srcDb), dstDb);
    }

    private static void copyFile(@NonNull final Context context, @NonNull final File srcFile, final File dstFileOrg) {
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
        catch (Exception e) {
            final String text = "copying failed: " + srcFile;
            CoreLogger.log(text, e);
            if (errors != null) //noinspection ThrowableResultOfMethodCallIgnored
                errors.put(text, e);
            return null;
        }
    }

    private static void handleError(final String text, final Map<String, Exception> map) {
        CoreLogger.logError(text);
        if (map != null) //noinspection ThrowableResultOfMethodCallIgnored
            map.put(text, new RuntimeException("copy file stack trace"));
    }

    private static String getDstFileName(@NonNull final String name) {
        final String suffix = Utils.getTmpFileSuffix();
        final int idx = name.lastIndexOf('.');
        return idx < 0 ? name + suffix: name.substring(0, idx) + suffix + name.substring(idx);
    }
}
