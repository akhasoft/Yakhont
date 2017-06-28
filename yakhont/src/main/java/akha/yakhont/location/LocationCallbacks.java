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
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ActivityLifecycle;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.technology.rx.BaseRx.LocationRx;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
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
 *
 * import android.location.Location;
 *
 * &#064;CallbacksInherited(LocationCallbacks.class)
 * public class MyActivity extends Activity implements LocationCallbacks.LocationListener {
 *
 *     &#064;Override
 *     public void onLocationChanged(Location location, Date date) {
 *         ...
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

    private static final String             TAG                                      = Utils.getTag(LocationCallbacks.class);

    private static final String             ARG_DECISION                             = TAG + ".decision";

    private static final int                DELAY                                    = 750;    //ms

    private static final AtomicReference<WeakReference<Activity>>
                                            sActivity                                = new AtomicReference<>();

    private LocationClient                  mLocationClient;
    private final Object                    mClientLock                              = new Object();

    private final AtomicInteger             mStartStopCounter                        = new AtomicInteger();
    private final AtomicInteger             mPauseResumeCounter                      = new AtomicInteger();

    private final Map<ActivityLifecycle, ScheduledFuture<?>>
                                            mFutures                                 = Collections.synchronizedMap(
            new EnumMap<ActivityLifecycle, ScheduledFuture<?>>(ActivityLifecycle.class));

    private final ScheduledExecutorService  mExecutor                                = Executors.newSingleThreadScheduledExecutor();
    private final Object                    mLock                                    = new Object();

    private Provider<BaseDialog>            mToast;

    private Provider<BaseDialog>            mAlertProvider;
    private BaseDialog                      mAlert;

    private final Set<LocationRx>           mRx                                      = Utils.newSet();

    private static Boolean                  sAccessToLocation;

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
         * Callback which is called from {@link Activity#onCreate}.
         *
         * @param activity
         *        The Activity
         *
         * @param savedInstanceState
         *        The last saved instance state of the Activity, or null
         *
         * @param locationCallbacks
         *        The {@code LocationCallbacks}
         */
        @SuppressWarnings("UnusedParameters")
        void onCreate(Activity activity, Bundle savedInstanceState, LocationCallbacks locationCallbacks);

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
         * Connects the client.
         */
        void connect();

        /**
         * Clears resolving error.
         */
        void clearResolvingError();
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
    protected LocationClient getLocationClient() {
        synchronized (mClientLock) {
            return mLocationClient;
        }
    }

    /**
     * Sets the location client.
     *
     * @param locationClient
     *        The {@code LocationClient}
     */
    @SuppressWarnings("WeakerAccess")
    protected void setLocationClient(@SuppressWarnings("SameParameterValue") final LocationClient locationClient) {
        synchronized (mClientLock) {
            mLocationClient = locationClient;
        }
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
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
     * Sets whether the access to location should be allowed or not. If not set, the confirmation dialog will be displayed.
     *
     * @param value
     *        The value to set
     */
    @SuppressWarnings({"SameParameterValue", "unused"})
    public static void allowAccessToLocation(final Boolean value) {
        sAccessToLocation = value;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityCreated(@NonNull final Activity activity, final Bundle savedInstanceState) {
        if (mAlertProvider == null) mAlertProvider = Core.getDagger().getAlertLocation();
        if (mToast         == null) mToast         = Core.getDagger().getToastLong();

        if (sAccessToLocation == null) sAccessToLocation = getLocationAccessDecision(activity);
        if (sAccessToLocation == null && getLocationClient() == null) {
            if (showConfirmationDialog(activity)) return;
            CoreLogger.logError("can not ask user is access to location allowed - so let's try to provide it");
        }

        onCreatedHelper(activity, savedInstanceState, false);
    }

    private void onCreatedHelper(@NonNull final Activity activity, final Bundle savedInstanceState, final boolean fromDialog) {
        if (sAccessToLocation != null && !sAccessToLocation) return;

        if (getLocationClient() == null) {
            setLocationClient();

            CoreLogger.log("LocationClient.onCreate()");
            getLocationClient().onCreate(activity, savedInstanceState, LocationCallbacks.this);
        }

        if (!fromDialog) return;

        onActivityStarted(activity);
        onActivityResumed(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivitySaveInstanceState(@NonNull final Activity activity, final Bundle outState) {
        final LocationClient locationClient = getLocationClient();
        if (locationClient != null) locationClient.onSaveInstanceState(activity, outState);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityStarted(@NonNull final Activity activity) {
        sActivity.set(new WeakReference<>(activity));

        if (getLocationClient() != null && mStartStopCounter.incrementAndGet() == 1)

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

        if (getLocationClient() != null && mPauseResumeCounter.incrementAndGet() == 1)

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
        if (getLocationClient() != null && mPauseResumeCounter.decrementAndGet() == 0)

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
        if (getLocationClient() != null && mStartStopCounter.decrementAndGet() == 0)

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

        if (getLocationClient() != null && getProceeded().size() == 0)

            addTask(ActivityLifecycle.DESTROYED, new Runnable() {
                @Override
                public void run() {
                    final LocationClient locationClient = getLocationClient();
                    if (locationClient != null && getProceeded().size() == 0) {

                        CoreLogger.log("LocationClient.onDestroy()");
                        locationClient.onDestroy(activity);

                        setLocationClient(null);
                    }
                }
            });
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

        for (final LocationRx locationRx: mRx)
            locationRx.onResult(location);
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
    public boolean register(final LocationRx locationRx) {
        if (locationRx == null)
            CoreLogger.logError("locationRx == null");
        return locationRx != null && mRx.add(locationRx);
    }

    /**
     * Removes a Rx component that was previously registered with {@link #register(akha.yakhont.technology.rx.BaseRx.LocationRx)}.
     *
     * @param locationRx
     *        The component to remove
     *
     * @return  {@code true} if component removing was successful, {@code false} otherwise
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public boolean unregister(final LocationRx locationRx) {
        if (locationRx == null)
            CoreLogger.logWarning("rxLocation == null");
        return locationRx != null && mRx.remove(locationRx);
    }

    /**
     * Returns the current location.
     *
     * @return  The current location
     */
    @SuppressWarnings("WeakerAccess")
    public Location getCurrentLocation() {
        final LocationClient locationClient = getLocationClient();
        if (locationClient != null) return locationClient.getCurrentLocation();

        CoreLogger.logWarning("locationClient == null");
        return null;
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
        if (locationClient != null) return locationClient.getLastUpdateTime();

        CoreLogger.logWarning("locationClient == null");
        return null;
    }

    private Boolean getLocationAccessDecision(@NonNull final Activity activity) {
        final SharedPreferences preferences = Utils.getPreferences(activity);
        final Boolean result = preferences.contains(ARG_DECISION) ? preferences.getBoolean(ARG_DECISION, false): null;

        CoreLogger.log("result " + result);
        return result;
    }

    private void setLocationAccessDecision(final int resultCode, @NonNull final Activity activity) {
        CoreLogger.log("resultCode " + resultCode);

        mAlert = null;
        if (resultCode == Activity.RESULT_CANCELED) return;

        final boolean decision = resultCode == Activity.RESULT_OK;

        if (decision) {
            sAccessToLocation = true;
            onCreatedHelper(activity, null, true);
        }

        Utils.runInBackground(new Runnable() {
            @Override
            public void run() {
                Utils.getPreferences(activity).edit().putBoolean(ARG_DECISION, decision).apply();
            }
        });
    }

    private boolean showConfirmationDialog(@NonNull final Activity activity) {
        if (mAlert != null)
            CoreLogger.logError("alert dialog already exist");

        mAlert = mAlertProvider.get();

        final boolean result = mAlert.start(activity, null);
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
     * @return  The coordinate in the DMS format
     */
    @SuppressWarnings("WeakerAccess")
    public static String toDms(double coordinate, final boolean isLongitude) {
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
            isLongitude ? (coordinate < 0 ? "W": "E"): (coordinate < 0 ? "S": "N"));
    }
    
    /**
     * Converts {@code Location} to the DMS format (e.g. 5°31′08″ N 87°04′18″ W).
     *
     * @param location
     *        The {@code Location}
     *
     * @return  The location in the DMS format
     */
    @SuppressWarnings("unused")
    public static String toDms(final Location location) {
        return toDms(location, null);
    }

    /**
     * Converts {@code Location} to the DMS format (e.g. 5°31′08″ N 87°04′18″ W).
     *
     * @param location
     *        The {@code Location}
     *
     * @param defValue
     *        The string to return if location is null
     *
     * @return  The location in the DMS format
     */
    @SuppressWarnings("unused")
    public static String toDms(final Location location, final String defValue) {
        return location == null ? defValue != null ? defValue: "N/A":
                String.format(CoreLogger.getLocale(), "%s %s",
                        toDms(location.getLatitude(), false), toDms(location.getLongitude(), true));
    }

    /**
     * Called by the Yakhont Weaver. See {@code Activity#onActivityResult Activity.onActivityResult()}.
     */
    @SuppressWarnings({"UnusedParameters", "unused"})
    public static void onActivityResult(@NonNull final Activity activity, final int requestCode, final int resultCode, final Intent data) {

        final LocationCallbacks locationCallbacks = getLocationCallbacks(activity);
        if (locationCallbacks == null) return;

        // it's not needed to call proceed(activity) 'cause the check is already done - when activity was added to collection

        CoreLogger.log("subject to call by weaver: activity " + Utils.getActivityName(activity) +
                ", requestCode " + requestCode + ", resultCode " + resultCode +
                " " + Utils.getActivityResultString(resultCode));

        switch (Utils.getRequestCode(requestCode)) {

            case LOCATION_CONNECTION_FAILED:
                locationCallbacks.getLocationClient().clearResolvingError();

                if (resultCode == Activity.RESULT_OK)
                    locationCallbacks.getLocationClient().connect();
                else
                    CoreLogger.logWarning("unknown result code " + resultCode);
                break;

            case LOCATION_CLIENT:
                locationCallbacks.getLocationClient().clearResolvingError();
                break;

            case LOCATION_ALERT:
                locationCallbacks.setLocationAccessDecision(resultCode, activity);
                break;

            default:
                CoreLogger.logWarning("unknown request code " + requestCode);
        }
    }

    /**
     * Called by the Yakhont Weaver; prevents crashing on emulators without Google APIs.
     *
     * @param activity
     *        The {@code Activity}
     *
     * @param e
     *        The {@code Exception} to handle
     */
    @SuppressWarnings({"UnusedParameters", "unused"})
    public static void startActivityForResultExceptionHandler(@NonNull final Activity activity,
                                                              @NonNull final RuntimeException e) {

        final LocationCallbacks locationCallbacks = getLocationCallbacks(activity);
        if (locationCallbacks == null) throw e;

        // it's not needed to call proceed(activity) 'cause the check is already done - when activity was added to collection

        CoreLogger.log("failed", e);

        locationCallbacks.mToast.get().start(activity, activity.getString(
                akha.yakhont.R.string.yakhont_location_error));
    }
}
