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

package org.apache.doris.nereids.rules.rewrite;

import org.apache.doris.nereids.trees.expressions.Alias;
import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.expressions.SequenceValue;
import org.apache.doris.nereids.trees.expressions.SlotReference;
import org.apache.doris.nereids.trees.expressions.StatementScopeIdGenerator;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.logical.LogicalOneRowRelation;
import org.apache.doris.nereids.trees.plans.logical.LogicalProject;
import org.apache.doris.nereids.trees.plans.logical.LogicalSequence;
import org.apache.doris.nereids.util.MemoTestUtils;
import org.apache.doris.nereids.util.PlanChecker;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class BindSequenceValueTest {
    @Test
    void sequenceValuesShareOneHiddenSlotPerRow() {
        SequenceValue currVal = new SequenceValue(1, 10, 3, 20, "db.seq", false);
        SequenceValue nextVal = new SequenceValue(1, 10, 3, 20, "db.seq", true);
        List<NamedExpression> projections = ImmutableList.of(
                new Alias(currVal), new Alias(nextVal), new Alias(nextVal));
        LogicalOneRowRelation source = new LogicalOneRowRelation(
                StatementScopeIdGenerator.newRelationId(), ImmutableList.of());
        LogicalProject<Plan> input = new LogicalProject<>(projections, source);

        Plan root = PlanChecker.from(MemoTestUtils.createConnectContext(), input)
                .applyTopDown(new BindSequenceValue())
                .getPlan();

        LogicalProject<?> project = Assertions.assertInstanceOf(LogicalProject.class, root);
        LogicalSequence<?> sequence = Assertions.assertInstanceOf(LogicalSequence.class, project.child());
        Assertions.assertEquals(1, sequence.getSequenceAliases().size());
        Assertions.assertEquals(nextVal, sequence.getSequenceAliases().get(0).child());
        SlotReference first = (SlotReference) ((Alias) project.getProjects().get(0)).child();
        Assertions.assertEquals(first, ((Alias) project.getProjects().get(1)).child());
        Assertions.assertEquals(first, ((Alias) project.getProjects().get(2)).child());
    }

    @Test
    void sequenceWithoutFromRewritesOnce() {
        SequenceValue nextVal = new SequenceValue(1, 10, 3, 20, "db.seq", true);
        LogicalOneRowRelation input = new LogicalOneRowRelation(
                StatementScopeIdGenerator.newRelationId(), ImmutableList.of(new Alias(nextVal)));

        Plan root = PlanChecker.from(MemoTestUtils.createConnectContext(), input)
                .applyTopDown(new BindSequenceValue())
                .getPlan();

        LogicalProject<?> project = Assertions.assertInstanceOf(LogicalProject.class, root);
        LogicalSequence<?> sequence = Assertions.assertInstanceOf(LogicalSequence.class, project.child());
        LogicalOneRowRelation source = Assertions.assertInstanceOf(LogicalOneRowRelation.class, sequence.child());
        Assertions.assertTrue(source.getProjects().isEmpty());
        Assertions.assertEquals(1, sequence.getSequenceAliases().size());
    }
}
