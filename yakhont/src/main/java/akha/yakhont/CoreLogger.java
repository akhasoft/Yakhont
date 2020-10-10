/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont;

import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.technology.Dagger2.UiModule;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;                      // for javadoc
import android.gesture.GesturePoint;
import android.gesture.GestureStroke;
import android.gesture.Prediction;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaMuxer.OutputFormat;
import android.media.MediaRecorder.AudioSource;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.PixelCopy.OnPixelCopyFinishedListener;      // for javadoc
import android.view.Surface;
import android.view.View;
import android.view.Window;                                     // for javadoc
import android.widget.Toast;

import androidx.annotation.AnyRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.collection.ArrayMap;
import androidx.recyclerview.widget.RecyclerView;               // for javadoc

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The <code>CoreLogger</code> class is responsible for logging; provides methods / threads / stack traces
 * info, splits long strings, supports 3-rd party loggers, etc. Also contains some additional components:
 *
 * <p><ul>
 *   <li>for recording audio / video: {@link VideoRecorder VideoRecorder}</li>
 *   <li>for making screenshots: {@link #getScreenshot(String)}</li>
 *   <li>for detecting (and handling) shaking device: {@link ShakeEventListener ShakeEventListener}</li>
 *   <li>for gestures recognition (and handling too): {@link GestureHandler GestureHandler}</li>
 * </ul>
 *
 * <p>And all these components could be used independently, i.e. not for logging only but for any purpose you need.
 *
 * <p>Some of available logging features are:
 * <ul>
 *   <li>No logging in release builds (except errors)</li>
 *   <li>Stack trace support: {@link #log(Level, String, boolean)}</li>
 *   <li>Byte arrays logging: {@link #toHex(byte[])}, {@link #toHex(byte[], int, int, Locale, Charset)}</li>
 *   <li>The logcat support: {@link #getLogCat(List, boolean, String)}</li>
 *   <li>Sending error reports via e-mail: {@link #sendLogCat(Activity, String...)}, {@link #sendData sendData}</li>
 *   <li>Sending error reports via e-mail on shaking device (or making Z-gesture): {@link #registerDataSender(Context, String...)}</li>
 *   <li>3-rd party loggers support: {@link LoggerExtender LoggerExtender}</li>
 * </ul>
 *
 * <p>Call {@link #setLogLevel} to set {@link Level log level}.
 * <br>Call {@link #setShowStack} to force stack trace logging.
 * <br>Call {@link #setShowThread} to force thread info logging.
 *
 * @see LogDebug
 *
 * @author akha
 */
public class CoreLogger {

    /**
     * The logging priority levels.
     *
     * @see Log
     */
    public enum Level {
        /** The "verbose" priority (please refer to {@link Log#VERBOSE}). */
        VERBOSE,
        /** The "debug" priority (please refer to {@link Log#DEBUG}). */
        DEBUG,
        /** The "info" priority (please refer to {@link Log#INFO}). */
        INFO,
        /** The "warning" priority (please refer to {@link Log#WARN}). */
        WARNING,
        /** The "error" priority (please refer to {@link Log#ERROR}). */
        ERROR,
        /** The "silent" priority, which just switches logging off. */
        SILENT      // should be last in enum
    }

    private static final String                         FORMAT                   = "%s: %s";
    private static final String                         FORMAT_INFO              = "%s.%s(line %s)" ;
    private static final String                         FORMAT_THREAD            = "THREAD: [%s] %s";
    private static final String                         FORMAT_APP_ID            = "APP ID: %s, %s" ;

    private static final String                         CLASS_NAME               = CoreLogger.class.getName();

    private static final String                         STACK_TRACE_TITLE        = "Yakhont CoreLogger stack trace";
    private static final String                         DEFAULT_MESSAGE          = "failed";

    private static final Level                          LEVEL_STACK              = Level.ERROR;
    private static final Level                          LEVEL_THREAD             = Level.WARNING;
    private static final Level                          LEVEL_APP_ID             = Level.ERROR;

    /** The maximum tag length (before API 25), value is {@value}. */
    @SuppressWarnings("WeakerAccess")
    public  static final int                            MAX_TAG_LENGTH           =   23;
    /** The maximum log record length, value is {@value}. */
    @SuppressWarnings("WeakerAccess")
    public  static final int                            MAX_LOG_LENGTH           = 4000;
    private static final int                            MAX_LOG_LINE_LENGTH      =  128;

    /**
     * Returned by {@link LoggerExtender#log} (if any) to prevent {@code CoreLogger}'s
     * default logging (the value is {@value}).
     */
    @SuppressWarnings("WeakerAccess")
    public  static final boolean                        EXTENDER_NO_DEFAULT_LOGS = true;

    private static       LoggerExtender                 sLoggerExtender;

    private static       AtomicReference<String>        sTag;
    private static       AtomicReference<String>        sAppId;

    private static       String                         sNewLine;

    private static       AtomicReference<Level>         sLogLevel;
    // should be consistent with javadoc below
    private static       AtomicReference<Level>         sLogLevelDefault;
    private static       AtomicBoolean                  sForceShowStack;
    private static       AtomicBoolean                  sForceShowThread;
    private static       AtomicBoolean                  sForceShowAppId;
    private static       AtomicBoolean                  sNoSilentBackDoor;

    private static       AtomicInteger                  sMaxLogLineLength;
    private static       AtomicBoolean                  sSplitToNewLine;
    private static       List<String>                   sLinesList;
    private static final Object                         sLock;

    /**
     * Allows usage of 3-rd party loggers.
     * <br>Usage example (for {@link <a href="https://github.com/JakeWharton/timber">Timber</a>}):
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * CoreLogger.setLoggerExtender(new CoreLogger.LoggerExtender() {
     *     &#064;Override
     *     public boolean log(Level level, String tag, String msg, Throwable throwable,
     *                        boolean showStack, StackTraceElement stackTraceElement) {
     *         if (level == Level.SILENT) return !CoreLogger.EXTENDER_NO_DEFAULT_LOGS;
     *
     *         // for long messages you can use CoreLogger.split(...)
     *         Timber.tag(tag);
     *         Timber.log(CoreLogger.toLogPriority(level), throwable, msg);
     *
     *         return CoreLogger.EXTENDER_NO_DEFAULT_LOGS;  // switched off default logging
     *     }
     * });
     * </pre>
     *
     * @see #setLoggerExtender
     */
    @SuppressWarnings("unused")
    public interface LoggerExtender {

        /**
         * Intended to log the info provided. The way of logging is up to implementation.
         * When logging should be handled by this {@code LoggerExtender} only, this method must return
         * {@link #EXTENDER_NO_DEFAULT_LOGS}. If this method returns {@code !EXTENDER_NO_DEFAULT_LOGS},
         * both {@code CoreLogger} and {@code LoggerExtender} will handle logging.
         *
         * @param level
         *        The logging priority level
         *
         * @param tag
         *        The tag
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
         * @return  {@link #EXTENDER_NO_DEFAULT_LOGS} to prevent {@code CoreLogger} logging that info
         *          by the Yakhont {@code CoreLogger}, {@code !EXTENDER_NO_DEFAULT_LOGS} otherwise
         */
        boolean log(Level level, String tag, String msg, Throwable throwable, boolean showStack,
                    StackTraceElement stackTraceElement);
    }

    /**
     * Converts CoreLogger's {@link Level} to {@link Log}'s priority.
     *
     * @param level
     *        The level
     *
     * @return  The priority
     *
     * @throws  RuntimeException
     *          If {@link Level} can not be converted to {@link Log}'s priority
     */
    @SuppressWarnings("unused")
    public static int toLogPriority(final Level level) {
        switch (level) {
            case VERBOSE: return Log.VERBOSE;
            case DEBUG  : return Log.DEBUG  ;
            case INFO   : return Log.INFO   ;
            case WARNING: return Log.WARN   ;
            case ERROR  : return Log.ERROR  ;
        }
        throw new RuntimeException("unsupported level: " + level);
    }

    private CoreLogger() {
    }

    static {
        sLock                                                                    = new Object();
        sGestureLibraryLock                                                      = new Object();
        sShakeLock                                                               = new Object();

        init();
    }

    /**
     * Makes CoreLogger cleanup; called from {@link Core#cleanUpFinal()}.
     */
    public static void cleanUpFinal() {
        init();
    }

    // should be called on switching Activities
    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void cleanUp() {
        if (sShakeEventListener != null) sShakeEventListener.unregister();
        sShakeEventListener             = null;
    }

    private static void init() {
        cleanUp();

        sLoggerExtender                 = null;

        sTag                            = new AtomicReference<>();
        sAppId                          = new AtomicReference<>();

        sNewLine                        = Objects.requireNonNull(System.getProperty("line.separator"));

        sLogLevel                       = new AtomicReference<>(Level.ERROR);
        // should be consistent with javadoc below
        sLogLevelDefault                = new AtomicReference<>(Level.INFO );
        sForceShowStack                 = new AtomicBoolean();
        sForceShowThread                = new AtomicBoolean();
        sForceShowAppId                 = new AtomicBoolean();
        sNoSilentBackDoor               = new AtomicBoolean();

        sMaxLogLineLength               = new AtomicInteger(MAX_LOG_LINE_LENGTH);
        sSplitToNewLine                 = new AtomicBoolean(true);

        synchronized (sLock) {
            sLinesList                  = new ArrayList<>();
        }

        sCmd                            = null;
        sHasScreenShot                  = false;
        sHasDb                          = false;
        sMoreFiles                      = null;
        sSubject                        = null;
        sAddresses                      = null;
        sUseShake                       = null;

        synchronized (sGestureLibraryLock) {
            sGestureLibrary             = null;
            sGestureLibraryLoad         = false;
            sGestureLibraryThreshold    = 0;
        }

        synchronized (sShakeLock) {
            sShakeThreshold             = null;
            sShakeDelay                 = null;
        }
    }

    /**
     * Gets the default log level.
     *
     * @return  The default log level
     *
     * @see #setDefaultLevel
     */
    public static Level getDefaultLevel() {
        return sLogLevelDefault.get();
    }

    /**
     * Sets the default log level (the default value is {@link Level#INFO}).
     * It's used for creating new log messages.
     *
     * @param level
     *        The default log level
     *
     * @return  The previous default log level
     */
    @SuppressWarnings("unused")
    public static Level setDefaultLevel(@NonNull final Level level) {
        return sLogLevelDefault.getAndSet(level);
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
        if (sLoggerExtender != null)
            Log.w(getTag("CoreLogger"), "already set LoggerExtender " + sLoggerExtender);
        sLoggerExtender = loggerExtender;
    }

    /**
     * Returns maximum log line length.
     *
     * @return  The maximum log line length
     */
    @SuppressWarnings("WeakerAccess")
    public static int getMaxLogLineLength() {
        return sMaxLogLineLength.get();
    }

    /**
     * Sets maximum log line length.
     *
     * @param value
     *        The value to set

     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static int setMaxLogLineLength(@IntRange(from = 1, to = MAX_LOG_LENGTH) final int value) {
        return value >= 1 && value <= MAX_LOG_LENGTH ?
                sMaxLogLineLength.getAndSet(value): sMaxLogLineLength.get();
    }

    /**
     * Indicates whether the detailed logging was enabled or not.
     *
     * @return  The detailed logging mode state
     */
    public static boolean isFullInfo() {
        return getLogLevel().ordinal() <= getDefaultLevel().ordinal();
    }

    /**
     * Sets the log level (the default value is {@link Level#ERROR}).
     * Log messages with level below this one will not be logged.
     *
     * @param level
     *        The value to set
     *
     * @return  The previous log level
     */
    @SuppressWarnings("unused")
    public static Level setLogLevel(@NonNull final Level level) {
        return sLogLevel.getAndSet(level);
    }

    /**
     * Gets the log level.
     *
     * @return  The current log level
     *
     * @see #setLogLevel
     */
    @SuppressWarnings("unused")
    public static Level getLogLevel() {
        return sLogLevel.get();
    }

    /**
     * Sets whether the stack trace should be logged for all messages.
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
     * Sets whether the thread info should be logged for all messages.
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
     * Sets whether the application ID should be logged for all messages.
     *
     * @param showAppId
     *        The value to set
     *
     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static boolean setShowAppId(final boolean showAppId) {
        return sForceShowAppId.getAndSet(showAppId);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean setNoSilentLogging(final boolean value) {
        return sNoSilentBackDoor.getAndSet(value);
    }

    /**
     * Sets the split mode (the default value is {@code true}).
     *
     * @param value
     *        The split mode: {@code true} to just insert 'new line' character in string,
     *        {@code false} to really split it
     *
     * @return  The previous value
     */
    @SuppressWarnings("unused")
    public static boolean setSplitToNewLine(final boolean value) {
        return sSplitToNewLine.getAndSet(value);
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
     * Gets the user defined Android {@link Log}'s tag (if any).
     *
     * @return  The tag's value (or null)
     */
    @SuppressWarnings("WeakerAccess")
    public static String getTag() {
        return sTag.get();
    }

    @NonNull
    private static String getTag(final String className) {
        final boolean lengthLimited = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N;

        String tag = getTag();
        if (tag == null)
            tag = className == null ? Utils.getTag((String) null): className;

        final int idx = tag.indexOf('$');
        tag = idx < 0 ? tag: tag.substring(0, idx);

        return !lengthLimited || tag.length() <= MAX_TAG_LENGTH ? tag: tag.substring(0, MAX_TAG_LENGTH);
    }

    /**
     * Performs logging with default ({@link Level#DEBUG DEBUG}) level.
     *
     * @param str
     *        The message to log
     */
    public static void log(@NonNull final String str) {
        log(getDefaultLevel(), str);
    }

    /**
     * Performs logging with default ({@link Level#ERROR ERROR}) level.
     *
     * @param throwable
     *        The Throwable to log
     */
    public static void log(@NonNull final Throwable throwable) {
        log(Level.ERROR, throwable);
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
    public static void log(@NonNull final String str, @NonNull final Throwable throwable) {
        log(Level.ERROR, str, throwable);
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
     * Performs logging with ({@link Level#WARNING WARNING}) level.
     *
     * @param str
     *        The message to log
     *
     * @param throwable
     *        The Throwable to log
     */
    @SuppressWarnings("unused")
    public static void logWarning(@NonNull final String str, @NonNull final Throwable throwable) {
        log(Level.WARNING, str, throwable);
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
    @SuppressWarnings("unused")
    public static void logError(@NonNull final String str, @NonNull final Throwable throwable) {
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
     * @param throwable
     *        The Throwable to log
     */
    public static void log(@NonNull final Level level, @NonNull final Throwable throwable) {
        log(level, DEFAULT_MESSAGE, throwable);
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
        log(level, str, throwable, null);
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

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean isNotLog() {
        return isNotLog(getDefaultLevel());
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static boolean isNotLog(@NonNull final Level level) {
        // allows to check whether the given level will be logged, or not
        return !isLogHelper(level) && sLoggerExtender == null;
    }

    private static boolean isLogHelper(@NonNull final Level level) {
        return level.ordinal() >= sLogLevel.get().ordinal();
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
     *        The Throwable to log (if any)
     *
     * @param showStack
     *        Indicates whether the stack trace should be logged or not (can be null)
     */
    @SuppressWarnings("WeakerAccess")
    public static void log(@NonNull final Level level, @NonNull final String str,
                           final Throwable throwable, Boolean showStack) {
        if (isNotLog(level)) return;
        final boolean isLog = isLogHelper(level);

        if (showStack == null) showStack = sForceShowStack.get() || throwable != null ||
                (level.ordinal() >= LEVEL_STACK.ordinal() && isFullInfo());

        final Throwable trace = isLog && (showStack || isMethodInfo()) ?
                throwable != null ? throwable: new Throwable(STACK_TRACE_TITLE): null;
        final StackTraceElement traceElement = trace == null ? null: getStackTraceElement(trace);

        final String className = traceElement == null ? null: stripPackageName(traceElement.getClassName());
        final String tag       = getTag(className);

        if (sLoggerExtender != null && sLoggerExtender.log(level, tag, str, throwable, showStack,
                traceElement) == EXTENDER_NO_DEFAULT_LOGS) return;

        if (isLog) log(level, tag, str, throwable != null ? throwable: showStack ? trace: null,
                traceElement, className);
    }

    private static StackTraceElement getStackTraceElement(@NonNull final Throwable throwable) {
        final StackTraceElement[] stackTraceElements = throwable.getStackTrace();

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < stackTraceElements.length; i++)
            if (!stackTraceElements[i].getClassName().equals(CLASS_NAME))
                return stackTraceElements[i];

        // should never happen
        Log.e(getTag("CoreLogger"), "can not find StackTraceElement");
        return null;
    }

    private static String stripPackageName(final String name) {
        if (name == null) return null;
        final int idx = name.lastIndexOf('.');
        return idx < 0 ? name: name.substring(idx + 1);
    }

    private static boolean isMethodInfo() {
        return isFullInfo();
    }

    /**
     * Splits message to log into lines (the maximum line length is defined by {@link #getMaxLogLineLength()}).
     *
     * @param list
     *        The list to collect parts (if null, the new one will be created)
     *
     * @param text
     *        The text to split
     *
     * @return  The list of parts
     *
     * @see     #split(List, String, int, boolean)
     */
    @SuppressWarnings({"UnusedReturnValue", "unused", "WeakerAccess"})
    public static List<String> split(final List<String> list, final String text) {
        return split(list, text, getMaxLogLineLength(), true);
    }

    /**
     * Splits message to log into parts. Keeps in mind spaces, tabs and line separators.
     *
     * @param list
     *        The list to collect parts (if null, the new one will be created)
     *
     * @param text
     *        The text to split
     *
     * @param maxLength
     *        The maximum length of part
     *
     * @param clearList
     *        if {@code true} the list will be cleared before adding parts, otherwise not
     *
     * @return  The list of parts
     */
    @SuppressWarnings("WeakerAccess")
    public static List<String> split(List<String> list, String text, final int maxLength,
                                     final boolean clearList) {
        if (text == null) return list;

        if (list == null)   list = new ArrayList<>();
        else if (clearList) list.clear();

        for (;;) {
            String       tmp = text;
            StringBuilder sb = null;
            for (;;) {
                final int idx = tmp.indexOf(sNewLine);
                if (idx < 0 || idx > maxLength) break;

                if (sb == null) sb = new StringBuilder();
                sb.append(tmp.substring(0, idx + 1));
                tmp = tmp.substring(idx + 1);
            }
            final String line = sb != null ? sb.append(getSubStr(tmp)).toString(): getSubStr(tmp);

            list.add(line);

            if (text.length() == line.length()) break;
            text = text.substring(line.length());
        }

        return list;
    }

    @SuppressWarnings("SameParameterValue")
    private static void reSplit(final List<String> list) {
        if (list == null || list.isEmpty()) return;

        final StringBuilder sb = new StringBuilder(list.get(0));
        for (int i = 1 ; i < list.size(); i++)
            sb.append(sNewLine).append(list.get(i));

        split(list, sb.toString(), MAX_LOG_LENGTH, true);
    }

    private static String getSubStr(@NonNull final String strOriginal) {
        final int maxLength = getMaxLogLineLength();
        if (strOriginal.length() <= maxLength) return strOriginal;

        final String str = strOriginal.substring(0, maxLength);
        final char endChar = strOriginal.charAt(maxLength);
        if (endChar == ' ' || endChar == '\t') return str;

        final int idx = Math.max(str.lastIndexOf(' '), str.lastIndexOf('\t'));
        return idx < 0 ? str: str.substring(0, idx + 1);
    }

    @NonNull
    private static String addMethodInfo(@NonNull final Level level, @NonNull String str,
                                        final String className, final String methodName,
                                        final Integer lineNumber) {
        if (isMethodInfo()) {
            final String methodInfo = String.format(Utils.getLocale(), FORMAT_INFO,
                    className  == null ? "<unknown class>" : className,
                    methodName == null ? "<unknown method>": methodName,
                    lineNumber == null ? "unknown"         : lineNumber.toString());
            str = String.format(FORMAT, methodInfo, str);
        }
        if (sForceShowThread.get() || level.ordinal() >= LEVEL_THREAD.ordinal())
            str = String.format(FORMAT_THREAD, Thread.currentThread().getName(), str);

        if (sForceShowAppId.get() || level.ordinal() >= LEVEL_APP_ID.ordinal()) {
            final String appIdGet = sAppId.get(), appId = appIdGet == null
                    ? (String) Utils.getBuildConfigField("APPLICATION_ID"): appIdGet;
            if (!TextUtils.isEmpty(appId)) {
                if (appIdGet == null) sAppId.set(appId);
                str = String.format(FORMAT_APP_ID, appId, str);
            }
        }
        return str;
    }

    private static void log(@NonNull final Level level, @NonNull final String tag,
                            @NonNull final String msg, final Throwable throwable,
                            final StackTraceElement traceElement, final String className) {
        final int    maxLength = getMaxLogLineLength();
        final String text      = addMethodInfo(level, msg, className,
                traceElement == null ? null: traceElement.getMethodName(),
                traceElement == null ? null: traceElement.getLineNumber());
        if (text.length() <= maxLength) {
            log(tag, text, throwable, level);
            return;
        }

        synchronized (sLock) {
            split(sLinesList, text, maxLength, true);
            if (sSplitToNewLine.get() && maxLength < MAX_LOG_LENGTH) reSplit(sLinesList);

            for (int i = 0 ; i < sLinesList.size(); i++)
                log(tag, sLinesList.get(i), throwable != null && i == sLinesList.size() - 1 ?
                        throwable: null, level);
        }
    }

    private static void log(@NonNull final String tag, @NonNull final String msg,
                            final Throwable throwable, @NonNull final Level level) {
        switch (level) {
            case VERBOSE:
                if (throwable != null)
                    Log.v(tag, msg, throwable);
                else
                    Log.v(tag, msg);
                break;

            case DEBUG:
                if (throwable != null)
                    Log.d(tag, msg, throwable);
                else
                    Log.d(tag, msg);
                break;

            case INFO:
                if (throwable != null)
                    Log.i(tag, msg, throwable);
                else
                    Log.i(tag, msg);
                break;

            case WARNING:
                if (throwable != null)
                    Log.w(tag, msg, throwable);
                else
                    Log.w(tag, msg);
                break;

            default:            // should never happen
                Log.e(tag, "unknown log level " + level.name());

            case SILENT:        // backdoor for logging - even in silent mode
                if (sNoSilentBackDoor.get()) break;

            case ERROR:
                if (throwable != null)
                    Log.e(tag, msg, throwable);
                else
                    Log.e(tag, msg);
                break;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Converts integer to hex string (e.g. "0xABCD").
     *
     * @param data
     *        The integer to convert
     *
     * @return  The integer hex representation
     */
    @SuppressWarnings("WeakerAccess")
    public static String toHex(final int data) {
        return "0x" + Integer.toHexString(data).toUpperCase(Utils.getLocale());
    }

    /**
     * Converts byte array to string.
     *
     * @param data
     *        The byte array to convert
     *
     * @return  The byte array readable representation
     */
    @SuppressWarnings("unused")
    public static String toHex(final byte[] data) {
        return toHex(data, 0, data == null ? 0: data.length, true);
    }

    /**
     * Converts byte array to string.
     *
     * @param data
     *        The byte array to convert
     *
     * @param offset
     *        The offset in bytes array
     *
     * @param length
     *        The bytes quantity to convert
     *
     * @param bytesOnly
     *        {@code true} to return bytes only (without string representation), {@code false} otherwise
     *
     * @return  The byte array readable representation
     */
    @SuppressWarnings("unused")
    public static String toHex(final byte[] data, int offset, final int length, final boolean bytesOnly) {
        return toHex(data, offset, length, bytesOnly, null, null);
    }

    /**
     * Converts byte array to string.
     *
     * @param data
     *        The byte array to convert
     *
     * @param offset
     *        The offset in bytes array
     *
     * @param length
     *        The bytes quantity to convert
     *
     * @param bytesOnly
     *        {@code true} to return bytes only (without string representation), {@code false} otherwise
     *
     * @param locale
     *        The locale (or null for default one)
     *
     * @param charset
     *        The charset (or null for default one)
     *
     * @return  The byte array readable representation
     */
    @SuppressWarnings("WeakerAccess")
    public static String toHex(final byte[] data, int offset, int length, final boolean bytesOnly,
                               final Locale locale, final Charset charset) {
        return toHex(data, offset, length, bytesOnly, locale, charset, false, true);
    }

    /**
     * Converts byte array to string (16 bytes per line).
     *
     * @param data
     *        The byte array to convert
     *
     * @param offset
     *        The offset in bytes array
     *
     * @param length
     *        The bytes quantity to convert
     *
     * @param locale
     *        The locale (or null for default one)
     *
     * @param charset
     *        The charset (or null for default one)
     *
     * @return  The byte array readable representation
     *
     * @see BaseResponse#setBytesQtyForArray
     */
    public static String toHex(final byte[] data, int offset, int length, final Locale locale,
                               final Charset charset) {
        final StringBuilder builder = new StringBuilder(sNewLine);
        for (;; length -= BYTES_PER_LINE, offset += BYTES_PER_LINE) {
            final boolean lastLine = length <= BYTES_PER_LINE || data.length - offset <= BYTES_PER_LINE;
            builder.append(toHex(data, offset, BYTES_PER_LINE, false, locale, charset,
                    true, lastLine)).append(sNewLine);
            if (lastLine) break;
        }
        return builder.substring(0, builder.length() - 1);
    }

    private static final int                            BYTES_PER_LINE           = 16;

    private static String toHex(final byte[] data, final int offset, int length, final boolean bytesOnly,
                                Locale locale, final Charset charset,
                                final boolean insertAfter4, final boolean lastLine) {
        if (data        == null) return null;
        if (data.length ==    0) return   "";

        if (insertAfter4 && length != BYTES_PER_LINE) {
            logError(String.format(locale, "wrong length %d for byte array, expected " +
                    BYTES_PER_LINE, length));
            return null;
        }
        if (locale == null) locale = Utils.getLocale();

        if (offset >= data.length) {
            logError(String.format(locale, "wrong offset %d for byte array (length %d)",
                    offset, data.length));
            return null;
        }
        if (offset + length > data.length) length = data.length - offset;

        try {
            final StringBuilder builder = new StringBuilder(hexFormat(data[offset], locale));
            for (int i = offset + 1; i < offset + length; i++) {
                builder.append(' ').append(hexFormat(data[i], locale));
                if (insertAfter4 && (i == offset +  3 && length >  4 || 
                                     i == offset +  7 && length >  8 ||
                                     i == offset + 11 && length > 12)) builder.append(' ');
            }
            if (offset + length < data.length && lastLine) builder.append("  ...  ");

            if (!bytesOnly) {
                if (insertAfter4)
                    for (int i = builder.length(); i < 62; i++) builder.append(' ');

                final byte[] buffer = new byte[length];
                for (int i = 0; i < length; i++) {
                    final int check = data[offset + i] & 0xFF;
                    buffer[i] = check < 32 || check == 127 || check == 255 ? 46: data[offset + i];
                }
                builder.append(charset == null ? new String(buffer): new String(buffer, charset));
            }
            return builder.toString();
        }
        catch (Exception exception) {
            log(exception);
            return null;
        }
    }

    private static String hexFormat(final byte data, @NonNull final Locale locale) {
        return String.format(locale, "%02X", data);
    }

    /**
     * Creates screenshot for the given Activity.
     *
     * @param activity
     *        The Activity
     *
     * @param dir
     *        The directory to save screenshot (or null for default one)
     *
     * @param suffix
     *        The screenshot filename's suffix
     *
     * @param format
     *        The screenshot format (or null for default one)
     *
     * @param quality
     *        The screenshot quality (0 - 100), null for default value (100)
     *
     * @param oldScreenshot
     *        {@code true} to force using {@link View#getDrawingCache()} for making screenshot, null
     *        for default value (by default Yakhont uses {@link PixelCopy#request(Window, Bitmap,
     *        OnPixelCopyFinishedListener, Handler)} - if available)
     *
     * @return  The screenshot's file
     */
    @SuppressWarnings("WeakerAccess")
    public static File getScreenshot(Activity activity, File dir, final String suffix,
                                     final Bitmap.CompressFormat format, final Integer quality,
                                     Boolean oldScreenshot) {
        if (activity      == null) activity = Utils.getCurrentActivity();
        if (activity      == null) logError("getScreenshot(): activity == null");

        if (suffix        == null) logError("suffix == null");
        if (oldScreenshot == null) oldScreenshot = false;

        if (dir == null && activity != null) dir = Utils.getTmpDir(activity);
        if (dir == null) logError("dir == null");
        if (dir == null || activity == null || suffix == null) return null;

        try {
            if (!Utils.isCurrentThreadMain())
                return Sender.getScreenshot(activity, dir, suffix, null, format, quality,
                        null, null, oldScreenshot);

            final Activity activityFinal        = activity;
            final File     dirFinal             = dir;
            final boolean  oldScreenshotFinal   = oldScreenshot;

            final File[]         screenshot     = new File[1];
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            Utils.runInBackground(new Runnable() {
                @Override
                public void run() {
                    screenshot[0] = Sender.getScreenshot(activityFinal, dirFinal, suffix, null,
                            format, quality, null, null, oldScreenshotFinal);
                    countDownLatch.countDown();
                }

                @NonNull
                @Override
                public String toString() {
                    return "getScreenshot";
                }
            });
            Utils.await(countDownLatch);

            return screenshot[0];
        }
        catch (Exception exception) {
            Sender.handleError("failed creating screenshot", exception, null);
            return null;
        }
    }

    /**
     * Creates screenshot for the current Activity.
     *
     * @param suffix
     *        The screenshot filename's suffix
     *
     * @return  The screenshot's file
     */
    @SuppressWarnings("unused")
    public static File getScreenshot(final String suffix) {
        return getScreenshot(null, null, suffix, Sender.SCREENSHOT_FORMAT, null, false);
    }

    /**
     * Returns resource name for the given ID.
     *
     * @param id
     *        The resource ID
     *
     * @return  The resource name
     */
    @SuppressWarnings("WeakerAccess")
    public static String getResourceName(@AnyRes final int id) {
        if (id == Core.NOT_VALID_RES_ID) {
            logWarning("getResourceName(): not valid resource ID " + id);
            return "NOT_VALID_RES_ID";
        }
        try {
            return Objects.requireNonNull(Utils.getApplication()).getResources().getResourceName(id);
        }
        catch (Exception exception) {
            log(exception);
            return null;
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static String getResourceDescription(@AnyRes final int id) {
        final String name = getResourceName(id);
        return String.format("%s (%s)", toHex(id), name != null ? name: "unknown");
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static String getViewDescription(final View view) {
        if (view == null) return "null";
        final int id = view.getId();
        return id == View.NO_ID ? "view without id " + view: getResourceDescription(id);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @NonNull
    public static String getDescription(final Object object) {
        return object == null ? "null": getDescription(object instanceof Class ?
                (Class<?>) object: object.getClass()) + ", toString(): " + object;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @NonNull
    public static String getDescription(Class<?> cls) {
        if (cls == null) return "null";
        final StringBuilder data = new StringBuilder();

        //noinspection ConditionalBreakInInfiniteLoop
        for (;;) {
            String name = cls.getSimpleName();
            if (TextUtils.isEmpty(name)) {          // anonymous
                name = cls.getName();
                if (Object.class.equals(cls.getSuperclass())) {
                    final Class<?>[] interfaces = cls.getInterfaces();

                    if (interfaces.length > 0) {
                        final StringBuilder tmp = new StringBuilder().append(name).append(" (");
                        for (final Class<?> clsInterface: interfaces)
                            tmp.append(clsInterface.getSimpleName()).append(", ");
                        name = tmp.delete(tmp.length() - 2, tmp.length()).append(")").toString();
                    }
                }
            }
            data.append("<-").append(name);
            cls = cls.getSuperclass();
            if (cls  == null || Object.class.equals(cls)) break;
        }
        return data.toString().substring(2);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // should be consistent with javadoc below
    private static final String                         LOGCAT_CMD               = "logcat -d";
    private static final int                            LOGCAT_BUFFER_SIZE       =  1024;
    private static final String                         LOGCAT_DEFAULT_SUBJECT   = "logcat";

    private interface LogHandler {
        @SuppressWarnings("RedundantThrows")
        void handle(String line) throws IOException;
    }

    private static void getLog(String cmd, @NonNull final LogHandler handler) {
        Process process = null;
        try {
            if (cmd == null) cmd = LOGCAT_CMD;
            log("about to execute " + cmd);
            process = Runtime.getRuntime().exec(Utils.removeExtraSpaces(cmd).split(" "));
            final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream()), LOGCAT_BUFFER_SIZE);

            while ((cmd = reader.readLine()) != null)
                handler.handle(cmd);
        }
        catch (/*IO*/Exception exception) {
            log("failed running logcat", exception);
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
     *        <code>logcat</code> command line, or null for default one ("logcat -d")
     *
     * @return  The list with log records
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    @NonNull
    public static List<String> getLogCat(final List<String> list, final boolean clearList, final String cmd) {
        final List<String> listActual = (list == null) ? new ArrayList<>(): list;
        if (clearList) listActual.clear();

        //noinspection Anonymous2MethodRef,Convert2Lambda
        getLog(cmd, new LogHandler() {
            @Override
            public void handle(String line) {
                listActual.add(line);
            }
        });
        return listActual;
    }

    /**
     * Sends e-mail with log records collected by the {@link #getLogCat getLogCat()}.
     *
     * @param activity
     *        The Activity
     *
     * @param addresses
     *        The e-mail addresses
     */
    @SuppressWarnings("unused")
    public static void sendLogCat(@NonNull final Activity activity, @NonNull final String... addresses) {
        sendLogCat(activity, null, true, LOGCAT_DEFAULT_SUBJECT, addresses);
    }

    /**
     * Sends e-mail with log records collected by the {@link #getLogCat}.
     *
     * @param activity
     *        The Activity
     *
     * @param cmd
     *        <code>logcat</code> command line, or null for the default one ("logcat -d")
     *
     * @param clearList
     *        Indicates whether the list should be cleared before adding new records
     *
     * @param subject
     *        The e-mail subject
     *
     * @param addresses
     *        The e-mail addresses
     */
    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public static void sendLogCat(@NonNull final Activity activity, final String cmd,
                                  final boolean clearList, @NonNull final String subject,
                                  @NonNull final String... addresses) {
        Utils.sendEmail(activity, subject, TextUtils.join(sNewLine,
                getLogCat(null, clearList, cmd)), null, addresses);
    }

    /**
     * Sends email with log records and some additional info (if any).
     * The ANR traces are always added (if available).
     *
     * @param context
     *        The Context
     *
     * @param cmd
     *        The logcat command to execute (or null to execute the default one)
     *
     * @param hasScreenshot
     *        {@code true} to include screenshot
     *
     * @param hasDb
     *        {@code true} to include cache database snapshot
     *
     * @param moreFiles
     *        Additional files to include (or null)
     *
     * @param subject
     *        The email subject (or null)
     *
     * @param addresses
     *        The list of email addresses to send data
     */
    @SuppressWarnings("WeakerAccess")
    public static void sendData(final Context context, final String cmd, final boolean hasScreenshot,
                                final boolean hasDb, final String[] moreFiles,
                                final String subject, final String... addresses) {
        Sender.sendEmail(context, cmd, hasScreenshot, hasDb, moreFiles, subject, addresses);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Registers email addresses to which send email with log records
     * and some additional info (if any). The ANR traces are always added (if available).
     *
     * @param context
     *        The Context
     *
     * @param addresses
     *        The email addresses to send data
     */
    @SuppressWarnings("unused")
    public static void registerDataSender(final Context context, final String... addresses) {
        registerDataSender(context, null, null, true, true,
                null, null, addresses);
    }

    /**
     * Registers email addresses to which send debug email with log records and some additional info (if any).
     * The ANR traces are always added (if available).
     *
     * @param context
     *        The Context
     *
     * @param useShake
     *        {@code true} to trigger on shaking device, {@code false} to trigger on gestures,
     *        null to trigger on both
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
     *
     * @param subject
     *        The email subject (or null)
     *
     * @param addresses
     *        The list of email addresses to send data
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    public static void registerDataSender(final Context   context,
                                          final Boolean   useShake,
                                          final String    cmd,
                                          final boolean   hasScreenShot,
                                          final boolean   hasDb,
                                          final String[]  moreFiles,
                                          final String    subject,
                                          final String... addresses) {
        sUseShake       = useShake;
        sCmd            = cmd;
        sHasScreenShot  = hasScreenShot;
        sHasDb          = hasDb;
        sMoreFiles      = moreFiles;
        sSubject        = subject;
        sAddresses      = addresses;

        if (context == null)
            logError("registerDataSender: context == null");
        else if ((sUseShake == null || sUseShake) && addresses != null && addresses.length > 0) {
            log("about to create ShakeEventListener");

            final ShakeEventListener shakeEventListener = new ShakeEventListener(createDataSenderHandler(context));
            synchronized (sShakeLock) {
                shakeEventListener.setThreshold(sShakeThreshold).setDelay(sShakeDelay);
            }
            if (sShakeEventListener != null) sShakeEventListener.unregister();

            sShakeEventListener = shakeEventListener;
            shakeEventListener.register();
        }
    }

    private static       String                         sCmd;
    private static       boolean                        sHasScreenShot;
    private static       boolean                        sHasDb;
    private static       String[]                       sMoreFiles;
    private static       String                         sSubject;
    private static       String[]                       sAddresses;
    private static       Boolean                        sUseShake;
    private static       ShakeEventListener             sShakeEventListener;

    private static File getTmpDir(final Context context) {
        return Utils.getTmpDir(context);
    }

    private static Runnable createDataSenderHandler(final Context context) {
        final Context  contextToUse = context.getApplicationContext();
        final Activity activity     = context instanceof Activity ? (Activity) context: Utils.getCurrentActivity();

        return new Runnable() {
            @Override
            public void run() {
                final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        sendData(contextToUse, sCmd, sHasScreenShot, sHasDb, sMoreFiles, sSubject, sAddresses);
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "sendData";
                    }
                };
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || VideoRecorder.isRunning()) {
                    runnable.run();
                    return;
                }

                final File tmpDir = getTmpDir(contextToUse);
                if (tmpDir == null) return;             // should never happen

                VideoRecorder.setHandler(runnable);

                final Runnable startVideo = new Runnable() {
                    @SuppressLint("NewApi")
                    @Override
                    public void run() {
                        VideoRecorder.start(activity, new File(tmpDir, String.format("%s%s.%s",
                                VIDEO_PREFIX, Utils.getTmpFileSuffix(),
                                VideoRecorder.sFileNameExtension)).getAbsolutePath());
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "VideoRecorder.start()";
                    }
                };

                if (UiModule.hasSnackbars()) {
                    Utils.safeRun(startVideo);
                    return;
                }

                //noinspection Convert2Lambda
                new Utils.SnackbarBuilder()
                        .setTextId(akha.yakhont.R.string.yakhont_record_video)
                        .setDuration(Snackbar.LENGTH_INDEFINITE)
                        .setRequestCode(Utils.getRequestCode(RequestCodes.LOGGER_VIDEO))

                        .setViewHandlersChain(true)
                        .setViewHandler(Utils.getDefaultSnackbarViewModifier())

                        .setActionTextId(akha.yakhont.R.string.yakhont_record_video_ok)
                        .setActionColor(Utils.getDefaultSnackbarActionColor())
                        .setAction(new View.OnClickListener() {
                            @Override
                            public void onClick(final View view) {
                                Utils.safeRun(startVideo);
                            }
                        }).show();
            }

            @NonNull
            @Override
            public String toString() {
                return "DataSenderHandler";
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Very simple gesture handler; allows to set the gestures recognition threshold and doesn't consume
     * unrecognized gestures (i.e. could be used, say, with {@link RecyclerView}).
     * <br>If you need more than one gesture libraries (2, 5, 100, ...) - just create 2-5-100
     * GestureHandlers and work with them in, say, {@link Activity#dispatchTouchEvent}
     * (could be useful if you have many gestures and want special threshold for each of them).
     * <br>Usage example:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import android.gesture.GestureLibrary;
     * import android.view.MotionEvent;
     *
     * public class YourActivity extends Activity {
     *
     *     private GestureHandler yourGestureHandler;
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *         super.onCreate(savedInstanceState);
     *
     *         // load gestures library from resources or file
     *         GestureLibrary yourGestureLibrary = GestureHandler.loadLibrary(...);
     *
     *         double threshold = 2.5;   // just for example - you can use whatever you want
     *
     *         yourGestureHandler = new GestureHandler(new Runnable() {
     *             &#064;Override
     *             public void run() {
     *                 // your code here (to handle gestures)
     *             }
     *         }, threshold, yourGestureLibrary);
     *     }
     *
     *     &#064;Override
     *     public boolean dispatchTouchEvent(MotionEvent event) {
     *         yourGestureHandler.handle(event);
     *         return super.dispatchTouchEvent(event);
     *     }
     * }
     * </pre>
     *
     * Also contains some helper methods for gestures libraries loading.
     *
     * <p>Note: implementation doesn't use {@link GestureOverlayView}.
     */
    @SuppressWarnings("WeakerAccess")
    public static class GestureHandler {

        private static final String                     DEFAULT_NAME             = "";

        private        final Map<String, Runnable>      mHandlers;
        private        final GestureLibrary             mLibrary;
        private        final double                     mThreshold;

        private static       ArrayList<GesturePoint>    sStrokeBuffer;

        static {
            init();
        }

        /**
         * Cleanups static fields in GestureHandler; called from {@link Core#cleanUpFinal()}.
         */
        public static void cleanUpFinal() {
            GestureHandlerZ.init();
            init();
        }

        private static void init() {
            sStrokeBuffer = new ArrayList<>();
        }

        /**
         * Initialises a newly created {@code GestureHandler} object.
         *
         * @param handlers
         *        The recognized gestures handlers (gesture's name + handler)
         *
         * @param threshold
         *        The gestures recognition threshold
         *
         * @param library
         *        The {@code GestureLibrary}
         */
        @SuppressWarnings("unused")
        public GestureHandler(final Map<String, Runnable> handlers, final double threshold,
                              final GestureLibrary library) {
            this(handlers, threshold, library, true);
        }

        /**
         * Initialises a newly created {@code GestureHandler} object.
         *
         * @param handlers
         *        The recognized gestures handlers (gesture's name + handler)
         *
         * @param threshold
         *        The gestures recognition threshold
         *
         * @param library
         *        The {@code GestureLibrary}
         *
         * @param load
         *        {@code true} to call {@link GestureLibrary#load}, {@code false} otherwise
         */
        public GestureHandler(final Map<String, Runnable> handlers, final double  threshold,
                              final GestureLibrary        library , final boolean load) {
            mLibrary   = load ? loadLibrary(library): library;
            mHandlers  = handlers;
            mThreshold = threshold;

            if (mLibrary  == null)                          logError("gesture library is null");
            if (mHandlers == null || mHandlers.size() == 0) logError("no handlers for gesture library");
        }

        /**
         * Initialises a newly created {@code GestureHandler} object.
         *
         * @param handler
         *        The recognized gesture(s) handler
         *
         * @param threshold
         *        The gestures recognition threshold
         *
         * @param library
         *        The {@code GestureLibrary}
         */
        @SuppressWarnings("unused")
        public GestureHandler(final Runnable handler, final double threshold, final GestureLibrary library) {
            this(getHandler(handler), threshold, library);
        }

        /**
         * Converts given handler to format which wil work with any gestures
         * (normally handler linked to gesture's name).
         *
         * @param handler
         *        The recognized gesture(s) handler
         *
         * @return  The converted handler
         */
        public static Map<String, Runnable> getHandler(final Runnable handler) {
            final Map<String, Runnable> handlers;
            if (handler != null) {
                handlers = Utils.newMap();
                handlers.put(DEFAULT_NAME, handler);
            }
            else {
                handlers = null;
                logError("null Runnable for gesture handling");
            }
            return handlers;
        }

        @SuppressWarnings("SameParameterValue")
        private GestureHandler(@NonNull final Runnable handler, final double threshold, @NonNull final Data data) {
            this(getHandler(handler), threshold, data.mLibrary, data.mLoad);
        }

        /**
         * Returns the {@code GestureLibrary} in use.
         *
         * @return  The {@code GestureLibrary}
         */
        @SuppressWarnings("unused")
        public GestureLibrary getLibrary() {
            return mLibrary;
        }

        /**
         * Returns the gestures recognition threshold in use.
         *
         * @return  The gestures recognition threshold
         */
        @SuppressWarnings("unused")
        public double getThreshold() {
            return mThreshold;
        }

        /**
         * Returns the recognized gestures handlers.
         *
         * @return  The recognized gestures handlers
         */
        @SuppressWarnings("unused")
        public Map<String, Runnable> getHandlers() {
            return mHandlers;
        }

        private static GestureLibrary loadLibrary(final GestureLibrary library) {
            if (library == null) {
                logError("load: gesture library is null");
                return null;
            }
            try {
                final boolean result = library.load();
                if (!result) logError("can't load gesture library: " + library);
                return result ? library: null;
            }
            catch (Exception exception) {
                log("failed to load gesture library: " + library, exception);
                return null;
            }
        }

        /**
         * Handles {@code MotionEvent} to recognize gestures.
         *
         * @param event
         *        The event to handle
         *
         * @return  {@code true} if this event was consumed, {@code false} otherwise
         */
        @SuppressWarnings({"UnusedReturnValue", "ConstantConditions"})
        public boolean handle(@NonNull final MotionEvent event) {
            boolean result = false;
            if (mLibrary == null || mHandlers == null || mHandlers.size() == 0) return result;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:           // fall through
                    sStrokeBuffer.clear();

                case MotionEvent.ACTION_MOVE:
                    sStrokeBuffer.add(new GesturePoint(event.getX(), event.getY(), event.getEventTime()));
                    break;

                case MotionEvent.ACTION_UP:
                    try {
                        final Gesture gesture = new Gesture();
                        gesture.addStroke(new GestureStroke(sStrokeBuffer));
                        final ArrayList<Prediction> predictions = mLibrary.recognize(gesture);

                        if (predictions.size() > 0) {
                            Double score = null;
                            String name  = null;
                            for (final Prediction prediction: predictions)
                                if (prediction.score >= mThreshold &&
                                        (score == null || prediction.score > score)) {
                                    score = prediction.score;
                                    name  = prediction.name ;
                                }

                            if (score == null)
                                log("gesture(s) prediction score(s) < threshold " + mThreshold);
                            else {
                                // event consumed (even if we'll have gesture handling errors)
                                result = true;

                                Runnable handler = null;
                                if (mHandlers.size() == 1) {
                                    String handlerName = mHandlers.keySet().iterator().next();
                                    if (handlerName.equals(DEFAULT_NAME) || handlerName.equals(name))
                                        handler = mHandlers.get(handlerName);
                                    else
                                        logError("unexpected gesture handler's name " +
                                                handlerName + ", should be " + name);
                                }
                                else if (mHandlers.containsKey(name))
                                    handler = mHandlers.get(name);
                                else
                                    logError("there's no gesture handler with name " + name);

                                if (handler == null)
                                    logError("null handler for gesture with name " + name);
                                else if (!Utils.safeRun(handler))
                                    logError("can't handle gesture, handler: " + handler);
                            }
                        }
                        else
                            logError("gesture predictions.size() == 0");
                    }
                    catch (Exception exception) {
                        log(exception);
                    }

                    sStrokeBuffer.clear();
                    break;
            }
            return result;
        }

        /**
         * Loads gestures library; please refer to {@link GestureLibraries#fromRawResource} for more info.
         *
         * @param id
         *        The library raw resource id
         *
         * @return  The gestures library (or null)
         */
        public static GestureLibrary loadLibrary(@RawRes final int id) {
            return loadLibrary(id, null, null);
        }

        /**
         * Loads gestures library; please refer to {@link GestureLibraries#fromFile(File)} for more info.
         *
         * @param file
         *        The library file
         *
         * @return  The gestures library (or null)
         */
        @SuppressWarnings("unused")
        public static GestureLibrary loadLibrary(final File file) {
            if (file != null) return loadLibrary(Core.NOT_VALID_RES_ID, file, null);

            logError("load GestureLibrary: file == null");
            return null;
        }

        /**
         * Loads gestures library; please refer to {@link GestureLibraries#fromFile(String)} for more info.
         *
         * @param file
         *        The library file name
         *
         * @return  The gestures library (or null)
         */
        @SuppressWarnings("unused")
        public static GestureLibrary loadLibrary(final String file) {
            if (file != null) return loadLibrary(Core.NOT_VALID_RES_ID, null, file);

            logError("load GestureLibrary: file path == null");
            return null;
        }

        private static GestureLibrary loadLibrary(@RawRes final int id, final File file, final String path) {
            try {
                if (file != null) return GestureLibraries.fromFile(file);
                if (path != null) return GestureLibraries.fromFile(path);
                return GestureLibraries.fromRawResource(Utils.getApplication(), id);
            }
            catch (Exception exception) {
                log("load GestureLibrary failed", exception);
                return null;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        protected static class Data {               // looks like a compiler issue

            private    final GestureLibrary             mLibrary;
            private    final boolean                    mLoad;

            private Data(@NonNull final GestureLibrary library, final boolean load) {
                mLibrary = library;
                mLoad    = load;
            }
        }
    }

    private static class GestureHandlerZ extends GestureHandler {

        private static final double                     THRESHOLD                = 2.5;

        private static       GestureLibrary             sLibrary;
        private static final Object                     sLock;

        static {
            sLock                                                                = new Object();
            init();
        }

        private static void init() {
            synchronized (sLock) {
                sLibrary = null;
            }
        }

        private GestureHandlerZ(@NonNull final Runnable runnable) {
            super(runnable, THRESHOLD, getLibraryHelper());
        }

        private static Data getLibraryHelper() {
            synchronized (sLock) {
                boolean load = false;
                if (sLibrary == null) {
                    sLibrary = loadLibrary(akha.yakhont.R.raw.yakhont_gestures);
                    load = sLibrary != null;
                }
                if (sLibrary == null) logError("can't load Yakhont gestures library");
                return new Data(sLibrary, load);
            }
        }
    }

    /**
     * Sets custom gestures library to trigger audio / video recording.
     *
     * @param library
     *        The gestures library
     *
     * @param load
     *        {@code true} to call {@link GestureLibrary#load}, {@code false} otherwise
     *
     * @param threshold
     *        The gestures recognition threshold
     */
    @SuppressWarnings("unused")
    public static void setGestureLibrary(final GestureLibrary library, final boolean load,
                                         final double threshold) {
        synchronized (sGestureLibraryLock) {
            sGestureLibrary             = library;
            sGestureLibraryLoad         = load;
            sGestureLibraryThreshold    = threshold;
        }
    }

    /**
     * Returns the gestures library to trigger audio / video recording.
     *
     * @return  The {@code GestureLibrary}
     *
     * @see GestureHandler
     */
    @SuppressWarnings("unused")
    public static GestureLibrary getGestureLibrary() {
        synchronized (sGestureLibraryLock) {
            return sGestureLibrary;
        }
    }

    private static       GestureLibrary                 sGestureLibrary;
    private static       boolean                        sGestureLibraryLoad;
    private static       double                         sGestureLibraryThreshold;
    private static final Object                         sGestureLibraryLock;

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean handleGestureForVideo(@NonNull final Activity activity, @NonNull final MotionEvent event) {
        if (!((sUseShake == null || !sUseShake) && sAddresses != null && sAddresses.length > 0)) return false;

        final Runnable runnable = createDataSenderHandler(activity);
        final GestureHandler gestureHandler;

        if (sGestureLibrary == null) {
            log("about to run Yakhont's default GesturesHandler (Z-gesture only)");
            gestureHandler = new GestureHandlerZ(runnable);
        }
        else {
            log("about to run custom GesturesHandler based on library: " + sGestureLibrary);
            gestureHandler = new GestureHandler(GestureHandler.getHandler(runnable),
                    sGestureLibraryThreshold, sGestureLibrary, sGestureLibraryLoad);
            synchronized (sGestureLibraryLock) {
                sGestureLibraryLoad = gestureHandler.mLibrary == null;
            }
        }
        return gestureHandler.handle(event);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static       Double                         sShakeThreshold;
    private static       Integer                        sShakeDelay;
    private static final Object                         sShakeLock;

    /**
     * Sets device shake parameters to trigger audio / video recording.
     *
     * @param threshold
     *        The device shake threshold (or null); default value is 2.0
     *
     * @param delay
     *        The device shake delay (or null); default value is 1000
     */
    @SuppressWarnings("unused")
    public static void setShakeParameters(final Double threshold, final Integer delay) {
        synchronized (sShakeLock) {
            sShakeThreshold     = threshold;
            sShakeDelay         = delay;
        }
    }

    /**
     * Simple shake device handler with customizable threshold. Usage example:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * public class YourActivity extends Activity {
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *         super.onCreate(savedInstanceState);
     *
     *         new ShakeEventListener(new Runnable() {
     *             &#064;Override
     *             public void run() {
     *                 // your code here (to handle device shaking)
     *             }
     *         }).register();
     *     }
     * }
     * </pre>
     */
    public static class ShakeEventListener implements SensorEventListener {

        private static final double                     THRESHOLD                =    2.0;
        private static final int                        DELAY                    = 1000;

        private              double                     mThreshold               = THRESHOLD;
        private              int                        mDelay                   = DELAY;

        private        final Runnable                   mHandler;
        private        final SensorManager              mSensorManager;
        private              Sensor                     mSensor;
        private              long                       mLastShakeTime;

        /**
         * Initialises a newly created {@code ShakeEventListener} object.
         *
         * @param runnable
         *        The {@code Runnable} to handle device shaking
         */
        @SuppressWarnings("WeakerAccess")
        public ShakeEventListener(@NonNull final Runnable runnable) {
            mSensorManager = (SensorManager) Objects.requireNonNull(Utils.getApplication())
                    .getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager != null)
                mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            else
                logWarning(Context.SENSOR_SERVICE + ": mSensorManager == null");
            mHandler = runnable;
        }

        /**
         * Sets device shaking threshold (to run device shaking handler).
         *
         * @param threshold
         *        The device shake threshold (or null); default value is 2.0
         *
         * @return  This {@code ShakeEventListener} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("WeakerAccess")
        public ShakeEventListener setThreshold(final Double threshold) {
            if (threshold != null) mThreshold = threshold;
            return this;
        }

        /**
         * Sets device shaking delay (to run device shaking handler).
         *
         * @param delay
         *        The device shake delay (or null); default value is 1000
         *
         * @return  This {@code ShakeEventListener} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
        public ShakeEventListener setDelay(final Integer delay) {
            if (delay != null) mDelay = delay;
            return this;
        }

        /**
         * Registers device shaking handler (please refer to {@link SensorManager#registerListener} for more info).
         */
        @SuppressWarnings("WeakerAccess")
        public void register() {
            try {
                if (mSensorManager == null) return;
                mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
                log("shake event listener registered");
            }
            catch (Exception exception) {
                log(exception);
            }
        }

        /**
         * Unregisters device shaking handler (please refer to {@link SensorManager#unregisterListener} for more info).
         */
        @SuppressWarnings("WeakerAccess")
        public void unregister() {
            try {
                if (mSensorManager == null) return;
                mSensorManager.unregisterListener(this, mSensor);
                log("shake event listener unregistered");
            }
            catch (Exception exception) {
                log(exception);
            }
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onSensorChanged(final SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

            double gravityTotal = 0;
            for (int i = 0; i <= 2; i++) {
                final float gravity = event.values[i] / SensorManager.GRAVITY_EARTH;
                gravityTotal += gravity * gravity;
            }
            if (Math.sqrt(gravityTotal) < mThreshold) return;

            final long currentTime = System.currentTimeMillis();
            if (mLastShakeTime + mDelay > currentTime) return;

            log("shake detected");
            mLastShakeTime = currentTime;
            Utils.safeRun(mHandler);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            log("onAccuracyChanged: Sensor " + sensor + ", accuracy " + accuracy);
        }
    }

    private static void delete(final File file) {
        try {
            if (file == null || !file.exists()) return;
            log("about to delete " + file);

            final boolean result = file.delete();
            if (!result) logWarning("can not delete " + file);
        }
        catch (Exception exception) {
            log(exception);
        }
    }

    private static void removeTmpFiles(final File dir) {
        try {
            if (dir == null || !dir.exists()) return;

            @SuppressWarnings("Convert2Lambda")
            final File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(final File dir, final String name) {
                    return name.startsWith(Sender.ZIP_PREFIX)        ||
                           name.startsWith(Sender.SCREENSHOT_PREFIX) ||
                           name.startsWith(VIDEO_PREFIX);
                }
            });
            if (files == null) return;

            for (final File file: files) {
                log("about to delete temp file: " + file.getAbsolutePath());
                delete(file);
            }
        }
        catch (Exception exception) {
            log(exception);
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void removeTmpFiles() {
        removeTmpFiles(Utils.getTmpDir(null));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Sender {

        private static final String                     ANR_TRACES               = "/data/anr/traces.txt";
        private static final String                     ZIP_PREFIX               = "yakhont_data";

        // screenshots defaults
        private static final int                        SCREENSHOT_QUALITY       =  100;
        private static final Bitmap.CompressFormat      SCREENSHOT_FORMAT        =  Bitmap.CompressFormat.PNG;

        private static final int                        DELAY                    = 3000;

        private static final String                     DEFAULT_PREFIX           = "yakhont_log";
        private static final String                     DEFAULT_EXTENSION        = "txt";

        private static final String                     SCREENSHOT_PREFIX        = "yakhont_screenshot";

        private static void sendEmail(final Context context, final String cmd, final boolean hasScreenshot,
                                      final boolean hasDb,  final String[]  moreFiles,
                                      final String subject, final String... addresses) {
            Utils.runInBackground(new Runnable() {
                @Override
                public void run() {
                    try {
                        Sender.sendEmailHelper(context, cmd, hasScreenshot, hasDb, moreFiles, subject, addresses);
                    }
                    catch (Exception exception) {
                        log("failed to send email", exception);
                    }
                }

                @NonNull
                @Override
                public String toString() {
                    return "sendEmailHelper";
                }
            });
        }

        private static void sendEmailHelper(final Context context, final String cmd, boolean hasScreenshot,
                                            final boolean hasDb,  final String[]  moreFiles,
                                            final String subject, final String... addresses) {
            if (context == null || addresses == null || addresses.length == 0) {
                logError("no arguments");
                return;
            }

            final Activity activity = Utils.getCurrentActivity();
            if (activity == null) return;

            final File tmpDir = getTmpDir(context);
            if (tmpDir == null) return;

            final String  suffix = Utils.getTmpFileSuffix();
            final boolean hasAnr = new File(ANR_TRACES).exists();

            final Map<String, Exception> errors = new ArrayMap<>();

            final ArrayList<String>  list = new ArrayList<>();
            if (hasAnr)              list.add(ANR_TRACES);

            final File db = hasDb ?
                    BaseCacheProvider.copyDbSync(context, null, null, errors): null;
            if (hasDb && db != null) list.add(db.getAbsolutePath());

            if (moreFiles != null)
                for (final String file: moreFiles) if (!TextUtils.isEmpty(file))
                                     list.add(file);

            final File log = getLogFile(cmd, tmpDir, suffix, errors);
            if (log != null)         list.add(log.getAbsolutePath());

            final String subjectToSend = subject == null ? DEFAULT_PREFIX + suffix: subject;

            String videoPath = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && VideoRecorder.isRunning()) {
                log("sendEmail: video recording is running");

                videoPath = VideoRecorder.stop();
                if (videoPath == null)
                    log("sendEmail: no video available");
                else {
                                     list.add(videoPath);
                    if (hasScreenshot) {
                        logWarning("video recording is running, so no screenshots");
                        hasScreenshot = false;
                    }
                }
            }
            final File video = videoPath == null ? null: new File(videoPath);

            final StringBuilder body = new StringBuilder(String.format(
                    "ANR traces: %s, screenshot: %s, video: %s, database: %s", isAdded(hasAnr),
                    isAdded(hasScreenshot), isAdded(videoPath != null), isAdded(hasDb)));

            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        final File zipFile = getTmpFile(ZIP_PREFIX, suffix, "zip", tmpDir);
                        if (!Utils.zip(errors, zipFile.getAbsolutePath(), list.toArray(new String[0])))
                            return;

                        for (final String error: errors.keySet())
                            body.append(sNewLine).append(sNewLine).append(error).append(sNewLine)
                                    .append(errors.get(error));

                        Utils.runInBackground(DELAY, new Runnable() {
                            @Override
                            public void run() {
                                Utils.sendEmail(activity, subjectToSend, body.toString(), zipFile, addresses);
                            }

                            @NonNull
                            @Override
                            public String toString() {
                                return "sendEmail";
                            }
                        });
                    }
                    finally {
                        delete(video);
                        delete(db);
                        delete(log);
                    }
                }

                @NonNull
                @Override
                public String toString() {
                    return "sendZip";
                }
            };

            if (hasScreenshot)
                getScreenshot(activity, tmpDir, suffix, errors, SCREENSHOT_FORMAT, null,
                        list, runnable, false);
            else
                complete(null, list, runnable);
        }

        private static String isAdded(final boolean added) {
            return (added ? "": "not ") + "added";
        }

        private static File getTmpFile(final String prefix, final String suffix,
                                       final String extension, final File dir) {
            return new File(dir, String.format("%s%s.%s", prefix, suffix, extension));
        }

        private static File getLogFile(final String cmd, final File tmpDir, final String suffix,
                                       final Map<String, Exception> errors) {
            try {
                final File tmpFile = getTmpFile(DEFAULT_PREFIX, suffix, DEFAULT_EXTENSION, tmpDir);
                final PrintWriter output = new PrintWriter(tmpFile);

                //noinspection Anonymous2MethodRef,Convert2Lambda
                getLog(cmd, new LogHandler() {
                    @Override
                    public void handle(String line) {
                        output.println(line);
                    }
                });
                output.close();

                return tmpFile;
            }
            catch (Exception exception) {
                handleError("failed creating log file", exception, errors);
            }
            return null;
        }

        private static void handleError(@NonNull final String text, @NonNull final Exception exception,
                                        final Map<String, Exception> map) {
            log(text, exception);
            if (map != null) map.put(text, exception);
        }

        private static void complete(final File screenshot, final List<String> list, final Runnable runnable) {
            try {
                if (screenshot != null) list.add(screenshot.getAbsolutePath());
                Utils.safeRun(runnable);
            }
            finally {
                delete(screenshot);
            }
        }

        private static File getScreenshot(final Activity activity, final File tmpDir,
                                          final String   suffix,   final Map<String, Exception> errors,
                                          final Bitmap.CompressFormat format, final Integer quality,
                                          final List<String> list, final Runnable runnable,
                                          final boolean forceOld) {
            File screenshot = null;
            try {
                if (forceOld || Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                    screenshot = getScreenshotOld(activity, tmpDir, suffix, errors, format, quality);
                else
                    screenshot = getScreenshotNew(activity, tmpDir, suffix, errors, format, quality);
            }
            catch (Exception exception) {
                handleError("failed creating screenshot", exception, errors);
            }
            if (runnable == null) return screenshot;

            complete(screenshot, list, runnable);
            return null;
        }

        @SuppressWarnings({"deprecation", "RedundantSuppression" /* lint bug workaround */ })
        private static File getScreenshotOld(final Activity activity, final File tmpDir,
                                             final String suffix,     final Map<String, Exception> errors,
                                             final Bitmap.CompressFormat format, final Integer quality) {
            final View    view  = activity.getWindow().getDecorView().getRootView();
            final boolean saved = view.isDrawingCacheEnabled();
            try {
                view.setDrawingCacheEnabled(true);
                File screenshot = null;
                try {
                    screenshot = saveScreenshot(view.getDrawingCache(), tmpDir, suffix, errors, format, quality);
                }
                catch (Exception exception) {
                    log(exception);
                }
                return screenshot;
            }
            finally {
                view.setDrawingCacheEnabled(saved);
            }
        }

        @TargetApi  (      Build.VERSION_CODES.O)
        @RequiresApi(api = Build.VERSION_CODES.O)
        private static File getScreenshotNew(final Activity activity, final File tmpDir,
                                             final String suffix,     final Map<String, Exception> errors,
                                             final Bitmap.CompressFormat format, final Integer quality) {
            if (Utils.isCurrentThreadMain()) {
                logError("PixelCopy.request() should be called from background thread");
                return null;
            }

            final DisplayMetrics dm     = activity.getResources().getDisplayMetrics();
            final Bitmap         bitmap = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels,
                    Bitmap.Config.ARGB_8888);

            final File[]         screenshot     = new File[1];
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            //noinspection Convert2Lambda
            PixelCopy.request(activity.getWindow(), bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
                @Override
                public void onPixelCopyFinished(int copyResult) {
                    if (copyResult == PixelCopy.SUCCESS)
                        screenshot[0] = saveScreenshot(bitmap, tmpDir, suffix, errors, format, quality);
                    else
                        logError("PixelCopy failed with copyResult " + copyResult);

                    countDownLatch.countDown();
                }
            }, new Handler(Looper.getMainLooper()));

            Utils.await(countDownLatch);
            return screenshot[0];
        }

        private static File saveScreenshot(final Bitmap bitmap, final File tmpDir,
                                           final String suffix, final Map<String, Exception> errors,
                                           Bitmap.CompressFormat format, final Integer quality) {
            if (format == null) format = SCREENSHOT_FORMAT;
            try {
                final File tmpFile = getTmpFile(SCREENSHOT_PREFIX, suffix, format.name(), tmpDir);
                final FileOutputStream outputStream = new FileOutputStream(tmpFile);
                //noinspection TryFinallyCanBeTryWithResources
                try {
                    bitmap.compress(format, quality != null ? quality: SCREENSHOT_QUALITY, outputStream);
                }
                finally {
                    outputStream.close();
                }
                return tmpFile;
            }
            catch (Exception exception) {
                handleError("failed creating screenshot", exception, errors);
                return null;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // based on https://github.com/google/grafika and https://github.com/saki4510t/AudioVideoRecordingSample

    private static final String                         VIDEO_PREFIX             = "yakhont_record";

    /**
     * Component to record video / audio, requires API version &gt;= {@link VERSION_CODES#LOLLIPOP LOLLIPOP}.
     * <br>This is simplified version, intended for applications debug only; don't use it for professional
     * audio / video recording.
     * <br>Usage example:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * VideoRecorder.start(activity, outputFile);
     * ...
     * VideoRecorder.stop();
     * </pre>
     *
     * Audio recording can be switched off by the dynamic permission {@link permission#RECORD_AUDIO} -
     * just don't provide it :-)
     *
     * To balance the video size and quality, use {@link #setVideoBitRate} (or {@link #setVideoBitRateQuality}).
     */
    @SuppressWarnings("WeakerAccess")
    @TargetApi  (      Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static class VideoRecorder {

        private static final int                        NO_TRACK                 = -1;
        private static final int                        INDEX_AUDIO              =  0;
        private static final int                        INDEX_VIDEO              =  1;

        private static final int                        CYC_BAR_AWAIT_TIMEOUT    = 3000;    // ms

        /** Suitable bit rate for 1080p (the value is {@value}). */
        public  static final int                        VIDEO_BIT_RATE_1080      = (int) (4.5 * 1024 * 1024);
        /** Suitable bit rate for 720p (the value is {@value}). */
        public  static final int                        VIDEO_BIT_RATE_720       = (int) (2.5 * 1024 * 1024);
        /** Suitable bit rate for 480p (the value is {@value}). */
        public  static final int                        VIDEO_BIT_RATE_480       =              1024 * 1024 ;
        /** Suitable bit rate for 360p (the value is {@value}). */
        public  static final int                        VIDEO_BIT_RATE_360       =               740 * 1024 ;
        /** Suitable bit rate for 240p (the value is {@value}). */
        public  static final int                        VIDEO_BIT_RATE_240       =               400 * 1024 ;
        /** Not-bad quality bit rate (the value is {@value}). */
        public  static final int                        VIDEO_BIT_RATE_HIGH      = (int) (6.8 * 1024 * 1024);
        /** File-size-economic bit rate (the value is {@value}). */
        public  static final int                        VIDEO_BIT_RATE_LOW       =               128 * 1024 ;

        private static final int                        VIDEO_BIT_RATE_DEFAULT   = VIDEO_BIT_RATE_240;

        private static       String                     sDisplayName;
        private static       String                     sFileNameExtension;

        private static       int                        sAudioSampleRate;
        private static       int                        sAudioSamplesPerFrame;
        private static       int                        sAudioFramesPerBuffer;
        private static       int                        sAudioBitRate;
        private static       int                        sAudioQualityRepeat;
        private static       int                        sAudioChannelCount;
        private static       int                        sAudioTimeout;
        private static       int                        sAudioChannelMask;
        private static       int                        sAudioCodecProfileLevel;
        private static       int                        sAudioFormat;
        private static       int                        sAudioSource;
        private static       String                     sAudioMimeType;

        private static       int                        sVideoFrameRate;
        private static       Integer                    sVideoBitRate;
        private static       Boolean                    sVideoBitRateHigh;
        private static       int                        sVideoColorFormat;
        private static       int                        sVirtualDisplayFlags;
        private static       String                     sVideoMimeType;

        private static       int                        sMediaMuxerOutputFormat;

        private static       boolean                    sWarnings;
        private static       int                        sCycBarAwaitTimeout;

        private static       MediaProjectionManager     sMediaProjectionManager;
        private static       MediaProjection            sMediaProjection;
        private static       MediaMuxer                 sMediaMuxer;
        private static final Object                     sMediaMuxerLock;
        private static       MediaCodec[]               sEncoders;
        private static       VirtualDisplay             sVirtualDisplay;
        private static       Surface                    sInputSurface;
        private static       long                       sPrevAudioTime;
        private static       int[]                      sTrackIndexes;
        private static       CyclicBarrier              sCyclicBarrier;

        private static       Runnable                   sHandler;
        private static       String                     sOutputFile;
        private static       boolean                    sIsOk;
        private static final Object                     sIsOkLock;

        private VideoRecorder() {
        }

        static {
            sMediaMuxerLock                                                      = new Object();
            sIsOkLock                                                            = new Object();

            init();
        }

        /**
         * Makes VideoRecorder cleanup; called from {@link Core#cleanUpFinal()}.
         */
        public static void cleanUpFinal() {
            stop();
            init();
        }

        private static void init() {
            sDisplayName             = "Yakhont's Virtual Display";
            sFileNameExtension       = "mp4";

            sAudioSampleRate         =     44100;
            sAudioSamplesPerFrame    =      1024;
            sAudioFramesPerBuffer    =        25;
            sAudioBitRate            = 64 * 1000;
            sAudioQualityRepeat      =         1;
            sAudioChannelCount       =         1;
            sAudioTimeout            = 10 * 1000;
            sAudioChannelMask        = AudioFormat.CHANNEL_IN_MONO;
            sAudioCodecProfileLevel  = CodecProfileLevel.AACObjectLC;
            sAudioFormat             = AudioFormat.ENCODING_PCM_16BIT;
            sAudioSource             = AudioSource.MIC;
            sAudioMimeType           = MediaFormat.MIMETYPE_AUDIO_AAC;

            sVideoFrameRate          = 30;
            sVideoBitRate            = null;
            sVideoBitRateHigh        = null;
            sVideoColorFormat        = CodecCapabilities.COLOR_FormatSurface;
            sVirtualDisplayFlags     = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
            sVideoMimeType           = MediaFormat.MIMETYPE_VIDEO_AVC;
            sMediaMuxerOutputFormat  = OutputFormat.MUXER_OUTPUT_MPEG_4;

            sWarnings                = true;
            sCycBarAwaitTimeout      = CYC_BAR_AWAIT_TIMEOUT;

            sMediaProjectionManager  = null;
            sMediaProjection         = null;
            sEncoders                = new MediaCodec[2];
            sVirtualDisplay          = null;
            sInputSurface            = null;
            sPrevAudioTime           = 0;
            sTrackIndexes            = new int[2];
            sCyclicBarrier           = null;

            synchronized (sMediaMuxerLock) {
                sMediaMuxer          = null;
            }

            sHandler                 = null;
            sOutputFile              = null;

            synchronized (sIsOkLock) {
                sIsOk                = false;
            }
        }

        private static boolean isOk() {
            synchronized (sIsOkLock) {
                return sIsOk;
            }
        }

        private static void setOk(final boolean ok) {
            synchronized (sIsOkLock) {
                sIsOk = ok;
            }
        }

        private static void setHandler(final Runnable handler) {
            sHandler = handler;
        }

        /**
         * Starts audio / video recording.
         *
         * @param activity
         *        The Activity
         *
         * @param outputFile
         *        The output file
         *
         * @return  {@code true} if recording started successfully, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        public static boolean start(@NonNull final Activity activity, @NonNull final String outputFile) {
            sOutputFile             = outputFile;
            final String permission = Manifest.permission.RECORD_AUDIO;

            final boolean result = new CorePermissions.RequestBuilder(activity)
                    .setNotRunAppSettings(true)
                    .setRationale("")           // suppress log error message
                    .addCallbacks(new CorePermissions.Callbacks(permission) {
                        @Override
                        public void onDenied() {
                            super.onDenied();
                            startWrapper(activity, outputFile, false);
                        }

                        @Override
                        public void onGranted() {
                            super.onGranted();
                            startWrapper(activity, outputFile, true);
                        }
                    })
                    .request();

            log(permission + " request result: " + (result ? "already granted": "not granted yet"));
            return result;
        }

        private static void startWrapper(@NonNull final Activity activity, @NonNull final String outputFile,
                                         final boolean useAudio) {
            Utils.postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    final boolean result = start(activity, outputFile, useAudio);
                    log(result ? getDefaultLevel(): Level.WARNING, "recording starting result: " + result);

                    if (result) return;
                    runHandler();
                    stop();
                }

                @NonNull
                @Override
                public String toString() {
                    return "startVideoRecording";
                }
            });
        }

        private static void runHandler() {
            if (sHandler == null) return;

            Utils.safeRun(sHandler);
            sHandler = null;
        }

        private static boolean start(@NonNull final Activity activity, @NonNull final String outputFile,
                                     final boolean useAudio) {
            setOk(false);

            if (isRunning()) {
                logWarning("recording already started");
                return false;
            }

            sMediaProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (sMediaProjectionManager == null) {      // should never happen
                logError("can't get MediaProjectionManager");
                return false;
            }
            log("about to prepare video recording, audio: " + useAudio + ", output file: " + outputFile);

            activity.startActivityForResult(sMediaProjectionManager.createScreenCaptureIntent(),
                    Utils.getRequestCode(useAudio ? RequestCodes.LOGGER_VIDEO_AUDIO_SYSTEM:
                            RequestCodes.LOGGER_VIDEO_SYSTEM, activity));
            return true;
        }

        /**
         * Checks if recording is in progress.
         *
         * @return  {@code true} if recording is in progress, {@code false} otherwise
         */
        public static boolean isRunning() {
            return sMediaProjectionManager != null;
        }

        private static void stop(final Level level, final MediaCodec codec) {
            if (codec == null) return;
            try {
                codec.stop();
            }
            catch (Exception exception) {
                log(level, "MediaCodec stop failed, "    + getDescription(codec), exception);
            }
            try {
                codec.release();
            }
            catch (Exception exception) {
                log(level, "MediaCodec release failed, " + getDescription(codec), exception);
            }
        }

        private static String getDescription(final MediaCodec codec) {
            try {
                return "codec: " + (codec == null ? "null": codec.getName());
            }
            catch (/*IllegalState*/Exception exception) {
                log(Level.WARNING, exception);
                return "codec: description is N/A";
            }
        }

        /**
         * Stops audio / video recording.
         *
         * @return  The output file name
         */
        public static String stop() {
            if (!isRunning()) return null;

            final boolean ok = isOk();
            if (ok)  showToast(R.string.yakhont_record_video_stop);
            log(ok ? getDefaultLevel(): Level.WARNING, "about to stop video recording, result: " + ok);

            final Level level = getDefaultLevel();

            stop(level, sEncoders[INDEX_AUDIO]);
            stop(level, sEncoders[INDEX_VIDEO]);

            if (sMediaMuxer != null) {
                try {
                    if (ok) sMediaMuxer.stop();
                }
                catch (Exception exception) {
                    log(level, "MediaMuxer stop failed", exception);
                }
                try {
                    sMediaMuxer.release();
                }
                catch (Exception exception) {
                    log(level, "MediaMuxer release failed", exception);
                }
            }

            if (sInputSurface != null)
                try {
                    sInputSurface.release();
                }
                catch (Exception exception) {
                    log(level, "InputSurface release failed", exception);
                }

            if (sMediaProjection != null)
                try {
                    sMediaProjection.stop();
                }
                catch (Exception exception) {
                    log(level, "MediaProjection stop failed", exception);
                }

            if (sVirtualDisplay != null)
                try {
                    sVirtualDisplay.release();
                }
                catch (Exception exception) {
                    log(level, "VirtualDisplay release failed", exception);
                }

            sMediaProjectionManager    = null;
            sMediaProjection           = null;
            sMediaMuxer                = null;
            sCyclicBarrier             = null;
            sInputSurface              = null;
            sEncoders    [INDEX_AUDIO] = null;
            sEncoders    [INDEX_VIDEO] = null;
            sVirtualDisplay            = null;
            sTrackIndexes[INDEX_AUDIO] = NO_TRACK;
            sTrackIndexes[INDEX_VIDEO] = NO_TRACK;

            final String result        = ok ? sOutputFile: null;
            sOutputFile                = null;

            setOk(false);
            return result;
        }

        private static void onActivityResult(@NonNull final Activity activity, final int requestCode,
                                             final int resultCode, final Intent data) {
            Utils.onActivityResult("CoreLogger", activity, requestCode, resultCode, data);
            final RequestCodes code = Utils.getRequestCode(requestCode);

            if (code != RequestCodes.LOGGER_VIDEO                   &&
                code != RequestCodes.LOGGER_VIDEO_SYSTEM            &&
                code != RequestCodes.LOGGER_VIDEO_AUDIO_SYSTEM) {

                log("CoreLogger: unknown request code " + requestCode);
                return;
            }
            final boolean useAudio = code == RequestCodes.LOGGER_VIDEO_AUDIO_SYSTEM;

            switch (code) {
                case LOGGER_VIDEO:
                    runHandler();
                    break;

                case LOGGER_VIDEO_SYSTEM:
                case LOGGER_VIDEO_AUDIO_SYSTEM:
                    if (resultCode != Activity.RESULT_OK) {
                        if (resultCode == Activity.RESULT_CANCELED)
                            log("video recording cancelled");
                        else
                            logError("createScreenCaptureIntent() failed, result code: " + resultCode);
                        break;
                    }
                    try {
                        sMediaProjection = sMediaProjectionManager.getMediaProjection(resultCode, data);
                        startRecording(activity, useAudio);

                        showToast(R.string.yakhont_record_video_info);
                        log("starting video recording...");
                    }
                    catch (Exception exception) {
                        log("start video recording failed", exception);

                        stop();
                        runHandler();
                    }
                    break;
            }
        }

        private static void showToast(@StringRes final int textId) {
            if (sWarnings) new Utils.ToastBuilder()
                    .setTextId(textId)
                    .setDuration(Toast.LENGTH_SHORT)
                    .show();
        }

        private static void startRecording(@NonNull final Activity activity, final boolean useAudio) throws IOException {
            sMediaMuxer    = new MediaMuxer(sOutputFile, sMediaMuxerOutputFormat);
            sCyclicBarrier = new CyclicBarrier(2, null);

            try {
                if (useAudio) setupAudio();
                if (sEncoders[INDEX_AUDIO] != null) {
                    sEncoders[INDEX_AUDIO].start();

                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                runAudio();
                            }
                            catch (Exception exception) {
                                log("audio record failed", exception);
                            }
                        }

                        @NonNull
                        @Override
                        public String toString() {
                            return "startAudioRecording";
                        }
                    }.start();
                }
            }
            catch (Exception exception) {
                log("start audio recording failed", exception);
                sEncoders[INDEX_AUDIO]  = null;
            }

            final DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
            setupVideo(displayMetrics.widthPixels, displayMetrics.heightPixels);

            sMediaProjection.createVirtualDisplay(sDisplayName, displayMetrics.widthPixels,
                    displayMetrics.heightPixels, displayMetrics.densityDpi, sVirtualDisplayFlags,
                    sInputSurface, null, null);

            if (sEncoders[INDEX_VIDEO] != null) sEncoders[INDEX_VIDEO].start();
        }

        private static void setupAudio() throws IOException {
            final MediaFormat mediaFormat = MediaFormat.createAudioFormat(sAudioMimeType,
                    sAudioSampleRate, sAudioChannelCount);

            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,  sAudioCodecProfileLevel);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, sAudioChannelMask);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,     sAudioBitRate);

            sEncoders[INDEX_AUDIO] = MediaCodec.createEncoderByType(sAudioMimeType);
            sEncoders[INDEX_AUDIO].configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }

        private static void runAudio() {
            sTrackIndexes[INDEX_AUDIO] = NO_TRACK;

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            final int minBufferSize = AudioRecord.getMinBufferSize(
                    sAudioSampleRate, sAudioChannelMask, sAudioFormat);
            int bufferSize = sAudioSamplesPerFrame * sAudioFramesPerBuffer;
            if (bufferSize <  minBufferSize)
                bufferSize = (minBufferSize / sAudioSamplesPerFrame + 1) * sAudioSamplesPerFrame * 2;

            AudioRecord audioRecord;
            try {
                audioRecord = new AudioRecord(sAudioSource, sAudioSampleRate, sAudioChannelMask,
                        sAudioFormat, bufferSize);
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    audioRecord = null;
                    logError("AudioRecord failed");
                }
            }
            catch (Exception exception) {
                audioRecord = null;
                log("AudioRecord failed", exception);
            }
            if (audioRecord == null) return;

            try {
                log("start audio recording");
                final ByteBuffer buffer = ByteBuffer.allocateDirect(sAudioSamplesPerFrame);

                audioRecord.startRecording();
                try {
                    for (;;) {
                        buffer.clear();
                        final int bytesQty = audioRecord.read(buffer, sAudioSamplesPerFrame);
                        if (bytesQty <= 0) continue;

                        buffer.position(bytesQty);
                        buffer.flip();

                        encodeAudio(buffer, bytesQty, getAudioTime());
                        if (!saveAudio()) break;
                    }
                }
                finally {
                    log("about to call AudioRecord.stop()");
                    audioRecord.stop();
                }
            }
            finally {
                log("about to call AudioRecord.release()");
                audioRecord.release();
            }
        }

        private static long getAudioTime() {
            final long result = System.nanoTime() / 1000;
            return Math.max(result, sPrevAudioTime);
        }

        private static void encodeAudio(final ByteBuffer buffer, final int length, final long time) {
            while (isOk()) {
                final int bufferId;
                try {
                    bufferId = sEncoders[INDEX_AUDIO].dequeueInputBuffer(sAudioTimeout);
                }
                catch (IllegalStateException exception) {
                    log(getDefaultLevel(), "audio record", exception);
                    return;
                }
                if (bufferId < 0) continue;

                final ByteBuffer inputBuffer = sEncoders[INDEX_AUDIO].getInputBuffer(bufferId);
                if (inputBuffer == null) {
                    logError("audio record: null input buffer, " + getDescription(sEncoders[INDEX_AUDIO]));
                    continue;
                }
                inputBuffer.clear();
                if (buffer != null) inputBuffer.put(buffer);

                if (length <= 0) {
                    log("audio record: send MediaCodec.BUFFER_FLAG_END_OF_STREAM");
                    sEncoders[INDEX_AUDIO].queueInputBuffer(bufferId, 0, 0,
                            time, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
                else
                    sEncoders[INDEX_AUDIO].queueInputBuffer(bufferId, 0, length,
                            time, 0);
                break;
            }
        }

        private static boolean saveAudio() {
            boolean isContinue = isOk();

            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            int status = 0;
            for (int i = 0; i < sAudioQualityRepeat; i++) {
                try {
                    status = sEncoders[INDEX_AUDIO].dequeueOutputBuffer(info, sAudioTimeout);
                }
                catch (IllegalStateException exception) {
                    log(getDefaultLevel(), "audio record: stop", exception);
                    return false;
                }
                if (status != MediaCodec.INFO_TRY_AGAIN_LATER) break;
            }
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                logWarning("audio record: wrong status MediaCodec.INFO_TRY_AGAIN_LATER");
                return isContinue;
            }

            //noinspection SwitchStatementWithTooFewBranches
            switch (status) {
/*              case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    logError("audio record: MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
*/
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    sTrackIndexes[INDEX_AUDIO] = sMediaMuxer.addTrack(sEncoders[INDEX_AUDIO].getOutputFormat());
                    log("audio track index: " + sTrackIndexes[INDEX_AUDIO] + ", " +
                            getDescription(sEncoders[INDEX_AUDIO]));

                    startMediaMuxer(sEncoders[INDEX_VIDEO] != null);
                    isContinue = true;
                    break;

                default:
                    if (status < 0) {
                        logError("unknown result from audioEncoder.dequeueOutputBuffer(): " + status);
                        break;
                    }
                    final ByteBuffer buffer = sEncoders[INDEX_AUDIO].getOutputBuffer(status);

                    if (buffer == null)                             // should never happen
                        logError("audio record: output buffer is null, status " + status);
                    else {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;

                        if (info.size != 0) {
                            info.presentationTimeUs = getAudioTime();
                            synchronized (sMediaMuxerLock) {
                                sMediaMuxer.writeSampleData(sTrackIndexes[INDEX_AUDIO], buffer, info);
                            }
                            sPrevAudioTime = info.presentationTimeUs;
                        }
                    }
                    sEncoders[INDEX_AUDIO].releaseOutputBuffer(status, false);

                    break;
            }

            return isContinue;
        }

        @SuppressWarnings("ConstantConditions")
        private static int getVideoBitRate(final int height) {
            if (sVideoBitRate     != null) return sVideoBitRate;
            if (sVideoBitRateHigh != null) return sVideoBitRateHigh ? VIDEO_BIT_RATE_HIGH: VIDEO_BIT_RATE_LOW;
            int value = VIDEO_BIT_RATE_DEFAULT;
            if (height <= 1080 && value > VIDEO_BIT_RATE_1080) value = VIDEO_BIT_RATE_1080;
            if (height <=  720 && value > VIDEO_BIT_RATE_720)  value = VIDEO_BIT_RATE_720;
            if (height <=  480 && value > VIDEO_BIT_RATE_480)  value = VIDEO_BIT_RATE_480;
            if (height <=  360 && value > VIDEO_BIT_RATE_360)  value = VIDEO_BIT_RATE_360;
            if (height <=  240 && value > VIDEO_BIT_RATE_240)  value = VIDEO_BIT_RATE_240;
            return value;
        }

        private static void setupVideo(final int width, final int height) throws IOException {
            final MediaFormat mediaFormat = MediaFormat.createVideoFormat(sVideoMimeType, width, height);

            final int bitRate = getVideoBitRate(height);
            CoreLogger.log("MediaFormat.KEY_BIT_RATE: " + bitRate);

            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, sVideoColorFormat);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,     bitRate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,   sVideoFrameRate);
            mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, sVideoFrameRate);
            mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000 * 1000 / sVideoFrameRate);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            sEncoders[INDEX_VIDEO] = MediaCodec.createEncoderByType(sVideoMimeType);
            sEncoders[INDEX_VIDEO].setCallback(getVideoCallback());

            sEncoders[INDEX_VIDEO].configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            sInputSurface = sEncoders[INDEX_VIDEO].createInputSurface();
        }

        private static MediaCodec.Callback getVideoCallback() {
            sTrackIndexes[INDEX_VIDEO] = NO_TRACK;

            return new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(final @NonNull MediaCodec codec, final int index) {
                    log("video record: onInputBufferAvailable(), " + getDescription(codec) +
                            ", index: " + index);
                }

                @Override
                public void onOutputBufferAvailable(final @NonNull MediaCodec codec, final int index,
                                                    final @NonNull MediaCodec.BufferInfo info) {
                    try {
                        outputBufferAvailable(codec, index, info);
                    }
                    catch (Exception exception) {
                        log("video record", exception);
                    }
                }

                private void outputBufferAvailable(final @NonNull MediaCodec codec, final int index,
                                                   final @NonNull MediaCodec.BufferInfo info) {
                    final ByteBuffer encodedData;
                    try {
                        encodedData = codec.getOutputBuffer(index);
                    }
                    catch (IllegalStateException exception) {
                        log(getDefaultLevel(), "video record", exception);
                        return;
                    }
                    if (encodedData == null) {
                        logError("video record: couldn't fetch MediaCodec buffer at index " +
                                index + ", " + getDescription(codec));
                        return;
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;

                    if (info.size != 0 && isOk()) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);

                        synchronized (sMediaMuxerLock) {
                            sMediaMuxer.writeSampleData(sTrackIndexes[INDEX_VIDEO], encodedData, info);
                        }
                    }
                    codec.releaseOutputBuffer(index, false);
                }

                @Override
                public void onError(final @NonNull MediaCodec codec,
                                    final @NonNull MediaCodec.CodecException exception) {
                    log("video record: error, " + getDescription(codec), exception);
                }

                @Override
                public void onOutputFormatChanged(final @NonNull MediaCodec  codec,
                                                  final @NonNull MediaFormat format) {
                    try {
                        outputFormatChanged(codec, format);
                    }
                    catch (Exception exception) {
                        log("video record", exception);
                    }
                }

                private void outputFormatChanged(final @NonNull MediaCodec  codec,
                                                 final @NonNull MediaFormat format) {
                    log("video record: output format changed, " + format);
                    if (sTrackIndexes[INDEX_VIDEO] >= 0) {
                        logError("video record: format changed twice " + format);
                        return;
                    }
                    sTrackIndexes[INDEX_VIDEO] = sMediaMuxer.addTrack(codec.getOutputFormat());
                    log("video track index: " + sTrackIndexes[INDEX_VIDEO] + ", " + getDescription(codec));

                    startMediaMuxer(sEncoders[INDEX_AUDIO] != null);
                }
            };
        }

        private static void startMediaMuxer(final boolean wait) {
            String                              info = "audio / video";
            if (sTrackIndexes[INDEX_VIDEO] < 0) info = "audio";
            if (sTrackIndexes[INDEX_AUDIO] < 0) info = "video";

            if (wait) {
                log(info + " record: wait");
                try {
                    sCyclicBarrier.await(sCycBarAwaitTimeout, TimeUnit.MILLISECONDS);
                }
                catch (Exception exception) {
                    log(info + " record: failed", exception);
                    return;
                }
            }

            synchronized (sIsOkLock) {
                if (!isOk() && (sTrackIndexes[INDEX_VIDEO] >= 0 || sTrackIndexes[INDEX_AUDIO] >= 0)) {
                    sMediaMuxer.start();

                    log(info + " record: started");
                    setOk(true);
                }
            }
        }

        /**
         * Indicates whether {@link Toast} alerts about start / stop recording should be displayed.
         *
         * @param value
         *        {@code true} to display alerts, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        public static void setWarnings(final boolean value) {
            sWarnings = value;
        }

        /**
         * Sets virtual display name. Please refer to {@link MediaProjection#createVirtualDisplay} for more info.
         *
         * @param value
         *        The virtual display name
         */
        @SuppressWarnings("unused")
        public static void setDisplayName(final String value) {
            sDisplayName = value;
        }

        /**
         * Sets output file name extension; the default value is "mp4".
         *
         * @param value
         *        The output file name extension
         */
        @SuppressWarnings("unused")
        public static void setFileNameExtension(final String value) {
            sFileNameExtension = value;
        }

        /**
         * Sets audio sample rate; the default value is 44100.
         *
         * @param value
         *        The audio sample rate
         */
        @SuppressWarnings("unused")
        public static void setAudioSampleRate(final int value) {
            sAudioSampleRate = value;
        }

        /**
         * Sets audio samples per frame; the default value is 1024.
         *
         * @param value
         *        The audio samples per frame
         */
        @SuppressWarnings("unused")
        public static void setAudioSamplesPerFrame(final int value) {
            sAudioSamplesPerFrame = value;
        }

        /**
         * Sets audio frames per buffer; the default value is 25.
         *
         * @param value
         *        The audio frames per buffer
         */
        @SuppressWarnings("unused")
        public static void setAudioFramesPerBuffer(final int value) {
            sAudioFramesPerBuffer = value;
        }

        /**
         * Sets audio bitrate; the default value is 64000.
         *
         * @param value
         *        The audio bitrate
         */
        @SuppressWarnings("unused")
        public static void setAudioBitRate(final int value) {
            sAudioBitRate = value;
        }

        /**
         * Sets audio channel mask; the default value is {@link AudioFormat#CHANNEL_IN_MONO}.
         *
         * @param value
         *        The audio channel mask
         */
        @SuppressWarnings("unused")
        public static void setAudioChannelMask(final int value) {
            sAudioChannelMask = value;
        }

        /**
         * Sets audio format; the default value is {@link AudioFormat#ENCODING_PCM_16BIT}.
         *
         * @param value
         *        The audio format
         */
        @SuppressWarnings("unused")
        public static void setAudioFormat(final int value) {
            sAudioFormat = value;
        }

        /**
         * Sets audio mime type; the default value is {@link MediaFormat#MIMETYPE_AUDIO_AAC}.
         *
         * @param value
         *        The audio mime type
         */
        @SuppressWarnings("unused")
        public static void setAudioMimeType(final String value) {
            sAudioMimeType = value;
        }

        /**
         * Sets audio quality; the default value is 1.
         *
         * @param value
         *        The audio quality
         */
        @SuppressWarnings("unused")
        public static void setAudioQuality(final int value) {
            sAudioQualityRepeat = value;
        }

        /**
         * Sets audio timeout; the default value is 10000. Please refer to
         * {@link MediaCodec#dequeueInputBuffer} and {@link MediaCodec#dequeueOutputBuffer} for more info.
         *
         * @param value
         *        The audio timeout
         */
        @SuppressWarnings("unused")
        public static void setAudioTimeout(final int value) {
            sAudioTimeout = value;
        }

        /**
         * Sets audio source; the default value is {@link AudioSource#MIC}.
         *
         * @param value
         *        The audio source
         */
        @SuppressWarnings("unused")
        public static void setAudioSource(final int value) {
            sAudioSource = value;
        }

        /**
         * Sets audio codec profile level; the default value is {@link CodecProfileLevel#AACObjectLC}.
         *
         * @param value
         *        The audio codec profile level
         */
        @SuppressWarnings("unused")
        public static void setAudioCodecProfileLevel(final int value) {
            sAudioCodecProfileLevel = value;
        }

        /**
         * Sets audio channels count; the default value is 1.
         *
         * @param value
         *        The audio channels count
         */
        @SuppressWarnings("unused")
        public static void setAudioChannelCount(final int value) {
            sAudioChannelCount = value;
        }

        /**
         * Sets virtual display flags; the default value is {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR}.
         * Please refer to {@link DisplayManager#createVirtualDisplay} for more info.
         *
         * @param value
         *        The virtual display flags
         */
        @SuppressWarnings("unused")
        public static void setVirtualDisplayFlags(final int value) {
            sVirtualDisplayFlags = value;
        }

        /**
         * Sets video mime type; the default value is {@link MediaFormat#MIMETYPE_VIDEO_AVC}.
         *
         * @param value
         *        The video mime type
         */
        @SuppressWarnings("unused")
        public static void setVideoMimeType(final String value) {
            sVideoMimeType = value;
        }

        /**
         * Sets video bitrate (overrides {@link #setVideoBitRateQuality}).
         *
         * <p>Use it to balance the video size and quality.
         *
         * @param value
         *        The video bitrate
         *
         * @see #VIDEO_BIT_RATE_HIGH
         * @see #VIDEO_BIT_RATE_LOW
         * @see #VIDEO_BIT_RATE_1080
         * @see #VIDEO_BIT_RATE_720
         * @see #VIDEO_BIT_RATE_480
         * @see #VIDEO_BIT_RATE_360
         * @see #VIDEO_BIT_RATE_240
         */
        @SuppressWarnings("unused")
        public static void setVideoBitRate(final int value) {
            sVideoBitRate     = value;
            sVideoBitRateHigh = null ;
        }

        /**
         * Sets video bitrate quality (simplified version of {@link #setVideoBitRate}).
         *
         * @param value
         *        {@code true} for {@link #VIDEO_BIT_RATE_HIGH}, {@code false} for {@link #VIDEO_BIT_RATE_LOW}
         *
         * @see #setVideoBitRate
         */
        @SuppressWarnings("unused")
        public static void setVideoBitRateQuality(final boolean value) {
            if (sVideoBitRate == null)
                sVideoBitRateHigh = value;
            else
                logError("video bitrate is already defined: " + sVideoBitRate);
        }

        /**
         * Sets video frame rate; the default value is 30.
         *
         * @param value
         *        The video frame rate
         */
        @SuppressWarnings("unused")
        public static void setVideoFrameRate(final int value) {
            sVideoFrameRate = value;
        }

        /**
         * Sets video color format; the default value is {@link CodecCapabilities#COLOR_FormatSurface}.
         *
         * @param value
         *        The video color format
         */
        @SuppressWarnings("unused")
        public static void setVideoColorFormat(final int value) {
            sVideoColorFormat = value;
        }

        /**
         * Sets {@link MediaMuxer} output format; the default value is {@link OutputFormat#MUXER_OUTPUT_MPEG_4}.
         *
         * @param value
         *        The {@link MediaMuxer} output format
         */
        @SuppressWarnings("unused")
        public static void setMediaMuxerOutputFormat(final int value) {
            sMediaMuxerOutputFormat = value;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public static void setCyclicBarrierAwaitTimeout(final int value) {
            sCycBarAwaitTimeout = value;
        }
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void onActivityResult(@NonNull final Activity activity, final int requestCode,
                                        final int resultCode, final Intent data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            VideoRecorder.onActivityResult(activity, requestCode, resultCode, data);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // @LogDebug support

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final boolean[] x) { return toString(Arrays.toString(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final char   [] x) { return toString(Arrays.toString(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final byte   [] x) { return toString(Arrays.toString(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final short  [] x) { return toString(Arrays.toString(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final int    [] x) { return toString(Arrays.toString(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final long   [] x) { return toString(Arrays.toString(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final float  [] x) { return toString(Arrays.toString(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final double [] x) { return toString(Arrays.toString(x)); }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final boolean x) { return toString(Boolean  .valueOf(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final char    x) { return toString(Character.valueOf(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final byte    x) { return toString(Byte     .valueOf(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final short   x) { return toString(Short    .valueOf(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final int     x) { return toString(Integer  .valueOf(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final long    x) { return toString(Long     .valueOf(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final float   x) { return toString(Float    .valueOf(x)); }
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String toString(final double  x) { return toString(Double   .valueOf(x)); }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static String toString(final Object object) {
        return object == null ? "null": object instanceof Object[] ?
                Arrays.deepToString((Object[]) object): object.toString();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static Level getLogDebugLevel(@NonNull final Object object, @NonNull final String name,
                                                  final Object... args) {
        try {
            return ((LogDebug) CoreReflection.getAnnotationMethod(
                    object, LogDebug.class, name, args)).value();
        }
        catch (Exception exception) {       // throws for obfuscated methods in release builds
            Log.e(getTag(null), "@LogDebug - getLogDebugLevel() failed (default one is accepted); " +
                    "if it's a problem, you can try to switch off obfuscation", exception);
            return Level.ERROR;             // should be consistent with @LogDebug default level
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static String getLogDebugDescription(@NonNull final Object object, @NonNull final String name,
                                                final Object value, final Object... args) {
        try {
            return String.format("object: %s, method: %s, return value: %s, arguments: %s",
                    getDescription(object), name, value, Arrays.deepToString(args));
        }
        catch (Exception exception) {       // should never happen
            Log.e(getTag(null), "@LogDebug - getLogDebugDescription() failed", exception);
            return "@LogDebug: object info is N/A";
        }
    }
}
