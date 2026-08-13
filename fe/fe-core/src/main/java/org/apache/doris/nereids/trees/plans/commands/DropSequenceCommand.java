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

import org.apache.doris.catalog.Database;
import org.apache.doris.catalog.Env;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.mysql.privilege.PrivPredicate;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.qe.ConnectContext;
import org.apache.doris.qe.StmtExecutor;

import java.util.List;

/** DROP SEQUENCE command. */
public class DropSequenceCommand extends Command implements ForwardWithSync {
    private final List<String> nameParts;
    private final boolean ifExists;

    public DropSequenceCommand(List<String> nameParts, boolean ifExists) {
        super(PlanType.DROP_SEQUENCE_COMMAND);
        this.nameParts = nameParts;
        this.ifExists = ifExists;
    }

    @Override
    public void run(ConnectContext ctx, StmtExecutor executor) throws Exception {
        CreateSequenceCommand.ResolvedName resolvedName = CreateSequenceCommand.resolveName(ctx, nameParts);
        if (!Env.getCurrentEnv().getAccessManager().checkDbPriv(ctx, "internal", resolvedName.dbName,
                PrivPredicate.DROP)) {
            throw new AnalysisException("DROP privilege denied for database " + resolvedName.dbName);
        }
        Database db = Env.getCurrentInternalCatalog().getDbOrDdlException(resolvedName.dbName);
        Env.getCurrentInternalCatalog().dropSequence(db.getId(), resolvedName.sequenceName, ifExists);
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitCommand(this, context);
    }

}
