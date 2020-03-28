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
package io.grpc.extension.loadbalancing.etcd.client;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import io.grpc.extension.loadbalancing.etcd.domain.Configuration;
import io.grpc.extension.loadbalancing.etcd.domain.Endpoint;
import io.grpc.extension.loadbalancing.etcd.domain.EndpointMetadata;
import io.grpc.internal.GrpcAttributes;
import io.grpc.internal.JsonParser;

public class EtcdNameResolver extends NameResolver {
	private static final Logger log = LoggerFactory.getLogger( EtcdNameResolver.class );
	private static final Attributes.Key<String> dataCenterAttribute = Attributes.Key.create( "dataCenter" );

	private final URI targetUri;
	private final Args args;
	private final Client client;
	private final Configuration options;
	private final AtomicReference<ConfigOrError> serviceConfig = new AtomicReference<>();
	private final AtomicReference<Map<String, ?>> rawServiceConfig = new AtomicReference<>(); // TODO(lantonia): Raw service configuration shall be removed when GRPC terminates its support.
	private final Map<String, EquivalentAddressGroup> endpoints = new HashMap<>();
	private Watch.Watcher watcher = null;

	public EtcdNameResolver(URI targetUri, Args args, Client client, Configuration options) {
		this.targetUri = targetUri;
		this.args = args;
		this.client = client;
		this.options = options;
	}

	@Override
	public void start(Listener2 listener) {
		// load initial state of available endpoints for given service
		final ByteSequence key = ByteSequence.from(
				options.getKeyPrefix() + extractServiceName( getServiceAuthority() ),
				StandardCharsets.UTF_8
		);
		try {
			client.getKVClient().get( key, GetOption.newBuilder().withPrefix( key ).build() ).get().getKvs().forEach( p -> {
				if ( key.equals( p.getKey() ) ) {
					// read service configuration
					final ConfigOrError parsed = parseServiceConfig( p.getValue() );
					serviceConfig.set( parsed );
				}
				else {
					// process registered endpoints
					final String k = p.getKey().toString( StandardCharsets.UTF_8 );
					endpoints.put( k, keyValueToAddressGroup( p ) );
				}
			} );
			final List<EquivalentAddressGroup> addresses = new ArrayList<>( endpoints.values() );
			log.debug( "Returning service configuration {}: {}", getServiceAuthority(), serviceConfig.get() );
			log.debug( "Returning endpoints for service {}: {}", getServiceAuthority(), addresses );
			listener.onResult(
					ResolutionResult.newBuilder().setAddresses( addresses )
							.setAttributes( Attributes.newBuilder().set( GrpcAttributes.NAME_RESOLVER_SERVICE_CONFIG, rawServiceConfig.get() ).build() )
							.setServiceConfig( serviceConfig.get() ).build()
			);
		}
		catch ( InterruptedException | ExecutionException e ) {
			throw Status.INTERNAL.withDescription(
					String.format( "Failed to initially discover service endpoints for %s", getServiceAuthority() )
			).withCause( e ).asRuntimeException();
		}

		// subscribe to state updates
		watcher = client.getWatchClient().watch( key, WatchOption.newBuilder().withPrefix( key ).build(), response -> {
			response.getEvents().forEach( event -> {
				if ( event.getKeyValue().getKey().equals( key ) ) {
					// service config
					final ConfigOrError parsed = parseServiceConfig( event.getKeyValue().getValue() );
					serviceConfig.set( parsed );
				}
				else {
					// registered endpoints
					final String address = event.getKeyValue().getKey().toString( StandardCharsets.UTF_8 );
					switch ( event.getEventType() ) {
						case PUT:
							endpoints.put( address, keyValueToAddressGroup( event.getKeyValue() ) );
							break;
						case DELETE:
							endpoints.remove( address );
							break;
					}
				}
			} );
			final List<EquivalentAddressGroup> addresses = new ArrayList<>( endpoints.values() );
			log.debug( "Updating service configuration {}: {}", getServiceAuthority(), serviceConfig.get() );
			log.debug( "Updating endpoints for service {}: {}", getServiceAuthority(), addresses );
			listener.onResult(
					ResolutionResult.newBuilder().setAddresses( addresses )
							.setAttributes( Attributes.newBuilder().set( GrpcAttributes.NAME_RESOLVER_SERVICE_CONFIG, rawServiceConfig.get() ).build() )
							.setServiceConfig( serviceConfig.get() ).build()
			);
		} );
	}

	private EquivalentAddressGroup keyValueToAddressGroup(KeyValue keyValue) {
		final String address = keyValue.getKey().toString( StandardCharsets.UTF_8 )
				.replace( options.getKeyPrefix(), "" );
		final String metadata = keyValue.getValue().toString( StandardCharsets.UTF_8 );
		final Attributes attributes = Attributes.newBuilder()
				.set( dataCenterAttribute, EndpointMetadata.parseEtcdValue( metadata ).getDataCenter() )
				.build();
		return new EquivalentAddressGroup( Endpoint.parseEtcdKey( address, options ).toSocketAddress(), attributes );
	}

	private String extractServiceName(String authority) {
		return authority.replace( "etcd://", "" );
	}

	private ConfigOrError parseServiceConfig(ByteSequence value) {
		final String text = value.toString( StandardCharsets.UTF_8 );
		if ( text == null || "".equals( text ) ) {
			return null;
		}
		try {
			final Map<String, ?> config = (Map<String, ?>) JsonParser.parse( text );
			rawServiceConfig.set( config );
			return args.getServiceConfigParser().parseServiceConfig( config );
		}
		catch ( IOException e ) {
			return NameResolver.ConfigOrError.fromError(
					Status.INTERNAL.withDescription( "Failed to parse service configuration of " + getServiceAuthority() ).withCause( e )
			);
		}
	}

	@Override
	public String getServiceAuthority() {
		return targetUri.getAuthority();
	}

	@Override
	public void shutdown() {
		if ( watcher != null ) {
			watcher.close();
			watcher = null;
		}
	}
}
