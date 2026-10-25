package br.com.hubnx

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * O WebView de sites externos NUNCA recebe HubNXNative (JavascriptInterface).
 * Apenas o WebView local da home tem acesso aos comandos nativos.
 */
class MainActivity : Activity() {
    companion object {
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/Wilianjcr/ambientes-de-teste/main/hubnx/releases.json"
        private const val CREATE_BACKUP = 2001
        private const val IMPORT_BACKUP = 2002
        private const val MAX_BACKUP_SIZE = 2 * 1024 * 1024
    }
    private lateinit var root: FrameLayout
    private lateinit var home: WebView
    private lateinit var browser: WebView
    private lateinit var menuButton: Button
    private lateinit var drawer: LinearLayout
    private lateinit var shade: View
    private var drawerOpen = false
    private var showingBrowser = false
    private var backupPending: String? = null
    private var fullScreenView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var lastUpdateCheck = 0L
    private var updateInFlight = false
    private var pendingPackageUrl: String? = null
    private var pendingSha256: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 18, 30)
        window.navigationBarColor = Color.rgb(10, 18, 30)
        root = FrameLayout(this)
        setContentView(root)

        home = createWebView()
        home.addJavascriptInterface(Bridge(), "HubNXNative")
        home.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                return if (u.scheme == "file" && u.path == "/android_asset/index.html") false else true
            }
        }
        browser = createWebView()
        browser.visibility = View.GONE
        browser.setOnTouchListener(object : View.OnTouchListener {
            private var edgeSwipe = false
            private var fromX = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        edgeSwipe = event.x < dp(25)
                        fromX = event.x
                        return edgeSwipe
                    }
                    MotionEvent.ACTION_UP -> if (edgeSwipe) {
                        if (event.x - fromX > dp(80)) openDrawer()
                        edgeSwipe = false
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> edgeSwipe = false
                }
                return edgeSwipe
            }
        })
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !allowedSiteUrl(request.url.toString())
            }
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !allowedSiteUrl(url)
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullScreenView != null) { callback.onCustomViewHidden(); return }
                fullScreenView = view
                fullScreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                menuButton.bringToFront()
            }
            override fun onHideCustomView() { closeFullscreen() }
        }
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        root.addView(browser, FrameLayout.LayoutParams(-1, -1))
        home.loadUrl("file:///android_asset/index.html")

        shade = View(this).apply {
            setBackgroundColor(0xb9000714.toInt())
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 29, 46))
            visibility = View.GONE
            setPadding(dp(12), dp(26), dp(12), dp(12))
        }
        val navWidth = resources.displayMetrics.widthPixels.coerceAtMost(dp(340))
        root.addView(drawer, FrameLayout.LayoutParams(navWidth, -1, Gravity.START))
        val drawerTitle = TextView(this).apply {
            text = "HubNX"
            textSize = 23f
            setTextColor(Color.WHITE)
            setPadding(dp(13), dp(4), dp(8), dp(19))
        }
        drawer.addView(drawerTitle)
        addDrawerItem("⌂  Início") { showHome("home") }
        addDrawerItem("▣  Streamings") { showHome("streamings") }
        addDrawerItem("★  Favoritos") { showHome("favorites") }
        addDrawerItem("▤  Todos os sites") { showHome("all") }
        addDrawerItem("＋  Adicionar site") { showHomeAction("add") }
        addDrawerItem("✎  Gerenciar sites") { showHomeAction("manage") }
        addDrawerItem("⇩  Exportar backup") { showHomeAction("backup") }
        addDrawerItem("⇧  Restaurar backup") { showHomeAction("import") }
        addDrawerItem("↻  Atualizações") { checkUpdates(true) }
        addDrawerItem("⚙  Configurações") { showHomeAction("settings") }
        menuButton = Button(this).apply {
            text = "☰"
            contentDescription = "Abrir menu do HubNX"
            textSize = 19f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xe018293d.toInt())
            setOnClickListener { if (drawerOpen) closeDrawer() else openDrawer() }
        }
        root.addView(menuButton, FrameLayout.LayoutParams(dp(54), dp(55), Gravity.TOP or Gravity.START))
        menuButton.visibility = View.GONE
        ui.postDelayed({ checkUpdates(false) }, 1500)
    }

    private fun createWebView(): WebView = WebView(this).apply {
        setBackgroundColor(Color.rgb(9, 14, 24))
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            setSupportMultipleWindows(false)
            builtInZoomControls = true
            displayZoomControls = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + .5f).toInt()
    private fun addDrawerItem(text: String, action: () -> Unit) {
        drawer.addView(Button(this).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(16), dp(8), dp(10), dp(8))
            setOnClickListener { closeDrawer(); action() }
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }
    private fun openDrawer() { drawerOpen = true; shade.visibility = View.VISIBLE; drawer.visibility = View.VISIBLE; drawer.bringToFront(); menuButton.bringToFront() }
    private fun closeDrawer() { drawerOpen = false; drawer.visibility = View.GONE; shade.visibility = View.GONE }
    private fun allowedSiteUrl(url: String): Boolean {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return (u.scheme == "http" || u.scheme == "https") && !u.host.isNullOrBlank() && u.userInfo == null
    }
    private fun openSite(url: String) {
        if (!allowedSiteUrl(url)) { toast("Endereço inválido"); return }
        showingBrowser = true
        browser.visibility = View.VISIBLE
        menuButton.visibility = View.VISIBLE
        menuButton.bringToFront()
        browser.loadUrl(url)
        closeDrawer()
    }
    private fun js(script: String) { home.evaluateJavascript(script, null) }
    private fun showHome(section: String = "home") {
        closeFullscreen()
        showingBrowser = false
        browser.visibility = View.GONE
        menuButton.visibility = View.GONE
        browser.loadUrl("about:blank")
        js("window.HubNX&&window.HubNX.home(" + JSONObject.quote(section) + ")")
    }
    private fun showHomeAction(action: String) {
        showHome("home")
        ui.postDelayed({ js("document.querySelector('[data-action=\"$action\"]')?.click()") }, 120)
    }
    private fun closeFullscreen() {
        fullScreenView?.let { root.removeView(it) }
        fullScreenView = null
        fullScreenCallback?.onCustomViewHidden()
        fullScreenCallback = null
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawerOpen -> closeDrawer()
            fullScreenView != null -> closeFullscreen()
            showingBrowser && browser.canGoBack() -> browser.goBack()
            showingBrowser -> showHome("home")
            else -> super.onBackPressed()
        }
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    /** Chamadas expostas SOMENTE à página local file:///android_asset/index.html. */
    inner class Bridge {
        @JavascriptInterface fun openSite(url: String, name: String) { ui.post { this@MainActivity.openSite(url) } }
        @JavascriptInterface fun exportBackup(json: String) {
            ui.post {
                backupPending = json
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "hubnx-backup.json")
                }, CREATE_BACKUP)
            }
        }
        @JavascriptInterface fun importBackup() {
            ui.post { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }, IMPORT_BACKUP) }
        }
        @JavascriptInterface fun checkUpdates(force: Boolean) { ui.post { this@MainActivity.checkUpdates(force) } }
        @JavascriptInterface fun downloadUpdate(url: String, sha: String) { ui.post { startDownload(url, sha) } }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == CREATE_BACKUP) {
            val text = backupPending ?: return
            worker.execute {
                runCatching { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Arquivo indisponível") }
                    .onSuccess { ui.post { toast("Backup salvo") } }
                    .onFailure { ui.post { toast("Não foi possível salvar backup") } }
            }
        } else if (requestCode == IMPORT_BACKUP) {
            worker.execute {
                runCatching {
                    val dataBytes = contentResolver.openInputStream(uri)?.use { inp ->
                        val buf=ByteArray(16384)
                        val out=ByteArrayOutputStream()
                        while(true) {
                            val n=inp.read(buf)
                            if(n<0)break
                            require(out.size()+n<=MAX_BACKUP_SIZE) { "Backup muito grande" }
                            out.write(buf,0,n)
                        }
                        out.toByteArray()
                    } ?: error("Arquivo indisponível")
                    require(dataBytes.size <= MAX_BACKUP_SIZE) { "Backup muito grande" }
                    String(dataBytes, Charsets.UTF_8)
                }.onSuccess { raw -> ui.post {
                    showHome("home")
                    js("window.HubNX&&window.HubNX.importBackup(" + JSONObject.quote(raw) + ")")
                } }.onFailure { ui.post { toast("Backup inválido ou maior que 2 MB") } }
            }
        }
    }
    private fun currentVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
    private fun versionGreater(remote: String, local: String): Boolean {
        fun parts(s: String) = s.split('.').map { it.toIntOrNull() }
        val r = parts(remote); val l = parts(local)
        if (r.size != 3 || l.size != 3 || r.any { it == null } || l.any { it == null }) return false
        return (0..2).firstNotNullOfOrNull { i -> if (r[i] != l[i]) (r[i] ?: 0) > (l[i] ?: 0) else null } ?: false
    }
    private fun checkUpdates(force: Boolean) {
        val now = System.currentTimeMillis()
        if (updateInFlight || (!force && now-lastUpdateCheck < 6*60*60*1000L)) return
        updateInFlight = true
        lastUpdateCheck = now
        worker.execute {
            val result = runCatching {
                val conn = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply { connectTimeout=9000; readTimeout=9000; requestMethod="GET" }
                try { require(conn.responseCode == 200); JSONObject(conn.inputStream.bufferedReader().use { it.readText() }) } finally { conn.disconnect() }
            }
            ui.post {
                updateInFlight = false
                result.onSuccess { obj ->
                    val info = obj.optJSONObject("platforms")?.optJSONObject("android")
                    val message = obj.toString()
                    js("window.HubNX&&window.HubNX.receiveUpdate(" + "JSON.parse(" + JSONObject.quote(message) + "))")
                    if (info?.optBoolean("published") == true && versionGreater(info.optString("version"), currentVersion())) {
                        val ver = info.optString("version")
                        AlertDialog.Builder(this).setTitle("Nova versão disponível")
                            .setMessage("Sua versão: ${currentVersion()}\nNova versão: $ver\n\n${info.optString("notes", "")}")
                            .setPositiveButton("Atualizar agora") { _,_ -> startDownload(info.optString("url"),info.optString("sha256")) }
                            .setNegativeButton("Agora não", null).show()
                    } else if(force) {
                        toast(if(info?.optBoolean("published")==true) "HubNX está atualizado" else "Ainda não existe APK final publicado")
                    }
                }.onFailure { if(force) toast("Não foi possível verificar atualizações") }
            }
        }
    }
    private fun startDownload(rawUrl: String, expectedSha: String) {
        if (!rawUrl.startsWith("https://") || !rawUrl.substringBefore('?').endsWith(".apk", true) || !expectedSha.matches(Regex("[0-9a-fA-F]{64}"))) {
            toast("Pacote de atualização inválido ou sem verificação SHA-256")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingPackageUrl = rawUrl; pendingSha256 = expectedSha
            toast("Autorize a instalação pelo HubNX e tente novamente")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        toast("Baixando atualização...")
        worker.execute {
            val result = runCatching {
                val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply { connectTimeout=15000; readTimeout=25000; instanceFollowRedirects=true }
                try {
                    require(conn.responseCode in 200..299)
                    require(conn.url.protocol == "https") { "Download inseguro" }
                    val dir=File(cacheDir,"updates").apply { mkdirs() }
                    val tmp=File(dir,"hubnx-download.tmp")
                    val sha=MessageDigest.getInstance("SHA-256")
                    conn.inputStream.use { inp -> tmp.outputStream().use { out ->
                        val chunk=ByteArray(32768)
                        var size=0L
                        while(true) {
                            val n=inp.read(chunk); if(n<0)break
                            size+=n; require(size<=150_000_000L) { "Arquivo muito grande" }
                            sha.update(chunk,0,n);out.write(chunk,0,n)
                        }
                    } }
                    val digest=sha.digest().joinToString("") { "%02x".format(it) }
                    require(digest.equals(expectedSha,true)) { "Assinatura de integridade diferente" }
                    val dest=File(dir,"hubnx-update.apk");if(dest.exists())dest.delete();require(tmp.renameTo(dest));dest
                } finally { conn.disconnect() }
            }
            ui.post {
                result.onSuccess { apk ->
                    val uri = FileProvider.getUriForFile(this,"$packageName.fileprovider",apk)
                    val install=Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri,"application/vnd.android.package-archive")
                        flags=Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { startActivity(install) } catch(_: ActivityNotFoundException) { toast("Instalador Android indisponível") }
                }.onFailure { toast("Falha ao baixar/verificar pacote: ${it.message}") }
            }
        }
    }
    override fun onDestroy() {
        browser.destroy()
        home.destroy()
        worker.shutdownNow()
        super.onDestroy()
    }
}
