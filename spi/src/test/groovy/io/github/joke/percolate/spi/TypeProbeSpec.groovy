package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.types.DeclKind
import io.github.joke.percolate.spi.types.Origin
import io.github.joke.percolate.spi.types.TypeDecl
import io.github.joke.percolate.spi.types.TypeSpace
import io.github.joke.percolate.spi.types.test.TestTypes
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class TypeProbeSpec extends Specification {

    // Literal decls (design D8): java.util.List as a plain member-less class shape, plus a hand-built
    // enum decl matching TestTypes.DAY_OF_WEEK's name — reflecting the real interfaces/enum risks tripping
    // over wildcard method signatures the v1 model doesn't represent (see ContainersSpec).
    private static final TypeDecl LIST_DECL = TypeDecl.of('java.util.List', ['E'], [])
    private static final TypeDecl DAY_OF_WEEK_DECL =
            new TypeDecl('java.time.DayOfWeek', DeclKind.ENUM, [], [], [], [], Origin.none())

    @Shared ResolveCtx ctx = new TypeSpaceResolveCtx(TypeSpace.of(LIST_DECL, DAY_OF_WEEK_DECL))

    def 'asTypeDecl(TypeRef) returns the declaration backing a declared type carried by the space'() {
        expect:
        TypeProbe.asTypeDecl(TestTypes.LIST_OF_STRING, ctx).get().qualifiedName == 'java.util.List'
    }

    def 'asTypeDecl(TypeRef) is empty for a declared type absent from the space'() {
        expect:
        TypeProbe.asTypeDecl(TestTypes.STRING, ctx).empty
    }

    def 'asTypeDecl(TypeRef) is empty for a non-declared type'() {
        expect:
        TypeProbe.asTypeDecl(TestTypes.INT, ctx).empty
    }

    def 'isType(TypeRef) matches by erasure and rejects a different name'() {
        expect:
        TypeProbe.isType(TestTypes.LIST_OF_STRING, 'java.util.List', ctx)
        !TypeProbe.isType(TestTypes.LIST_OF_STRING, 'java.util.Set', ctx)
    }

    def 'isType(TypeRef) rejects a non-declared type'() {
        expect:
        !TypeProbe.isType(TestTypes.INT, 'java.util.List', ctx)
    }

    def 'isEnum(TypeRef) recognises an enum declaration and rejects others'() {
        expect:
        TypeProbe.isEnum(TestTypes.DAY_OF_WEEK, ctx)
        !TypeProbe.isEnum(TestTypes.LIST_OF_STRING, ctx)
        !TypeProbe.isEnum(TestTypes.INT, ctx)
    }

    def 'simpleName(TypeRef) strips the package from a declared type'() {
        expect:
        TypeProbe.simpleName(TestTypes.LIST_OF_STRING) == 'List'
    }

    def 'simpleName(TypeRef) falls back to the source form for a non-declared type'() {
        expect:
        TypeProbe.simpleName(TestTypes.INT) == 'int'
    }

    private static final class TypeSpaceResolveCtx implements ResolveCtx {
        private final TypeSpace typeSpace

        TypeSpaceResolveCtx(final TypeSpace typeSpace) {
            this.typeSpace = typeSpace
        }

        @Override
        javax.lang.model.util.Elements elements() {
            throw new UnsupportedOperationException()
        }

        @Override
        javax.lang.model.util.Types types() {
            throw new UnsupportedOperationException()
        }

        @Override
        CallableMethods callableMethods() {
            null
        }

        @Override
        TypeSpace typeSpace() {
            typeSpace
        }
    }
}
