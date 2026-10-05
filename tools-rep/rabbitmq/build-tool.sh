#!/usr/bin/env bash
# 轻量构建：用本机 IDEA lib 作编译 classpath，编译 src，再平铺合并第三方依赖为单 fat jar。
# 产出：build/libs/cool-request-tool-rabbitmq-1.1.0.jar
set -euo pipefail
cd "$(dirname "$0")"

# 编译需能读 IDEA 2026.2 平台类(class 版本 69)，用 IDEA 自带 JDK25(jbr)；
# 输出 --release 17 保证二进制兼容运行时(宿主插件 Java 17)。
JDK_HOME="${JDK_HOME:-$USERPROFILE/AppData/Local/Programs/IntelliJ IDEA/jbr}"
JAVAC="$JDK_HOME/bin/javac"
JAVA="$JDK_HOME/bin/java"

IDEA_DIR="${IDEA_DIR:-$USERPROFILE/AppData/Local/Programs/IntelliJ IDEA}"

OUT=build/classes
PKG=build/fat
rm -rf "$OUT" "$PKG" build/libs
mkdir -p "$OUT" "$PKG" build/libs

# --- 编译 classpath: 字面通配 IDE lib/*（javac 自展开，避免超长）+ SDK + amqp + slf4j ---
IDEA_LIB=$(cygpath -m "$IDEA_DIR/lib")
CP="$IDEA_LIB/*;$(cygpath -m "$(pwd)/lib/coolrequest-tool-1.0-SNAPSHOT.jar");$(cygpath -m "$(pwd)/lib/amqp-client-5.21.0.jar");$(cygpath -m "$(pwd)/lib/slf4j-api-1.7.36.jar");$(cygpath -m "$IDEA_DIR/plugins/json/lib/intellij.json.jar")"

"$JAVAC" --release 17 -classpath "$CP" -d "$OUT" src/main/java/dev/coolrequest/tool/rabbitmq/*.java

echo "=== 编译完成，类数量 ==="
find "$OUT" -name '*.class' | wc -l

# --- 资源(三个契约文件)进 classes ---
cp src/main/resources/coolrequest.tool src/main/resources/tool.name src/main/resources/logo.svg "$OUT/"

# --- fat jar: 平铺第三方依赖 + 自身类/资源 ---
cp -r "$OUT/." "$PKG/"
for j in "$(pwd)"/lib/amqp-client-5.21.0.jar "$(pwd)"/lib/slf4j-api-1.7.36.jar \
         "$(pwd)"/lib/jackson-core-2.15.0.jar "$(pwd)"/lib/jackson-databind-2.15.0.jar "$(pwd)"/lib/jackson-annotations-2.20.jar; do
  unzip -q -o "$j" -d "$PKG"
done

# 剔除宿主/SDK 平台类(宿主父加载器已提供)与第三方自带的 IntelliJ 无关内容碰撞
rm -rf "$PKG"/com/intellij "$PKG"/dev/coolrequest/tool/CoolToolPanel.class "$PKG"/dev/coolrequest/tool/ToolPanelFactory.class "$PKG"/META-INF/MANIFEST.MF

# 打包
cd "$PKG"
"$JAVA" -jar "$JDK_HOME"/lib/jrt-fs.jar >/dev/null 2>&1 || true  # no-op
jar cf "$(pwd)/../libs/cool-request-tool-rabbitmq-1.1.0.jar" .
cd ../..

echo ""
echo "=== 产物 ==="
ls -la build/libs/
echo "=== 根目录三资源校验 ==="
unzip -l build/libs/cool-request-tool-rabbitmq-1.1.0.jar | grep -E "coolrequest.tool|tool.name|logo.svg"
echo "=== 关键类 ==="
unzip -l build/libs/cool-request-tool-rabbitmq-1.1.0.jar | grep -E "rabbitmq/(RabbitMQToolFactory|RabbitMQMainPanel|ConsumerPanel)\.class|com/rabbitmq/client/ConnectionFactory.class"