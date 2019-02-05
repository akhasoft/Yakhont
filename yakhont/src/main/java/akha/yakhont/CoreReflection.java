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

import akha.yakhont.Core.Utils;
import akha.yakhont.CoreLogger.Level;

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        catch (Exception exception) {
            CoreLogger.log(exception);
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

        CoreLogger.log(String.format("about to invoke method %s on object %s",
                method.toGenericString(), CoreLogger.getDescription(object)));

        try {
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
            catch (IllegalAccessException exception) {
                CoreLogger.log("failed invoke " + method.getName(), exception);
                throw exception;
            }
            finally {
                if (!accessible) method.setAccessible(false);
            }
        }
        catch (RuntimeException exception) {
            CoreLogger.log("failed invoke " + method.getName(), exception);
            return null;
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
        catch (Exception exception) {
            CoreLogger.log(exception);
            return null;
        }
    }

    /**
     * Finds method to invoke.
     *
     * @param object
     *        The object (or object's class) on which to find method
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
    public static Method findMethod(@NonNull final Object object,
                                    @NonNull final String methodName, @NonNull final Class... args) {
        return findMethod(Level.WARNING, object, methodName, args);
    }

    private static Method findMethod(Level level, @NonNull final Object object,
                                     @NonNull final String methodName, @NonNull final Class... args) {
        Class<?> tmpClass = getClass(object);
        try {
            return tmpClass.getMethod(methodName, args);
        }
        catch (/*NoSuchMethod*/Exception exception) {
            CoreLogger.log(CoreLogger.getDefaultLevel(), "Class.getMethod('" + methodName +
                    "') failed", exception);
        }

        //noinspection ConditionalBreakInInfiniteLoop
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

        CoreLogger.log(level,"class " + CoreLogger.getDescription(getClass(object)) +
                ", method " + methodName + " not found");
        return null;
    }

    /**
     * Checks if parameter is array or {@link Collection}.
     *
     * @param object
     *        The object (or object's class) on which to check
     *
     * @return  {@code true} if parameter is a container for other objects, {@code false} otherwise
     */
    public static boolean isNotSingle(@NonNull final Object object) {
        final Class<?> cls = getClass(object);

        // should be consistent with getObjects(Object)
        return cls.isArray() || Collection.class.isAssignableFrom(cls);
    }

    /**
     * Returns the size of parameter (if it's an array or {@link Collection}).
     *
     * @param object
     *        The object
     *
     * @return  the object's size
     */
    @SuppressWarnings("WeakerAccess")
    public static int getSize(@NonNull final Object object) {
        final Class<?> cls = getClass(object);

        if (cls.isArray())                          return Array.getLength(object);
        if (Collection.class.isAssignableFrom(cls)) return ((Collection) object).size();

        CoreLogger.logError("failed getSize() for class " + CoreLogger.getDescription(cls));
        return 0;
    }

    /**
     * Returns contained objects if parameter is array or {@link Collection} (null otherwise).
     *
     * @param object
     *        The object
     *
     * @return  The list of objects (or null)
     */
    public static List<Object> getObjects(final Object object) {
        if (object == null) {
            CoreLogger.logWarning("getObjects(): parameter is null");
            return null;
        }
        final Class<?>  cls = object.getClass();    // not getClass(object)
        List<Object> result = null;

        // should be consistent with isSingle(Object)
        try {
            if (cls.isArray()) {
                result = new ArrayList<>();
                for (int i = 0; i < Array.getLength(object); i++)
                    result.add(Array.get(object, i));
            }
            else if (Collection.class.isAssignableFrom(cls))
                result = new ArrayList<>((Collection<?>) object);
        }
        catch (Exception exception) {
            CoreLogger.log("failed getObjects() for class " + CoreLogger.getDescription(cls), exception);
            return null;
        }

        if (result == null)
            CoreLogger.logWarning("neither array nor Collection: " + CoreLogger.getDescription(cls));
        return result;
    }

    /**
     * Returns contained object at given position if parameter is array or {@link List} (null otherwise).
     *
     * @param object
     *        The object
     *
     * @param position
     *        The position
     *
     * @return  The object at given position (or null)
     */
    public static Object getObject(final Object object, final int position) {
        if (object == null) {
            CoreLogger.logWarning("getObject(): object is null");
            return null;
        }
        if (position < 0) {
            CoreLogger.logError("wrong position " + position);
            return null;
        }
        final Class<?> cls = object.getClass();     // not getClass(object)

        try {
            if (cls.isArray()) {
                if (checkSize(Array.getLength(object), position, "array"))
                    return Array.get(object, position);
            }
            else if (List.class.isAssignableFrom(cls)) {
                if (checkSize(((List<?>) object).size(), position, "List"))
                    return ((List<?>) object).get(position);
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                     android.util.ArraySet.class.isAssignableFrom(cls)) {
                if (checkSize(((android.util.ArraySet<?>) object).size(), position, "ArraySet"))
                    return ((android.util.ArraySet<?>) object).valueAt(position);
            }
            else if (ArraySet.class.isAssignableFrom(cls)) {
                if (checkSize(((ArraySet<?>) object).size(), position, "ArraySet (support library)"))
                    return ((ArraySet<?>) object).valueAt(position);
            }
        }
        catch (Exception exception) {
            CoreLogger.log("failed getObject() for class " + CoreLogger.getDescription(cls), exception);
            return null;
        }

        CoreLogger.logError("unknown collection: " + CoreLogger.getDescription(cls));
        return null;
    }

    private static boolean checkSize(final int size, final int position, @NonNull final String info) {
        final boolean nOk = position < 0 || position >= size;
        if (nOk)
            CoreLogger.logError(String.format(Utils.getLocale(),
                    "wrong position %d, %s length is %d", position, info, size));
        return !nOk;
    }

    /**
     * Merges data (if parameters are arrays or Collections).
     *
     * @param object1
     *        The array or {@link Collection}
     *
     * @param object2
     *        The array or {@link Collection}
     *
     * @return  The merged data (or null)
     */
    @SuppressWarnings("unchecked")
    public static Object mergeObjects(final Object object1, final Object object2) {
        if (object1 == null) return object2;
        if (object2 == null) return object1;

        if (!isNotSingle(object1))
            if (!isNotSingle(object2)) {
                CoreLogger.logWarning("about to merge single objects: " + object1.getClass() +
                        ", " + object2.getClass());
                final Object tmp = Array.newInstance(object1.getClass(), 1);
                Array.set(tmp, 0, object1);
                return mergeObjects(tmp, object2);
            }
            else
                return mergeObjects(object2, object1);

        int size2 = getSize(object2);

        // not getClass(object)
        final Class<?> cls1 = object1.getClass();
        final Class<?> cls2 = object2.getClass();

        if (Collection.class.isAssignableFrom(cls1)) {

            if (Collection.class.isAssignableFrom(cls2))  // @SuppressWarnings("unchecked")
                ((Collection) object1).addAll((Collection) object2);

            else if (cls2.isArray())
                for (int i = 0; i < size2; i++)           // @SuppressWarnings("unchecked")
                    ((Collection) object1).add(Array.get(object2, i));

            else
                ((Collection) object1).add(object2);

            return object1;
        }

        if (cls1.isArray()) {
            if (!isNotSingle(object2)) size2 = 1;

            final int     size1 = Array.getLength(object1);
            final Object result = Array.newInstance(cls1.getComponentType(), size1 + size2);

            for (int i = 0; i < size1; i++)
                Array.set(result, i, Array.get(object1, i));

            if (Collection.class.isAssignableFrom(cls2)) {
                int i = size1;
                final Iterator iterator = ((Collection) object2).iterator();
                //noinspection WhileLoopReplaceableByForEach
                while (iterator.hasNext())
                    Array.set(result, i++, iterator.next());
            }
            else if (cls2.isArray())
                for (int i = 0; i < size2; i++)
                    Array.set(result, i + size1, Array.get(object2, i));
            else
                Array.set(result, size1, object2);

            return result;
        }

        // should never happen
        CoreLogger.logError("can't merge objects: " + cls1 + ", " + cls2);
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
            CoreLogger.logWarning("derived class and super class are the same: " + CoreLogger.getDescription(derivedClass));
            return methods;
        }
        if (!baseClass.isAssignableFrom(derivedClass)) {
            CoreLogger.logError("class " + CoreLogger.getDescription(derivedClass) +
                    " is not derived from " + CoreLogger.getDescription(baseClass));
            return methods;
        }
        if (baseClass.equals(Object.class))
            CoreLogger.logWarning("about to find overridden methods of java.lang.Object: " +
                    CoreLogger.getDescription(derivedClass));

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

                final Boolean check = equalsMethods(baseMethod, derivedMethod);
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
    public static Boolean equalsMethods(final Method method1, final Method method2) {
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
                    CoreLogger.getDescription(return1) + " and " + CoreLogger.getDescription(return2));
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
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static boolean isPackagePrivate(final Method method) {
        if (method == null) {
            CoreLogger.logError("method == null");
            return false;
        }
        return isPackagePrivate(method.getModifiers());
    }

    /**
     * Checks the scope of the modifiers (field / method / etc).
     *
     * @param modifiers
     *        The modifiers to check
     *
     * @return  {@code true} if the modifiers has "package-private" (default) scope, {@code false} otherwise
     */
    @SuppressWarnings("WeakerAccess")
    public static boolean isPackagePrivate(final int modifiers) {
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
    public static List<Method> findMethods(final Class<?> methodsClass,     final Class<?> stopClass,
                                           final boolean  includePublic,    final boolean  includeProtected,
                                           final boolean  includePrivate,   final boolean  includePackage,
                                           final Boolean  includeSynthetic, final Boolean  includeBridge) {
        final List<Method> methods = new ArrayList<>();
        if (methodsClass == null) {
            CoreLogger.logError("class == null");
            return methods;
        }

        Class tmpClass = methodsClass;
        for (;;) {
            final Method[] tmpMethods = tmpClass.getDeclaredMethods();
            for (final Method method: tmpMethods) {
                if (!checkModifiers(method.getModifiers(), includePublic, includeProtected,
                        includePrivate, includePackage))   continue;

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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean checkModifiers(final int modifiers,
                                          final boolean includePublic, final boolean includeProtected,
                                          final boolean includePrivate, final boolean includePackage) {
        if (!includePublic    && Modifier.isPublic   (modifiers)) return false;
        if (!includeProtected && Modifier.isProtected(modifiers)) return false;
        if (!includePrivate   && Modifier.isPrivate  (modifiers)) return false;
        //noinspection RedundantIfStatement
        if (!includePackage   && isPackagePrivate    (modifiers)) return false;
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Finds field.
     *
     * @param object
     *        The object (or object's class) on which to find this field
     *
     * @param fieldName
     *        The field name
     *
     * @return  The field object or null (if not found)
     */
    @SuppressWarnings("WeakerAccess")
    public static Field findField(@NonNull final Object object, @NonNull final String fieldName) {
        Class tmpClass = getClass(object);

        //noinspection ConditionalBreakInInfiniteLoop
        for (;;) {
            try {
                return tmpClass.getDeclaredField(fieldName);
            }
            catch (NoSuchFieldException ignored) {
            }

            if ((tmpClass = tmpClass.getSuperclass()) == null) break;
        }

        CoreLogger.logWarning("class " + CoreLogger.getDescription(getClass(object)) +
                ", field " + fieldName + " not found");
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
    public static <T> T setField(@NonNull final Object object, @NonNull final String fieldName,
                                 final T newValue) {
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

        try {
            final boolean accessible = field.isAccessible();
            if (!accessible) field.setAccessible(true);

            try {
                @SuppressWarnings("unchecked")
                final T value = (T) field.get(object);

                if (set) field.set(object, newValue);
                return value;
            }
            catch (IllegalAccessException exception) {
                CoreLogger.log("failed doField " + field.getName(), exception);
            }
            finally {
                if (!accessible) field.setAccessible(false);
            }
        }
        catch (RuntimeException exception) {
            CoreLogger.log("failed doField " + field.getName(), exception);
        }

        return null;
    }

    /**
     * Returns object's fields as a map (name / value). Hidden fields are prefixed with the class name.
     *
     * @param object
     *        The object on which to get fields
     *
     * @param stopClass
     *        The super class(es) fields are retrieved too; specifies at which (if any) of super class
     *        algorithm should stop retrieving fields
     *
     * @param prefixWithClass
     *        {@code true} to prefix field with the class name, {@code false} otherwise
     *
     * @param includeHidden
     *        {@code true} to include hidden fields, {@code false} otherwise
     *
     * @param includePublic
     *        {@code true} to include public fields, {@code false} otherwise
     *
     * @param includeProtected
     *        {@code true} to include protected fields, {@code false} otherwise
     *
     * @param includePrivate
     *        {@code true} to include private fields, {@code false} otherwise
     *
     * @param includePackage
     *        {@code true} to include package-private fields, {@code false} otherwise
     *
     * @param includeStatic
     *        {@code true} to include static fields only, {@code false} to exclude them,
     *        {@code null} to include all (no check for 'static' flag)
     *
     * @param includeSynthetic
     *        {@code true} to include synthetic fields only, {@code false} to exclude them,
     *        {@code null} to include all (no check for 'synthetic' flag)
     *
     * @return  The object's fields as a map
     */
    public static Map<String, Object> getFields(@NonNull final Object object,  final Class<?> stopClass,
                                                final boolean prefixWithClass, final boolean includeHidden,
                                                final boolean includePublic,   final boolean includeProtected,
                                                final boolean includePrivate,  final boolean includePackage,
                                                final Boolean includeStatic,   final Boolean includeSynthetic) {
        final Map<String, Object> map = Utils.newMap();

        final Collection<Field> fields = getFields(object, stopClass, includeHidden,
                includePublic, includeProtected, includePrivate, includePackage,
                includeStatic, includeSynthetic);

        for (final Field field: fields) {
            final String key   = field.getName();
            final Object value = getField(object, field);

            if (map.containsKey(key))
                map.put(adjustFieldName(key, field.getDeclaringClass()), value);
            else
                map.put(prefixWithClass && key.indexOf('.') < 0 ?
                        adjustFieldName(key, field.getDeclaringClass()): key, value);
        }

        return map;
    }

    /**
     * Returns object's fields as a collection.
     *
     * @param object
     *        The object on which to get fields
     *
     * @param stopClass
     *        The super class(es) fields are retrieved too; specifies at which (if any) of super class
     *        algorithm should stop retrieving fields
     *
     * @param includeHidden
     *        {@code true} to include hidden fields, {@code false} otherwise
     *
     * @param includePublic
     *        {@code true} to include public fields, {@code false} otherwise
     *
     * @param includeProtected
     *        {@code true} to include protected fields, {@code false} otherwise
     *
     * @param includePrivate
     *        {@code true} to include private fields, {@code false} otherwise
     *
     * @param includePackage
     *        {@code true} to include package-private fields, {@code false} otherwise
     *
     * @param includeStatic
     *        {@code true} to include static fields only, {@code false} to exclude them,
     *        {@code null} to include all (no check for 'static' flag)
     *
     * @param includeSynthetic
     *        {@code true} to include synthetic fields only, {@code false} to exclude them,
     *        {@code null} to include all (no check for 'synthetic' flag)
     *
     * @return  The object's fields as a collection
     */
    @SuppressWarnings("WeakerAccess")
    public static Collection<Field> getFields(@NonNull final Object object, final Class<?> stopClass,
                                              final boolean includeHidden,
                                              final boolean includePublic, final boolean includeProtected,
                                              final boolean includePrivate, final boolean includePackage,
                                              final Boolean includeStatic, final Boolean includeSynthetic) {
        final Map<String, Field > map = Utils.newMap();

        final Class fieldsClass = object.getClass();    // not getClass(object)
        Class tmpClass = fieldsClass;
        for (;;) {
            final Field[] fields = tmpClass.getDeclaredFields();
            for (final Field field: fields) {
                final int modifiers = field.getModifiers();
                if (!checkModifiers(modifiers, includePublic, includeProtected,
                        includePrivate, includePackage))    continue;

                if (includeStatic != null) {
                    final boolean isStatic = Modifier.isStatic(modifiers);
                    if (!includeStatic &&  isStatic ||
                         includeStatic && !isStatic)        continue;
                }
                if (includeSynthetic != null) {
                    final boolean isSynthetic = field.isSynthetic();
                    if (!includeSynthetic &&  isSynthetic ||
                         includeSynthetic && !isSynthetic)  continue;
                }

                String key = field.getName();
                if (map.containsKey(key)) {
                    if (!includeHidden) continue;
                    key = adjustFieldName(key, tmpClass);
                }
                map.put(key, field);
            }

            if (fieldsClass.equals(stopClass)) break;

            tmpClass = tmpClass.getSuperclass();
            if (tmpClass == null || tmpClass.equals(stopClass)) break;
        }

        final Set<Field> set = Utils.newSet();
        set.addAll(map.values());
        return set;
    }

    private static String adjustFieldName(@NonNull final String name, @NonNull final Class cls) {
        return String.format("%s.%s", cls.getName(), name);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Gets the object's annotation.
     *
     * @param object
     *        The object (or object's class) on which to get this annotation
     *
     * @param annotation
     *        The annotation class
     *
     * @return  The annotation object or null (if not found)
     */
    public static Annotation getAnnotation(@NonNull final Object object,
                                           @NonNull final Class<? extends Annotation> annotation) {
        return ((Class<?>) getClass(object)).getAnnotation(annotation);
    }

    /**
     * Checks whether the object was annotated.
     *
     * @param object
     *        The object (or object's class) to check
     *
     * @param annotation
     *        The annotation class
     *
     * @return  {@code true} if the object was annotated, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public static boolean isAnnotated(@NonNull final Object object,
                                      @NonNull final Class<? extends Annotation> annotation) {
        return ((Class<?>) getClass(object)).isAnnotationPresent(annotation);
    }

    /**
     * Checks whether the method was annotated.
     *
     * @param object
     *        The object (or object's class) to check
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
    public static boolean isAnnotatedMethod(@NonNull final Object object,
                                            @NonNull final Class<? extends Annotation> annotation,
                                            @NonNull final String methodName, @NonNull final Class... args) {
        final Method method = findMethod(Level.ERROR, object, methodName, args);
        checkForNull(method, "method == null");

        return method.isAnnotationPresent(annotation);
    }

    /**
     * Checks whether the field was annotated.
     *
     * @param object
     *        The object (or object's class) to check
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
