# Oracle 系统包场景覆盖与实施计划

日期：2026-09-05。状态：规划，尚未实现。
基于当前 Doris 工作区和 `/tmp/plsql/1.txt`、`2.txt`、`3.txt`。
架构背景见 [现有 PL/SQL 调研](plsql-implementation-and-package-extension.md)。

## 1. 交付边界

三个包逐个交付，每阶段包含接口语义、实现、测试和样例迁移说明。
第一轮覆盖提供文件中实际使用的接口及必要依赖；其他接口列入扩展清单，不以“整个 Oracle 包已兼容”描述交付。

当前规划假设：保留包名、方法名及样例所需调用形式，其他 Oracle SQL 语法允许逐项迁移改写。
若要求原文直接运行，需扩大语法、类型及 SQL 兼容工作量；该偏好已向用户询问，尚未确认。
源 Oracle 版本、NLS_CHARACTERSET、脱敏明文/密文对或对照环境也待补充。
这些信息不阻塞框架和随机数阶段，但影响字节转换、历史密文兼容的最终验收。

## 2. 场景清单与依赖

| 编号 | 原始位置 | 必须覆盖的行为 | 必要关联工作 |
| --- | --- | --- | --- |
| S1 | 1.txt 开头 INSERT SELECT | CAST_TO_RAW 逐行生成邮件正文字节，并经 TO_BLOB 写入 | 二进制列类型与参数绑定迁移；序列、SYSDATE、日期格式、拼接语义核对 |
| S2 | 1.txt 模板 SELECT | 子查询中的 CAST_TO_VARCHAR2 读取正文和附件 | blob_info 实际类型及编码；相关子查询、ROWNUM 限行迁移 |
| S3 | 1.txt / 2.txt DES3_DEC | 字符串重载、命名参数、decrypted_string OUT、去除尾部零字符 | 十六进制到 RAW 的隐式转换、无损密文字节通路、RTRIM/CHR、函数创建与调用入口 |
| S4 | 2.txt DES3_ENC | RAW 重载、命名参数、encrypted_data OUT、RAWTOHEX | RAW(n)、RPAD/TRUNC/LENGTH/CHR/TO_CHAR、函数局部声明与返回 |
| S5 | 1.txt BLOB_TO_VARCHAR | UTL_RAW.CONVERT、CAST_TO_VARCHAR2、DBMS_LOB.GETLENGTH/SUBSTR | BLOB 入参、字节切片、CEIL、循环、EMPTY_CLOB 返回类型 |
| S6 | 3.txt VALUE(low, high) | 带边界随机小数 | 参数占位符替换；NUMBER 精度与 NULL/边界行为 |
| S7 | 3.txt 随机排序与分组抽样 | VALUE 与 VALUE() 两种形式，逐行随机排序后限行 | SAMPLE、ROWNUM、DISTINCT/UNION ALL 的操作顺序、残缺 SQL 片段修复 |
| S8 | 3.txt 日期表达式 | FLOOR(VALUE()*3+1) 及日期减天数 | 每行求值，Oracle 日期算术到 Doris 日期函数的迁移 |

所有场景最终都要有可执行用例。迁移附属语法不能被遗漏，也不能用包级单测替代场景验收。
原文件含有绑定占位符、业务表依赖和不完整 SQL，测试使用脱敏小表与明确参数，同时保存原句到迁移句的映射。

样例本身有需保留并说明的行为：

- DES3_ENC 按字符 LENGTH 对齐，而算法要求字节长度对齐；中文可能暴露差异。
- 样例长度恰为 8 的倍数时仍补整块零；包层不能自行添加另一种 padding。
- DES3_DEC 把 VARCHAR2 i_data 交给 RAW 形参；结合 ENC 的 RAWTOHEX 返回值，需验证隐式 hex 解码链。
- BLOB_TO_VARCHAR 循环每次覆盖 v_varchar，超过 2000 字节时只保留最后一段，没有拼接。
  “忠实执行原函数”和“返回完整 BLOB 文本”应分别验证，不静默改写业务逻辑。
- BLOB 以 2000 字节切块可能切断多字节字符；NULL BLOB 与空 LOB 的处理也要分别核对。

## 3. Oracle 语义基线

DBMS_RANDOM 本轮聚焦 VALUE、RANDOM 和 STRING。VALUE 零参为 [0,1)，双参为 [low,high)，文档指定
NUMBER 和 38 位精度；RANDOM 返回 32 位有符号整数；STRING 按选项生成指定长度的随机字符。
SEED、NORMAL、INITIALIZE、TERMINATE 留作后续，暂不承诺 Oracle 同种子逐值序列复现。
[Oracle DBMS_RANDOM](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_RANDOM.html)

UTL_RAW 本轮聚焦 CAST_TO_RAW、CAST_TO_VARCHAR2、CONVERT。前两者在文本与 RAW 间转换，
RAW 是字节值，不能与其十六进制显示混淆。CONVERT 的顺序为 r、目标字符集、源字符集，
接受样例中的 language_territory.charset 形式，语言和地区不参与转换。
[Oracle UTL_RAW](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/UTL_RAW.html)

Oracle UTF8 与 AL32UTF8 不可统一按 Java UTF-8 处理：前者是 CESU-8，补充字符的编码不同。
[Oracle Unicode 说明](https://docs.oracle.com/database/121/NLSPG/ch6unicode.htm)
当前 SQL 执行路径只接受样例使用的 UTF8/AL32UTF8 名称并执行字节保持转换；PL 路径使用 Java UTF-8。
这覆盖样例中的同字符集转换和常见 BMP 文本，Oracle UTF8 补充字符的 CESU-8 差异仍需后续补齐。

DBMS_OBFUSCATION_TOOLKIT 本轮聚焦 DES3ENCRYPT/DES3DECRYPT 的 RAW、VARCHAR2 过程重载，
以及同算法的函数返回形式。支持命名 OUT 和缺省 which/iv；2-key 为默认模式，另有 3-key，采用 CBC。
输入长度必须按 8 字节对齐。默认 IV 的有效字节、错误分支及历史版本行为需以对照向量锁定；
官方页面中的部分短密钥说明与 2/3-key 长度限制不一致，不直接把文字复制成校验代码。
DES、MD5、GETKEY 系列不属于本轮样例必需方法。
[Oracle DBMS_OBFUSCATION_TOOLKIT](https://docs.oracle.com/cd/E24693_01/appdev.11203/e23448/d_obtool.htm)

DBMS_LOB 仅补样例要求的读取子集：BLOB GETLENGTH 和 SUBSTR（字节数量、从 1 起始偏移、返回 RAW），
并明确 TO_BLOB、EMPTY_CLOB 的迁移或兼容路径，不扩大为完整 LOB locator 系统。
[Oracle DBMS_LOB](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_LOB.html)

## 4. 贴合 Doris 的设计

### 4.1 PL 过程路径

沿用 `objects/Xxx.java + XxxClass.java`、`PlObject/PlClass/MethodDictionary`，在 `Exec.registerBuiltins()` 注册。
不使用 CREATE PACKAGE 脚本来安装系统包，也不引入元数据持久化和包源码加载依赖。

给现有分派增加兼容适配层：先保留实参表达式及参数名，解析签名后只求值 IN/INOUT，
OUT 必须绑定可写目标；成功返回后通过统一入口回写。
签名描述只覆盖当前需要的参数名、模式、默认值与 RAW/STRING/NUMBER 类型。
保留旧 Method 接口适配，DBMS_OUTPUT、UTL_FILE、Table 原有调用必须回归。
错误在可被 PL handler 处理的边界转成 Signal，并确保作用域/调用栈 finally 恢复。

### 4.2 SQL 路径

现有 SQL `pkg.func(...)` 会被解析为带 dbName 的函数，`pkg.value` 会被解析为限定列引用。
只注册 PL 对象不会让 S1、S2、S7、S8 生效。

新增有明确白名单的系统包 SQL 解析/绑定适配，处理带括号调用和 DBMS_RANDOM.VALUE 零参写法。
同时定义同名数据库 UDF、表别名/列的歧义规则；不全局拦截所有带点标识符，不做文本正则替换。
SQL 表达式复用或新增 Nereids scalar expression，逐行计算交给 BE；不在 FE 遍历查询行调用 Exec。
PLSQLLogicalPlanBuilder 与普通 LogicalPlanBuilder 应复用适配规则。

DBMS_RANDOM SQL 表达式沿用 UniqueFunction/VolatileIdentity 非确定性机制，不能常量折叠或跨调用合并。
当前 Random 双参返回 BIGINT，零参返回 DOUBLE，不能把 Oracle VALUE(low,high) 直接别名到 Random(low,high)。
按 NUMBER 精度设计专用表达式和必要的 Decimal 执行支持；low+(high-low)*rand() 仅可作为经明确接受的
近似迁移方案，不能冒充完整兼容。检查大范围溢出、上界舍入与动态列参数。

### 4.3 RAW 与二进制桥接

PL 增加明确的 RAW 类型及 byte[] 值语义，补声明长度、赋值/复制、NULL、参数绑定和输出显示。
SQL 优先复用已有 VARBINARY/VarBinaryLiteral，补 SQL 结果到 Var 的无损字节读取；
不为本需求直接新增一套 BE 存储类型。
RAW 是有界短字节串，BLOB 读取适配独立保留长度限制，不能把所有 BLOB 限成 RAW(n)。

特别注意：当前 `to_binary()` 是 hex 解码，`from_binary()` 是 hex 编码
（`be/src/exprs/function/function_varbinary.cpp`）。它们不能直接用作 CAST_TO_RAW/CAST_TO_VARCHAR2 的别名。
可评估复用其 hex 核心实现支撑 HEXTORAW/RAWTOHEX；文本编解码另行实现，FE/BE 使用同一套测试向量。

密文经过 VARCHAR2 重载时可能含任意字节。禁止经有损 Java String 解码再编码；
需要显式的原始字节承载/转换策略，并用 Oracle 样例确认行为。SQL 展示 hex 与文本解码保持分离。

## 5. 按难度及依赖分阶段

| 阶段 | 工作 | 难度 | 阶段验收 |
| --- | --- | --- | --- |
| P0 | 固定 S1–S8、源类型/字符集、解析探针、Oracle 对照脚本、SQL 适配规则 | 中 | 每个场景都有依赖、迁移方式和预期结果定义；未确认事项显式标记 |
| P1 | DBMS_RANDOM.VALUE/RANDOM/STRING：PL 与 SQL 调用、非确定性与精度 | 源码完成，待外部验证；VALUE 当前返回 DOUBLE | S6–S8，范围、字符集、长度和随机排序逐行变化通过 |
| P2 | RAW 字节表示、UTL_RAW 三方法、SQL 二进制桥接 | 源码完成，待外部验证；字符集范围见上文 | 文本/字节向量、S1/S2 和加密前置数据链通过 |
| P2b | DBMS_LOB.GETLENGTH/SUBSTR、BLOB/TO_BLOB/EMPTY_CLOB 适配 | 源码完成，待外部验证；当前限定 BLOB 读取 | S5，0/1/1999/2000/2001/4001 字节及字符跨块验证，S1/S2 完整闭环 |
| P3 | 命名参数/OUT 调用模型、DES3 RAW/STRING 过程与函数形式 | 源码进行中，PL 过程核心已接入 | S3/S4，Oracle→Doris 解密及 Doris→Oracle 解密，固定密文逐字节一致 |
| P4 | 全部场景集成、普通 SQL 与 PL 内 SQL、原有包回归、兼容说明 | 中 | S1–S8 全部有运行结果；附属语法迁移清单无遗漏 |

主线顺序：DBMS_RANDOM → UTL_RAW → DBMS_OBFUSCATION_TOOLKIT；P2b 是此次场景覆盖的一部分。
P0 可先开展加密对照数据准备，避免到 P3 才发现缺少源版本/字符集。
不预估固定工期：SQL 精度方案、字符集和原文兼容程度确定后再拆分具体提交。

### 当前样例覆盖状态

| 场景 | 当前状态 | 尚需确认 |
| --- | --- | --- |
| S1 `TO_BLOB(UTL_RAW.CAST_TO_RAW(...))` | 函数链已实现 | 目标列需迁移为 VARBINARY |
| S2 `UTL_RAW.CAST_TO_VARCHAR2(blob_info)` | RAW 与兼容 STRING 入参已实现 | 实际表字段类型需核对 |
| S3 DES3 字符串解密过程 | 命名参数、OUT、hex 隐式 RAW、RTRIM/CHR 已实现 | Oracle 固定密文对照 |
| S4 DES3 RAW 加密过程 | RAW、OUT、RPAD/TRUNC/CHR/RAWTOHEX 已实现 | Oracle 固定密文对照 |
| S5 BLOB_TO_VARCHAR | GETLENGTH/SUBSTR/CONVERT/CEIL 已实现 | 多块函数原文只保留最后一块；需确认是否保持原逻辑 |
| S6 `DBMS_RANDOM.VALUE(low, high)` | 已实现 | NUMBER 38 位精度目前近似为 DOUBLE |
| S7/S8 随机排序 | 裸 VALUE 和逐行随机值已实现 | ROWNUM、SAMPLE 等外围 SQL 兼容不属于包函数本身 |

## 6. 关键测试与验收方式

- 解析/解释器测试：裸属性、嵌套包调用、函数局部声明、命名参数乱序、未知/重复参数、
  缺参/默认值、OUT 传常量报错、异常后同连接继续运行。
- RANDOM：范围、NULL、等界/倒置边界经 Oracle 对照定义、Decimal 精度、动态边界，
  多行/多批次/多个调用的独立求值；抽样用结构与范围断言，不比较随机结果的固定行顺序。
- RAW：ASCII、中文、补充字符、0x00/0xFF、空值、长度边界、无效 hex、非法编码、
  charset 同名转换和 UTF8/AL32UTF8 差异；SQL 和 PL 得到相同字节。
- LOB：使用明确的 VARBINARY 测试表，分别测试 NULL、空值、跨块和原函数最后一段行为。
- DES3：单向 Oracle 固定向量优先于自加自解；覆盖两种密钥模式、默认/显式 IV、
  8 字节边界、整块零填充、中文、非法密钥/密文和两类 OUT。
- 测试层次：Java 包核心与 Exec 执行单测；新增 BE 表达式的针对性单测；
  `regression-test/suites/plsql_p0/` 的真实 CREATE PROCEDURE/CALL 及普通 SQL 场景。
- 场景中的独立 CREATE FUNCTION 需验证普通 SQL 入口、PL routine registry 和函数 RETURN 的完整链路。
  若普通 SQL 要逐行调用用户 PL 函数，不能假定现有 DorisFunctionRegistry 等于 SQL UDF 注册；
  优先给出等价迁移封装，若要求原文调用则另列必要实现，不宣称自动支持。

## 7. 当前待确认事项

1. 原文直接运行，还是允许明确的 Oracle SQL 迁移改写。
2. Oracle 版本、NLS_CHARACTERSET；实际 blob_info/mail_body 列定义与内容编码。
3. 脱敏明文—密文对或 Oracle 对照环境，特别是默认 IV 和 STRING 密文通路。
4. BLOB_TO_VARCHAR 的期望是保留末段，还是业务上要完整文本；先忠实记录原行为。

无需等待这些信息即可先做 P0 和 P1 的具体设计；P3 兼容性验收必须有对照证据。

## 8. 实施记录

2026-09-05 开始 P1：

- PL 路径注册连接级 DBMS_RANDOM 对象，支持 VALUE、RANDOM 和 STRING。`DbmsRandomClass` 负责
  方法派发、参数个数、类型、NULL 与长度检查，`DbmsRandom` 只实现已类型化的随机计算。
- 普通 SQL 保留 DBMS_RANDOM 包限定名进入分析阶段，由 Nereids 函数注册表分别解析为内部函数
  `dbms_random_value`、`dbms_random_random` 和 `dbms_random_string`，并在 BE 中逐行计算。VALUE
  当前有效精度是 DOUBLE，尚未达到 Oracle NUMBER 的 38 位精度。
- 增加解析测试、包核心测试和 `plsql_p0/test_dbms_random.groovy` 场景源码。
- 按用户要求不运行 UT、FE/BE 编译或回归测试；只执行源码检查和 `git diff --check`。
