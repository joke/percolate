package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.PrivateTypeUniverse
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

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared ResolveCtx ctx = new SimpleResolveCtx(javac)
    @Shared TypeMirror optionalOfString = javac.types().getDeclaredType(
            javac.elements().getTypeElement('java.util.Optional'), javac.STRING)
    @Shared TypeMirror setOfString = javac.types().getDeclaredType(
            javac.elements().getTypeElement('java.util.Set'), javac.STRING)
    @Shared TypeMirror streamOfString = javac.types().getDeclaredType(
            javac.elements().getTypeElement('java.util.stream.Stream'), javac.STRING)

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
        type << [optionalOfString, setOfString, streamOfString, javac.LIST_OF_STRING, javac.STRING,
                 javac.INT]
    }

    def 'Containers.isStream delegates its declared-type check to TypeProbe.isType'() {
        expect:
        Containers.isStream(type, ctx) == TypeProbe.isType(type, 'java.util.stream.Stream', ctx)

        where:
        type << [streamOfString, optionalOfString, setOfString, javac.LIST_OF_STRING, javac.STRING,
                 javac.INT]
    }

    def 'Containers.isList delegates its declared-type check to TypeProbe.isType'() {
        expect:
        Containers.isList(type, ctx) == TypeProbe.isType(type, 'java.util.List', ctx)

        where:
        type << [javac.LIST_OF_STRING, setOfString, optionalOfString, streamOfString, javac.STRING,
                 javac.INT]
    }

    def 'Containers.isSet delegates its declared-type check to TypeProbe.isType'() {
        expect:
        Containers.isSet(type, ctx) == TypeProbe.isType(type, 'java.util.Set', ctx)

        where:
        type << [setOfString, javac.LIST_OF_STRING, optionalOfString, streamOfString, javac.STRING,
                 javac.INT]
    }

    private static final class SimpleResolveCtx implements ResolveCtx {
        private final PrivateTypeUniverse javac

        SimpleResolveCtx(final PrivateTypeUniverse javac) {
            this.javac = javac
        }

        @Override
        javax.lang.model.util.Elements elements() {
            javac.elements()
        }

        @Override
        javax.lang.model.util.Types types() {
            javac.types()
        }

        @Override
        CallableMethods callableMethods() {
            null
        }
    }
}
