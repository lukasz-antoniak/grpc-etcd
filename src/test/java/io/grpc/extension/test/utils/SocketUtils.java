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

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.net.ServerSocketFactory;

// copied from https://github.com/spring-projects/spring-framework/blob/master/spring-core/src/main/java/org/springframework/util/SocketUtils.java
// not to introduce additional dependencies
public abstract class SocketUtils {
	private static final int PORT_RANGE_MIN = 1024;
	private static final int PORT_RANGE_MAX = 65535;
	private static final Random random = new Random( System.currentTimeMillis() );

	public static int findAvailableTcpPort() {
		return findAvailableTcpPort( PORT_RANGE_MIN, PORT_RANGE_MAX );
	}

	public static int findAvailableTcpPort(int minPort, int maxPort) {
		return SocketType.TCP.findAvailablePort( minPort, maxPort );
	}

	public static int findAvailableUdpPort() {
		return findAvailableUdpPort( PORT_RANGE_MIN, PORT_RANGE_MAX );
	}

	public static int findAvailableUdpPort(int minPort, int maxPort) {
		return SocketType.UDP.findAvailablePort( minPort, maxPort );
	}

	private enum SocketType {
		TCP {
			@Override
			protected boolean isPortAvailable(int port) {
				try {
					ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket(
							port, 1, InetAddress.getByName( "localhost" )
					);
					serverSocket.close();
					return true;
				}
				catch (Exception ex) {
					return false;
				}
			}
		},
		UDP {
			@Override
			protected boolean isPortAvailable(int port) {
				try {
					DatagramSocket socket = new DatagramSocket( port, InetAddress.getByName( "localhost" ) );
					socket.close();
					return true;
				}
				catch (Exception ex) {
					return false;
				}
			}
		};

		protected abstract boolean isPortAvailable(int port);

		private int findRandomPort(int minPort, int maxPort) {
			int portRange = maxPort - minPort;
			return minPort + random.nextInt( portRange + 1 );
		}

		int findAvailablePort(int minPort, int maxPort) {
			int portRange = maxPort - minPort;
			int candidatePort;
			int searchCounter = 0;
			do {
				if ( searchCounter > portRange ) {
					throw new IllegalStateException( String.format(
							"Could not find an available %s port in the range [%d, %d] after %d attempts",
							name(), minPort, maxPort, searchCounter
					) );
				}
				candidatePort = findRandomPort( minPort, maxPort );
				searchCounter++;
			}
			while ( !isPortAvailable( candidatePort ) );

			return candidatePort;
		}

		SortedSet<Integer> findAvailablePorts(int numRequested, int minPort, int maxPort) {
			SortedSet<Integer> availablePorts = new TreeSet<>();
			int attemptCount = 0;
			while ( ( ++attemptCount <= numRequested + 100 ) && availablePorts.size() < numRequested ) {
				availablePorts.add( findAvailablePort( minPort, maxPort ) );
			}

			if ( availablePorts.size() != numRequested ) {
				throw new IllegalStateException( String.format(
						"Could not find %d available %s ports in the range [%d, %d]", numRequested, name(), minPort, maxPort
				) );
			}

			return availablePorts;
		}
	}
}
