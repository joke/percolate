package io.github.joke.percolate.processor.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.MapOptKey
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class TemporalToStringStrategySpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()
    TypeElement stringElement = Stub() { asType() >> Stub(TypeMirror) }
    ResolutionContext ctx = Stub() {
        getTypes() >> types
        getElements() >> elements
        getOption(MapOptKey.DATE_FORMAT) >> Optional.empty()
    }

    TemporalToStringStrategy strategy = new TemporalToStringStrategy()

    def declaredType(String name) {
        Stub(DeclaredType) {
            getKind() >> TypeKind.DECLARED
            toString() >> name
        }
    }

    def setup() {
        elements.getTypeElement('java.lang.String') >> stringElement
    }

    def 'returns empty for primitive source type'() {
        given:
        final sourceType = Stub(TypeMirror) { getKind() >> TypeKind.INT }
        final targetType = declaredType('java.lang.String')
        types.isSameType(targetType, stringElement.asType()) >> true

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty when source is not a supported temporal type'() {
        given:
        final sourceType = declaredType('com.example.Unsupported')
        final targetType = declaredType('java.lang.String')
        types.isSameType(targetType, stringElement.asType()) >> true

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty when target is not String'() {
        given:
        final sourceType = declaredType('java.time.LocalDate')
        final targetType = declaredType('java.lang.Integer')
        types.isSameType(targetType, stringElement.asType()) >> false

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns proposal for #typeName without format option using toString()'() {
        given:
        final sourceType = declaredType(typeName)
        final targetType = declaredType('java.lang.String')
        types.isSameType(targetType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('val')).toString() == 'val.toString()'

        where:
        typeName << [
                'java.time.LocalDate',
                'java.time.LocalTime',
                'java.time.LocalDateTime',
                'java.time.ZonedDateTime',
                'java.time.OffsetDateTime',
                'java.time.OffsetTime',
                'java.time.Duration',
                'java.time.Period',
        ]
    }

    def 'Instant without format option uses toString()'() {
        given:
        final sourceType = declaredType('java.time.Instant')
        final targetType = declaredType('java.lang.String')
        types.isSameType(targetType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('val')).toString() == 'val.toString()'
    }

    def 'returns proposal for #typeName with DATE_FORMAT using formatter'() {
        given:
        final sourceType = declaredType(typeName)
        final targetType = declaredType('java.lang.String')
        final ctxWithFormat = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getOption(MapOptKey.DATE_FORMAT) >> Optional.of('dd.MM.yyyy')
        }
        types.isSameType(targetType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctxWithFormat)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('val')).toString()
                .contains("ofPattern(\"dd.MM.yyyy\")")

        where:
        typeName << [
                'java.time.LocalDate',
                'java.time.LocalDateTime',
                'java.time.ZonedDateTime',
                'java.time.OffsetDateTime',
                'java.time.OffsetTime',
        ]
    }

    def 'Instant with DATE_FORMAT bridges through atZone(systemDefault())'() {
        given:
        final sourceType = declaredType('java.time.Instant')
        final targetType = declaredType('java.lang.String')
        final ctxWithFormat = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getOption(MapOptKey.DATE_FORMAT) >> Optional.of('dd.MM.yyyy HH:mm')
        }
        types.isSameType(targetType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctxWithFormat)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code.contains('atZone(')
        code.contains('systemDefault()')
        code.contains("ofPattern(\"dd.MM.yyyy HH:mm\")")
    }

    def 'strategy is set on returned proposal'() {
        given:
        final sourceType = declaredType('java.time.LocalDate')
        final targetType = declaredType('java.lang.String')
        types.isSameType(targetType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().strategy.is(strategy)
    }
}
