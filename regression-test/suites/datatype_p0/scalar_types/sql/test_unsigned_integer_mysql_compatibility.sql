-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

-- Standalone compatibility checks for unsigned integer support.
--
-- Run with a client that continues after an expected error, for example:
--   mysql --force -uroot -h127.0.0.1 -P9030 -D regression_test \
--       < test_unsigned_integer_mysql_compatibility.sql
--
-- This file deliberately does not set or depend on sql_mode.
-- "MYSQL COMPATIBLE" marks behavior that this implementation promises.
-- "DORIS DIFFERENCE" records an intentional compatibility boundary.
-- Statements marked "EXPECT ERROR" are negative test cases.

DROP TABLE IF EXISTS unsigned_integer_mysql_compatibility;

CREATE TABLE unsigned_integer_mysql_compatibility (
    id INT NOT NULL,
    u8 TINYINT UNSIGNED,
    u16 SMALLINT UNSIGNED,
    u32 INT UNSIGNED,
    u64 BIGINT UNSIGNED
)
DUPLICATE KEY(id)
DISTRIBUTED BY HASH(id) BUCKETS 1
PROPERTIES ("replication_num" = "1");

-- MYSQL COMPATIBLE: all four unsigned integer declarations are retained by
-- table metadata and SHOW CREATE TABLE.
SHOW CREATE TABLE unsigned_integer_mysql_compatibility;
DESC unsigned_integer_mysql_compatibility;

-- MYSQL COMPATIBLE: minimum and maximum values of every supported unsigned
-- integer type can be stored and returned without loss.
INSERT INTO unsigned_integer_mysql_compatibility VALUES
    (1, 0, 0, 0, 0),
    (2, 255, 65535, 4294967295, 18446744073709551615);

SELECT id, u8, u16, u32, u64
FROM unsigned_integer_mysql_compatibility
ORDER BY id;

-- MYSQL COMPATIBLE: the four logical ranges are checked at the table sink.
-- EXPECT ERROR: below the lower bound.
INSERT INTO unsigned_integer_mysql_compatibility VALUES
    (10, -1, 0, 0, 0);

-- EXPECT ERROR: above each upper bound.
INSERT INTO unsigned_integer_mysql_compatibility VALUES
    (11, 256, 0, 0, 0);
INSERT INTO unsigned_integer_mysql_compatibility VALUES
    (12, 0, 65536, 0, 0);
INSERT INTO unsigned_integer_mysql_compatibility VALUES
    (13, 0, 0, 4294967296, 0);
INSERT INTO unsigned_integer_mysql_compatibility VALUES
    (14, 0, 0, 0, 18446744073709551616);

-- Verify that failed writes did not add an invalid row.
SELECT COUNT(*) AS valid_row_count
FROM unsigned_integer_mysql_compatibility;

-- MYSQL COMPATIBLE: explicit AS UNSIGNED means an unsigned 64-bit integer.
-- A negative integer is converted modulo 2^64.
SELECT CAST(0 AS UNSIGNED) AS unsigned_zero,
       CAST(-1 AS UNSIGNED) AS unsigned_max;
SELECT CAST(CAST(-1 AS UNSIGNED) AS SIGNED) AS signed_minus_one;

-- MYSQL COMPATIBLE: typed casts use the declared unsigned integer range.
SELECT CAST(255 AS TINYINT UNSIGNED) AS u8,
       CAST(65535 AS SMALLINT UNSIGNED) AS u16,
       CAST(4294967295 AS INT UNSIGNED) AS u32,
       CAST(18446744073709551615 AS BIGINT UNSIGNED) AS u64;

-- MYSQL COMPATIBLE: comparisons are exact across signed and unsigned integer
-- carriers, including values above the signed BIGINT maximum.
SELECT u64 > CAST(9223372036854775807 AS LARGEINT) AS above_signed_bigint,
       u64 = CAST(18446744073709551615 AS LARGEINT) AS equals_unsigned_max
FROM unsigned_integer_mysql_compatibility
WHERE id = 2;

-- MYSQL COMPATIBLE for values whose final result is in the unsigned range.
SELECT u32 + 1 AS add_result,
       u32 - 1 AS subtract_result,
       u32 * 2 AS multiply_result,
       u32 DIV 2 AS div_result,
       u32 MOD 10 AS mod_result,
       u32 / 2 AS decimal_divide_result
FROM unsigned_integer_mysql_compatibility
WHERE id = 2;

-- Unsigned is a descriptor on the integer carrier, not a replacement for the
-- existing numeric coercion rules. Decimal and Double operands keep their
-- original categories and signed negative operands keep their values.
SELECT u32 + CAST(1.25 AS DECIMAL(10, 2)) AS unsigned_plus_decimal,
       u32 + CAST(1.25 AS DOUBLE) AS unsigned_plus_double,
       u32 + (-1) AS unsigned_plus_negative_signed,
       u32 / CAST(1.25 AS DECIMAL(10, 2)) AS unsigned_divide_decimal,
       u32 / CAST(2 AS DOUBLE) AS unsigned_divide_double
FROM unsigned_integer_mysql_compatibility
WHERE id = 2;

-- IN finds its common comparison carrier without changing a signed value into
-- an unsigned value during coercion.
SELECT u32 IN (-1, 0, 4294967295) AS unsigned_in_result
FROM unsigned_integer_mysql_compatibility
ORDER BY id;

-- MOD follows the dividend (left operand) for its unsigned result descriptor.
-- The first expression must remain signed and return -1 instead of failing an
-- unsigned final-range check merely because the divisor is unsigned.
SELECT CAST(-5 AS BIGINT) MOD CAST(2 AS INT UNSIGNED) AS signed_mod_unsigned,
       CAST(5 AS INT UNSIGNED) MOD -2 AS unsigned_mod_signed;

-- MYSQL COMPATIBLE: an unsigned expression whose final value is negative or
-- greater than 2^64-1 is rejected before it is returned.
-- EXPECT ERROR: negative final unsigned result.
SELECT CAST(1 AS UNSIGNED) - 2;

-- EXPECT ERROR: final result exceeds BIGINT UNSIGNED.
SELECT CAST(18446744073709551615 AS UNSIGNED) + 1;

-- DORIS DIFFERENCE / EXPECT ERROR: constant folding is bottom-up, so FE rejects
-- the overflowing inner constant expression before the outer subtraction can
-- restore it to the unsigned range.
SELECT (CAST(18446744073709551615 AS UNSIGNED) + 1) - 1;

-- DORIS DIFFERENCE: a runtime expression is checked only at the final sink.
-- LARGEINT can carry the temporary value, so the legal final value is returned.
SELECT (u64 + 1) - 1 AS recovered_after_runtime_intermediate_overflow
FROM unsigned_integer_mysql_compatibility
WHERE id = 2;

-- DORIS DIFFERENCE / EXPECT ERROR: out-of-range table writes are rejected
-- independently of sql_mode. Doris does not implement MySQL non-strict
-- clamping-to-boundary plus warning behavior for unsigned columns.
INSERT INTO unsigned_integer_mysql_compatibility VALUES
    (20, 300, 0, 0, 0);

-- DORIS DIFFERENCE: NO_UNSIGNED_SUBTRACTION is intentionally unsupported.
-- The subtraction remains unsigned and fails when its final value is negative.
-- EXPECT ERROR.
SELECT CAST(1 AS UNSIGNED) - 2 AS no_unsigned_subtraction_is_not_supported;

-- Compatibility exclusions (documentation-only because these are parser
-- errors and are not part of the four supported unsigned integer types):
--   MEDIUMINT UNSIGNED
--   LARGEINT UNSIGNED
--   DECIMAL UNSIGNED
--   FLOAT UNSIGNED
--   DOUBLE UNSIGNED
--   ZEROFILL
--
-- String, floating-point, oversized DECIMAL, DIV/MOD mixed-sign edge cases,
-- and their warning/NULL behavior continue to use Doris's existing coercion
-- rules. They are not claimed to be fully MySQL compatible by this change.

DROP TABLE IF EXISTS unsigned_integer_aggregate_compatibility;
DROP TABLE IF EXISTS unsigned_integer_mysql_compatibility;
