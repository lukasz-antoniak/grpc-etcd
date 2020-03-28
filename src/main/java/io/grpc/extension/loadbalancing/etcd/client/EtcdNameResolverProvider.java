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

import java.net.URI;

import io.etcd.jetcd.Client;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.extension.loadbalancing.etcd.domain.Configuration;

public class EtcdNameResolverProvider extends NameResolverProvider {
	private final Client client;
	private final Configuration options;

	public EtcdNameResolverProvider(Client client, Configuration options) {
		this.client = client;
		this.options = options;
	}

	@Override
	public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
		return new EtcdNameResolver( targetUri, args, client, options );
	}

	@Override
	protected boolean isAvailable() {
		return true;
	}

	@Override
	protected int priority() {
		return 5;
	}

	@Override
	public String getDefaultScheme() {
		return "etcd";
	}
}
