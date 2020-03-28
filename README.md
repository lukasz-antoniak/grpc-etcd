# gRPC Load Balancing with ETCD

[![License](https://img.shields.io/github/license/lukasz-antoniak/grpc-etcd.svg)](https://raw.githubusercontent.com/lukasz-antoniak/grpc-etcd/master/LICENSE)

The objective of hereby project is to enable service discovery and client-side load balancing with ETCD cluster acting
as _lookaside_ load balancer ([gRPC Blog](https://grpc.io/blog/grpc-load-balancing/#lookaside-load-balancing)).

All servers implementing given contract will create unique keys (with common prefix) in ETCD. Assume service
which fully qualified name equals `helloworld.Greeter`, two available instances may create keys `helloworld.Greeter/server1:3333`
and `helloworld.Greeter/server2:4444`. Clients connected to ETCD watch for updates to common prefix and act
accordingly, when new entry is added or existing removed.

## Quickstart

### Server Bootstrapping

Correct propagation of endpoint availability to ETCD requires notification about gRPC server lifecycle events like
startup or shutdown. Below listing shows how to decorate well-known server initialization with `ListenableServerState`.
Supply `EtcdServiceRegistry` that automatically handles instance registration and deregistration with service
discovery.

```java
// initialize ETCD client
Client etcdClient = Client.builder().endpoints( "http://localhost:2379" ).build();

// choose service discovery options
Configuration options = Configuration.builder().withKeyPrefix( "services" ).build()

// create ETCD service registry client
EtcdServiceRegistry serviceDiscovery = new EtcdServiceRegistry( etcdClient, options );

// bootstrap gRPC server
Server server = ListenableServerStateImpl.decorate(
        ServerBuilder.forPort( 8080 )
                .addService( new GreeterImpl() )
                .build()
).addStateListener( serviceDiscovery );
server.start();
```

### Client Initialization

Bootstrapping gRPC client is simpler and requires only providing correct `NameResolverProvider`.

```java
// initialize ETCD client
Client etcdClient = Client.builder().endpoints( "http://localhost:2379" ).build();

// choose service discovery options
Configuration options = Configuration.builder().withKeyPrefix( "services" ).build()

// create gRPC client
ManagedChannel channel = ManagedChannelBuilder.forTarget( "etcd://helloworld.Greeter" )
    .nameResolverFactory( new EtcdNameResolverProvider( etcdClient, options ) )
    .build();
```

## Documentation

### Configuration Options

Table describes client-side and server-side configuration options:

| Property Name  | Default | Description                                                                                                                                                                        |
|----------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `keyPrefix`    |         | Prefix added to ETCD which allows to distinguish service registry keys from other users of the cluster.                                                                            |
| `keySeparator` | `/`     | Character used as a separator between prefix, fully qualified service name and endpoint addresses.                                                                                 |
| `leaseTtlSec`  | `10`    | Time-to-live (in seconds) of ETCD lease associated with server key. If gRPC server does not send keep-alive on time, it will be considered dead and removed from service registry. |

### Propagating Service Configuration

[Service configuration](https://github.com/grpc/grpc/blob/master/doc/service_config.md) is a mechanism allowing service
owners to publish parameters that will be automatically propagated and used by all clients of their service.
ETCD clients listen not only to updates of service key children, but also to the key itself. Value stored under the key
can contain gRPC service configuration in JSON format. Users need to leverage external tools like `etcdctl` or any
ETCD client (e.g. `jetcd`) to manually update service configuration.

```shell script
$ export SERVICE_CONFIG="
{
   \"loadBalancingPolicy\": \"round_robin\",
   \"methodConfig\": [
      {
         \"name\": [
            {
               \"service\": \"helloworld.Greeter\",
               \"method\": \"SayHello\"
            }
         ],
         \"waitForReady\": false,
         \"retryPolicy\": {
            \"maxAttempts\": 3,
            \"initialBackoff\": \"2.1s\",
            \"maxBackoff\": \"2.2s\",
            \"backoffMultiplier\": 3,
            \"retryableStatusCodes\": [
               \"UNAVAILABLE\",
               \"RESOURCE_EXHAUSTED\"
            ]
         }
      }
   ],
   \"retryThrottling\": {
      \"maxTokens\": 10,
      \"tokenRatio\": 0.1
   }
}
"
$ etcdctl put helloworld.Greeter "$( echo $SERVICE_CONFIG )"
```

## Build from Source

Unit tests take advantage of test containers, so developers need to install and start Docker environment first.

```shell script
$ mvn clean install -DskipTests
$ mvn clean install
```
