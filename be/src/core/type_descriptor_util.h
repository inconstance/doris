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

#pragma once

#include <cstdint>
#include <memory>
#include <vector>

#include "common/status.h"
#include "core/column/column.h"
#include "core/data_type/define_primitive_type.h"

namespace doris {

class Block;
class VExprContext;

inline constexpr uint64_t TYPE_DESCRIPTOR_DEFAULT = 0;
inline constexpr uint64_t TYPE_DESCRIPTOR_CODE_MASK = 0xFFFFULL;
inline constexpr uint64_t TYPE_DESCRIPTOR_UNSIGNED_MASK = 1ULL << 16;
inline constexpr uint64_t TYPE_DESCRIPTOR_SUPPORTED_MASK = TYPE_DESCRIPTOR_UNSIGNED_MASK;

inline bool is_unsigned_integer_descriptor(uint64_t descriptor) {
    return descriptor == TYPE_DESCRIPTOR_UNSIGNED_MASK;
}

inline bool is_unsigned_bigint_descriptor(uint64_t descriptor, PrimitiveType carrier) {
    return is_unsigned_integer_descriptor(descriptor) && carrier == TYPE_LARGEINT;
}

inline bool is_valid_type_descriptor_carrier(uint64_t descriptor, PrimitiveType carrier) {
    if ((descriptor & ~TYPE_DESCRIPTOR_SUPPORTED_MASK) != 0) return false;
    if (!is_unsigned_integer_descriptor(descriptor)) return descriptor == TYPE_DESCRIPTOR_DEFAULT;
    return carrier == TYPE_SMALLINT || carrier == TYPE_INT || carrier == TYPE_BIGINT ||
           carrier == TYPE_LARGEINT;
}

Status validate_unsigned_range(const IColumn& column, PrimitiveType carrier_type,
                               uint64_t type_descriptor);

Status get_unsigned_maximum(PrimitiveType carrier_type, Int128& maximum);

Status validate_unsigned_result_block(
        const Block& block, const std::vector<std::shared_ptr<VExprContext>>& output_expr_ctxs);

} // namespace doris
