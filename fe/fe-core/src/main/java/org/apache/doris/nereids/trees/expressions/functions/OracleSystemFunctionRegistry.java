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

package org.apache.doris.nereids.trees.expressions.functions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Maps Oracle system package calls to Doris internal builtin functions. */
public final class OracleSystemFunctionRegistry {
    private static final Map<String, String> FUNCTION_NAMES = ImmutableMap.<String, String>builder()
            .put(qualifiedName("UTL_RAW", "CAST_TO_RAW"), "utl_raw_cast_to_raw")
            .put(qualifiedName("UTL_RAW", "CAST_TO_VARCHAR2"), "utl_raw_cast_to_varchar2")
            .put(qualifiedName("UTL_RAW", "CONVERT"), "utl_raw_convert")
            .put(qualifiedName("DBMS_LOB", "GETLENGTH"), "dbms_lob_getlength")
            .put(qualifiedName("DBMS_LOB", "SUBSTR"), "dbms_lob_substr")
            .put(qualifiedName("DBMS_RANDOM", "RANDOM"), "dbms_random_random")
            .put(qualifiedName("DBMS_RANDOM", "STRING"), "dbms_random_string")
            .put(qualifiedName("DBMS_RANDOM", "VALUE"), "dbms_random_value")
            .build();
    private static final Set<String> FUNCTIONS_WITH_BARE_ACCESS = ImmutableSet.of(
            qualifiedName("DBMS_RANDOM", "RANDOM"),
            qualifiedName("DBMS_RANDOM", "VALUE")
    );

    private OracleSystemFunctionRegistry() {
    }

    /** Resolve a package-qualified Oracle function name to an unqualified Doris builtin name. */
    public static Optional<String> resolve(String packageName, String functionName) {
        if (packageName == null || functionName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(FUNCTION_NAMES.get(qualifiedName(packageName, functionName)));
    }

    /** Resolve only functions for which Oracle permits omitted parentheses. */
    public static Optional<String> resolveBareAccess(String packageName, String functionName) {
        if (packageName == null || functionName == null) {
            return Optional.empty();
        }
        String qualifiedName = qualifiedName(packageName, functionName);
        if (!FUNCTIONS_WITH_BARE_ACCESS.contains(qualifiedName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(FUNCTION_NAMES.get(qualifiedName));
    }

    private static String qualifiedName(String packageName, String functionName) {
        return (packageName + "." + functionName).toUpperCase(Locale.ROOT);
    }
}
