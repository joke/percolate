package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.graph.AccessEdge
import io.github.joke.percolate.processor.graph.MappingEdge
import io.github.joke.percolate.processor.graph.SourcePropertyNode
import io.github.joke.percolate.processor.graph.SourceRootNode
import io.github.joke.percolate.processor.graph.TargetPropertyNode
import io.github.joke.percolate.processor.model.MapDirective
import io.github.joke.percolate.processor.model.MapperModel
import io.github.joke.percolate.processor.model.MappingMethodModel
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import java.util.EnumSet

@Tag('unit')
class BuildGraphStageSpec extends Specification {

    def 'directive creates source root, source property and target property connected by access and mapping edges'() {
        given:
        final method = methodWithDirective('src', emptyType(), emptyType(), 'firstName', 'givenName')
        final model = new MapperModel(Mock(TypeElement), [method])
        final stage = new BuildGraphStage()

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        final graph = result.value().methodGraphs[method]
        graph.vertexSet().any { it instanceof SourceRootNode && it.name == 'src' }
        graph.vertexSet().any { it instanceof SourcePropertyNode && it.name == 'firstName' }
        graph.vertexSet().any { it instanceof TargetPropertyNode && it.name == 'givenName' }
        graph.edgeSet().any { it instanceof AccessEdge }
        graph.edgeSet().any { it instanceof MappingEdge }
    }

    def 'nested chain creates multi-hop access edge path from source root to leaf'() {
        given:
        final method = methodWithDirective('src', emptyType(), emptyType(), 'address.street', 'street')
        final model = new MapperModel(Mock(TypeElement), [method])
        final stage = new BuildGraphStage()

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        final graph = result.value().methodGraphs[method]
        final sourceNodes = graph.vertexSet().findAll { it instanceof SourcePropertyNode }*.name
        sourceNodes.containsAll(['address', 'street'])
        graph.edgeSet().count { it instanceof AccessEdge } == 2
        graph.edgeSet().count { it instanceof MappingEdge } == 1
    }

    def 'shared prefix node is reused across two chains with same parent segment'() {
        given:
        final directives = [new MapDirective('address.street', 'street'), new MapDirective('address.city', 'city')]
        final method = new MappingMethodModel(methodElement('src'), emptyType(), emptyType(), directives)
        final model = new MapperModel(Mock(TypeElement), [method])
        final stage = new BuildGraphStage()

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        final graph = result.value().methodGraphs[method]
        final addressNodes = graph.vertexSet().findAll { it instanceof SourcePropertyNode && it.name == 'address' }
        addressNodes.size() == 1
        graph.edgeSet().count { it instanceof AccessEdge } == 3
    }

    def 'auto-maps same-name properties when no directive is declared'() {
        given:
        final sourceType = declaredType('name')
        final targetType = declaredType('name')
        final method = new MappingMethodModel(methodElement('src'), sourceType, targetType, [])
        final model = new MapperModel(Mock(TypeElement), [method])
        final stage = new BuildGraphStage()

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        final graph = result.value().methodGraphs[method]
        graph.edgeSet().any { it instanceof MappingEdge }
        graph.vertexSet().any { it instanceof SourcePropertyNode && it.name == 'name' }
        graph.vertexSet().any { it instanceof TargetPropertyNode && it.name == 'name' }
    }

    def 'explicit directive prevents auto-mapping of already-mapped target property'() {
        given:
        final targetType = declaredType('name')
        final method = new MappingMethodModel(methodElement('src'), emptyType(), targetType, [new MapDirective('fullName', 'name')])
        final model = new MapperModel(Mock(TypeElement), [method])
        final stage = new BuildGraphStage()

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        final graph = result.value().methodGraphs[method]
        final targetNode = graph.vertexSet().find { it instanceof TargetPropertyNode && it.name == 'name' }
        graph.inDegreeOf(targetNode) == 1
        graph.edgeSet().count { it instanceof MappingEdge } == 1
        (graph.getEdgeSource(graph.edgeSet().find { it instanceof MappingEdge }) as SourcePropertyNode).name == 'fullName'
    }

    def 'creates isolated graphs per method with no shared nodes'() {
        given:
        final method1 = methodWithDirective('srcA', emptyType(), emptyType(), 'a', 'b')
        final method2 = methodWithDirective('srcX', emptyType(), emptyType(), 'x', 'y')
        final model = new MapperModel(Mock(TypeElement), [method1, method2])
        final stage = new BuildGraphStage()

        expect:
        final result = stage.execute(model)
        result.isSuccess()
        result.value().methodGraphs.size() == 2
        final graph1 = result.value().methodGraphs[method1]
        final graph2 = result.value().methodGraphs[method2]
        !graph1.vertexSet().any { v -> graph2.vertexSet().contains(v) }
    }

    private MappingMethodModel methodWithDirective(
            final String paramName, final TypeMirror sourceType, final TypeMirror targetType,
            final String source, final String target) {
        return new MappingMethodModel(methodElement(paramName), sourceType, targetType, [new MapDirective(source, target)])
    }

    private ExecutableElement methodElement(final String paramName) {
        final param = Stub(VariableElement) {
            getSimpleName() >> Stub(Name) { toString() >> paramName }
        }
        return Stub(ExecutableElement) {
            getParameters() >> [param]
        }
    }

    private TypeMirror emptyType() {
        final te = Stub(TypeElement) { getEnclosedElements() >> [] }
        return Stub(DeclaredType) { asElement() >> te }
    }

    private TypeMirror declaredType(final String... propertyNames) {
        final members = propertyNames.collect { name -> getterElement(name) }
        final te = Stub(TypeElement) { getEnclosedElements() >> members }
        return Stub(DeclaredType) { asElement() >> te }
    }

    private ExecutableElement getterElement(final String propertyName) {
        final methodName = 'get' + propertyName.capitalize()
        Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> Stub(Name) { toString() >> methodName }
            getModifiers() >> EnumSet.of(Modifier.PUBLIC)
            getParameters() >> []
            getReturnType() >> Stub(TypeMirror) { getKind() >> TypeKind.DECLARED }
        }
    }
}
