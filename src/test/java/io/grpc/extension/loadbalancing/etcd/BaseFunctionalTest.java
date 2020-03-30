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
package io.grpc.extension.loadbalancing.etcd;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.launcher.junit4.EtcdClusterResource;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.examples.helloworld.GreeterImpl;
import io.grpc.extension.lifecycle.ListenableServerStateImpl;
import io.grpc.extension.loadbalancing.etcd.client.EtcdNameResolverProvider;
import io.grpc.extension.loadbalancing.etcd.server.EtcdServiceRegistry;
import io.grpc.extension.loadbalancing.etcd.domain.Configuration;
import io.grpc.extension.test.utils.HeaderServerInterceptor;
import io.grpc.extension.test.utils.SocketUtils;

public abstract class BaseFunctionalTest {
	static {
		System.setProperty( "io.grpc.internal.ManagedChannelImpl.enableServiceConfigErrorHandling", "true" );
	}

	@Rule
	public final EtcdClusterResource etcd = new EtcdClusterResource( "test-etcd", 1 );
	protected Client etcdClient = null;
	protected EtcdNameResolverProvider nameResolver = null;
	protected Server gRpcServer = null;
	protected final Configuration options = Configuration.builder().withKeyPrefix( "services" ).build();

	@Before
	public void setUp() throws Exception {
		etcdClient = Client.builder().endpoints( etcd.getClientEndpoints() ).build();
		nameResolver = new EtcdNameResolverProvider( etcdClient, options );
		NameResolverRegistry.getDefaultRegistry().register( nameResolver );
		gRpcServer = createAndStartServer();
	}

	@After
	public void tearDown() throws Exception {
		if ( gRpcServer != null && ! gRpcServer.isShutdown() ) {
			shutdownServer( gRpcServer );
			gRpcServer = null;
		}
		if ( nameResolver != null ) {
			NameResolverRegistry.getDefaultRegistry().deregister( nameResolver );
			nameResolver = null;
		}
		if ( etcdClient != null ) {
			etcdClient.close();
		}
	}

	protected Server createAndStartServer() throws Exception {
		final EtcdServiceRegistry serviceDiscovery = new EtcdServiceRegistry( etcdClient, options );
		int port = SocketUtils.findAvailableTcpPort();
		final Server server = ListenableServerStateImpl.decorate(
				ServerBuilder.forPort( port )
						.addService( new GreeterImpl() )
						.intercept( new HeaderServerInterceptor( port ) )
						.build()
		).addStateListener( serviceDiscovery );
		server.start();
		return server;
	}

	protected void shutdownServer(Server server) throws Exception {
		server.shutdown();
		server.awaitTermination();
	}
}
