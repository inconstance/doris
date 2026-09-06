// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.doris.plsql.objects;

import org.apache.doris.plsql.Var;
import org.apache.doris.plsql.objects.MethodParams.Arity;

/** Class descriptor and argument validation for the supported DBMS_LOB subset. */
public class DbmsLobClass implements PlClass {
    private static final int MAX_SUBSTR_AMOUNT = 32767;
    public static final DbmsLobClass INSTANCE = new DbmsLobClass();

    private final MethodDictionary<DbmsLob> methodDictionary = new MethodDictionary<>();

    private DbmsLobClass() {
        methodDictionary.put("getlength", (self, args) -> {
            MethodParams params = new MethodParams("getlength", args, Arity.UNARY);
            return params.isNullAt(0) ? new Var(Var.Type.BIGINT) : self.getLength(params.rawAt(0));
        });
        methodDictionary.put("substr", (self, args) -> {
            MethodParams params = new MethodParams("substr", args, Arity.oneOf(1, 2, 3));
            if (params.isNullAt(0) || (args.size() > 1 && params.isNullAt(1))
                    || (args.size() > 2 && params.isNullAt(2))) {
                return new Var(Var.Type.RAW);
            }
            int amount = args.size() > 1 ? params.integerAt(1) : MAX_SUBSTR_AMOUNT;
            int offset = args.size() > 2 ? params.integerAt(2) : 1;
            if (amount < 1 || amount > MAX_SUBSTR_AMOUNT || offset < 1) {
                return new Var(Var.Type.RAW);
            }
            return self.substr(params.rawAt(0), amount, offset);
        });
    }

    @Override
    public DbmsLob newInstance() {
        return new DbmsLob(this);
    }

    @Override
    public MethodDictionary<DbmsLob> methodDictionary() {
        return methodDictionary;
    }
}
