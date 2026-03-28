## 1. Add @AutoService annotations to SPI implementations

- [x] 1.1 Add `@AutoService(SourcePropertyDiscovery.class)` to `GetterDiscovery`
- [x] 1.2 Add `@AutoService(TargetPropertyDiscovery.class)` to `ConstructorDiscovery`
- [x] 1.3 Add `@AutoService(SourcePropertyDiscovery.class)` to `FieldDiscovery.Source`
- [x] 1.4 Add `@AutoService(TargetPropertyDiscovery.class)` to `FieldDiscovery.Target`

## 2. Remove manual META-INF/services files

- [x] 2.1 Delete `processor/src/main/resources/META-INF/services/io.github.joke.percolate.processor.spi.SourcePropertyDiscovery`
- [x] 2.2 Delete `processor/src/main/resources/META-INF/services/io.github.joke.percolate.processor.spi.TargetPropertyDiscovery`

## 3. Fix ServiceLoader classloader

- [x] 3.1 Change `ServiceLoader.load(serviceClass)` to `ServiceLoader.load(serviceClass, DiscoverStage.class.getClassLoader())` in `DiscoverStage.loadAndSort()`

## 4. Verify

- [x] 4.1 Build the processor module and confirm AutoService generates META-INF/services entries in build output
- [x] 4.2 Run existing tests to verify no regressions
