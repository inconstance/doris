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
import org.apache.doris.plsql.exception.ArityException;
import org.apache.doris.plsql.exception.PlValidationException;
import org.apache.doris.plsql.exception.TypeException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Signatures and argument handling for DBMS_OBFUSCATION_TOOLKIT. */
public class DbmsObfuscationToolkitClass implements PlClass {
    public static final DbmsObfuscationToolkitClass INSTANCE = new DbmsObfuscationToolkitClass();

    private final MethodDictionary<DbmsObfuscationToolkit> methodDictionary = new MethodDictionary<>();

    private DbmsObfuscationToolkitClass() {
        methodDictionary.putParameterized("des3encrypt", (self, arguments) -> des3(self, true, arguments));
        methodDictionary.putParameterized("des3decrypt", (self, arguments) -> des3(self, false, arguments));
    }

    private Var des3(DbmsObfuscationToolkit self, boolean encrypt, List<MethodArgument> arguments) {
        String methodName = encrypt ? "des3encrypt" : "des3decrypt";
        if (arguments.size() < 2 || arguments.size() > 5) {
            throw new ArityException(null, "wrong number of arguments in call to '" + methodName + "'");
        }

        boolean stringOverload = isStringOverload(arguments);
        String inputName = stringOverload ? "input_string" : "input";
        String keyName = stringOverload ? "key_string" : "key";
        String outputName = stringOverload
                ? (encrypt ? "encrypted_string" : "decrypted_string")
                : (encrypt ? "encrypted_data" : "decrypted_data");
        validateNames(arguments, inputName, keyName, outputName,
                stringOverload ? "iv_string" : "iv");
        MethodArgument input = find(arguments, inputName, 0);
        MethodArgument key = find(arguments, keyName, 1);
        MethodArgument output = findOptional(arguments, outputName);
        if (output == null && arguments.size() >= 3 && arguments.get(2).name() == null
                && (arguments.get(2).value().isNull()
                || arguments.get(2).value().type == (stringOverload ? Var.Type.STRING : Var.Type.RAW))) {
            output = arguments.get(2);
        }
        boolean procedure = output != null;
        MethodArgument whichArg = findOptional(arguments, "which", procedure ? 3 : 2);
        MethodArgument ivArg = findOptional(arguments, stringOverload ? "iv_string" : "iv",
                procedure ? 4 : 3);

        byte[] inputBytes = bytes(input, stringOverload);
        byte[] keyBytes = bytes(key, stringOverload);
        int which = whichArg == null ? 0 : integer(whichArg);
        byte[] iv = ivArg == null || ivArg.value().isNull() ? null : bytes(ivArg, stringOverload);
        byte[] result = encrypt ? self.encrypt(inputBytes, keyBytes, which, iv)
                : self.decrypt(inputBytes, keyBytes, which, iv);
        Var resultVar = stringOverload
                ? new Var(new String(result, StandardCharsets.ISO_8859_1)) : new Var(result);
        if (procedure) {
            if (!output.isWritable()) {
                throw new PlValidationException(null, outputName + " must be a writable variable");
            }
            output.setOutput(resultVar);
            return null;
        }
        return resultVar;
    }

    private static void validateNames(List<MethodArgument> arguments, String inputName,
            String keyName, String outputName, String ivName) {
        Set<String> allowed = new HashSet<>();
        allowed.add(inputName);
        allowed.add(keyName);
        allowed.add(outputName);
        allowed.add("which");
        allowed.add(ivName);
        Set<String> seen = new HashSet<>();
        boolean namedSeen = false;
        for (MethodArgument argument : arguments) {
            if (argument.name() == null) {
                if (namedSeen) {
                    throw new PlValidationException(null,
                            "positional argument cannot follow a named argument");
                }
                continue;
            }
            namedSeen = true;
            String name = argument.name().toLowerCase(Locale.ROOT);
            if (!allowed.contains(name)) {
                throw new PlValidationException(null, "unknown argument '" + argument.name() + "'");
            }
            if (!seen.add(name)) {
                throw new PlValidationException(null, "duplicate argument '" + argument.name() + "'");
            }
        }
    }

    private static boolean isStringOverload(List<MethodArgument> arguments) {
        for (MethodArgument argument : arguments) {
            if (argument.name() != null && argument.name().toLowerCase(Locale.ROOT).contains("string")) {
                return true;
            }
        }
        return !arguments.isEmpty() && arguments.get(0).value().type == Var.Type.STRING;
    }

    private static MethodArgument find(List<MethodArgument> arguments, String name, int position) {
        MethodArgument argument = findOptional(arguments, name, position);
        if (argument == null) {
            throw new PlValidationException(null, "missing required argument '" + name + "'");
        }
        return argument;
    }

    private static MethodArgument findOptional(List<MethodArgument> arguments, String name) {
        return findOptional(arguments, name, -1);
    }

    private static MethodArgument findOptional(List<MethodArgument> arguments, String name, int position) {
        for (MethodArgument argument : arguments) {
            if (argument.name() != null && argument.name().equalsIgnoreCase(name)) {
                return argument;
            }
        }
        if (position >= 0 && position < arguments.size() && arguments.get(position).name() == null) {
            return arguments.get(position);
        }
        return null;
    }

    private static byte[] bytes(MethodArgument argument, boolean stringOverload) {
        if (argument.value().isNull()) {
            return new byte[0];
        }
        if (stringOverload && argument.value().type == Var.Type.STRING) {
            return ((String) argument.value().value).getBytes(StandardCharsets.ISO_8859_1);
        }
        if (!stringOverload && argument.value().type == Var.Type.RAW) {
            return (byte[]) argument.value().value;
        }
        throw new TypeException(null, stringOverload ? Var.Type.STRING : Var.Type.RAW,
                argument.value().type, argument.value().value);
    }

    private static int integer(MethodArgument argument) {
        Integer value = new MethodParams("which", java.util.Collections.singletonList(argument.value()),
                MethodParams.Arity.UNARY).integerAt(0);
        return value == null ? -1 : value;
    }

    @Override
    public DbmsObfuscationToolkit newInstance() {
        return new DbmsObfuscationToolkit(this);
    }

    @Override
    public MethodDictionary<DbmsObfuscationToolkit> methodDictionary() {
        return methodDictionary;
    }
}
