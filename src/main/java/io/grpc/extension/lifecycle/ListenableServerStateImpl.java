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
package io.grpc.extension.lifecycle;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerServiceDefinition;

// copied from https://gist.github.com/jhaber/1871b4ab87bb43bf86fa4cb580f88827
public class ListenableServerStateImpl extends ListenableServerState {
	private enum State { NOT_STARTED, STARTED, STOPPING, STOPPED }

	private final Server delegate;
	private final List<ServerStateListener> listeners;
	private final AtomicReference<State> state;

	private ListenableServerStateImpl(Server delegate) {
		this.delegate = delegate;
		this.listeners = new CopyOnWriteArrayList<>();
		this.state = new AtomicReference<>( State.NOT_STARTED );
	}

	public static ListenableServerState decorate(Server server) {
		return new ListenableServerStateImpl( server );
	}

	@Override
	public ListenableServerState addStateListener(ServerStateListener listener) {
		listeners.add( new ServerListenerWrapper( listener ) );
		return this;
	}

	@Override
	public ListenableServerState start() throws IOException {
		delegate.start();
		transitionTo( State.STARTED );
		return this;
	}

	@Override
	public int getPort() {
		return delegate.getPort();
	}

	@Override
	public List<ServerServiceDefinition> getServices() {
		return delegate.getServices();
	}

	@Override
	public List<ServerServiceDefinition> getImmutableServices() {
		return delegate.getImmutableServices();
	}

	@Override
	public List<ServerServiceDefinition> getMutableServices() {
		return delegate.getMutableServices();
	}

	@Override
	public ListenableServerState shutdown() {
		delegate.shutdown();
		transitionTo( State.STOPPING );
		return this;
	}

	@Override
	public ListenableServerState shutdownNow() {
		delegate.shutdownNow();
		transitionTo( State.STOPPING );
		return this;
	}

	@Override
	public boolean isShutdown() {
		return delegate.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		if ( delegate.isTerminated() ) {
			transitionTo( State.STOPPED );
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		if ( delegate.awaitTermination( timeout, unit ) ) {
			transitionTo( State.STOPPED );
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public void awaitTermination() throws InterruptedException {
		delegate.awaitTermination();
		transitionTo( State.STOPPED );
	}

	private void transitionTo(State state) {
		State previous = this.state.getAndAccumulate( state, (a, b) -> a.ordinal() < b.ordinal() ? b : a );
		if ( previous != state ) {
			for ( ServerStateListener listener : listeners ) {
				switch ( state ) {
					case STARTED:
						listener.serverStarted( this );
						break;
					case STOPPING:
						listener.serverStopping( this );
						break;
					case STOPPED:
						listener.serverStopped( this );
						break;
					default:
						throw new IllegalArgumentException( "Unexpected state " + state );
				}
			}
		}
	}

	private static class ServerListenerWrapper implements ServerStateListener {
		private static final Logger log = LoggerFactory.getLogger( ServerListenerWrapper.class );

		private final ServerStateListener delegate;

		private ServerListenerWrapper(ServerStateListener delegate) {
			this.delegate = delegate;
		}

		@Override
		public void serverStarted(ListenableServerState server) {
			try {
				delegate.serverStarted( server );
			}
			catch (Throwable t) {
				log.error( "Exception calling server listener: {}", delegate, t );
			}
		}

		@Override
		public void serverStopping(ListenableServerState server) {
			try {
				delegate.serverStopping( server );
			}
			catch (Throwable t) {
				log.error( "Exception calling server listener: {}", delegate, t );
			}
		}

		@Override
		public void serverStopped(ListenableServerState server) {
			try {
				delegate.serverStopped( server );
			}
			catch (Throwable t) {
				log.error( "Exception calling server listener: {}", delegate, t );
			}
		}
	}
}
