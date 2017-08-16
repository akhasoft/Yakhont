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

package akha.yakhont.location;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.ConfigurationChangedListener;
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CorePermissions;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ActivityLifecycle;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.technology.rx.BaseRx.LocationRx;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;

import dagger.Lazy;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Provider;

/**
 * Extends the {@link BaseActivityCallbacks} class to provide location APIs support. For example, in Activity:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import akha.yakhont.callback.annotation.CallbacksInherited;
 * import akha.yakhont.location.LocationCallbacks;
 * import akha.yakhont.location.LocationCallbacks.LocationListener;
 *
 * import android.location.Location;
 *
 * &#064;CallbacksInherited(LocationCallbacks.class)
 * public class MyActivity extends Activity implements LocationListener {
 *
 *     &#064;Override
 *     public void onLocationChanged(Location location, Date date) {
 *         // your code here
 *     }
 *
 *     public Location getLocation() {
 *         return LocationCallbacks.getLocationCallbacks(this).getCurrentLocation();
 *     }
 * }
 * </pre>
 *
 * @author akha
 */
public class LocationCallbacks extends BaseActivityCallbacks implements ConfigurationChangedListener {

    private   static final String                                   TAG                 = Utils.getTag(LocationCallbacks.class);

    private   static final String                                   ARG_DECISION        = TAG + ".decision";

    private   static final int                                      DELAY               = 750;    //ms

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static final AtomicReference<WeakReference<Activity>> sActivity           = new AtomicReference<>();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        Lazy<LocationClient>                           mLocationClient;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Object                                         mClientLock         = new Object();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  AtomicInteger                                  mStartStopCounter   = new AtomicInteger();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  AtomicInteger                                  mPauseResumeCounter = new AtomicInteger();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Map<ActivityLifecycle, ScheduledFuture<?>>     mFutures            = Collections.synchronizedMap(
            new EnumMap<ActivityLifecycle, ScheduledFuture<?>>(ActivityLifecycle.class));

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  ScheduledExecutorService
                                                                    mExecutor           = Executors.newSingleThreadScheduledExecutor();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Object                                         mLock               = new Object();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        Provider<BaseDialog>                           mToastProvider;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        Provider<BaseDialog>                           mAlertProvider;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        BaseDialog                                     mAlert;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Map<Activity, Set<LocationRx>>                 mRx                 = Utils.newWeakMap();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static Boolean                                        sAccessToLocation;

    /**
     * Activity should implement this interface for receiving notifications when the location has changed.
     */
    public interface LocationListener {

        /**
         * Called when the location has changed.
         *
         * @param location
         *        The updated location
         *
         * @param date
         *        The last update time
         */
        void onLocationChanged(Location location, @SuppressWarnings("UnusedParameters") Date date);
    }

    /**
     * The location API.
     */
    public interface LocationClient {

        /**
         * Returns the current location.
         *
         * @return  The current location
         */
        @SuppressWarnings("unused")
        Location getCurrentLocation();

        /**
         * Returns the location's last update time.
         *
         * @return  The last update time
         */
        @SuppressWarnings("unused")
        Date getLastUpdateTime();

        /**
         * Sets the {@code locationCallbacks} which is the owner of this client.
         *
         * @param locationCallbacks
         *        The {@code locationCallbacks}
         */
        void setLocationCallbacks(LocationCallbacks locationCallbacks);

        /**
         * Callback which is called from {@link Activity#onCreate}.
         *
         * @param activity
         *        The Activity
         *
         * @param savedInstanceState
         *        The last saved instance state of the Activity, or null
         */
        @SuppressWarnings("UnusedParameters")
        void onCreate(Activity activity, Bundle savedInstanceState);

        /**
         * Callback which is called from {@link Activity#onSaveInstanceState}.
         *
         * @param activity
         *        The Activity
         *
         * @param savedInstanceState
         *        The last saved instance state of the Activity, or null
         */
        @SuppressWarnings("UnusedParameters")
        void onSaveInstanceState(Activity activity, Bundle savedInstanceState);

        /**
         * Callback which is called from {@link Activity#onStart}.
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings("UnusedParameters")
        void onStart(Activity activity);

        /**
         * Callback which is called from {@link Activity#onResume}.
         *
         * @param activity
         *        The Activity
         */
        void onResume(Activity activity);

        /**
         * Callback which is called from {@link Activity#onPause}.
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings("UnusedParameters")
        void onPause(Activity activity);

        /**
         * Callback which is called from {@link Activity#onStop}.
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings("UnusedParameters")
        void onStop(Activity activity);

        /**
         * Callback which is called from {@link Activity#onDestroy}.
         *
         * @param activity
         *        The Activity
         */
        @SuppressWarnings({"EmptyMethod", "UnusedParameters"})
        void onDestroy(Activity activity);

        /**
         * See {@link Activity#onActivityResult Activity.onActivityResult()}.
         *
         * @param activity
         *        The Activity
         *
         * @param requestCode
         *        The request code originally supplied to {@link Activity#startActivityForResult(Intent, int)},
         *        allowing you to identify who this result came from
         *
         * @param resultCode
         *        The integer result code returned by the child activity through its {@link Activity#setResult(int)}
         *
         * @param data
         *        The Intent, which can return result data to the caller
         *
         * @return  {@code true} if {@code requestCode} was handled, {@code false} otherwise
         */
        @SuppressWarnings("UnusedParameters")
        boolean onActivityResult(Activity activity, RequestCodes requestCode, int resultCode, Intent data);
    }

    /**
     * Initialises a newly created {@code LocationCallbacks} object.
     */
    public LocationCallbacks() {
    }

    /**
     * Returns the location client.
     *
     * @return  The {@code LocationClient}
     */
    @SuppressWarnings("WeakerAccess")
    public LocationClient getLocationClient() {
        return getLocationClient(false);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected LocationClient getLocationClient(final boolean silent) {
        synchronized (mClientLock) {
            final LocationClient locationClient = mLocationClient == null ? null: mLocationClient.get();
            if (locationClient == null && !silent) CoreLogger.logWarning("locationClient == null");
            return locationClient;
        }
    }

    /**
     * Clears the location client.
     */
    @SuppressWarnings("WeakerAccess")
    protected void clearLocationClient() {
        synchronized (mClientLock) {
            mLocationClient = null;
        }
    }

    /**
     * Sets the location client (via {@link akha.yakhont.technology.Dagger2 Dagger2}).
     */
    @SuppressWarnings("WeakerAccess")
    protected void setLocationClient() {
        synchronized (mClientLock) {
            if (mLocationClient == null) mLocationClient = Core.getDagger().getLocationClient();
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void onRegister() {
        Core.register(this);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void onUnregister() {
        Core.unregister(this);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onChangedConfiguration(Configuration newConfig) {
        if (mAlert == null) return;

        mAlert.stop();
        mAlert = null;
    }

    private void addTask(@NonNull final ActivityLifecycle lifecycle, @NonNull final Runnable runnable) {
        synchronized (mLock) {
            addTaskNotSync(lifecycle, runnable);
        }
    }

    private void addTaskNotSync(@NonNull final ActivityLifecycle lifecycle, @NonNull final Runnable runnable) {
        final ScheduledFuture<?> future = mFutures.get(lifecycle);
        if (future != null && !future.isDone() && !future.isCancelled()) {
            CoreLogger.logWarning("already scheduled " + lifecycle);
            return;
        }

        mFutures.put(lifecycle, mExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Exception e) {
                    CoreLogger.log("failed " + lifecycle, e);
                }
            }
        }, DELAY, TimeUnit.MILLISECONDS));
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityCreated(@NonNull final Activity activity, final Bundle savedInstanceState) {
        if (mAlertProvider == null) mAlertProvider = Core.getDagger().getAlertLocation();
        if (mToastProvider == null) mToastProvider = Core.getDagger().getToastLong();

        // we need it here to allow user set location client parameters
        if (getLocationClient(true) == null) setLocationClient();

        final LocationClient locationClient = getLocationClient();
        if (locationClient != null)
            locationClient.setLocationCallbacks(LocationCallbacks.this);

        if (sAccessToLocation == null) sAccessToLocation = getLocationAccessDecision(activity);
        if (sAccessToLocation == null) {
            if (showConfirmationDialog(activity)) return;

            CoreLogger.logError("can not ask user is access to location allowed - so let's try to provide it");
            sAccessToLocation = true;
        }

        onCreatedHelper(activity, savedInstanceState, false);
    }

    private void onCreatedHelper(@NonNull final Activity activity, final Bundle savedInstanceState,
                                 boolean fromDialog) {
        if (!isAccessToLocationAllowed()) return;

        final String          permission        = Manifest.permission.ACCESS_FINE_LOCATION;
        final boolean         granted           = CorePermissions.check(activity, permission);

        if (!granted)         sAccessToLocation = false;

        if (!fromDialog) fromDialog   = !granted;
        final boolean fromDialogFinal = fromDialog;

        final boolean result = new CorePermissions.RequestBuilder(activity, permission)
                .setOnGranted(new Runnable() {
                    @Override
                    public void run() {
                        CoreLogger.log("LocationClient.onCreate()");

                        sAccessToLocation = true;

                        final LocationClient locationClient = getLocationClient();
                        if (locationClient != null)
                            locationClient.onCreate(activity, savedInstanceState);

                        if (!fromDialogFinal) return;

                        onActivityStarted(activity);
                        onActivityResumed(activity);
                    }
                })
                .request();
        CoreLogger.log(permission + " request result: " + (result ? "already granted": "not granted yet"));
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivitySaveInstanceState(@NonNull final Activity activity, final Bundle outState) {
        if (!isAccessToLocationAllowed()) return;

        final LocationClient locationClient = getLocationClient();
        if (locationClient != null) locationClient.onSaveInstanceState(activity, outState);
    }

    /**
     * Sets whether the access to location should be allowed or not. If not set,
     * the location access confirmation dialog will be displayed.
     *
     * @param value
     *        The value to set
     */
    @SuppressWarnings({"SameParameterValue", "unused"})
    public static void allowAccessToLocation(final Boolean value) {
        sAccessToLocation = value;
    }

    /**
     * Checks whether the access to location was allowed or not.
     *
     * @return  {@code true} if access to location was allowed, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    protected static boolean isAccessToLocationAllowed() {
        return sAccessToLocation != null && sAccessToLocation;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityStarted(@NonNull final Activity activity) {
        sActivity.set(new WeakReference<>(activity));

        if (isAccessToLocationAllowed() && mStartStopCounter.incrementAndGet() == 1)

            addTask(ActivityLifecycle.STARTED, new Runnable() {
                @Override
                public void run() {
                    final LocationClient locationClient = getLocationClient();
                    if (locationClient != null && mStartStopCounter.get() == 1) {

                        CoreLogger.log("LocationClient.onStart()");
                        locationClient.onStart(activity);
                    }
                }
            });
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityResumed(@NonNull final Activity activity) {
        sActivity.set(new WeakReference<>(activity));

        if (isAccessToLocationAllowed() && mPauseResumeCounter.incrementAndGet() == 1)

            addTask(ActivityLifecycle.RESUMED, new Runnable() {
                @Override
                public void run() {
                    final LocationClient locationClient = getLocationClient();
                    if (locationClient != null && mPauseResumeCounter.get() == 1) {

                        CoreLogger.log("LocationClient.onResume()");
                        locationClient.onResume(activity);
                    }
                }
            });
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityPaused(@NonNull final Activity activity) {
        if (isAccessToLocationAllowed() && mPauseResumeCounter.decrementAndGet() == 0)

            addTask(ActivityLifecycle.PAUSED, new Runnable() {
                @Override
                public void run() {
                    final LocationClient locationClient = getLocationClient();
                    if (locationClient != null && mPauseResumeCounter.get() == 0) {

                        CoreLogger.log("LocationClient.onPause()");
                        locationClient.onPause(activity);
                    }
                }
            });
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityStopped(@NonNull final Activity activity) {
        if (isAccessToLocationAllowed() && mStartStopCounter.decrementAndGet() == 0)

            addTask(ActivityLifecycle.STOPPED, new Runnable() {
                @Override
                public void run() {
                    final LocationClient locationClient = getLocationClient();
                    if (locationClient != null && mStartStopCounter.get() == 0) {

                        CoreLogger.log("LocationClient.onStop()");
                        locationClient.onStop(activity);
                    }
                }
            });
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityDestroyed(@NonNull final Activity activity) {
        if (sActivity.get().get() == activity) sActivity.set(new WeakReference<Activity>(null));

        if (isAccessToLocationAllowed() && getProceeded().size() == 0)

            addTask(ActivityLifecycle.DESTROYED, new Runnable() {
                @Override
                public void run() {
                    final LocationClient locationClient = getLocationClient();
                    if (locationClient != null && getProceeded().size() == 0) {

                        CoreLogger.log("LocationClient.onDestroy()");
                        locationClient.onDestroy(activity);

                        clearLocationClient();
                    }
                }
            });

        final Set<LocationRx> setLocationRx = getRx(activity);
        if (setLocationRx == null) return;

        for (final LocationRx locationRx: setLocationRx)
            locationRx.cleanup();

        mRx.remove(activity);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Activity getActivity() {
        final Activity activity = sActivity.get().get();
        if (activity == null)
            CoreLogger.logError("activity == null");
        return activity;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void onLocationChanged(final Location location, final Date date) {
        for (final Activity activity: getProceeded())
            if (activity instanceof LocationListener)
                ((LocationListener) activity).onLocationChanged(location, date);

        for (final Set<LocationRx> setLocationRx: mRx.values())
            for (final LocationRx locationRx: setLocationRx)
                locationRx.onResult(location);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void onLocationError(final Throwable throwable) {
        for (final Set<LocationRx> setLocationRx: mRx.values())
            for (final LocationRx locationRx: setLocationRx)
                locationRx.onError(throwable);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public void onLocationError(final String error) {
        for (final Set<LocationRx> setLocationRx: mRx.values())
            for (final LocationRx locationRx: setLocationRx)
                locationRx.onError(error);
    }

    /**
     * Registers Rx component to be notified about location changes.
     *
     * @param locationRx
     *        The component to register
     *
     * @return  {@code true} if registration was successful, {@code false} otherwise
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean register(final Activity activity, final LocationRx locationRx) {
        if (!checkData(activity, locationRx)) return false;

        if (!mRx.containsKey(activity)) mRx.put(activity, Utils.<LocationRx>newSet());

        final boolean result = mRx.get(activity).add(locationRx);
        CoreLogger.log(result ? Level.DEBUG: Level.ERROR, "register Rx: result == " + result);
        return result;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkData(final Activity activity, final LocationRx locationRx) {
        if (activity   == null) CoreLogger.logError("activity == null");
        if (locationRx == null) CoreLogger.logError("locationRx == null");
        return activity != null && locationRx != null;
    }

    /**
     * Removes a Rx component that was previously registered with {@link #register(Activity, akha.yakhont.technology.rx.BaseRx.LocationRx)}.
     *
     * @param locationRx
     *        The component to remove
     *
     * @return  {@code true} if component removing was successful, {@code false} otherwise
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean unregister(final Activity activity, final LocationRx locationRx) {
        if (!checkData(activity, locationRx)) return false;

        final Set<LocationRx> setLocationRx = getRx(activity);
        if (setLocationRx == null) return false;

        final boolean result = setLocationRx.remove(locationRx);
        CoreLogger.log(result ? Level.DEBUG: Level.WARNING, "unregister Rx: result == " + result);
        return result;
    }

    private Set<LocationRx> getRx(@NonNull final Activity activity) {
        final Set<LocationRx> setLocationRx = mRx.get(activity);
        if (setLocationRx == null)
            CoreLogger.logWarning("there's no registered locationRx for Activity " + activity);
        return setLocationRx;
    }

    /**
     * Returns the current location.
     *
     * @return  The current location
     */
    @SuppressWarnings("WeakerAccess")
    public Location getCurrentLocation() {
        final LocationClient locationClient = getLocationClient();
        return locationClient == null ? null: locationClient.getCurrentLocation();
    }

    /**
     * Returns the current location.
     *
     * @param activity
     *        The activity
     *
     * @return  The current location
     */
    public static Location getCurrentLocation(final Activity activity) {
        if (activity == null) {
            CoreLogger.logWarning("activity == null");
            return null;
        }
        LocationCallbacks locationCallbacks = getLocationCallbacks(activity);
        return locationCallbacks == null ? null: locationCallbacks.getCurrentLocation();
    }

    /**
     * Returns the location's last update time.
     *
     * @return  The last update time
     */
    @SuppressWarnings("unused")
    public Date getLastUpdateTime() {
        final LocationClient locationClient = getLocationClient();
        return locationClient == null ? null: locationClient.getLastUpdateTime();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected Boolean getLocationAccessDecision(@NonNull final Activity activity) {
        final SharedPreferences preferences = Utils.getPreferences(activity);
        final Boolean result = preferences.contains(ARG_DECISION) ?
                preferences.getBoolean(ARG_DECISION, false): null;

        CoreLogger.log("getLocationAccessDecision: result " + result);
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public void setLocationAccessDecision(final int resultCode, @NonNull final Activity activity) {
        CoreLogger.log("resultCode " + resultCode);

        mAlert = null;

        switch (resultCode) {
            case Activity.RESULT_CANCELED:
                return;
            case Activity.RESULT_OK:
            case Activity.RESULT_FIRST_USER:
                break;
            default:
                CoreLogger.logWarning("unknown result code " + resultCode);
                break;
        }

        // in case of Snackbar decision is always true
        final boolean decision = resultCode == Activity.RESULT_OK;
        sAccessToLocation = decision;

        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                Utils.getPreferences(activity).edit().putBoolean(ARG_DECISION, decision).apply();
            }
        });

        if (decision) onCreatedHelper(activity, null, true);
    }

    /**
     * Clears the saved result of the location access confirmation dialog.
     *
     * @param activity
     *        The activity
     */
    @SuppressWarnings("unused")
    public static void clearLocationAccessDecision(@NonNull final Activity activity) {
        sAccessToLocation = null;
        Utils.getPreferences(activity).edit().remove(ARG_DECISION).apply();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected boolean showConfirmationDialog(@NonNull final Activity activity) {
        if (mAlert != null)
            CoreLogger.logError("alert dialog is already exists");

        mAlert = mAlertProvider.get();

        final boolean result = mAlert.start(activity, null, null);
        if (!result)
            CoreLogger.logError("can not start alert dialog");

        return result;
    }

    /**
     * Returns the {@code LocationCallbacks} object for the given activity.
     *
     * @param activity
     *        The activity
     *
     * @return  The {@code LocationCallbacks} object
     */
    @SuppressWarnings("WeakerAccess")
    public static LocationCallbacks getLocationCallbacks(@NonNull final Activity activity) {
        for (final BaseActivityCallbacks callbacks: BaseActivityLifecycleProceed.getCallbacks())
            if (callbacks instanceof LocationCallbacks && callbacks.getProceeded().contains(activity))
                return (LocationCallbacks) callbacks;

        CoreLogger.logWarning("locationCallbacks == null");
        return null;
    }
    
    /**
     * Converts latitude or longitude to the DMS format (e.g. 5°31′08″ N).
     *
     * @param coordinate
     *        The latitude or longitude
     *
     * @param isLongitude
     *        {@code true} if coordinate is longitude, {@code false} otherwise
     *
     * @param context
     *        The {@code Context}
     *
     * @return  The coordinate in the DMS format
     */
    @SuppressWarnings("WeakerAccess")
    public static String toDms(double coordinate, final boolean isLongitude, @NonNull final Context context) {
        if (Math.abs(coordinate) > 90 * (isLongitude ? 2: 1) || Double.isNaN(coordinate))
            throw new IllegalArgumentException("coordinate = " + coordinate);
        
        final int degrees = (int) Math.floor(coordinate);
        
        coordinate -= degrees;
        coordinate *= 60;
        
        final int minutes = (int) Math.floor(coordinate);

        coordinate -= minutes;
        coordinate *= 60;
        
        final int seconds = (int) Math.round(coordinate);

        return String.format(CoreLogger.getLocale(), "%d°%02d'%02d\" %s", degrees, minutes, seconds, 
            isLongitude ? (coordinate < 0 ?
                    context.getString(akha.yakhont.R.string.yakhont_location_w)  :
                    context.getString(akha.yakhont.R.string.yakhont_location_e)) : (coordinate < 0 ?
                    context.getString(akha.yakhont.R.string.yakhont_location_s)  :
                    context.getString(akha.yakhont.R.string.yakhont_location_n)));
    }
    
    /**
     * Converts {@code Location} to the DMS format (e.g. 5°31′08″ N 87°04′18″ W).
     *
     * @param location
     *        The {@code Location}
     *
     * @param context
     *        The {@code Context}
     *
     * @return  The location in the DMS format
     */
    @SuppressWarnings("unused")
    public static String toDms(final Location location, @NonNull final Context context) {
        return toDms(location, context, null, null);
    }

    /**
     * Converts {@code Location} to the DMS format (e.g. 5°31′08″ N 87°04′18″ W).
     *
     * @param location
     *        The {@code Location}
     *
     * @param context
     *        The {@code Context}
     *
     * @param message
     *        The message to return, e.g. "Location: %s"
     *
     * @param defValue
     *        The string to return if location is null
     *
     * @return  The location in the DMS format
     */
    @SuppressWarnings("unused")
    public static String toDms(final Location location, @NonNull final Context context,
                               final String message, final String defValue) {
        final Locale locale = CoreLogger.getLocale();
        return String.format(locale, message != null ? message:
                        context.getString(akha.yakhont.R.string.yakhont_location_msg),
                location != null ? String.format(locale, "%s %s",
                        toDms(location.getLatitude(), false, context),
                        toDms(location.getLongitude(), true, context)):
                        defValue != null ? defValue:
                                context.getString(akha.yakhont.R.string.yakhont_location_msg_na));
    }

    /**
     * Called by the Yakhont Weaver. See {@link Activity#onActivityResult Activity.onActivityResult()}.
     */
    @SuppressWarnings({"UnusedParameters", "unused"})
    public static void onActivityResult(@NonNull final Activity activity, final int requestCode,
                                        final int resultCode, final Intent data) {
        Utils.onActivityResult("LocationCallbacks", activity, requestCode, resultCode, data);

        final LocationCallbacks locationCallbacks = getLocationCallbacks(activity);
        if (locationCallbacks == null) {
            CoreLogger.log("nothing to do");
            return;
        }

        // it's not needed to call proceed(activity) 'cause the check is already done - when activity was added to collection

        final LocationClient locationClient = locationCallbacks.getLocationClient();
        if (locationClient == null) return;

        final RequestCodes code = Utils.getRequestCode(requestCode);
        if (locationClient.onActivityResult(activity, code, resultCode, data)) return;

        switch (code) {
            case LOCATION_ALERT:
                locationCallbacks.setLocationAccessDecision(resultCode, activity);
                break;

            default:
                CoreLogger.logWarning("unknown request code " + code);
                break;
        }
    }

    /**
     * Called by the Yakhont Weaver; prevents crashing on emulators without Google APIs.
     *
     * @param activity
     *        The {@code Activity}
     *
     * @param exception
     *        The {@code Exception} to handle
     */
    @SuppressWarnings({"UnusedParameters", "unused"})
    public static void startActivityForResultExceptionHandler(@NonNull final Activity activity,
                                                              @NonNull final RuntimeException exception) {

        final LocationCallbacks locationCallbacks = getLocationCallbacks(activity);
        if (locationCallbacks == null) throw exception;

        // it's not needed to call proceed(activity) 'cause the check is already done - when activity was added to collection

        CoreLogger.log("failed", exception);

        locationCallbacks.mToastProvider.get().start(activity, activity.getString(
                akha.yakhont.R.string.yakhont_location_error), null);
    }
}
