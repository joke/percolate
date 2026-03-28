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
        def sourceType = Mock(TypeMirror)
        def targetType = Stub(TypeMirror) { getKind() >> TypeKind.DECLARED }
        def param = Stub(VariableElement) { asType() >> sourceType }
        def mapAnnotation = Stub(Map) {
            source() >> 'firstName'
            target() >> 'givenName'
        }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.ABSTRACT] as Set)
            getParameters() >> [param]
            getReturnType() >> targetType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> mapAnnotation
        }
        def mapperType = Stub(TypeElement) { getEnclosedElements() >> [method] }

        when:
        def result = stage.execute(mapperType)

        then:
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
        def sourceType = Mock(TypeMirror)
        def targetType = Stub(TypeMirror) { getKind() >> TypeKind.DECLARED }
        def param = Stub(VariableElement) { asType() >> sourceType }
        def map1 = Stub(Map) { source() >> 'firstName'; target() >> 'givenName' }
        def map2 = Stub(Map) { source() >> 'lastName'; target() >> 'familyName' }
        def mapList = Stub(MapList) { value() >> ([map1, map2] as Map[]) }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.ABSTRACT] as Set)
            getParameters() >> [param]
            getReturnType() >> targetType
            getAnnotation(MapList) >> mapList
        }
        def mapperType = Stub(TypeElement) { getEnclosedElements() >> [method] }

        when:
        def result = stage.execute(mapperType)

        then:
        result.isSuccess()
        result.value().methods[0].directives.size() == 2
    }

    def 'ignores non-abstract methods'() {
        given:
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([] as Set)
        }
        def mapperType = Stub(TypeElement) { getEnclosedElements() >> [method] }

        when:
        def result = stage.execute(mapperType)

        then:
        result.isSuccess()
        result.value().methods.isEmpty()
    }

    def 'fails when method has no parameters'() {
        given:
        def targetType = Stub(TypeMirror) { getKind() >> TypeKind.DECLARED }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.ABSTRACT] as Set)
            getParameters() >> []
            getReturnType() >> targetType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> null
        }
        def mapperType = Stub(TypeElement) { getEnclosedElements() >> [method] }

        when:
        def result = stage.execute(mapperType)

        then:
        !result.isSuccess()
        result.errors().any { it.message.contains('source parameter') }
    }

    def 'fails when method has void return type'() {
        given:
        def voidType = Stub(TypeMirror) { getKind() >> TypeKind.VOID }
        def param = Stub(VariableElement) { asType() >> Mock(TypeMirror) }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.ABSTRACT] as Set)
            getParameters() >> [param]
            getReturnType() >> voidType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> null
        }
        def mapperType = Stub(TypeElement) { getEnclosedElements() >> [method] }

        when:
        def result = stage.execute(mapperType)

        then:
        !result.isSuccess()
        result.errors().any { it.message.contains('non-void return type') }
    }

    def 'method without @Map annotations produces empty directives'() {
        given:
        def sourceType = Mock(TypeMirror)
        def targetType = Stub(TypeMirror) { getKind() >> TypeKind.DECLARED }
        def param = Stub(VariableElement) { asType() >> sourceType }
        def method = Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getModifiers() >> ([Modifier.ABSTRACT] as Set)
            getParameters() >> [param]
            getReturnType() >> targetType
            getAnnotation(MapList) >> null
            getAnnotation(Map) >> null
        }
        def mapperType = Stub(TypeElement) { getEnclosedElements() >> [method] }

        when:
        def result = stage.execute(mapperType)

        then:
        result.isSuccess()
        result.value().methods[0].directives.isEmpty()
    }
}
