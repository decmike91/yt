package fr.decmike.ytmp3

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.decmike.ytmp3.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val urlRegex = Regex("""https?://\S+""")

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startDownload() else toast("Autorisation de stockage nécessaire")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        Downloader.init(this)
        handleShare(intent)

        b.pasteButton.setOnClickListener {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            b.urlInput.setText(extractUrl(text) ?: text.trim())
        }
        b.downloadButton.setOnClickListener { startDownload() }
        b.cancelButton.setOnClickListener { Downloader.cancel() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Downloader.state.collect { s ->
                    b.downloadButton.isEnabled = s.ready && !s.busy
                    b.cancelButton.visibility = if (s.busy) View.VISIBLE else View.GONE
                    b.progress.visibility = if (s.busy || !s.ready) View.VISIBLE else View.INVISIBLE
                    b.progress.isIndeterminate = !s.ready || (s.busy && s.progress == 0)
                    if (s.ready) b.progress.setProgressCompat(s.progress, true)
                    b.status.text = s.status
                    b.log.text = s.log
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Lien reçu via « Partager » depuis l'appli YouTube : on le récupère et on lance. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val url = extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()) ?: return
        b.urlInput.setText(url)
        intent.action = null
        lifecycleScope.launch {
            val s = Downloader.state.first { it.ready }
            if (!s.busy && b.urlInput.text.toString() == url) startDownload()
        }
    }

    private fun extractUrl(text: String) = urlRegex.find(text)?.value

    private fun startDownload() {
        val url = b.urlInput.text.toString().trim()
        if (extractUrl(url) == null) {
            toast("Collez une URL YouTube valide")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        Downloader.download(this, url, b.sponsorBlock.isChecked)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
