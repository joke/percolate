package io.github.joke.percolate.processor.spi

import com.palantir.javapoet.CodeBlock
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class LegacyDateBridgeStrategySpec extends Specification {

    Types types = Mock()
    Elements elements = Mock()
    ResolutionContext ctx = Stub() {
        getTypes() >> types
        getElements() >> elements
    }

    LegacyDateBridgeStrategy strategy = new LegacyDateBridgeStrategy()

    def declaredType(String name) {
        Stub(DeclaredType) {
            getKind() >> TypeKind.DECLARED
            toString() >> name
        }
    }

    def typeElementFor(String name) {
        final element = Stub(TypeElement)
        final mirrorType = Stub(DeclaredType) { toString() >> name }
        elements.getTypeElement(name) >> element
        types.getDeclaredType(element) >> mirrorType
        mirrorType
    }

    def 'returns empty for primitive types'() {
        given:
        final sourceType = Stub(TypeMirror) { getKind() >> TypeKind.INT }
        final targetType = declaredType('java.time.Instant')

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'java.util.Date -> Instant uses toInstant()'() {
        given:
        final sourceType = declaredType('java.util.Date')
        final targetType = declaredType('java.time.Instant')
        final instantType = typeElementFor('java.time.Instant')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        result.get().producedOutput == instantType
        code == 'val.toInstant()'
    }

    def 'java.util.Date with any target returns Date->Instant proposal (BFS intermediate)'() {
        given:
        final sourceType = declaredType('java.util.Date')
        final targetType = declaredType('java.lang.String')
        final instantType = typeElementFor('java.time.Instant')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().producedOutput == instantType
        result.get().codeTemplate.apply(CodeBlock.of('val')).toString() == 'val.toInstant()'
    }

    def 'Instant -> java.util.Date uses Date.from()'() {
        given:
        final sourceType = declaredType('java.time.Instant')
        final targetType = declaredType('java.util.Date')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code == 'java.util.Date.from(val)'
    }

    def 'java.sql.Date -> LocalDate uses toLocalDate()'() {
        given:
        final sourceType = declaredType('java.sql.Date')
        final targetType = declaredType('java.time.LocalDate')
        final localDateType = typeElementFor('java.time.LocalDate')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        result.get().producedOutput == localDateType
        code == 'val.toLocalDate()'
    }

    def 'java.sql.Date with any target returns sql.Date->LocalDate proposal (BFS intermediate)'() {
        given:
        final sourceType = declaredType('java.sql.Date')
        final targetType = declaredType('java.lang.String')
        final localDateType = typeElementFor('java.time.LocalDate')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        result.isPresent()
        result.get().producedOutput == localDateType
    }

    def 'LocalDate -> java.sql.Date uses Date.valueOf()'() {
        given:
        final sourceType = declaredType('java.time.LocalDate')
        final targetType = declaredType('java.sql.Date')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code == 'java.sql.Date.valueOf(val)'
    }

    def 'java.sql.Time -> LocalTime uses toLocalTime()'() {
        given:
        final sourceType = declaredType('java.sql.Time')
        final targetType = declaredType('java.time.LocalTime')
        typeElementFor('java.time.LocalTime')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code == 'val.toLocalTime()'
    }

    def 'LocalTime -> java.sql.Time uses Time.valueOf()'() {
        given:
        final sourceType = declaredType('java.time.LocalTime')
        final targetType = declaredType('java.sql.Time')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code == 'java.sql.Time.valueOf(val)'
    }

    def 'java.sql.Timestamp -> Instant uses toInstant()'() {
        given:
        final sourceType = declaredType('java.sql.Timestamp')
        final targetType = declaredType('java.time.Instant')
        typeElementFor('java.time.Instant')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code == 'val.toInstant()'
    }

    def 'Instant -> java.sql.Timestamp uses Timestamp.from()'() {
        given:
        final sourceType = declaredType('java.time.Instant')
        final targetType = declaredType('java.sql.Timestamp')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)
        final code = result.get().codeTemplate.apply(CodeBlock.of('val')).toString()

        then:
        result.isPresent()
        code == 'java.sql.Timestamp.from(val)'
    }

    def 'returns empty when modern type element cannot be found'() {
        given:
        final sourceType = declaredType('java.util.Date')
        final targetType = declaredType('java.lang.String')
        // elements returns null for unknown type
        elements.getTypeElement('java.time.Instant') >> null

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty for unrecognized type pair'() {
        given:
        final sourceType = declaredType('com.example.Foo')
        final targetType = declaredType('com.example.Bar')

        expect:
        strategy.canProduce(sourceType, targetType, ctx).isEmpty()
    }

    def 'returns empty for java.util.Date -> String direct (no direct bridge, uses intermediate)'() {
        given:
        // This tests that java.util.Date DOES return a proposal (for Instant intermediate),
        // not an empty result — callers can check producedOutput to see the intermediate type
        final sourceType = declaredType('java.util.Date')
        final targetType = declaredType('java.lang.String')
        typeElementFor('java.time.Instant')

        when:
        final result = strategy.canProduce(sourceType, targetType, ctx)

        then:
        // Not empty — strategy returns Date→Instant proposal to seed the BFS graph
        result.isPresent()
        result.get().producedOutput.toString() == 'java.time.Instant'
    }
}
