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

import org.junit.Assert;
import org.junit.Test;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.options.GetOption;
import io.grpc.Server;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.extension.loadbalancing.etcd.domain.Endpoint;
import io.grpc.extension.loadbalancing.etcd.domain.EndpointMetadata;

public class ServerTest extends BaseFunctionalTest {
	@Test
	public void testEndpointRegistration() throws Exception {
		checkServersRegistered( 1L );
	}

	@Test
	public void testEndpointRemoval() throws Exception {
		shutdownServer( gRpcServer );

		final ByteSequence key = ByteSequence.from(
				options.getKeyPrefix() + GreeterGrpc.SERVICE_NAME + "/", StandardCharsets.UTF_8
		);
		Assert.assertEquals(
				0L,
				etcdClient.getKVClient().get(
						key, GetOption.newBuilder().withCountOnly( true ).withPrefix( key ).build()
				).get().getCount()
		);
	}

	@Test
	public void testMultipleInstances() throws Exception {
		Server secondServer = null;
		try {
			secondServer = createAndStartServer();

			checkServersRegistered( 2L );
		}
		finally {
			if ( secondServer != null ) {
				shutdownServer( secondServer );
			}
		}
	}

	private void checkServersRegistered(long count) throws Exception {
		final ByteSequence key = ByteSequence.from(
				options.getKeyPrefix() + GreeterGrpc.SERVICE_NAME + "/", StandardCharsets.UTF_8
		);
		Assert.assertEquals(
				count,
				etcdClient.getKVClient().get(
						key, GetOption.newBuilder().withCountOnly( true ).withPrefix( key ).build()
				).get().getCount()
		);
		etcdClient.getKVClient().get( key, GetOption.newBuilder().withPrefix( key ).build() ).get().getKvs().forEach(
				kv -> {
					// make sure we can parse endpoint address
					final String entryKey = kv.getKey().toString( StandardCharsets.UTF_8 ).replace( options.getKeyPrefix(), "" );
					Assert.assertNotNull( Endpoint.parseEtcdKey( entryKey, options ) );
					Assert.assertNotNull( EndpointMetadata.parseEtcdValue( kv.getValue().toString( StandardCharsets.UTF_8 ) ) );
				}
		);
	}
}
