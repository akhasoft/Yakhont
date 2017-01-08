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
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.SupportHelper;
import akha.yakhont.location.LocationCallbacks.LocationClient;

import android.app.Activity;
import android.app.Dialog;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Date;

import javax.inject.Provider;

/**
 * The client to work with Google Play services location APIs.
 *
 * @author akha
 */
public class GoogleLocationClient implements LocationClient, ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public  static final String                TAG                                  = Utils.getTag(GoogleLocationClient.class);

    private static final String                ARG_REQUEST_UPDATES                  = TAG + ".request_updates";
    private static final String                ARG_LOCATION                         = TAG + ".location";
    private static final String                ARG_TIME                             = TAG + ".time";

    private static final String                ARG_RESOLVING_ERROR                  = TAG + ".resolving_error";

    // milliseconds
    private static final int                   UPDATE_INTERVAL_HIGH_ACCURACY        = 10 * 1000;
    private static final int                   UPDATE_INTERVAL_LOW_ACCURACY         = 60 * 1000;

    private static final int                   REQUEST_CODE                         = Utils.getRequestCode(RequestCodes.LOCATION_CONNECTION_FAILED);

    private              Provider<BaseDialog>  mToast;

    private              int                   mPriority, mInterval, mFastestInterval;

    private              boolean               mRequestingLocationUpdates, mWasPaused;

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected            LocationCallbacks     mLocationCallbacks;
    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected            GoogleApiClient       mClient;

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected            LocationRequest       mLocationRequest;

    private              Location              mCurrentLocation;
    private              Date                  mLastUpdateTime;

    private              boolean               mResolvingError, mSystemErrorDialog  = true;
    private   final      Object                mBuildLock                           = new Object();

    /**
     * Initialises a newly created {@code GoogleLocationClient} object.
     */
    public GoogleLocationClient() {
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
     * Sets the requesting location updates parameters.
     *
     * @param requestingLocationUpdates
     *        {@code true} for requesting location updates, {@code false} otherwise
     *
     * @param highAccuracy
     *        {@code true} for high accuracy location updates, {@code false} otherwise
     *
     * @return  This {@code GoogleLocationClient} object, so that setters can be chained
     */
    @SuppressWarnings("SameParameterValue")
    public GoogleLocationClient setRequestingLocationUpdates(final boolean requestingLocationUpdates, final boolean highAccuracy) {
        mRequestingLocationUpdates  = requestingLocationUpdates;

        mPriority                   = (highAccuracy) ? LocationRequest.PRIORITY_HIGH_ACCURACY: LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;

        mInterval                   = (highAccuracy) ? UPDATE_INTERVAL_HIGH_ACCURACY:          UPDATE_INTERVAL_LOW_ACCURACY;
        mFastestInterval            =  mInterval / 2;

        return this;
     }

    /**
     * Sets the requesting location updates parameters.
     *
     * @param requestingLocationUpdates
     *        {@code true} for requesting location updates, {@code false} otherwise
     *
     * @param priority
     *        The priority of the request (use {@link LocationRequest} priority constants)
     *
     * @param interval
     *        The desired interval for active location updates, in milliseconds
     *
     * @param fastestInterval
     *        The fastest interval for location updates, in milliseconds
     *
     * @return  This {@code GoogleLocationClient} object, so that setters can be chained
     */
    @SuppressWarnings("unused")
    public GoogleLocationClient setRequestingLocationUpdates(final boolean requestingLocationUpdates, final int priority,
                                                             final int interval, final int fastestInterval) {
        mRequestingLocationUpdates  = requestingLocationUpdates;

        mPriority                   = priority;

        mInterval                   = interval;
        mFastestInterval            = fastestInterval;

        return this;
    }

    /**
     * Sets the "show system error dialog" flag.
     *
     * @param systemErrorDialog
     *        {@code true} to display system error dialog, {@code false} otherwise
     *
     * @return  This {@code LocationClient} object
     */
    @SuppressWarnings("unused")
    public LocationClient showSystemErrorDialog(final boolean systemErrorDialog) {
        mSystemErrorDialog = systemErrorDialog;
        return this;
    }

    /**
     * Please refer to the base method description.
     */
    public void clearResolvingError() {
        mResolvingError = false;
    }

    private void getFromBundle(final Bundle savedInstanceState) {
        CoreLogger.log("getFromBundle");
        if (savedInstanceState == null) return;

        if (savedInstanceState.keySet().contains(ARG_REQUEST_UPDATES))
            mRequestingLocationUpdates = savedInstanceState.getBoolean(ARG_REQUEST_UPDATES);

        if (savedInstanceState.keySet().contains(ARG_LOCATION))
            setLocation((Location) savedInstanceState.getParcelable(ARG_LOCATION));

        if (savedInstanceState.keySet().contains(ARG_TIME))
            mLastUpdateTime = (Date) savedInstanceState.getSerializable(ARG_TIME);

        if (savedInstanceState.keySet().contains(ARG_RESOLVING_ERROR))
            mResolvingError = savedInstanceState.getBoolean(ARG_RESOLVING_ERROR);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onSaveInstanceState(Activity activity, Bundle savedInstanceState) {
        savedInstanceState.putBoolean     (ARG_REQUEST_UPDATES, mRequestingLocationUpdates);
        savedInstanceState.putParcelable  (ARG_LOCATION,        mCurrentLocation          );
        savedInstanceState.putSerializable(ARG_TIME,            mLastUpdateTime           );
        savedInstanceState.putBoolean     (ARG_RESOLVING_ERROR, mResolvingError           );
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void connect() {
        CoreLogger.log("connect: isConnecting() " + mClient.isConnecting() + ", isConnected() " + mClient.isConnected() +
                ", mResolvingError " + mResolvingError);

        if (!mResolvingError && !mClient.isConnecting() && !mClient.isConnected()) mClient.connect();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState, LocationCallbacks locationCallbacks) {

        mWasPaused = false;

        getFromBundle(savedInstanceState);

        mLocationCallbacks = locationCallbacks;

        if (mToast == null) mToast = Core.getDagger().getToastLong();

        buildClient(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onStart(Activity activity) {
        connect();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onResume(Activity activity) {
        if (mWasPaused && mClient.isConnected() && mRequestingLocationUpdates) startLocationUpdates(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onPause(Activity activity) {
        mWasPaused = true;

        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mClient.isConnected()) stopLocationUpdates();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onStop(Activity activity) {
        if (mClient.isConnected()) mClient.disconnect();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onDestroy(Activity activity) {
    }

    /**
     * Starts location updates.
     *
     * @param activity
     *        The Activity
     */
    @SuppressWarnings("WeakerAccess")
    protected void startLocationUpdates(final Activity activity) {
        CoreLogger.log("startLocationUpdates, LocationRequest: " + mLocationRequest);

        final PendingResult<Status> result = LocationServices.FusedLocationApi.requestLocationUpdates(
                mClient, mLocationRequest, this, activity == null ? null: activity.getMainLooper());
        handleStatusResult("startLocationUpdates", result);
    }

    /**
     * Stops location updates.
     */
    @SuppressWarnings("WeakerAccess")
    protected void stopLocationUpdates() {
        CoreLogger.log("stopLocationUpdates");

        final PendingResult<Status> result = LocationServices.FusedLocationApi.removeLocationUpdates(
                mClient, this);
        handleStatusResult("stopLocationUpdates", result);
    }

    private void handleStatusResult(@NonNull final String prefix, @NonNull final PendingResult<Status> result) {
        result.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                CoreLogger.log(prefix + ", status success: " + status.isSuccess());
                CoreLogger.log(prefix + ", status code   : " + status.getStatusCode());
                CoreLogger.log(prefix + ", status message: " + status.getStatusMessage());
                CoreLogger.log(prefix + ", status info   : " + status);
            }
        });
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onLocationChanged(Location location) {
        setLocation(location);
        mLastUpdateTime  = lastUpdateTime();

        mLocationCallbacks.onLocationChanged(location, mLastUpdateTime);
    }

    private void setLocation(final Location location) {
        CoreLogger.log("new location: " + location);

        mCurrentLocation = location;
    }

    private Date lastUpdateTime() {
        return new Date();
    }

    private void buildClient(@NonNull final Activity activity) {
        CoreLogger.log("buildClient");

        synchronized (mBuildLock) {
            mClient = new GoogleApiClient.Builder(activity)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();

            newLocationRequest();
        }
    }

    private void newLocationRequest() {
        mLocationRequest = new LocationRequest();

        mLocationRequest.setInterval       (mInterval       );
        mLocationRequest.setFastestInterval(mFastestInterval);
        mLocationRequest.setPriority       (mPriority       );
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        CoreLogger.log("onConnected, mRequestingLocationUpdates: " + mRequestingLocationUpdates);

        if (mCurrentLocation == null) {
            setLocation(LocationServices.FusedLocationApi.getLastLocation(mClient));
            mLastUpdateTime = lastUpdateTime();
        }

        if (mRequestingLocationUpdates) startLocationUpdates(LocationCallbacks.getActivity());
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onConnectionSuspended(int cause) {
        CoreLogger.log("cause " + cause);

        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs until onConnected() is called.

        clearResolvingError();

        connect();  // TODO: 11.09.2015
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        CoreLogger.log("connection failed: error code " + result.getErrorCode() + ", result: " + result +
                ", resolving error: " + mResolvingError + ", system error dialog: " + mSystemErrorDialog);

        if (mResolvingError) return;

        mResolvingError = true;

        final Activity activity = LocationCallbacks.getActivity();
        if (activity == null)
            CoreLogger.logError("activity == null");

        if (!result.hasResolution()) {
            if (!mSystemErrorDialog) {
                if (activity != null) mToast.get().start(activity, activity.getString(
                        akha.yakhont.R.string.yakhont_location_error_connection));

                clearResolvingError();
                return;
            }

            if (activity != null) SupportHelper.showLocationErrorDialog(activity, result.getErrorCode());
            return;
        }

        if (activity == null) {
            CoreLogger.logError("no startResolutionForResult");
            return;
        }

        try {
            result.startResolutionForResult(activity, REQUEST_CODE);
        }
        catch (IntentSender.SendIntentException e) {
            CoreLogger.log("failed", e);

            clearResolvingError();

            connect();
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Dialog getErrorDialog(@NonNull final Activity activity,
                                        final int errorCode, @SuppressWarnings("SameParameterValue") final int requestCode) {
        return GoogleApiAvailability.getInstance().getErrorDialog(activity, errorCode, requestCode);
    }
}
