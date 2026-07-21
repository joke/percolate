# Changelog

## 1.0.0 (2026-07-21)


### ⚠ BREAKING CHANGES

* every published module's Maven artifactId drops the percolate- prefix (e.g. io.github.joke.percolate:bom, not :percolate-bom) - the group already disambiguates, and nothing has been published under the old coordinates yet. Also fixes the internal :dependencies platform leaking into spi's published POM at its source instead of via XML surgery.
* **spi:** relocate JavaPoet into percolate-owned package
* **processor:** the generated <Mapper>Impl class was previously unconditionally final; it is now final only when -Apercolate.classes.final=true is set.
* internal SPI — OperationSpec gains a required label, OperationCodegen.render drops the VarNames parameter, and the VarNames and LoopContainerCodegen types are removed.
* ResolveCtx drops mapperType()/currentMethod().
* internal SPI — Demand candidate semantics + binding name, Candidate nullness, single Container base/handle. No @Map or generated-code contract change; integration suite stays semantically equivalent (mapHuman, crossings, constants/defaults, type conversion).
* compose containers through an explicit Stream pipeline
* **processor:** cut over to the bipartite Value/Operation engine
* **spi:** reshape strategy SPI to OperationSpec; port builtins

### Features

* add expansion test harness and property-based tests for graph expansion ([010ae1d](https://github.com/joke/percolate/commit/010ae1dcaced273cf7cd45d6c311bd455d662e3c))
* add per-strategy unit specs and begin expansion-pruning redesign ([1553fe8](https://github.com/joke/percolate/commit/1553fe8a1763114e6c8652e212fb0f571a7b12a9))
* **annotations,spi,processor,strategies-builtin,docs:** add temporal (date/time) type mapping ([a8eec36](https://github.com/joke/percolate/commit/a8eec369c10e21a8b5e2a251ff9b4ce48722d85c))
* **codegen:** hoist assembly arguments to local variables ([6eb9c74](https://github.com/joke/percolate/commit/6eb9c7402ad2d558b1459ebadf59016a4925f14c))
* **codegen:** slot-derived hoisted-local names and configurable declaration style ([101666a](https://github.com/joke/percolate/commit/101666a57d03d1abd46051bc20fb9c4b64397c2e))
* compose containers through an explicit Stream pipeline ([5b500ad](https://github.com/joke/percolate/commit/5b500ad5f509cb2c42c6fbef7046530355a4c203))
* **docs,processor,spi,strategies-builtin,reactor,reactor-blocking:** make documentation drive the e2e test set ([3b60d30](https://github.com/joke/percolate/commit/3b60d3076e37660688dc5abbc2cd8dbd9ee9b167))
* **docs:** add Antora user manual hosted on GitHub Pages ([0ab56a0](https://github.com/joke/percolate/commit/0ab56a0ded2baf89be930706eff24400de7f7ffe))
* **docs:** adopt org.antora plugin + opt-in docTags generation ([168da2d](https://github.com/joke/percolate/commit/168da2df33511d5a563982a73acb1a39c8c6d164))
* **docs:** own example fixtures in their module via antora-collector ([19e4053](https://github.com/joke/percolate/commit/19e4053eb6201433c95a4359c785f2416363401a))
* **docs:** reactive container example owned by the reactor module ([95b0a37](https://github.com/joke/percolate/commit/95b0a3702ea33d12ee84e3bc7c6ba404ee350f9d))
* **docs:** single-source generated output from real generation ([d53d386](https://github.com/joke/percolate/commit/d53d3866d25db42a78fbae23cb6aa99c2ecdaf82))
* **engine:** candidate-free target-driven strategy SPI ([f4f1252](https://github.com/joke/percolate/commit/f4f1252499545676bf312fe09b17f9dccc40391e))
* **engine:** direct container-return mappers + ship reactor-blocking ([c8f6c30](https://github.com/joke/percolate/commit/c8f6c3096b0a7e7119c3056b82e3c5b1498c5a4d))
* **engine:** forward target-bound source-path descent ([4411fd2](https://github.com/joke/percolate/commit/4411fd2921a66f9b0f28dbf4f6238784d4c5126e))
* **engine:** grounding-by-match type-variable ports (spike-gated, additive) ([9016bc7](https://github.com/joke/percolate/commit/9016bc7caec57927c1a5dad6be37ec79e84b9bd6))
* **engine:** per-binding self-call rule + reactive grounding views ([9b702a4](https://github.com/joke/percolate/commit/9b702a4f154471d05b7115022362489768444687))
* **engine:** SourceProjection SPI + target-driven StreamMap functor-lift ([d496235](https://github.com/joke/percolate/commit/d496235491dd41cd7ba802322bfabf2d4bd01230))
* **engine:** target-driven container family + reuse-only ports ([e7b0e32](https://github.com/joke/percolate/commit/e7b0e32eb72847b07757f348f09a402df2bcf340))
* **engine:** unify port sourcing into an explicit mode ([bf7ff84](https://github.com/joke/percolate/commit/bf7ff846d3c66c6f7ce48ffd5cce547a6f3593d4))
* extract SPI and builtin strategies into separate modules ([998afea](https://github.com/joke/percolate/commit/998afeadbd76a1ce3033805815d33751e8a0d4d6))
* fold source-path descent into the work-list; delete SeedStage ([2123062](https://github.com/joke/percolate/commit/21230624303879cfcd10c48907c15f3c91776188))
* implement expansion-pruning redesign with satisfy() validation ([073053a](https://github.com/joke/percolate/commit/073053aacedaa6870435db60c1cbee46fc93dfbf))
* make expansion a uniform demand work-list; collapse Container SPI ([d52371f](https://github.com/joke/percolate/commit/d52371fa4ba9b7ee8a01b5b1b930ea6f5066e637))
* make graph dumps readable; sharpen the codegen SPI surface ([fe385fa](https://github.com/joke/percolate/commit/fe385fa96b4e295b24c55a4dcbc397e0490ae5cc))
* **packaging:** consumer starter + BOM and module-aligned e2e tests ([12bb6d8](https://github.com/joke/percolate/commit/12bb6d8d68f87967a27d884df8584326db92db52))
* **processor,architecture-tests:** decompose engine stages into testable collaborators ([45f6e8d](https://github.com/joke/percolate/commit/45f6e8da5a5629f0544fff3409640228b20e72c0))
* **processor:** add @Map constant and defaultValue support ([01245b0](https://github.com/joke/percolate/commit/01245b0e8ba0848dae0731cf26c1fcc4d5d62d3a))
* **processor:** add annotation processing framework with Dagger wiring ([80d2f36](https://github.com/joke/percolate/commit/80d2f367ceb92732e95463807c201e88e0cd8e5f))
* **processor:** add bipartite Value/Operation graph core ([c099d53](https://github.com/joke/percolate/commit/c099d536e62ff1ce1f86ef3be81f4fe13457d13e))
* **processor:** add container expansion with element-scope graph and SPI bridges ([7cf0755](https://github.com/joke/percolate/commit/7cf0755a3e00d8cefefdbda48633ab30a7acdfb7))
* **processor:** add discovery stages pipeline with diagnostics ([8f95391](https://github.com/joke/percolate/commit/8f95391b1cda7ce0d8e72cd3136010857bff7af8))
* **processor:** add expansion engine, strategy SPI, and validation stages ([6d34bf9](https://github.com/joke/percolate/commit/6d34bf940734183c8e1ba2bff19f69ba5bf2ab46))
* **processor:** add method-call bridge strategy and iterative expansion ([8965c02](https://github.com/joke/percolate/commit/8965c0220b6558fe78526482aed5c0848a87aca8))
* **processor:** add parameters.final/methods.final/classes.final compile-time switches ([c598b63](https://github.com/joke/percolate/commit/c598b634a6ee928f20dc2b17f872215aa6ebe4b4))
* **processor:** add seed-graph, debug dump, and graph model stages ([1bdadb6](https://github.com/joke/percolate/commit/1bdadb64f0b0c4d0cd3128129cb5cd75e34ae2b3))
* **processor:** bind seed chain realisation; remove GetterRead ([b3da571](https://github.com/joke/percolate/commit/b3da5719d4f9575a9c5388e87741b070f8a3da0a))
* **processor:** complete Path B nested-slot lifecycle; archive nullability ([3aa9d19](https://github.com/joke/percolate/commit/3aa9d191e4fa66202d4d4339875c4c01c1de56a4))
* **processor:** compose lossless primitive conversions (box/unbox/widen) ([4685947](https://github.com/joke/percolate/commit/4685947885a03b0d79b61a4ecbaaf61329b8965f))
* **processor:** cut over to the bipartite Value/Operation engine ([7befbc0](https://github.com/joke/percolate/commit/7befbc0e915790151c658d49a1dd13947086f60a))
* **processor:** defer unrealizable mappers across rounds ([1532b8b](https://github.com/joke/percolate/commit/1532b8b86384d051232aaecd741024951fb60db5))
* **processor:** discovery adapter — javax.lang.model → owned TypeSpace (Phase 2, increment A) ([1130325](https://github.com/joke/percolate/commit/1130325609f50cd4b2bbe7862373fb9108d29c89))
* **processor:** emit mapper implementations via GenerateStage ([28d1bac](https://github.com/joke/percolate/commit/28d1bacf55e5e99208159ab13304cd030294dd62))
* **processor:** implement processing pipeline with stages, graph model, and SPI discovery ([d547729](https://github.com/joke/percolate/commit/d5477295ba50587d5a0bae81268f01329e1afce2))
* **processor:** JSpecify-aware nullability via engine-managed paired typing ([695658c](https://github.com/joke/percolate/commit/695658cb04dbc92e4ccf6a1a693d4ee7681f6167))
* **processor:** let type-divergent overloaded constructors compete by per-type leaves ([f5f2972](https://github.com/joke/percolate/commit/f5f2972ad818f2731e43d762a8c17063d929a7bd))
* **processor:** refactor expansion phases to derive-then-apply discipline ([50fbe2d](https://github.com/joke/percolate/commit/50fbe2d4a73e8f6fde45cdb2693b6a0869add58b))
* **processor:** render codegen from a cheapest-SAT plan view ([7e4f38d](https://github.com/joke/percolate/commit/7e4f38d0b93d37537931cd19f2b14bdb864c2919))
* **processor:** render per-scope debug graphs via JGraphT DOTExporter ([fe45c1c](https://github.com/joke/percolate/commit/fe45c1c6a663ce5d99b7a2a05766da19a5b4576a))
* **processor:** route graph Value identity through the owned model (Phase 2, increment C) ([26bfd40](https://github.com/joke/percolate/commit/26bfd40b0be114b5687f6c6155a313c7fcad66c2))
* **processor:** split container bridges; multi-fire per-match groups ([ed1d332](https://github.com/joke/percolate/commit/ed1d332b5115baa457bd675ed1e8b276dced75d5))
* **processor:** unify SAT into a single cost-vector extraction fold ([654797d](https://github.com/joke/percolate/commit/654797dc3d937ea460ea798d80fb1c7106c78920))
* **processor:** wire the discovery adapter into production (Phase 2, increment B) ([a1011ed](https://github.com/joke/percolate/commit/a1011edc30ea744be03115ecffb6da6104dbdec9))
* **reactor:** Project Reactor (Flux/Mono) containers as a third-party SPI plugin ([a259f43](https://github.com/joke/percolate/commit/a259f43af5f172384322e50ce8dbff461bfc95bc))
* **spi,processor,strategies-builtin,reactor:** complete type-query-seam Phases 3-5 ([6f5289a](https://github.com/joke/percolate/commit/6f5289a48b96acf5f7e5171af4bb7f3d01434d0b))
* **spi,processor:** give the 2 permanently-real-mirror specs a private javac substrate (Phase 2, increment J) ([009bbe3](https://github.com/joke/percolate/commit/009bbe33eb573e5427b8bafa5095ef44450ae7d0))
* **spi,processor:** spike the ResolveCtx type-query seam — SourceCandidates + ListContainer (type-query-seam Phase 2, GO) ([1f1c3c8](https://github.com/joke/percolate/commit/1f1c3c89e13c70ae10cc15f412e2110fa6e9e360))
* **spi,strategies-builtin:** scope the TypeName emitter to wildcard-free sites (Phase 2, increment I) ([a44c0e8](https://github.com/joke/percolate/commit/a44c0e8fc27f6c987429733dd5b30494885e1b8b))
* **spi:** add OperationSpec/Demand strategy foundation ([6a45985](https://github.com/joke/percolate/commit/6a459859f8ca973930fa3a24bf936ecf38f65149))
* **spi:** add TypeRef bridge accessors to ProduceDemand/DescendDemand (Phase 2, increment K) ([f0510b5](https://github.com/joke/percolate/commit/f0510b53bc37c3bf9fd18581eaddcd5bf132f075))
* **spi:** add TypeRef-backed Containers/TypeProbe overloads (Phase 2, increment E) ([280b968](https://github.com/joke/percolate/commit/280b968e18e64d4b684a26637699a176a00306fc))
* **spi:** add unified ExpansionStrategy surface (unify-expansion-spi group 2) ([69bb575](https://github.com/joke/percolate/commit/69bb57506abf3354b11752c054d38f22d74db9ff))
* **spi:** container-codegen SPI + composer stream weaving ([124bb91](https://github.com/joke/percolate/commit/124bb91c867b079ee370f2cf54d7deb92787d97c))
* **spi:** conversion + accessor archetype bases ([845ac9e](https://github.com/joke/percolate/commit/845ac9e0602fbdb174c3a75f7dd8d3c627546756))
* **spi:** dual-type Port/OperationSpec with derived TypeRef fields (Phase 2, increment F) ([5c4fe25](https://github.com/joke/percolate/commit/5c4fe25f8446041b637bbb5fb677e0da7ecd269d))
* **spi:** give MethodCandidate an adapter-resolved MethodSig (Phase 2, task 3.2 increment) ([e834141](https://github.com/joke/percolate/commit/e8341410745eaaf2da92a2b97bce06bef4e738b4))
* **spi:** land the owned type model in spi — evict-javax-model Phase 1 ([44ec123](https://github.com/joke/percolate/commit/44ec1237a35c7b734f700dfe11ab482296a3264e))
* **spi:** relocate JavaPoet into percolate-owned package ([3b423a3](https://github.com/joke/percolate/commit/3b423a32778fa68b31ff6a0f7dbcbaf548f81107))
* **spi:** reshape strategy SPI to OperationSpec; port builtins ([fbf113c](https://github.com/joke/percolate/commit/fbf113cc758b4ffcfb9e2fdd03a86ec2349c85c2))
* **spi:** spike the owned type model — TypeRef/TypeSpace prototype (GO) ([faf9dfb](https://github.com/joke/percolate/commit/faf9dfb6700307168fc60c684f60c5330dfd2b98))
* **spi:** unify expansion strategies behind one ExpansionStrategy SPI ([e4a1c95](https://github.com/joke/percolate/commit/e4a1c9507665557671abc4d337bd735ec10e74e1))
* **strategies-builtin,reactor,reactor-blocking,spi:** wrap long generated fluent chains at call boundaries ([58b498b](https://github.com/joke/percolate/commit/58b498bf8b95978a6f0a6fba1cead9de1aca6582))
* **strategies-builtin,spi,architecture-tests:** cutover strategies-builtin unit specs to the mocked ResolveCtx seam ([c42a043](https://github.com/joke/percolate/commit/c42a043fc87614dfc7948b8987674a23f7e26f9c))
* subgraph-scoped expansion; cross-group fixed-point ([ee7b3eb](https://github.com/joke/percolate/commit/ee7b3ebad4e890cb167d5ee76d156846b8e95135))
* weight-based property discovery; method/field/getter resolvers ([1d78fa1](https://github.com/joke/percolate/commit/1d78fa1669dde40a30838e6db120724122794c55))
* wire jqwik into expansion property specs via ExpansionPropertyBase ([2d69f98](https://github.com/joke/percolate/commit/2d69f989ab2c7b42b453aa9f205978972e45bfa1))


### Bug Fixes

* **docs:** bracket whole generated methods in doc include-tags ([69af16c](https://github.com/joke/percolate/commit/69af16c6c99439b913e337b7dc33826bf9b13b7a))
* **processor:** align element scopes so container element maps weave correctly ([d8ffdca](https://github.com/joke/percolate/commit/d8ffdca978e77293338e01b1ba16c21be6767b8c))
* **processor:** name container element slots in codegen ([1d7dd5f](https://github.com/joke/percolate/commit/1d7dd5f26f3c1347083e9541d8d2576ea0e61aab))
* **processor:** name the target field in requireNonNull crossing messages ([41575d4](https://github.com/joke/percolate/commit/41575d4b476de6ca72a2e21f5ad4d7463d5e07d3))
* **processor:** resolve all PMD violations ([fdce40d](https://github.com/joke/percolate/commit/fdce40d51c32dbad1ce3e2d439414fca10f36e54))
* **processor:** restore container conversion for differing-type directive bindings ([52347e4](https://github.com/joke/percolate/commit/52347e47db51d16647d8e9af94d71b07605f03ec))
* **processor:** wire GenerateStage into Dagger; widen RealisedSubgraph.delegate ([cbdb75b](https://github.com/joke/percolate/commit/cbdb75bb5ffcc82fe07a908b856f39e63252eabd))


### Build System

* automate release/publishing and drop percolate- artifactId prefix ([a7db450](https://github.com/joke/percolate/commit/a7db45005ae0b5b94a06a2734a2aa98a2c3c3601))
