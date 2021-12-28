@echo off

python -c "import platform; major, minor, patch = platform.python_version_tuple(); print(major);" 2>NUL > tmp_value_file
SET /p PYTHON_VERSION=<tmp_value_file
DEL tmp_value_file

if defined PYTHON_VERSION (
    GOTO hasPython
) else (
    GOTO installPython3
)

:hasPython
echo Python Version: %PYTHON_VERSION%
if %PYTHON_VERSION% == 2 (
    echo Python3 not installed.
    GOTO installPython3
) else (
    GOTO startConfig
)
EXIT /B 0

:installPython3
echo Downloading python3 package...
echo %TEMP%
bitsadmin /transfer DownloadPythonJob /download /priority normal https://www.python.org/ftp/python/3.9.9/python-3.9.9-amd64.exe %TEMP%\python3-amd64.exe
echo Installing python3...
%TEMP%\python3-amd64.exe InstallAllUsers=1 PrependPath=1 Include_test=0
python --version
DEL  %TEMP%\python3-amd64.exe
GOTO startConfig
EXIT /B 0

:startConfig
echo Start config...
python %0\..\configure.py
pause
EXIT /B 0