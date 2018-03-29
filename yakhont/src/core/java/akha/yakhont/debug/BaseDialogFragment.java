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

package akha.yakhont.debug;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;

/**
 * The <code>BaseDialogFragment</code> class is intended for debug purposes. Overridden methods most of the time just adds lifecycle logging.
 * Some additional debug Fragments can be found in the full version.        {@yakhont.preprocessor.remove.in.generated}
 *
 * @author akha
 */
@SuppressWarnings("JavaDoc")
@TargetApi(Build.VERSION_CODES.HONEYCOMB)                       //YakhontPreprocessor:removeInFlavor
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)               //YakhontPreprocessor:removeInFlavor
public class BaseDialogFragment extends DialogFragment {        // don't modify this line: it's subject to change by the Yakhont preprocessor

    /**
     * Initialises a newly created {@code BaseDialogFragment} object.
     */
    public BaseDialogFragment() {
    }

    /**
     * Override to change the logging message.
     *
     * @return  The logging message (for debugging)
     */
    @SuppressWarnings("JavaDoc")
    protected String getDebugMessage() {
        return "dialog fragment " + BaseFragment.getFragmentName(this);
    }

    /**
     * Override to change the logging level.
     * <br>The default value is {@link Level#WARNING WARNING}.
     *
     * @return  The logging priority level (for debugging)
     */
    @SuppressWarnings("SameReturnValue")
    protected Level getDebugLevel() {
        return Level.WARNING;
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onActivityCreated(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        CoreLogger.log(getDebugMessage() + ", requestCode " + requestCode +
                ", resultCode " + resultCode + " " + Utils.getActivityResultString(resultCode));
                
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    @SuppressWarnings("deprecation")
    public void onAttach(Activity activity) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onAttach(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onAttach(Context context) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onAttach(context);
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-protected void onBindDialogView(View view) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    super.onBindDialogView(view);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onCancel(DialogInterface dialog) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onCancel(dialog);
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-public void onClick(DialogInterface dialog, int which) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", which " + which, false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    super.onClick(dialog, which);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", newConfig " + newConfig, false);

        super.onConfigurationChanged(newConfig);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onCreate(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onCreate(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        return super.onCreateDialog(savedInstanceState);
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-protected View onCreateDialogView(Context context) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    return super.onCreateDialogView(context);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onDestroy() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onDestroy();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onDestroyView() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onDestroyView();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onDetach() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onDetach();
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-public void onDialogClosed(boolean positiveResult) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", positiveResult " + positiveResult, false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-    super.onDialogClosed(positiveResult);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragment,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragment-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onDismiss(DialogInterface dialog) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onDismiss(dialog);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper                                                                                  //YakhontPreprocessor:removeInFlavor
    @Override                                                                                   //YakhontPreprocessor:removeInFlavor
    @SuppressWarnings("deprecation")                                                            //YakhontPreprocessor:removeInFlavor
    public void onInflate(AttributeSet attrs, Bundle savedInstanceState) {                      //YakhontPreprocessor:removeInFlavor
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", attrs " + attrs +                //YakhontPreprocessor:removeInFlavor
                ", savedInstanceState " + savedInstanceState, false);                           //YakhontPreprocessor:removeInFlavor

        super.onInflate(attrs, savedInstanceState);                                             //YakhontPreprocessor:removeInFlavor
    }                                                                                           //YakhontPreprocessor:removeInFlavor

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    @SuppressWarnings("deprecation")
    public void onInflate(Activity activity, AttributeSet attrs, Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", attrs " + attrs + ", savedInstanceState " + savedInstanceState, false);

        super.onInflate(activity, attrs, savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", attrs " + attrs + ", savedInstanceState " + savedInstanceState, false);

        super.onInflate(context, attrs, savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onLowMemory() {
        CoreLogger.log(Utils.getOnLowMemoryLevel(), getDebugMessage(), false);

        super.onLowMemory();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onPause() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onPause();
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,ListPreferenceDialogFragment,MultiSelectListPreferenceDialogFragment-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,ListPreferenceDialogFragment,MultiSelectListPreferenceDialogFragment-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,ListPreferenceDialogFragment,MultiSelectListPreferenceDialogFragment-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,ListPreferenceDialogFragment,MultiSelectListPreferenceDialogFragment-protected void onPrepareDialogBuilder(android.app.AlertDialog.Builder builder) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,ListPreferenceDialogFragment,MultiSelectListPreferenceDialogFragment-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,ListPreferenceDialogFragment,MultiSelectListPreferenceDialogFragment-    super.onPrepareDialogBuilder(builder);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragment,ListPreferenceDialogFragment,MultiSelectListPreferenceDialogFragment-}

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-protected void onPrepareDialogBuilder(android.support.v7.app.AlertDialog.Builder builder) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-    super.onPrepareDialogBuilder(builder);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", requestCode " + requestCode +
                ", permissions " + Arrays.deepToString(permissions) +
                ", grantResults " + Arrays.toString(grantResults), false);

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onResume() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onResume();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onSaveInstanceState(Bundle outState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", outState " + outState, false);

        super.onSaveInstanceState(outState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onStart() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
        
        super.onStart();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onStop() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onStop();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper                                                                                                                                  //YakhontPreprocessor:removeInFlavor
    @Override                                                                                                                                   //YakhontPreprocessor:removeInFlavor
    public void onTrimMemory(int level) {                                                                                                       //YakhontPreprocessor:removeInFlavor
        CoreLogger.log(Utils.getOnTrimMemoryLevel(level), getDebugMessage() + ", level " + Utils.getOnTrimMemoryLevelString(level), false);     //YakhontPreprocessor:removeInFlavor

        super.onTrimMemory(level);                                                                                                              //YakhontPreprocessor:removeInFlavor
    }                                                                                                                                           //YakhontPreprocessor:removeInFlavor

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onViewCreated(view, savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onViewStateRestored(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void setRetainInstance(boolean retain) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", retain " + retain, true);

        super.setRetainInstance(retain);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void setTargetFragment(Fragment fragment, int requestCode) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", fragment " + fragment + ", requestCode " + requestCode, true);

        super.setTargetFragment(fragment, requestCode);
    }
}
