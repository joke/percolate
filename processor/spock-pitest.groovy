// Spock configuration used ONLY by the pitest minion JVMs (wired via -Dspock.configuration in the pitest block of
// the root build). It replaces the developer's global ~/.spock/SpockConfig.groovy, which enables parallel execution
// and optimizeRunOrder. pitest launches its own JVMs and does not inherit the Test task's parallel-disabling system
// properties, so without this the minions run the engine seam specs against the shared-static, non-thread-safe
// TypeUniverse javac concurrently — which deadlocks a spec and corrupts ~/.spock/RunHistory (the recurring
// TypeUniverse concurrency issue; see openspec/notes.md). Serialise everything here; the mockito mock-maker is kept
// because some specs mock final classes (e.g. MapperStep) which needs the inline maker.
mockMaker {
  preferredMockMaker spock.mock.MockMakers.mockito
}
runner {
  optimizeRunOrder false
  parallel {
    enabled false
  }
}
