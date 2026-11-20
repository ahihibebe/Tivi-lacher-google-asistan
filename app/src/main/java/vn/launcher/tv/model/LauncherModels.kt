package vn.launcher.tv.model

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import vn.launcher.tv.data.AdminData
import vn.launcher.tv.data.MediaLibrary

enum class LauncherTheme(val id: Int) {
    MAC_OS(0),
    TULIP_BLUE(1),
    TULIP_PURPLE(2);

    companion object {
        fun fromId(id: Int): LauncherTheme = entries.firstOrNull { it.id == id } ?: MAC_OS
    }
}

enum class DockTab(val index: Int) {
    APPS(0),
    RECENTS(1),
    MOST_USED(2)
}

enum class PriceKind(val key: String) {
    GOLD("gold"),
    COFFEE("coffee"),
    PEPPER("pepper"),
    FUEL("fuel")
}

data class AppItem(
    val label: String,
    val pkg: String,
    val cls: String,
    val image: Drawable?,
    val isBanner: Boolean,
    val isSelf: Boolean
)

data class TvChannelSlot(
    val slot: Int,
    val name: String,
    val link: String
)

data class WeatherState(
    val enabled: Boolean = true,
    val hasData: Boolean = false,
    val temp: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val code: Int = 0,
    val rain: Int = 0,
    val city: String = ""
)

data class SystemBarsState(
    val enabled: Boolean = true,
    val storageUsedGb: Double = 0.0,
    val storageTotalGb: Double = 0.0,
    val storagePercent: Int = 0,
    val ramUsedGb: Double = 0.0,
    val ramTotalGb: Double = 0.0,
    val ramPercent: Int = 0
)

data class HeaderState(
    val greeting: String = "",
    val timeText: String = "",
    val dateText: String = "",
    val lunarText: String = "",
    val holidayText: String? = null,
    val lunarAlertText: String? = null
)

data class NetworkPillState(
    val visible: Boolean = true,
    val label: String = "Wi-Fi",
    val isEthernet: Boolean = false,
    val isOffline: Boolean = false
)

data class NowPlayingPillState(
    val visible: Boolean = false,
    val text: String = "",
    val isInternal: Boolean = false,
    val pendingIntent: PendingIntent? = null
)

data class MusicPlayerState(
    val tracks: List<MediaLibrary.Track> = emptyList(),
    val tracksLoaded: Boolean = false,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val coverArt: Bitmap? = null
)

data class SlideshowState(
    val cardEnabled: Boolean = true,
    val uris: List<Uri> = emptyList(),
    val loaded: Boolean = false,
    val currentIndex: Int = -1,
    val cardBitmap: Bitmap? = null,
    val hintText: String? = null,
    val fullscreenOpen: Boolean = false,
    val fullscreenIndex: Int = 0,
    val fullscreenBitmap: Bitmap? = null,
    val fullscreenPaused: Boolean = false
)

data class DisplayPrefs(
    val tvOn: Boolean = true,
    val musicFrameOn: Boolean = true,
    val photoFrameOn: Boolean = true,
    val weatherOn: Boolean = true,
    val wifiOn: Boolean = true,
    val voiceOn: Boolean = true,
    val musicOn: Boolean = true,
    val barsOn: Boolean = true,
    val holidayOn: Boolean = true,
    val lunarAlertOn: Boolean = true,
    val nameOn: Boolean = true,
    val soundOn: Boolean = true
)

data class PriceSection(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class PriceMatchRow(
    val sectionTitle: String,
    val area: String,
    val values: List<String>
)

sealed interface ActiveDialog {
    data object SettingsMain : ActiveDialog
    data object ThemeMenu : ActiveDialog
    data object WallpaperMenu : ActiveDialog
    data object WallpaperOpacity : ActiveDialog
    data class QrUpload(val url: String, val qrBitmap: Bitmap?) : ActiveDialog
    data class LocalWallpapers(val files: List<java.io.File>, val folderPath: String) : ActiveDialog
    data object LanguageMenu : ActiveDialog
    data object EditUserName : ActiveDialog
    data object AboutInfo : ActiveDialog
    data object TvFrameMenu : ActiveDialog
    data class EditChannel(val slot: Int, val name: String, val link: String) : ActiveDialog
    data object VoiceChannelsEdit : ActiveDialog
    data object KeyMapMenu : ActiveDialog
    data object PickAppForKey : ActiveDialog
    data class CaptureKey(val pkg: String, val label: String) : ActiveDialog
    data class ConfirmRemoveKey(val keyCode: Int, val keyName: String, val appLabel: String) : ActiveDialog
    data object FptLinkEdit : ActiveDialog
    data object PriceMenu : ActiveDialog
    data object EditPriceUrls : ActiveDialog
    data class PriceViewer(
        val kind: PriceKind,
        val zone: Int,
        val speakOnLoad: Boolean,
        val url: String,
        val spokenLocation: String? = null,
        val resolvedProvince: String? = null,
        val updatedDate: String = "",
        val sourceLabel: String = "",
        val sections: List<PriceSection> = emptyList(),
        val matchedRows: List<PriceMatchRow> = emptyList(),
        val loading: Boolean = true,
        val errorMessage: String? = null
    ) : ActiveDialog
    data class VoiceListening(
        val partialText: String = "",
        val statusText: String = "",
        val errorText: String? = null
    ) : ActiveDialog
    data class TimeBoard(
        val timeText: String,
        val solarDate: String,
        val lunarDate: String,
        val holidayText: String?,
        val lunarAlert: String?,
        val spokenSummary: String
    ) : ActiveDialog
    data class WeatherBoard(
        val location: String,
        val dateText: String,
        val tempC: Int,
        val minC: Int,
        val maxC: Int,
        val rainPct: Int,
        val description: String,
        val weatherCode: Int,
        val spokenSummary: String,
        val loading: Boolean = false
    ) : ActiveDialog
    data class AiAnswerBoard(
        val query: String,
        val answerText: String = "",
        val spokenText: String = "",
        val loading: Boolean = true,
        val errorMessage: String? = null
    ) : ActiveDialog
    data object DisplayMenu : ActiveDialog
    data object ChooseProvince : ActiveDialog
    data class ChooseWard(
        val province: AdminData.Province,
        val wards: List<AdminData.Ward>?,
        val loading: Boolean,
        val error: Boolean
    ) : ActiveDialog
    data class ManualWardInput(val province: AdminData.Province) : ActiveDialog
    data object MediaSettings : ActiveDialog
    data object ChooseSlideSeconds : ActiveDialog
    data class ChooseSlideFolders(val albums: List<MediaLibrary.Album>, val selectedIds: Set<String>) : ActiveDialog
    data object DefaultLauncherMenu : ActiveDialog
    data class AdbHelp(val title: String, val intro: String, val command: String) : ActiveDialog
    data object HiddenAppsMenu : ActiveDialog
    data object MusicPanel : ActiveDialog
    data object ConfirmPowerOff : ActiveDialog
    data class TileActions(val app: AppItem) : ActiveDialog
    data class SpeechPopup(val title: String, val message: String) : ActiveDialog
    data class CrashReport(val trace: String) : ActiveDialog
}

data class LauncherUiState(
    val lang: String = "vi",
    val theme: LauncherTheme = LauncherTheme.MAC_OS,
    val safeMode: Boolean = false,
    val userName: String = "",
    val appVersion: String = "3.0.0",
    val bgSpec: String = "preset:0",
    val bgAlphaPercent: Int = 100,
    val customWallpaperVersion: Int = 0,
    val display: DisplayPrefs = DisplayPrefs(),
    val header: HeaderState = HeaderState(),
    val weather: WeatherState = WeatherState(),
    val sysBars: SystemBarsState = SystemBarsState(),
    val networkPill: NetworkPillState = NetworkPillState(),
    val usbConnected: Boolean = false,
    val nowPlayingPill: NowPlayingPillState = NowPlayingPillState(),
    val tvChannels: List<TvChannelSlot> = emptyList(),
    val allApps: List<AppItem> = emptyList(),
    val dockTab: DockTab = DockTab.APPS,
    val dockApps: List<AppItem> = emptyList(),
    val gridApps: List<AppItem> = emptyList(),
    val focusedLabel: String = "",
    val movingPkg: String? = null,
    val music: MusicPlayerState = MusicPlayerState(),
    val slideshow: SlideshowState = SlideshowState(),
    val slideSeconds: Int = 8,
    val activeDialog: ActiveDialog? = null,
    val bluetoothStatus: String = "—",
    val keyShortcuts: Map<Int, String> = emptyMap(),
    val hiddenPackages: Set<String> = emptySet(),
    val fptLink: String = "",
    val voiceChannelFpt: String = "",
    val voiceChannelVtvgo: String = "",
    val voiceChannelVieon: String = "",
    val priceUrls: Map<PriceKind, String> = emptyMap(),
    val wxProvinceLabel: String = "",
    val wxWardLabel: String = "—",
    val volumeLevel: Int = 0,
    val volumeMax: Int = 15,
    val localIp: String? = null
) {
    val isEnglish: Boolean get() = lang == "en"
}
