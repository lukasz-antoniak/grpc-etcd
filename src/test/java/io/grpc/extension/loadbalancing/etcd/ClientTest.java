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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import io.etcd.jetcd.ByteSequence;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.extension.test.utils.HeaderClientInterceptor;

public class ClientTest extends BaseFunctionalTest {
	private ManagedChannel channel = null;
	private GreeterGrpc.GreeterBlockingStub client = null;

	public void setUp() throws Exception {
		super.setUp();
		channel = ManagedChannelBuilder.forTarget( "etcd://" + GreeterGrpc.SERVICE_NAME )
				.defaultLoadBalancingPolicy( "round_robin" )
				.usePlaintext().build();
		client = GreeterGrpc.newBlockingStub( channel ).withInterceptors( new HeaderClientInterceptor() );
		HeaderClientInterceptor.requestCountPerInstance.clear();
	}

	@Override
	public void tearDown() throws Exception {
		client = null;
		if ( channel != null && ! channel.isShutdown() ) {
			channel.shutdown().awaitTermination( 5, TimeUnit.SECONDS );
		}
		channel = null;
		super.tearDown();
	}

	@Test
	public void testContactSingleServer() {
		sendRequestAndCheckResponse();
	}

	@Test
	public void testServiceConfigApply() throws Exception {
		// setup service configuration
		final ByteSequence keyServiceConfig = ByteSequence.from( "services/helloworld.Greeter", StandardCharsets.UTF_8 );
		final ByteSequence valueServiceConfig = ByteSequence.from(
				Files.readAllBytes( Paths.get( getClass().getClassLoader().getResource( "test-service-config.json" ).toURI() ) )
		);
		etcdClient.getKVClient().put( keyServiceConfig, valueServiceConfig );

		Thread.sleep( 100 );

		Server secondServer = null;
		try {
			secondServer = createAndStartServer();
			for ( int i = 0; i < 100; ++i ) {
				sendRequestAndCheckResponse();
			}
			final Map<Integer, AtomicInteger> statistics = HeaderClientInterceptor.requestCountPerInstance;
			// service configuration enforced pick_first policy, so all requests should go to first instance
			Assert.assertEquals( 1, statistics.size() );
			// we have captured statistics of all requests
			Assert.assertEquals( 100, statistics.get( statistics.keySet().iterator().next() ).get() );
		}
		finally {
			if ( secondServer != null ) {
				shutdownServer( secondServer );
			}
		}

		etcdClient.getKVClient().delete( keyServiceConfig );
	}

	@Test
	public void testServiceConfigUpdateAtRuntime() throws Exception {
		sendRequestAndCheckResponse();

		Server secondServer = null;
		try {
			secondServer = createAndStartServer();
			for ( int i = 0; i < 100; ++i ) {
				sendRequestAndCheckResponse();
			}
			final Map<Integer, AtomicInteger> statistics = HeaderClientInterceptor.requestCountPerInstance;
			Assert.assertEquals( 2, statistics.size() );
			final Integer[] keys = statistics.keySet().toArray( new Integer[ 2 ] );
			// we have captured statistics of all requests
			final int firstInstanceCount = statistics.get( keys[0] ).get();
			final int secondInstanceCount = statistics.get( keys[1] ).get();
			Assert.assertEquals( 101, firstInstanceCount + secondInstanceCount );
			// distribution of round-robin policy should not differ much, let us assume 10%
			Assert.assertTrue( Math.abs( firstInstanceCount - secondInstanceCount ) < 10 );

			// update the service configuration, so that only first instance should receive traffic
			final ByteSequence keyServiceConfig = ByteSequence.from( "services/helloworld.Greeter", StandardCharsets.UTF_8 );
			final ByteSequence valueServiceConfig = ByteSequence.from(
					Files.readAllBytes( Paths.get( getClass().getClassLoader().getResource( "test-service-config.json" ).toURI() ) )
			);
			etcdClient.getKVClient().put( keyServiceConfig, valueServiceConfig );

			Thread.sleep( 100 );

			for ( int i = 0; i < 100; ++i ) {
				sendRequestAndCheckResponse();
			}
			// first or second instance received all traffic
			Assert.assertTrue(
					firstInstanceCount + 100 == statistics.get( keys[0] ).get()
					|| secondInstanceCount + 100 == statistics.get( keys[1] ).get()
			);

			etcdClient.getKVClient().delete( keyServiceConfig );
		}
		finally {
			if ( secondServer != null ) {
				shutdownServer( secondServer );
			}
		}
	}

	@Test
	public void testLoadBalancing() throws Exception {
		sendRequestAndCheckResponse();

		Server secondServer = null;
		try {
			secondServer = createAndStartServer();
			for ( int i = 0; i < 100; ++i ) {
				sendRequestAndCheckResponse();
			}
			final Map<Integer, AtomicInteger> statistics = HeaderClientInterceptor.requestCountPerInstance;
			Assert.assertEquals( 2, statistics.size() );
			final Integer[] keys = statistics.keySet().toArray( new Integer[ 2 ] );
			// we have captured statistics of all requests
			Assert.assertEquals( 101, statistics.get( keys[0] ).get() + statistics.get( keys[1] ).get() );
			// distribution of round-robin policy should not differ much, let us assume 10%
			Assert.assertTrue( Math.abs( statistics.get( keys[0] ).get() - statistics.get( keys[1] ).get() ) < 10 );
		}
		finally {
			if ( secondServer != null ) {
				shutdownServer( secondServer );
			}
		}
	}

	// first make some successful request, then we shutdown current server and start a new one
	// expectation is that application will be able to reconnect
	@Test
	public void testFailover() throws Exception {
		sendRequestAndCheckResponse();

		shutdownServer( gRpcServer );
		Server secondServer = null;
		try {
			secondServer = createAndStartServer();
			Thread.sleep( 100 );
			sendRequestAndCheckResponse();
			channel.shutdown().awaitTermination( 5, TimeUnit.SECONDS ); // not necessary, but just to cleanly shutdown client
		}
		finally {
			if ( secondServer != null ) {
				shutdownServer( secondServer );
			}
		}
	}

	@Test
	public void testLateServerStartup() throws Exception {
		shutdownServer( gRpcServer );
		Server server = null;

		final ManagedChannel channel = ManagedChannelBuilder.forTarget( "etcd://" + GreeterGrpc.SERVICE_NAME )
				.defaultLoadBalancingPolicy( "round_robin" ).usePlaintext().build();
		final GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub( channel );

		try {
			client.sayHello( HelloRequest.newBuilder().setName( "failed" ).build() );
			Assert.fail( "Runtime exception expected, no endpoints available." );
		}
		catch ( StatusRuntimeException e ) {
			Assert.assertEquals( e.getStatus().getCode(), Status.UNAVAILABLE.getCode() );
			Assert.assertTrue( e.getStatus().getDescription().contains( "NameResolver returned no usable address. addrs=[]" ) );
		}
		catch ( Exception e ) {
			Assert.fail( "Unexpected exception." );
		}

		try {
			// client is ready but server was nto started yet
			server = createAndStartServer();
			Thread.sleep( 100 );

			final String name = UUID.randomUUID().toString();
			final HelloReply reply = client.sayHello( HelloRequest.newBuilder().setName( name ).build() );
			Assert.assertEquals( "Hello " + name, reply.getMessage() );
		}
		finally {
			if ( server != null ) {
				shutdownServer( server );
			}
		}

		channel.shutdown().awaitTermination( 5, TimeUnit.SECONDS ); // not necessary, but just to cleanly shutdown client
	}

	@Test(expected = StatusRuntimeException.class)
	public void testServerUnavailable() throws Exception {
		shutdownServer( gRpcServer );
		sendRequestAndCheckResponse();
	}

	private void sendRequestAndCheckResponse() {
		final HelloReply reply = client.sayHello( HelloRequest.newBuilder().setName( "Lukasz" ).build() );
		Assert.assertEquals( "Hello Lukasz", reply.getMessage() );
	}
}
