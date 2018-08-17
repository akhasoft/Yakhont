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

package retrofit;

import akha.yakhont.Core.Utils.TypeHelper;

import android.support.annotation.NonNull;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

import retrofit.converter.Converter;

/** @exclude */ @SuppressWarnings("JavaDoc")
public class YakhontRestAdapter<T> {

    private Class<T>                        mService;
    private Map<Method, RestMethodInfo>     mCache;
    private Converter                       mConverter;

    public T create(@NonNull final Class<T> service, @NonNull final RestAdapter restAdapter) {
        mService   = service;
        mCache     = restAdapter.getMethodInfoCache(service);
        mConverter = restAdapter.converter;

        return restAdapter.create(service);
    }

    public Type getType(final Method method) {
        return method == null ? null: RestAdapter.getMethodInfo(mCache, method).responseObjectType;
    }

    public Method findDefaultMethod(@NonNull final Type typeResponse) {
        for (final Method method: mService.getMethods()) {
            final Class<?>[] params = method.getParameterTypes();
            if (params != null && params.length == 1 && Callback.class.isAssignableFrom(params[0]) &&
                    TypeHelper.checkType(typeResponse, getType(method)))
                return method;
        }
        return null;
    }

    public Converter getConverter() {
        return mConverter;
    }
}
