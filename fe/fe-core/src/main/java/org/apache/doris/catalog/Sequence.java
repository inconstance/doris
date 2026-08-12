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

import org.apache.doris.common.AnalysisException;
import org.apache.doris.common.UserException;
import org.apache.doris.persist.gson.GsonPostProcessable;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Metadata and allocation state for a database-level sequence. */
public class Sequence implements GsonPostProcessable {
    public static final BigInteger POSITIVE_DEFAULT_MIN = BigInteger.ONE;
    public static final BigInteger POSITIVE_DEFAULT_MAX = BigInteger.TEN.pow(28).subtract(BigInteger.ONE);
    public static final BigInteger NEGATIVE_DEFAULT_MIN = BigInteger.TEN.pow(27).subtract(BigInteger.ONE).negate();
    public static final BigInteger NEGATIVE_DEFAULT_MAX = BigInteger.ONE.negate();
    public static final long DEFAULT_CACHE_SIZE = 20;
    private static final BigInteger INT128_MIN = BigInteger.ONE.shiftLeft(127).negate();
    private static final BigInteger INT128_MAX = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

    @SerializedName("id")
    private long id;
    @SerializedName("dbId")
    private long dbId;
    @SerializedName("name")
    private String name;
    @SerializedName("startValue")
    private String startValue;
    @SerializedName("increment")
    private String increment;
    @SerializedName("minValue")
    private String minValue;
    @SerializedName("maxValue")
    private String maxValue;
    @SerializedName("cacheSize")
    private long cacheSize;
    @SerializedName("cycle")
    private boolean cycle;
    @SerializedName("nextValue")
    private String nextValue;
    @SerializedName("exhausted")
    private boolean exhausted;
    @SerializedName("version")
    private long version;
    @SerializedName("cycleEpoch")
    private long cycleEpoch;

    private Sequence() {
    }

    public Sequence(long id, long dbId, String name, BigInteger startValue, BigInteger increment,
            BigInteger minValue, BigInteger maxValue, long cacheSize, boolean cycle) throws AnalysisException {
        validateDefinition(startValue, increment, minValue, maxValue, cacheSize, cycle);
        this.id = id;
        this.dbId = dbId;
        this.name = Objects.requireNonNull(name, "name");
        this.startValue = startValue.toString();
        this.increment = increment.toString();
        this.minValue = minValue.toString();
        this.maxValue = maxValue.toString();
        this.cacheSize = cacheSize;
        this.cycle = cycle;
        this.nextValue = this.startValue;
        this.version = 1;
    }

    public static void validateDefinition(BigInteger start, BigInteger step, BigInteger min, BigInteger max,
            long cacheSize, boolean cycle) throws AnalysisException {
        if (start == null || step == null || min == null || max == null) {
            throw new AnalysisException("Sequence values must not be null");
        }
        if (step.signum() == 0) {
            throw new AnalysisException("INCREMENT BY must not be 0");
        }
        if (min.compareTo(max) >= 0) {
            throw new AnalysisException("MINVALUE must be less than MAXVALUE");
        }
        if (start.compareTo(min) < 0 || start.compareTo(max) > 0) {
            throw new AnalysisException("START WITH must be between MINVALUE and MAXVALUE");
        }
        checkInt128("START WITH", start);
        checkInt128("INCREMENT BY", step);
        checkInt128("MINVALUE", min);
        checkInt128("MAXVALUE", max);
        if (cacheSize < 0) {
            throw new AnalysisException("CACHE must be positive, or use NOCACHE");
        }
        BigInteger capacity = max.subtract(min).divide(step.abs()).add(BigInteger.ONE);
        if (cycle && cacheSize > 0 && BigInteger.valueOf(cacheSize).compareTo(capacity) > 0) {
            throw new AnalysisException("CACHE size must not exceed the number of values in one cycle");
        }
    }

    private static void checkInt128(String property, BigInteger value) throws AnalysisException {
        if (value.compareTo(INT128_MIN) < 0 || value.compareTo(INT128_MAX) > 0) {
            throw new AnalysisException(property + " is outside the LARGEINT range");
        }
    }

    private static BigInteger parseCanonical(String property, String value) throws IOException {
        try {
            BigInteger parsed = new BigInteger(value);
            if (!parsed.toString().equals(value)) {
                throw new IOException(property + " is not a canonical decimal integer: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IOException(property + " is not a decimal integer: " + value, e);
        }
    }

    @Override
    public void gsonPostProcess() throws IOException {
        BigInteger parsedStart = parseCanonical("START WITH", startValue);
        BigInteger parsedIncrement = parseCanonical("INCREMENT BY", increment);
        BigInteger parsedMin = parseCanonical("MINVALUE", minValue);
        BigInteger parsedMax = parseCanonical("MAXVALUE", maxValue);
        parseCanonical("next value", nextValue);
        try {
            validateDefinition(parsedStart, parsedIncrement, parsedMin, parsedMax, cacheSize, cycle);
        } catch (AnalysisException e) {
            throw new IOException("Invalid persisted sequence " + name + ": " + e.getMessage(), e);
        }
    }

    /** Atomically decides, persists and commits an allocation while holding the sequence lock. */
    public synchronized AllocationResult allocate(long count, long expectedVersion, StatePersister persister)
            throws UserException {
        PendingAllocation allocation = prepareAllocation(count, expectedVersion);
        long allocationTicket = persister.persist(id, expectedVersion, allocation.newState);
        commit(allocation);
        return new AllocationResult(allocation.segments, expectedVersion, allocationTicket);
    }

    /** Builds the next metadata version for ALTER SEQUENCE. */
    public synchronized Sequence alteredCopy(BigInteger alteredIncrement, BigInteger alteredMin,
            BigInteger alteredMax, Long alteredCacheSize, Boolean alteredCycle,
            boolean restart, BigInteger restartValue) throws AnalysisException {
        BigInteger newIncrement = alteredIncrement == null ? getIncrement() : alteredIncrement;
        BigInteger newMin = alteredMin == null ? getMinValue() : alteredMin;
        BigInteger newMax = alteredMax == null ? getMaxValue() : alteredMax;
        long newCacheSize = alteredCacheSize == null ? cacheSize : alteredCacheSize;
        boolean newCycle = alteredCycle == null ? cycle : alteredCycle;
        BigInteger newNext = restart ? (restartValue == null ? getStartValue() : restartValue) : getNextValue();
        // START WITH is historical metadata after the sequence has advanced. ALTER validates the value
        // that can actually be issued next; a bare RESTART deliberately validates the original start.
        BigInteger validationValue = exhausted && !restart
                ? (newIncrement.signum() > 0 ? newMax : newMin) : newNext;
        validateDefinition(validationValue, newIncrement, newMin, newMax, newCacheSize, newCycle);
        if ((!exhausted || restart) && (newNext.compareTo(newMin) < 0 || newNext.compareTo(newMax) > 0)) {
            throw new AnalysisException("Sequence next value must be between MINVALUE and MAXVALUE after ALTER");
        }
        checkInt128("RESTART WITH", newNext);

        Sequence altered = new Sequence();
        altered.id = id;
        altered.dbId = dbId;
        altered.name = name;
        altered.startValue = startValue;
        altered.increment = newIncrement.toString();
        altered.minValue = newMin.toString();
        altered.maxValue = newMax.toString();
        altered.cacheSize = newCacheSize;
        altered.cycle = newCycle;
        altered.nextValue = newNext.toString();
        altered.exhausted = restart ? false : exhausted;
        altered.version = Math.addExact(version, 1);
        altered.cycleEpoch = Math.addExact(cycleEpoch, 1);
        return altered;
    }

    synchronized PendingAllocation prepareAllocation(long count, long expectedVersion) throws UserException {
        if (expectedVersion != version) {
            throw new UserException("Stale sequence version " + expectedVersion + ", current version is " + version);
        }
        if (count <= 0) {
            throw new UserException("Sequence allocation count must be positive");
        }
        if (cacheSize == 0 && count != 1) {
            throw new UserException("NOCACHE sequence allocation count must be 1");
        }
        if (cacheSize > 0 && count > cacheSize) {
            throw new UserException("Sequence allocation count " + count + " exceeds CACHE " + cacheSize);
        }
        if (exhausted) {
            throw new UserException("Sequence " + name + " has reached its limit");
        }

        BigInteger step = getIncrement();
        BigInteger min = getMinValue();
        BigInteger max = getMaxValue();
        BigInteger current = getNextValue();
        long epoch = cycleEpoch;
        long remaining = count;
        List<RangeSegment> segments = new ArrayList<>();

        while (remaining > 0) {
            long available = valuesUntilBoundary(current, step, min, max).min(BigInteger.valueOf(Long.MAX_VALUE))
                    .longValueExact();
            long segmentCount = Math.min(remaining, available);
            segments.add(new RangeSegment(current, step, segmentCount, epoch));
            remaining -= segmentCount;
            BigInteger candidate = current.add(step.multiply(BigInteger.valueOf(segmentCount)));
            boolean beyond = step.signum() > 0 ? candidate.compareTo(max) > 0 : candidate.compareTo(min) < 0;
            if (!beyond) {
                current = candidate;
                continue;
            }
            if (!cycle) {
                if (remaining > 0) {
                    throw new UserException("Sequence " + name + " has insufficient values for allocation");
                }
                return new PendingAllocation(this, segments,
                        new AllocationState(current.toString(), true, epoch), version);
            }
            current = step.signum() > 0 ? min : max;
            epoch = Math.addExact(epoch, 1);
        }
        return new PendingAllocation(this, segments,
                new AllocationState(current.toString(), false, epoch), version);
    }

    private static BigInteger valuesUntilBoundary(BigInteger current, BigInteger step,
            BigInteger min, BigInteger max) {
        BigInteger distance = step.signum() > 0 ? max.subtract(current) : current.subtract(min);
        return distance.divide(step.abs()).add(BigInteger.ONE);
    }

    private synchronized void commit(PendingAllocation allocation) {
        Preconditions.checkState(allocation.owner == this, "Allocation belongs to another sequence");
        Preconditions.checkState(allocation.expectedVersion == version, "Sequence changed before allocation commit");
        nextValue = allocation.newState.nextValue;
        exhausted = allocation.newState.exhausted;
        cycleEpoch = allocation.newState.cycleEpoch;
    }

    /** Applies a state advance while replaying the edit log. */
    public synchronized void replayState(AllocationState state, long expectedVersion) {
        if (expectedVersion != version) {
            return;
        }
        nextValue = state.nextValue;
        exhausted = state.exhausted;
        cycleEpoch = state.cycleEpoch;
    }

    public long getId() {
        return id;
    }

    public long getDbId() {
        return dbId;
    }

    public String getName() {
        return name;
    }

    public BigInteger getStartValue() {
        return new BigInteger(startValue);
    }

    public BigInteger getIncrement() {
        return new BigInteger(increment);
    }

    public BigInteger getMinValue() {
        return new BigInteger(minValue);
    }

    public BigInteger getMaxValue() {
        return new BigInteger(maxValue);
    }

    public BigInteger getNextValue() {
        return new BigInteger(nextValue);
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public boolean isCycle() {
        return cycle;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    public long getVersion() {
        return version;
    }

    public long getCycleEpoch() {
        return cycleEpoch;
    }

    public String toCreateSql(String dbName) {
        StringBuilder sql = new StringBuilder("CREATE SEQUENCE `")
                .append(dbName.replace("`", "``"))
                .append("`.`")
                .append(name.replace("`", "``"))
                .append("`\nSTART WITH ").append(startValue)
                .append("\nINCREMENT BY ").append(increment)
                .append("\nMINVALUE ").append(minValue)
                .append("\nMAXVALUE ").append(maxValue)
                .append(cacheSize == 0 ? "\nNOCACHE" : "\nCACHE " + cacheSize)
                .append(cycle ? "\nCYCLE" : "\nNOCYCLE");
        return sql.toString();
    }

    public static class RangeSegment {
        private final BigInteger startValue;
        private final BigInteger increment;
        private final long count;
        private final long cycleEpoch;

        public RangeSegment(BigInteger startValue, BigInteger increment, long count, long cycleEpoch) {
            this.startValue = startValue;
            this.increment = increment;
            this.count = count;
            this.cycleEpoch = cycleEpoch;
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

        public long getCycleEpoch() {
            return cycleEpoch;
        }
    }

    public static class AllocationState {
        @SerializedName("nextValue")
        private String nextValue;
        @SerializedName("exhausted")
        private boolean exhausted;
        @SerializedName("cycleEpoch")
        private long cycleEpoch;

        private AllocationState() {
        }

        public AllocationState(String nextValue, boolean exhausted, long cycleEpoch) {
            this.nextValue = nextValue;
            this.exhausted = exhausted;
            this.cycleEpoch = cycleEpoch;
        }

        public String getNextValue() {
            return nextValue;
        }

        public boolean isExhausted() {
            return exhausted;
        }

        public long getCycleEpoch() {
            return cycleEpoch;
        }
    }

    public static class AllocationResult {
        private final List<RangeSegment> segments;
        private final long sequenceVersion;
        private final long allocationTicket;

        public AllocationResult(List<RangeSegment> segments, long sequenceVersion, long allocationTicket) {
            this.segments = segments;
            this.sequenceVersion = sequenceVersion;
            this.allocationTicket = allocationTicket;
        }

        public List<RangeSegment> getSegments() {
            return segments;
        }

        public long getSequenceVersion() {
            return sequenceVersion;
        }

        public long getAllocationTicket() {
            return allocationTicket;
        }
    }

    public static class PendingAllocation {
        private final Sequence owner;
        private final List<RangeSegment> segments;
        private final AllocationState newState;
        private final long expectedVersion;
        private boolean committed;

        private PendingAllocation(Sequence owner, List<RangeSegment> segments,
                AllocationState newState, long expectedVersion) {
            this.owner = owner;
            this.segments = Collections.unmodifiableList(segments);
            this.newState = newState;
            this.expectedVersion = expectedVersion;
        }

        public List<RangeSegment> getSegments() {
            return segments;
        }

        public AllocationState getNewState() {
            return newState;
        }

        synchronized void commit() {
            Preconditions.checkState(!committed, "Allocation has already been committed");
            owner.commit(this);
            committed = true;
        }
    }

    @FunctionalInterface
    public interface StatePersister {
        long persist(long sequenceId, long sequenceVersion, AllocationState state) throws UserException;
    }
}
