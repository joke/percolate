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
class StringToTemporalStrategySpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()
    TypeElement stringElement = Stub() { asType() >> Stub(TypeMirror) }
    ResolutionContext ctx = Stub() {
        getTypes() >> types
        getElements() >> elements
        getOption(MapOptKey.DATE_FORMAT) >> Optional.empty()
    }

    StringToTemporalStrategy strategy = new StringToTemporalStrategy()

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
        final targetType = declaredType('java.time.LocalDate')

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty when source is not String'() {
        given:
        final sourceType = declaredType('java.lang.Integer')
        final targetType = declaredType('java.time.LocalDate')
        types.isSameType(sourceType, stringElement.asType()) >> false

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty when target is not a supported temporal type'() {
        given:
        final sourceType = declaredType('java.lang.String')
        final targetType = declaredType('com.example.Unsupported')
        types.isSameType(sourceType, stringElement.asType()) >> true

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns proposal for String -> #typeName without format using T.parse()'() {
        given:
        final sourceType = declaredType('java.lang.String')
        final targetType = declaredType(typeName)
        types.isSameType(sourceType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().codeTemplate.apply(CodeBlock.of('val')).toString() ==~ /.*\.parse\(val\)/

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

    def 'Instant without format option uses Instant.parse()'() {
        given:
        final sourceType = declaredType('java.lang.String')
        final targetType = declaredType('java.time.Instant')
        types.isSameType(sourceType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code == 'java.time.Instant.parse(val)'
    }

    def 'returns proposal for String -> #typeName with DATE_FORMAT using formatter'() {
        given:
        final sourceType = declaredType('java.lang.String')
        final targetType = declaredType(typeName)
        final ctxWithFormat = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getOption(MapOptKey.DATE_FORMAT) >> Optional.of('dd.MM.yyyy')
        }
        types.isSameType(sourceType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctxWithFormat)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code.contains("ofPattern(\"dd.MM.yyyy\")")
        code.contains('val')

        where:
        typeName << [
                'java.time.LocalDate',
                'java.time.LocalDateTime',
                'java.time.ZonedDateTime',
                'java.time.OffsetDateTime',
                'java.time.OffsetTime',
        ]
    }

    def 'String -> Instant with DATE_FORMAT parses via LocalDateTime then atZone'() {
        given:
        final sourceType = declaredType('java.lang.String')
        final targetType = declaredType('java.time.Instant')
        final ctxWithFormat = Stub(ResolutionContext) {
            getTypes() >> types
            getElements() >> elements
            getOption(MapOptKey.DATE_FORMAT) >> Optional.of('dd.MM.yyyy HH:mm')
        }
        types.isSameType(sourceType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctxWithFormat)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code.contains('LocalDateTime')
        code.contains('atZone(')
        code.contains('systemDefault()')
        code.contains('toInstant()')
        code.contains("ofPattern(\"dd.MM.yyyy HH:mm\")")
    }

    def 'strategy is set on returned proposal'() {
        given:
        final sourceType = declaredType('java.lang.String')
        final targetType = declaredType('java.time.LocalDate')
        types.isSameType(sourceType, stringElement.asType()) >> true

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().strategy.is(strategy)
    }
}
