package id.sch.smkn2pwk.paskibra

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Layar utama aplikasi.
 *
 * Aplikasi ini membuka website Paskibra yang sudah online di dalam WebView,
 * lalu menambahkan fitur khas aplikasi: splash screen, menu bawah, tarik-untuk-
 * muat-ulang, dan tampilan error ketika internet mati.
 * Website asli TIDAK diubah sama sekali.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // ====== PENGATURAN YANG MUDAH DIUBAH ======
        const val SITE_HOST = "paskibra-smkn2pwk.site.je"
        const val START_URL = "https://$SITE_HOST/"

        // Halaman khusus admin: tidak dibuka di aplikasi (aplikasi hanya untuk pengunjung)
        private val BLOCKED_PATHS = listOf("/login", "/logout", "/admin", "/dashboard")

        // CSS tambahan khusus di aplikasi (menyembunyikan tombol LOGIN admin dari menu web)
        private const val APP_CSS = ".btn-nav-login{display:none!important}"
    }

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var errorView: View
    private lateinit var errorMessage: TextView
    private lateinit var bottomNav: BottomNavigationView

    private var hasError = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()           // splash screen (logo + latar maroon)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        errorMessage = findViewById(R.id.errorMessage)
        bottomNav = findViewById(R.id.bottomNav)

        setupWebView()
        setupBottomNav()
        setupSwipeRefresh()
        setupBackButton()

        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener { loadStartOrReload() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadStartOrReload()
        }
        registerNetworkCallback()
    }

    // ---------------------------------------------------------------- WebView
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true            // website memakai JavaScript (modal, pencarian anggota)
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        // Cookie dibutuhkan agar formulir pendaftaran (token CSRF Laravel) berfungsi
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.setProgressCompat(newProgress, true)
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleUrl(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                hasError = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                swipeRefresh.isRefreshing = false
                if (!hasError) showContent()
                view.evaluateJavascript(injectCssScript(), null)
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) showError(getString(R.string.error_message))
            }

            override fun onReceivedHttpError(
                view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    showError(getString(R.string.error_server_message))
                }
            }
        }
    }

    /** Menentukan tautan dibuka di aplikasi, di aplikasi lain, atau diblokir. */
    private fun handleUrl(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return true

        if (scheme != "http" && scheme != "https") {      // tel:, mailto:, whatsapp:
            openExternal(uri); return true
        }
        if (uri.host == SITE_HOST) {
            val path = uri.path ?: "/"
            if (BLOCKED_PATHS.any { path.startsWith(it) }) {
                Toast.makeText(this, R.string.admin_blocked, Toast.LENGTH_SHORT).show()
                return true
            }
            return false                                   // halaman website sendiri -> buka di WebView
        }
        openExternal(uri)                                  // Instagram, TikTok, dll -> aplikasi/browser
        return true
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun injectCssScript(): String =
        "(function(){if(document.getElementById('app-style'))return;" +
            "var s=document.createElement('style');s.id='app-style';" +
            "s.textContent='$APP_CSS';document.head.appendChild(s);})();"

    // ------------------------------------------------------------- Navigasi
    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profil -> goToSection("profil")
                R.id.nav_berita -> goToSection("berita")
                R.id.nav_galeri -> goToSection("galeri")
                R.id.nav_agenda -> goToSection("agenda")
                R.id.nav_lainnya -> { showMoreMenu(); return@setOnItemSelectedListener false }
            }
            true
        }
        // Mengetuk ulang menu yang sama tetap menggulir ke bagian tersebut
        bottomNav.setOnItemReselectedListener { item ->
            when (item.itemId) {
                R.id.nav_profil -> goToSection("profil")
                R.id.nav_berita -> goToSection("berita")
                R.id.nav_galeri -> goToSection("galeri")
                R.id.nav_agenda -> goToSection("agenda")
            }
        }
    }

    private fun showMoreMenu() {
        PopupMenu(this, bottomNav).apply {
            menuInflater.inflate(R.menu.menu_more, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.more_organigram -> goToSection("organigram")
                    R.id.more_anggota -> goToSection("anggota")
                    R.id.more_prestasi -> goToSection("prestasi")
                    R.id.more_tentang -> openTentangKami()
                    R.id.more_daftar -> webView.loadUrl("https://$SITE_HOST/daftar")
                }
                true
            }
            show()
        }
    }

    private fun isOnHomePage(): Boolean {
        val uri = webView.url?.let { Uri.parse(it) } ?: return false
        return uri.host == SITE_HOST && (uri.path.isNullOrEmpty() || uri.path == "/")
    }

    /** Pindah ke bagian (section) di halaman beranda: Profil, Berita, Galeri, dst. */
    private fun goToSection(id: String) {
        if (hasError) { loadStartOrReload(); return }
        if (isOnHomePage()) {
            webView.evaluateJavascript(
                "(function(){var e=document.getElementById('$id');" +
                    "if(e){e.scrollIntoView({behavior:'smooth'});}})();", null
            )
        } else {
            webView.loadUrl("https://$SITE_HOST/#$id")
        }
    }

    /** Membuka jendela "Tentang Kami" yang sudah ada di website. */
    private fun openTentangKami() {
        if (isOnHomePage()) {
            webView.evaluateJavascript(
                "(function(){var m=document.getElementById('modalTentangKami');" +
                    "if(m){m.classList.add('active');}" +
                    "else{location.href='/tentang-kami';}})();", null
            )
        } else {
            webView.loadUrl("https://$SITE_HOST/tentang-kami")
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.maroon)
        // Tarik-untuk-muat-ulang hanya aktif jika halaman sedang di posisi paling atas
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        swipeRefresh.setOnRefreshListener { loadStartOrReload() }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack() && !hasError) webView.goBack() else finish()
            }
        })
    }

    // ------------------------------------------------- Koneksi & tampilan error
    private fun loadStartOrReload() {
        if (!isOnline()) {
            swipeRefresh.isRefreshing = false
            showError(getString(R.string.error_message))
            return
        }
        hasError = false
        val current = webView.url
        if (current != null && current.startsWith("http") && !errorView.isShown) {
            webView.reload()
        } else {
            webView.loadUrl(START_URL)
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Otomatis memuat ulang saat internet kembali menyala. */
    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { if (hasError) loadStartOrReload() }
            }
        }
        connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun showError(message: String) {
        hasError = true
        swipeRefresh.isRefreshing = false
        progressBar.visibility = View.GONE
        errorMessage.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun showContent() {
        errorView.visibility = View.GONE
    }

    // -------------------------------------------------------------- Lifecycle
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }

    override fun onDestroy() {
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        webView.destroy()
        super.onDestroy()
    }
}
