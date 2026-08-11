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

package org.apache.doris.nereids.trees.expressions.functions.scalar;

import org.apache.doris.nereids.trees.expressions.SlotReference;
import org.apache.doris.nereids.types.BigIntType;
import org.apache.doris.nereids.types.DecimalV3Type;
import org.apache.doris.nereids.types.DoubleType;
import org.apache.doris.qe.ConnectContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SignSignatureTest {
    private ConnectContext previousContext;

    @BeforeEach
    void setUp() {
        previousContext = ConnectContext.get();
        ConnectContext connectContext = new ConnectContext();
        connectContext.setThreadLocalInfo();
    }

    @AfterEach
    void tearDown() {
        ConnectContext.remove();
        if (previousContext != null) {
            previousContext.setThreadLocalInfo();
        }
    }

    @Test
    void testMysqlReturnsBigInt() {
        ConnectContext.get().getSessionVariable().setSqlDialect("doris");
        Sign sign = new Sign(new SlotReference("value", DoubleType.INSTANCE));

        Assertions.assertEquals(BigIntType.INSTANCE, sign.getSignatures().get(0).returnType);
    }

    @Test
    void testOracleNumberReturnsDecimalOneZero() {
        ConnectContext.get().getSessionVariable().setSqlDialect("oracle");
        Sign sign = new Sign(new SlotReference("value", DecimalV3Type.createDecimalV3Type(12, 4)));

        Assertions.assertTrue(sign.getSignatures().stream().anyMatch(signature
                -> signature.getArgType(0) instanceof DecimalV3Type
                && signature.returnType.equals(DecimalV3Type.createDecimalV3Type(1, 0))));

        Sign nativeDouble = new Sign(new SlotReference("double_value", DoubleType.INSTANCE));
        Assertions.assertEquals(BigIntType.INSTANCE, nativeDouble.getSignatures().get(0).returnType);
    }
}
