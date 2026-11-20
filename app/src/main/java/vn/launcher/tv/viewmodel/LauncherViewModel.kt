package vn.launcher.tv.viewmodel

import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.app.role.RoleManager
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.launcher.tv.R
import vn.launcher.tv.data.AdbLocal
import vn.launcher.tv.data.AdminData
import vn.launcher.tv.data.CrashGuard
import vn.launcher.tv.data.GeminiAiService
import vn.launcher.tv.data.Holidays
import vn.launcher.tv.data.LunarCalendar
import vn.launcher.tv.data.MediaLibrary
import vn.launcher.tv.data.PriceRepository
import vn.launcher.tv.data.UploadServer
import vn.launcher.tv.data.VoiceCommand
import vn.launcher.tv.data.WeatherService
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.model.AppItem
import vn.launcher.tv.model.DisplayPrefs
import vn.launcher.tv.model.DockTab
import vn.launcher.tv.model.HeaderState
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.model.MusicPlayerState
import vn.launcher.tv.model.NetworkPillState
import vn.launcher.tv.model.NowPlayingPillState
import vn.launcher.tv.model.PriceKind
import vn.launcher.tv.model.PriceMatchRow
import vn.launcher.tv.model.PriceSection
import vn.launcher.tv.model.SlideshowState
import vn.launcher.tv.model.SystemBarsState
import vn.launcher.tv.model.TvChannelSlot
import vn.launcher.tv.model.WeatherState
import vn.launcher.tv.service.MediaListener
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale
import java.util.TreeSet

sealed interface LauncherEvent {
    data class ShowToast(val message: String) : LauncherEvent
    data class LaunchIntent(val intent: Intent) : LauncherEvent
    data class LaunchIntentsWithFallback(val intents: List<Intent>) : LauncherEvent
    data object RequestVoiceRecognition : LauncherEvent
    data object RequestGoogleVoiceDialog : LauncherEvent
    data object CancelVoiceRecognition : LauncherEvent
    data object RequestSystemVoiceSearch : LauncherEvent
    data class LaunchSearch(val query: String, val onYouTube: Boolean = false) : LauncherEvent
    data object RequestWallpaperPicker : LauncherEvent
    data object RequestDefaultHomeRole : LauncherEvent
    data class RequestMediaPermissions(val perms: Array<String>) : LauncherEvent
    data class RequestAudioPermission(val perm: String) : LauncherEvent
    data object RequestLocationPermission : LauncherEvent
    data object ScrollHomeTop : LauncherEvent
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val prefs: SharedPreferences = application.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    private var sounds: SoundPool? = null
    private var soundMove: Int = 0
    private var soundSelect: Int = 0
    private var quietFocus: Boolean = false

    private var player: MediaPlayer? = null
    private var playerReady: Boolean = false
    private var pendingMediaAction: (() -> Unit)? = null

    private var uploadServer: UploadServer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var pendingSpeech: String? = null

    private var lastSignature: String = ""
    private var lastWeatherAttempt: Long = 0L
    private var weatherBusy: Boolean = false
    private var lastDay: Int = -1

    private var pollingJob: Job? = null
    private var slideCardJob: Job? = null
    private var slideFsJob: Job? = null
    private var musicProgressJob: Job? = null
    private var priceFetchJob: Job? = null
    private var aiFetchJob: Job? = null

    private var priceSections: List<PriceSection> = emptyList()
    private var priceMatchedRows: List<PriceMatchRow> = emptyList()
    private var priceUpdatedDate: String = ""
    private var currentPriceKind: PriceKind = PriceKind.GOLD
    private var currentPriceZone: Int = 0
    private var currentSpokenLocation: String? = null
    private var currentResolvedProvince: String? = null
    private var priceSpokenForOpen: Boolean = false

    init {
        val safeMode = CrashGuard.recent(app, 60_000L)
        initSounds()
        loadInitialPrefs(safeMode)
        refreshApps(force = true)
        updateHeader()
        showCachedWeather()
        if (!safeMode) {
            startPolling()
            startCardSlides()
        } else {
            toast(tr("Chế độ an toàn: launcher vừa gặp lỗi", "Safe mode: the launcher just hit an error"))
        }
        checkLastCrash()
    }

    private fun tr(vi: String, enText: String): String = if (_uiState.value.isEnglish) enText else vi

    private fun toast(msg: String) {
        _events.tryEmit(LauncherEvent.ShowToast(msg))
    }

    private fun loadInitialPrefs(safeMode: Boolean) {
        if (prefs.getInt("priceUrlVer", 0) < 1) {
            val savedCoffee = prefs.getString("urlCoffee", null)?.trim()
            val edit = prefs.edit().putInt("priceUrlVer", 1)
            if (savedCoffee == "https://giacaphe.com/gia-ca-phe-noi-dia/") {
                edit.remove("urlCoffee")
            }
            edit.apply()
        }
        val lang = prefs.getString("lang", "vi") ?: "vi"
        val theme = LauncherTheme.fromId(prefs.getInt("theme", 0))
        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val volMax = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
        val volCur = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

        _uiState.update {
            it.copy(
                lang = lang,
                theme = theme,
                safeMode = safeMode,
                userName = prefs.getString("name", "") ?: "",
                appVersion = appVersion(),
                bgSpec = prefs.getString("bg", "preset:0") ?: "preset:0",
                bgAlphaPercent = prefs.getInt("bgAlpha", 100),
                display = DisplayPrefs(
                    tvOn = prefs.getBoolean("tvOn", true),
                    musicFrameOn = prefs.getBoolean("musicFrameOn", true),
                    photoFrameOn = prefs.getBoolean("photoFrameOn", true),
                    weatherOn = prefs.getBoolean("weatherOn", true),
                    wifiOn = prefs.getBoolean("wifiOn", true),
                    voiceOn = prefs.getBoolean("voiceOn", true),
                    musicOn = prefs.getBoolean("musicOn", true),
                    barsOn = prefs.getBoolean("barsOn", true),
                    holidayOn = prefs.getBoolean("holidayOn", true),
                    lunarAlertOn = prefs.getBoolean("lunarAlertOn", true),
                    nameOn = prefs.getBoolean("nameOn", true),
                    soundOn = prefs.getBoolean("sound", true)
                ),
                tvChannels = listOf(
                    TvChannelSlot(0, chName(0), chLink(0)),
                    TvChannelSlot(1, chName(1), chLink(1))
                ),
                slideSeconds = prefs.getInt("slideSec", 8),
                keyShortcuts = readKeyMap(),
                hiddenPackages = hiddenSet(),
                fptLink = fptLink(),
                voiceChannelFpt = channelList(),
                voiceChannelVtvgo = vtvgoList(),
                voiceChannelVieon = vieonList(),
                priceUrls = PriceKind.entries.associateWith { k -> priceUrl(k) },
                wxProvinceLabel = wxProvinceLabel(lang == "en"),
                wxWardLabel = wxWardLabel(lang == "en"),
                bluetoothStatus = readBluetoothStatus(lang == "en"),
                volumeLevel = volCur,
                volumeMax = volMax,
                localIp = localIp()
            )
        }
    }

    private fun initSounds() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            sounds = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build().also { sp ->
                soundMove = sp.load(app, R.raw.move, 1)
                soundSelect = sp.load(app, R.raw.select, 1)
            }
        } catch (_: Throwable) {
        }
    }

    fun playMove() {
        if (_uiState.value.display.soundOn && !quietFocus && soundMove != 0) {
            sounds?.play(soundMove, 0.6f, 0.6f, 1, 0, 1f)
        }
    }

    fun playSelect() {
        if (_uiState.value.display.soundOn && soundSelect != 0) {
            sounds?.play(soundSelect, 0.7f, 0.7f, 1, 0, 1f)
        }
    }

    fun onResume() {
        updateHeader()
        refreshApps(force = false)
        refreshTabDock()
        if (!_uiState.value.safeMode) {
            updateSysBars()
            updateNetwork()
            updateStatusPills()
            showCachedWeather()
            maybeFetchWeather(force = false)
            startCardSlides()
            startPolling()
        }
    }

    fun onPause() {
        pollingJob?.cancel()
        slideCardJob?.cancel()
        stopUploadServer()
    }

    fun onHomePressed() {
        if (_uiState.value.movingPkg != null) endMove()
        closeDialog()
        _events.tryEmit(LauncherEvent.ScrollHomeTop)
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                updateStatusPills()
                updateSysBars()
                updateNetwork()
                maybeFetchWeather(force = false)
                val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if (today != lastDay) updateHeader() else updateTimeOnly()
                delay(2000L)
            }
        }
    }

    private fun checkLastCrash() {
        val full = CrashGuard.read(app) ?: return
        CrashGuard.clear(app)
        _uiState.update { it.copy(activeDialog = ActiveDialog.CrashReport(full)) }
    }

    fun copyToClipboard(label: String, text: String) {
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(label, text))
            toast(tr("Đã sao chép", "Copied"))
        } catch (_: Exception) {
            toast(tr("Không sao chép được", "Could not copy"))
        }
    }

    // ---------------------------------------------------------------- Header (greeting, clock, lunar, holidays)
    private fun updateTimeOnly() {
        val c = Calendar.getInstance()
        val timeText = String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        _uiState.update { it.copy(header = it.header.copy(timeText = timeText)) }
    }

    private fun updateHeader() {
        val c = Calendar.getInstance()
        lastDay = c.get(Calendar.DAY_OF_YEAR)
        val en = _uiState.value.isEnglish
        val h = c.get(Calendar.HOUR_OF_DAY)
        val timeText = String.format(Locale.US, "%02d:%02d", h, c.get(Calendar.MINUTE))
        val greetBase = when {
            h < 12 -> if (en) "Good morning" else "Chào buổi sáng"
            h < 18 -> if (en) "Good afternoon" else "Chào buổi chiều"
            else -> if (en) "Good evening" else "Chào buổi tối"
        }
        val name = if (_uiState.value.display.nameOn) _uiState.value.userName.trim() else ""
        val greeting = (if (name.isNotEmpty()) "$greetBase, $name" else greetBase).uppercase(Locale.forLanguageTag(_uiState.value.lang))

        val dd = c.get(Calendar.DAY_OF_MONTH)
        val mm = c.get(Calendar.MONTH) + 1
        val yy = c.get(Calendar.YEAR)
        val dow = c.get(Calendar.DAY_OF_WEEK) - 1
        val dateText = (if (en) "${DAY_EN[dow]}, $dd ${MON_EN[mm - 1]}" else "${THU_VI[dow]}, $dd tháng $mm")
            .uppercase(Locale.forLanguageTag(_uiState.value.lang))

        val lunar = try {
            LunarCalendar.solarToLunar(dd, mm, yy)
        } catch (_: Exception) {
            null
        }
        val lunarText = try {
            LunarCalendar.format(dd, mm, yy, en)
        } catch (_: Exception) {
            ""
        }

        val holidayText = if (_uiState.value.display.holidayOn) {
            try {
                Holidays.next(c, en)?.let { r ->
                    when {
                        r.days == 0 -> if (en) "Today is ${r.name}" else "Hôm nay là ${r.name}"
                        en -> "${r.days} ${if (r.days == 1) "day" else "days"} until ${r.name}"
                        else -> "Còn ${r.days} ngày đến ${r.name}"
                    }
                }
            } catch (_: Exception) {
                null
            }
        } else null

        val lunarAlertText = if (_uiState.value.display.lunarAlertOn && lunar != null) {
            try {
                val ld = lunar[0]
                val t = (c.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                val next = LunarCalendar.solarToLunar(
                    t.get(Calendar.DAY_OF_MONTH),
                    t.get(Calendar.MONTH) + 1,
                    t.get(Calendar.YEAR)
                )
                val msg = when {
                    ld == 1 -> tr("Hôm nay mùng 1 âm lịch", "Today is the 1st of the lunar month")
                    ld == 14 -> tr("Hôm nay 14 âm lịch – mai là Rằm", "Lunar 14th today – full moon tomorrow")
                    ld == 15 -> tr("Hôm nay Rằm (15 âm lịch)", "Today is the full moon (lunar 15th)")
                    next[0] == 1 -> tr("Hôm nay là ngày cuối tháng âm lịch ($ld)", "Last day of the lunar month ($ld)")
                    else -> null
                }
                msg?.let { "● $it" }
            } catch (_: Exception) {
                null
            }
        } else null

        _uiState.update {
            it.copy(
                header = HeaderState(
                    greeting = greeting,
                    timeText = timeText,
                    dateText = dateText,
                    lunarText = lunarText,
                    holidayText = holidayText,
                    lunarAlertText = lunarAlertText
                )
            )
        }
    }

    // ---------------------------------------------------------------- Sys Bars & Network & USB & Now Playing
    private fun updateSysBars() {
        if (!_uiState.value.display.barsOn) return
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.totalBytes
            val free = st.availableBytes
            val used = total - free
            val sPct = if (total > 0) ((used * 100) / total).toInt() else 0

            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            val ramUsed = mi.totalMem - mi.availMem
            val rPct = if (mi.totalMem > 0) ((ramUsed * 100) / mi.totalMem).toInt() else 0

            _uiState.update {
                it.copy(
                    sysBars = SystemBarsState(
                        enabled = true,
                        storageUsedGb = used / 1073741824.0,
                        storageTotalGb = total / 1073741824.0,
                        storagePercent = sPct,
                        ramUsedGb = ramUsed / 1073741824.0,
                        ramTotalGb = mi.totalMem / 1073741824.0,
                        ramPercent = rPct
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun updateNetwork() {
        val en = _uiState.value.isEnglish
        var isEth = false
        var isOffline = false
        val label = try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val nc = if (net != null) cm.getNetworkCapabilities(net) else null
            when {
                nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    isEth = true
                    "LAN"
                }
                nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifiName()
                else -> {
                    isOffline = true
                    if (en) "Offline" else "Chưa kết nối"
                }
            }
        } catch (_: Exception) {
            "Wi-Fi"
        }

        _uiState.update {
            it.copy(
                networkPill = NetworkPillState(
                    visible = it.display.wifiOn,
                    label = label,
                    isEthernet = isEth,
                    isOffline = isOffline
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiName(): String {
        try {
            val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ssid = wm?.connectionInfo?.ssid
            if (!ssid.isNullOrEmpty() && !ssid.contains("unknown ssid") && ssid != "0x") {
                return if (ssid.length > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid.substring(1, ssid.length - 1)
                } else {
                    ssid
                }
            }
        } catch (_: Exception) {
        }
        return "Wi-Fi"
    }

    private fun usbConnected(): Boolean {
        try {
            val sm = app.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null && Build.VERSION.SDK_INT >= 24) {
                for (v in sm.storageVolumes) {
                    if (v.isRemovable && Environment.MEDIA_MOUNTED == v.state) return true
                }
            }
        } catch (_: Throwable) {
        }
        try {
            val um = app.getSystemService(Context.USB_SERVICE) as? UsbManager
            if (um != null) {
                for (d in um.deviceList.values) {
                    for (i in 0 until d.interfaceCount) {
                        if (d.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return false
    }

    private fun updateStatusPills() {
        val usb = usbConnected()
        val nowPlaying = if (_uiState.value.display.musicOn) currentMusicPill() else NowPlayingPillState(visible = false)
        _uiState.update {
            it.copy(
                usbConnected = usb,
                nowPlayingPill = nowPlaying
            )
        }
    }

    private fun currentMusicPill(): NowPlayingPillState {
        val m = _uiState.value.music
        if (m.isPlaying && m.currentIndex in m.tracks.indices) {
            val t = m.tracks[m.currentIndex]
            val text = if (t.artist.isEmpty()) t.title else "${t.title} - ${t.artist}"
            return NowPlayingPillState(visible = true, text = text, isInternal = true)
        }
        try {
            val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val list = msm?.getActiveSessions(ComponentName(app, MediaListener::class.java)).orEmpty()
            for (c in list) {
                val ps = c.playbackState
                if (ps != null && ps.state == PlaybackState.STATE_PLAYING) {
                    val md = c.metadata
                    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    val text = if (title.isNullOrEmpty()) {
                        tr("Đang phát nhạc", "Playing music")
                    } else if (artist.isNullOrEmpty()) {
                        title
                    } else {
                        "$title - $artist"
                    }
                    return NowPlayingPillState(visible = true, text = text, pendingIntent = c.sessionActivity)
                }
            }
        } catch (_: Exception) {
        }
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && am.isMusicActive) {
                return NowPlayingPillState(visible = true, text = tr("Đang phát nhạc", "Playing music"))
            }
        } catch (_: Exception) {
        }
        return NowPlayingPillState(visible = false)
    }

    fun onNowPlayingClick() {
        playSelect()
        val np = _uiState.value.nowPlayingPill
        if (np.isInternal) {
            openMusicPanel()
            return
        }
        try {
            if (np.pendingIntent != null) {
                np.pendingIntent.send()
                return
            }
        } catch (_: Exception) {
        }
        _events.tryEmit(
            LauncherEvent.LaunchIntent(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            )
        )
    }

    // ---------------------------------------------------------------- Weather
    private fun showCachedWeather() {
        val enabled = prefs.getBoolean("weatherOn", true)
        val has = prefs.getLong("w_time", 0L) != 0L
        _uiState.update {
            it.copy(
                weather = WeatherState(
                    enabled = enabled,
                    hasData = has,
                    temp = prefs.getFloat("w_temp", 0f).toDouble(),
                    min = prefs.getFloat("w_min", 0f).toDouble(),
                    max = prefs.getFloat("w_max", 0f).toDouble(),
                    code = prefs.getInt("w_code", 0),
                    rain = prefs.getInt("w_rain", 0),
                    city = prefs.getString("w_city", "") ?: ""
                )
            )
        }
    }

    fun maybeFetchWeather(force: Boolean) {
        if (!prefs.getBoolean("weatherOn", true) || weatherBusy) return
        val now = System.currentTimeMillis()
        if (!force) {
            if (now - lastWeatherAttempt < 5 * 60 * 1000L) return
            if (now - prefs.getLong("w_time", 0L) < 30 * 60 * 1000L) return
        }
        lastWeatherAttempt = now
        weatherBusy = true
        val eng = _uiState.value.isEnglish
        val manual = wxManualCoords()
        val manualLabel = prefs.getString("wxLabel", "")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val d = WeatherService.fetch(app, eng, manual, manualLabel)
                prefs.edit()
                    .putFloat("w_temp", d.temp.toFloat())
                    .putFloat("w_min", d.min.toFloat())
                    .putFloat("w_max", d.max.toFloat())
                    .putInt("w_code", d.code)
                    .putInt("w_rain", d.rain)
                    .putString("w_city", d.city)
                    .putLong("w_time", System.currentTimeMillis())
                    .apply()
                withContext(Dispatchers.Main) { showCachedWeather() }
            } catch (_: Exception) {
            } finally {
                weatherBusy = false
            }
        }
    }

    private fun wxManualCoords(): Pair<Double, Double>? {
        val la = prefs.getString("wxLat", null)?.toDoubleOrNull() ?: return null
        val lo = prefs.getString("wxLon", null)?.toDoubleOrNull() ?: return null
        return la to lo
    }

    private fun wxProvinceLabel(en: Boolean = _uiState.value.isEnglish): String {
        val p = AdminData.byName(prefs.getString("wxProv", ""))
        return p?.shortName() ?: if (en) "Automatic" else "Tự động"
    }

    private fun wxWardLabel(en: Boolean = _uiState.value.isEnglish): String {
        if (AdminData.byName(prefs.getString("wxProv", "")) == null) return "—"
        val w = prefs.getString("wxWard", "") ?: ""
        return w.ifEmpty { if (en) "Whole province/city" else "Cả tỉnh/thành phố" }
    }

    fun selectAutoWeatherLocation() {
        prefs.edit().remove("wxProv").remove("wxWard").remove("wxLat").remove("wxLon").remove("wxLabel").apply()
        _uiState.update {
            it.copy(
                wxProvinceLabel = wxProvinceLabel(),
                wxWardLabel = wxWardLabel(),
                activeDialog = ActiveDialog.DisplayMenu
            )
        }
        toast(tr("Đã chuyển về vị trí tự động", "Switched to automatic location"))
        weatherRefreshNow()
    }

    fun selectProvince(p: AdminData.Province) {
        prefs.edit()
            .putString("wxProv", p.name)
            .remove("wxWard")
            .putString("wxLat", p.lat.toString())
            .putString("wxLon", p.lon.toString())
            .putString("wxLabel", p.shortName())
            .apply()
        _uiState.update {
            it.copy(
                wxProvinceLabel = wxProvinceLabel(),
                wxWardLabel = wxWardLabel(),
                activeDialog = ActiveDialog.DisplayMenu
            )
        }
        toast(tr("Đã chọn ", "Selected ") + p.shortName())
        weatherRefreshNow()
    }

    fun openWardChooser() {
        val p = AdminData.byName(prefs.getString("wxProv", ""))
        if (p == null) {
            toast(tr("Hãy chọn Tỉnh / Thành phố trước", "Choose a Province / City first"))
            return
        }
        _uiState.update { it.copy(activeDialog = ActiveDialog.ChooseWard(p, null, loading = true, error = false)) }
        viewModelScope.launch(Dispatchers.IO) {
            val wards = try {
                AdminData.loadWards(app, p)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                val cur = _uiState.value.activeDialog
                if (cur is ActiveDialog.ChooseWard && cur.province == p) {
                    _uiState.update {
                        it.copy(
                            activeDialog = ActiveDialog.ChooseWard(
                                province = p,
                                wards = wards,
                                loading = false,
                                error = wards == null
                            )
                        )
                    }
                }
            }
        }
    }

    fun selectWholeProvinceWard(p: AdminData.Province) {
        selectProvince(p)
    }

    fun selectWard(p: AdminData.Province, wardName: String, reopenDisplay: Boolean = true) {
        if (wardName.isBlank()) return
        toast(tr("Đang xác định vị trí…", "Locating…"))
        viewModelScope.launch(Dispatchers.IO) {
            val fc = AdminData.geocode("$wardName, ${p.name}, Việt Nam")
            withContext(Dispatchers.Main) {
                val ed = prefs.edit()
                    .putString("wxProv", p.name)
                    .putString("wxWard", wardName)
                    .putString("wxLabel", "$wardName, ${p.shortName()}")
                if (fc != null) {
                    ed.putString("wxLat", fc.first.toString()).putString("wxLon", fc.second.toString())
                    toast(tr("Đã chọn ", "Selected ") + wardName)
                } else {
                    ed.putString("wxLat", p.lat.toString()).putString("wxLon", p.lon.toString())
                    toast(
                        tr(
                            "Không tìm thấy tọa độ phường/xã này, dùng vị trí của tỉnh/thành phố",
                            "Could not find this ward's coordinates, using the province/city location"
                        )
                    )
                }
                ed.apply()
                _uiState.update {
                    it.copy(
                        wxProvinceLabel = wxProvinceLabel(),
                        wxWardLabel = wxWardLabel(),
                        activeDialog = if (reopenDisplay) ActiveDialog.DisplayMenu else it.activeDialog
                    )
                }
                weatherRefreshNow()
            }
        }
    }

    fun updateWeatherLocation() {
        toast(tr("Đang cập nhật vị trí thời tiết…", "Updating weather location…"))
        val p = AdminData.byName(prefs.getString("wxProv", ""))
        val ward = prefs.getString("wxWard", "") ?: ""
        if (p != null && ward.isNotEmpty()) {
            selectWard(p, ward, reopenDisplay = false)
        } else {
            weatherRefreshNow()
        }
    }

    private fun weatherRefreshNow() {
        prefs.edit().remove("w_time").apply()
        lastWeatherAttempt = 0L
        showCachedWeather()
        maybeFetchWeather(force = true)
    }

    // ---------------------------------------------------------------- Apps & Dock & Reorder
    private fun hiddenSet(): Set<String> = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())

    private fun queryResolve(): List<ResolveInfo> {
        val pm = app.packageManager
        val list = ArrayList<ResolveInfo>()
        list.addAll(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
        list.addAll(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
        val out = ArrayList<ResolveInfo>()
        val seen = HashSet<String>()
        for (ri in list) {
            if (seen.add(ri.activityInfo.packageName)) out.add(ri)
        }
        return out
    }

    private fun signature(raw: List<ResolveInfo>): String {
        val pk = TreeSet<String>()
        for (ri in raw) pk.add(ri.activityInfo.packageName)
        return "$pk|${TreeSet(hiddenSet())}|${prefs.getString("order", "")}"
    }

    fun refreshApps(force: Boolean) {
        val raw = queryResolve()
        val sig = signature(raw)
        if (!force && sig == lastSignature) return
        lastSignature = sig

        val pm = app.packageManager
        val hidden = hiddenSet()
        val items = ArrayList<AppItem>()
        for (ri in raw) {
            val pkg = ri.activityInfo.packageName
            if (hidden.contains(pkg)) continue
            val bn = ri.activityInfo.loadBanner(pm)
            items.add(
                AppItem(
                    label = ri.loadLabel(pm).toString(),
                    pkg = pkg,
                    cls = ri.activityInfo.name,
                    image = bn ?: ri.loadIcon(pm),
                    isBanner = bn != null,
                    isSelf = pkg == app.packageName
                )
            )
        }
        val ordered = orderApps(items)
        val dockApps = computeDockApps(ordered, _uiState.value.dockTab)
        val gridApps = if (ordered.size > COLUMNS) ordered.subList(COLUMNS, ordered.size) else emptyList()
        _uiState.update {
            it.copy(
                allApps = ordered,
                dockApps = dockApps,
                gridApps = gridApps,
                focusedLabel = dockApps.firstOrNull()?.label ?: it.focusedLabel
            )
        }
    }

    private fun defaultOrder(items: List<AppItem>): List<AppItem> {
        val sorted = items.sortedWith { x, y -> x.label.compareTo(y.label, ignoreCase = true) }
        val out = ArrayList<AppItem>()
        for (key in DOCK_PREF) {
            val match = sorted.firstOrNull { !it.isSelf && !out.contains(it) && it.label.lowercase(Locale.ROOT).contains(key) }
            if (match != null) out.add(match)
        }
        for (a in sorted) if (!a.isSelf && !out.contains(a)) out.add(a)
        for (a in sorted) if (a.isSelf) out.add(a)
        return out
    }

    private fun orderApps(items: List<AppItem>): List<AppItem> {
        val def = defaultOrder(items)
        val saved = prefs.getString("order", "") ?: ""
        if (saved.isEmpty()) return def
        val out = ArrayList<AppItem>()
        for (p in saved.split(",")) {
            val match = def.firstOrNull { it.pkg == p && !out.contains(it) }
            if (match != null) out.add(match)
        }
        for (a in def) if (!out.contains(a)) out.add(a)
        return out
    }

    private fun launchCounts(): MutableMap<String, Int> {
        val m = LinkedHashMap<String, Int>()
        val raw = prefs.getString("counts", "") ?: ""
        for (e in raw.split(",")) {
            val i = e.lastIndexOf(':')
            if (i <= 0) continue
            val count = e.substring(i + 1).toIntOrNull() ?: continue
            m[e.substring(0, i)] = count
        }
        return m
    }

    private fun recordLaunch(pkg: String) {
        if (pkg == app.packageName) return
        val r = ArrayList<String>().apply { add(pkg) }
        for (p in (prefs.getString("recents", "") ?: "").split(",")) {
            if (p.isNotEmpty() && p != pkg && r.size < 12) r.add(p)
        }
        val c = launchCounts()
        c[pkg] = (c[pkg] ?: 0) + 1
        val countsStr = c.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit()
            .putString("recents", r.joinToString(","))
            .putString("counts", countsStr)
            .apply()
    }

    private fun computeDockApps(all: List<AppItem>, tab: DockTab): List<AppItem> {
        return when (tab) {
            DockTab.APPS -> all.take(COLUMNS)
            DockTab.RECENTS -> {
                val out = ArrayList<AppItem>()
                for (pkg in (prefs.getString("recents", "") ?: "").split(",")) {
                    if (pkg.isEmpty()) continue
                    val a = all.firstOrNull { it.pkg == pkg && !it.isSelf }
                    if (a != null && !out.contains(a)) out.add(a)
                    if (out.size >= COLUMNS) break
                }
                out
            }
            DockTab.MOST_USED -> {
                val counts = launchCounts()
                all.filter { !it.isSelf && counts.containsKey(it.pkg) }
                    .sortedByDescending { counts[it.pkg] ?: 0 }
                    .take(COLUMNS)
            }
        }
    }

    private fun refreshTabDock() {
        val tab = _uiState.value.dockTab
        if (tab == DockTab.APPS || _uiState.value.movingPkg != null) return
        _uiState.update { it.copy(dockApps = computeDockApps(it.allApps, tab)) }
    }

    fun selectDockTab(tab: DockTab) {
        playSelect()
        if (tab == _uiState.value.dockTab) return
        _uiState.update {
            it.copy(
                dockTab = tab,
                dockApps = computeDockApps(it.allApps, tab)
            )
        }
    }

    fun onTileFocused(label: String) {
        playMove()
        if (_uiState.value.movingPkg == null) {
            _uiState.update { it.copy(focusedLabel = label) }
        }
    }

    fun launchApp(a: AppItem) {
        playSelect()
        if (a.isSelf) {
            openSettings()
            return
        }
        recordLaunch(a.pkg)
        if (isFpt(a) && fptLink().isNotEmpty()) {
            if (openLinkIn(fptLink(), a.pkg)) return
        }
        launchPlain(a)
    }

    private fun launchPlain(a: AppItem) {
        val it = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(a.pkg, a.cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        _events.tryEmit(LauncherEvent.LaunchIntent(it))
    }

    fun openTileMenu(a: AppItem) {
        playSelect()
        _uiState.update { it.copy(activeDialog = ActiveDialog.TileActions(a)) }
    }

    fun startMove(a: AppItem) {
        closeDialog()
        if (_uiState.value.dockTab != DockTab.APPS) {
            _uiState.update {
                it.copy(
                    dockTab = DockTab.APPS,
                    dockApps = computeDockApps(it.allApps, DockTab.APPS)
                )
            }
        }
        _uiState.update {
            it.copy(
                movingPkg = a.pkg,
                focusedLabel = tr("Đang di chuyển: ", "Moving: ") + a.label
            )
        }
        toast(tr("Dùng phím mũi tên để di chuyển, bấm OK để xong", "Use the arrow keys to move, press OK when done"))
    }

    fun moveBy(delta: Int) {
        val pkg = _uiState.value.movingPkg ?: return
        val list = _uiState.value.allApps.toMutableList()
        val idx = list.indexOfFirst { it.pkg == pkg }
        if (idx < 0) return
        val n = idx + delta
        if (n !in list.indices) return
        val item = list.removeAt(idx)
        list.add(n, item)
        playMove()
        _uiState.update {
            it.copy(
                allApps = list,
                dockApps = computeDockApps(list, DockTab.APPS),
                gridApps = if (list.size > COLUMNS) list.subList(COLUMNS, list.size) else emptyList(),
                focusedLabel = tr("Đang di chuyển: ", "Moving: ") + item.label
            )
        }
    }

    fun endMove() {
        val pkg = _uiState.value.movingPkg ?: return
        val orderStr = _uiState.value.allApps.joinToString(",") { it.pkg }
        prefs.edit().putString("order", orderStr).apply()
        lastSignature = signature(queryResolve())
        val label = _uiState.value.allApps.firstOrNull { it.pkg == pkg }?.label ?: ""
        _uiState.update { it.copy(movingPkg = null, focusedLabel = label) }
        toast(tr("Đã lưu vị trí", "Position saved"))
    }

    fun hideApp(a: AppItem) {
        val h = hiddenSet().toMutableSet().apply { add(a.pkg) }
        prefs.edit().putStringSet("hidden", h).apply()
        _uiState.update { it.copy(hiddenPackages = h, activeDialog = null) }
        refreshApps(force = true)
        toast(
            tr(
                "Đã ẩn ${a.label} (hiện lại trong Cài đặt → Ứng dụng đã ẩn)",
                "${a.label} hidden (restore in Settings → Hidden apps)"
            )
        )
    }

    fun restoreHiddenApp(pkg: String) {
        val h = hiddenSet().toMutableSet().apply { remove(pkg) }
        prefs.edit().putStringSet("hidden", h).apply()
        _uiState.update { it.copy(hiddenPackages = h) }
        refreshApps(force = true)
        toast(tr("Đã hiện lại ứng dụng", "App is visible again"))
        if (h.isEmpty()) closeDialog()
    }

    fun uninstallApp(a: AppItem) {
        closeDialog()
        _events.tryEmit(LauncherEvent.LaunchIntent(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))))
    }

    // ---------------------------------------------------------------- TV Channels & FPT Play
    private fun chName(i: Int): String = prefs.getString("ch${i}Name", DEFAULT_CH[i][0]) ?: DEFAULT_CH[i][0]
    private fun chLink(i: Int): String = prefs.getString("ch${i}Link", DEFAULT_CH[i][1]) ?: DEFAULT_CH[i][1]
    private fun isFpt(a: AppItem): Boolean = a.label.lowercase(Locale.ROOT).contains("fpt play")
    private fun fptLink(): String = (prefs.getString("fptLink", DEFAULT_FPT_LINK) ?: DEFAULT_FPT_LINK).trim()

    private fun findByLabel(key: String): AppItem? =
        _uiState.value.allApps.firstOrNull { !it.isSelf && it.label.lowercase(Locale.ROOT).contains(key) }

    private fun openLinkIn(link: String, pkg: String): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (i.resolveActivity(app.packageManager) != null) {
                _events.tryEmit(LauncherEvent.LaunchIntent(i))
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openChannelSlot(slot: Int) {
        playSelect()
        val link = chLink(slot).trim()
        val fpt = findByLabel("fpt play")
        if (fpt != null) recordLaunch(fpt.pkg)
        if (link.isEmpty()) {
            if (fpt != null) launchPlain(fpt)
            else toast(tr("Chưa có liên kết cho kênh này", "No link set for this channel"))
            return
        }
        if (fpt != null) {
            if (openLinkIn(link, fpt.pkg)) return
            toast(tr("FPT Play không nhận liên kết này, đang mở ứng dụng", "FPT Play did not accept this link, opening the app"))
            launchPlain(fpt)
            return
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        _events.tryEmit(LauncherEvent.LaunchIntent(i))
    }

    fun saveChannelSlot(slot: Int, name: String, link: String) {
        prefs.edit().putString("ch${slot}Name", name).putString("ch${slot}Link", link).apply()
        refreshTvChannels()
    }

    fun resetChannelSlot(slot: Int) {
        prefs.edit().remove("ch${slot}Name").remove("ch${slot}Link").apply()
        refreshTvChannels()
    }

    private fun refreshTvChannels() {
        _uiState.update {
            it.copy(
                tvChannels = listOf(
                    TvChannelSlot(0, chName(0), chLink(0)),
                    TvChannelSlot(1, chName(1), chLink(1))
                )
            )
        }
    }

    fun saveFptLink(link: String) {
        prefs.edit().putString("fptLink", link).apply()
        _uiState.update { it.copy(fptLink = fptLink()) }
        toast(tr("Đã lưu", "Saved"))
    }

    fun resetFptLink() {
        prefs.edit().remove("fptLink").apply()
        _uiState.update { it.copy(fptLink = fptLink()) }
        toast(tr("Đã khôi phục liên kết mặc định", "Default link restored"))
    }

    // ---------------------------------------------------------------- Settings & Display & Themes & Wallpaper
    fun openSettings() {
        playSelect()
        _uiState.update {
            it.copy(
                bluetoothStatus = readBluetoothStatus(it.isEnglish),
                localIp = localIp(),
                activeDialog = ActiveDialog.SettingsMain
            )
        }
    }

    fun openDialog(dialog: ActiveDialog) {
        playSelect()
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun closeDialog() {
        stopUploadServer()
        _uiState.update { it.copy(activeDialog = null) }
    }

    fun setThemeChoice(theme: LauncherTheme) {
        if (theme == _uiState.value.theme) {
            closeDialog()
            return
        }
        val ed = prefs.edit().putInt("theme", theme.id)
        val bg = prefs.getString("bg", "preset:0") ?: "preset:0"
        var newBg = bg
        if (theme == LauncherTheme.MAC_OS) {
            if (bg == "theme") {
                ed.putString("bg", "preset:0")
                newBg = "preset:0"
            }
        } else if (bg != "custom") {
            ed.putString("bg", "theme")
            newBg = "theme"
        }
        ed.apply()
        _uiState.update { it.copy(theme = theme, bgSpec = newBg, activeDialog = null) }
    }

    fun setLanguage(langCode: String) {
        if (langCode != _uiState.value.lang) {
            prefs.edit().putString("lang", langCode).apply()
            val en = langCode == "en"
            _uiState.update {
                it.copy(
                    lang = langCode,
                    wxProvinceLabel = wxProvinceLabel(en),
                    wxWardLabel = wxWardLabel(en),
                    bluetoothStatus = readBluetoothStatus(en),
                    activeDialog = null
                )
            }
            updateHeader()
            updateNetwork()
            updateStatusPills()
            maybeFetchWeather(force = true)
        } else {
            closeDialog()
        }
    }

    fun setUserName(name: String) {
        prefs.edit().putString("name", name).putBoolean("nameOn", true).apply()
        _uiState.update {
            it.copy(
                userName = name,
                display = it.display.copy(nameOn = true)
            )
        }
        updateHeader()
    }

    fun toggleDisplayPref(key: String) {
        val current = prefs.getBoolean(key, true)
        val now = !current
        prefs.edit().putBoolean(key, now).apply()
        playSelect()

        val d = _uiState.value.display
        val updated = when (key) {
            "tvOn" -> d.copy(tvOn = now)
            "musicFrameOn" -> d.copy(musicFrameOn = now)
            "photoFrameOn" -> d.copy(photoFrameOn = now)
            "weatherOn" -> d.copy(weatherOn = now)
            "wifiOn" -> d.copy(wifiOn = now)
            "voiceOn" -> d.copy(voiceOn = now)
            "musicOn" -> d.copy(musicOn = now)
            "barsOn" -> d.copy(barsOn = now)
            "holidayOn" -> d.copy(holidayOn = now)
            "lunarAlertOn" -> d.copy(lunarAlertOn = now)
            "nameOn" -> d.copy(nameOn = now)
            "sound" -> d.copy(soundOn = now)
            else -> d
        }
        _uiState.update { it.copy(display = updated) }

        when (key) {
            "weatherOn" -> {
                showCachedWeather()
                if (now) maybeFetchWeather(force = true)
            }
            "wifiOn" -> updateNetwork()
            "musicOn" -> updateStatusPills()
            "barsOn" -> {
                if (now) updateSysBars() else _uiState.update { it.copy(sysBars = it.sysBars.copy(enabled = false)) }
            }
            "holidayOn", "lunarAlertOn", "nameOn" -> updateHeader()
            "photoFrameOn" -> {
                _uiState.update { it.copy(slideshow = it.slideshow.copy(cardEnabled = now)) }
                if (now) startCardSlides() else slideCardJob?.cancel()
            }
        }
    }

    fun setVolume(level: Int) {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val clamped = level.coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
            _uiState.update { it.copy(volumeLevel = clamped, volumeMax = max) }
        } catch (_: Exception) {
        }
    }

    fun adjustVolume(delta: Int) {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val next = (current + delta).coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
            _uiState.update { it.copy(volumeLevel = next, volumeMax = max) }
            playMove()
        } catch (_: Exception) {
        }
    }

    fun selectPresetWallpaper(index: Int) {
        val spec = "preset:$index"
        prefs.edit().putString("bg", spec).apply()
        _uiState.update { it.copy(bgSpec = spec, activeDialog = null) }
    }

    fun setWallpaperOpacity(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        prefs.edit().putInt("bgAlpha", clamped).apply()
        _uiState.update { it.copy(bgAlphaPercent = clamped) }
    }

    fun applyCustomWallpaperFromUri(uri: Uri) {
        applyCustomAsync { app.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open URI") }
    }

    fun applyCustomWallpaperFromFile(file: File) {
        applyCustomAsync { FileInputStream(file) }
    }

    private fun applyCustomAsync(opener: () -> InputStream) {
        val dm = app.resources.displayMetrics
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bm = decodeCover(opener, dm.widthPixels, dm.heightPixels) ?: throw IllegalStateException("decode")
                FileOutputStream(File(app.filesDir, "wallpaper.jpg")).use { os ->
                    bm.compress(Bitmap.CompressFormat.JPEG, 90, os)
                }
                prefs.edit().putString("bg", "custom").apply()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            bgSpec = "custom",
                            customWallpaperVersion = it.customWallpaperVersion + 1,
                            activeDialog = null
                        )
                    }
                    toast(tr("Đã đổi hình nền", "Wallpaper changed"))
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    toast(tr("Không đọc được ảnh này", "Could not read this photo"))
                }
            }
        }
    }

    private fun decodeCover(opener: () -> InputStream, w: Int, h: Int): Bitmap? {
        val op = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        opener().use { BitmapFactory.decodeStream(it, null, op) }
        var s = 1
        while (op.outWidth / (s * 2) >= w && op.outHeight / (s * 2) >= h) s *= 2
        op.inJustDecodeBounds = false
        op.inSampleSize = s
        val src = opener().use { BitmapFactory.decodeStream(it, null, op) } ?: return null
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val cw = minOf(src.width, Math.round(w / scale))
        val ch = minOf(src.height, Math.round(h / scale))
        val x = maxOf(0, (src.width - cw) / 2)
        val y = maxOf(0, (src.height - ch) / 2)
        val crop = Bitmap.createBitmap(src, x, y, cw, ch)
        return Bitmap.createScaledBitmap(crop, w, h, true)
    }

    fun openLocalWallpapers() {
        val dir = app.getExternalFilesDir("wallpapers")
        val files = dir?.listFiles { _, name ->
            val l = name.lowercase(Locale.ROOT)
            l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")
        }?.toList().orEmpty()
        _uiState.update {
            it.copy(activeDialog = ActiveDialog.LocalWallpapers(files, dir?.absolutePath ?: ""))
        }
    }

    fun startQrUpload() {
        val ip = lanIp()
        if (ip == null) {
            toast(tr("TV chưa kết nối mạng", "The TV is not connected to a network"))
            return
        }
        stopUploadServer()
        val rnd = ByteArray(9).also { SecureRandom().nextBytes(it) }
        val token = rnd.joinToString("") { String.format(Locale.US, "%02x", it) }
        val page = buildUploadPage(token)
        try {
            val srv = UploadServer(token, page) { data ->
                applyCustomAsync { ByteArrayInputStream(data) }
            }
            val port = srv.start()
            uploadServer = srv
            val url = "http://$ip:$port/$token"
            val qrBitmap = makeQr(url, 480)
            _uiState.update { it.copy(activeDialog = ActiveDialog.QrUpload(url, qrBitmap)) }
        } catch (_: Exception) {
            stopUploadServer()
            toast(tr("Không mở được cổng nhận ảnh", "Could not open the upload port"))
        }
    }

    private fun stopUploadServer() {
        uploadServer?.stop()
        uploadServer = null
    }

    private fun buildUploadPage(token: String): String {
        return try {
            app.resources.openRawResource(R.raw.upload_page).bufferedReader(Charsets.UTF_8).use { it.readText() }
                .replace("@TITLE@", tr("Gửi ảnh nền cho TV", "Send a wallpaper to the TV"))
                .replace("@HINT@", tr("Chọn một ảnh trong điện thoại rồi bấm Gửi.", "Pick a photo from your phone, then tap Send."))
                .replace("@BTN@", tr("Gửi", "Send"))
                .replace("@PICK@", tr("Chưa chọn ảnh", "Pick a photo first"))
                .replace("@DONE@", tr("Đã gửi! Xem trên TV.", "Sent! Check the TV."))
                .replace("@ERR@", tr("Gửi thất bại, hãy thử lại.", "Failed, please try again."))
                .replace("@TOKEN@", token)
        } catch (_: Exception) {
            "<html><body>Error</body></html>"
        }
    }

    private fun makeQr(text: String, sizePx: Int): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = m.width
            val h = m.height
            val px = IntArray(w * h) { idx ->
                val x = idx % w
                val y = idx / w
                if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- Quick Tiles & ADB
    fun openInputs() {
        val i = Intent("com.android.tv.action.VIEW_INPUTS")
        if (i.resolveActivity(app.packageManager) != null) {
            _events.tryEmit(LauncherEvent.LaunchIntent(i))
            return
        }
        adbRun("input keyevent 178") { _, err ->
            if (err != null) {
                toast(
                    tr(
                        "Không mở được danh sách nguồn vào. Bật ADB nội bộ hoặc dùng nút INPUT trên remote",
                        "Cannot open the input list. Enable local ADB or use the INPUT button on the remote"
                    )
                )
            }
        }
    }

    fun openBluetooth() {
        _events.tryEmit(
            LauncherEvent.LaunchIntentsWithFallback(
                listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            )
        )
    }

    fun openNetworkSettings() {
        _events.tryEmit(
            LauncherEvent.LaunchIntentsWithFallback(
                listOf(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            )
        )
    }

    fun openStorageSettings() {
        _events.tryEmit(
            LauncherEvent.LaunchIntentsWithFallback(
                listOf(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            )
        )
    }

    fun openSystemSettings() {
        _events.tryEmit(LauncherEvent.LaunchIntent(Intent(Settings.ACTION_SETTINGS)))
    }

    fun powerOff() {
        closeDialog()
        toast(tr("Đang tắt nguồn TV…", "Shutting down TV…"))
        val shutdownCmd = "(svc power shutdown 2>/dev/null) || (reboot -p 2>/dev/null) || (setprop sys.powerctl shutdown 2>/dev/null) || input keyevent --longpress 26"
        adbRun(shutdownCmd) { _, err ->
            if (err != null) {
                val msgLower = err.lowercase()
                // Ignore connection drop caused by the TV actually powering off mid-command
                val poweredOffMidStream = msgLower.contains("eof") || msgLower.contains("broken pipe") || msgLower.contains("reset")
                if (!poweredOffMidStream) {
                    toast(tr("Cần bật ADB nội bộ để tắt TV: ", "Local ADB is needed to turn off the TV: ") + err)
                }
            }
        }
    }

    fun autoSetDefaultLauncher() {
        toast(tr("Đang kết nối ADB nội bộ…", "Connecting to local ADB…"))
        adbRun("cmd package set-home-activity ${app.packageName}/vn.launcher.tv.MainActivity") { out, err ->
            if (err != null) {
                toast(tr("Lỗi ADB nội bộ: ", "Local ADB error: ") + err)
            } else {
                val o = out?.trim().orEmpty()
                toast(o.ifEmpty { tr("Đã đặt làm launcher mặc định", "Set as default launcher") })
            }
        }
    }

    fun requestSystemDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = app.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                        toast(tr("Đã là launcher mặc định", "Already the default launcher"))
                        return
                    }
                    _events.tryEmit(LauncherEvent.RequestDefaultHomeRole)
                    return
                }
            } catch (_: Exception) {
            }
        }
        _events.tryEmit(
            LauncherEvent.LaunchIntentsWithFallback(
                listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            )
        )
    }

    private fun adbRun(cmd: String, cb: (String?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var out: String? = null
            var err: String? = null
            try {
                out = AdbLocal.shell(app, cmd)
            } catch (t: Throwable) {
                val c = t.cause ?: t
                err = c.javaClass.simpleName + (c.message?.let { ": $it" } ?: "")
            }
            withContext(Dispatchers.Main) { cb(out, err) }
        }
    }

    // ---------------------------------------------------------------- Media Permissions & Music Player
    private fun mediaPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasMediaPerm(): Boolean = mediaPerms().all {
        app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun withMediaPermission(action: () -> Unit) {
        if (hasMediaPerm()) {
            action()
            return
        }
        pendingMediaAction = action
        _events.tryEmit(LauncherEvent.RequestMediaPermissions(mediaPerms()))
    }

    fun onMediaPermissionsResult(granted: Boolean) {
        val action = pendingMediaAction
        pendingMediaAction = null
        if (granted) {
            action?.invoke()
            startCardSlides()
        } else {
            toast(
                tr(
                    "Chưa được cấp quyền đọc nhạc/ảnh.",
                    "Media permission was not granted."
                )
            )
        }
    }

    fun openMusicPanel() {
        withMediaPermission {
            _uiState.update { it.copy(activeDialog = ActiveDialog.MusicPanel) }
            if (!_uiState.value.music.tracksLoaded) {
                loadTracks(deep = false) {
                    if (_uiState.value.music.tracks.isNotEmpty() && _uiState.value.music.currentIndex < 0) {
                        playTrack(0)
                    }
                }
            }
            startMusicProgressLoop()
        }
    }

    fun refreshMusicLibrary() {
        withMediaPermission {
            val curUri = _uiState.value.music.let { m ->
                m.tracks.getOrNull(m.currentIndex)?.uri
            }
            toast(tr("Đang quét lại nhạc…", "Scanning for music…"))
            loadTracks(deep = true) {
                val list = _uiState.value.music.tracks
                val newIdx = if (curUri != null) list.indexOfFirst { it.uri == curUri } else -1
                _uiState.update { it.copy(music = it.music.copy(currentIndex = newIdx)) }
                toast(tr("Tìm thấy ${list.size} bài hát", "Found ${list.size} songs"))
            }
        }
    }

    private fun loadTracks(deep: Boolean, onDone: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = MediaLibrary.loadTracks(app, deep)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(music = it.music.copy(tracks = list, tracksLoaded = true))
                }
                onDone?.invoke()
            }
        }
    }

    fun playTrack(idx: Int) {
        val tracks = _uiState.value.music.tracks
        if (idx !in tracks.indices) return
        val track = tracks[idx]
        try {
            releasePlayer()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(app, track.uri)
                setOnPreparedListener { p ->
                    playerReady = true
                    p.start()
                    _uiState.update {
                        it.copy(
                            music = it.music.copy(
                                isPlaying = true,
                                durationMs = p.duration.toLong().coerceAtLeast(0L)
                            )
                        )
                    }
                    startMusicProgressLoop()
                    updateStatusPills()
                }
                setOnCompletionListener { nextTrack() }
                setOnErrorListener { _, _, _ ->
                    playerReady = false
                    toast(tr("Không phát được bài này", "Cannot play this track"))
                    _uiState.update { it.copy(music = it.music.copy(isPlaying = false)) }
                    true
                }
            }
            player = mp
            _uiState.update {
                it.copy(
                    music = it.music.copy(
                        currentIndex = idx,
                        isPlaying = false,
                        coverArt = null,
                        positionMs = 0L,
                        durationMs = track.duration
                    )
                )
            }
            mp.prepareAsync()
            loadCoverAsync(idx, track.uri)
        } catch (_: Exception) {
            toast(tr("Không phát được bài này", "Cannot play this track"))
        }
    }

    private fun loadCoverAsync(idx: Int, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val cover = MediaLibrary.loadCover(app, uri)
            withContext(Dispatchers.Main) {
                if (_uiState.value.music.currentIndex == idx) {
                    _uiState.update { it.copy(music = it.music.copy(coverArt = cover)) }
                }
            }
        }
    }

    fun toggleMusicPlay() {
        withMediaPermission {
            if (!_uiState.value.music.tracksLoaded) {
                loadTracks(deep = false) {
                    if (_uiState.value.music.tracks.isNotEmpty()) playTrack(0)
                }
                return@withMediaPermission
            }
            val mp = player
            if (mp == null) {
                val idx = _uiState.value.music.currentIndex
                if (idx >= 0) playTrack(idx) else if (_uiState.value.music.tracks.isNotEmpty()) playTrack(0)
                return@withMediaPermission
            }
            if (!playerReady) return@withMediaPermission
            try {
                if (mp.isPlaying) mp.pause() else mp.start()
                _uiState.update { it.copy(music = it.music.copy(isPlaying = mp.isPlaying)) }
                updateStatusPills()
            } catch (_: Exception) {
            }
        }
    }

    fun stopMusicPlayback() {
        releasePlayer()
        musicProgressJob?.cancel()
        _uiState.update {
            it.copy(music = it.music.copy(isPlaying = false, positionMs = 0L))
        }
        updateStatusPills()
    }

    fun nextTrack() {
        withMediaPermission {
            val tracks = _uiState.value.music.tracks
            if (tracks.isEmpty()) {
                if (!_uiState.value.music.tracksLoaded) loadTracks(false) { if (_uiState.value.music.tracks.isNotEmpty()) playTrack(0) }
                return@withMediaPermission
            }
            val next = (_uiState.value.music.currentIndex + 1) % tracks.size
            playTrack(next)
        }
    }

    fun prevTrack() {
        withMediaPermission {
            val tracks = _uiState.value.music.tracks
            if (tracks.isEmpty()) {
                if (!_uiState.value.music.tracksLoaded) loadTracks(false) { if (_uiState.value.music.tracks.isNotEmpty()) playTrack(0) }
                return@withMediaPermission
            }
            try {
                val mp = player
                if (mp != null && playerReady && mp.currentPosition > 3000) {
                    mp.seekTo(0)
                    return@withMediaPermission
                }
            } catch (_: Exception) {
            }
            val idx = _uiState.value.music.currentIndex
            playTrack(if (idx <= 0) tracks.size - 1 else idx - 1)
        }
    }

    private fun startMusicProgressLoop() {
        musicProgressJob?.cancel()
        musicProgressJob = viewModelScope.launch {
            while (isActive) {
                val mp = player
                if (mp != null && playerReady) {
                    try {
                        _uiState.update {
                            it.copy(
                                music = it.music.copy(
                                    isPlaying = mp.isPlaying,
                                    positionMs = mp.currentPosition.toLong().coerceAtLeast(0L),
                                    durationMs = mp.duration.toLong().coerceAtLeast(0L)
                                )
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
                delay(500L)
            }
        }
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playerReady = false
    }

    // ---------------------------------------------------------------- Photo Slideshow
    private fun slideMs(): Long = prefs.getInt("slideSec", 8) * 1000L

    fun startCardSlides() {
        slideCardJob?.cancel()
        if (!_uiState.value.display.photoFrameOn) return
        if (!hasMediaPerm()) {
            _uiState.update {
                it.copy(
                    slideshow = it.slideshow.copy(
                        hintText = tr("Bấm để cấp quyền và chọn ảnh trình chiếu", "Press to grant access and set up the slideshow")
                    )
                )
            }
            return
        }
        if (!_uiState.value.slideshow.loaded) {
            loadSlides {
                if (_uiState.value.slideshow.uris.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            slideshow = it.slideshow.copy(
                                hintText = tr("Chưa thấy ảnh. Giữ OK để chọn thư mục", "No photos found. Hold OK to choose folders")
                            )
                        )
                    }
                } else {
                    startCardSlideTimer()
                }
            }
        } else if (_uiState.value.slideshow.uris.isNotEmpty()) {
            startCardSlideTimer()
        }
    }

    private fun startCardSlideTimer() {
        slideCardJob?.cancel()
        slideCardJob = viewModelScope.launch {
            if (_uiState.value.slideshow.cardBitmap == null) advanceCardSlide()
            while (isActive) {
                delay(slideMs())
                advanceCardSlide()
            }
        }
    }

    private suspend fun advanceCardSlide() {
        val uris = _uiState.value.slideshow.uris
        if (uris.isEmpty()) return
        val nextIdx = (_uiState.value.slideshow.currentIndex + 1) % uris.size
        val bm = withContext(Dispatchers.IO) {
            MediaLibrary.loadScaled(app, uris[nextIdx], 800)
        } ?: return
        _uiState.update {
            it.copy(
                slideshow = it.slideshow.copy(
                    currentIndex = nextIdx,
                    cardBitmap = bm,
                    hintText = null
                )
            )
        }
    }

    private fun loadSlides(onDone: (() -> Unit)? = null) {
        val buckets = prefs.getStringSet("ssBuckets", null)
        viewModelScope.launch(Dispatchers.IO) {
            val uris = MediaLibrary.loadImages(app, buckets)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(slideshow = it.slideshow.copy(uris = uris, loaded = true))
                }
                onDone?.invoke()
            }
        }
    }

    fun openSlideshow() {
        playSelect()
        withMediaPermission {
            if (!_uiState.value.slideshow.loaded) {
                loadSlides { showFullscreenSlideshow() }
            } else {
                showFullscreenSlideshow()
            }
        }
    }

    private fun showFullscreenSlideshow() {
        val uris = _uiState.value.slideshow.uris
        if (uris.isEmpty()) {
            toast(tr("Không tìm thấy ảnh. Chọn thư mục ảnh trong Cài đặt → Nhạc & ảnh", "No photos found. Choose folders in Settings → Music & photos"))
            openDialog(ActiveDialog.MediaSettings)
            return
        }
        val startIdx = _uiState.value.slideshow.currentIndex.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                activeDialog = null,
                slideshow = it.slideshow.copy(
                    fullscreenOpen = true,
                    fullscreenIndex = startIdx,
                    fullscreenPaused = false
                )
            )
        }
        loadFullscreenPhoto(startIdx)
        startFullscreenTimer()
    }

    private fun startFullscreenTimer() {
        slideFsJob?.cancel()
        slideFsJob = viewModelScope.launch {
            while (isActive) {
                delay(slideMs())
                if (!_uiState.value.slideshow.fullscreenPaused) {
                    stepFullscreenSlideshow(1)
                }
            }
        }
    }

    fun stepFullscreenSlideshow(delta: Int) {
        val n = _uiState.value.slideshow.uris.size
        if (n == 0) return
        val next = ((_uiState.value.slideshow.fullscreenIndex + delta) % n + n) % n
        _uiState.update { it.copy(slideshow = it.slideshow.copy(fullscreenIndex = next)) }
        loadFullscreenPhoto(next)
        startFullscreenTimer()
    }

    fun toggleFullscreenSlideshowPause() {
        val paused = !_uiState.value.slideshow.fullscreenPaused
        _uiState.update { it.copy(slideshow = it.slideshow.copy(fullscreenPaused = paused)) }
        toast(if (paused) tr("Tạm dừng", "Paused") else tr("Tiếp tục", "Playing"))
    }

    fun closeFullscreenSlideshow() {
        slideFsJob?.cancel()
        _uiState.update { it.copy(slideshow = it.slideshow.copy(fullscreenOpen = false)) }
    }

    private fun loadFullscreenPhoto(idx: Int) {
        val uri = _uiState.value.slideshow.uris.getOrNull(idx) ?: return
        val dm = app.resources.displayMetrics
        val dim = minOf(1920, maxOf(dm.widthPixels, dm.heightPixels))
        viewModelScope.launch(Dispatchers.IO) {
            val bm = MediaLibrary.loadScaled(app, uri, dim)
            withContext(Dispatchers.Main) {
                if (_uiState.value.slideshow.fullscreenIndex == idx && bm != null) {
                    _uiState.update { it.copy(slideshow = it.slideshow.copy(fullscreenBitmap = bm)) }
                }
            }
        }
    }

    fun chooseSlideFolders() {
        withMediaPermission {
            viewModelScope.launch(Dispatchers.IO) {
                val albums = MediaLibrary.loadAlbums(app)
                withContext(Dispatchers.Main) {
                    if (albums.isEmpty()) {
                        toast(tr("Không thấy thư mục ảnh nào", "No photo folders found"))
                    } else {
                        val sel = HashSet(prefs.getStringSet("ssBuckets", emptySet()) ?: emptySet())
                        _uiState.update {
                            it.copy(activeDialog = ActiveDialog.ChooseSlideFolders(albums, sel))
                        }
                    }
                }
            }
        }
    }

    fun saveSlideFolders(selected: Set<String>?) {
        val ed = prefs.edit()
        if (selected == null) ed.remove("ssBuckets") else ed.putStringSet("ssBuckets", selected)
        ed.apply()
        _uiState.update {
            it.copy(
                activeDialog = null,
                slideshow = it.slideshow.copy(loaded = false, currentIndex = -1, cardBitmap = null)
            )
        }
        startCardSlides()
        if (selected != null) toast(tr("Đã lưu", "Saved"))
    }

    fun setSlideSeconds(sec: Int) {
        prefs.edit().putInt("slideSec", sec).apply()
        _uiState.update { it.copy(slideSeconds = sec, activeDialog = null) }
        startCardSlides()
    }

    fun refreshAllMediaLibrary() {
        closeDialog()
        _uiState.update {
            it.copy(slideshow = it.slideshow.copy(loaded = false, currentIndex = -1, cardBitmap = null))
        }
        withMediaPermission {
            refreshMusicLibrary()
            startCardSlides()
        }
    }

    // ---------------------------------------------------------------- Voice Commands & TTS
    fun startVoiceCommand() {
        playSelect()
        _events.tryEmit(LauncherEvent.RequestVoiceRecognition)
    }

    fun startSystemVoiceSearch() {
        playSelect()
        _events.tryEmit(LauncherEvent.RequestSystemVoiceSearch)
    }

    fun showVoiceListening(status: String) {
        _uiState.update {
            it.copy(activeDialog = ActiveDialog.VoiceListening(partialText = "", statusText = status, errorText = null))
        }
    }

    fun onVoicePartialResult(text: String) {
        _uiState.update { st ->
            val cur = st.activeDialog
            if (cur is ActiveDialog.VoiceListening) {
                st.copy(activeDialog = cur.copy(partialText = text, errorText = null))
            } else {
                st
            }
        }
    }

    fun onVoiceListeningError(msg: String) {
        _uiState.update { st ->
            val cur = st.activeDialog
            if (cur is ActiveDialog.VoiceListening) {
                st.copy(activeDialog = cur.copy(errorText = msg))
            } else {
                st
            }
        }
    }

    fun switchToGoogleVoiceDialog() {
        closeDialog()
        _events.tryEmit(LauncherEvent.CancelVoiceRecognition)
        _events.tryEmit(LauncherEvent.RequestGoogleVoiceDialog)
    }

    fun dismissVoiceListening() {
        _events.tryEmit(LauncherEvent.CancelVoiceRecognition)
        if (_uiState.value.activeDialog is ActiveDialog.VoiceListening) {
            closeDialog()
        }
    }

    fun handleVoiceResults(cands: List<String>?) {
        if (_uiState.value.activeDialog is ActiveDialog.VoiceListening) {
            _uiState.update { it.copy(activeDialog = null) }
        }
        if (cands.isNullOrEmpty()) {
            toast(tr("Không nghe rõ, hãy thử lại", "Did not catch that, try again"))
            return
        }
        for (raw in cands) {
            val full = VoiceCommand.digitWords(VoiceCommand.norm(raw))
            if (full.isEmpty()) continue
            if (handleSpecialVoice(raw, full)) return

            val n = VoiceCommand.digitWords(VoiceCommand.stripVerb(VoiceCommand.norm(raw)))
            if (n.isEmpty()) continue
            var chPart = n
            var target: String? = null
            val m = VOICE_ON.matchEntire(n)
            if (m != null) {
                chPart = m.groupValues[1]
                target = m.groupValues[2]
            }
            if (looksLikeChannel(chPart)) {
                openChannelVoice(chPart, target)
                return
            }
            val a = findAppByVoice(n)
            if (a != null) {
                toast(tr("Mở ", "Opening ") + a.label)
                launchApp(a)
                return
            }
        }

        // General search fallback -> smart answer from Google AI
        val primaryQuery = cands.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (primaryQuery.isNotEmpty()) {
            askGoogleAi(primaryQuery)
        } else {
            toast(tr("Không nghe rõ, hãy thử lại", "Did not catch that, try again"))
        }
    }

    fun askGoogleAi(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val en = _uiState.value.isEnglish
        aiFetchJob?.cancel()
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.AiAnswerBoard(
                    query = q,
                    loading = true
                )
            )
        }
        aiFetchJob = viewModelScope.launch {
            val res = GeminiAiService.askStreaming(app, q, en) { partial ->
                _uiState.update { st ->
                    val cur = st.activeDialog
                    if (cur is ActiveDialog.AiAnswerBoard && cur.query == q) {
                        st.copy(
                            activeDialog = cur.copy(
                                answerText = partial,
                                loading = true,
                                errorMessage = null
                            )
                        )
                    } else {
                        st
                    }
                }
            }
            val cur = _uiState.value.activeDialog
            if (cur is ActiveDialog.AiAnswerBoard && cur.query == q) {
                _uiState.update {
                    it.copy(
                        activeDialog = cur.copy(
                            answerText = res.answerText.ifEmpty { cur.answerText },
                            spokenText = res.spokenText,
                            loading = false,
                            errorMessage = res.errorMessage,
                            isAppCheckError = res.isAppCheckError
                        )
                    )
                }
                if (res.spokenText.isNotBlank()) {
                    speak(res.spokenText)
                }
            }
        }
    }

    fun openAppCheckConfig(returnQuery: String? = null) {
        _uiState.update { it.copy(activeDialog = ActiveDialog.AppCheckConfig(returnQuery = returnQuery)) }
    }

    fun saveAppCheckDebugToken(token: String?, returnQuery: String? = null) {
        vn.launcher.tv.data.saveCustomDebugTokenAndReconfigure(app, token)
        val cleared = token.isNullOrBlank()
        toast(
            if (cleared) tr("Đã chuyển về App Check reCAPTCHA mặc định", "Switched back to default reCAPTCHA App Check")
            else tr("Đã lưu App Check Debug Token", "Saved App Check Debug Token")
        )
        if (!returnQuery.isNullOrBlank()) {
            askGoogleAi(returnQuery)
        } else {
            closeDialog()
        }
    }

    fun openExternalSearch(query: String, onYouTube: Boolean) {
        val q = query.trim()
        if (q.isEmpty()) return
        val msg = if (onYouTube) {
            tr("Đang tìm trên YouTube: $q", "Searching YouTube for $q")
        } else {
            tr("Đang tìm trên Google: $q", "Searching Google for $q")
        }
        toast(msg)
        speak(msg)
        _events.tryEmit(LauncherEvent.LaunchSearch(q, onYouTube = onYouTube))
    }

    private fun handleSpecialVoice(raw: String, full: String): Boolean {
        if (VoiceCommand.has(
                full, "may gio", "gio may", "ngay may", "ngay gio", "bay gio la", "hom nay ngay",
                "hom nay la ngay", "thoi gian", "am lich", "what time", "time is it", "what is the time",
                "what date", "today s date", "date today"
            )
        ) {
            speakDateTime()
            return true
        }
        if (VoiceCommand.has(full, "thoi tiet", "weather", "nhiet do", "troi co mua")) {
            val loc = VoiceCommand.parseWeatherLocation(raw, full)
            speakWeather(loc)
            return true
        }
        VoiceCommand.parsePriceIntent(raw, full)?.let { p ->
            openPrice(kind = p.kind, zone = p.zone, speakOnLoad = true, spokenLocation = p.spokenLocation)
            return true
        }
        VoiceCommand.parseAiPrompt(raw, full)?.let { aiQuery ->
            askGoogleAi(aiQuery)
            return true
        }
        VoiceCommand.parseSearchIntent(raw, full)?.let { s ->
            if (s.query.isEmpty()) {
                startSystemVoiceSearch()
            } else if (s.onYouTube || s.explicitBrowser) {
                openExternalSearch(s.query, onYouTube = s.onYouTube)
            } else {
                askGoogleAi(s.query)
            }
            return true
        }
        if (VoiceCommand.has(full, "tat nhac", "dung nhac", "tam dung nhac", "ngung nhac", "stop music", "pause music", "turn off music")) {
            voiceMusicOff()
            return true
        }
        if (VoiceCommand.has(full, "mo nhac", "bat nhac", "phat nhac", "play music", "start music", "turn on music")) {
            voiceMusicOn()
            return true
        }
        if (VoiceCommand.has(full, "cai dat", "mo cai dat", "settings", "open settings")) {
            openSettings()
            return true
        }
        return false
    }

    private fun voiceScore(a: AppItem, qk: String): Int {
        val lk = VoiceCommand.key(VoiceCommand.norm(a.label))
        var sc = VoiceCommand.scoreKeys(lk, qk)
        if (VoiceCommand.canonApp(lk) == qk) sc = maxOf(sc, 100)
        if (qk == "googleplay" && (a.pkg == "com.android.vending" || lk.contains("googleplay") ||
                lk.contains("chplay") || lk.contains("cuahangplay") || lk.contains("playstore"))
        ) {
            sc = maxOf(sc, 100)
        }
        return sc
    }

    private fun findAppByVoice(n: String): AppItem? {
        val qk = VoiceCommand.canonApp(VoiceCommand.key(n))
        var best: AppItem? = null
        var bestScore = 0
        for (a in _uiState.value.allApps) {
            if (a.isSelf) continue
            val sc = voiceScore(a, qk)
            if (sc > bestScore) {
                bestScore = sc
                best = a
            }
        }
        return if (bestScore >= 60) best else null
    }

    private fun looksLikeChannel(chPart: String): Boolean {
        val k = VoiceCommand.key(VoiceCommand.canonChannel(chPart))
        if (k.isEmpty()) return false
        return findChannelIn("fpt", k) != null || findChannelIn("vtvgo", k) != null ||
            findChannelIn("vieon", k) != null || k.matches(CHANNEL_REGEX)
    }

    private fun lookupChannel(list: String, k: String): Pair<String, String>? {
        for (e in list.split(",")) {
            val bar = e.indexOf('|')
            if (bar <= 0) continue
            val name = e.substring(0, bar).trim()
            val link = e.substring(bar + 1).trim()
            if (link.isNotEmpty() && VoiceCommand.key(VoiceCommand.norm(name)) == k) {
                return name to link
            }
        }
        return null
    }

    private fun findChannelIn(kind: String, k: String): Pair<String, String>? {
        if (kind == "vtvgo") return lookupChannel(vtvgoList(), k)
        if (kind == "vieon") return lookupChannel(vieonList(), k)
        for (i in 0..1) {
            if (VoiceCommand.key(VoiceCommand.norm(chName(i))) == k) return chName(i) to chLink(i)
        }
        lookupChannel(channelList(), k)?.let { return it }
        if (k.matches(CHANNEL_REGEX) && findByLabel("fpt play") != null) {
            val slug = if (k.startsWith("vtv") || k.startsWith("htv")) "$k-hd" else k
            return k.uppercase(Locale.ROOT) to "https://fptplay.vn/xem-truyen-hinh/$slug"
        }
        return null
    }

    private fun openChannelVoice(chPart: String, targetQuery: String?) {
        val k = VoiceCommand.key(VoiceCommand.canonChannel(chPart))
        val fpt = findByLabel("fpt play")
        val vtvgo = findByLabel("vtvgo")
        val vieon = findByLabel("vieon")
        val target = if (targetQuery != null) {
            findAppByVoice(targetQuery) ?: run {
                toast(tr("Không thấy ứng dụng: ", "App not found: ") + targetQuery)
                return
            }
        } else {
            fpt ?: vtvgo ?: vieon ?: run {
                toast(tr("Chưa cài FPT Play, VTVgo hay VieON", "FPT Play, VTVgo or VieON is not installed"))
                return
            }
        }
        val kind = when {
            target == vtvgo -> "vtvgo"
            target == vieon -> "vieon"
            isFpt(target) -> "fpt"
            else -> "other"
        }
        val ch = if (kind == "other") null else findChannelIn(kind, k)
        recordLaunch(target.pkg)
        if (ch == null) {
            toast(tr("Chưa có liên kết kênh ${k.uppercase(Locale.ROOT)}, mở ${target.label}", "No link for channel ${k.uppercase(Locale.ROOT)}, opening ${target.label}"))
            launchPlain(target)
            return
        }
        toast(tr("Mở kênh ", "Opening channel ") + "${ch.first} – ${target.label}")
        if (!openLinkIn(ch.second, target.pkg)) {
            toast(tr("${target.label} không nhận liên kết kênh này, đang mở ứng dụng", "${target.label} did not accept this channel link, opening the app"))
            launchPlain(target)
        }
    }

    private fun channelList(): String = prefs.getString("chList", DEFAULT_CHANNEL_LIST) ?: DEFAULT_CHANNEL_LIST
    private fun vtvgoList(): String = prefs.getString("chListVtvgo", defaultVtvgoList()) ?: defaultVtvgoList()
    private fun vieonList(): String = prefs.getString("chListVieon", defaultVieonList()) ?: defaultVieonList()

    fun saveVoiceChannels(fpt: String, vtvgo: String, vieon: String) {
        prefs.edit().putString("chList", fpt).putString("chListVtvgo", vtvgo).putString("chListVieon", vieon).apply()
        _uiState.update {
            it.copy(
                voiceChannelFpt = channelList(),
                voiceChannelVtvgo = vtvgoList(),
                voiceChannelVieon = vieonList()
            )
        }
    }

    fun resetVoiceChannels() {
        prefs.edit().remove("chList").remove("chListVtvgo").remove("chListVieon").apply()
        _uiState.update {
            it.copy(
                voiceChannelFpt = channelList(),
                voiceChannelVtvgo = vtvgoList(),
                voiceChannelVieon = vieonList()
            )
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        app.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun speak(text: String) {
        val clean = text.replace(Regex("(\\d)\\.(\\d{3})"), "$1$2").replace(Regex("(\\d)\\.(\\d{3})"), "$1$2")
        val existing = tts
        if (existing == null) {
            pendingSpeech = clean
            ttsReady = false
            val googlePkg = "com.google.android.tts"
            val useGoogle = isPackageInstalled(googlePkg)
            val initListener = TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    if (useGoogle) {
                        // Retry with system default TTS engine if Google TTS init failed
                        try {
                            tts?.shutdown()
                        } catch (_: Exception) {
                        }
                        tts = TextToSpeech(app) { st2 ->
                            onTtsReady(st2)
                        }
                    } else {
                        toast(tr("TV không có giọng đọc", "This TV has no text-to-speech voice"))
                    }
                    return@OnInitListener
                }
                onTtsReady(status)
            }
            tts = if (useGoogle) {
                TextToSpeech(app, initListener, googlePkg)
            } else {
                TextToSpeech(app, initListener)
            }
            return
        }
        if (!ttsReady) {
            pendingSpeech = clean
            return
        }
        existing.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "vl")
    }

    private fun onTtsReady(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            toast(tr("TV không có giọng đọc", "This TV has no text-to-speech voice"))
            return
        }
        try {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        } catch (_: Exception) {
        }
        val r = engine.setLanguage(if (_uiState.value.isEnglish) Locale.US else Locale.forLanguageTag("vi-VN"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast(tr("TV chưa có giọng đọc tiếng Việt (cài Google Text-to-speech)", "No voice for this language (install Google Text-to-speech)"))
            return
        }
        ttsReady = true
        pendingSpeech?.let {
            engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, "vl")
            pendingSpeech = null
        }
    }

    private fun speakDateTime() {
        val c = Calendar.getInstance()
        val hh = c.get(Calendar.HOUR_OF_DAY)
        val mm = c.get(Calendar.MINUTE)
        val d = c.get(Calendar.DAY_OF_MONTH)
        val mo = c.get(Calendar.MONTH) + 1
        val y = c.get(Calendar.YEAR)
        val dow = c.get(Calendar.DAY_OF_WEEK) - 1
        val en = _uiState.value.isEnglish

        val timeText = String.format(Locale.US, "%02d:%02d", hh, mm)
        val solarDate = if (en) "${DAY_EN[dow]}, ${MON_EN[mo - 1]} $d, $y" else "${THU_VI[dow]}, ngày $d tháng $mo năm $y"
        val lunarDate = try {
            LunarCalendar.format(d, mo, y, en)
        } catch (_: Exception) {
            ""
        }
        val holiday = try {
            Holidays.next(c, en)?.let { r ->
                when {
                    r.days == 0 -> if (en) "Today is ${r.name}" else "Hôm nay là ${r.name}"
                    en -> "${r.days} ${if (r.days == 1) "day" else "days"} until ${r.name}"
                    else -> "Còn ${r.days} ngày đến ${r.name}"
                }
            }
        } catch (_: Exception) {
            null
        }
        val lunarAlert = _uiState.value.header.lunarAlertText

        val spoken = if (en) {
            "It is $hh:${String.format(Locale.US, "%02d", mm)}. $solarDate."
        } else {
            val lunarSpoken = try {
                val l = LunarCalendar.solarToLunar(d, mo, y)
                " Âm lịch: ngày ${l[0]} tháng ${l[1]}" + (if (l[3] == 1) " nhuận" else "") + "."
            } catch (_: Exception) {
                ""
            }
            "Bây giờ là $hh giờ $mm phút, $solarDate.$lunarSpoken"
        }

        speak(spoken)
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.TimeBoard(
                    timeText = timeText,
                    solarDate = solarDate,
                    lunarDate = lunarDate,
                    holidayText = holiday,
                    lunarAlert = lunarAlert,
                    spokenSummary = spoken
                )
            )
        }
    }

    private fun speakWeather(spokenLocation: String? = null) {
        val eng = _uiState.value.isEnglish
        val cached = _uiState.value.weather
        val c = Calendar.getInstance()
        val dateText = if (eng) {
            "${DAY_EN[c.get(Calendar.DAY_OF_WEEK) - 1]}, ${c.get(Calendar.DAY_OF_MONTH)} ${MON_EN[c.get(Calendar.MONTH)]}"
        } else {
            "${THU_VI[c.get(Calendar.DAY_OF_WEEK) - 1]}, ${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.YEAR)}"
        }

        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.WeatherBoard(
                    location = spokenLocation ?: cached.city.ifEmpty { tr("Đang xác định vị trí…", "Locating…") },
                    dateText = dateText,
                    tempC = Math.round(cached.temp).toInt(),
                    minC = Math.round(cached.min).toInt(),
                    maxC = Math.round(cached.max).toInt(),
                    rainPct = cached.rain,
                    description = if (cached.hasData) WeatherService.describe(cached.code, eng).second else tr("Đang tải thời tiết…", "Loading weather…"),
                    weatherCode = cached.code,
                    spokenSummary = "",
                    loading = true
                )
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            var coords = wxManualCoords()
            var label = prefs.getString("wxLabel", "")
            if (!spokenLocation.isNullOrBlank()) {
                val resolved = AdminData.resolveLocation(spokenLocation)
                if (resolved?.lat != null && resolved.lon != null) {
                    coords = resolved.lat to resolved.lon
                    label = resolved.displayLabel
                } else {
                    label = spokenLocation
                }
            }
            val d = try {
                WeatherService.fetch(app, eng, coords, label)
            } catch (_: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (d != null) {
                    if (spokenLocation.isNullOrBlank()) {
                        prefs.edit()
                            .putFloat("w_temp", d.temp.toFloat())
                            .putFloat("w_min", d.min.toFloat())
                            .putFloat("w_max", d.max.toFloat())
                            .putInt("w_code", d.code)
                            .putInt("w_rain", d.rain)
                            .putString("w_city", d.city)
                            .putLong("w_time", System.currentTimeMillis())
                            .apply()
                        showCachedWeather()
                    }
                    showWeatherBoardResult(d.temp, d.min, d.max, d.code, d.rain, d.city, dateText)
                } else if (spokenLocation.isNullOrBlank() && _uiState.value.weather.hasData) {
                    val w = _uiState.value.weather
                    showWeatherBoardResult(w.temp, w.min, w.max, w.code, w.rain, w.city, dateText)
                } else {
                    toast(tr("Không lấy được thời tiết", "Could not get the weather"))
                    closeDialog()
                }
            }
        }
    }

    private fun showWeatherBoardResult(t: Double, mn: Double, mx: Double, code: Int, rain: Int, city: String, dateText: String) {
        val desc = WeatherService.describe(code, _uiState.value.isEnglish).second
        val where = city.ifEmpty { tr("vị trí hiện tại", "your current location") }
        val text = if (_uiState.value.isEnglish) {
            String.format(
                Locale.US,
                "Weather in %s: %s, %d degrees, low %d, high %d, chance of rain %d percent.",
                where, desc, Math.round(t), Math.round(mn), Math.round(mx), rain
            )
        } else {
            "Thời tiết tại $where: $desc, nhiệt độ ${Math.round(t)} độ, thấp nhất ${Math.round(mn)} độ, cao nhất ${Math.round(mx)} độ, khả năng mưa $rain phần trăm."
        }
        speak(text)
        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.WeatherBoard(
                    location = where,
                    dateText = dateText,
                    tempC = Math.round(t).toInt(),
                    minC = Math.round(mn).toInt(),
                    maxC = Math.round(mx).toInt(),
                    rainPct = rain,
                    description = desc,
                    weatherCode = code,
                    spokenSummary = text,
                    loading = false
                )
            )
        }
    }

    private fun sendMediaKey(code: Int) {
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (_: Exception) {
        }
    }

    private fun voiceMusicOff() {
        if (_uiState.value.music.isPlaying) toggleMusicPlay()
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        toast(tr("Đã tắt nhạc", "Music paused"))
    }

    private fun voiceMusicOn() {
        val m = _uiState.value.music
        if (m.currentIndex >= 0) {
            if (player == null) playTrack(m.currentIndex) else if (!m.isPlaying) toggleMusicPlay()
            toast(tr("Đang phát nhạc", "Playing music"))
            return
        }
        withMediaPermission {
            loadTracks(false) {
                if (_uiState.value.music.tracks.isNotEmpty()) {
                    playTrack(0)
                    toast(tr("Đang phát nhạc", "Playing music"))
                } else {
                    sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                    toast(tr("Không có nhạc trong máy, gửi lệnh phát tới ứng dụng đang mở", "No local music, sent play to the active app"))
                }
            }
        }
    }

    // ---------------------------------------------------------------- Prices
    private fun priceUrl(kind: PriceKind): String = when (kind) {
        PriceKind.GOLD -> prefs.getString("urlGold", "https://webgia.com/gia-vang/sjc/") ?: "https://webgia.com/gia-vang/sjc/"
        PriceKind.COFFEE -> prefs.getString("urlCoffee", "https://giacaphe.com/") ?: "https://giacaphe.com/"
        PriceKind.PEPPER -> prefs.getString("urlPepper", "https://giacaphe.com/gia-tieu-hom-nay/") ?: "https://giacaphe.com/gia-tieu-hom-nay/"
        PriceKind.FUEL -> prefs.getString("urlFuel", "https://webgia.com/gia-xang-dau/petrolimex/") ?: "https://webgia.com/gia-xang-dau/petrolimex/"
    }

    fun openPrice(
        kind: PriceKind,
        zone: Int = 0,
        speakOnLoad: Boolean = false,
        spokenLocation: String? = null
    ) {
        currentPriceKind = kind
        currentPriceZone = zone
        currentSpokenLocation = spokenLocation
        currentResolvedProvince = null
        priceSections = emptyList()
        priceMatchedRows = emptyList()
        priceUpdatedDate = PriceRepository.todayDateFormatted()
        priceSpokenForOpen = false
        val targetUrl = priceUrl(kind)

        _uiState.update {
            it.copy(
                activeDialog = ActiveDialog.PriceViewer(
                    kind = kind,
                    zone = zone,
                    speakOnLoad = speakOnLoad,
                    url = targetUrl,
                    spokenLocation = spokenLocation,
                    resolvedProvince = null,
                    updatedDate = priceUpdatedDate,
                    sourceLabel = if (kind == PriceKind.COFFEE || kind == PriceKind.PEPPER) "giacaphe.com" else "webgia.com",
                    loading = true
                )
            )
        }
        fetchPriceInBackground(kind, targetUrl, spokenLocation, speakOnLoad)
    }

    fun refreshCurrentPrice() {
        val cur = _uiState.value.activeDialog as? ActiveDialog.PriceViewer ?: return
        priceSpokenForOpen = false
        _uiState.update {
            it.copy(activeDialog = cur.copy(loading = true, errorMessage = null))
        }
        fetchPriceInBackground(cur.kind, cur.url, cur.spokenLocation, speakOnLoad = false)
    }

    private fun fetchPriceInBackground(
        kind: PriceKind,
        url: String,
        spokenLocation: String?,
        speakOnLoad: Boolean
    ) {
        priceFetchJob?.cancel()
        priceFetchJob = viewModelScope.launch(Dispatchers.IO) {
            var resolvedProv: String? = null
            var displayLoc = spokenLocation
            if (!spokenLocation.isNullOrBlank()) {
                val res = AdminData.resolveLocation(spokenLocation)
                if (res != null) {
                    resolvedProv = res.provinceHint
                    if (res.displayLabel.isNotBlank()) displayLoc = res.displayLabel
                } else {
                    resolvedProv = AdminData.findProvinceFuzzy(spokenLocation)
                }
            }
            val result = PriceRepository.fetch(kind, url)
            val matched = PriceRepository.findMatchedRows(result.sections, displayLoc ?: spokenLocation, resolvedProv)

            withContext(Dispatchers.Main) {
                val cur = _uiState.value.activeDialog
                if (cur is ActiveDialog.PriceViewer && cur.kind == kind) {
                    currentSpokenLocation = displayLoc
                    currentResolvedProvince = resolvedProv
                    if (result.sections.isNotEmpty()) {
                        priceSections = result.sections
                        priceMatchedRows = matched
                        priceUpdatedDate = result.updatedDate

                        _uiState.update {
                            it.copy(
                                activeDialog = cur.copy(
                                    spokenLocation = displayLoc,
                                    resolvedProvince = resolvedProv,
                                    updatedDate = result.updatedDate,
                                    sourceLabel = result.sourceLabel.ifEmpty { cur.sourceLabel },
                                    sections = result.sections,
                                    matchedRows = matched,
                                    loading = false,
                                    errorMessage = null
                                )
                            )
                        }
                        if (speakOnLoad && !priceSpokenForOpen) {
                            priceSpokenForOpen = true
                            speak(
                                PriceRepository.summarizeForSpeech(
                                    kind = kind,
                                    zone = currentPriceZone,
                                    sections = result.sections,
                                    matchedRows = matched,
                                    spokenLocation = displayLoc,
                                    resolvedProvince = resolvedProv,
                                    updatedDate = result.updatedDate,
                                    isEnglish = _uiState.value.isEnglish
                                )
                            )
                        }
                    } else if (cur.sections.isNotEmpty()) {
                        val refreshedMatch = PriceRepository.findMatchedRows(cur.sections, displayLoc ?: spokenLocation, resolvedProv)
                        priceMatchedRows = refreshedMatch
                        _uiState.update {
                            it.copy(
                                activeDialog = cur.copy(
                                    spokenLocation = displayLoc,
                                    resolvedProvince = resolvedProv,
                                    matchedRows = refreshedMatch,
                                    loading = false
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun savePriceUrls(gold: String, coffee: String, pepper: String, fuel: String) {
        prefs.edit().putString("urlGold", gold).putString("urlCoffee", coffee)
            .putString("urlPepper", pepper).putString("urlFuel", fuel).apply()
        _uiState.update { it.copy(priceUrls = PriceKind.entries.associateWith { k -> priceUrl(k) }) }
    }

    fun resetPriceUrls() {
        prefs.edit().remove("urlGold").remove("urlCoffee").remove("urlPepper").remove("urlFuel").apply()
        _uiState.update { it.copy(priceUrls = PriceKind.entries.associateWith { k -> priceUrl(k) }) }
    }

    fun onPriceJsonExtracted(jsonValue: String, speakOnLoad: Boolean, extractedFromUrl: String? = null): Boolean {
        val parsed = PriceRepository.parseJsonPayload(currentPriceKind, jsonValue) ?: return false
        if (parsed.sections.isEmpty()) return false
        val currentViewer = _uiState.value.activeDialog as? ActiveDialog.PriceViewer
        if (currentPriceKind == PriceKind.COFFEE && currentViewer != null) {
            val curDomCount = currentViewer.sections
                .filter { PriceRepository.isDomesticCoffeeSection(it) }
                .sumOf { it.rows.size }
            val newDomCount = parsed.sections
                .filter { PriceRepository.isDomesticCoffeeSection(it) }
                .sumOf { it.rows.size }
            if (curDomCount > 0 && newDomCount < curDomCount) {
                return false
            }
        }
        val matched = PriceRepository.findMatchedRows(parsed.sections, currentSpokenLocation, currentResolvedProvince)
        priceSections = parsed.sections
        priceMatchedRows = matched
        priceUpdatedDate = parsed.updatedDate
        val host = try {
            if (!extractedFromUrl.isNullOrBlank()) java.net.URL(extractedFromUrl).host.removePrefix("www.") else ""
        } catch (_: Exception) {
            ""
        }

        _uiState.update { st ->
            val cur = st.activeDialog as? ActiveDialog.PriceViewer ?: return@update st
            st.copy(
                activeDialog = cur.copy(
                    sections = parsed.sections,
                    matchedRows = matched,
                    updatedDate = parsed.updatedDate,
                    sourceLabel = host.ifEmpty { cur.sourceLabel },
                    loading = false,
                    errorMessage = null
                )
            )
        }
        if (speakOnLoad && !priceSpokenForOpen) {
            priceSpokenForOpen = true
            speak(
                PriceRepository.summarizeForSpeech(
                    kind = currentPriceKind,
                    zone = currentPriceZone,
                    sections = parsed.sections,
                    matchedRows = matched,
                    spokenLocation = currentSpokenLocation,
                    resolvedProvince = currentResolvedProvince,
                    updatedDate = parsed.updatedDate,
                    isEnglish = _uiState.value.isEnglish
                )
            )
        }
        return true
    }

    fun onPriceExtractionFailed() {
        _uiState.update { st ->
            val cur = st.activeDialog as? ActiveDialog.PriceViewer ?: return@update st
            st.copy(activeDialog = cur.copy(loading = false))
        }
    }

    fun speakPriceRow(row: List<String>) {
        if (row.isEmpty()) return
        val area = row[0].trim()
        val vals = row.drop(1).filter { it.isNotBlank() }.joinToString(", ")
        if (area.isNotEmpty() && vals.isNotEmpty()) {
            speak("$area: $vals")
        } else if (area.isNotEmpty()) {
            speak(area)
        }
    }

    fun speakCurrentPrices() {
        speak(
            PriceRepository.summarizeForSpeech(
                kind = currentPriceKind,
                zone = currentPriceZone,
                sections = priceSections,
                matchedRows = priceMatchedRows,
                spokenLocation = currentSpokenLocation,
                resolvedProvince = currentResolvedProvince,
                updatedDate = priceUpdatedDate,
                isEnglish = _uiState.value.isEnglish
            )
        )
    }

    // ---------------------------------------------------------------- Key Shortcuts
    private fun readKeyMap(): Map<Int, String> {
        val m = LinkedHashMap<Int, String>()
        val raw = prefs.getString("keymap", "") ?: ""
        for (e in raw.split(",")) {
            val i = e.indexOf(':')
            if (i <= 0) continue
            val code = e.substring(0, i).toIntOrNull() ?: continue
            m[code] = e.substring(i + 1)
        }
        return m
    }

    private fun saveKeyMap(m: Map<Int, String>) {
        val s = m.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().putString("keymap", s).apply()
        _uiState.update { it.copy(keyShortcuts = m) }
    }

    fun assignKeyShortcut(keyCode: Int, pkg: String, label: String): Boolean {
        if (isReservedKey(keyCode)) {
            toast(tr("Phím này dùng để điều hướng, hãy chọn phím khác", "This key is used for navigation, pick another"))
            return false
        }
        val m = readKeyMap().toMutableMap()
        m[keyCode] = pkg
        saveKeyMap(m)
        closeDialog()
        toast("${keyName(keyCode)} → $label")
        return true
    }

    fun removeKeyShortcut(keyCode: Int) {
        val m = readKeyMap().toMutableMap()
        m.remove(keyCode)
        saveKeyMap(m)
        closeDialog()
        toast(tr("Đã xóa", "Removed"))
    }

    fun isVoiceKeyShortcut(keyCode: Int): Boolean {
        val pkg = _uiState.value.keyShortcuts[keyCode]
        return pkg == "@voice" || pkg == "@assistant"
    }

    fun launchByKey(keyCode: Int): Boolean {
        val pkg = _uiState.value.keyShortcuts[keyCode] ?: return false
        if (pkg == "@voice") {
            if (_uiState.value.slideshow.fullscreenOpen) closeFullscreenSlideshow()
            startVoiceCommand()
            return true
        }
        if (pkg == "@assistant") {
            if (_uiState.value.slideshow.fullscreenOpen) closeFullscreenSlideshow()
            startSystemVoiceSearch()
            return true
        }
        val item = _uiState.value.allApps.firstOrNull { it.pkg == pkg } ?: return false
        launchApp(item)
        return true
    }

    fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "")

    fun appLabelOf(pkg: String): String {
        if (pkg == "@voice") return tr("Trợ lý giọng nói (nói lệnh & Google AI)", "Voice Assistant (commands & AI)")
        if (pkg == "@assistant") return "Google Assistant"
        return try {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun isReservedKey(k: Int): Boolean = when (k) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_UNKNOWN -> true
        else -> false
    }

    // ---------------------------------------------------------------- Helpers
    private fun appVersion(): String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "3.0.0"
    } catch (_: Exception) {
        "3.0.0"
    }

    private fun readBluetoothStatus(en: Boolean): String = try {
        val bm = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val ad = bm?.adapter
        when {
            ad == null -> if (en) "Not available" else "Không có"
            ad.isEnabled -> if (en) "On" else "Đang bật"
            else -> if (en) "Off" else "Đã tắt"
        }
    } catch (_: Throwable) {
        "—"
    }

    private fun localIp(): String? = lanIp()

    private fun lanIp(): String? {
        var fallback: String? = null
        try {
            val nis = NetworkInterface.getNetworkInterfaces() ?: return null
            while (nis.hasMoreElements()) {
                val ni = nis.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val n = ni.name
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        if (n.startsWith("wlan") || n.startsWith("eth")) return a.hostAddress
                        if (fallback == null) fallback = a.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return fallback
    }

    override fun onCleared() {
        super.onCleared()
        stopUploadServer()
        releasePlayer()
        tts?.shutdown()
        sounds?.release()
    }

    companion object {
        private val DOCK_PREF = arrayOf("youtube", "fpt play", "tv360", "vtv")
        private const val COLUMNS = 4
        private const val DEFAULT_FPT_LINK = "https://fptplay.vn/trang/channel"
        private val DEFAULT_CH = arrayOf(
            arrayOf("THVL1", "https://fptplay.vn/xem-truyen-hinh/thvl1"),
            arrayOf("VTV1", "https://fptplay.vn/xem-truyen-hinh/vtv1-hd")
        )
        private val THU_VI = arrayOf("Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy")
        private val DAY_EN = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        private val MON_EN = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        private val CHANNEL_REGEX = Regex("(vtv|htv)\\d{1,2}|(thvl|vtc|antv|vov)\\d{0,2}|vtvcantho|quochoi|nhandan")
        private val VOICE_ON = Regex("^(.*?)\\s+(?:tren ung dung|tren|trong|bang|qua|on|in|using|with)\\s+(.+)$")

        private const val DEFAULT_CHANNEL_LIST =
            "THVL1|https://fptplay.vn/xem-truyen-hinh/thvl1, THVL2|https://fptplay.vn/xem-truyen-hinh/thvl2, " +
                "VTV1|https://fptplay.vn/xem-truyen-hinh/vtv1-hd, VTV2|https://fptplay.vn/xem-truyen-hinh/vtv2-hd, " +
                "VTV3|https://fptplay.vn/xem-truyen-hinh/vtv3-hd, VTV4|https://fptplay.vn/xem-truyen-hinh/vtv4-hd, " +
                "VTV5|https://fptplay.vn/xem-truyen-hinh/vtv5-hd, VTV6|https://fptplay.vn/xem-truyen-hinh/vtv6-hd, " +
                "VTV7|https://fptplay.vn/xem-truyen-hinh/vtv7-hd, VTV8|https://fptplay.vn/xem-truyen-hinh/vtv8-hd, " +
                "VTV9|https://fptplay.vn/xem-truyen-hinh/vtv9-hd, HTV7|https://fptplay.vn/xem-truyen-hinh/htv7-hd, " +
                "HTV9|https://fptplay.vn/xem-truyen-hinh/htv9-hd"

        private fun defaultVtvgoList(): String = (1..9).joinToString(", ") { i ->
            "VTV$i|https://vtvgo.vn/xem-truc-tuyen-kenh-vtv$i-$i.html"
        }

        private fun defaultVieonList(): String = (1..9).joinToString(", ") { i ->
            "VTV$i|https://vieon.vn/truyen-hinh-truc-tuyen/vtv$i-hd"
        }
    }
}
