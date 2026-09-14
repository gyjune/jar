@echo off

call "%~dp0\gradlew" assembleRelease --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo Gradle build failed!
    exit /b %ERRORLEVEL%
)

call "%~dp0\jar\genJar.bat" ec
if %ERRORLEVEL% NEQ 0 (
    echo genJar failed!
    exit /b %ERRORLEVEL%
)

pause