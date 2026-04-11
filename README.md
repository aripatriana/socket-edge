# Socket Edge v3.0.0

## ISO 8583 TCP Load Balancer — High Performance Socket System

Socket Edge is a Netty-based ISO 8583 TCP message load balancer designed for payment processing environments. It supports dual-role (client & server), multiport, multisession, and active-passive clustering.

## What's New in v3.0

### Architecture Refactor
- **Eliminated circular dependency** between `SocketFactory` and `SocketManager`
- **Removed all static global state** — `SystemBootstrap.getConfig()` and `isCluster()` replaced with injectable `SystemConfig` record
- **New `ChannelGroup` abstraction** — explicit model for server↔client socket pairing
- **New `ChannelGroupRegistry`** — O(1) lookup replacing implicit name-based queries
- **New `SocketLifecycleCoordinator`** — extracted lifecycle orchestration from Netty handlers
- **Slim `SystemBootstrap`** — pure composition root with linear dependency graph

### Critical Bug Fixes
- **Fixed: OOM DoS vulnerability** — `LengthFieldBasedFrameDecoder` limited to 8KB (was `Integer.MAX_VALUE`)
- **Fixed: Blocking event loop** — `SocketChannel.send()` now non-blocking (removed `awaitUninterruptibly()`)
- **Fixed: Race condition** in `ServerInboundHandler.channelActive()` — async connect no longer followed by sync check
- **Fixed: Dead code** — proper pattern matching with early return in `channelRead()`
- **Fixed: Correlation key collision** — keys now use `field=value` format to prevent ambiguity
- **Fixed: Hardcoded ISO fields** — `IsoParser` now extracts ALL fields dynamically
- **Fixed: Inflight counter leak** — proper decrement in error/finally paths

### Code Quality
- Fixed typo: `SockeEndpointField` → `SocketEndpointField`
- Fixed typo: `validIPAddresss` → `validIPAddress`
- Fixed typo: `occured` → `occurred`
- Removed hardcoded developer Windows path
- PCI-DSS safe logging (no raw ISO message content)

## Dependency Graph (No Cycles)

```
SystemConfig (immutable record)
    ↓
ChannelGroupRegistry (pure registry)
    ↓
SocketLifecycleCoordinator (lifecycle orchestration)
    ↓
SocketFactory (socket creation)
    ↓
SocketManager (socket lifecycle)
    ↓
SEEngine (message routing — late-bind)
```

## Quick Start

```bash
# Build
mvn clean package -DskipTests

# Run standalone
java -Dserver.mode=standalone \
     -Dbase.dir=/path/to/resources \
     -jar target/socket-edge-3.0.0.jar

# Run cluster
java -Dserver.mode=cluster \
     -Dbase.dir=/path/to/resources \
     -jar target/socket-edge-3.0.0.jar
```

## Configuration

### system.conf
Core runtime configuration including server port, ISO packager, cache TTL, and SEDA pipeline settings.

### channel.conf
Channel definitions using custom DSL:
```
channel {
    name fello
    type tcp

    server {
        listen 127.0.0.1 27000
        pool 127.0.0.1
    }

    client {
        connect 127.0.0.1 26000 weight 100 priority 0 maxfails 3 failtimeout 30
        strategy roundrobin
    }

    profile iso8583
}

profile iso8583 {
    direction inbound {
        de1 in ["0800", "0200", "0420", "0421"]
    }
    direction outbound {
        de1 in ["0810", "0210", "0430"]
    }
    correlation {
        de2
        de11
        de37
        de13
        de12
    }
}
```

### cluster.conf
Cluster configuration for active-passive HA using JGroups + Hazelcast.

## HTTP Admin API

| Endpoint | Description |
|----------|-------------|
| `GET /healthcheck` | Health check with cluster role |
| `GET /socket/status?id=all` | Socket runtime state |
| `GET /socket/queues?id=all` | Message queue depths |
| `GET /socket/metrics?id=all` | Latency & TPS metrics |
| `GET /socket/start?id=<id>` | Start socket |
| `GET /socket/stop?id=<id>` | Stop socket |
| `GET /socket/restart?id=<id>` | Restart socket |
| `GET /config/validate` | Validate channel.conf changes |
| `GET /config/reload` | Hot-reload channel.conf |
| `GET /count-cache` | Correlation cache size |

## Tech Stack

- **Java 21** (Virtual Threads)
- **Netty 4.x** (TCP I/O)
- **Apache Camel** (SEDA routing)
- **jPOS** (ISO 8583 parsing)
- **JGroups** (Cluster leader election)
- **Hazelcast** (Distributed correlation store)
- **Micrometer** (Metrics/Telemetry)
- **Typesafe Config** (Configuration)

## Author

Ari Patriana — @aripatrianadev
