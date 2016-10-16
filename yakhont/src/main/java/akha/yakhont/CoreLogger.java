/*
 * Copyright (C) 2016 akha, a.k.a. Alexander Kharitonov
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

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The <code>CoreLogger</code> class is responsible for logging. In addition to usability and flexible settings
 * it provides such build-in features as auto-disable logging in release builds, stack-trace and thread info logging,
 * support for 3-rd party loggers etc.
 *
 * <p>There is also the possibility to send log records via e-mail (useful for hardly-reproduced errors).
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
        SILENT      // switches off logging (via call to "setLevel"); should be last in the enum
    }

    private static final String                         FORMAT                   = "%s: %s";
    private static final String                         FORMAT_INFO              = "%s.%s(line %d)";
    private static final String                         FORMAT_THREAD            = "[%s] %s";

    private static final String                         CLASS_NAME               = CoreLogger.class.getName();

    private static       LoggerExtender                 sLoggerExtender;

    private static final AtomicReference<String>        sTag                     = new AtomicReference<>();

    private static final AtomicReference<Level>         sLogLevel                = new AtomicReference<>(Level.ERROR);
    private static final Level                          sForceShowStackLevel     = Level.ERROR;
    private static final Level                          sForceShowThreadLevel    = Level.ERROR;

    private static final AtomicBoolean                  sShowStack               = new AtomicBoolean();
    private static final AtomicBoolean                  sShowThread              = new AtomicBoolean();
    private static final AtomicBoolean                  sFullInfo                = new AtomicBoolean();

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
     * Indicates whether the detailed logging should be enabled or not.
     *
     * @return  The detailed logging flag
     */
    public static boolean isFullInfo() {    // supports different behaviour for debug and release builds
        return sFullInfo.get();
    }

    /**
     * Sets the detailed logging flag.
     *
     * @param fullInfo
     *        The value to set
     *
     * @return  The previous value of the detailed logging flag
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setFullInfo(final boolean fullInfo) { // normally should be set from Application.onCreate() only
        sLogLevel.set(fullInfo ? Level.DEBUG: Level.ERROR);
        return sFullInfo.getAndSet(fullInfo);
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
    public static String setTag(@NonNull final String tag) {    // same as above
        return sTag.getAndSet(tag);
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
        if (!isFullInfo())
            Log.w(getTag(), "new log level ignored: " + level);
        return isFullInfo() ? sLogLevel.getAndSet(level): sLogLevel.get();
    }

    /**
     * Sets whether the stack trace should be logged.
     *
     * @param showStack
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static boolean setShowStack(final boolean showStack) {
        if (!isFullInfo())
            Log.w(getTag(), "new show stack ignored: " + showStack);
        return isFullInfo() ? sShowStack.getAndSet(showStack): sShowStack.get();
    }

    /**
     * Sets whether the thread info should be logged.
     *
     * @param showThread
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static boolean setShowThread(final boolean showThread) {
        return sShowThread.getAndSet(showThread);
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

    private static StackTraceElement getStackTraceElement() {
        final Exception exception = new RuntimeException();
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

    @NonNull
    private static String addMethodInfo(@NonNull final Level level, @NonNull String str, final StackTraceElement stackTraceElement) {
        if (isFullInfo()) {
            final String methodInfo = stackTraceElement == null ? null: String.format(getLocale(), FORMAT_INFO,
                    stripPackageName(stackTraceElement.getClassName()),
                    stackTraceElement.getMethodName(),
                    stackTraceElement.getLineNumber());
            if (methodInfo != null) str = String.format(FORMAT, methodInfo, str);
        }
        return sShowThread.get() || level.ordinal() >= sForceShowThreadLevel.ordinal()
                ? String.format(FORMAT_THREAD, Thread.currentThread().getName(), str): str;
    }

    /**
     * Performs logging (the default level).
     *
     * @param str
     *        The message to log
     */
    public static void log(@NonNull final String str) {
        log(Level.DEBUG, str);
    }

    /**
     * Performs logging (the default level).
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
     * Performs logging (the "warning" level).
     *
     * @param str
     *        The message to log
     */
    public static void logWarning(@NonNull final String str) {
        log(Level.WARNING, str);
    }

    /**
     * Performs logging (the "error" level).
     *
     * @param str
     *        The message to log
     */
    public static void logError(@NonNull final String str) {
        log(Level.ERROR, str);
    }

    /**
     * Performs logging (the "error" level).
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
        log(level, str, throwable, sShowStack.get() || level.ordinal() >= sForceShowStackLevel.ordinal());
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
     *        Indicates whether the stack trace should be logged
     */
    public static void log(@SuppressWarnings("SameParameterValue") @NonNull final Level level,
                           @SuppressWarnings("SameParameterValue") @NonNull final String str,
                           @SuppressWarnings("SameParameterValue") final boolean showStack) {
        log(level, str, null, showStack);
    }

    private static void log(@NonNull final Level level, @NonNull final String str, Throwable throwable, final boolean showStack) {
        final StackTraceElement stackTraceElement = isNotLog(level) ? null: getStackTraceElement();

        if (sLoggerExtender != null && sLoggerExtender.log(level, str, throwable, showStack, stackTraceElement)) return;

        log(level, str, throwable, showStack, stackTraceElement);
    }

    private static boolean isNotLog(@NonNull final Level level) {
        return level.ordinal() < sLogLevel.get().ordinal();
    }

    private static void log(@NonNull final Level level, @NonNull final String str, Throwable throwable,
                            final boolean showStack, final StackTraceElement stackTraceElement) {
        if (isNotLog(level)) return;

        if (throwable == null)
            throwable = showStack && isFullInfo() ? new RuntimeException(
                    "this is not a real exception - just a stack trace"): null;

        final String tag = getTag(), data = addMethodInfo(level, str, stackTraceElement);

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
    public static List<String> logCatGet(List<String> list, final boolean clearList,
                                         final String cmd) {
        if (list == null) list = new ArrayList<>();
        if (clearList) list.clear();

        Process logCatProcess = null;
        try {
            logCatProcess = Runtime.getRuntime().exec(Core.Utils.removeExtraSpaces(
                    cmd == null ? LOGCAT_CMD: cmd).split(" "));
            final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    logCatProcess.getInputStream()), LOGCAT_BUFFER_SIZE);
            String line;
            while ((line = reader.readLine()) != null)
                list.add(line);
        }
        catch (IOException | IllegalArgumentException e) {
            log("failed", e);
        }
        finally {
            if (logCatProcess != null) logCatProcess.destroy();
        }
        return list;
    }

    /**
     * Sends e-mail with log records collected by the {@link #logCatGet logCatGet()}.
     *
     * @param activity
     *        The Activity
     *
     * @param address
     *        The e-mail address
     */
    @SuppressWarnings("unused")
    public static void logCatSend(@NonNull final Activity activity, @NonNull final String address) {
        logCatSend(activity, address, "logcat", true, null);
    }

    /**
     * Sends e-mail with log records collected by the {@link #logCatGet logCatGet()}.
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
    public static void logCatSend(@NonNull final Activity activity,
                                  @NonNull final String address, @NonNull final String subject,
                                  final boolean clearList, final String cmd) {
        Core.Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");

                intent.putExtra(Intent.EXTRA_EMAIL,   new String[] {address});
                intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                intent.putExtra(Intent.EXTRA_TEXT,    TextUtils.join("\n",
                        logCatGet(null, clearList, cmd)));

                activity.startActivity(Intent.createChooser(intent, "Send logcat..."));
            }
        });
    }
}
