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

package org.apache.doris.plsql.objects;

import org.apache.doris.plsql.Var;
import org.apache.doris.plsql.exception.PlValidationException;
import org.apache.doris.plsql.objects.MethodParams.Arity;

import java.math.BigDecimal;

/** Class descriptor for DBMS_RANDOM. */
public class DbmsRandomClass implements PlClass {
    private static final int MAX_STRING_LENGTH = 32767;

    public static final DbmsRandomClass INSTANCE = new DbmsRandomClass();

    private final MethodDictionary<DbmsRandom> methodDictionary = new MethodDictionary<>();

    private DbmsRandomClass() {
        methodDictionary.put("random", (self, args) -> {
            Arity.NULLARY.check("random", args);
            return self.random();
        });
        methodDictionary.put("string", (self, args) -> {
            MethodParams params = new MethodParams("string", args, Arity.BINARY);
            if (params.isNullAt(0) || params.isNullAt(1)) {
                return new Var(Var.Type.STRING);
            }
            int length = params.integerAt(1);
            if (length < 0 || length > MAX_STRING_LENGTH) {
                throw new PlValidationException(null, "DBMS_RANDOM.STRING length must be between 0 and "
                        + MAX_STRING_LENGTH + ", got " + length);
            }
            return self.string(params.stringAt(0), length);
        });
        methodDictionary.put("value", (self, args) -> {
            MethodParams params = new MethodParams("value", args, Arity.oneOf(0, 2));
            if (args.isEmpty()) {
                return self.value();
            }
            if (params.isNullAt(0) || params.isNullAt(1)) {
                return new Var(Var.Type.DECIMAL);
            }
            BigDecimal low = params.decimalAt(0);
            BigDecimal high = params.decimalAt(1);
            if (low.compareTo(high) >= 0) {
                throw new PlValidationException(null,
                        "DBMS_RANDOM.VALUE lower bound must be less than upper bound");
            }
            return self.value(low, high);
        });
    }

    @Override
    public DbmsRandom newInstance() {
        return new DbmsRandom(this);
    }

    @Override
    public MethodDictionary<DbmsRandom> methodDictionary() {
        return methodDictionary;
    }
}
