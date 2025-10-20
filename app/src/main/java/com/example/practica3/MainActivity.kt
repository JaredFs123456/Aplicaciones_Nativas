package com.example.practica3

import android.Manifest
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.practica3.data.getThemeName
import com.example.practica3.data.isDark
import com.example.practica3.data.setDark
import com.example.practica3.data.setThemeName
import com.example.practica3.playback.PlayerService
import com.example.practica3.ui.AudioAdapter
import com.example.practica3.ui.AudioEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AudioAdapter

    // Mantener la lista visible para poder calcular el índice
    private var currentEntries = mutableListOf<AudioEntry>()

    // Picker para *múltiples* documentos (playlist simple)
    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult

            // Construimos la lista visible y conservamos permisos
            val entries = uris.map { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                AudioEntry(resolveDisplayName(uri), uri.toString())
            }

            // Actualizamos adapter + referencia local
            currentEntries = entries.toMutableList()
            adapter.replaceAll(entries)

            // Inicia el servicio (compat 24–25) y envía la cola al PlayerService
            startForegroundServiceCompat(Intent(this, PlayerService::class.java))
            startService(Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PLAY_URIS
                putStringArrayListExtra(
                    PlayerService.EXTRA_URIS,
                    ArrayList(entries.map { it.uriString })
                )
            })
        }

    private val reqPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1) Aplica tema guardado ANTES de setContentView
        val themeName = runBlocking { applicationContext.getThemeName() }
        when (themeName) {
            "azul" -> setTheme(R.style.Theme_Practica3_Azul)
            else -> setTheme(R.style.Theme_Practica3_Guinda)
        }
        val dark = runBlocking { applicationContext.isDark() }
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.app_name)

        // 2) Permisos
        pedirPermisos()

        // 3) Botones
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            // Acepta 1 o *varios* audios
            pickAudio.launch(arrayOf("audio/*"))
        }
        findViewById<Button>(R.id.btnToggle).setOnClickListener {
            startService(
                Intent(this, PlayerService::class.java)
                    .setAction(PlayerService.ACTION_TOGGLE)
            )
        }

        // 4) RecyclerView
        recycler = findViewById(R.id.recycler)
        adapter = AudioAdapter(mutableListOf()) { entry ->
            // Buscar índice dentro de la cola actual y saltar por índice
            val index = currentEntries.indexOfFirst { it.uriString == entry.uriString }
            if (index >= 0) {
                startService(Intent(this, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_PLAY_INDEX
                    putExtra(PlayerService.EXTRA_INDEX, index)
                })
            } else {
                // Fallback por si no hay cola (caso raro)
                startService(Intent(this, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_PLAY_URI
                    putExtra(PlayerService.EXTRA_URI, entry.uriString)
                })
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
    }

    private fun pedirPermisos() {
        val faltan = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_AUDIO)
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)

            // Necesario para controlar la linterna
            add(Manifest.permission.CAMERA)
        }
        if (faltan.isNotEmpty()) reqPerms.launch(faltan.toTypedArray())
    }

    // Helper compat para arrancar servicio foreground en API 24–25
    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)   // API 26+
        } else {
            startService(intent)             // API 24–25
        }
    }

    // 5) Menú para cambiar tema y modo
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_theme_guinda -> {
                lifecycleScope.launch {
                    applicationContext.setThemeName("guinda")
                    recreate()
                }
                return true
            }
            R.id.action_theme_azul -> {
                lifecycleScope.launch {
                    applicationContext.setThemeName("azul")
                    recreate()
                }
                return true
            }
            R.id.action_dark_toggle -> {
                lifecycleScope.launch {
                    val now = applicationContext.isDark()
                    applicationContext.setDark(!now)
                    AppCompatDelegate.setDefaultNightMode(
                        if (!now) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // Obtener un nombre legible para cada URI (DISPLAY_NAME)
    private fun resolveDisplayName(uri: Uri): String {
        var name: String? = null
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && it.moveToFirst()) name = it.getString(idx)
        }
        return name ?: (uri.lastPathSegment ?: "Audio")
    }
}
