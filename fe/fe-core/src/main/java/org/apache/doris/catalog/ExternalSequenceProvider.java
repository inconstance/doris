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

package org.apache.doris.catalog;

import org.apache.doris.common.UserException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

/** Adapter point between FE sequence allocation and an external sequence service. */
public interface ExternalSequenceProvider {
    Allocation allocate(Request request) throws UserException;

    /** The gRPC adapter should construct this request without adding FE-internal version fields. */
    class Request {
        private final long dbId;
        private final String dbName;
        private final long sequenceId;
        private final String sequenceName;
        private final long count;

        public Request(long dbId, String dbName, long sequenceId, String sequenceName, long count) {
            this.dbId = dbId;
            this.dbName = dbName;
            this.sequenceId = sequenceId;
            this.sequenceName = sequenceName;
            this.count = count;
        }

        public long getDbId() {
            return dbId;
        }

        public String getDbName() {
            return dbName;
        }

        public long getSequenceId() {
            return sequenceId;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public long getCount() {
            return count;
        }
    }

    /** A segment never crosses a CYCLE boundary; a wrapped allocation therefore contains multiple segments. */
    class Segment {
        private final BigInteger startValue;
        private final BigInteger increment;
        private final long count;

        public Segment(BigInteger startValue, BigInteger increment, long count) {
            this.startValue = startValue;
            this.increment = increment;
            this.count = count;
        }

        public BigInteger getStartValue() {
            return startValue;
        }

        public BigInteger getIncrement() {
            return increment;
        }

        public long getCount() {
            return count;
        }
    }

    class Allocation {
        private final List<Segment> segments;
        private final OptionalLong allocationId;

        public Allocation(List<Segment> segments, OptionalLong allocationId) {
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
            this.allocationId = allocationId;
        }

        public List<Segment> getSegments() {
            return segments;
        }

        public OptionalLong getAllocationId() {
            return allocationId;
        }
    }
}
