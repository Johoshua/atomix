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
package net.kuujo.copycat.raft;

import net.kuujo.copycat.io.Buffer;
import net.kuujo.copycat.io.serializer.CopycatSerializable;
import net.kuujo.copycat.io.util.ReferenceCounted;
import net.kuujo.copycat.io.util.ReferenceManager;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Raft cluster member.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class RaftMember implements ReferenceCounted<RaftMember>, CopycatSerializable {

  /**
   * Returns a new Raft member builder.
   *
   * @return A new Raft member builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Raft member builder.
   */
  public static class Builder {
    private int id;
    private Type type;

    private Builder() {
    }

    /**
     * Sets the member ID.
     *
     * @param id The member ID.
     * @return The member builder.
     */
    public Builder withId(int id) {
      this.id = id;
      return this;
    }

    /**
     * Sets the member type.
     *
     * @param type The member type.
     * @return The member builder.
     */
    public Builder withType(Type type) {
      this.type = type;
      return this;
    }

    /**
     * Builds the member.
     *
     * @return A new Raft member.
     */
    public RaftMember build() {
      if (type == null)
        throw new NullPointerException("member type cannot be null");
      return new RaftMember(id, type);
    }
  }

  private final ReferenceManager<RaftMember> referenceManager;
  private final AtomicInteger references = new AtomicInteger();
  private Type type;
  private int id;
  private long version = 1;
  private long commitIndex;
  private long recycleIndex;

  /**
   * Raft member type.<p>
   *
   * The member type indicates how cluster members behave in terms of joining and leaving the cluster and how the
   * members participate in log replication. {@link Type#ACTIVE} members are full voting members of the cluster that
   * participate in Copycat's consensus protocol. {@link Type#PASSIVE} members may join and leave the cluster at will
   * without impacting the availability of a resource and receive only committed log entries via a gossip protocol.
   */
  public static enum Type {

    /**
     * Indicates that the member is a remote client of the cluster.
     */
    REMOTE(-1),

    /**
     * Indicates that the member is a passive, non-voting member of the cluster.
     */
    PASSIVE(0),

    /**
     * Indicates that the member is an active voting member of the cluster.
     */
    ACTIVE(1);

    /**
     * Returns the type for the given identifier.
     *
     * @param id The type identifier.
     * @return The member type.
     */
    public static Type forId(int id) {
      switch (id) {
        case -1:
          return REMOTE;
        case 0:
          return PASSIVE;
        case 1:
          return ACTIVE;
      }
      throw new IllegalArgumentException("invalid member type identifier");
    }

    private final byte id;

    private Type(int id) {
      this.id = (byte) id;
    }

    /**
     * Returns the type identifier.
     *
     * @return The unique type identifier.
     */
    public byte id() {
      return id;
    }
  }

  protected RaftMember(ReferenceManager<RaftMember> referenceManager) {
    this.referenceManager = referenceManager;
  }

  RaftMember(int id, Type type) {
    this.referenceManager = null;
    this.id = id;
    this.type = type;
  }

  /**
   * Resets the member.
   */
  protected void reset() {
    references.set(1);
  }

  /**
   * Returns the member ID.
   *
   * @return The member ID.
   */
  public int id() {
    return id;
  }

  /**
   * Returns the member type.
   *
   * @return The member type.
   */
  public Type type() {
    return type;
  }

  /**
   * Returns the member version.
   *
   * @return The member version.
   */
  public long version() {
    return version;
  }

  /**
   * Sets the member version.
   *
   * @param version The member version.
   * @return The member info.
   */
  RaftMember version(long version) {
    this.version = version;
    return this;
  }

  /**
   * Returns the member's commit index.
   *
   * @return The member's commit index.
   */
  public long commitIndex() {
    return commitIndex;
  }

  /**
   * Sets the member index.
   *
   * @param index The member's commit index.
   * @return The member info.
   */
  RaftMember commitIndex(long index) {
    this.commitIndex = index;
    return this;
  }

  /**
   * Returns the member's recycle index.
   *
   * @return The member's recycle index.
   */
  public long recycleIndex() {
    return recycleIndex;
  }

  /**
   * Sets the member's recycle index.
   *
   * @param index The member's recycle index.
   * @return The member info.
   */
  RaftMember recycleIndex(long index) {
    this.recycleIndex = index;
    return this;
  }

  /**
   * Updates the member info.
   *
   * @param info The member info to update.
   * @return Indicates whether the member's state was updated.
   */
  boolean update(RaftMember info) {
    if (info.version > this.version) {
      this.type = info.type;
      this.version = info.version;
      this.commitIndex = info.commitIndex;
      this.recycleIndex = info.recycleIndex;
      return true;
    }
    return false;
  }

  @Override
  public RaftMember acquire() {
    references.incrementAndGet();
    return this;
  }

  @Override
  public void release() {
    if (references.decrementAndGet() == 0)
      close();
  }

  @Override
  public int references() {
    return references.get();
  }

  @Override
  public void readObject(Buffer buffer) {
    id = buffer.readInt();
    type = RaftMember.Type.forId(buffer.readByte());
    version = buffer.readLong();
    commitIndex = buffer.readLong();
    recycleIndex = buffer.readLong();
  }

  @Override
  public void writeObject(Buffer buffer) {
    buffer.writeInt(id)
      .writeByte(type.id())
      .writeLong(version)
      .writeLong(commitIndex)
      .writeLong(recycleIndex);
  }

  @Override
  public void close() {
    referenceManager.release(this);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof RaftMember) {
      RaftMember member = (RaftMember) object;
      return member.id == id
        && member.type == type
        && member.version == version
        && member.commitIndex == commitIndex
        && member.recycleIndex == recycleIndex;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hashCode = 17;
    hashCode = 37 * hashCode + id;
    hashCode = 37 * hashCode + type.hashCode();
    hashCode = 37 * hashCode + (int)(version ^ (version >>> 32));
    hashCode = 37 * hashCode + (int)(commitIndex ^ (commitIndex >>> 32));
    hashCode = 37 * hashCode + (int)(recycleIndex ^ (recycleIndex >>> 32));
    return hashCode;
  }

  @Override
  public String toString() {
    return String.format("RaftMember[id=%s, type=%s, version=%d, commitIndex=%d, recycleIndex=%d]", id, type, version, commitIndex, recycleIndex);
  }

}
