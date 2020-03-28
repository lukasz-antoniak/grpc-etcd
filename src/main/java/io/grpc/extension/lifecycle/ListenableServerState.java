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

import io.grpc.Server;

// copied from https://gist.github.com/jhaber/1871b4ab87bb43bf86fa4cb580f88827
public abstract class ListenableServerState extends Server {
	public abstract ListenableServerState addStateListener(ServerStateListener listener);
}
