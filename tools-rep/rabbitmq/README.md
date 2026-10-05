# Cool Request RabbitMQ 小工具

Cool Request（IntelliJ 平台插件）的 RabbitMQ 可视化小工具。以独立 JAR 形式被宿主 Cool Request 加载，
在宿主「工具」页即可完成 RabbitMQ 连接管理、消息生产、消息消费与消息查看。

## 功能

- 多连接管理：host/port/vhost/用户名/密码，增删改选，选中即记忆，跨会话保留。
- 生产面板：Exchange + 类型（direct/topic/fanout/headers）+ RoutingKey + Content-Type + Headers(JSON) + 消息体，发送即给结果/异常。
- 消费面板：实时表格（时间/DeliveryTag/RoutingKey/大小/Body 摘要），Prefetch、行数上限、自动声明队列、Body 多策略解码（Text/JSON/Hex）。
- 消息详情：完整元数据 + Body 解码切换 + 复制。
- 全部网络/阻塞操作在后台线程执行，不卡 UI；关闭面板自动释放连接与消费器。
- 错误可读化：连接失败/认证失败/vhost 不存在/队列参数冲突等所有失败场景，统一翻译为「原因 + 中文建议 + 异常类型与定位」，不显示裸 `null`（部分 AMQP 异常 `getMessage()` 为 null，真实原因在 cause 链里）。
- 发送结果可信：Headers(Json) 非法在发送前拦截报错（不再静默忽略导致 header 未生效）；发布使用 publisher mandatory 确认，消息未被任何队列接收时明确报告「已由 broker 退回」，避免 headers 交换机"假 OK"。

## 目录结构

```
├── build-tool.sh / build-tool.bat   # 轻量构建：编译 src + 平铺合并依赖为单 fat jar
├── lib/                             # 本地依赖（见下）
├── src/main/java/dev/coolrequest/tool/rabbitmq/   # 工具实现
├── src/main/resources/              # coolrequest.tool / tool.name / logo.svg / plugin.xml
└── test/manual/                     # 无 IDE 依赖的全链路自测（凭据走命令行参数）
```

## 构建

工具必须打成"单 fat jar"且 jar 根目录含 `coolrequest.tool`、`tool.name`、`logo.svg` 三个资源；
第三方依赖（amqp-client / slf4j）需内嵌，宿主用类加载器只从工具 jar 加载业务类。

Linux/macOS（Git Bash）：
```bash
./build-tool.sh
```
Windows（cmd）：
```bat
build-tool.bat
```

构建脚本用本机 IDEA 2026.2 的 lib 目录作编译 classpath（自带 JDK25 javac，`--release 17` 输出兼容运行时），
产物：`build/libs/cool-request-tool-rabbitmq-1.1.0.jar`。

## 本地依赖（lib/）

- `coolrequest-tool-1.0-SNAPSHOT.jar` — Cool Request Tool SDK（含 `dev.coolrequest.tool.ToolPanelFactory` / `CoolToolPanel`），从已装插件的 `lib/coolrequest-tool-1.0-SNAPSHOT.jar` 拷贝。
- `amqp-client-5.21.0.jar` — RabbitMQ Java 客户端。
- `slf4j-api-1.7.36.jar` — amqp-client 的日志门面（内嵌，运行期 idle）。

## 装配到 Cool Request

在 Cool Request 的「工具」页 → Widget Store → **Install local plugin**，选择上面的产物 jar 即可。
或在宿主 `%USERPROFILE%\.config\.cool-request\request\tools\` 放入 jar 并把其绝对路径加入宿主工具列表。

## 功能测试（连真实 RabbitMQ）

`test/manual/AmqpErrorsHarness.java` 为无 IDE 依赖的自测 harness，覆盖：正确凭据订阅对照、错误密码、
错误端口、错误 vhost、passive 声明不存在队列、镜像 Producer 的交换机自动声明、队列属性冲突声明、
mandatory 未路由退回（无 header / header 不匹配 → NO_ROUTE；匹配 → 已路由）等场景。
凭据全部走命令行参数，不写入源码：

```bash
JDK25=<IDEA 自带 jbr>
CP="build/classes;lib/amqp-client-5.21.0.jar;lib/slf4j-api-1.7.36.jar"
"$JDK25/bin/javac" --release 17 -cp "$CP" -d build/harness test/manual/AmqpErrorsHarness.java
"$JDK25/bin/java" -cp "build/harness;$CP" AmqpErrorsHarness <host> <port> <vhost> <user> <pass>
```
