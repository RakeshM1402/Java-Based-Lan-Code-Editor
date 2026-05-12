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
call mvnw.cmd clean package -DskipTests >nul 2>&1

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   [SUCCESS] Build Complete!
    echo ========================================
    echo.
    
    :: Get IP using PowerShell and write to temp file
    powershell -NoProfile -Command "(Get-NetIPAddress -InterfaceAlias '*Wi-Fi*' -AddressFamily IPv4).IPAddress" > temp_ip.txt 2>nul
    
    :: Read IP from file
    set /p LOCAL_IP=<temp_ip.txt
    del temp_ip.txt 2>nul
    
    :: Fallback if empty
    if not defined LOCAL_IP (
        powershell -NoProfile -Command "[System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) | Where-Object {$_.AddressFamily -eq 'InterNetwork'} | Select-Object -First 1 -ExpandProperty IPAddressToString" > temp_ip.txt 2>nul
        set /p LOCAL_IP=<temp_ip.txt
        del temp_ip.txt 2>nul
    )
    
    echo Starting server on port 5000...
    echo.
    echo ========================================
    echo.
    echo   OPEN THIS LINK IN YOUR BROWSER:
    echo.
    echo   http://%LOCAL_IP%:5100
    echo.
    echo   Share this link with friends!
    echo.
    echo ========================================
    echo.
    echo Press Ctrl+C to stop the server
    echo.
    
    :: Start server
    java -jar target\CollaborativeEditor.jar server 5000
    
) else (
    echo.
    echo [ERROR] Build failed.
    pause
    exit /b 1
)
