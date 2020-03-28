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
package io.grpc.extension.test.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class HeaderClientInterceptor implements ClientInterceptor {
	public static final Map<Integer, AtomicInteger> requestCountPerInstance = new ConcurrentHashMap<>();

	@Override
	public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
		return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>( next.newCall( method, callOptions ) ) {
			@Override
			public void start(Listener<RespT> responseListener, Metadata headers) {
				super.start( new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>( responseListener ) {
					@Override
					public void onHeaders(Metadata headers) {
						final Integer port = Integer.parseInt( headers.get( HeaderServerInterceptor.PORT_HEADER_KEY ) );
						requestCountPerInstance.putIfAbsent( port, new AtomicInteger( 0 ) );
						requestCountPerInstance.get( port ).incrementAndGet();
						super.onHeaders( headers );
					}
				}, headers );
			}
		};
	}
}
