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
package io.grpc.extension.loadbalancing.etcd.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.grpc.Status;

public class Configuration {
	private static final long DEFAULT_LEASE_TTL_SEC = 10;
	private final long leaseTtlSec;

	private static final HostNameResolver DEFAULT_HOST_NAME_RESOLVER = new HostNameResolver() {};
	private final HostNameResolver hostNameResolver;

	private static final String DEFAULT_DATA_CENTER = "unknown";
	private final String dataCenter;

	private static final String DEFAULT_KEY_PREFIX = "";
	private final String keyPrefix;

	private static final String DEFAULT_KEY_SEPARATOR = "/";
	private final String keySeparator;

	private Configuration(long leaseTtlSec, HostNameResolver hostNameResolver, String dataCenter, String keyPrefix,
						  String keySeparator) {
		this.leaseTtlSec = leaseTtlSec;
		this.hostNameResolver = hostNameResolver;
		this.dataCenter = dataCenter;
		this.keyPrefix = ! "".equals( keyPrefix ) && ! keyPrefix.endsWith( keySeparator ) ? keyPrefix + keySeparator : keyPrefix;
		this.keySeparator = keySeparator;
	}

	public long getLeaseTtlSec() {
		return leaseTtlSec;
	}

	public HostNameResolver getHostNameResolver() {
		return hostNameResolver;
	}

	public String getDataCenter() {
		return dataCenter;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public String getKeySeparator() {
		return keySeparator;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private long leaseTtlSec = DEFAULT_LEASE_TTL_SEC;
		private HostNameResolver hostNameResolver = DEFAULT_HOST_NAME_RESOLVER;
		private String dataCenter = DEFAULT_DATA_CENTER;
		private String keyPrefix = DEFAULT_KEY_PREFIX;
		private String keySeparator = DEFAULT_KEY_SEPARATOR;

		public Builder withLeaseTtlSec(long leaseTtlSec) {
			this.leaseTtlSec = leaseTtlSec;
			return this;
		}

		public Builder withHostNameResolver(HostNameResolver hostNameResolver) {
			this.hostNameResolver = hostNameResolver;
			return this;
		}

		public Builder withDataCenter(String dataCenter) {
			this.dataCenter = dataCenter;
			return this;
		}

		public Builder withKeyPrefix(String keyPrefix) {
			this.keyPrefix = keyPrefix;
			return this;
		}

		public Builder withKeySeparator(String keySeparator) {
			this.keySeparator = keySeparator;
			return this;
		}

		public Configuration build() {
			return new Configuration(
					leaseTtlSec, hostNameResolver, dataCenter, keyPrefix, keySeparator
			);
		}
	}

	public interface HostNameResolver {
		default String resolveAddress() {
			try {
				return InetAddress.getLocalHost().getHostName();
			}
			catch (UnknownHostException e) {
				throw Status.INTERNAL.withDescription( "Unable to resolve host name" )
						.withCause( e ).asRuntimeException();
			}
		}
	}
}
