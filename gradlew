#!/bin/sh
# Gradle 启动脚本（POSIX）
DIR="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -Xmx2048m -Dfile.encoding=UTF-8 -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
