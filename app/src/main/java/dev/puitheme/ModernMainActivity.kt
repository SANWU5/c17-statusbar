// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

/** The settings app uses the same Miuix component system as the current LSPosed manager. */
class ModernMainActivity : ComponentActivity() {
    var page by mutableIntStateOf(0)
    var freeNoticeRequired by mutableStateOf(true)
        private set
    var donationImage by mutableStateOf<String?>(null)
    var selectedGroup by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var category by mutableStateOf("statusbar")
    var values by mutableStateOf<Map<String, *>>(emptyMap<String, Any>())
        private set
    var runtimeActive by mutableStateOf(false)
        private set
    var runtimeMessage by mutableStateOf("正在检测模块激活状态…")
        private set
    var checkingRuntime by mutableStateOf(false)
        private set
    var framework by mutableStateOf(AppFrameworkStatus.current())
        private set
    val lspEnabled get() = framework.connected && framework.compatible
    var rootGranted by mutableStateOf(false)
        private set
    var rootMessage by mutableStateOf("尚未授予 Root 权限")
        private set
    var checkingRoot by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var checkingUpdates by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf("手动查看 GitHub 最新正式发布")
        private set
    private var latestUpdate: GitHubUpdates.Result? = null
    private var updateRequest: AppUpdateInstaller.Request? = null
    var maintenanceRunning by mutableStateOf(false)
        private set
    var desktopIconHidden by mutableStateOf(false)
        private set
    var desktopIconAvailable by mutableStateOf(true)
        private set
    var confirmation by mutableStateOf<UiConfirmation?>(null)
    var editing by mutableStateOf<SettingsCatalog.Item?>(null)
    var colorEditing by mutableStateOf<SettingsCatalog.Item?>(null)
    var fontCatalogOpen by mutableStateOf(false)
    var fontRenameTarget by mutableStateOf<FontLibrary.Entry?>(null)
    var fontLibraryEntries by mutableStateOf<List<FontLibrary.Entry>>(emptyList())
        private set
    private var fontLibraryRequest = 0
    var notificationOverridesOpen by mutableStateOf(false)
    var iconAssignmentsOpen by mutableStateOf(false)
    var iconAssignmentCategory by mutableStateOf("hint")
    var shadeWallpaperOpen by mutableStateOf(false)
    var panelMode by mutableStateOf(PanelMode.UNKNOWN)
        private set
    var iconLibraryOpen by mutableStateOf(false)
    var iconPacks by mutableStateOf<List<IconPackRepository.Pack>>(emptyList())
        private set
    var applicationChoices by mutableStateOf<List<ApplicationChoice>>(emptyList())
        private set
    var iconPackDownloading by mutableStateOf(false)
        private set
    private var iconPackCancellation: IconPackDownload.Cancellation? = null
    var downloadingFont by mutableStateOf<FontCatalog.Entry?>(null)
        private set
    var fontDownloadPercent by mutableIntStateOf(0)
        private set
    private var fontCancellation: FontDownloadRepository.Cancellation? = null
    val canEdit get() = !freeNoticeRequired && !maintenanceRunning && (runtimeActive || rootGranted || lspEnabled)
    @Volatile private var resumed = false
    @Volatile private var destroyed = false
    private lateinit var raw: SharedPreferences
    private lateinit var preferences: SharedPreferences
    private var probe: ModuleRuntimeStatus.ProbeHandle? = null
    private var frameworkSubscription: AppFrameworkStatus.Subscription? = null
    private var rootRequest: RootAccess.Request? = null
    private var restartRequest: SystemUiRestart.Request? = null
    private lateinit var navHost: ComposeView
    private var navigationInsets by mutableStateOf(PaddingValues())
    var navigationHeight by mutableFloatStateOf(72f)
        private set
    private lateinit var modalLayer: ComposeView
    private var keyboardOpen = false
    private val handler = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor()
    private var pendingFile: Pair<String, Uri>? = null
    private var wallpaperImportScene: String? = null
    private val wallpaperResourcesLock = Any()
    private val preparedWallpapers = mutableSetOf<ShadeWallpaperRepository.PreparedWallpaper>()
    private val panelModeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { refreshPanelMode() }
    }
    private fun refreshPanelMode() {
        if (!destroyed) panelMode = PanelMode.query(this).getString("mode", PanelMode.UNKNOWN)
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post {
            if (!destroyed) {
                values = raw.all
                handler.removeCallbacks(notifySettings)
                if (SettingsSnapshot.notificationAllowed(raw)) handler.post(notifySettings)
            }
        }
    }
    private val notifySettings = Runnable {
        if (!destroyed && SettingsSnapshot.notificationAllowed(raw))
            contentResolver.notifyChange(Uri.parse(StatusBarSettings.CONTENT_URI), null)
    }
    private val importConfig = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) queueFile("import", uri)
    }
    private val exportConfig = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) queueFile("export", uri)
    }
    private val importFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) queueFile("font", uri)
    }
    private val importNotificationIcon = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) queueFile("notification_icon", uri)
    }
    private val importIconPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) queueFile("icon_pack", uri)
    }
    private val importPuiTheme = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) queueFile("pui_theme", uri)
    }
    private val importShadeWallpaper = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val scene = wallpaperImportScene
        if (uri != null && ShadeWallpaperSettings.available() && ShadeWallpaperSettings.validScene(scene)) queueFile("wallpaper:$scene", uri)
    }
    private val exportLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) work.execute {
            runCatching { ModuleDiagnostics.writeExport(this, uri) }
                .onSuccess { runOnUiThread { toast("日志已保存") } }
                .onFailure { runOnUiThread { toast("日志保存失败：${it.message}") } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        freeNoticeRequired = !FreeNotice.accepted(this)
        raw = StatusBarSettings.preferences(this)
        AppFrameworkStatus.start(this)
        refreshPanelMode()
        runCatching {
            contentResolver.registerContentObserver(Settings.System.getUriFor(PanelMode.SETTING), false, panelModeObserver)
            contentResolver.registerContentObserver(Settings.System.getUriFor(PanelMode.DEFAULT_SETTING), false, panelModeObserver)
        }
        wallpaperImportScene = savedInstanceState?.getString("wallpaper_scene")?.takeIf { ShadeWallpaperSettings.validScene(it) }
        preferences = ActivationGuardPreferences(raw, { resumed && canEdit && !destroyed }, {
            handler.post { toast("激活模块或授予 Root 后即可保存配置") }
        }).withPersistence(this) { handler.post { toast("设置保存失败，请稍后重新保存") } }
        values = raw.all
        raw.registerOnSharedPreferenceChangeListener(listener)
        frameworkSubscription = AppFrameworkStatus.observe { status ->
            if (!destroyed) {
                framework = status
                if (resumed) replayFile()
            }
        }
        page = savedInstanceState?.getInt("page") ?: 0
        selectedGroup = savedInstanceState?.getString("group")
        query = savedInstanceState?.getString("query") ?: ""
        category = savedInstanceState?.getString("category") ?: "statusbar"
        savedInstanceState?.getString("file_uri")?.let {
            pendingFile = (savedInstanceState.getString("file_kind") ?: "import") to Uri.parse(it)
        }
        val density = resources.displayMetrics.density
        val root = FrameLayout(this)
        val content = ComposeView(this).apply {
            consumeWindowInsets = false
            setContent { C17Theme { C17Pages(this@ModernMainActivity) } }
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        navHost = ComposeView(this).apply {
            consumeWindowInsets = false
            setContent { C17Theme { C17Navigation(page, navigationInsets) { selectPage(it) } } }
        }
        root.addView(navHost, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = 0; rightMargin = 0; bottomMargin = 0
        })
        navHost.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val height = (bottom - top) / density
            if (height > 0 && navigationHeight != height) navigationHeight = height
        }
        modalLayer = ComposeView(this).apply {
            consumeWindowInsets = false
            setContent { C17Theme { C17OverlayLayer(this@ModernMainActivity) } }
            visibility = View.GONE
        }
        // Native z-order also matches touch and accessibility order: all app popups own this layer.
        root.addView(modalLayer, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, root)
            keyboardOpen = compat.isVisible(WindowInsetsCompat.Type.ime())
            val safeSides = compat.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            val safeLeft = safeSides.left
            val safeRight = safeSides.right
            val bottom = compat.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val contentParams = content.layoutParams as FrameLayout.LayoutParams
            if (contentParams.leftMargin != safeLeft || contentParams.rightMargin != safeRight) {
                contentParams.leftMargin = safeLeft
                contentParams.rightMargin = safeRight
                content.layoutParams = contentParams
            }
            // Keep the opaque surface full width and down to the window edge. Only the tab
            // content uses safe insets, so the system gesture area never exposes the page.
            val rtl = root.layoutDirection == View.LAYOUT_DIRECTION_RTL
            navigationInsets = PaddingValues(start = ((if (rtl) safeRight else safeLeft) / density).dp,
                end = ((if (rtl) safeLeft else safeRight) / density).dp, bottom = (bottom / density).dp)
            updateNavigation()
            insets
        }
        setContentView(root)
        updateNavigation()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    freeNoticeRequired -> finish()
                    donationImage != null -> donationImage = null
                    colorEditing != null -> colorEditing = null
                    downloadingFont != null -> cancelFontDownload()
                    fontRenameTarget != null -> { fontRenameTarget = null; fontCatalogOpen = true }
                    fontCatalogOpen -> fontCatalogOpen = false
                    iconPackDownloading -> cancelIconPackDownload()
                    iconAssignmentsOpen -> if (!busy) iconAssignmentsOpen = false
                    shadeWallpaperOpen -> if (!busy) shadeWallpaperOpen = false
                    iconLibraryOpen -> if (!busy) iconLibraryOpen = false
                    notificationOverridesOpen -> notificationOverridesOpen = false
                    editing != null -> editing = null
                    confirmation != null -> dismissConfirmation()
                    selectedGroup != null -> { selectedGroup = null; updateNavigation() }
                    page != 0 -> selectPage(0)
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume(); resumed = true
        refreshPanelMode()
        val iconState = LauncherIcon.read(this)
        desktopIconHidden = iconState.hidden
        desktopIconAvailable = iconState.success
        values = raw.all
        checkActivation()
        if (!freeNoticeRequired && getSharedPreferences("app_migrations", MODE_PRIVATE).getBoolean("root_requested", false) && !rootGranted) requestRoot()
        replayFile()
    }

    override fun onPause() {
        if (downloadingFont != null) cancelFontDownload()
        if (iconPackDownloading) cancelIconPackDownload()
        if (updateRequest?.isInstalling == false) { updateRequest?.cancel(); updateRequest = null; busy = false }
        if (NumericTrial.active()) {
            NumericTrial.rollback()
            confirmation = null
        }
        handler.removeCallbacks(notifySettings); notifySettings.run()
        SettingsSnapshot.flushPending(this)
        resumed = false; runtimeActive = false
        if (!checkingRoot) rootGranted = false
        probe?.cancel(); probe = null
        super.onPause()
    }

    override fun onDestroy() {
        fontCancellation?.cancel()
        iconPackCancellation?.cancel()
        destroyed = true; raw.unregisterOnSharedPreferenceChangeListener(listener)
        runCatching { contentResolver.unregisterContentObserver(panelModeObserver) }
        probe?.cancel(); frameworkSubscription?.close(); rootRequest?.cancel(); restartRequest?.cancel(); updateRequest?.cancel()
        handler.removeCallbacksAndMessages(null); work.shutdown()
        val abandonedWallpapers = synchronized(wallpaperResourcesLock) {
            preparedWallpapers.toList().also { preparedWallpapers.clear() }
        }
        abandonedWallpapers.forEach { it.close() }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page); outState.putString("group", selectedGroup)
        outState.putString("query", query); outState.putString("category", category)
        outState.putString("wallpaper_scene", wallpaperImportScene)
        pendingFile?.let { outState.putString("file_kind", it.first); outState.putString("file_uri", it.second.toString()) }
        super.onSaveInstanceState(outState)
    }

    fun selectPage(index: Int) {
        page = index; selectedGroup = null; updateNavigation()
    }
    fun openGroup(id: String) { selectedGroup = id; if (id == "font") refreshFontLibrary(); updateNavigation() }
    fun closeGroup() { selectedGroup = null; updateNavigation() }
    fun syncModalLayer(visible: Boolean) {
        updateNavigation()
        if (::modalLayer.isInitialized) modalLayer.visibility = if (visible) View.VISIBLE else View.GONE
    }
    private fun updateNavigation() { if (::navHost.isInitialized) navHost.visibility = if (!freeNoticeRequired && selectedGroup == null && !keyboardOpen && !iconLibraryOpen && !iconAssignmentsOpen) View.VISIBLE else View.GONE }
    fun acceptFreeNotice(input: String): String? {
        if (!FreeNotice.matches(input)) return "请完整输入上面的短句，包含标点。"
        if (!FreeNotice.accept(this, input)) return "确认记录保存失败，请重试。"
        freeNoticeRequired = false
        updateNavigation()
        replayFile()
        if (resumed && !rootGranted && !checkingRoot && getSharedPreferences("app_migrations", MODE_PRIVATE).getBoolean("root_requested", false)) requestRoot()
        return null
    }
    fun value(key: String): Any? = SettingsCatalog.value(values, key)
    fun bool(key: String): Boolean = if (key.startsWith("shade_wallpaper_") && !ShadeWallpaperSettings.available()) false else StatusBarSettings.bool(values, key)
    fun save(key: String, value: Any): Boolean {
        if (!resumed || !canEdit) { toast("激活模块或授予 Root 后即可保存配置"); return false }
        val featureUnavailable = ShadeWallpaperSettings.unavailableReason(key)
        if (featureUnavailable.isNotEmpty()) { toast(featureUnavailable); return false }
        if (value is String) {
            val invalid = when (key) {
                NotificationIconOverrides.RULES -> NotificationIconOverrides.validationError(value)
                IconPackAssignments.ASSIGNMENTS -> IconPackAssignments.validationError(value)
                IconPackRepository.LAYERS -> runCatching { IconPackRepository.layers(value); null }.getOrElse { it.message ?: "图标库配置无效" }
                else -> null
            }
            if (invalid != null) { toast(invalid); return false }
        }
        if (value == true) {
            val unavailable = SettingsCatalog.unavailableReason(key, raw.all)
            if (unavailable.isNotEmpty()) { toast(unavailable); return false }
        }
        val editor = preferences.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Int -> editor.putInt(key, value)
            is String -> editor.putString(key, value)
            else -> return false
        }
        editor.apply()
        return true
    }
    fun tryNumber(item: SettingsCatalog.Item, value: Float) {
        if (!resumed || !canEdit || busy) { toast("激活模块或授予 Root 后即可修改"); return }
        val trialContext = raw.all
        val confirmed = SettingEditor.storedValue(item, trialContext)
        if (!NumericTrial.begin(this, raw, preferences, item.key, value)) {
            toast("无法开始试用，已保留原来的数值"); return
        }
        editing = null
        confirmation = UiConfirmation("试用${item.title}",
            SettingEditor.trialSummary(item, confirmed, value, trialContext),
            "保存此数值", expiresAt = NumericTrial.deadline(), cancelAction = { NumericTrial.rollback() }) {
                toast(if (NumericTrial.keep()) "数值已保存" else if (NumericTrial.active()) "保存失败，正在恢复原数值" else "试用已结束，原数值已恢复")
            }
    }
    fun checkActivation() {
        probe?.cancel(); checkingRuntime = true; runtimeActive = false; runtimeMessage = "正在验证当前版本…"
        probe = ModuleRuntimeStatus.probe(this) { result ->
            if (!destroyed && resumed) {
                runtimeActive = result.active
                checkingRuntime = false
                runtimeMessage = if (result.active) "${result.frameworkName} ${result.frameworkVersion}" else result.message
                replayFile()
            }
        }
    }
    fun requestRoot() {
        if (checkingRoot) return
        checkingRoot = true; rootMessage = "正在检查 Root 授权…"
        getSharedPreferences("app_migrations", MODE_PRIVATE).edit().putBoolean("root_requested", true).apply()
        rootRequest = RootAccess.check { result ->
            checkingRoot = false; rootRequest = null
            if (!destroyed) { rootGranted = result.granted; rootMessage = result.message; if (resumed) replayFile() }
        }
    }
    fun restartSystemUi() {
        if (busy) return
        confirmation = UiConfirmation("重启系统界面", "状态栏与通知中心会短暂重新加载，随后应用当前模块和配置。", "重启") {
            busy = true; runtimeActive = false
            restartRequest = SystemUiRestart.restart { result ->
                if (!destroyed) {
                    busy = false; toast(result.message)
                    handler.postDelayed({ if (resumed) checkActivation() }, if (result.success) 1800 else 0)
                }
            }
        }
    }
    fun chooseImport() { if (canEdit) importConfig.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) else toast("请先激活模块或授予 Root") }
    fun chooseExport() { if (canEdit) exportConfig.launch("C17-settings.json") else toast("请先激活模块或授予 Root") }
    fun chooseFont() { if (canEdit && !busy) { fontCatalogOpen = false; importFont.launch(arrayOf("*/*")) } else toast("请先激活模块或授予 Root") }
    fun chooseOpenFont() { refreshFontLibrary(); fontCatalogOpen = true }
    fun refreshFontLibrary() {
        if (destroyed || maintenanceRunning) return
        val request = ++fontLibraryRequest
        val snapshot = values
        work.execute {
            val result = runCatching {
                FontLibrary.retainCurrent(applicationContext, snapshot[StatusBarSettings.FONT_REVISION]?.toString(), snapshot[StatusBarSettings.FONT_NAME]?.toString())
                FontLibrary.retainDownloads(applicationContext)
                FontLibrary.entries(applicationContext)
            }
            handler.post { if (!destroyed && request == fontLibraryRequest && !maintenanceRunning) {
                result.onSuccess { fontLibraryEntries = it }.onFailure { ModuleDiagnostics.error("font", "Font library unavailable", it) }
            } }
        }
    }
    fun currentFontTitle(): String = when (value(StatusBarSettings.FONT_MODE)?.toString()) {
        "pingfang" -> "苹方"
        "custom" -> value(StatusBarSettings.FONT_NAME)?.toString()?.takeIf { it.isNotBlank() } ?: "导入字体"
        else -> "跟随系统"
    }
    fun selectLibraryFont(entry: FontLibrary.Entry) {
        if (!resumed || !canEdit || busy) return
        busy = true; fontCatalogOpen = false
        work.execute {
            try {
                val prepared = FontLibrary.prepare(applicationContext, entry.revision)
                handler.post {
                    val result = runCatching { prepared.use { commitSelectedFont(prepared, entry.name) } }
                    busy = false
                    if (!destroyed) { refreshFontLibrary(); toast(if (result.isSuccess) "已选择 ${entry.name}" else "字体未切换，原字体已保留：${result.exceptionOrNull()?.message}") }
                }
            } catch (error: Exception) { handler.post { busy = false; if (!destroyed) { refreshFontLibrary(); toast("字体未切换：${error.message}") } } }
        }
    }
    private fun commitSelectedFont(prepared: FontRepository.PreparedFont, name: String): String {
        val keys = listOf(StatusBarSettings.FONT_MODE, StatusBarSettings.FONT_REVISION, StatusBarSettings.FONT_NAME)
        val previous = raw.all
        return prepared.commit({ resumed && canEdit && !destroyed && !maintenanceRunning }, {
            preferences.edit().putString(StatusBarSettings.FONT_MODE, "custom")
                .putString(StatusBarSettings.FONT_REVISION, prepared.revision).putString(StatusBarSettings.FONT_NAME, name).commit()
        }, {
            val restore = raw.edit()
            keys.forEach { key -> if (previous.containsKey(key)) restore.putString(key, previous[key] as String?) else restore.remove(key) }
            check(restore.commit()) { "原字体选项未能恢复" }
        })
    }
    fun renameLibraryFont(entry: FontLibrary.Entry, input: String): String? {
        if (!resumed || !canEdit || busy) return "请先激活模块或授予 Root"
        val name = FontLibrary.displayName(input)
        if (input.isBlank()) return "请输入字体名称"
        busy = true; fontRenameTarget = null
        work.execute {
            val result = runCatching { FontLibrary.rename(applicationContext, entry.revision, name) }
            handler.post {
                if (!destroyed) {
                    if (result.isSuccess && value(StatusBarSettings.FONT_REVISION) == entry.revision)
                        preferences.edit().putString(StatusBarSettings.FONT_NAME, name).apply()
                    busy = false; refreshFontLibrary()
                    if (result.isFailure) toast("重命名失败：${result.exceptionOrNull()?.message}")
                }
            }
        }
        return null
    }
    fun deleteLibraryFont(entry: FontLibrary.Entry) {
        if (!canEdit || busy) return
        val active = value(StatusBarSettings.FONT_REVISION) == entry.revision
        confirmation = UiConfirmation("删除 ${entry.name}", if (active) "这款字体正在使用。删除后改为跟随系统，其他导入字体会保留。" else "删除这款字体，其他已导入或下载的字体会保留。", "删除") {
            if (!resumed || !canEdit || busy) return@UiConfirmation
            val current = value(StatusBarSettings.FONT_REVISION) == entry.revision
            val previous = raw.all
            val changes = FontLibrary.deletionFallback(previous, entry.revision)
            busy = true
            work.execute {
                try {
                    val prepared = FontLibrary.prepareDeletion(applicationContext, entry.revision, current)
                    handler.post {
                        val result = runCatching { prepared.use {
                            prepared.commit({ resumed && canEdit && !destroyed && !maintenanceRunning }, {
                                if (changes.isEmpty()) true else preferences.edit().also { editor -> changes.forEach { (key, selected) -> editor.putString(key, selected) } }.commit()
                            }, {
                                val restore = raw.edit()
                                changes.keys.forEach { key -> if (previous.containsKey(key)) restore.putString(key, previous[key] as String?) else restore.remove(key) }
                                check(restore.commit()) { "原字体配置未能恢复" }
                            })
                        } }
                        busy = false
                        if (!destroyed) { refreshFontLibrary(); fontCatalogOpen = true; toast(if (result.isSuccess) "字体已删除" else "删除未完成：${result.exceptionOrNull()?.message}") }
                    }
                } catch (error: Exception) { handler.post { busy = false; if (!destroyed) { refreshFontLibrary(); fontCatalogOpen = true; toast("字体未删除：${error.message}") } } }
            }
        }
    }
    fun downloadFont(entry: FontCatalog.Entry) {
        if (!resumed || !canEdit || busy) return
        val token = FontDownloadRepository.Cancellation()
        fontCatalogOpen = false; downloadingFont = entry; fontDownloadPercent = 0; fontCancellation = token; busy = true
        work.execute {
            try {
                val snapshot = values
                FontLibrary.retainCurrent(applicationContext, snapshot[StatusBarSettings.FONT_REVISION]?.toString(), snapshot[StatusBarSettings.FONT_NAME]?.toString())
                val prepared = FontDownloadRepository.prepare(applicationContext, entry, { received, total ->
                    runOnUiThread { if (fontCancellation === token) fontDownloadPercent = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0 }
                }, token)
                try { FontLibrary.remember(applicationContext, prepared, entry.displayName) }
                catch (error: Exception) { prepared.close(); throw error }
                runOnUiThread {
                    prepared.use {
                        if (fontCancellation !== token) return@runOnUiThread
                        if (token.asBoolean || destroyed || !resumed || !canEdit) {
                            fontCancellation = null; downloadingFont = null; busy = false
                            if (!destroyed) toast("编辑权限已变化，原字体已保留")
                            return@runOnUiThread
                        }
                        val keys = listOf(StatusBarSettings.FONT_MODE, StatusBarSettings.FONT_REVISION, StatusBarSettings.FONT_NAME)
                        val previous = raw.all
                        val result = runCatching {
                            prepared.commit({ resumed && canEdit && !destroyed && !token.asBoolean }, {
                                preferences.edit().putString(StatusBarSettings.FONT_MODE, "custom")
                                    .putString(StatusBarSettings.FONT_REVISION, prepared.revision)
                                    .putString(StatusBarSettings.FONT_NAME, entry.displayName).commit()
                            }, {
                                val restore = raw.edit()
                                keys.forEach { key -> if (previous.containsKey(key)) restore.putString(key, previous[key] as String?) else restore.remove(key) }
                                check(restore.commit()) { "原字体选项未能恢复" }
                            })
                        }
                        fontCancellation = null; downloadingFont = null; busy = false
                        refreshFontLibrary()
                        toast(if (result.isSuccess) "字体已下载，可在各功能中调节粗细；启用文字字体后生效" else "字体应用失败，原字体已保留：${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (fontCancellation === token) {
                        fontCancellation = null; downloadingFont = null; busy = false
                        toast("下载失败，原字体已保留：${error.message}")
                    }
                }
            }
        }
    }
    fun cancelFontDownload() {
        fontCancellation?.cancel(); fontCancellation = null; downloadingFont = null; busy = false
    }
    fun chooseNotificationIcon() { if (canEdit) importNotificationIcon.launch(arrayOf("image/png", "image/jpeg", "image/webp")) else toast("请先激活模块或授予 Root") }
    fun chooseLog() { exportLog.launch("C17-diagnostics.txt") }
    fun clearLogHistory() {
        if (busy || maintenanceRunning) return
        confirmation = UiConfirmation("清空日志", "删除本模块保存的历史诊断日志及分享副本，配置不受影响。", "清空") {
            busy = true
            work.execute {
                val result = runCatching { MaintenanceReset.clearLogs(applicationContext) }
                handler.post { if (!destroyed) { busy = false; toast(if (result.isSuccess) "历史日志已清空" else "清空失败，请稍后重试") } }
            }
        }
    }
    fun deleteAllSettings() {
        if (busy || !canEdit || maintenanceRunning) return
        confirmation = UiConfirmation("删除所有配置", "删除当前配置、恢复备份、导入字体和自定义图标，并恢复全部默认设置。免费声明确认与 Root 请求标识保留。此操作无法撤销，可先导出配置。", "删除并恢复默认") {
            if (!resumed || !canEdit || busy) return@UiConfirmation
            pendingFile = null; editing = null; colorEditing = null; fontCatalogOpen = false
            iconLibraryOpen = false; notificationOverridesOpen = false
            iconAssignmentsOpen = false; shadeWallpaperOpen = false
            cancelIconPackDownload()
            cancelFontDownload(); handler.removeCallbacks(notifySettings)
            maintenanceRunning = true; busy = true
            work.execute {
                val result = runCatching { MaintenanceReset.reset(applicationContext) }
                handler.post {
                    if (!destroyed) {
                        maintenanceRunning = false; busy = false; values = raw.all; selectedGroup = null; iconPacks = emptyList()
                        toast(result.getOrNull()?.message ?: "删除未完成，请稍后重试；未确认的旧配置不会重新发布")
                        if (resumed) { notifySettings.run(); updateNavigation() }
                    }
                }
            }
        }
    }
    private fun installUpdate(result: GitHubUpdates.Result) {
        if (!resumed || destroyed || busy || maintenanceRunning || !rootGranted) { toast("请先授予 Root 权限"); return }
        busy = true; updateMessage = "准备下载更新…"
        updateRequest = AppUpdateInstaller.install(applicationContext, result,
            { !destroyed && resumed && rootGranted && !maintenanceRunning }, object : AppUpdateInstaller.Callback {
                override fun onStage(stage: AppUpdateInstaller.Stage) {
                    if (!destroyed) updateMessage = when (stage) {
                        AppUpdateInstaller.Stage.DOWNLOADING -> "正在下载正式更新…"
                        AppUpdateInstaller.Stage.VERIFYING -> "正在校验安装包…"
                        AppUpdateInstaller.Stage.INSTALLING -> "正在覆盖安装，配置会保留…"
                    }
                }
                override fun onResult(success: Boolean, message: String) {
                    if (!destroyed) { updateRequest = null; busy = false; updateMessage = message; toast(message) }
                }
            })
    }
    fun requestDesktopIconHidden(hidden: Boolean) {
        if (freeNoticeRequired || busy || maintenanceRunning) return
        val apply = {
            val result = LauncherIcon.setHidden(this, hidden)
            if (result.success) desktopIconHidden = result.hidden
            toast(result.message)
        }
        if (hidden) confirmation = UiConfirmation("隐藏桌面图标", "隐藏后，模块和配置继续生效。可从 LSPosed 管理器的“模块设置”重新打开本应用，并在这里恢复图标。", "隐藏", action = apply)
        else apply()
    }

    fun dismissConfirmation() { confirmation?.cancelAction?.invoke(); confirmation = null }
    fun openUrl(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("没有可用的浏览器") } }
    fun checkUpdates() {
        if (!resumed || destroyed || checkingUpdates || busy || maintenanceRunning) return
        checkingUpdates = true
        updateMessage = "正在检查 GitHub 正式发布…"
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }.getOrDefault("")
        // The existing checker performs bounded HTTP work off the main thread and posts its result to it.
        GitHubUpdates.fetch(version) { result ->
            if (destroyed) return@fetch
            checkingUpdates = false
            latestUpdate = result
            updateMessage = result.message
            // A delayed result must not replace a parameter trial, file confirmation or font dialog.
            if (resumed && page == 2 && confirmation == null && editing == null && colorEditing == null &&
                !fontCatalogOpen && downloadingFont == null && !busy) {
                val details = buildString {
                    append(result.message)
                    if (result.version.isNotEmpty()) append("\n发布版本：${result.version}")
                    if (result.body.isNotEmpty()) append("\n\n${result.body}")
                }
                val download = result.newer && GitHubUpdates.isApkUrl(result.apkUrl)
                val rootInstall = download && rootGranted && result.canInstall()
                confirmation = UiConfirmation(if (result.newer) "发现新版本" else "更新检查", details,
                    if (rootInstall) "下载并安装" else if (download) "下载 APK" else "查看正式发布") {
                    if (rootInstall) installUpdate(result) else if (download) openUrl(result.apkUrl) else openLatestRelease()
                }
            }
        }
    }
    fun openLatestRelease() {
        val url = latestUpdate?.releaseUrl?.takeIf(GitHubUpdates::isReleaseUrl)
            ?: "${GitHubUpdates.REPO_URL}/releases/latest"
        openUrl(url)
    }
    private fun queueFile(kind: String, uri: Uri) {
        val permission = if (kind == "export") Intent.FLAG_GRANT_WRITE_URI_PERMISSION else Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (kind != "notification_icon") runCatching { contentResolver.takePersistableUriPermission(uri, permission) }
        pendingFile = kind to uri; replayFile()
    }
    private fun replayFile() {
        if (!resumed || !canEdit || busy || destroyed || confirmation != null) return
        val action = pendingFile ?: return
        busy = true
        if (action.first == "font") { prepareFont(action.second); return }
        if (action.first == "notification_icon") { prepareNotificationIcon(action.second); return }
        if (action.first == "icon_pack") { prepareIconPack(action.second); return }
        if (action.first == "pui_theme") { preparePuiTheme(action.second); return }
        if (action.first.startsWith("wallpaper:") && !ShadeWallpaperSettings.available()) {
            pendingFile = null; busy = false; toast(ShadeWallpaperSettings.unavailableReason()); return
        }
        if (action.first.startsWith("wallpaper:")) { prepareShadeWallpaper(action.first.substringAfter(':'), action.second); return }
        val snapshot = values
        work.execute {
            try {
                if (action.first == "export") {
                    contentResolver.openOutputStream(action.second, "wt").use { ConfigTransfer.write(it, snapshot) }
                    runOnUiThread { pendingFile = null; busy = false; toast("配置已导出") }
                } else {
                    val prepared = contentResolver.openInputStream(action.second).use { ConfigTransfer.read(it, ConfigTransfer.customFontAvailable(this)) }
                    runOnUiThread {
                        busy = false
                        if (!destroyed) confirmation = UiConfirmation("导入配置", "将更新 ${prepared.count} 项设置。${prepared.warning}\n现有字体文件和安全模式会保留。", "导入", cancelAction = { pendingFile = null }) {
                            pendingFile = null
                            if (!resumed || !canEdit) { toast("权限状态已变化，请重新授权后导入"); return@UiConfirmation }
                            busy = true
                            val result = runCatching { ConfigTransfer.commit(preferences, prepared) }
                            busy = false; toast(if (result.getOrDefault(false)) "配置已导入" else "导入未完成，请重试")
                        }
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { pendingFile = null; busy = false; toast("${if (action.first == "export") "导出" else "导入"}失败：${error.message}") }
            }
        }
    }
    private fun prepareFont(uri: Uri) {
        var name = "自选字体"
        runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0).take(120)
        } }
        val displayName = FontLibrary.displayName(name)
        val previousFont = values
        work.execute {
            try {
                FontLibrary.retainCurrent(applicationContext, previousFont[StatusBarSettings.FONT_REVISION]?.toString(), previousFont[StatusBarSettings.FONT_NAME]?.toString())
                val prepared = FontRepository.prepareFont(applicationContext, uri)
                try { FontLibrary.remember(applicationContext, prepared, displayName) }
                catch (error: Exception) { prepared.close(); throw error }
                runOnUiThread {
                    try {
                        prepared.use {
                            val previous = raw.all
                            prepared.commit({ resumed && canEdit && !destroyed }, {
                                preferences.edit().putString(StatusBarSettings.FONT_MODE, "custom")
                                    .putString(StatusBarSettings.FONT_REVISION, prepared.revision).putString(StatusBarSettings.FONT_NAME, displayName).commit()
                            }, {
                                val restore = raw.edit()
                                listOf(StatusBarSettings.FONT_MODE, StatusBarSettings.FONT_REVISION, StatusBarSettings.FONT_NAME).forEach { key ->
                                    if (previous.containsKey(key)) restore.putString(key, previous[key] as String?) else restore.remove(key)
                                }
                                check(restore.commit()) { "原字体选项未能恢复" }
                            })
                            pendingFile = null; busy = false; refreshFontLibrary(); toast("字体已导入")
                        }
                    } catch (error: Exception) { pendingFile = null; busy = false; toast("字体导入失败：${error.message}") }
                }
            } catch (error: Exception) { runOnUiThread { pendingFile = null; busy = false; toast("字体导入失败：${error.message}") } }
        }
    }
    private fun prepareNotificationIcon(uri: Uri) {
        work.execute {
            try {
                val name = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) cursor.getString(0).take(120) else "自定义图片"
                } }.getOrDefault("自定义图片")
                val prepared = NotificationIconRepository.prepare(applicationContext, uri)
                runOnUiThread {
                    try {
                        prepared.use {
                            val keys = listOf(NotificationIconArea.MODE, NotificationIconArea.IMAGE_NAME, NotificationIconArea.IMAGE_REVISION)
                            val previous = raw.all
                            prepared.commit({ resumed && canEdit && !destroyed }, {
                                preferences.edit().putString(NotificationIconArea.MODE, "image")
                                    .putString(NotificationIconArea.IMAGE_NAME, name)
                                    .putString(NotificationIconArea.IMAGE_REVISION, prepared.revision).commit()
                            }, {
                                val restore = raw.edit()
                                keys.forEach { key -> if (previous.containsKey(key)) restore.putString(key, previous[key] as String?) else restore.remove(key) }
                                check(restore.commit()) { "原图片配置未能恢复" }
                            })
                            pendingFile = null; busy = false; toast("通知图标图片已导入")
                        }
                    } catch (error: Exception) { pendingFile = null; busy = false; toast("图片导入失败：${error.message}") }
                }
            } catch (error: Exception) { runOnUiThread { pendingFile = null; busy = false; toast("图片导入失败：${error.message}") } }
        }
    }
    fun openShadeWallpaperImport(scene: String) {
        if (!ShadeWallpaperSettings.available()) { toast(ShadeWallpaperSettings.unavailableReason()); return }
        if (!canEdit || busy || !ShadeWallpaperSettings.validScene(scene)) return
        wallpaperImportScene = scene
        importShadeWallpaper.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
    }
    private fun prepareShadeWallpaper(scene: String, uri: Uri) {
        if (!ShadeWallpaperSettings.available()) { pendingFile = null; busy = false; return }
        work.execute {
            try {
                val prepared = ShadeWallpaperRepository.prepare(applicationContext, uri, scene)
                val accepted = synchronized(wallpaperResourcesLock) {
                    !destroyed && preparedWallpapers.add(prepared)
                }
                if (!accepted) { prepared.close(); return@execute }
                val posted = handler.post {
                    if (destroyed) {
                        synchronized(wallpaperResourcesLock) { preparedWallpapers.remove(prepared) }
                        prepared.close(); return@post
                    }
                    try {
                        prepared.use {
                            val key = ShadeWallpaperSettings.revisionKey(scene)
                            val previous = raw.all[key]
                            prepared.commit({ resumed && canEdit && !destroyed }, {
                                preferences.edit().putString(key, prepared.revision).commit()
                            }, {
                                val restore = raw.edit()
                                if (previous is String) restore.putString(key, previous) else restore.remove(key)
                                check(restore.commit()) { "壁纸配置恢复失败" }
                            })
                            pendingFile = null; busy = false
                            work.execute { ShadeWallpaperRepository.prune(applicationContext, scene, prepared.revision) }
                            toast("${ShadeWallpaperSettings.title(scene)}壁纸已导入")
                        }
                    } catch (error: Exception) { pendingFile = null; busy = false; toast("壁纸导入失败：${error.message}") }
                    finally { synchronized(wallpaperResourcesLock) { preparedWallpapers.remove(prepared) } }
                }
                if (!posted) {
                    synchronized(wallpaperResourcesLock) { preparedWallpapers.remove(prepared) }
                    prepared.close()
                }
            } catch (error: Exception) {
                handler.post { if (!destroyed) { pendingFile = null; busy = false; toast("壁纸导入失败：${error.message}") } }
            }
        }
    }
    fun deleteShadeWallpaper(scene: String) {
        if (!resumed || !canEdit || busy || !ShadeWallpaperSettings.validScene(scene)) return
        val saved = preferences.edit().putBoolean(ShadeWallpaperSettings.enabledKey(scene), false)
            .putString(ShadeWallpaperSettings.revisionKey(scene), "").commit()
        if (!saved) { toast("壁纸配置保存失败，图片已保留"); return }
        busy = true
        work.execute {
            val result = runCatching { ShadeWallpaperRepository.delete(applicationContext, scene) }
            handler.post { if (!destroyed) { busy = false; toast(if (result.isSuccess) "这张壁纸已删除" else "配置已关闭，图片删除失败，请重试") } }
        }
    }
    fun openNotificationOverrides() {
        notificationOverridesOpen = true
        work.execute {
            val apps = runCatching {
                packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).map { info ->
                    ApplicationChoice(info.packageName, runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(info.packageName),
                        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            handler.post { if (!destroyed) applicationChoices = apps }
        }
    }
    fun applicationLabel(packageName: String): String = applicationChoices.firstOrNull { it.packageName == packageName }?.label ?: packageName
    private fun saveManagedString(key: String, json: String): Boolean {
        if (!resumed || !canEdit || destroyed) { toast("激活模块或授予 Root 后即可保存配置"); return false }
        val result = runCatching { preferences.edit().putString(key, json).commit() }.getOrDefault(false)
        values = raw.all
        if (!result) toast("配置未能完整保存，请重试")
        return result
    }
    fun saveNotificationOverride(packageName: String, text: String, enabled: Boolean, oldPackage: String? = null): String {
        if (!resumed || !canEdit || busy) return "请先激活模块或授予 Root"
        return try {
            var source = value(NotificationIconOverrides.RULES)?.toString() ?: "[]"
            if (packageName != oldPackage && NotificationIconOverrides.rules(source).any { it.packageName == packageName })
                return "该应用已设置，请编辑现有规则"
            if (!oldPackage.isNullOrBlank() && oldPackage != packageName) source = NotificationIconOverrides.remove(source, oldPackage)
            val updated = NotificationIconOverrides.put(source, packageName, text, enabled)
            if (saveManagedString(NotificationIconOverrides.RULES, updated)) "" else "保存失败，请重试"
        } catch (error: Exception) { error.message ?: "应用规则无效" }
    }
    fun removeNotificationOverride(packageName: String) {
        if (busy) return
        runCatching { NotificationIconOverrides.remove(value(NotificationIconOverrides.RULES)?.toString() ?: "[]", packageName) }
            .onSuccess { saveManagedString(NotificationIconOverrides.RULES, it) }.onFailure { toast(it.message ?: "规则删除失败") }
    }
    fun saveNotificationAppIconOverride(packageName: String, sourcePackage: String, enabled: Boolean, previousPackage: String? = null): String {
        if (!resumed || !canEdit || busy) return "请先激活模块或授予 Root"
        return try {
            var source = value(NotificationIconOverrides.RULES)?.toString() ?: "[]"
            if (packageName != previousPackage && NotificationIconOverrides.rules(source).any { it.packageName == packageName })
                return "该应用已设置，请编辑现有规则"
            if (!previousPackage.isNullOrBlank() && previousPackage != packageName) source = NotificationIconOverrides.remove(source, previousPackage)
            val updated = NotificationIconOverrides.putAppIcon(source, packageName, sourcePackage, enabled)
            if (enabled) packageManager.getApplicationInfo(sourcePackage, 0)
            if (saveManagedString(NotificationIconOverrides.RULES, updated)) "" else "保存失败，请重试"
        } catch (error: Exception) { error.message?.takeIf { it.isNotBlank() } ?: "所选应用图标不可用" }
    }
    fun openIconAssignments(category: String = "hint") {
        iconAssignmentCategory = category.takeIf { it in listOf("hint", "wifi", "cellular", "battery") } ?: "hint"
        iconAssignmentsOpen = true; updateNavigation(); refreshIconLibraries()
    }
    fun saveIconAssignment(targetRole: String, packId: String, sourceRole: String): String {
        if (!resumed || !canEdit || busy) return "请先激活模块或授予 Root"
        return try {
            val pack = iconPacks.firstOrNull { it.id == packId } ?: return "图标库已不存在，请重新选择"
            if (configuredLayers().none { it.id == packId && it.enabled }) return "请先启用所选图标库"
            if (!pack.manifest.icons.containsKey(sourceRole)) return "所选图案已不存在"
            val updated = IconPackAssignments.put(value(IconPackAssignments.ASSIGNMENTS)?.toString() ?: "[]", targetRole, packId, sourceRole)
            if (saveManagedString(IconPackAssignments.ASSIGNMENTS, updated)) "" else "保存失败，请重试"
        } catch (error: Exception) { error.message ?: "图标替换规则无效" }
    }
    fun removeIconAssignment(targetRole: String) {
        runCatching { IconPackAssignments.remove(value(IconPackAssignments.ASSIGNMENTS)?.toString() ?: "[]", targetRole) }
            .onSuccess { saveManagedString(IconPackAssignments.ASSIGNMENTS, it) }.onFailure { toast(it.message ?: "恢复失败") }
    }
    fun openIconLibrary() { iconLibraryOpen = true; updateNavigation(); refreshIconLibraries() }
    private fun refreshIconLibraries() {
        work.execute {
            val result = runCatching {
                IconPackRepository.installBundled(applicationContext)
                IconPackRepository.list(applicationContext)
            }
            handler.post { if (!destroyed) {
                result.onSuccess { packs ->
                    iconPacks = packs
                    if (resumed && canEdit) {
                        val layers = configuredLayers().toMutableList()
                        val known = layers.map { it.id }.toSet()
                        packs.filter { it.id !in known }.forEach { layers.add(IconPackRepository.Layer(it.id, false, false)) }
                        if (layers.size != known.size) saveIconLayers(layers)
                    }
                }.onFailure { toast("图标库读取失败：${it.message}") }
            } }
        }
    }
    private fun configuredLayers(): List<IconPackRepository.Layer> = IconPackRepository.layers(value(IconPackRepository.LAYERS)?.toString() ?: "[]")
    private fun saveIconLayers(layers: List<IconPackRepository.Layer>): Boolean {
        return try { saveManagedString(IconPackRepository.LAYERS, IconPackRepository.layersJson(layers)) }
        catch (error: Exception) { toast(error.message ?: "图标库配置无效"); false }
    }
    fun setIconPackEnabled(id: String, enabled: Boolean) {
        if (busy || !canEdit || !resumed) return
        runCatching {
            val layers = configuredLayers().toMutableList()
            val index = layers.indexOfFirst { it.id == id }
            if (index >= 0) layers[index] = IconPackRepository.Layer(id, enabled, layers[index].autoMatch)
            else layers.add(IconPackRepository.Layer(id, enabled, false))
            saveIconLayers(layers)
        }.onFailure { toast(it.message ?: "图标库状态保存失败") }
    }
    fun setIconLibraryAutoMatch(id: String, autoMatch: Boolean) {
        if (busy || !canEdit || !resumed) return
        runCatching {
            val layers = configuredLayers().toMutableList()
            val index = layers.indexOfFirst { it.id == id }
            if (index >= 0) layers[index] = IconPackRepository.Layer(id, layers[index].enabled, autoMatch)
            else layers.add(IconPackRepository.Layer(id, false, autoMatch))
            saveIconLayers(layers)
        }.onFailure { toast(it.message?.takeIf { text -> text.isNotBlank() } ?: "图标库使用方式保存失败") }
    }
    fun saveIconPackOrder(order: List<String>): Boolean {
        if (busy || !canEdit || !resumed) return false
        return try {
            check(order.distinct().size == order.size && order.toSet() == iconPacks.map { it.id }.toSet()) { "图标库列表已变化，请重新排序" }
            val previous = configuredLayers()
            val reordered = order.map { id -> previous.firstOrNull { it.id == id } ?: IconPackRepository.Layer(id, false, false) }
                .toMutableList()
            // Missing imported files retain their saved disabled/enabled/order records, rather than losing configuration silently.
            reordered.addAll(previous.filter { it.id !in order })
            saveIconLayers(reordered)
        } catch (error: Exception) { toast(error.message ?: "排序未能保存"); false }
    }
    fun renameIconPack(id: String, name: String) {
        if (busy || !canEdit || !resumed) return
        busy = true
        work.execute {
            val result = runCatching { IconPackRepository.rename(applicationContext, id, name) }
            handler.post { if (!destroyed) {
                busy = false
                result.onSuccess { pack -> iconPacks = iconPacks.map { if (it.id == id) pack else it }; toast("名称已保存") }
                    .onFailure { toast("重命名失败：${it.message}") }
            } }
        }
    }
    fun deleteIconPack(id: String, name: String) {
        if (busy || !canEdit) return
        iconLibraryOpen = false
        confirmation = UiConfirmation("删除图标库", "删除“${name}”及其导入文件，其他图标库与原生样式保留。", "删除",
            cancelAction = { iconLibraryOpen = true }) {
            if (!resumed || !canEdit || busy) { toast("权限状态已变化，请重新打开图标库"); return@UiConfirmation }
            val remaining = runCatching { configuredLayers().filter { it.id != id } }.getOrElse {
                toast(it.message ?: "图标库配置读取失败"); return@UiConfirmation
            }
            if (!saveIconLayers(remaining)) { iconLibraryOpen = true; return@UiConfirmation }
            busy = true
            work.execute {
                val result = runCatching { IconPackRepository.delete(applicationContext, id) }
                handler.post { if (!destroyed) {
                    busy = false; iconLibraryOpen = true
                    result.onSuccess { iconPacks = iconPacks.filter { it.id != id }; toast("图标库已删除") }
                        .onFailure { toast("图标库已停用，文件删除失败：${it.message}"); refreshIconLibraries() }
                } }
            }
        }
    }
    fun chooseIconPack() {
        if (canEdit && !busy) importIconPack.launch(arrayOf("*/*")) else toast("请先激活模块或授予 Root")
    }
    fun openPuiThemeImport() {
        if (canEdit && !busy) importPuiTheme.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        else toast("请先激活模块或授予 Root")
    }
    private fun preparePuiTheme(uri: Uri) {
        work.execute {
            try { completeIconPack(IconPackRepository.preparePuiTheme(applicationContext, uri), null) }
            catch (error: Exception) { handler.post { if (!destroyed) {
                pendingFile = null; busy = false; toast("PUI 主题导入失败：${error.message}")
            } } }
        }
    }
    private fun prepareIconPack(uri: Uri) {
        work.execute {
            try { completeIconPack(IconPackRepository.prepare(applicationContext, uri, ""), null) }
            catch (error: Exception) { handler.post { if (!destroyed) {
                pendingFile = null; busy = false; toast("图标库导入失败：${error.message}")
            } } }
        }
    }
    fun downloadIconPack(source: String, name: String) {
        if (!resumed || !canEdit || busy || destroyed) return
        if (source.isBlank()) { toast("请填写 GitHub 仓库或下载网址"); return }
        val token = IconPackDownload.Cancellation()
        iconPackCancellation = token; iconPackDownloading = true; busy = true
        work.execute {
            try { completeIconPack(IconPackRepository.prepareDownload(applicationContext, source, name, token), token) }
            catch (error: Exception) { handler.post { if (!destroyed && iconPackCancellation === token) {
                iconPackCancellation = null; iconPackDownloading = false; busy = false
                toast("图标库下载失败：${error.message}")
            } } }
        }
    }
    private fun completeIconPack(prepared: IconPackRepository.PreparedPack, token: IconPackDownload.Cancellation?) {
        handler.post {
            try { prepared.use {
                if (token != null && iconPackCancellation !== token) return@post
                val layers = configuredLayers().toMutableList()
                val wasImported = iconPacks.any { it.id == prepared.pack.id }
                val pack = prepared.commit { resumed && canEdit && !destroyed && (token == null || !token.getAsBoolean()) }
                if (layers.none { it.id == pack.id }) layers.add(IconPackRepository.Layer(pack.id, false, false))
                val saved = saveIconLayers(layers)
                iconPacks = iconPacks.filter { it.id != pack.id } + pack
                toast(if (!saved) "图标库文件已导入，排序配置未保存；请在管理页重试" else if (wasImported) "相同图标库已更新名称，原启用状态保留" else "图标库已导入，默认禁用；启用后可逐项选择")
            } } catch (error: Exception) { if (!destroyed) toast("图标库导入失败：${error.message}") }
            finally {
                prepared.close()
                if (token == null || iconPackCancellation === token) {
                    pendingFile = null; iconPackCancellation = null; iconPackDownloading = false; busy = false
                }
            }
        }
    }
    fun cancelIconPackDownload() {
        iconPackCancellation?.cancel(); iconPackCancellation = null; iconPackDownloading = false; busy = false
    }
    fun pickColor(item: SettingsCatalog.Item) {
        if (!canEdit) { toast("请先激活模块或授予 Root"); return }
        colorEditing = item
    }
    fun saveColor(item: SettingsCatalog.Item, color: Int, customAlpha: Boolean) {
        if (resumed && canEdit) preferences.edit().putInt(item.key, color).putBoolean(item.alphaKey, customAlpha).apply()
    }
    fun resetGroup(group: SettingsCatalog.Group, items: List<SettingsCatalog.Item> = group.items, title: String = group.title) {
        val resetting = items.toList()
        confirmation = UiConfirmation("恢复${title}默认设置", "只恢复当前页面的设置，其他配置会保留。", "恢复默认") {
            if (!canEdit) { toast("请先激活模块或授予 Root"); return@UiConfirmation }
            val editor = preferences.edit()
            resetting.forEach { editor.remove(it.key); if (it.type == "color") editor.remove(StatusBarSettings.alphaKey(it.key)) }
            editor.apply()
        }
    }
    fun toast(message: String) { if (!destroyed) Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}

data class UiConfirmation(val title: String, val summary: String, val button: String, val delaySeconds: Int = 0,
    val expiresAt: Long = 0,
    val cancelAction: (() -> Unit)? = null, val action: () -> Unit)
