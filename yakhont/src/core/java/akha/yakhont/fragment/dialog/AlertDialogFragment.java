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

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreReflection;
import akha.yakhont.SupportHelper;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;

/**
 * The <code>AlertDialogFragment</code> class is intended to display standard alerts to the user.
 *
 * @author akha
 */
@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public class AlertDialogFragment extends CommonDialogFragment implements DialogInterface.OnClickListener {

    private static final String             TAG                 = Utils.getTag(AlertDialogFragment.class);

    private static final String             ARG_REQUEST_CODE    = TAG + ".request_code";
    private static final String             ARG_YES_NO          = TAG + ".yes_no";
    private static final String             ARG_TEXT_ID         = TAG + ".text_id";

    @StringRes private static final int     RES_ID_OK           = akha.yakhont.R.string.yakhont_alert_ok;
    @StringRes private static final int     RES_ID_CANCEL       = akha.yakhont.R.string.yakhont_alert_cancel;

    @StringRes private static final int     RES_ID_YES          = akha.yakhont.R.string.yakhont_alert_yes;
    @StringRes private static final int     RES_ID_NO           = akha.yakhont.R.string.yakhont_alert_no;

    private int                             mTransactionId;
    private Intent                          mIntent;

    // it's a hack so method is not needed - public field is enough
    /** @exclude */ @SuppressWarnings("JavaDoc")
    public boolean                          mSupportFragmentManagerHack;

    /**
     * Initialises a newly created {@code AlertDialogFragment} object.
     */
    public AlertDialogFragment() {
    }

    /**
     * Creates new instance of {@code AlertDialogFragment} object.
     *
     * @param resId
     *        The resource ID of the dialog message's text
     *
     * @param requestCode
     *        The integer request code to pass to the {@link Activity#onActivityResult Activity.onActivityResult()}
     *
     * @param yesNo
     *        {@code true} for YES / NO buttons, {@code false} for OK / CANCEL ones
     *
     * @return  The newly created {@code AlertDialogFragment} object
     */
    @NonNull
    public static AlertDialogFragment newInstance(@StringRes final int resId, final Integer requestCode, final Boolean yesNo) {
        return newInstance(null, resId, requestCode, yesNo, new AlertDialogFragment());
    }

    /**
     * Creates new instance of {@code AlertDialogFragment} object.
     *
     * @param text
     *        The dialog message's text
     *
     * @param requestCode
     *        The integer request code to pass to the {@link Activity#onActivityResult Activity.onActivityResult()}
     *
     * @param yesNo
     *        {@code true} for YES / NO buttons, {@code false} for OK / CANCEL ones
     *
     * @return  The newly created {@code AlertDialogFragment} object
     */
    @NonNull
    public static AlertDialogFragment newInstance(final String text, final Integer requestCode, final Boolean yesNo) {
        return newInstance(text, null, requestCode, yesNo, new AlertDialogFragment());
    }

    /**
     * Initialises the new instance of {@code AlertDialogFragment} object.
     *
     * @param text
     *        The dialog message's text (or null)
     *
     * @param resId
     *        The resource ID of the dialog message's text (or null)
     *
     * @param requestCode
     *        The integer request code to pass to the {@link Activity#onActivityResult Activity.onActivityResult()}
     *
     * @param yesNo
     *        {@code true} for YES / NO buttons, {@code false} for OK / CANCEL ones
     *
     * @param fragment
     *        The new instance of {@code AlertDialogFragment} object
     *
     * @return  The {@code AlertDialogFragment} object
     */
    @NonNull
    protected static AlertDialogFragment newInstance(final String text, final Integer resId,
                                                     final Integer requestCode, final Boolean yesNo,
                                                     @NonNull final AlertDialogFragment fragment) {
        final Bundle arguments = new Bundle();

        if (text        != null) arguments.putString (ARG_TEXT,         text);
        if (resId       != null) arguments.putInt    (ARG_TEXT_ID,      resId);
        if (requestCode != null) arguments.putInt    (ARG_REQUEST_CODE, requestCode);
        if (yesNo       != null) arguments.putBoolean(ARG_YES_NO,       yesNo);

        fragment.setArguments(arguments);
        return fragment;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @Override
    protected String getDefaultTag() {
        return TAG;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        final Dialog dialog = getDefaultBuilder().create();

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        return dialog;
    }

    @SuppressLint("ObsoleteSdkInt")
    private AlertDialog.Builder getBuilder(@NonNull final Activity activity, @NonNull final Bundle arguments) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            return new AlertDialog.Builder(activity, arguments.getInt(ARG_THEME));

        CoreLogger.logWarning("AlertDialog.Builder(Context context, int themeResId) is not supported");
        return new AlertDialog.Builder(activity);
    }

    /**
     * Returns the default alert dialog builder.
     *
     * @return  The {@code AlertDialog.Builder}
     */
    @NonNull
    protected AlertDialog.Builder getDefaultBuilder() {
        final Bundle arguments = getArguments();

        final Activity activity = getDialogActivity();
        final AlertDialog.Builder builder = arguments.containsKey(ARG_THEME)
                ? getBuilder(activity, arguments)
                : new AlertDialog.Builder(activity);

        final boolean yesNo = arguments.getBoolean(ARG_YES_NO);

        return builder
                .setMessage(arguments.containsKey(ARG_TEXT) ? arguments.getString(ARG_TEXT):
                        activity.getString(arguments.getInt(ARG_TEXT_ID)))
                .setPositiveButton(yesNo ? RES_ID_YES:  RES_ID_OK,      this)
                .setNegativeButton(yesNo ? RES_ID_NO :  RES_ID_CANCEL,  this);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);      // just for logging

        callback(Activity.RESULT_CANCELED);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        CoreLogger.log("which " + which + " " + Utils.getDialogInterfaceString(which));

        callback(which == DialogInterface.BUTTON_POSITIVE ? Activity.RESULT_OK: Activity.RESULT_FIRST_USER);
        stop();
    }

    /**
     * If target fragment is not null, calls {@link Fragment#onActivityResult Fragment.onActivityResult()};
     * otherwise, calls {@link Activity#onActivityResult Activity.onActivityResult()}.
     *
     * @param result
     *        The integer result code
     */
    protected void callback(final int result) {
        CoreLogger.log("result == " + result + " " + Utils.getActivityResultString(result) +
                ", intent == " + mIntent);

        final Fragment target = getTargetFragment();
        CoreLogger.log("target == " + target);

        if (target != null) {
            target.onActivityResult(getTargetRequestCode(), result, mIntent);
            return;
        }

        final int requestCode = getArguments().getInt(ARG_REQUEST_CODE);

        final Activity activity = getActivity();
        if (activity == null) {
            CoreLogger.logError("activity == null");
            return;
        }

        Utils.onActivityResult(activity, requestCode, result, mIntent);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected boolean startDialog(final Intent data) {
        mIntent = data;

        final FragmentManager fragmentManager = getDialogFragmentManager();
        if (fragmentManager == null) return false;

        // well, it's a hack... maybe some day I will find another way to overcome SupportFragmentManager
        CoreLogger.log("mSupportFragmentManagerHack == " + mSupportFragmentManagerHack);
        //noinspection ConstantConditions
        if (mSupportFragmentManagerHack && SupportHelper.isSupportMode(getActivity()))
            CoreReflection.invokeSafe(fragmentManager, "noteStateNotSaved");

        final Fragment parent = fragmentManager.getFragment(getArguments(), CommonDialogFragment.ARG_PARENT);
        CoreLogger.log("parent == " + parent);

        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (parent instanceof DialogFragment) transaction.remove(parent);
        transaction.addToBackStack(null);

        mTransactionId = show(transaction, getTagToUse());
        return true;
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    protected boolean stopDialog() {

        // dismiss() does the same but sometimes with wrong FragmentManager

        final Dialog dialog = getDialog();
        if (dialog == null)
            CoreLogger.logError("dialog == null");
        else
            dialog.dismiss();

        final FragmentManager fragmentManager = getDialogFragmentManager();
        if (fragmentManager == null) return false;

        fragmentManager.popBackStack(mTransactionId, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        return true;
    }
}
