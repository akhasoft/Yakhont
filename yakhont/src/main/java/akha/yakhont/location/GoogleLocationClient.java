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
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
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
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import javax.inject.Provider;

/**
 * The client to work with {@link GoogleApiClient}-based Google Play Services Location API.
 *
 * @author akha
 */
public class GoogleLocationClient extends BaseGoogleLocationClient implements ConnectionCallbacks, OnConnectionFailedListener {

    private static final String                ARG_RESOLVING_ERROR      = TAG + ".resolving_error";
    private static final String                ARG_SYSTEM_ERROR_DIALOG  = TAG + ".system_error_dialog";

    private static final int                   REQUEST_CODE             = Utils.getRequestCode(RequestCodes.LOCATION_CONNECTION_FAILED);

    private              Provider<BaseDialog>  mToast;

    private              boolean               mWasPaused;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected            GoogleApiClient       mClient;

    private              boolean               mSystemErrorDialog       = true;
    private              boolean               mResolvingError;

    /**
     * Initialises a newly created {@code GoogleLocationClient} object.
     */
    public GoogleLocationClient() {
        CoreLogger.logWarning("please consider using new Google Location Services API");
    }

    /**
     *  Returns the Google Play Services Location API client.
     *
     * @return  This {@code GoogleApiClient}
     */
    @SuppressWarnings("unused")
    public GoogleApiClient getClient() {
        return mClient;
    }

    /**
     * Sets the "show system error dialog" flag. The default value is {@code true}.
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
     * Clears resolving error.
     */
    @SuppressWarnings("WeakerAccess")
    protected void clearResolvingError() {
        mResolvingError = false;
    }

    /**
     * Connects the client.
     */
    @SuppressWarnings("WeakerAccess")
    protected void connect() {
        CoreLogger.log("connect: isConnecting() " + mClient.isConnecting() + ", isConnected() " + mClient.isConnected() +
                ", mResolvingError " + mResolvingError);
        if (!mResolvingError && !mClient.isConnecting() && !mClient.isConnected()) mClient.connect();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onSaveInstanceState(Activity activity, Bundle savedInstanceState) {
        super.onSaveInstanceState(activity, savedInstanceState);

        savedInstanceState.putBoolean(ARG_RESOLVING_ERROR    , mResolvingError   );
        savedInstanceState.putBoolean(ARG_SYSTEM_ERROR_DIALOG, mSystemErrorDialog);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected void getFromBundle(final Bundle savedInstanceState) {
        super.getFromBundle(savedInstanceState);
        if (savedInstanceState == null) return;

        if (savedInstanceState.keySet().contains              (ARG_RESOLVING_ERROR    ))
            mResolvingError    = savedInstanceState.getBoolean(ARG_RESOLVING_ERROR    );
        if (savedInstanceState.keySet().contains              (ARG_SYSTEM_ERROR_DIALOG))
            mSystemErrorDialog = savedInstanceState.getBoolean(ARG_SYSTEM_ERROR_DIALOG);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onCreate(Activity activity, Bundle savedInstanceState) {
        mWasPaused = false;
        if (mToast == null) mToast = Core.getDagger().getToastLong();

        super.onCreate(activity, savedInstanceState);
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
        if (mWasPaused && mClient.isConnected()) startLocationUpdates(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onPause(Activity activity) {
        mWasPaused = true;

        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mClient.isConnected()) stopLocationUpdates(activity);
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
    @SuppressWarnings("WeakerAccess")
    @Override
    protected void requestLocationUpdates(@NonNull final Activity        activity,
                                          @NonNull final LocationRequest locationRequest) {
        super.requestLocationUpdates(activity, locationRequest);

        try {
            final PendingResult<Status> result = LocationServices.FusedLocationApi.requestLocationUpdates(
                    mClient, locationRequest, this, activity.getMainLooper());
            handleStatusResult("requestLocationUpdates", result);
        }
        catch (SecurityException exception) {   // should never happen
            CoreLogger.log("failed", exception);
        }
    }

    /**
     * Please refer to the base method description.
     */
    @SuppressWarnings("WeakerAccess")
    @Override
    protected void stopLocationUpdates(final Activity activity) {
        super.stopLocationUpdates(activity);

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
    protected void buildClient(@NonNull final Activity activity) {
        mClient = new GoogleApiClient.Builder(activity)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        CoreLogger.log("onConnected");
        try {
            onLocationChanged(LocationServices.FusedLocationApi.getLastLocation(mClient));
        }
        catch (SecurityException exception) {   // should never happen
            CoreLogger.log("failed", exception);
        }

        startLocationUpdates(LocationCallbacks.getActivity());
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
        if (!onConnectionFailedHelper(result))
            mLocationCallbacks.onLocationError(result.toString());
    }

    private boolean onConnectionFailedHelper(@NonNull ConnectionResult result) {
        CoreLogger.log("connection failed: error code " + result.getErrorCode() + ", result: " + result +
                ", resolving error: " + mResolvingError + ", system error dialog: " + mSystemErrorDialog);

        if (mResolvingError) return true;

        mResolvingError = true;

        final Activity activity = LocationCallbacks.getActivity();
        if (activity == null)
            CoreLogger.logError("activity == null");

        if (!result.hasResolution()) {
            if (!mSystemErrorDialog) {
                if (activity != null) mToast.get().start(activity, activity.getString(
                        akha.yakhont.R.string.yakhont_location_error_connection), null);

                clearResolvingError();
                return false;
            }

            if (activity != null) SupportHelper.showLocationErrorDialog(activity, result.getErrorCode());
            return false;
        }

        if (activity == null) {
            CoreLogger.logError("no startResolutionForResult");
            return false;
        }

        try {
            result.startResolutionForResult(activity, REQUEST_CODE);
        }
        catch (SendIntentException e) {
            CoreLogger.log("failed", e);
            clearResolvingError();
            connect();
        }
        return true;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static Dialog getErrorDialog(@NonNull final Activity activity, final int errorCode,
                                        @SuppressWarnings("SameParameterValue") final int requestCode) {
        return GoogleApiAvailability.getInstance().getErrorDialog(activity, errorCode, requestCode);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean onActivityResult(Activity activity, RequestCodes requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case LOCATION_CONNECTION_FAILED:
                clearResolvingError();

                switch (resultCode) {
                    case Activity.RESULT_OK:
                        connect();
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                    case Activity.RESULT_FIRST_USER:
                        CoreLogger.logWarning("unexpected result code " + resultCode);
                        break;
                    default:
                        CoreLogger.logWarning("unknown result code " + resultCode);
                        break;
                }
                break;

            case LOCATION_CLIENT:
                clearResolvingError();
                break;

            default:
                return false;
        }
        return true;
    }
}
