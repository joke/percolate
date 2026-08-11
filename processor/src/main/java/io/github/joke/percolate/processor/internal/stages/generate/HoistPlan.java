package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.NameAllocator;
import io.github.joke.percolate.processor.internal.graph.Value;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

import static java.lang.Character.toLowerCase;

// The separable, pure hoist decision plus variable naming for one method body (design D1/D2/D5). Given the
// ExtractedPlan reachable from a method return-root, it decides which in-plan Values materialise as named
// locals: a Value with a chosen producer that either feeds a port of an n-ary Operation (getPorts().size() >= 2
// — a multi-argument assembly call) or is consumed by more than one in-plan port (so it is evaluated once, not
// re-rendered per use). Single-port chains (container iterate/collect/flatMap/wrap/unwrap, conversions,
// accessors, nullness crossings) and bare leaves (parameter / element-lambda roots, which have no chosen
// producer) stay inline.
//
// It mutates neither the MapperGraph nor the ExtractedPlan and adds no codegen IR — it is the seam toward a
// future per-scope binding schedule. Naming lives here too: each hoisted local is named after the slot it
// materialises (Location.slotName() — the target field, source segment, or element role) and a lambda parameter
// after its element type, made unique within the method by a NameAllocator seeded with the method's parameter
// names (so a local never shadows a parameter, collisions get a suffix, and reserved words are sanitised).
// IdentityHashMap is the point: every memo here is keyed by Value/Operation instance identity, not value equality.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
@RequiredArgsConstructor
final class HoistPlan {

    private final Set<Value> hoisted;

    private final NameAllocator names;

    private final Map<Value, CodeBlock> references = new IdentityHashMap<>();

    boolean isHoisted(final Value value) {
        return hoisted.contains(value);
    }

    // Allocates a unique name for a hoisted value from its slot name, records its reference, returns it.
    String declare(final Value value) {
        final var name = names.newName(slotBase(value));
        references.put(value, CodeBlock.of("$N", name));
        return name;
    }

    // The variable reference of a hoisted, already-declared value.
    CodeBlock reference(final Value value) {
        final var ref = references.get(value);
        if (ref == null) {
            throw new IllegalStateException("hoisted Value referenced before declaration: " + value.id());
        }
        return ref;
    }

    // Allocates a unique lambda-parameter name for an element of elementType (from the child input decl).
    String lambdaName(final TypeMirror elementType) {
        return names.newName(typeBase(elementType));
    }

    String slotBase(final Value value) {
        final var slot = value.getLoc().slotName();
        return slot.isEmpty() ? "value" : slot;
    }

    String typeBase(final TypeMirror type) {
        final var simple = declaredSimpleName(type);
        return simple.isEmpty() ? "element" : toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    String declaredSimpleName(final TypeMirror type) {
        return type instanceof DeclaredType
                ? ((DeclaredType) type).asElement().getSimpleName().toString()
                : "";
    }
}
