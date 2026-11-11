#!/usr/bin/env python3
"""
YT MP3 - Télécharge le son d'une vidéo YouTube en MP3, sans publicité.

Utilisation :
    python yt_mp3.py                       -> ouvre l'interface graphique
    python yt_mp3.py URL [URL ...] [-o DOSSIER] [--no-sponsorblock]
"""
from __future__ import annotations

import argparse
import os
import queue
import shutil
import sys
import threading
from pathlib import Path

try:
    import yt_dlp
except ImportError:  # pragma: no cover
    print("Le module yt-dlp est manquant. Lancez : pip install -r requirements.txt")
    sys.exit(1)

DEFAULT_DIR = Path.home() / "Music" / "YT"


def find_ffmpeg() -> str | None:
    """ffmpeg du système si présent, sinon celui fourni par imageio-ffmpeg."""
    path = shutil.which("ffmpeg")
    if path:
        return path
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return None


def find_deno() -> str | None:
    """Deno (moteur JavaScript) : nécessaire à yt-dlp pour YouTube depuis fin 2025."""
    path = shutil.which("deno")
    if path:
        return path
    bundled = Path(getattr(sys, "_MEIPASS", "")) / ("deno.exe" if os.name == "nt" else "deno")
    if getattr(sys, "_MEIPASS", None) and bundled.is_file():  # exécutable PyInstaller
        return str(bundled)
    try:
        import deno

        return deno.find_deno_bin()
    except Exception:
        return None


def build_options(out_dir: Path, sponsorblock: bool, hook=None, logger=None) -> dict:
    opts: dict = {
        "format": "bestaudio/best",
        "outtmpl": str(out_dir / "%(title)s.%(ext)s"),
        "noplaylist": True,
        "windowsfilenames": True,
        "writethumbnail": True,
        "quiet": True,
        "no_warnings": True,
        "postprocessors": [],
    }
    if sponsorblock:
        # Supprime les segments sponsorisés / autopromo intégrés dans la vidéo
        opts["postprocessors"] += [
            {"key": "SponsorBlock", "categories": ["sponsor", "selfpromo", "interaction"]},
            {"key": "ModifyChapters", "remove_sponsor_segments": ["sponsor", "selfpromo", "interaction"]},
        ]
    opts["postprocessors"] += [
        {"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "0"},
        {"key": "FFmpegMetadata", "add_metadata": True},
        {"key": "FFmpegThumbnailsConvertor", "format": "jpg", "when": "before_dl"},
        {"key": "EmbedThumbnail"},
    ]
    ffmpeg = find_ffmpeg()
    if ffmpeg:
        opts["ffmpeg_location"] = ffmpeg
    deno_bin = find_deno()
    if deno_bin:
        opts["js_runtimes"] = {"deno": {"path": deno_bin}}
    if hook:
        opts["progress_hooks"] = [hook]
    if logger:
        opts["logger"] = logger
    return opts


def download(urls: list[str], out_dir: Path, sponsorblock: bool = True, hook=None, logger=None) -> int:
    out_dir.mkdir(parents=True, exist_ok=True)
    with yt_dlp.YoutubeDL(build_options(out_dir, sponsorblock, hook, logger)) as ydl:
        return ydl.download(urls)


# --------------------------------------------------------------------------- CLI
def cli(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description="Télécharge le son YouTube en MP3")
    p.add_argument("urls", nargs="+", help="URL(s) YouTube")
    p.add_argument("-o", "--output", default=str(DEFAULT_DIR), help=f"dossier (défaut : {DEFAULT_DIR})")
    p.add_argument("--no-sponsorblock", action="store_true", help="ne pas retirer les segments sponsorisés")
    a = p.parse_args(argv)

    def hook(d):
        if d["status"] == "downloading":
            print(f"\r  {d.get('_percent_str', '').strip()}  {d.get('_speed_str', '').strip()}   ", end="", flush=True)
        elif d["status"] == "finished":
            print("\n  Conversion en MP3...")

    if not find_ffmpeg():
        print("ffmpeg introuvable : pip install imageio-ffmpeg (ou installez ffmpeg).")
        return 1
    out = Path(a.output).expanduser()
    try:
        code = download(a.urls, out, not a.no_sponsorblock, hook)
    except yt_dlp.utils.DownloadError as e:
        print(f"\nErreur : {e}")
        return 1
    print(f"Terminé -> {out}" if code == 0 else "Des erreurs sont survenues.")
    return code


# --------------------------------------------------------------------------- GUI
def gui() -> None:
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    root = tk.Tk()
    root.title("YT MP3 - Téléchargeur audio YouTube")
    root.geometry("620x380")
    root.minsize(500, 340)

    events: "queue.Queue[tuple]" = queue.Queue()
    url_var = tk.StringVar()
    dir_var = tk.StringVar(value=str(DEFAULT_DIR))
    sb_var = tk.BooleanVar(value=True)
    status_var = tk.StringVar(value="Collez une URL YouTube puis cliquez sur Télécharger.")

    frm = ttk.Frame(root, padding=12)
    frm.pack(fill="both", expand=True)
    frm.columnconfigure(1, weight=1)

    ttk.Label(frm, text="URL :").grid(row=0, column=0, sticky="w")
    url_entry = ttk.Entry(frm, textvariable=url_var)
    url_entry.grid(row=0, column=1, sticky="ew", padx=6)

    def paste():
        try:
            url_var.set(root.clipboard_get().strip())
        except tk.TclError:
            pass

    ttk.Button(frm, text="Coller", command=paste).grid(row=0, column=2)

    ttk.Label(frm, text="Dossier :").grid(row=1, column=0, sticky="w", pady=8)
    ttk.Entry(frm, textvariable=dir_var).grid(row=1, column=1, sticky="ew", padx=6)

    def choose():
        d = filedialog.askdirectory(initialdir=dir_var.get())
        if d:
            dir_var.set(d)

    ttk.Button(frm, text="Parcourir…", command=choose).grid(row=1, column=2)

    ttk.Checkbutton(frm, text="Retirer les segments sponsorisés (SponsorBlock)", variable=sb_var).grid(
        row=2, column=0, columnspan=3, sticky="w"
    )

    dl_btn = ttk.Button(frm, text="Télécharger en MP3")
    dl_btn.grid(row=3, column=0, columnspan=3, pady=10, sticky="ew")

    bar = ttk.Progressbar(frm, maximum=100)
    bar.grid(row=4, column=0, columnspan=3, sticky="ew")
    ttk.Label(frm, textvariable=status_var, wraplength=580).grid(row=5, column=0, columnspan=3, sticky="w", pady=6)

    log = tk.Text(frm, height=7, state="disabled", wrap="word")
    log.grid(row=6, column=0, columnspan=3, sticky="nsew")
    frm.rowconfigure(6, weight=1)

    class Logger:
        def debug(self, msg):
            if not msg.startswith("[debug]"):
                events.put(("log", msg))

        def info(self, msg):
            events.put(("log", msg))

        def warning(self, msg):
            events.put(("log", "⚠ " + msg))

        def error(self, msg):
            events.put(("log", "✖ " + msg))

    def hook(d):
        if d["status"] == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate")
            pct = d["downloaded_bytes"] * 100 / total if total else 0
            events.put(("progress", pct, f"Téléchargement… {pct:.0f} %"))
        elif d["status"] == "finished":
            events.put(("progress", 100, "Conversion en MP3…"))

    def worker(url, out, sb):
        try:
            code = download([url], out, sb, hook, Logger())
            events.put(("done", code == 0, str(out)))
        except Exception as e:  # noqa: BLE001
            events.put(("done", False, str(e)))

    def start():
        url = url_var.get().strip()
        if not url:
            messagebox.showwarning("YT MP3", "Collez d'abord une URL YouTube.")
            return
        if not find_ffmpeg():
            messagebox.showerror("YT MP3", "ffmpeg introuvable.\npip install imageio-ffmpeg")
            return
        dl_btn.state(["disabled"])
        bar["value"] = 0
        status_var.set("Démarrage…")
        threading.Thread(target=worker, args=(url, Path(dir_var.get()).expanduser(), sb_var.get()), daemon=True).start()

    dl_btn.configure(command=start)
    url_entry.bind("<Return>", lambda _e: start())

    def poll():
        try:
            while True:
                ev = events.get_nowait()
                if ev[0] == "progress":
                    bar["value"] = ev[1]
                    status_var.set(ev[2])
                elif ev[0] == "log":
                    log.configure(state="normal")
                    log.insert("end", ev[1] + "\n")
                    log.see("end")
                    log.configure(state="disabled")
                elif ev[0] == "done":
                    dl_btn.state(["!disabled"])
                    if ev[1]:
                        bar["value"] = 100
                        status_var.set(f"✔ Terminé ! MP3 enregistré dans {ev[2]}")
                        url_var.set("")
                    else:
                        status_var.set(f"✖ Échec : {ev[2]}")
        except queue.Empty:
            pass
        root.after(100, poll)

    poll()
    url_entry.focus()
    root.mainloop()


if __name__ == "__main__":
    if len(sys.argv) > 1:
        sys.exit(cli(sys.argv[1:]))
    gui()
