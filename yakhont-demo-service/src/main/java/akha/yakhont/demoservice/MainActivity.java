/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.demoservice;

import akha.yakhont.Core;
import akha.yakhont.Core.Utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends Activity {

    private static final int SERVICE_DELAY = 2000;  // ms

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (BuildConfig.DEBUG) Core.setFullLoggingInfo(true);       // optional

        Button buttonStartService = findViewById(R.id.button);
        buttonStartService.setTransformationMethod(null);   // switches off button's text capitalization

        buttonStartService.setOnClickListener(view -> {
            finish();
            Utils.runInBackground(SERVICE_DELAY, () -> startService(
                    new Intent(Utils.getApplication().getApplicationContext(), MainService.class)));
        });
    }
}
