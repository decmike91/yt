# YT MP3 🎵

Télécharge le son d'une vidéo YouTube en **MP3** à partir de son URL, **sans publicité** :
l'appli récupère directement le flux audio (via [yt-dlp](https://github.com/yt-dlp/yt-dlp)),
les pubs YouTube ne sont donc jamais lues ni téléchargées. En option (activée par défaut),
les **segments sponsorisés** intégrés dans la vidéo sont retirés grâce à [SponsorBlock](https://sponsor.ajay.app).

MP3 en qualité maximale (VBR 0), avec titre, artiste et pochette intégrés.

| | Android | PC (Windows / Linux / macOS) |
|---|---|---|
| Dossier | `Musique/YT` | `~/Music/YT` (modifiable) |
| Code | [`android/`](android) (Kotlin) | [`pc/yt_mp3.py`](pc/yt_mp3.py) (Python) |

## 📥 Téléchargement

Les fichiers prêts à l'emploi sont compilés automatiquement à chaque modification :
👉 **[Releases → latest](../../releases/tag/latest)**

- `YT-MP3-android-arm64.apk` : pour la plupart des téléphones Android (8.0+)
- `YT-MP3-android-universel.apk` : si le premier ne s'installe pas
- `YT-MP3-windows.exe` : Windows, rien à installer

## 📱 Android

1. Téléchargez l'APK depuis le téléphone et ouvrez-le (autorisez « Installer des applis inconnues »).
2. Dans l'appli YouTube : **Partager → YT MP3**, le téléchargement démarre tout seul.
   Ou copiez l'URL, ouvrez YT MP3, **Coller** puis **Télécharger en MP3**.
3. Les MP3 apparaissent dans `Musique/YT` et dans votre lecteur audio.

Au démarrage, l'appli met yt-dlp à jour automatiquement, ce qui évite les pannes quand YouTube change.

## 💻 PC (Python)

Prérequis : Python 3.10+.

```bash
cd pc
pip install -r requirements.txt   # yt-dlp, ffmpeg et Deno sont installés par pip
python yt_mp3.py                  # interface graphique
python yt_mp3.py URL [URL ...] -o ~/Musique   # ligne de commande
```

Sous Windows, double-cliquez sur `pc/lancer.bat` ; sous Linux/macOS, `pc/lancer.sh`.

Option `--no-sponsorblock` pour garder la vidéo complète.

**Si YouTube ne marche plus**, mettez à jour yt-dlp : `pip install -U "yt-dlp[default]"`.

## 🛠 Compiler soi-même

- **Android** : `cd android && ./gradlew assembleRelease` (JDK 17 + SDK Android), ou ouvrez `android/` dans Android Studio.
- **Windows .exe** : voir `.github/workflows/build.yml` (PyInstaller).

La clé de signature `android/app/ytmp3.keystore` est volontairement dans le dépôt pour que chaque
nouvelle version s'installe par-dessus l'ancienne. Elle n'est destinée qu'à un usage personnel.

## ⚖️ Usage

Pour un usage personnel, sur des contenus que vous avez le droit de télécharger.
Les conditions d'utilisation de YouTube interdisent en principe le téléchargement hors des fonctions prévues.
