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

#include "core/type_descriptor_util.h"

#include <type_traits>

#include "common/compiler_util.h"
#include "core/assert_cast.h"
#include "core/block/block.h"
#include "core/column/column_const.h"
#include "core/column/column_nullable.h"
#include "core/column/column_vector.h"
#include "exprs/vexpr.h"
#include "exprs/vexpr_context.h"

namespace doris {

template <typename ColumnType, size_t logical_bits>
Status validate_values(const IColumn& column, const NullMap* null_map) {
    const auto& values = assert_cast<const ColumnType&>(column).get_data();
    using ValueType = std::remove_cv_t<std::remove_reference_t<decltype(values[0])>>;
    using UnsignedType = std::make_unsigned_t<ValueType>;
    bool overflow = false;
    for (size_t i = 0; i < values.size(); ++i) {
        if (null_map != nullptr && (*null_map)[i]) {
            continue;
        }
        overflow |= (static_cast<UnsignedType>(values[i]) >> logical_bits) != 0;
    }
    if (UNLIKELY(overflow)) {
        return Status::Error<ErrorCode::ARITHMETIC_OVERFLOW_ERRROR>(
                "Integer value is out of range for the required unsigned logical type");
    }
    return Status::OK();
}

Status get_unsigned_maximum(PrimitiveType carrier_type, Int128& maximum) {
    switch (carrier_type) {
    case TYPE_SMALLINT:
        maximum = 255;
        return Status::OK();
    case TYPE_INT:
        maximum = 65535;
        return Status::OK();
    case TYPE_BIGINT:
        maximum = 4294967295LL;
        return Status::OK();
    case TYPE_LARGEINT:
        maximum = (Int128 {1} << 64) - 1;
        return Status::OK();
    default:
        return Status::InvalidArgument("Invalid unsigned integer carrier: {}", carrier_type);
    }
}

Status validate_unsigned_range(const IColumn& input, PrimitiveType carrier_type,
                               uint64_t type_descriptor) {
    if (type_descriptor == TYPE_DESCRIPTOR_DEFAULT) {
        return Status::OK();
    }
    if (!is_valid_type_descriptor_carrier(type_descriptor, carrier_type)) {
        return Status::InvalidArgument("Invalid type descriptor/carrier mapping: {}/{}",
                                       type_descriptor, carrier_type);
    }
    const IColumn* column = &input;
    if (const auto* const_column = check_and_get_column<ColumnConst>(column)) {
        column = &const_column->get_data_column();
    }
    const NullMap* null_map = nullptr;
    if (const auto* nullable = check_and_get_column<ColumnNullable>(column)) {
        null_map = &nullable->get_null_map_data();
        column = &nullable->get_nested_column();
    }
    switch (carrier_type) {
    case TYPE_SMALLINT:
        return validate_values<ColumnInt16, 8>(*column, null_map);
    case TYPE_INT:
        return validate_values<ColumnInt32, 16>(*column, null_map);
    case TYPE_BIGINT:
        return validate_values<ColumnInt64, 32>(*column, null_map);
    case TYPE_LARGEINT:
        return validate_values<ColumnInt128, 64>(*column, null_map);
    default:
        return Status::InvalidArgument("Unsupported unsigned range validation carrier type: {}",
                                       carrier_type);
    }
}

Status validate_unsigned_result_block(
        const Block& block, const std::vector<std::shared_ptr<VExprContext>>& output_expr_ctxs) {
    if (block.columns() != output_expr_ctxs.size()) {
        return Status::InternalError("Result block has {} columns but {} output expressions",
                                     block.columns(), output_expr_ctxs.size());
    }
    for (size_t i = 0; i < output_expr_ctxs.size(); ++i) {
        const auto& root = output_expr_ctxs[i]->root();
        RETURN_IF_ERROR(validate_unsigned_range(*block.get_by_position(i).column,
                                                root->data_type()->get_primitive_type(),
                                                root->type_descriptor()));
    }
    return Status::OK();
}

} // namespace doris
