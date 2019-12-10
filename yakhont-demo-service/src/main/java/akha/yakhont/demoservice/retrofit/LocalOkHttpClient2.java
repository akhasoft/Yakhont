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

package akha.yakhont.demoservice.retrofit;

import akha.yakhont.technology.retrofit.BaseLocalOkHttpClient2;
import akha.yakhont.technology.retrofit.Retrofit2;

import java.util.Date;

// OkHttp3 local client for Retrofit 2
public class LocalOkHttpClient2 extends BaseLocalOkHttpClient2 {

    public LocalOkHttpClient2(Retrofit2 retrofit2) {
        super(retrofit2);
    }

    @Override
    protected String getContent() {
        return getJson("*** Yakhont demo service result, " + new Date() + " ***");
    }
}
