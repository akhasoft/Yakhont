/*
 * Copyright (C) 2015-2018 akha, a.k.a. Alexander Kharitonov
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

import akha.yakhont.Core.Utils;

import android.util.Log;

import java.io.IOException;

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

public class LocalJsonClient2 extends OkHttpClient {

    private static final String[] DATA = new String[] {
            "Duvel",
            "Abbaye de Brogne",
            "Chimay",
            "Delirium Tremens",
            "Gouden Carolus",
            "Green Killer",
            "Gulden Draak",
            "Liefmans",
            "Orval Trappist",
            "Pauwel Kwak",
            "Petrus",
            "Rodenbach Grand Cru",
            "Val-Dieu",
            "Waterloo",
            "Westmalle",
            "Westvleteren",
            "Wilderen Goud"
    };

    private int mEmulatedNetworkDelay;

    private static String getJson() {
        StringBuilder builder = new StringBuilder("[");
        for (String str: DATA)
            builder.append("{\"title\":\"").append(str).append("\"},");
        return builder.replace(builder.length() - 1, builder.length(), "]").toString();
    }

    @SuppressWarnings("unused")
    public LocalJsonClient2 setEmulatedNetworkDelay(int delay) {
        mEmulatedNetworkDelay = delay;
        return this;
    }

    private Response execute(final Request request) {
        return new Response.Builder()
                .code(200)
                .protocol(Protocol.HTTP_1_0)
                .request(request)
                .message("")
                .body(ResponseBody.create(MediaType.parse("application/json"),
                        getJson().getBytes()))
                .build();
    }

    @Override
    public Call newCall(final Request request) {
        return new Call() {
            @Override
            public Response execute() {
                return LocalJsonClient2.this.execute(request);
            }

            @Override
            public void enqueue(final Callback responseCallback) {
                if (mEmulatedNetworkDelay > 0) {
                    final Call call = this;
                    //noinspection Convert2Lambda
                    Utils.runInBackground(mEmulatedNetworkDelay * 1000, new Runnable() {
                        @Override
                        public void run() {
                            enqueueWrapper(call, responseCallback);
                        }
                    });
                }
                else
                    enqueueWrapper(this, responseCallback);
            }

            private void enqueueWrapper(Call call, Callback responseCallback) {
                try {
                    responseCallback.onResponse(call, execute());
                }
                catch (IOException e) {
                    responseCallback.onFailure(call, e);
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
