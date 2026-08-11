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
import org.apache.doris.nereids.types.DoubleType;
import org.apache.doris.nereids.types.FloatType;
import org.apache.doris.nereids.types.IntegerType;
import org.apache.doris.nereids.types.LargeIntType;
import org.apache.doris.nereids.types.SmallIntType;
import org.apache.doris.nereids.types.TinyIntType;
import org.apache.doris.nereids.types.coercion.IntegralType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class AbsSignatureTest {

    @Test
    public void testMysqlCompatibleNumericSignatures() {
        List<FunctionSignature> signatures = Abs.SIGNATURES;

        assertSignature(signatures, TinyIntType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, SmallIntType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, IntegerType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, BigIntType.INSTANCE, BigIntType.INSTANCE);
        assertSignature(signatures, LargeIntType.INSTANCE, LargeIntType.INSTANCE);
        assertSignature(signatures, FloatType.INSTANCE, DoubleType.INSTANCE);
        assertSignature(signatures, DoubleType.INSTANCE, DoubleType.INSTANCE);
    }

    @Test
    public void testUnsignedDescriptorPropagation() {
        assertUnsignedReturn(SmallIntType.INSTANCE, BigIntType.INSTANCE);
        assertUnsignedReturn(IntegerType.INSTANCE, BigIntType.INSTANCE);
        assertUnsignedReturn(BigIntType.INSTANCE, BigIntType.INSTANCE);
        assertUnsignedReturn(LargeIntType.INSTANCE, LargeIntType.INSTANCE);
    }

    private void assertUnsignedReturn(IntegralType carrierType, DataType expectedCarrierType) {
        IntegralType unsignedType = carrierType.withTypeDescriptor(Type.TYPE_DESCRIPTOR_UNSIGNED_MASK);
        Abs abs = new Abs(new SlotReference("unsigned_value", unsignedType));
        FunctionSignature signature = Abs.SIGNATURES.stream()
                .filter(candidate -> candidate.argumentsTypes.get(0).equals(carrierType))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        DataType returnType = abs.computeSignature(signature).returnType;

        Assertions.assertEquals(expectedCarrierType, returnType);
        Assertions.assertTrue(((IntegralType) returnType).isUnsigned());
    }

    private void assertSignature(List<FunctionSignature> signatures,
            org.apache.doris.nereids.types.DataType argumentType,
            org.apache.doris.nereids.types.DataType returnType) {
        Assertions.assertTrue(signatures.stream().anyMatch(signature
                -> signature.argumentsTypes.size() == 1
                && signature.argumentsTypes.get(0).equals(argumentType)
                && signature.returnType.equals(returnType)));
    }
}
