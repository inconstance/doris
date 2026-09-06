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

import org.apache.doris.nereids.analyzer.UnboundFunction;
import org.apache.doris.nereids.analyzer.UnboundSlot;
import org.apache.doris.nereids.trees.expressions.Expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class OracleSystemFunctionParserTest {
    private final NereidsParser parser = new NereidsParser();

    @Test
    public void testDbmsRandomValueWithoutParentheses() {
        assertDbmsRandomValue(parser.parseExpression("DBMS_RANDOM.VALUE"), 0);
    }

    @Test
    public void testDbmsRandomValueWithoutArguments() {
        assertDbmsRandomValue(parser.parseExpression("DBMS_RANDOM.VALUE()"), 0);
    }

    @Test
    public void testDbmsRandomValueWithBounds() {
        assertDbmsRandomValue(parser.parseExpression("DBMS_RANDOM.VALUE(10.5, 20.5)"), 2);
    }

    @Test
    public void testDbmsRandomValueArityIsCheckedDuringFunctionBinding() {
        assertDbmsRandomValue(parser.parseExpression("DBMS_RANDOM.VALUE(1)"), 1);
    }

    @Test
    public void testDbmsRandomWithoutParentheses() {
        assertDbmsRandomFunction(parser.parseExpression("DBMS_RANDOM.RANDOM"), "RANDOM", 0);
    }

    @Test
    public void testDbmsRandomWithParentheses() {
        assertDbmsRandomFunction(parser.parseExpression("DBMS_RANDOM.RANDOM()"), "RANDOM", 0);
    }

    @Test
    public void testDbmsRandomString() {
        assertDbmsRandomFunction(parser.parseExpression("DBMS_RANDOM.STRING('x', 32)"), "STRING", 2);
        assertInstanceOf(UnboundSlot.class, parser.parseExpression("DBMS_RANDOM.STRING"));
    }

    @Test
    public void testUtlRawFunctionsRemainPackageQualifiedUntilBinding() {
        assertPackageFunction(parser.parseExpression("UTL_RAW.CAST_TO_RAW('Doris')"),
                "UTL_RAW", "CAST_TO_RAW", 1);
        assertPackageFunction(parser.parseExpression("UTL_RAW.CAST_TO_VARCHAR2(raw_column)"),
                "UTL_RAW", "CAST_TO_VARCHAR2", 1);
        assertPackageFunction(parser.parseExpression("UTL_RAW.CONVERT(raw_column, 'UTF8', 'UTF8')"),
                "UTL_RAW", "CONVERT", 3);
    }

    @Test
    public void testDbmsLobFunctionsRemainPackageQualifiedUntilBinding() {
        assertPackageFunction(parser.parseExpression("DBMS_LOB.GETLENGTH(blob_column)"),
                "DBMS_LOB", "GETLENGTH", 1);
        assertPackageFunction(parser.parseExpression("DBMS_LOB.SUBSTR(blob_column, 2000, 1)"),
                "DBMS_LOB", "SUBSTR", 3);
    }

    @Test
    public void testOtherQualifiedIdentifiersAreUnchanged() {
        assertInstanceOf(UnboundSlot.class, parser.parseExpression("table_name.value"));
        Expression expression = parser.parseExpression("schema_name.value()");
        assertInstanceOf(UnboundFunction.class, expression);
        UnboundFunction function = (UnboundFunction) expression;
        assertEquals("schema_name", function.getDbName());
    }

    private void assertDbmsRandomValue(Expression expression, int arity) {
        assertDbmsRandomFunction(expression, "VALUE", arity);
    }

    private void assertDbmsRandomFunction(Expression expression, String name, int arity) {
        assertPackageFunction(expression, "DBMS_RANDOM", name, arity);
    }

    private void assertPackageFunction(Expression expression, String packageName, String name, int arity) {
        assertInstanceOf(UnboundFunction.class, expression);
        UnboundFunction function = (UnboundFunction) expression;
        assertEquals(name, function.getName().toUpperCase());
        assertEquals(packageName, function.getDbName().toUpperCase());
        assertEquals(arity, function.arity());
    }
}
