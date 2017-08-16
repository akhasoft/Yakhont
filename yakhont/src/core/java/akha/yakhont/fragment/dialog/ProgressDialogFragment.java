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
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.loader.BaseLoader;
import akha.yakhont.loader.wrapper.BaseResponseLoaderWrapper.CoreLoad;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import javax.inject.Provider;

/**
 * The <code>ProgressDialogFragment</code> class is intended to display a progress indicator to the user.
 *
 * @author akha
 */
@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
public class ProgressDialogFragment extends CommonDialogFragment {

    private static final String         TAG                 = Utils.getTag(ProgressDialogFragment.class);

    @StringRes private static final int INFO_STRING_RES_ID  = akha.yakhont.R.string.yakhont_loader_progress;
    @StringRes private static final int  DEF_STRING_RES_ID  = akha.yakhont.R.string.yakhont_loader_progress_def_info;

    private static final int            REQUEST_CODE_ALERT  = Utils.getRequestCode(RequestCodes.PROGRESS_ALERT);

    private Provider<BaseDialog>        mAlertProvider;
    private BaseDialog                  mAlert;

    @SuppressWarnings("unused")
    private boolean                     mConfirmation       = true;
    private Boolean                     mCancelled;

    /**
     * Initialises a newly created {@code ProgressDialogFragment} object.
     */
    public ProgressDialogFragment() {
    }

    /**
     * Creates new instance of {@code ProgressDialogFragment} object.
     *
     * @param text
     *        The dialog message's text
     *
     * @return  The newly created {@code ProgressDialogFragment} object
     */
    @NonNull
    public static ProgressDialogFragment newInstance(final String text) {
        return newInstance(text, new ProgressDialogFragment());
    }

    /**
     * Initialises the new instance of {@code ProgressDialogFragment} object.
     *
     * @param text
     *        The dialog message's text
     *
     * @param fragment
     *        The new instance of {@code ProgressDialogFragment} object
     *
     * @return  The {@code ProgressDialogFragment} object
     */
    @NonNull
    protected static ProgressDialogFragment newInstance(final String text, @NonNull final ProgressDialogFragment fragment) {
        final Bundle arguments = new Bundle();

        if (text != null) arguments.putString(ARG_TEXT, text);

        fragment.setArguments(arguments);
        return fragment;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected String getDefaultTag() {
        return TAG;
    }

    /**
     * Sets the "confirmation dialog" flag. If set to {@code true} the confirmation will be displayed
     * when user will try to cancel the progress dialog.
     * <br>The default value is {@code true}.
     *
     * @param confirmation
     *        The value to set
     *
     * @return  This {@code ProgressDialogFragment} object
     */
    public ProgressDialogFragment setConfirmation(final boolean confirmation) {
        mConfirmation = confirmation;
        return this;
    }

    /**
     * Returns the message to display.
     *
     * @param context
     *        The context
     *
     * @return  The message
     */
    protected String getMessage(@NonNull final Context context) {
        final Bundle arguments = getArguments();
        return context.getString(INFO_STRING_RES_ID, arguments.containsKey(ARG_TEXT)
                ? arguments.getString(ARG_TEXT)
                : context.getString(DEF_STRING_RES_ID));
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        final Bundle arguments  = getArguments();
        final Activity activity = getDialogActivity();

        final ProgressDialog progress = arguments.containsKey(ARG_THEME)
                ? new ProgressDialog(activity, arguments.getInt(ARG_THEME))
                : new ProgressDialog(activity);

        progress.setMessage(getMessage(activity));

        progress.setCancelable(true);
        progress.setCanceledOnTouchOutside(false);

        return progress;
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mAlertProvider == null) mAlertProvider = Core.getDagger().getAlertProgress();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);      // just for logging

        CoreLogger.log("mConfirmation " + mConfirmation);

        if (!mConfirmation) {
            cancel();
            return;
        }

        mAlert = mAlertProvider.get();

        if (mAlert instanceof Fragment) {
            final Fragment alert = (Fragment) mAlert;
            alert.setTargetFragment(this, REQUEST_CODE_ALERT);

            final FragmentManager fragmentManager = getDialogFragmentManager();
            if (fragmentManager != null) {
                Bundle bundle = alert.getArguments();
                if (bundle == null) bundle = new Bundle();

                fragmentManager.putFragment(bundle, CommonDialogFragment.ARG_PARENT, this);
                alert.setArguments(bundle);
            }
        }

        if (mAlert.start(getDialogActivity(), null, null)) return;

        CoreLogger.logError("can not start alert dialog");
        cancel();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected boolean startDialog(final Intent data) {
        final FragmentManager fragmentManager = getDialogFragmentManager();
        if (fragmentManager == null) return false;

        final String tag = getTagToUse();

        if (fragmentManager.findFragmentByTag(tag) != null) return true;

        show(fragmentManager, tag);
        return true;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected boolean stopDialog() {
        final FragmentManager fragmentManager = getDialogFragmentManager();
        if (fragmentManager == null) return false;

        if (mAlert != null) {
            mCancelled = false;

            final boolean result = mAlert.stop();

            mAlert = null;
            return result;
        }

        dismiss();

        // dismiss() does the same but it seems sometimes with wrong FragmentManager
        try {
            fragmentManager.beginTransaction().remove(this).commit();
        }
        catch (Exception e) {
            CoreLogger.log(Level.ERROR, "remove progress", false);
        }

        return true;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);  // just for logging

        mAlert = null;

        if (requestCode != REQUEST_CODE_ALERT) {
            CoreLogger.logError("unknown request code " + requestCode);
            return;
        }

        switch (resultCode) {
            case Activity.RESULT_OK:
                mCancelled = true;
                break;
            case Activity.RESULT_CANCELED:
            case Activity.RESULT_FIRST_USER:
                break;
            default:
                CoreLogger.logWarning("unknown result code " + resultCode);
                break;
        }
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onStart() {
        super.onStart();

        if (mCancelled == null) return;

        if (mCancelled)
            cancel();
        else
            stopDialog();
    }

    /**
     * The callback which is called when user cancels the progress dialog.
     */
    protected void cancel() {
    }

    /**
     * The <code>ProgressLoaderDialogFragment</code> class is intended to display a data loading progress indicator to the user.
     */
    public static class ProgressLoaderDialogFragment extends ProgressDialogFragment {

        /**
         * Initialises a newly created {@code ProgressLoaderDialogFragment} object.
         */
        public ProgressLoaderDialogFragment() {
        }

        /**
         * Creates new instance of {@code ProgressLoaderDialogFragment} object.
         *
         * @param text
         *        The dialog message's text
         *
         * @return  The newly created {@code ProgressLoaderDialogFragment} object
         */
        @NonNull
        public static ProgressLoaderDialogFragment newInstance(final String text) {
            return (ProgressLoaderDialogFragment) ProgressDialogFragment.newInstance(text, new ProgressLoaderDialogFragment());
        }

        /**
         * Cancels all currently running {@link akha.yakhont.loader.wrapper.BaseLoaderWrapper loaders}.
         */
        @Override
        protected void cancel() {
            CoreLogger.logWarning("cancel");

            final CoreLoad coreLoad = BaseLoader.getCoreLoad(getDialogActivity());
            if (coreLoad != null) {
                Utils.runInBackground(new Runnable() {
                    @Override
                    public void run() {
                        coreLoad.cancelLoaders();
                    }
                });
            }
            else
                CoreLogger.logError("CoreLoad not found");
        }
    }
}
