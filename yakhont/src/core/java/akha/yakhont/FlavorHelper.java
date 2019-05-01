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

package akha.yakhont;

import akha.yakhont.technology.rx.BaseRx;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.Rx2;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

/** @exclude */ @SuppressWarnings("JavaDoc")
public class FlavorHelper {

    private FlavorHelper() {
    }

    @SuppressWarnings("EmptyMethod")
    public static void setRxErrorHandlerDefault() {
    }

    @SuppressWarnings("EmptyMethod")
    public static void setRxErrorHandlerJustLog() {
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public static <D> Object handleRx(final Object handler, final Method method, final Object[] args,
                                      final CallbackRx<D> callback) {
        return null;
    }

    @SuppressWarnings({"EmptyMethod", "unused"})
    public static void cancelRx(final Object subscription) {
    }

    public static <D> CommonRx<D> getCommonRx(final boolean isRx2) {
        if (isRx2) return new Rx2<>();

        CoreLogger.logError("for Rx 1 support please use the full version of Yakhont");
        return null;
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class FlavorCommonRx {

        @SuppressWarnings("EmptyMethod")
        public void unsubscribe() {
        }

        @SuppressWarnings({"EmptyMethod", "unused"})
        public static void unsubscribeAnonymous(@NonNull final String msg) {
        }

        @SuppressWarnings({"EmptyMethod", "unused"})
        public static <D> void add(final BaseRx<D> rx, final Object resultRx) {
        }
    }
}
