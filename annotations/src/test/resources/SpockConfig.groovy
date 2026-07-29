mockMaker {
    preferredMockMaker spock.mock.MockMakers.mockito
}
runner {
    parallel {
        enabled true
    }
}
timeout {
    globalTimeout java.time.Duration.ofMinutes(1);
    applyGlobalTimeoutToFixtures false
}
