@echo off
title Collaborative Code Editor - Setup
color 0A
echo ========================================
echo   Collaborative Code Editor Setup
echo ========================================
echo.

:: Check Java
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java not found!
    echo Please install Java 17 from: https://adoptium.net/
    pause
    exit /b 1
)

echo [OK] Java found
echo.

:: Create Maven wrapper directory
echo [SETUP] Setting up Maven Wrapper...
mkdir ".mvn\wrapper" 2>nul

:: Download Maven Wrapper JAR
set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
set WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo Downloading maven-wrapper.jar...
    powershell -NoProfile -Command "& {Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '.mvn\wrapper\maven-wrapper.jar'}"
)
echo [OK] Maven Wrapper ready

:: Build project
echo.
echo [BUILD] Building project...
call mvnw.cmd clean package -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   [SUCCESS] Build Complete!
    echo ========================================
    echo.
    echo Run server:  java -jar target\CollaborativeEditor.jar server 5000
    echo Run client:  java -jar target\CollaborativeEditor.jar client [IP] 5000 [username]
    echo.
    echo Web UI: http://localhost:5100
    echo.
    pause
) else (
    echo.
    echo [ERROR] Build failed. Check output above.
    pause
    exit /b 1
)
