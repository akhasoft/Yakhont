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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;

import javax.inject.Provider;

/**
 * The <code>ProgressDialogFragment</code> class is intended to display a progress indicator to the user.
 *
 * @author akha
 */
@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public class ProgressDialogFragment extends CommonDialogFragment {

    private static final String         TAG                 = Utils.getTag(ProgressDialogFragment.class);

    /** The name of the entry in the bundle from which to retrieve the maximum allowed progress value (if any). */
    @SuppressWarnings("WeakerAccess")
    protected static final String       ARG_MAX             = TAG + ".max";

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
     * @return  The newly created {@code ProgressDialogFragment} object
     */
    @NonNull
    public static ProgressDialogFragment newInstance() {
        return newInstance((String) null);
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
        return newInstance(text, 0);
    }

    /**
     * Creates new instance of {@code ProgressDialogFragment} object.
     *
     * @param text
     *        The dialog message's text
     *
     * @param maxProgress
     *        The maximum allowed progress value (if any)
     *
     * @return  The newly created {@code ProgressDialogFragment} object
     */
    @NonNull
    public static ProgressDialogFragment newInstance(final String text, final int maxProgress) {
        return newInstance(text, maxProgress, new ProgressDialogFragment());
    }

    /**
     * Initialises the new instance of {@code ProgressDialogFragment} object.
     *
     * @param fragment
     *        The new instance of {@code ProgressDialogFragment} object
     *
     * @return  The {@code ProgressDialogFragment} object
     */
    @NonNull
    protected static ProgressDialogFragment newInstance(@NonNull final ProgressDialogFragment fragment) {
        return newInstance(null, 0, fragment);
    }

    /**
     * Initialises the new instance of {@code ProgressDialogFragment} object.
     *
     * @param text
     *        The dialog message's text
     *
     * @param maxProgress
     *        The maximum allowed progress value (if any)
     *
     * @param fragment
     *        The new instance of {@code ProgressDialogFragment} object
     *
     * @return  The {@code ProgressDialogFragment} object
     */
    @NonNull
    protected static ProgressDialogFragment newInstance(final String text, final int maxProgress,
                                                        @NonNull final ProgressDialogFragment fragment) {
        final Bundle arguments = new Bundle();

        if (text        != null) arguments.putString(ARG_TEXT, text);
        if (maxProgress >     0) arguments.putInt   (ARG_MAX , maxProgress);

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
    @SuppressWarnings("deprecation")    // the UI is fully customizable via Dagger 2
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        final Bundle arguments  = getArguments();
        final Activity activity = getDialogActivity();

        final android.app.ProgressDialog progress = arguments.containsKey(ARG_THEME)
                ? new android.app.ProgressDialog(activity, arguments.getInt(ARG_THEME))
                : new android.app.ProgressDialog(activity);

        progress.setMessage(getMessage(activity));

        progress.setCancelable(true);
        progress.setCanceledOnTouchOutside(false);

        final boolean isIndeterminate = !arguments.containsKey(ARG_MAX);
        progress.setIndeterminate(isIndeterminate);
        if (!isIndeterminate) progress.setMax(arguments.getInt(ARG_MAX));

        return progress;
    }

    /**
     * Sets the current progress (if not in indeterminate mode).
     *
     * @param value the current progress, a value between 0 and {@link android.app.ProgressDialog#getMax()}
     *
     * @return  This {@code ProgressDialogFragment} object
     */
    @SuppressWarnings("deprecation")    // the UI is fully customizable via Dagger 2
    public ProgressDialogFragment setProgress(@IntRange(from = 0) final int value) {
        final Dialog dialog = getDialog();
        if (dialog == null)
            CoreLogger.logError("dialog == null");
        else if (!(dialog instanceof android.app.ProgressDialog))
            CoreLogger.logError("dialog should be instance of android.app.ProgressDialog");
        else {
            final android.app.ProgressDialog progress = (android.app.ProgressDialog) dialog;
            if (progress.isIndeterminate())
                CoreLogger.logError("the ProgressDialog is in indeterminate mode");
            else
                progress.setProgress(value);
        }
        return this;
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
         * @return  The newly created {@code ProgressLoaderDialogFragment} object
         */
        @NonNull
        public static ProgressLoaderDialogFragment newInstance() {
            return (ProgressLoaderDialogFragment) ProgressDialogFragment.newInstance(
                    new ProgressLoaderDialogFragment());
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
            return (ProgressLoaderDialogFragment) ProgressDialogFragment.newInstance(
                    text, 0, new ProgressLoaderDialogFragment());
        }

        /**
         * Cancels all currently running {@link akha.yakhont.loader.wrapper.BaseLoaderWrapper loaders}.
         */
        @Override
        protected void cancel() {
            CoreLogger.logWarning("cancel");

            final CoreLoad coreLoad = BaseLoader.getCoreLoad(getDialogActivity());
            if (coreLoad != null) {
                //noinspection Anonymous2MethodRef,Convert2Lambda
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
