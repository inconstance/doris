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

import org.apache.doris.nereids.rules.Rule;
import org.apache.doris.nereids.rules.RuleType;
import org.apache.doris.nereids.trees.expressions.Alias;
import org.apache.doris.nereids.trees.expressions.ExprId;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.expressions.SequenceValue;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.expressions.StatementScopeIdGenerator;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.logical.LogicalOneRowRelation;
import org.apache.doris.nereids.trees.plans.logical.LogicalProject;
import org.apache.doris.nereids.trees.plans.logical.LogicalSequence;
import org.apache.doris.nereids.util.ExpressionUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materialize Sequence pseudocolumns immediately below the final project of a query block.
 * This rule is intentionally Sequence-specific: Doris 2.1 has no general volatile-expression
 * extraction phase, and importing the newer phase would change unrelated expression semantics.
 */
public class BindSequenceValue implements RewriteRuleFactory {
    @Override
    public List<Rule> buildRules() {
        return ImmutableList.of(
                logicalProject().thenApply(ctx -> rewriteProject(ctx.root))
                        .toRule(RuleType.BIND_SEQUENCE_VALUE),
                logicalOneRowRelation().thenApply(ctx -> rewriteOneRowRelation(ctx.root))
                        .toRule(RuleType.BIND_SEQUENCE_VALUE));
    }

    private Plan rewriteProject(LogicalProject<Plan> project) {
        SequenceBinding binding = bind(project.getProjects());
        if (binding == null) {
            return project;
        }
        if (project.isDistinct()) {
            throw new org.apache.doris.nereids.exceptions.AnalysisException(
                    "NEXTVAL and CURRVAL are not allowed with SELECT DISTINCT");
        }
        return project.withProjectsAndChild(binding.outputs,
                new LogicalSequence<>(binding.aliases, project.child()));
    }

    private Plan rewriteOneRowRelation(LogicalOneRowRelation relation) {
        SequenceBinding binding = bind(relation.getProjects());
        if (binding == null) {
            return relation;
        }
        LogicalOneRowRelation valueSource = relation.withProjects(ImmutableList.of());
        return new LogicalProject<>(binding.outputs,
                new LogicalSequence<>(binding.aliases, valueSource));
    }

    private SequenceBinding bind(List<NamedExpression> outputs) {
        Map<Long, SequenceValue> values = new LinkedHashMap<>();
        for (NamedExpression output : outputs) {
            output.foreach(expression -> {
                if (expression instanceof SequenceValue) {
                    SequenceValue value = (SequenceValue) expression;
                    SequenceValue previous = values.get(value.getSequenceId());
                    // NEXTVAL dominates CURRVAL for the same output row.
                    if (previous == null || (!previous.isNextVal() && value.isNextVal())) {
                        values.put(value.getSequenceId(), value);
                    }
                }
            });
        }
        if (values.isEmpty()) {
            return null;
        }

        Map<Long, Slot> slotsBySequenceId = Maps.newHashMap();
        ImmutableList.Builder<Alias> aliases = ImmutableList.builderWithExpectedSize(values.size());
        for (SequenceValue value : values.values()) {
            ExprId exprId = StatementScopeIdGenerator.newExprId();
            Alias alias = new Alias(exprId, value,
                    "$_sequence_" + value.getSequenceId() + "_" + exprId.asInt() + "_$");
            aliases.add(alias);
            slotsBySequenceId.put(value.getSequenceId(), alias.toSlot());
        }

        Map<Expression, Slot> replacements = Maps.newHashMap();
        for (NamedExpression output : outputs) {
            output.foreach(expression -> {
                if (expression instanceof SequenceValue) {
                    SequenceValue value = (SequenceValue) expression;
                    replacements.put(value, slotsBySequenceId.get(value.getSequenceId()));
                }
            });
        }
        ImmutableList.Builder<NamedExpression> rewritten =
                ImmutableList.builderWithExpectedSize(outputs.size());
        for (NamedExpression output : outputs) {
            rewritten.add((NamedExpression) ExpressionUtils.replace(output, replacements));
        }

        List<Alias> sequenceAliases = aliases.build();
        return new SequenceBinding(rewritten.build(), sequenceAliases);
    }

    private static class SequenceBinding {
        private final List<NamedExpression> outputs;
        private final List<Alias> aliases;

        private SequenceBinding(List<NamedExpression> outputs, List<Alias> aliases) {
            this.outputs = outputs;
            this.aliases = aliases;
        }
    }
}
