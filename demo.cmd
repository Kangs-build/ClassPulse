@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
 echo Install Java 17 or newer, then run demo.cmd again.
 pause
 exit /b 1
)
java -Dclasspulse.demo=true -Dclasspulse.db=data/college-demo.db -cp "lib/*;out" DemoSetup
if errorlevel 1 (
 pause
 exit /b 1
)
echo Open http://127.0.0.1:8080 after the server starts.
echo Administrator: admin@college.example / AdminDemo123!
echo Activate student1@college.example using the demo inbox code and choose a ClassPulse password.
echo Approve teacher@college.example in the administrator dashboard before teacher activation.
echo No real email is sent. Press Ctrl+C to stop.
java -Dclasspulse.demo=true -Dclasspulse.db=data/college-demo.db -cp "lib/*;out" ClassPulseServer
if errorlevel 1 pause
