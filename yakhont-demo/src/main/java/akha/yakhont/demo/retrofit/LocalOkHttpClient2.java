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

package akha.yakhont.demo.retrofit;

import akha.yakhont.CoreLogger;
import akha.yakhont.technology.retrofit.BaseLocalOkHttpClient2;
import akha.yakhont.technology.retrofit.Retrofit2;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// OkHttp3 local client for Retrofit 2
public class LocalOkHttpClient2 extends BaseLocalOkHttpClient2 {

    private final LocalOkHttpClientHelper   mLocalOkHttpClientHelper;

    public LocalOkHttpClient2(Context context, Retrofit2 retrofit2) {
        super(retrofit2);

        mLocalOkHttpClientHelper = new LocalOkHttpClientHelper(context);
    }

    public LocalOkHttpClientHelper getLocalOkHttpClientHelper() {
        return mLocalOkHttpClientHelper;
    }

    @Override
    protected Response handle(final Request request) throws IOException {
        LocalOkHttpClientHelper.Data data = mLocalOkHttpClientHelper.execute(request.url().toString(),
                request.method());

        InputStream stream = data.stream();
        byte[] content = new byte[stream.available()];

        int result = stream.read(content);
        if (result <= 0) {
            CoreLogger.logError("can't read input stream");
            return null;
        }

        return new Response.Builder()
                .code(HTTP_CODE_OK)
                .protocol(Protocol.HTTP_1_0)
                .request(request)
                .message(data.message())
                .body(ResponseBody.create(MediaType.parse(data.mimeType()), content))
                .build();
    }
}
