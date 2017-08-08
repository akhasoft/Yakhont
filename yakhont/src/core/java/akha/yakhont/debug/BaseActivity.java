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

package akha.yakhont.debug;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.debug.BaseApplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.View;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The <code>BaseActivity</code> class is intended for debug purposes. Overridden methods most of the time just adds lifecycle logging.
 * Some additional debug Activities can be found in the full version.   {@yakhont.preprocessor.remove.in.generated}
 *
 * @author akha
 */
@SuppressLint("Registered")
@SuppressWarnings({"unused", "JavaDoc"})
public class BaseActivity extends Activity {        // don't modify this line: it's subject to change by the Yakhont preprocessor

    /**
     * Initialises a newly created {@code BaseActivity} object.
     */
    public BaseActivity() {
    }

    /**
     * Override to change the logging message.
     *
      * @return  The logging message (for debugging)
     */
    @SuppressWarnings("JavaDoc")
    protected String getDebugMessage() {
        return "activity " + Utils.getActivityName(this);
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
    public void onActionModeFinished(ActionMode mode) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", mode " + mode, false);

        super.onActionModeFinished(mode);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActionModeStarted(ActionMode mode) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", mode " + mode, false);

        super.onActionModeStarted(mode);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", resultCode " + resultCode + ", data " + data, false);

        super.onActivityReenter(resultCode, data);
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
    public void onAttachFragment(android.app.Fragment fragment) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", fragment " + fragment, false);

        if (android.support.v4.app.FragmentActivity.class.isInstance(this))
            CoreLogger.logWarning("about to attach android.app.Fragment to FragmentActivity");

        super.onAttachFragment(fragment);
    }

    //YakhontPreprocessor:addToGenerated-FragmentActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-FragmentActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-FragmentActivity-@Override
    //YakhontPreprocessor:addToGenerated-FragmentActivity-public void onAttachFragment(android.support.v4.app.Fragment fragment) {
    //YakhontPreprocessor:addToGenerated-FragmentActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", fragment " + fragment, false);
    //YakhontPreprocessor:addToGenerated-FragmentActivity-    super.onAttachFragment(fragment);
    //YakhontPreprocessor:addToGenerated-FragmentActivity-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onAttachedToWindow() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onAttachedToWindow();
    }

    /**
     * Please refer to the base method description.
     */
    @Override
    public void onBackPressed() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onBackPressed();
    }

    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-@Override
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-public boolean onChildClick(android.widget.ExpandableListView parent, View v,
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-                            int groupPosition, int childPosition, long id) {
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", groupPosition " + groupPosition +
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-            ", childPosition " + childPosition + ", id " + id, false);
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-    return super.onChildClick(parent, v, groupPosition, childPosition, id);
    //YakhontPreprocessor:addToGenerated-ExpandableListActivity-}

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
    protected void onCreate(Bundle savedInstanceState) {
        BaseApplication.setStrictMode(getApplication(), getDebugMessage());

        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onCreate(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    @SuppressWarnings("deprecation")
    protected Dialog onCreateDialog(int id) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", id " + id, false);

        return super.onCreateDialog(id);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    @SuppressWarnings("deprecation")
    protected Dialog onCreateDialog(int id, Bundle args) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", id " + id + ", args " + args, false);

        return super.onCreateDialog(id, args);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", parent " + parent +
                ", name " + name + ", attrs " + attrs, false);

        return super.onCreateView(parent, name, context, attrs);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() +
                ", name " + name + ", attrs " + attrs, false);

        return super.onCreateView(name, context, attrs);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onDestroy() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onDestroy();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onDetachedFromWindow() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onDetachedFromWindow();
    }

    //YakhontPreprocessor:addToGenerated-PreferenceActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-@Override
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-public void onHeaderClick(android.preference.PreferenceActivity.Header header, int position) {
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", position " + position, false);
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-    super.onHeaderClick(header, position);
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-}

    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-@Override
    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-protected void onListItemClick(android.widget.ListView l, View v, int position, long id) {
    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage() +
    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-            ", position " + position + ", id " + id, false);
    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-    super.onListItemClick(l, v, position, id);
    //YakhontPreprocessor:addToGenerated-ListActivity,LauncherActivity,PreferenceActivity-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onLowMemory() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onLowMemory();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public boolean onNavigateUp() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        return super.onNavigateUp();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public boolean onNavigateUpFromChild(Activity child) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", child " + child, false);

        return super.onNavigateUpFromChild(child);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onNewIntent(Intent intent) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", intent " + intent, false);

        super.onNewIntent(intent);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onPause() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onPause();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onPostCreate(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onPostCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState +
                ", persistentState " + persistentState, false);

        super.onPostCreate(savedInstanceState, persistentState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onPostResume() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onPostResume();
    }

    //YakhontPreprocessor:addToGenerated-PreferenceActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-@Override
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-public boolean onPreferenceStartFragment(android.preference.PreferenceFragment caller,
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-                                         android.preference.Preference pref) {
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-    return super.onPreferenceStartFragment(caller, pref);
    //YakhontPreprocessor:addToGenerated-PreferenceActivity-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    @SuppressWarnings("deprecation")
    protected void onPrepareDialog(int id, Dialog dialog) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", id " + id, false);

        super.onPrepareDialog(id, dialog);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    @SuppressWarnings("deprecation")
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", id " + id + ", args " + args, false);

        super.onPrepareDialog(id, dialog, args);
    }

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
    protected void onRestart() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onRestart();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState, false);

        super.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState, PersistableBundle persistentState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", savedInstanceState " + savedInstanceState +
                ", persistentState " + persistentState, false);

        super.onRestoreInstanceState(savedInstanceState, persistentState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onResume() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onResume();
    }

    //YakhontPreprocessor:addToGenerated-FragmentActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-FragmentActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-FragmentActivity-@Override
    //YakhontPreprocessor:addToGenerated-FragmentActivity-protected void onResumeFragments() {
    //YakhontPreprocessor:addToGenerated-FragmentActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-FragmentActivity-    super.onResumeFragments();
    //YakhontPreprocessor:addToGenerated-FragmentActivity-}

    //YakhontPreprocessor:addToGenerated-FragmentActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-FragmentActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-FragmentActivity-@Override
    //YakhontPreprocessor:addToGenerated-FragmentActivity-public Object onRetainCustomNonConfigurationInstance() {
    //YakhontPreprocessor:addToGenerated-FragmentActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-FragmentActivity-    return super.onRetainCustomNonConfigurationInstance();
    //YakhontPreprocessor:addToGenerated-FragmentActivity-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", outState " + outState, false);

        super.onSaveInstanceState(outState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", outState " + outState +
                ", outPersistentState " + outPersistentState, false);

        super.onSaveInstanceState(outState, outPersistentState);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onStart() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onStart();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onStateNotSaved() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onStateNotSaved();
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    protected void onStop() {
        CoreLogger.log(getDebugLevel(), getDebugMessage(), false);

        super.onStop();
    }

    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-@Override
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-public void onSupportActionModeFinished(android.support.v7.view.ActionMode mode) {
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", mode " + mode, false);
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-    super.onSupportActionModeFinished(mode);
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-}

    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-@Override
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-public void onSupportActionModeStarted(android.support.v7.view.ActionMode mode) {
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage() + ", mode " + mode, false);
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-    super.onSupportActionModeStarted(mode);
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-}

    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-/** Please refer to the base method description. */
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-@CallSuper
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-@Override
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-public boolean onSupportNavigateUp() {
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-    CoreLogger.log(getDebugLevel(), getDebugMessage(), false);
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-    return super.onSupportNavigateUp();
    //YakhontPreprocessor:addToGenerated-AppCompatActivity,ActionBarActivity-}

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void onTrimMemory(int level) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", level " +
                Utils.getOnTrimMemoryLevelString(level), false);

        super.onTrimMemory(level);
    }

    /**
     * Please refer to the base method description.
     */
    @CallSuper
    @Override
    public void setRequestedOrientation(int requestedOrientation) {
        CoreLogger.log(getDebugLevel(), getDebugMessage() + ", requestedOrientation " + requestedOrientation, true);

        super.setRequestedOrientation(requestedOrientation);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final BackKeyInActionMode mBackKeyInActionMode      = new BackKeyInActionMode();    //YakhontPreprocessor:removeInGenerated

    /**
     * Handles the Back key in ActionMode.
     * For example (in Fragment):
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * private ActionMode.Callback mCallback = new ActionMode.Callback() {
     *
     *     &#064;Override
     *     public boolean onCreateActionMode(ActionMode mode, Menu menu) {
     *         ((BaseActivity) getActivity()).checkBackKeyAndReset();
     *         return true;
     *     }
     *
     *     &#064;Override
     *     public void onDestroyActionMode(ActionMode mode) {
     *         if (((BaseActivity) getActivity()).checkBackKeyAndReset())
     *             // handle Back key (discard changes and exit ActionMode)
     *         else
     *             // save changes and exit ActionMode
     *     }
     * };
     * </pre>
     *
     * @return  {@code true} if the Back key was pressed, {@code false} otherwise
     */
    public boolean checkBackKeyAndReset() {                                                     //YakhontPreprocessor:removeInGenerated
        return mBackKeyInActionMode.checkBackKeyAndReset();                                     //YakhontPreprocessor:removeInGenerated
    }                                                                                           //YakhontPreprocessor:removeInGenerated

    /** @exclude {@yakhont.preprocessor.remove.in.generated} */ @SuppressWarnings("JavaDoc")
    @CallSuper                                                                                  //YakhontPreprocessor:removeInGenerated
    @Override                                                                                   //YakhontPreprocessor:removeInGenerated
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {                                  //YakhontPreprocessor:removeInGenerated
        mBackKeyInActionMode.handleKeyEvent(event);                                             //YakhontPreprocessor:removeInGenerated

        return super.dispatchKeyEvent(event);                                                   //YakhontPreprocessor:removeInGenerated
    }                                                                                           //YakhontPreprocessor:removeInGenerated

    @SuppressWarnings("unused")                                                                 //YakhontPreprocessor:removeInGenerated
    private static class BackKeyInActionMode {                                                  //YakhontPreprocessor:removeInGenerated

        private final AtomicBoolean mIsBackWasPressed           = new AtomicBoolean();          //YakhontPreprocessor:removeInGenerated

        public void handleKeyEvent(final KeyEvent event) {                                      //YakhontPreprocessor:removeInGenerated
            mIsBackWasPressed.set(event.getKeyCode() == KeyEvent.KEYCODE_BACK);                 //YakhontPreprocessor:removeInGenerated
        }                                                                                       //YakhontPreprocessor:removeInGenerated

        public boolean checkBackKeyAndReset() {                                                 //YakhontPreprocessor:removeInGenerated
            return mIsBackWasPressed.getAndSet(false);                                          //YakhontPreprocessor:removeInGenerated
        }                                                                                       //YakhontPreprocessor:removeInGenerated
    }                                                                                           //YakhontPreprocessor:removeInGenerated
}
