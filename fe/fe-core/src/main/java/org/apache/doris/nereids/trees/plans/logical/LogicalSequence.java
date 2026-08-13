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

package org.apache.doris.nereids.trees.plans.logical;

import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.properties.LogicalProperties;
import org.apache.doris.nereids.trees.expressions.Alias;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.PropagateFuncDeps;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

/** Side-effect boundary that materializes one Sequence value per input row and sequence id. */
public class LogicalSequence<CHILD_TYPE extends Plan> extends LogicalUnary<CHILD_TYPE>
        implements PropagateFuncDeps {
    private final List<Alias> sequenceAliases;

    public LogicalSequence(List<Alias> sequenceAliases, CHILD_TYPE child) {
        this(sequenceAliases, Optional.empty(), Optional.empty(), child);
    }

    private LogicalSequence(List<Alias> sequenceAliases, Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, CHILD_TYPE child) {
        super(PlanType.LOGICAL_SEQUENCE, groupExpression, logicalProperties, child);
        Preconditions.checkArgument(!sequenceAliases.isEmpty());
        this.sequenceAliases = ImmutableList.copyOf(sequenceAliases);
    }

    public List<Alias> getSequenceAliases() {
        return sequenceAliases;
    }

    @Override
    public List<? extends Expression> getExpressions() {
        return sequenceAliases;
    }

    @Override
    public List<Slot> computeOutput() {
        return ImmutableList.<Slot>builder().addAll(child().getOutput())
                .addAll(sequenceAliases.stream().map(Alias::toSlot).iterator()).build();
    }

    @Override
    public LogicalSequence<Plan> withChildren(List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new LogicalSequence<>(sequenceAliases, groupExpression, Optional.of(getLogicalProperties()),
                children.get(0));
    }

    @Override
    public LogicalSequence<CHILD_TYPE> withGroupExpression(Optional<GroupExpression> groupExpression) {
        return new LogicalSequence<>(sequenceAliases, groupExpression, Optional.of(getLogicalProperties()), child());
    }

    @Override
    public Plan withGroupExprLogicalPropChildren(Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new LogicalSequence<>(sequenceAliases, groupExpression, logicalProperties, children.get(0));
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitLogicalSequence(this, context);
    }

}
