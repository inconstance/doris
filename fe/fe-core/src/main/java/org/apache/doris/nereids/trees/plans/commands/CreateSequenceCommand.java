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

/** CREATE SEQUENCE command. */
public class CreateSequenceCommand extends Command implements ForwardWithSync {
    /** Supported CREATE SEQUENCE options. */
    public enum Option {
        START, INCREMENT, MINVALUE, NOMINVALUE, MAXVALUE, NOMAXVALUE, CACHE, NOCACHE, CYCLE, NOCYCLE
    }

    private final List<String> nameParts;
    private final boolean ifNotExists;
    private final Map<Option, String> options;

    public CreateSequenceCommand(List<String> nameParts, boolean ifNotExists, Map<Option, String> options) {
        super(PlanType.CREATE_SEQUENCE_COMMAND);
        this.nameParts = nameParts;
        this.ifNotExists = ifNotExists;
        this.options = new EnumMap<>(options);
    }

    @Override
    public void run(ConnectContext ctx, StmtExecutor executor) throws Exception {
        ResolvedName resolvedName = resolveName(ctx, nameParts);
        if (!Env.getCurrentEnv().getAccessManager().checkDbPriv(ctx, "internal", resolvedName.dbName,
                PrivPredicate.CREATE)) {
            throw new AnalysisException("CREATE privilege denied for database " + resolvedName.dbName);
        }
        Database db = Env.getCurrentInternalCatalog().getDbOrDdlException(resolvedName.dbName);
        BigInteger increment = value(Option.INCREMENT, BigInteger.ONE);
        boolean ascending = increment.signum() > 0;
        BigInteger min = options.containsKey(Option.MINVALUE) ? value(Option.MINVALUE, null)
                : ascending ? Sequence.POSITIVE_DEFAULT_MIN : Sequence.NEGATIVE_DEFAULT_MIN;
        BigInteger max = options.containsKey(Option.MAXVALUE) ? value(Option.MAXVALUE, null)
                : ascending ? Sequence.POSITIVE_DEFAULT_MAX : Sequence.NEGATIVE_DEFAULT_MAX;
        BigInteger start = options.containsKey(Option.START) ? value(Option.START, null)
                : ascending ? min : max;
        long cache = options.containsKey(Option.NOCACHE) ? 0
                : options.containsKey(Option.CACHE) ? parseCache(options.get(Option.CACHE))
                : Sequence.DEFAULT_CACHE_SIZE;
        boolean cycle = options.containsKey(Option.CYCLE);
        Sequence sequence = new Sequence(Env.getCurrentEnv().getNextId(), db.getId(), resolvedName.sequenceName,
                start, increment, min, max, cache, cycle);
        Env.getCurrentInternalCatalog().createSequence(sequence, ifNotExists);
    }

    private BigInteger value(Option option, BigInteger defaultValue) {
        String value = options.get(option);
        return value == null ? defaultValue : new BigInteger(value);
    }

    private static long parseCache(String value) throws AnalysisException {
        try {
            long cache = Long.parseLong(value);
            if (cache < 2) {
                throw new AnalysisException("CACHE must be at least 2, or use NOCACHE");
            }
            return cache;
        } catch (NumberFormatException e) {
            throw new AnalysisException("CACHE is outside the BIGINT range: " + value);
        }
    }

    static ResolvedName resolveName(ConnectContext ctx, List<String> nameParts) throws AnalysisException {
        if (nameParts.size() == 1 && ctx.getDatabase() != null && !ctx.getDatabase().isEmpty()) {
            return new ResolvedName(ctx.getDatabase(), nameParts.get(0));
        }
        if (nameParts.size() == 2) {
            return new ResolvedName(nameParts.get(0), nameParts.get(1));
        }
        throw new AnalysisException("Sequence name must be sequence or database.sequence");
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitCommand(this, context);
    }

    static class ResolvedName {
        final String dbName;
        final String sequenceName;

        ResolvedName(String dbName, String sequenceName) {
            this.dbName = dbName;
            this.sequenceName = sequenceName;
        }
    }
}
