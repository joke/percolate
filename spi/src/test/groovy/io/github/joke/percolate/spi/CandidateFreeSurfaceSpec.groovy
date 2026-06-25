package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * Guards the candidate-free, uniform target-driven SPI surface (change {@code target-driven-engine} §§ 3.2/3.5): the
 * {@code spi} package exposes no candidate-iterating mixin and the {@link Demand} producer contract carries no
 * source-candidate snapshot, and {@link Containers} performs its declared-type checks through {@link TypeProbe}.
 */
@Tag('unit')
class CandidateFreeSurfaceSpec extends Specification {

    @Shared ResolveCtx ctx = new SimpleResolveCtx()
    @Shared TypeMirror optionalOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.Optional'), TypeUniverse.STRING)
    @Shared TypeMirror setOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.Set'), TypeUniverse.STRING)
    @Shared TypeMirror streamOfString = TypeUniverse.types().getDeclaredType(
            TypeUniverse.elements().getTypeElement('java.util.stream.Stream'), TypeUniverse.STRING)

    def 'the spi package exposes no CombinatorialMatch mixin'() {
        when:
        Class.forName('io.github.joke.percolate.spi.CombinatorialMatch')

        then:
        thrown(ClassNotFoundException)
    }

    def 'the spi package exposes no Candidate snapshot type'() {
        when:
        Class.forName('io.github.joke.percolate.spi.Candidate')

        then:
        thrown(ClassNotFoundException)
    }

    def 'the Demand producer contract exposes no candidates() accessor'() {
        expect:
        ProduceDemand.methods.every { it.name != 'candidates' }
    }

    def 'Containers.isOptional delegates its declared-type check to TypeProbe.isType'() {
        expect:
        Containers.isOptional(type, ctx) == TypeProbe.isType(type, 'java.util.Optional', ctx)

        where:
        type << [optionalOfString, setOfString, streamOfString, TypeUniverse.LIST_OF_STRING, TypeUniverse.STRING,
                 TypeUniverse.INT]
    }

    def 'Containers.isStream delegates its declared-type check to TypeProbe.isType'() {
        expect:
        Containers.isStream(type, ctx) == TypeProbe.isType(type, 'java.util.stream.Stream', ctx)

        where:
        type << [streamOfString, optionalOfString, setOfString, TypeUniverse.LIST_OF_STRING, TypeUniverse.STRING,
                 TypeUniverse.INT]
    }

    def 'Containers.isList delegates its declared-type check to TypeProbe.isType'() {
        expect:
        Containers.isList(type, ctx) == TypeProbe.isType(type, 'java.util.List', ctx)

        where:
        type << [TypeUniverse.LIST_OF_STRING, setOfString, optionalOfString, streamOfString, TypeUniverse.STRING,
                 TypeUniverse.INT]
    }

    def 'Containers.isSet delegates its declared-type check to TypeProbe.isType'() {
        expect:
        Containers.isSet(type, ctx) == TypeProbe.isType(type, 'java.util.Set', ctx)

        where:
        type << [setOfString, TypeUniverse.LIST_OF_STRING, optionalOfString, streamOfString, TypeUniverse.STRING,
                 TypeUniverse.INT]
    }

    private static final class SimpleResolveCtx implements ResolveCtx {
        @Override
        javax.lang.model.util.Elements elements() {
            TypeUniverse.elements()
        }

        @Override
        javax.lang.model.util.Types types() {
            TypeUniverse.types()
        }

        @Override
        CallableMethods callableMethods() {
            null
        }
    }
}
