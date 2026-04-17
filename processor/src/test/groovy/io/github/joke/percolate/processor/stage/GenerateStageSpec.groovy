package io.github.joke.percolate.processor.stage

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.LiftEdge
import io.github.joke.percolate.processor.graph.LiftKind
import io.github.joke.percolate.processor.graph.NullWidenEdge
import io.github.joke.percolate.processor.graph.PropertyNode
import io.github.joke.percolate.processor.graph.PropertyReadEdge
import io.github.joke.percolate.processor.graph.SourceParamNode
import io.github.joke.percolate.processor.graph.TargetSlotNode
import io.github.joke.percolate.processor.graph.TypeTransformEdge
import io.github.joke.percolate.processor.graph.TypedValueNode
import io.github.joke.percolate.processor.graph.ValueEdge
import io.github.joke.percolate.processor.graph.ValueNode
import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.match.MappingAssignment
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.match.ResolvedAssignment
import io.github.joke.percolate.processor.model.ConstructorParamAccessor
import io.github.joke.percolate.processor.model.FieldReadAccessor
import io.github.joke.percolate.processor.model.FieldWriteAccessor
import io.github.joke.percolate.processor.model.GetterAccessor
import io.github.joke.percolate.processor.model.MappingMethodModel
import io.github.joke.percolate.processor.model.WriteAccessor
import io.github.joke.percolate.processor.spi.TypeTransformStrategy
import org.jgrapht.alg.shortestpath.BFSShortestPath
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.Filer
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.tools.JavaFileObject

@Tag('unit')
class GenerateStageSpec extends Specification {

    final filer = Mock(Filer)
    final stage = new GenerateStage(filer)

    def 'NullWidenEdge on resolved path triggers IllegalStateException'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final paramElem  = makeParam('order', orderType)
        final getter     = new GetterAccessor('name', stringType, methodElement('getName'))
        final writer     = new ConstructorParamAccessor('name', stringType, Stub(ExecutableElement), 0)

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src   = new SourceParamNode(paramElem, orderType)
        final prop  = new PropertyNode('name', stringType, getter)
        final slot  = new TargetSlotNode('name', stringType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())
        graph.addEdge(prop, slot, new NullWidenEdge())

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final ra = new ResolvedAssignment(assignment, path, null)
        final matching = methodMatching(orderType, stringType, [paramElem], [assignment])

        when:
        stage.execute(mapperType('com.example', 'OrderMapper'), [(matching): [ra]])

        then:
        thrown(IllegalStateException)
    }

    def 'LiftEdge(NULL_CHECK) on resolved path triggers IllegalStateException'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final paramElem  = makeParam('order', orderType)
        final getter     = new GetterAccessor('name', stringType, methodElement('getName'))
        final writer     = new ConstructorParamAccessor('name', stringType, Stub(ExecutableElement), 0)

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src   = new SourceParamNode(paramElem, orderType)
        final prop  = new PropertyNode('name', stringType, getter)
        final slot  = new TargetSlotNode('name', stringType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())

        final innerGraph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final innerA = new TypedValueNode(stringType, 'A')
        final innerB = new TypedValueNode(stringType, 'B')
        innerGraph.addVertex(innerA); innerGraph.addVertex(innerB)
        innerGraph.addEdge(innerA, innerB,
                new TypeTransformEdge(Stub(TypeTransformStrategy), stringType, stringType, { it }))
        final innerPath = new BFSShortestPath<>(innerGraph).getPath(innerA, innerB)
        final liftEdge = new LiftEdge(LiftKind.NULL_CHECK, innerPath)
        liftEdge.codeTemplate = { it }
        graph.addEdge(prop, slot, liftEdge)

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final ra = new ResolvedAssignment(assignment, path, null)
        final matching = methodMatching(orderType, stringType, [paramElem], [assignment])

        when:
        stage.execute(mapperType('com.example', 'OrderMapper'), [(matching): [ra]])

        then:
        thrown(IllegalStateException)
    }

    def 'constructor body is generated for all-ConstructorParamAccessor assignments'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final paramElem  = makeParam('order', orderType)
        final getter     = new GetterAccessor('name', stringType, methodElement('getName'))
        final writer     = new ConstructorParamAccessor('name', stringType, Stub(ExecutableElement), 0)

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src   = new SourceParamNode(paramElem, orderType)
        final prop  = new PropertyNode('name', stringType, getter)
        final slot  = new TargetSlotNode('name', stringType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())
        final xfm = new TypeTransformEdge(
                Stub(TypeTransformStrategy), stringType, stringType, { input -> input })
        xfm.resolveTemplate()
        graph.addEdge(prop, slot, xfm)

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final ra = new ResolvedAssignment(assignment, path, null)
        final matching = methodMatching(orderType, stringType, [paramElem], [assignment])

        and:
        final captured = new StringWriter()
        final fileObj = Stub(JavaFileObject) {
            openWriter() >> captured
        }
        filer.createSourceFile(_, _) >> fileObj

        when:
        final result = stage.execute(mapperType('com.example', 'OrderMapper'), [(matching): [ra]])

        then:
        result.isSuccess()
        captured.toString().contains('return new')
        captured.toString().contains('order.getName()')
    }

    def 'field body is generated when target slot is FieldWriteAccessor'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final paramElem  = makeParam('order', orderType)
        final getter     = new GetterAccessor('name', stringType, methodElement('getName'))
        final writer     = new FieldWriteAccessor('name', stringType, Stub(VariableElement))

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src   = new SourceParamNode(paramElem, orderType)
        final prop  = new PropertyNode('name', stringType, getter)
        final slot  = new TargetSlotNode('name', stringType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())
        final xfm = new TypeTransformEdge(
                Stub(TypeTransformStrategy), stringType, stringType, { input -> input })
        xfm.resolveTemplate()
        graph.addEdge(prop, slot, xfm)

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final ra = new ResolvedAssignment(assignment, path, null)
        final matching = methodMatching(orderType, stringType, [paramElem], [assignment])

        and:
        final captured = new StringWriter()
        final fileObj = Stub(JavaFileObject) {
            openWriter() >> captured
        }
        filer.createSourceFile(_, _) >> fileObj

        when:
        final result = stage.execute(mapperType('com.example', 'OrderMapper'), [(matching): [ra]])

        then:
        result.isSuccess()
        captured.toString().contains('target.name = order.getName()')
        captured.toString().contains('return target')
    }

    def 'FieldReadAccessor in chain emits field-access syntax'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final orderType  = typeMirror('test.Order')
        final paramElem  = makeParam('order', orderType)
        final reader     = new FieldReadAccessor('name', stringType, Stub(VariableElement))
        final writer     = new ConstructorParamAccessor('name', stringType, Stub(ExecutableElement), 0)

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final src   = new SourceParamNode(paramElem, orderType)
        final prop  = new PropertyNode('name', stringType, reader)
        final slot  = new TargetSlotNode('name', stringType, writer)
        graph.addVertex(src); graph.addVertex(prop); graph.addVertex(slot)
        graph.addEdge(src, prop, new PropertyReadEdge())
        final xfm = new TypeTransformEdge(
                Stub(TypeTransformStrategy), stringType, stringType, { input -> input })
        xfm.resolveTemplate()
        graph.addEdge(prop, slot, xfm)

        final path = new BFSShortestPath<>(graph).getPath(src, slot)
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)
        final ra = new ResolvedAssignment(assignment, path, null)
        final matching = methodMatching(orderType, stringType, [paramElem], [assignment])

        and:
        final captured = new StringWriter()
        final fileObj = Stub(JavaFileObject) {
            openWriter() >> captured
        }
        filer.createSourceFile(_, _) >> fileObj

        when:
        stage.execute(mapperType('com.example', 'OrderMapper'), [(matching): [ra]])

        then:
        captured.toString().contains('order.name')
    }

    private MethodMatching methodMatching(
            final TypeMirror sourceType,
            final TypeMirror targetType,
            final List<VariableElement> params,
            final List<MappingAssignment> assignments) {
        final method = Stub(ExecutableElement) {
            getSimpleName() >> Stub(Name) { toString() >> 'map' }
            getParameters() >> params
        }
        final model = new MappingMethodModel(method, sourceType, targetType, [])
        new MethodMatching(method, model, assignments)
    }

    private VariableElement makeParam(final String name, final TypeMirror type) {
        Stub(VariableElement) {
            getSimpleName() >> Stub(Name) { toString() >> name }
            asType() >> type
        }
    }

    private ExecutableElement methodElement(final String name) {
        Stub(ExecutableElement) {
            getSimpleName() >> Stub(Name) { toString() >> name }
        }
    }

    private TypeElement mapperType(final String packageName, final String simpleName) {
        final pkg = Stub(PackageElement) {
            getQualifiedName() >> Stub(Name) { toString() >> packageName }
        }
        Stub(TypeElement) {
            getEnclosingElement() >> pkg
            getSimpleName() >> Stub(Name) { toString() >> simpleName }
            getQualifiedName() >> Stub(Name) { toString() >> "${packageName}.${simpleName}".toString() }
        }
    }

    private TypeMirror typeMirror(final String name) {
        Stub(TypeMirror) {
            toString() >> name
            accept(_, _) >> ClassName.get('java.lang', 'Object')
        }
    }
}
