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

import com.google.gson.Gson;

public class EndpointMetadata {
	private static final Gson gson = new Gson();

	private final String dataCenter;
	private final long timestamp;

	public EndpointMetadata(Configuration options) {
		this.dataCenter = options.getDataCenter();
		this.timestamp = System.currentTimeMillis();
	}

	public static EndpointMetadata parseEtcdValue(String metadata) {
		return gson.fromJson( metadata, EndpointMetadata.class );
	}

	public String toEtcdValue() {
		return gson.toJson( this );
	}

	public String getDataCenter() {
		return dataCenter;
	}
}
