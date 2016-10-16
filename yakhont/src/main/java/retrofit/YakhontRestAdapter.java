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

package retrofit;

import android.support.annotation.NonNull;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;

/** @exclude */ @SuppressWarnings("JavaDoc")
public class YakhontRestAdapter<T> {

    private T                               mHandler;
    private Class<T>                        mService;
    private Map<Method, RestMethodInfo>     mMethodInfoCache;

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public interface YakhontCallback<D> extends Callback<D> {
        boolean setType(Type type);
    }

    @SuppressWarnings("unchecked")
    public T create(@NonNull final Class<T> service, @NonNull final RestAdapter restAdapter) {
        mHandler            = restAdapter.create(service);
        mService            = service;
        mMethodInfoCache    = restAdapter.getMethodInfoCache(service);

        return (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] {service}, new YakhontHandler());
    }

    private class YakhontHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            final boolean notInvoke = (!method.getDeclaringClass().equals(Object.class)
                    && args != null && args.length > 0 && args[args.length - 1] instanceof YakhontCallback)
                    && ((YakhontCallback) args[args.length - 1]).setType(getType(method));
            return notInvoke ? null: method.invoke(mHandler, args);
        }
    }

    private Type getType(@NonNull final Method method) {
        return RestAdapter.getMethodInfo(mMethodInfoCache, method).responseObjectType;
    }

    public Method findMethod(@NonNull final Class responseType) {
        for (final Method method: mService.getMethods()) {
            final Type type = getType(method);
            if (type.equals(responseType) || (responseType.isArray() && type instanceof GenericArrayType
                    && responseType.getComponentType().equals(((GenericArrayType) type).getGenericComponentType()))) return method;
        }
        return null;
    }

    public T getHandler() {
        return mHandler;
    }
}
