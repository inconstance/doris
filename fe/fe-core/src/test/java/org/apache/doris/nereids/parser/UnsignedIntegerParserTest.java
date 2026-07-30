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

import org.apache.doris.catalog.Type;
import org.apache.doris.nereids.exceptions.NotSupportedException;
import org.apache.doris.nereids.trees.expressions.Cast;
import org.apache.doris.nereids.types.LargeIntType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UnsignedIntegerParserTest extends ParserTestBase {
    private final NereidsParser parser = new NereidsParser();

    @Test
    public void testUnsignedIntegerTypes() {
        Assertions.assertTrue(parser.parseDataType("tinyint unsigned").isUnsignedIntegerType());
        Assertions.assertTrue(parser.parseDataType("smallint unsigned").isUnsignedIntegerType());
        Assertions.assertTrue(parser.parseDataType("int unsigned").isUnsignedIntegerType());
        Assertions.assertTrue(parser.parseDataType("integer unsigned").isUnsignedIntegerType());
        Assertions.assertTrue(parser.parseDataType("bigint unsigned").isUnsignedIntegerType());
    }

    @Test
    public void testRejectUnsupportedUnsignedType() {
        NotSupportedException exception = Assertions.assertThrows(NotSupportedException.class,
                () -> parser.parseDataType("largeint unsigned"));
        Assertions.assertTrue(exception.getMessage().contains("only supported"));
    }

    @Test
    public void testUnsignedCastTargets() {
        Cast mysqlCast = (Cast) parser.parseExpression("cast(-1 as unsigned)");
        Cast typedCast = (Cast) parser.parseExpression("cast(-1 as bigint unsigned)");
        Assertions.assertEquals(LargeIntType.INSTANCE.withTypeDescriptor(Type.TYPE_DESCRIPTOR_UNSIGNED_MASK),
                mysqlCast.getDataType());
        Assertions.assertEquals(LargeIntType.INSTANCE.withTypeDescriptor(Type.TYPE_DESCRIPTOR_UNSIGNED_MASK),
                typedCast.getDataType());
        Assertions.assertTrue(mysqlCast.isExplicitType());
        Assertions.assertTrue(typedCast.isExplicitType());
    }
}
