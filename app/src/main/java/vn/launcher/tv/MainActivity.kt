package vn.launcher.tv

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.launcher.tv.data.CrashGuard
import vn.launcher.tv.data.configureAppCheck
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.ui.screens.ActiveDialogHost
import vn.launcher.tv.ui.screens.FullscreenSlideshowDialog
import vn.launcher.tv.ui.screens.HomeScreen
import vn.launcher.tv.ui.theme.VietLauncherTheme
import vn.launcher.tv.viewmodel.LauncherEvent
import vn.launcher.tv.viewmodel.LauncherViewModel
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private var speechRecognizer: SpeechRecognizer? = null

    private val voiceIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            vm.handleVoiceResults(matches)
        }
    }

    private val systemVoiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* system voice search handles its own UI */ }

    private val pickWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.applyCustomWallpaperFromUri(uri)
        }
    }

    private val openDocWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.applyCustomWallpaperFromUri(uri)
        }
    }

    private val defaultHomeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val en = vm.uiState.value.isEnglish
        if (result.resultCode == RESULT_OK) {
            showToast(if (en) "Set as default launcher" else "Đã đặt làm launcher mặc định")
        }
    }

    private val mediaPermsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        vm.onMediaPermissionsResult(results.values.isNotEmpty() && results.values.all { it })
    }

    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchVoiceRecognizer()
        } else {
            val en = vm.uiState.value.isEnglish
            showToast(if (en) "Microphone permission required for voice commands" else "Cần cấp quyền micro cho lệnh giọng nói")
            launchRecognizerIntentFallback()
        }
    }

    private val locationPermsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            vm.maybeFetchWeather(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuard.install(applicationContext)
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        askLocationOnceIfNeeded()

        setContent {
            val state by vm.uiState.collectAsStateWithLifecycle()
            val scrollState = rememberScrollState()
            val scope = rememberCoroutineScope()

            LaunchedEffect(vm) {
                vm.events.collect { event ->
                    when (event) {
                        is LauncherEvent.ShowToast -> showToast(event.message)
                        is LauncherEvent.LaunchIntent -> launchIntentSafely(event.intent)
                        is LauncherEvent.LaunchIntentsWithFallback -> launchWithFallback(event.intents)
                        LauncherEvent.RequestVoiceRecognition -> startVoiceRecognitionFlow()
                        LauncherEvent.RequestGoogleVoiceDialog -> launchRecognizerIntentFallback()
                        LauncherEvent.CancelVoiceRecognition -> destroySpeechRecognizer()
                        LauncherEvent.RequestSystemVoiceSearch -> launchSystemVoiceSearch()
                        is LauncherEvent.LaunchSearch -> launchVoiceSearchQuery(event.query, event.onYouTube)
                        LauncherEvent.RequestWallpaperPicker -> launchWallpaperPicker()
                        LauncherEvent.RequestDefaultHomeRole -> launchDefaultHomeRole()
                        is LauncherEvent.RequestMediaPermissions -> mediaPermsLauncher.launch(event.perms)
                        is LauncherEvent.RequestAudioPermission -> audioPermLauncher.launch(event.perm)
                        LauncherEvent.RequestLocationPermission -> locationPermsLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        LauncherEvent.ScrollHomeTop -> scope.launch { scrollState.animateScrollTo(0) }
                    }
                }
            }

            BackHandler(
                enabled = scrollState.value > 40 &&
                    state.activeDialog == null &&
                    !state.slideshow.fullscreenOpen
            ) {
                scope.launch { scrollState.animateScrollTo(0) }
            }

            VietLauncherTheme(theme = state.theme) {
                HomeScreen(
                    state = state,
                    scrollState = scrollState,
                    onTileFocused = vm::onTileFocused,
                    onMoveSound = vm::playMove,
                    onAppClick = vm::launchApp,
                    onAppLongClick = vm::openTileMenu,
                    onChannelClick = vm::openChannelSlot,
                    onChannelLongClick = { slot ->
                        val cur = state.tvChannels.getOrNull(slot)
                        vm.openDialog(
                            ActiveDialog.EditChannel(
                                slot = slot,
                                name = cur?.name.orEmpty(),
                                link = cur?.link.orEmpty()
                            )
                        )
                    },
                    onVoiceClick = vm::startVoiceCommand,
                    onVoiceLongClick = vm::startSystemVoiceSearch,
                    onUsbClick = vm::openStorageSettings,
                    onWifiClick = vm::openNetworkSettings,
                    onSettingsClick = vm::openSettings,
                    onNowPlayingClick = vm::onNowPlayingClick,
                    onSlideshowClick = vm::openSlideshow,
                    onSlideshowLongClick = vm::chooseSlideFolders,
                    onTabSelected = vm::selectDockTab
                )

                ActiveDialogHost(
                    state = state,
                    vm = vm,
                    onPickDeviceImage = ::launchWallpaperPicker
                )

                if (state.slideshow.fullscreenOpen) {
                    FullscreenSlideshowDialog(
                        slideshow = state.slideshow,
                        theme = state.theme,
                        onStep = vm::stepFullscreenSlideshow,
                        onTogglePause = vm::toggleFullscreenSlideshowPause,
                        onClose = vm::closeFullscreenSlideshow
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        destroySpeechRecognizer()
        super.onDestroy()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val state = vm.uiState.value
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            state.activeDialog !is ActiveDialog.CaptureKey
        ) {
            if (vm.isVoiceKeyShortcut(code)) {
                return vm.launchByKey(code)
            }
            if (code == KeyEvent.KEYCODE_VOICE_ASSIST ||
                code == KeyEvent.KEYCODE_ASSIST ||
                code == KeyEvent.KEYCODE_SEARCH
            ) {
                vm.startVoiceCommand()
                return true
            }
        }

        if (state.activeDialog == null && !state.slideshow.fullscreenOpen) {

            if (state.movingPkg != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            vm.moveBy(-1)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            vm.moveBy(1)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            vm.moveBy(-4)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            vm.moveBy(4)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            if (event.repeatCount == 0) vm.endMove()
                            return true
                        }
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> return true
                    }
                }
            } else if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (vm.launchByKey(code)) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun askLocationOnceIfNeeded() {
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        if (prefs.getBoolean("locAsked", false)) return
        val hasFine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) return
        val st = vm.uiState.value
        if (st.weather.enabled || st.networkPill.visible) {
            prefs.edit().putBoolean("locAsked", true).apply()
            locationPermsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun launchIntentSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            val en = vm.uiState.value.isEnglish
            showToast(if (en) "Cannot open this app or link" else "Không mở được ứng dụng hoặc liên kết này")
        }
    }

    private fun launchWithFallback(intents: List<Intent>) {
        for (intent in intents) {
            try {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) {
            }
        }
        val en = vm.uiState.value.isEnglish
        showToast(if (en) "Could not open system screen" else "Không mở được màn hình cài đặt này")
    }

    private fun launchWallpaperPicker() {
        try {
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) {
                pickWallpaperLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                openDocWallpaperLauncher.launch(arrayOf("image/*"))
            }
        } catch (_: Exception) {
            try {
                openDocWallpaperLauncher.launch(arrayOf("image/*"))
            } catch (_: Exception) {
                val en = vm.uiState.value.isEnglish
                showToast(if (en) "No photo picker available" else "Không mở được trình chọn ảnh")
            }
        }
    }

    private fun launchDefaultHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    defaultHomeRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            } catch (_: Exception) {
            }
        }
        launchWithFallback(
            listOf(
                Intent(android.provider.Settings.ACTION_HOME_SETTINGS),
                Intent(android.provider.Settings.ACTION_SETTINGS)
            )
        )
    }

    private fun startVoiceRecognitionFlow() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        launchVoiceRecognizer()
    }

    private fun launchVoiceRecognizer() {
        val en = vm.uiState.value.isEnglish
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            launchRecognizerIntentFallback()
            return
        }
        try {
            destroySpeechRecognizer()
            vm.showVoiceListening(if (en) "Connecting microphone…" else "Đang bật micro…")
            val sr = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    vm.showVoiceListening(if (en) "Listening… speak now" else "Đang nghe… hãy nói lệnh")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        vm.onVoiceListeningError(
                            if (en) "Did not hear clearly. Try Google Voice below."
                            else "Không nghe rõ. Bấm \"Dùng Google Voice\" bên dưới hoặc nói lại."
                        )
                    } else {
                        vm.dismissVoiceListening()
                        launchRecognizerIntentFallback()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    vm.handleVoiceResults(matches)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        vm.onVoicePartialResult(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val recIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (en) "en-US" else "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            sr.startListening(recIntent)
        } catch (_: Exception) {
            vm.dismissVoiceListening()
            launchRecognizerIntentFallback()
        }
    }

    private fun launchRecognizerIntentFallback() {
        val en = vm.uiState.value.isEnglish
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (en) "en-US" else "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (en) "Speak a command…" else "Nói lệnh điều khiển hoặc tìm kiếm…")
        }
        try {
            voiceIntentLauncher.launch(i)
        } catch (_: Exception) {
            showToast(if (en) "No voice recognition app available" else "Thiết bị chưa có nhận diện giọng nói")
        }
    }

    private fun launchSystemVoiceSearch() {
        val intents = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(RecognizerIntent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        for (i in intents) {
            try {
                if (i.resolveActivity(packageManager) != null) {
                    systemVoiceLauncher.launch(i)
                    return
                }
            } catch (_: Exception) {
            }
        }
        launchRecognizerIntentFallback()
    }

    private fun launchVoiceSearchQuery(query: String, onYouTube: Boolean) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        if (onYouTube) {
            val ytUrl = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
            val candidates = listOf(
                Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv")
                    .putExtra(SearchManager.QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
                    .putExtra(SearchManager.QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Intent(Intent.ACTION_VIEW, ytUrl).setPackage("com.google.android.youtube.tv")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Intent(Intent.ACTION_VIEW, ytUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            launchWithFallback(candidates)
            return
        }

        val candidates = listOf(
            Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        launchWithFallback(candidates)
    }

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }
}
