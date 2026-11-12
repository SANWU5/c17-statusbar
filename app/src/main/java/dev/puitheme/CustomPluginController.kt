// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

/** Local transactions and public repository requests have independent lifetimes. */
class CustomPluginController(private val app: ModernMainActivity) {
    var opened by mutableStateOf(false)
    private var selectedIdValue by mutableStateOf<String?>(null)
    var selectedId: String?
        get() = selectedIdValue
        set(value) { if (value != selectedIdValue) { moreRefreshEpoch++; cancelFormStatus(); settingsPath = emptyList(); editing = null }; selectedIdValue = value }
    var settingsPath by mutableStateOf<List<String>>(emptyList())
        private set
    fun openSettingsGroup(path: List<String>) {
        if (working || path.size > 3) return
        val controls = selected?.descriptor?.optJSONArray("ui") ?: return
        if (path.isEmpty() || (0 until controls.length()).any {
                CustomPluginSchema.groupPath(controls.getJSONObject(it)).take(path.size) == path
            }) settingsPath = path.toList()
    }
    fun resetSettingsPath() { settingsPath = emptyList() }
    var tab by mutableIntStateOf(1)
        private set
    var plugins by mutableStateOf<List<CustomPluginRepository.Plugin>>(emptyList())
        private set
    private var localWorking by mutableStateOf(false)
    var catalogLoading by mutableStateOf(false)
        private set
    var downloadLoading by mutableStateOf(false)
        private set
    val working get() = localWorking || downloadLoading
    var error by mutableStateOf("")
        private set
    var catalogError by mutableStateOf("")
        private set
    var preview by mutableStateOf<CustomPluginRepository.Preview?>(null)
        private set
    var previewOrigin by mutableStateOf("本地文件")
        private set
    private data class EditDraft(val id: String, val version: Int, val control: JSONObject)
    private var editDraft by mutableStateOf<EditDraft?>(null)
    var editing: JSONObject?
        get() = editDraft?.control
        set(value) {
            val plugin = selected
            editDraft = if (value == null || plugin == null) null
                else EditDraft(plugin.id, plugin.versionCode, JSONObject(value.toString()))
        }
    var helpOpen by mutableStateOf(false)
    var policyOpen by mutableStateOf(false)
    var policyAccepted by mutableStateOf(false)
        private set
    var sortOpen by mutableStateOf(false)
    var sortDraft by mutableStateOf<CustomPluginSortDraft?>(null)
        private set
    var sortLoading by mutableStateOf(false)
        private set
    var sortSaving by mutableStateOf(false)
        private set
    @Volatile private var sortGeneration = 0
    private var sortModeValue by mutableStateOf("custom")
    val sortMode get() = sortModeValue
    var sortAscending by mutableStateOf(true)
        private set
    private var manualOrder by mutableStateOf<List<String>>(emptyList())
    val sortedPlugins get() = CustomPluginOrdering.sort(plugins, sortMode, sortAscending, manualOrder)
    var sources by mutableStateOf(listOf(CustomPluginCatalog.defaultSource()))
        private set
    var selectedSourceId by mutableStateOf<String?>(CustomPluginCatalog.defaultSource().id)
        private set
    var catalog by mutableStateOf<CustomPluginCatalog.Catalog?>(null)
        private set
    val catalogEntries get() = catalog?.entries ?: emptyList()
    var repositoryInputOpen by mutableStateOf(false)
    var repositoryInput by mutableStateOf("")
    val hasDialog get() = preview != null || editing != null || helpOpen || policyOpen || sortOpen || repositoryInputOpen || sortDraft != null || sortLoading
    val selected get() = plugins.firstOrNull { it.id == selectedId }
    var frameworkScopes by mutableStateOf(AppFrameworkStatus.ScopeSnapshot.unknown())
        private set
    private var scopeGeneration = 0
    private var scopeEvidenceTask: Future<*>? = null
    private val scopeEvidenceWorker = Executors.newSingleThreadExecutor()
    private var frameworkSubscription: AppFrameworkStatus.Subscription? = null
    var scopeRequestStatus by mutableStateOf<CustomPluginScopeController.Snapshot?>(null)
        private set
    var scopeRequestPluginId by mutableStateOf<String?>(null)
        private set
    private var scopeRequest: CustomPluginScopeController.Request? = null
    private var scopeRequestGeneration = 0
    var scopeRestartPluginId by mutableStateOf<String?>(null)
        private set
    var scopeRestartMessage by mutableStateOf("")
        private set
    val scopeRequestRunning get() = scopeRequestStatus?.state == CustomPluginScopeController.State.CHECKING ||
        scopeRequestStatus?.state == CustomPluginScopeController.State.PENDING
    private val formSnapshots = mutableStateMapOf<String, JSONObject>()
    fun statusText(id: String, key: String) = formSnapshots[id]?.optJSONObject("status")?.optString(key).orEmpty()
    fun actionReady(id: String, key: String): Boolean {
        if (app.bool(StatusBarSettings.SAFE_MODE)) return false
        val actions = formSnapshots[id]?.optJSONArray("actions") ?: return false
        return (0 until actions.length()).any { actions.optString(it) == key }
    }
    val selectedSource get() = sources.firstOrNull { it.id == selectedSourceId } ?: sources.first()
    private val ui by lazy { app.getSharedPreferences("custom_plugin_ui", Context.MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    // A target process may take eight seconds to answer. Reading its status must never
    // disable controls or queue configuration writes behind that remote response.
    private val statusWorker = Executors.newSingleThreadExecutor()
    private var statusTask: Future<*>? = null
    @Volatile private var statusGeneration = 0
    private val network = Executors.newSingleThreadExecutor()
    private var networkTask: Future<*>? = null
    @Volatile private var networkGeneration = 0
    private var pendingAfterPolicy: (() -> Unit)? = null
    @Volatile private var closed = false
    @Volatile private var moreRefreshEpoch = 0
    private val previewLock = Any()
    // A producer owns its preview until queued here, then the UI or cleanup owns it.
    private val pendingPreviews = mutableMapOf<CustomPluginRepository.Preview, Int?>()
    private var pendingUri: String? = null
    private data class ConfigurationTarget(val id: String, val version: Int)
    private var configurationImportTarget: ConfigurationTarget? = null
    private var configurationExportTarget: ConfigurationTarget? = null
    private var observed = false
    private var reloadRequested = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (closed || !opened) return
            if (localWorking) reloadRequested = true else refresh()
        }
    }
    private val repository get() = CustomPluginRepository(app.applicationContext)
    private val picker = app.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingUri = uri.toString(); prepare(uri)
        }
    }
    private val configurationPicker = app.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = configurationImportTarget; configurationImportTarget = null
        if (uri != null && target != null) local {
            val prepared = requireNotNull(app.contentResolver.openInputStream(uri)) { "无法打开所选配置文件" }.use {
                repository.previewConfiguration(target.id, target.version, it)
            }
            deliver {
                app.confirmation = UiConfirmation("导入插件配置",
                    trf("将 %1\$s（%2\$s）的 %3\$s 项设置导入此插件。保留插件启用状态及文件未包含的设置。", prepared.name, prepared.version, prepared.settingCount), "导入") {
                    if (plugins.firstOrNull { it.id == prepared.id }?.enabled == true && !app.canEdit)
                        app.toast("激活模块或授予 Root 后即可修改已启用的插件")
                    else local(after = { app.toast("插件配置已导入") }) { repository.importConfiguration(prepared); loadPlugins() }
                }
            }
        }
    }
    private val configurationWriter = app.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val target = configurationExportTarget; configurationExportTarget = null
        if (uri != null && target != null) local(after = { app.toast("插件配置已导出") }) {
            val json = repository.exportConfiguration(target.id, target.version)
            requireNotNull(app.contentResolver.openOutputStream(uri, "wt")) { "无法写入所选配置文件" }.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun restore(saved: Bundle?) {
        if (frameworkSubscription == null) frameworkSubscription = AppFrameworkStatus.observe { refreshScopeEvidence() }
        if (!observed) {
            app.contentResolver.registerContentObserver(Uri.parse(StatusBarSettings.CONTENT_URI), true, observer); observed = true
        }
        runCatching {
            sortModeValue = ui.getString("sort_mode", "custom")?.takeIf(CustomPluginOrdering::validMode) ?: "custom"
            sortAscending = ui.getBoolean("sort_ascending", true)
            val ids = JSONArray(ui.getString("manual_order", "[]"))
            manualOrder = (0 until ids.length()).map { ids.getString(it) }.distinct().take(64)
            policyAccepted = ui.getInt("policy_version", 0) >= CustomPluginPolicy.VERSION
            val stored = JSONArray(ui.getString("sources", "[]"))
            val added = (0 until minOf(stored.length(), 7)).mapNotNull { index -> runCatching { CustomPluginCatalog.restore(stored.getJSONObject(index)) }.getOrNull() }
            sources = (listOf(CustomPluginCatalog.defaultSource()) + added).distinctBy { it.id }
            selectedSourceId = ui.getString("selected_source", sources.first().id)?.takeIf { id -> sources.any { it.id == id } } ?: sources.first().id
        }.onFailure { error = "部分显示设置未能读取，已保留默认值" }
        opened = saved?.getBoolean("custom_plugins_open") == true
        selectedId = saved?.getString("custom_plugins_selected")
        tab = (saved?.getInt("custom_plugins_tab", 1) ?: 1).coerceIn(0, 1)
        policyOpen = saved?.getBoolean("custom_plugins_policy") == true
        pendingUri = saved?.getString("custom_plugins_uri")
        saved?.getString("custom_plugins_config_import_id")?.let {
            configurationImportTarget = ConfigurationTarget(it, saved.getInt("custom_plugins_config_import_version"))
        }
        saved?.getString("custom_plugins_config_export_id")?.let {
            configurationExportTarget = ConfigurationTarget(it, saved.getInt("custom_plugins_config_export_version"))
        }
        if (opened) {
            if (pendingUri != null) prepare(Uri.parse(pendingUri)) else refresh()
            if (tab == 1) refreshCatalog()
        }
    }
    fun save(out: Bundle) {
        out.putBoolean("custom_plugins_open", opened); out.putString("custom_plugins_selected", selectedId)
        out.putInt("custom_plugins_tab", tab); out.putBoolean("custom_plugins_policy", policyOpen)
        pendingUri?.let { out.putString("custom_plugins_uri", it) }
        configurationImportTarget?.let { out.putString("custom_plugins_config_import_id", it.id); out.putInt("custom_plugins_config_import_version", it.version) }
        configurationExportTarget?.let { out.putString("custom_plugins_config_export_id", it.id); out.putInt("custom_plugins_config_export_version", it.version) }
    }
    fun open() { opened = true; selectedId = null; tab = 1; refresh(); refreshCatalog() }
    fun leave() { moreRefreshEpoch++; cancelFormStatus(); cancelNetwork(); dismissSort(); opened = false }
    fun selectTab(value: Int) {
        tab = value.coerceIn(0, 1); selectedId = null
        if (tab == 1 && catalog == null && !catalogLoading) refreshCatalog()
    }
    fun resume() { if (opened && pendingUri != null && preview == null && !localWorking) prepare(Uri.parse(pendingUri)) else if (opened && !localWorking) refreshPage() }
    fun refresh() { refreshScopeEvidence(); local { loadPlugins() } }
    // Explicit refresh/resume also retries missing or stale runtime evidence. ContentObserver
    // refreshes deliberately do not: a form read itself emits a configuration notification.
    fun refreshPage() { refreshScopeEvidence(); local(after = { selected?.let(::refreshFormStatus) }) { loadPlugins() } }
    private fun refreshScopeEvidence() {
        val generation = ++scopeGeneration
        scopeEvidenceTask?.cancel(true)
        val framework = AppFrameworkStatus.current()
        if (closed || !framework.connected || !framework.compatible) {
            frameworkScopes = AppFrameworkStatus.ScopeSnapshot.unknown(); return
        }
        try { scopeEvidenceTask = scopeEvidenceWorker.submit {
            val evidence = AppFrameworkStatus.queryScopes()
            deliver { if (generation == scopeGeneration && AppFrameworkStatus.current() === framework) frameworkScopes = evidence }
        } } catch (_: RejectedExecutionException) { /* Closing the page cancels pending evidence. */ }
    }
    fun requestPluginScopes(plugin: CustomPluginRepository.Plugin) {
        if (closed || working || scopeRequestRunning) return
        scopeRequest?.close()
        val generation = ++scopeRequestGeneration
        scopeRequestPluginId = plugin.id
        scopeRequestStatus = null
        scopeRequest = CustomPluginScopeController.request(app.applicationContext, plugin.descriptor) { result ->
            if (!closed && generation == scopeRequestGeneration) {
                scopeRequestStatus = result
                if (result.state != CustomPluginScopeController.State.CHECKING &&
                    result.state != CustomPluginScopeController.State.PENDING) refreshScopeEvidence()
            }
        }
        // Close the gap before the helper's first main-thread callback (rapid double taps).
        scopeRequestStatus = scopeRequest?.current()
    }
    fun cancelScopeRequest() {
        scopeRequestGeneration++; scopeRequest?.close(); scopeRequest = null
        scopeRequestStatus = null; scopeRequestPluginId = null
        refreshScopeEvidence()
    }
    fun restartPluginScopes(plugin: CustomPluginRepository.Plugin) {
        if (closed || working || scopeRequestRunning) return
        scopeRestartPluginId = plugin.id
        scopeRestartMessage = "正在重启已授权的作用域…"
        local(after = { refreshScopeEvidence(); refreshFormStatus(plugin) }) {
            try {
                val message = CustomPluginScopeRestart.restart(app.applicationContext, plugin.descriptor)
                deliver { scopeRestartMessage = message; app.toast(message) }
            } catch (failure: Exception) {
                deliver { scopeRestartMessage = failure.message?.take(400) ?: "作用域重启失败，请检查 Root 授权" }
                throw failure
            }
        }
    }
    fun openNativeSettings(plugin: CustomPluginRepository.Plugin) {
        if (working || app.bool(StatusBarSettings.SAFE_MODE) || !plugin.descriptor.has("nativeSettings")) return
        app.startActivity(Intent(app, CustomPluginNativeSettingsActivity::class.java)
            .putExtra(CustomPluginNativeSettingsActivity.EXTRA_ID, plugin.id).putExtra(CustomPluginNativeSettingsActivity.EXTRA_VERSION, plugin.versionCode))
    }
    fun chooseFile() = withPolicy { if (!working && !hasDialog) picker.launch(arrayOf("*/*")) }
    fun dismissPreview() { if (!localWorking) { val old = preview; preview = null; pendingUri = null; releasePreview(old) } }
    private fun prepare(uri: Uri) = local {
        try {
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "plugin.js"
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use {
                if (it != null && it.moveToFirst()) name = it.getString(0)
            }
            val input = requireNotNull(app.contentResolver.openInputStream(uri)) { "无法打开所选文件" }
            val prepared = preparePreview(input, name)
            deliverPreview(prepared, "本地文件")
        } catch (failure: Exception) { deliver { pendingUri = null }; throw failure }
    }
    fun install() {
        val prepared = preview ?: return
        if (!policyAccepted) { withPolicy { install() }; return }
        val origin = previewOrigin; pendingUri = null
        local {
            val result = repository.install(prepared, prepared.upgrade, origin)
            releasePreview(prepared)
            deliver { selectedId = result.id; tab = 0; if (preview === prepared) preview = null; app.toast(if (prepared.upgrade) "插件已更新，请检查后启用" else "插件已导入，启用后生效") }
            loadPlugins()
        }
    }
    fun setEnabled(plugin: CustomPluginRepository.Plugin, enabled: Boolean) {
        if (enabled && !app.canEdit) { app.toast("激活模块或授予 Root 后即可启用插件"); return }
        if (enabled && !policyAccepted) { withPolicy { setEnabled(plugin, true) }; return }
        local { repository.setEnabled(plugin.id, enabled); loadPlugins() }
    }
    fun disableAll() = local { repository.setAllDisabled(); loadPlugins() }
    fun chooseConfiguration(plugin: CustomPluginRepository.Plugin) {
        if (working || hasDialog) return
        if (plugin.enabled && !app.canEdit) { app.toast("激活模块或授予 Root 后即可修改已启用的插件"); return }
        configurationImportTarget = ConfigurationTarget(plugin.id, plugin.versionCode)
        configurationPicker.launch(arrayOf("*/*"))
    }
    fun exportConfiguration(plugin: CustomPluginRepository.Plugin) {
        if (working || hasDialog) return
        configurationExportTarget = ConfigurationTarget(plugin.id, plugin.versionCode)
        configurationWriter.launch("${plugin.id}-config.json")
    }
    fun resetConfiguration(plugin: CustomPluginRepository.Plugin) {
        if (plugin.enabled && !app.canEdit) { app.toast("激活模块或授予 Root 后即可修改已启用的插件"); return }
        app.confirmation = UiConfirmation("恢复插件默认设置", trf("恢复 %s 的全部独立配置，保留插件当前启用状态。", plugin.name), "恢复默认") {
            local { repository.resetState(plugin.id); loadPlugins() }
        }
    }
    fun returnFromRelatedSettings() { opened = true; tab = 0; refresh() }
    fun update(key: String, value: Any) {
        val id = selectedId ?: return
        if (selected?.enabled == true && !app.canEdit) { app.toast("激活模块或授予 Root 后即可修改已启用的插件"); return }
        local { repository.updateState(id, key, value); loadPlugins() }
    }
    /** Dialogs commit only to the exact plugin version and control they were opened from. */
    fun saveEditing(control: JSONObject, value: Any): String? {
        val draft = editDraft
        if (draft == null || draft.control !== control || !currentEdit(draft)) {
            editing = null
            return "插件设置已改变，请重新打开编辑"
        }
        if (working || closed) return "正在处理，请稍后重试"
        if (selected?.enabled == true && !app.canEdit) return "激活模块或授予 Root 后即可修改已启用的插件"
        local { repository.updateStateControl(draft.id, draft.version, draft.control, value); loadPlugins() }
        return null
    }
    private fun currentEdit(draft: EditDraft): Boolean {
        val plugin = selected ?: return false
        if (plugin.id != draft.id || plugin.versionCode != draft.version) return false
        val controls = plugin.descriptor.optJSONArray("ui") ?: return false
        val key = draft.control.optString("key")
        return (0 until controls.length()).any {
            val field = controls.getJSONObject(it)
            field.optString("key") == key && field.toString() == draft.control.toString()
        }
    }
    fun applyPreset(plugin: CustomPluginRepository.Plugin, control: JSONObject) {
        if (plugin.enabled && !app.canEdit) { app.toast("激活模块或授予 Root 后即可修改已启用的插件"); return }
        local(after = { app.toast(trf("已应用%s", CustomPluginLocalization.forPlugin(plugin).tr(control.getString("title")))) }) {
            repository.applyPreset(plugin.id, plugin.versionCode, control.getString("key")); loadPlugins()
        }
    }
    fun refreshFormStatus(plugin: CustomPluginRepository.Plugin) {
        cancelFormStatus()
        if (!plugin.enabled || app.bool(StatusBarSettings.SAFE_MODE)) { formSnapshots.remove(plugin.id); return }
        val form = CustomPluginSchema.form(plugin.descriptor)
        if (form.optJSONArray("actions")?.length() == 0 && form.optJSONArray("statuses")?.length() == 0) return
        val generation = statusGeneration
        try { statusTask = statusWorker.submit {
            val snapshot = runCatching { repository.formStatus(plugin.id) }.getOrNull()
            deliver {
                if (generation == statusGeneration && selectedId == plugin.id && selected?.versionCode == plugin.versionCode && selected?.enabled == true) {
                    if (snapshot == null) formSnapshots.remove(plugin.id) else formSnapshots[plugin.id] = snapshot
                }
            }
        } } catch (_: RejectedExecutionException) { /* Activity was closed while scheduling a read. */ }
    }
    private fun cancelFormStatus() { statusGeneration++; statusTask?.cancel(true); statusTask = null }
    fun runAction(plugin: CustomPluginRepository.Plugin, key: String, confirmedInstance: String? = null) {
        if (closed || localWorking) return
        if (app.bool(StatusBarSettings.SAFE_MODE) || !plugin.enabled || !app.canEdit) return
        val snapshot = formSnapshots[plugin.id] ?: return
        val instance = snapshot.optString("instanceId")
        if (instance.isBlank() || !actionReady(plugin.id, key)) return
        if (confirmedInstance != null && confirmedInstance != instance) {
            app.toast("插件运行实例已更新，请重新确认操作"); return
        }
        val controls = plugin.descriptor.optJSONArray("ui")
        val control = (0 until (controls?.length() ?: 0)).mapNotNull { controls?.optJSONObject(it) }
            .firstOrNull { it.optString("type") == "action" && it.optString("key") == key }
        val confirmation = control?.optString("confirmation").orEmpty()
        if (confirmedInstance == null && confirmation.isNotBlank()) {
            val text = CustomPluginLocalization.forPlugin(plugin)
            app.confirmation = UiConfirmation(text.tr(control!!.optString("title")), text.tr(confirmation), "确认继续") {
                runAction(plugin, key, instance)
            }
            return
        }
        val monitorRefresh = CustomPluginMoreRefresh.matches(plugin.id, key)
        val refreshEpoch = if (monitorRefresh) ++moreRefreshEpoch else 0
        cancelFormStatus()
        val actionStatusGeneration = statusGeneration
        local {
            if (monitorRefresh && (closed || refreshEpoch != moreRefreshEpoch)) return@local
            val before = if (monitorRefresh) sortCatalog(plugin.id, CustomPluginMoreRefresh.CATALOG_ACTION, instance).optJSONObject("catalogRefresh") else null
            if (monitorRefresh && (closed || refreshEpoch != moreRefreshEpoch)) return@local
            val result = try { repository.runAction(plugin.id, key, instance) } catch (failure: Exception) {
                val current = runCatching { repository.formStatus(plugin.id) }.getOrNull()
                deliver { if (actionStatusGeneration == statusGeneration) {
                    if (current == null) formSnapshots.remove(plugin.id) else formSnapshots[plugin.id] = current
                } }
                throw failure
            }
            val updated = JSONObject(snapshot.toString()).put("status", result.optJSONObject("status") ?: JSONObject())
            deliver { if (actionStatusGeneration == statusGeneration) formSnapshots[plugin.id] = updated }
            check(result.optBoolean("ok")) { result.optString("error", "插件操作未完成") }
            val value = result.opt("value")
            // ui.action-state: an action may hand back settings to store (e.g. a calibration it measured).
            val returned = (value as? JSONObject)?.optJSONObject("state")
            if (returned != null && CustomPluginSupport.requires(plugin.descriptor, "ui.action-state")) {
                for (name in returned.keys()) repository.updateStateVersion(plugin.id, plugin.versionCode, name, returned.get(name))
                loadPlugins()
            }
            val message = when (value) { is String -> value; is JSONObject -> value.optString("message"); else -> "" }
            if (monitorRefresh) awaitMoreRefresh(plugin.id, instance, refreshEpoch, before)
            else deliver { app.toast(CustomPluginLocalization.forPlugin(plugin).tr(message).ifBlank { "操作已完成" }.take(200)) }
        }
    }
    private fun awaitMoreRefresh(id: String, instance: String, epoch: Int, before: JSONObject?) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 6000
        var generation = 0L
        try {
            for (attempt in 0 until 24) {
                if (closed || epoch != moreRefreshEpoch) return
                val current = sortCatalog(id, CustomPluginMoreRefresh.CATALOG_ACTION, instance).optJSONObject("catalogRefresh")
                if (closed || epoch != moreRefreshEpoch) return
                if (generation == 0L) generation = CustomPluginMoreRefresh.generation(before, current)
                val terminal = CustomPluginMoreRefresh.terminal(current, generation)
                if (terminal != null) {
                    deliver { if (epoch == moreRefreshEpoch) app.toast(terminal.take(200)) }
                    return
                }
                if (android.os.SystemClock.elapsedRealtime() >= deadline) break
                Thread.sleep(250)
            }
            if (!closed && epoch == moreRefreshEpoch) deliver {
                if (epoch == moreRefreshEpoch) error = "未能确认目录更新结果，请稍后重新读取；后台更新可能仍在进行"
            }
        } catch (failure: Exception) {
            if (!closed && epoch == moreRefreshEpoch) deliver {
                if (epoch == moreRefreshEpoch) error = failure.message?.take(400) ?: "读取目录更新结果失败"
            }
        }
    }
    fun openSort(plugin: CustomPluginRepository.Plugin, control: JSONObject) {
        if (working || sortDraft != null || sortLoading) return
        val callback = control.optString("optionsAction")
        if (!plugin.enabled || !app.canEdit || !app.runtimeActive || app.bool(StatusBarSettings.SAFE_MODE)) {
            app.toast("插件启用并在当前宿主就绪后才能排序"); return
        }
        if (!actionReady(plugin.id, callback)) {
            app.toast("插件操作尚未就绪，请展开控制中心后刷新重试"); return
        }
        val generation = ++sortGeneration
        sortLoading = true
        local {
            try {
                val current = repository.list().firstOrNull { it.id == plugin.id }
                    ?: error("插件已移除，请重新打开")
                check(current.enabled && current.versionCode == plugin.versionCode) { "插件状态已改变，请重新打开排序" }
                val field = sortControl(current, control.getString("key"), callback)
                val baseValue = sortValue(current, field)
                val snapshot = repository.formStatus(plugin.id)
                val instance = sortInstance(snapshot, callback)
                val catalog = CustomPluginSort.read(baseValue, sortCatalog(plugin.id, callback, instance))
                if (generation != sortGeneration || closed) return@local
                deliver {
                    if (generation == sortGeneration && opened && selectedId == plugin.id) {
                        formSnapshots[plugin.id] = snapshot
                        sortDraft = CustomPluginSortDraft(plugin.id, plugin.versionCode, field.getString("key"), callback,
                            field.getString("title"), baseValue, instance, catalog)
                    }
                }
            } catch (failure: Exception) {
                if (generation == sortGeneration) throw failure
            } finally { deliver { if (generation == sortGeneration) sortLoading = false } }
        }
    }
    fun dismissSort() {
        if (sortSaving) return
        sortGeneration++; sortLoading = false; sortDraft = null
    }
    fun saveSort(draft: CustomPluginSortDraft, order: List<String>, hidden: Set<String> = draft.catalog.hidden) {
        if (working || sortDraft !== draft) return
        if (!app.canEdit || !app.runtimeActive || app.bool(StatusBarSettings.SAFE_MODE)) {
            app.toast("当前宿主未就绪，恢复插件运行后再保存排序"); return
        }
        val value = try { draft.catalog.value(order, hidden) } catch (failure: Exception) {
            error = failure.message.orEmpty(); return
        }
        val generation = sortGeneration
        sortSaving = true
        local {
            try {
                val current = repository.list().firstOrNull { it.id == draft.pluginId }
                    ?: error("插件已移除，请重新打开")
                check(current.enabled && current.versionCode == draft.versionCode) { "插件状态已改变，请重新打开排序" }
                val field = sortControl(current, draft.key, draft.optionsAction)
                check(sortValue(current, field) == draft.baseValue) { "排序已在其他位置修改，请重新打开" }
                val snapshot = repository.formStatus(draft.pluginId)
                check(sortInstance(snapshot, draft.optionsAction) == draft.instanceId) { "插件运行实例已更新，请重新打开排序" }
                val fresh = sortCatalog(draft.pluginId, draft.optionsAction, draft.instanceId)
                check(draft.catalog.matches(fresh)) { fresh.optString("message").ifBlank { "排序项目已改变，请重新打开" } }
                check(generation == sortGeneration && !closed && app.canEdit && app.runtimeActive && !app.bool(StatusBarSettings.SAFE_MODE)) {
                    "排序窗口已失效，请重新打开"
                }
                repository.updateStateCompared(draft.pluginId, draft.versionCode, draft.key, draft.baseValue, value)
                loadPlugins()
                deliver {
                    if (generation == sortGeneration) { sortDraft = null; formSnapshots[draft.pluginId] = snapshot; app.toast("磁贴管理已保存") }
                }
            } finally { deliver { sortSaving = false } }
        }
    }
    private fun sortControl(plugin: CustomPluginRepository.Plugin, key: String, callback: String): JSONObject {
        val controls = plugin.descriptor.optJSONArray("ui") ?: error("排序设置已改变，请重新打开")
        return (0 until controls.length()).mapNotNull { controls.optJSONObject(it) }.firstOrNull {
            it.optString("type") == "sort" && it.optString("key") == key && it.optString("optionsAction") == callback
        } ?: error("排序设置已改变，请重新打开")
    }
    private fun sortValue(plugin: CustomPluginRepository.Plugin, field: JSONObject): String {
        val value = plugin.state.opt(field.getString("key")) ?: CustomPluginSchema.defaultValue(field)
        CustomPluginSort.validateValue(value)
        return value as String
    }
    private fun sortInstance(snapshot: JSONObject, callback: String): String {
        val instance = snapshot.optString("instanceId")
        val actions = snapshot.optJSONArray("actions")
        check(instance.isNotBlank() && actions != null && (0 until actions.length()).any { actions.optString(it) == callback }) {
            "插件操作尚未就绪，请展开控制中心后重试"
        }
        return instance
    }
    private fun sortCatalog(id: String, callback: String, instance: String): JSONObject {
        val result = repository.runAction(id, callback, instance)
        check(result.optBoolean("ok")) { result.optString("error", "读取排序项目失败") }
        return result.optJSONObject("value") ?: error("插件未提供有效的排序列表")
    }
    fun delete(plugin: CustomPluginRepository.Plugin) {
        app.confirmation = UiConfirmation(trf("删除 %s", plugin.name), "删除插件及其独立设置，撤销它的运行修改。", "删除") {
            local { repository.delete(plugin.id); loadPlugins(); deliver { selectedId = null } }
        }
    }
    fun setSortMode(value: String) {
        if (!CustomPluginOrdering.validMode(value)) return
        sortModeValue = value; ui.edit().putString("sort_mode", value).apply()
    }
    fun toggleSortDirection() { sortAscending = !sortAscending; ui.edit().putBoolean("sort_ascending", sortAscending).apply() }
    fun movePlugin(id: String, delta: Int) {
        if (working) return
        val ordered = sortedPlugins.map { it.id }.toMutableList()
        val old = ordered.indexOf(id); val target = old + delta
        if (old < 0 || target !in ordered.indices) return
        ordered.removeAt(old); ordered.add(target, id)
        commitPluginOrder(ordered)
    }
    fun commitPluginOrder(ids: List<String>) {
        if (working || ids.size != plugins.size || ids.toSet().size != ids.size || ids.toSet() != plugins.map { it.id }.toSet()) return
        val oldMode = sortModeValue; val oldAscending = sortAscending; val oldOrder = manualOrder
        manualOrder = ids.toList(); sortModeValue = "custom"; sortAscending = true
        local {
            val saved = try {
                ui.edit().putString("sort_mode", "custom").putBoolean("sort_ascending", true)
                    .putString("manual_order", JSONArray(ids).toString()).commit()
            } catch (_: Exception) { false }
            if (!saved) {
                runCatching {
                    ui.edit().putString("sort_mode", oldMode).putBoolean("sort_ascending", oldAscending)
                        .putString("manual_order", JSONArray(oldOrder).toString()).commit()
                }
                deliver { manualOrder = oldOrder; sortModeValue = oldMode; sortAscending = oldAscending }
                throw IllegalStateException("排序保存失败，请重试")
            }
        }
    }
    fun isDefaultSource(source: CustomPluginCatalog.Source) = source.id == CustomPluginCatalog.defaultSource().id
    fun selectSource(id: String) {
        if (sources.none { it.id == id } || localWorking) return
        cancelNetwork(); selectedSourceId = id; catalog = null
        ui.edit().putString("selected_source", id).apply(); refreshCatalog()
    }
    fun addSource(input: String) = withPolicy {
        if (sources.size >= 8) { error = "最多添加 7 个第三方仓库"; return@withPolicy }
        request(download = false) { generation ->
            val source = CustomPluginCatalog.source(input.trim()); val loaded = CustomPluginCatalog.fetch(source)
            deliverNetwork(generation) {
                val existing = sources.firstOrNull { it.id == source.id || it.indexUrl == source.indexUrl }
                if (existing == null) sources = sources + source
                selectedSourceId = existing?.id ?: source.id; catalog = loaded; repositoryInputOpen = false
                persistSources(); repositoryInput = ""
            }
        }
    }
    fun removeSource(id: String) {
        val source = sources.firstOrNull { it.id == id } ?: return
        if (isDefaultSource(source)) return
        cancelNetwork(); sources = sources.filter { it.id != id }
        if (selectedSourceId == id) { selectedSourceId = sources.first().id; catalog = null }
        persistSources(); if (catalog == null) refreshCatalog()
    }
    fun refreshCatalog() {
        val source = selectedSource
        request(download = false) { generation ->
            val loaded = CustomPluginCatalog.fetch(source)
            deliverNetwork(generation) { if (selectedSourceId == source.id) catalog = loaded }
        }
    }
    fun download(entry: CustomPluginCatalog.Entry) = withPolicy {
        val current = catalog
        if (current == null || current.source.id != selectedSourceId || current.entries.none { it === entry }) {
            error = "仓库目录已更新，请重新选择插件"; return@withPolicy
        }
        val source = current.source
        request(download = true) { generation ->
            val bytes = CustomPluginCatalog.download(entry)
            val prepared = preparePreview(ByteArrayInputStream(bytes), entry.id + ".zip")
            try {
                val descriptor = prepared.descriptor
                require(descriptor.getString("id") == entry.id && descriptor.getString("name") == entry.name
                    && descriptor.getString("version") == entry.version && descriptor.getInt("versionCode") == entry.versionCode
                    && descriptor.getInt("apiVersion") == entry.apiVersion) { "插件包与仓库目录声明不一致，已取消导入" }
                if (entry.author.isNotBlank()) require(descriptor.optString("author") == entry.author) { "插件作者与目录不一致" }
                val actual = descriptor.optJSONArray("capabilities") ?: JSONArray()
                fun values(array: JSONArray) = (0 until array.length()).map { array.getString(it) }.toSet()
                require(values(actual) == values(entry.capabilities)) { "插件能力与目录不一致，已取消导入" }
            } catch (failure: Throwable) { releasePreview(prepared); throw failure }
            deliverPreview(prepared, source.name + " · " + source.homeUrl, generation)
        }
    }
    fun openRepositoryHome() { app.openUrl(selectedSource.homeUrl) }
    fun openRepositoryReadme() { app.openUrl(catalog?.readmeUrl?.takeIf { it.isNotBlank() } ?: selectedSource.homeUrl) }
    fun openRepositoryChanges() { app.openUrl(catalog?.changesUrl?.takeIf { it.isNotBlank() } ?: selectedSource.homeUrl) }
    private fun persistSources() {
        val saved = JSONArray(); sources.filterNot(::isDefaultSource).forEach { saved.put(it.serialize()) }
        ui.edit().putString("sources", saved.toString()).putString("selected_source", selectedSourceId).apply()
    }
    private fun withPolicy(action: () -> Unit) {
        if (closed || localWorking) return
        if (policyAccepted) action() else { pendingAfterPolicy = action; policyOpen = true }
    }
    fun acceptPolicy() = local(after = {
        policyAccepted = true; policyOpen = false
        val pending = pendingAfterPolicy; pendingAfterPolicy = null; pending?.invoke()
    }) { check(ui.edit().putInt("policy_version", CustomPluginPolicy.VERSION).commit()) { "确认记录保存失败，请重试" } }
    fun dismissPolicy() { if (!localWorking) { policyOpen = false; pendingAfterPolicy = null } }
    fun dismissError() { error = ""; catalogError = "" }
    fun back(): Boolean {
        if (sortLoading) { dismissSort(); return true }
        if (localWorking) return true
        if (catalogLoading || downloadLoading) cancelNetwork()
        when {
            sortDraft != null -> dismissSort()
            preview != null -> dismissPreview(); editing != null -> editing = null
            policyOpen -> dismissPolicy(); repositoryInputOpen -> repositoryInputOpen = false
            sortOpen -> sortOpen = false; helpOpen -> helpOpen = false
            settingsPath.isNotEmpty() -> settingsPath = settingsPath.dropLast(1)
            selectedId != null -> selectedId = null; else -> return false
        }
        return true
    }
    fun cancelNetwork() {
        networkGeneration++; networkTask?.cancel(true); networkTask = null; catalogLoading = false; downloadLoading = false
        val discarded = synchronized(previewLock) {
            val values = pendingPreviews.filterValues { it != null }.keys.toList()
            values.forEach { pendingPreviews.remove(it) }; values
        }
        discarded.forEach(::releasePreview)
    }
    fun close() {
        scopeRequestGeneration++; scopeRequest?.close(); scopeRequest = null
        if (closed) return
        closed = true; moreRefreshEpoch++; sortGeneration++; sortDraft = null; sortLoading = false; cancelNetwork(); scopeGeneration++; frameworkSubscription?.close(); frameworkSubscription = null
        val discarded = synchronized(previewLock) { pendingPreviews.keys.toList().also { pendingPreviews.clear() } }
        val shown = preview; preview = null; pendingUri = null
        (discarded + listOfNotNull(shown)).distinct().forEach(::releasePreview)
        if (observed) runCatching { app.contentResolver.unregisterContentObserver(observer) }
        observed = false
        cancelFormStatus(); statusWorker.shutdownNow(); scopeEvidenceTask?.cancel(true); scopeEvidenceWorker.shutdownNow()
        worker.shutdown(); network.shutdownNow()
    }
    private fun preparePreview(input: InputStream, name: String): CustomPluginRepository.Preview {
        var prepared: CustomPluginRepository.Preview? = null
        try {
            input.use { prepared = repository.preview(it, name) }
            return requireNotNull(prepared)
        } catch (failure: Throwable) { releasePreview(prepared); throw failure }
    }
    private fun deliverPreview(prepared: CustomPluginRepository.Preview, origin: String, generation: Int? = null) {
        val queued = synchronized(previewLock) {
            if (closed || generation != null && generation != networkGeneration) false
            else { pendingPreviews[prepared] = generation; true }
        }
        if (!queued) { releasePreview(prepared); return }
        try {
            app.runOnUiThread {
                val owned = synchronized(previewLock) { pendingPreviews.containsKey(prepared).also { if (it) pendingPreviews.remove(prepared) } }
                if (!owned) return@runOnUiThread
                if (closed || generation != null && generation != networkGeneration) { releasePreview(prepared); return@runOnUiThread }
                val old = preview; preview = prepared; previewOrigin = origin
                if (old !== prepared) releasePreview(old)
            }
        } catch (failure: Throwable) {
            val owned = synchronized(previewLock) { pendingPreviews.containsKey(prepared).also { if (it) pendingPreviews.remove(prepared) } }
            if (owned) releasePreview(prepared)
            throw failure
        }
    }
    private fun releasePreview(prepared: CustomPluginRepository.Preview?) {
        if (prepared == null) return
        val cleanup = Runnable { runCatching { prepared.close() } }
        // Queue behind an in-flight install, which may still read the staging files.
        // Late network/worker results must also be releasable after Activity shutdown.
        try { worker.execute(cleanup) }
        catch (_: RejectedExecutionException) { previewCleanup.execute(cleanup) }
    }
    private companion object {
        val previewCleanup = Executors.newSingleThreadExecutor { task ->
            Thread(task, "C17-plugin-preview-cleanup").apply { isDaemon = true }
        }
    }
    private fun loadPlugins() {
        val current = repository.list()
        deliver {
            plugins = current
            editDraft?.let { if (!currentEdit(it)) editing = null }
            val controls = selected?.descriptor?.optJSONArray("ui")
            if (settingsPath.isNotEmpty() && (0 until (controls?.length() ?: 0)).none {
                    CustomPluginSchema.groupPath(controls!!.getJSONObject(it)).take(settingsPath.size) == settingsPath
                }) settingsPath = emptyList()
        }
    }
    private fun deliver(block: () -> Unit) { app.runOnUiThread { if (!closed) block() } }
    private fun deliverNetwork(generation: Int, block: () -> Unit) { deliver { if (generation == networkGeneration) block() } }
    private fun request(download: Boolean, block: (Int) -> Unit) {
        if (closed) return
        cancelNetwork(); val generation = networkGeneration
        catalogError = ""; error = ""; downloadLoading = download; catalogLoading = !download
        networkTask = network.submit {
            try { block(generation) }
            catch (failure: Exception) { deliverNetwork(generation) {
                if (download) error = failure.message?.take(400) ?: "获取插件失败" else catalogError = failure.message?.take(400) ?: "读取仓库失败"
            } }
            finally { deliverNetwork(generation) { catalogLoading = false; downloadLoading = false } }
        }
    }
    private fun local(after: (() -> Unit)? = null, block: () -> Unit) {
        if (closed || localWorking) return
        localWorking = true; error = ""
        worker.execute {
            var success = false
            try { block(); success = true }
            catch (failure: Exception) {
                val current = runCatching { repository.list() }.getOrNull()
                deliver { error = failure.message?.take(400) ?: "插件操作失败"; if (current != null) plugins = current }
            } finally { deliver {
                localWorking = false
                if (success) after?.invoke()
                if (reloadRequested && !localWorking) { reloadRequested = false; refresh() }
            } }
        }
    }
}
