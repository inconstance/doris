// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <algorithm>
#include <cstring>
#include <memory>

#include "common/status.h"
#include "core/assert_cast.h"
#include "core/block/block.h"
#include "core/column/column_nullable.h"
#include "core/column/column_string.h"
#include "core/column/column_varbinary.h"
#include "core/column/column_vector.h"
#include "core/data_type/data_type_number.h"
#include "core/data_type/data_type_nullable.h"
#include "core/data_type/data_type_string.h"
#include "core/data_type/data_type_varbinary.h"
#include "exprs/function/function.h"
#include "exprs/function/function_helpers.h"
#include "exprs/function/simple_function_factory.h"

namespace doris {

#include "common/compile_check_begin.h"

class FunctionDbmsLobGetLength : public IFunction {
public:
    static constexpr auto name = "dbms_lob_getlength";
    static FunctionPtr create() { return std::make_shared<FunctionDbmsLobGetLength>(); }
    String get_name() const override { return name; }
    size_t get_number_of_arguments() const override { return 1; }
    DataTypePtr get_return_type_impl(const ColumnsWithTypeAndName&) const override {
        return std::make_shared<DataTypeInt64>();
    }

    Status execute_impl(FunctionContext*, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t rows) const override {
        const auto& source = assert_cast<const ColumnVarbinary&>(
                *block.get_by_position(arguments[0]).column);
        auto output = ColumnInt64::create(rows);
        for (size_t i = 0; i < rows; ++i) {
            output->get_data()[i] = source.get_data()[i].size();
        }
        block.replace_by_position(result, std::move(output));
        return Status::OK();
    }
};

class FunctionDbmsLobSubstr : public IFunction {
public:
    static constexpr auto name = "dbms_lob_substr";
    static FunctionPtr create() { return std::make_shared<FunctionDbmsLobSubstr>(); }
    String get_name() const override { return name; }
    size_t get_number_of_arguments() const override { return 3; }
    DataTypePtr get_return_type_impl(const ColumnsWithTypeAndName&) const override {
        return make_nullable(std::make_shared<DataTypeVarbinary>());
    }

    Status execute_impl(FunctionContext*, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t rows) const override {
        const auto& raw = assert_cast<const ColumnVarbinary&>(
                *block.get_by_position(arguments[0]).column);
        const auto [amount_column, amount_const] =
                unpack_if_const(block.get_by_position(arguments[1]).column);
        const auto [offset_column, offset_const] =
                unpack_if_const(block.get_by_position(arguments[2]).column);
        const auto& amounts = assert_cast<const ColumnInt32&>(*amount_column).get_data();
        const auto& offsets = assert_cast<const ColumnInt32&>(*offset_column).get_data();
        auto output = ColumnVarbinary::create();
        output->get_data().assign(rows, StringView());
        auto null_map = ColumnUInt8::create(rows, 0);
        for (size_t i = 0; i < rows; ++i) {
            int32_t amount = amounts[index_check_const(i, amount_const)];
            int32_t offset = offsets[index_check_const(i, offset_const)];
            if (amount < 1 || amount > 32767 || offset < 1) {
                null_map->get_data()[i] = 1;
                continue;
            }
            const auto& value = raw.get_data()[i];
            size_t start = static_cast<size_t>(offset - 1);
            size_t length = start >= value.size()
                    ? 0 : std::min(static_cast<size_t>(amount), value.size() - start);
            auto [inline_value, destination] = VarBinaryOP::alloc(output.get(), i, length);
            if (length > 0) {
                memcpy(destination, value.data() + start, length);
            }
            VarBinaryOP::check_and_insert_data(output->get_data()[i], destination,
                                               cast_set<uint32_t>(length), inline_value);
        }
        block.replace_by_position(
                result, ColumnNullable::create(std::move(output), std::move(null_map)));
        return Status::OK();
    }
};

class FunctionToBlob : public IFunction {
public:
    static constexpr auto name = "to_blob";
    static FunctionPtr create() { return std::make_shared<FunctionToBlob>(); }
    String get_name() const override { return name; }
    size_t get_number_of_arguments() const override { return 1; }
    DataTypePtr get_return_type_impl(const ColumnsWithTypeAndName&) const override {
        return std::make_shared<DataTypeVarbinary>();
    }
    Status execute_impl(FunctionContext*, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t rows) const override {
        block.replace_by_position(result,
                block.get_by_position(arguments[0]).column->clone_resized(rows));
        return Status::OK();
    }
};

class FunctionEmptyClob : public IFunction {
public:
    static constexpr auto name = "empty_clob";
    static FunctionPtr create() { return std::make_shared<FunctionEmptyClob>(); }
    String get_name() const override { return name; }
    size_t get_number_of_arguments() const override { return 0; }
    DataTypePtr get_return_type_impl(const ColumnsWithTypeAndName&) const override {
        return std::make_shared<DataTypeString>();
    }
    Status execute_impl(FunctionContext*, Block& block, const ColumnNumbers&,
                        uint32_t result, size_t rows) const override {
        auto output = ColumnString::create();
        for (size_t i = 0; i < rows; ++i) {
            output->insert_default();
        }
        block.replace_by_position(result, std::move(output));
        return Status::OK();
    }
};

void register_function_dbms_lob(SimpleFunctionFactory& factory) {
    factory.register_function<FunctionDbmsLobGetLength>();
    factory.register_function<FunctionDbmsLobSubstr>();
    factory.register_function<FunctionToBlob>();
    factory.register_function<FunctionEmptyClob>();
}

#include "common/compile_check_end.h"
} // namespace doris
