@echo off
setlocal
cd /d "%~dp0"
call build.cmd
if errorlevel 1 exit /b 1
java -cp "lib/*;out" CollegeAuthTests
if errorlevel 1 exit /b 1
java -cp "lib/*;out" AcademicPolicyTests
if errorlevel 1 exit /b 1
where node >nul 2>nul
if errorlevel 1 (
 echo Optional browser-script checks skipped: Node.js is not installed.
 exit /b 0
)
node tests/frontend-test.js
exit /b %errorlevel%
