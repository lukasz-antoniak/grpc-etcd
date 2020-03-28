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

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

public class HeaderServerInterceptor implements ServerInterceptor {
	public static final Metadata.Key<String> PORT_HEADER_KEY = Metadata.Key.of( "server_port", Metadata.ASCII_STRING_MARSHALLER );

	private final Integer port;

	public HeaderServerInterceptor(Integer port) {
		this.port = port;
	}

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
		return next.startCall( new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>( call ) {
			@Override
			public void sendHeaders(Metadata responseHeaders) {
				responseHeaders.put( PORT_HEADER_KEY, port.toString() );
				super.sendHeaders( responseHeaders );
			}
		}, headers);
	}
}
