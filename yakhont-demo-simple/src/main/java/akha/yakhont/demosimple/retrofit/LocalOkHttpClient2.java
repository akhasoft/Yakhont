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

package akha.yakhont.demosimple.retrofit;

import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.retrofit.Retrofit2.BodyCache;
import akha.yakhont.technology.retrofit.Retrofit2.BodySaverInterceptor;

import okhttp3.logging.HttpLoggingInterceptor;

// also base OkHttp3 local client, but with Yakhont dependencies
public abstract class LocalOkHttpClient2 extends LocalOkHttpClient2Base {

    private final   Retrofit2   mRetrofit2;

    protected LocalOkHttpClient2(Retrofit2 retrofit2) {
        mRetrofit2 = retrofit2;
        final HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BODY);
        add(logger);

        add(new BodySaverInterceptor() {
            @Override
            public void set(BodyCache data) {
                mRetrofit2.setData(data);
            }
        });
    }
}