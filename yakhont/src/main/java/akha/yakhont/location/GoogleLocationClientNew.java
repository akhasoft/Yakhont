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

import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

/**
 * The client to work with {@link FusedLocationProviderClient}-based Google Play Services Location API.
 *
 * @author akha
 */
public class GoogleLocationClientNew extends BaseGoogleLocationClient {

    /** The default pending intent action (the value is {@value}). */
    @SuppressWarnings("WeakerAccess")
    public  static final String INTENT_DEFAULT_ACTION = "akha.yakhont.location.GoogleLocationClientNew.ACTION_GET_LOCATION";

    /** The default pending intent request code. */
    @SuppressWarnings("WeakerAccess")
    public  static final int    INTENT_DEFAULT_CODE   = Utils.getRequestCode(RequestCodes.LOCATION_INTENT);

    private static final String ARG_REQUEST_UPDATES   = TAG + ".request_location_updates";
    private static final String ARG_RUN_UPDATES       = TAG + ".run_location_updates";
    private static final String ARG_INTENT            = TAG + ".intent";

    private static final int    REQUEST_CODE          = Utils.getRequestCode(RequestCodes.LOCATION_CHECK_SETTINGS);

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected   FusedLocationProviderClient     mFusedLocationClient;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected   SettingsClient                  mSettingsClient;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected   LocationCallback                mLocationCallback;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected   PendingIntent                   mPendingIntent;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected   boolean                         mRequestLocationUpdates;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected   boolean                         mRunLocationUpdates;

    /**
     * Initialises a newly created {@code GoogleLocationClientNew} object.
     */
    public GoogleLocationClientNew() {
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
     *  Returns the Google Play Services Location API Settings client.
     *
     * @return  This {@code GoogleApiClient}
     */
    @SuppressWarnings("unused")
    public SettingsClient getSettingsClient() {
        return mSettingsClient;
    }

    /**
     * Forces to set requesting location updates flag.
     * Normally it sets automatically, set it only if you really know what you're doing.
     *
     * @param value
     *        {@code true} for requesting location updates, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public void setRequestLocationUpdates(final boolean value) {
        mRequestLocationUpdates = value;
    }

    /**
     * Sets the flag indicating whether the location updates should be run from
     * {@link #startSettingsUpdates(Activity)} or not. The default value is {@code true}.
     *
     * @param value
     *        {@code true} for run location updates, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public void setRunLocationUpdates(final boolean value) {
        mRunLocationUpdates = value;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
        mRequestLocationUpdates = true;
        mRunLocationUpdates     = true;

        super.onCreate(activity, savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onResume(Activity activity) {
        CoreLogger.log("mRequestLocationUpdates " + mRequestLocationUpdates);
        if (mRequestLocationUpdates) startSettingsUpdates(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onSaveInstanceState(Activity activity, Bundle savedInstanceState) {
        super.onSaveInstanceState(activity, savedInstanceState);

        savedInstanceState.putBoolean(ARG_REQUEST_UPDATES, mRequestLocationUpdates);
        savedInstanceState.putBoolean(ARG_RUN_UPDATES,     mRunLocationUpdates    );

        if (mPendingIntent != null)
            savedInstanceState.putParcelable(ARG_INTENT,   mPendingIntent         );
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void getFromBundle(final Bundle savedInstanceState) {
        super.getFromBundle(savedInstanceState);
        if (savedInstanceState == null) return;

        if (savedInstanceState.keySet().contains                      (ARG_REQUEST_UPDATES))
            mRequestLocationUpdates = savedInstanceState.getBoolean   (ARG_REQUEST_UPDATES);
        if (savedInstanceState.keySet().contains                      (ARG_RUN_UPDATES    ))
            mRunLocationUpdates     = savedInstanceState.getBoolean   (ARG_RUN_UPDATES    );

        if (savedInstanceState.keySet().contains                      (ARG_INTENT         ))
            mPendingIntent          = savedInstanceState.getParcelable(ARG_INTENT         );
    }

    /**
     * Please refer to the base method description.
     */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @Override
    protected void buildClient(@NonNull final Activity activity) {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);

        try {
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
        catch (SecurityException exception) {   // should never happen
            CoreLogger.log("failed", exception);
        }

        mSettingsClient = LocationServices.getSettingsClient(activity);

        createLocationCallback();
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void taskOnFailure(final Exception exception) {
        // it seems not a good idea to notify Rx about this, so just log
        CoreLogger.log("task exception", exception);
        logException(exception);
    }

    private static void logException(final Exception exception) {
        if (!(exception instanceof ApiException)) return;

        final ApiException apiException = (ApiException) exception;
        final int code = apiException.getStatusCode();
        CoreLogger.logError("ApiException - code: " + code + " " + getStatusCodeDescription(code) +
                ", message: " + apiException.getStatusMessage());
    }

    private void log(final Task task) {
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

    @SuppressWarnings("WeakerAccess")
    protected LocationSettingsRequest createNewLocationSettingsRequest() {
        return new LocationSettingsRequest.Builder()
                .addLocationRequest(createNewLocationRequest())
                .build();
    }

    /**
     * Starts settings updates.
     *
     * @param activity
     *        The activity
     */
    @SuppressWarnings("WeakerAccess")
    protected void startSettingsUpdates(final Activity activity) {
        mSettingsClient.checkLocationSettings(createNewLocationSettingsRequest())
                .addOnCompleteListener(activity, new OnCompleteListener<LocationSettingsResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                        log(task);
                        if (!task.isSuccessful()) {
                            taskOnFailure(task.getException());
                            return;
                        }

                        CoreLogger.log("All location settings are satisfied");
                        if (mRunLocationUpdates) startLocationUpdates(activity);
                    }
                })
                .addOnFailureListener(activity, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        if (!checkLocationSettingsOnFailure(activity, exception))
                            onLocationError(exception, null);
                    }
                });
    }

    private void onLocationError(final Exception exception, final String error) {
        if (!mRunLocationUpdates) return;

        mRequestLocationUpdates = false;

        if (exception != null)
            mLocationCallbacks.onLocationError(exception);
        else
            mLocationCallbacks.onLocationError(error);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "BooleanMethodIsAlwaysInverted"})
    protected boolean checkLocationSettingsOnFailure(final Activity activity, @NonNull Exception exception) {
        logException(exception);

        if (!(exception instanceof ApiException)) {
            CoreLogger.log("exception is not instance of ApiException", exception);
            return false;
        }

        final ApiException apiException = (ApiException) exception;
        final int code = apiException.getStatusCode();

        switch (code) {
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                CoreLogger.log("Location settings are not satisfied; about to upgrade location settings");
                try {
                    // Show the dialog
                    ((ResolvableApiException) apiException).startResolutionForResult(
                            activity, REQUEST_CODE);
                    return true;
                }
                catch (SendIntentException sendIntentException) {
                    CoreLogger.log("ResolvableApiException: unable startResolutionForResult",
                            sendIntentException);
                }
                break;

            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                CoreLogger.logError("Location settings are inadequate, and cannot be " +
                        "fixed here; you can try to fix in Settings");
                break;

            default:
                CoreLogger.logWarning("not handled status code: " + code);
                break;
        }
        return false;
    }

    private static String getStatusCodeDescription(int code) {
        return code == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE ?
                "SETTINGS_CHANGE_UNAVAILABLE" : CommonStatusCodes.getStatusCodeString(code);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean onActivityResult(Activity activity, RequestCodes requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case LOCATION_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // nothing to do, startSettingsUpdates() will be called in onResume again
                        CoreLogger.log("User agreed to make required location settings changes");
                        break;

                    case Activity.RESULT_CANCELED:
                        final String info = "User choose not to make required location settings changes";
                        CoreLogger.logWarning(info);

                        onLocationError(null, info);
                        break;

                    default:
                        CoreLogger.logWarning("unknown result code " + resultCode);
                        break;
                }
                return true;
        }
        return false;
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
     * Sets {@code PendingIntent} for
     * {@link FusedLocationProviderClient#requestLocationUpdates(LocationRequest, PendingIntent)}.
     *
     * @param pendingIntent
     *        The {@code PendingIntent}
     *
     * @return  This {@code GoogleLocationClientNew} object, so that setters can be chained
     */
    @SuppressWarnings("unused")
    public GoogleLocationClientNew setPendingIntent(final PendingIntent pendingIntent) {
        mPendingIntent = pendingIntent;
        return this;
    }

    /**
     * Creates {@code PendingIntent} for
     * {@link FusedLocationProviderClient#requestLocationUpdates(LocationRequest, PendingIntent)}.
     *
     * @param activity
     *        The activity
     *
     * @param classBroadcast
     *        The class representing the user-defined {@code BroadcastReceiver}
     *
     * @param action
     *        The intent action
     *
     * @param requestCode
     *        The intent request code
     *
     * @return  The {@code PendingIntent}
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    public static PendingIntent createPendingIntentBroadcast(@NonNull final Activity activity,
                                                             @NonNull final Class    classBroadcast,
                                                             @NonNull final String   action,
                                                             final int      requestCode) {
        final Intent intent = new Intent(activity, classBroadcast);
        intent.setAction(action);
        return PendingIntent.getBroadcast(activity, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Creates {@code PendingIntent} for
     * {@link FusedLocationProviderClient#requestLocationUpdates(LocationRequest, PendingIntent)}
     * with default intent parameters.
     *
     * @param activity
     *        The activity
     *
     * @param classBroadcast
     *        The class representing the user-defined {@code BroadcastReceiver}
     *
     * @return  The {@code PendingIntent}
     *
     * @see #INTENT_DEFAULT_ACTION
     * @see #INTENT_DEFAULT_CODE
     */
    @SuppressWarnings("unused")
    public static PendingIntent createPendingIntentBroadcast(@NonNull final Activity activity,
                                                             @NonNull final Class    classBroadcast) {
        return createPendingIntentBroadcast(activity, classBroadcast, INTENT_DEFAULT_ACTION, INTENT_DEFAULT_CODE);
    }

    /**
     * Creates {@code PendingIntent} for
     * {@link FusedLocationProviderClient#requestLocationUpdates(LocationRequest, PendingIntent)}.
     *
     * @param activity
     *        The activity
     *
     * @param classService
     *        The class representing the user-defined {@code Service}
     *
     * @param action
     *        The intent action
     *
     * @param requestCode
     *        The intent request code
     *
     * @return  The {@code PendingIntent}
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    @SuppressLint("ObsoleteSdkInt")
    public static PendingIntent createPendingIntentService(@NonNull final Activity activity,
                                                           @NonNull final Class    classService,
                                                           @NonNull final String   action,
                                                           final int      requestCode) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            CoreLogger.logError("For apps targeting API level O, only PendingIntent.getBroadcast() should be used");
            return null;
        }
        final Intent intent = new Intent(activity, classService);
        intent.setAction(action);
        return PendingIntent.getService(activity, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Creates {@code PendingIntent} for
     * {@link FusedLocationProviderClient#requestLocationUpdates(LocationRequest, PendingIntent)}
     * with default intent parameters.
     *
     * @param activity
     *        The activity
     *
     * @param classService
     *        The class representing the user-defined {@code Service}
     *
     * @return  The {@code PendingIntent}
     *
     * @see #INTENT_DEFAULT_ACTION
     * @see #INTENT_DEFAULT_CODE
     */
    @SuppressWarnings("unused")
    public static PendingIntent createPendingIntentService(@NonNull final Activity activity,
                                                           @NonNull final Class    classService) {
        return createPendingIntentService(activity, classService, INTENT_DEFAULT_ACTION, INTENT_DEFAULT_CODE);
    }

    /**
     * Extracts location info from the {@code Intent}.
     *
     * @param intent
     *        The {@code Intent}
     *
     * @param action
     *        The intent action
     *
     * @return  The {@code Location}
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    public static Location getLocation(@NonNull final Intent intent, @NonNull final String action) {
        final String actionIntent = intent.getAction();
        if (action.equals(actionIntent)) {
            final LocationResult locationResult = LocationResult.extractResult(intent);
            if (locationResult != null) return locationResult.getLastLocation();

            CoreLogger.logError("LocationResult == null");
        }
        else
            CoreLogger.logError("action mismatch: found " + actionIntent + ", required " + action);

        return null;
    }

    /**
     * Extracts location info from the {@code Intent} with default intent parameters.
     *
     * @param intent
     *        The {@code Intent}
     *
     * @return  The {@code Location}
     *
     * @see #INTENT_DEFAULT_ACTION
     */
    @SuppressWarnings("unused")
    public static Location getLocation(@NonNull final Intent intent) {
        return getLocation(intent, INTENT_DEFAULT_ACTION);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void requestLocationUpdates(@NonNull final Activity activity,
                                          @NonNull final LocationRequest locationRequest) {
        super.requestLocationUpdates(activity, locationRequest);

        if (mPendingIntent != null)
            requestLocationUpdates(activity, locationRequest, mPendingIntent);
        else
            requestLocationUpdates(activity, locationRequest, mLocationCallback);
    }

    /**
     * Requests location updates.
     *
     * @param activity
     *        The activity
     *
     * @param locationRequest
     *        The {@code LocationRequest}
     *
     * @param locationCallback
     *        The {@code LocationCallback}
     */
    @SuppressWarnings("WeakerAccess")
    protected void requestLocationUpdates(@NonNull final Activity         activity,
                                          @NonNull final LocationRequest  locationRequest,
                                          @NonNull final LocationCallback locationCallback) {
        try {
            mFusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback, activity.getMainLooper())

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
        catch (SecurityException exception) {   // should never happen
            CoreLogger.log("failed", exception);
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
     *
     * @param pendingIntent
     *        The {@code PendingIntent}
     */
    @SuppressWarnings("WeakerAccess")
    protected void requestLocationUpdates(@NonNull final Activity        activity,
                                          @NonNull final LocationRequest locationRequest,
                                          @NonNull final PendingIntent   pendingIntent) {
        try {
            mFusedLocationClient.requestLocationUpdates(locationRequest, pendingIntent)
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
        catch (SecurityException exception) {   // should never happen
            CoreLogger.log("failed", exception);
        }
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void stopLocationUpdates(final Activity activity) {
        super.stopLocationUpdates(activity);

        if (mPendingIntent != null)
            mFusedLocationClient.removeLocationUpdates(mPendingIntent)
                    .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            log(task);
                        }
                    });
        else
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                    .addOnCompleteListener(activity, new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            log(task);
                        }
                    });
    }
}
