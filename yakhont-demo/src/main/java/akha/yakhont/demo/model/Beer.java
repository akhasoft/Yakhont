/*
 * Copyright (C) 2016 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.demo.model;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

@SuppressWarnings("unused")
public class Beer {

    @SerializedName("title")
    @SuppressWarnings("unused")
    private String mTitle;

    @SerializedName("image")
    @SuppressWarnings("unused")
    private String mImage;

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "title: %s, image: %s", mTitle, mImage);
    }
}
