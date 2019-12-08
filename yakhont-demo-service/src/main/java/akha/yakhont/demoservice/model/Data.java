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

package akha.yakhont.demoservice.model;

import androidx.annotation.NonNull;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import java.util.Locale;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class Data extends BaseObservable {      // POJO with Data Binding Library support

    @SerializedName("title")
    @SuppressWarnings("unused")
    private String mTitle;

    @Bindable
    public String getTitle() {
        return mTitle;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "title: %s", mTitle);
    }
}
