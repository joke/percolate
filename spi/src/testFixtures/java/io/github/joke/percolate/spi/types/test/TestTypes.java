package io.github.joke.percolate.spi.types.test;

import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.types.DeclKind;
import io.github.joke.percolate.spi.types.FieldSig;
import io.github.joke.percolate.spi.types.MemberFlag;
import io.github.joke.percolate.spi.types.MethodSig;
import io.github.joke.percolate.spi.types.Origin;
import io.github.joke.percolate.spi.types.ParamSig;
import io.github.joke.percolate.spi.types.PrimitiveKind;
import io.github.joke.percolate.spi.types.TypeDecl;
import io.github.joke.percolate.spi.types.TypeRef;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * A {@code java.lang.reflect} mirror from a compiled fixture class's structure into a {@link TypeDecl} (design
 * D8) — the reflection-mirror construction path for {@code spi} testFixtures. Mirrors declared members only
 * (methods, fields, constructors) and the declared supertype edges, expressed in terms of the class's own type
 * parameters exactly as {@link Class#getGenericSuperclass()}/{@link Class#getGenericInterfaces()} already do —
 * no {@code javax.lang.model}, no compiler session, thread-safe and eager by construction.
 */
@UtilityClass
@SuppressWarnings("PMD.TestClassWithoutTestCases") // fixture builder, not a test class despite the name
public class TestTypes {

    private static final Map<Class<?>, PrimitiveKind> PRIMITIVE_KINDS = primitiveKinds();

    /** Prebuilt plain-value constants — safe in {@code where:} blocks and across threads by construction. */
    public static final TypeRef STRING = TypeRef.declared("java.lang.String");

    public static final TypeRef INTEGER = TypeRef.declared("java.lang.Integer");
    public static final TypeRef LONG_TYPE = TypeRef.declared("java.lang.Long");
    public static final TypeRef INT = TypeRef.primitive(PrimitiveKind.INT);
    public static final TypeRef LONG = TypeRef.primitive(PrimitiveKind.LONG);
    public static final TypeRef DAY_OF_WEEK = TypeRef.declared("java.time.DayOfWeek");
    public static final TypeRef LIST_OF_STRING = TypeRef.declared("java.util.List", STRING);
    public static final TypeRef LIST_OF_INT = TypeRef.declared("java.util.List", INTEGER);

    /** The {@link TypeDecl} mirroring {@code type}'s declared structure. */
    public TypeDecl of(final Class<?> type) {
        return new TypeDecl(
                requireCanonicalName(type),
                declKindOf(type),
                typeParametersOf(type),
                superEdgesOf(type),
                methodsOf(type),
                fieldsOf(type),
                Origin.none());
    }

    /** The class's declared constructors, modelled as {@link MethodSig}s flagged {@link MemberFlag#CONSTRUCTOR}. */
    public List<MethodSig> constructorsOf(final Class<?> type) {
        return List.of(type.getDeclaredConstructors()).stream()
                .map(ctor -> constructorSigOf(type, ctor))
                .collect(Collectors.toUnmodifiableList());
    }

    /** The {@link TypeRef} for a {@code java.lang.reflect.Type} — the recursive conversion the mirror walks. */
    public TypeRef typeRefOf(final Type type) {
        if (type instanceof Class) {
            return classRefOf((Class<?>) type);
        }
        if (type instanceof ParameterizedType) {
            return parameterizedRefOf((ParameterizedType) type);
        }
        if (type instanceof TypeVariable) {
            return TypeRef.variable(((TypeVariable<?>) type).getName());
        }
        if (type instanceof GenericArrayType) {
            return TypeRef.array(typeRefOf(((GenericArrayType) type).getGenericComponentType()));
        }
        throw new IllegalArgumentException("unsupported reflected type shape (no wildcards in v1): " + type);
    }

    private TypeRef classRefOf(final Class<?> type) {
        if (type == void.class) {
            return TypeRef.none();
        }
        if (type.isArray()) {
            return TypeRef.array(typeRefOf(type.getComponentType()));
        }
        final var primitive = PRIMITIVE_KINDS.get(type);
        if (primitive != null) {
            return TypeRef.primitive(primitive);
        }
        return TypeRef.declared(requireCanonicalName(type));
    }

    private TypeRef parameterizedRefOf(final ParameterizedType type) {
        final var args = List.of(type.getActualTypeArguments()).stream()
                .map(TestTypes::typeRefOf)
                .collect(Collectors.toUnmodifiableList());
        return TypeRef.declared(requireCanonicalName((Class<?>) type.getRawType()), args);
    }

    private DeclKind declKindOf(final Class<?> type) {
        if (type.isInterface()) {
            return DeclKind.INTERFACE;
        }
        if (type.isEnum()) {
            return DeclKind.ENUM;
        }
        return DeclKind.CLASS;
    }

    private List<String> typeParametersOf(final Class<?> type) {
        return List.of(type.getTypeParameters()).stream()
                .map(TypeVariable::getName)
                .collect(Collectors.toUnmodifiableList());
    }

    private List<TypeRef> superEdgesOf(final Class<?> type) {
        final var edges = new ArrayList<TypeRef>();
        final var superclass = type.getGenericSuperclass();
        if (superclass != null) {
            edges.add(typeRefOf(superclass));
        }
        List.of(type.getGenericInterfaces()).forEach(iface -> edges.add(typeRefOf(iface)));
        return List.copyOf(edges);
    }

    private List<MethodSig> methodsOf(final Class<?> type) {
        return List.of(type.getDeclaredMethods()).stream()
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .map(method -> methodSigOf(type, method))
                .collect(Collectors.toUnmodifiableList());
    }

    private MethodSig methodSigOf(final Class<?> declaring, final Method method) {
        return new MethodSig(
                method.getName(),
                requireCanonicalName(declaring),
                parametersOf(method.getParameters()),
                typeRefOf(method.getGenericReturnType()),
                Nullability.UNKNOWN,
                memberFlags(method.getModifiers(), method.isDefault(), false),
                Origin.none());
    }

    private List<FieldSig> fieldsOf(final Class<?> type) {
        return List.of(type.getDeclaredFields()).stream()
                .filter(field -> !field.isSynthetic())
                .map(field -> fieldSigOf(type, field))
                .collect(Collectors.toUnmodifiableList());
    }

    private FieldSig fieldSigOf(final Class<?> declaring, final Field field) {
        return new FieldSig(
                field.getName(),
                requireCanonicalName(declaring),
                typeRefOf(field.getGenericType()),
                Nullability.UNKNOWN,
                memberFlags(field.getModifiers(), false, false),
                Origin.none());
    }

    private MethodSig constructorSigOf(final Class<?> declaring, final Constructor<?> ctor) {
        return new MethodSig(
                "<init>",
                requireCanonicalName(declaring),
                parametersOf(ctor.getParameters()),
                selfTypeOf(declaring),
                Nullability.NON_NULL,
                memberFlags(ctor.getModifiers(), false, true),
                Origin.none());
    }

    private TypeRef selfTypeOf(final Class<?> type) {
        final var typeParameters =
                typeParametersOf(type).stream().map(TypeRef::variable).collect(Collectors.toUnmodifiableList());
        return TypeRef.declared(requireCanonicalName(type), typeParameters);
    }

    private List<ParamSig> parametersOf(final Parameter... parameters) {
        return IntStream.range(0, parameters.length)
                .mapToObj(i -> parameterSigOf(parameters[i], i))
                .collect(Collectors.toUnmodifiableList());
    }

    private ParamSig parameterSigOf(final Parameter parameter, final int index) {
        final var name = parameter.isNamePresent() ? parameter.getName() : "arg" + index;
        return new ParamSig(name, typeRefOf(parameter.getParameterizedType()), Nullability.UNKNOWN);
    }

    private Set<MemberFlag> memberFlags(final int modifiers, final boolean isDefault, final boolean isConstructor) {
        final var flags = EnumSet.noneOf(MemberFlag.class);
        if (Modifier.isPublic(modifiers)) {
            flags.add(MemberFlag.PUBLIC);
        }
        if (Modifier.isPrivate(modifiers)) {
            flags.add(MemberFlag.PRIVATE);
        }
        if (Modifier.isStatic(modifiers)) {
            flags.add(MemberFlag.STATIC);
        }
        if (Modifier.isAbstract(modifiers)) {
            flags.add(MemberFlag.ABSTRACT);
        }
        if (isDefault) {
            flags.add(MemberFlag.DEFAULT);
        }
        if (isConstructor) {
            flags.add(MemberFlag.CONSTRUCTOR);
        }
        return Set.copyOf(flags);
    }

    private String requireCanonicalName(final Class<?> type) {
        final var canonicalName = type.getCanonicalName();
        if (canonicalName == null) {
            throw new IllegalArgumentException("no canonical name (anonymous/local class): " + type);
        }
        return canonicalName;
    }

    @SuppressWarnings("PMD.UseConcurrentHashMap") // built once, frozen via Map.copyOf, never mutated after
    private Map<Class<?>, PrimitiveKind> primitiveKinds() {
        final Map<Class<?>, PrimitiveKind> kinds = new HashMap<>();
        kinds.put(boolean.class, PrimitiveKind.BOOLEAN);
        kinds.put(byte.class, PrimitiveKind.BYTE);
        kinds.put(short.class, PrimitiveKind.SHORT);
        kinds.put(char.class, PrimitiveKind.CHAR);
        kinds.put(int.class, PrimitiveKind.INT);
        kinds.put(long.class, PrimitiveKind.LONG);
        kinds.put(float.class, PrimitiveKind.FLOAT);
        kinds.put(double.class, PrimitiveKind.DOUBLE);
        return Map.copyOf(kinds);
    }
}
