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

#include <algorithm>
#include <cctype>
#include <cstring>
#include <memory>
#include <string>

#include "common/status.h"
#include "core/assert_cast.h"
#include "core/block/block.h"
#include "core/column/column_string.h"
#include "core/column/column_varbinary.h"
#include "core/data_type/data_type_string.h"
#include "core/data_type/data_type_varbinary.h"
#include "exprs/function/function.h"
#include "exprs/function/function_helpers.h"
#include "exprs/function/simple_function_factory.h"

namespace doris {

#include "common/compile_check_begin.h"

class FunctionUtlRawCastToRaw : public IFunction {
public:
    static constexpr auto name = "utl_raw_cast_to_raw";
    static FunctionPtr create() { return std::make_shared<FunctionUtlRawCastToRaw>(); }
    String get_name() const override { return name; }
    size_t get_number_of_arguments() const override { return 1; }
    DataTypePtr get_return_type_impl(const ColumnsWithTypeAndName&) const override {
        return std::make_shared<DataTypeVarbinary>();
    }

    Status execute_impl(FunctionContext*, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t rows) const override {
        const auto& source = assert_cast<const ColumnString&>(
                *block.get_by_position(arguments[0]).column);
        auto output = ColumnVarbinary::create();
        output->get_data().assign(rows, StringView());
        for (size_t i = 0; i < rows; ++i) {
            StringRef value = source.get_data_at(i);
            auto [inline_value, destination] = VarBinaryOP::alloc(output.get(), i, value.size);
            if (value.size > 0) {
                memcpy(destination, value.data, value.size);
            }
            VarBinaryOP::check_and_insert_data(output->get_data()[i], destination,
                                               cast_set<uint32_t>(value.size), inline_value);
        }
        block.replace_by_position(result, std::move(output));
        return Status::OK();
    }
};

class FunctionUtlRawCastToVarchar2 : public IFunction {
public:
    static constexpr auto name = "utl_raw_cast_to_varchar2";
    static FunctionPtr create() { return std::make_shared<FunctionUtlRawCastToVarchar2>(); }
    String get_name() const override { return name; }
    size_t get_number_of_arguments() const override { return 1; }
    DataTypePtr get_return_type_impl(const ColumnsWithTypeAndName&) const override {
        return std::make_shared<DataTypeString>();
    }

    Status execute_impl(FunctionContext*, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t rows) const override {
        auto output = ColumnString::create();
        const auto& source = block.get_by_position(arguments[0]).column;
        if (const auto* raw = check_and_get_column<ColumnVarbinary>(source.get())) {
            for (size_t i = 0; i < rows; ++i) {
                const auto& value = raw->get_data()[i];
                output->insert_data(value.data(), value.size());
            }
        } else if (const auto* string = check_and_get_column<ColumnString>(source.get())) {
            for (size_t i = 0; i < rows; ++i) {
                StringRef value = string->get_data_at(i);
                output->insert_data(value.data, value.size);
            }
        } else {
            return Status::InvalidArgument("UTL_RAW.CAST_TO_VARCHAR2 expects RAW input");
        }
        block.replace_by_position(result, std::move(output));
        return Status::OK();
    }
};

class FunctionUtlRawConvert : public IFunction {
public:
    static constexpr auto name = "utl_raw_convert";
    static FunctionPtr create() { return std::make_shared<FunctionUtlRawConvert>(); }
    String get_name() const override { return name; }
    size_t get_number_of_arguments() const override { return 3; }
    DataTypePtr get_return_type_impl(const ColumnsWithTypeAndName&) const override {
        return std::make_shared<DataTypeVarbinary>();
    }

    Status execute_impl(FunctionContext*, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t rows) const override {
        const auto& raw = assert_cast<const ColumnVarbinary&>(
                *block.get_by_position(arguments[0]).column);
        const auto& to_charset = assert_cast<const ColumnString&>(
                *block.get_by_position(arguments[1]).column);
        const auto& from_charset = assert_cast<const ColumnString&>(
                *block.get_by_position(arguments[2]).column);
        auto output = ColumnVarbinary::create();
        output->get_data().assign(rows, StringView());
        for (size_t i = 0; i < rows; ++i) {
            if (!is_utf8(to_charset.get_data_at(i)) || !is_utf8(from_charset.get_data_at(i))) {
                return Status::InvalidArgument(
                        "UTL_RAW.CONVERT currently supports Oracle UTF8 and AL32UTF8 character sets");
            }
            const auto& value = raw.get_data()[i];
            auto [inline_value, destination] = VarBinaryOP::alloc(output.get(), i, value.size());
            if (value.size() > 0) {
                memcpy(destination, value.data(), value.size());
            }
            VarBinaryOP::check_and_insert_data(output->get_data()[i], destination,
                                               cast_set<uint32_t>(value.size()), inline_value);
        }
        block.replace_by_position(result, std::move(output));
        return Status::OK();
    }

private:
    static bool is_utf8(StringRef charset) {
        std::string name(charset.data, charset.size);
        auto separator = name.rfind('.');
        if (separator != std::string::npos) {
            name.erase(0, separator + 1);
        }
        std::transform(name.begin(), name.end(), name.begin(),
                       [](unsigned char value) { return std::toupper(value); });
        return name == "UTF8" || name == "AL32UTF8";
    }
};

void register_function_utl_raw(SimpleFunctionFactory& factory) {
    factory.register_function<FunctionUtlRawCastToRaw>();
    factory.register_function<FunctionUtlRawCastToVarchar2>();
    factory.register_function<FunctionUtlRawConvert>();
}

#include "common/compile_check_end.h"
} // namespace doris
