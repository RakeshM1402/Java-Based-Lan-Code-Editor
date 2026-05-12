@REM Maven Wrapper startup script for Windows
@REM Generated for Collaborative Code Editor

@echo off
setlocal enabledelayedexpansion

set MAVEN_PROJECTBASEDIR=%~dp0
set WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

if exist "%WRAPPER_JAR%" goto run
echo Downloading Maven Wrapper...
powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"

:run
"%JAVA_HOME%\bin\java.exe" ^
    -classpath "%WRAPPER_JAR%" ^
    org.apache.maven.wrapper.MavenWrapperMain %*