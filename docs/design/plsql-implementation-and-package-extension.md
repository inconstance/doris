# PL/SQL 实现与系统包扩展调研

调研日期：2026-09-05。源码基线：`820cefbdba`，以当前工作区为准。
本次仅新增调研文档，没有修改实现，也没有执行数据库 DDL 或文件包 I/O。
结论依据本仓库源码；不是 Oracle 兼容性认证。

## 1. 执行架构

主要代码位于 `fe/fe-core/src/main/java/org/apache/doris/plsql/`。
解释器与语法由 Hive HPL/SQL 移植并经 Doris 修改，语言包含多种过程 SQL 风格。

```text
MySQL 协议 SQL
  -> Nereids DorisParser / CallCommand / CallFunc
  -> CallProcedure.run()
  -> ConnectContext.getPlSqlOperation()（连接内懒初始化并复用）
  -> PlSqlOperation.execute()
  -> Exec.parseAndEval()
  -> PLLexer + PLParser -> Exec visitor
       ├─ Scope / Var / Expression / Signal：过程控制和表达式
       ├─ PlObject / MethodDictionary：Java 内置系统包
       ├─ FunctionRegistry / Package：用户过程、函数、包
       └─ Stmt -> QueryExecutor -> Doris SQL 执行
```

- `PLParser.g4` 导入 `DorisParser`，`PLLexer.g4` 提供过程语言词法。
- 外层普通 SQL 入口有 CREATE PROCEDURE、CALL、SHOW/DROP PROCEDURE；
  PLParser 中存在 CREATE PACKAGE、匿名块等规则，不代表普通 SQL 入口也直接接受它们。
- CREATE PROCEDURE 的命令路径保存源码；CALL 才进入解释器加载、解析和执行。
- `Exec` 直接遍历 ANTLR parse tree，没有独立的过程语言字节码或完整语义分析阶段。
- `Exec.init()` 创建全局作用域，注册 SQLCODE、SQLSTATE 等变量、内置函数和系统包。
- 服务端使用 `DorisFunctionRegistry`、`DorisPackageRegistry`、`PlsqlQueryExecutor`。
  默认构造的 `Exec` 使用 JDBC 和内存注册表，供独立脚本路径使用；这两条路径不能混为一谈。

### SQL 与变量的衔接

`Stmt.statement()` 抽取 SQL 原文，交给 `PlsqlQueryExecutor.executeQuery()`。
后者克隆 ConnectContext，为每条查询建立 ConnectProcessor，便于多个游标保有各自的查询状态。
执行仍走 Doris 的分析、规划和协调器。

`NereidsParser` 在 `isRunProcedure()` 时选择 `PLSqlLogicalPlanBuilder`。
其 `visitColumnReference()` 先查过程变量，找到后转成 Nereids Literal，否则生成 UnboundSlot。
因此变量与列重名有语义影响；文件句柄等 PL 对象也不能直接假设可作为 SQL 值。

普通 SELECT 通过 `PlsqlResult` 回传结果集；SELECT INTO 填写变量并检查无行、多行。
`EXECUTE IMMEDIATE` 有单独的 `Stmt.exec()` 路径，其无 INTO 结果写入 Console。
语法虽接受 USING，当前该执行方法没有实现 using_clause 的绑定。

## 2. 三套扩展机制

| 机制 | 注册及执行 | 适合的扩展 |
| --- | --- | --- |
| Java 系统包 | Exec.registerBuiltins -> PL_OBJECT -> MethodDictionary | DBMS_*、UTL_* 及有状态原生能力 |
| 用户 PL 包 | PackageRegistry -> 源码 -> Package -> 过程/函数 parse tree | 用户编写的过程逻辑与包变量 |
| 内置标量函数 | BuiltinFunctions + FunctionString/Datetime/Misc | 普通过程表达式函数 |

`objects/TableClass` 也使用对象分派，提供 FIRST、LAST、NEXT、PRIOR、COUNT、EXISTS、DELETE
和 `__GETITEM__` / `__SETITEM__`。它可作为集合对象的参考，但不是另一个 DBMS/UTL 包；
存在对象实现也不等于所有集合声明、BULK COLLECT 语法均已贯通。

### Java 系统包的调用约定

1. `XxxClass implements PlClass` 保存方法字典，`newInstance()` 创建实例。
2. `Xxx implements PlObject` 保存实例状态与依赖，通过 `plClass()` 找到字典。
3. `Exec.registerBuiltins()` 将实例放入全局常量变量，类型为 `Var.Type.PL_OBJECT`。
4. `visitExpr_dot_method_call()` 找到变量，将参数求值，再调用 `Method.call(self, List<Var>)`。
5. 有返回值的方法返回 Var；现有过程方法返回 Java null。

字典按大写方法名查找，不区分大小写，一个名称只有一个 Method，尚无签名级重载选择。
`MethodParams` 仅做参数数量校验和 Java class cast，不提供完整的 PL 类型转换、默认值、
命名参数重排、OUT/IN OUT 绑定或统一空值约定。
目前 dispatch 对整个 func_param 求值，参数名称并未用于绑定。
变量表达式可能返回原 Var 引用，所以不能简单断言“绝对无法修改实参”；但没有受约束的
可写实参协议，不宜据此实现稳定的 OUT API。

## 3. 已实现的 DBMS/UTL 包

仓库 PL/SQL 实现中只发现以下两个内置包。没有 DBMS_SQL、DBMS_RANDOM、
DBMS_LOB、UTL_HTTP、UTL_RAW 等包的注册和实现。
Oracle 外部数据源初始化 SQL 中的 UTL_RAW 使用不属于 Doris 的 PL 实现。

### DBMS_OUTPUT

源码：`objects/DbmOutputClass.java`、`objects/DbmOutput.java`。
注意 Java 类名确实是 DbmOutput，SQL 名称是 DBMS_OUTPUT。

| 方法 | 当前行为 |
| --- | --- |
| PUT_LINE(value) | 恰好一个参数；对 Var 调用 toString()，输出一行；返回 null |

没有 ENABLE、DISABLE、PUT、NEW_LINE、GET_LINE、GET_LINES 等方法。
没有包自己的行队列、读取指针、启停状态或容量限制。
服务端 Console 是 `PlsqlResult`，文本累积在 StringBuilder，执行末尾作为 OK 消息返回。
每次 `PlSqlOperation.execute()` 会 reset 文本结果；独立脚本路径使用其 Console。
以后实现 GET_LINES 时，需定义独立的包缓冲区及其与现有返回消息的关系。

### UTL_FILE

源码：`objects/UtlFileClass.java`、`objects/UtlFile.java`、`File.java`。

| 方法 | 当前签名与行为 |
| --- | --- |
| FOPEN(dir, name [, mode, ...]) | 至少两个参数；返回 FILE Var；只解释第三个参数，额外参数未校验或使用 |
| GET_LINE(file) | 一个参数，返回字符串；没有输出缓冲区参数 |
| PUT(file, string) | 两个参数，写字符串 |
| PUT_LINE(file, string) | 两个参数，写字符串并追加换行 |
| FCLOSE(file) | 一个参数，关闭输入/输出流 |

`Var.defineType()` 硬编码 `UTL_FILE.FILE_TYPE -> Var.Type.FILE`。
这里的 FILE 值是 `org.apache.doris.plsql.File`，不是另一个 PlObject。

实现边界：

- FOPEN 的 r 为读，w 为覆盖写。缺省及其他模式均落入 create(overwrite=false)，没有 append 实现，
  也没有非法模式拒绝；第四个参数不会限制行长。
- 底层 `FileSystem.get(new Configuration())` 使用 Hadoop FileSystem，具体文件系统取决于配置；
  dir 是 Path 的组成部分，没有数据库 DIRECTORY 对象解析层。
- `writeChars/readChar` 按 Java 两字节字符读写，不是 UTF-8 文本读写。
- GET_LINE 读到 LF 或 EOF 停止；EOF 返回已读文本或空串，其他 IOException 返回空串，
  无法仅靠结果区分空行、文件末尾和部分读取错误；CRLF 的 CR 不会被剔除。
- File.open/create/writeString/close 捕获 IOException 后 printStackTrace，未统一转成 PL Signal。
  打开失败仍可返回 FILE 包装对象，之后访问空流可能出现 NullPointerException。
- 没有 IS_OPEN、FFLUSH、FCLOSE_ALL、FREMOVE、FRENAME 等方法；没有统一的文件句柄登记和会话清理。
- 该路径没有 SQL 层的目录授权检查或用户到文件系统身份的映射；扩展文件能力时需要明确执行身份。

## 4. 用户过程与 CREATE PACKAGE

过程和包元数据由 `PlsqlMetaClient -> PlsqlManager` 管理，键包含 name、catalogId、dbId。
存储内容是过程源码，或者包 header/body 源码；EditLog 与 JSON checkpoint 支持恢复。
写操作检查 ADMIN，非 Master 通过 Thrift 转发；ownerName 是存储字段，
不能据此推断已实现 definer/invoker 权限模型。

`DorisFunctionRegistry.saveInCache()` 实际 cache.put 已注释，注释明确提到跨 FE 失效与切主问题。
用户包则经 `Exec.findPackage()` 按需加载源码，实例保存在连接内的 packages Map，包变量随实例保留。
这个 Map 以包名为键，和持久层完整键不同；切库、同名包和跨连接替换需专门测试。

`Package` 按名称分别保存函数、过程，复用 `InMemoryFunctionRegistry.setCallParameters()`。
该绑定器有命名参数匹配以及过程 OUT/INOUT 回写，但仍有边界：

- 有实参时要求实参与形参数量严格一致，没有应用形参默认值。
- 无实参时直接返回，缺参诊断不完整。
- 不存在的命名参数未找到时回退到原位置，没有明确拒绝。
- OUT 回写只识别特定 qident 表达式，函数路径传入的 out Map 为 null。
- publicFuncs/publicProcs 被收集，但执行方法未据此验证可见性。
- 包体引用 create_function_stmt/create_procedure_stmt 规则，内部成员仍要求 CREATE/ALTER/REPLACE 前缀，
  不能直接套用通常的无 CREATE 成员声明格式。

### 已定位的问题

`PlsqlManager.addPackage()` 在判断 isForce 前无条件执行了一次 put，之后非 force 分支再 putIfAbsent。
这导致非 force 分支判断时条目必已存在，并且抛错前已改变内存，尚未写 EditLog。
`PlsqlMetaClient.addPlsqlPackage()` 的 Master 分支传入 false；header/body 写入会经过此路径。
这是源码可直接确认的逻辑问题，本次未对服务执行复现，也未修复。

## 5. 异常和生命周期

`Signal`、作用域 handler 和 SQLCODE/SQLSTATE 共同控制执行；`visitStmt()` 在存在未处理 Signal 时
跳过普通语句。`PlSqlOperation` 最后调用 printExceptions，把残留错误汇总到 MySQL 状态。

当前 `visitException_block_item()` 只对 WHEN OTHERS 执行 handler，不能假定所有命名异常已经实现。
Java 方法抛出的异常还需要看具体调用路径：CALL visitor 有 catch 并 signal，直接表达式调用
未使用同样的局部转换，可能冒泡至最外层而错过块内异常处理。

Exec 和系统包实例随 PlSqlOperation 在连接内复用；每次调用仅明确 reset 结果对象。
若新增包需要可变状态，应放在实例中，不能放在单例 XxxClass 中。
若新增包持有文件、网络连接、游标，应增加明确的关闭协议；当前 PlObject 没有 close/reset 接口。
作用域进出及用户过程调用多处未用 finally，抛出 Java 异常后的栈恢复和下一次调用需专项验证。

## 6. 验证与测试现状

`regression-test/suites/plsql_p0/` 有 5 个 suite，覆盖变量、SQL 操作、循环游标、
过程展示和 information_schema.routines；p2 有一个 SHOW 过滤失败 suite。
部分结果集调用被注释，suite 注释提示现有 JDBC 测试方式处理多结果集及 PRINT 的限制。
这不应扩展解释为 JDBC 本身完全不支持存储过程结果集。
未发现以 plsql 为路径的 FE 单元测试，也未检索到 DBMS_OUTPUT/UTL_FILE 的直接回归用例。

本次将工作区已有 ANTLR 4.13.1 生成代码单独编译到 `/tmp/doris-plsql-research/classes`，
以临时 Probe 检查解析分支。没有重新生成 parser，所以结果只作为源码阅读的辅助：

| 输入 | 解析结果 |
| --- | --- |
| CALL DBMS_OUTPUT.PUT_LINE('HELLO'); | 0 错误，进入 dot_method |
| BEGIN DBMS_OUTPUT.PUT_LINE('HELLO'); END; | 0 错误，进入 dot_method |
| 块中声明 UTL_FILE.FILE_TYPE 并赋值 FOPEN(...) | 0 错误，FOPEN 进入 dot_method |
| PUT_LINE(A => 'HELLO') | 0 错误；只证明接受语法，不证明参数绑定正确 |
| CREATE PACKAGE P AS PROCEDURE X(); END P; | 0 错误 |
| 包体内 PROCEDURE X() AS BEGIN NULL; END; | 2 个语法错误，与当前成员规则要求一致 |

没有运行 FE 完整构建、PL 执行单元测试或集群回归；本报告不声称上述包端到端可用。

## 7. 后续扩展建议

先确定具体包的方法签名、参数模式、返回类型、状态范围和错误语义，再选择扩展层。
普通 `PACKAGE.METHOD(args)` 原生能力通常只需增加 Xxx/XxxClass 并在 registerBuiltins 注册，
不需要给每个包名增加词法关键字，也不需要修改元数据 Thrift。

推荐顺序：

1. 为现有 DBMS_OUTPUT、UTL_FILE 补解析到 dispatch 的最小执行测试，固定既有行为。
2. 若目标 API 需要 OUT、命名参数、默认值或重载，先补 Method 的签名与参数绑定抽象，
   保留实参表达式/可写目标；避免在每个包里重复处理。
3. 增加统一异常转换和必要的资源释放钩子，验证失败后同一连接仍能正确执行下一次调用。
4. 逐个实现方法。纯函数优先复用 Var；有状态包使用每 Exec 实例；执行 SQL 的包注入 QueryExecutor。
5. 新增类型先检查 Var 类型表示与赋值规则。FILE_TYPE 的硬编码仅是现状，不宜作为所有包类型的长期方案。
6. 只有扩展目标涉及用户 CREATE PACKAGE，才同步修复元数据写入、包体语法、可见性及缓存命名空间问题。

测试至少涵盖方法存在性、大小写、参数数量/类型/NULL、默认值和命名参数、OUT 非变量实参、
同连接状态保留/跨连接隔离、异常后继续执行。文件包另加 EOF、编码、模式、无效句柄和关闭测试。
DBMS_OUTPUT 输出可用可注入 Console 做精确断言；SQL 可观察结果沿用写入测试表再 SELECT 的回归方式。
