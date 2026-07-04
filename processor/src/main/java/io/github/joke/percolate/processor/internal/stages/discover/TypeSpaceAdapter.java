package io.github.joke.percolate.processor.internal.stages.discover;

import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.types.DeclKind;
import io.github.joke.percolate.spi.types.FieldSig;
import io.github.joke.percolate.spi.types.MemberFlag;
import io.github.joke.percolate.spi.types.MethodSig;
import io.github.joke.percolate.spi.types.Origin;
import io.github.joke.percolate.spi.types.ParamSig;
import io.github.joke.percolate.spi.types.TypeDecl;
import io.github.joke.percolate.spi.types.TypeRef;
import io.github.joke.percolate.spi.types.TypeRefs;
import io.github.joke.percolate.spi.types.TypeSpace;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import lombok.RequiredArgsConstructor;

/**
 * The discovery adapter (design D6, change {@code evict-javax-model}): the single place javac's
 * {@code javax.lang.model} is read into the owned type currency. It materialises the reachable declared-type
 * closure from a mapper's declared surface into an immutable {@link TypeSpace} of {@link TypeDecl}/
 * {@link MethodSig}/{@link FieldSig} values, resolving member nullness at the boundary (where the mirrors are in
 * hand). The pure type→{@link TypeRef} conversion is {@link TypeRefs}; this adapter adds the closure walk, the
 * member model, and nullness resolution. The walk is eager and cycle-safe (a visited set over qualified names);
 * reached only from the single-threaded annotation-processing round, it is race-free by context and the produced
 * snapshot is immutable.
 *
 * <p>JDK types ({@code java.*}/{@code javax.*}) are materialised <b>edge-only</b> — their declared supertype
 * edges and type parameters (which the assignability walk needs) without member enumeration (which no strategy
 * reads off a JDK type: containers match on erasure, accessors read DTO members). That bounds the closure and
 * avoids walking the JDK's member-signature closure.
 *
 * <p>Transitional scope: the {@link Origin} registry (design D5, for diagnostics positioning) is stubbed to
 * {@link Origin#none()} until the diagnostics stages migrate onto the model — the only consumer of it.
 */
@RequiredArgsConstructor
public final class TypeSpaceAdapter {

    private final NullabilityResolver resolver;

    /** The immutable {@link TypeSpace} of the declared-type closure reachable from {@code roots}. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-round closure walk
    public TypeSpace build(final Collection<? extends TypeMirror> roots) {
        final Map<String, TypeDecl> decls = new HashMap<>();
        final Deque<TypeElement> queue = new ArrayDeque<>();
        final Set<String> seen = new HashSet<>();
        roots.forEach(root -> enqueue(root, queue, seen));
        while (!queue.isEmpty()) {
            final var decl = declOf(queue.poll(), queue, seen);
            decls.put(decl.getQualifiedName(), decl);
        }
        return TypeSpace.of(decls.values().toArray(new TypeDecl[0]));
    }

    private TypeDecl declOf(final TypeElement element, final Deque<TypeElement> queue, final Set<String> seen) {
        final var qualifiedName = element.getQualifiedName().toString();
        final var superEdges = superEdgesOf(element, queue, seen);
        final var typeParameters = element.getTypeParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.toUnmodifiableList());
        final List<MethodSig> methods = new ArrayList<>();
        final List<FieldSig> fields = new ArrayList<>();
        if (!isJdk(qualifiedName)) {
            enumerateMembers(element, queue, seen, methods, fields);
        }
        return new TypeDecl(
                qualifiedName,
                declKindOf(element.getKind()),
                typeParameters,
                superEdges,
                methods,
                fields,
                Origin.none());
    }

    private List<TypeRef> superEdgesOf(
            final TypeElement element, final Deque<TypeElement> queue, final Set<String> seen) {
        final List<TypeRef> edges = new ArrayList<>();
        addDeclaredEdge(element.getSuperclass(), edges, queue, seen);
        element.getInterfaces().forEach(iface -> addDeclaredEdge(iface, edges, queue, seen));
        return List.copyOf(edges);
    }

    private void addDeclaredEdge(
            final TypeMirror supertype,
            final List<TypeRef> edges,
            final Deque<TypeElement> queue,
            final Set<String> seen) {
        if (supertype.getKind() == TypeKind.DECLARED) {
            enqueue(supertype, queue, seen);
            edges.add(TypeRefs.of(supertype));
        }
    }

    private void enumerateMembers(
            final TypeElement element,
            final Deque<TypeElement> queue,
            final Set<String> seen,
            final List<MethodSig> methods,
            final List<FieldSig> fields) {
        final var enclosed = element.getEnclosedElements();
        for (final var method : ElementFilter.methodsIn(enclosed)) {
            enqueue(method.getReturnType(), queue, seen);
            method.getParameters().forEach(parameter -> enqueue(parameter.asType(), queue, seen));
            methods.add(methodSigOf(element, method, false));
        }
        for (final var constructor : ElementFilter.constructorsIn(enclosed)) {
            constructor.getParameters().forEach(parameter -> enqueue(parameter.asType(), queue, seen));
            methods.add(methodSigOf(element, constructor, true));
        }
        for (final var field : ElementFilter.fieldsIn(enclosed)) {
            enqueue(field.asType(), queue, seen);
            fields.add(fieldSigOf(element, field));
        }
    }

    /**
     * Package-visible so {@link DiscoverCallableMethodsStage} can resolve a {@link MethodSig} for one candidate
     * method directly, without waiting for a full closure walk over its declaring type.
     */
    MethodSig methodSigOf(final TypeElement declaring, final ExecutableElement method, final boolean isConstructor) {
        final var parameters =
                method.getParameters().stream().map(this::paramSigOf).collect(Collectors.toUnmodifiableList());
        final var name = isConstructor ? "<init>" : method.getSimpleName().toString();
        final var returnType = isConstructor ? selfRefOf(declaring) : TypeRefs.of(method.getReturnType());
        final var returnNullness =
                isConstructor ? Nullability.NON_NULL : resolver.resolve(method.getReturnType(), method);
        return new MethodSig(
                name,
                declaring.getQualifiedName().toString(),
                parameters,
                returnType,
                returnNullness,
                memberFlags(method, isConstructor),
                Origin.none());
    }

    private FieldSig fieldSigOf(final TypeElement declaring, final VariableElement field) {
        return new FieldSig(
                field.getSimpleName().toString(),
                declaring.getQualifiedName().toString(),
                TypeRefs.of(field.asType()),
                resolver.resolve(field.asType(), field),
                memberFlags(field, false),
                Origin.none());
    }

    private ParamSig paramSigOf(final VariableElement parameter) {
        return new ParamSig(
                parameter.getSimpleName().toString(),
                TypeRefs.of(parameter.asType()),
                resolver.resolve(parameter.asType(), parameter));
    }

    private TypeRef selfRefOf(final TypeElement element) {
        final var args = element.getTypeParameters().stream()
                .map(parameter -> TypeRef.variable(parameter.getSimpleName().toString()))
                .collect(Collectors.toUnmodifiableList());
        return TypeRef.declared(element.getQualifiedName().toString(), args);
    }

    private void enqueue(final TypeMirror type, final Deque<TypeElement> queue, final Set<String> seen) {
        if (type.getKind() == TypeKind.DECLARED) {
            enqueueDeclared((DeclaredType) type, queue, seen);
        } else if (type.getKind() == TypeKind.ARRAY) {
            enqueue(((ArrayType) type).getComponentType(), queue, seen);
        }
    }

    private void enqueueDeclared(final DeclaredType type, final Deque<TypeElement> queue, final Set<String> seen) {
        if (type.asElement() instanceof TypeElement) {
            final var element = (TypeElement) type.asElement();
            if (seen.add(element.getQualifiedName().toString())) {
                queue.add(element);
            }
        }
        type.getTypeArguments().forEach(arg -> enqueue(arg, queue, seen));
    }

    private static Set<MemberFlag> memberFlags(final Element member, final boolean isConstructor) {
        final var flags = EnumSet.noneOf(MemberFlag.class);
        final var modifiers = member.getModifiers();
        if (modifiers.contains(Modifier.PUBLIC)) {
            flags.add(MemberFlag.PUBLIC);
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            flags.add(MemberFlag.PRIVATE);
        }
        if (modifiers.contains(Modifier.STATIC)) {
            flags.add(MemberFlag.STATIC);
        }
        if (modifiers.contains(Modifier.ABSTRACT)) {
            flags.add(MemberFlag.ABSTRACT);
        }
        if (modifiers.contains(Modifier.DEFAULT)) {
            flags.add(MemberFlag.DEFAULT);
        }
        if (isConstructor) {
            flags.add(MemberFlag.CONSTRUCTOR);
        }
        return Set.copyOf(flags);
    }

    private static DeclKind declKindOf(final ElementKind kind) {
        if (kind == ElementKind.INTERFACE) {
            return DeclKind.INTERFACE;
        }
        if (kind == ElementKind.ENUM) {
            return DeclKind.ENUM;
        }
        return DeclKind.CLASS;
    }

    private static boolean isJdk(final String qualifiedName) {
        return qualifiedName.startsWith("java.") || qualifiedName.startsWith("javax.");
    }
}
