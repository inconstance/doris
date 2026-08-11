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

import org.apache.doris.catalog.FunctionSignature;
import org.apache.doris.catalog.Type;
import org.apache.doris.nereids.trees.expressions.SlotReference;
import org.apache.doris.nereids.trees.expressions.literal.IntegerLiteral;
import org.apache.doris.nereids.types.BigIntType;
import org.apache.doris.nereids.types.DataType;
import org.apache.doris.nereids.types.DateTimeV2Type;
import org.apache.doris.nereids.types.DecimalV3Type;
import org.apache.doris.nereids.types.IntegerType;
import org.apache.doris.nereids.types.VarcharType;
import org.apache.doris.nereids.types.coercion.IntegralType;
import org.apache.doris.qe.ConnectContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoundSignatureTest {
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
    void testMysqlDecimalPrecision() {
        ConnectContext.get().getSessionVariable().setSqlDialect("doris");
        DecimalV3Type inputType = DecimalV3Type.createDecimalV3Type(12, 4);

        Assertions.assertEquals(DecimalV3Type.createDecimalV3Type(9, 0),
                computeDecimalSignature(inputType, 0).returnType);
        Assertions.assertEquals(DecimalV3Type.createDecimalV3Type(11, 2),
                computeDecimalSignature(inputType, 2).returnType);
        Assertions.assertEquals(inputType, computeDecimalSignature(inputType, 4).returnType);
    }

    @Test
    void testOracleNumberPreservesMappedDecimalType() {
        ConnectContext.get().getSessionVariable().setSqlDialect("oracle");
        DecimalV3Type inputType = DecimalV3Type.createDecimalV3Type(12, 4);

        Assertions.assertEquals(inputType, computeDecimalSignature(inputType, 2).returnType);
    }

    @Test
    void testOracleDatetimeSignaturesUseVarcharFormat() {
        ConnectContext.get().getSessionVariable().setSqlDialect("oracle");
        Round round = new Round(new SlotReference("datetime_value", DateTimeV2Type.of(6)));

        Assertions.assertTrue(round.getSignatures().stream().anyMatch(signature
                -> signature.argumentsTypes.size() == 2
                && signature.argumentsTypes.get(0) instanceof DateTimeV2Type
                && signature.argumentsTypes.get(1).equals(VarcharType.SYSTEM_DEFAULT)
                && signature.returnType.equals(DateTimeV2Type.SYSTEM_DEFAULT)));
    }

    @Test
    void testUnsignedDescriptorPropagation() {
        IntegralType unsignedType = IntegerType.INSTANCE.withTypeDescriptor(
                Type.TYPE_DESCRIPTOR_UNSIGNED_MASK);
        Round round = new Round(new SlotReference("unsigned_value", unsignedType));
        FunctionSignature signature = Round.SIGNATURES.stream()
                .filter(candidate -> candidate.argumentsTypes.size() == 1
                        && candidate.getArgType(0).equals(IntegerType.INSTANCE))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        DataType returnType = round.computeSignature(signature).returnType;

        Assertions.assertEquals(BigIntType.INSTANCE, returnType);
        Assertions.assertTrue(((IntegralType) returnType).isUnsigned());
    }

    private FunctionSignature computeDecimalSignature(DecimalV3Type inputType, int scale) {
        Round round = new Round(new SlotReference("decimal_value", inputType),
                new IntegerLiteral(scale));
        FunctionSignature wildcard = Round.SIGNATURES.stream()
                .filter(signature -> signature.argumentsTypes.size() == 2
                        && signature.getArgType(0) instanceof DecimalV3Type)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        return round.computeSignature(wildcard);
    }
}
