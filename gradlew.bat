@echo off
setlocal

set "ANDROID_HOME=D:\2026\codexwork\AndroidSDK"
set "ANDROID_SDK_ROOT=D:\2026\codexwork\AndroidSDK"
set "GRADLE_USER_HOME=D:\2026\codexwork\.gradle"

for /f "delims=" %%G in ('dir /s /b "D:\2026\codexwork\.gradle\wrapper\dists\gradle-8.7-bin\gradle.bat" 2^>nul') do (
  set "GRADLE_BIN=%%G"
  goto :found_gradle
)

echo Gradle 8.7 was not found under D:\2026\codexwork\.gradle\wrapper\dists.
exit /b 1

:found_gradle
call "%GRADLE_BIN%" %*
