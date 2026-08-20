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
import org.apache.doris.persist.gson.GsonUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

class SequenceTest {
    @Test
    void allocatesBigIntegerRangeAndCommitsOnlyExplicitly() throws Exception {
        BigInteger start = new BigInteger("9999999999999999999999999990");
        Sequence sequence = sequence(start, BigInteger.ONE, start,
                new BigInteger("9999999999999999999999999999"), 10, false);

        java.util.List<Sequence.RangeSegment> segments = sequence.allocate(10, 1, (id, version, state) -> {
            Assertions.assertEquals(start, sequence.getNextValue());
            return 1;
        }).getSegments();
        Assertions.assertEquals(start, segments.get(0).getStartValue());
        Assertions.assertTrue(sequence.isExhausted());
        Assertions.assertThrows(UserException.class, () -> sequence.prepareAllocation(1, 1));
    }

    @Test
    void splitsPositiveCycleAtBoundary() throws Exception {
        Sequence sequence = sequence(BigInteger.valueOf(8), BigInteger.ONE, BigInteger.ONE,
                BigInteger.TEN, 8, true);
        java.util.List<Sequence.RangeSegment> segments = sequence.allocate(5, 1,
                (id, version, state) -> 1).getSegments();

        Assertions.assertEquals(2, segments.size());
        Assertions.assertEquals(BigInteger.valueOf(8), segments.get(0).getStartValue());
        Assertions.assertEquals(3, segments.get(0).getCount());
        Assertions.assertEquals(BigInteger.ONE, segments.get(1).getStartValue());
        Assertions.assertEquals(2, segments.get(1).getCount());
        Assertions.assertEquals(BigInteger.valueOf(3), sequence.getNextValue());
    }

    @Test
    void splitsNegativeCycleAtBoundary() throws Exception {
        Sequence sequence = sequence(BigInteger.valueOf(-8), BigInteger.valueOf(-1), BigInteger.valueOf(-10),
                BigInteger.valueOf(-1), 8, true);
        java.util.List<Sequence.RangeSegment> segments = sequence.allocate(5, 1,
                (id, version, state) -> 1).getSegments();

        Assertions.assertEquals(BigInteger.valueOf(-8), segments.get(0).getStartValue());
        Assertions.assertEquals(3, segments.get(0).getCount());
        Assertions.assertEquals(BigInteger.valueOf(-1), segments.get(1).getStartValue());
        Assertions.assertEquals(2, segments.get(1).getCount());
    }

    @Test
    void noCycleAllocationIsAllOrNothing() throws Exception {
        Sequence sequence = sequence(BigInteger.valueOf(8), BigInteger.ONE, BigInteger.ONE,
                BigInteger.TEN, 10, false);
        Assertions.assertThrows(UserException.class,
                () -> sequence.allocate(4, 1, (id, version, state) -> 1));
        Assertions.assertEquals(BigInteger.valueOf(8), sequence.getNextValue());
        Assertions.assertEquals(3,
                sequence.allocate(3, 1, (id, version, state) -> 1).getSegments().get(0).getCount());
    }

    @Test
    void enforcesNoCacheAndVersion() throws Exception {
        Sequence sequence = sequence(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.TEN, 0, false);
        Assertions.assertThrows(UserException.class,
                () -> sequence.allocate(2, 1, (id, version, state) -> 1));
        Assertions.assertThrows(UserException.class,
                () -> sequence.allocate(1, 2, (id, version, state) -> 1));
        sequence.allocate(1, 1, (id, version, state) -> 1);
        Assertions.assertEquals(BigInteger.valueOf(2), sequence.getNextValue());
    }

    @Test
    void validatesDefinitions() {
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ONE, BigInteger.ZERO,
                BigInteger.ONE, BigInteger.TEN, 1, false));
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ZERO, BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 1, false));
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ONE, BigInteger.valueOf(3),
                BigInteger.ONE, BigInteger.TEN, 5, true));
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ONE, BigInteger.valueOf(3),
                BigInteger.ONE, BigInteger.TEN, 3, true));
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ONE, BigInteger.ONE,
                BigInteger.ONE, BigInteger.TEN, 1, false));
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ONE, BigInteger.valueOf(9),
                BigInteger.ONE, BigInteger.TEN, 2, false));
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.TEN, BigInteger.valueOf(-9),
                BigInteger.ONE, BigInteger.TEN, 2, false));
        Assertions.assertDoesNotThrow(() -> sequence(BigInteger.ONE, BigInteger.valueOf(3),
                BigInteger.ONE, BigInteger.TEN, 2, true));
    }

    @Test
    void alterRejectsCacheEqualToCycleCapacity() throws Exception {
        Sequence sequence = sequence(BigInteger.ONE, BigInteger.valueOf(3), BigInteger.ONE,
                BigInteger.TEN, 3, false);

        Assertions.assertThrows(AnalysisException.class,
                () -> sequence.alteredCopy(null, null, null, null, true, false, null));
    }

    @Test
    void survivesJsonSnapshot() throws Exception {
        Sequence sequence = sequence(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.TEN, 3, true);
        sequence.allocate(3, 1, (id, version, state) -> 1);
        Sequence restored = GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(sequence), Sequence.class);

        Assertions.assertEquals(BigInteger.valueOf(4), restored.getNextValue());
        Assertions.assertEquals(BigInteger.valueOf(3), restored.getLastValue());
        Assertions.assertEquals(BigInteger.ONE, restored.getIncrement());
        Assertions.assertEquals(1, restored.getVersion());
    }

    @Test
    void sequenceColumnDefaultSurvivesJsonSnapshot() {
        Column column = new Column("id", Type.BIGINT, true, null, false,
                "db.seq.NEXTVAL", "", true,
                new org.apache.doris.analysis.DefaultValueExprDef(java.util.Arrays.asList("db", "seq")),
                Column.COLUMN_UNIQUE_ID_INIT_VALUE, "db.seq.NEXTVAL");

        Column restored = GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(column), Column.class);
        Assertions.assertTrue(restored.hasSequenceDefault());
        Assertions.assertEquals(java.util.Arrays.asList("db", "seq"), restored.getDefaultSequenceNameParts());
        Assertions.assertTrue(restored.toSql().contains("DEFAULT db.seq.NEXTVAL"));
        Assertions.assertFalse(restored.toThrift().isSetDefaultValue());
    }

    @Test
    void persistenceFailureDoesNotAdvanceState() throws Exception {
        Sequence sequence = sequence(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE, BigInteger.TEN, 2, false);
        Assertions.assertThrows(UserException.class, () -> sequence.allocate(2, 1,
                (id, version, state) -> {
                    throw new UserException("journal failed");
                }));
        Assertions.assertEquals(BigInteger.ONE, sequence.getNextValue());
    }

    @Test
    void restartCreatesNewVersionAndResetsExhaustion() throws Exception {
        Sequence sequence = sequence(BigInteger.valueOf(8), BigInteger.ONE, BigInteger.ONE,
                BigInteger.TEN, 3, false);
        sequence.allocate(3, 1, (id, version, state) -> 1);
        Assertions.assertTrue(sequence.isExhausted());

        Sequence restarted = sequence.alteredCopy(null, null, null, null, null, true, BigInteger.valueOf(5));
        Assertions.assertEquals(2, restarted.getVersion());
        Assertions.assertEquals(BigInteger.valueOf(5), restarted.getNextValue());
        Assertions.assertFalse(restarted.isExhausted());
        Assertions.assertThrows(UserException.class, () -> restarted.allocate(1, 1,
                (id, version, state) -> 2));
    }

    @Test
    void restartWithoutValueUsesSequenceBoundary() throws Exception {
        Sequence sequence = sequence(BigInteger.valueOf(7), BigInteger.ONE, BigInteger.ONE,
                BigInteger.TEN, 2, false);
        sequence.allocate(2, 1, (id, version, state) -> 1);
        Sequence restarted = sequence.alteredCopy(null, null, null, null, null, true, null);
        Assertions.assertEquals(BigInteger.ONE, restarted.getNextValue());

        Sequence descending = sequence(BigInteger.valueOf(-7), BigInteger.ONE.negate(), BigInteger.TEN.negate(),
                BigInteger.ONE.negate(), 2, false);
        Sequence descendingRestarted = descending.alteredCopy(null, null, null, null, null, true, null);
        Assertions.assertEquals(BigInteger.ONE.negate(), descendingRestarted.getNextValue());
    }

    @Test
    void renamePreservesIdentityAndAllocationState() throws Exception {
        Sequence sequence = sequence(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE,
                BigInteger.TEN, 3, true);
        sequence.allocate(2, 1, (id, version, state) -> 1);

        Sequence renamed = sequence.renamedCopy("seq2");
        Assertions.assertEquals(sequence.getId(), renamed.getId());
        Assertions.assertEquals("seq2", renamed.getName());
        Assertions.assertEquals(BigInteger.valueOf(3), renamed.getNextValue());
        Assertions.assertEquals(2, renamed.getVersion());

        Database db = new Database(20, "db");
        db.writeLock();
        try {
            Assertions.assertTrue(db.registerSequence(sequence));
            Assertions.assertTrue(db.replaceSequence(sequence, renamed));
            Assertions.assertNull(db.getSequenceNullable("seq"));
            Assertions.assertSame(renamed, db.getSequenceNullable("seq2"));
            Assertions.assertSame(renamed, db.getSequenceNullable(sequence.getId()));
        } finally {
            db.writeUnlock();
        }
    }

    @Test
    void alterBoundsValidateLastAllocatedValue() throws Exception {
        Sequence sequence = sequence(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE,
                BigInteger.TEN, 10, false);
        sequence.allocate(7, 1, (id, version, state) -> 1);

        Assertions.assertThrows(AnalysisException.class,
                () -> sequence.alteredCopy(null, BigInteger.valueOf(8), null,
                        null, null, false, null));
        Sequence altered = sequence.alteredCopy(null, BigInteger.valueOf(7), null,
                null, null, false, null);
        Assertions.assertEquals(BigInteger.valueOf(8), altered.getNextValue());
        Assertions.assertEquals(BigInteger.valueOf(7),
                altered.alteredCopy(null, null, null, null, null, true, null).getNextValue());
    }

    @Test
    void localAlterValidatesLastValueAndRecomputesNextValue() throws Exception {
        Sequence sequence = sequence(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO,
                BigInteger.TEN, 5, false);
        sequence.allocate(5, 1, (id, version, state) -> 1);
        Assertions.assertEquals(BigInteger.valueOf(5), sequence.getLastValue());
        Assertions.assertEquals(BigInteger.valueOf(6), sequence.getNextValue());

        Sequence exhausted = sequence.alteredCopy(null, null, BigInteger.valueOf(5),
                null, null, false, null);
        Assertions.assertTrue(exhausted.isExhausted());
        Assertions.assertThrows(UserException.class,
                () -> exhausted.allocate(1, exhausted.getVersion(), (id, version, state) -> 2));
        Assertions.assertThrows(AnalysisException.class,
                () -> sequence.alteredCopy(null, BigInteger.valueOf(6), null,
                        null, null, false, null));

        Sequence changedIncrement = sequence.alteredCopy(BigInteger.valueOf(2), null, null,
                null, null, false, null);
        Assertions.assertEquals(BigInteger.valueOf(7), changedIncrement.getNextValue());
    }

    @Test
    void validatesOracleSequenceNumericRange() {
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ONE, BigInteger.ONE,
                Sequence.NEGATIVE_DEFAULT_MIN.subtract(BigInteger.ONE), BigInteger.TEN, 2, false));
        Assertions.assertThrows(AnalysisException.class, () -> sequence(BigInteger.ONE, BigInteger.ONE,
                BigInteger.ONE, Sequence.POSITIVE_DEFAULT_MAX.add(BigInteger.ONE), 2, false));
    }

    private static Sequence sequence(BigInteger start, BigInteger increment, BigInteger min, BigInteger max,
            long cache, boolean cycle) throws AnalysisException {
        return new Sequence(10, 20, "seq", start, increment, min, max, cache, cycle);
    }
}
