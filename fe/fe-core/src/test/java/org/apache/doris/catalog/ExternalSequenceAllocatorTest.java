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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

class ExternalSequenceAllocatorTest {
    @Test
    void assemblesTruncatedCycleResponses() throws Exception {
        Sequence sequence = new Sequence(10, 20, "seq", BigInteger.valueOf(8), BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 5, true);
        AtomicInteger calls = new AtomicInteger();
        ExternalSequenceAllocator.setProvider(request -> {
            Assertions.assertEquals("db", request.getDbName());
            Assertions.assertEquals("seq", request.getSequenceName());
            if (calls.getAndIncrement() == 0) {
                Assertions.assertEquals(5, request.getSize());
                return new ExternalSequenceProvider.Response(BigInteger.valueOf(8), BigInteger.ONE, 3);
            }
            Assertions.assertEquals(2, request.getSize());
            return new ExternalSequenceProvider.Response(BigInteger.ONE, BigInteger.ONE, 2);
        });

        Sequence.AllocationResult result = ExternalSequenceAllocator.allocate("db", sequence, 5, 1);
        Assertions.assertEquals(2, result.getSegments().size());
        Assertions.assertEquals(2, calls.get());
        // The external provider is the cursor authority; FE does not advance or journal local allocation state.
        Assertions.assertEquals(BigInteger.valueOf(8), sequence.getNextValue());
    }

    @Test
    void assignsArrivalTicketAfterCompleteAllocation() throws Exception {
        Sequence sequence = new Sequence(10, 20, "seq", BigInteger.ONE, BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 2, false);
        ExternalSequenceAllocator.setProvider(request ->
                new ExternalSequenceProvider.Response(BigInteger.ONE, BigInteger.ONE, request.getSize()));

        long first = ExternalSequenceAllocator.allocate("db", sequence, 1, 1).getAllocationTicket();
        long second = ExternalSequenceAllocator.allocate("db", sequence, 1, 1).getAllocationTicket();
        Assertions.assertTrue(second > first);
    }

    @Test
    void rejectsTruncatedNoCycleResponse() throws Exception {
        Sequence noCycle = new Sequence(10, 20, "seq", BigInteger.valueOf(9), BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 4, false);
        ExternalSequenceAllocator.setProvider(request ->
                new ExternalSequenceProvider.Response(BigInteger.valueOf(9), BigInteger.ONE, 2));
        Assertions.assertThrows(UserException.class,
                () -> ExternalSequenceAllocator.allocate("db", noCycle, 4, 1));
    }

    @Test
    void rejectsResponseTruncatedBeforeBoundary() throws Exception {
        Sequence cycle = new Sequence(10, 20, "seq", BigInteger.valueOf(9), BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 4, true);
        ExternalSequenceAllocator.setProvider(request ->
                new ExternalSequenceProvider.Response(BigInteger.valueOf(7), BigInteger.ONE, 2));
        Assertions.assertThrows(UserException.class,
                () -> ExternalSequenceAllocator.allocate("db", cycle, 4, 1));
    }

    @Test
    void assemblesNegativeCycleResponses() throws Exception {
        Sequence sequence = new Sequence(10, 20, "seq", BigInteger.valueOf(-9), BigInteger.ONE.negate(),
                BigInteger.TEN.negate(), BigInteger.ONE.negate(), 4, true);
        AtomicInteger calls = new AtomicInteger();
        ExternalSequenceAllocator.setProvider(request -> calls.getAndIncrement() == 0
                ? new ExternalSequenceProvider.Response(BigInteger.valueOf(-9), BigInteger.ONE.negate(), 2)
                : new ExternalSequenceProvider.Response(BigInteger.ONE.negate(), BigInteger.ONE.negate(), 2));

        Sequence.AllocationResult result = ExternalSequenceAllocator.allocate("db", sequence, 4, 1);
        Assertions.assertEquals(2, result.getSegments().size());
        Assertions.assertEquals(BigInteger.ONE.negate(), result.getSegments().get(1).getStartValue());
    }
}
