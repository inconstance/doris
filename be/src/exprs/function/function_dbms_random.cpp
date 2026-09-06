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

#include <glog/logging.h>

#include <cstdint>
#include <limits>
#include <memory>
#include <random>
#include <string>
#include <string_view>

#include "common/status.h"
#include "core/assert_cast.h"
#include "core/block/block.h"
#include "core/block/column_numbers.h"
#include "core/column/column_const.h"
#include "core/column/column_string.h"
#include "core/column/column_vector.h"
#include "core/data_type/data_type_number.h"
#include "core/data_type/data_type_string.h"
#include "exprs/function/function.h"
#include "exprs/function/simple_function_factory.h"
#include "exprs/function_context.h"

namespace doris {

#include "common/compile_check_begin.h"
class DbmsRandomFunction : public IFunction {
public:
    bool use_default_implementation_for_constants() const override { return false; }

    Status open(FunctionContext* context, FunctionContext::FunctionStateScope scope) override {
        if (scope == FunctionContext::THREAD_LOCAL) {
            auto generator = std::make_shared<std::mt19937_64>(std::random_device()());
            context->set_function_state(scope, generator);
        }
        return Status::OK();
    }

protected:
    static std::mt19937_64* get_generator(FunctionContext* context) {
        auto* generator = reinterpret_cast<std::mt19937_64*>(
                context->get_function_state(FunctionContext::THREAD_LOCAL));
        DCHECK(generator != nullptr);
        return generator;
    }
};

class DbmsRandomValue : public DbmsRandomFunction {
public:
    static constexpr auto name = "dbms_random_value";

    static FunctionPtr create() { return std::make_shared<DbmsRandomValue>(); }

    String get_name() const override { return name; }

    size_t get_number_of_arguments() const override { return 0; }

    bool is_variadic() const override { return true; }

    DataTypePtr get_return_type_impl(const DataTypes& arguments) const override {
        return std::make_shared<DataTypeFloat64>();
    }

    Status execute_impl(FunctionContext* context, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t input_rows_count) const override {
        DCHECK(arguments.empty() || arguments.size() == 2);
        auto* generator = get_generator(context);

        auto result_column = ColumnFloat64::create(input_rows_count);
        auto& result_data = result_column->get_data();
        if (arguments.empty()) {
            std::uniform_real_distribution<double> distribution(0.0, 1.0);
            for (size_t i = 0; i < input_rows_count; ++i) {
                result_data[i] = distribution(*generator);
            }
        } else {
            const auto [low_column, low_is_const] =
                    unpack_if_const(block.get_by_position(arguments[0]).column);
            const auto [high_column, high_is_const] =
                    unpack_if_const(block.get_by_position(arguments[1]).column);
            const auto& low_data = assert_cast<const ColumnFloat64&>(*low_column).get_data();
            const auto& high_data = assert_cast<const ColumnFloat64&>(*high_column).get_data();
            for (size_t i = 0; i < input_rows_count; ++i) {
                double low = low_data[index_check_const(i, low_is_const)];
                double high = high_data[index_check_const(i, high_is_const)];
                if (low >= high) {
                    return Status::InvalidArgument(
                            "DBMS_RANDOM.VALUE lower bound must be less than upper bound, "
                            "got [{}, {})",
                            low, high);
                }
                std::uniform_real_distribution<double> distribution(low, high);
                result_data[i] = distribution(*generator);
            }
        }

        block.replace_by_position(result, std::move(result_column));
        return Status::OK();
    }
};

class DbmsRandomInteger : public DbmsRandomFunction {
public:
    static constexpr auto name = "dbms_random_random";

    static FunctionPtr create() { return std::make_shared<DbmsRandomInteger>(); }

    String get_name() const override { return name; }

    size_t get_number_of_arguments() const override { return 0; }

    DataTypePtr get_return_type_impl(const DataTypes& arguments) const override {
        return std::make_shared<DataTypeInt32>();
    }

    Status execute_impl(FunctionContext* context, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t input_rows_count) const override {
        DCHECK(arguments.empty());
        auto* generator = get_generator(context);
        std::uniform_int_distribution<int32_t> distribution(
                std::numeric_limits<int32_t>::min(), std::numeric_limits<int32_t>::max());
        auto result_column = ColumnInt32::create(input_rows_count);
        auto& result_data = result_column->get_data();
        for (size_t i = 0; i < input_rows_count; ++i) {
            result_data[i] = distribution(*generator);
        }
        block.replace_by_position(result, std::move(result_column));
        return Status::OK();
    }
};

class DbmsRandomString : public DbmsRandomFunction {
public:
    static constexpr auto name = "dbms_random_string";
    static constexpr int32_t max_string_length = 32767;

    static FunctionPtr create() { return std::make_shared<DbmsRandomString>(); }

    String get_name() const override { return name; }

    size_t get_number_of_arguments() const override { return 2; }

    DataTypePtr get_return_type_impl(const DataTypes& arguments) const override {
        return std::make_shared<DataTypeString>();
    }

    Status execute_impl(FunctionContext* context, Block& block, const ColumnNumbers& arguments,
                        uint32_t result, size_t input_rows_count) const override {
        DCHECK_EQ(arguments.size(), 2);
        auto* generator = get_generator(context);
        const auto [option_column, option_is_const] =
                unpack_if_const(block.get_by_position(arguments[0]).column);
        const auto [length_column, length_is_const] =
                unpack_if_const(block.get_by_position(arguments[1]).column);
        const auto& options = assert_cast<const ColumnString&>(*option_column);
        const auto& lengths = assert_cast<const ColumnInt32&>(*length_column).get_data();
        auto result_column = ColumnString::create();

        for (size_t i = 0; i < input_rows_count; ++i) {
            int32_t length = lengths[index_check_const(i, length_is_const)];
            if (length < 0 || length > max_string_length) {
                return Status::InvalidArgument(
                        "DBMS_RANDOM.STRING length must be between 0 and {}, got {}",
                        max_string_length, length);
            }
            StringRef option = options.get_data_at(index_check_const(i, option_is_const));
            std::string_view characters = characters_for(option);
            std::uniform_int_distribution<size_t> distribution(0, characters.size() - 1);
            std::string value(length, '\0');
            for (char& character : value) {
                character = characters[distribution(*generator)];
            }
            result_column->insert_data(value.data(), value.size());
        }

        block.replace_by_position(result, std::move(result_column));
        return Status::OK();
    }

private:
    static std::string_view characters_for(StringRef option) {
        static constexpr std::string_view uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        static constexpr std::string_view lowercase = "abcdefghijklmnopqrstuvwxyz";
        static constexpr std::string_view mixed_case =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        static constexpr std::string_view alphanumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        static constexpr std::string_view printable =
                " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
                "abcdefghijklmnopqrstuvwxyz{|}~";

        char selector = option.size == 0 ? 'u' : option.data[0];
        switch (selector) {
        case 'l':
        case 'L':
            return lowercase;
        case 'a':
        case 'A':
            return mixed_case;
        case 'x':
        case 'X':
            return alphanumeric;
        case 'p':
        case 'P':
            return printable;
        case 'u':
        case 'U':
        default:
            return uppercase;
        }
    }
};

void register_function_dbms_random(SimpleFunctionFactory& factory) {
    factory.register_function<DbmsRandomInteger>();
    factory.register_function<DbmsRandomString>();
    factory.register_function<DbmsRandomValue>();
}
#include "common/compile_check_end.h"
} // namespace doris
