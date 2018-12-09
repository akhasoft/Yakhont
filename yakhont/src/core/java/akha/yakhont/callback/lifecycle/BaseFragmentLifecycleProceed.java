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

package akha.yakhont.callback.lifecycle;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.callback.BaseCallbacks.BaseCacheCallbacks;
import akha.yakhont.callback.BaseCallbacks.BaseLifecycleProceed;
import akha.yakhont.debug.BaseFragment;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Extends the {@link BaseLifecycleProceed} class to provide the {@link Fragment} lifecycle support.
 * For the moment supported lifecycle is close to the {@link akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ActivityLifecycle}
 * but will likely expand in future releases.
 *
 * @see BaseFragmentCallbacks
 *
 * @author akha
 */
@TargetApi  (      Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public abstract class BaseFragmentLifecycleProceed extends BaseLifecycleProceed {

    /**
     * Initialises a newly created {@code BaseFragmentLifecycleProceed} object.
     */
    public BaseFragmentLifecycleProceed() {
    }

    /**
     * The {@link Fragment} lifecycle.
     */
    public enum FragmentLifecycle {
        /** The fragment is first created. */
        CREATED,
        /** The fragment is becoming visible to the user. */
        STARTED,
        /** The fragment will start interacting with the user. */
        RESUMED,
        /** The fragment is no longer interacting with the user. */
        PAUSED,
        /** The fragment is no longer visible to the user. */
        STOPPED,
        /** The fragment is destroyed. */
        DESTROYED,
        /** Called to ask the fragment to save its current dynamic state. */
        SAVE_INSTANCE_STATE,

        /** Tells the fragment that its activity has completed its own {@link android.app.Activity#onCreate(Bundle) Activity.onCreate()}. */
        ACTIVITY_CREATED
    }

    private static final Map<String, FragmentLifecycle>     CALLBACKS;

    static {
        final Map<String, FragmentLifecycle> callbacks = new HashMap<>();

        callbacks.put("onFragmentCreated",                  FragmentLifecycle.CREATED);
        callbacks.put("onFragmentStarted",                  FragmentLifecycle.STARTED);
        callbacks.put("onFragmentResumed",                  FragmentLifecycle.RESUMED);
        callbacks.put("onFragmentPaused",                   FragmentLifecycle.PAUSED);
        callbacks.put("onFragmentStopped",                  FragmentLifecycle.STOPPED);
        callbacks.put("onFragmentDestroyed",                FragmentLifecycle.DESTROYED);
        callbacks.put("onFragmentSaveInstanceState",        FragmentLifecycle.SAVE_INSTANCE_STATE);

        callbacks.put("onFragmentActivityCreated",          FragmentLifecycle.ACTIVITY_CREATED);

        CALLBACKS = Collections.unmodifiableMap(callbacks);
    }

    private static final Map<BaseFragmentCallbacks, Set<FragmentLifecycle>>
                                                            sCallbacks                  = Utils.newMap();

    /**
     * Returns the collection of registered callbacks handlers.
     *
     * @return  The registered callbacks handlers
     */
    @SuppressWarnings("unused")
    public static Collection<BaseFragmentCallbacks> getCallbacks() {
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
    @SuppressWarnings({"UnusedReturnValue", "ConstantConditions", "SameReturnValue", "unused"})
    public static boolean register(@NonNull final BaseFragmentCallbacks callbacks) {
        return register(callbacks, false);
    }

    /**
     * Registers the callbacks handler.
     *
     * @param callbacks
     *        The callbacks handler to register
     *
     * @param silent
     *        {@code true} to suppress 'no implemented callbacks' error reporting
     *
     * @return  {@code true} if the callbacks handler was successfully registered, {@code false} otherwise
     */
    @SuppressWarnings({"UnusedReturnValue", "ConstantConditions", "SameReturnValue", "unused"})
    public static boolean register(@NonNull final BaseFragmentCallbacks callbacks,
                                   final boolean silent) {
        return register(sCallbacks, callbacks, FragmentLifecycle.class, CALLBACKS,
                BaseFragmentCallbacks.class, silent);
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
    public static boolean unregister(@NonNull final Class<? extends BaseFragmentCallbacks> callbacksClass) {
        return unregister(sCallbacks, callbacksClass);
    }

    /**
     * Applies registered callbacks to the given fragment.
     *
     * @param lifeCycle
     *        The fragment state
     *
     * @param fragment
     *        The fragment to apply callbacks to
     *
     * @param state
     *        The additional information concerning the fragment state
     */
    @SuppressWarnings("WeakerAccess")
    protected static void apply(@NonNull final FragmentLifecycle lifeCycle, @NonNull final Fragment fragment, final Bundle state) {

        final Boolean created;
        switch (lifeCycle) {
            case CREATED:       created = Boolean.TRUE;     break;
            case DESTROYED:     created = Boolean.FALSE;    break;
            default:            created = null;             break;
        }

        for (final BaseFragmentCallbacks callbacks: sCallbacks.keySet())
            //noinspection Convert2Lambda
            apply(sCallbacks, callbacks, created, lifeCycle, fragment, new Runnable() {
                @Override
                public void run() {
                    apply(callbacks, lifeCycle, fragment, state);
                }
            });
    }

    private static void apply(@NonNull final BaseFragmentCallbacks callbacks,
                              @NonNull final FragmentLifecycle lifeCycle, @NonNull final Fragment fragment, final Bundle state) {

        CoreLogger.log(CoreLogger.Level.INFO, "proceeding: lifeCycle " + lifeCycle + ", " + callbacks.getClass().getName());

        switch (lifeCycle) {
            case CREATED:               callbacks.onFragmentCreated          (fragment, state);     break;
            case STARTED:               callbacks.onFragmentStarted          (fragment       );     break;
            case RESUMED:               callbacks.onFragmentResumed          (fragment       );     break;
            case PAUSED:                callbacks.onFragmentPaused           (fragment       );     break;
            case STOPPED:               callbacks.onFragmentStopped          (fragment       );     break;
            case DESTROYED:             callbacks.onFragmentDestroyed        (fragment       );     break;
            case SAVE_INSTANCE_STATE:   callbacks.onFragmentSaveInstanceState(fragment, state);     break;

            case ACTIVITY_CREATED:      callbacks.onFragmentActivityCreated  (fragment, state);     break;

            default:            // should never happen
                CoreLogger.logError("unknown lifeCycle state " + lifeCycle);
                break;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////

    private static final String                             PREFIX                      = "subject to call by weaver - ";

    private static boolean                                  sActive                     = true;

    /**
     * Activates Yakhont Weaver.
     *
     * @param active
     *        {@code true} to activate Yakhont Weaver, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static void setActive(final boolean active) {
        sActive = active;
    }

    private static String getFragmentName(@NonNull final Fragment fragment) {
        return BaseFragment.getFragmentName(fragment);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void log(@NonNull final Fragment fragment, @NonNull final String info) {
        log(getFragmentName(fragment), info);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#CREATED CREATED}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     *
     * @param savedInstanceState
     *        The additional information concerning the fragment state
     */
    @SuppressWarnings("unused")
    public static void onCreated(@NonNull final Fragment fragment, final Bundle savedInstanceState) {
        if (!sActive) return;

        log(fragment, PREFIX + "onCreated callback");

        apply(FragmentLifecycle.CREATED, fragment, savedInstanceState);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#DESTROYED DESTROYED}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onDestroyed(@NonNull final Fragment fragment) {
        if (!sActive) return;

        log(fragment, PREFIX + "onDestroyed callback");

        apply(FragmentLifecycle.DESTROYED, fragment, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#SAVE_INSTANCE_STATE SAVE_INSTANCE_STATE}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     *
     * @param outState
     *        The additional information concerning the fragment state
     */
    @SuppressWarnings("unused")
    public static void onSaveInstanceState(@NonNull final Fragment fragment, final Bundle outState) {
        if (!sActive) return;

        log(fragment, PREFIX + "onSaveInstanceState callback");

        apply(FragmentLifecycle.SAVE_INSTANCE_STATE, fragment, outState);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#RESUMED RESUMED}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onResumed(@NonNull final Fragment fragment) {
        if (!sActive) return;

        log(fragment, PREFIX + "onResumed callback");

        apply(FragmentLifecycle.RESUMED, fragment, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#PAUSED PAUSED}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onPaused(@NonNull final Fragment fragment) {
        if (!sActive) return;

        log(fragment, PREFIX + "onPaused callback");

        apply(FragmentLifecycle.PAUSED, fragment, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#STARTED STARTED}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onStarted(@NonNull final Fragment fragment) {
        if (!sActive) return;

        log(fragment, PREFIX + "onStarted callback");

        apply(FragmentLifecycle.STARTED, fragment, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#STOPPED STOPPED}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onStopped(@NonNull final Fragment fragment) {
        if (!sActive) return;

        log(fragment, PREFIX + "onStopped callback");

        apply(FragmentLifecycle.STOPPED, fragment, null /* ignored */);
    }

    /**
     * Called by the Yakhont Weaver to apply registered callbacks to the given fragment.
     * The lifecycle state is {@link FragmentLifecycle#ACTIVITY_CREATED ACTIVITY_CREATED}.
     *
     * @param fragment
     *        The fragment to apply callbacks to
     */
    @SuppressWarnings("unused")
    public static void onActivityCreated(@NonNull final Fragment fragment, final Bundle savedInstanceState) {
        if (!sActive) return;

        log(fragment, PREFIX + "onActivityCreated callback");

        apply(FragmentLifecycle.ACTIVITY_CREATED, fragment, savedInstanceState);
    }

    /**
     * Extends the {@link BaseCacheCallbacks} class to provide the fragment life cycle callbacks support.
     * For the moment supported callbacks are close to the {@link android.app.Application.ActivityLifecycleCallbacks}
     * but will likely expand in future releases.
     * <br>By default all callbacks are empty.
     *
     * <p>Usage example (for more examples please refer to {@link akha.yakhont.callback.BaseCallbacks general Activity},
     * {@link akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks Activity}
     * and {@link akha.yakhont.callback.BaseCallbacks#proceed(Object, Class) simple Activity} ones):
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * package com.yourpackage;
     *
     * public class YourFragmentCallbacks extends BaseCallbacks.BaseFragmentCallbacks {
     *
     *     &#064;Override
     *     public void onFragmentCreated(Fragment fragment, Bundle savedInstanceState) {
     *
     *         // your code here (NOTE: you don't have to call fragment.onFragmentCreated() -
     *         //   it's already done by the Weaver)
     *     }
     * }
     * </pre>
     *
     * Annotate necessary Fragments:
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.callback.annotation.CallbacksInherited;
     *
     * &#064;CallbacksInherited(com.yourpackage.YourFragmentCallbacks.class)
     * public class YourFragment extends Fragment {
     *     ...
     * }
     * </pre>
     *
     * And register your callbacks handler (see also {@link akha.yakhont.Core}):
     *
     * <p><pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.Core;
     *
     * import com.yourpackage.YourFragmentCallbacks;
     *
     * public class YourApplication extends Application {
     *
     *     &#064;Override
     *     public void onCreate() {
     *         super.onCreate();
     *
     *         BaseFragmentLifecycleProceed.register(new YourFragmentCallbacks());
     *         // or
     *         // BaseFragmentLifecycleProceed.register(
     *         //     (BaseFragmentCallbacks) new YourFragmentCallbacks().setForceProceed(true));
     *         // to apply callback handlers to all fragments (annotated or not)
     *     }
     * }
     * </pre>
     *
     * Please refer to the {@link akha.yakhont.callback.BaseCallbacks} for more details.
     *
     * @see FragmentLifecycle
     */
    @SuppressWarnings("unused")
    public static abstract class BaseFragmentCallbacks extends BaseCacheCallbacks<Fragment> {

        /**
         * Initialises a newly created {@code BaseFragmentCallbacks} object.
         */
        public BaseFragmentCallbacks() {
        }

        /** The callback for {@link Fragment#onCreate}.            */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentCreated          (@NonNull final Fragment fragment, final Bundle savedInstanceState) {}
        /** The callback for {@link Fragment#onStart}.             */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentStarted          (@NonNull final Fragment fragment                                 ) {}
        /** The callback for {@link Fragment#onResume}.            */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentResumed          (@NonNull final Fragment fragment                                 ) {}
        /** The callback for {@link Fragment#onPause}.             */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentPaused           (@NonNull final Fragment fragment                                 ) {}
        /** The callback for {@link Fragment#onStop}.              */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentStopped          (@NonNull final Fragment fragment                                 ) {}
        /** The callback for {@link Fragment#onDestroy}.           */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentDestroyed        (@NonNull final Fragment fragment                                 ) {}
        /** The callback for {@link Fragment#onSaveInstanceState}. */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentSaveInstanceState(@NonNull final Fragment fragment, final Bundle outState          ) {}

        /** The callback for {@link Fragment#onActivityCreated}.   */ @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})
        public void onFragmentActivityCreated  (@NonNull final Fragment fragment, final Bundle savedInstanceState) {}
    }

    /** @exclude */@SuppressWarnings("JavaDoc")
    public static class ValidateFragmentCallbacks extends BaseFragmentCallbacks {
    }
}
