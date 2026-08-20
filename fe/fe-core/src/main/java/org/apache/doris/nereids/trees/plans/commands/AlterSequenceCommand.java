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
import org.apache.doris.catalog.Sequence;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.mysql.privilege.PrivPredicate;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.qe.ConnectContext;
import org.apache.doris.qe.StmtExecutor;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** ALTER SEQUENCE command, including RESTART and RESTART WITH. */
public class AlterSequenceCommand extends Command implements ForwardWithSync {
    private final List<String> nameParts;
    private final Map<CreateSequenceCommand.Option, String> options;
    private final boolean restart;
    private final BigInteger restartValue;
    private final String newName;

    public AlterSequenceCommand(List<String> nameParts, Map<CreateSequenceCommand.Option, String> options,
            boolean restart, BigInteger restartValue, String newName) {
        super(PlanType.ALTER_SEQUENCE_COMMAND);
        this.nameParts = nameParts;
        this.options = new EnumMap<>(options);
        this.restart = restart;
        this.restartValue = restartValue;
        this.newName = newName;
    }

    @Override
    public void run(ConnectContext ctx, StmtExecutor executor) throws Exception {
        CreateSequenceCommand.ResolvedName resolvedName = CreateSequenceCommand.resolveName(ctx, nameParts);
        if (!Env.getCurrentEnv().getAccessManager().checkDbPriv(ctx, "internal", resolvedName.dbName,
                PrivPredicate.ALTER)) {
            throw new AnalysisException("ALTER privilege denied for database " + resolvedName.dbName);
        }
        Database db = Env.getCurrentInternalCatalog().getDbOrDdlException(resolvedName.dbName);
        Sequence existing = db.getSequenceNullable(resolvedName.sequenceName);
        if (existing == null) {
            throw new AnalysisException("Unknown sequence: " + resolvedName.sequenceName);
        }
        if (newName != null) {
            Env.getCurrentInternalCatalog().renameSequence(db.getId(), resolvedName.sequenceName, newName);
            return;
        }

        BigInteger increment = decimalOption(CreateSequenceCommand.Option.INCREMENT);
        BigInteger effectiveIncrement = increment == null ? existing.getIncrement() : increment;
        boolean ascending = effectiveIncrement.signum() > 0;
        BigInteger minValue = options.containsKey(CreateSequenceCommand.Option.NOMINVALUE)
                ? ascending ? Sequence.POSITIVE_DEFAULT_MIN : Sequence.NEGATIVE_DEFAULT_MIN
                : decimalOption(CreateSequenceCommand.Option.MINVALUE);
        BigInteger maxValue = options.containsKey(CreateSequenceCommand.Option.NOMAXVALUE)
                ? ascending ? Sequence.POSITIVE_DEFAULT_MAX : Sequence.NEGATIVE_DEFAULT_MAX
                : decimalOption(CreateSequenceCommand.Option.MAXVALUE);
        Long cacheSize = options.containsKey(CreateSequenceCommand.Option.NOCACHE) ? 0L
                : longOption(CreateSequenceCommand.Option.CACHE);
        Boolean cycle = options.containsKey(CreateSequenceCommand.Option.CYCLE) ? Boolean.TRUE
                : options.containsKey(CreateSequenceCommand.Option.NOCYCLE) ? Boolean.FALSE : null;
        Env.getCurrentInternalCatalog().alterSequence(db.getId(), resolvedName.sequenceName, increment,
                minValue, maxValue, cacheSize, cycle, restart, restartValue);
    }

    private BigInteger decimalOption(CreateSequenceCommand.Option option) {
        String value = options.get(option);
        return value == null ? null : new BigInteger(value);
    }

    private Long longOption(CreateSequenceCommand.Option option) throws AnalysisException {
        String value = options.get(option);
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 2) {
                throw new AnalysisException("CACHE must be at least 2, or use NOCACHE");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new AnalysisException("CACHE is outside the BIGINT range: " + value);
        }
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitCommand(this, context);
    }

}
