@echo off
REM Installe les dépendances (la première fois) puis lance l'interface
python -m pip install -q -r "%~dp0requirements.txt"
start "" pythonw "%~dp0yt_mp3.py"
