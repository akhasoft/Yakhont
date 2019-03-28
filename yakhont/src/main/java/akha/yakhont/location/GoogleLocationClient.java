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
import akha.yakhont.Core.Utils.RetainDialogFragment;
import akha.yakhont.CoreLogger;
// ProGuard issue
// import akha.yakhont.R;
import akha.yakhont.location.LocationCallbacks.LocationClient;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;

import java.lang.ref.WeakReference;

import javax.inject.Provider;

/**
 * The client to work with {@link GoogleApiClient}-based Google Play Services Location API.
 *
 * @author akha
 */
public class GoogleLocationClient extends BaseGoogleLocationClient implements ConnectionCallbacks, OnConnectionFailedListener {

    private static final String                ARG_DIALOG_ERROR         = TAG + ".dialog_error";
    private static final String                ARG_RESOLVING_ERROR      = TAG + ".resolving_error";
    private static final String                ARG_SYSTEM_ERROR_DIALOG  = TAG + ".system_error_dialog";

    private static final int                   REQUEST_CODE_ERROR       = Utils.getRequestCode(
            RequestCodes.LOCATION_CLIENT);
    private static final int                   REQUEST_CODE_FAILED      = Utils.getRequestCode(
            RequestCodes.LOCATION_CONNECTION_FAILED);

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
    public GoogleApiClient getGoogleApiClient() {
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
    @Override
    protected void buildClient(@NonNull final Activity activity) {
        super.buildClient(activity);

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

        final Activity activity = LocationCallbacks.getActivity();

        getLastLocation(activity);

        startLocationUpdates(activity);
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

        connect();
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

            if (activity != null) showLocationErrorDialog(activity, result.getErrorCode());
            return false;
        }

        if (activity == null) {
            CoreLogger.logError("no startResolutionForResult");
            return false;
        }

        try {
            result.startResolutionForResult(activity, REQUEST_CODE_FAILED);
        }
        catch (/*SendIntent*/Exception exception) {
            CoreLogger.log(exception);
            clearResolvingError();
            connect();
        }
        return true;
    }

    private static void showLocationErrorDialog(@NonNull final Activity activity, final int errorCode) {

        final LocationErrorDialogFragment errorDialogFragment = new LocationErrorDialogFragment();

        final Bundle args = new Bundle();
        args.putInt(ARG_DIALOG_ERROR, errorCode);
        errorDialogFragment.setArguments(args);

        if (activity instanceof FragmentActivity) {

            final FragmentManager fragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
            if (fragmentManager == null) {
                CoreLogger.logError("no ErrorDialogFragment: fragmentManager == null");
                return;
            }

            errorDialogFragment.setCurrentActivity(activity);

            errorDialogFragment.show(fragmentManager, LocationErrorDialogFragment.TAG);
        }
        else {
            final Dialog dialog = getErrorDialog(activity, errorCode, REQUEST_CODE_ERROR);

            //noinspection Convert2Lambda
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    LocationErrorDialogFragment.onDismiss(activity);
                }
            });

            dialog.show();
        }
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static class LocationErrorDialogFragment extends RetainDialogFragment {

        @SuppressWarnings("WeakerAccess")
        public  static final String            TAG                      = "LocationErrorDialogFragment";

        private WeakReference<Activity>        mActivity;

        private void setCurrentActivity(@NonNull final Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        private Activity getCurrentActivity() {
            Activity activity = null;
            if (mActivity == null)          // should never happen
                CoreLogger.logError("mActivity == null");
            else {
                activity = mActivity.get();
                if (activity == null)       // should never happen
                    CoreLogger.logError("activity == null");
            }
            return activity;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle arguments = getArguments();
            final int errorCode = arguments != null ? arguments.getInt(ARG_DIALOG_ERROR):
                    ConnectionResult.INTERNAL_ERROR;    // should never happen
            CoreLogger.log(arguments != null ? CoreLogger.getDefaultLevel(): CoreLogger.Level.ERROR,
                    "error code: " + errorCode);

            final Activity activity = getCurrentActivity();
            return GoogleLocationClient.getErrorDialog(activity != null ? activity:
                    Utils.getCurrentActivity() /* should never happen */, errorCode, REQUEST_CODE_ERROR);
        }

        private static void onDismiss(final Activity activity) {
            if (activity == null)
                CoreLogger.logError("activity == null, onDismiss failed");
            else
                Utils.onActivityResult(activity, REQUEST_CODE_ERROR,
                        Activity.RESULT_CANCELED /* ignored */, null);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);

            onDismiss(getCurrentActivity());
        }
    }

    private static Dialog getErrorDialog(@NonNull final Activity activity, final int errorCode,
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
