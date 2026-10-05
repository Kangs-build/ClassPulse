@echo off
setlocal
cd /d "%~dp0"
echo This starts the preserved original database without the demo inbox.
echo Approved roster and real email delivery must be configured for production enrollment.
echo For the working college-login presentation, run demo.cmd instead.
java -cp "lib/*;out" ClassPulseServer
if errorlevel 1 pause
