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
import java.util.Arrays;
import java.util.Collections;
import java.util.OptionalLong;

class ExternalSequenceAllocatorTest {
    @Test
    void acceptsCycleSegmentsAndProviderAllocationId() throws Exception {
        Sequence sequence = new Sequence(10, 20, "seq", BigInteger.valueOf(8), BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 5, true);
        ExternalSequenceAllocator.setProvider(request -> new ExternalSequenceProvider.Allocation(Arrays.asList(
                new ExternalSequenceProvider.Segment(BigInteger.valueOf(8), BigInteger.ONE, 3),
                new ExternalSequenceProvider.Segment(BigInteger.ONE, BigInteger.ONE, 2)),
                OptionalLong.of(101)));

        Sequence.AllocationResult result = ExternalSequenceAllocator.allocate("db", sequence, 5, 1);
        Assertions.assertEquals(2, result.getSegments().size());
        Assertions.assertEquals(1, result.getSegments().get(1).getCycleEpoch());
        Assertions.assertEquals(101, result.getAllocationTicket());
        // The external provider is the cursor authority; FE does not advance or journal local allocation state.
        Assertions.assertEquals(BigInteger.valueOf(8), sequence.getNextValue());
    }

    @Test
    void assignsArrivalTicketWhenAllocationIdIsAbsent() throws Exception {
        Sequence sequence = new Sequence(10, 20, "seq", BigInteger.ONE, BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 2, false);
        ExternalSequenceAllocator.setProvider(request -> new ExternalSequenceProvider.Allocation(
                Collections.singletonList(new ExternalSequenceProvider.Segment(
                        BigInteger.ONE, BigInteger.ONE, request.getCount())), OptionalLong.empty()));

        long first = ExternalSequenceAllocator.allocate("db", sequence, 1, 1).getAllocationTicket();
        long second = ExternalSequenceAllocator.allocate("db", sequence, 1, 1).getAllocationTicket();
        Assertions.assertTrue(second > first);
    }

    @Test
    void rejectsInvalidCycleAndCountResponses() throws Exception {
        Sequence noCycle = new Sequence(10, 20, "seq", BigInteger.valueOf(9), BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 4, false);
        ExternalSequenceAllocator.setProvider(request -> new ExternalSequenceProvider.Allocation(Arrays.asList(
                new ExternalSequenceProvider.Segment(BigInteger.valueOf(9), BigInteger.ONE, 2),
                new ExternalSequenceProvider.Segment(BigInteger.ONE, BigInteger.ONE, 2)), OptionalLong.empty()));
        Assertions.assertThrows(UserException.class,
                () -> ExternalSequenceAllocator.allocate("db", noCycle, 4, 1));

        Sequence cycle = new Sequence(10, 20, "seq", BigInteger.valueOf(9), BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 4, true);
        ExternalSequenceAllocator.setProvider(request -> new ExternalSequenceProvider.Allocation(
                Collections.singletonList(new ExternalSequenceProvider.Segment(
                        BigInteger.valueOf(9), BigInteger.ONE, 2)), OptionalLong.empty()));
        Assertions.assertThrows(UserException.class,
                () -> ExternalSequenceAllocator.allocate("db", cycle, 4, 1));
    }
}
