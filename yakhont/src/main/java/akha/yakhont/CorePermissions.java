/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont;

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.ConfigurationChangedListener;
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.technology.Dagger2.UiModule;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Provider;

/**
 * The helper class for work with Android Dynamic Permissions API. Usage example:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * new CorePermissions.RequestBuilder(activity)
 *     .addOnGranted(android.Manifest.permission.ACCESS_FINE_LOCATION, new Runnable() {
 *         &#064;Override
 *         public void run() {
 *             // your code here (to run after granting dynamic permission), e.g. 'requestLocationUpdates()'
 *         }
 *     })
 *     .request();
 * </pre>
 *
 * @author akha
 */
@SuppressWarnings("JavadocReference")
public class CorePermissions implements ConfigurationChangedListener {

    private   static final String                   TAG             = Utils.getTag(CorePermissions.class);

    private   static final String                   ARG_VIEW_ID     = TAG + ".view_id";
    private   static final String                   ARG_PERMISSION  = TAG + ".permission";

    private   static final String                   ID_PREFIX       = "Yakhont: ";

    @IdRes
    private   static final int                      ID_VIEW_IDS     = R.id.yakhont_permissions_view_ids;
    @IdRes
    private   static final int                      ID_OBJECTS      = R.id.yakhont_permissions_objects;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        final List<Callbacks>          mCallbacks      = new ArrayList<>();
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static final Object                   sCallbacksLock  = new Object();

    private          final Provider<BaseDialog>     mAlertProvider, mAlertDeniedProvider;
    private                BaseDialog               mAlert, mAlertDenied;

    // for Activity.onResume() after Android App Settings
    private                boolean                  mHandlePermissions;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        final boolean                  mNotRunAppSettings;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected              int                      mRequestCode;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected        final String                   mRationale;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @IdRes
    protected              int                      mViewId;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected CorePermissions(final String rationale, final boolean notRunAppSettings) {
        mRationale              = rationale == null ? null: rationale.trim();
        mNotRunAppSettings      = notRunAppSettings;

        mAlertProvider          = Core.getDagger().getAlertPermission();
        mAlertDeniedProvider    = Core.getDagger().getAlertPermissionDenied();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public void onChangedConfiguration(final Configuration newConfig) {
        if (mAlert       != null) mAlert.stop();
        if (mAlertDenied != null) mAlertDenied.stop();

        mAlert       = null;
        mAlertDenied = null;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static boolean checkData(final Context context, final String permission) {
        if (context    == null)            CoreLogger.logError("context == null");
        if (TextUtils.isEmpty(permission)) CoreLogger.logError("empty permission");

        return context != null && permission != null;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess", "BooleanMethodIsAlwaysInverted"})
    protected static boolean checkData(final Context context, final String... permissions) {
        if (permissions == null) {
            CoreLogger.logError("permissions == null");
            return false;
        }
        if (permissions.length == 0) {
            CoreLogger.logError("permissions.length == 0");
            return false;
        }

        for (final String permission: permissions)
            if (!checkData(context, permission)) return false;

        return true;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static boolean check(final Callbacks... callbacks) {
        final boolean nOk = callbacks == null || callbacks.length == 0;
        if (nOk) CoreLogger.logError("no permissions callbacks");
        return !nOk;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static boolean check(final List<Callbacks> callbacks) {
        return check(callbacks == null ? null: callbacks.toArray(new Callbacks[0]));
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess", "SameReturnValue"})
    protected void requestWrapper(final Activity activity, final String... permissions) {
        CoreLogger.log("about to request permissions: " + getPermissionsDesc(permissions));
        ActivityCompat.requestPermissions(activity, permissions, mRequestCode);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected void requestHandler(final Activity activity, final String... permissions) {
        final ArrayList<String> rationalePermissions = new ArrayList<>();
        for (final String permission: permissions)
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
                rationalePermissions.add(permission);

        // from the official Google example:
        //
        // Request permission. It's possible this can be auto answered if device policy
        // sets the permission in a given state or the user denied the permission
        // previously and checked "Never ask again".
        if (rationalePermissions.isEmpty()) {
            requestWrapper(activity, permissions);
            return;
        }
        else if (mRationale == null) CoreLogger.logWarning(
                "shouldShowRequestPermissionRationale return true but rationale is empty");

        // from the official Google example:
        //
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        final String rationalePermissionsDesc = getPermissionsDesc(rationalePermissions.toArray(new String[0]));

        if (TextUtils.isEmpty(mRationale)) {
            CoreLogger.logWarning("rationale requested but not provided for permissions " +
                    rationalePermissionsDesc);
            requestWrapper(activity, permissions);
            return;
        }

        if (mAlert != null)
            CoreLogger.logError("permissions rationale alert dialog already exists");

        if (permissions.length > 1)
            CoreLogger.logWarning("more than 1 permissions for rationale: " + mRationale + ", " +
                    Arrays.toString(permissions));

        mAlert = mAlertProvider.get();
        UiModule.setId(mAlert, getPermissionId(permissions[0]));

        if (mAlert.start(mRationale, getIntent(mViewId, permissions[0])))
            CoreLogger.log("started rationale alert dialog for permissions " +
                    rationalePermissionsDesc);
        else
            CoreLogger.logError("can't start rationale alert dialog for permissions " +
                    rationalePermissionsDesc);
    }

    private static String getPermissionId(final String permission) {
        return ID_PREFIX + permission;
    }

    private static Intent getIntent(final @IdRes int viewId, final String permission) {
        final Intent intent = new Intent().putExtra(ARG_VIEW_ID, viewId);
        if (!TextUtils.isEmpty(permission))
            intent.putExtra(ARG_PERMISSION, permission);
        return intent;
    }

    /**
     * Checks whether the given permission was granted or not.
     *
     * @param context
     *        The Context
     *
     * @param permission
     *        The permission (e.g. Manifest.permission.ACCESS_FINE_LOCATION)
     *
     * @return  {@code true} if permission was granted, {@code false} otherwise
     */
    public static boolean check(final Context context, final String permission) {
        boolean result = checkData(context, permission);
        if (result) {
            final int check = ActivityCompat.checkSelfPermission(context, permission);
            if (check != PackageManager.PERMISSION_GRANTED)
                CoreLogger.logWarning("permission " + permission + ", check result: " +
                        getPermissionGrantResult(check));
            result = check == PackageManager.PERMISSION_GRANTED;
        }
        CoreLogger.log("permission " + permission + " check result: " + result);
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    public static String getPermissionGrantResult(final int permissionGrantResult) {
        switch (permissionGrantResult) {
            case PackageManager.PERMISSION_GRANTED   :
                return         "PERMISSION_GRANTED"  ;
            case PackageManager.PERMISSION_DENIED    :
                return         "PERMISSION_DENIED"   ;
            default:        // should never happen
                return "unknown permission grant result: " + Utils.getUnknownResult(permissionGrantResult);
        }
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "UnusedParameters", "unused", "RedundantSuppression"})
    public static void onActivityResult(@NonNull final Activity activity, final int requestCode,
                                        final int resultCode, final Intent data) {
        final Collection<CorePermissions> collection = onActivityResultHelper(activity, requestCode, resultCode, data);
        if (collection == null) return;

        for (final CorePermissions corePermissions: collection) {
            if (corePermissions.mAlert       != null) UiModule.releaseCountDownLatch(corePermissions.mAlert);
            if (corePermissions.mAlertDenied != null) UiModule.releaseCountDownLatch(corePermissions.mAlertDenied);

            corePermissions.mAlert       = null;
            corePermissions.mAlertDenied = null;
        }
    }

    private static Collection<CorePermissions> onActivityResultHelper(@NonNull final Activity activity,
                                                                               final int      requestCode,
                                                                               final int      resultCode,
                                                                               final Intent   data) {
        Utils.onActivityResult("CorePermissions", activity, requestCode, resultCode, data);
        final RequestCodes code = Utils.getRequestCode(requestCode);

        if (code != RequestCodes.PERMISSIONS_RATIONALE_ALERT &&
            code != RequestCodes.PERMISSIONS_DENIED_ALERT) {

            CoreLogger.log("CorePermissions: unknown request code " + requestCode);
            return null;
        }

        if (data == null) {
            CoreLogger.logError("permission data == null");
            return null;
        }
        if (!data.hasExtra(ARG_VIEW_ID)) {
            CoreLogger.logError("no view info in permission data");
            return null;
        }
        final int viewId = data.getIntExtra(ARG_VIEW_ID, Core.NOT_VALID_VIEW_ID);

        final List<CorePermissions> list = getCorePermissions(activity, viewId);
        if (list == null || list.size() == 0) {
            CoreLogger.logWarning("onActivityResult: no CorePermissions");
            return null;
        }

        List<CorePermissions> result = list;
        switch (code) {
            case PERMISSIONS_RATIONALE_ALERT:
                CoreLogger.log("PERMISSIONS_RATIONALE_ALERT result: " + UiModule.getPermissionResult(resultCode));

                switch (resultCode) {
                    case Activity.RESULT_OK:
                    case UiModule.SNACKBAR_DISMISSED_REASON_ACTION:
                        final String permission;
                        if (!data.hasExtra(ARG_PERMISSION)) {       // should never happen
                            CoreLogger.logError("no permission info in permission data");
                            permission = null;
                        }
                        else
                            permission = data.getStringExtra(ARG_PERMISSION);

                        final List<String> permissionsNoRationale = new ArrayList<>();
                        for (final CorePermissions corePermissions: list) {
                            final List<String> permissions = new ArrayList<>();

                            for (final Callbacks callbacks: corePermissions.mCallbacks)
                                if (TextUtils.isEmpty(corePermissions.mRationale))
                                    addPermission(permissionsNoRationale, callbacks.mPermission);
                                else if (permission != null && permission.equals(callbacks.mPermission))
                                    addPermission(permissions, callbacks.mPermission);

                            requestPermissions(permissions, corePermissions, activity);
                        }
                        requestPermissions(permissionsNoRationale, list.get(0), activity);

                        return null;

                    case Activity.RESULT_CANCELED:                          // swipe
                    case UiModule.SNACKBAR_DISMISSED_REASON_MANUAL:         // call to 'dismiss()'
                        result = new ArrayList<>(list);

                        for (int i = list.size() - 1; i >= 0; i--) {
                            final CorePermissions corePermissions = list.get(i);

                            if (check(corePermissions.mCallbacks))
                                //noinspection Convert2Lambda
                                handle(new CallableHelper() {
                                    @Override
                                    public void call(final String permission, final int grantResult,
                                                     final Callbacks callbacks, final CorePermissions corePermissions,
                                                     final List<CorePermissions> list) {
                                        callbacks.onGrantResult(PackageManager.PERMISSION_DENIED,
                                                corePermissions, list);
                                    }
                                }, corePermissions, list);
                        }
                        break;

                    case UiModule.SNACKBAR_DISMISSED_REASON_TIMEOUT:
                    case UiModule.SNACKBAR_DISMISSED_REASON_CONSECUTIVE:
                    case UiModule.SNACKBAR_DISMISSED_REASON_UNKNOWN:
                        break;      // nothing to do

                    default:
                        CoreLogger.logWarning("unknown result code: " + resultCode);
                        break;
                }
                break;

            case PERMISSIONS_DENIED_ALERT:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        final String applicationId = (String) Utils.getBuildConfigField("APPLICATION_ID");
                        if (applicationId == null) {
                            CoreLogger.logError("can't find BuildConfig.APPLICATION_ID");
                            break;
                        }

                        final View view = ViewHelper.getView(activity, viewId);
                        if (view != null) {
                            setTag(view, list.toArray(new CorePermissions[0]));

                            for (final CorePermissions corePermissions: list)
                                corePermissions.mHandlePermissions = true;
                        }
                        else
                            CoreLogger.logWarning("view == null");

                        activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", applicationId, null))
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                        result = null;
                        break;

                    case Activity.RESULT_CANCELED:
                    case Activity.RESULT_FIRST_USER:
                        break;      // nothing to do
                }
                break;
        }

        for (final CorePermissions corePermissions: list)
            Core.unregister(corePermissions);

        return result;
    }

    private static void addPermission(final List<String> permissions, final String permission) {
        if (permissions.contains(permission))    // should never happen
            CoreLogger.logWarning("duplicated permission " + permission);
        else
            permissions.add(permission);
    }

    private static void requestPermissions(final List<String> permissions,
                                           final CorePermissions corePermissions, final Activity activity) {
        if (permissions != null && permissions.size() > 0)
            corePermissions.requestWrapper(activity, permissions.toArray(new String[0]));
    }

    private static void setTag(final View view, final CorePermissions... corePermissions) {
        if (view == null) {
            CoreLogger.logError("CorePermissions view == null");
            return;
        }
        final Object tag = view.getTag(ID_OBJECTS);

        @SuppressWarnings("unchecked")
        final ArrayList<CorePermissions> list = tag == null ? new ArrayList<>(): (ArrayList<CorePermissions>) tag;
        if (tag == null) {
            CoreLogger.log("CorePermissions, about to view.setTag");
            ViewHelper.setTag(view, ID_OBJECTS, list);
        }

        for (final CorePermissions tmpCorePermissions: corePermissions) {
            final String tmpPermissionDesc = CorePermissions.getPermissionsDesc(tmpCorePermissions.mCallbacks);
            boolean found = false;

            if (tmpPermissionDesc == null)      // should never happen
                CoreLogger.logError("can't find CorePermissions description for callbacks: " +
                        tmpCorePermissions.mCallbacks);
            else
                for (final CorePermissions tmp: list)
                    if (tmpPermissionDesc.equals(CorePermissions.getPermissionsDesc(tmp.mCallbacks))) {
                        CoreLogger.logError("CorePermissions, object is already in list: " + tmp);
                        found = true;       // should never happen
                        break;
                    }
            if (!found) list.add(tmpCorePermissions);
        }
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused", "RedundantSuppression"})
    public static void onResume(final Activity activity) {
        final List<CorePermissions> list = get(activity, null);
        if (list == null) return;

        for (int i = list.size() - 1; i >= 0; i--) {
            final CorePermissions corePermissions = list.get(i);

            if (!corePermissions.mHandlePermissions) continue;
            corePermissions.mHandlePermissions = false;

            if (corePermissions.mAlertDenied != null)
                UiModule.releaseCountDownLatch(corePermissions.mAlertDenied);
            corePermissions.mAlertDenied = null;

            if (!check(corePermissions.mCallbacks)) continue;

            //noinspection Convert2Lambda
            handle(new CallableHelper() {
                @Override
                public void call(final String permission,   final int grantResult,
                                 final Callbacks callbacks, final CorePermissions corePermissions,
                                 final List<CorePermissions> list) {
                    callbacks.onGrantResult(ActivityCompat.checkSelfPermission(activity,
                            callbacks.mPermission), corePermissions, list);
                }
            }, corePermissions, list);
        }
    }

    private static List<CorePermissions> get(final Activity activity, final Integer requestCode) {
        final Set<Integer> viewIds = getIds(activity);
        if (viewIds == null) {
            CoreLogger.logWarning("failed get view IDs, set == null");
            return null;
        }
        for (final int viewId: viewIds) {
            final List<CorePermissions> list = getCorePermissions(activity, viewId);
            if (list != null) {
                final ArrayList<CorePermissions> result = new ArrayList<>();
                for (final CorePermissions corePermissions: list)
                    if (requestCode == null || corePermissions.mRequestCode == requestCode)
                        result.add(corePermissions);
                if (!result.isEmpty()) return result;
            }
        }
        return null;
    }

    // subject to call by the Yakhont Weaver
    /** @exclude */ @SuppressWarnings({"JavaDoc", "unused", "RedundantSuppression"})
    public static void onRequestPermissionsResult(final Activity activity, final int requestCode,
                                                  final String[] permissions, final int[] grantResults) {
        CoreLogger.log("CorePermissions.onRequestPermissionsResult: " + Arrays.toString(permissions) +
                ", results: " + Arrays.toString(toString(grantResults)));

        final List<CorePermissions> list = get(activity, requestCode);
        if (list == null)
            CoreLogger.log("CorePermissions: unknown request code " + requestCode);
        else
            for (int i = list.size() - 1; i >= 0; i--)
                onRequestHelper(activity, permissions, grantResults, list.get(i), list);
    }

    private static String[] toString(final int[] grantResults) {
        if (grantResults == null) return null;
        final String[] result = new String[grantResults.length];

        for (int i = 0; i < grantResults.length; i++)
            result[i] = getPermissionGrantResult(grantResults[i]);
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void onRequestHelper(final Activity activity,
                                          final String[] permissions, final int[] grantResults,
                                          @NonNull final CorePermissions corePermissions,
                                          @NonNull final List<CorePermissions> list) {
        if (onRequestHandler(activity, permissions, grantResults, corePermissions, list) != null)
            Core.unregister(corePermissions);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static List<CorePermissions> getCorePermissions(final Activity activity, @IdRes final int viewId) {
        final View view = ViewHelper.getView(activity, viewId);
        @SuppressWarnings("unchecked")
        final List<CorePermissions> result = view == null ? null: (List<CorePermissions>) view.getTag(ID_OBJECTS);

        CoreLogger.log(result == null ? Level.ERROR: CoreLogger.getDefaultLevel(),
                "CorePermissions == " + result);
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static Boolean onRequestHandler(final Activity activity,
                                              final String[] permissions, final int[] grantResults,
                                              @NonNull final CorePermissions corePermissions,
                                              @NonNull final List<CorePermissions> list) {
        // Snackbar's queue handling: remove rationale's Snackbar(s) for already granted permission(s)
        //   and call the next Snackbar (if any) from the queue
        if (corePermissions.mAlert != null) {
            final ArrayList<String> grantedPermissions = new ArrayList<>();
            if (grantResults != null && permissions != null)
                for (int i = 0; i < grantResults.length; i++)
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                        grantedPermissions.add(getPermissionId(permissions[i]));

            UiModule.releaseCountDownLatch(corePermissions.mAlert, grantedPermissions);
            corePermissions.mAlert = null;
        }

        if (permissions  == null) {     // should never happen
            CoreLogger.logError("permissions == null");
            return false;
        }
        if (grantResults == null) {     // should never happen
            CoreLogger.logError("grantResults == null");
            return false;
        }
        // from the official Google example:
        //   if user interaction was interrupted, the permission request is cancelled and you receive empty arrays
        if (grantResults.length == 0) {
            CoreLogger.logWarning("User interaction was cancelled");
            return false;
        }
        if (!checkData(activity, permissions)) return false;

        //noinspection Convert2Lambda
        final CallableHelper callableHelper = new CallableHelper() {
            @Override
            public void call(final String permission, final int grantResult, final Callbacks callbacks,
                             final CorePermissions corePermissions, final List<CorePermissions> list) {
                if (callbacks.mPermission.equals(permission))
                    callbacks.onGrantResult(grantResult, corePermissions, list);
            }
        };

        boolean granted = true;
        for (int i = 0; i < grantResults.length; i++)
            switch (grantResults[i]) {
                case PackageManager.PERMISSION_GRANTED:
                    CoreLogger.log("Permission " + permissions[i] + " was granted");

                    handle(callableHelper, permissions[i], grantResults[i], corePermissions, list);
                    break;

                case PackageManager.PERMISSION_DENIED:
                    CoreLogger.logWarning("Permission " + permissions[i] + " was denied");

                    granted = false;
                    break;

                default:    // should never happen
                    CoreLogger.logWarning("Unknown grant result " + grantResults[i] +
                            " for permission " + permissions[i]);
                    granted = false;
                    break;
            }
        if (granted) return true;

        // Notify the user that they have rejected a permission (copied from the official Google resources);
        //   from the official Google example:

        // It is important to remember that a permission might have been rejected without asking
        // the user for permission (device policy or "Never ask again" prompts). Therefore, a user
        // interface 'affordance' is typically implemented when permissions are denied. Otherwise,
        // your app could appear unresponsive to touches or interactions which have required permissions.

        CoreLogger.logWarning("Permission denied, mNotRunAppSettings: " + corePermissions.mNotRunAppSettings);

        if (corePermissions.mNotRunAppSettings) {
            for (int i = 0; i < grantResults.length; i++)
                if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                    handle(callableHelper, permissions[i], grantResults[i], corePermissions, list);
            return null;
        }

        if (corePermissions.mAlertDenied != null)
            CoreLogger.logError("permissions denied alert dialog is already exists");

        corePermissions.mAlertDenied = corePermissions.mAlertDeniedProvider.get();

        if (UiModule.isSnackbar(corePermissions.mAlertDenied) && UiModule.hasSnackbars()) {
            CoreLogger.logWarning("permission denied alert dialog will not be started 'cause " +
                    "Snackbar's queue is not empty");

            corePermissions.mAlertDenied = null;
            return null;
        }

        if (!corePermissions.mAlertDenied.start(permissions.length == 1 ?
                        activity.getString(R.string.yakhont_permission_denied_alert):
                        activity.getString(R.string.yakhont_permissions_denied_alert),
                getIntent(corePermissions.mViewId, null))) {
            // should never happen
            CoreLogger.logError("can not start permission denied alert dialog");

            for (int i = 0; i < grantResults.length; i++)
                if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                    handle(callableHelper, permissions[i], grantResults[i], corePermissions, list);
        }
        return null;
    }

    private static void handle(@NonNull final CallableHelper callable, @NonNull final String permission,
                               final int grantResult, @NonNull final CorePermissions corePermissions,
                               final List<CorePermissions> list) {
        for (int i = corePermissions.mCallbacks.size() - 1; i >= 0; i--)
            callable.call(permission, grantResult, corePermissions.mCallbacks.get(i), corePermissions, list);
    }

    private static void handle(@NonNull final CallableHelper callable, @NonNull final CorePermissions corePermissions,
                               final List<CorePermissions> list) {
        handle(callable, "" /* not used */, 0 /* not used */, corePermissions, list);
    }

    private interface CallableHelper {
        void call(String permission, int grantResult, Callbacks callbacks,
                  CorePermissions corePermissions, List<CorePermissions> list);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static Set<Integer> getIds(final Activity activity) {
        if (activity == null) {
            CoreLogger.logError("getIds(): activity == null");
            return null;
        }

        final View view = ViewHelper.getView(activity);
        if (view == null) {
            CoreLogger.logError("view == null");
            return null;
        }

        final Set<Integer> set = getIdsHelper(view);

        CoreLogger.log(set != null ? CoreLogger.getDefaultLevel(): Level.WARNING,
                "CorePermissions set of view IDs: " + set);
        return set;
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> getIdsHelper(@NonNull final View view) {
        return (Set<Integer>) view.getTag(ID_VIEW_IDS);
    }

    private static String getPermissionsDesc(final List<Callbacks> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) return null;

        return getPermissionsDesc(callbacks.toArray(new Callbacks[0]));
    }

    private static String getPermissionsDesc(final Callbacks... callbacks) {
        if (callbacks == null || callbacks.length == 0) return null;

        final String[] permissions = new String[callbacks.length];
        for (int i = 0; i < callbacks.length; i++)
            permissions[i] = callbacks[i].mPermission;

        return getPermissionsDesc(permissions);
    }

    private static String getPermissionsDesc(final String... permissions) {
        if (permissions == null || permissions.length == 0) return null;

        Arrays.sort(permissions);
        return Arrays.toString(permissions);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The callbacks to handle dynamic permissions.
     */
    @SuppressWarnings("WeakerAccess")
    public abstract static class Callbacks {

        private final String                        mPermission;

        /**
         * Initialises a newly created {@code Callbacks} object.
         *
         * @param permission
         *        The permission
         */
        @SuppressWarnings("WeakerAccess")
        public Callbacks(final String permission) {
            if (TextUtils.isEmpty(permission)) throw new RuntimeException("empty permission");
            mPermission = permission;
        }

        /**
         * Returns permission associated with given callbacks.
         *
         * @return  The permission
         */
        @SuppressWarnings({"unused", "RedundantSuppression"})
        public String getPermission() {
            return mPermission;
        }

        private void onGrantResult(final int grantResult, final CorePermissions corePermissions,
                                   final List<CorePermissions> list) {
            final boolean success = grantResult == PackageManager.PERMISSION_GRANTED;

            CoreLogger.log(success ? CoreLogger.getDefaultLevel(): Level.WARNING,
                    "permission " + mPermission + ", grant result " + grantResult);
            try {
                if (success) onGranted();
                else         onDenied ();
            }
            catch (Exception exception) {
                CoreLogger.log(exception);
            }

            synchronized (sCallbacksLock) {
                if (corePermissions != null) corePermissions.mCallbacks.remove(this);

                if (list != null && corePermissions != null && corePermissions.mCallbacks.isEmpty())
                    list.remove(corePermissions);
            }
        }

        /**
         * Runs on permission denied callback.
         * <p>Note: don't call this method (just override), it should be called by Yakhont only.
         */
        @SuppressWarnings("WeakerAccess")
        public void onDenied() {
            CoreLogger.logWarning("denied permission " + mPermission);
        }

        /**
         * Runs on permission granted callback.
         * <p>Note: don't call this method (just override), it should be called by Yakhont only.
         */
        @SuppressWarnings("WeakerAccess")
        public void onGranted() {
            CoreLogger.log("granted permission " + mPermission);
        }

        @NonNull
        @Override
        public String toString() {
            return "callbacks for permission '" + mPermission + "'";
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for permission(s) requests.
     */
    public static class RequestBuilder {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final WeakReference<Activity>     mActivity;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       String                      mRationale;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       boolean                     mNotRunAppSettings;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final List<Callbacks>             mCallbacks      = new ArrayList<>();

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Integer                     mRequestCode;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @IdRes
        protected       int                         mViewId         = Core.NOT_VALID_VIEW_ID;

        /**
         * Initialises a newly created {@code RequestBuilder} object.
         *
         * @param activity
         *        The Activity (or null for the current one)
         */
        @SuppressWarnings("WeakerAccess")
        public RequestBuilder(final Activity activity) {
            mActivity = activity == null ? null: new WeakReference<>(activity);
        }

        /**
         * Adds on permission(s) granted or denied callbacks.
         *
         * @param callbacks
         *        The callbacks (or null to clear callbacks list)
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
        public RequestBuilder addCallbacks(final Callbacks... callbacks) {
            if (callbacks == null)
                mCallbacks.clear();
            else
                Collections.addAll(mCallbacks, callbacks);
            return this;
        }

        /**
         * Returns th list of already added permission's callbacks.
         *
         * @return  The callback's list
         */
        @SuppressWarnings({"unused", "RedundantSuppression"})
        public List<Callbacks> getCallbacks() {
            return mCallbacks;
        }

        /**
         * Adds on permission granted callback.
         *
         * @param runnable
         *        The callback (or null to clear callbacks list)
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        public RequestBuilder addOnGranted(final String permission, final Runnable runnable) {
            if (permission == null)
                CoreLogger.logError("permission == null");
            else
                addCallbacks(runnable == null ? null: new Callbacks(permission) {
                    @SuppressWarnings({"unused", "RedundantSuppression"})
                    @Override
                    public void onGranted() {
                        super.onGranted();
                        runnable.run();
                    }
                });
            return this;
        }

        /**
         * Set to {@code true} to prevent showing Android Application Settings.
         *
         * @param notRunAppSettings
         *        The value to set
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        public RequestBuilder setNotRunAppSettings(final boolean notRunAppSettings) {
            mNotRunAppSettings = notRunAppSettings;
            return this;
        }

        /**
         * Sets the permission rationale. Please refer to
         * {@link ActivityCompat#shouldShowRequestPermissionRationale} for more info.
         *
         * @param rationale
         *        The rationale (provide "" to suppress log error message concerning 'shouldShowRequestPermissionRationale')
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        public RequestBuilder setRationale(final String rationale) {
            mRationale = rationale;
            return this;
        }

        /**
         * Sets the permission rationale. Please refer to
         * {@link ActivityCompat#shouldShowRequestPermissionRationale} for more info.
         *
         * @param rationaleId
         *        The rationale ID in resources
         *
         * @param args
         *        The additional arguments (if any) to format the rationale
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        public RequestBuilder setRationale(@StringRes final int rationaleId, final Object... args) {
            mRationale = Objects.requireNonNull(Utils.getApplication()).getString(rationaleId, args);
            return this;
        }

        /**
         * Sets the {@link View} to use.
         *
         * @param viewId
         *        The View to use
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings({"unused", "RedundantSuppression"})
        public RequestBuilder setView(@IdRes final int viewId) {
            mViewId = viewId;
            return this;
        }

        /**
         * Sets the request code to use. Please refer to {@link Activity#onActivityResult}
         * and {@link Activity#onRequestPermissionsResult} for more info.
         *
         * @param requestCode
         *        The request code to use
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        public RequestBuilder setRequestCode(@IntRange(from = 0) final Integer requestCode) {
            mRequestCode = requestCode;
            return this;
        }

        /**
         * Requests permission(s).
         *
         * @return  {@code true} if permission(s) was already granted, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        public boolean request() {
            final boolean result = requestHelper();

            CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.INFO, "request result: " + result);
            return result;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected boolean requestHelper() {
            if (!check(mCallbacks)) return false;
            CoreLogger.log("callbacks " + Arrays.toString(mCallbacks.toArray(new Callbacks[0])));

            Activity tmpActivity = mActivity == null ? null: mActivity.get();
            if (tmpActivity == null) tmpActivity = Utils.getCurrentActivity();
            if (tmpActivity == null) {
                CoreLogger.logError("activity is null");
                return false;
            }
            final Activity activity = tmpActivity;

            final CorePermissions corePermissions       = new CorePermissions(mRationale, mNotRunAppSettings);
            corePermissions.mViewId                     = mViewId;

            for (final Callbacks callbacks: mCallbacks) {
                boolean found = false;
                for (final Callbacks tmp: corePermissions.mCallbacks)
                    if (callbacks.mPermission.equals(tmp.mPermission)) {
                        found = true;
                        break;
                    }
                if (found)
                    CoreLogger.logError("callbacks for permission '" + callbacks.mPermission +
                            "' are already defined");
                else
                    corePermissions.mCallbacks.add(callbacks);
            }

            if (mRequestCode == null)
                mRequestCode = Utils.getRequestCode(RequestCodes.PERMISSIONS_ALERT, activity);
            corePermissions.mRequestCode                = mRequestCode;

            final List<String> permissions = new ArrayList<>();
            //noinspection Convert2Lambda
            handle(new CallableHelper() {
                @Override
                public void call(final String permission,   final int grantResult,
                                 final Callbacks callbacks, final CorePermissions corePermissions,
                                 final List<CorePermissions> list) {
                    if (check(activity, callbacks.mPermission))
                        callbacks.onGrantResult(PackageManager.PERMISSION_GRANTED, corePermissions, list);
                    else
                        permissions.add(callbacks.mPermission);
                }
            }, corePermissions, null);

            if (permissions.isEmpty()) {
                CoreLogger.log("permissions are already granted, so nothing to do");
                return true;
            }
            if (!setupView(activity, corePermissions)) {
                CoreLogger.logError("setupView() returned false");
                return false;
            }
            Core.register(corePermissions);

            corePermissions.requestHandler(activity, permissions.toArray(new String[0]));
            return true;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected boolean setupView(@NonNull final Activity        activity,
                                    @NonNull final CorePermissions corePermissions) {
            final View view = ViewHelper.getView(activity, mViewId);
            if (!checkView(view)) return false;

            final View baseView = ViewHelper.getView(activity);
            if (!checkView(baseView)) return false;

            if (baseView.getTag(ID_VIEW_IDS) == null)
                ViewHelper.setTag(baseView, ID_VIEW_IDS, Utils.<Integer>newSet());

            final Set<Integer> viewIds = getIds(activity);
            if (viewIds == null) return false;

            final boolean result = viewIds.add(mViewId);
            CoreLogger.log(result ? CoreLogger.getDefaultLevel(): Level.ERROR,
                    "CorePermissions add view ID: " + result);

            setTag(view, corePermissions);
            return true;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean checkView(final View view) {
            return view != null;
        }
    }
}
