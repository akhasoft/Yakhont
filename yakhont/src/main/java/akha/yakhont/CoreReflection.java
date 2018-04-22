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

package akha.yakhont;

import akha.yakhont.CoreLogger.Level;

import android.support.annotation.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    public static <T> T invokeSafe(@NonNull final Object object, @NonNull final String methodName, final Object... args) {
        try {
            return invoke(object, methodName, args);
        }
        catch (Exception e) {
            CoreLogger.log("invoke", e);
            return null;
        }
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
     * @param <T>
     *        The type of data to return
     *
     * @return  The result of method invoking
     *
     * @throws  InvocationTargetException
     *          please refer to the exception description
     *
     * @throws  IllegalAccessException
     *          please refer to the exception description
     */
    @SuppressWarnings("WeakerAccess")
    public static <T> T invoke(@NonNull final Object object, @NonNull final String methodName, final Object... args)
            throws InvocationTargetException, IllegalAccessException {
        final Class[] classes = new Class[args == null ? 0: args.length];
        for (int i = 0; i < classes.length; i++)
            //noinspection ConstantConditions
            classes[i] = args[i] == null ? null: args[i].getClass();

        final Method method = findMethod(Level.ERROR, object, methodName, classes);
        //noinspection RedundantTypeArguments
        return method == null ? null: CoreReflection.<T>invoke(getObject(object), method, args);
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
     * @param <T>
     *        The type of data to return
     *
     * @return  The result of method invoking
     *
     * @throws  InvocationTargetException
     *          please refer to the exception description
     *
     * @throws  IllegalAccessException
     *          please refer to the exception description
     */
    @SuppressWarnings("WeakerAccess")
    public static <T> T invoke(final Object object, final Method method, final Object... args)
            throws InvocationTargetException, IllegalAccessException {
        checkForNull(method, "method == null");

        CoreLogger.log("about to invoke method " + method.toGenericString());

        final boolean accessible = method.isAccessible();
        if (!accessible) {
            CoreLogger.logWarning("method is not accessible");
            method.setAccessible(true);
        }

        try {
            @SuppressWarnings("unchecked")
            final T result = (T) method.invoke(object, args);
            return result;
        }
        catch (ClassCastException | IllegalAccessException exception) {
            CoreLogger.log(method.getName(), exception);
            throw exception;
        }
        finally {
            if (!accessible) //noinspection ThrowFromFinallyBlock
                method.setAccessible(false);
        }
    }

    /**
     * Same as {@link #invoke(Object, Method, Object...)} but never throws exceptions.
     */
    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue", "unused"})
    public static <T> T invokeSafe(@NonNull final Object object, @NonNull final Method method, final Object... args) {
        try {
            return invoke(object, method, args);
        }
        catch (Exception e) {
            CoreLogger.log("invoke", e);
            return null;
        }
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
        return findMethod(Level.WARNING, object, methodName, args);
    }

    private static Method findMethod(Level level, @NonNull final Object object, @NonNull final String methodName, @NonNull final Class... args) {
        Class<?> tmpClass = getClass(object);
        try {
            return tmpClass.getMethod(methodName, args);
        }
        catch (NoSuchMethodException e) {
            CoreLogger.log(Level.DEBUG, "Class.getMethod('" + methodName + "') failed", e);
        }

        for (;;) {
            final Method[] methods = tmpClass.getDeclaredMethods();

            for (final Method method: methods) {
                if (!methodName.equals(method.getName())) continue;

                final Class<?>[] params = method.getParameterTypes();
                if (args.length != params.length) continue;

                if (args.length == 0) return method;

                for (int i = 0; i < params.length; i++) {
                    Class<?> currentClass = args[i];

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

        CoreLogger.log(level,"class " + getClass(object).getName() + ", method " + methodName + " not found");
        return null;
    }

    /**
     * Finds list of the overridden methods.
     *
     * @param derivedClass
     *        The class which may override methods of the 'baseClass'
     *
     * @param baseClass
     *        The class which methods could be overridden by the 'derivedClass'
     *
     * @return  The list of the overridden methods
     */
    @NonNull
    public static List<Method> findOverriddenMethods(final Class<?> derivedClass, final Class<?> baseClass) {
        final List<Method> methods = new ArrayList<>();
        if (derivedClass == null) {
            CoreLogger.logError("derived class == null");
            return methods;
        }
        if (baseClass == null) {
            CoreLogger.logError("super class == null");
            return methods;
        }
        if (derivedClass.equals(baseClass)) {
            CoreLogger.logWarning("derived class and super class are the same: " + derivedClass.getName());
            return methods;
        }
        if (!baseClass.isAssignableFrom(derivedClass)) {
            CoreLogger.logError("class " + derivedClass.getName() + " is not derived from " +
                    baseClass.getName());
            return methods;
        }
        if (baseClass.equals(Object.class))
            CoreLogger.logWarning("about to find overridden methods of java.lang.Object: " + derivedClass.getName());

        final List<Method> derivedMethods = findMethods(derivedClass, baseClass,
                true, true, false, true,
                false, true);
        final List<Method>    baseMethods = findMethods(baseClass, null,
                true, true, false, true,
                false, true);

        for (final Method derivedMethod: derivedMethods) {
            final int modifiers = derivedMethod.getModifiers();
            if (Modifier.isStatic  (modifiers) ||
                Modifier.isAbstract(modifiers)) continue;

            for (final Method baseMethod: baseMethods) {
                final int baseModifiers = baseMethod.getModifiers();
                if (Modifier.isStatic(baseModifiers) ||
                    Modifier.isFinal (baseModifiers)) continue;

                final Boolean check = methodsEquals(baseMethod, derivedMethod);
                if (check != null && check) {
                    methods.add(derivedMethod);
                    break;
                }
            }
        }
        return methods;
    }

    /**
     * Compares methods by name, parameters and return type.
     *
     * @param method1
     *        The 1st method to compare
     *
     * @param method2
     *        The 2nd method to compare
     *
     * @return  {@code true} if name, parameters and return type are the same,
     *          {@code false} if name or parameters are not the same,
     *          {@code null} if name and parameters are the same, but return types are not
     *          (it's not possible in source code - but possible on the JVM level)
     */
    @SuppressWarnings("WeakerAccess")
    public static Boolean methodsEquals(final Method method1, final Method method2) {
        if (method1 == null)
            CoreLogger.logError("method1 == null");
        else if (method2 == null)
            CoreLogger.logError("method2 == null");
        else {
            final String name1 = method1.getName();
            final String name2 = method2.getName();
            if (!name1.equals(name2)) return false;

            final Class<?>[] params1 = method1.getParameterTypes();
            final Class<?>[] params2 = method2.getParameterTypes();
            if (params1.length != params2.length) return false;

            for (int i = 0; i < params1.length; i++)
                if (!params1[i].equals(params2[i])) return false;

            final Class<?> return1 = method1.getReturnType();
            final Class<?> return2 = method2.getReturnType();
            if (return1.equals(return2)) return true;

            CoreLogger.logWarning("return types of methods " + name1 + " are not the same: " +
                    return1.getName() + " and " + return2.getName());
            return null;
        }
        return false;
    }

    /**
     * Checks the scope of the method.
     *
     * @param method
     *        The method to check
     *
     * @return  {@code true} if the method has "package-private" (default) scope, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean isPackagePrivate(final Method method) {
        if (method == null) {
            CoreLogger.logError("method == null");
            return false;
        }
        final int modifiers = method.getModifiers();
        return !Modifier.isPublic (modifiers) && !Modifier.isProtected(modifiers) &&
               !Modifier.isPrivate(modifiers);
    }

    /**
     * Finds list of methods of the class, based on selection flags (see below).
     *
     * @param methodsClass
     *        The class which methods should be retrieved
     *
     * @param stopClass
     *        The super class(es) methods are retrieved too; specifies at which (if any) of super class
     *        algorithm should stop retrieving methods
     *
     * @param includePublic
     *        {@code true} to include public methods, {@code false} otherwise
     *
     * @param includeProtected
     *        {@code true} to include protected methods, {@code false} otherwise
     *
     * @param includePrivate
     *        {@code true} to include private methods, {@code false} otherwise
     *
     * @param includePackage
     *        {@code true} to include package-private methods, {@code false} otherwise
     *
     * @param includeSynthetic
     *        {@code true} to include synthetic methods only, {@code false} to exclude them,
     *        {@code null} to include all (no check for 'synthetic' flag)
     *
     * @param includeBridge
     *        {@code true} to include bridge methods only, {@code false} to exclude them,
     *        {@code null} to include all (no check for 'bridge' flag);
     *        if set, 'includeBridge' has higher priority than 'includeSynthetic'
     *
     * @return  The list of selected methods of the class
     */
    @SuppressWarnings({"ConstantConditions" /* lint complains: method too complex to analyze :-) */, "WeakerAccess", "SameParameterValue"})
    @NonNull
    public static List<Method> findMethods(final Class<?> methodsClass, final Class<?> stopClass,
                                           final boolean includePublic,    final boolean includeProtected,
                                           final boolean includePrivate,   final boolean includePackage,
                                           final Boolean includeSynthetic, final Boolean includeBridge) {
        final List<Method> methods = new ArrayList<>();
        if (methodsClass == null) {
            CoreLogger.logError("class == null");
            return methods;
        }
        Class tmpClass = methodsClass;
        for (;;) {
            final Method[] tmpMethods = tmpClass.getDeclaredMethods();
            for (final Method method: tmpMethods) {
                final int modifiers = method.getModifiers();

                if (!includePublic    && Modifier.isPublic   (modifiers)) continue;
                if (!includeProtected && Modifier.isProtected(modifiers)) continue;
                if (!includePrivate   && Modifier.isPrivate  (modifiers)) continue;
                if (!includePackage   && isPackagePrivate    (method))    continue;

                if (includeSynthetic == null) {
                    if (skipBridge(includeBridge, method)) continue;
                }
                else if (includeSynthetic) {
                    if (!method.isSynthetic())             continue;
                    if (skipBridge(includeBridge, method)) continue;
                }
                else if (method.isSynthetic()) {
                    if (includeBridge != null && includeBridge && method.isBridge())
                        CoreLogger.logWarning("although synthetic methods should be excluded, " +
                                "we include bridge method " + method.getName());
                    else
                        continue;
                }

                methods.add(method);
            }
            if (methodsClass.equals(stopClass)) break;

            tmpClass = tmpClass.getSuperclass();
            if (tmpClass == null || tmpClass.equals(stopClass)) break;
        }
        return methods;
    }

    private static boolean skipBridge(final Boolean includeBridge, final Method method) {
        return includeBridge != null && includeBridge != method.isBridge();
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

        CoreLogger.logWarning("class " + getClass(object).getName() + ", field " + fieldName + " not found");
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
     * @param <T>
     *        The type of data to return
     *
     * @return  The field value
     */
    @SuppressWarnings("unused")
    public static <T> T getField(@NonNull final Object object, @NonNull final String fieldName) {
        final Field field = findField(object, fieldName);
        //noinspection RedundantTypeArguments
        return field == null ? null: CoreReflection.<T>getField(object, field);
    }

    /**
     * Gets the value of the field.
     *
     * @param object
     *        The object on which to get this field
     *
     * @param field
     *        The field
     *
     * @param <T>
     *        The type of data to return
     *
     * @return  The field value
     */
    @SuppressWarnings("unused")
    public static <T> T getField(@NonNull final Object object, final Field field) {
        return doField(false, getObject(object), field, null /* ignored */);
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
     * @param <T>
     *        The type of data to return
     *
     * @return  The field's previous value
     */
    @SuppressWarnings("unused")
    public static <T> T setField(@NonNull final Object object, @NonNull final String fieldName, final T newValue) {
        final Field field = findField(object, fieldName);
        return field == null ? null: setField(object, field, newValue);
    }

    /**
     * Sets the value of the field.
     *
     * @param object
     *        The object on which to set this field
     *
     * @param field
     *        The field
     *
     * @param newValue
     *        The field's new value
     *
     * @param <T>
     *        The type of data to return
     *
     * @return  The field's previous value
     */
    @SuppressWarnings("unused")
    public static <T> T setField(@NonNull final Object object, final Field field, final T newValue) {
        return doField(true, getObject(object), field, newValue);
    }

    private static <T> T doField(final boolean set, final Object object, final Field field, final T newValue) {
        checkForNull(field, "field == null");

        final boolean accessible = field.isAccessible();
        field.setAccessible(true);

        try {
            @SuppressWarnings("unchecked")
            final T value = (T) field.get(object);

            if (set) field.set(object, newValue);
            return value;
        }
        catch (ClassCastException | IllegalAccessException e) {
            CoreLogger.log(field.getName(), e);
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
        final Method method = findMethod(Level.ERROR, object, methodName, args);
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
