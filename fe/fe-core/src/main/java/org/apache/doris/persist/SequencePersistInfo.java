// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.persist;

import org.apache.doris.catalog.Sequence;
import org.apache.doris.common.io.Text;
import org.apache.doris.common.io.Writable;
import org.apache.doris.persist.gson.GsonUtils;

import com.google.gson.annotations.SerializedName;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Journal payload shared by sequence create, drop and allocation-state operations. */
public class SequencePersistInfo implements Writable {
    @SerializedName("dbId")
    private long dbId;
    @SerializedName("sequenceId")
    private long sequenceId;
    @SerializedName("sequenceName")
    private String sequenceName;
    @SerializedName("sequenceVersion")
    private long sequenceVersion;
    @SerializedName("sequence")
    private Sequence sequence;
    @SerializedName("allocationState")
    private Sequence.AllocationState allocationState;

    private SequencePersistInfo() {
    }

    public static SequencePersistInfo create(Sequence sequence) {
        SequencePersistInfo info = new SequencePersistInfo();
        info.dbId = sequence.getDbId();
        info.sequenceId = sequence.getId();
        info.sequenceName = sequence.getName();
        info.sequenceVersion = sequence.getVersion();
        info.sequence = sequence;
        return info;
    }

    public static SequencePersistInfo drop(long dbId, long sequenceId, String sequenceName) {
        SequencePersistInfo info = new SequencePersistInfo();
        info.dbId = dbId;
        info.sequenceId = sequenceId;
        info.sequenceName = sequenceName;
        return info;
    }

    public static SequencePersistInfo state(long dbId, long sequenceId, long sequenceVersion,
            Sequence.AllocationState state) {
        SequencePersistInfo info = new SequencePersistInfo();
        info.dbId = dbId;
        info.sequenceId = sequenceId;
        info.sequenceVersion = sequenceVersion;
        info.allocationState = state;
        return info;
    }

    public long getDbId() {
        return dbId;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public long getSequenceVersion() {
        return sequenceVersion;
    }

    public Sequence getSequence() {
        return sequence;
    }

    public Sequence.AllocationState getAllocationState() {
        return allocationState;
    }

    public static SequencePersistInfo read(DataInput in) throws IOException {
        return GsonUtils.GSON.fromJson(Text.readString(in), SequencePersistInfo.class);
    }

    @Override
    public void write(DataOutput out) throws IOException {
        Text.writeString(out, GsonUtils.GSON.toJson(this));
    }
}
