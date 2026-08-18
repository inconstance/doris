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
import java.util.List;

/** Validates external allocations and converts them to the existing FE-to-BE allocation model. */
public final class ExternalSequenceAllocator {
    private static final Object TICKET_LOCK = new Object();
    private static volatile ExternalSequenceProvider provider;
    private static long arrivalTicket;

    private ExternalSequenceAllocator() {
    }

    public static void setProvider(ExternalSequenceProvider externalProvider) {
        synchronized (TICKET_LOCK) {
            provider = externalProvider;
            arrivalTicket = 0;
        }
    }

    public static Sequence.AllocationResult allocate(String dbName, Sequence sequence,
            long count, long expectedVersion) throws UserException {
        ExternalSequenceProvider currentProvider = provider;
        if (currentProvider == null) {
            throw new UserException("External sequence provider is not initialized");
        }
        synchronized (sequence) {
            sequence.validateAllocationRequest(count, expectedVersion);
            List<Sequence.RangeSegment> segments = allocateSegments(
                    currentProvider, dbName, sequence, count);
            return new Sequence.AllocationResult(segments, sequence.getVersion(), nextArrivalTicket());
        }
    }

    private static long nextArrivalTicket() throws UserException {
        synchronized (TICKET_LOCK) {
            if (arrivalTicket == Long.MAX_VALUE) {
                throw new UserException("External sequence arrival ticket overflow");
            }
            return ++arrivalTicket;
        }
    }

    private static List<Sequence.RangeSegment> allocateSegments(ExternalSequenceProvider currentProvider,
            String dbName, Sequence sequence, long requestedCount) throws UserException {
        BigInteger increment = sequence.getIncrement();
        BigInteger min = sequence.getMinValue();
        BigInteger max = sequence.getMaxValue();
        BigInteger expectedStart = null;
        long remaining = requestedCount;
        List<Sequence.RangeSegment> result = new ArrayList<>(2);

        while (remaining > 0) {
            ExternalSequenceProvider.Response response = currentProvider.allocate(
                    new ExternalSequenceProvider.Request(dbName, sequence.getName(), remaining));
            if (response == null || response.getStart() == null || response.getIncrement() == null) {
                throw new UserException("External sequence provider returned an incomplete response");
            }
            if (!increment.equals(response.getIncrement())) {
                throw new UserException("External sequence response increment does not match sequence metadata");
            }
            if (response.getSize() <= 0 || response.getSize() > remaining) {
                throw new UserException("External sequence response size must be between 1 and " + remaining);
            }

            BigInteger start = response.getStart();
            BigInteger last = start.add(increment.multiply(BigInteger.valueOf(response.getSize() - 1)));
            checkInBounds(start, min, max);
            checkInBounds(last, min, max);

            if (expectedStart != null && !start.equals(expectedStart)) {
                throw new UserException("External sequence response did not restart at " + expectedStart);
            }
            result.add(new Sequence.RangeSegment(start, increment, response.getSize()));
            remaining -= response.getSize();
            if (remaining == 0) {
                break;
            }

            BigInteger next = last.add(increment);
            boolean crossedBoundary = increment.signum() > 0
                    ? next.compareTo(max) > 0 : next.compareTo(min) < 0;
            if (!crossedBoundary) {
                throw new UserException("External sequence provider returned fewer values before reaching the limit");
            }
            if (!sequence.isCycle()) {
                throw new UserException("Sequence " + sequence.getName() + " has insufficient values for allocation");
            }
            expectedStart = increment.signum() > 0 ? min : max;
        }
        return result;
    }

    private static void checkInBounds(BigInteger value, BigInteger min, BigInteger max) throws UserException {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new UserException("External sequence value " + value + " is outside [" + min + ", " + max + "]");
        }
    }
}
