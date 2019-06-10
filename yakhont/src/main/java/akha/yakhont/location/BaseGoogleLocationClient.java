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

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.location.LocationCallbacks.LocationClient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.Date;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

/**
 * The base class for clients to work with Google Play Services Location APIs.
 *
 * @see GoogleLocationClient
 * @see GoogleLocationClientNew
 *
 * @author akha
 */
public abstract class BaseGoogleLocationClient implements LocationClient, LocationListener {

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static final String  TAG                                  = Utils.getTag(BaseGoogleLocationClient.class);

    private   static final String  ARG_REQUEST_UPDATES                  = TAG + ".request_updates";
    private   static final String  ARG_REQUEST_PRIORITY                 = TAG + ".request_priority";
    private   static final String  ARG_REQUEST_INTERVAL                 = TAG + ".request_interval";
    private   static final String  ARG_REQUEST_FASTEST_INTERVAL         = TAG + ".request_fastest_interval";

    private   static final String  ARG_REQUEST_EXPIRATION_DURATION      = TAG + ".request_expiration_duration";
    private   static final String  ARG_REQUEST_EXPIRATION_TIME          = TAG + ".request_expiration_time";
    private   static final String  ARG_REQUEST_MAX_WAIT_TIME            = TAG + ".request_max_wait_time";
    private   static final String  ARG_REQUEST_NUM_UPDATES              = TAG + ".request_num_updates";
    private   static final String  ARG_REQUEST_SMALLEST_DISPLACEMENT    = TAG + ".request_smallest_displacement";

    private   static final String  ARG_LOCATION                         = TAG + ".location";
    private   static final String  ARG_TIME                             = TAG + ".time";
    private   static final String  ARG_UNIQUE_UPDATES                   = TAG + ".unique_updates";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static final int     UPDATE_INTERVAL_HIGH_ACCURACY        = 10 * 1000;    // milliseconds
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static final int     UPDATE_INTERVAL_LOW_ACCURACY         = 60 * 1000;    // milliseconds

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       FusedLocationProviderClient
                                        mFusedLocationClient;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       LocationCallback    mLocationCallback;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       LocationCallbacks   mLocationCallbacks;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Location            mCurrentLocation;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Date                mLastUpdateTime;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       boolean             mUniqueUpdates                  = true;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       int                 mPriority;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Long                mInterval;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       long                mFastestInterval;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Long                mExpirationDuration;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Long                mExpirationTime;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Long                mMaxWaitTime;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Integer             mNumUpdates;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       Float               mSmallestDisplacement;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected       boolean             mRequestingLocationUpdates;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected final Object              mBuildLock                      = new Object();

    /**
     * Initialises a newly created {@code BaseGoogleLocationClient} object.
     */
    @SuppressWarnings("WeakerAccess")
    protected BaseGoogleLocationClient() {
    }

    /**
     *  Returns the Google Play Services Location API client.
     *
     * @return  This {@code GoogleApiClient}
     */
    @SuppressWarnings("unused")
    public FusedLocationProviderClient getClient() {
        return mFusedLocationClient;
    }

    /**
     * Sets the flag indicating whether the location updates callbacks should be called
     * for changed values only. The default value is {@code true}.
     *
     * @param value
     *        {@code true} for changed values only location updates, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public void setUniqueUpdates(final boolean value) {
        mUniqueUpdates = value;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Location getCurrentLocation() {
        return mCurrentLocation;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Date getLastUpdateTime() {
        return mLastUpdateTime;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void setLocationCallbacks(final LocationCallbacks locationCallbacks) {
        mLocationCallbacks = locationCallbacks;
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onSaveInstanceState(final Activity activity, final Bundle savedInstanceState) {
        savedInstanceState.putBoolean     (ARG_REQUEST_UPDATES,          mRequestingLocationUpdates);

        savedInstanceState.putInt         (ARG_REQUEST_PRIORITY             , mPriority            );
        if (mInterval != null)
            savedInstanceState.putLong    (ARG_REQUEST_INTERVAL             , mInterval            );
        savedInstanceState.putLong        (ARG_REQUEST_FASTEST_INTERVAL     , mFastestInterval     );

        if (mExpirationDuration   != null)
            savedInstanceState.putLong    (ARG_REQUEST_EXPIRATION_DURATION  , mExpirationDuration  );
        if (mExpirationTime       != null)
            savedInstanceState.putLong    (ARG_REQUEST_EXPIRATION_TIME      , mExpirationTime      );
        if (mMaxWaitTime          != null)
            savedInstanceState.putLong    (ARG_REQUEST_MAX_WAIT_TIME        , mMaxWaitTime         );
        if (mNumUpdates           != null)
            savedInstanceState.putInt     (ARG_REQUEST_NUM_UPDATES          , mNumUpdates          );
        if (mSmallestDisplacement != null)
            savedInstanceState.putFloat   (ARG_REQUEST_SMALLEST_DISPLACEMENT, mSmallestDisplacement);

        savedInstanceState.putParcelable  (ARG_LOCATION                     , mCurrentLocation     );
        savedInstanceState.putSerializable(ARG_TIME                         , mLastUpdateTime      );
        savedInstanceState.putBoolean     (ARG_UNIQUE_UPDATES               , mUniqueUpdates       );
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onCreate(final Activity activity, final Bundle savedInstanceState) {
        getFromBundle(savedInstanceState);
        createClient(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onStart(final Activity activity) {
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onResume(final Activity activity) {
        startLocationUpdates(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onPause(final Activity activity) {
        stopLocationUpdates(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onStop(final Activity activity) {
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onDestroy(final Activity activity) {
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the requesting location updates parameters.
     *
     * @param requestingLocationUpdates
     *        {@code true} for requesting location updates, {@code false} otherwise
     *
     * @param highAccuracy
     *        {@code true} for high accuracy location updates, {@code false} otherwise
     *
     * @return  This {@code BaseGoogleLocationClient} object, so that setters can be chained
     *
     * @see LocationRequest
     */
    @SuppressWarnings({"SameParameterValue", "UnusedReturnValue", "WeakerAccess", "unused"})
    public BaseGoogleLocationClient setLocationUpdatesParameters(final boolean requestingLocationUpdates,
                                                                 final boolean highAccuracy) {
        final long interval = (highAccuracy) ? UPDATE_INTERVAL_HIGH_ACCURACY:
                                               UPDATE_INTERVAL_LOW_ACCURACY;
        setLocationUpdatesParameters(requestingLocationUpdates,
                (highAccuracy) ? LocationRequest.PRIORITY_HIGH_ACCURACY:
                                 LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
                interval, interval / 2);

        return this;
    }

    /**
     * Sets the requesting location updates parameters.
     *
     * @param requestingLocationUpdates
     *        {@code true} for requesting location updates, {@code false} otherwise
     *
     * @param priority
     *        Sets the priority of the request
     *
     * @param interval
     *        Sets the desired interval for active location updates, in milliseconds
     *
     * @param fastestInterval
     *        Explicitly sets the fastest interval for location updates, in milliseconds
     *
     * @return  This {@code BaseGoogleLocationClient} object, so that setters can be chained
     *
     * @see LocationRequest
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public BaseGoogleLocationClient setLocationUpdatesParameters(final boolean requestingLocationUpdates,
                                                                 final int     priority,
                                                                 final long    interval,
                                                                 final long    fastestInterval) {
        return setLocationUpdatesParameters(requestingLocationUpdates,
                priority, interval, fastestInterval, null, null, null, null, null);
    }

    /**
     * Sets the requesting location updates parameters.
     *
     * @param requestingLocationUpdates
     *        {@code true} for requesting location updates, {@code false} otherwise
     *
     * @param priority
     *        Sets the priority of the request
     *
     * @param interval
     *        Sets the desired interval for active location updates, in milliseconds
     *
     * @param fastestInterval
     *        Explicitly sets the fastest interval for location updates, in milliseconds
     *
     * @param expirationDuration
     *        Sets the duration of this request, in milliseconds
     *
     * @param expirationTime
     *        Sets the request expiration time, in millisecond since boot
     *
     * @param maxWaitTime
     *        Sets the maximum wait time in milliseconds for location updates
     *
     * @param numUpdates
     *        Sets the number of location updates
     *
     * @param smallestDisplacement
     *        Sets the minimum displacement between location updates (in meters)
     *
     * @return  This {@code BaseGoogleLocationClient} object, so that setters can be chained
     *
     * @see LocationRequest
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess", "SameParameterValue"})
    public BaseGoogleLocationClient setLocationUpdatesParameters(final boolean requestingLocationUpdates,
                                                                 final int     priority,
                                                                 final long    interval,
                                                                 final long    fastestInterval,
                                                                 final Long    expirationDuration,
                                                                 final Long    expirationTime,
                                                                 final Long    maxWaitTime,
                                                                 final Integer numUpdates,
                                                                 final Float   smallestDisplacement) {
        CoreLogger.log("setLocationUpdatesParameters, requestingLocationUpdates: " + requestingLocationUpdates       );
        CoreLogger.log("setLocationUpdatesParameters, priority                 : " + getPriorityDescription(priority));
        CoreLogger.log("setLocationUpdatesParameters, interval                 : " + interval                        );
        CoreLogger.log("setLocationUpdatesParameters, fastestInterval          : " + fastestInterval                 );

        CoreLogger.log("setLocationUpdatesParameters, expirationDuration       : " + expirationDuration              );
        CoreLogger.log("setLocationUpdatesParameters, expirationTime           : " + expirationTime                  );
        CoreLogger.log("setLocationUpdatesParameters, maxWaitTime              : " + maxWaitTime                     );
        CoreLogger.log("setLocationUpdatesParameters, numUpdates               : " + numUpdates                      );
        CoreLogger.log("setLocationUpdatesParameters, smallestDisplacement     : " + smallestDisplacement            );

        mRequestingLocationUpdates  = requestingLocationUpdates;
        mPriority                   = priority;
        mInterval                   = interval;
        mFastestInterval            = fastestInterval;

        mExpirationDuration         = expirationDuration;
        mExpirationTime             = expirationTime;
        mMaxWaitTime                = maxWaitTime;
        mNumUpdates                 = numUpdates;
        mSmallestDisplacement       = smallestDisplacement;

        return this;
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState
     *        The activity state saved in the {@link Bundle}
     */
    @SuppressWarnings("WeakerAccess")
    @CallSuper
    protected void getFromBundle(final Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        CoreLogger.log("getFromBundle");

        if (savedInstanceState.keySet().contains                      (ARG_REQUEST_UPDATES))
            mRequestingLocationUpdates = savedInstanceState.getBoolean(ARG_REQUEST_UPDATES);

        if (savedInstanceState.keySet().contains               (ARG_REQUEST_PRIORITY             ))
            mPriority             = savedInstanceState.getInt  (ARG_REQUEST_PRIORITY             );
        if (savedInstanceState.keySet().contains               (ARG_REQUEST_INTERVAL             ))
            mInterval             = savedInstanceState.getLong (ARG_REQUEST_INTERVAL             );
        if (savedInstanceState.keySet().contains               (ARG_REQUEST_FASTEST_INTERVAL     ))
            mFastestInterval      = savedInstanceState.getLong (ARG_REQUEST_FASTEST_INTERVAL     );

        if (savedInstanceState.keySet().contains               (ARG_REQUEST_EXPIRATION_DURATION  ))
            mExpirationDuration   = savedInstanceState.getLong (ARG_REQUEST_EXPIRATION_DURATION  );
        if (savedInstanceState.keySet().contains               (ARG_REQUEST_EXPIRATION_TIME      ))
            mExpirationTime       = savedInstanceState.getLong (ARG_REQUEST_EXPIRATION_TIME      );
        if (savedInstanceState.keySet().contains               (ARG_REQUEST_MAX_WAIT_TIME        ))
            mMaxWaitTime          = savedInstanceState.getLong (ARG_REQUEST_MAX_WAIT_TIME        );
        if (savedInstanceState.keySet().contains               (ARG_REQUEST_NUM_UPDATES          ))
            mNumUpdates           = savedInstanceState.getInt  (ARG_REQUEST_NUM_UPDATES          );
        if (savedInstanceState.keySet().contains               (ARG_REQUEST_SMALLEST_DISPLACEMENT))
            mSmallestDisplacement = savedInstanceState.getFloat(ARG_REQUEST_SMALLEST_DISPLACEMENT);

        if (savedInstanceState.keySet().contains                       (ARG_LOCATION      ))
            //noinspection RedundantCast
            setLocation((Location)   savedInstanceState.getParcelable  (ARG_LOCATION      ));
        if (savedInstanceState.keySet().contains                       (ARG_TIME          ))
            mLastUpdateTime = (Date) savedInstanceState.getSerializable(ARG_TIME          );
        if (savedInstanceState.keySet().contains                       (ARG_UNIQUE_UPDATES))
            mUniqueUpdates  =        savedInstanceState.getBoolean     (ARG_UNIQUE_UPDATES);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onLocationChanged(final Location location) {
        if (location == null) {
            CoreLogger.log("new location is null");
            return;
        }

        final boolean changed = mCurrentLocation == null                  ||
                location.getLatitude()  != mCurrentLocation.getLatitude() ||
                location.getLongitude() != mCurrentLocation.getLongitude();
        CoreLogger.log("location changed: " + changed);

        setLocation(location);
        mLastUpdateTime = lastUpdateTime();

        if (mUniqueUpdates && !changed) return;

        mLocationCallbacks.onLocationChanged(location, mLastUpdateTime);
    }

    private Date lastUpdateTime() {
        return new Date();
    }

    /**
     * Sets the current location.
     *
     * @param location
     *        The current location
     */
    @SuppressWarnings("WeakerAccess")
    protected void setLocation(final Location location) {
        CoreLogger.log("new location: " + location);
        mCurrentLocation = location;
    }

    private void createClient(@NonNull final Activity activity) {
        CoreLogger.log("buildClient");
        synchronized (mBuildLock) {
            buildClient(activity);
        }
    }

    /**
     * Builds the client to work with Google Play Services Location APIs.
     *
     * @param activity
     *        The activity
     */
    @SuppressWarnings("WeakerAccess")
    protected void buildClient(@NonNull final Activity activity) {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        createLocationCallback();
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onLocationChanged(locationResult.getLastLocation());
            }
        };
    }

    /**
     * Creates new {@code LocationRequest}.
     *
     * @return
     *        The {@code LocationRequest}
     */
    @SuppressWarnings("WeakerAccess")
    public LocationRequest createNewLocationRequest() {
        final LocationRequest locationRequest = LocationRequest.create();

        if (mInterval == null)
            mRequestingLocationUpdates = true;
        else {
            locationRequest.setInterval                (mInterval            );
            locationRequest.setFastestInterval         (mFastestInterval     );
            locationRequest.setPriority                (mPriority            );

            if (mExpirationDuration   != null)
                locationRequest.setExpirationDuration  (mExpirationDuration  );
            if (mExpirationTime       != null)
                locationRequest.setExpirationTime      (mExpirationTime      );
            if (mMaxWaitTime          != null)
                locationRequest.setMaxWaitTime         (mMaxWaitTime         );
            if (mNumUpdates           != null)
                locationRequest.setNumUpdates          (mNumUpdates          );
            if (mSmallestDisplacement != null)
                locationRequest.setSmallestDisplacement(mSmallestDisplacement);
        }

        CoreLogger.log("createNewLocationRequest, priority            : " + getPriorityDescription(
                                                                            locationRequest.getPriority            ()));
        CoreLogger.log("createNewLocationRequest, interval            : " + locationRequest.getInterval            () );
        CoreLogger.log("createNewLocationRequest, fastestInterval     : " + locationRequest.getFastestInterval     () );

        CoreLogger.log("createNewLocationRequest, expirationTime      : " + locationRequest.getExpirationTime      () );
        CoreLogger.log("createNewLocationRequest, maxWaitTime         : " + locationRequest.getMaxWaitTime         () );
        CoreLogger.log("createNewLocationRequest, numUpdates          : " + locationRequest.getNumUpdates          () );
        CoreLogger.log("createNewLocationRequest, smallestDisplacement: " + locationRequest.getSmallestDisplacement() );

        return locationRequest;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static String getPriorityDescription(final int priority) {
        switch (priority) {
            case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                return "PRIORITY_BALANCED_POWER_ACCURACY";
            case LocationRequest.PRIORITY_HIGH_ACCURACY:
                return "PRIORITY_HIGH_ACCURACY";
            case LocationRequest.PRIORITY_LOW_POWER:
                return "PRIORITY_LOW_POWER";
            case LocationRequest.PRIORITY_NO_POWER:
                return "PRIORITY_NO_POWER";
            default:
                return "unknown priority " + priority;
        }
    }

    /**
     * Starts location updates.
     *
     * @param activity
     *        The activity
     */
    @SuppressWarnings("WeakerAccess")
    protected void startLocationUpdates(@NonNull final Activity activity) {
        CoreLogger.log("startLocationUpdates, mRequestingLocationUpdates: " + mRequestingLocationUpdates);
        if (!mRequestingLocationUpdates) return;

        CoreLogger.log("startLocationUpdates");
        try {
            requestLocationUpdates(activity, createNewLocationRequest());
        }
        catch (Exception exception) {
            CoreLogger.log("startLocationUpdates failed", exception);
        }
    }

    /**
     * Requests location updates.
     *
     * @param activity
     *        The activity
     *
     * @param locationRequest
     *        The {@code LocationRequest}
     */
    @SuppressLint("MissingPermission")
    @SuppressWarnings("WeakerAccess")
    protected void requestLocationUpdates(@NonNull final Activity activity,
                                          @NonNull final LocationRequest locationRequest) {
        CoreLogger.log("requestLocationUpdates, LocationRequest: " + locationRequest);

        try {
            //noinspection Anonymous2MethodRef,Convert2Lambda
            mFusedLocationClient.requestLocationUpdates(locationRequest,
                    mLocationCallback, activity.getMainLooper())

                    .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            log(task);
                        }
                    })
                    .addOnFailureListener(activity, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            taskOnFailure(exception);
                            mLocationCallbacks.onLocationError(exception);
                        }
                    });
        }
        catch (/*Security*/Exception exception) {   // should never happen
            CoreLogger.log("requestLocationUpdates failed", exception);
        }
    }

    /**
     * Stops location updates.
     *
     * @param activity
     *        The activity
     */
    @SuppressWarnings("WeakerAccess")
    protected void stopLocationUpdates(final Activity activity) {
        CoreLogger.log("stopLocationUpdates");

        // happens when system permissions dialog pops up
        if (mFusedLocationClient == null) return;

        //noinspection Anonymous2MethodRef,Convert2Lambda
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        log(task);
                    }
                });
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @SuppressLint("MissingPermission")
    protected void getLastLocation(@NonNull final Activity activity) {
        try {
            //noinspection Anonymous2MethodRef,Convert2Lambda
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(activity, new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            log(task);
                            if (task.isSuccessful())
                                onLocationChanged(task.getResult());
                            else
                                taskOnFailure(task.getException());
                        }
                    }).addOnFailureListener(activity, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            taskOnFailure(exception);
                        }
            });
        }
        catch (/*Security*/Exception exception) {   // should never happen
            CoreLogger.log("getLastLocation failed", exception);
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void taskOnFailure(final Exception exception) {
        // it seems not a good idea to notify Rx about this, so just log
        CoreLogger.log("task exception", exception);
        logException(exception);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void logException(final Exception exception) {
        if (!(exception instanceof ApiException)) return;

        final ApiException apiException = (ApiException) exception;
        final int code = apiException.getStatusCode();
        CoreLogger.logError("ApiException - code: " + code + " " + getStatusCodeDescription(code) +
                ", message: " + apiException.getMessage());
    }

    private static String getStatusCodeDescription(final int code) {
        return code == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE ?
                "SETTINGS_CHANGE_UNAVAILABLE" : CommonStatusCodes.getStatusCodeString(code);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void log(final Task task) {
        if (task == null) {
            CoreLogger.logError("task == null");
            return;
        }
        CoreLogger.log("task.isSuccessful() " + task.isSuccessful() +
                "task.isComplete() " + task.isComplete());
        if (task.isSuccessful()) return;

        final Exception exception = task.getException();
        if (exception == null) {
            CoreLogger.log("task.getException() == null");
            return;
        }
        CoreLogger.log("task.getException()", exception);
        logException(exception);
    }
}
