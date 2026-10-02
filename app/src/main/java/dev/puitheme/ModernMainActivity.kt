// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.ComposeView
import java.util.concurrent.Executors

/** The settings app uses the same Miuix component system as the current LSPosed manager. */
class ModernMainActivity : ComponentActivity() {
    var page by mutableIntStateOf(0)
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
    var confirmation by mutableStateOf<UiConfirmation?>(null)
    var editing by mutableStateOf<SettingsCatalog.Item?>(null)
    var colorEditing by mutableStateOf<SettingsCatalog.Item?>(null)
    var fontCatalogOpen by mutableStateOf(false)
    var downloadingFont by mutableStateOf<FontCatalog.Entry?>(null)
        private set
    var fontDownloadPercent by mutableIntStateOf(0)
        private set
    private var fontCancellation: FontDownloadRepository.Cancellation? = null
    val canEdit get() = runtimeActive || rootGranted || lspEnabled
    @Volatile private var resumed = false
    @Volatile private var destroyed = false
    private lateinit var raw: SharedPreferences
    private lateinit var preferences: SharedPreferences
    private var probe: ModuleRuntimeStatus.ProbeHandle? = null
    private var frameworkSubscription: AppFrameworkStatus.Subscription? = null
    private var rootRequest: RootAccess.Request? = null
    private var restartRequest: SystemUiRestart.Request? = null
    private lateinit var navHost: ComposeView
    private lateinit var modalLayer: ComposeView
    private var keyboardOpen = false
    private val handler = Handler(Looper.getMainLooper())
    private val work = Executors.newSingleThreadExecutor()
    private var pendingFile: Pair<String, Uri>? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post {
            if (!destroyed) values = raw.all
            handler.removeCallbacks(notifySettings)
            handler.post(notifySettings)
        }
    }
    private val notifySettings = Runnable {
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
        raw = StatusBarSettings.preferences(this)
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
            setContent { C17Theme { C17Navigation(page) { selectPage(it) } } }
        }
        root.addView(navHost, FrameLayout.LayoutParams(-1, (72 * density).toInt(), Gravity.BOTTOM).apply {
            leftMargin = 0; rightMargin = 0; bottomMargin = 0
        })
        modalLayer = ComposeView(this).apply {
            consumeWindowInsets = false
            setContent { C17Theme { C17OverlayLayer(this@ModernMainActivity) } }
            visibility = View.GONE
        }
        // Native z-order also matches touch and accessibility order: all app popups own this layer.
        root.addView(modalLayer, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val safeLeft: Int
            val safeRight: Int
            val bottom = if (android.os.Build.VERSION.SDK_INT >= 30) {
                keyboardOpen = insets.isVisible(WindowInsets.Type.ime())
                val safeSides = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                safeLeft = safeSides.left
                safeRight = safeSides.right
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                val cutout = if (android.os.Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
                safeLeft = maxOf(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0)
                safeRight = maxOf(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0)
                insets.systemWindowInsetBottom
            }
            val contentParams = content.layoutParams as FrameLayout.LayoutParams
            if (contentParams.leftMargin != safeLeft || contentParams.rightMargin != safeRight) {
                contentParams.leftMargin = safeLeft
                contentParams.rightMargin = safeRight
                content.layoutParams = contentParams
            }
            val params = navHost.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = safeLeft
            params.rightMargin = safeRight
            params.bottomMargin = bottom
            navHost.layoutParams = params
            updateNavigation()
            insets
        }
        setContentView(root)
        updateNavigation()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    colorEditing != null -> colorEditing = null
                    downloadingFont != null -> cancelFontDownload()
                    fontCatalogOpen -> fontCatalogOpen = false
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
        values = raw.all
        checkActivation()
        if (getSharedPreferences("app_migrations", MODE_PRIVATE).getBoolean("root_requested", false) && !rootGranted) requestRoot()
        replayFile()
    }

    override fun onPause() {
        if (downloadingFont != null) cancelFontDownload()
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
        destroyed = true; raw.unregisterOnSharedPreferenceChangeListener(listener)
        probe?.cancel(); frameworkSubscription?.close(); rootRequest?.cancel(); restartRequest?.cancel()
        handler.removeCallbacksAndMessages(null); work.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page); outState.putString("group", selectedGroup)
        outState.putString("query", query); outState.putString("category", category)
        pendingFile?.let { outState.putString("file_kind", it.first); outState.putString("file_uri", it.second.toString()) }
        super.onSaveInstanceState(outState)
    }

    fun selectPage(index: Int) {
        page = index; selectedGroup = null; updateNavigation()
    }
    fun openGroup(id: String) { selectedGroup = id; updateNavigation() }
    fun closeGroup() { selectedGroup = null; updateNavigation() }
    fun syncModalLayer(visible: Boolean) {
        if (::modalLayer.isInitialized) modalLayer.visibility = if (visible) View.VISIBLE else View.GONE
    }
    private fun updateNavigation() { if (::navHost.isInitialized) navHost.visibility = if (selectedGroup == null && !keyboardOpen) View.VISIBLE else View.GONE }
    fun value(key: String): Any? = SettingsCatalog.value(values, key)
    fun bool(key: String): Boolean = StatusBarSettings.bool(values, key)
    fun save(key: String, value: Any): Boolean {
        if (!resumed || !canEdit) { toast("激活模块或授予 Root 后即可保存配置"); return false }
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
    fun chooseFont() { if (canEdit) importFont.launch(arrayOf("*/*")) else toast("请先激活模块或授予 Root") }
    fun chooseOpenFont() { if (canEdit && !busy) fontCatalogOpen = true else toast("请先激活模块或授予 Root") }
    fun downloadFont(entry: FontCatalog.Entry) {
        if (!resumed || !canEdit || busy) return
        val token = FontDownloadRepository.Cancellation()
        fontCatalogOpen = false; downloadingFont = entry; fontDownloadPercent = 0; fontCancellation = token; busy = true
        work.execute {
            try {
                val prepared = FontDownloadRepository.prepare(applicationContext, entry, { received, total ->
                    runOnUiThread { if (fontCancellation === token) fontDownloadPercent = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0 }
                }, token)
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
    fun dismissConfirmation() { confirmation?.cancelAction?.invoke(); confirmation = null }
    fun openUrl(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("没有可用的浏览器") } }
    fun checkUpdates() {
        if (!resumed || destroyed || checkingUpdates || busy) return
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
                confirmation = UiConfirmation(if (result.newer) "发现新版本" else "更新检查", details,
                    if (download) "下载 APK" else "查看正式发布") {
                    if (download) openUrl(result.apkUrl) else openLatestRelease()
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
        val displayName = name
        work.execute {
            try {
                val prepared = FontRepository.prepareFont(applicationContext, uri)
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
                            pendingFile = null; busy = false; toast("字体已导入")
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
