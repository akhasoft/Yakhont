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

package akha.yakhont.demo.retrofit;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

// for Retrofit 2
public class LocalJsonClient2 extends OkHttpClient {

    private final LocalJsonClientHelper mLocalJsonClientHelper;

    public LocalJsonClient2(Context context) {
        mLocalJsonClientHelper = new LocalJsonClientHelper(context);
    }

    public LocalJsonClientHelper getLocalJsonClientHelper() {
        return mLocalJsonClientHelper;
    }

    private Response execute(final Request request) throws IOException {
        LocalJsonClientHelper.Data data = mLocalJsonClientHelper.execute(request.url().toString(),
                request.method());

        InputStream stream = data.stream();
        byte[] content = new byte[stream.available()];
        //noinspection ResultOfMethodCallIgnored
        stream.read(content);

        ResponseBody body = ResponseBody.create(MediaType.parse(data.mimeType()), content);

        return new Response.Builder()
                .code(LocalJsonClientHelper.HTTP_CODE_OK)
                .protocol(Protocol.HTTP_1_0)
                .request(request)
                .message(data.message())
                .body(body)
                .build();
    }

    @Override
    public Call newCall(final Request request) {
        return new Call() {
            @Override
            public Response execute() throws IOException {
                return LocalJsonClient2.this.execute(request);
            }

            @Override
            public void enqueue(Callback responseCallback) {
                try {
                    responseCallback.onResponse(this, execute());
                }
                catch (IOException e) {
                    responseCallback.onFailure(this, e);
                }
            }

            @Override
            public Call clone() {
                try {
                    return (Call) super.clone();
                }
                catch (CloneNotSupportedException e) {      // should never happen
                    Log.e("LocalJsonClient2", "clone failed", e);
                    return null;
                }
            }

            @Override
            public Request request() {
                return request;
            }

            @Override
            public void cancel() {
            }

            @Override
            public boolean isExecuted() {
                return false;
            }

            @Override
            public boolean isCanceled() {
                return false;
            }
        };
    }

    @Override
    public WebSocket newWebSocket(final Request request, WebSocketListener listener) {
        return new WebSocket() {
            @Override
            public Request request() {
                return request;
            }

            @Override
            public long queueSize() {
                return 0;
            }

            @Override
            public boolean send(String text) {
                return true;
            }

            @Override
            public boolean send(ByteString bytes) {
                return true;
            }

            @Override
            public boolean close(int code, String reason) {
                return true;
            }

            @Override
            public void cancel() {
            }
        };
    }
}
