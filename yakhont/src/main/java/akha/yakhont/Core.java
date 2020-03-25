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

import akha.yakhont.Core.Utils.DataStore;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.adapter.BaseCacheAdapter;
import akha.yakhont.adapter.BaseCacheAdapter.BaseCacheAdapterWrapper;
import akha.yakhont.adapter.BaseCacheAdapter.CacheAdapter;
import akha.yakhont.callback.BaseCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.HideKeyboardCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.OrientationCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ValidateActivityCallbacks;
import akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed.ValidateFragmentCallbacks;
import akha.yakhont.debug.BaseFragment;
import akha.yakhont.loader.BaseLiveData;
import akha.yakhont.loader.BaseLiveData.CacheLiveData;
import akha.yakhont.loader.BaseLiveData.LiveDataDialog;
import akha.yakhont.loader.BaseResponse;
import akha.yakhont.loader.BaseResponse.Source;
import akha.yakhont.loader.BaseViewModel;
import akha.yakhont.loader.BaseViewModel.PagingViewModel;
import akha.yakhont.loader.wrapper.BaseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.LoaderCallback;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.LoaderCallbacks;
import akha.yakhont.location.LocationCallbacks;
import akha.yakhont.technology.Dagger2;
import akha.yakhont.technology.Dagger2.Parameters;
import akha.yakhont.technology.Dagger2.UiModule;
import akha.yakhont.technology.Dagger2.UiModule.ViewHandler;
import akha.yakhont.technology.Dagger2.UiModule.ViewModifier;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.Rx3;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Dialog;
import android.app.Service;
import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;     // for javadoc
import android.net.Network;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Lifecycle.Event;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.paging.DataSource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.material.snackbar.Snackbar;

/**
 * The base class for the Yakhont library. Normally initialized automatically, via Yakhont Weaver,
 * but you can do it explicitly (please refer to {@link Dagger2} for more info):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * &#064;Override
 * protected void onCreate(Bundle savedInstanceState) {
 *
 *     Core.init(getApplication());
 *
 *     super.onCreate(savedInstanceState);
 *
 *     // your code here: setContentView(...) etc.
 * }
 * </pre>
 *
 * And the same in Application:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * &#064;Override
 * public void onCreate() {
 *     super.onCreate();
 *
 *     Core.init(this);
 * }
 * </pre>
 *
 * @see #init(Application, boolean, boolean)
 * @see #init(Application, Boolean, Dagger2)
 *
 * @author akha
 */
@SuppressWarnings("JavadocReference")
public class Core implements DefaultLifecycleObserver {

    /** Not valid resource ID (the value is {@value}). */
    @AnyRes
    public static final int                             NOT_VALID_RES_ID            = 0;

    /** Not valid View ID (the value is {@value}). */
    @IdRes
    public static final int                             NOT_VALID_VIEW_ID           = View.NO_ID;

    /** Not valid color (the value is {@value}). */
    @IdRes  public static final int                     NOT_VALID_COLOR             = Integer.MAX_VALUE;

    private static final String                         BASE_URI                    = "content://%s.provider";
    @SuppressWarnings("unused")
    private static final String                         LOG_TAG_FORMAT              = "v.%s-%d-%s";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public  static final int                            TIMEOUT_CONNECTION          =  20;  // seconds
    // should be consistent with setRunNetworkMonitorInterval description
    private static final int                            TIMEOUT_NETWORK_MONITOR     = 300;  // 5 minutes

    private static final int                            ADJUST_TIMEOUT_THRESHOLD    = 300;

    // use my birthday as the unique offset... why not?
    private static final int                            REQUEST_CODES_OFFSET        = 19631201;
    private static final short                          REQUEST_CODES_OFFSET_SHORT  = 11263;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public enum RequestCodes {
        UNKNOWN,
        LOCATION_CHECK_SETTINGS,
        LOCATION_CONNECTION_FAILED,
        LOCATION_CLIENT,
        LOCATION_INTENT,
        PERMISSIONS_ALERT,
        PERMISSIONS_RATIONALE_ALERT,
        PERMISSIONS_DENIED_ALERT,
        LOGGER_VIDEO,
        LOGGER_VIDEO_SYSTEM,
        LOGGER_VIDEO_AUDIO_SYSTEM
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
    private static       boolean                        sbyUser, sSupport, sSetOrientation, sHideKeyboard, sOldConnection;

    private              Init                           mInit;
    private              WeakReference<Application>     mApplication;
    private              Dagger2                        mDagger;

    private        final AtomicBoolean                  mResumed                    = new AtomicBoolean();
    private        final AtomicBoolean                  mStarted                    = new AtomicBoolean();

    private        final DataStore                      mStore                      = new DataStore();

    // 10 minutes (as in WorkManager)
    private              Long                           mAwaitDefaultTimeout        = 10 * 60 * 1000L;

    /**
     *  The data loading dialog API.
     */
    public interface BaseDialog {

        /**
         * Starts data loading dialog.
         *
         * @param text
         *        The text to display
         *
         * @param data
         *        The additional data to send to {@link Activity#onActivityResult}
         *
         * @return  {@code true} if dialog was started successfully, {@code false} otherwise
         */
        boolean start(String text, Intent data);

        /**
         * Stops data load dialog.
         */
        @SuppressWarnings("UnusedReturnValue")
        void stop();

        /**
         * Confirms data load canceling.
         *
         * @param context
         *        The {@link Activity}
         *
         * @param view
         *        The {@link Dialog}'s view (or null if you're not going to use {@link Snackbar})
         *
         * @return  {@code true} if confirmation supported, {@code false} otherwise
         */
        boolean confirm(Activity context, View view);

        /**
         * Sets data load canceling handler.
         *
         * @param runnable
         *        The data load canceling handler
         *
         * @return  {@code true} if data load canceling supported, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean setOnCancel(Runnable runnable);

        /**
         * Invokes data load canceling handler (if supported and set).
         *
         * @return  {@code true} if data load canceling supported and set, {@code false} otherwise
         *
         * @see #setOnCancel
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean cancel();
    }

    /**
     * The callback API which allows registered components to be notified about device configuration
     * changes.
     *
     * @see   #register(ConfigurationChangedListener)
     * @see #unregister(ConfigurationChangedListener)
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
        return sInstance.mDagger;
    }

    /**
     * Sets configuration for the Yakhont library; provide null for default (or already set) values.
     *
     * @param supportMode
     *        Forces working in support mode (by using weaving for calling application callbacks
     *        instead of registering via
     *        {@link Application#registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks)}
     *        and {@link Application#registerComponentCallbacks(ComponentCallbacks)}).
     *        Mostly for debug purposes.
     *
     * @param setOrientation
     *        Switches ON / OFF the screen orientation callback (please refer to {@link OrientationCallbacks})
     *
     * @param hideKeyboard
     *        Switches ON / OFF the virtual keyboard callback (please refer to {@link HideKeyboardCallbacks})
     *
     * @param oldConnectionCheck
     *        {@code true} to force using {@link ConnectivityManager#getActiveNetworkInfo()}
     *        for checking network connection (by default Yakhont uses {@link NetworkCallback})
     */
    @SuppressWarnings("unused")
    public static void config(final Boolean supportMode, final Boolean setOrientation, final Boolean hideKeyboard,
                              final Boolean oldConnectionCheck) {
        if (supportMode         != null) sSupport            = supportMode;
        if (setOrientation      != null) sSetOrientation     = setOrientation;
        if (hideKeyboard        != null) sHideKeyboard       = hideKeyboard;
        if (oldConnectionCheck  != null) sOldConnection      = oldConnectionCheck;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Initializes the Yakhont library.
     *
     * @param application
     *        The Application
     *
     * @param useGoogleLocationOldApi
     *        {@code true} for {@link GoogleApiClient}-based Google Location API,
     *        {@code false} for {@link FusedLocationProviderClient}-based one
     *
     * @param useSnackbarIsoToast
     *        {@code true} for using {@link Snackbar} instead of {@link Toast}
     *
     * @return  {@code false} if library was already initialized before, {@code true} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean init(@SuppressWarnings("SameParameterValue") @NonNull final Application application            ,
                               @SuppressWarnings("SameParameterValue")          final boolean     useGoogleLocationOldApi,
                               @SuppressWarnings("SameParameterValue")          final boolean     useSnackbarIsoToast) {
        return init(application, null, getDefaultDagger(useGoogleLocationOldApi, useSnackbarIsoToast));
    }

    /**
     * Initializes the Yakhont library. Usage example:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.callback.BaseCallbacks.Validator;
     * import akha.yakhont.location.LocationCallbacks.LocationClient;
     * import akha.yakhont.technology.Dagger2;
     * import akha.yakhont.technology.Dagger2.CallbacksValidationModule;
     * import akha.yakhont.technology.Dagger2.LocationModule;
     * import akha.yakhont.technology.Dagger2.Parameters;
     * import akha.yakhont.technology.Dagger2.UiModule;
     *
     * import dagger.BindsInstance;
     * import dagger.Component;
     * import dagger.Module;
     *
     * public class YourActivity extends Activity {
     *
     *     &#064;Override
     *     protected void onCreate(Bundle savedInstanceState) {
     *
     *         Core.init(getApplication(), null, DaggerYourActivity_YourDagger
     *             .builder()
     *             .parameters(Parameters.create())
     *             .build()
     *         );
     *
     *         super.onCreate(savedInstanceState);
     *
     *         // your code here: setContentView(...) etc.
     *     }
     *
     *     // default implementation - customize only modules you need
     * //  &#064;Component(modules = {CallbacksValidationModule.class, LocationModule.class, UiModule.class})
     *
     *     &#064;Component(modules = {YourLocationModule.class, YourCallbacksValidationModule.class,
     *                                YourUiModule.class})
     *     interface YourDagger extends Dagger2 {
     *
     *         &#064;Component.Builder
     *         interface Builder {
     *             &#064;BindsInstance
     *             Builder parameters(Parameters parameters);
     *             YourDagger build();
     *         }
     *     }
     *
     *     // customize Yakhont callbacks validation here
     *     &#064;Module
     *     static class YourCallbacksValidationModule extends CallbacksValidationModule {
     *
     *         &#064;Override
     *         protected Validator getCallbacksValidator() {
     *             return super.getCallbacksValidator();
     *         }
     *     }
     *
     *     // customize Yakhont location client here
     *     &#064;Module
     *     static class YourLocationModule extends LocationModule {
     *
     *         &#064;Override
     *         protected LocationClient getLocationClient(boolean oldApi) {
     *             return super.getLocationClient(oldApi);
     *         }
     *     }
     *
     *     // customize Yakhont GUI here
     *     &#064;Module
     *     static class YourUiModule extends UiModule {
     *
     *         &#064;Override
     *         protected BaseDialog getPermissionAlert(Integer requestCode, Integer duration) {
     *             return super.getPermissionAlert(requestCode, duration);
     *         }
     *
     *         &#064;Override
     *         protected BaseDialog getToast(boolean useSnackbarIsoToast, Integer duration) {
     *             return super.getToast(useSnackbarIsoToast, duration);
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
     * @return  {@code false} if library was already initialized before, {@code true} otherwise
     *
     * @see Dagger2
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean init(@NonNull final Application application,
                               final Boolean fullInfo, final Dagger2 dagger) {
        return init(application, fullInfo, dagger, true);
    }

    private static boolean init(@NonNull final Application application,
                                final Boolean fullInfo, final Dagger2 dagger, final boolean byUser) {
        if (fullInfo == null && dagger == null && byUser) {
            CoreLogger.logError("the Yakhont library initialization was already done");
            return false;
        }

        if (sInstance != null && sbyUser)
            CoreLogger.logWarning("the default Yakhont library initialization will be updated");

        final boolean firstInit = sInstance == null;
        if (firstInit) sInstance = new Core();

        sbyUser = byUser;

        if (firstInit) sInstance.mApplication = new WeakReference<>(application);

        initDagger(dagger, byUser);

        sInstance.mInit = sInstance.new Init();
        sInstance.mInit.logging(application, fullInfo != null ? fullInfo:
                Utils.isDebugMode(application.getPackageName()), false);
        sInstance.mInit.allRemaining(application);

        CoreLogger.log("uri "         + Utils.getBaseUri());
        CoreLogger.log("support "     + sSupport);

        sInstance.mInit.registerCallbacks(application, firstInit);

        CoreLogger.removeTmpFiles();

        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // initDefault(...) methods are subjects to call by the Yakhont Weaver

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
    public static void initDefault(@NonNull final Service service) {
        initDefault(service.getApplication());
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
    public static void initDefault(@NonNull final Activity activity) {
        if (sInstance == null)
            initDefault(activity.getApplication());
        else
            cleanUpComponents();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static void initDefault(@NonNull final Application application) {
        if (sInstance == null) init(application, null, null, false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Adjusts logging.
     *
     * @param fullInfo
     *        Indicates whether the detailed logging should be enabled or not
     */
    @SuppressWarnings("unused")
    public static void setFullLoggingInfo(final boolean fullInfo) {
        sInstance.mInit.logging(sInstance.mApplication.get(), fullInfo, true);
    }

    /**
     * Sets the Rx components behaviour in case of uncaught exceptions. For more info please refer to
     * {@link <a href="https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling">Rx error handling</a>}.
     *
     * @param terminate
     *        {@code true} to terminate application in case of uncaught Rx exception, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static void setRxUncaughtExceptionBehavior(final boolean terminate) {
        if (terminate) {
            FlavorHelper.setRxErrorHandlerDefault();    // Rx / Rx2 support is in full version
            Rx3         .setErrorHandlerDefault();
        }
        else {
            FlavorHelper.setRxErrorHandlerJustLog();    // Rx / Rx2 support is in full version
            Rx3         .setErrorHandlerJustLog();
        }
        CommonRx.setSafeFlag(!terminate);
    }

    private static void initDagger(final Dagger2 dagger, final boolean byUser) {
        if (!byUser && sInstance.mDagger != null && dagger == null) return;
        sInstance.mDagger = dagger != null ? dagger: getDefaultDagger();

        BaseCallbacks.setValidator(sInstance.mDagger.getCallbacksValidator());
    }

    private static Dagger2 getDefaultDagger(final boolean useGoogleLocationOldApi,
                                            final boolean useSnackbarIsoToast) {
        return getDefaultDagger(Parameters.create(useGoogleLocationOldApi, useSnackbarIsoToast));
    }

    private static Dagger2 getDefaultDagger() {
        return getDefaultDagger(Parameters.create());
    }

    private static Dagger2 getDefaultDagger(final Parameters parameters) {
        return akha.yakhont.technology.DaggerDagger2_DefaultComponent
                .builder()
                .parameters(parameters)
                .build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Checks whether the application is visible or not.
     *
     * @return  {@code true} if {@link ProcessLifecycleOwner} dispatched {@link Event#ON_START} event and
     *          {@code false} in case of {@link Event#ON_STOP} one
     *
     * @see ProcessLifecycleOwner
     */
    @SuppressWarnings({"WeakerAccess", "BooleanMethodIsAlwaysInverted"})
    public static boolean isVisible() {
        try {
            return sInstance.mStarted.get();    // BaseActivityLifecycleProceed.isVisible();
        }
        catch (/*NullPointer*/Exception exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
        }
        return false;
    }

    /**
     * Checks whether the application is in foreground or not.
     *
     * @return  {@code true} if {@link ProcessLifecycleOwner} dispatched {@link Event#ON_RESUME} event and
     *          {@code false} in case of {@link Event#ON_PAUSE} one
     *
     * @see ProcessLifecycleOwner
     */
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "WeakerAccess"})
    public static boolean isInForeground() {
        try {
            return sInstance.mResumed.get();    // BaseActivityLifecycleProceed.isInForeground();
        }
        catch (/*NullPointer*/Exception exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
        }
        return false;
    }

    /**
     * Please refer to the base method description.
     *
     * @see ProcessLifecycleOwner
     */
    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        logLifecycle("onCreate");
    }

    /**
     * Please refer to the base method description.
     *
     * @see ProcessLifecycleOwner
     */
    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        try {
            sInstance.mStarted.set(true);
        }
        catch (/*NullPointer*/Exception exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
        }
        logLifecycle("onStart");
    }

    /**
     * Please refer to the base method description.
     *
     * @see ProcessLifecycleOwner
     */
    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        try {
            sInstance.mResumed.set(true);
        }
        catch (/*NullPointer*/Exception exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
        }
        logLifecycle("onResume");
    }

    /**
     * Please refer to the base method description.
     *
     * @see ProcessLifecycleOwner
     */
    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        try {
            sInstance.mResumed.set(false);
        }
        catch (/*NullPointer*/Exception exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
        }
        logLifecycle("onPause");
    }

    /**
     * Please refer to the base method description.
     *
     * @see ProcessLifecycleOwner
     */
    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        try {
            sInstance.mStarted.set(false);
        }
        catch (/*NullPointer*/Exception exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
        }
        logLifecycle("onStop");
    }

    private void logLifecycle(final String info) {
        CoreLogger.log(CoreLogger.getDefaultLevel(), info +
                ", application " + CoreLogger.getDescription(Utils.getApplication()), false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private                Runnable                     mCleanUpCallback;
    private final          AtomicInteger                mServicesCounter            = new AtomicInteger();
    private final          AtomicBoolean                mNoAutoCleanUp              = new AtomicBoolean();

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
    public static void considerService(final boolean increment) {
        if (increment)
            sInstance.mServicesCounter.incrementAndGet();
        else
            sInstance.mServicesCounter.decrementAndGet();
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "unused"})
    public static void cleanUpDefault(final int timeout, final Activity activity) {
        if (!sInstance.mNoAutoCleanUp.get()) cleanUpFinal(timeout, activity);
    }

    /**
     * Sets auto Yakhont cleanup flag.
     *
     * @param value
     *        The value to set ({@code true} for no auto cleanup, {@code false} otherwise)
     */
    @SuppressWarnings("unused")
    public static void setNoAutoCleanUp(final boolean value) {
        sInstance.mNoAutoCleanUp.set(value);
    }

    /**
     * Sets user-defined callback to call from {@link #cleanUpFinal(int, Activity) cleanUp}.
     *
     * @param value
     *        The callback
     */
    @SuppressWarnings("unused")
    public static void setCleanUpCallback(final Runnable value) {
        sInstance.mCleanUpCallback = value;
    }

    /**
     * Makes Yakhont cleanup (on exit from last {@link Activity} or last {@link Service}).
     * Normally called automatically via Yakhont Weaver (please refer to weaver.config for more info)
     * - but it could be switched off (via call to {@link #setNoAutoCleanUp}), and then you can do it
     * by yourself (say, from {@link Activity#onDestroy onDestroy} method in your root {@link Activity}).
     *
     * @param timeout
     *        The cleanup timeout (in ms); provide 0 to start immediately
     *
     * @param activity
     *        The activity (null for current one or {@link Service})
     *
     * @see #cleanUpFinal()
     */
    @SuppressWarnings("unused")
    public static void cleanUpFinal(final int timeout, final Activity activity) {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final Activity activityCurrent = Utils.getCurrentActivity();
                if (activityCurrent != null && activityCurrent != activity) return;

                int servicesCounter = 0;
                try {
                    servicesCounter = sInstance.mServicesCounter.get();
                }
                catch (/*NullPointer*/Exception exception) {
                    CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
                }

                if (servicesCounter > 0 || !isLastActivity(activityCurrent)) return;

                CoreLogger.logWarning("about to cleanup Yakhont, activity: " +
                        CoreLogger.getDescription(activityCurrent));
                try {
                    if (sInstance.mCleanUpCallback != null) Utils.safeRun(sInstance.mCleanUpCallback);
                }
                catch (/*NullPointer*/Exception exception) {
                    CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
                }

                cleanUpFinal();
            }

            @NonNull
            @Override
            public String toString() {
                return "cleanUp";
            }
        };

        if (timeout <= 0)
            Utils.runInBackground(runnable);
        else
            Utils.runInBackground(timeout, runnable);
    }

    /**
     * Makes Yakhont cleanup; called from {@link #cleanUpFinal(int, Activity)}.
     */
    @SuppressWarnings("WeakerAccess")
    public static void cleanUpFinal() {
        if (sInstance == null) return;

        enableFragmentManagerDebugLogging(false);

        final Core instance = sInstance;
        Utils.postToMainLoop(new Runnable() {
            @Override
            public void run() {
                getLifecycle().removeObserver(instance);
            }

            @NonNull
            @Override
            public String toString() {
                return "getLifecycle().removeObserver()";
            }
        });

        sInstance.mInit.unregisterConnectionHandler();
        sInstance.mInit.unregisterCallbacksSupport ();

        sInstance.mAppCallbacksListeners .clear();
        sInstance.mNetworkStatusListeners.clear();

        sbyUser         = false;
        sSupport        = false;
        sSetOrientation = false;
        sHideKeyboard   = false;
        sOldConnection  = false;

        Utils.init();
        Utils.ViewHelper.init();

        cleanUpComponentsFinal();

        sInstance = null;
    }

    private static void cleanUpComponentsFinal() {
        BaseCacheProvider.cleanUpFinal();
        BaseCacheAdapter .cleanUpFinal();

        BaseFragmentLifecycleProceed.cleanUpFinal();
        BaseActivityLifecycleProceed.cleanUpFinal();
        BaseCallbacks               .cleanUpFinal();

        BaseResponse.cleanUpFinal();

        BaseLiveData                       .cleanUpFinal();
        LiveDataDialog.ProgressDefault     .cleanUpFinal();
        LiveDataDialog                     .cleanUpFinal();
        BaseViewModel.BaseViewModelProvider.cleanUpFinal();

        BaseLoaderWrapper.LoadParameters    .cleanUpFinal();
        BaseResponseLoaderWrapper.CoreLoader.cleanUpFinal();

        LocationCallbacks.cleanUpFinal();

        Parameters.cleanUpFinal();
        UiModule  .cleanUpFinal();

        Rx3         .cleanUpFinal  ();
        FlavorHelper.cleanUpRxFinal();
        CommonRx    .cleanUpFinal  ();

        CoreLogger.GestureHandler.cleanUpFinal();
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) CoreLogger.VideoRecorder.cleanUpFinal();
        CoreLogger.cleanUpFinal();
    }

    /**
     * Makes Yakhont cleanup on switching Activities; called from {@code initDefault} (see weaver.config).
     */
    @SuppressWarnings("WeakerAccess")
    public static void cleanUpComponents() {
        UiModule  .cleanUp();
        CoreLogger.cleanUp();
    }

    /**
     * Return whether this activity is the root of a task (and there is only one task in the process).
     * The root is the first activity in a task.
     *
     * @param activity
     *        The activity (or null for current one)
     *
     * @return  {@code true} if given Activity is the root (and there is only one task), {@code false} otherwise
     */
    @SuppressWarnings({"WeakerAccess", "BooleanMethodIsAlwaysInverted"})
    public static boolean isLastActivity(Activity activity) {
        if (activity == null) activity = Utils.getCurrentActivity();
        if (activity == null) return true;

        if (!activity.isTaskRoot()) return false;
        try {
            final ActivityManager activityManager =
                    (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                final int taskQty;
                if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
                    taskQty = getTaskQtyNew(activityManager);
                else
                    taskQty = getTaskQtyOld(activityManager);
                return taskQty <= 1;
            }
        }
        catch (Exception exception) {
            CoreLogger.log(exception);
        }
        return true;
    }

    @TargetApi  (      Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static int getTaskQtyNew(final ActivityManager activityManager) {
        return activityManager.getAppTasks().size();
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private static int getTaskQtyOld(final ActivityManager activityManager) {
        return activityManager.getRunningTasks(2).size();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private                ApplicationCallbacks         mAppCallbacks;

    private final          Set<ConfigurationChangedListener>
                                                        mAppCallbacksListeners      = Utils.newSet();

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
     * Removes a {@code ConfigurationChangedListener} component that was previously registered
     * with {@link #register(ConfigurationChangedListener)}.
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
        final boolean result = add ? sInstance.mAppCallbacksListeners.add   (listener):
                                     sInstance.mAppCallbacksListeners.remove(listener);

        CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.WARNING,
                "result: " + result + ", listener: " + listener);
        return result;
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void onApplicationConfigurationChanged(final Configuration newConfig) {
        if (sInstance.mAppCallbacks == null) return;
        sInstance.mAppCallbacks.onConfigurationChanged(newConfig);
    }

    @TargetApi  (      Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static class ApplicationCallbacks2 extends ApplicationCallbacks implements ComponentCallbacks2 {

        @Override
        public void onTrimMemory(int level) {
            CoreLogger.log(Utils.getOnTrimMemoryLevel(level),
                    "level " + Utils.getOnTrimMemoryLevelString(level));
        }
    }

    private static class ApplicationCallbacks extends BaseListeners implements ComponentCallbacks {

        @Override
        public void onConfigurationChanged(@NonNull final Configuration newConfig) {
            try {
                onConfigurationChangedHelper(newConfig);
            }
            catch (Exception exception) {
                CoreLogger.log(exception instanceof NullPointerException
                        ? CoreLogger.getDefaultLevel(): Level.ERROR, exception);
            }
        }

        private void onConfigurationChangedHelper(@NonNull final Configuration newConfig) {
            CoreLogger.logWarning("newConfig " + newConfig);

            for (final ConfigurationChangedListener listener: sInstance.mAppCallbacksListeners)
                notifyListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.onChangedConfiguration(newConfig);
                        }
                        catch (Exception exception) {
                            CoreLogger.log("onConfigurationChanged failed, listener: " +
                                    listener, exception);
                        }
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "ConfigurationChangedListener.onChangedConfiguration()";
                    }
                });
        }

        @Override
        public void onLowMemory() {
            CoreLogger.log(Utils.getOnLowMemoryLevel(), "low memory");
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static class BaseListeners {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void notifyListener(@NonNull final Runnable runnable) {
            Utils.safeRun(runnable);
        }
    }

    private static Lifecycle getLifecycle() {
        return ProcessLifecycleOwner.get().getLifecycle();
    }

    private static void enableFragmentManagerDebugLogging(final boolean value) {
        BaseFragment.enableFragmentManagerDebugLogging(value);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings({"unused", "InnerClassMayBeStatic"})
    private class Init extends BaseListeners {

        private              String                     mBaseUri;

        private              boolean                    mLogLevelSet;
        private        final Object                     mLogLevelSetLock                = new Object();

        private        final AtomicBoolean              mConnected                      = new AtomicBoolean(true);
        private        final Object                     mConnectedLock                  = new Object();
        private        final AtomicBoolean              mRunNetworkMonitor              = new AtomicBoolean(true);
        private        final AtomicInteger              mConnectionCheckInterval        = new AtomicInteger(TIMEOUT_NETWORK_MONITOR);

        private              ConnectivityManager.NetworkCallback
                                                        mNetworkCallback;

        private              ActivityLifecycleProceed   mActivityLifecycleProceed;
        private              ApplicationCallbacks2      mApplicationCallbacks2;

        private              HideKeyboardCallbacks      mHideKeyboardCallbacks;
        private              OrientationCallbacks       mOrientationCallbacks;

        public void registerCallbacks(@NonNull final Application application, final boolean firstInit) {
            if (firstInit) registerCallbacks(application);

            if (sHideKeyboard) {
                if (mHideKeyboardCallbacks == null) {
                    mHideKeyboardCallbacks  = new HideKeyboardCallbacks();
                    register((BaseActivityCallbacks) mHideKeyboardCallbacks.setForceProceed(true));
                }
            }
            else {
                if (mHideKeyboardCallbacks != null) {
                    BaseActivityLifecycleProceed.unregister(mHideKeyboardCallbacks);
                    mHideKeyboardCallbacks  = null;
                }
            }
            if (sSetOrientation) {
                if (mOrientationCallbacks == null) {
                    mOrientationCallbacks  = new OrientationCallbacks();
                    register((BaseActivityCallbacks) mOrientationCallbacks.setForceProceed(true));
                }
            }
            else {
                if (mOrientationCallbacks != null) {
                    BaseActivityLifecycleProceed.unregister(mOrientationCallbacks);
                    mOrientationCallbacks  = null;
                }
            }

            registerCallbacksSupport(application);
        }

        private void registerCallbacks(@NonNull final Application application) {
            // don't remove validation
            BaseFragmentLifecycleProceed.register(new ValidateFragmentCallbacks(), true);
            BaseActivityLifecycleProceed.register(new ValidateActivityCallbacks(), true);

            register(new LocationCallbacks());

            Utils.postToMainLoop(new Runnable() {
                @Override
                public void run() {
                    getLifecycle().addObserver(sInstance);
                }

                @NonNull
                @Override
                public String toString() {
                    return "getLifecycle().addObserver()";
                }
            });
        }

        @SuppressWarnings({"UnusedReturnValue", "unused"})
        private boolean register(@NonNull final BaseActivityCallbacks callbacks) {
            return BaseActivityLifecycleProceed.register(callbacks);
        }

        @SuppressLint("ObsoleteSdkInt")
        private void registerCallbacksSupport(@NonNull final Application application) {
            final boolean iceCream = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
            if (iceCream && !sSupport) {
                if (mActivityLifecycleProceed == null) {
                    mActivityLifecycleProceed  = new ActivityLifecycleProceed();
                    application.registerActivityLifecycleCallbacks(mActivityLifecycleProceed);
                }
                if (mApplicationCallbacks2    == null) {
                    mApplicationCallbacks2     = new ApplicationCallbacks2();
                    application.registerComponentCallbacks(mApplicationCallbacks2);
                }
                BaseActivityLifecycleProceed.setActive(false);
            }
            else {
                if (iceCream) {
                    if (mActivityLifecycleProceed != null) {
                        application.unregisterActivityLifecycleCallbacks(mActivityLifecycleProceed);
                        mActivityLifecycleProceed  = null;
                    }
                    if (mApplicationCallbacks2    != null) {
                        application.unregisterComponentCallbacks        (mApplicationCallbacks2);
                        mApplicationCallbacks2     = null;
                    }
                }
                BaseActivityLifecycleProceed.setActive(true);
                if (sInstance.mAppCallbacks == null) sInstance.mAppCallbacks = new ApplicationCallbacks();
            }
        }

        private void unregisterCallbacksSupport() {
            final Application application = Utils.getApplication();
            if (mActivityLifecycleProceed != null)
                Objects.requireNonNull(application).unregisterActivityLifecycleCallbacks(mActivityLifecycleProceed);
            if (mApplicationCallbacks2    != null)
                Objects.requireNonNull(application).unregisterComponentCallbacks        (mApplicationCallbacks2);
        }

        private void logging(@NonNull final Application application, final boolean fullInfo,
                             final boolean forceSet) {
            final String pkgName = application.getPackageName();

            if (!Utils.isDebugMode(pkgName)) {
                String version = "N/A";
                try {
                    final PackageInfo packageInfo = application.getPackageManager().getPackageInfo(
                            pkgName, 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        version = String.valueOf(packageInfo.getLongVersionCode());
                    else
                        version = String.valueOf(getVersionOld(packageInfo));
                }
                catch (/*PackageManager.NameNotFound*/Exception exception) {
                    CoreLogger.log("can not define version code", exception);
                }

                CoreLogger.setTag(String.format(Utils.getLocale(), LOG_TAG_FORMAT, version,
                        akha.yakhont.BuildConfig.VERSION_CODE, akha.yakhont.BuildConfig.FLAVOR));
            }

            synchronized (mLogLevelSetLock) {
                if (forceSet || !mLogLevelSet) {
                    CoreLogger.setLogLevel(fullInfo ? CoreLogger.getDefaultLevel(): Level.ERROR);
                    mLogLevelSet = true;
                }
            }
            enableFragmentManagerDebugLogging(fullInfo);
        }

        @SuppressWarnings({"deprecation", "RedundantSuppression" /* lint bug workaround */ })
        private int getVersionOld(@NonNull final PackageInfo packageInfo) {
            return packageInfo.versionCode;
        }

        @SuppressLint("MissingPermission")
        @TargetApi  (      Build.VERSION_CODES.LOLLIPOP)
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private void handleConnectionNew(final ConnectivityManager connectivityManager,
                                         final boolean start) {
            if (start) {
                if (mNetworkCallback != null) {
                    CoreLogger.logWarning("already registered " + mNetworkCallback);
                    return;
                }
                mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull final Network network) {
                        CoreLogger.log("NetworkCallback.onAvailable()");
                        setConnected(true);
                    }

                    @Override
                    public void onBlockedStatusChanged(@NonNull final Network network,
                                                       final boolean blocked) {
                        CoreLogger.log(blocked ? Level.WARNING: CoreLogger.getDefaultLevel(),
                                "NetworkCallback.onBlockedStatusChanged() " + blocked);
                        setConnected(!blocked);
                    }

                    @Override
                    public void onLosing(@NonNull final Network network, final int maxMsToLive) {
                        CoreLogger.log(Level.WARNING, "NetworkCallback.onLosing()");
                        setConnected(false);
                    }

                    @Override
                    public void onLost(@NonNull final Network network) {
                        CoreLogger.log(Level.WARNING, "NetworkCallback.onLost()");
                        setConnected(false);
                    }

                    @Override
                    public void onUnavailable() {
                        CoreLogger.log(Level.WARNING, "NetworkCallback.onUnavailable()");
                        setConnected(false);
                    }
                };
                connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), mNetworkCallback);
            }
            else
                unregisterNew(connectivityManager);
        }

        @TargetApi  (      Build.VERSION_CODES.LOLLIPOP)
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private void unregisterNew(final ConnectivityManager connectivityManager) {
            try {
                if (mNetworkCallback == null) {
                    CoreLogger.logWarning("already unregistered");
                    return;
                }
                connectivityManager.unregisterNetworkCallback(mNetworkCallback);
                mNetworkCallback = null;
            }
            catch (Exception exception) {
                CoreLogger.log(exception);
            }
        }

        private void unregisterConnectionHandler() {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                final ConnectivityManager connectivityManager = getConnectivityManager(
                        Objects.requireNonNull(Utils.getApplication()));
                if (connectivityManager != null) unregisterNew(connectivityManager);
            }
            unregisterOld();
        }

        private void setConnected(final boolean isConnected) {
            synchronized (mConnectedLock) {
                if (mConnected.getAndSet(isConnected) != isConnected) {
                    CoreLogger.log((isConnected ? Level.INFO: Level.WARNING),
                            "network is " + (isConnected ? "": "NOT ") + "available");
                    onNetworkStatusChanged(isConnected);
                }
            }
        }

        private void handleConnectionOld(final ConnectivityManager connectivityManager,
                                         final boolean start) {
            if (start) {
                if (Utils.sExecutorHelperConnection != null) {
                    CoreLogger.logWarning("network monitor already running");
                    return;
                }
                final long period = adjustTimeout(mConnectionCheckInterval.get());

                Utils.sExecutorHelperConnection = new Utils.ExecutorHelper();
                if (Utils.sExecutorHelperConnection.runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        if (isVisible() && isInForeground()) handleConnectionOld();
                    }

                    @SuppressWarnings({"deprecation", "RedundantSuppression" /* lint bug workaround */})
                    private void handleConnectionOld() {
                        @SuppressLint("MissingPermission")
                        final android.net.NetworkInfo activeInfo = connectivityManager.getActiveNetworkInfo();
                        setConnected(activeInfo != null && activeInfo.isConnected());
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "timer for network monitoring, period (ms) is " + period;
                    }
                }, 0, period, false) == null)   // should never happen
                    CoreLogger.logError("can't run network monitoring");
            }
            else
                unregisterOld();
        }

        private void unregisterOld() {
            if (Utils.sExecutorHelperConnection == null) {
                CoreLogger.logWarning("network monitor already stopped");
                return;
            }
            CoreLogger.log("about to cancel network monitoring");
            Utils.sExecutorHelperConnection.cancel();
            Utils.sExecutorHelperConnection = null;
        }

        private ConnectivityManager getConnectivityManager(@NonNull final Application application) {
            final ConnectivityManager connectivityManager =
                    (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null)
                CoreLogger.logWarning(Context.CONNECTIVITY_SERVICE + ": connectivityManager == null");
            return connectivityManager;
        }

        private void handleConnection(@NonNull final Application application, Boolean start,
                                      final boolean forceOld) {
            final ConnectivityManager connectivityManager = getConnectivityManager(application);
            if (connectivityManager == null) return;

            if (start == null) start = mRunNetworkMonitor.get();

            CoreLogger.log(start ? CoreLogger.getDefaultLevel(): Level.WARNING,
                    "run network monitor == " + start);

            if (!forceOld && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                handleConnectionNew(connectivityManager, start);
            else
                handleConnectionOld(connectivityManager, start);
        }

        @SuppressWarnings("UnusedReturnValue")
        private void allRemaining(@NonNull final Application application) {
            mBaseUri = String.format(BASE_URI, application.getPackageName());
            handleConnection(application, null, sOldConnection);
        }

        private void onNetworkStatusChanged(final boolean isConnected) {
            try {
                for (final NetworkStatusListener listener: sInstance.mNetworkStatusListeners)
                    notifyListener(new Runnable() {
                        @Override
                        public void run() {
                            listener.onNetworkStatusChanged(isConnected);
                        }

                        @NonNull
                        @Override
                        public String toString() {
                            return "NetworkStatusListener.onNetworkStatusChanged()";
                        }
                    });
            }
            catch (/*NullPointer*/Exception exception) {
                CoreLogger.log(CoreLogger.getDefaultLevel(), exception);
            }
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static void setRunNetworkMonitor(final boolean runNetworkMonitor) {
        if (sInstance.mInit.mRunNetworkMonitor.compareAndSet(!runNetworkMonitor, runNetworkMonitor))
            sInstance.mInit.handleConnection(sInstance.mApplication.get(), null, false);
    }

    /**
     * Sets the network monitor update interval and forces to use network polling on devices with
     * API level >= {@link VERSION_CODES#LOLLIPOP} (normally such devices uses
     * {@link <a href="https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback">ConnectivityManager.NetworkCallback</a>}.
     *
     * @param interval
     *        The update interval in seconds (<= 5 min) or milliseconds (> 5 min). The default value is 5 min.
     *
     * @return  The previous interval
     */
    @SuppressWarnings("unused")
    public static int setRunNetworkMonitorInterval(final int interval) {
//      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return -1;
        synchronized (sInstance.mInit.mConnectedLock) {
            if (sInstance.mInit.mConnectionCheckInterval.get() == interval) return interval;
            if (interval <= 0) {
                CoreLogger.logError("wrong network monitor interval " + interval);
                return sInstance.mInit.mConnectionCheckInterval.get();
            }
            final Application application = sInstance.mApplication.get();

            sInstance.mInit.handleConnection(application, false, false);
            final int previous = sInstance.mInit.mConnectionCheckInterval.getAndSet(interval);
            sInstance.mInit.handleConnection(application, true, true);

            return previous;
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public interface NetworkStatusListener {
        void onNetworkStatusChanged(boolean isConnected);
    }

    private        final Set<NetworkStatusListener>     mNetworkStatusListeners         = Utils.newSet();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean register(@NonNull final NetworkStatusListener listener) {
        return sInstance.mNetworkStatusListeners.add(listener);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
    public static boolean unregister(@NonNull final NetworkStatusListener listener) {
        return sInstance.mNetworkStatusListeners.remove(listener);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static long adjustTimeout(Long timeout) {
        if (timeout == null || timeout <= 0) {
            if (timeout == null) CoreLogger.log("time interval == null, default value will be used");
            else                 CoreLogger.logError("wrong time interval " + timeout);
            timeout = (long) TIMEOUT_CONNECTION;
        }
        if (timeout <= ADJUST_TIMEOUT_THRESHOLD) timeout *= 1000;

        CoreLogger.log("accepted time interval (ms): " + timeout);
        return timeout;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static int adjustTimeout(final Integer timeout) {
        final Long tmp = timeout == null ? null: Long.valueOf(timeout);
        return (int) adjustTimeout(tmp);
    }

    /**
     * Sets some data (singleton) to keep in this application's store.
     *
     * @param key
     *        The key
     *
     * @param value
     *        The data
     *
     * @param <V>
     *        The type of data
     *
     * @return  The previous data for the given key (or null)
     *
     * @see #getSingleton
     */
    @SuppressWarnings("unused")
    public static <V> V setSingleton(final String key, final V value) {
        return sInstance.mStore.setData(key, value);
    }

    /**
     * Returns the data (associated with the given key singleton) kept in this application's store.
     *
     * @param key
     *        The key
     *
     * @param <V>
     *        The type of data
     *
     * @return  The data for the given key (or null)
     *
     * @see #setSingleton
     */
    @SuppressWarnings({"unchecked", "unused"})
    public static <V> V getSingleton(final String key) {
        return (V) sInstance.mStore.getData(key);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The Yakhont utilities class.
     */
    public static class Utils {

        private static       UriResolver                sUriResolver;

        private static       ExecutorHelper             sExecutorHelper;
        private static       ExecutorHelper             sExecutorHelperConnection;

        private Utils() {
        }

        static {
            sDebugLock                = new Object();
            init();
        }

        private static void init() {
            try {
                if (sExecutorHelper  != null) sExecutorHelper.cancel(true);
            }
            catch (Exception exception) {
                CoreLogger.log("Core.Utils init error", exception);
            }

            sExecutorHelper           = new ExecutorHelper();
            sExecutorHelperConnection = null;

            synchronized (sDebugLock) {
                sDebug                = null;
            }

            //noinspection Convert2Lambda
            sUriResolver              = new UriResolver() {
                @Override
                public Uri getUri(@NonNull final String tableName) {
                    return Uri.parse(String.format("%s/%s", getBaseUri(), tableName));
                }
            };
        }

        /**
         * Runs {@link Runnable#run} without throwing exceptions (if any).
         *
         * @param runnable
         *        The Runnable
         *
         * @return  {@code true} if the Runnable was successfully ran, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        public static boolean safeRun(final Runnable runnable) {
            if (runnable == null)
                CoreLogger.logWarning("runnable == null");
            else {
                try {
                    runnable.run();
                    return true;
                }
                catch (Exception exception) {
                    CoreLogger.log("failed runnable: " + runnable, exception);
                }
            }
            return false;
        }

        /**
         * Runs {@link Callable#call} without throwing exceptions (if any).
         *
         * @param callable
         *        The Callable
         *
         * @param <T>
         *        The result type of method {@link Callable#call}
         *
         * @return  The result of calling method {@link Callable#call} (or null)
         */
        public static <T> T safeRun(final Callable<T> callable) {
            if (callable == null)
                CoreLogger.logWarning("callable == null");
            else {
                try {
                    return callable.call();
                }
                catch (Exception exception) {
                    CoreLogger.log("failed callable: " + callable, exception);
                }
            }
            return null;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public static boolean safeRunBoolean(final Callable<Boolean> callable) {
            final Boolean result = safeRun(callable);
            return result == null ? false: result;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Locale getLocale() {
            return Locale.getDefault();
        }

        /**
         * Returns the current {@link Application}.
         *
         * @return  The current {@link Application}
         */
        @SuppressWarnings("unused")
        public static Application getApplication() {
            try {
                // should never happen
                if      (sInstance.mApplication       == null) CoreLogger.logError("Application == null");
                else if (sInstance.mApplication.get() == null) CoreLogger.logError("Application.get() == null");

                return Objects.requireNonNull(sInstance.mApplication == null /* should never happen */
                        ? null: sInstance.mApplication.get());
            }
            catch (Exception exception) {   // should never happen
                CoreLogger.log(exception);
                return null;
            }
        }

        /**
         * Returns the current {@link Activity} (if any).
         *
         * @return  The current {@link Activity} (or null)
         */
        @SuppressWarnings("unused")
        public static Activity getCurrentActivity() {
            return BaseActivityLifecycleProceed.getCurrentActivity();
        }

        /**
         * Returns the default {@link View} of the given {@link Activity}.
         * The default View Id is stored in the resources ({@link akha.yakhont.R.id#yakhont_default_view_id})
         * and for the moment is {@link android.R.id#content android.R.id.content}.
         *
         * @param activity
         *        The {@link Activity}
         *
         * @return  The default {@link View} (or null)
         */
        @SuppressWarnings("unused")
        public static View getDefaultView(final Activity activity) {
            return ViewHelper.getView(activity);
        }

        /**
         * Returns the data loading progress indicator (default implementation).
         *
         * @return  The data loading progress indicator
         */
        @SuppressWarnings("unused")
        public static BaseDialog getLoadingProgressDefault() {
            return LiveDataDialog.getInstance();
        }

        /**
         * Sets the default {@link View} ID (e.g. to show {@link Snackbar}).
         *
         * @param resId
         *        The resource ID of the {@link View} (should be common for all used Activities)
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
            return ExecutorHelper.getMainHandler();
        }

        /**
         * Causes the runnable to be added to the message queue.
         *
         * @param runnable
         *        The Runnable that will be executed
         *
         * @return  Returns {@code true} if the Runnable was successfully placed in to the
         *          message queue, {@code false} otherwise.
         */
        @SuppressWarnings("UnusedReturnValue")
        public static boolean postToMainLoop(@NonNull final Runnable runnable) {
            return ExecutorHelper.postToMainLoop(runnable);
        }

        /**
         * Causes the runnable to be added to the message queue.
         *
         * @param delay
         *        The delay (in milliseconds) until the Runnable will be executed
         *
         * @param runnable
         *        The Runnable that will be executed
         *
         * @return  Returns {@code true} if the Runnable was successfully placed in to the
         *          message queue, {@code false} otherwise.
         */
        @SuppressWarnings("unused")
        public static boolean postToMainLoop(final long delay, @NonNull final Runnable runnable) {
            return ExecutorHelper.postToMainLoop(delay, runnable);
        }

        /**
         * Returns {@code true} if the current thread is the main thread of the application,
         * {@code false} otherwise.
         *
         * @return  The main thread indication
         */
        public static boolean isCurrentThreadMain() {
            return ExecutorHelper.isCurrentThreadMain();
        }

        /**
         * Causes the runnable to run in background.
         *
         * @param runnable
         *        The Runnable that will be executed
         *
         * @return  The {@link Future}
         */
        @SuppressWarnings("UnusedReturnValue")
        public static Future<?> runInBackground(@NonNull final Runnable runnable) {
            return sExecutorHelper.runInBackground(runnable);
        }

        /**
         * Causes the runnable to run in background.
         *
         * @param delay
         *        The delay (in milliseconds) until the Runnable will be executed
         *
         * @param runnable
         *        The Runnable that will be executed
         *
         * @return  The {@link Future}
         */
        public static Future<?> runInBackground(final long delay, @NonNull final Runnable runnable) {
            return sExecutorHelper.runInBackground(runnable, delay, false);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public static int getInvertedColor(final int color) {
            return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color));
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static int getRequestCode(@NonNull final RequestCodes requestCode) {
            return RequestCodesHandler.getRequestCode(requestCode);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "SameParameterValue"})
        public static int getRequestCode(@NonNull final RequestCodes requestCode,
                                         @NonNull final Activity     activity) {
            return RequestCodesHandler.getRequestCode(requestCode, activity);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static RequestCodes getRequestCode(final int requestCode) {
            return RequestCodesHandler.getRequestCode(requestCode);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getCacheTableName(@NonNull final Uri uri) {
            try {
                return uri.getPathSegments().get(0);
            }
            catch (Exception exception) {
                CoreLogger.log("can't find DB cache table name for uri " + uri, exception);
                return null;
            }
        }

        /**
         * Clears cache table.
         *
         * @param tableName
         *        The table name
         */
        public static void clearCache(final String tableName) {
            if (tableName == null) {
                CoreLogger.logWarning("tableName == null");
                return;
            }
            clearCache(Utils.getUri(tableName));
        }

        /**
         * Clears cache table.
         *
         * @param uri
         *        The URI
         */
        public static void clearCache(final Uri uri) {
            final String table = Utils.getCacheTableName(uri);
            if (table == null) {
                CoreLogger.logWarning("not defined cache table name for clearing");
                return;
            }
            CoreLogger.logWarning("about to clear cache table " + table);
            Objects.requireNonNull(Utils.getApplication()).getContentResolver().delete(
                    uri, null, null);
        }

        /**
         * Finds URI for the given table.
         *
         * @param tableName
         *        The table name
         *
         * @return  The URI
         */
        @SuppressWarnings("WeakerAccess")
        public static Uri getUri(@NonNull final String tableName) {
            return getUriResolver().getUri(tableName);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @NonNull
        public static String getBaseUri() {
            return sInstance.mInit.mBaseUri;
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
            synchronized (sInstance.mInit.mConnectedLock) {
                return sInstance.mInit.mConnected.get();
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        public static Orientation getOrientationToSet(@NonNull final Context context) {
            // landscape allowed on tablets, so use resources, no constants
            final Resources resources = context.getResources();

            if (!resources.getBoolean(R.bool.yakhont_landscape))
                return Orientation.PORTRAIT;
            else
                return resources.getBoolean(R.bool.yakhont_portrait) ?
                        Orientation.UNSPECIFIED: Orientation.LANDSCAPE;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getUnknownResult(final int result) {
            CoreLogger.log(Level.WARNING, "unknown result " + result, true);
            return String.valueOf(result);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public static String getDialogInterfaceString(final int which) {
            switch (which) {
                case        DialogInterface.BUTTON_POSITIVE                                 :
                    return "DialogInterface.BUTTON_POSITIVE"                                ;
                case        DialogInterface.BUTTON_NEGATIVE                                 :
                    return "DialogInterface.BUTTON_NEGATIVE"                                ;
                case        DialogInterface.BUTTON_NEUTRAL                                  :
                    return "DialogInterface.BUTTON_NEUTRAL"                                 ;
                default                                                                     :
                    return "unknown DialogInterface result: " + getUnknownResult(which)     ;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getActivityResultString(final int result) {
            switch (result) {
                case        Activity.RESULT_OK                                              :
                    return "Activity.RESULT_OK"                                             ;
                case        Activity.RESULT_CANCELED                                        :
                    return "Activity.RESULT_CANCELED"                                       ;
                case        Activity.RESULT_FIRST_USER                                      :
                    return "Activity.RESULT_FIRST_USER"                                     ;
                default                                                                     :
                    return "unknown Activity result: " + getUnknownResult(result)           ;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getOnTrimMemoryLevelString(final int level) {
            switch (level) {
                case        ComponentCallbacks2.TRIM_MEMORY_COMPLETE                        :
                    return "ComponentCallbacks2.TRIM_MEMORY_COMPLETE"                       ;
                case        ComponentCallbacks2.TRIM_MEMORY_MODERATE                        :
                    return "ComponentCallbacks2.TRIM_MEMORY_MODERATE"                       ;
                case        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND                      :
                    return "ComponentCallbacks2.TRIM_MEMORY_BACKGROUND"                     ;
                case        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN                       :
                    return "ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN"                      ;
                case        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL                :
                    return "ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL"               ;
                case        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW                     :
                    return "ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW"                    ;
                case        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE                :
                    return "ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE"               ;
                default                                                                     :
                    return "unknown OnTrimMemory() level: " + getUnknownResult(level)       ;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static Level getOnTrimMemoryLevel(final int level) {
            switch (level) {
                case ComponentCallbacks2.TRIM_MEMORY_COMPLETE                               :
                    return Level.ERROR                                                      ;
                case ComponentCallbacks2.TRIM_MEMORY_MODERATE                               :
                    return Level.WARNING                                                    ;
                case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND                             :
                case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN                              :
                    return Level.INFO                                                       ;

                case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL                       :
                    //noinspection DuplicateBranchesInSwitch
                    return Level.ERROR                                                      ;
                case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW                            :
                    //noinspection DuplicateBranchesInSwitch
                    return Level.WARNING                                                    ;
                case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE                       :
                    //noinspection DuplicateBranchesInSwitch
                    return Level.INFO                                                       ;

                // unknown level
                default                                                                     :
                    CoreLogger.log(Level.WARNING, "unknown level " + level, true);
                    return Level.ERROR                                                      ;
            }
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "SameReturnValue"})
        public static Level getOnLowMemoryLevel() {
            return Level.WARNING                                                            ;
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static void onActivityResult(@NonNull final String prefix, @NonNull final Activity activity,
                                            final int requestCode, final int resultCode, final Intent data) {
            CoreLogger.log((prefix.isEmpty() ? "": prefix + ".") + "onActivityResult" +
                    (prefix.isEmpty() ? "": ": subject to call by the Yakhont Weaver"));
            CoreLogger.log("activity   : " + CoreLogger.getDescription(activity));
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
        public static String getTag(@NonNull final Class cls) {
            return getTag(cls.getSimpleName());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        @NonNull
        public static String getTag(final String name) {
            final String prefix = "yakhont";
            return name == null || name.trim().length() == 0 ? prefix:
                    String.format("%s-%s", prefix, name);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String removeExtraSpaces(final String str) {
            return str == null ? null: str.trim().replaceAll("\\s+", " ");
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String replaceSpecialChars(final String str) {
            return str == null ? null: str.trim().replaceAll("[^A-Za-z0-9_]", "_");
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static String getTmpFileSuffix() {
            return "_" + replaceSpecialChars(DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.LONG, getLocale())
                    .format(new Date(System.currentTimeMillis())));
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static File getTmpDir(Context context) {
            if (context == null) context = getApplication();

            File dir;
            if (checkTmpDir(dir = Objects.requireNonNull(context)
                    .getExternalFilesDir(Environment.DIRECTORY_PICTURES))) return dir;
            if (checkTmpDir(dir = context.getExternalCacheDir()))          return dir;

            CoreLogger.logError("can not find tmp directory");
            return null;
        }

        private static boolean checkTmpDir(final File dir) {
            CoreLogger.log("check directory: " + dir);
            return dir != null && dir.isDirectory() && dir.canWrite();
        }

        /**
         * Creates ZIP file.
         *
         * @param zipFile
         *        The destination ZIP file
         *
         * @param srcFiles
         *        The source files
         *
         * @return  {@code true} if ZIP file was created successfully, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        public static boolean zip(final String zipFile, final String... srcFiles) {
            return zip(null, zipFile, srcFiles);
        }

        private static final int                        ZIP_BUFFER_SIZE                 = 2048;

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static boolean zip(final Map<String, Exception> errors,
                                  final String zipFile, final String... srcFiles) {
            if (zipFile == null || srcFiles == null || srcFiles.length == 0) {
                CoreLogger.logError("no arguments");
                return false;
            }
            try {
                final ZipOutputStream outputStream = new ZipOutputStream(
                        new BufferedOutputStream(new FileOutputStream(zipFile)));
                final byte[] buffer = new byte[ZIP_BUFFER_SIZE];

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
                    catch (Exception exception) {
                        handleZipError("failed creating ZIP entry " + srcFile, exception, errors);
                    }

                outputStream.close();
                return true;
            }
            catch (Exception exception) {
                handleZipError("failed creating ZIP " + zipFile, exception, errors);
                return false;
            }
        }

        private static void handleZipError(final String text, final Exception exception,
                                           final Map<String, Exception> map) {
            CoreLogger.log(text, exception);
            if (map != null) map.put(text, exception);
        }

        /**
         * Sends email.
         *
         * @param activity
         *        The Activity
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
         * @param addresses
         *        The email's addresses
         *
         */
        public static void sendEmail(final Activity activity, final String subject, final String text,
                                     final File attachment, final String... addresses) {
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
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse( "file://" + attachment.getAbsolutePath()));

                    activity.startActivity(Intent.createChooser(intent,
                            activity.getString(R.string.yakhont_sending_email)));
                }

                @NonNull
                @Override
                public String toString() {
                    return "Sending debug email";
                }
            });
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "BooleanMethodIsAlwaysInverted"})
        public static boolean equals(final Object object1, final Object object2) {
            return object1 == null && object2 == null || object1 != null && object1.equals(object2);
        }

        private static       Boolean                        sDebug;
        private static final Object                         sDebugLock;

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
        public static Object getBuildConfigField(@NonNull final String fieldName) {
            try {
                //noinspection ConstantConditions
                return getBuildConfigField(getApplication().getPackageName(), fieldName);
            }
            catch (/*NullPointer*/Exception exception) {
                CoreLogger.log(exception);
                return null;
            }
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public static Object getBuildConfigField(@NonNull final String packageName,
                                                 @NonNull final String fieldName) {
            try {
                return CoreReflection.getField(Class.forName(packageName + ".BuildConfig"), fieldName);
            }
            catch (/*ClassNotFound*/Exception exception) {
                CoreLogger.log(Level.WARNING, exception);
                return null;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <E> Set<E> newWeakSet() {         // temp solution
            return Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <K, V> Map<K, V> newWeakMap() {   // temp solution
            return Collections.synchronizedMap(new WeakHashMap<>());
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
        public static <E> List<E> newList() {           // temp solution
            return new CopyOnWriteArrayList<>();
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <E> Set<E> newSet() {             // temp solution
            // order should be kept
            return Collections.synchronizedSet(new LinkedHashSet<>());
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <K, V> Map<K, V> newMap() {       // temp solution
            // order should be kept
            return Collections.synchronizedMap(new LinkedHashMap<>());
        }

        /**
         * Shows {@link Toast}.
         *
         * @param text
         *        The text to show in {@code Toast}
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Toast.LENGTH_LONG} or
         *        {@code Toast.LENGTH_SHORT}, null for default value
         */
        @SuppressWarnings("unused")
        public static void showToast(final String text, final Integer duration) {
            UiModule.showToast(text, duration);
        }

        /**
         * Shows {@link Toast}.
         *
         * @param resId
         *        The string ID to show in {@code Toast}
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Toast.LENGTH_LONG} or
         *        {@code Toast.LENGTH_SHORT}, null for default value
         */
        @SuppressWarnings("unused")
        public static void showToast(@StringRes final int resId, final Integer duration) {
            UiModule.showToast(resId, duration);
        }

        /**
         * Shows {@link Toast}.
         *
         * @param toast
         *        The {@code Toast} to show
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Toast.LENGTH_LONG} or
         *        {@code Toast.LENGTH_SHORT}, null for default value
         */
        @SuppressWarnings("unused")
        public static void showToastExt(final Toast toast, final Integer duration) {
            UiModule.showToastExt(toast, duration);
        }

        /**
         * Shows {@link Toast}.
         *
         * @param viewId
         *        The layout ID to show in {@code Toast}
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Toast.LENGTH_LONG} or
         *        {@code Toast.LENGTH_SHORT}, null for default value
         */
        @SuppressWarnings("unused")
        public static void showToastExt(@LayoutRes final int viewId, final Integer duration) {
            UiModule.showToastExt(viewId, duration);
        }

        /**
         * Builds and shows {@link Toast} with custom duration (please refer to {@link #setDuration}).
         *
         * <p>Can call {@link Activity#onActivityResult} (see {@link #setRequestCode}).
         */
        public static class ToastBuilder {

            @LayoutRes
            private            Integer                  mViewLayoutId;

            @StringRes
            private            int                      mTextId                         = Core.NOT_VALID_RES_ID;
            private            String                   mText;

            private            Integer                  mDuration, mRequestCode;
            private            Intent                   mData;

            private            Integer                  mGravity;
            private            int                      mXOffset;
            private            int                      mYOffset;

            private            Float                    mHorizontalMargin;
            private            float                    mVerticalMargin;

            /**
             * Initialises a newly created {@code ToastBuilder} object.
             */
            @SuppressWarnings("unused")
            public ToastBuilder() {
            }

            /**
             * Sets the Toast's {@link View}.
             *
             * @param viewLayoutId
             *        The View's layout ID
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setViewLayoutId(@LayoutRes final int viewLayoutId) {
                mViewLayoutId     = viewLayoutId;
                return this;
            }

            /**
             * Sets the Toast's text.
             *
             * @param textId
             *        The {@code String} ID in resources
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setTextId(@StringRes final int textId) {
                mTextId           = textId;
                return this;
            }

            /**
             * Sets the Toast's text.
             *
             * @param text
             *        The {@code String}
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setText(final String text) {
                mText             = text;
                return this;
            }

            /**
             * Sets the Toast's duration.
             *
             * @param duration
             *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Toast.LENGTH_LONG} or
             *        {@code Toast.LENGTH_SHORT}, null for default value
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setDuration(final Integer duration) {
                mDuration         = duration;
                return this;
            }

            /**
             * Sets the Toast's request code. If not null, on dismissing {@link Toast} will call
             * {@link Activity#onActivityResult}.
             *
             * @param requestCode
             *        The request code
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setRequestCode(@IntRange(from = 0) final Integer requestCode) {
                mRequestCode      = requestCode;
                return this;
            }


            /**
             * Sets the data for {@link Activity#onActivityResult} (please refer to {@link #setRequestCode}).
             *
             * @param data
             *        The Intent
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setData(final Intent data) {
                mData             = data;
                return this;
            }

            /**
             * Please refer to {@link Toast#setGravity}.
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setGravity(final Integer gravity, final int xOffset, final int yOffset) {
                mGravity          = gravity;
                mXOffset          = xOffset;
                mYOffset          = yOffset;
                return this;
            }

            /**
             * Please refer to {@link Toast#setMargin}.
             *
             * @return  This {@code ToastBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public ToastBuilder setMargin(final Float horizontalMargin, final float verticalMargin) {
                mHorizontalMargin = horizontalMargin;
                mVerticalMargin   = verticalMargin;
                return this;
            }

            private Toast run(final boolean show) {
                return UiModule.showToast(mViewLayoutId != null ? mViewLayoutId: Core.NOT_VALID_RES_ID,
                        mTextId, mText, mDuration, mRequestCode, mData, mGravity, mXOffset, mYOffset,
                        mHorizontalMargin, mVerticalMargin, show);
            }

            /**
             * Just creates the {@link Toast} (without showing).
             *
             * @return  The created (but not shown) {@link Toast} (or null)
             */
            @SuppressWarnings("unused")
            public Toast create() {
                return run(false);
            }

            /**
             * Creates and shows the {@link Toast}.
             *
             * @return  The created and shown {@link Toast} (or null)
             */
            @SuppressWarnings("unused")
            public Toast show() {
                return run(true);
            }
        }

        /**
         * Shows {@link Snackbar} using default {@link View} of the current {@link Activity}.
         *
         * @param text
         *        The text to show in {@code Snackbar}
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Snackbar.LENGTH_INDEFINITE},
         *        {@code Snackbar.LENGTH_LONG} or {@code Snackbar.LENGTH_SHORT}, null for default value
         */
        @SuppressWarnings("unused")
        public static void showSnackbar(final String text, final Integer duration) {
            UiModule.showSnackbar(text, duration);
        }

        /**
         * Shows {@link Snackbar} using default {@link View} of the current {@link Activity}.
         *
         * @param resId
         *        The string ID to show in {@code Snackbar}
         *
         * @param duration
         *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Snackbar.LENGTH_INDEFINITE},
         *        {@code Snackbar.LENGTH_LONG} or {@code Snackbar.LENGTH_SHORT}, null for default value
         */
        @SuppressWarnings("unused")
        public static void showSnackbar(@StringRes final int resId, final Integer duration) {
            UiModule.showSnackbar(resId, duration);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public static int getColor(@ColorRes final int id) {
            return getColor(id, null);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "deprecation", "WeakerAccess"})
        public static int getColor(@ColorRes final int id, final Resources.Theme theme) {
            final Resources resources = Objects.requireNonNull(getApplication()).getResources();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                return resources.getColor(id, theme);
            else
                return resources.getColor(id);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static int getDefaultSnackbarActionColor() {
            return getColor(R.color.yakhont_color_snackbar_action);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static ViewModifier getDefaultSnackbarViewModifier() {
            //noinspection Convert2Lambda
            return new ViewModifier() {
                @SuppressWarnings("unused")
                @Override
                public void modify(final View view, final ViewHandler viewHandler) {
                    view.setBackgroundResource(R.drawable.yakhont_snackbar_background);
                    viewHandler.getTextView().setTextColor(getColor(R.color.yakhont_color_snackbar_text));
                }
            };
        }

        /**
         * Builds and shows {@link Snackbar} with Views customization possibilities (please refer to
         * {@link #setViewHandler}).
         *
         * <p>Sequential Snackbar's calls are queued.
         *
         * <p>By default calls {@link Activity#onActivityResult} (see {@link #setRequestCode}).
         */
        public static class SnackbarBuilder {

            @IdRes
            private            Integer                  mViewId                         = Core.NOT_VALID_VIEW_ID;
            private            WeakReference<View>      mView;

            @StringRes
            private            int                      mTextId                         = Core.NOT_VALID_RES_ID;
            private            String                   mText;

            private            Integer                  mDuration, mRequestCode;
            private            Intent                   mData;

            private            View.OnClickListener     mAction;
            private            Integer                  mMaxLines;

            @StringRes
            private            int                      mActionTextId                   = Core.NOT_VALID_RES_ID;
            private            String                   mActionText;

            private            ColorStateList           mActionColors;
            @ColorRes
            private            int                      mActionColorId                  = Core.NOT_VALID_RES_ID;
            @ColorInt
            private            int                      mActionColor                    = Core.NOT_VALID_COLOR;

            private final      List<ViewModifier>       mViewModifiers                  = new ArrayList<>();
            private            boolean                  mViewHandlersChain;

            /**
             * Initialises a newly created {@code SnackbarBuilder} object.
             */
            public SnackbarBuilder() {
            }

            /**
             * Sets the {@link View} for {@link Snackbar#make}.
             *
             * @param viewId
             *        The View ID (or null for default one)
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setViewId(@IdRes final Integer viewId) {
                mViewId          = viewId;
                return this;
            }

            /**
             * Sets the {@link View} for {@link Snackbar#make}.
             *
             * <p>Note: use it for Snackbars which should be shown immediately
             * (ignoring Snackbar's queue), e.g. Snackbars based on {@link Dialog}'s Views.
             *
             * @param view
             *        The View
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("UnusedReturnValue")
            public SnackbarBuilder setView(final View view) {
                mView            = view == null ? null: new WeakReference<>(view);
                return this;
            }

            /**
             * Sets the Snackbar's text.
             *
             * @param textId
             *        The {@code String} ID in resources
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            public SnackbarBuilder setTextId(@StringRes final int textId) {
                mTextId          = textId;
                return this;
            }

            /**
             * Sets the Snackbar's text.
             *
             * @param text
             *        The {@code String}
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setText(final String text) {
                mText            = text;
                return this;
            }

            /**
             * Sets the Snackbar's duration.
             *
             * @param duration
             *        duration in seconds (<= 5 min), milliseconds (> 5 min), {@code Snackbar.LENGTH_INDEFINITE},
             *        {@code Snackbar.LENGTH_LONG} or {@code Snackbar.LENGTH_SHORT}, null for default value
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            public SnackbarBuilder setDuration(final Integer duration) {
                mDuration        = duration;
                return this;
            }

            /**
             * Sets the Snackbar's request code. If not null, on dismissing {@link Snackbar} will call
             * action listener (see {@link #setAction}).
             *
             * @param requestCode
             *        The request code
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setRequestCode(@IntRange(from = 0) final Integer requestCode) {
                mRequestCode     = requestCode;
                return this;
            }

            /**
             * Sets the data for {@link Activity#onActivityResult} (please refer to {@link #setAction}).
             *
             * @param data
             *        The Intent
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setData(final Intent data) {
                mData            = data;
                return this;
            }

            /**
             * Sets the Snackbar's action. If not null (or {@link #setRequestCode} set non-null value),
             * on dismissing {@link Snackbar} will call it, (default action is {@link Activity#onActivityResult}).
             *
             * @param action
             *        The Snackbar's action
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            public SnackbarBuilder setAction(final View.OnClickListener action) {
                mAction          = action;
                return this;
            }

            /**
             * Sets the Snackbar's action text.
             *
             * @param actionTextId
             *        The {@code String} ID in resources
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            public SnackbarBuilder setActionTextId(@StringRes final int actionTextId) {
                mActionTextId    = actionTextId;
                return this;
            }

            /**
             * Sets the Snackbar's action text.
             *
             * @param actionText
             *        The {@code String}
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setActionText(final String actionText) {
                mActionText      = actionText;
                return this;
            }

            /**
             * Sets the 'max lines' value for Snackbar's text
             * (please refer to {@link TextView#setMaxLines} for more info).
             *
             * @param maxLines
             *        The 'max lines' value
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setMaxLines(final Integer maxLines) {
                mMaxLines        = maxLines;
                return this;
            }

            /**
             * Sets the colors for the Snackbar's action text (please refer to
             * {@link Snackbar#setActionTextColor(ColorStateList) setActionTextColor} for more info).
             *
             * @param actionColors
             *        The colors
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setActionColors(final ColorStateList actionColors) {
                mActionColors    = actionColors;
                return this;
            }

            /**
             * Sets the color for the Snackbar's action text (please refer to
             * {@link Snackbar#setActionTextColor(int)} setActionTextColor} for more info).
             *
             * @param actionColorId
             *        The color ID in resources
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setActionColorId(@ColorRes final int actionColorId) {
                mActionColorId   = actionColorId;
                return this;
            }

            /**
             * Sets the color for the Snackbar's action text (please refer to
             * {@link Snackbar#setActionTextColor(int)} setActionTextColor} for more info).
             *
             * @param actionColor
             *        The color
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setActionColor(@ColorInt final int actionColor) {
                mActionColor     = actionColor;
                return this;
            }

            /**
             * Sets the View handler for the Snackbar's view. Usage example:
             *
             * <p><pre style="background-color: silver; border: thin solid black;">
             * setToastViewHandler((view, vh) -&gt; {
             *     view.setBackgroundColor(Color.BLUE);
             *     vh.getTextView().setTextColor(Color.GREEN);
             * });
             * </pre>
             *
             * @param viewModifier
             *        The Snackbar's view modifier
             *
             * @return  This {@code SnackbarBuilder} object to allow for chaining of calls to set methods
             */
            @SuppressWarnings("unused")
            public SnackbarBuilder setViewHandler(final ViewModifier viewModifier) {
                if (viewModifier == null)
                    mViewModifiers.clear();
                else if (mViewHandlersChain || mViewModifiers.size() == 0)
                    mViewModifiers.add(viewModifier);
                else
                    mViewModifiers.set(0, viewModifier);
                return this;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public SnackbarBuilder setViewHandlersChain(final boolean viewHandlersChain) {
                mViewHandlersChain = viewHandlersChain;
                return this;
            }

            private Snackbar run(final boolean show) {
                return UiModule.showSnackbar(mView == null ? null: mView.get(), mViewId,
                        mTextId, mText, mMaxLines, mDuration, mRequestCode, mData,
                        mActionTextId, mActionText, mAction, mActionColors, mActionColorId,
                        mActionColor, mViewModifiers, show);
            }

            /**
             * Just creates the {@link Snackbar} (without showing).
             *
             * @return  The created (but not shown) {@link Snackbar} (or null)
             */
            @SuppressWarnings("unused")
            public Snackbar create() {
                return run(false);
            }

            /**
             * Creates and shows (or put in queue) the {@link Snackbar}.
             *
             * @return  The created and shown {@link Snackbar} (or null)
             */
            public Snackbar show() {
                return run(true);
            }
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public static class RequestCodesHandler {

            private static final RequestCodes[]         REQUEST_CODES_VALUES            = RequestCodes.values();

            private RequestCodesHandler() {
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static int getRequestCode(@NonNull final RequestCodes requestCode) {
                return getRequestCode(requestCode, REQUEST_CODES_OFFSET);
            }

            private static int getRequestCode(@NonNull final RequestCodes requestCode, final int offset) {
                return requestCode.ordinal() + offset;
            }

            private static Throwable checkRequestCode(final int requestCode, final Activity activity,
                                                      final Method method, final Level level) {
                try {
                    CoreReflection.invoke(activity, method, requestCode);
                    return null;
                }
                catch (Throwable throwable) {
                    CoreLogger.log(level != null ? level: CoreLogger.getDefaultLevel(),
                            "checkRequestCode failed", throwable);
                    return throwable;
                }
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static int getRequestCode(@NonNull final RequestCodes requestCode,
                                             @NonNull final Activity     activity) {
                final Method method = CoreReflection.findMethod(activity,
                        "validateRequestPermissionsRequestCode", int.class);
                if (method != null) {
                    int result = getRequestCode(requestCode);
                    if (checkRequestCode(result, activity, method, null) == null) return result;
                }
                int result = getRequestCode(requestCode, REQUEST_CODES_OFFSET_SHORT);
                final Throwable throwable = method == null ? null:
                        checkRequestCode(result, activity, method, Level.ERROR);

                if (throwable != null) CoreLogger.log("getRequestCode failed", throwable);
                return result;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static RequestCodes getRequestCode(final int requestCode) {
                final RequestCodes code = getRequestCode(requestCode, REQUEST_CODES_OFFSET);
                return code.equals(RequestCodes.UNKNOWN) ? getRequestCode(requestCode,
                        REQUEST_CODES_OFFSET_SHORT): code;
            }

            private static RequestCodes getRequestCode(int requestCode, final int offset) {
                requestCode -= offset;
                return requestCode < 0 || requestCode >= REQUEST_CODES_VALUES.length ?
                        RequestCodes.UNKNOWN: REQUEST_CODES_VALUES[requestCode];
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static class ExecutorHelper {

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public  static final int                    THREAD_POOL_SIZE                = 8;

            private final ScheduledExecutorService      mExecutorService;
            private final ScheduledExecutorService      mExecutorServiceSingle;

            private final List<Future<?>>               mTasks                          = new ArrayList<>();

            private       boolean                       mCancelled;

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public ExecutorHelper() {
                this(THREAD_POOL_SIZE, null);
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "SameParameterValue", "WeakerAccess"})
            public ExecutorHelper(final int corePoolSize, final ThreadFactory threadFactory) {
                mExecutorService = threadFactory == null ?
                        Executors.newScheduledThreadPool(corePoolSize):
                        Executors.newScheduledThreadPool(corePoolSize, threadFactory);
                mExecutorServiceSingle = threadFactory == null ?
                        Executors.newSingleThreadScheduledExecutor():
                        Executors.newSingleThreadScheduledExecutor(threadFactory);
            }

            private static Handler getMainHandler() {
                return new Handler(Looper.getMainLooper());
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static boolean postToMainLoop(@NonNull final Runnable runnable) {
                final boolean result = getMainHandler().post(prepareRunnable(runnable));
                if (!result) CoreLogger.logError("post to main loop failed for: " + runnable);
                return result;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static boolean postToMainLoop(final long delay, @NonNull final Runnable runnable) {
                final boolean result = getMainHandler().postDelayed(prepareRunnable(runnable), delay);
                if (!result) CoreLogger.logError("delayed post to main loop failed for: " + runnable);
                return result;
            }

            private static Runnable prepareRunnable(@NonNull final Runnable runnable) {
                return new Runnable() {
                    @Override
                    public void run() {
                        safeRun(runnable);
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return runnable.toString();
                    }
                };
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static boolean isCurrentThreadMain() {
                return Thread.currentThread() == getMainHandler().getLooper().getThread();
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public Future<?> runInBackground(@NonNull final Runnable runnable) {
                return runInBackground(runnable, 0, false);
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "SameParameterValue"})
            public Future<?> runInBackground(@NonNull final Runnable runnable, final long delay,
                                             final boolean singleThread) {
                return runInBackground(runnable, delay, 0, singleThread);
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public Future<?> runInBackground(@NonNull final Runnable runnable, final long delay,
                                             final long period, final boolean singleThread) {
                CoreLogger.log("about to run " + runnable + ", delay " + delay +
                        ", period " + period + ", singleThread " + singleThread);
                final Future<?> result = submit(singleThread ? mExecutorServiceSingle: mExecutorService,
                        prepareRunnable(runnable), delay, period);
                if (result != null) mTasks.add(result);

                CoreLogger.log(result == null ? Level.ERROR: CoreLogger.getDefaultLevel(),
                        "submit result: " + result);
                return result;
            }

            private Future<?> submit(@NonNull final ScheduledExecutorService service,
                                     @NonNull final Runnable runnable,
                                     final long delay, final long period) {
                if (mCancelled)
                    CoreLogger.logError("cancelled; can't run " + runnable);
                else
                    try {
                        return period > 0 ? service.scheduleAtFixedRate(runnable, delay, period,
                                TimeUnit.MILLISECONDS): delay > 0 ? service.schedule(runnable, delay,
                                TimeUnit.MILLISECONDS): service.submit(runnable);
                    }
                    catch (Exception exception) {
                        CoreLogger.log("submit failed: " + runnable, exception);
                    }
                return null;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
            public boolean isCancelled() {
                return mCancelled;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
            public void cancel() {
                cancel(false);
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "SameParameterValue", "WeakerAccess"})
            public void cancel(final boolean forceStopTasks) {
                if (mCancelled) return;
                mCancelled = true;

                CoreLogger.log("about to cancel executor " + this + ", forceStopTasks " + forceStopTasks);

                if (!forceStopTasks) {
                    for (final Future<?> task: mTasks)
                        task.cancel(false);
                    mTasks.clear();
                }
                cancel(mExecutorService,       forceStopTasks);
                cancel(mExecutorServiceSingle, forceStopTasks);
            }

            @SuppressWarnings("UnusedReturnValue")
            private static List<Runnable> cancel(@NonNull final ScheduledExecutorService service,
                                                 final boolean forceStopTasks) {
                try {
                    if (forceStopTasks) return service.shutdownNow();

                    service.shutdown();
                }
                catch (Exception exception) {
                    CoreLogger.log(exception);
                }
                return null;
            }
        }

        /**
         * Helper class for handling the Back key in ActionMode
         * (in {@code BaseActivity} it's already done). For example (in Activity):
         * <p>
         * <pre style="background-color: silver; border: thin solid black;">
         * import akha.yakhont.Core.Utils;
         *
         * private final Utils.BackKeyInActionModeHandler mBackKeyHandler =
         *     new Utils.BackKeyInActionModeHandler();
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
         *     // methods 'onPrepareActionMode(...)' and 'onActionItemClicked(...)'
         *     // are skipped for simplification
         * };
         *
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
         * @see #onAdjustMeasuredView(MeasuredViewAdjuster, View)
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

        /**
         * The callback helper for calling
         * {@link MeasuredViewAdjuster#adjustMeasuredView(View)}. For example:
         *
         * <pre style="background-color: silver; border: thin solid black;">
         * import akha.yakhont.Core.Utils;
         * import akha.yakhont.Core.Utils.MeasuredViewAdjuster;
         *
         * public class YourFragment extends Fragment implements MeasuredViewAdjuster {
         *
         *     &#064;Override
         *     public View onCreateView(LayoutInflater inflater, ViewGroup container,
         *                              Bundle savedInstanceState) {
         *         super.onCreateView(inflater, container, savedInstanceState);
         *
         *         // your code here
         *         View view = ...;
         *
         *         Utils.onAdjustMeasuredView(this, view);
         *         return view;
         *     }
         *
         *     &#064;Override
         *     public void adjustMeasuredView(View view) {
         *         int height = view.getMeasuredHeight();
         *         int width  = view.getMeasuredWidth();
         *
         *         // your code here
         *     }
         * }
         * </pre>
         *
         * @param container
         *        The view container (e.g. Fragment)
         *
         * @param view
         *        The view to handle
         */
        @SuppressWarnings("WeakerAccess")                                                           //YakhontPreprocessor:removeInGenerated
        @SuppressLint("ObsoleteSdkInt")                                                             //YakhontPreprocessor:removeInGenerated
        public static void onAdjustMeasuredView(@NonNull final MeasuredViewAdjuster container,
                                                @NonNull final View                 view) {
            ViewHelper.onAdjustMeasuredView(container, view);
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
            private static final int                    DEF_VIEW_REF_ID                 = R.id.yakhont_default_view_id;
            @IdRes
            private static final int                    DEF_VIEW_ID                     = android.R.id.content;
            @IdRes
            private static int                          sDefViewId;

            private ViewHelper() {
            }

            static {
                init();
            }

            private static void init() {
                sDefViewId  = DEF_VIEW_ID;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedReturnValue"})
            public static Object setTag(final View view, @IdRes final int id, final Object value) {
                if (view == null) {
                    CoreLogger.logError("view == null");
                    return null;
                }

                final Object previous = view.getTag(id);
                try {
                    view.setTag(id, value);
                }
                catch (/*IllegalArgument*/Exception exception) {
                    CoreLogger.log(exception);
                    return null;
                }

                if (previous != null) CoreLogger.log("view: " + CoreLogger.getViewDescription(view) +
                        ", tag: " + id + ", previous value: " + previous);
                return previous;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static void onAdjustMeasuredView(@NonNull final MeasuredViewAdjuster container,
                                                    @NonNull final View                 view) {
                view.getViewTreeObserver().addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {

                            @Override
                            public void onGlobalLayout() {
                                try {
                                    container.adjustMeasuredView(view);
                                }
                                catch (Exception exception) {
                                    CoreLogger.log(exception);
                                }
                                finally {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                    else
                                        removeListener();
                                }
                            }

                            @SuppressWarnings({"deprecation", "RedundantSuppression" /* lint bug workaround */ })
                            private void removeListener() {
                                view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                            }
                        });
            }

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
            public static View findView(final View parentView, @NonNull final ViewVisitor visitor) {
                return findView(parentView, visitor, Level.ERROR);
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static View findView(final View parentView, @NonNull final ViewVisitor visitor,
                                        @NonNull final Level level) {
                if (parentView == null) {
                    CoreLogger.logError("parentView == null");
                    return null;
                }

                final View[] viewHelper = new View[1];

                //noinspection Convert2Lambda
                visitView(parentView, new ViewVisitor() {
                    @Override
                    public boolean handle(final View view) {
                        final boolean found = visitor.handle(view);
                        if (found && viewHelper[0] == null) viewHelper[0] = view;
                        return found;
                    }
                });

                CoreLogger.log(viewHelper[0] == null ? level: CoreLogger.getDefaultLevel(),
                        "result of find view: " + CoreLogger.getViewDescription(viewHelper[0]));

                return viewHelper[0];
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static void findView(@NonNull final Collection<View> views, final View parentView,
                                        @NonNull final ViewVisitor      visitor) {
                if (parentView == null) {
                    CoreLogger.logError("parentView == null");
                    return;
                }

                //noinspection Convert2Lambda
                visitView(parentView, new ViewVisitor() {
                    @SuppressWarnings("unused")
                    @Override
                    public boolean handle(final View view) {
                        if (visitor.handle(view)) views.add(view);
                        return false;
                    }
                });
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

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static View getView(final Activity activity) {
                return getView(activity, NOT_VALID_VIEW_ID);
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static View getView(Activity activity, @IdRes final int viewId) {
                if (activity == null) {
                    CoreLogger.logWarning("getView(): activity == null, current activity will be used");
                    activity = getCurrentActivity();
                }
                CoreLogger.logWarning("getView() from activity: " + CoreLogger.getDescription(activity));

                if (viewId != NOT_VALID_VIEW_ID) {
                    final View view = activity == null ? null: activity.findViewById(viewId);
                    if (view == null)
                        CoreLogger.logError("can not find view with ID " +
                                CoreLogger.getResourceDescription(viewId));
                    return view;
                }

                final Resources resources = Objects.requireNonNull(getApplication()).getResources();
                @IdRes final int defaultViewId = getDefaultViewId(resources);

                final String defaultViewDescription = CoreLogger.getResourceDescription(defaultViewId);
                CoreLogger.log("default view is " + defaultViewDescription);

                View view = null;
                try {
                    view = activity == null ? null: activity.findViewById(defaultViewId);
                }
                catch (Exception exception) {
                    CoreLogger.log(exception);
                }
                if (view == null) {
                    CoreLogger.logWarning(defaultViewDescription +
                            " not found, getWindow().getDecorView() will be used");
                    CoreLogger.logWarning("Note that calling this function \"locks in\" various " +
                            "characteristics of the window that can not, from this point forward, be changed");

                    final Window window = activity == null ? null: activity.getWindow();
                    if (window == null) CoreLogger.logError("window == null, Activity " +
                            CoreLogger.getDescription(activity));

                    view = window == null ? null: window.getDecorView();
                }
                if (view == null)
                    CoreLogger.logError("can not find View for Activity " +
                            CoreLogger.getDescription(activity));
                else
                    CoreLogger.log(String.format("found View %s for Activity %s",
                            CoreLogger.getViewDescription(view), CoreLogger.getDescription(activity)));
                return view;
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static View getViewForSnackbar(final Activity activity, final Integer viewId) {
                View view = viewId == null ? null: ViewHelper.getView(activity, viewId);

                if (view == null && (viewId == null || viewId != NOT_VALID_VIEW_ID)) {
                    CoreLogger.log("about to try to find default view for Snackbar");
                    view = ViewHelper.getView(activity);
                }
                if (view != null) {
                    //noinspection Convert2Lambda
                    final View viewChild = ViewHelper.findView(view, new ViewHelper.ViewVisitor() {
                        @Override
                        public boolean handle(final View viewTmp) {
                            return !(viewTmp instanceof ViewGroup || viewTmp instanceof ViewStub);
                        }
                    });
                    if (viewChild != null) view = viewChild;
                }
                if (view == null)
                    CoreLogger.logError("Snackbar view == null");
                else
                    CoreLogger.log("found View for Snackbar " + CoreLogger.getViewDescription(view));

                return view;
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static class TypeHelper {

            private TypeHelper() {
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static Type getType(final Method method) {
                return method == null ? null: getType(method.getGenericReturnType());
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static Type getType(final Type type) {
                return type == null               ||
                       type instanceof Collection || type instanceof GenericArrayType ? type:
                       getParameterizedType(type);  // actually - Rx handling
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static Type getParameterizedType(final Type type) {
                return type instanceof ParameterizedType ?
                        ((ParameterizedType) type).getActualTypeArguments()[0]: type;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static Type getGenericComponentType(final Type type) {
                return type instanceof GenericArrayType ?
                        ((GenericArrayType) type).getGenericComponentType(): type;
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "unused"})
            public static Type getParameterizedOrGenericComponentType(final Type type) {
                final Type genericArrayType = getGenericComponentType(type);
                return type != null && !type.equals(genericArrayType) ? genericArrayType: getParameterizedType(type);
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static boolean isCollection(final Type type) {
                final Type typeRaw = getParameterizedRawType(type);
                CoreLogger.log("typeRaw: " + typeRaw);
                return typeRaw instanceof Class && Collection.class.isAssignableFrom((Class) typeRaw);
            }

            /** @exclude */ @SuppressWarnings("JavaDoc")
            public static boolean checkType(@NonNull final Type typeResponse,
                                            @NonNull final Type typeMethod) {
                CoreLogger.log("typeResponse: " + typeResponse);
                CoreLogger.log("typeMethod  : " + typeMethod  );

                if (typeResponse instanceof Class) {
                    final Class classResponse = (Class) typeResponse;
                    final boolean result = typeResponse.equals(typeMethod) ||
                            (classResponse.isArray() && typeMethod instanceof GenericArrayType &&
                                    getGenericComponentType(typeMethod).equals(
                                            classResponse.getComponentType()));
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

        /**
         * Intended to use with {@link #cursorHelper} method.
         */
        public interface CursorHandler {

            /**
             * Handles the current cursor row.
             *
             * @param cursor
             *        The {@code Cursor}
             *
             * @return  {@code true} to move to the next row (after handling the current one), {@code false} otherwise
             *
             * @see #cursorHelper
             */
            boolean handle(Cursor cursor);
        }

        /**
         * Helper method to work with {@code Cursor} objects. Iterates through all cursor rows (if any)
         * and applies to each one the handler provided.
         *
         * @param cursor
         *        The {@code Cursor}
         *
         * @param cursorHandler
         *        The custom handler for each cursor row
         *
         * @param position
         *        The cursor position to move (0 - for 1st) or null - to work from the current one
         *
         * @param onlyOne
         *        {@code true} if only one row should be handled, {@code false} otherwise
         *
         * @param closeOrRestorePos
         *        null - close cursor after handling, {@code true} - restore initial cursor position,
         *        {@code false} - leave cursor as is
         *
         * @return  {@code true} in case of no errors, {@code false} otherwise
         *
         * @see CursorHandler
         */
        public static boolean cursorHelper(final Cursor cursor, final CursorHandler cursorHandler,
                                           final Integer position, final boolean onlyOne,
                                           final Boolean closeOrRestorePos) {
            if (cursorHandler == null) {
                CoreLogger.logWarning("nothing to do");
                return true;
            }
            if (cursor == null) {
                CoreLogger.logWarning("cursor == null");
                return true;
            }
            if (cursor.isClosed()) {
                CoreLogger.logError("cursor closed");
                return false;
            }

            final boolean close      = closeOrRestorePos == null;
            final boolean restorePos = close ? false: closeOrRestorePos;

            try {
                if (position != null && !cursor.moveToPosition(position)) {
                    if (position == 0) {
                        CoreLogger.logWarning("empty cursor");
                        return true;
                    }
                    else {
                        CoreLogger.logError("wrong cursor position " + position);
                        return false;
                    }
                }

                if (cursor.isBeforeFirst()) {
                    CoreLogger.logError("Cursor.isBeforeFirst()");
                    return false;
                }
                if (cursor.isAfterLast()) {
                    CoreLogger.logError("Cursor.isAfterLast()");
                    return false;
                }

                final int pos = restorePos ? cursor.getPosition(): -1;

                while (!cursor.isAfterLast()) {
                    if (!cursorHandler.handle(cursor)) break;
                    if (onlyOne) {
                        CoreLogger.logError("wrong combination: moveToNext and onlyOne");
                        break;
                    }
                    if (!cursor.moveToNext()) break;
                }

                if (restorePos && !cursor.moveToPosition(pos))
                    CoreLogger.logError("failed moveToPosition " + pos);

                return true;
            }
            catch (Exception exception) {
                CoreLogger.log(exception);
            }
            finally {
                if (close) close(cursor);
            }
            return false;
        }

        /**
         * Closes the given cursor.
         *
         * @param cursor
         *        The {@code Cursor}
         *
         * @return  {@code true} if cursor was closed, {@code false} if cursor is null or already closed
         */
        @SuppressWarnings("UnusedReturnValue")
        public static boolean close(final Cursor cursor) {
            if (cursor != null && !cursor.isClosed()) {
                CoreLogger.log(CoreLogger.getDefaultLevel(), "about to close cursor " + cursor, true);
                try {
                    cursor.close();
                    return true;
                }
                catch (Exception exception) {
                    CoreLogger.log(exception);
                }
            }
            else
                CoreLogger.logWarning("not closeable cursor " + cursor);

            return false;
        }

        /**
         * Wrapper for {@link CountDownLatch#await()}.
         *
         * @param countDownLatch
         *        The {@code CountDownLatch}
         *
         * @return  {@code true} if no errors, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        public static boolean await(final CountDownLatch countDownLatch) {
            return await(countDownLatch, null);
        }

        /**
         * Wrapper for {@link CountDownLatch#await()}.
         *
         * @param countDownLatch
         *        The {@code CountDownLatch}
         *
         * @param timeout
         *        The timeout (0 for endless awaiting, null for the default value)
         *
         * @return  {@code true} if no errors, {@code false} otherwise
         *
         * @see #setAwaitDefaultTimeout
         */
        public static boolean await(final CountDownLatch countDownLatch, final Long timeout) {
            return handle(countDownLatch, timeout != null ? timeout: sInstance.mAwaitDefaultTimeout != null
                    ? sInstance.mAwaitDefaultTimeout: 0);
        }

        /**
         * Sets the default {@link CountDownLatch#await(long, TimeUnit)} timeout.
         *
         * @param timeout
         *        The timeout (0 for endless awaiting, null for the default value)
         */
        @SuppressWarnings("unused")
        public static void setAwaitDefaultTimeout(final Long timeout) {
            sInstance.mAwaitDefaultTimeout = timeout;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedReturnValue", "unused"})
        public static Long getAwaitDefaultTimeout() {
            return sInstance.mAwaitDefaultTimeout;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedReturnValue"})
        public static boolean cancel(final CountDownLatch countDownLatch) {
            return handle(countDownLatch, null);
        }

        private static boolean handle(final CountDownLatch countDownLatch, final Long timeout) {
            boolean result = false;

            if (countDownLatch == null)
                CoreLogger.logWarning("countDownLatch == null");
            else
                try {
                    if (timeout == null)
                        while (countDownLatch.getCount() > 0)
                            countDownLatch.countDown();

                    else if (timeout <= 0) countDownLatch.await();
                    else {
                        final long tmp = adjustTimeout(timeout);
                        if (!              countDownLatch.await(tmp, TimeUnit.MILLISECONDS))
                            CoreLogger.logWarning(tmp + " (ms) timeout for CountDownLatch: " + countDownLatch);
                    }
                    result = true;
                }
                catch (/*Interrupted*/Exception exception) {
                    CoreLogger.log(exception);
                }

            CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.ERROR,
                    "countDownLatch.await() result: " + result);
            return result;
        }

        /**
         * Implements the {@code DialogFragment} which is not destroyed after screen orientation
         * changing (actually it's a Google API bug workaround).
         */
        public static abstract class RetainDialogFragment extends DialogFragment {

            /**
             * Initialises a newly created {@code RetainDialogFragment} object.
             */
            public RetainDialogFragment() {
                setRetainInstance(true);
            }

            /**
             * Please refer to the base method description.
             */
            @CallSuper
            @Override
            public void onDestroyView() {
                onDestroyView(this);
                super.onDestroyView();
            }

            /**
             * Should be called from overridden {@link DialogFragment#onDestroyView} before call
             * to the super method (to prevent retained {@code DialogFragment} dismissing on
             * screen orientation changing).
             *
             * @param dialogFragment
             *        The retained {@code DialogFragment}
             */
            public static void onDestroyView(final DialogFragment dialogFragment) {
                if (dialogFragment == null) return;
                final Dialog dialog = dialogFragment.getDialog();

                // handles https://code.google.com/p/android/issues/detail?id=17423
                if (dialog != null && dialogFragment.getRetainInstance())
                    dialog.setDismissMessage(null);
            }
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static class DataStore implements Serializable /* 'cause of map */ {

            private final Map<String, Object>           mStore                          = newMap();

            @SuppressWarnings("unchecked")
            public <V> V setData(final String key, final V value) {
                if (key == null) {
                    CoreLogger.logError("setData: key == null");
                    return null;
                }
                return (V) mStore.put(key, value);
            }

            public Object getData(final String key) {
                if (key == null) {
                    CoreLogger.logError("getData: key == null");
                    return null;
                }
                return mStore.get(key);
            }
        }

        /**
         * Helper class for {@code CoreLoad} interface.
         *
         * @see CoreLoad
         */
        public static class CoreLoadHelper {

            /**
             * Returns the {@link BaseLoaderWrapper loader} associated with
             * the given {@code CoreLoad} component (most of the time it's one and only).
             *
             * @param coreLoad
             *        The {@code CoreLoad}
             *
             * @param <T>
             *        The type of data associated with the {@code BaseLoaderWrapper}
             *
             * @param <E>
             *        The type of error (if any)
             *
             * @param <D>
             *        The type of data to load
             *
             * @return  The {@code BaseLoaderWrapper}
             *
             * @see CoreLoad#getLoaders
             */
            @SuppressWarnings("unchecked")
            public static <T, E, D> BaseLoaderWrapper<T> getLoader(final CoreLoad<E, D> coreLoad) {
                if (coreLoad == null) {
                    CoreLogger.logWarning("CoreLoad == null, can't get loader");
                    return null;
                }

                final List<BaseLoaderWrapper<?>> list = coreLoad.getLoaders();
                if (list != null && list.size() > 1)
                    CoreLogger.logWarning("expected one and only loader but actually " + list.size());

                return list == null || list.size() == 0 ? null : (BaseLoaderWrapper<T>) list.get(0);
            }

            /**
             * Sets callback for paged loading.
             *
             * @param coreLoad
             *        The {@code CoreLoad}
             *
             * @param callback
             *        The {@code LoaderCallback}
             *
             * @param callbackError
             *        The {@code LoaderCallback} for errors handling (or null for default one)
             *
             * @param activity
             *        The {@code Activity}
             *
             * @param <E>
             *        The type of error (if any)
             *
             * @param <D>
             *        The type of data to load
             *
             * @return  The {@code CoreLoad} passed as parameter

             * @see #setPagingCallbacks
             */
            @SuppressWarnings({"unused"})
            public static <E, D> CoreLoad<E, D> setPagingCallback(final CoreLoad<E,    D> coreLoad,
                                                                  final LoaderCallback<D> callback,
                                                                  final Runnable          callbackError,
                                                                  final Activity          activity) {
                CacheLiveData tmpData = null;

                final BaseLoaderWrapper loader = getLoader(coreLoad);
                if (loader != null) {
                    final BaseViewModel<?> model = loader.findViewModel(
                            BaseViewModel.cast(activity, null));
                    if (model instanceof PagingViewModel) {
                        final BaseLiveData tmp = model.getData();
                        if (tmp instanceof CacheLiveData)
                            tmpData = (CacheLiveData) tmp;
                        else
                            CoreLogger.logError("wrong data (expected CacheLiveData): " + tmp);
                    }
                    else
                        CoreLogger.logError("wrong model (expected PagingViewModel): " + model);
                }
                if (tmpData == null) return setPagingCallbacks(coreLoad, null);

                final CacheLiveData liveData = tmpData;

                return setPagingCallbacks(coreLoad, new LoaderCallbacks<E, D>() {
                    @Override
                    public void onLoadFinished(final D data, final Source source) {
                        Utils.safeRun(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoadFinished(data, source);
                            }

                            @NonNull
                            @Override
                            public String toString() {
                                return "setPagingCallbacks - LoaderCallback.onLoadFinished()";
                            }
                        });
                    }

                    @Override
                    public void onLoadError(final E error, final Source source) {
                        Utils.safeRun(callbackError != null ? callbackError:
                                getPagingDefaultErrorCallback(liveData, loader, callback));
                    }
                });
            }

            /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
            public static <D> Runnable getPagingDefaultErrorCallback(
                    final CacheLiveData liveData, final BaseLoaderWrapper loader, final LoaderCallback<D> callback) {

                if (liveData == null || loader == null || callback == null) {
                    if (liveData == null) CoreLogger.logError("liveData == null");
                    if (loader   == null) CoreLogger.logError("loader   == null");
                    if (callback == null) CoreLogger.logError("callback == null");

                    return null;
                }

                return new Runnable() {
                    @Override
                    public void run() {
                        final Cursor cursor = liveData.getCursor();
                        Object data = null;
                        try {
                            final CacheAdapter adapter = loader.getAdapter();
                            if (adapter instanceof BaseCacheAdapterWrapper)
                                data = ((BaseCacheAdapterWrapper) adapter).getAdapter().getConverter()
                                        .getConverter().getData(cursor, null);
                            else
                                CoreLogger.logError("wrong adapter (expected BaseCacheAdapterWrapper): " + adapter);
                        }
                        catch (Exception exception) {
                            CoreLogger.log(exception);
                        }
                        Utils.close(cursor);

                        @SuppressWarnings("unchecked")
                        final D tmp         = (D) (data == null ? Collections.EMPTY_LIST: data);
                        final Source source =      data == null ? Source.UNKNOWN: Source.CACHE;

                        Utils.safeRun(new Runnable() {
                            @Override
                            public void run() {
                                callback.onLoadFinished(tmp, source);
                            }

                            @NonNull
                            @Override
                            public String toString() {
                                return "getPagingDefaultErrorCallback - LoaderCallback.onLoadFinished()";
                            }
                        });
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "getPagingDefaultErrorCallback";
                    }
                };
            }

            /**
             * Sets callback for paged loading.
             *
             * @param coreLoad
             *        The {@code CoreLoad}
             *
             * @param callback
             *        The {@code LoaderCallback}
             *
             * @param <E>
             *        The type of error (if any)
             *
             * @param <D>
             *        The type of data to load
             *
             * @return  The {@code CoreLoad} passed as parameter

             * @see #setPagingCallbacks
             */
            @SuppressWarnings("unused")
            public static <E, D> CoreLoad<E, D> setPagingCallback(final CoreLoad<E,    D> coreLoad,
                                                                  final LoaderCallback<D> callback) {
                return setPagingCallback(coreLoad, callback, null, getCurrentActivity());
            }

            /**
             * Sets callbacks for paged loading.
             *
             * @param coreLoad
             *        The {@code CoreLoad}
             *
             * @param callbacks
             *        The {@code LoaderCallbacks}
             *
             * @param <E>
             *        The type of error (if any)
             *
             * @param <D>
             *        The type of data to load
             *
             * @return  The {@code CoreLoad} passed as parameter
             */
            @SuppressWarnings("WeakerAccess")
            public static <E, D> CoreLoad<E, D> setPagingCallbacks(final CoreLoad       <E, D> coreLoad,
                                                                   final LoaderCallbacks<E, D> callbacks) {
                if (coreLoad == null) {
                    if (callbacks != null)
                        CoreLogger.logWarning("CoreLoad == null, can't set paging callbacks");
                    return null;
                }

                final List<BaseLoaderWrapper<?>> loaders = coreLoad.getLoaders();
                if (loaders == null || loaders.size() == 0) {
                    CoreLogger.log("setPagingCallbacks, empty loaders list");
                    return coreLoad;
                }

                if (loaders.size() != 1)
                    CoreLogger.logError("one and only loader should be defined");
                else {
                    final BaseLoaderWrapper<?> loader = loaders.iterator().next();
                    if (loader instanceof BaseResponseLoaderWrapper) {
                        @SuppressWarnings("unchecked")
                        final BaseResponseLoaderWrapper<?, ?, E, D> tmpLoader =
                                (BaseResponseLoaderWrapper<?, ?, E, D>) loader;
                        tmpLoader.setPagingCallbacks(callbacks);
                    }
                    else
                        CoreLogger.logError("can't set paging callbacks for " +
                                CoreLogger.getDescription(loader) + ", expected BaseResponseLoaderWrapper");
                }
                return coreLoad;
            }

            /**
             * Invalidates all {@link DataSource DataSource} associated with the given {@code CoreLoad} component.
             *
             * @param coreLoad
             *        The {@code CoreLoad}
             *
             * @param activity
             *        The {@code Activity}
             *
             * @param <E>
             *        The type of error (if any)
             *
             * @param <D>
             *        The type of data to load
             *
             * @return  The {@code CoreLoad} passed as parameter
             */
            @SuppressWarnings("WeakerAccess")
            public static <E, D> CoreLoad<E, D> invalidateDataSources(final CoreLoad<E, D> coreLoad,
                                                                      final Activity       activity) {
                if (coreLoad == null) {
                    CoreLogger.log("invalidateDataSources, CoreLoad == null");
                    return null;
                }

                final List<BaseLoaderWrapper<?>> loaders = coreLoad.getLoaders();
                if (loaders == null || loaders.size() == 0) {
                    CoreLogger.log("invalidateDataSources, empty loaders list");
                    return coreLoad;
                }

                CoreLogger.log("about to invalidate DataSources");

                for (final BaseLoaderWrapper baseLoaderWrapper: loaders)
                    baseLoaderWrapper.invalidateDataSource(activity);

                return coreLoad;
            }

            /**
             * Invalidates all {@link DataSource DataSource} associated with the given {@code CoreLoad} component.
             *
             * @param coreLoad
             *        The {@code CoreLoad}
             *
             * @param <E>
             *        The type of error (if any)
             *
             * @param <D>
             *        The type of data to load
             *
             * @return  The {@code CoreLoad} passed as parameter
             */
            @SuppressWarnings("unused")
            public static <E, D> CoreLoad<E, D> invalidateDataSources(final CoreLoad<E, D> coreLoad) {
                return invalidateDataSources(coreLoad, null);
            }
        }
    }
}
