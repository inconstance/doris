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

package org.apache.doris.nereids.glue.translator;

import org.apache.doris.analysis.Expr;
import org.apache.doris.catalog.ScalarType;
import org.apache.doris.catalog.Type;
import org.apache.doris.nereids.parser.Dialect;
import org.apache.doris.nereids.trees.expressions.Alias;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.expressions.functions.ResultTypeDescriptorProvider;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.logical.LogicalResultSink;
import org.apache.doris.nereids.trees.plans.physical.PhysicalPlan;
import org.apache.doris.nereids.trees.plans.physical.PhysicalResultSink;
import org.apache.doris.planner.PlanFragment;
import org.apache.doris.qe.SessionVariable;

import java.util.List;

/** Applies function-declared attributes to result-boundary carrier types. */
public final class ResultTypeDescriptorResolver {

    private ResultTypeDescriptorResolver() {
    }

    /** Apply descriptors only to direct result expressions, never to nested expressions. */
    public static void apply(Plan analyzedPlan, PhysicalPlan physicalPlan, PlanFragment root,
            SessionVariable sessionVariable) {
        List<NamedExpression> outputs;
        if (analyzedPlan instanceof LogicalResultSink) {
            outputs = ((LogicalResultSink<?>) analyzedPlan).getOutputExprs();
        } else if (physicalPlan instanceof PhysicalResultSink) {
            outputs = ((PhysicalResultSink<?>) physicalPlan).getOutputExprs();
        } else {
            return;
        }
        if (root.getOutputExprs().size() != outputs.size()) {
            return;
        }

        Dialect dialect = Dialect.getByName(sessionVariable.getSqlDialect());
        if (dialect == null) {
            return;
        }
        for (int i = 0; i < outputs.size(); i++) {
            Expression expression = outputs.get(i);
            while (expression instanceof Alias) {
                expression = expression.child(0);
            }
            if (!(expression instanceof ResultTypeDescriptorProvider)) {
                continue;
            }
            long attributes = ((ResultTypeDescriptorProvider) expression)
                    .computeResultTypeDescriptor(dialect);
            if (attributes == Type.TYPE_DESCRIPTOR_DEFAULT) {
                continue;
            }

            Expr output = root.getOutputExprs().get(i);
            if (!(output.getType() instanceof ScalarType)) {
                continue;
            }
            Type carrierType = output.getType();
            long descriptor = (carrierType.getTypeDescriptor() & ~Type.TYPE_DESCRIPTOR_CODE_MASK)
                    | attributes;
            output.setType(((ScalarType) carrierType).withTypeDescriptor(descriptor));
        }
    }
}
