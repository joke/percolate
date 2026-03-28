package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.Map
import io.github.joke.percolate.MapList
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

@Tag('unit')
class AnalyzeStageSpec extends Specification {

    AnalyzeStage stage = new AnalyzeStage()

    def 'extracts abstract method with @Map annotation'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Stub(TypeMirror) { kind >> TypeKind.DECLARED }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final mapAnnotation = Stub(Map) {
            source() >> 'firstName'
            target() >> 'givenName'
        }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.ABSTRACT] as Set)
            parameters >> [param]
            returnType >> targetType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> mapAnnotation
        }
        final mapperType = Stub(TypeElement) { enclosedElements >> [method] }

        expect:
        final result = stage.execute(mapperType)
        result.isSuccess()
        result.value().methods.size() == 1
        result.value().methods[0].directives.size() == 1
        result.value().methods[0].directives[0].source == 'firstName'
        result.value().methods[0].directives[0].target == 'givenName'
        result.value().methods[0].sourceType == sourceType
        result.value().methods[0].targetType == targetType
    }

    def 'extracts method with multiple @Map annotations via @MapList'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Stub(TypeMirror) { kind >> TypeKind.DECLARED }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final map1 = Stub(Map) { source() >> 'firstName'; target() >> 'givenName' }
        final map2 = Stub(Map) { source() >> 'lastName'; target() >> 'familyName' }
        final mapList = Stub(MapList) { value() >> ([map1, map2] as Map[]) }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.ABSTRACT] as Set)
            parameters >> [param]
            returnType >> targetType
            getAnnotation(MapList) >> mapList
        }
        final mapperType = Stub(TypeElement) { enclosedElements >> [method] }

        expect:
        final result = stage.execute(mapperType)
        result.isSuccess()
        result.value().methods[0].directives.size() == 2
    }

    def 'ignores non-abstract methods'() {
        given:
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([] as Set)
        }
        final mapperType = Stub(TypeElement) { enclosedElements >> [method] }

        expect:
        final result = stage.execute(mapperType)
        result.isSuccess()
        result.value().methods.isEmpty()
    }

    def 'fails when method has no parameters'() {
        given:
        final targetType = Stub(TypeMirror) { kind >> TypeKind.DECLARED }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.ABSTRACT] as Set)
            parameters >> []
            returnType >> targetType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> null
        }
        final mapperType = Stub(TypeElement) { enclosedElements >> [method] }

        expect:
        final result = stage.execute(mapperType)
        !result.isSuccess()
        result.errors().any { it.message.contains('source parameter') }
    }

    def 'fails when method has void return type'() {
        given:
        final voidType = Stub(TypeMirror) { kind >> TypeKind.VOID }
        final param = Stub(VariableElement) { asType() >> Mock(TypeMirror) }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.ABSTRACT] as Set)
            parameters >> [param]
            returnType >> voidType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> null
        }
        final mapperType = Stub(TypeElement) { enclosedElements >> [method] }

        expect:
        final result = stage.execute(mapperType)
        !result.isSuccess()
        result.errors().any { it.message.contains('non-void return type') }
    }

    def 'method without @Map annotations produces empty directives'() {
        given:
        final sourceType = Mock(TypeMirror)
        final targetType = Stub(TypeMirror) { kind >> TypeKind.DECLARED }
        final param = Stub(VariableElement) { asType() >> sourceType }
        final method = Stub(ExecutableElement) {
            kind >> ElementKind.METHOD
            modifiers >> ([Modifier.ABSTRACT] as Set)
            parameters >> [param]
            returnType >> targetType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> null
        }
        final mapperType = Stub(TypeElement) { enclosedElements >> [method] }

        expect:
        final result = stage.execute(mapperType)
        result.isSuccess()
        result.value().methods[0].directives.isEmpty()
    }
}
