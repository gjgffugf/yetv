#!/usr/bin/env bash
# 本地编译入口（Termux / Linux / macOS 通用）
#
# ⚠️ 关键：必须用 ./gradlew（wrapper 锁定的 Gradle 8.2），
#    不能直接敲 `gradle` —— 系统安装的 Gradle 版本对不上会报
#    `HasConvention` / `buildFlowServiceProperty` 这类插件加载错误。
#
# 用法：
#   bash build.sh          编译 Debug APK
#   bash build.sh release  编译 Release APK
set -e
cd "$(dirname "$0")"

echo "── 环境检查 ──"

# 1. JDK：AGP 8.1.4 要求 JDK 17
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="java"
fi
if ! command -v "$JAVA" >/dev/null 2>&1 && [ ! -x "$JAVA" ]; then
  echo "✗ 找不到 java，请先安装 JDK 17 并设置 JAVA_HOME"
  exit 1
fi
JV=$("$JAVA" -version 2>&1 | head -1)
echo "Java: $JV"
MAJ=$(echo "$JV" | sed -E 's/.*version "([0-9]+).*/\1/')
if [ "$MAJ" != "17" ] && [ "$MAJ" != "18" ] && [ "$MAJ" != "21" ]; then
  echo "⚠ 当前 JDK 主版本 $MAJ，AGP 8.1.4 要求 17（18/21 也可）"
fi

# 2. Android SDK
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "⚠ 未设置 ANDROID_HOME / ANDROID_SDK_ROOT，AGP 会找不到 SDK"
else
  echo "SDK: ${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
fi

# 3. Gradle wrapper 版本
WRAP=$(grep distributionUrl gradle/wrapper/gradle-wrapper.properties | sed -E 's#.*gradle-([0-9.]+)-.*#\1#')
echo "Gradle(wrapper): $WRAP"

# 4. 如果 PATH 里有系统 gradle，提醒别用它
if command -v gradle >/dev/null 2>&1; then
  SYSV=$(gradle --version 2>/dev/null | grep '^Gradle' | awk '{print $2}')
  echo "⚠ 检测到系统 gradle $SYSV —— 请不要直接用它，本脚本一律走 ./gradlew"
fi

echo "── 检查 gradlew 可执行性 ──"

CUR="$(pwd)"
# Android 的共享存储（/sdcard、/storage）挂载时带 noexec，
# 这种分区上 chmod +x 也没用 —— 必须把项目放到 Termux 家目录下。
case "$CUR" in
  /sdcard/*|/storage/*|/mnt/sdcard/*)
    echo "✗ 项目在共享存储上：$CUR"
    echo "  Android 的 /sdcard 挂载时带 noexec，任何脚本都无法执行。"
    echo "  请把项目移到家目录后重试："
    echo "    cp -r "$CUR" ~/yetv && cd ~/yetv && bash build.sh"
    exit 1
    ;;
esac

# 先尝试补执行位
if [ ! -x gradlew ]; then
  chmod +x gradlew 2>/dev/null || true
fi

# 仍然不可执行就退化成 `sh gradlew`（gradlew 本身就是 POSIX sh 脚本，
# 交给 sh 解释不需要执行位，这是绕过 noexec / 权限丢失最稳的办法）
if [ -x gradlew ]; then
  GRADLE_CMD="./gradlew"
  echo "执行方式: ./gradlew"
else
  GRADLE_CMD="sh gradlew"
  echo "执行方式: sh gradlew（gradlew 无执行位，改用 sh 解释）"
fi

# clean 模式：清掉 Gradle 变换缓存（JdkImageTransform 缓存损坏时必做）
if [ "${1:-}" = "clean" ]; then
  echo "── 清理变换缓存 ──"
  rm -rf "${HOME}/.gradle/caches/transforms-2" 2>/dev/null
  rm -rf "${HOME}/.gradle/caches/journal-1" 2>/dev/null
  echo "已清理 ~/.gradle/caches/transforms-2"
  echo "现在重新执行： bash build.sh"
  exit 0
fi

echo "── 开始编译 ──"
TASK="assembleDebug"
[ "${1:-}" = "release" ] && TASK="assembleRelease"
$GRADLE_CMD "$TASK" --stacktrace

echo "── 完成 ──"
find . -name "*.apk" -newermt "-10 minutes" 2>/dev/null | grep -v intermediates || true
