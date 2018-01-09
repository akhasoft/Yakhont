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

package akha.yakhont.fragment.dialog;

import akha.yakhont.Core;
import akha.yakhont.Core.BaseDialog;
import akha.yakhont.Core.RequestCodes;
import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.SupportHelper;
import akha.yakhont.debug.BaseDialogFragment;
import akha.yakhont.location.GoogleLocationClient;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import java.lang.ref.WeakReference;

/**
 * The <code>CommonDialogFragment</code> is the base class for other dialogs.
 *
 * @see AlertDialogFragment
 * @see ProgressDialogFragment
 *
 * @author akha
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
public abstract class CommonDialogFragment extends BaseDialogFragment implements BaseDialog {

    private   static final String       TAG                 = Utils.getTag(CommonDialogFragment.class);

    private   static final String       ARG_DIALOG_ERROR    = TAG + ".dialog_error";

    /** The name of the entry in the bundle from which to retrieve the parent fragment reference. */
    @SuppressWarnings("WeakerAccess")
    public    static final String       ARG_PARENT          = TAG + ".parent";

    /** The name of the entry in the bundle from which to retrieve the text of the dialog message. */
    @SuppressWarnings("WeakerAccess")
    protected static final String       ARG_TEXT            = TAG + ".text";
    /** The name of the entry in the bundle from which to retrieve the dialog's theme. */
    @SuppressWarnings("WeakerAccess")
    protected static final String       ARG_THEME           = TAG + ".theme";

    private String                      mTag;
    private WeakReference<Activity>     mActivity           = new WeakReference<>(null);

    /**
     * Initialises a newly created {@code CommonDialogFragment} object.
     */
    @SuppressWarnings("WeakerAccess")
    public CommonDialogFragment() {
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    protected abstract String getDefaultTag();

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected String getTagToUse() {
        return mTag != null ? mTag: getDefaultTag();
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "unused"})
    public void assignTag(@NonNull final String tag) {
        mTag = tag;
    }

    private void setDialogActivity(final Activity activity) {
        mActivity = new WeakReference<>(activity);
        if (activity == null)
            CoreLogger.logError("activity == null");
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected Activity getDialogActivity() {
        final Activity activity = mActivity.get();
        if (activity == null)
            CoreLogger.logWarning("activity == null");
        return activity;
    }

    /** @exclude */
    @SuppressWarnings({"JavaDoc", "WeakerAccess"})
    protected FragmentManager getDialogFragmentManager() {
        final Activity activity = getDialogActivity();
        if (activity == null) return null;

        final FragmentManager fragmentManager = SupportHelper.getFragmentManager(activity, this);
        if (fragmentManager == null)
            CoreLogger.logError("fragmentManager == null");

        return fragmentManager;
    }

    /**
     * Sets the dialog's theme.
     *
     * @param themeResId
     *        The resource ID of the theme
     *
     * @return  This {@code CommonDialogFragment} object
     */
    @SuppressWarnings("unused")
    public CommonDialogFragment setTheme(final int themeResId) {
        final Bundle arguments = getArguments();
        arguments.putInt(ARG_THEME, themeResId);
        setArguments(arguments);
        return this;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean start(final Activity context, final String text, final Intent data) {
        setDialogActivity(context);

        if (text != null) {
            final Bundle arguments = getArguments();
            arguments.putString(ARG_TEXT, text);
            setArguments(arguments);
        }

        final boolean result = startDialog(data);

        CoreLogger.log(result ? CoreLogger.Level.DEBUG : CoreLogger.Level.ERROR, "start = " + result);
        return result;
    }

    /**
     * Starts dialog.
     *
     * @return  {@code true} if dialog was started successfully, {@code false} otherwise
     */
    protected abstract boolean startDialog(final Intent data);

    /**
     * Please refer to the base method description.
     */
    @Override
    public boolean stop() {
        final boolean result = stopDialog();

        CoreLogger.log(result ? CoreLogger.Level.DEBUG: CoreLogger.Level.ERROR, "stop = " + result);
        return result;
    }

    /**
     * Stops dialog.
     *
     * @return  {@code true} if dialog was stopped successfully, {@code false} otherwise
     */
    protected abstract boolean stopDialog();

    /**
     * Please refer to the base method description.
     */
    @Override
    public void dismiss() {
        try {
            super.dismiss();
        }
        catch (Exception e) {
            CoreLogger.log("dismiss failed", e);
        }
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static BaseDialog getDaggerAlert(@StringRes final int resId, final int requestCode, final Boolean yesNo) {
        final AlertDialogFragment alert = AlertDialogFragment.newInstance(resId, requestCode, yesNo);

        // see comments in AlertDialogFragment
        if (requestCode == Utils.getRequestCode(RequestCodes.PERMISSIONS_DENIED_ALERT))
            alert.mSupportFragmentManagerHack = true;

        return alert;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static BaseDialog getDaggerProgress() {
        return ProgressDialogFragment.ProgressLoaderDialogFragment.newInstance();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void showLocationErrorDialog(final Activity activity, final int errorCode) {
        final LocationErrorDialogFragment errorDialogFragment = new LocationErrorDialogFragment();

        final Bundle args = new Bundle();
        args.putInt(ARG_DIALOG_ERROR, errorCode);
        errorDialogFragment.setArguments(args);

        if (activity == null) {
            CoreLogger.logError("no ErrorDialogFragment: activity == null");
            return;
        }

        final FragmentManager fragmentManager = SupportHelper.getFragmentManager(activity, new Fragment());
        if (fragmentManager == null) {
            CoreLogger.logError("no ErrorDialogFragment: fragmentManager == null");
            return;
        }

        errorDialogFragment.show(fragmentManager, LocationErrorDialogFragment.TAG);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class LocationErrorDialogFragment extends DialogFragment {

        public  static final String     TAG                 = LocationErrorDialogFragment.class.getName();

        private static final int        REQUEST_CODE        = Utils.getRequestCode(Core.RequestCodes.LOCATION_CLIENT);

        private              Activity   mActivity;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int errorCode = getArguments().getInt(ARG_DIALOG_ERROR);
            CoreLogger.log("error code: " + errorCode);

            mActivity = Utils.getCurrentActivity();

            return GoogleLocationClient.getErrorDialog(mActivity, errorCode, REQUEST_CODE);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            Utils.onActivityResult(mActivity, REQUEST_CODE, Activity.RESULT_CANCELED /* ignored */, null);
        }
    }
}
