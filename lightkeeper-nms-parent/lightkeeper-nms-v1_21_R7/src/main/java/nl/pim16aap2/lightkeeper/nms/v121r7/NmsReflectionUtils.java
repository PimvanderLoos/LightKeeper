package nl.pim16aap2.lightkeeper.nms.v121r7;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * Generic reflection utilities shared by NMS adapter code.
 *
 * <p>All methods are pure reflection helpers with no dependency on any Minecraft, Bukkit, or Spigot
 * internals. They are kept package-private so that only the v1_21_R7 adapter module can use them.
 */
final class NmsReflectionUtils
{
    private NmsReflectionUtils()
    {
    }

    /**
     * Loads a class by name, preferring the supplied class loader and falling back to the system class loader.
     *
     * @param className
     *     Fully qualified class name.
     * @param classLoader
     *     Preferred class loader.
     * @return
     *     Resolved class.
     * @throws ClassNotFoundException
     *     When the class cannot be found in either loader.
     */
    static Class<?> resolveClass(String className, ClassLoader classLoader)
        throws ClassNotFoundException
    {
        try
        {
            return Class.forName(className, false, classLoader);
        }
        catch (ClassNotFoundException ignored)
        {
            return Class.forName(className);
        }
    }

    /**
     * Tries to load each candidate class name in order and returns the first one found.
     *
     * @param classLoader
     *     Preferred class loader passed to {@link #resolveClass}.
     * @param classNames
     *     Candidate fully qualified class names.
     * @return
     *     The first resolvable class.
     * @throws ClassNotFoundException
     *     When none of the candidates can be resolved.
     */
    static Class<?> resolveFirstClass(ClassLoader classLoader, String... classNames)
        throws ClassNotFoundException
    {
        ClassNotFoundException lastException = null;
        for (final String className : classNames)
        {
            try
            {
                return resolveClass(className, classLoader);
            }
            catch (ClassNotFoundException exception)
            {
                lastException = exception;
            }
        }
        throw Objects.requireNonNullElseGet(
            lastException,
            () -> new ClassNotFoundException("No candidate class names were provided.")
        );
    }

    /**
     * Finds a static factory method on {@code ownerClass} (or any of its superclasses) whose return type is
     * assignable to {@code returnTypeClass} and whose parameter types match {@code parameterTypes}.
     *
     * @param ownerClass
     *     Class to search (including superclass chain).
     * @param returnTypeClass
     *     Expected return type (supertype match accepted).
     * @param parameterTypes
     *     Expected parameter types (supertype match accepted per parameter).
     * @return
     *     Found method with accessibility set.
     * @throws NoSuchMethodException
     *     When no matching static factory method exists.
     */
    static Method resolveStaticFactoryMethod(
        Class<?> ownerClass,
        Class<?> returnTypeClass,
        Class<?>... parameterTypes
    )
        throws NoSuchMethodException
    {
        for (Class<?> cursor = ownerClass; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (!Modifier.isStatic(method.getModifiers()))
                    continue;
                if (!returnTypeClass.isAssignableFrom(method.getReturnType()))
                    continue;
                final Class<?>[] methodParameterTypes = method.getParameterTypes();
                if (methodParameterTypes.length != parameterTypes.length)
                    continue;

                boolean compatible = true;
                for (int idx = 0; idx < methodParameterTypes.length; ++idx)
                {
                    if (!methodParameterTypes[idx].isAssignableFrom(parameterTypes[idx]))
                    {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible)
                    continue;

                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
            "Failed to resolve static factory method on "
                + ownerClass.getName()
                + " for return type "
                + returnTypeClass.getName()
        );
    }

    /**
     * Finds a static no-argument factory method on {@code ownerClass} whose return type is
     * assignable to {@code returnTypeClass}.
     *
     * @see #resolveStaticFactoryMethod(Class, Class, Class...)
     */
    static Method resolveStaticNoArgFactoryMethod(Class<?> ownerClass, Class<?> returnTypeClass)
        throws NoSuchMethodException
    {
        return resolveStaticFactoryMethod(ownerClass, returnTypeClass);
    }

    /**
     * Finds a field by name on {@code ownerClass}, falling back to type-based discovery if the name is not found.
     *
     * @param ownerClass
     *     Class to search.
     * @param preferredName
     *     Field name to try first.
     * @param fieldType
     *     Type to search for when name lookup fails.
     * @return
     *     Found field with accessibility set.
     * @throws NoSuchFieldException
     *     When neither the name nor type lookup succeeds.
     */
    static Field resolveFieldByNameOrType(Class<?> ownerClass, String preferredName, Class<?> fieldType)
        throws NoSuchFieldException
    {
        try
        {
            final Field field = ownerClass.getField(preferredName);
            field.setAccessible(true);
            return field;
        }
        catch (NoSuchFieldException ignored)
        {
            final Field typedField = findFieldByType(ownerClass, fieldType);
            if (typedField != null)
                return typedField;

            throw new NoSuchFieldException(
                "Failed to resolve field '" + preferredName + "' on " + ownerClass.getName()
            );
        }
    }

    /**
     * Finds a field by name on {@code ownerClass}, falling back to assignability-based discovery when the name is
     * not found. Unlike {@link #resolveFieldByNameOrType}, the fallback checks whether the field's type is
     * assignable <em>from</em> {@code acceptedType} (i.e., the field is a supertype of the accepted type).
     *
     * <p>The fallback skips static fields and fields declared as {@code Object}: an {@code Object}-typed field is
     * a supertype of everything, so accepting it would make resolution depend on declaration order instead of on
     * a meaningful type match.
     *
     * @param ownerClass
     *     Class to search (including superclass chain).
     * @param preferredName
     *     Field name to try first.
     * @param acceptedType
     *     Type whose supertype the field's declared type must be.
     * @return
     *     Found field with accessibility set.
     * @throws NoSuchFieldException
     *     When neither the name nor the type-assignability lookup succeeds.
     */
    static Field resolveFieldByNameOrAcceptedType(
        Class<?> ownerClass,
        String preferredName,
        Class<?> acceptedType
    )
        throws NoSuchFieldException
    {
        try
        {
            final Field field = ownerClass.getField(preferredName);
            field.setAccessible(true);
            return field;
        }
        catch (NoSuchFieldException ignored)
        {
            for (Class<?> cursor = ownerClass; cursor != null; cursor = cursor.getSuperclass())
            {
                for (final Field field : cursor.getDeclaredFields())
                {
                    if (Modifier.isStatic(field.getModifiers()))
                        continue;
                    if (field.getType() == Object.class || !field.getType().isAssignableFrom(acceptedType))
                        continue;
                    field.setAccessible(true);
                    return field;
                }
            }
            throw new NoSuchFieldException(
                "Failed to resolve field '" + preferredName + "' on " + ownerClass.getName()
            );
        }
    }

    /**
     * Searches the class hierarchy for a public or declared no-argument method with the given name.
     *
     * @param type
     *     Root class to search.
     * @param methodName
     *     Method name.
     * @return
     *     Found method with accessibility set, or {@code null} when not found.
     */
    static @Nullable Method findNamedNoArgMethod(Class<?> type, String methodName)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            try
            {
                final Method method = cursor.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
                // Continue searching in super class.
            }
        }
        return null;
    }

    /**
     * Searches the class hierarchy for a public or declared no-argument method with the given return type.
     *
     * @param type
     *     Root class to search.
     * @param returnType
     *     Expected return type (supertype match accepted).
     * @return
     *     Found method with accessibility set, or {@code null} when not found.
     */
    static @Nullable Method findNoArgMethodByReturnType(Class<?> type, Class<?> returnType)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (method.getParameterCount() != 0 || !returnType.isAssignableFrom(method.getReturnType()))
                    continue;
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    /**
     * Searches the class hierarchy for a field whose type is assignable to the given field type.
     *
     * @param type
     *     Root class to search.
     * @param fieldType
     *     Expected field type (supertype match accepted).
     * @return
     *     Found field with accessibility set, or {@code null} when not found.
     */
    static @Nullable Field findFieldByType(Class<?> type, Class<?> fieldType)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Field field : cursor.getDeclaredFields())
            {
                if (!fieldType.isAssignableFrom(field.getType()))
                    continue;
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    /**
     * Searches the class hierarchy for a method with the exact name and parameter types.
     *
     * @param type
     *     Root class to search.
     * @param methodName
     *     Method name.
     * @param parameterTypes
     *     Exact parameter types.
     * @return
     *     Found method with accessibility set, or {@code null} when not found.
     */
    static @Nullable Method findNamedMethod(Class<?> type, String methodName, Class<?>... parameterTypes)
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            try
            {
                final Method method = cursor.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
                // Continue searching in super class.
            }
        }
        return null;
    }

    /**
     * Searches the class hierarchy for a method whose parameter count matches and whose parameter types are
     * assignable from the supplied argument types.
     *
     * @param type
     *     Root class to search.
     * @param argumentTypes
     *     Argument types the parameters must be assignable from.
     * @return
     *     Found method with accessibility set.
     * @throws NoSuchMethodException
     *     When no compatible method exists.
     */
    static Method findCompatibleMethod(Class<?> type, Class<?>... argumentTypes)
        throws NoSuchMethodException
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != argumentTypes.length)
                    continue;

                boolean compatible = true;
                for (int idx = 0; idx < parameterTypes.length; ++idx)
                {
                    if (!parameterTypes[idx].isAssignableFrom(argumentTypes[idx]))
                    {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible)
                    continue;

                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
            "Failed to locate compatible method on "
                + type.getName()
                + " with parameter types "
                + List.of(argumentTypes)
        );
    }

    /**
     * Resolves an enum constant by its {@link Enum#name() name}, tolerating obfuscated field names.
     *
     * @param enumClass
     *     Enum class to search.
     * @param name
     *     Unobfuscated constant name (e.g. {@code "CLIENTBOUND"}).
     * @return
     *     Matching enum constant.
     * @throws IllegalStateException
     *     When no constant with that name exists.
     */
    static Object resolveEnumConstant(Class<?> enumClass, String name)
    {
        final Object[] constants = Objects.requireNonNull(
            enumClass.getEnumConstants(), "Expected enum constants for " + enumClass.getName());
        for (final Object constant : constants)
        {
            if (((Enum<?>) constant).name().equals(name))
                return constant;
        }
        throw new IllegalStateException(
            "No enum constant '" + name + "' found in " + enumClass.getName() + ".");
    }

    /**
     * Finds a public, non-static method by return type and parameter types, tolerating obfuscated method
     * names. Parameters and the return type are matched by assignability.
     *
     * @param type
     *     Class to search (including superclasses).
     * @param returnType
     *     Required return type (supertype match accepted; use {@code void.class} for void).
     * @param parameterTypes
     *     Required parameter types (each matched by assignability from the declared parameter).
     * @return
     *     Found method with accessibility set.
     * @throws NoSuchMethodException
     *     When no matching public method exists.
     */
    static Method findPublicMethod(Class<?> type, Class<?> returnType, Class<?>... parameterTypes)
        throws NoSuchMethodException
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()))
                    continue;
                if (!returnType.isAssignableFrom(method.getReturnType()))
                    continue;
                if (!parametersAssignable(method, parameterTypes))
                    continue;

                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
            "Failed to resolve public method on " + type.getName() + " returning " + returnType.getName()
                + " with parameters " + List.of(parameterTypes) + ".");
    }

    private static boolean parametersAssignable(Method method, Class<?>... parameterTypes)
    {
        final Class<?>[] declared = method.getParameterTypes();
        if (declared.length != parameterTypes.length)
            return false;
        for (int idx = 0; idx < declared.length; ++idx)
        {
            if (!declared[idx].isAssignableFrom(parameterTypes[idx]))
                return false;
        }
        return true;
    }

    /**
     * Finds a non-static, no-argument method whose return type is assignable to the given type.
     *
     * <p>Unlike {@link #findNoArgMethodByReturnType}, static methods are skipped so an instance accessor is
     * preferred over a static default/factory of the same return type.
     *
     * @param type
     *     Root class to search.
     * @param returnType
     *     Expected return type (supertype match accepted).
     * @return
     *     Found method with accessibility set.
     * @throws NoSuchMethodException
     *     When no matching instance method exists.
     */
    static Method findInstanceNoArgMethodByReturnType(Class<?> type, Class<?> returnType)
        throws NoSuchMethodException
    {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
        {
            for (final Method method : cursor.getDeclaredMethods())
            {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0)
                    continue;
                if (!returnType.isAssignableFrom(method.getReturnType()))
                    continue;

                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
            "Failed to resolve instance no-arg method on " + type.getName() + " returning "
                + returnType.getName() + ".");
    }

    /**
     * Drains all elements from a queue into an immutable list.
     *
     * @param queue
     *     Queue to drain.
     * @return
     *     Immutable snapshot of the drained elements, in poll order.
     */
    static List<String> drainQueue(Queue<String> queue)
    {
        final List<String> drained = new ArrayList<>();
        String value;
        while ((value = queue.poll()) != null)
            drained.add(value);
        return List.copyOf(drained);
    }

    /**
     * Invokes a named no-argument method on {@code target} and returns its value if it returns a
     * {@link String}; returns {@code null} on any failure.
     *
     * @param target
     *     Instance to invoke on.
     * @param methodName
     *     Method name to look up via {@link Class#getMethod}.
     * @return
     *     String return value, or {@code null} when the method is absent, throws, or returns a non-String type.
     */
    static @Nullable String invokeStringMethod(Object target, String methodName)
    {
        try
        {
            final Method method = target.getClass().getMethod(methodName);
            if (method.getReturnType() != String.class)
                return null;
            return (String) method.invoke(target);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }
}
