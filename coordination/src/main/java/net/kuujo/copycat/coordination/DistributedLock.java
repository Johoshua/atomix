/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.coordination;

import net.kuujo.copycat.Resource;
import net.kuujo.copycat.Stateful;
import net.kuujo.copycat.coordination.state.LockCommands;
import net.kuujo.copycat.coordination.state.LockState;
import net.kuujo.copycat.Raft;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Asynchronous lock.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@Stateful(LockState.class)
public class DistributedLock extends Resource implements AsyncLock {
  private final Queue<Consumer<Boolean>> queue = new ConcurrentLinkedQueue<>();

  public DistributedLock(Raft protocol) {
    super(protocol);
    protocol.session().onReceive(this::receive);
  }

  /**
   * Handles a received session message.
   */
  private void receive(boolean locked) {
    Consumer<Boolean> consumer = queue.poll();
    if (consumer != null) {
      consumer.accept(locked);
    }
  }

  @Override
  public CompletableFuture<Void> lock() {
    CompletableFuture<Void> future = new CompletableFuture<>();
    Consumer<Boolean> consumer = locked -> future.complete(null);
    queue.add(consumer);
    submit(LockCommands.Lock.builder().withTimeout(-1).build()).whenComplete((result, error) -> {
      if (error != null) {
        queue.remove(consumer);
      }
    });
    return future;
  }

  @Override
  public CompletableFuture<Boolean> tryLock() {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    Consumer<Boolean> consumer = future::complete;
    queue.add(consumer);
    submit(LockCommands.Lock.builder().build()).whenComplete((result, error) -> {
      if (error != null) {
        queue.remove(consumer);
      }
    });
    return future;
  }

  @Override
  public CompletableFuture<Boolean> tryLock(long time) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    Consumer<Boolean> consumer = future::complete;
    queue.add(consumer);
    submit(LockCommands.Lock.builder().withTimeout(time).build()).whenComplete((result, error) -> {
      if (error != null) {
        queue.remove(consumer);
      }
    });
    return future;
  }

  @Override
  public CompletableFuture<Boolean> tryLock(long time, TimeUnit unit) {
    return submit(LockCommands.Lock.builder().withTimeout(time, unit).build());
  }

  @Override
  public CompletableFuture<Void> unlock() {
    return submit(LockCommands.Unlock.builder().build());
  }

}
