/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
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

import akha.yakhont.technology.retrofit.Retrofit2;
import akha.yakhont.technology.rx.BaseRx;
import akha.yakhont.technology.rx.BaseRx.CallbackRx;
import akha.yakhont.technology.rx.BaseRx.CommonRx;
import akha.yakhont.technology.rx.Rx;
import akha.yakhont.technology.rx.Rx.RxSubscription;
import akha.yakhont.technology.rx.Rx2;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

/** @exclude */ @SuppressWarnings("JavaDoc")
public class FlavorHelper {                    // full

    private FlavorHelper() {
    }

    public static void cleanUpRxFinal() {
        Rx.cleanUpFinal();
    }

    public static void setRxErrorHandlerDefault() {
        Rx.setErrorHandlerDefault();
    }

    public static void setRxErrorHandlerJustLog() {
        Rx.setErrorHandlerJustLog();
    }

    public static <D> Object handleRx(final Object handler, final Method method, final Object[] args,
                                      final CallbackRx<D> callback) throws Exception {
        return Rx.handle(handler, method, args, callback);
    }

    public static void cancelRx(final Object subscription) {
        Rx.cancel(subscription);
    }

    public static <D> CommonRx<D> getCommonRx(final boolean isRx2) {
        return isRx2 ? new Rx2<>(): new Rx<>();
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static class FlavorCommonRx {

        // for anonymous Rx
        private static final RxSubscription     sRxSubscription     = new RxSubscription();

        private        final RxSubscription     mRxSubscription     = new RxSubscription();

        public RxSubscription getRxSubscription() {
            return mRxSubscription;
        }

        public void unsubscribe() {
            mRxSubscription.unsubscribe();
        }

        public static void unsubscribeAnonymous(@NonNull final String msg) {
            if (!sRxSubscription.notEmpty()) return;

            CoreLogger.logWarning(String.format(msg, "subscribers"));
            sRxSubscription.unsubscribe();
        }

        public static <D> void add(final BaseRx<D> rx, final Object resultRx) {
            getRxSubscriptionHandler(rx).add(resultRx);
        }

        public static <D> RxSubscription getRxSubscriptionHandler(final BaseRx<D> rx) {
            Retrofit2.checkRxComponent(rx);
            return rx == null ? sRxSubscription: rx.getRx().getFlavorCommonRx().mRxSubscription;
        }
    }
}
