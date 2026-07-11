package io.github.joke.percolate.spi;

import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/**
 * The narrow, mockable type-query seam (change {@code type-query-seam}): the engine and strategies ask their type and
 * member questions here and treat every {@link TypeMirror}/{@link Element}/{@link TypeElement} as an opaque
 * pass-through token they never interrogate directly. The seam methods are declared as defaults over
 * {@link #types()}/{@link #elements()} so real-javac implementations (production and the test harness alike) get them
 * for free; {@code types()}/{@code elements()} themselves are removed once the last caller of them migrates (Phases
 * 4-5), leaving these the sole type/member surface.
 */
public interface ResolveCtx {
    Types types();

    Elements elements();

    @Nullable
    CallableMethods callableMethods();

    /** The project-wide default zone id from {@code -Apercolate.time.zone=…}, else empty when the option is unset. */
    Optional<String> configuredTimeZone();

    // ---- type algebra --------------------------------------------------------------------------------------

    /** Whether {@code a} and {@code b} denote the same type. */
    default boolean isSameType(final TypeMirror a, final TypeMirror b) {
        return types().isSameType(a, b);
    }

    /** Whether {@code a} is assignable to {@code b}. */
    default boolean isAssignable(final TypeMirror a, final TypeMirror b) {
        return types().isAssignable(a, b);
    }

    /** The erasure of {@code type}. */
    default TypeMirror erasure(final TypeMirror type) {
        return types().erasure(type);
    }

    /** The raw {@link TypeKind} of {@code type} — an escape hatch for lattice/table-keyed code (e.g. widening). */
    default TypeKind kind(final TypeMirror type) {
        return type.getKind();
    }

    /** Whether {@code type} is a primitive. */
    default boolean isPrimitive(final TypeMirror type) {
        return type.getKind().isPrimitive();
    }

    /** Whether {@code type} is an array. */
    default boolean isArray(final TypeMirror type) {
        return type.getKind() == TypeKind.ARRAY;
    }

    /** Whether {@code type} is a declared (class/interface) type. */
    default boolean isDeclared(final TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED;
    }

    /** Whether {@code type} is a type variable. */
    default boolean isTypeVariable(final TypeMirror type) {
        return type.getKind() == TypeKind.TYPEVAR;
    }

    /** The {@code index}-th type argument of the declared type {@code type}. */
    default TypeMirror typeArgument(final TypeMirror type, final int index) {
        if (!isDeclared(type)) {
            throw new IllegalArgumentException("Not a declared type: " + type);
        }
        final var args = ((DeclaredType) type).getTypeArguments();
        if (index < 0 || index >= args.size()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for type arguments of " + type);
        }
        return args.get(index);
    }

    /** The number of type arguments of the declared type {@code type}. */
    default int typeArgumentCount(final TypeMirror type) {
        if (!isDeclared(type)) {
            throw new IllegalArgumentException("Not a declared type: " + type);
        }
        return ((DeclaredType) type).getTypeArguments().size();
    }

    /** The component type of the array type {@code type}. */
    default TypeMirror arrayComponent(final TypeMirror type) {
        if (!isArray(type)) {
            throw new IllegalArgumentException("Not an array type: " + type);
        }
        return ((ArrayType) type).getComponentType();
    }

    /** The declared type {@code element<args…>}. */
    default TypeMirror declaredType(final TypeElement element, final TypeMirror... args) {
        return types().getDeclaredType(element, args);
    }

    /** The array type over {@code component}. */
    default TypeMirror arrayType(final TypeMirror component) {
        return types().getArrayType(component);
    }

    /** The boxed wrapper type of the primitive {@code primitive}. */
    default TypeMirror boxed(final TypeMirror primitive) {
        return types().boxedClass((PrimitiveType) primitive).asType();
    }

    /** The unboxed primitive type of the wrapper {@code wrapper}. */
    default TypeMirror unboxed(final TypeMirror wrapper) {
        return types().unboxedType(wrapper);
    }

    /** The primitive type of kind {@code kind} (e.g. {@code TypeKind.INT}). */
    default TypeMirror primitiveType(final TypeKind kind) {
        return types().getPrimitiveType(kind);
    }

    /** A short display name for {@code type}: a declared type's simple name, else its string form. */
    default String simpleName(final TypeMirror type) {
        return asTypeElement(type)
                .map(element -> element.getSimpleName().toString())
                .orElseGet(type::toString);
    }

    /** The fully-qualified name of a declared type's element, else its string form. */
    default String qualifiedName(final TypeMirror type) {
        return asTypeElement(type)
                .map(element -> element.getQualifiedName().toString())
                .orElseGet(type::toString);
    }

    /** The backing {@link TypeElement} of a declared type, or empty for a non-declared (primitive/array/…) type. */
    default Optional<TypeElement> asTypeElement(final TypeMirror type) {
        if (!isDeclared(type)) {
            return Optional.empty();
        }
        final var element = types().asElement(type);
        return element instanceof TypeElement ? Optional.of((TypeElement) element) : Optional.empty();
    }

    /** The {@link TypeElement} named {@code fqn} (e.g. {@code java.util.List}), or {@code null} if unresolvable. */
    @Nullable
    default TypeElement typeElementNamed(final String fqn) {
        return elements().getTypeElement(fqn);
    }

    /** The direct superclass of the declared type {@code type} (a {@code NONE}-kind type when there is none). */
    default TypeMirror superclassOf(final TypeMirror type) {
        return asTypeElement(type).map(TypeElement::getSuperclass).orElseGet(() -> types().getNoType(TypeKind.NONE));
    }

    // ---- higher-level type predicates (Containers/TypeProbe carve-over) -------------------------------------

    /** Whether {@code type}'s erasure is {@code java.util.List}. */
    default boolean isList(final TypeMirror type) {
        return isType(type, "java.util.List");
    }

    /** Whether {@code type}'s erasure is {@code java.util.Set}. */
    default boolean isSet(final TypeMirror type) {
        return isType(type, "java.util.Set");
    }

    /** Whether {@code type}'s erasure is {@code java.util.Optional}. */
    default boolean isOptional(final TypeMirror type) {
        return isType(type, "java.util.Optional");
    }

    /** Whether {@code type}'s erasure is {@code java.util.stream.Stream}. */
    default boolean isStream(final TypeMirror type) {
        return isType(type, "java.util.stream.Stream");
    }

    /** Whether {@code type} is assignable to {@code java.util.Collection}. */
    default boolean isCollection(final TypeMirror type) {
        return isAssignableToNamed(type, "java.util.Collection");
    }

    /** Whether {@code type} is assignable to {@code java.lang.Iterable}. */
    default boolean isIterable(final TypeMirror type) {
        return isAssignableToNamed(type, "java.lang.Iterable");
    }

    /** Whether {@code type} is an {@code enum} declaration. */
    default boolean isEnum(final TypeMirror type) {
        return asTypeElement(type)
                .map(element -> element.getKind() == ElementKind.ENUM)
                .orElse(false);
    }

    /** Whether {@code element} is a reference type — i.e. usable as a generic type argument (not a primitive). */
    default boolean isReferenceType(final TypeMirror element) {
        return isDeclared(element) || isArray(element) || isTypeVariable(element);
    }

    /** Whether {@code type}'s erasure is the type named {@code fqn} (e.g. {@code java.util.List}). */
    default boolean isType(final TypeMirror type, final String fqn) {
        if (!isDeclared(type)) {
            return false;
        }
        final var named = typeElementNamed(fqn);
        return named != null && isSameType(erasure(type), erasure(named.asType()));
    }

    private boolean isAssignableToNamed(final TypeMirror type, final String fqn) {
        if (!isDeclared(type)) {
            return false;
        }
        final var named = typeElementNamed(fqn);
        return named != null && isAssignable(erasure(type), erasure(named.asType()));
    }

    // ---- member reflection -----------------------------------------------------------------------------------

    /** All members of {@code parent} — inherited and declared, including constructors. */
    default Stream<? extends Element> membersOf(final TypeElement parent) {
        return elements().getAllMembers(parent).stream();
    }

    /** Whether {@code member} is a field. */
    default boolean isField(final Element member) {
        return member.getKind() == ElementKind.FIELD;
    }

    /** Whether {@code member} is a method. */
    default boolean isMethod(final Element member) {
        return member.getKind() == ElementKind.METHOD;
    }

    /** Whether {@code member} is a constructor. */
    default boolean isConstructor(final Element member) {
        return member.getKind() == ElementKind.CONSTRUCTOR;
    }

    /** Whether {@code member} is declared {@code private}. */
    default boolean isPrivate(final Element member) {
        return member.getModifiers().contains(Modifier.PRIVATE);
    }

    /** Whether {@code member} is declared {@code static}. */
    default boolean isStatic(final Element member) {
        return member.getModifiers().contains(Modifier.STATIC);
    }
}
