/*
 * Copyright (C) 2015-2019 akha, a.k.a. Alexander Kharitonov
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
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ExecutorHelper;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CorePermissions;
import akha.yakhont.CorePermissions.RequestBuilder;
// ProGuard issue
// import akha.yakhont.R;
import akha.yakhont.callback.BaseCallbacks.CallbacksCustomizer;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.ActivityLifecycle;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.CurrentActivityHelper;
import akha.yakhont.technology.Dagger2;
import akha.yakhont.technology.rx.BaseRx.LocationRx;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;

import dagger.Lazy;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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
 * public class YourActivity extends Activity implements LocationListener {
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
public class LocationCallbacks extends BaseActivityCallbacks implements CallbacksCustomizer {

    private   static final int                                      DELAY               = 750;    // ms

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static final CurrentActivityHelper                    sActivity           = new CurrentActivityHelper();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        Lazy<LocationClient>                           mLocationClient;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Object                                         mClientLock         = new Object();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  AtomicInteger                                  mStartStopCounter   = new AtomicInteger();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  AtomicInteger                                  mPauseResumeCounter = new AtomicInteger();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Map<ActivityLifecycle, Future<?>>              mFutures            = Collections.synchronizedMap(
            new EnumMap<>(ActivityLifecycle.class));

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  ExecutorHelper                                 mExecutor           = new ExecutorHelper();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Object                                         mLock               = new Object();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        Provider<BaseDialog>                           mToastProvider;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final  Map<Activity, Set<LocationRx>>                 mRx                 = Utils.newWeakMap();

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        Integer                                        mPermissionsRequestCode;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        String                                         mPermissionsRationale;

    /**
     * Activity should implement this interface for receiving notifications when the location has changed.
     */
    @SuppressWarnings("WeakerAccess")
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
     * Please refer to the base method description.
     * <p>Note: first element in parameters used for setting
     * {@link RequestBuilder#setRationale}, pass null to avoid such setting.
     * <p>First and second elements in properties used for setting
     * {@link RequestBuilder#setRationale} and
     * {@link RequestBuilder#setRequestCode}, pass {@link Core#NOT_VALID_RES_ID}
     * to avoid first setting and any int (which is not conflict with your other request codes).
     */
    @Override
    public void set(final String[] parameters, final int[] properties) {
        if (parameters != null && parameters.length > 0 && parameters[0] != null) {
            if (properties != null && properties.length > 0 && properties[0] != Core.NOT_VALID_RES_ID)
                CoreLogger.logWarning("properties[0] will be ignored 'cause " +
                        "permissions rationale is already defined as 'parameters'");
            if (parameters.length != 1)
                CoreLogger.logWarning("only first parameter accepted, all others are ignored: " +
                        Arrays.deepToString(parameters));
            mPermissionsRationale = parameters[0];
        }
        if (properties != null && properties.length > 0) {
            if (!(parameters != null && parameters.length > 0 && properties[0] != Core.NOT_VALID_RES_ID)) {
                if (properties[0] == Core.NOT_VALID_RES_ID)
                    CoreLogger.logWarning("wrong permissions rationale String ID " + properties[0]);
                else
                    mPermissionsRationale = Objects.requireNonNull(Utils.getApplication())
                            .getString(properties[0]);
            }
            if (properties.length > 2) {
                final Integer[] tmp = new Integer[properties.length];
                for (int i = 0; i < properties.length; i++)
                    tmp[i] = properties[i];
                CoreLogger.logWarning("only first 2 parameter accepted, all others are ignored: " +
                        Arrays.deepToString(tmp));
            }
            if (properties.length > 1) {
                mPermissionsRequestCode = properties[1];
                CoreLogger.log("permissions request code set to " + properties[1] +
                        ", object: " + this);
            }
        }
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
     * Sets the location client (via {@link Dagger2}).
     */
    @SuppressWarnings("WeakerAccess")
    protected void setLocationClient() {
        synchronized (mClientLock) {
            if (mLocationClient == null) mLocationClient = Core.getDagger().getLocationClient();
        }
    }

    private void addTask(@NonNull final ActivityLifecycle lifecycle, @NonNull final Runnable runnable) {
        synchronized (mLock) {
            addTaskNotSync(lifecycle, runnable);
        }
    }

    private void addTaskNotSync(@NonNull final ActivityLifecycle lifecycle, @NonNull final Runnable runnable) {
        final Future<?> future = mFutures.get(lifecycle);
        if (future != null && !future.isDone() && !future.isCancelled()) {
            CoreLogger.logWarning("already scheduled " + lifecycle);
            return;
        }

        //noinspection Convert2Lambda
        mFutures.put(lifecycle, mExecutor.runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Exception e) {
                    CoreLogger.log("addTaskNotSync failed " + lifecycle, e);
                }
            }

            @NonNull
            @Override
            public String toString() {
                return runnable.toString();
            }
        }, DELAY, true));
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityCreated(@NonNull final Activity activity, final Bundle savedInstanceState) {
        if (mToastProvider == null) mToastProvider = Core.getDagger().getToastLong();

        // we need it here to allow user set location client parameters
        if (getLocationClient(true) == null) setLocationClient();

        final LocationClient locationClient = getLocationClient();
        if (locationClient != null)
            locationClient.setLocationCallbacks(LocationCallbacks.this);

        onCreatedHelper(activity, savedInstanceState, false);
    }

    private void onCreatedHelper(@NonNull final Activity activity, final Bundle savedInstanceState,
                                 @SuppressWarnings("SameParameterValue") boolean fromDialog) {
        final String          permission        = Manifest.permission.ACCESS_FINE_LOCATION;
        final boolean         granted           = CorePermissions.check(activity, permission);

        if (!fromDialog) fromDialog   = !granted;
        final boolean fromDialogFinal = fromDialog;

        @SuppressWarnings("Convert2Lambda")
        final boolean result = new RequestBuilder(activity, permission)
                .setRationale  (mPermissionsRationale  )
                .setRequestCode(mPermissionsRequestCode)
                .setOnGranted  (new Runnable() {
                    @Override
                    public void run() {
                        CoreLogger.log("LocationClient.onCreate()");

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
        final LocationClient locationClient = getLocationClient();
        if (locationClient != null) locationClient.onSaveInstanceState(activity, outState);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityStarted(@NonNull final Activity activity) {
        sActivity.set(activity);

        if (mStartStopCounter.incrementAndGet() == 1)

            //noinspection Convert2Lambda
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
        sActivity.set(activity);

        if (mPauseResumeCounter.incrementAndGet() == 1)

            //noinspection Convert2Lambda
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
        if (mPauseResumeCounter.decrementAndGet() == 0)

            //noinspection Convert2Lambda
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
        if (mStartStopCounter.decrementAndGet() == 0)

            //noinspection Convert2Lambda
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
        sActivity.clear(activity);

        if (getProceeded().size() == 0)

            //noinspection Convert2Lambda
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
        return sActivity.get();
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

        if (!mRx.containsKey(activity)) //noinspection RedundantTypeArguments
            mRx.put(activity, Utils.<LocationRx>newSet());

        final boolean result = Objects.requireNonNull(mRx.get(activity)).add(locationRx);
        CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.ERROR, "register Rx: result == " + result);
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
        CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.WARNING, "unregister Rx: result == " + result);
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

        return String.format(Utils.getLocale(), "%d°%02d'%02d\" %s", degrees, minutes, seconds,
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
        final Locale locale = Utils.getLocale();
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

        CoreLogger.logWarning("unknown request code " + code);
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

        CoreLogger.log("startActivityForResultExceptionHandler failed", exception);

        locationCallbacks.mToastProvider.get().start(activity, activity.getString(
                akha.yakhont.R.string.yakhont_location_error), null);
    }
}
