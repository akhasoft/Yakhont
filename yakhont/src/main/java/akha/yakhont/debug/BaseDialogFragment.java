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

package akha.yakhont.debug;

import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.RetainDialogFragment;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.LogDebug;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Arrays;

/**
 * The <code>BaseDialogFragment</code> class is intended for debug purposes.
 * Overridden methods most of the time just adds lifecycle logging.
 * Some additional debug Fragments can be found in the full version.        {@yakhont.preprocessor.remove.in.generated}
 *
 * @see LogDebug
 *
 * @author akha
 */
@SuppressWarnings({"JavaDoc", "WeakerAccess"})
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
    @SuppressWarnings("WeakerAccess")
    protected String getDebugMessage() {
        return "dialog fragment " + BaseFragment.getFragmentDescription(this);
    }

    /**
     * Override to change the logging level.
     * <br>The default value is {@link CoreLogger#getDefaultLevel()}.
     *
     * @return  The logging priority level (for debugging)
     */
    @SuppressWarnings({"SameReturnValue", "WeakerAccess"})
    protected Level getDebugLevel() {
        return CoreLogger.getDefaultLevel();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onActivityCreated(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
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
    public void onAttach(@NonNull final Activity activity) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onAttach(activity);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onAttach(@NonNull final Context context) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onAttach(context);
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-protected void onBindDialogView(final View view) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    super.onBindDialogView(view);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onCancel(dialog);
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-public void onClick(final DialogInterface dialog, final int which) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", which " + which, false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    super.onClick(dialog, which);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", newConfig " + newConfig, false);

        super.onConfigurationChanged(newConfig);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onCreate(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        return super.onCreateDialog(savedInstanceState);
    }

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-protected View onCreateDialogView(final Context context) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    return super.onCreateDialogView(context);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-}

    /**
     * Please refer to the base method description.
     */
    @Nullable
    @CallSuper
    @Override
    public View onCreateView(@NonNull  final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
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

        RetainDialogFragment.onDestroyView(this);

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

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-public void onDialogClosed(final boolean positiveResult) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", positiveResult " + positiveResult, false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-    super.onDialogClosed(positiveResult);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat,MultiSelectListPreferenceDialogFragmentCompat-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onDismiss(dialog);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    @SuppressWarnings("deprecation")
    public void onInflate(@NonNull final Activity activity, @NonNull final AttributeSet attrs, final Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", attrs " + attrs + ", savedInstanceState " + savedInstanceState, false);

        super.onInflate(activity, attrs, savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onInflate(@NonNull final Context context, @NonNull final AttributeSet attrs, final Bundle savedInstanceState) {
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

    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-@CallSuper
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-@Override
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-protected void onPrepareDialogBuilder(final androidx.appcompat.app.AlertDialog.Builder builder) {
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-    super.onPrepareDialogBuilder(builder);
    //YakhontPreprocessor:addToGenerated-EditTextPreferenceDialogFragmentCompat,ListPreferenceDialogFragmentCompat-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", requestCode " + requestCode +
                ", permissions "  + Arrays.toString(permissions) +
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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
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
    @CallSuper
    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onViewCreated(view, savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onViewStateRestored(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void setRetainInstance(final boolean retain) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", retain " + retain, true);

        super.setRetainInstance(retain);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void setTargetFragment(final Fragment fragment, final int requestCode) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", fragment " + fragment + ", requestCode " + requestCode, true);

        super.setTargetFragment(fragment, requestCode);
    }
}
