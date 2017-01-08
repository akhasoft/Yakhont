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

package akha.yakhont.demo.gui;

import akha.yakhont.demo.MainActivity;
import akha.yakhont.demo.R;

import akha.yakhont.SupportHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

/**
 * not directly related to the Yakhont Demo - just some GUI stuff
 */
public class SplashActivity extends /* Activity */ android.support.v7.app.AppCompatActivity {

    private final static int DELAY_ACTIVITY_START   = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //noinspection ConstantConditions
        setTheme(SupportHelper.isSupportMode(this) ? R.style.AppThemeCompat_Splash: R.style.AppThemeCompat_Splash_Hack);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);
        overridePendingTransition(android.R.anim.fade_in, 0);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final Activity activity = SplashActivity.this;

                activity.startActivity(new Intent(activity, MainActivity.class));
                activity.overridePendingTransition(R.anim.hyperspace_in, R.anim.hyperspace_out);
                
                activity.finish();
            }
        }, DELAY_ACTIVITY_START);
    }
}
