# Implementation Plan — Socket Edge Optimizations

## Goal

Implement all 13 valid optimization recommendations from the analysis. OPT-08 was removed after verification (the [validate()](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/strategy/SelectionStrategy.java#48-65) method is properly inherited from [SelectionStrategy](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/strategy/SelectionStrategy.java#31-66) default method).

---

## Proposed Changes

### Critical

---

#### [MODIFY] [SystemBootstrap.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/SystemBootstrap.java)

**OPT-01:** Remove hardcoded developer path from static block (line 199–209). Keep only the `base.dir` null-check validation.

**OPT-03:** Change `public static Config sc` to `private static volatile Config sc` and add a `public static Config getConfig()` accessor.

**OPT-07:** Fix circular null-reference by swapping construction order — create [SocketManager](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/socket/SocketManager.java#15-328) first (without factory), then [SocketFactory](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/socket/SocketFactory.java#36-119), then bind factory to manager.

**OPT-05:** Add `correlationStore` shutdown to the [handleLifecycle()](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/SystemBootstrap.java#513-555) shutdown hook.

---

#### [MODIFY] [DslParser.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/utils/DslParser.java)

**OPT-02:** Un-comment the [validateProfiles()](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/utils/DslParser.java#445-457) method body (lines 449–455).

**OPT-04:** Remove dead code block at lines 60–63 in [extractBlocks()](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/utils/DslParser.java#29-91).

---

### High Priority

---

#### [MODIFY] [CorrelationStore.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/cache/CorrelationStore.java)

**OPT-06:** Add `default void shutdown() {}` method to interface.

---

#### [MODIFY] [HazelcastCorrelationStore.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/cache/HazelcastCorrelationStore.java)

**OPT-06:** Add `@Override shutdown()` method (no-op since Hazelcast handles its own lifecycle).

---

#### [MODIFY] [SocketManager.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/socket/SocketManager.java)

**OPT-07:** Add a `bindSocketFactory(SocketFactory factory)` method to allow deferred factory binding.

---

#### [MODIFY] [SocketFactory.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/socket/SocketFactory.java)

**OPT-07:** Remove [SocketManager](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/socket/SocketManager.java#15-328) from constructor, add [bindSocketManager()](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/engine/SEEngine.java#125-136) setter.

---

### Medium Priority

---

#### [MODIFY] [NettyHttpServer.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/http/NettyHttpServer.java)

**OPT-09:** Change worker `NioEventLoopGroup` from 1 thread to 2 threads.

---

#### [MODIFY] [SEEngine.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/core/engine/SEEngine.java)

**OPT-11:** Add WARN log when falling back to a different channel than the original in outbound reply.

---

#### [MODIFY] [ConfigUtil.java](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/java/com/socket/edge/utils/ConfigUtil.java)

**OPT-03 + OPT-13:** Update import to use `SystemBootstrap.getConfig()` instead of static field access.

---

> [!NOTE]
> **OPT-10** (shared ScheduledExecutorService for client reconnection) and **OPT-12/14** (toString, Javadoc) are deferred as they require more invasive changes and have lower impact. They can be done in a follow-up iteration if desired.

---

## Verification Plan

### Automated Tests

The project has 18 existing test files with comprehensive coverage of the affected components. Existing tests should pass after the changes.

**Command to run all tests:**
```bash
cd "c:/Users/yohanes.kusumo/OneDrive - Jalin Pembayaran Nusantara/1. Apps/1. GitApp/23. socket-edge-dev/socket-edge-dev/socket-edge-dev"
mvn test
```

**Key tests that validate affected areas:**
- [DslParserTest](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/test/java/com/socket/edge/utils/DslParserTest.java#9-246) — validates DSL parsing and profile validation (OPT-02 will cause [shouldParseValidDsl()](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/test/java/com/socket/edge/utils/DslParserTest.java#13-61) to exercise the newly enabled validation)
- [CacheCorrelationStoreTest](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/test/java/com/socket/edge/core/cache/CacheCorrelationStoreTest.java#15-133) — validates TTL, cleanup, and concurrent access (OPT-05/06)
- `ChannelCfgSelectorTest`, `ChannelCfgProcessorTest` — config processing
- Strategy tests: `RoundRobinStrategyTest`, `LeastConnectionStrategyTest`, `HashStrategyTest`
- Transport tests: `ClientTransportTest`, `ServerTransportTest`, `TransportProviderTest`, `TransportRegisterTest`
- `SocketManagerTest`, `SocketChannelPoolTest`

### Manual Verification

Since these changes affect the core bootstrap sequence, runtime cluster configuration, and the DSL parser, the user should verify:

1. **Build succeeds**: Run `mvn clean package` and confirm the JAR builds without errors
2. **Startup in standalone mode**: Run the app with `-Dserver.mode=standalone -Dbase.dir=<path>` and confirm all components initialize
3. **Confirm profile validation**: Intentionally reference a non-existent profile in [channel.conf](file:///c:/Users/yohanes.kusumo/OneDrive%20-%20Jalin%20Pembayaran%20Nusantara/1.%20Apps/1.%20GitApp/23.%20socket-edge-dev/socket-edge-dev/socket-edge-dev/src/main/resources/conf/channel.conf) and verify the app now fails fast at startup with a clear error message
