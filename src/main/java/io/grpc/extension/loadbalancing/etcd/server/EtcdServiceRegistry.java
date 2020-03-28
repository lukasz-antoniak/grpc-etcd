/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.grpc.extension.loadbalancing.etcd.server;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.CloseableClient;
import io.etcd.jetcd.Observers;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.PutOption;
import io.grpc.ServiceDescriptor;
import io.grpc.extension.lifecycle.ListenableServerState;
import io.grpc.extension.lifecycle.ServerStateListener;
import io.grpc.extension.loadbalancing.etcd.domain.Endpoint;
import io.grpc.extension.loadbalancing.etcd.domain.Configuration;
import io.grpc.extension.loadbalancing.etcd.domain.EndpointMetadata;
import io.grpc.stub.StreamObserver;

public class EtcdServiceRegistry implements ServerStateListener {
	private static final Logger log = LoggerFactory.getLogger( EtcdServiceRegistry.class );
	private static final StreamObserver<LeaseKeepAliveResponse> noOpObserver = Observers.observer( response -> {
	} );

	private final Client client;
	private final Configuration options;
	private final String hostName;

	private Long leaseId = null;
	private CloseableClient leaseWatcher = null;

	public EtcdServiceRegistry(Client client, Configuration options) {
		this.client = client;
		this.options = options;
		this.hostName = options.getHostNameResolver().resolveAddress();
	}

	@Override
	public void serverStarted(ListenableServerState server) {
		server.getServices().forEach( service -> registerEndpoint( server.getPort(), service.getServiceDescriptor() ) );
	}

	@Override
	public void serverStopping(ListenableServerState server) {
		server.getServices().forEach( service -> unregisterEndpoint( service.getServiceDescriptor() ) );
	}

	private void registerEndpoint(int port, ServiceDescriptor serviceDescriptor) {
		try {
			if ( leaseId == null ) {
				leaseId = client.getLeaseClient().grant( options.getLeaseTtlSec() ).get().getID();
				leaseWatcher = client.getLeaseClient().keepAlive( leaseId, noOpObserver );
			}
			log.debug( "Register gRPC service {} endpoint {}:{}", serviceDescriptor.getName(), hostName, port );
			client.getKVClient().put(
					serviceEndpointKey( port, serviceDescriptor ), serviceEndpointValue(),
					PutOption.newBuilder().withLeaseId( leaseId ).build()
			).get();
		}
		catch ( InterruptedException | ExecutionException e ) {
			log.error( "Failed to register gRPC service endpoint. Consider restarting your application!", e );
		}
	}

	private void unregisterEndpoint(ServiceDescriptor serviceDescriptor) {
		try {
			if ( leaseId != null ) {
				// if operation fails or times out, we do not need to eagerly retry it
				// since etcd will still remove associated keys once lease expires
				leaseWatcher.close();
				leaseWatcher = null;
				client.getLeaseClient().revoke( leaseId ).get();
				leaseId = null;
			}
		}
		catch ( InterruptedException | ExecutionException e ) {
			log.error( "Failed to unregister gRPC service endpoint", e );
		}
	}

	private ByteSequence serviceEndpointKey(int port, ServiceDescriptor serviceDescriptor) {
		return ByteSequence.from(
				options.getKeyPrefix() + new Endpoint( serviceDescriptor.getName(), hostName, port ).toEtcdKey( options ),
				StandardCharsets.UTF_8
		);
	}

	private ByteSequence serviceEndpointValue() {
		return ByteSequence.from(
				new EndpointMetadata( options ).toEtcdValue(), StandardCharsets.UTF_8
		);
	}
}
