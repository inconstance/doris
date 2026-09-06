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

package org.apache.doris.nereids.trees.plans.commands;

import org.apache.doris.analysis.StmtType;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.qe.ConnectContext;
import org.apache.doris.qe.StmtExecutor;

import java.util.Objects;

/** Execute one anonymous PL/SQL DECLARE or BEGIN/END block. */
public class PlSqlBlockCommand extends Command implements ForwardWithSync {
    private final String source;

    public PlSqlBlockCommand(String source) {
        super(PlanType.PLSQL_BLOCK_COMMAND);
        this.source = Objects.requireNonNull(source, "source is null");
    }

    @Override
    public void run(ConnectContext ctx, StmtExecutor executor) {
        ctx.getPlSqlOperation().execute(ctx, source);
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitPlSqlBlockCommand(this, context);
    }

    @Override
    public StmtType stmtType() {
        return StmtType.EXECUTE;
    }
}
