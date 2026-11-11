#!/bin/sh
# Installe les dépendances (la première fois) puis lance l'interface
cd "$(dirname "$0")"
python3 -m pip install -q --user -r requirements.txt
exec python3 yt_mp3.py "$@"
