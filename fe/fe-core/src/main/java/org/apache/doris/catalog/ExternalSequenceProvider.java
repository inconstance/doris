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

/** Adapter point between FE sequence allocation and an external sequence service. */
public interface ExternalSequenceProvider {
    Response allocate(Request request) throws UserException;

    /** Request shape exposed by the existing external service. */
    class Request {
        private final String dbName;
        private final String sequenceName;
        private final long size;

        public Request(String dbName, String sequenceName, long size) {
            this.dbName = dbName;
            this.sequenceName = sequenceName;
            this.size = size;
        }

        public String getDbName() {
            return dbName;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public long getSize() {
            return size;
        }
    }

    /** A response is truncated at the current MINVALUE/MAXVALUE boundary. */
    class Response {
        private final BigInteger start;
        private final BigInteger increment;
        private final long size;

        public Response(BigInteger start, BigInteger increment, long size) {
            this.start = start;
            this.increment = increment;
            this.size = size;
        }

        public BigInteger getStart() {
            return start;
        }

        public BigInteger getIncrement() {
            return increment;
        }

        public long getSize() {
            return size;
        }
    }
}
