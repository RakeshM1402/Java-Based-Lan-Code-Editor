@echo off
setlocal

set WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
set PROJECT_DIR=%~dp0
set PROJECT_DIR=%PROJECT_DIR:~0,-1%

if not exist "%WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    powershell -NoProfile -Command "& {Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%WRAPPER_JAR%'}"
)

java -Dmaven.multiModuleProjectDirectory="%PROJECT_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
