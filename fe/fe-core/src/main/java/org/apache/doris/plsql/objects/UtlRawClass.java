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

import com.google.common.io.BaseEncoding;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Class descriptor and argument validation for UTL_RAW. */
public class UtlRawClass implements PlClass {
    public static final UtlRawClass INSTANCE = new UtlRawClass();

    private final MethodDictionary<UtlRaw> methodDictionary = new MethodDictionary<>();

    private UtlRawClass() {
        methodDictionary.put("cast_to_raw", (self, args) -> {
            MethodParams params = new MethodParams("cast_to_raw", args, Arity.UNARY);
            return params.isNullAt(0) ? new Var(Var.Type.RAW) : self.castToRaw(params.stringAt(0));
        });
        methodDictionary.put("cast_to_varchar2", (self, args) -> {
            MethodParams params = new MethodParams("cast_to_varchar2", args, Arity.UNARY);
            if (params.isNullAt(0)) {
                return new Var(Var.Type.STRING);
            }
            Var input = args.get(0);
            if (input.type == Var.Type.STRING) {
                try {
                    byte[] bytes = BaseEncoding.base16().decode(params.stringAt(0).toUpperCase(Locale.ROOT));
                    return new Var(new String(bytes, StandardCharsets.ISO_8859_1));
                } catch (IllegalArgumentException e) {
                    throw new PlValidationException(null,
                            "UTL_RAW.CAST_TO_VARCHAR2 implicit RAW input must be hexadecimal");
                }
            }
            return self.castToVarchar2(params.rawAt(0));
        });
        methodDictionary.put("convert", (self, args) -> {
            MethodParams params = new MethodParams("convert", args, Arity.of(3));
            if (params.isNullAt(0) || params.isNullAt(1) || params.isNullAt(2)) {
                return new Var(Var.Type.RAW);
            }
            try {
                return self.convert(params.rawAt(0), charset(params.stringAt(1)), charset(params.stringAt(2)));
            } catch (CharacterCodingException e) {
                throw new PlValidationException(null, "UTL_RAW.CONVERT cannot convert the supplied RAW value");
            }
        });
    }

    private static Charset charset(String oracleName) {
        String name = oracleName.substring(oracleName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        switch (name) {
            case "UTF8":
            case "AL32UTF8":
                return StandardCharsets.UTF_8;
            case "US7ASCII":
                return StandardCharsets.US_ASCII;
            case "WE8ISO8859P1":
                return StandardCharsets.ISO_8859_1;
            default:
                throw new PlValidationException(null, "UTL_RAW.CONVERT unsupported character set: " + oracleName);
        }
    }

    @Override
    public UtlRaw newInstance() {
        return new UtlRaw(this);
    }

    @Override
    public MethodDictionary<UtlRaw> methodDictionary() {
        return methodDictionary;
    }
}
