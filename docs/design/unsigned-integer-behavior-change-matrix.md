# Unsigned Integer Behavior Change Matrix

## 1. 文档目的

本文档记录无符号整数支持重构后的行为变化、MySQL 兼容范围、已知差异及三个 PR 的代码边界。

实现仍按以下三个提交阶段组织；提交在评审修改后会重写，因此文档不记录易失效的 commit hash：

| PR 阶段 | 主题 |
| --- | --- |
| PR 1 | 基础类型定义、FE 持久化及 FE/BE 协议 |
| PR 2 | 计算、结果回包及最终 sink 检查 |
| PR 3 | 显式 CAST 及相关处理 |

当前方案遵循以下约束：

1. 保留 `Type.typeDescriptor` 位掩码方案。
2. 不增加独立的 `UnsignedIntegerType` 类；Nereids 与 Catalog 一样使用 carrier type 加
   `typeDescriptor`，descriptor 不参与类型推导。
3. 不修改 BE 底层整数运算函数。
4. 不增加用于无符号溢出检查的表达式，不因检查改变正常执行计划结构。
5. 可在 FE 折叠的常量表达式立即检查无符号范围；运行时表达式仅在最终输出或写入 sink 检查。
6. `typeDescriptor` 由 FE 持久化，不写入 BE TabletMeta、TabletSchema 或 protobuf。
7. 不增加或依赖 strict mode、`NO_UNSIGNED_SUBTRACTION` 等 SQL mode。

## 2. 支持范围

### 2.1 逻辑类型与物理承载

| SQL 逻辑类型 | 逻辑范围 | Doris signed carrier | 十进制最大位数 | MySQL 范围 |
| --- | ---: | --- | ---: | --- |
| `TINYINT UNSIGNED` | `0 ~ 255` | `SMALLINT` | 3 | 一致 |
| `SMALLINT UNSIGNED` | `0 ~ 65535` | `INT` | 5 | 一致 |
| `INT UNSIGNED` | `0 ~ 4294967295` | `BIGINT` | 10 | 一致 |
| `BIGINT UNSIGNED` | `0 ~ 18446744073709551615` | `LARGEINT` | 20 | 一致 |

unsigned 是逻辑类型属性，底层仍使用 Doris 已有 signed primitive type 和列实现。这样可以完整承载对应 MySQL 无符号范围，而不需要修改 BE 底层整数列和四则运算模板。

### 2.2 `typeDescriptor` 编码

| 字段 | 值 | 含义 |
| --- | ---: | --- |
| `TYPE_DESCRIPTOR_DEFAULT` | `0` | 普通类型 |
| `TYPE_DESCRIPTOR_CODE_MASK` | `0xFFFF` | 预留的类型编码区域 |
| `TYPE_DESCRIPTOR_UNSIGNED_MASK` | `1 << 16` | unsigned 标记 |
| `TYPE_DESCRIPTOR_SUPPORTED_MASK` | `TYPE_DESCRIPTOR_UNSIGNED_MASK` | 当前支持的 descriptor 位 |

当前仅接受默认值或 unsigned 位，其他未知位由 FE 拒绝。BE 接收 descriptor 后按需识别，但不在表达式构造阶段使用 `CHECK` 强制终止进程。

## 3. 总体行为矩阵

兼容等级定义：

- **对齐**：用户可观察的核心值、范围或协议行为与 MySQL 一致。
- **部分对齐**：常见值行为一致，但类型元数据、错误时机或边界转换可能不同。
- **有意差异**：按照本次评审约束明确保留的 Doris 行为。
- **不在范围**：本次不提供支持。

| 场景 | 改造前 Doris | 当前 Doris | MySQL | 兼容等级 | 所属 PR |
| --- | --- | --- | --- | --- | --- |
| 解析 `TINYINT UNSIGNED` | 不具备完整逻辑类型支持 | 解析为 unsigned tinyint | 支持 | 对齐 | PR 1 |
| 解析 `SMALLINT UNSIGNED` | 不具备完整逻辑类型支持 | 解析为 unsigned smallint | 支持 | 对齐 | PR 1 |
| 解析 `INT/INTEGER UNSIGNED` | 不具备完整逻辑类型支持 | 解析为 unsigned int | 支持 | 对齐 | PR 1 |
| 解析 `BIGINT UNSIGNED` | 不具备完整逻辑类型支持 | 解析为 unsigned bigint | 支持 | 对齐 | PR 1 |
| `LARGEINT UNSIGNED` | 不支持 | 明确拒绝 | MySQL 无 `LARGEINT` | 对齐 Doris 范围定义 | PR 1 |
| `MEDIUMINT UNSIGNED` | 不支持 | 不支持 | 支持 | 不在范围 | - |
| `DECIMAL/FLOAT/DOUBLE UNSIGNED` | 不支持 | 不支持 | MySQL 支持但已弃用部分属性 | 不在范围 | - |
| `ZEROFILL` | 非本功能 | 未增加 | 支持但已弃用 | 不在范围 | - |
| `SHOW CREATE TABLE` | 无 unsigned 语义 | 显示原始逻辑类型及 `unsigned` | 显示 `UNSIGNED` | 对齐 | PR 1 |
| `DESC` 类型显示 | 仅显示 signed primitive | 显示 unsigned 逻辑类型 | 显示 unsigned 类型 | 对齐 | PR 1 |
| FE Journal/Image 持久化 | 无 unsigned descriptor | 持久化 `typeDescriptor` | MySQL 自有字典 | 满足 Doris 需求 | PR 1 |
| FE 到 BE 类型传输 | 无 unsigned descriptor | Thrift 携带 descriptor | 不适用 | 满足 Doris 需求 | PR 1 |
| BE TabletMeta 持久化 | 无 | 仍不持久化 descriptor | 不适用 | 有意设计 | - |
| MySQL 协议列类型 | 暴露 carrier 风险 | 暴露原始 MySQL 整数类型 | 原始整数类型 | 对齐 | PR 2 |
| MySQL 协议 unsigned flag | 无 | 设置 `UNSIGNED_FLAG` | 设置该 flag | 对齐 | PR 2 |
| 文本结果回包 | 不能完整表达 unsigned 语义 | 按无符号逻辑值返回 | 返回无符号值 | 对齐 | PR 2 |
| 二进制结果回包 | 按 signed carrier 编码 | 按原始 MySQL 宽度和 unsigned bit pattern 编码 | 对应 unsigned 整数编码 | 对齐 | PR 2 |
| Arrow Flight 最终输出 | 无 unsigned 范围检查 | 输出前统一检查 | 不适用 | 满足当前设计 | PR 2 |
| 表写入最终检查 | 无 unsigned 范围 | 检查 `0 ~ 2^N-1` | 检查列范围 | 部分对齐 | PR 2 |
| 非严格模式越界写入 | Doris 原有行为 | 仍报错/过滤，不 clamp | MySQL 可截断并 warning | 有意差异 | PR 2 |
| 中间表达式溢出检查 | 无 unsigned 语义 | 不检查 | MySQL 可在运算点报错 | 有意差异 | PR 2 |
| 最终查询结果溢出 | 无 unsigned 语义 | MySQL/Arrow sink 返回错误 | MySQL 返回错误 | 对齐最终结果 | PR 2 |
| `CAST(... AS UNSIGNED)` | 目标为现有兼容类型 | 目标为 `BIGINT UNSIGNED` | unsigned 64-bit integer | 对齐 | PR 3 |
| `CAST(-1 AS UNSIGNED)` | 不能稳定得到 unsigned max | 得到 `2^64-1` | 得到 `2^64-1` | 对齐 | PR 3 |
| 隐式负数转 unsigned | 无明确语义 | 不执行显式 CAST 的模转换 | 依场景和 SQL mode 而异 | 部分对齐 | PR 3 |
| `TRY_CAST` | Doris 原有语义 | 保留原有失败转 NULL 机制并处理显式 unsigned | MySQL 无完全等价接口 | Doris 特有 | PR 3 |

## 4. DDL、元数据和协议行为

### 4.1 建表与展示

```sql
CREATE TABLE t (
    u8  TINYINT UNSIGNED,
    u16 SMALLINT UNSIGNED,
    u32 INT UNSIGNED,
    u64 BIGINT UNSIGNED
);
```

用户可观察到的 schema 保留逻辑类型：

```text
tinyint unsigned
smallint unsigned
int unsigned
bigint unsigned
```

内部 primitive type 则分别为 `SMALLINT`、`INT`、`BIGINT` 和 `LARGEINT`。

### 4.2 FE 持久化边界

FE 的 `Type` JSON 持久化包含 `typeDescriptor`。类型从 FE Journal/Image 恢复后，可以根据 carrier 和 descriptor 还原 unsigned 逻辑类型。

BE 不把 descriptor 写入 TabletMeta 或 TabletSchema。执行时所需 descriptor 由 FE 通过 Thrift descriptor 下发。这避免扩大 BE 存储元数据改动范围。

### 4.3 FE/BE Thrift

| Thrift 结构 | 新字段 | 用途 |
| --- | --- | --- |
| `TScalarType` | `optional i64 type_descriptor` | 表达式、slot 等运行时类型 |
| `TColumnType` | `optional i64 type_descriptor` | FE 类型传递 |
| `TExprNode` | `optional bool is_explicit_cast` | 区分用户显式 CAST 与 planner 隐式转换 |

字段均为 optional，默认 descriptor 为 `0`，以保持旧节点和普通 signed 类型兼容。

## 5. 算术与比较矩阵

### 5.1 类型规整

| 操作 | 涉及 unsigned 时的当前处理 | 原因 |
| --- | --- | --- |
| `+`、`-`、`*` | 清除 descriptor 后复用原 common type 和 promotion；补齐 `BIGINT` carrier 到 `LARGEINT` 的 unsigned 晋升，再恢复 descriptor | U32 算术可以用 U64 承载，descriptor 不改变 signed 操作数的数值语义 |
| `MOD` | 复用原推导，结果 descriptor 只由左操作数决定 | 避免仅因除数 unsigned 而改变余数符号 |
| `DIV` | 清除 descriptor 后复用原整数整除推导 | 不把 signed 参数提前转换为 unsigned |
| `/` | unsigned 整数按自身十进制位数转 Decimal；原 Decimal scale 和 Double 类别保持不变 | 精确承载 U64，同时不破坏混合数值类型 |
| 整数比较、`IN` | 使用去除 descriptor 的 carrier 查找公共类型 | 精确比较且结果仍为 Boolean |
| `CASE WHEN` | 本阶段不修改 | 沿用 Doris 原有行为，不承诺 mixed signed/unsigned 结果 descriptor |

unsigned 子节点扩宽时只保留自身 descriptor；signed 子节点始终 Cast 到普通 signed carrier。例如：

| 表达式 | 是否增加 Cast | 说明 |
| --- | --- | --- |
| `BIGINT UNSIGNED + BIGINT UNSIGNED` | 否 | 两侧已经是 `LARGEINT` carrier |
| `INT UNSIGNED + INT UNSIGNED` | 是 | `BIGINT` carrier 补齐晋升到 `LARGEINT`，结果为 `BIGINT UNSIGNED` |
| `INT UNSIGNED + (-1)` | 是 | unsigned 一侧扩到 `LARGEINT`，signed `-1` 也只扩 carrier，不附加 unsigned descriptor |
| `INT UNSIGNED + DECIMAL/DOUBLE` | 按原规则 | 结果仍为 Decimal/Double，不附加 unsigned descriptor |
| `BIGINT UNSIGNED = LARGEINT` | 否 | 两侧 carrier 已相同，descriptor 不参与比较类型推导 |

这些 Cast 是完成物理类型规整所必需的普通类型转换，不是溢出检查表达式。

### 5.2 合法最终结果

| SQL 示例 | 当前结果 | MySQL 结果 | 结论 |
| --- | ---: | ---: | --- |
| `CAST(1 AS UNSIGNED) + 2` | `3` | `3` | 对齐 |
| `CAST(3 AS UNSIGNED) - 2` | `1` | `1` | 对齐 |
| `CAST(3 AS UNSIGNED) * 2` | `6` | `6` | 对齐 |
| `CAST(5 AS UNSIGNED) DIV 2` | `2` | `2` | 常见值对齐 |
| `CAST(5 AS UNSIGNED) MOD 2` | `1` | `1` | 常见值对齐 |
| `CAST(5 AS UNSIGNED) / 2` | 精确 Decimal 结果 | 精确数值结果 | 数值对齐，metadata 可能不同 |

### 5.3 最终结果越界

```sql
SELECT CAST(1 AS UNSIGNED) - 2;
SELECT CAST(18446744073709551615 AS UNSIGNED) + 1;
```

两个示例都可在 FE 完成常量折叠，因此 Doris 在分析阶段检查折叠结果并立即报错，不生成执行计划。含列或其他运行时值的同类表达式仍在最终 result/table sink 检查。最终可观察结果与 MySQL 默认 unsigned 语义一致，但错误阶段可能不同。

### 5.4 中间结果越界但最终恢复

纯常量表达式和运行时表达式的行为不同：

| SQL | 当前 Doris 行为 | 原因 |
| --- | --- | --- |
| `SELECT (CAST(U64_MAX AS UNSIGNED) + 1) - 1` | FE 分析阶段报 unsigned overflow | 常量折叠采用 bottom-up 顺序；内层 `U64_MAX + 1` 先折叠并立即检查，外层 `- 1` 没有机会恢复范围 |
| `SELECT (u64 + 1) - 1 FROM t WHERE u64 = U64_MAX` | 成功返回 `U64_MAX` | 含列的表达式不能在 FE 折叠；BE 使用 `LARGEINT` 保存中间值，只在最终 sink 检查最终结果 |

因此，“只检查最终结果”仅描述运行时计算路径，不适用于能够在 FE 常量折叠阶段确定越界的子表达式。这是当前有意保留、需要与团队确认的语义边界。

```sql
SELECT (u64 + 1) - 1 FROM t WHERE u64 = 18446744073709551615;
```

| 系统 | 行为 |
| --- | --- |
| 当前 Doris | 运行时中间值由 `LARGEINT` 承载，最终值合法，因此成功返回 `18446744073709551615` |
| MySQL | 可能在内部加法超过 unsigned 64-bit 范围时立即报错 |

这是本方案最重要的有意差异。纯常量写法会在 FE 折叠子表达式时直接报错；上述差异仅适用于不能在 FE 确定结果的运行时表达式。若要求运行时也完全复制 MySQL 的表达式级错误时机，需要增加逐层检查表达式或修改 BE 底层运算，与当前评审约束冲突。

### 5.5 signed/unsigned 混合边界

合法范围内的整数比较通过双方 carrier 的原公共类型实现精确比较。以下复杂边界不承诺与 MySQL 完全一致：

- unsigned 与负 signed 的 `DIV`；
- unsigned 与负 signed 的 `MOD`；
- 带负 signed 操作数的最终 unsigned 结果范围；
- integer 与 string 混合；
- 超长数字字符串及带非数字后缀的字符串；
- warning、NULL、截断与错误之间的选择。

这些场景继续使用 Doris 原有的类型转换与错误处理规则。

## 6. 聚合函数

本阶段不修改 `SUM`、`SUM0` 和 `AVG` 的类型推导、聚合状态或 BE 实现。unsigned 输入的聚合行为暂不作为本需求的兼容性承诺，后续单独设计和评审。

## 7. CAST 行为矩阵

| 表达式 | 当前 Doris | MySQL | 兼容等级 |
| --- | --- | --- | --- |
| `CAST(1 AS UNSIGNED)` | `BIGINT UNSIGNED` 值 `1` | unsigned 64-bit 值 `1` | 对齐 |
| `CAST(-1 AS UNSIGNED)` | `18446744073709551615` | `18446744073709551615` | 对齐 |
| `CAST(CAST(-1 AS UNSIGNED) AS SIGNED)` | `-1` | `-1` | 对齐 |
| `CAST(255 AS TINYINT UNSIGNED)` | `255` | `255` | 对齐 |
| `CAST(65535 AS SMALLINT UNSIGNED)` | `65535` | `65535` | 对齐 |
| `CAST(4294967295 AS INT UNSIGNED)` | `4294967295` | `4294967295` | 对齐 |
| `CAST(18446744073709551615 AS BIGINT UNSIGNED)` | 最大值 | 最大值 | 对齐 |
| 负数显式转窄 unsigned | 最终范围规则与现有 CAST/sink 结合 | MySQL 有各类型转换规则 | 需要 E2E 确认 |
| 超过 `2^64-1` 的值转 unsigned | 原有 CAST 加最终检查 | 可能伴随 warning/截断 | 部分对齐 |
| 非数字字符串转 unsigned | Doris 原有 CAST 规则 | 受 SQL mode 和 warning 规则影响 | 不承诺 |

只有用户显式编写的 CAST 才通过 `is_explicit_cast` 触发负数按 `2^64` 归一化。Planner 为物理 carrier 插入的隐式 Cast 不触发该语义，防止普通类型规整意外改变数值。

## 8. 写入与输出检查矩阵

| Sink | 是否检查 unsigned 范围 | 处理方式 |
| --- | --- | --- |
| MySQL result writer | 是 | 返回查询错误 |
| Arrow Flight result writer | 是 | 返回查询错误 |
| OLAP table sink | 是 | 标记无效行并按现有导入错误机制处理 |
| 中间 Block/Exchange | 否 | 允许宽 carrier 保存中间结果 |
| BE 底层算术函数 | 否 | 保持原逻辑 |
| BE TabletMeta 持久化 | 不适用 | 不保存 descriptor |

统一范围：

| Carrier | unsigned 上限 |
| --- | ---: |
| `SMALLINT` | `255` |
| `INT` | `65535` |
| `BIGINT` | `4294967295` |
| `LARGEINT` | `18446744073709551615` |

NULL 值跳过范围检查。Const 和 Nullable 列在检查前展开到实际数据列。

## 9. SQL mode 差异

本次没有增加 SQL mode，也不依赖 strict mode。

| MySQL 能力 | 当前 Doris |
| --- | --- |
| strict 模式下越界写入报错 | 越界写入按 Doris 现有导入错误机制处理 |
| 非 strict 模式下可 clamp 并 warning | 不新增 clamp 行为 |
| `NO_UNSIGNED_SUBTRACTION` 可使 unsigned subtraction 返回 signed | 不支持，仍采用 unsigned 结果语义 |
| 不同模式影响字符串转换 warning/error | 保持 Doris 原有 CAST 行为 |

例如：

```sql
SELECT CAST(1 AS UNSIGNED) - 2;
```

当前 Doris 不提供通过 SQL mode 返回 `-1` 的分支，最终结果按 unsigned 范围检查并报错。

## 10. 三个 PR 的边界

### 10.1 PR 1：基础定义、持久化和协议

包含：

- `Type.typeDescriptor` 位掩码；
- `IntegralType.typeDescriptor` 标记；
- 各整数 carrier 内部私有 descriptor singleton；
- Parser 将 SQL unsigned 宽度映射到 carrier 加 descriptor；
- `UNSIGNED` 语法解析；
- `toSql`、`SHOW CREATE TABLE` 和列类型展示；
- FE JSON 持久化；
- `TScalarType`、`TColumnType` descriptor；
- BE runtime slot 接收 descriptor。

不包含：

- 算术行为；
- 结果范围检查；
- MySQL/Arrow unsigned 回包；
- 显式 CAST 的模转换。

该 PR 的独立验收目标是：可以建表，schema 可恢复，查询元数据能显示 unsigned。

### 10.2 PR 2：计算、回包和最终检查

包含：

- descriptor 与 common type/promotion 正交的算术类型规整；
- signed/unsigned、Decimal/Double、IN 和 MOD 左操作数规则；
- signed/unsigned 精确比较；
- `/` 使用 Decimal；
- MySQL metadata `UNSIGNED_FLAG`；
- MySQL 二进制无符号编码；
- MySQL/Arrow 最终结果检查；
- OLAP table sink 范围检查。

不包含：

- BE 底层 `plus/minus/multiply/div/mod` 改造；
- 新的 overflow-check expression；
- 中间结果逐层检查；
- strict mode 或 `NO_UNSIGNED_SUBTRACTION`。

### 10.3 PR 3：CAST 和其他相关

包含：

- `CAST(... AS UNSIGNED)` 解析为 `BIGINT UNSIGNED`；
- `TExprNode.is_explicit_cast`；
- 显式负整数转 unsigned bigint 时按模 `2^64` 归一化；
- `SimplifyCastRule` 保留显式 unsigned CAST 的语义。

不包含：

- 为隐式 carrier Cast 应用模转换；
- 完整复制 MySQL 字符串/浮点/Decimal 转 unsigned 的 warning 和 SQL mode 行为。

## 11. 已删除或明确不采用的旧方案

| 旧方案内容 | 当前处理 |
| --- | --- |
| 独立 `UnsignedIntegerType` 类 | 删除，unsigned 标记进入 `IntegralType` |
| 修改 BE 底层整数运算逻辑 | 不采用 |
| 每层表达式插入溢出检查 | 不采用 |
| 为溢出检查改变正常执行计划 | 不采用 |
| BE TabletMeta/TabletSchema/protobuf 持久化 descriptor | 不采用 |
| 新增 unsigned strict SQL mode | 删除 |
| `NO_UNSIGNED_SUBTRACTION` 类模式 | 不支持 |
| descriptor/carrier 不匹配时使用 BE `CHECK` | 删除，避免进程级终止 |

## 12. 测试映射

### 12.1 FE 单元测试

| 测试类 | 覆盖内容 |
| --- | --- |
| `UnsignedIntegerParserTest` | 四种 unsigned 类型解析、非法类型拒绝、CAST target |
| `DataTypeTest` | unsigned Nereids/catalog 类型转换 |
| `ScalarTypeTest` | FE 类型持久化 |
| `MysqlColDefTest` | MySQL 列类型及 `UNSIGNED_FLAG` |
| `FoldConstantTest` | unsigned 常量计算的值、结果类型以及常量上下溢出 |
| `TypeCoercionUtilsTest` | carrier/descriptor 正交推导、混合数值、IN、MOD 和比较 |

### 12.2 SQL 行为测试

SQL 文件：

```text
regression-test/suites/datatype_p0/scalar_types/sql/
    test_unsigned_integer_mysql_compatibility.sql
```

覆盖：

- 建表、`DESC`、`SHOW CREATE TABLE`；
- 四种类型的最小值、最大值；
- 低于 0 和超过上限的写入；
- 文本查询结果；
- `CAST AS UNSIGNED` 和 `CAST(-1 AS UNSIGNED)`；
- typed unsigned CAST；
- signed/unsigned 精确比较；
- `+`、`-`、`*`、`/`、`DIV`、`MOD`；
- 最终结果溢出；
- 中间结果越界但最终恢复；
- 无 SQL mode 的写入行为；
- `NO_UNSIGNED_SUBTRACTION` 差异；
- 不在范围内的类型和转换说明。

文件包含预期失败语句，需要使用能在错误后继续执行的客户端：

```bash
mysql --force -uroot -h127.0.0.1 -P9030 -D regression_test \
  < regression-test/suites/datatype_p0/scalar_types/sql/test_unsigned_integer_mysql_compatibility.sql
```

## 13. 对外兼容性承诺

可以明确承诺：

1. 支持 `TINYINT`、`SMALLINT`、`INT/INTEGER`、`BIGINT` 的 `UNSIGNED` 定义。
2. 支持对应 MySQL 范围 `0 ~ 2^N-1`。
3. `SHOW CREATE TABLE`、查询列 metadata 和 MySQL 协议保留 unsigned 语义。
4. 合法范围内的整数计算和整数比较返回精确值。
5. 常量折叠在 FE 执行 unsigned 范围检查，运行时结果在最终查询输出和表写入检查。
6. `CAST(-1 AS UNSIGNED)` 返回 `18446744073709551615`。

必须注明的兼容差异：

1. Doris 使用更宽的 signed carrier 保存运行时中间结果，并在最终 sink 检查 unsigned 范围；可折叠常量在 FE 提前检查。
2. 运行时中间结果暂时超过 unsigned 范围、最终恢复合法时，Doris 可以成功，而 MySQL 可能提前报错。
3. Doris 不实现 MySQL 非严格模式下的越界 clamp 和 warning。
4. Doris 不支持 `NO_UNSIGNED_SUBTRACTION`。
5. `CASE WHEN`、`SUM/AVG` 暂不在本阶段兼容性承诺范围内。
6. `/`、混合 signed/unsigned 的 `DIV/MOD`、字符串、浮点和 oversized Decimal 转换边界不承诺完全一致。
7. `MEDIUMINT UNSIGNED`、非整数 `UNSIGNED`、`ZEROFILL` 不在本次范围。

## 14. 当前验证状态

截至本文档生成时：

- FE Checkstyle 已通过；
- 已增加针对类型、解析、常量折叠、协议和类型规整的单元测试；
- 已生成独立 SQL 行为测试；
- SQL 文件尚未执行；
- 完整 FE/BE 编译尚未执行，由最终验证阶段完成。
