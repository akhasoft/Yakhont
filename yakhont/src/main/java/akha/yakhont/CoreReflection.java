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

package akha.yakhont;

import android.support.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The helper class for work with Java Reflection API.
 *
 * @author akha
 */
public class CoreReflection {

    private static final Map<Class, Class>                  UNBOXING;

    static {
        final Map<Class, Class> unboxing = new HashMap<>();

        unboxing.put(Boolean  .class,   boolean.class);

        unboxing.put(Character.class,   char   .class);

        unboxing.put(Byte     .class,   byte   .class);
        unboxing.put(Short    .class,   short  .class);
        unboxing.put(Integer  .class,   int    .class);
        unboxing.put(Long     .class,   long   .class);

        unboxing.put(Float    .class,   float  .class);
        unboxing.put(Double   .class,   double .class);

        UNBOXING = Collections.unmodifiableMap(unboxing);
    }

    private CoreReflection() {
    }

    private static Class getClass(@NonNull final Object object) {
        return object instanceof Class ? (Class) object: object.getClass();
    }

    private static Object getObject(@NonNull final Object object) {
        return object instanceof Class ? null: object;
    }

    private static void checkForNull(final Object object, @NonNull final String message) {
        if (object == null) throw new UnsupportedOperationException(message);
    }

    /**
     * Same as {@link #invoke(Object, String, Object...)} but never throws exceptions.
     */
    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    public static Object invokeSafe(@NonNull final Object object, @NonNull final String methodName, final Object... args) {
        try {
            return invoke(object, methodName, args);
        }
        catch (Exception e) {
            CoreLogger.log("invoke", e);
        }
        return null;
    }

    /**
     * Invokes method and returns the result.
     *
     * @param object
     *        The object on which to call this method
     *
     * @param methodName
     *        The method name
     *
     * @param args
     *        The method arguments
     *
     * @return  The result of method invoking
     *
     * @throws  InvocationTargetException
     *          please refer to the exception description
     */
    @SuppressWarnings("WeakerAccess")
    public static Object invoke(@NonNull final Object object, @NonNull final String methodName,
                                final Object... args) throws InvocationTargetException {

        final Class[] classes = new Class[args == null ? 0: args.length];
        for (int i = 0; i < classes.length; i++)
            //noinspection ConstantConditions
            classes[i] = args[i] == null ? null: args[i].getClass();

        return invoke(getObject(object), findMethod(object, methodName, classes), args);
    }

    /**
     * Invokes method and returns the result.
     *
     * @param object
     *        The object on which to call this method
     *
     * @param method
     *        The method
     *
     * @param args
     *        The method arguments
     *
     * @return  The result of method invoking
     *
     * @throws  InvocationTargetException
     *          please refer to the exception description
     */
    @SuppressWarnings("WeakerAccess")
    public static Object invoke(final Object object, final Method method,
                                final Object... args) throws InvocationTargetException {

        checkForNull(method, "method == null");

        CoreLogger.log("about to invoke method " + method.toGenericString());

        final boolean accessible = method.isAccessible();
        if (!accessible) {
            CoreLogger.logWarning("method is not accessible");
            method.setAccessible(true);
        }

        try {
            return method.invoke(object, args);
        }
        catch (IllegalAccessException    e) {          // should never happen
            CoreLogger.log(method.getName(), e);
        }
        finally {
            if (!accessible) //noinspection ThrowFromFinallyBlock
                method.setAccessible(false);
        }

        return null;
    }

    /**
     * Finds method to invoke.
     *
     * @param object
     *        The object on which to call this method
     *
     * @param methodName
     *        The method name
     *
     * @param args
     *        The method arguments
     *
     * @return  The method object or null (if not found)
     */
    @SuppressWarnings("WeakerAccess")
    public static Method findMethod(@NonNull final Object object, @NonNull final String methodName, @NonNull final Class... args) {
        Class tmpClass = getClass(object);

        for (;;) {
            final Method[] methods = tmpClass.getDeclaredMethods();

            for (final Method method: methods) {
                if (!methodName.equals(method.getName())) continue;

                final Class<?>[] params = method.getParameterTypes();
                if (args.length != params.length) continue;

                if (args.length == 0) return method;

                for (int i = 0; i < params.length; i++) {
                    Class currentClass = args[i];

                    if (params[i].isPrimitive()) {
                        if (currentClass == null) break;

                        if (UNBOXING.containsKey(currentClass)) currentClass = UNBOXING.get(currentClass);
                    }

                    if (currentClass != null && !params[i].isAssignableFrom(currentClass)) break;

                    if (i == params.length - 1) return method;
                }
            }

            if ((tmpClass = tmpClass.getSuperclass()) == null) break;
        }

        CoreLogger.logError("class " + getClass(object).getName() + ", method " + methodName + " not found");
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Finds field.
     *
     * @param object
     *        The object on which to find this field
     *
     * @param fieldName
     *        The field name
     *
     * @return  The field object or null (if not found)
     */
    @SuppressWarnings("WeakerAccess")
    public static Field findField(@NonNull final Object object, @NonNull final String fieldName) {
        Class tmpClass = getClass(object);

        for (;;) {
            try {
                return tmpClass.getDeclaredField(fieldName);
            }
            catch (NoSuchFieldException ignored) {
            }

            if ((tmpClass = tmpClass.getSuperclass()) == null) break;
        }

        CoreLogger.logError("class " + getClass(object).getName() + ", field " + fieldName + " not found");
        return null;
    }

    /**
     * Gets the value of the field.
     *
     * @param object
     *        The object on which to get this field
     *
     * @param fieldName
     *        The field name
     *
     * @return  The field value
     */
    @SuppressWarnings("unused")
    public static Object getField(@NonNull final Object object, @NonNull final String fieldName) {
        return doField(false, getClass(object), getObject(object), fieldName, null /* ignored */);
    }

    /**
     * Sets the value of the field.
     *
     * @param object
     *        The object on which to set this field
     *
     * @param fieldName
     *        The field name
     *
     * @param newValue
     *        The field's new value
     *
     * @return  The field's previous value
     */
    @SuppressWarnings("unused")
    public static Object setField(@NonNull final Object object, @NonNull final String fieldName, final Object newValue) {
        return doField(true, getClass(object), getObject(object), fieldName, newValue);
    }

    private static Object doField(final boolean set, @NonNull Class fieldClass, final Object object,
                                  @NonNull final String fieldName, final Object newValue) {

        final Field field = findField(fieldClass, fieldName);
        checkForNull(field, "field == null");

        final boolean accessible = field.isAccessible();
        field.setAccessible(true);

        try {
            final Object value = field.get(object);
            if (set)
                field.set(object, newValue);
            return value;
        }
        catch (IllegalAccessException e) {  // should never happen
            CoreLogger.log(fieldName, e);
        }
        finally {
            //noinspection ThrowFromFinallyBlock
            field.setAccessible(accessible);
        }

        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Gets the object's annotation.
     *
     * @param object
     *        The object on which to get this annotation
     *
     * @param annotation
     *        The annotation class
     *
     * @return  The annotation object or null (if not found)
     */
    public static Annotation getAnnotation(@NonNull final Object object, @NonNull final Class<? extends Annotation> annotation) {
        return ((Class<?>) getClass(object)).getAnnotation(annotation);
    }

    /**
     * Checks whether the object was annotated.
     *
     * @param object
     *        The object to check
     *
     * @param annotation
     *        The annotation class
     *
     * @return  {@code true} if the object was annotated, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean isAnnotated(@NonNull final Object object, @NonNull final Class<? extends Annotation> annotation) {
        return ((Class<?>) getClass(object)).isAnnotationPresent(annotation);
    }

    /**
     * Checks whether the method was annotated.
     *
     * @param object
     *        The object to check
     *
     * @param annotation
     *        The annotation class
     *
     * @param methodName
     *        The method name
     *
     * @param args
     *        The method arguments
     *
     * @return  {@code true} if the method was annotated, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean isAnnotatedMethod(@NonNull final Object object, @NonNull final Class<? extends Annotation> annotation,
                                            @NonNull final String methodName, @NonNull final Class... args) {
        final Method method = findMethod(object, methodName, args);
        checkForNull(method, "method == null");

        return method.isAnnotationPresent(annotation);
    }

    /**
     * Checks whether the field was annotated.
     *
     * @param object
     *        The object to check
     *
     * @param annotation
     *        The annotation class
     *
     * @param fieldName
     *        The field name
     *
     * @return  {@code true} if the field was annotated, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean isAnnotatedField(@NonNull final Object object, @NonNull final Class<? extends Annotation> annotation,
                                           @NonNull final String fieldName) {
        final Field field = findField(object, fieldName);
        checkForNull(field, "field == null");

        return field.isAnnotationPresent(annotation);
    }
}
