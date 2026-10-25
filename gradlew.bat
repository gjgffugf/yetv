@rem Gradle 启动脚本 (Windows)
@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java
)
"%JAVACMD%" -Xmx1024m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
