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

package org.apache.doris.nereids.parser;

import org.apache.doris.analysis.StatementBase;
import org.apache.doris.nereids.glue.LogicalPlanAdapter;
import org.apache.doris.nereids.trees.plans.commands.CreateProcedureCommand;
import org.apache.doris.nereids.trees.plans.commands.PlSqlBlockCommand;
import org.apache.doris.nereids.trees.plans.commands.TransactionBeginCommand;
import org.apache.doris.nereids.trees.plans.logical.LogicalPlan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class PlSqlBlockParserTest {
    private final NereidsParser parser = new NereidsParser();

    @Test
    public void testCreateProcedureIsOneStatement() {
        String sql = "CREATE PROCEDURE p() BEGIN "
                + "DECLARE first_value INT = 1; "
                + "DECLARE second_value INT = 2; "
                + "PRINT first_value + second_value; END;";
        List<StatementBase> statements = parser.parseSQL(sql);
        assertEquals(1, statements.size());
        assertInstanceOf(CreateProcedureCommand.class, logicalPlan(statements.get(0)));
    }

    @Test
    public void testDeclareAnonymousBlockIsOneStatement() {
        String sql = "DECLARE value INT = 1; BEGIN PRINT value; END;";
        List<StatementBase> statements = parser.parseSQL(sql);
        assertEquals(1, statements.size());
        assertInstanceOf(PlSqlBlockCommand.class, logicalPlan(statements.get(0)));
    }

    @Test
    public void testBeginAnonymousBlockIsOneStatement() {
        String sql = "BEGIN PRINT 'first'; PRINT 'second'; END;";
        List<StatementBase> statements = parser.parseSQL(sql);
        assertEquals(1, statements.size());
        assertInstanceOf(PlSqlBlockCommand.class, logicalPlan(statements.get(0)));
    }

    @Test
    public void testTransactionBeginIsUnchanged() {
        assertInstanceOf(TransactionBeginCommand.class, parser.parseSingle("BEGIN;"));
    }

    private LogicalPlan logicalPlan(StatementBase statement) {
        return ((LogicalPlanAdapter) statement).getLogicalPlan();
    }
}
