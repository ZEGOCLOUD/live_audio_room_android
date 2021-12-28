#!/usr/bin/env bash

installPython3() {
    echo "Downloading python3 package..."
    curl https://www.python.org/ftp/python/3.9.9/python-3.9.9-macos11.pkg --output /tmp/python3.pkg
    echo "Installing python3..."
    sudo installer -pkg /tmp/python3.pkg -target /
    python3 --version
    rm -f /tmp/python3.pkg
}

if ! hash python3; then
    if ! hash python; then
        echo "Python is not installed"
        installPython3
    else
        # check if python point to python3
        if [ $(python -c 'import platform; major, minor, patch = platform.python_version_tuple(); print(major);') -eq 2 ]
        then
            echo "Python3 is not installed"
            installPython3
        else
            alias python3=python
        fi
    fi
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PY_SCRIPT=$SCRIPT_DIR/configure.py
python3 $PY_SCRIPT