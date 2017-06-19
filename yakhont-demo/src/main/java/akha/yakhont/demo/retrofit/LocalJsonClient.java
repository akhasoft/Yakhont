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
import java.util.ArrayList;

import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedInput;

/**
 * based on implementation of Matt Swanson
 * (please refer to https://gist.github.com/swanson/7dee3f3474e30fe8f15c)
 */
public class LocalJsonClient implements Client {

    private final LocalJsonClientHelper mLocalJsonClientHelper;

    public LocalJsonClient(Context context) {
        mLocalJsonClientHelper = new LocalJsonClientHelper(context);
    }

    public LocalJsonClientHelper getLocalJsonClientHelper() {
        return mLocalJsonClientHelper;
    }

    @Override
    public Response execute(Request request) throws IOException {
        LocalJsonClientHelper.Data data = mLocalJsonClientHelper.execute(request.getUrl(), request.getMethod());
        return new Response(request.getUrl(), LocalJsonClientHelper.HTTP_CODE_OK, data.message(),
                new ArrayList<Header>(), new TypedInputStream(data));
    }

    private static class TypedInputStream implements TypedInput {

        private final LocalJsonClientHelper.Data mData;

        private TypedInputStream(LocalJsonClientHelper.Data data) {
            mData = data;
        }

        @Override
        public String mimeType() {
            return mData.mimeType();
        }

        @Override
        public long length() {
            try {
                return mData.stream().available();
            }
            catch (IOException e) {
                Log.e("LocalJsonClient", "error", e);
                return 0;
            }
        }

        @Override
        public InputStream in() throws IOException {
            return mData.stream();
        }
    }
}
