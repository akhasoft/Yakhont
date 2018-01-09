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

package akha.yakhont.callback;

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger;
import akha.yakhont.CoreLogger.Level;
import akha.yakhont.CoreReflection;
import akha.yakhont.callback.annotation.Callbacks;
import akha.yakhont.callback.annotation.CallbacksInherited;
import akha.yakhont.callback.annotation.StopCallbacks;
import akha.yakhont.callback.annotation.StopCallbacksInherited;

import android.support.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * The <code>BaseCallbacks</code> is the one of the base classes for working with callbacks. Most implementations should not
 * derive directly from it, but instead inherit from 
 * {@link akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks} or 
 * {@yakhont.link BaseFragmentLifecycleProceed.BaseFragmentCallbacks}.
 *
 * <p>Since the <code>BaseCallbacks</code> is a base class for callbacks handlers, the instances of it should be registered
 * (it's close to the process of registering {@link android.app.Application.ActivityLifecycleCallbacks}).
 * <br>The low-level registration goes via call to {@link BaseProceed#register BaseProceed.register}, but most implementations
 * should do it using {@link akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed#register BaseActivityLifecycleProceed.register} or
 * {@yakhont.link BaseFragmentLifecycleProceed#register(akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed.BaseFragmentCallbacks) BaseFragmentLifecycleProceed.register}.
 *
 * <p>After registering, the implemented callbacks are ready to be called for every object of type T.
 * To proceed callbacks the object should be annotated with {@link Callbacks} or {@link CallbacksInherited}.
 * Another possibility (to proceed all objects) is to set the "force proceed" flag
 * (see {@link #setForceProceed(boolean)}) to <code>true</code>.
 *
 * <p>Usage example (creating callback handler for <code>Activity.onActionModeStarted</code>;
 * for more examples please refer to {@link akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks Activity}, 
 * {@yakhont.link BaseFragmentLifecycleProceed.BaseFragmentCallbacks Fragment} and
 * {@link #proceed(Object, Class) simple Activity} ones):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * package com.mypackage;
 *
 * import akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed;
 *
 * public class MyCallbacks extends BaseCallbacks.BaseActivityCallbacks {
 *
 *     private static final MyCallbacks sInstance = new MyCallbacks();
 *
 *     private MyCallbacks() {
 *         BaseActivityLifecycleProceed.register(this);
 *     }
 *
 *     public static void myHandler(Activity activity, ActionMode mode) {
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
 * &#064;CallbacksInherited(com.mypackage.MyCallbacks.class)
 * public class MyActivity extends Activity {
 *     ...
 * }
 * </pre>
 *
 * And add the following line to the <code>weaver.config</code> (which says to the Yakhont Weaver to insert the call to
 * <code>MyCallbacks.myHandler()</code> in the compiled <code>Activity.onActionModeStarted()</code>
 * method body - at the beginning or at the end, depending on the 2nd parameter - see below):
 *
 * <p><pre style="background-color: silver; border: thin solid black;">
 * android.app.Activity.onActionModeStarted before 'com.mypackage.MyCallbacks.myHandler($0, $$);'
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
 * @yakhont.see BaseCallbacks.BaseProceed
 * @yakhont.see BaseActivityLifecycleProceed.BaseActivityCallbacks
 * @yakhont.see BaseFragmentLifecycleProceed.BaseFragmentCallbacks
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
     * Sets the "force proceed" flag. If set to {@code true} it forces to proceed callbacks for all objects of type T.
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
     * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    protected boolean proceed(@NonNull final T object) {
        return proceed(object, (Map<T, Boolean>) null);
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
     * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    protected boolean proceed(@NonNull final T object, final Map<T, Boolean> cache) {
        return proceed(object, getClass(), mForceProceed, cache);
    }

    /**
     * Checks whether the callbacks for the given object should be proceeded or not.
     *
     * <p>Usage example (creating callback handler for <code>Service.onStartCommand</code>;
     * for more examples please refer to {@link akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed.BaseActivityCallbacks Activity}, 
     * {@yakhont.link BaseFragmentLifecycleProceed.BaseFragmentCallbacks Fragment} and
     * {@link BaseCallbacks general Activity} ones):
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * package com.mypackage;
     *
     * public class MyCallbacks { // you can create new class or add handler(s) to the existing one
     *
     *     public static void myHandler(Service service, Intent intent, int flags, int startId) {
     *    
     *         // proceed annotated Services only
     *         if (!BaseCallbacks.proceed(service, MyCallbacks.class)) return;
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
     * &#064;CallbacksInherited(com.mypackage.MyCallbacks.class)
     * public class MyService extends Service {
     *     ...
     * }
     * </pre>
     *
     * And add the following line to the <code>weaver.config</code>:
     *
     * <pre style="background-color: silver; border: thin solid black;">
     * android.app.Service.onStartCommand  true  'com.mypackage.MyCallbacks.myHandler($0, $$);'
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
     * @param <T>
     *        The type of object for which callbacks should be proceeded
     *
     * @return  {@code true} if callbacks should be proceeded, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static <T> boolean proceed(@NonNull final T object, @NonNull final Class callbackClass) {
        return proceed(object, callbackClass, false, null);
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
    public static <T> boolean proceed(@NonNull final T object, @NonNull final Class callbackClass, final boolean forceProceed,
                                      final Map<T, Boolean> cache) {
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
            if (!isProceed(object, callbackClass)) {
                log("no Callbacks found", callbackClass);
                return false;
            }
            log("Callbacks found", callbackClass);
        }

        if (isReject(object, callbackClass)) {
            log("StopCallbacks found", callbackClass);
            return false;
        }

        log("checking is OK - proceeding", callbackClass);
        return true;
    }

    private static void log(@NonNull final String text, @NonNull final Class callbackClass) {
        CoreLogger.log(Level.DEBUG, String.format(FORMAT, text, callbackClass.getName()));
    }

    private static <T> boolean isProceed(@NonNull final T object, @NonNull final Class callbackClass) {

        Annotation annotation = CoreReflection.getAnnotation(object, CallbacksInherited.class);
        final Class<? extends BaseCallbacks>[] callbacksInherited
                = annotation == null ? null: ((CallbacksInherited) annotation).value();

        if (isFound(object, callbacksInherited, callbackClass)) return true;

        annotation = CoreReflection.getAnnotation(object, Callbacks.class);
        final Class<? extends BaseCallbacks>[] callbacksNotInherited
                = annotation == null ? null: ((Callbacks)          annotation).value();

        return isFound(object, callbacksNotInherited, callbackClass);
    }

    private static <T> boolean isReject(@NonNull final T object, @NonNull final Class callbackClass) {

        Annotation annotation = CoreReflection.getAnnotation(object, StopCallbacksInherited.class);
        final Class<? extends BaseCallbacks>[] stopCallbacksInherited
                = annotation == null ? null: ((StopCallbacksInherited) annotation).value();

        if (isFound(object, stopCallbacksInherited, callbackClass)) return true;

        annotation = CoreReflection.getAnnotation(object, StopCallbacks.class);
        final Class<? extends BaseCallbacks>[] stopCallbacksNotInherited
                = annotation == null ? null: ((StopCallbacks)          annotation).value();

        return isFound(object, stopCallbacksNotInherited, callbackClass);
    }

    private static <T> boolean isFound(@NonNull final T                       object,
                                       final Class<? extends BaseCallbacks>[] callbackClasses,
                                       @NonNull final Class                   callbackClass) {
        if (sValidator == null)
            CoreLogger.logWarning("sValidator == null");
        else
            sValidator.validate(object, callbackClasses);

        if (callbackClasses != null)
            for (final Class<? extends BaseCallbacks> tmpClass: callbackClasses)
                if (tmpClass.isAssignableFrom(callbackClass)) return true;

        return false;
    }

    /** The callback which is called when the instance of this class registered.   */ @SuppressWarnings("WeakerAccess")
    protected void onRegister  () {}
    /** The callback which is called when the instance of this class unregistered. */ @SuppressWarnings("WeakerAccess")
    protected void onUnregister() {}

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
        public BaseCacheCallbacks() {
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
        protected boolean proceed(@NonNull final T object) {
            return proceed(object, mAll);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The <code>BaseProceed</code> is intended for support of {@link BaseCallbacks} instances registration.
     * Most implementations should not use it directly, but instead call
     * {@link akha.yakhont.callback.lifecycle.BaseActivityLifecycleProceed#register BaseActivityLifecycleProceed.register} or
     * {@yakhont.link BaseFragmentLifecycleProceed#register(akha.yakhont.callback.lifecycle.BaseFragmentLifecycleProceed.BaseFragmentCallbacks) BaseFragmentLifecycleProceed.register}.
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
                        proceed = callbacks.proceed(object);
                        add(callbacks, proceed, object);
                    }
                    else
                        remove(callbacks, object);

                if (condition != null && !condition.call()) return false;

                // it's possible to be registered after onCreate
                if (!(isCreate != null &&  isCreate)) proceed = callbacks.proceed(object);
                if (!(isCreate != null && !isCreate)) add(callbacks, proceed, object);

                return proceed;
            }
            catch (Exception e) {
                CoreLogger.log("proceed failed", e);
                return false;
            }
        }

        private static <T> void add(@NonNull final BaseCacheCallbacks<T> callbacks, final boolean proceed, @NonNull final T object) {
            if (!callbacks.mAll.containsKey(object))                callbacks.mAll.put(object, proceed);
            if (proceed && !callbacks.mProceeded.contains(object))  callbacks.mProceeded.add(object);
        }

        private static <T> void remove(@NonNull final BaseCacheCallbacks<T> callbacks, @NonNull final T object) {
            if (callbacks.mAll.containsKey(object))                 callbacks.mAll.remove(object);
            if (callbacks.mProceeded.contains(object))              callbacks.mProceeded.remove(object);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Extends the {@link BaseProceed} class to provide the base object's lifecycle support.
     *
     * @yakhont.see BaseCallbacks
     * @yakhont.see BaseActivityLifecycleProceed
     * @yakhont.see BaseFragmentLifecycleProceed
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
                @NonNull final Class<E> type, @NonNull final Map<String, E> namesMap) {

            final Set<E> lifeCycles = getImplementedCallbacks(callbacks, type, namesMap);
            if (lifeCycles.size() == 0)
                CoreLogger.logWarning("no implemented callbacks found for " + callbacks.getClass().getName());

            return register(callbacksMap, callbacks, lifeCycles);
        }

        @NonNull
        private static <E extends Enum<E>, C extends BaseCacheCallbacks> Set<E> getImplementedCallbacks(
                @NonNull final C callbacks, @NonNull final Class<E> type, @NonNull final Map<String, E> namesMap) {

            final EnumSet<E> lifeCycles = EnumSet.noneOf(type);

            final Class callbacksClass = callbacks.getClass();

            Class tmpClass = callbacksClass;
            for (;;) {
                final Method[] methods = tmpClass.getDeclaredMethods();

                for (final Method method: methods) {
                    final E lifeCycle = namesMap.get(method.getName());
                    if (lifeCycle != null) lifeCycles.add(lifeCycle);
                }

                if ((tmpClass = tmpClass.getSuperclass()).equals(BaseCacheCallbacks.class)) break;
            }

            if (lifeCycles.size() > 0) {
                CoreLogger.log("implemented callbacks in " + callbacksClass.getName() + ":");
                for (final E lifeCycle: lifeCycles)
                    CoreLogger.log("  " + lifeCycle.name());
            }
            else
                CoreLogger.logError("no implemented callbacks found in " + callbacksClass.getName());

            return Collections.unmodifiableSet(lifeCycles);
        }

        /** @exclude */
        @SuppressWarnings({"JavaDoc", "WeakerAccess", "SameParameterValue"})
        public static <E extends Enum<E>, T> void apply(
                @NonNull final Map<? extends BaseCacheCallbacks<T>, Set<E>> callbacksMap, @NonNull final BaseCacheCallbacks<T> callbacks,
                final Boolean created, @NonNull final E lifeCycle, @NonNull final T object, @NonNull final Runnable runnable) {

            if (proceed(callbacks, created, object, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
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
