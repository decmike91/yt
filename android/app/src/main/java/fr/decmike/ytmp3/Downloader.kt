package fr.decmike.ytmp3

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** État affiché par l'écran. */
data class UiState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val progress: Int = 0,
    val status: String = "Initialisation du moteur…",
    val log: String = "",
)

/**
 * Singleton : le téléchargement continue si l'écran tourne ou si l'appli passe en arrière-plan.
 * yt-dlp récupère directement le flux audio : aucune publicité n'est lue ni téléchargée.
 */
object Downloader {
    private const val PROCESS_ID = "ytmp3"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var initJob: Job? = null

    fun init(context: Context) {
        if (initJob != null) return
        val app = context.applicationContext
        initJob = scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                _state.update { it.copy(status = "Mise à jour de yt-dlp…") }
                // YouTube change souvent : on garde yt-dlp à jour automatiquement.
                runCatching { YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE) }
                    .onFailure { e -> appendLog("Mise à jour impossible : ${e.message}") }
                val version = runCatching { YoutubeDL.getInstance().version(app) }.getOrNull()
                _state.update {
                    it.copy(ready = true, status = "Prêt. Collez ou partagez un lien YouTube.")
                }
                appendLog("yt-dlp ${version ?: "?"} prêt.")
            } catch (e: Exception) {
                _state.update { it.copy(status = "Erreur d'initialisation : ${e.message}") }
            }
        }
    }

    fun download(context: Context, url: String, sponsorBlock: Boolean) {
        if (_state.value.busy || !_state.value.ready) return
        val app = context.applicationContext
        _state.update { it.copy(busy = true, progress = 0, status = "Analyse du lien…", log = "") }
        scope.launch {
            val tmp = File(app.cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
            try {
                val req = YoutubeDLRequest(url).apply {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--no-playlist")
                    addOption("--embed-metadata")
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")
                    addOption("--windows-filenames")
                    if (sponsorBlock) addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction")
                    addOption("-o", "${tmp.absolutePath}/%(title)s.%(ext)s")
                }
                YoutubeDL.getInstance().execute(req, PROCESS_ID) { progress, _, line ->
                    val p = progress.toInt().coerceIn(0, 100)
                    val status = when {
                        line.contains("[ExtractAudio]") -> "Conversion en MP3…"
                        line.contains("[SponsorBlock]") || line.contains("[ModifyChapters]") ->
                            "Suppression des segments sponsorisés…"
                        p > 0 -> "Téléchargement… $p %"
                        else -> _state.value.status
                    }
                    _state.update { it.copy(progress = p, status = status) }
                    if (line.isNotBlank()) appendLog(line)
                }
                val mp3s = tmp.listFiles { f -> f.extension.equals("mp3", true) }.orEmpty()
                if (mp3s.isEmpty()) error("aucun fichier MP3 produit")
                val names = mp3s.map { saveToMusic(app, it) }
                _state.update {
                    it.copy(
                        busy = false, progress = 100,
                        status = "✔ Enregistré dans Musique/YT :\n" + names.joinToString("\n"),
                    )
                }
            } catch (e: Exception) {
                val msg = if (e is YoutubeDL.CanceledException) "Annulé." else "✖ Échec : ${e.message?.lines()?.lastOrNull { l -> l.isNotBlank() } ?: e}"
                _state.update { it.copy(busy = false, status = msg) }
                appendLog(e.message ?: e.toString())
            } finally {
                tmp.deleteRecursively()
            }
        }
    }

    fun cancel() {
        runCatching { YoutubeDL.getInstance().destroyProcessById(PROCESS_ID) }
    }

    private fun appendLog(line: String) {
        _state.update { s ->
            val lines = (s.log + "\n" + line).trim().lines()
            s.copy(log = lines.takeLast(60).joinToString("\n"))
        }
    }

    /** Copie le MP3 dans le dossier public Musique/YT et le rend visible des lecteurs audio. */
    private fun saveToMusic(context: Context, file: File): String {
        val name = file.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/YT")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("impossible de créer $name")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "YT")
            dir.mkdirs()
            val dest = File(dir, name)
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("audio/mpeg"), null)
        }
        return name
    }
}
