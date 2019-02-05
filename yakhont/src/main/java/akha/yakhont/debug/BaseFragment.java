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

package akha.yakhont.debug;

import akha.yakhont.Core.Utils;
import akha.yakhont.Core.Utils.MeasuredViewAdjuster;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.LogDebug;

import android.app.Activity;
import android.content.Context;
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.Arrays;

/**
 * The <code>BaseFragment</code> class is intended for debug purposes.
 * Overridden methods most of the time just adds lifecycle logging.
 * Some additional debug Fragments can be found in the full version.    {@yakhont.preprocessor.remove.in.generated}
 *
 * @see LogDebug
 *
 * @author akha
 */
@SuppressWarnings("JavaDoc")
//YakhontPreprocessor:addToGenerated-SearchFragment-@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
//YakhontPreprocessor:addToGenerated-SearchFragment-@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
public class BaseFragment extends Fragment {        // don't modify this line: it's subject to change by the Yakhont preprocessor

    /**
     * Initialises a newly created {@code BaseFragment} object.
     */
    public BaseFragment() {
    }

    /**
     * Override to change the logging message.
     *
     * @return  The logging message (for debugging)
     */
    @SuppressWarnings("WeakerAccess")
    protected String getDebugMessage() {
        return "fragment " + getFragmentDescription(this);
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

    /** @exclude */ @SuppressWarnings("JavaDoc")
    @NonNull
    public static String getFragmentDescription(@NonNull final Object fragment) {
        return CoreLogger.getDescription(fragment);
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void enableFragmentManagerDebugLogging(final boolean enable) {
        FragmentManager.enableDebugLogging(enable);
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

    /**
     * Please refer to the base method description.
     */
    @CallSuper                                                                                          //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
    @Override                                                                                           //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
    public void onAttachFragment(Fragment childFragment) {                                              //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", childFragment " + childFragment, false); //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment

        super.onAttachFragment(childFragment);                                                          //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
    }                                                                                                   //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment

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
    @Nullable
    @CallSuper
    @Override
    public View onCreateView(@NonNull  LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
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

    //YakhontPreprocessor:addToGenerated-GuidedStepSupportFragment-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-GuidedStepSupportFragment-@CallSuper
    //YakhontPreprocessor:addToGenerated-GuidedStepSupportFragment-@Override
    //YakhontPreprocessor:addToGenerated-GuidedStepSupportFragment-public void onGuidedActionClicked(androidx.leanback.widget.GuidedAction action) {
    //YakhontPreprocessor:addToGenerated-GuidedStepSupportFragment-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", action " + action, false);
    //YakhontPreprocessor:addToGenerated-GuidedStepSupportFragment-    super.onGuidedActionClicked(action);
    //YakhontPreprocessor:addToGenerated-GuidedStepSupportFragment-}

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

    //YakhontPreprocessor:addToGenerated-ListFragment-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-ListFragment-@CallSuper
    //YakhontPreprocessor:addToGenerated-ListFragment-@Override
    //YakhontPreprocessor:addToGenerated-ListFragment-public void onListItemClick(android.widget.ListView l, View v, int position, long id) {
    //YakhontPreprocessor:addToGenerated-ListFragment-    CoreLogger.log(getDebugLevel(), getDebugMessage() +
    //YakhontPreprocessor:addToGenerated-ListFragment-            ", position " + position + ", id " + id, false);
    //YakhontPreprocessor:addToGenerated-ListFragment-    super.onListItemClick(l, v, position, id);
    //YakhontPreprocessor:addToGenerated-ListFragment-}

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

    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-@CallSuper
    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-@Override
    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-public boolean onPreferenceTreeClick(android.preference.PreferenceScreen preferenceScreen,
    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-                                     android.preference.Preference preference) {
    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-    return super.onPreferenceTreeClick(preferenceScreen, preference);
    //YakhontPreprocessor:addToGenerated-PreferenceFragmentCompat-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", requestCode " + requestCode +
                ", permissions "  + Arrays.deepToString(permissions) +
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
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
    @CallSuper                                                                                                                //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
    @Override                                                                                                                 //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
    public void setTargetFragment(Fragment fragment, int requestCode) {                                                       //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", fragment " + fragment + ", requestCode " + requestCode, true); //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment

        super.setTargetFragment(fragment, requestCode);                                                                       //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment
    }                                                                                                                         //YakhontPreprocessor:removeInGenerated-LeanbackListPreferenceDialogFragment,LeanbackPreferenceDialogFragment

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Allows the view layout adjusting keeping in mind the measured dimensions
     * (height, width) of the view.
     *
     * @param view
     *        The view to handle
     *
     * @see MeasuredViewAdjuster#adjustMeasuredView(View)
     */
    @SuppressWarnings({"EmptyMethod", "UnusedParameters", "WeakerAccess"})                      //YakhontPreprocessor:removeInGenerated
    protected void adjustMeasuredView(@NonNull final View view) {                               //YakhontPreprocessor:removeInGenerated
    }                                                                                           //YakhontPreprocessor:removeInGenerated

    /**
     * The callback helper for calling {@link #adjustMeasuredView(View)}. For example:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * public class YourFragment extends BaseFragment {
     *
     *     &#064;Override
     *     public View onCreateView(LayoutInflater inflater, ViewGroup container,
     *                              Bundle savedInstanceState) {
     *         super.onCreateView(inflater, container, savedInstanceState);
     *
     *         View view = ...;
     *
     *         onAdjustMeasuredView(view);
     *         return view;
     *     }
     *
     *     &#064;Override
     *     protected void adjustMeasuredView(View view) {
     *         int height = view.getMeasuredHeight();
     *         int width  = view.getMeasuredWidth();
     *
     *         // your code here
     *     }
     * }
     * </pre>
     *
     * @param view
     *        The view to handle
     */
    @SuppressWarnings("unused")                                                                 //YakhontPreprocessor:removeInGenerated
    protected void onAdjustMeasuredView(@NonNull final View view) {                             //YakhontPreprocessor:removeInGenerated
        Utils.onAdjustMeasuredView(mViewAdjusterImpl, view);                                    //YakhontPreprocessor:removeInGenerated
    }                                                                                           //YakhontPreprocessor:removeInGenerated

    private class MeasuredViewAdjusterImpl implements MeasuredViewAdjuster {                    //YakhontPreprocessor:removeInGenerated
        @Override                                                                               //YakhontPreprocessor:removeInGenerated
        public void adjustMeasuredView(final View view) {                                       //YakhontPreprocessor:removeInGenerated
            BaseFragment.this.adjustMeasuredView(view);                                         //YakhontPreprocessor:removeInGenerated
        }                                                                                       //YakhontPreprocessor:removeInGenerated
    }                                                                                           //YakhontPreprocessor:removeInGenerated

    private final MeasuredViewAdjusterImpl mViewAdjusterImpl = new MeasuredViewAdjusterImpl();  //YakhontPreprocessor:removeInGenerated
}
