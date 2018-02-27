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

package akha.yakhont;

import akha.yakhont.Core.Utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The <code>CoreLogger</code> class is responsible for logging.
 * Call {@link #setLogLevel} or {@link #setFullInfo} to set {@link Level log level}.
 * Call {@link #setShowStack} to force stack trace logging.
 * Call {@link #setShowThread} to force thread info logging.
 *
 * @author akha
 */
public class CoreLogger {

    /**
     * The logging priority levels.
     */
    public enum Level {
        /** The "debug" priority. */
        DEBUG,
        /** The "info" priority. */
        INFO,
        /** The "warning" priority. */
        WARNING,
        /** The "error" priority. */
        ERROR,
        /** The "silent" priority, which just switches logging off. */
        SILENT      // should be last in enum
    }

    private static final String                         FORMAT                   = "%s: %s";
    private static final String                         FORMAT_INFO              = "%s.%s(line %d)";
    private static final String                         FORMAT_THREAD            = "[%s] %s";

    private static final String                         CLASS_NAME               = CoreLogger.class.getName();

    private static final Level                          LEVEL_STACK              = Level.ERROR;
    private static final Level                          LEVEL_THREAD             = Level.WARNING;

    private static       LoggerExtender                 sLoggerExtender;

    private static final AtomicReference<String>        sTag                     = new AtomicReference<>();

    private static final AtomicReference<Level>         sLogLevel                = new AtomicReference<>(Level.ERROR);
    private static final AtomicBoolean                  sForceShowStack          = new AtomicBoolean();
    private static final AtomicBoolean                  sForceShowThread         = new AtomicBoolean();

    /**
     * Allows usage of 3-rd party loggers. Please refer to {@link #setLoggerExtender(LoggerExtender)}.
     */
    @SuppressWarnings("unused")
    public interface LoggerExtender {

        /**
         * Intended to log the info provided. The way of logging is up to implementation.
         * When logging is handled by this {@code LoggerExtender}, this method must return {@code true}.
         * If this method returns {@code false}, {@code CoreLogger} will attempts to handle the logging on its own.
         *
         * @param level
         *        The logging priority level
         *
         * @param msg
         *        The message to log
         *
         * @param throwable
         *        The Throwable to log (if any)
         *
         * @param showStack
         *        Indicates whether the stack trace should be logged too
         *
         * @param stackTraceElement
         *        The stack trace to log
         *
         * @return  {@code true} to prevent {@code CoreLogger} from logging that info, {@code false} otherwise
         */
        boolean log(Level level, String msg, Throwable throwable, boolean showStack, StackTraceElement stackTraceElement);
    }

    private CoreLogger() {
    }

    /**
     * Gets the registered {@code LoggerExtender} (if any).
     *
     * @return  {@code LoggerExtender} or null
     */
    @SuppressWarnings("unused")
    public static LoggerExtender getLoggerExtender() {
        return sLoggerExtender;
    }

    /**
     * Registers the 3-rd party logger to use (as add-on or full replacement for {@code CoreLogger}).
     *
     * @param loggerExtender
     *        The wrapper for 3-rd party logger
     */
    @SuppressWarnings("unused")
    public static void setLoggerExtender(final LoggerExtender loggerExtender) {
        sLoggerExtender = loggerExtender;
    }

    /**
     * Indicates whether the detailed logging was enabled or not.
     *
     * @return  The detailed logging mode state
     */
    public static boolean isFullInfo() {
        return isFullInfo(sLogLevel.get());
    }

    private static boolean isFullInfo(final Level level) {
        return level.ordinal() == Level.DEBUG.ordinal();
    }

    /**
     * Sets the detailed logging mode; normally called only once from Application.onCreate()
     * or some other starting point.
     *
     * @param fullInfo
     *        The value to set
     *
     * @return  The previous state of the detailed logging mode
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setFullInfo(final boolean fullInfo) {
        return isFullInfo(setLogLevel(fullInfo ? Level.DEBUG: Level.ERROR));
    }

    /**
     * Sets the log level.
     *
     * @param level
     *        The value to set
     *
     * @return  The previous value of the log level
     */
    @SuppressWarnings("unused")
    public static Level setLogLevel(@NonNull final Level level) {
        return sLogLevel.getAndSet(level);
    }

    /**
     * Sets whether the stack trace should be logged for all methods.
     *
     * @param showStack
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static boolean setShowStack(final boolean showStack) {
        return sForceShowStack.getAndSet(showStack);
    }

    /**
     * Sets whether the thread info should be logged for all methods.
     *
     * @param showThread
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static boolean setShowThread(final boolean showThread) {
        return sForceShowThread.getAndSet(showThread);
    }

    /**
     * Sets the Android {@link Log}'s tag.
     *
     * @param tag
     *        The value to set
     *
     * @return  The previous value of the {@link Log} tag
     */
    @SuppressWarnings("UnusedReturnValue")
    public static String setTag(@NonNull final String tag) {
        return sTag.getAndSet(tag);
    }

    /**
     * Gets the Android {@link Log}'s tag.
     *
     * @return  The tag's value
     */
    @NonNull
    public static String getTag() {
        final String tag = sTag.get();
        return tag == null ? CLASS_NAME: tag;
    }

    /**
     * Performs logging with default ({@link Level#DEBUG DEBUG}) level.
     *
     * @param str
     *        The message to log
     */
    public static void log(@NonNull final String str) {
        log(Level.DEBUG, str);
    }

    /**
     * Performs logging with default ({@link Level#ERROR ERROR}) level.
     *
     * @param str
     *        The message to log
     *
     * @param throwable
     *        The Throwable to log
     */
    public static void log(@NonNull final String str, final Throwable throwable) {
        logError(str, throwable);
    }

    /**
     * Performs logging with ({@link Level#WARNING WARNING}) level.
     *
     * @param str
     *        The message to log
     */
    public static void logWarning(@NonNull final String str) {
        log(Level.WARNING, str);
    }

    /**
     * Performs logging with ({@link Level#ERROR ERROR}) level.
     *
     * @param str
     *        The message to log
     */
    public static void logError(@NonNull final String str) {
        log(Level.ERROR, str);
    }

    /**
     * Performs logging with ({@link Level#ERROR ERROR}) level.
     *
     * @param str
     *        The message to log
     *
     * @param throwable
     *        The Throwable to log
     */
    public static void logError(@NonNull final String str, final Throwable throwable) {
        log(Level.ERROR, str, throwable);
    }

    /**
     * Performs logging.
     *
     * @param level
     *        The log level
     *
     * @param str
     *        The message to log
     */
    public static void log(@NonNull final Level level, @NonNull final String str) {
        log(level, str, null);
    }

    /**
     * Performs logging.
     *
     * @param level
     *        The log level
     *
     * @param str
     *        The message to log
     *
     * @param throwable
     *        The Throwable to log
     */
    public static void log(@NonNull final Level level, @NonNull final String str, final Throwable throwable) {
        log(level, str, throwable, sForceShowStack.get() || level.ordinal() >= LEVEL_STACK.ordinal());
    }

    /**
     * Performs logging.
     *
     * @param level
     *        The log level
     *
     * @param str
     *        The message to log
     *
     * @param showStack
     *        Indicates whether the stack trace should be logged or not
     */
    public static void log(@SuppressWarnings("SameParameterValue") @NonNull final Level level,
                           @SuppressWarnings("SameParameterValue") @NonNull final String str,
                           @SuppressWarnings("SameParameterValue") final boolean showStack) {
        log(level, str, null, showStack);
    }

    private static void log(@NonNull final Level level, @NonNull final String str, Throwable throwable, final boolean showStack) {
        final boolean isLog = level.ordinal() >= sLogLevel.get().ordinal();

        final Exception stackTrace = isLog && (showStack || isMethodInfo()) ?
                new RuntimeException("CoreLogger stack trace"): null;
        final StackTraceElement traceElement = stackTrace == null ? null: getStackTraceElement(stackTrace);

        if (sLoggerExtender != null && sLoggerExtender.log(
                level, str, throwable, showStack, traceElement)) return;

        if (isLog) log(level, str, throwable, showStack, stackTrace, traceElement);
    }

    private static StackTraceElement getStackTraceElement(@NonNull final Exception exception) {
        final StackTraceElement[] stackTraceElements = exception.getStackTrace();

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < stackTraceElements.length; i++)
            if (!stackTraceElements[i].getClassName().equals(CLASS_NAME))
                return stackTraceElements[i];

        // should never happen
        Log.e(getTag(), "can not find StackTraceElement");
        return null;
    }

    @NonNull
    private static String stripPackageName(@NonNull final String fullName) {
        return fullName.substring(fullName.lastIndexOf(".") + 1);
    }

    private static boolean isMethodInfo() {
        return isFullInfo();
    }

    @NonNull
    private static String addMethodInfo(@NonNull final Level level, @NonNull String str, final StackTraceElement traceElement) {
        if (isMethodInfo()) {
            final String methodInfo = traceElement == null ? null: String.format(getLocale(), FORMAT_INFO,
                    stripPackageName(traceElement.getClassName()),
                    traceElement.getMethodName(),
                    traceElement.getLineNumber());
            if (methodInfo != null) str = String.format(FORMAT, methodInfo, str);
        }
        return sForceShowThread.get() || level.ordinal() >= LEVEL_THREAD.ordinal()
                ? String.format(FORMAT_THREAD, Thread.currentThread().getName(), str): str;
    }

    private static void log(@NonNull final Level level, @NonNull final String str, Throwable throwable,
                            final boolean showStack, final Exception stackTrace, final StackTraceElement traceElement) {
        if (throwable == null && showStack) throwable = stackTrace;
        final String tag = getTag(), data = addMethodInfo(level, str, traceElement);

        switch (level) {
            case DEBUG:
                if (throwable == null)
                    Log.d(tag, data);
                else
                    Log.d(tag, data, throwable);
                break;

            case INFO:
                if (throwable == null)
                    Log.i(tag, data);
                else
                    Log.i(tag, data, throwable);
                break;

            case WARNING:
                if (throwable == null)
                    Log.w(tag, data);
                else
                    Log.w(tag, data, throwable);
                break;

            default:            // should never happen
                Log.e(tag, "unknown log level " + level.name());

            case SILENT:        // backdoor for logging - even in silent mode
//                break;        // uncomment break to close the backdoor

            case ERROR:
                if (throwable == null)
                    Log.e(tag, data);
                else
                    Log.e(tag, data, throwable);
                break;
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Locale getLocale() {
        return Locale.getDefault();
    }

    private static final String                         LOGCAT_CMD               = "logcat -d";
    private static final int                            LOGCAT_BUFFER_SIZE       = 1024;

    private interface LogHandler {
        @SuppressWarnings("RedundantThrows")
        void handle(String line) throws IOException;
    }

    private static void getLog(@NonNull final String cmd, @NonNull final LogHandler handler) {
        Process process = null;
        try {
            @SuppressWarnings("ConstantConditions") final String cmdToExecute =
                    cmd == null ? LOGCAT_CMD: cmd;
            log("about to execute " + cmdToExecute);
            process = Runtime.getRuntime().exec(Utils.removeExtraSpaces(cmdToExecute).split(" "));
            final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()), LOGCAT_BUFFER_SIZE);

            String line;
            while ((line = reader.readLine()) != null)
                handler.handle(line);
        }
        catch (IOException e) {
            log("failed running logcat", e);
        }
        finally {
            if (process != null) process.destroy();
        }
    }

    /**
     * Collects records from the main log buffer of the Android logging system.
     *
     * @param list
     *        The list to store log records (or null to create the new one)
     *
     * @param clearList
     *        Indicates whether the list should be cleared before adding new records
     *
     * @param cmd
     *        <code>logcat</code> command line, or null for the default one ("logcat -d")
     *
     * @return  The list with log records
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public static List<String> getLogCat(List<String> list, final boolean clearList, final String cmd) {
        @SuppressWarnings("Convert2Diamond")
        final List<String> listActual = (list == null) ? new ArrayList<String>(): list;
        if (clearList) listActual.clear();

        //noinspection Anonymous2MethodRef,Convert2Lambda
        getLog(cmd, new LogHandler() {
            @Override
            public void handle(String line) throws IOException {
                listActual.add(line);
            }
        });
        return list;
    }

    /**
     * Sends e-mail with log records collected by the {@link #getLogCat getLogCat()}.
     *
     * @param activity
     *        The Activity
     *
     * @param address
     *        The e-mail address
     */
    @SuppressWarnings("unused")
    public static void sendLogCat(@NonNull final Activity activity, @NonNull final String address) {
        sendLogCat(activity, address, "logcat", true, null);
    }

    /**
     * Sends e-mail with log records collected by the {@link #getLogCat getLogCat()}.
     *
     * @param activity
     *        The Activity
     *
     * @param address
     *        The e-mail address
     *
     * @param subject
     *        The e-mail subject
     *
     * @param clearList
     *        Indicates whether the list should be cleared before adding new records
     *
     * @param cmd
     *        <code>logcat</code> command line, or null for the default one ("logcat -d")
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public static void sendLogCat(@NonNull final Activity activity,
                                  @NonNull final String address, @NonNull final String subject,
                                  final boolean clearList, final String cmd) {
        Utils.sendEmail(activity, new String[] {address}, subject, TextUtils.join(System.getProperty("line.separator"),
                getLogCat(null, clearList, cmd)), null);
    }

    /**
     * Sends email with log records and some additional info (if any).
     * The ANR traces are always added (if available).
     *
     * @param context
     *        The Context
     *
     * @param addresses
     *        The list of email addresses to send data
     *
     * @param subject
     *        The email subject (or null)
     *
     * @param cmd
     *        The logcat command to execute (or null to execute the default one)
     *
     * @param hasScreenShot
     *        {@code true} to include screen shot
     *
     * @param hasDb
     *        {@code true} to include cache database snapshot
     *
     * @param moreFiles
     *        Additional files to include (or null)
     */
    @SuppressWarnings("WeakerAccess")
    public static void sendData(final Context context, final String[] addresses, final String subject,
                                final String cmd, final boolean hasScreenShot, final boolean hasDb,
                                final String[] moreFiles) {
        //noinspection Convert2Lambda
        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    Sender.sendEmailSync(context, addresses, subject, cmd, hasScreenShot, hasDb, moreFiles);
                }
                catch (Exception e) {
                    log("failed sending email", e);
                }
            }
        });
    }

    /**
     * Registers email addresses to which on shaking device to send email with log records
     * and some additional info (if any). The ANR traces are always added (if available).
     *
     * @param context
     *        The Context
     *
     * @param address
     *        The email address to send data
     */
    @SuppressWarnings("unused")
    public static void registerShakeDataSender(final Context context, final String address) {
        if (address == null) {
            logError("address == null");
            return;
        }
        registerShakeDataSender(context, new String[] {address}, null, null, true, true, null);
    }

    /**
     * Registers email addresses to which on shaking device to send email with log records
     * and some additional info (if any). The ANR traces are always added (if available).
     *
     * @param context
     *        The Context
     *
     * @param addresses
     *        The list of email addresses to send data
     *
     * @param subject
     *        The email subject (or null)
     *
     * @param cmd
     *        The logcat command to execute (or null to execute the default one)
     *
     * @param hasScreenShot
     *        {@code true} to include screen shot
     *
     * @param hasDb
     *        {@code true} to include cache database snapshot
     *
     * @param moreFiles
     *        Additional files to include (or null)
     */
    @SuppressWarnings("WeakerAccess")
    public static void registerShakeDataSender(final Context context, final String[] addresses,
                                               @SuppressWarnings("SameParameterValue") final String  subject,
                                               @SuppressWarnings("SameParameterValue") final String  cmd,
                                               @SuppressWarnings("SameParameterValue") final boolean hasScreenShot,
                                               @SuppressWarnings("SameParameterValue") final boolean hasDb,
                                               @SuppressWarnings("SameParameterValue") final String[] moreFiles) {
        if (context == null || addresses == null || addresses.length == 0) {
            logError("no arguments");
            return;
        }
        final Context contextToUse = context.getApplicationContext();

        //noinspection Convert2Lambda
        new ShakeEventListener(contextToUse, new Runnable() {
            @Override
            public void run() {
                sendData(contextToUse, addresses, subject, cmd, hasScreenShot, hasDb, moreFiles);
            }
        }).register();
    }

    private static class ShakeEventListener implements SensorEventListener {

        private static final float                      THRESHOLD                =    2;
        private static final int                        DELAY                    = 1000;

        private final Runnable          mHandler;
        private final SensorManager     mSensorManager;
        private       Sensor            mSensor;
        private       long              mLastShakeTime;

        public ShakeEventListener(@NonNull final Context context, @NonNull final Runnable handler) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager != null)
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            else
                logWarning(Context.SENSOR_SERVICE + ": mSensorManager == null");
            mHandler = handler;
        }

        public void register() {
            if (mSensorManager == null) return;
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
            logWarning("shake event listener registered");
        }

        @SuppressWarnings("unused")
        public void unregister() {
            if (mSensorManager == null) return;
            mSensorManager.unregisterListener(this, mSensor);
            logWarning("shake event listener unregistered");
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

            double gravityTotal = 0;
            for (int i = 0; i <= 2; i++) {
                final float gravity = event.values[i] / SensorManager.GRAVITY_EARTH;
                gravityTotal += gravity * gravity;
            }
            if (Math.sqrt(gravityTotal) < THRESHOLD) return;

            final long currentTime = System.currentTimeMillis();
            if (mLastShakeTime + DELAY > currentTime) return;

            log("shake detected");
            mLastShakeTime = currentTime;
            mHandler.run();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    private static class Sender {

        private static final String                     ANR_TRACES               = "/data/anr/traces.txt";
        private static final String                     ZIP_PREFIX               = "data_yakhont";
        private static final int                        SCREENSHOT_JPEG_QUALITY  = 100;

        private static void sendEmailSync(final Context context, final String[] addresses, final String subject,
                                          final String cmd, final boolean hasScreenShot, final boolean hasDb,
                                          final String[] moreFiles) {
            if (context == null || addresses == null || addresses.length == 0) {
                logError("no arguments");
                return;
            }

            final Activity activity = Utils.getCurrentActivity();
            if (activity == null) return;

            final File tmpDir    = Utils.getTmpDir(context);
            final String suffix  = Utils.getTmpFileSuffix();
            final boolean hasAnr = new File(ANR_TRACES).exists();

            File db = null, screenShot = null;

            final Map<String, Exception> errors = new ArrayMap<>();

            removePreviousZipFiles(tmpDir);

            final ArrayList<String>     list = new ArrayList<>();
            if (hasAnr)                 list.add(ANR_TRACES);
            if (hasScreenShot) {
                screenShot = getScreenShot(activity, tmpDir, suffix, errors);
                if (screenShot != null) list.add(screenShot.getAbsolutePath());
            }
            if (hasDb) {
                db = BaseCacheProvider.copyDbSync(context, null, null, errors);
                if (db != null)         list.add(db.getAbsolutePath());
            }
            if (moreFiles != null)
                for (final String file: moreFiles)
                    if (!TextUtils.isEmpty(file))
                                        list.add(file);

            final File log = getLogFile(cmd, tmpDir, suffix, errors);
            if (log != null)            list.add(log.getAbsolutePath());

            final String subjectToSend = subject == null ? "log" + suffix: subject;
            final StringBuilder body = new StringBuilder(String.format(
                    "ANR traces %b, screenshot %b, database %b", hasAnr, hasScreenShot, hasDb));
            try {
                final File zipFile = getTmpFile(ZIP_PREFIX, suffix, "zip", tmpDir);
                if (!Utils.zip(list.toArray(new String[list.size()]), zipFile.getAbsolutePath(), errors)) return;

                final String newLine = System.getProperty("line.separator");
                for (final String error: errors.keySet())
                    body.append(newLine).append(newLine).append(error).append(newLine).append(errors.get(error));

                Utils.sendEmail(activity, addresses, subjectToSend, body.toString(), zipFile);
            }
//            catch (IOException e) {
//                log("failed creating tmp ZIP file", e);
//            }
            finally {
                delete(db);
                delete(screenShot);
                delete(log);
            }
        }

        private static void delete(final File file) {
            if (file == null) return;
            log("about to delete " + file);
            final boolean result = file.delete();
            if (!result) logWarning("can not delete " + file);
        }

        private static void removePreviousZipFiles(final File dir) {
            if (dir == null) return;

            @SuppressWarnings("Convert2Lambda")
            final File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(final File dir, final String name) {
                    return name.startsWith(ZIP_PREFIX);
                }
            });
            if (files == null) return;

            for (final File file: files)
                delete(file);
        }
/*
        private static File getTmpFile(final String prefix, final String suffix, final File dir)
                throws IOException {
            return File.createTempFile(prefix,
                    suffix.startsWith(".") ? suffix: "." + suffix, dir);
        }
*/
        private static File getTmpFile(final String prefix, final String suffix,
                                       final String extension, final File dir) {
            return new File(dir, String.format("%s%s.%s", prefix, suffix, extension));
        }

        private static File getLogFile(final String cmd, final File tmpDir, final String suffix,
                                       final Map<String, Exception> errors) {
            try {
                final File tmpFile = getTmpFile("log", suffix, "txt", tmpDir);
                final PrintWriter output = new PrintWriter(tmpFile);

                //noinspection Anonymous2MethodRef,Convert2Lambda
                getLog(cmd, new LogHandler() {
                    @Override
                    public void handle(String line) throws IOException {
                        output.println(line);
                    }
                });
                output.close();

                return tmpFile;
            }
            catch (Exception e) {
                handleError("failed creating log file", e, errors);
            }
            return null;
        }

        private static void handleError(final String text, final Exception exception,
                                        final Map<String, Exception> map) {
            log(text, exception);
            if (map != null) //noinspection ThrowableResultOfMethodCallIgnored
                map.put(text, exception);
        }

        private static File getScreenShot(final Activity activity, final File tmpDir, final String suffix,
                                          final Map<String, Exception> errors) {
            try {
                final View view = activity.getWindow().getDecorView().getRootView();
                final boolean savedValue = view.isDrawingCacheEnabled();

                final File tmpFile = getTmpFile("screen", suffix, "jpg", tmpDir);
                final FileOutputStream outputStream = new FileOutputStream(tmpFile);

                try {
                    view.setDrawingCacheEnabled(true);
                    view.getDrawingCache().compress(Bitmap.CompressFormat.JPEG, SCREENSHOT_JPEG_QUALITY, outputStream);
                    outputStream.close();
                    return tmpFile;
                }
                finally {
                    view.setDrawingCacheEnabled(savedValue);
                }
            }
            catch (Exception e) {
                handleError("failed creating screenshot", e, errors);
                return null;
            }
        }
    }
}
