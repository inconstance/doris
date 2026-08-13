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

import org.apache.doris.catalog.Column;
import org.apache.doris.catalog.Database;
import org.apache.doris.catalog.Env;
import org.apache.doris.catalog.ScalarType;
import org.apache.doris.catalog.Sequence;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.mysql.privilege.PrivPredicate;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.qe.ConnectContext;
import org.apache.doris.qe.ShowResultSet;
import org.apache.doris.qe.ShowResultSetMetaData;
import org.apache.doris.qe.StmtExecutor;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** SHOW CREATE SEQUENCE command. */
public class ShowCreateSequenceCommand extends Command implements NoForward {
    private static final ShowResultSetMetaData META_DATA = ShowResultSetMetaData.builder()
            .addColumn(new Column("Sequence", ScalarType.createVarchar(128)))
            .addColumn(new Column("Create Sequence", ScalarType.createVarchar(1024)))
            .build();

    private final List<String> nameParts;

    public ShowCreateSequenceCommand(List<String> nameParts) {
        super(PlanType.SHOW_CREATE_SEQUENCE_COMMAND);
        this.nameParts = nameParts;
    }

    @Override
    public void run(ConnectContext ctx, StmtExecutor executor) throws Exception {
        CreateSequenceCommand.ResolvedName resolvedName = CreateSequenceCommand.resolveName(ctx, nameParts);
        if (!Env.getCurrentEnv().getAccessManager().checkDbPriv(ctx, "internal", resolvedName.dbName,
                PrivPredicate.SHOW)) {
            throw new AnalysisException("SHOW privilege denied for database " + resolvedName.dbName);
        }
        Database db = Env.getCurrentInternalCatalog().getDbOrDdlException(resolvedName.dbName);
        Sequence sequence = db.getSequenceNullable(resolvedName.sequenceName);
        if (sequence == null) {
            throw new AnalysisException("Unknown sequence: " + resolvedName.sequenceName);
        }
        executor.sendResultSet(new ShowResultSet(META_DATA, ImmutableList.of(
                ImmutableList.of(sequence.getName(), sequence.toCreateSql(resolvedName.dbName)))));
    }

    public ShowResultSetMetaData getMetaData() {
        return META_DATA;
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitCommand(this, context);
    }
}
