/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import akha.yakhont.technology.rx.BaseRx.RxVersions;
import akha.yakhont.technology.rx.Rx;
import akha.yakhont.technology.rx.Rx.RxSubscription;
import akha.yakhont.technology.rx.Rx2;
import akha.yakhont.technology.rx.Rx3;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

/** @exclude */ @SuppressWarnings("JavaDoc")
public class FlavorHelper {                    // full

    private FlavorHelper() {
    }

    public static void cleanUpRxFinal() {
        Rx .cleanUpFinal();
        Rx2.cleanUpFinal();
    }

    public static void setRxErrorHandlerDefault() {
        Rx .setErrorHandlerDefault();
        Rx2.setErrorHandlerDefault();
    }

    public static void setRxErrorHandlerJustLog() {
        Rx .setErrorHandlerJustLog();
        Rx2.setErrorHandlerJustLog();
    }

    private static void checkVersion(final RxVersions version, final RxVersions versionReal) {
        if (version != null && !version.equals(versionReal))
            CoreLogger.logError("RxJava error: declared " + version + ", but actually is " + versionReal);
    }

    public static <D> Object handleRx(final Object handler, final Method method, final Object[] args,
                                      final CallbackRx<D> callback) throws Exception {
        final RxVersions version = CommonRx.getVersion();

        Object result = Rx2.handle(handler, method, args, callback);
        if (result != null) {
            checkVersion(version, RxVersions.VERSION_2);
            return result;
        }

        result = Rx.handle(handler, method, args, callback);
        if (result != null)
            checkVersion(version, RxVersions.VERSION_1);

        return result;
    }

    public static void cancelRx(final Object object) {
        Rx2.cancel(object);
        Rx .cancel(object);
    }

    public static <D> CommonRx<D> getCommonRx(final RxVersions version) {
        switch (version) {
            case VERSION_1:
                return new Rx <>();
            case VERSION_2:
                return new Rx2<>();
            case VERSION_3:
                return new Rx3<>();
            default:                // should never happen
                CoreLogger.logError("getCommonRx(): wrong RxVersion " + version);
                return null;
        }
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
