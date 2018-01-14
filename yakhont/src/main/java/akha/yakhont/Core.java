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

import akha.yakhont.CoreLogger.Level;
import akha.yakhont.callback.BaseCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.HideKeyboardCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.OrientationCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ValidateActivityCallbacks;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.technology.Dagger2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.AnyRes;
import android.support.annotation.IdRes;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The base class for the library. Usage example in Activity:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * &#064;Override
 * protected void onCreate(Bundle savedInstanceState) {
 *     Core.init(getApplication());
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
 *     Core.init(this);
 * }
 * </pre>
 *
 * @see #init(Application)
 * @see #init(Application, boolean, boolean, boolean)
 * @see #init(Application, Boolean, Dagger2)
 *
 * @author akha
 */
public class Core {

    /** Not valid resource ID (the value is {@value}). */
    @AnyRes public static final int                     NOT_VALID_RES_ID            = 0;

    /** Not valid View ID (the value is {@value}). */
    @IdRes public static final int                      NOT_VALID_VIEW_ID           = View.NO_ID;

    private static final String                         BASE_URI                    = "content://%s.provider";
    @SuppressWarnings("unused")
    private static final String                         LOG_TAG_FORMAT              = "%s-v.%d-%d-%s";

    private static final String                         PREFERENCES_NAME            = "BasePreferences";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @IntRange(from = 1) public  static final int        TIMEOUT_CONNECTION          = 20;   // seconds
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @IntRange(from = 0) public  static final int        TIMEOUT_CONNECTION_TIMER    =  3;

    @IntRange(from = 0) private static final int        TIMEOUT_NETWORK_MONITOR     =  3;   // seconds

    // use my birthday as the offset... why not?
    private static final int                            REQUEST_CODES_OFFSET        = 19631201;
    private static final short                          REQUEST_CODES_OFFSET_SHORT  = 11263;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public enum RequestCodes {
        UNKNOWN,
        LOCATION_CHECK_SETTINGS,
        LOCATION_CONNECTION_FAILED,
        LOCATION_ALERT,
        LOCATION_CLIENT,
        LOCATION_INTENT,
        PROGRESS_ALERT,
        PERMISSIONS_ALERT,
        PERMISSIONS_RATIONALE_ALERT,
        PERMISSIONS_DENIED_ALERT
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
    private static       boolean                        sSupport;
    private static       WeakReference<Application>     sApplication;
    private static       Dagger2                        sDagger;

    /**
     *  The dialog API that are common to the whole library.
     *  Every custom dialog implementation which is intended to replace the default one should implement that interface.
     *  See {@link #init(Application, Boolean, Dagger2)} and {@link Dagger2} for more details concerning custom dialogs.
     */
    public interface BaseDialog {

        /**
         * Starts dialog.
         *
         * @param context
         *        The Activity
         *
         * @param text
         *        The text to display
         *
         * @param data
         *        The additional data to send to {@link Activity#onActivityResult Activity.onActivityResult()}
         *
         * @return  {@code true} if dialog was started successfully, {@code false} otherwise
         */
        boolean start(Activity context, String text, Intent data);

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
         * Starts an asynchronous load (e.g. {@code getApi().data().enqueue(callback)}).
         *
         * @param callback
         *        The callback
         *
         * @yakhont.see BaseResponseLoaderWrapper#CoreLoad CoreLoad
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
     * Initializes the library.
     *
     * @param application
     *        The Application
     *
     * @return  {@code true} if library initialization was successful, {@code false} otherwise (library is already activated)
     */
    @SuppressWarnings("unused")
    public static boolean init(@SuppressWarnings("SameParameterValue") @NonNull final Application application) {
        return init(application, null, null);
    }

    /**
     * Initializes the library.
     *
     * @param application
     *        The Application
     *
     * @param useGoogleLocationOldApi
     *        {@code true} for {@link com.google.android.gms.common.api.GoogleApiClient}-based Google Location API,
     *        {@code false} for {@link com.google.android.gms.location.FusedLocationProviderClient}-based one
     *
     * @param useSnackbarIsoAlert
     *        {@code true} for using {@link Snackbar} instead of dialog alert
     *
     * @param useSnackbarIsoToast
     *        {@code true} for using {@link Snackbar} instead of {@link Toast}
     *
     * @return  {@code true} if library initialization was successful, {@code false} otherwise (library is already activated)
     */
    @SuppressWarnings("unused")
    public static boolean init(@SuppressWarnings("SameParameterValue") @NonNull final Application application            ,
                               @SuppressWarnings("SameParameterValue")          final boolean     useGoogleLocationOldApi,
                               @SuppressWarnings("SameParameterValue")          final boolean     useSnackbarIsoAlert    ,
                               @SuppressWarnings("SameParameterValue")          final boolean     useSnackbarIsoToast) {
        return init(application, null, getDefaultDagger(useGoogleLocationOldApi, useSnackbarIsoAlert, useSnackbarIsoToast));
    }

    /**
     * Initializes the library. Usage example:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.callback.BaseCallbacks.Validator;
     * import akha.yakhont.fragment.dialog.CommonDialogFragment;
     * import akha.yakhont.location.LocationCallbacks.LocationClient;
     * import akha.yakhont.technology.Dagger2;
     *
     * import dagger.BindsInstance;
     * import dagger.Component;
     * import dagger.Module;
     * import dagger.Provides;
     *
     * public class MyActivity extends Activity {
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *         Core.init(getApplication(), null, DaggerMyActivity_MyDagger
     *             .builder()
     *             .parameters(Dagger2.Parameters.create())
     *             .build()
     *         );
     *
     *         super.onCreate(savedInstanceState);
     *         ...
     *     }
     *
     *     // custom progress dialog theme example
     *
     *     &#064;Component(modules = {MyLocationModule.class, MyUiModule.class,
     *                                MyCallbacksValidationModule.class})
     *     interface MyDagger extends Dagger2 {
     *
     *         &#064;Component.Builder
     *         interface Builder {
     *             &#064;BindsInstance
     *             Builder parameters(Dagger2.Parameters parameters);
     *             MyDagger build();
     *         }
     *     }
     *
     *     &#064;Module
     *     static class MyCallbacksValidationModule extends Dagger2.CallbacksValidationModule {
     *
     *         &#064;Provides
     *         Validator provideCallbacksValidator() {
     *             return getCallbacksValidator();
     *         }
     *     }
     *
     *     &#064;Module
     *     static class MyLocationModule extends Dagger2.LocationModule {
     *
     *         &#064;Provides
     *         LocationClient provideLocationClient(Dagger2.Parameters parameters) {
     *             // return null if you don't need location API
     *             return getLocationClient(getFlagLocation(parameters));
     *         }
     *     }
     *
     *     &#064;Module
     *     static class MyUiModule extends Dagger2.UiModule {
     *
     *         &#064;Override
     *         protected Core.BaseDialog getProgress() {
     *             return ((CommonDialogFragment) super.getProgress())
     *                 .setTheme(R.style.MyTheme);
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
     * @return  {@code true} if library initialization was successful, {@code false} otherwise (library is already activated)
     *
     * @see Dagger2
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean init(@SuppressWarnings("SameParameterValue") @NonNull final Application application,
                              @SuppressWarnings("SameParameterValue")           final Boolean     fullInfo,
                              @SuppressWarnings("SameParameterValue")           final Dagger2     dagger) {

        if (sInstance != null) {
            CoreLogger.logWarning("the Yakhont library initialization is already done");
            return false;
        }
        sInstance = new Core();

        sApplication = new WeakReference<>(application);

        initDagger(dagger);

        Init.logging(application,
                fullInfo == null ? Utils.isDebugMode(application.getPackageName()): fullInfo);
        Init.allRemaining(application);

        CoreLogger.log("orientation "   + Utils.getOrientation(application));
        CoreLogger.log("uri "           + Utils.getBaseUri());
        CoreLogger.log("support "       + sSupport);

        registerCallbacks(application);

        return true;
    }

    private static void initDagger(final Dagger2 dagger) {
        sDagger = dagger != null ? dagger: getDefaultDagger();

        BaseCallbacks.setValidator(sDagger.getCallbacksValidator());
    }

    private static Dagger2 getDefaultDagger(final boolean useGoogleLocationOldApi,
                                            final boolean useSnackbarIsoAlert,
                                            final boolean useSnackbarIsoToast) {
        return getDefaultDagger(Dagger2.Parameters.create(
                useGoogleLocationOldApi, useSnackbarIsoAlert, useSnackbarIsoToast));
    }

    private static Dagger2 getDefaultDagger() {
        return getDefaultDagger(Dagger2.Parameters.create());
    }

    private static Dagger2 getDefaultDagger(final Dagger2.Parameters parameters) {
        return akha.yakhont.technology.DaggerDagger2_DefaultComponent
                .builder()
                .parameters(parameters)
                .build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressLint("ObsoleteSdkInt")
    private static void registerCallbacks(@NonNull final Application application) {
        // don't remove
        SupportHelper.registerValidateFragmentCallbacks();
        // don't remove
        register(new ValidateActivityCallbacks());

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
    public static boolean register(final ConfigurationChangedListener listener) {
        return registerHelper(listener, true);
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
    public static boolean unregister(final ConfigurationChangedListener listener) {
        return registerHelper(listener, false);
    }

    private static boolean registerHelper(final ConfigurationChangedListener listener,
                                          final boolean add) {
        if (listener == null) {
            CoreLogger.logError("listener == null");
            return false;
        }
        final boolean result = add ?
                sAppCallbacksListeners.add(listener): sAppCallbacksListeners.remove(listener);

        CoreLogger.log(result ? Level.DEBUG: Level.WARNING,
                "result: " + result + ", listener: " + listener);
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
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
                        try {
                            listener.onChangedConfiguration(newConfig);
                        }
                        catch (Exception e) {
                            CoreLogger.log("onConfigurationChanged failed, listener: " + listener);
                        }
                    }
                });
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onLowMemory() {
            CoreLogger.log(Utils.getOnLowMemoryLevel(), "low memory");
        }

        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        private static class ApplicationCallbacks2 extends ApplicationCallbacks implements ComponentCallbacks2 {

            /**
             * Please refer to the base method description.
             */
            @Override
            public void onTrimMemory(int level) {
                CoreLogger.log(Utils.getOnTrimMemoryLevel(level),
                        "level " + Utils.getOnTrimMemoryLevelString(level));
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
                CoreLogger.log("notifyListener failed", e);
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

                private static final String PERMISSION = Manifest.permission.ACCESS_NETWORK_STATE;

                @Override
                public void run() {
                    if (!isVisible() || !isInForeground()) return;

                    final ConnectivityManager connectivityManager =
                            (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
                    if (connectivityManager == null) {
                        CoreLogger.logWarning(Context.CONNECTIVITY_SERVICE + ": connectivityManager == null");
                        return;
                    }

                    final Activity activity = Utils.getCurrentActivity();
                    if (activity == null) {
                        CoreLogger.logWarning("network monitoring: activity == null");
                        return;
                    }

                    final boolean result = new CorePermissions.RequestBuilder(activity, PERMISSION)
                            .setOnGranted(new Runnable() {
                                @Override
                                public void run() {
                                    @SuppressLint("MissingPermission") final NetworkInfo activeInfo =
                                            connectivityManager.getActiveNetworkInfo();

                                    boolean isConnected = false;
                                    if (activeInfo != null && activeInfo.isConnected()) isConnected = true;

                                    if (sConnected.getAndSet(isConnected) != isConnected) {
                                        CoreLogger.log((isConnected ? Level.INFO: Level.WARNING),
                                                "network is " + (isConnected ? "": "NOT ") + "available");
                                        onNetworkStatusChanged(isConnected);
                                    }
                                }
                            })
                            .request();
                    CoreLogger.log(PERMISSION + " request result: " + (result ? "already granted": "not granted yet"));
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

        /** To use with {@link #showToast(String, boolean)} etc. The value is {@value}. */
        @SuppressWarnings("unused")
        public static final  boolean                    SHOW_DURATION_LONG              = true;

        private static       UriResolver                sUriResolver                    = new UriResolver() {
            @Override
            public Uri getUri(@NonNull final String tableName) {
                return Uri.parse(String.format("%s/%s", getBaseUri(), tableName));
            }
        };

        private static final ThreadPostHelper           sThreadPostHelper               = new ThreadPostHelper   ();
        private static final RequestCodesHandler        sRequestCodesHandler            = new RequestCodesHandler();

        private Utils() {
        }

        /**
         * Returns the current {@code Application}.
         *
         * @return  The current {@code Application}
         */
        @SuppressWarnings("unused")
        public static Application getApplication() {
            // should never happen
            if      (sApplication       == null) CoreLogger.logError("sApplication == null");
            else if (sApplication.get() == null) CoreLogger.logError("sApplication.get() == null");

            return sApplication == null ? null: sApplication.get();
        }

        /**
         * Returns the current {@code Activity} (if any).
         *
         * @return  The current {@code Activity} (or null)
         */
        @SuppressWarnings("unused")
        public static Activity getCurrentActivity() {
            return BaseActivityLifecycleProceed.getCurrentActivity();
        }

        /**
         * Returns the default {@code View} of the given {@code Activity}.
         * The default View Id is stored in the resources ({@code yakhont_default_view_id})
         * and for the moment is {@link android.R.id#content android.R.id.content}.
         *
         * @param activity
         *        The Activity
         *
         * @return  The default View (or null)
         */
        @SuppressWarnings("unused")
        public static View getDefaultView(final Activity activity) {
            return ViewHelper.getView(activity);
        }

        /**
         * Shows {@link Toast}.
         *
         * @param text
         *        The text to show
         *
         * @param durationLong
         *        {@link #SHOW_DURATION_LONG} for using {@link Toast#LENGTH_LONG},
         *        !{@link #SHOW_DURATION_LONG} for {@link Toast#LENGTH_SHORT}
         */
        @SuppressWarnings("unused")
        public static void showToast(final String text, final boolean durationLong) {
            Dagger2.UiModule.showToast(text, durationLong);
        }

        /**
         * Shows {@link Toast}.
         *
         * @param resId
         *        The resource ID of the string resource to show
         *
         * @param durationLong
         *        {@link #SHOW_DURATION_LONG} for using {@link Toast#LENGTH_LONG},
         *        !{@link #SHOW_DURATION_LONG} for {@link Toast#LENGTH_SHORT}
         */
        @SuppressWarnings("unused")
        public static void showToast(@StringRes final int resId, final boolean durationLong) {
            Dagger2.UiModule.showToast(resId, durationLong);
        }

        /**
         * Shows {@link Snackbar} using default {@code View} of the current {@code Activity}.
         *
         * @param text
         *        The text to show
         *
         * @param durationLong
         *        {@link #SHOW_DURATION_LONG} for using {@link Snackbar#LENGTH_LONG},
         *        !{@link #SHOW_DURATION_LONG} for {@link Snackbar#LENGTH_SHORT}
         */
        @SuppressWarnings("unused")
        public static void showSnackbar(final String text, final boolean durationLong) {
            Dagger2.UiModule.showSnackbar(text, durationLong);
        }

        /**
         * Shows {@link Snackbar} using default {@code View} of the current {@code Activity}.
         *
         * @param resId
         *        The resource ID of the string resource to show
         *
         * @param durationLong
         *        {@link #SHOW_DURATION_LONG} for using {@link Snackbar#LENGTH_LONG},
         *        !{@link #SHOW_DURATION_LONG} for {@link Snackbar#LENGTH_SHORT}
         */
        @SuppressWarnings("unused")
        public static void showSnackbar(@StringRes final int resId, final boolean durationLong) {
            Dagger2.UiModule.showSnackbar(resId, durationLong);
        }

        /**
         * Sets the default View ID (e.g. to show {@link Snackbar}).
         *
         * @param resId
         *        The resource ID of the View (should be common for all Activities)
         */
        @SuppressWarnings("unused")
        public static void setDefaultViewId(@IdRes final int resId) {
            ViewHelper.sDefViewId = resId;
        }

        /**
         * Returns handler for the application's main thread.
         *
         * @return  The Handler
         */
        @NonNull
        @SuppressWarnings("unused")
        public static Handler getHandlerMainThread() {
            return sThreadPostHelper.mHandler;
        }

        /**
         * Causes the runnable to be added to the message queue.
         *
         * @param runnable
         *        The Runnable that will be executed
         */
        public static void postToMainLoop(@NonNull final Runnable runnable) {
            sThreadPostHelper.postToMainLoop(runnable);
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
            sThreadPostHelper.postToMainLoop(delay, runnable);
        }

        /**
         * Returns {@code true} if the current thread is the main thread of the application, {@code false} otherwise.
         *
         * @return  The main thread flag
         */
        public static boolean isCurrentThreadMain() {
            return sThreadPostHelper.isCurrentThreadMain();
        }

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "UnusedReturnValue"})
        public static Thread runInBackground(@NonNull final Runnable runnable) {
            return sThreadPostHelper.runInBackground(runnable);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedReturnValue"})
        public static Thread runInBackground(@SuppressWarnings("SameParameterValue") final boolean forceNewThread,
                                             @NonNull final Runnable runnable) {
            return sThreadPostHelper.runInBackground(forceNewThread, runnable);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static SharedPreferences getPreferences(@NonNull final ContextWrapper contextWrapper) {
            return contextWrapper.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public static int getInvertedColor(final int color) {
            return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color));
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static int getRequestCode(@NonNull final RequestCodes requestCode) {
            return sRequestCodesHandler.getRequestCode(requestCode);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "SameParameterValue"})
        public static int getRequestCode(@NonNull final RequestCodes requestCode,
                                         @NonNull final Activity     activity) {
            return sRequestCodesHandler.getRequestCode(requestCode, activity);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static RequestCodes getRequestCode(int requestCode) {
            return sRequestCodesHandler.getRequestCode(requestCode);
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
                return resources.getBoolean(akha.yakhont.R.bool.yakhont_portrait) ?
                        Orientation.UNSPECIFIED: Orientation.LANDSCAPE;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getDialogInterfaceString(final int which) {
            switch (which) {
                case        DialogInterface.BUTTON_POSITIVE                   :
                    return "DialogInterface.BUTTON_POSITIVE"                  ;
                case        DialogInterface.BUTTON_NEGATIVE                   :
                    return "DialogInterface.BUTTON_NEGATIVE"                  ;
                case        DialogInterface.BUTTON_NEUTRAL                    :
                    return "DialogInterface.BUTTON_NEUTRAL"                   ;
                default                                                       :
                    return "unknown DialogInterface result: " + which         ;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getActivityResultString(final int result) {
            switch (result) {
                case        Activity.RESULT_OK                                :
                    return "Activity.RESULT_OK"                               ;
                case        Activity.RESULT_CANCELED                          :
                    return "Activity.RESULT_CANCELED"                         ;
                case        Activity.RESULT_FIRST_USER                        :
                    return "Activity.RESULT_FIRST_USER"                       ;
                default                                                       :
                    return "unknown Activity result: " + result               ;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getOnTrimMemoryLevelString(final int level) {
            switch (level) {
                case        ComponentCallbacks2.TRIM_MEMORY_COMPLETE          :
                    return "ComponentCallbacks2.TRIM_MEMORY_COMPLETE"         ;
                case        ComponentCallbacks2.TRIM_MEMORY_MODERATE          :
                    return "ComponentCallbacks2.TRIM_MEMORY_MODERATE"         ;
                case        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND        :
                    return "ComponentCallbacks2.TRIM_MEMORY_BACKGROUND"       ;
                case        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN         :
                    return "ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN"        ;
                case        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL  :
                    return "ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL" ;
                case        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW       :
                    return "ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW"      ;
                case        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE  :
                    return "ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE" ;
                default                                                       :
                    return "unknown OnTrimMemory() level: " + level           ;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Level getOnTrimMemoryLevel(final int level) {
            switch (level) {
                case ComponentCallbacks2.TRIM_MEMORY_COMPLETE                 :
                    return Level.ERROR                                        ;
                case ComponentCallbacks2.TRIM_MEMORY_MODERATE                 :
                    return Level.WARNING                                      ;
                case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND               :
                case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN                :
                    return Level.INFO                                         ;

                case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL         :
                    return Level.ERROR                                        ;
                case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW              :
                    return Level.WARNING                                      ;
                case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE         :
                    return Level.INFO                                         ;

                // unknown level
                default                                                       :
                    return Level.ERROR                                        ;
            }
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "SameReturnValue"})
        public static Level getOnLowMemoryLevel() {
            return Level.WARNING                                              ;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void onActivityResult(@NonNull final String prefix, @NonNull final Activity activity,
                                            final int requestCode, final int resultCode, final Intent data) {
            CoreLogger.log((prefix.isEmpty() ? "": prefix + ".") + "onActivityResult" +
                    (prefix.isEmpty() ? "": ": subject to call by weaver"));
            CoreLogger.log("activity   : " + getActivityName(activity));
            CoreLogger.log("requestCode: " + requestCode + " " + getRequestCode(requestCode).name());
            CoreLogger.log("resultCode : " + resultCode  + " " + getActivityResultString(resultCode));
            CoreLogger.log("intent     : " + data);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void onActivityResult(@NonNull final Activity activity,
                                            final int requestCode, final int resultCode, final Intent data) {
            onActivityResult("", activity, requestCode, resultCode, data);
/*
        if (activity instanceof BaseActivity) {
            ((BaseActivity) activity).onActivityResult(requestCode, resultCode, data);
            return;
        }
*/
            CoreReflection.invokeSafe(activity, "onActivityResult", requestCode, resultCode, data);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        public static String getActivityName(@NonNull final Activity activity) {
            return activity.getLocalClassName();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getTag(@NonNull final Class c) {
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

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getTmpFileSuffix() {
            return "_" + replaceSpecialChars(DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.LONG, Locale.getDefault())
                    .format(new Date(System.currentTimeMillis())));
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static File getTmpDir(@NonNull final Context context) {
            File dir;
            if (checkDir(dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)))                    return dir;
            if (checkDir(dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)))  return dir;
            if (checkDir(dir = Environment.getExternalStorageDirectory()))                                      return dir;
            if (checkDir(dir = context.getExternalCacheDir()))                                                  return dir;

            CoreLogger.logError("can not find tmp directory");
            return null;
        }

        private static boolean checkDir(final File dir) {
            CoreLogger.log("check directory: " + dir);
            return dir != null && dir.isDirectory() && dir.canWrite();
        }

        /**
         * Creates ZIP file.
         *
         * @param srcFiles
         *        The source files
         *
         * @param zipFile
         *        The destination ZIP file
         *
         * @return  {@code true} if ZIP file was created successfully, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        public static boolean zip(final String[] srcFiles, final String zipFile) {
            return zip(srcFiles, zipFile, null);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static boolean zip(final String[] srcFiles, final String zipFile,
                                  final Map<String, Exception> errors) {
            if (zipFile == null || srcFiles == null || srcFiles.length == 0) {
                CoreLogger.logError("no arguments");
                return false;
            }
            try {
                final ZipOutputStream outputStream = new ZipOutputStream(
                        new BufferedOutputStream(new FileOutputStream(zipFile)));
                final byte[] buffer = new byte[2048];

                for (final String srcFile: srcFiles)
                    try {
                        final BufferedInputStream inputStream = new BufferedInputStream(
                                new FileInputStream(srcFile), buffer.length);
                        final int pos = srcFile.lastIndexOf(File.separator);
                        final ZipEntry entry = new ZipEntry(pos < 0 ? srcFile: srcFile.substring(pos + 1));
                        outputStream.putNextEntry(entry);

                        int length;
                        while ((length = inputStream.read(buffer)) != -1)
                            outputStream.write(buffer, 0, length);
                        inputStream.close();
                    }
                    catch (Exception e) {
                        handleError("failed creating ZIP entry " + srcFile, e, errors);
                    }

                outputStream.close();
                return true;
            }
            catch (Exception e) {
                handleError("failed creating ZIP " + zipFile, e, errors);
                return false;
            }
        }

        private static void handleError(final String text, final Exception exception,
                                        final Map<String, Exception> map) {
            CoreLogger.log(text, exception);
            if (map != null) //noinspection ThrowableResultOfMethodCallIgnored
                map.put(text, exception);
        }

        /**
         * Sends email.
         *
         * @param activity
         *        The Activity
         *
         * @param addresses
         *        The email's addresses
         *
         * @param subject
         *        The email's subject
         *
         * @param text
         *        The email's text
         *
         * @param attachment
         *        The email's attachment (or null)
         *
         */
        public static void sendEmail(final Activity activity, final String[] addresses,
                                     final String subject, final String text, final File attachment) {
            if (activity == null || addresses == null || addresses.length == 0 ||
                    subject == null || text == null) {
                CoreLogger.logError("no arguments");
                return;
            }
            runInBackground(new Runnable() {
                @Override
                public void run() {
                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");

                    intent.putExtra(Intent.EXTRA_EMAIL,     addresses);
                    intent.putExtra(Intent.EXTRA_SUBJECT,   subject);
                    intent.putExtra(Intent.EXTRA_TEXT,      text);

                    if (attachment != null)
                        intent.putExtra(Intent.EXTRA_STREAM,
                                Uri.parse( "file://" + attachment.getAbsolutePath()));

                    activity.startActivity(Intent.createChooser(intent, "Sending email..."));
                }
            });
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
                    final Boolean debug = (Boolean) getBuildConfigField(packageName, "DEBUG");
                    sDebug = debug != null ? debug: false;
                }
                return sDebug;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Object getBuildConfigField(@NonNull final String packageName,
                                                 @NonNull final String fieldName) {
            try {
                return CoreReflection.getField(Class.forName(packageName + ".BuildConfig"), fieldName);
            }
            catch (ClassNotFoundException e) {
                CoreLogger.log(Level.INFO, "getBuildConfigField failed", e);
                return null;
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

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public static <E> List<E> newList() {           // temp solution
            return new CopyOnWriteArrayList<>();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <E> Set<E> newSet() {             // temp solution
            // order should be kept
            return Collections.synchronizedSet(new LinkedHashSet<E>());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <K, V> Map<K, V> newMap() {       // temp solution
            // order should be kept
            return Collections.synchronizedMap(new LinkedHashMap<K, V>());
        }

        private static class RequestCodesHandler {

            private static final RequestCodes[]         REQUEST_CODES_VALUES            = RequestCodes.values();

            private int getRequestCode(@NonNull final RequestCodes requestCode) {
                return getRequestCode(requestCode, REQUEST_CODES_OFFSET);
            }

            private int getRequestCode(@NonNull final RequestCodes requestCode, final int offset) {
                return requestCode.ordinal() + offset;
            }

            private Exception checkRequestCode(final int requestCode, final Activity activity, final Method method) {
                try {
                    CoreReflection.invoke(activity, method, requestCode);
                    return null;
                }
                catch (Exception exception) {
                    CoreLogger.log(Level.WARNING, "checkRequestCode failed", exception);
                    return exception;
                }
            }

            private int getRequestCode(@NonNull final RequestCodes requestCode, @NonNull final Activity activity) {
                int result = getRequestCode(requestCode);

                final Method method = CoreReflection.findMethod(activity,
                        "validateRequestPermissionsRequestCode", int.class);
                if (method                                     == null) return result;
                //noinspection ThrowableResultOfMethodCallIgnored
                if (checkRequestCode(result, activity, method) == null) return result;

                result = getRequestCode(requestCode, REQUEST_CODES_OFFSET_SHORT);
                final Exception exception = checkRequestCode(result, activity, method);
                if (exception                                  == null) return result;

                CoreLogger.log("getRequestCode failed", exception);
                return result;
            }

            private RequestCodes getRequestCode(int requestCode) {
                final RequestCodes code = getRequestCode(requestCode, REQUEST_CODES_OFFSET);
                return !code.equals(RequestCodes.UNKNOWN) ? code:
                        getRequestCode(requestCode, REQUEST_CODES_OFFSET_SHORT);
            }

            private RequestCodes getRequestCode(int requestCode, final int offset) {
                requestCode -= offset;
                return requestCode < 0 || requestCode >= REQUEST_CODES_VALUES.length ?
                        RequestCodes.UNKNOWN: REQUEST_CODES_VALUES[requestCode];
            }
        }

        private static class ThreadPostHelper {

            private final Handler                       mHandler                        = new Handler(Looper.getMainLooper());

            private void postToMainLoop(@NonNull final Runnable runnable) {
                mHandler.post(prepareRunnable(runnable));
            }

            private void postToMainLoop(final long delay, @NonNull final Runnable runnable) {
                mHandler.postDelayed(prepareRunnable(runnable), delay);
            }

            private Runnable prepareRunnable(@NonNull final Runnable runnable) {
                return new Runnable() {
                    @Override
                    public void run() {
                        try {
                            runnable.run();
                        }
                        catch (Exception e) {
                            CoreLogger.log(Level.WARNING, "prepareRunnable failed", e);
                        }
                    }
                };
            }

            private boolean isCurrentThreadMain() {
                return Thread.currentThread() == mHandler.getLooper().getThread();
            }

            private Thread runInBackground(@NonNull final Runnable runnable) {
                return runInBackground(false, runnable);
            }

            private Thread runInBackground(@SuppressWarnings("SameParameterValue") final boolean forceNewThread,
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
        }

        /**
         * Helper class for handling the Back key in ActionMode. For example (in Activity):
         * <p>
         * <pre style="background-color: silver; border: thin solid black;">
         * // in BaseActivity it's already done
         * private final Core.Utils.BackKeyInActionModeHandler mBackKeyHandler =
         *     new Core.Utils.BackKeyInActionModeHandler();
         *
         * private ActionMode.Callback mCallback = new ActionMode.Callback() {
         *
         *     &#064;Override
         *     public boolean onCreateActionMode(ActionMode mode, Menu menu) {
         *         // in BaseActivity - just call method 'checkBackKeyAndReset()'
         *         mBackKeyHandler.checkBackKeyAndReset();
         *         return true;
         *     }
         *
         *     &#064;Override
         *     public void onDestroyActionMode(ActionMode mode) {
         *         // in BaseActivity - just call method 'checkBackKeyAndReset()'
         *         if (mBackKeyHandler.checkBackKeyAndReset())
         *             // handle Back key (discard changes and exit ActionMode)
         *         else
         *             // save changes and exit ActionMode
         *     }
         *
         *     // 'onPrepareActionMode(...)' and 'onActionItemClicked(...)'
         *     // are skipped for simplification
         * };
         *
         * // in BaseActivity it's already done
         * &#064;Override
         * public boolean dispatchKeyEvent(&#064;NonNull KeyEvent event) {
         *     mBackKeyHandler.handleKeyEvent(event);
         *
         *     return super.dispatchKeyEvent(event);
         * }
         * </pre>
         */
        @SuppressWarnings("unused")
        public static class BackKeyInActionModeHandler {

            private final AtomicBoolean                 mIsBackWasPressed               = new AtomicBoolean();

            /**
             * Handles the Back key events.
             *
             * @param event
             *        The KeyEvent to handle
             */
            public void handleKeyEvent(final KeyEvent event) {
                mIsBackWasPressed.set(event.getKeyCode() == KeyEvent.KEYCODE_BACK);
            }

            /**
             * Checks which key was pressed to exit ActionMode.
             *
             * @return  {@code true} if the Back key was pressed, {@code false} otherwise
             */
            public boolean checkBackKeyAndReset() {
                return mIsBackWasPressed.getAndSet(false);
            }
        }

        /**
         *  The API for measured view layout adjusting.
         *
         * @yakhont.see BaseFragment#onAdjustMeasuredView(Core.Utils.MeasuredViewAdjuster,View) BaseFragment.onAdjustMeasuredView(MeasuredViewAdjuster, View)
         */
        public interface MeasuredViewAdjuster {

            /**
             * Allows the view layout adjusting keeping in mind the measured dimensions
             * (height, width) of the view.
             *
             * @param view
             *        The view to handle
             */
            void adjustMeasuredView(View view);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static class ViewHelper {

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public interface ViewVisitor {
                boolean handle(View view);
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static final boolean                 VIEW_FOUND                      = true;

            @IdRes
            private static final int                    DEF_VIEW_REF_ID                 =
                    akha.yakhont.R.id.yakhont_default_view_id;
            @IdRes
            private static final int                    DEF_VIEW_ID                     =
                    android.R.id.content;
            @IdRes
            private static int                          sDefViewId                      = DEF_VIEW_ID;

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static boolean visitView(@NonNull final View        parentView,
                                            @NonNull final ViewVisitor visitor) {
                if (visitor.handle(parentView))
                    return true;

                if (parentView instanceof ViewGroup) {
                    final ViewGroup viewGroup = (ViewGroup) parentView;
                    for (int i = 0; i < viewGroup.getChildCount(); i++)
                        if (visitView(viewGroup.getChildAt(i), visitor)) return true;
                }
                return false;
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static View findView(@NonNull final View        parentView,
                                        @NonNull final ViewVisitor visitor) {
                final View[] viewHelper = new View[1];

                visitView(parentView, new ViewVisitor() {
                    @Override
                    public boolean handle(final View view) {
                        final boolean found = visitor.handle(view);
                        if (found && viewHelper[0] == null) viewHelper[0] = view;
                        return found;
                    }
                });
                CoreLogger.log(viewHelper[0] == null ? Level.ERROR: Level.DEBUG,
                        "result of find view: " + viewHelper[0]);

                return viewHelper[0];
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static View getView(final Activity activity) {
                return getView(activity, NOT_VALID_VIEW_ID);
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static View getView(final Activity activity, @IdRes final int viewId) {
                if (activity == null) {
                    CoreLogger.logError("activity == null");
                    return null;
                }
                if (viewId != NOT_VALID_VIEW_ID) {
                    final View view = activity.findViewById(viewId);
                    if (view == null)
                        CoreLogger.logError("can not find view with ID " + viewId);
                    return view;
                }

                final Resources resources = activity.getResources();
                @IdRes final int defaultViewId = getDefaultViewId(resources);
                CoreLogger.log("defaultViewId " + defaultViewId +
                    " 0x" + Integer.toHexString(defaultViewId));

                final String defaultViewName = resources.getResourceName(defaultViewId);
                CoreLogger.log("default view is " + defaultViewName);

                View view = activity.findViewById(defaultViewId);
                if (view == null) {
                    CoreLogger.logWarning(defaultViewName + " not found, getWindow().getDecorView() will be used");
                    CoreLogger.logWarning("Note that calling this function \"locks in\" various " +
                            "characteristics of the window that can not, from this point forward, be changed");

                    final Window window = activity.getWindow();
                    if (window == null) CoreLogger.logError("window == null");

                    view = window == null ? null: window.getDecorView();
                }
                if (view == null)
                    CoreLogger.logError("can not find View for Activity " + activity);

                return view;
            }

            @IdRes
            private static int getDefaultViewId(final Resources resources) {
                if (sDefViewId != DEF_VIEW_ID) return sDefViewId;

                // it seems it doesn't work anymore (looks like a bug in API)
                final TypedValue typedValue = new TypedValue();
                resources.getValue(DEF_VIEW_REF_ID, typedValue, true);

                return typedValue.resourceId == DEF_VIEW_REF_ID ||
                       typedValue.resourceId == NOT_VALID_RES_ID ? sDefViewId: typedValue.resourceId;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static class TypeHelper {

            public static Type getParameterizedType(final Type type) {
                return type instanceof ParameterizedType ?
                        ((ParameterizedType) type).getActualTypeArguments()[0]: type;
            }

            public static Type getGenericComponentType(final Type type) {
                return type instanceof GenericArrayType ?
                        ((GenericArrayType) type).getGenericComponentType(): type;
            }

            public static Type getParameterizedOrGenericComponentType(final Type type) {
                final Type genericArrayType = getGenericComponentType(type);
                return type != null && !type.equals(genericArrayType) ? genericArrayType: getParameterizedType(type);
            }

            public static boolean isCollection(final Type type) {
                final Type typeRaw = getParameterizedRawType(type);
                CoreLogger.log("typeRaw: " + typeRaw);
                return typeRaw instanceof Class && Collection.class.isAssignableFrom((Class) typeRaw);
            }

            public static boolean checkType(@NonNull final Type typeResponse,
                                            @NonNull final Type typeMethod) {
                CoreLogger.log("typeResponse: " + typeResponse);
                CoreLogger.log("typeMethod  : " + typeMethod  );

                if (typeResponse instanceof Class) {
                    final Class classResponse = (Class) typeResponse;
                    final boolean result = typeResponse.equals(typeMethod) ||
                            (classResponse.isArray() && typeMethod instanceof GenericArrayType
                                    && classResponse.getComponentType().equals(getGenericComponentType(typeMethod)));
                    if (result) return true;
                }

                if (!checkParameterizedRawTypes(typeResponse, typeMethod))      return false;
                if (!isCollection(typeMethod))                                  return false;

                final Type type = getParameterizedType(typeResponse);
                CoreLogger.log("type to check: " + type);

                return type != null && type.equals(getParameterizedType(typeMethod));
            }

            private static boolean checkParameterizedRawTypes(final Type typeResponse, final Type typeMethod) {
                final Type type = getParameterizedRawType(typeResponse);
                return type != null && type.equals(getParameterizedRawType(typeMethod));
            }

            private static Type getParameterizedRawType(final Type type) {
                return type instanceof ParameterizedType ? ((ParameterizedType) type).getRawType(): null;
            }
        }
    }
}
