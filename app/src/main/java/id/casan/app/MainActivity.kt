package id.casan.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import id.casan.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var hadError = false
    private val selectedBrands = linkedSetOf<String>()
    private val favoriteBrands = linkedSetOf<String>()
    // true setelah halaman dimuat penuh, sampai filter favorit sudah diterapkan
    private var pendingFavorites = true
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupSwipeRefresh()
        setupBackHandling()
        setupBrandBar()

        binding.retryButton.setOnClickListener { loadSite() }

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            loadSite(intent?.data?.toString())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() = with(binding.webView) {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@with, true)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                val host = uri.host.orEmpty()
                val internal = uri.scheme == "https" &&
                    (host == HOST || host.endsWith(".$HOST"))
                if (internal) return false

                // Tautan eksternal / tel: / mailto: / wa.me dibuka di aplikasi lain
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (e: ActivityNotFoundException) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                hadError = false
                binding.progressBar.visibility = View.VISIBLE
                // Halaman dimuat ulang -> filter situs kembali kosong
                clearBrandSelection()
                pendingFavorites = true
            }

            // Dipanggil juga saat situs (SPA) pindah halaman tanpa muat ulang
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                updateBrandBarVisibility(url)
                applyFavoritesIfNeeded(url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.offlineView.visibility = if (hadError) View.VISIBLE else View.GONE
                binding.swipeRefresh.visibility = if (hadError) View.GONE else View.VISIBLE
                updateBrandBarVisibility(url)
                applyFavoritesIfNeeded(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // Hanya anggap gagal bila halaman utama yang error
                if (request.isForMainFrame) hadError = true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooserLauncher.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback = null
                    false
                }
            }
        }

        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.downloading, fileName),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.download_failed, Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    // ---- Filter brand di bawah ------------------------------------------------

    private fun setupBrandBar() {
        favoriteBrands.clear()
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.let { saved ->
            // hanya brand yang masih ada di daftar BRANDS
            favoriteBrands.addAll(saved.filter { s -> BRANDS.any { it.name == s } })
        }
        val inflater = LayoutInflater.from(this)
        BRANDS.forEach { brand ->
            val item = inflater.inflate(R.layout.item_brand, binding.brandContainer, false)
            item.findViewById<ImageView>(R.id.brandLogo).setImageResource(brand.logo)
            item.findViewById<TextView>(R.id.brandName).text = brand.name
            item.contentDescription = brand.name
            item.tag = brand.name
            item.setOnClickListener { toggleBrand(brand.name) }
            // Tekan lama = tandai / hapus favorit
            item.setOnLongClickListener {
                toggleFavorite(brand.name)
                true
            }
            binding.brandContainer.addView(item)
        }
        refreshBrandSelectionUi()
    }

    private fun toggleBrand(name: String) {
        if (!selectedBrands.add(name)) selectedBrands.remove(name)
        refreshBrandSelectionUi()
        runBrandFilter(0) {
            // Tombol filter situs tidak ditemukan -> batalkan pilihan
            selectedBrands.remove(name)
            refreshBrandSelectionUi()
            Toast.makeText(this, R.string.filter_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    /** Jalankan skrip filter; bila filter situs belum tampil, coba lagi tiap 0,7 detik. */
    private fun runBrandFilter(retriesLeft: Int, onFail: () -> Unit) {
        binding.webView.evaluateJavascript(BrandFilterScript.build(selectedBrands)) { result ->
            if (result?.contains("nofilter") == true) {
                if (retriesLeft > 0) {
                    binding.webView.postDelayed({ runBrandFilter(retriesLeft - 1, onFail) }, 700)
                } else {
                    onFail()
                }
            }
        }
    }

    private fun toggleFavorite(name: String) {
        val added = favoriteBrands.add(name)
        if (!added) favoriteBrands.remove(name)
        prefs.edit().putStringSet(KEY_FAVORITES, favoriteBrands.toSet()).apply()
        refreshBrandSelectionUi()
        val msg = if (added) R.string.favorite_added else R.string.favorite_removed
        Toast.makeText(this, getString(msg, name), Toast.LENGTH_SHORT).show()
    }

    /** Saat aplikasi dibuka (atau di-refresh), otomatis tampilkan brand favorit. */
    private fun applyFavoritesIfNeeded(url: String?) {
        if (!pendingFavorites || hadError || !isHomeUrl(url)) return
        pendingFavorites = false
        if (favoriteBrands.isEmpty()) return
        selectedBrands.clear()
        selectedBrands.addAll(BRANDS.map { it.name }.filter { it in favoriteBrands })
        refreshBrandSelectionUi()
        runBrandFilter(12) {
            selectedBrands.clear()
            refreshBrandSelectionUi()
        }
    }

    private fun refreshBrandSelectionUi() {
        for (i in 0 until binding.brandContainer.childCount) {
            val v = binding.brandContainer.getChildAt(i)
            val name = v.tag as String
            val selected = name in selectedBrands
            v.findViewById<ImageView>(R.id.brandLogo).isSelected = selected
            v.findViewById<TextView>(R.id.brandName).isSelected = selected
            v.findViewById<ImageView>(R.id.brandStar).visibility =
                if (name in favoriteBrands) View.VISIBLE else View.GONE
        }
    }

    private fun clearBrandSelection() {
        if (selectedBrands.isEmpty()) return
        selectedBrands.clear()
        refreshBrandSelectionUi()
    }

    /** Bar hanya tampil di beranda (tempat daftar stasiun), bukan di Order/Profile dll. */
    private fun updateBrandBarVisibility(url: String?) {
        binding.brandBar.visibility =
            if (isHomeUrl(url) && !hadError) View.VISIBLE else View.GONE
    }

    private fun isHomeUrl(url: String?): Boolean {
        val path = url?.let { Uri.parse(it).path }.orEmpty().trimEnd('/')
        return path.isEmpty() || path in HOME_PATHS
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { binding.webView.reload() }
        // Cegah refresh saat konten web belum di posisi paling atas
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.webView.scrollY > 0
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadSite(url: String? = null) {
        hadError = false
        binding.offlineView.visibility = View.GONE
        binding.swipeRefresh.visibility = View.VISIBLE
        if (!isOnline()) {
            // Tetap coba muat: WebView dapat memakai cache bila ada
            binding.webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        } else {
            binding.webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        }
        binding.webView.loadUrl(url ?: START_URL)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { loadSite(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onPause() {
        binding.webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val HOST = "casan.id"
        private const val START_URL = "https://casan.id/"
        private const val PREFS = "casan_prefs"
        private const val KEY_FAVORITES = "favorite_brands"

        // Path yang dianggap beranda; sesuaikan bila rute beranda situs berbeda
        private val HOME_PATHS = setOf("/home", "/beranda")
    }
}
