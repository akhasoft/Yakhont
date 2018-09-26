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

package akha.yakhont;

import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.ConfigurationChangedListener;
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.ViewHelper;
import akha.yakhont.CoreLogger.Level;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.IdRes;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import javax.inject.Provider;

/**
 * The helper class for work with Android Permissions API. Usage example:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * new CorePermissions.RequestBuilder(activity, android.Manifest.permission.ACCESS_FINE_LOCATION)
 *     .setOnGranted(new Runnable() {
 *         &#064;Override
 *         public void run() {
 *             // your code here, e.g. requestLocationUpdates()
 *         }
 *     })
 *     .request();
 * </pre>
 *
 * @author akha
 */
public class CorePermissions implements ConfigurationChangedListener {

    private static final String TAG                    = Utils.getTag(CorePermissions.class);
    private static final String ARG_PERMISSIONS        = TAG + ".permissions";
    private static final String ARG_VIEW_ID            = TAG + ".view_id";

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected     Runnable               mOnDenied;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected     Runnable               mOnGranted;

    private final Provider<BaseDialog>   mAlertProvider, mAlertDeniedProvider;
    private       BaseDialog             mAlert, mAlertDenied;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected     int                    mRequestCode;
    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    @IdRes
    protected     int                    mViewId;

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected PermissionsNamesTranslator mPermissionsNamesTranslator;

    /**
     * This class can be used to customize the visual representation of permissions.
     */
    @SuppressWarnings("unused")
    public interface PermissionsNamesTranslator {

        /**
         * Returns the visual representation of permissions to use in GUI.
         *
         * @param permissions
         *        The permissions to display
         *
         * @return  The visual representation of permissions
         */
        String getDisplayNames(String[] permissions);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected CorePermissions() {
        mAlertProvider          = Core.getDagger().getAlertPermission();
        mAlertDeniedProvider    = Core.getDagger().getAlertPermissionDenied();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    public void onChangedConfiguration(Configuration newConfig) {
        if (mAlert       != null) mAlert.stop();
        if (mAlertDenied != null) mAlertDenied.stop();

        mAlert       = null;
        mAlertDenied = null;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static boolean checkData(final Context context, final String permission) {
        if (context    == null) CoreLogger.logError("context == null");
        if (permission == null) CoreLogger.logError("permission == null");
        return context != null && permission != null;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess", "BooleanMethodIsAlwaysInverted"})
    protected static boolean checkData(final Activity activity, final String[] permissions) {
        if (permissions == null) {
            CoreLogger.logError("permissions == null");
            return false;
        }
        if (permissions.length == 0) {
            CoreLogger.logError("permissions.length == 0");
            return false;
        }

        for (final String permission: permissions)
            if (!checkData(activity, permission)) return false;
        return true;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static String[] check(final Activity activity, final String[] permissions) {
        final ArrayList<String> list = new ArrayList<>();
        for (final String permission: permissions)
            if (!check(activity, permission))
                list.add(permission);

        if (list.size() == 0)
            CoreLogger.log("permissions" + Arrays.deepToString(permissions) +
                    " are already granted, so nothing to do");

        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return list.size() == 0 ? null: list.toArray(new String[list.size()]);
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess", "SameReturnValue"})
    protected boolean requestWrapper(final Activity activity, final String[] permissions) {
        CoreLogger.log("about to request permissions: " + Arrays.deepToString(permissions));

        ActivityCompat.requestPermissions(activity, permissions, mRequestCode);
        return false;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected boolean requestHandler(final Activity activity, String[] permissions) {

        boolean shouldProvideRationale = false;
        for (final String permission: permissions)
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                shouldProvideRationale = true;
                break;
            }

        if (!shouldProvideRationale) {
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".

            return requestWrapper(activity, permissions);
        }

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.

        String permissions2display;
        if (mPermissionsNamesTranslator != null)
            permissions2display = mPermissionsNamesTranslator.getDisplayNames(permissions);
        else {
            final StringBuilder builder = new StringBuilder(getPermissionDisplayName(permissions[0]));
            for (int i = 1; i < permissions.length; i++)
                builder.append(", ").append(getPermissionDisplayName(permissions[i]));
            permissions2display = builder.toString();
        }

        if (mAlert != null)
            CoreLogger.logError("permissions alert is dialog already exists");

        mAlert = mAlertProvider.get();

        if (!mAlert.start(activity, permissions.length == 1 ?
                        activity.getString(akha.yakhont.R.string.yakhont_permission_alert,  permissions2display):
                        activity.getString(akha.yakhont.R.string.yakhont_permissions_alert, permissions2display),
                new Intent().putExtra(ARG_PERMISSIONS, permissions).putExtra(ARG_VIEW_ID, mViewId)))
            CoreLogger.logError("can not start permissions alert dialog");

        return false;
    }

    private static String getPermissionDisplayName(@NonNull final String permission) {
        final int idx = permission.lastIndexOf('.');
        return idx < 0 ? permission: permission.substring(idx + 1);
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
        final boolean result = checkData(context, permission) &&
                ActivityCompat.checkSelfPermission(context, permission)
                        == PackageManager.PERMISSION_GRANTED;
        CoreLogger.log("permission " + permission + " check result: " + result);
        return result;
    }

    /**
     * Called by the Yakhont Weaver. Please refer to {@link Activity#onActivityResult Activity.onActivityResult()}.
     */
    @SuppressWarnings({"UnusedParameters", "unused"})
    public static void onActivityResult(@NonNull final Activity activity, final int requestCode,
                                        final int resultCode, final Intent data) {
        Utils.onActivityResult("CorePermissions", activity, requestCode, resultCode, data);
        final RequestCodes code = Utils.getRequestCode(requestCode);

        if (code != RequestCodes.PERMISSIONS_RATIONALE_ALERT &&
            code != RequestCodes.PERMISSIONS_DENIED_ALERT) {

            CoreLogger.logWarning("unknown request code " + requestCode);
            return;
        }
        if (data == null) {
            CoreLogger.logError("intent == null");
            return;
        }
        if (!data.hasExtra(ARG_VIEW_ID)) {
            CoreLogger.logError("no view info in intent");
            return;
        }

        final CorePermissions corePermissions = getCorePermissions(activity,
                data.getIntExtra(ARG_VIEW_ID, Core.NOT_VALID_VIEW_ID));
        if (corePermissions == null) return;

        switch (code) {
            case PERMISSIONS_RATIONALE_ALERT:
                corePermissions.mAlert = null;

                switch (resultCode) {
                    case Activity.RESULT_OK:
                        corePermissions.requestWrapper(activity, data.getStringArrayExtra(ARG_PERMISSIONS));
                        return;

                    default:    // fall through
                        CoreLogger.logWarning("unknown result code " + resultCode);

                    case Activity.RESULT_CANCELED:
                    case Activity.RESULT_FIRST_USER:
                        runOnDenied(corePermissions.mOnDenied);
                        break;
                }
                break;

            case PERMISSIONS_DENIED_ALERT:
                corePermissions.mAlertDenied = null;

                switch (resultCode) {
                    case Activity.RESULT_OK:
                        final String applicationId = (String) Utils.getBuildConfigField(
                                activity.getApplication().getPackageName(), "APPLICATION_ID");
                        if (applicationId == null) {
                            CoreLogger.logError("can't find BuildConfig.APPLICATION_ID");
                            break;
                        }

                        activity.startActivity(new Intent()
                                .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", applicationId, null))
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        break;

                    case Activity.RESULT_CANCELED:
                    case Activity.RESULT_FIRST_USER:
                        break;

                    default:
                        CoreLogger.logWarning("unknown result code " + resultCode);
                        break;
                }
                break;
        }

        Core.unregister(corePermissions);
    }

    /**
     * Called by the Yakhont Weaver. Please refer to {@link Activity#onRequestPermissionsResult Activity.onRequestPermissionsResult()}.
     */
    @SuppressWarnings("unused")
    public static void onRequestPermissionsResult(final Activity activity, final int requestCode,
                                                  final String[] permissions, final int[] grantResults) {
        final Set<Integer> viewIds = getIds(activity);
        if (viewIds == null) {
            CoreLogger.logError("failed get view IDs, set == null");
            return;
        }

        CorePermissions corePermissions = null;
        for (final int viewId: viewIds) {
            final CorePermissions corePermissionsTmp = getCorePermissions(activity, viewId);
            if (corePermissionsTmp != null && corePermissionsTmp.mRequestCode == requestCode) {
                corePermissions = corePermissionsTmp;
                break;
            }
        }

        if (corePermissions == null)
            CoreLogger.logWarning("unknown request code " + requestCode);
        else
            onRequestHelper(activity, permissions, grantResults, corePermissions);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void onRequestHelper(final Activity activity,
                                          final String[] permissions, final int[] grantResults,
                                          @NonNull final CorePermissions corePermissions) {
        Boolean result = onRequestHandler(activity, permissions, grantResults, corePermissions);
        if (result == null)
            result = false;
        else
            Core.unregister(corePermissions);

        if (!result) runOnDenied(corePermissions.mOnDenied);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static CorePermissions getCorePermissions(final Activity activity, @IdRes final int viewId) {
        final View view = ViewHelper.getView(activity, viewId);
        final CorePermissions result = view == null ? null:
                (CorePermissions) view.getTag(akha.yakhont.R.id.yakhont_permissions_object);

        CoreLogger.log(result == null ? Level.ERROR: Level.DEBUG, "CorePermissions == " + result);
        return result;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static Boolean onRequestHandler(final Activity activity,
                                              final String[] permissions, final int[] grantResults,
                                              @NonNull final CorePermissions corePermissions) {
        if (grantResults == null) {
            CoreLogger.logError("grantResults == null");
            return false;
        }
        if (!checkData(activity, permissions)) return false;

        // If user interaction was interrupted, the permission request is cancelled and you receive empty arrays
        if (grantResults.length == 0) {
            CoreLogger.logWarning("User interaction was cancelled");
            return false;
        }

        boolean granted = true;
        for (int i = 0; i < grantResults.length; i++)
            switch (grantResults[i]) {
                case PackageManager.PERMISSION_GRANTED:
                    CoreLogger.log("Permission " + permissions[i] + " was granted");
                    break;

                case PackageManager.PERMISSION_DENIED:
                    CoreLogger.logError("Permission " + permissions[i] + " was denied");
                    granted = false;
                    break;

                default:
                    CoreLogger.logWarning("Unknown grant result " + grantResults[i] +
                            " for permission " + permissions[i]);
                    granted = false;
                    break;
            }

        if (granted) {
            runOnGranted(corePermissions.mOnGranted);
            return true;
        }

        // Notify the user that they have rejected a permission.

        // It is important to remember that a permission might have been
        // rejected without asking the user for permission (device policy or "Never ask
        // again" prompts). Therefore, a user interface affordance is typically implemented
        // when permissions are denied. Otherwise, your app could appear unresponsive to
        // touches or interactions which have required permissions.

        CoreLogger.logError("Permission denied");

        if (corePermissions.mAlertDenied != null)
            CoreLogger.logError("permissions denied alert dialog is already exists");

        corePermissions.mAlertDenied = corePermissions.mAlertDeniedProvider.get();

        if (!corePermissions.mAlertDenied.start(activity, permissions.length == 1 ?
                        activity.getString(akha.yakhont.R.string.yakhont_permission_denied_alert):
                        activity.getString(akha.yakhont.R.string.yakhont_permissions_denied_alert),
                new Intent().putExtra(ARG_VIEW_ID, corePermissions.mViewId)))
            CoreLogger.logError("can not start permission denied alert dialog");

        return null;
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static Set<Integer> getIds(final Activity activity) {
        if (activity == null) {
            CoreLogger.logError("activity == null");
            return null;
        }

        final View view = ViewHelper.getView(activity);
        if (view == null) {
            CoreLogger.logError("view == null");
            return null;
        }

        final Set<Integer> set = getIdsHelper(view);

        CoreLogger.log(set != null ? Level.DEBUG: Level.ERROR, "CorePermissions set of view IDs: " + set);
        return set;
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> getIdsHelper(@NonNull final View view) {
        return (Set<Integer>) view.getTag(akha.yakhont.R.id.yakhont_permissions_view_ids);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void runOnGranted(final Runnable callback) {
        runCallback(callback, false);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void runOnDenied(final Runnable callback) {
        runCallback(callback, true);
    }

    /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected static void runCallback(final Runnable callback, final boolean denied) {
        CoreLogger.log(!denied && callback == null ? Level.WARNING: Level.DEBUG,
                (denied ? "onDenied": "onGranted") + " == " + callback);
        if (callback != null) callback.run();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builder class for permission(s) requests.
     */
    public static class RequestBuilder {

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final WeakReference<Activity>     mActivity;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected final String[]                    mPermissions;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       boolean                     mUseRationale   = true;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Runnable                    mOnDenied;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Runnable                    mOnGranted;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       PermissionsNamesTranslator  mPermissionsNamesTranslator;

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected       Integer                     mRequestCode;
        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        @IdRes
        protected       int                         mViewId         = Core.NOT_VALID_VIEW_ID;

        /**
         * Initialises a newly created {@code RequestBuilder} object.
         *
         * @param activity
         *        The Activity
         *
         * @param permission
         *        The permission (e.g. Manifest.permission.ACCESS_FINE_LOCATION)
         */
        public RequestBuilder(final Activity activity, final String permission) {
            this(activity, new String[] {permission});
        }

        /**
         * Initialises a newly created {@code RequestBuilder} object.
         *
         * @param activity
         *        The Activity
         *
         * @param permissions
         *        The permissions
         */
        @SuppressWarnings("WeakerAccess")
        public RequestBuilder(final Activity activity, final String[] permissions) {
            mActivity    = new WeakReference<>(activity);
            mPermissions = permissions;
        }

        /**
         * Sets on permission(s) denied callback.
         *
         * @param runnable
         *        The callback
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public RequestBuilder setOnDenied(final Runnable runnable) {
            mOnDenied = runnable;
            return this;
        }

        /**
         * Sets on permission(s) granted callback.
         *
         * @param runnable
         *        The callback
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        public RequestBuilder setOnGranted(final Runnable runnable) {
            mOnGranted = runnable;
            return this;
        }

        /**
         * Sets the "use Rationale" flag. Please refer to
         * {@link ActivityCompat#shouldShowRequestPermissionRationale} for more info.
         *
         * @param useRationale
         *        The value to use
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public RequestBuilder setUseRationale(final boolean useRationale) {
            mUseRationale = useRationale;
            return this;
        }

        /**
         * Sets the {@link PermissionsNamesTranslator} to use.
         *
         * @param permissionsNamesTranslator
         *        The PermissionsNamesTranslator
         *
         * @return  This {@code RequestBuilder} object to allow for chaining of calls to set methods
         */
        @SuppressWarnings("unused")
        public RequestBuilder setPermissionsNamesTranslator(final PermissionsNamesTranslator permissionsNamesTranslator) {
            mPermissionsNamesTranslator = permissionsNamesTranslator;
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
        @SuppressWarnings("unused")
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
        @SuppressWarnings("unused")
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

            CoreLogger.log(result ? Level.DEBUG: Level.INFO, "request result: " + result);
            return result;
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected CorePermissions createCorePermissions() {
            return new CorePermissions();
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected boolean requestHelper() {
            final Activity activity = mActivity.get();
            if (activity == null) {
                CoreLogger.logError("The activity is null");

                runOnDenied(mOnDenied);
                return false;
            }

            final CorePermissions corePermissions = createCorePermissions();

            corePermissions.mOnDenied                   = mOnDenied;
            corePermissions.mOnGranted                  = mOnGranted;

            corePermissions.mViewId                     = mViewId;

            if (mRequestCode == null)
                mRequestCode = Utils.getRequestCode(RequestCodes.PERMISSIONS_ALERT, activity);
            corePermissions.mRequestCode                = mRequestCode;

            corePermissions.mPermissionsNamesTranslator = mPermissionsNamesTranslator;

            CoreLogger.log("onDenied " + mOnDenied + ", onGranted " + mOnGranted);
            if (!checkData(activity, mPermissions)) {
                runOnDenied(mOnDenied);
                return false;
            }

            final String[] permissions = check(activity, mPermissions);
            if (permissions == null) {          // permissions are already granted
                runOnGranted(mOnGranted);
                return true;
            }

            if (!setupView(activity, corePermissions)) return false;

            Core.register(corePermissions);

            return !mUseRationale ? corePermissions.requestWrapper(activity, permissions):
                    corePermissions.requestHandler(activity, permissions);
        }

        /** @exclude */ @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected boolean setupView(@NonNull final Activity        activity,
                                  @NonNull final CorePermissions corePermissions) {
            final View view = ViewHelper.getView(activity, mViewId);
            if (!checkView(view)) return false;

            final View baseView = ViewHelper.getView(activity);
            if (!checkView(baseView)) return false;

            final int key = akha.yakhont.R.id.yakhont_permissions_view_ids;
            if (baseView.getTag(key) == null) baseView.setTag(key, Utils.<Integer>newSet());

            final Set<Integer> viewIds = getIds(activity);
            if (viewIds == null) {
                runOnDenied(mOnDenied);
                return false;
            }
            final boolean result = viewIds.add(mViewId);
            CoreLogger.log(result ? Level.DEBUG: Level.ERROR, "CorePermissions add view ID: " + result);

            //noinspection ConstantConditions
            view.setTag(akha.yakhont.R.id.yakhont_permissions_object, corePermissions);
            return true;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean checkView(final View view) {
            if (view == null) runOnDenied(mOnDenied);
            return view != null;
        }
    }
}
