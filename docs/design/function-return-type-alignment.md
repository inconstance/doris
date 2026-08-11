# MySQL / Oracle 函数类型与回包对齐参考

本文面向 Doris 函数 signature、结果列元数据和协议回包对齐。结论基于 MySQL 8.4 源码及官方文档、Oracle Database 21c SQL Language Reference。除非特别说明，`NULL` 输入均按对应数据库的原生行为传播。

## 约定

- `DECIMAL(p,s)`：精度为 `p`，小数位为 `s`；`I = p-s` 为整数位数。
- `L(x)`：字符串表达式 `x` 的最大字符数；`B(x)`：最大字节数。
- MySQL 协议中的 `BIGINT` 对应 `MYSQL_TYPE_LONGLONG`；`DOUBLE` 对应 `MYSQL_TYPE_DOUBLE`；`DECIMAL` 对应 `MYSQL_TYPE_NEWDECIMAL`。
- 本文的 Oracle 模式假定 Oracle `NUMBER` 映射为 Doris `DECIMAL`。按当前范围不单独对齐 `BINARY_FLOAT`；Doris `FLOAT` 统一提升到 `DOUBLE`。
- “不存在”表示 Oracle SQL 没有该同名内置函数，不能把兼容改写描述成 Oracle 原生行为。

## 一页汇总

| 函数 | MySQL 入参及返回值 | Oracle 入参及返回值 | 主要不兼容点 |
|---|---|---|---|
| `ABS(x)` | 整数→`BIGINT`；浮点/字符串→`DOUBLE`；decimal→原 `DECIMAL(p,s)` | 数值/可转数值；返回与参数相同数值类型 | Oracle `NUMBER` 应保持 decimal；MySQL 整数必须提升到 BIGINT |
| `CEIL(x)` / `CEIL(datetime[,fmt])` | 数值：整数→`BIGINT`；浮点/字符串→`DOUBLE`；decimal 按下表推导，部分结果转 `BIGINT` | 数值：返回与参数相同数值类型；23c+ 还支持 datetime/interval 重载，datetime 结果恒为 `DATE` | decimal 返回类别不同；Oracle datetime 需要独立 signature 和执行实现 |
| `ROUND(x[,d])` | 整数→`BIGINT`；浮点/字符串→`DOUBLE`；decimal 按下表 | `ROUND(x)` 与 `x` 同类型；指定 `d` 时返回 `NUMBER` | 返回类型与舍入算法均可能不同 |
| `TRUNCATE(x,d)` / `TRUNC(x[,d])` | MySQL 名称为 `TRUNCATE`，必须 2 参数；类型规则同 `ROUND`，但不增加进位位 | Oracle 名称为 `TRUNC`；1 或 2 参数；无浮点参数时返回 `NUMBER` 规则见下 | 名称、参数个数及返回类型不同 |
| `POW(x,y)` / `POWER(x,y)` | 二者同义；参数按 `DOUBLE` 求值并返回 `DOUBLE` | 仅 `POWER`；任一 binary float→`BINARY_DOUBLE`，否则→`NUMBER` | 当前兼容策略统一返回 `DOUBLE`；暂不将无固定 `(p,s)` 的 Oracle NUMBER 映射为 DECIMAL |
| `SQRT(x)` | 参数按 `DOUBLE` 求值，返回 `DOUBLE` | 返回与数值参数相同类型 | Oracle NUMBER 应保持 decimal |
| `SIGN(x)` | 参数按 `DOUBLE` 求值，返回 `BIGINT`（值为 -1/0/1） | 返回 `NUMBER` | 回包类型不同 |
| `CONCAT(a,...)` | 至少 1 个字符串参数；返回聚合 charset/collation 的字符串 | 恰好 2 参数；支持字符及 LOB，返回类型按 Oracle 类型规则 | 参数个数、NULL/空串、LOB 类型不同 |
| `CONCAT_WS(sep,a,...)` | 至少 2 参数；字符串 | 不存在 | Oracle 模式应报未知函数，除非另设兼容扩展 |
| `ELT(n,a,...)` | `n` 为 BIGINT 语义；字符串 | 不存在 | 同上 |
| `MAKE_SET(bits,a,...)` | `bits` 为 64 位位图；字符串 | 不存在 | 同上 |
| `SUBSTRING` / `SUBSTR` | 同义；2 或 3 参数；返回基于首参的字符串 | 只有 `SUBSTR`；2 或 3 参数；返回首参类型（CHAR/NCHAR 除外） | position=0 语义相反；Oracle 无 `SUBSTRING` |
| `LEFT(s,n)` | 字符串，最多 `min(L(s),n)` 个字符 | 不存在 | 可改写不等于原生函数 |
| `LENGTH(s)` | 返回 `BIGINT`，值为字节数 | 返回 `NUMBER`，值为字符数 | 含义和类型都不同 |
| `CHAR_LENGTH` / `CHARACTER_LENGTH` | 同义；返回 `BIGINT`，值为字符数 | Oracle Database SQL 无这两个原生函数 | 不应与 Oracle `LENGTH` 混称原生别名 |
| `UPPER` / `UCASE` | 同义；字符串 | 仅 `UPPER`，返回与参数相同字符类型 | Oracle 无 `UCASE`；LOB/定长类型规则不同 |
| `LOWER` / `LCASE` | 同义；字符串 | 仅 `LOWER`，返回与参数相同字符类型 | Oracle 无 `LCASE`；LOB/定长类型规则不同 |

## 数值函数

### ABS

| 模式 | 第一个参数 | 返回类型/精度 |
|---|---|---|
| MySQL | 任意整数 | `BIGINT`，保留 unsigned 标志；`ABS(BIGINT_MIN)` 报整数溢出 |
| MySQL | `FLOAT` / `DOUBLE` / 可转数值字符串 | `DOUBLE` |
| MySQL | `DECIMAL(p,s)` | `DECIMAL(p,s)` |
| Oracle | `NUMBER(p,s)` | 同参数，即 `NUMBER(p,s)`；映射为 Doris `DECIMAL(p,s)` |
| Oracle | 其他数值类型 | 与参数相同数值类型 |

依据：MySQL 的 `Item_func_num1`/`Item_func_abs::resolve_type` 选择整数、double、decimal 三条路径，并显式检查最小 `longlong` 溢出；Oracle 明确规定返回与参数相同的数值类型。[MySQL 源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_func.cc)；[Oracle ABS](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/ABS.html)

### CEIL

| 模式 | 参数 | 返回类型/算法 |
|---|---|---|
| MySQL | 整数 | `BIGINT` |
| MySQL | `FLOAT` / `DOUBLE` / 可转数值字符串 | `DOUBLE` |
| MySQL | `DECIMAL(p,s)` | 先令 `q=p-s+(s!=0 ? 1 : 0)`，构造 `DECIMAL(q,0)`；若该类型的 `max_length<20`，再改成 `BIGINT`，否则保持 `DECIMAL(q,0)` |
| Oracle | `NUMBER(p,s)` | 返回同一数值数据类型；映射侧保持 decimal，而不是无条件改成 BIGINT |

注意：`DECIMAL(q,0)` 的有符号 `max_length` 通常是 `q+1`，因此常见边界表现为 `q<=18` 才转 BIGINT；严谨实现仍应复用源码的 `max_length<20` 判定，避免遗漏 signedness/display-length 规则。[MySQL 数值函数源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_func.cc#L3064-L3100)；[Oracle CEIL](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/CEIL.html)

Oracle 23c 及之后还定义了 `CEIL(datetime [, fmt])`。省略 `fmt` 时按 `DD`（天）处理；即使输入为 `TIMESTAMP`，返回类型也始终为 Oracle `DATE`。按 Doris 现有 JDBC 映射，Oracle `DATE` 对应 `DATETIMEV2(0)`，因为 Oracle DATE 包含到秒，而 Doris `DATEV2` 不包含时间字段。

| Oracle datetime 调用 | 返回类型 | 舍入单位 |
|---|---|---|
| `CEIL(datetime)` | `DATE` → Doris `DATETIMEV2(0)` | `DD` |
| `CEIL(datetime,'YYYY'/'YEAR'/...)` | `DATE` → Doris `DATETIMEV2(0)` | 年 |
| `CEIL(datetime,'Q')` | `DATE` → Doris `DATETIMEV2(0)` | 季度 |
| `CEIL(datetime,'MM'/'MON'/'MONTH'/'RM')` | `DATE` → Doris `DATETIMEV2(0)` | 月 |
| `CEIL(datetime,'DDD'/'DD'/'J')` | `DATE` → Doris `DATETIMEV2(0)` | 日 |
| `CEIL(datetime,'HH'/'HH12'/'HH24')` | `DATE` → Doris `DATETIMEV2(0)` | 小时 |
| `CEIL(datetime,'MI')` | `DATE` → Doris `DATETIMEV2(0)` | 分钟 |
| 周、ISO 年、世纪格式 | `DATE` → Doris `DATETIMEV2(0)` | 依赖各自日历规则；`D/DAY/DY` 还依赖 `NLS_TERRITORY`，不能直接等同 Doris `week_ceil` |

当前实现只绑定能够严格复用现有 Doris 日历语义的年、季度、月、日、小时和分钟格式。周、ISO 年、世纪格式在补齐对应日历/NLS 上下文前明确报错，避免静默产生错误结果。[Oracle CEIL datetime](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/ceil-datetime.html)

### FLOOR

MySQL `FLOOR` 与 `CEIL` 共用整数值函数的返回类型推导。设 DECIMAL 输入为
`DECIMAL(p,s)`，`q=p-s+(s>0 ? 1 : 0)`：

| 模式和参数 | 返回类型 |
|---|---|
| MySQL 任意整数 | `BIGINT`，复制第一个参数的 unsigned 属性 |
| MySQL FLOAT、DOUBLE、非数值 | `DOUBLE` |
| MySQL DECIMAL，`DECIMAL(q,0).max_length < 20` | `BIGINT`；有符号类型对应 `q<=18` |
| MySQL DECIMAL，其他情况 | `DECIMAL(q,0)` |
| Oracle NUMBER | 与参数相同的数值类型；按当前映射保持输入 DECIMAL |
| Oracle FLOAT | 按项目约定沿用 MySQL，返回 `DOUBLE` |

MySQL 原生数值 `FLOOR` 只有一个参数；Doris 已有的二参数数值形式作为扩展保留原有
signature 和 precision 行为。Oracle 26 还支持 `FLOOR(datetime[,fmt])`，结果始终为
`DATE`，映射为 `DATETIMEV2(0)`；默认格式为 `DD`，并且日期 FLOOR 与 TRUNC 同义。
当前只绑定年、季度、月、日、小时和分钟格式，依赖 `NLS_TERRITORY` 的周起始格式继续
明确报错。Oracle interval 重载在 Doris 没有对应 interval 类型时不做伪映射。

依据：[MySQL FLOOR 文档](https://dev.mysql.com/doc/refman/8.4/en/mathematical-functions.html)、
[MySQL `Item_func_int_val`](https://github.com/mysql/mysql-server/blob/8.4/sql/item_func.cc)、
[Oracle FLOOR(number)](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/FLOOR.html)、
[Oracle FLOOR(datetime)](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/floor-datetime.html)

### ROUND 与 TRUNCATE / TRUNC

MySQL 对 `DECIMAL(p,s)` 和常量第二参数 `d` 的精确元数据算法如下。最终 precision 至少为 1；`d` 先限制到 MySQL decimal 最大 scale 范围。

| `d` 条件 | MySQL `ROUND` 返回 | MySQL `TRUNCATE` 返回 |
|---|---|---|
| `d <= 0` | `DECIMAL(max(1,p-s+1),0)` | `DECIMAL(max(1,p-s),0)` |
| `0 < d < s` | `DECIMAL(p-(s-d)+1,d)` | `DECIMAL(p-(s-d),d)` |
| `d >= s` | `DECIMAL(p,s)` | `DECIMAL(p,s)` |
| `d` 非常量 | `DECIMAL(p,s)` | `DECIMAL(p,s)` |

多出的 1 位用于容纳 ROUND 进位，例如 `ROUND(DECIMAL(5,3) 99.999,2)` 的结果类型为 `DECIMAL(5,2)`；TRUNCATE 不会产生进位，因此不增加。其他入参规则：

| 模式 | 调用 | 返回类型与数值规则 |
|---|---|---|
| MySQL | `ROUND(integer[,d])` | `BIGINT`；exact-value 使用四舍五入、半值远离 0 |
| MySQL | `ROUND(real/string[,d])` | `DOUBLE`；approximate-value 使用平台 C 库舍入，常见为 ties-to-even |
| MySQL | `TRUNCATE(integer,d)` | `BIGINT`，向 0 截断 |
| MySQL | `TRUNCATE(real/string,d)` | `DOUBLE`，向 0 截断 |
| Oracle | `ROUND(n)` | 返回与 `n` 相同的数值类型 |
| Oracle | `ROUND(n,d)` | 返回 `NUMBER`；正数公式为 `FLOOR(n*10^d+0.5)*10^-d`，负数按对称规则处理 |
| Oracle | `TRUNC(n)` | 返回与 `n` 相同的数值类型 |
| Oracle | `TRUNC(n,d)` | 返回 `NUMBER`；`d<0` 截断小数点左侧，始终向 0 丢弃被截部分 |

在 Doris 中，`TRUNC` 与 `TRUNCATE` 作为全局函数别名绑定到同一个表达式和 BE 执行名；
dialect 只选择可用 signature：MySQL 使用标准二参数数值形式，Oracle 增加一参数数值形式和
`TRUNC(datetime[,fmt])`。Oracle 一、二参数 NUMBER 均按当前映射保持输入 DECIMAL，日期
TRUNC 返回 `DATE`，即 `DATETIMEV2(0)`，并与日期 FLOOR 共用执行逻辑。Oracle interval
重载在 Doris 没有对应 interval 类型时不做伪映射。

ROUND/TRUNCATE 的结果 precision 可能跨越 Decimal32/64/128/256 的物理宽度边界；BE 必须
按照 planner 的结果 DECIMAL 类型直接生成目标宽度列，不能只修改 FE metadata 后仍返回输入
宽度的 ColumnDecimal。

依据：[MySQL `Item_func_round::resolve_type`](https://github.com/mysql/mysql-server/blob/8.4/sql/item_func.cc#L3182-L3246)、[MySQL ROUND/TRUNCATE 文档](https://dev.mysql.com/doc/refman/8.4/en/mathematical-functions.html)、[Oracle ROUND](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/ROUND-number.html)、[Oracle TRUNC](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/TRUNC-number.html)

### POW / POWER、SQRT、SIGN

| 函数 | MySQL | Oracle |
|---|---|---|
| `POW(x,y)` / `POWER(x,y)` | 同义；两个参数默认传播为 `DOUBLE`，返回 `DOUBLE` | 仅 `POWER` 原生；任一参数为 `BINARY_FLOAT/BINARY_DOUBLE` 时返回 `BINARY_DOUBLE`，否则返回 `NUMBER`；负底数要求指数为整数 |
| `SQRT(x)` | 参数默认传播为 `DOUBLE`，返回 `DOUBLE`；负数返回 `NULL` 并告警 | 返回与参数相同数值类型；NUMBER 负数非法，binary float 可产生 NaN |
| `SIGN(x)` | 参数按 `DOUBLE` 读取，回包 `BIGINT`，值 -1/0/1 | 返回 `NUMBER`；NUMBER 值 -1/0/1；binary float 使用符号位，NaN 返回 +1 |

依据：[MySQL 数值函数源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_func.cc)、[Oracle POWER](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/POWER.html)、[Oracle SQRT](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/SQRT.html)、[Oracle SIGN](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/SIGN.html)

当前实现决策：`POW/POWER` 在两个 dialect 下均沿用 Doris 的 `DOUBLE, DOUBLE -> DOUBLE`
签名和 BE `std::pow(double, double)` 实现。Oracle 对非 binary float 参数规定返回 NUMBER，
但 NUMBER 没有可直接推导的固定 `(precision, scale)`；在确定统一映射规则前，不使用任意
`DECIMAL(p,s)` 冒充 Oracle NUMBER 回包。

`SQRT` 同样暂时在两个 dialect 下沿用 Doris 的 `DOUBLE -> nullable DOUBLE` 签名。该行为
与 MySQL 一致，包括负数返回 NULL。Oracle NUMBER 原生应返回 NUMBER，且负数非法；Oracle
binary float 的负数结果为 NaN。由于当前不为无固定 `(p,s)` 的 NUMBER 强行选择 DECIMAL，
并且本阶段不考虑 BINARY_FLOAT/BINARY_DOUBLE 的特殊语义，所以不新增 Oracle SQRT 执行分支。

`SIGN` 的结果范围固定为 `{-1,0,1}`，因此不存在 POWER/SQRT 的不确定 scale 问题。MySQL
使用 `Item_int_func` 返回有符号 `BIGINT`；Doris 的 MySQL signature 和 BE 结果列相应从
TINYINT 改为 BIGINT。Oracle NUMBER 映射的 DECIMAL 入参使用 `DECIMAL(1,0)` 返回值；Oracle
模式中的原生 Doris 整数和 FLOAT/DOUBLE 仍走 `DOUBLE -> BIGINT`，不附加 Oracle NUMBER
语义。BINARY_FLOAT/BINARY_DOUBLE 的 sign-bit/NaN 特殊规则暂不实现。

## 字符串构造函数

### CONCAT 与 CONCAT_WS

| 函数/模式 | 参数与 NULL | 返回类型和最大长度 |
|---|---|---|
| MySQL `CONCAT(a1,...,an)` | 任一参数 NULL→NULL；非字符串隐式转字符串 | 聚合所有参数 charset/collation；`Lout = ΣL(ai)`；超过 `max_allowed_packet` 时返回 NULL 并告警 |
| MySQL `CONCAT_WS(sep,a1,...,an)` | `sep` NULL→NULL；其后的 NULL 参数被跳过；空串不跳过 | 聚合 charset/collation；`Lout = ΣL(ai) + (n-1)*L(sep)` |
| Oracle `CONCAT(a,b)` | 恰好 2 参数；Oracle 当前将零长度字符串视为 NULL，但 `CONCAT(x,NULL)` 返回 `x` | 支持 CHAR/VARCHAR2/NCHAR/NVARCHAR2/CLOB/NCLOB；返回与首参同字符集，LOB/national 类型按无损转换优先级提升 |
| Oracle `CONCAT_WS` | 不存在 | — |

MySQL 长度公式是 metadata 上界，实际 `CONCAT_WS` 只在两个实际非 NULL 项之间插入分隔符。[MySQL 字符串源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_strfunc.cc#L1051-L1121)；[MySQL 字符串函数文档](https://dev.mysql.com/doc/refman/8.4/en/string-functions.html)；[Oracle CONCAT](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/CONCAT.html)

### ELT 与 MAKE_SET

| 函数 | MySQL 参数/值语义 | MySQL 返回元数据 | Oracle |
|---|---|---|---|
| `ELT(n,a1,...,ak)` | `n` 按 BIGINT，1-based；`n<=0`、`n>k`、n NULL 或选中项 NULL→NULL | 聚合候选项 charset/collation；`Lout=max(L(ai))`；nullable 恒为 true | 不存在 |
| `MAKE_SET(bits,a1,...,ak)` | `bits` 按无符号 64 位位图；bit 0 选择 a1；最多处理前 64 项；被选择的 NULL 项跳过；bits NULL→NULL | 聚合候选项 charset/collation；上界 `Lout=ΣL(ai)+(k-1)`（逗号） | 不存在 |

依据：[MySQL ELT/MAKE_SET 源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_strfunc.cc#L2206-L2314)、[MySQL 字符串函数文档](https://dev.mysql.com/doc/refman/8.4/en/string-functions.html)

## 字符串截取与长度

### SUBSTRING / SUBSTR

| 项目 | MySQL | Oracle |
|---|---|---|
| 名称 | `SUBSTRING` 与 `SUBSTR` 同义 | 仅 `SUBSTR` 原生 |
| 参数 | `(s,pos)` 或 `(s,pos,len)`；pos/len 按 BIGINT | `(s,pos)` 或 `(s,pos,len)`；pos/len 为 NUMBER 或可转 NUMBER，并转为整数 |
| 起点 | 正数从 1 开始；负数从末尾；`pos=0` 返回空串 | 正数从 1 开始；负数从末尾；`pos=0` 当作 1 |
| 长度 | 省略则到末尾；`len<=0` 返回空串 | 省略则到末尾；`len<1` 返回 NULL |
| 返回类型 | 基于首参 charset/collation 的字符串 | 与首参相同；但 CHAR→VARCHAR2，NCHAR→NVARCHAR2；CLOB/NCLOB 保持 LOB |

MySQL metadata 最大字符数算法（仅当对应参数是可求值常量时收窄）：

| 条件 | `Lout` |
|---|---|
| 初始/参数非常量 | `L(s)` |
| 常量 `pos>0` | `L(s)-min(pos-1,L(s))` |
| 常量 `pos<0` | `min(-pos,L(s))`（若 `-pos>L(s)`，源码上界为 0） |
| 再有常量 `len<0` | `0` |
| 再有常量 `0<=len<=INT_MAX` | `min(当前上界,len)` |

依据：[MySQL SUBSTR 源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_strfunc.cc#L1454-L1520)、[Oracle SUBSTR](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/SUBSTR.html)

### LEFT

| 模式 | 参数与值语义 | 返回元数据 |
|---|---|---|
| MySQL | `LEFT(s,n)`；取左侧 n 个字符；`n<=0` 返回空串；任一参数 NULL→NULL | 首参 charset/collation；常量 `n<0` 时 `Lout=0`，常量 `0<=n<=INT32_MAX` 时 `Lout=min(L(s),n)`，否则 `Lout=L(s)` |
| Oracle | 不存在原生 `LEFT` | 可按产品兼容策略改写 `SUBSTR(s,1,n)`，但要额外处理 `n<=0`、空串/NULL，不能仅靠名称别名保证 MySQL 行为 |

依据：[MySQL LEFT 源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_strfunc.cc#L1362-L1410)

### LENGTH、CHAR_LENGTH / CHARACTER_LENGTH

| 函数/模式 | 计算单位 | 返回类型 | 备注 |
|---|---|---|---|
| MySQL `LENGTH(s)` | 字节 | `BIGINT` | NULL→NULL |
| MySQL `CHAR_LENGTH(s)` | 字符 | `BIGINT` | `CHARACTER_LENGTH` 是同义词 |
| Oracle `LENGTH(s)` | 输入字符集定义的字符 | `NUMBER` | CHAR 的尾随空格计入；支持字符类型及 CLOB/NCLOB |
| Oracle `CHAR_LENGTH` / `CHARACTER_LENGTH` | — | — | Oracle Database SQL Language Reference 未定义为原生函数；不要依据 Oracle Database Lite/ODBC escape 文档当作数据库内核行为 |

Oracle 的字节版本是 `LENGTHB`，并另有 `LENGTHC/LENGTH2/LENGTH4`；所以 Oracle 模式下不能把 `LENGTH` 继续绑定为 MySQL 的字节长度实现。[MySQL 字符串函数文档](https://dev.mysql.com/doc/refman/8.4/en/string-functions.html)；[Oracle LENGTH](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/LENGTH.html)

## 大小写转换

| 模式 | 函数名 | 返回类型/长度 |
|---|---|---|
| MySQL | `UPPER(s)`=`UCASE(s)` | 聚合首参 charset/collation；`Lout=L(s)*caseup_multiply` |
| MySQL | `LOWER(s)`=`LCASE(s)` | 聚合首参 charset/collation；`Lout=L(s)*casedn_multiply` |
| Oracle | `UPPER(s)` | 与 `s` 相同类型；支持 CHAR/VARCHAR2/NCHAR/NVARCHAR2/CLOB/NCLOB；使用底层字符集的 binary mapping |
| Oracle | `LOWER(s)` | 与 `s` 相同类型；同上 |
| Oracle | `UCASE` / `LCASE` | 不存在 |

MySQL 的乘数由具体字符集提供，不能假设大小写映射永远等长。Oracle 语言相关转换是另一个函数族 `NLS_UPPER/NLS_LOWER`。[MySQL 大小写源码](https://github.com/mysql/mysql-server/blob/8.4/sql/item_strfunc.cc#L1342-L1360)、[Oracle UPPER](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/UPPER.html)、[Oracle LOWER](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/LOWER.html)

## Doris 对齐落点

| 差异种类 | 首选实现位置 | 本批函数示例 |
|---|---|---|
| 仅静态返回类型不同 | dialect 下选择不同 signature | Oracle NUMBER 的 `ABS/CEIL/POWER/SQRT/SIGN`；MySQL 整数→BIGINT |
| 返回精度依赖常量参数 | 函数自身的 signature/precision 推导 | MySQL `ROUND/TRUNCATE`、`SUBSTR`、`LEFT` |
| 只影响协议类型标志且不能由 signature 表达 | 保留的 result type descriptor provider；当前不要全局启用 | 后续确有 write type 特例时使用 |
| 值语义不同 | FE 绑定到 dialect 专用函数或 BE 专用实现 | `SUBSTR(pos=0)`、`LENGTH` 字节/字符、ROUND 算法 |
| 函数在目标 dialect 不存在 | 解析/绑定阶段报未知函数；若产品明确要求，再单独做兼容扩展 | Oracle 的 `CONCAT_WS/ELT/MAKE_SET/LEFT/UCASE/LCASE/SUBSTRING/POW` |

实现顺序应是：先用 signature 表达类型；再在函数局部实现依赖常量的精度/长度推导；只有 signature 无法表达且差异确实仅属于对外 write type 时，才启用 provider。不能用协议回包标志掩盖实际执行类型或值语义差异，否则 prepared statement/JDBC 首次获取的 metadata 与实际 binary row 解码会不一致。
