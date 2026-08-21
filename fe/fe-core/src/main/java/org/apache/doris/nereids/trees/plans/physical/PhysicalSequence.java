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

package org.apache.doris.nereids.trees.plans.physical;

import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.properties.DataTrait;
import org.apache.doris.nereids.properties.LogicalProperties;
import org.apache.doris.nereids.properties.PhysicalProperties;
import org.apache.doris.nereids.trees.expressions.Alias;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.nereids.util.Utils;
import org.apache.doris.statistics.Statistics;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

/** Physical Sequence operator. */
public class PhysicalSequence<CHILD_TYPE extends Plan> extends PhysicalUnary<CHILD_TYPE> {
    private final List<Alias> sequenceAliases;

    public PhysicalSequence(List<Alias> aliases, LogicalProperties logicalProperties, CHILD_TYPE child) {
        this(aliases, Optional.empty(), logicalProperties, PhysicalProperties.ANY, null, child);
    }

    private PhysicalSequence(List<Alias> aliases, Optional<GroupExpression> groupExpression,
            LogicalProperties logicalProperties, PhysicalProperties physicalProperties,
            Statistics statistics, CHILD_TYPE child) {
        super(PlanType.PHYSICAL_SEQUENCE, groupExpression, logicalProperties, physicalProperties, statistics, child);
        this.sequenceAliases = ImmutableList.copyOf(aliases);
    }

    public List<Alias> getSequenceAliases() {
        return sequenceAliases;
    }

    @Override
    public String toString() {
        return Utils.toSqlString("PhysicalSequence",
                "stats", statistics,
                "sequences", sequenceAliases);
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
    public PhysicalSequence<Plan> withChildren(List<Plan> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new PhysicalSequence<>(sequenceAliases, groupExpression, getLogicalProperties(),
                physicalProperties, statistics, children.get(0));
    }

    @Override
    public PhysicalSequence<CHILD_TYPE> withGroupExpression(Optional<GroupExpression> groupExpression) {
        return new PhysicalSequence<>(sequenceAliases, groupExpression, getLogicalProperties(),
                physicalProperties, statistics, child());
    }

    @Override
    public Plan withGroupExprLogicalPropChildren(Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, List<Plan> children) {
        return new PhysicalSequence<>(sequenceAliases, groupExpression, logicalProperties.get(),
                physicalProperties, statistics, children.get(0));
    }

    @Override
    public PhysicalSequence<CHILD_TYPE> withPhysicalPropertiesAndStats(
            PhysicalProperties physicalProperties, Statistics statistics) {
        return new PhysicalSequence<>(sequenceAliases, groupExpression, getLogicalProperties(),
                physicalProperties, statistics, child());
    }

    @Override
    public PhysicalSequence<CHILD_TYPE> resetLogicalProperties() {
        return new PhysicalSequence<>(sequenceAliases, Optional.empty(), null,
                physicalProperties, statistics, child());
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitPhysicalSequence(this, context);
    }

    @Override
    public void computeUnique(DataTrait.Builder builder) {
        builder.addUniqueSlot(child().getLogicalProperties().getTrait());
    }

    @Override
    public void computeUniform(DataTrait.Builder builder) {
        builder.addUniformSlot(child().getLogicalProperties().getTrait());
    }

    @Override
    public void computeEqualSet(DataTrait.Builder builder) {
        builder.addEqualSet(child().getLogicalProperties().getTrait());
    }

    @Override
    public void computeFd(DataTrait.Builder builder) {
        builder.addFuncDepsDG(child().getLogicalProperties().getTrait());
    }
}
