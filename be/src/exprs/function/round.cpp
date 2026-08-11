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

#include "exprs/function/round.h"

#include "exprs/function/simple_function_factory.h"

namespace doris {

// We split round funcs from register_function_math() in math.cpp to here,
// so that to speed up compile time and make code more readable.
void register_function_round(SimpleFunctionFactory& factory) {
#define REGISTER_CEIL_NUMBER(TYPE) factory.register_function<FunctionCeilNumber<TYPE>>();
    REGISTER_CEIL_NUMBER(TYPE_BOOLEAN)
    REGISTER_CEIL_NUMBER(TYPE_TINYINT)
    REGISTER_CEIL_NUMBER(TYPE_SMALLINT)
    REGISTER_CEIL_NUMBER(TYPE_INT)
    REGISTER_CEIL_NUMBER(TYPE_BIGINT)
    REGISTER_CEIL_NUMBER(TYPE_LARGEINT)
    REGISTER_CEIL_NUMBER(TYPE_FLOAT)
#undef REGISTER_CEIL_NUMBER

#define REGISTER_FLOOR_NUMBER(TYPE) factory.register_function<FunctionFloorNumber<TYPE>>();
    REGISTER_FLOOR_NUMBER(TYPE_TINYINT)
    REGISTER_FLOOR_NUMBER(TYPE_SMALLINT)
    REGISTER_FLOOR_NUMBER(TYPE_INT)
    REGISTER_FLOOR_NUMBER(TYPE_BIGINT)
    REGISTER_FLOOR_NUMBER(TYPE_LARGEINT)
    REGISTER_FLOOR_NUMBER(TYPE_FLOAT)
#undef REGISTER_FLOOR_NUMBER

#define REGISTER_ROUND_INTEGER(TYPE)                            \
    factory.register_function<FunctionRoundInteger<TYPE, 1>>(); \
    factory.register_function<FunctionRoundInteger<TYPE, 2>>();
    REGISTER_ROUND_INTEGER(TYPE_TINYINT)
    REGISTER_ROUND_INTEGER(TYPE_SMALLINT)
    REGISTER_ROUND_INTEGER(TYPE_INT)
    REGISTER_ROUND_INTEGER(TYPE_BIGINT)
    REGISTER_ROUND_INTEGER(TYPE_LARGEINT)
#undef REGISTER_ROUND_INTEGER

    factory.register_function<FunctionRoundReal<TYPE_FLOAT, 1>>();
    factory.register_function<FunctionRoundReal<TYPE_FLOAT, 2>>();
    factory.register_function<FunctionRoundReal<TYPE_DOUBLE, 1>>();
    factory.register_function<FunctionRoundReal<TYPE_DOUBLE, 2>>();

    factory.register_function<FunctionTruncateReal<TYPE_FLOAT, 1>>();
    factory.register_function<FunctionTruncateReal<TYPE_FLOAT, 2>>();
    factory.register_function<FunctionTruncateReal<TYPE_DOUBLE, 1>>();
    factory.register_function<FunctionTruncateReal<TYPE_DOUBLE, 2>>();

#define REGISTER_TRUNCATE_INTEGER(TYPE)                            \
    factory.register_function<FunctionTruncateInteger<TYPE, 1>>(); \
    factory.register_function<FunctionTruncateInteger<TYPE, 2>>();
    REGISTER_TRUNCATE_INTEGER(TYPE_TINYINT)
    REGISTER_TRUNCATE_INTEGER(TYPE_SMALLINT)
    REGISTER_TRUNCATE_INTEGER(TYPE_INT)
    REGISTER_TRUNCATE_INTEGER(TYPE_BIGINT)
    REGISTER_TRUNCATE_INTEGER(TYPE_LARGEINT)
#undef REGISTER_TRUNCATE_INTEGER

#define REGISTER_ROUND_FUNCTIONS(IMPL)                                                        \
    factory.register_function<                                                                \
            FunctionRounding<IMPL<FloorName>, RoundingMode::Floor, TieBreakingMode::Auto>>(); \
    factory.register_function<                                                                \
            FunctionRounding<IMPL<CeilName>, RoundingMode::Ceil, TieBreakingMode::Auto>>();   \
    factory.register_function<FunctionRounding<IMPL<RoundBankersName>, RoundingMode::Round,   \
                                               TieBreakingMode::Bankers>>();
    REGISTER_ROUND_FUNCTIONS(DoubleRoundOneImpl)
    REGISTER_ROUND_FUNCTIONS(DoubleRoundTwoImpl)
#undef REGISTER_ROUND_FUNCTIONS

#define REGISTER_ROUND_FUNCTIONS_IMPL(IMPL, TYPE)                                                 \
    factory.register_function<FunctionRounding<IMPL<TruncateName, TYPE>, RoundingMode::Trunc,     \
                                               TieBreakingMode::Auto>>();                         \
    factory.register_function<FunctionRounding<IMPL<FloorName, TYPE>, RoundingMode::Floor,        \
                                               TieBreakingMode::Auto>>();                         \
    factory.register_function<FunctionRounding<IMPL<RoundName, TYPE>, RoundingMode::Round,        \
                                               TieBreakingMode::Auto>>();                         \
    factory.register_function<                                                                    \
            FunctionRounding<IMPL<CeilName, TYPE>, RoundingMode::Ceil, TieBreakingMode::Auto>>(); \
    factory.register_function<FunctionRounding<IMPL<RoundBankersName, TYPE>, RoundingMode::Round, \
                                               TieBreakingMode::Bankers>>();

#define REGISTER_ROUND_FUNCTIONS(IMPL)                    \
    REGISTER_ROUND_FUNCTIONS_IMPL(IMPL, TYPE_DECIMAL32)   \
    REGISTER_ROUND_FUNCTIONS_IMPL(IMPL, TYPE_DECIMAL64)   \
    REGISTER_ROUND_FUNCTIONS_IMPL(IMPL, TYPE_DECIMAL128I) \
    REGISTER_ROUND_FUNCTIONS_IMPL(IMPL, TYPE_DECIMAL256)
    REGISTER_ROUND_FUNCTIONS(DecimalRoundOneImpl)
    REGISTER_ROUND_FUNCTIONS(DecimalRoundTwoImpl)
#undef REGISTER_ROUND_FUNCTIONS
#undef REGISTER_ROUND_FUNCTIONS_IMPL

    factory.register_alias("ceil", "dceil");
    factory.register_alias("ceil", "ceiling");
    factory.register_alias("floor", "dfloor");
    factory.register_alias("round", "dround");
    factory.register_alias("truncate", "trunc");
}

} // namespace doris
