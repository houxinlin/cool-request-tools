@echo off
rem 轻量构建：编译 src，平铺合并依赖为 fat jar。
rem 产出 build\libs\cool-request-tool-rabbitmq-1.1.0.jar
setlocal
cd /d "%~dp0"

set "JDK_HOME=%JDK_HOME%\bin" 
if not exist "%JDK_HOME%\javac.exe" set "JDK_HOME=C:\Users\USER\.jdks\ms-17.0.19\bin"
if not exist "%JDK_HOME%\javac.exe" (echo JDK not found & exit /b 1)

set "IDEA_DIR=%USERPROFILE%\AppData\Local\Programs\IntelliJ IDEA"
if not exist "%IDEA_DIR%\lib\util.jar" set "IDEA_DIR=%IDEA_DIR%"

set "OUT=build\classes"
set "PKG=build\fat"
if exist "build" rd /s /q build
mkdir "%OUT%" "%PKG%" build\libs 2>nul

rem classpath
set "CP="
for /r "%IDEA_DIR%\lib" %%f in (*.jar) do call set "CP=%%CP%%;%%f"
set "CP=%CP%;%CD%\lib\coolrequest-tool-1.0-SNAPSHOT.jar;%CD%\lib\amqp-client-5.21.0.jar;%CD%\lib\slf4j-api-1.7.36.jar"

set "SRCS="
for /r src\main\java %%f in (*.java) do call set "SRCS=%%SRCS%% %%f"

"%JDK_HOME%\javac" -encoding UTF-8 -source 17 -target 17 -cp "%CP%" -d "%OUT%" %SRCS%
if errorlevel 1 (echo COMPILE FAIL & exit /b 1)
echo COMPILE OK

copy /Y src\main\resources\coolrequest.tool "%OUT%\" >nul
copy /Y src\main\resources\tool.name "%OUT%\" >nul
copy /Y src\main\resources\logo.svg "%OUT%\" >nul

xcopy /S /Y "%OUT%\*" "%PKG%\" >nul
for %%j in ("%CD%\lib\amqp-client-5.21.0.jar" "%CD%\lib\slf4j-api-1.7.36.jar") do unzip -q -o "%%j" -d "%PKG%"
if not exist "%PKG%\com\intellij" ( rem ok ) 
rd /s /q "%PKG%\com\intellij" 2>nul
del /q "%PKG%\dev\coolrequest\tool\CoolToolPanel.class" 2>nul
del /q "%PKG%\dev\coolrequest\tool\ToolPanelFactory.class" 2>nul
del /q "%PKG%\META-INF\MANIFEST.MF" 2>nul

cd "%PKG%"
jar cf "..\libs\cool-request-tool-rabbitmq-1.1.0.jar" .
cd ..\..
echo DONE
dir build\libs