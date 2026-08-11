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
import org.apache.doris.nereids.types.BigIntType;
import org.apache.doris.nereids.types.DataType;
import org.apache.doris.nereids.types.DateTimeV2Type;
import org.apache.doris.nereids.types.DecimalV3Type;
import org.apache.doris.nereids.types.DoubleType;
import org.apache.doris.nereids.types.FloatType;
import org.apache.doris.nereids.types.IntegerType;
import org.apache.doris.nereids.types.LargeIntType;
import org.apache.doris.nereids.types.SmallIntType;
import org.apache.doris.nereids.types.TinyIntType;
import org.apache.doris.nereids.types.VarcharType;
import org.apache.doris.nereids.types.coercion.IntegralType;
import org.apache.doris.qe.ConnectContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CeilSignatureTest {
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
    void testMysqlCompatibleNumericSignatures() {
        List<FunctionSignature> signatures = Ceil.SIGNATURES;

        assertSignature(signatures, TinyIntType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, SmallIntType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, IntegerType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, BigIntType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, LargeIntType.INSTANCE, LargeIntType.INSTANCE);
        assertSignature(signatures, FloatType.INSTANCE, DoubleType.INSTANCE);
        assertSignature(signatures, DoubleType.INSTANCE, DoubleType.INSTANCE);
    }

    @Test
    void testMysqlDecimalResultUsesBigIntWhenMaxLengthIsBelowTwenty() {
        ConnectContext.get().getSessionVariable().setSqlDialect("doris");
        DecimalV3Type inputType = DecimalV3Type.createDecimalV3Type(12, 4);
        FunctionSignature result = computeDecimalSignature(inputType);

        Assertions.assertEquals(BigIntType.INSTANCE, result.returnType);
        Assertions.assertEquals(inputType, result.argumentsTypes.get(0));
    }

    @Test
    void testMysqlLargeDecimalResultHasZeroScaleAndCarryDigit() {
        ConnectContext.get().getSessionVariable().setSqlDialect("doris");
        DecimalV3Type inputType = DecimalV3Type.createDecimalV3Type(38, 4);

        Assertions.assertEquals(DecimalV3Type.createDecimalV3Type(35, 0),
                computeDecimalSignature(inputType).returnType);
    }

    @Test
    void testMysqlDecimalBigIntDisplayLengthBoundary() {
        ConnectContext.get().getSessionVariable().setSqlDialect("doris");

        Assertions.assertEquals(BigIntType.INSTANCE,
                computeDecimalSignature(DecimalV3Type.createDecimalV3Type(18, 0)).returnType);
        Assertions.assertEquals(DecimalV3Type.createDecimalV3Type(19, 0),
                computeDecimalSignature(DecimalV3Type.createDecimalV3Type(19, 0)).returnType);
    }

    @Test
    void testOracleNumberPreservesMappedDecimalType() {
        ConnectContext.get().getSessionVariable().setSqlDialect("oracle");
        DecimalV3Type inputType = DecimalV3Type.createDecimalV3Type(12, 4);

        Assertions.assertEquals(inputType, computeDecimalSignature(inputType).returnType);
    }

    @Test
    void testDatetimeSignaturesAreOracleOnlyAndReturnOracleDateMapping() {
        Ceil ceil = new Ceil(new SlotReference("datetime_value", DateTimeV2Type.of(6)));

        ConnectContext.get().getSessionVariable().setSqlDialect("doris");
        Assertions.assertFalse(ceil.getSignatures().stream().anyMatch(signature
                -> signature.argumentsTypes.get(0) instanceof DateTimeV2Type));

        ConnectContext.get().getSessionVariable().setSqlDialect("oracle");
        Assertions.assertTrue(ceil.getSignatures().stream().anyMatch(signature
                -> signature.argumentsTypes.size() == 1
                && signature.argumentsTypes.get(0) instanceof DateTimeV2Type
                && signature.returnType.equals(DateTimeV2Type.SYSTEM_DEFAULT)));
        Assertions.assertTrue(ceil.getSignatures().stream().anyMatch(signature
                -> signature.argumentsTypes.size() == 2
                && signature.argumentsTypes.get(0) instanceof DateTimeV2Type
                && signature.argumentsTypes.get(1).equals(VarcharType.SYSTEM_DEFAULT)
                && signature.returnType.equals(DateTimeV2Type.SYSTEM_DEFAULT)));
    }

    @Test
    void testUnsignedDescriptorPropagation() {
        assertUnsignedReturn(SmallIntType.INSTANCE, BigIntType.INSTANCE);
        assertUnsignedReturn(IntegerType.INSTANCE, BigIntType.INSTANCE);
        assertUnsignedReturn(BigIntType.INSTANCE, BigIntType.INSTANCE);
        assertUnsignedReturn(LargeIntType.INSTANCE, LargeIntType.INSTANCE);
    }

    private FunctionSignature computeDecimalSignature(DecimalV3Type inputType) {
        Ceil ceil = new Ceil(new SlotReference("decimal_value", inputType));
        FunctionSignature wildcard = Ceil.SIGNATURES.stream()
                .filter(signature -> signature.argumentsTypes.size() == 1
                        && signature.argumentsTypes.get(0) instanceof DecimalV3Type)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        return ceil.computeSignature(wildcard);
    }

    private void assertUnsignedReturn(IntegralType carrierType, DataType expectedCarrierType) {
        IntegralType unsignedType = carrierType.withTypeDescriptor(Type.TYPE_DESCRIPTOR_UNSIGNED_MASK);
        Ceil ceil = new Ceil(new SlotReference("unsigned_value", unsignedType));
        FunctionSignature signature = Ceil.SIGNATURES.stream()
                .filter(candidate -> candidate.argumentsTypes.size() == 1
                        && candidate.argumentsTypes.get(0).equals(carrierType))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        DataType returnType = ceil.computeSignature(signature).returnType;

        Assertions.assertEquals(expectedCarrierType, returnType);
        Assertions.assertTrue(((IntegralType) returnType).isUnsigned());
    }

    private void assertSignature(List<FunctionSignature> signatures, DataType argumentType,
            DataType returnType) {
        Assertions.assertTrue(signatures.stream().anyMatch(signature
                -> signature.argumentsTypes.size() == 1
                && signature.argumentsTypes.get(0).equals(argumentType)
                && signature.returnType.equals(returnType)));
    }
}
