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

package akha.yakhont.callback;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CoreReflection;
import akha.yakhont.callback.annotation.Callbacks;
import akha.yakhont.callback.annotation.CallbacksInherited;
import akha.yakhont.callback.annotation.StopCallbacks;
import akha.yakhont.callback.annotation.StopCallbacksInherited;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks;
import akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed;
import akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed.BaseFragmentCallbacks;

import android.app.Application.ActivityLifecycleCallbacks;
import androidx.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * The <code>BaseCallbacks</code> is the one of the base classes for working with callbacks. Most
 * implementations should not derive directly from it, but instead inherit from
 * {@link BaseActivityCallbacks} or {@link BaseFragmentCallbacks}.
 *
 * <p>Since the <code>BaseCallbacks</code> is a base class for callbacks handlers, the instances of
 * it should be registered (it's close to the process of registering {@link ActivityLifecycleCallbacks}).
 * <br>The low-level registration goes via call to {@link BaseProceed#register BaseProceed.register},
 * but most implementations should do it using {@link BaseActivityLifecycleProceed#register} or
 * {@link BaseFragmentLifecycleProceed#register(BaseFragmentCallbacks)}.
 *
 * <p>After registering, the implemented callbacks are ready to be called for every object of type T.
 * To proceed callbacks the object should be annotated with {@link Callbacks} or {@link CallbacksInherited}.
 * Another possibility (to proceed all objects) is to set the "force proceed" flag
 * (see {@link #setForceProceed(boolean)}) to <code>true</code>.
 *
 * <p>Usage example (creating callback handler for <code>Activity.onActionModeStarted</code>;
 * for more examples please refer to {@link BaseActivityCallbacks Activity},
 * {@link BaseFragmentCallbacks Fragment} and
 * {@link #proceed(Object, Class, BaseCallbacks) simple Activity} ones):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * package com.yourpackage;
 *
 * import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
 *
 * public class YourCallbacks extends BaseCallbacks.BaseActivityCallbacks {
 *
 *     private static final YourCallbacks sInstance = new YourCallbacks();
 *
 *     private YourCallbacks() {
 *         BaseActivityLifecycleProceed.register(this);
 *     }
 *
 *     public static void yourHandler(Activity activity, ActionMode mode) {
 *    
 *         // proceed annotated Activities only
 *         if (!BaseCallbacks.BaseProceed.proceed(sInstance, activity)) return;
 *
 *         // your code here (NOTE: you don't have to call activity.onActionModeStarted() -
 *         //   it's already done by the Weaver)
 *     }
 * }
 * </pre>
 *
 * Annotate necessary Activities:
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * import akha.yakhont.callback.annotation.CallbacksInherited;
 *
 * &#064;CallbacksInherited(com.yourpackage.YourCallbacks.class)
 * public class YourActivity extends Activity {
 *     ...
 * }
 * </pre>
 *
 * And add the following line to the <code>weaver.config</code> (which says to the Yakhont Weaver to insert the call to
 * <code>YourCallbacks.yourHandler()</code> in the compiled <code>Activity.onActionModeStarted()</code>
 * method body - at the beginning or at the end, depending on the 2nd parameter - see below):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * android.app.Activity.onActionModeStarted before 'com.yourpackage.YourCallbacks.yourHandler($0, $$);'
 * </pre>
 *
 * Here $0 means 'this', $$ - the list of method arguments
 * (for more info please visit {@link <a href="http://jboss-javassist.github.io/javassist/">the Javassist site</a>}, in particular,
 * {@link <a href="http://jboss-javassist.github.io/javassist/tutorial/tutorial2.html#before">Inserting source text at the beginning/end of a method body</a>}).
 *
 * <p>The line in <code>weaver.config</code> should contain 3 tokens delimited by whitespaces:
 *
 * <p>{@code
 * <fully qualified class name>.method[(signature)] <action> 'code'
 * }
 *
 * <p>The possible actions are:
 * <p><dl>
 *  <dt>before</dt>
 *  <dd>- insert 'code' at the beginning of the method body</dd>
 *  <dt>after</dt>
 *  <dd>- insert 'code' at the end of the method body</dd>
 *  <dt>finally</dt>
 *  <dd>- insert 'code' in the 'finally' clause</dd>
 *  <dt>&lt;fully qualified exception name&gt;</dt>
 *  <dd>- insert 'code' in the 'catch' clause *</dd>
 * </dl>
 *
 * * Note that the inserted code fragment must end with a 'throw' or 'return' statement.
 *
 * <p>Some other base classes for working with callbacks are {@link BaseProceed}, {@link BaseLifecycleProceed},
 * {@link BaseCacheCallbacks} and annotations in the <code>akha.yakhont.callback.annotation</code> package.
 *
 * @param <T>
 *        The type of objects for which callbacks should be proceeded
 *
 * @see BaseProceed
 * @see BaseActivityCallbacks
 * @see BaseFragmentCallbacks
 *
 * @author akha
 */
public abstract class BaseCallbacks<T> {

    private static final String                             FORMAT                      = "-%s (%s)";

    private boolean                                         mForceProceed;

    private static Validator                                sValidator;

    /**
     * The callbacks annotations validation API.
     */
    public interface Validator {

        /**
         * Validates the given object's callbacks annotations.
         *
         * @param object
         *        The annotated object
         *
         * @param callbackClasses
         *        The callbacks (if any)
         *
         * @return  {@code true} if validation was successful, {@code false} otherwise
         */
        @SuppressWarnings("UnusedReturnValue")
        boolean validate(Object object, Class<? extends BaseCallbacks>[] callbackClasses);
    }

    /**
     * The API for sending parameters to callbacks.
     */
    @SuppressWarnings("WeakerAccess")
    public interface CallbacksCustomizer {

        /**
         * Sends parameters to callback.
         *
         * @param parameters
         *        The parameters to send (if any)
         *
         * @param properties
         *        The properties to send (if any)
         */
        void set(String[] parameters, int[] properties);
    }

    /**
     * Initialises a newly created {@code BaseCallbacks} object.
     */
    @SuppressWarnings("WeakerAccess")
    public BaseCallbacks() {
    }

    /** @exclude */ @SuppressWarnings("JavaDoc")
    public static void setValidator(final Validator validator) {
        sValidator = validator;
    }

    /**
     * Sets the "force proceed" flag. If set to {@code true} it forces to proceed callbacks for all
     * objects of type T.
     * <br>The default value is {@code false}.
     *
     * @param forceProceed
     *        The value to set
     *
     * @return  This {@code BaseCallbacks} object
     */
    @SuppressWarnings("unused")
    public BaseCallbacks setForceProceed(final boolean forceProceed) {
        mForceProceed = forceProceed;
        return this;
    }

    /**
     * Checks whether the callbacks for the given object should be proceeded or not.
     *
     * @param object
     *        The object for which callbacks could be proceeded
     *
     * @param callback
     *        The {@code BaseCallbacks}
     *
     * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    protected boolean proceed(@NonNull final T object, @NonNull final BaseCallbacks<T> callback) {
        return proceed(object, (Map<T, Boolean>) null, callback);
    }

    /**
     * Checks whether the callbacks for the given object should be proceeded or not.
     *
     * @param object
     *        The object for which callbacks could be proceeded
     *
     * @param cache
     *        The cache of already checked objects
     *
     * @param callback
     *        The {@code BaseCallbacks}
     *
     * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    protected boolean proceed(@NonNull final T object, final Map<T, Boolean> cache,
                              @NonNull final BaseCallbacks<T>  callback) {
        return proceed(object, getClass(), callback, mForceProceed, cache);
    }

    /**
     * Checks whether the callbacks for the given object should be proceeded or not.
     *
     * <p>Usage example (creating callback handler for <code>Service.onStartCommand</code>;
     * for more examples please refer to {@link BaseActivityCallbacks Activity},
     * {@link BaseFragmentCallbacks Fragment} and {@link BaseCallbacks general Activity} ones):
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * package com.yourpackage;
     *
     * public class YourCallbacks { // you can create new class or add handler(s) to the existing one
     *
     *     public static void yourHandler(Service service, Intent intent, int flags, int startId) {
     *
     *         // proceed annotated Services only
     *         if (!BaseCallbacks.proceed(service, YourCallbacks.class)) return;
     *
     *         // your code here (NOTE: you don't have to call service.onStartCommand() -
     *         //   it's already done by the Weaver)
     *     }
     * }
     * </pre>
     *
     * Annotate necessary Services:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * import akha.yakhont.callback.annotation.CallbacksInherited;
     *
     * &#064;CallbacksInherited(com.yourpackage.YourCallbacks.class)
     * public class YourService extends Service {
     *     ...
     * }
     * </pre>
     *
     * And add the following line to the <code>weaver.config</code>:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * android.app.Service.onStartCommand  true  'com.yourpackage.YourCallbacks.yourHandler($0, $$);'
     * </pre>
     *
     * Please refer to the {@link BaseCallbacks} for more details.
     *
     * @param object
     *        The object for which callbacks could be proceeded
     *
     * @param callbackClass
     *        The class of the callbacks handler
     *
     * @param callback
     *        The {@code BaseCallbacks}
     *
     * @param <T>
     *        The type of object for which callbacks should be proceeded
     *
     * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static <T> boolean proceed(@NonNull final T object, @NonNull final Class callbackClass,
                                      @NonNull final BaseCallbacks<T> callback) {
        return proceed(object, callbackClass, callback, false, null);
    }

    /**
     * Checks whether the callbacks for the given object should be proceeded or not.
     *
     * @param object
     *        The object for which callbacks could be proceeded
     *
     * @param callbackClass
     *        The class of the callbacks handler
     *
     * @param callback
     *        The {@code BaseCallbacks}
     *
     * @param forceProceed
     *        The "force proceed" flag
     *
     * @param cache
     *        The cache of already checked objects
     *
     * @param <T>
     *        The type of object for which callbacks should be proceeded
     *
     * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static <T> boolean proceed(@NonNull final T object, @NonNull final Class callbackClass,
                                      @NonNull final BaseCallbacks<T> callback,
                                      final boolean forceProceed, final Map<T, Boolean> cache) {
        log("start checking", callbackClass);

        if (cache != null) {
            final Boolean proceed = cache.get(object);
            if (proceed != null)
                if (proceed) {
                    log("checking is OK - found in cache - proceeding", callbackClass);
                    return true;
                }
                else {
                    log("found in cache - rejecting", callbackClass);
                    return false;
                }
        }

        if (forceProceed)
            log("force proceed", callbackClass);
        else {
            if (!isProceed(object, callbackClass, callback)) {
                log("no Callbacks found", callbackClass);
                return false;
            }
            log("Callbacks found", callbackClass);
        }

        if (isReject(object, callbackClass, callback)) {
            log("StopCallbacks found", callbackClass);
            return false;
        }

        log("checking is OK - proceeding", callbackClass);
        return true;
    }

    private static void log(@NonNull final String text, @NonNull final Class callbackClass) {
        CoreLogger.log(String.format(FORMAT, text, callbackClass.getName()));
    }

    private static <T> boolean isProceed(@NonNull final T object, @NonNull final Class callbackClass,
                                         @NonNull final BaseCallbacks<T> callback) {
        Annotation
        annotation = CoreReflection.getAnnotation(object, CallbacksInherited.class);

        Class<? extends BaseCallbacks>[]
        callbacks  = annotation == null ? null: ((CallbacksInherited) annotation).value();

        String[]
        parameters = annotation == null ? null: ((CallbacksInherited) annotation).parameters();
        int[]
        properties = annotation == null ? null: ((CallbacksInherited) annotation).properties();

        if (isFound(object, callbacks, callbackClass, callback, parameters, properties)) return true;

        annotation = CoreReflection.getAnnotation(object, Callbacks.class);
        callbacks  = annotation == null ? null: ((Callbacks)          annotation).value();

        parameters = annotation == null ? null: ((Callbacks)          annotation).parameters();
        properties = annotation == null ? null: ((Callbacks)          annotation).properties();

        return isFound(object, callbacks, callbackClass, callback, parameters, properties);
    }

    private static <T> boolean isReject(@NonNull final T object, @NonNull final Class callbackClass,
                                        @NonNull final BaseCallbacks<T> callback) {

        Annotation
        annotation = CoreReflection.getAnnotation(object, StopCallbacksInherited.class);

        Class<? extends BaseCallbacks>[]
        callbacks  = annotation == null ? null: ((StopCallbacksInherited) annotation).value();

        String[]
        parameters = annotation == null ? null: ((StopCallbacksInherited) annotation).parameters();
        int[]
        properties = annotation == null ? null: ((StopCallbacksInherited) annotation).properties();

        if (isFound(object, callbacks, callbackClass, callback, parameters, properties)) return true;

        annotation = CoreReflection.getAnnotation(object, StopCallbacks.class);
        callbacks  = annotation == null ? null: ((StopCallbacks)          annotation).value();

        parameters = annotation == null ? null: ((StopCallbacks)          annotation).parameters();
        properties = annotation == null ? null: ((StopCallbacks)          annotation).properties();

        return isFound(object, callbacks, callbackClass, callback, parameters, properties);
    }

    private static <T> boolean isFound(@NonNull final T                       object,
                                       final Class<? extends BaseCallbacks>[] callbackClasses,
                                       @NonNull final Class                   callbackClass,
                                                final BaseCallbacks<T>        callback,
                                       final String[] parameters, final int[] properties) {
        if (sValidator == null)
            CoreLogger.logWarning("sValidator == null");
        else
            sValidator.validate(object, callbackClasses);

        if (callbackClasses != null)
            for (final Class<? extends BaseCallbacks> baseCallbacksClass: callbackClasses)
                if (baseCallbacksClass.isAssignableFrom(callbackClass)) {
                    if (callback != null)
                        if (callback instanceof CallbacksCustomizer)
                            ((CallbacksCustomizer) callback).set(parameters, properties);
                        else if (parameters != null && parameters.length > 0 ||
                                 properties != null && properties.length > 0)
                            CoreLogger.log(callbackClasses.length == 1 ? Level.WARNING: CoreLogger.getDefaultLevel(),
                                    "can't pass parameters to callback 'cause it " +
                                    "doesn't implement CallbacksCustomizer: " +
                                            CoreLogger.getDescription(callback));
                    return true;
                }
        return false;
    }

    /** The callback which is called when the instance of this class registered.   */
    @SuppressWarnings({"WeakerAccess", "EmptyMethod"}) protected void onRegister  () {}
    /** The callback which is called when the instance of this class unregistered. */
    @SuppressWarnings({"WeakerAccess", "EmptyMethod"}) protected void onUnregister() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseCallbacks} class to provide cache (of already checked objects) support.
     *
     * <p>Please refer to the {@link BaseCallbacks} for more details.
     *
     * @param <T>
     *        The type of object for which callbacks should be proceeded
     */
    public static abstract class BaseCacheCallbacks<T> extends BaseCallbacks<T> {

        private final Set<T>                    mProceeded                              = Utils.newWeakSet();
        private final Map<T, Boolean>           mAll                                    = Utils.newWeakMap();

        /**
         * Initialises a newly created {@code BaseCacheCallbacks} object.
         */
        protected BaseCacheCallbacks() {
        }

        /**
         * Returns collection of already proceeded objects.
         *
         * @return The proceeded objects
         */
        @SuppressWarnings("WeakerAccess")
        public Set<T> getProceeded() {
            return mProceeded;
        }

        /**
         * Please refer to the base method description.
         */
        @Override
        protected boolean proceed(@NonNull final T object, @NonNull final BaseCallbacks<T> callback) {
            return proceed(object, mAll, callback);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The <code>BaseProceed</code> is intended for support of {@link BaseCallbacks} instances registration.
     * Most implementations should not use it directly, but instead call
     * {@link BaseActivityLifecycleProceed#register} or
     * {@link BaseFragmentLifecycleProceed#register(BaseFragmentCallbacks)}.
     *
     * @see BaseCallbacks
     */
    @SuppressWarnings("WeakerAccess")
    public static abstract class BaseProceed {

        /**
         * Initialises a newly created {@code BaseProceed} object.
         */
        public BaseProceed() {
        }

        /**
         * Registers the callbacks handler.
         *
         * @param callbacksMap
         *        The registered callbacks handlers collection
         *
         * @param callbacks
         *        The callbacks handler to register
         *
         * @param data
         *        The additional data associated with the callbacks handler
         *
         * @param <C>
         *        The type of the callbacks handler
         *
         * @param <D>
         *        The type of data associated with the callbacks handler
         *
         * @return  {@code true} if the callbacks handler was successfully registered, {@code false} otherwise
         */
        @SuppressWarnings({"SameParameterValue", "SameReturnValue"})
        public static <C extends BaseCacheCallbacks, D> boolean register(
                @NonNull final Map<C, D> callbacksMap, @NonNull final C callbacks, @NonNull final D data) {
            callbacks.onRegister();

            callbacksMap.put(callbacks, data);
            return true;
        }

        /**
         * Unregisters the callbacks handler.
         *
         * @param callbacksMap
         *        The registered callbacks handlers collection
         *
         * @param callbacksClass
         *        The class of the callbacks handler to unregister
         *
         * @param <T>
         *        The type of object for which callbacks should be proceeded
         *
         * @param <D>
         *        The type of data associated with the callbacks handler
         *
         * @return  {@code true} if the callbacks handler was successfully unregistered, {@code false} otherwise
         */
        @SuppressWarnings("SameParameterValue")
        public static <T, D> boolean unregister(@NonNull final Map<? extends BaseCacheCallbacks<T>, D> callbacksMap,
                                                @NonNull final Class<? extends BaseCacheCallbacks<T>> callbacksClass) {
            for (final BaseCacheCallbacks<T> callbacks: callbacksMap.keySet())
                if (callbacks.getClass().equals(callbacksClass)) {
                    callbacks.onUnregister();

                    callbacksMap.remove(callbacks);
                    return true;
                }

            CoreLogger.logError("can not unregister " + callbacksClass.getName());
            return false;
        }

        /**
         * Checks whether the callbacks for the given object should be proceeded or not.
         *
         * @param callbacks
         *        The callbacks handler
         *
         * @param object
         *        The object for which callbacks could be proceeded
         *
         * @param <T>
         *        The type of object for which callbacks should be proceeded
         *
         * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
         */
        @SuppressWarnings("unused")
        public static <T> boolean proceed(@NonNull final BaseCacheCallbacks<T> callbacks, @NonNull final T object) {
            return proceed(callbacks, null, object, null);
        }

        /** @exclude */ @SuppressWarnings("JavaDoc")
        public static <T> boolean proceed(@NonNull final BaseCacheCallbacks<T> callbacks, final Boolean isCreate,
                                          @NonNull final T object, final Callable<Boolean> condition) {
            try {
                boolean proceed = false;
                if (isCreate != null)
                    if (isCreate) {
                        proceed = callbacks.proceed(object, callbacks);
                        add(callbacks, proceed, object);
                    }
                    else
                        remove(callbacks, object);

                if (condition != null && !condition.call()) return false;

                // it's possible to be registered after onCreate
                if (!(isCreate != null &&  isCreate)) proceed = callbacks.proceed(object, callbacks);
                if (!(isCreate != null && !isCreate)) add(callbacks, proceed, object);

                return proceed;
            }
            catch (Exception exception) {
                CoreLogger.log(exception);
                return false;
            }
        }

        private static <T> void add(@NonNull final BaseCacheCallbacks<T> callbacks, final boolean proceed, @NonNull final T object) {
            if (!callbacks.mAll.containsKey(object))                callbacks.mAll.put(object, proceed);
            //noinspection RedundantCollectionOperation
            if (proceed && !callbacks.mProceeded.contains(object))  callbacks.mProceeded.add(object);
        }

        private static <T> void remove(@NonNull final BaseCacheCallbacks<T> callbacks, @NonNull final T object) {
            //noinspection RedundantCollectionOperation
            if (callbacks.mAll.containsKey(object))                 callbacks.mAll.remove(object);
            //noinspection RedundantCollectionOperation
            if (callbacks.mProceeded.contains(object))              callbacks.mProceeded.remove(object);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseProceed} class to provide the base object's lifecycle support.
     *
     * @see BaseCallbacks
     * @see BaseActivityLifecycleProceed
     * @see BaseFragmentLifecycleProceed
     */
    public static abstract class BaseLifecycleProceed extends BaseProceed {
                                                                                                  /*
                             Make things as simple as possible, but not simpler.
                               - A saying attributed to Albert Einstein
                                                                                                  */
        private static final String                         FORMAT                      = "%s (%s)";

        /**
         * Initialises a newly created {@code BaseLifecycleProceed} object.
         */
        @SuppressWarnings("WeakerAccess")
        public BaseLifecycleProceed() {
        }

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess"})
        protected static void log(@NonNull final String name, @NonNull final String info) {
            CoreLogger.log(String.format(FORMAT, info, name));
        }

        /**
         * Registers the callbacks handler.
         *
         * @param callbacksMap
         *        The registered callbacks handlers collection
         *
         * @param callbacks
         *        The callbacks handler to register
         *
         * @param type
         *        The class of the lifecycle enumeration
         *
         * @param namesMap
         *        The lifecycle callbacks method's names to enumeration mapping
         *
         * @param baseClass
         *        The type of the base callbacks handler
         *
         * @param silent
         *        {@code true} to suppress 'no implemented callbacks' error reporting
         *
         * @param <E>
         *        The type of the lifecycle enumeration
         *
         * @param <C>
         *        The type of the callbacks handler
         *
         * @return  {@code true} if the callbacks handler was successfully registered, {@code false} otherwise
         */
        @SuppressWarnings({"WeakerAccess", "SameReturnValue", "SameParameterValue"})
        public static <E extends Enum<E>, C extends BaseCacheCallbacks> boolean register(
                @NonNull final Map<C, Set<E>> callbacksMap, @NonNull final C callbacks,
                @NonNull final Class<E> type, @NonNull final Map<String, E> namesMap,
                @NonNull final Class<? extends BaseCacheCallbacks> baseClass, final boolean silent) {

            final Set<E> lifeCycles = getImplementedCallbacks(callbacks, type, namesMap,
                    baseClass, silent);

            return register(callbacksMap, callbacks, lifeCycles);
        }

        @NonNull
        private static <E extends Enum<E>, C extends BaseCacheCallbacks> Set<E> getImplementedCallbacks(
                @NonNull final C callbacks, @NonNull final Class<E> type, @NonNull final Map<String, E> namesMap,
                @NonNull final Class<? extends BaseCacheCallbacks> baseClass, final boolean silent) {

            final EnumSet<E> lifeCycles = EnumSet.noneOf(type);

            final Class callbacksClass = callbacks.getClass();

            final List<Method> methods = CoreReflection.findOverriddenMethods(callbacksClass, baseClass);
            for (final Method method: methods) {
                final E lifeCycle = namesMap.get(method.getName());
                if (lifeCycle != null) lifeCycles.add(lifeCycle);
            }

            if (lifeCycles.size() > 0) {
                CoreLogger.log("implemented callbacks in " + callbacksClass.getName() + ":");
                for (final E lifeCycle: lifeCycles)
                    CoreLogger.log("  " + lifeCycle.name());
            }
            else
                if (!silent) CoreLogger.logError("no implemented callbacks found in " +
                        callbacksClass.getName());

            return Collections.unmodifiableSet(lifeCycles);
        }

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess", "SameParameterValue"})
        public static <E extends Enum<E>, T> void apply(
                @NonNull final Map<? extends BaseCacheCallbacks<T>, Set<E>> callbacksMap, @NonNull final BaseCacheCallbacks<T> callbacks,
                final Boolean created, @NonNull final E lifeCycle, @NonNull final T object, @NonNull final Runnable runnable) {

            //noinspection Convert2Lambda
            if (proceed(callbacks, created, object, new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    final Set<E> lifeCycles = callbacksMap.get(callbacks);
                    return lifeCycles == null || lifeCycles.size() == 0 || lifeCycles.contains(lifeCycle);
                }
            }))
                try {
                    runnable.run();
                }
                catch (Exception e) {
                    CoreLogger.log("apply failed", e);
                }
        }
    }
}
