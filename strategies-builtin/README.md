# percolate-strategies-builtin

Ships the eleven `@AutoService`-registered built-in strategies for percolate: `DirectAssign`, `ListMap`, `ListWrap`, `SetMap`, `SetWrap`, `OptionalMap`, `OptionalUnwrap`, `OptionalWrap`, `MethodCallBridge`, `GetterRead`, `ConstructorCall`.

The module depends only on `percolate-spi` and uses Google `auto-service` to generate `META-INF/services` service registration files.

Included automatically by `percolate-processor`'s `runtimeOnly` declaration — end users get the built-ins transparently on their annotation-processor classpath. Users wanting custom-only setups may `exclude` this artifact:

```groovy
annotationProcessor ('io.github.joke.percolate:processor') {
    exclude group: 'io.github.joke.percolate', module: 'strategies-builtin'
}
```
