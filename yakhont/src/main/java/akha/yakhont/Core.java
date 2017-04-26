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

import akha.yakhont.CoreLogger.Level;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.HideKeyboardCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.OrientationCallbacks;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.technology.Dagger2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The base class for the library. Usage example in Activity:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * &#064;Override
 * protected void onCreate(Bundle savedInstanceState) {
 *     Core.run(getApplication());
 *
 *     super.onCreate(savedInstanceState);
 *     ...
 * }
 * </pre>
 *
 * And the same in Application:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * &#064;Override
 * public void onCreate() {
 *     super.onCreate();
 *     ...
 *     Core.run(this);
 * }
 * </pre>
 *
 * @see #run(Application)
 * @see #run(Application, Boolean, Dagger2)
 *
 * @author akha
 */
public class Core {

    private static final String                         BASE_URI                    = "content://%s.provider";
    @SuppressWarnings("unused")
    private static final String                         LOG_TAG_FORMAT              = "%s-v.%d-%d-%s";

    private static final String                         PREFERENCES_NAME            = "BasePreferences";

    private static final RequestCodes[]                 REQUEST_CODES_VALUES        = RequestCodes.values();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @IntRange(from = 1) public  static final int        TIMEOUT_CONNECTION          = 20;   // seconds
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @IntRange(from = 0) public  static final int        TIMEOUT_CONNECTION_TIMER    =  3;

    @IntRange(from = 0) private static final int        TIMEOUT_NETWORK_MONITOR     =  3;   // seconds

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public enum RequestCodes {
        LOCATION_CONNECTION_FAILED,
        LOCATION_ALERT,
        LOCATION_CLIENT,
        PROGRESS_ALERT
    }

    /**
     * The screen orientations.
     */
    public enum Orientation {
        /** The "unspecified" orientation, which means the default one. */
        UNSPECIFIED,
        /** The "portrait" orientation. */
        PORTRAIT,
        /** The "landscape" orientation. */
        LANDSCAPE
    }

    private static       Core                           sInstance;
    private static       Dagger2                        sDagger;
    private static       boolean                        sSupport;

    /**
     *  The dialog API that are common to the whole library.
     *  Every custom dialog implementation which is intended to replace the default one should implement that interface.
     *  See {@link #run(Application, Boolean, Dagger2)} and {@link Dagger2} for more details concerning custom dialogs.
     */
    public interface BaseDialog {

        /**
         * Starts dialog.
         *
         * @param context
         *        The Context
         *
         * @param text
         *        The text to display
         *
         * @return  {@code true} if dialog was started successfully, {@code false} otherwise
         */
        boolean start(Context context, String text);

        /**
         * Stops dialog.
         *
         * @return  {@code true} if dialog was stopped successfully, {@code false} otherwise
         */
        boolean stop();
    }

    /**
     * The callback API which allows registered components to be notified about device configuration changes.
     * Please refer to {@link #register(ConfigurationChangedListener)} and {@link #unregister(ConfigurationChangedListener)}.
     */
    public interface ConfigurationChangedListener {

        /**
         * Called by the system when the device configuration changes.
         *
         * @param newConfig
         *        The new device configuration
         */
        @SuppressWarnings("UnusedParameters")
        void onChangedConfiguration(Configuration newConfig);
    }

    /**
     * The API to configure loaders.
     */
    public interface ConfigurableLoader {

        /**
         * Sets the "force cache" flag. Setting to {@code true} forces loading data from cache.
         *
         * @param forceCache
         *        The value to set
         */
        @SuppressWarnings({"EmptyMethod", "UnusedReturnValue"}) void setForceCache(boolean forceCache);

        /**
         * Sets the "no progress" flag. Set to {@code true} to not display loading progress.
         *
         * @param noProgress
         *        The value to set
         */
        @SuppressWarnings({"EmptyMethod", "UnusedReturnValue"}) void setNoProgress(boolean noProgress);
    }

    /**
     * The API for data loading.
     *
     * @param <C>
     *        The type of callback
     */
    @SuppressWarnings("unused")
    public interface Requester<C> {

        /**
         * Starts an asynchronous load.
         *
         * @param callback
         *        The callback
         *
         * @yakhont.see BaseLoader#makeRequest(C) BaseLoader.makeRequest()
         */
        void makeRequest(C callback);
    }

    /**
     * The API to resolve URI.
     */
    public interface UriResolver {

        /**
         * Finds URI for the given table.
         *
         * @param tableName
         *        The table name
         *
         * @return  The URI
         */
        Uri getUri(String tableName);
    }

    private Core() {
    }

    /**
     * Returns the Dagger2 component.
     *
     * @return  The Dagger2
     */
    @NonNull
    public static Dagger2 getDagger() {
        return sDagger;
    }

    /**
     * Forces working in support mode (use weaving mechanism for calling the application callbacks instead of registering via
     * {@link Application#registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks)} and
     * {@link Application#registerComponentCallbacks(ComponentCallbacks)}).
     * Mostly for debug purposes.
     */
    @SuppressWarnings("unused")
    public static void forceSupportMode() {
        sSupport = true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Activates the library.
     *
     * @param application
     *        The Application
     *
     * @return  {@code true} if library activation was successful, {@code false} otherwise (library is already activated)
     */
    @SuppressWarnings("unused")
    public static boolean run(@SuppressWarnings("SameParameterValue") @NonNull        final Application       application) {
        return run(application, null, null);
    }

    /**
     * Activates the library. Usage example:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.fragment.dialog.CommonDialogFragment;
     * import akha.yakhont.technology.Dagger2;
     *
     * import dagger.Component;
     * import dagger.Module;
     *
     * public class MyActivity extends Activity {
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *         Core.run(getApplication(), BuildConfig.DEBUG, DaggerMyActivity_MyDagger.create());
     *
     *         super.onCreate(savedInstanceState);
     *         ...
     *     }
     *
     *     // custom progress dialog theme example
     *
     *     &#064;Component(modules = {Dagger2.LocationModule.class, MyUiModule.class})
     *     interface MyDagger extends Dagger2 {
     *     }
     *
     *     &#064;Module
     *     static class MyUiModule extends Dagger2.UiModule {
     *
     *         &#064;Override
     *         protected Core.BaseDialog getProgress() {
     *             return ((CommonDialogFragment) super.getProgress()).setTheme(R.style.MyTheme);
     *         }
     *     }
     * }
     * </pre>
     *
     * @param application
     *        The Application
     *
     * @param fullInfo
     *        Indicates whether the detailed logging should be enabled or not
     *
     * @param dagger
     *        The Dagger2 component
     *
     * @return  {@code true} if library activation was successful, {@code false} otherwise (library is already activated)
     *
     * @see Dagger2
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean run(@SuppressWarnings("SameParameterValue") @NonNull        final Application       application,
                              @SuppressWarnings("SameParameterValue")                 final Boolean           fullInfo,
                              @SuppressWarnings("SameParameterValue")                 final Dagger2           dagger) {

        if (sInstance != null) {
            CoreLogger.logWarning("already done");
            return false;
        }
        sInstance = new Core();

        Init.logging(application,
                fullInfo == null ? Utils.isDebugMode(application.getPackageName()): fullInfo);
        Init.allRemaining(application);

        CoreLogger.log("orientation "   + Utils.getOrientation(application));
        CoreLogger.log("uri "           + Utils.getBaseUri());
        CoreLogger.log("support "       + sSupport);

        registerCallbacks(application);

        sDagger = dagger != null ? dagger: akha.yakhont.technology.DaggerDagger2_DefaultComponent.create();
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressLint("ObsoleteSdkInt")
    private static void registerCallbacks(@NonNull final Application application) {
        register((BaseActivityCallbacks) new HideKeyboardCallbacks()               .setForceProceed(true));
        register((BaseActivityCallbacks) new OrientationCallbacks()                .setForceProceed(true));
        register((BaseActivityCallbacks) SupportHelper.getWorkerFragmentCallbacks().setForceProceed(true));

        register((BaseActivityCallbacks) new LocationCallbacks());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !sSupport) {
            application.registerActivityLifecycleCallbacks(new ActivityLifecycleProceed());
            application.registerComponentCallbacks        (new ApplicationCallbacks.ApplicationCallbacks2());
        }
        else {
            BaseActivityLifecycleProceed.setActive(true);
            sAppCallbacks = new ApplicationCallbacks();
        }
    }

    @SuppressWarnings({"UnusedReturnValue", "ConstantConditions", "unused"})
    private static boolean register(@NonNull final BaseActivityCallbacks callbacks) {
        return BaseActivityLifecycleProceed.register(callbacks);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess", "BooleanMethodIsAlwaysInverted"})
    public static boolean isVisible() {
        return BaseActivityLifecycleProceed.isVisible();
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "BooleanMethodIsAlwaysInverted", "WeakerAccess"})
    public static boolean isInForeground() {
        return BaseActivityLifecycleProceed.isInForeground();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private       static   ApplicationCallbacks         sAppCallbacks;

    private final static   Set<ConfigurationChangedListener>
                                                        sAppCallbacksListeners      = Utils.newSet();

    /**
     * Registers component to be notified about device configuration changes.
     *
     * @param listener
     *        The component to register
     *
     * @return  {@code true} if registration was successful, {@code false} otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean register(@NonNull final ConfigurationChangedListener listener) {
        return sAppCallbacksListeners.add(listener);
    }

    /**
     * Removes a {@code ConfigurationChangedListener} component that was previously registered with {@link #register(ConfigurationChangedListener)}.
     *
     * @param listener
     *        The component to remove
     *
     * @return  {@code true} if component removing was successful, {@code false} otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean unregister(@NonNull final ConfigurationChangedListener listener) {
        return sAppCallbacksListeners.remove(listener);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public static void onApplicationConfigurationChanged(final Configuration newConfig) {
        if (sAppCallbacks == null) return;

        CoreLogger.log("subject to call by weaver");

        sAppCallbacks.onConfigurationChanged(newConfig);
    }

    private static class ApplicationCallbacks extends BaseListeners implements ComponentCallbacks {

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onConfigurationChanged(final Configuration newConfig) {
            CoreLogger.logWarning("newConfig " + newConfig);

            for (final ConfigurationChangedListener listener: sAppCallbacksListeners)
                notifyListener(new Runnable() {
                    @Override
                    public void run() {
                        listener.onChangedConfiguration(newConfig);
                    }
                });
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onLowMemory() {
            CoreLogger.logWarning("low memory");
        }

        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        private static class ApplicationCallbacks2 extends ApplicationCallbacks implements ComponentCallbacks2 {

            /**
             * Please refer to the base method description.
             */
            @Override
            public void onTrimMemory(int level) {
                CoreLogger.logWarning("level " + level);
            }
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class BaseListeners {

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void notifyListener(@NonNull final Runnable runnable) {
            try {
                runnable.run();
            }
            catch (Exception e) {
                CoreLogger.log("failed", e);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    private static class Init extends BaseListeners {

        private static final AtomicBoolean              sConnected                      = new AtomicBoolean(true);
        private static boolean                          sRunNetworkMonitor;
        private static String                           sBaseUri;

        private static void logging(@NonNull final Application application, final boolean fullInfo) {
            int version = -1;
            try {
                version = application.getPackageManager().getPackageInfo(
                        application.getPackageName(), 0).versionCode;
            }
            catch (PackageManager.NameNotFoundException e) {
                CoreLogger.log("can not define version code", e);
            }
            CoreLogger.setTag(String.format(CoreLogger.getLocale(), LOG_TAG_FORMAT,
                    application.getClass().getSimpleName(), version,
                    akha.yakhont.BuildConfig.VERSION_CODE, akha.yakhont.BuildConfig.FLAVOR));

            CoreLogger.setFullInfo(fullInfo);

            SupportHelper.enableFragmentManagerDebugLogging(fullInfo);
            SupportHelper.enableLoaderManagerDebugLogging  (fullInfo);
        }

        @SuppressWarnings("UnusedReturnValue")
        private static void allRemaining(@NonNull final Application application) {
            sBaseUri = String.format(BASE_URI, application.getPackageName());

            if (!sRunNetworkMonitor) return;

            new Timer("timer for network monitoring").scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (!isVisible() || !isInForeground()) return;

                    boolean isConnected = false;

                    final ConnectivityManager connectivityManager =
                            (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
                    final NetworkInfo activeInfo = connectivityManager.getActiveNetworkInfo();

                    if (activeInfo != null && activeInfo.isConnected()) isConnected = true;

                    if (sConnected.getAndSet(isConnected) != isConnected) {
                        CoreLogger.log((isConnected ? Level.INFO: Level.WARNING),
                                "network is " + (isConnected ? "": "NOT ") + "available");
                        onNetworkStatusChanged(isConnected);
                    }
                }
            }, 0, TIMEOUT_NETWORK_MONITOR * 1000);
        }

        private static void onNetworkStatusChanged(final boolean isConnected) {
            for (final NetworkStatusListener listener: sNetworkStatusListeners)
                notifyListener(new Runnable() {
                    @Override
                    public void run() {
                        listener.onNetworkStatusChanged(isConnected);
                    }
                });
        }
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public static void setRunNetworkMonitor(final boolean runNetworkMonitor) {
        Init.sRunNetworkMonitor = runNetworkMonitor;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public interface NetworkStatusListener {
        void onNetworkStatusChanged(boolean isConnected);
    }

    private static final Set<NetworkStatusListener>     sNetworkStatusListeners         = Utils.newSet();

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean register(@NonNull final NetworkStatusListener listener) {
        return sNetworkStatusListeners.add(listener);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean unregister(@NonNull final NetworkStatusListener listener) {
        return sNetworkStatusListeners.remove(listener);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * An utility class.
     */
    public static class Utils {

        /** Not valid resource ID (the value is {@value}). */
        public static final int                         NOT_VALID_RES_ID                = 0;

        private static final Handler                    sHandler                        = new Handler(Looper.getMainLooper());
        private static       UriResolver                sUriResolver                    = new UriResolver() {
            @Override
            public Uri getUri(@NonNull final String tableName) {
                return Uri.parse(String.format("%s/%s", getBaseUri(), tableName));
            }
        };

        private Utils() {
        }

        /**
         * Returns handler for the application's main looper.
         *
         * @return  The Handler
         */
        @NonNull
        @SuppressWarnings("unused")
        public static Handler getHandlerMainLooper() {
            return sHandler;
        }

        /**
         * Causes the runnable to be added to the message queue.
         *
         * @param runnable
         *        The Runnable that will be executed
         */
        public static void postToMainLoop(@NonNull final Runnable runnable) {
            sHandler.post(prepareRunnable(runnable));
        }

        /**
         * Causes the runnable to be added to the message queue.
         *
         * @param delay
         *        The delay (in milliseconds) until the Runnable will be executed
         *
         * @param runnable
         *        The Runnable that will be executed
         */
        @SuppressWarnings("unused")
        public static void postToMainLoop(final long delay, @NonNull final Runnable runnable) {
            sHandler.postDelayed(prepareRunnable(runnable), delay);
        }

        private static Runnable prepareRunnable(@NonNull final Runnable runnable) {
            return new Runnable() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    }
                    catch (Exception e) {
                        CoreLogger.log(Level.WARNING, "failed", e);
                    }
                }
            };
        }

        /**
         * Returns {@code true} if the current thread is the main thread of the application, {@code false} otherwise.
         *
         * @return  The main thread flag
         */
        public static boolean isCurrentThreadMain() {
            return Thread.currentThread() == sHandler.getLooper().getThread();
        }

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "UnusedReturnValue"})
        public static Thread runInBackground(@NonNull final Runnable runnable) {
            return runInBackground(false, runnable);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Thread runInBackground(@SuppressWarnings("SameParameterValue") final boolean forceNewThread,
                                             @NonNull final Runnable runnable) {
            if (forceNewThread || isCurrentThreadMain()) {
                CoreLogger.log("about to run in new thread, forceNewThread " + forceNewThread);
                final Thread thread = new Thread(prepareRunnable(runnable));

                thread.start();
                return thread;
            }

            CoreLogger.log("about to run in current thread");

            prepareRunnable(runnable).run();
            return null;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static SharedPreferences getPreferences(@NonNull final ContextWrapper contextWrapper) {
            return contextWrapper.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static int getRequestCode(RequestCodes requestCode) {
            return requestCode.ordinal();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static RequestCodes getRequestCode(int requestCode) {
            return REQUEST_CODES_VALUES[requestCode];
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getLoaderTableName(@NonNull final Uri uri) {
            return uri.getPathSegments().get(0);
        }

        /**
         * Finds URI for the given table.
         *
         * @param tableName
         *        The table name
         *
         * @return  The URI
         */
        public static Uri getUri(@NonNull final String tableName) {
            return getUriResolver().getUri(tableName);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        public static String getBaseUri() {
            return Init.sBaseUri;
        }

        /**
         * Returns the URI resolver component.
         *
         * @return  The UriResolver
         */
        @NonNull
        public static UriResolver getUriResolver() {
            return sUriResolver;
        }

        /**
         * Sets the URI resolver component.
         *
         * @param uriResolver
         *        The URI resolver component
         */
        @SuppressWarnings("unused")
        public static void setUriResolver(@NonNull final UriResolver uriResolver) {
            sUriResolver = uriResolver;
        }

        /**
         * Returns {@code true} if network connected, {@code false} otherwise.
         *
         * @return  The network status flag
         */
        public static boolean isConnected() {
            return Init.sConnected.get();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        public static Orientation getOrientation(@NonNull final Context context) {
            // landscape allowed on tablets, so use resources, no constants
            final Resources resources = context.getResources();

            if (!resources.getBoolean(akha.yakhont.R.bool.yakhont_landscape))
                return Orientation.PORTRAIT;
            else
                return resources.getBoolean(akha.yakhont.R.bool.yakhont_portrait) ? Orientation.UNSPECIFIED: Orientation.LANDSCAPE;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getDialogInterfaceString(final int which) {
            String meaning = "unknown";
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    meaning = "DialogInterface.BUTTON_POSITIVE";
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    meaning = "DialogInterface.BUTTON_NEGATIVE";
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    meaning = "DialogInterface.BUTTON_NEUTRAL";
                    break;
            }
            return meaning;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getActivityResultString(final int result) {
            String meaning = "unknown";
            switch (result) {
                case Activity.RESULT_OK:
                    meaning = "Activity.RESULT_OK";
                    break;
                case Activity.RESULT_CANCELED:
                    meaning = "Activity.RESULT_CANCELED";
                    break;
                case Activity.RESULT_FIRST_USER:
                    meaning = "Activity.RESULT_FIRST_USER";
                    break;
            }
            return meaning;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        public static String getActivityName(@NonNull final Activity activity) {
            return activity.getLocalClassName();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getTag(@NonNull final Class c) {   // TODO: 09.12.2015 improve
            return String.format("%s-%s", "yakhont", c.getName());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String removeExtraSpaces(String str) {
            return str == null ? null: str.trim().replaceAll("\\s+", " ");
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String replaceSpecialChars(String str) {
            return str == null ? null: str.trim().replaceAll("[^A-Za-z0-9_]", "_");
        }

        private static       Boolean sDebug;
        private static final Object  sDebugLock = new Object();

        /**
         * Checks the debug mode.
         *
         * @param packageName
         *        The name of this application's package
         *
         * @return  {@code true} if the application is running in the debug mode, {@code false} otherwise
         */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public static boolean isDebugMode(@NonNull final String packageName) {
            synchronized (sDebugLock) {
                if (sDebug == null) {
                    boolean debug = false;
                    try {
                        debug = (Boolean) CoreReflection.getField(Class.forName(packageName + ".BuildConfig"), "DEBUG");
                    }
                    catch (ClassNotFoundException e) {
                        CoreLogger.log(Level.INFO, "failed", e);
                    }
                    //noinspection UnnecessaryBoxing
                    sDebug = Boolean.valueOf(debug);
                }
                return sDebug;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <E> Set<E> newWeakSet() {         // temp solution
            return Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<E, Boolean>()));
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <K, V> Map<K, V> newWeakMap() {   // temp solution
            return Collections.synchronizedMap(new WeakHashMap<K, V>());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <E> Set<E> newSet() {             // temp solution
            return Collections.synchronizedSet(new LinkedHashSet<E>());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <K, V> Map<K, V> newMap() {       // temp solution
            return Collections.synchronizedMap(new LinkedHashMap<K, V>());
        }
    }
}
