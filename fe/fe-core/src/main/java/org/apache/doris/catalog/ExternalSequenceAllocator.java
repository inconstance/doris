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
import java.util.OptionalLong;

/** Validates external allocations and converts them to the existing FE-to-BE allocation model. */
public final class ExternalSequenceAllocator {
    private static final Object TICKET_LOCK = new Object();
    private static volatile ExternalSequenceProvider provider;
    private static long arrivalTicket;
    private static Boolean providerUsesAllocationId;

    private ExternalSequenceAllocator() {
    }

    public static void setProvider(ExternalSequenceProvider externalProvider) {
        synchronized (TICKET_LOCK) {
            provider = externalProvider;
            arrivalTicket = 0;
            providerUsesAllocationId = null;
        }
    }

    public static Sequence.AllocationResult allocate(String dbName, Sequence sequence,
            long count, long expectedVersion) throws UserException {
        sequence.validateAllocationRequest(count, expectedVersion);
        ExternalSequenceProvider currentProvider = provider;
        if (currentProvider == null) {
            throw new UserException("External sequence provider is not initialized");
        }

        ExternalSequenceProvider.Request request = new ExternalSequenceProvider.Request(
                sequence.getDbId(), dbName, sequence.getId(), sequence.getName(), count);
        ExternalSequenceProvider.Allocation allocation = currentProvider.allocate(request);
        if (allocation == null) {
            throw new UserException("External sequence provider returned a null allocation");
        }
        List<Sequence.RangeSegment> segments = validateSegments(sequence, allocation.getSegments(), count);
        OptionalLong allocationId = allocation.getAllocationId();
        long ticket = allocationTicket(allocationId);
        return new Sequence.AllocationResult(segments, sequence.getVersion(), ticket);
    }

    private static long allocationTicket(OptionalLong allocationId) throws UserException {
        if (allocationId.isPresent() && allocationId.getAsLong() < 0) {
            throw new UserException("External sequence allocationId must not be negative");
        }
        synchronized (TICKET_LOCK) {
            boolean usesAllocationId = allocationId.isPresent();
            if (providerUsesAllocationId != null && providerUsesAllocationId != usesAllocationId) {
                throw new UserException("External sequence provider must consistently return allocationId or omit it");
            }
            providerUsesAllocationId = usesAllocationId;
            if (usesAllocationId) {
                return allocationId.getAsLong();
            }
            // Without a provider allocationId, successful responses are ordered by their arrival at this FE.
            if (arrivalTicket == Long.MAX_VALUE) {
                throw new UserException("External sequence arrival ticket overflow");
            }
            return ++arrivalTicket;
        }
    }

    private static List<Sequence.RangeSegment> validateSegments(Sequence sequence,
            List<ExternalSequenceProvider.Segment> externalSegments, long requestedCount) throws UserException {
        if (externalSegments == null || externalSegments.isEmpty()) {
            throw new UserException("External sequence provider returned no segments");
        }
        BigInteger increment = sequence.getIncrement();
        BigInteger min = sequence.getMinValue();
        BigInteger max = sequence.getMaxValue();
        BigInteger expectedStart = null;
        long total = 0;
        long epoch = sequence.getCycleEpoch();
        List<Sequence.RangeSegment> result = new ArrayList<>(externalSegments.size());

        for (ExternalSequenceProvider.Segment segment : externalSegments) {
            if (segment == null || segment.getStartValue() == null || segment.getIncrement() == null) {
                throw new UserException("External sequence provider returned an incomplete segment");
            }
            if (!increment.equals(segment.getIncrement())) {
                throw new UserException("External sequence segment increment does not match sequence metadata");
            }
            if (segment.getCount() <= 0) {
                throw new UserException("External sequence segment count must be positive");
            }
            try {
                total = Math.addExact(total, segment.getCount());
            } catch (ArithmeticException e) {
                throw new UserException("External sequence allocation count overflow");
            }

            BigInteger start = segment.getStartValue();
            BigInteger last = start.add(increment.multiply(BigInteger.valueOf(segment.getCount() - 1)));
            checkInBounds(start, min, max);
            checkInBounds(last, min, max);

            if (expectedStart != null) {
                boolean crossedBoundary = increment.signum() > 0
                        ? expectedStart.compareTo(max) > 0 : expectedStart.compareTo(min) < 0;
                BigInteger requiredStart = crossedBoundary ? (increment.signum() > 0 ? min : max) : expectedStart;
                if (!start.equals(requiredStart) || (crossedBoundary && !sequence.isCycle())) {
                    throw new UserException("External sequence segments are not contiguous");
                }
                if (crossedBoundary) {
                    try {
                        epoch = Math.addExact(epoch, 1);
                    } catch (ArithmeticException e) {
                        throw new UserException("External sequence cycle epoch overflow");
                    }
                }
            }
            result.add(new Sequence.RangeSegment(start, increment, segment.getCount(), epoch));
            expectedStart = last.add(increment);
        }
        if (total != requestedCount) {
            throw new UserException("External sequence provider returned " + total
                    + " values, but " + requestedCount + " were requested");
        }
        return result;
    }

    private static void checkInBounds(BigInteger value, BigInteger min, BigInteger max) throws UserException {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new UserException("External sequence value " + value + " is outside [" + min + ", " + max + "]");
        }
    }
}
