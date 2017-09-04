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

package akha.yakhont.callback.lifecycle;

import akha.yakhont.Core;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.callback.BaseCallbacks.BaseCacheCallbacks;
import akha.yakhont.callback.BaseCallbacks.BaseLifecycleProceed;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extends the {@link BaseLifecycleProceed} class to provide the {@link Activity} lifecycle support.
 * For the moment supported lifecycle is similar to the {@link android.app.Application.ActivityLifecycleCallbacks}
 * but will likely expand in future releases.
 *
 * @see BaseActivityCallbacks
 *
 * @author akha
 */
public abstract class BaseActivityLifecycleProceed extends BaseLifecycleProceed {

    private static final String                             FORMAT_VALUE                = "%s == %d (%s)";

    private static final AtomicInteger                      sResumed                    = new AtomicInteger();
    private static final AtomicInteger                      sPaused                     = new AtomicInteger();
    private static final AtomicInteger                      sStarted                    = new AtomicInteger();
    private static final AtomicInteger                      sStopped                    = new AtomicInteger();

    private static final CurrentActivityHelper              sActivity                   = new CurrentActivityHelper();

    /**
     * Initialises a newly created {@code BaseActivityLifecycleProceed} object.
     */
    @SuppressWarnings("WeakerAccess")
    public BaseActivityLifecycleProceed() {
    }

    /**
     * Returns the current {@code Activity} (if any).
     *
     * @return  The current {@code Activity} (or null)
     */
    public static Activity getCurrentActivity() {
        return sActivity.get();
    }

    private static String getActivityName(@NonNull final Activity activity) {
        return Utils.getActivityName(activity);
    }

    private static void log(@NonNull final String info, final int value, @NonNull final String name) {
        CoreLogger.log(String.format(CoreLogger.getLocale(), FORMAT_VALUE, info, value, name));
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void log(@NonNull final Activity activity, @NonNull final String info) {
        log(getActivityName(activity), info);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void update(@NonNull final Activity activity, @NonNull final AtomicInteger value, @NonNull final String info) {
        log(info, value.incrementAndGet(), getActivityName(activity));
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static boolean isVisible() {
        return sStarted.get() > sStopped.get();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static boolean isInForeground() {
        return sResumed.get() > sPaused.get();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The {@link Activity} lifecycle.
     */
    public enum ActivityLifecycle {
        /** The activity is first created. */
        CREATED,
        /** The activity is becoming visible to the user. */
        STARTED,
        /** The activity will start interacting with the user. */
        RESUMED,
        /** The system is about to start resuming a previous activity. */
        PAUSED,
        /** The activity is no longer visible to the user. */
        STOPPED,
        /** The activity is destroyed. */
        DESTROYED,
        /** Called to retrieve per-instance state from an activity before being killed. */
        SAVE_INSTANCE_STATE
    }

    private static final Map<String, ActivityLifecycle>     CALLBACKS;

    static {
        final Map<String, ActivityLifecycle> callbacks = new HashMap<>();

        callbacks.put("onActivityCreated",                  ActivityLifecycle.CREATED);
        callbacks.put("onActivityStarted",                  ActivityLifecycle.STARTED);
        callbacks.put("onActivityResumed",                  ActivityLifecycle.RESUMED);
        callbacks.put("onActivityPaused",                   ActivityLifecycle.PAUSED);
        callbacks.put("onActivityStopped",                  ActivityLifecycle.STOPPED);
        callbacks.put("onActivityDestroyed",                ActivityLifecycle.DESTROYED);
        callbacks.put("onActivitySaveInstanceState",        ActivityLifecycle.SAVE_INSTANCE_STATE);

        CALLBACKS = Collections.unmodifiableMap(callbacks);
    }

    private static final Map<BaseActivityCallbacks, Set<ActivityLifecycle>>
                                                            sCallbacks                  = Utils.newMap();

    /**
     * Returns the collection of registered callbacks handlers.
     *
     * @return  The registered callbacks handlers
     */
    public static Collection<BaseActivityCallbacks> getCallbacks() {
        return sCallbacks.keySet();
    }

    /**
     * Registers the callbacks handler.
     *
     * @param callbacks
     *        The callbacks handler to register
     *
     * @return  {@code true} if the callbacks handler was successfully registered, {@code false} otherwise
     */
    @SuppressWarnings({"UnusedReturnValue", "ConstantConditions", "SameReturnValue"})
    public static boolean register(@NonNull final BaseActivityCallbacks callbacks) {
        return register(sCallbacks, callbacks, ActivityLifecycle.class, CALLBACKS);
    }

    /**
     * Unregisters the callbacks handler.
     *
     * @param callbacksClass
     *        The class of the callbacks handler to unregister
     *
     * @return  {@code true} if the callbacks handler was successfully unregistered, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean unregister(@NonNull final Class<? extends BaseActivityCallbacks> callbacksClass) {
        return unregister(sCallbacks, callbacksClass);
    }

    /**
     * Applies registered callbacks to the given activity.
     *
     * @param lifeCycle
     *        The activity state
     *
     * @param activity
     *        The activity to apply callbacks to
     *
     * @param state
     *        The additional information concerning the activity state
     */
    @SuppressWarnings("WeakerAccess")
    protected static void apply(@NonNull final ActivityLifecycle lifeCycle, @NonNull final Activity activity, final Bundle state) {

        switch (lifeCycle) {
            case STARTED:
            case RESUMED:
                sActivity.set(activity);
                break;
            case DESTROYED:
                sActivity.clear(activity);
                break;
        }

        final Boolean created;
        switch (lifeCycle) {
            case CREATED:       created = Boolean.TRUE;     break;
            case DESTROYED:     created = Boolean.FALSE;    break;
            default:            created = null;             break;
        }

        for (final BaseActivityCallbacks callbacks: sCallbacks.keySet())
            apply(sCallbacks, callbacks, created, lifeCycle, activity, new Runnable() {
                @Override
                public void run() {
                    apply(callbacks, lifeCycle, activity, state);
                }
            });
    }

    private static void apply(@NonNull final BaseActivityCallbacks callbacks,
                              @NonNull final ActivityLifecycle lifeCycle, @NonNull final Activity activity, final Bundle state) {

        CoreLogger.log(CoreLogger.Level.INFO, "proceeding: lifeCycle " + lifeCycle + ", " + callbacks.getClass().getName());

        switch (lifeCycle) {
            case CREATED:               callbacks.onActivityCreated          (activity, state);     break;
            case STARTED:               callbacks.onActivityStarted          (activity       );     break;
            case RESUMED:               callbacks.onActivityResumed          (activity       );     break;
            case PAUSED:                callbacks.onActivityPaused           (activity       );     break;
            case STOPPED:               callbacks.onActivityStopped          (activity       );     break;
            case DESTROYED:             callbacks.onActivityDestroyed        (activity       );     break;
            case SAVE_INSTANCE_STATE:   callbacks.onActivitySaveInstanceState(activity, state);     break;

            default:            // should never happen
                CoreLogger.logError("unknown lifeCycle state " + lifeCycle);
                break;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final String                             PREFIX                      = "subject to call by weaver - ";

    private static boolean                                  sActive;

    /**
     * Activates Yakhont Weaver (normally on devices with API version &lt;
     * {@link android.os.Build.VERSION_CODES#ICE_CREAM_SANDWICH ICE_CREAM_SANDWICH}).
     *
     * @param active
     *        {@code true} to activate Yakhont Weaver, {@code false} otherwise
     */
    @SuppressWarnings("SameParameterValue")
    public static void setActive(final boolean active) {
        sActive = active;
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given activity.
     * The lifecycle state is {@link ActivityLifecycle#CREATED CREATED}.
     *
     * @param activity
     *        The activity to apply callbacks to
     *
     * @param savedInstanceState
     *        The additional information concerning the activity state
     */
    @SuppressWarnings("unused")
    public static void onCreated(@NonNull final Activity activity, final Bundle savedInstanceState) {
        if (!sActive) return;

        log(activity, PREFIX + "onCreated callback");

        apply(ActivityLifecycle.CREATED, activity, savedInstanceState);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given activity.
     * The lifecycle state is {@link ActivityLifecycle#DESTROYED DESTROYED}.
     *
     * @param activity
     *        The activity to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onDestroyed(@NonNull final Activity activity) {
        if (!sActive) return;

        log(activity, PREFIX + "onDestroyed callback");

        apply(ActivityLifecycle.DESTROYED, activity, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given activity.
     * The lifecycle state is {@link ActivityLifecycle#SAVE_INSTANCE_STATE SAVE_INSTANCE_STATE}.
     *
     * @param activity
     *        The activity to apply callbacks to
     *
     * @param outState
     *        The additional information concerning the activity state
     */
    @SuppressWarnings("unused")
    public static void onSaveInstanceState(@NonNull final Activity activity, final Bundle outState) {
        if (!sActive) return;

        log(activity, PREFIX + "onSaveInstanceState callback");

        apply(ActivityLifecycle.SAVE_INSTANCE_STATE, activity, outState);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given activity.
     * The lifecycle state is {@link ActivityLifecycle#RESUMED RESUMED}.
     *
     * @param activity
     *        The activity to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onResumed(@NonNull final Activity activity) {
        if (!sActive) return;

        update(activity, sResumed, PREFIX + "sResumed");

        apply(ActivityLifecycle.RESUMED, activity, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given activity.
     * The lifecycle state is {@link ActivityLifecycle#PAUSED PAUSED}.
     *
     * @param activity
     *        The activity to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onPaused(@NonNull final Activity activity) {
        if (!sActive) return;

        update(activity, sPaused, PREFIX + "sPaused");

        apply(ActivityLifecycle.PAUSED, activity, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given activity.
     * The lifecycle state is {@link ActivityLifecycle#STARTED STARTED}.
     *
     * @param activity
     *        The activity to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onStarted(@NonNull final Activity activity) {
        if (!sActive) return;

        update(activity, sStarted, PREFIX + "sStarted");

        apply(ActivityLifecycle.STARTED, activity, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given activity.
     * The lifecycle state is {@link ActivityLifecycle#STOPPED STOPPED}.
     *
     * @param activity
     *        The activity to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onStopped(@NonNull final Activity activity) {
        if (!sActive) return;

        update(activity, sStopped, PREFIX + "sStopped");

        apply(ActivityLifecycle.STOPPED, activity, null /* ignored */);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseActivityLifecycleProceed} class to use on devices with
     * API version >= {@link android.os.Build.VERSION_CODES#ICE_CREAM_SANDWICH}.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static class ActivityLifecycleProceed extends BaseActivityLifecycleProceed implements Application.ActivityLifecycleCallbacks {

        /**
         * Initialises a newly created {@code ActivityLifecycleProceed} object.
         */
        public ActivityLifecycleProceed() {
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            log(activity, "onActivityCreated callback");

            apply(ActivityLifecycle.CREATED, activity, savedInstanceState);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityDestroyed(Activity activity) {
            log(activity, "onActivityDestroyed callback");

            apply(ActivityLifecycle.DESTROYED, activity, null /* ignored */);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            log(activity, "onActivitySaveInstanceState callback");

            apply(ActivityLifecycle.SAVE_INSTANCE_STATE, activity, outState);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityResumed(Activity activity) {
            update(activity, sResumed, "sResumed");

            apply(ActivityLifecycle.RESUMED, activity, null /* ignored */);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityPaused(Activity activity) {
            update(activity, sPaused, "sPaused");

            apply(ActivityLifecycle.PAUSED, activity, null /* ignored */);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityStarted(Activity activity) {
            update(activity, sStarted, "sStarted");

            apply(ActivityLifecycle.STARTED, activity, null /* ignored */);
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityStopped(Activity activity) {
            update(activity, sStopped, "sStopped");

            apply(ActivityLifecycle.STOPPED, activity, null /* ignored */);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseCacheCallbacks} class to provide the activity life cycle callbacks support.
     * For the moment supported callbacks are similar to the {@link android.app.Application.ActivityLifecycleCallbacks}
     * but will likely expand in future releases.
     * <br>By default all callbacks are empty.
     *
     * <p>Usage example (for more examples please refer to {@link akha.yakhont.callback.BaseCallbacks general Activity},
     * {@yakhont.link BaseFragmentLifecycleProceed.BaseFragmentCallbacks Fragment}
     * and {@link akha.yakhont.callback.BaseCallbacks#proceed(Object, Class) simple Activity} ones):
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * package com.mypackage;
     *
     * public class MyActivityCallbacks extends BaseCallbacks.BaseActivityCallbacks {
     *
     *     &#064;Override
     *     public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
     *
     *         // your code here (NOTE: you don't have to call activity.onActivityCreated() -
     *         //   it's already done by the Weaver)
     *     }
     * }
     * </pre>
     *
     * Annotate necessary Activities:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.callback.annotation.CallbacksInherited;
     *
     * &#064;CallbacksInherited(com.mypackage.MyActivityCallbacks.class)
     * public class MyActivity extends Activity {
     *     ...
     * }
     * </pre>
     *
     * And register your callbacks handler (see also {@link akha.yakhont.Core}):
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.Core;
     *
     * import com.mypackage.MyActivityCallbacks;
     *
     * public class MyApplication extends Application {
     *
     *     &#064;Override
     *     public void onCreate() {
     *         super.onCreate();
     *         ...
     *         Core.init(this);
     *
     *         BaseActivityLifecycleProceed.register(new MyActivityCallbacks());
     *         // OR
     *         // BaseActivityLifecycleProceed.register(
     *         //     (BaseActivityCallbacks) new MyActivityCallbacks().setForceProceed(true));
     *         // to apply callback handlers to ALL activities (annotated or not)
     *     }
     * }
     * </pre>
     *
     * Please refer to the {@link akha.yakhont.callback.BaseCallbacks} for more details.
     *
     * @see ActivityLifecycle
     */
    public static abstract class BaseActivityCallbacks extends BaseCacheCallbacks<Activity> {

        /**
         * Initialises a newly created {@code BaseActivityCallbacks} object.
         */
        public BaseActivityCallbacks() {
        }

        /** The callback for {@link Activity#onCreate}.            */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onActivityCreated          (@NonNull final Activity activity, final Bundle savedInstanceState) {}
        /** The callback for {@link Activity#onStart}.             */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onActivityStarted          (@NonNull final Activity activity                                 ) {}
        /** The callback for {@link Activity#onResume}.            */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onActivityResumed          (@NonNull final Activity activity                                 ) {}
        /** The callback for {@link Activity#onPause}.             */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onActivityPaused           (@NonNull final Activity activity                                 ) {}
        /** The callback for {@link Activity#onStop}.              */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onActivityStopped          (@NonNull final Activity activity                                 ) {}
        /** The callback for {@link Activity#onDestroy}.           */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onActivityDestroyed        (@NonNull final Activity activity                                 ) {}
        /** The callback for {@link Activity#onSaveInstanceState}. */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onActivitySaveInstanceState(@NonNull final Activity activity, final Bundle outState          ) {}
    }

    /**
     * Hides the keyboard when activity paused.
     */
    public static class HideKeyboardCallbacks extends BaseActivityCallbacks {

        /**
         * Initialises a newly created {@code HideKeyboardCallbacks} object.
         */
        public HideKeyboardCallbacks() {
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityPaused(@NonNull final Activity activity) {
            hideKeyboard(activity);
        }

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public static void hideKeyboard(@NonNull final Activity activity) {
            final InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager == null) {
                CoreLogger.logWarning("can not get InputMethodManager");
                return;
            }

            View currentFocus = activity.getWindow().getCurrentFocus();
            if (currentFocus == null)
                currentFocus = activity.getWindow().getDecorView().findFocus();
            if (currentFocus != null)
                inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            else
                CoreLogger.logWarning("can not get current focus");
        }
    }

    /**
     * Sets the screen orientation.
     */
    public static class OrientationCallbacks extends BaseActivityCallbacks {

        /**
         * Initialises a newly created {@code OrientationCallbacks} object.
         */
        public OrientationCallbacks() {
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        public void onActivityCreated(@NonNull final Activity activity, final Bundle savedInstanceState) {
            setOrientation(activity);
        }

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        public static void setOrientation(@NonNull final Activity activity) {
            final Core.Orientation orientation = Utils.getOrientation(activity);
            switch (orientation) {

                case LANDSCAPE:
                    CoreLogger.log("about to set orientation to " + orientation.name());
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    break;

                case PORTRAIT:
                    CoreLogger.log("about to set orientation to " + orientation.name());
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    break;

                case UNSPECIFIED:
                    CoreLogger.logWarning("unspecified orientation " + orientation.name());
                    break;

                default:
                    CoreLogger.logError("unknown orientation " + orientation.name());
                    break;
            }
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class CurrentActivityHelper {

        private final AtomicReference<WeakReference<Activity>>
                                                            mActivity                   = new AtomicReference<>();

        private void setActivity(final Activity activity) {
            mActivity.set(new WeakReference<>(activity));
        }

        public void set(final Activity activity) {
            if (!check(activity)) return;
            if (get(true) != activity) setActivity(activity);
        }

        public void clear(final Activity activity) {
            if (!check(activity)) return;
            if (get(true) == activity) setActivity(null);
        }

        public Activity get() {
            return get(false);
        }

        private Activity get(final boolean silent) {
            final WeakReference<Activity> weakReference = mActivity.get();
            final Activity activity = weakReference == null ? null: weakReference.get();
            if (!silent) check(activity);
            return activity;
        }

        private boolean check(final Activity activity) {
            if (activity == null) CoreLogger.logError("activity == null");
            return activity != null;
        }
    }
}
