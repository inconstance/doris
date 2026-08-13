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

import org.apache.doris.nereids.analyzer.UnboundSequenceValue;
import org.apache.doris.nereids.exceptions.ParseException;
import org.apache.doris.nereids.trees.plans.commands.AlterSequenceCommand;
import org.apache.doris.nereids.trees.plans.commands.CreateSequenceCommand;
import org.apache.doris.nereids.trees.plans.commands.DropSequenceCommand;
import org.apache.doris.nereids.trees.plans.commands.ShowCreateSequenceCommand;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SequenceCommandParserTest {
    private final NereidsParser parser = new NereidsParser();

    @Test
    void parsesSequenceDdl() {
        Assertions.assertInstanceOf(CreateSequenceCommand.class, parser.parseSingle(
                "CREATE SEQUENCE db.seq START WITH -10 INCREMENT BY 2 MINVALUE -10 "
                        + "MAXVALUE 100 CACHE 10 CYCLE"));
        Assertions.assertInstanceOf(AlterSequenceCommand.class,
                parser.parseSingle("ALTER SEQUENCE db.seq RESTART WITH 42 NOCACHE NOCYCLE"));
        Assertions.assertInstanceOf(AlterSequenceCommand.class,
                parser.parseSingle("ALTER SEQUENCE seq RESTART"));
        Assertions.assertInstanceOf(DropSequenceCommand.class,
                parser.parseSingle("DROP SEQUENCE IF EXISTS db.seq"));
        Assertions.assertInstanceOf(ShowCreateSequenceCommand.class,
                parser.parseSingle("SHOW CREATE SEQUENCE db.seq"));
    }

    @Test
    void rejectsConflictingAndInvalidAlterOptions() {
        Assertions.assertThrows(ParseException.class,
                () -> parser.parseSingle("CREATE SEQUENCE seq CACHE 2 NOCACHE"));
        Assertions.assertThrows(ParseException.class,
                () -> parser.parseSingle("ALTER SEQUENCE seq START WITH 2"));
        Assertions.assertThrows(ParseException.class,
                () -> parser.parseSingle("ALTER SEQUENCE seq RESTART RESTART WITH 2"));
    }

    @Test
    void parsesOracleSequencePseudocolumns() {
        UnboundSequenceValue nextVal = Assertions.assertInstanceOf(UnboundSequenceValue.class,
                parser.parseExpression("seq.NEXTVAL"));
        Assertions.assertEquals(java.util.Collections.singletonList("seq"), nextVal.getNameParts());
        Assertions.assertTrue(nextVal.isNextVal());

        UnboundSequenceValue currVal = Assertions.assertInstanceOf(UnboundSequenceValue.class,
                parser.parseExpression("db.seq.CURRVAL"));
        Assertions.assertEquals(java.util.Arrays.asList("db", "seq"), currVal.getNameParts());
        Assertions.assertFalse(currVal.isNextVal());
        Assertions.assertThrows(ParseException.class,
                () -> parser.parseExpression("catalog.db.seq.NEXTVAL"));
    }
}
