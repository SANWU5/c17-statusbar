// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.puitheme.iosstatusbar.R
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.CardDefaults as CouiCardDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val WelcomeWidth = 560.dp
private val WelcomeGutter = 20.dp
private val WelcomeIconSize = 92.dp
private val WelcomeHeroIconSize = 60.dp
/** Icon tile of a row, and where the text beside it starts: the row's inset, the tile and the gap. */
private val WelcomeTileSize = 36.dp
private val WelcomeRowInset = 16.dp
private val WelcomeTextStart = WelcomeRowInset + WelcomeTileSize + 12.dp
private val WelcomeSectionGap = 24.dp
private const val RevealItemMillis = 460
private const val RevealStaggerMillis = 70
private const val RevealItems = 5
private val RevealEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)

/** First launch and About → 欢迎页与声明 share one overview; only the first time it ends with the acknowledgment. */
@Composable
internal fun WelcomeFlow(app: ModernMainActivity) {
    val language = C17Language.current(app)
    // Created before the language list, so leaving the list without a change keeps the draft and scroll position.
    val draft = rememberWelcomeDraft(language)
    if (app.welcomeLanguageOpen) {
        WelcomeLanguagePage(language, onChoose = app::chooseWelcomeLanguage, onClose = app::closeWelcome, scrollFade = app.ui.edgeFade)
        return
    }
    val iconPx = with(LocalDensity.current) { WelcomeIconSize.roundToPx() }
    val icon = remember(app, iconPx) { welcomeIconBitmap(app.packageManager.getApplicationIcon(app.packageName), iconPx) }
    if (app.welcomeGreeting) {
        WelcomeGreetingPage(icon, language, onChoose = app::chooseWelcomeLanguage, onContinue = app::finishGreeting,
            scrollFade = app.ui.edgeFade, uiStyle = app.ui.uiStyle, onUiStyle = app::updateUiStyle)
        return
    }
    val review = !app.welcomeRequired
    WelcomePage(app.appDisplayName, icon, language, draft, review, needsPhrase = !review && app.freeNoticeRequired,
        onLanguage = { app.openWelcome(languageOnly = true) }, onClose = app::closeWelcome, onComplete = app::completeWelcome,
        onTelegram = { app.openUrl(ProjectContact.TELEGRAM_URL) },
        onCopy = { ProjectContact.copyCryptoAddress(app, it) }, scrollFade = app.ui.edgeFade)
}

/** What the reader has entered. A different translation needs a fresh acknowledgment; rotation and the language list keep it. */
@Stable
internal class WelcomeDraft(
    private val typedState: MutableState<String>,
    private val agreedState: MutableState<Boolean>,
    private val revealedState: MutableState<Boolean>,
    val scroll: ScrollState,
) {
    var typed by typedState
    var agreed by agreedState
    /** The entrance plays once; it does not repeat after rotation or a visit to the language list. */
    var revealed by revealedState
    /** [typed] without the input method's unfinished composition. It only colours the guide, never confirms. */
    var settled by mutableStateOf(typedState.value)
}

@Composable
internal fun rememberWelcomeDraft(language: String): WelcomeDraft {
    val typed = key(language) { rememberSaveable { mutableStateOf("") } }
    val agreed = key(language) { rememberSaveable { mutableStateOf(false) } }
    val scroll = key(language) { rememberScrollState() }
    val revealed = rememberSaveable { mutableStateOf(false) }
    return remember(typed, agreed, revealed, scroll) { WelcomeDraft(typed, agreed, revealed, scroll) }
}

/**
 * The overview itself, independent of the activity. [onComplete] returns null when done, true when the typed phrase
 * does not match and false when the record could not be saved. [animate] false shows the settled page at once;
 * [opened] starts with the rows that open in place already open (previews).
 */
@Composable
internal fun WelcomePage(
    appName: String,
    icon: ImageBitmap,
    language: String,
    draft: WelcomeDraft,
    review: Boolean,
    needsPhrase: Boolean,
    onLanguage: () -> Unit,
    onClose: () -> Unit,
    onComplete: (String?, String) -> Boolean?,
    onTelegram: () -> Unit,
    onCopy: (String) -> Boolean,
    animate: Boolean = true,
    scrollFade: Boolean = true,
    opened: Boolean = false,
) {
    val phrase = stringResource(R.string.welcome_free_phrase)
    val failed = stringResource(R.string.welcome_save_failed)
    val mismatch = stringResource(R.string.welcome_confirm_mismatch)
    val required = stringResource(R.string.welcome_confirm_required)
    var error by remember(language) { mutableStateOf<String?>(null) }
    val ready = if (needsPhrase) FreeNotice.matches(draft.typed, phrase) else draft.agreed
    val confirmArea = remember { BringIntoViewRequester() }
    val editor = remember { WelcomeEditor() }
    val scope = rememberCoroutineScope()
    val reveal = rememberWelcomeReveal(draft, animate)
    val accents = rememberWelcomeAccents()

    // Once the keyboard has finished opening over the typed phrase, scroll the acknowledgment above it.
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(editor) {
        snapshotFlow { if (editor.focused) ime.getBottom(density) else -1 }.distinctUntilChanged()
            .collectLatest { bottom -> if (bottom > 0) { delay(160); confirmArea.bringIntoView() } }
    }

    fun proceed() {
        when {
            review -> onClose()
            // Not ready yet: lead to what is missing instead of a silent disabled button.
            !ready -> {
                error = if (needsPhrase && draft.typed.isNotEmpty()) mismatch else required
                scope.launch { confirmArea.bringIntoView() }
                if (needsPhrase) editor.focus()
            }
            else -> error = onComplete(if (needsPhrase) draft.typed else null, phrase)?.let { if (it) mismatch else failed }
        }
    }

    C17Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Content that scrolls up under the status bar fades out like every other page ("滚动渐隐").
                Column(Modifier.fillMaxSize().edgeFade(draft.scroll, scrollFade).verticalScroll(draft.scroll),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = WelcomeWidth).fillMaxWidth().padding(horizontal = WelcomeGutter)
                        .padding(top = 6.dp, bottom = 24.dp)) {
                        // App287: one pass from top to bottom. The features are told once (the hero no longer repeats them
                        // as cards), licence, disclaimer and the donation addresses open in place, rows are compact.
                        WelcomeHero(appName, icon, accents, language, onLanguage, Modifier.welcomeReveal(reveal, 0))
                        WelcomeIntro(accents, Modifier.padding(top = 22.dp).welcomeReveal(reveal, 1))
                        WelcomeStatements(accents, opened, Modifier.padding(top = WelcomeSectionGap).welcomeReveal(reveal, 2))
                        // App285: the Telegram group and the crypto donation, right after "free forever, donations optional".
                        WelcomeSupport(accents, onTelegram, onCopy, opened, Modifier.padding(top = WelcomeSectionGap).welcomeReveal(reveal, 3))
                        if (review) WelcomeReviewNote(accents, Modifier.padding(top = 22.dp).welcomeReveal(reveal, 4))
                        else WelcomeConfirm(draft, phrase, needsPhrase, error, editor, onEdited = { error = null },
                            modifier = Modifier.padding(top = WelcomeSectionGap).bringIntoViewRequester(confirmArea).welcomeReveal(reveal, 4))
                    }
                }
                WelcomeScrollEdge(draft.scroll, Modifier.align(Alignment.BottomCenter))
            }
            WelcomeFooter(stringResource(if (review) R.string.welcome_close else R.string.welcome_start), onClick = ::proceed)
        }
    }
}

/** Staggered rise-in of the four blocks, driven by one clock; layers only, nothing is measured again. */
@Composable
private fun rememberWelcomeReveal(draft: WelcomeDraft, animate: Boolean): State<Float> {
    val progress = remember { Animatable(if (draft.revealed || !animate) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(RevealItemMillis + RevealStaggerMillis * (RevealItems - 1), easing = LinearEasing))
        }
        draft.revealed = true
    }
    return progress.asState()
}

private fun Modifier.welcomeReveal(progress: State<Float>, index: Int): Modifier = graphicsLayer {
    val total = (RevealItemMillis + RevealStaggerMillis * (RevealItems - 1)).toFloat()
    val local = ((progress.value * total - index * RevealStaggerMillis) / RevealItemMillis).coerceIn(0f, 1f)
    val eased = RevealEasing.transform(local)
    alpha = eased
    translationY = (1f - eased) * 20.dp.toPx()
}

/** Theme-derived accents: the primary and two companions at the same perceived brightness, plus caution and neutral. */
@Immutable
private class WelcomeAccents(val primary: Color, val second: Color, val third: Color, val caution: Color, val neutral: Color)

@Composable
private fun rememberWelcomeAccents(): WelcomeAccents {
    val colors = MiuixTheme.colorScheme
    val dark = LocalC17Dark.current
    val primary = colors.primary
    val neutral = colors.onSurfaceVariantSummary
    return remember(primary, neutral, dark) {
        WelcomeAccents(primary, primary.welcomeHue(52f), primary.welcomeHue(-52f),
            if (dark) Color(0xFFFFB74D) else Color(0xFFB45F06), neutral)
    }
}

/** Another hue at the same saturation and relative luminance, so a companion accent keeps the primary's contrast. */
internal fun Color.welcomeHue(degrees: Float): Color {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val chroma = max - min
    if (chroma < .02f) return this
    val lightness = (max + min) / 2f
    val saturation = (chroma / (1f - kotlin.math.abs(2f * lightness - 1f))).coerceIn(0f, 1f)
    val hue = when (max) {
        red -> 60f * (((green - blue) / chroma) % 6f)
        green -> 60f * ((blue - red) / chroma + 2f)
        else -> 60f * ((red - green) / chroma + 4f)
    }
    val turned = ((hue + degrees) % 360f + 360f) % 360f
    val target = luminance()
    var low = 0f
    var high = 1f
    repeat(20) {
        val middle = (low + high) / 2f
        if (Color.hsl(turned, saturation, middle).luminance() < target) low = middle else high = middle
    }
    return Color.hsl(turned, saturation, (low + high) / 2f, alpha)
}

/**
 * The icon's own layers without the launcher's mask, so the page always gives it the same rounded shape. Adaptive
 * layers are 1.5 times the visible icon (108 dp art, 72 dp viewport); other drawables are drawn as they are.
 */
internal fun welcomeIconBitmap(drawable: Drawable, size: Int): ImageBitmap {
    if (drawable !is AdaptiveIconDrawable) return drawableBitmap(drawable, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val inset = size / 4
    for (layer in listOfNotNull(drawable.background, drawable.foreground)) {
        layer.setBounds(-inset, -inset, size + inset, size + inset)
        layer.draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/** Continuous corners: a superellipse, the app-icon outline without the visible kink of circular corners. */
private object WelcomeSquircle : Shape {
    private const val Exponent = 5.0
    private const val Points = 96

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2f
        val b = size.height / 2f
        val path = Path()
        for (index in 0..Points) {
            val angle = 2.0 * Math.PI * index / Points
            val cos = kotlin.math.cos(angle)
            val sin = kotlin.math.sin(angle)
            val x = a + a * (Math.signum(cos) * Math.pow(kotlin.math.abs(cos), 2.0 / Exponent)).toFloat()
            val y = b + b * (Math.signum(sin) * Math.pow(kotlin.math.abs(sin), 2.0 / Exponent)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Centred text wraps into even lines instead of leaving a lone character below (mostly CJK). */
private val welcomeBalanced: TextStyle
    @Composable get() = MiuixTheme.textStyles.main.copy(
        lineBreak = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Normal, LineBreak.WordBreak.Default))

/** The card corner of the selected component family, for the tinted blocks that are not cards themselves. */
private val welcomeCorner: Dp
    @Composable @ReadOnlyComposable get() = when (c17Style) {
        C17UiStyle.MIUIX -> CardDefaults.CornerRadius
        C17UiStyle.COUI -> CouiCardDefaults.CornerRadius
        C17UiStyle.MATERIAL -> C17MaterialCardRadius
    }

@Composable
private fun WelcomeLanguagePill(language: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val name = welcomeLanguageName(language)
    val label = stringResource(R.string.about_language)
    Row(modifier.heightIn(min = 40.dp).clip(CircleShape).background(colors.surfaceContainer)
        .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .clearAndSetSemantics { contentDescription = "$label: $name" }
        .padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Language, null, Modifier.size(18.dp), tint = colors.onSurfaceVariantSummary)
        Spacer(Modifier.width(6.dp))
        Text(name, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, color = colors.onSurface)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = colors.onSurfaceVariantSummary)
    }
}

@Composable
private fun WelcomeHero(
    appName: String, icon: ImageBitmap, accents: WelcomeAccents, language: String, onLanguage: () -> Unit, modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        WelcomeLanguagePill(language, onLanguage, Modifier.align(Alignment.End))
        Spacer(Modifier.height(6.dp))
        // App280: the icon alone. The glow and coloured shadow that sat under it drew differently across ROMs.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(icon, contentDescription = null, modifier = Modifier.size(WelcomeHeroIconSize).clip(WelcomeSquircle))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.welcome_hello), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    color = accents.primary)
                Text(appName, Modifier.semantics { heading() }, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                    color = colors.onSurface, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.welcome_tagline), fontSize = 15.sp, lineHeight = 21.sp, color = colors.onSurfaceVariantSummary)
    }
}

/** Who wrote it, as the first row of 支持与交流: the developer's own avatar, the name and where to reach them. */
@Composable
private fun WelcomeDeveloper(accents: WelcomeAccents) {
    val colors = MiuixTheme.colorScheme
    val label = stringResource(R.string.welcome_developer_label)
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = WelcomeRowInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Ringed in the theme colours so the avatar sits on light and dark surfaces alike.
        Image(painterResource(R.drawable.welcome_developer_avatar), contentDescription = null,
            modifier = Modifier.size(WelcomeTileSize)
                .border(1.5.dp, Brush.linearGradient(listOf(accents.primary, accents.second)), CircleShape)
                .padding(2.5.dp).clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WELCOME_DEVELOPER, fontSize = 15.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text(" · $label", fontSize = 14.sp, lineHeight = 21.sp, color = colors.onSurfaceVariantSummary)
            }
            Text(stringResource(R.string.welcome_developer_note), fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun WelcomeIntro(accents: WelcomeAccents, modifier: Modifier = Modifier) {
    Column(modifier) {
        WelcomeSectionTitle(stringResource(R.string.welcome_intro_title))
        UiGroupCard {
            Column(Modifier.padding(horizontal = WelcomeRowInset, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WelcomeEntry(Icons.Outlined.SignalCellularAlt, accents.primary,
                    stringResource(R.string.welcome_feature_statusbar_title), stringResource(R.string.welcome_feature_statusbar_body))
                WelcomeEntry(Icons.Outlined.GridView, accents.second,
                    stringResource(R.string.welcome_feature_panel_title), stringResource(R.string.welcome_feature_panel_body))
                WelcomeEntry(Icons.Outlined.Extension, accents.third,
                    stringResource(R.string.welcome_feature_plugin_title), stringResource(R.string.welcome_feature_plugin_body),
                    badge = stringResource(R.string.welcome_plugin_paid_badge), badgeColor = accents.caution)
                WelcomeEntry(Icons.Outlined.Celebration, accents.primary,
                    stringResource(R.string.welcome_easter_egg_title), stringResource(R.string.welcome_easter_egg_body))
            }
        }
        Spacer(Modifier.height(10.dp))
        WelcomeNotice(accents.caution)
    }
}

/** What to have and what to do if something goes wrong, in one quiet caution block below the features. */
@Composable
private fun WelcomeNotice(caution: Color) {
    val dark = LocalC17Dark.current
    val surface = MiuixTheme.colorScheme.surfaceContainer
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(welcomeCorner)).background(lerp(surface, caution, if (dark) .16f else .1f))
        .padding(horizontal = WelcomeRowInset, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WelcomeNoticeRow(Icons.Outlined.Key, caution, stringResource(R.string.welcome_requirement))
        WelcomeNoticeRow(Icons.Outlined.HealthAndSafety, caution, stringResource(R.string.welcome_intro_safety))
    }
}

@Composable
private fun WelcomeNoticeRow(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(18.dp), tint = tint)
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), fontSize = 13.5.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
private fun WelcomeStatements(accents: WelcomeAccents, opened: Boolean, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val dark = LocalC17Dark.current
    Column(modifier) {
        WelcomeSectionTitle(stringResource(R.string.welcome_statement_title))
        // Paid copies are scams, so "free forever" stands alone on a card tinted with the theme colour.
        C17Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(WelcomeRowInset),
            colors = CardDefaults.defaultColors(color = lerp(colors.surfaceContainer, accents.primary, if (dark) .18f else .09f),
                contentColor = colors.onSurface)) {
            WelcomeEntry(Icons.Outlined.VolunteerActivism, accents.primary, stringResource(R.string.welcome_free_title),
                stringResource(R.string.welcome_free_body), solid = true, reading = true)
        }
        Spacer(Modifier.height(10.dp))
        // Plugins are separate products and may cost money, the developer's own included: said right below "free forever"
        // so the two are never read as a contradiction.
        C17Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(WelcomeRowInset),
            colors = CardDefaults.defaultColors(color = lerp(colors.surfaceContainer, accents.caution, if (dark) .16f else .1f),
                contentColor = colors.onSurface)) {
            WelcomeEntry(Icons.Outlined.Sell, accents.caution, stringResource(R.string.welcome_plugin_paid_title),
                stringResource(R.string.welcome_plugin_paid_body), reading = true)
        }
        Spacer(Modifier.height(10.dp))
        // The two long texts open in place: their titles say what they are, and the page stays one or two screens.
        UiGroupCard {
            WelcomeFold(Icons.Outlined.Code, accents.neutral, stringResource(R.string.welcome_respect_title), opened) {
                WelcomeReading(stringResource(R.string.welcome_respect_body))
                Text(stringResource(R.string.welcome_translation_note), Modifier.padding(top = 6.dp), fontSize = 12.5.sp,
                    lineHeight = 18.sp, color = colors.onSurfaceVariantSummary)
            }
            WelcomeDivider(Modifier.padding(start = WelcomeTextStart))
            WelcomeFold(Icons.Outlined.PrivacyTip, accents.neutral, stringResource(R.string.welcome_disclaimer_title), opened) {
                WelcomeReading(stringResource(R.string.welcome_disclaimer_body))
            }
        }
    }
}

/** Statement text, read in full: the body colour, not the quiet summary colour. */
@Composable
private fun WelcomeReading(text: String) {
    Text(text, fontSize = 14.sp, lineHeight = 21.sp, color = MiuixTheme.colorScheme.onSurface)
}

/**
 * A row that opens its text in place. Closed, it is the icon, the title and a chevron; the state is told to
 * accessibility services through the expand and collapse actions, which they announce in the reader's language.
 */
@Composable
private fun WelcomeFold(icon: ImageVector, accent: Color, title: String, opened: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(opened) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(C17Motion.ExpandMillis), label = "welcomeFold")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }
            .semantics { if (open) collapse { open = false; true } else expand { open = true; true } }
            .padding(horizontal = WelcomeRowInset, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            WelcomeTile(icon, accent)
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 15.5.sp, lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
                tint = colors.onSurfaceVariantSummary)
        }
        AnimatedVisibility(open, enter = C17Motion.expandEnter, exit = C17Motion.expandExit) {
            Column(Modifier.fillMaxWidth().padding(start = WelcomeTextStart, end = WelcomeRowInset, bottom = 14.dp), content = content)
        }
    }
}

/** An icon tile beside a title and its text. [reading] marks statements, which are read in full and use the body colour. */
@Composable
private fun WelcomeEntry(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    solid: Boolean = false,
    reading: Boolean = false,
    badge: String? = null,
    badgeColor: Color = accent,
) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        WelcomeTile(icon, accent, solid)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false).semantics { heading() }, fontSize = 15.5.sp, lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                badge?.let { Spacer(Modifier.width(8.dp)); WelcomeBadge(it, badgeColor) }
            }
            if (reading) WelcomeReading(body)
            else Text(body, fontSize = 13.5.sp, lineHeight = 19.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}

/** A short tag beside a title, such as "may be paid" on the plugin feature. */
@Composable
private fun WelcomeBadge(text: String, color: Color) {
    val dark = LocalC17Dark.current
    Text(text, Modifier.clip(CircleShape).background(color.copy(alpha = if (dark) .2f else .13f)).padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
}

@Composable
private fun WelcomeTile(icon: ImageVector, accent: Color, solid: Boolean = false, size: Dp = WelcomeTileSize) {
    val dark = LocalC17Dark.current
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .3f))
        .background(if (solid) accent else accent.copy(alpha = if (dark) .2f else .13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * .54f), tint = if (solid) MiuixTheme.colorScheme.onPrimary else accent)
    }
}

/**
 * The developer, the Telegram group and the crypto donation (App285). Since App292 two networks with different
 * addresses, Ethereum (ERC20) and Tron (TRC20). They open in place (App287); 关于 → 联系与捐赠 shows them as well.
 */
@Composable
private fun WelcomeSupport(
    accents: WelcomeAccents, onTelegram: () -> Unit, onCopy: (String) -> Boolean, opened: Boolean, modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier) {
        WelcomeSectionTitle(stringResource(R.string.welcome_support_title))
        UiGroupCard {
            WelcomeDeveloper(accents)
            WelcomeDivider(Modifier.padding(start = WelcomeTextStart))
            Row(Modifier.fillMaxWidth().padding(horizontal = WelcomeRowInset, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                WelcomeTile(Icons.Outlined.Forum, accents.second)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.welcome_telegram_title), Modifier.semantics { heading() }, fontSize = 15.5.sp,
                        lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Text(stringResource(R.string.welcome_telegram_body), fontSize = 13.sp, lineHeight = 18.sp,
                        color = colors.onSurfaceVariantSummary)
                }
                Spacer(Modifier.width(10.dp))
                WelcomePillButton(stringResource(R.string.welcome_telegram_join), colors.primary, onTelegram)
            }
            WelcomeDivider(Modifier.padding(start = WelcomeTextStart))
            WelcomeFold(Icons.Outlined.AccountBalanceWallet, accents.primary, stringResource(R.string.welcome_donate_title), opened) {
                Text(stringResource(R.string.welcome_donate_body), fontSize = 13.5.sp, lineHeight = 19.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(12.dp))
                WelcomeAddress(ProjectContact.CRYPTO_ETH_NETWORK, ProjectContact.CRYPTO_ETH_ADDRESS, onCopy)
                Spacer(Modifier.height(8.dp))
                WelcomeAddress(ProjectContact.CRYPTO_TRON_NETWORK, ProjectContact.CRYPTO_TRON_ADDRESS, onCopy)
                Spacer(Modifier.height(10.dp))
                WelcomeNoticeRow(Icons.Outlined.ErrorOutline, accents.caution, stringResource(R.string.welcome_donate_warning))
            }
            WelcomeDivider(Modifier.padding(start = WelcomeTextStart))
            WelcomeFold(Icons.Outlined.QrCode2, accents.second, stringResource(R.string.welcome_wechat_title), opened) {
                WelcomeQrCode("donation/wechat.png", 1490f / 2030f, stringResource(R.string.welcome_wechat_title), accents.primary)
            }
            WelcomeDivider(Modifier.padding(start = WelcomeTextStart))
            WelcomeFold(Icons.Outlined.QrCode2, accents.primary, stringResource(R.string.welcome_alipay_title), opened) {
                WelcomeQrCode("donation/alipay.jpg", 1440f / 2160f, stringResource(R.string.welcome_alipay_title), accents.primary)
            }
        }
    }
}

/** The same donation codes as 关于 → 联系与捐赠, decoded only while the row is open. */
@Composable
private fun WelcomeQrCode(asset: String, ratio: Float, description: String, accent: Color) {
    val context = LocalContext.current
    val image = remember(asset) {
        runCatching { context.assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    if (image != null) {
        Image(image, description, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Fit)
        Spacer(Modifier.height(10.dp))
    }
    WelcomeNoticeRow(Icons.Outlined.Info, accent, stringResource(R.string.welcome_donate_about_hint))
}

/** One address in full, in a fixed-width face so it can be compared character by character, with its own copy button. */
@Composable
private fun WelcomeAddress(label: String, address: String, onCopy: (String) -> Boolean) {
    val colors = MiuixTheme.colorScheme
    var copied by remember(address) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(copied) { if (copied != null) { delay(2000); copied = null } }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.onSurface.copy(alpha = .05f))
        .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariantSummary)
            Spacer(Modifier.height(2.dp))
            Text(address, fontSize = 13.5.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace, color = colors.onSurface)
        }
        Spacer(Modifier.width(10.dp))
        val text = stringResource(when (copied) { true -> R.string.welcome_copied; false -> R.string.welcome_copy_failed; null -> R.string.welcome_copy })
        WelcomePillButton(text, if (copied == false) colors.error else colors.primary) { copied = onCopy(address) }
    }
}

/** A small filled pill for actions inside a card (join, copy). */
@Composable
private fun WelcomePillButton(text: String, color: Color, onClick: () -> Unit) {
    val dark = LocalC17Dark.current
    Box(Modifier.heightIn(min = 36.dp).clip(CircleShape).background(color.copy(alpha = if (dark) .22f else .14f))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun WelcomeConfirm(
    draft: WelcomeDraft,
    phrase: String,
    needsPhrase: Boolean,
    error: String?,
    editor: WelcomeEditor,
    onEdited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        WelcomeSectionTitle(stringResource(R.string.welcome_confirm_title))
        UiGroupCard {
            if (needsPhrase) WelcomePhrase(draft, phrase, editor, onEdited)
            else UiSwitchRow(stringResource(R.string.welcome_agree), checked = draft.agreed,
                onChecked = { draft.agreed = it; onEdited() })
        }
        error?.let { WelcomeError(it, Modifier.padding(top = 12.dp)) }
    }
}

/** How much of the phrase is already typed correctly, and whether the next typed character is wrong. */
@Immutable
internal class WelcomeProgress(val correct: Int, val wrong: Boolean)

internal fun welcomeProgress(typed: String, phrase: String): WelcomeProgress {
    val correct = FreeNotice.correctPrefix(typed, phrase)
    return WelcomeProgress(correct, typed.length > correct)
}

@Composable
private fun WelcomePhrase(draft: WelcomeDraft, phrase: String, editor: WelcomeEditor, onEdited: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val matched = FreeNotice.matches(draft.typed, phrase)
    val progress = welcomeProgress(draft.settled, phrase)
    Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Text(stringResource(R.string.welcome_confirm_type), fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariantSummary)
        Spacer(Modifier.height(12.dp))
        WelcomePhraseGuide(phrase, if (matched) WelcomeProgress(phrase.length, false) else progress)
        Spacer(Modifier.height(14.dp))
        WelcomePhraseField(draft, editor, matched, onEdited)
        Spacer(Modifier.height(12.dp))
        WelcomePhraseStatus(progress, phrase.length, matched)
    }
}

/** The sentence to type. Typed characters turn the theme colour, the next one is underlined, a wrong one turns red. */
@Composable
private fun WelcomePhraseGuide(phrase: String, progress: WelcomeProgress) {
    val colors = MiuixTheme.colorScheme
    val dark = LocalC17Dark.current
    val done = progress.correct.coerceIn(0, phrase.length)
    val text = remember(phrase, done, progress.wrong, colors.primary, colors.error, colors.onSurface) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.primary)) { append(phrase, 0, done) }
            if (done < phrase.length) {
                val next = if (progress.wrong) SpanStyle(color = colors.error, background = colors.error.copy(alpha = .16f))
                else SpanStyle(color = colors.onSurface, textDecoration = TextDecoration.Underline)
                withStyle(next) { append(phrase, done, done + 1) }
                withStyle(SpanStyle(color = colors.onSurface)) { append(phrase, done + 1, phrase.length) }
            }
        }
    }
    val bar = colors.primary
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(lerp(colors.surfaceContainer, colors.primary, if (dark) .12f else .06f))
        .drawBehind {
            val width = 3.dp.toPx()
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
            drawRect(bar, Offset(x, 0f), Size(width, size.height))
        }
        .clearAndSetSemantics { contentDescription = phrase }
        .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
        Text(text, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
    }
}

/** Holds the native editor so the start button can focus it; the view itself never leaves the page. */
@Stable
private class WelcomeEditor {
    var view: ManualConfirmationEditText? = null
    var focused by mutableStateOf(false)

    fun focus() {
        val target = view ?: return
        if (target.requestFocus()) target.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(target, 0)
    }
}

@Composable
private fun WelcomePhraseField(draft: WelcomeDraft, editor: WelcomeEditor, matched: Boolean, onEdited: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val textColor = colors.onSurface.toArgb()
    val hintColor = colors.onSurfaceVariantSummary.toArgb()
    val hint = stringResource(R.string.welcome_confirm_hint)
    val currentEdited by rememberUpdatedState(onEdited)
    val active = matched || editor.focused
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(colors.surface)
        .border(if (active) 1.5.dp else 1.dp, if (active) colors.primary else colors.outline, shape)
        .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        key(draft) {
            AndroidView(factory = { context ->
                ManualConfirmationEditText(context).apply {
                    textSize = 16f
                    background = null
                    setPadding(0, 0, 0, 0)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            val text = s?.toString() ?: ""
                            val from = if (s == null) -1 else BaseInputConnection.getComposingSpanStart(s)
                            val to = if (s == null) -1 else BaseInputConnection.getComposingSpanEnd(s)
                            draft.typed = text
                            draft.settled = if (from in 0 until to && to <= text.length) text.removeRange(from, to) else text
                            currentEdited()
                        }
                    })
                    setOnFocusChangeListener { _, focused -> editor.focused = focused }
                    editor.view = this
                }
            }, update = { view ->
                view.hint = hint
                view.setTextColor(textColor)
                view.setHintTextColor(hintColor)
                if (view.text.toString() != draft.typed) { view.setText(draft.typed); view.setSelection(view.text.length) }
            }, onRelease = { view ->
                if (editor.view === view) { editor.view = null; editor.focused = false }
            }, modifier = Modifier.weight(1f).heightIn(min = 56.dp))
        }
        if (matched) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(22.dp), tint = colors.primary)
        }
    }
}

@Composable
private fun WelcomePhraseStatus(progress: WelcomeProgress, total: Int, matched: Boolean) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (matched) {
            Icon(Icons.Outlined.TaskAlt, null, Modifier.size(16.dp), tint = colors.primary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.welcome_confirm_matched), Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = colors.primary)
        } else {
            Icon(Icons.Outlined.Lock, null, Modifier.size(15.dp), tint = colors.onSurfaceVariantSummary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.welcome_confirm_no_paste), Modifier.weight(1f), fontSize = 12.5.sp, lineHeight = 18.sp,
                color = colors.onSurfaceVariantSummary)
            Spacer(Modifier.width(10.dp))
            Text("${progress.correct}/$total", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                color = if (progress.wrong) colors.error else colors.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun WelcomeReviewNote(accents: WelcomeAccents, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.TaskAlt, null, Modifier.size(18.dp), tint = accents.primary)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.welcome_review_note), fontSize = 13.5.sp, lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun WelcomeSectionTitle(text: String) {
    Text(text, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp).semantics { heading() }, fontSize = 17.sp,
        lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
}

@Composable
private fun WelcomeDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.dividerLine))
}

@Composable
private fun WelcomeError(text: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.padding(top = 1.dp).size(17.dp), tint = colors.error)
        Spacer(Modifier.width(6.dp))
        Text(text, Modifier.weight(1f), fontSize = 13.5.sp, lineHeight = 19.sp, color = colors.error)
    }
}

/** A short fade above the footer while more of the page lies below. */
@Composable
private fun WelcomeScrollEdge(scroll: ScrollState, modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.surface
    val more by remember(scroll) { derivedStateOf { scroll.canScrollForward } }
    val alpha by animateFloatAsState(if (more) 1f else 0f, tween(180), label = "welcomeEdge")
    Box(modifier.fillMaxWidth().height(28.dp).graphicsLayer { this.alpha = alpha }
        .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))))
}

@Composable
private fun WelcomeFooter(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        C17TextButton(text = text, onClick = onClick, primary = true,
            modifier = Modifier.widthIn(max = WelcomeWidth).fillMaxWidth().padding(horizontal = WelcomeGutter)
                .padding(top = 6.dp, bottom = 14.dp).heightIn(min = 52.dp))
    }
}

@Composable
internal fun WelcomeLanguagePage(current: String, onChoose: (String) -> Boolean, onClose: () -> Unit, scrollFade: Boolean = true) {
    val colors = MiuixTheme.colorScheme
    val scroll = rememberScrollState()
    var saveFailed by remember { mutableStateOf(false) }
    C17Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Column(Modifier.weight(1f).fillMaxWidth().edgeFade(scroll, scrollFade).verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = WelcomeWidth).fillMaxWidth().padding(horizontal = WelcomeGutter)
                    .padding(top = 28.dp, bottom = 28.dp)) {
                    WelcomeTile(Icons.Outlined.Translate, colors.primary, size = 52.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.welcome_language_title), Modifier.semantics { heading() }, fontSize = 28.sp,
                        lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.welcome_language_summary), fontSize = 14.sp, lineHeight = 21.sp,
                        color = colors.onSurfaceVariantSummary)
                    if (saveFailed) WelcomeError(stringResource(R.string.welcome_save_failed), Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(20.dp))
                    WelcomeLanguageList(current) { saveFailed = !onChoose(it) }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.welcome_language_missing), Modifier.padding(horizontal = 4.dp), fontSize = 13.sp,
                        lineHeight = 19.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            WelcomeFooter(stringResource(R.string.welcome_close), onClick = onClose)
        }
    }
}

/** Every offered language under its own name; the line below says it in the language shown now. */
@Composable
private fun WelcomeLanguageList(current: String, onChoose: (String) -> Unit) {
    val shown = LocalConfiguration.current.locales[0]
    val system = remember { Resources.getSystem().configuration.locales[0] }
    val follow = stringResource(R.string.welcome_language_follow_system)
    UiGroupCard {
        for (option in C17Language.OPTIONS) {
            val native = option.nativeName
            val summary = if (native == null) welcomeDisplayName(system, system)
            else welcomeDisplayName(Locale.forLanguageTag(option.tag), shown).takeIf { it != native }
            C17RadioButtonPreference(title = native ?: follow, summary = summary, selected = option.tag == current,
                onClick = { onChoose(option.tag) })
        }
    }
}

private const val GreetingHoldMillis = 1800L

/**
 * App285 first page: "welcome" in each offered language in turn, then the language list. It starts in the phone's own
 * language when offered, so the title and button are readable; choosing a row applies it at once and stays here.
 */
@Composable
internal fun WelcomeGreetingPage(
    icon: ImageBitmap, current: String, onChoose: (String) -> Boolean, onContinue: () -> Unit, scrollFade: Boolean = true,
    uiStyle: String = C17UiStyle.MIUIX.id, onUiStyle: (String) -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    val scroll = rememberScrollState()
    var saveFailed by remember { mutableStateOf(false) }
    var index by rememberSaveable { mutableStateOf(WelcomeGreetings.start(current)) }
    LaunchedEffect(Unit) {
        while (true) { delay(GreetingHoldMillis); index = (index + 1) % WelcomeGreetings.ORDER.size }
    }
    C17Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Column(Modifier.weight(1f).fillMaxWidth().edgeFade(scroll, scrollFade).verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = WelcomeWidth).fillMaxWidth().padding(horizontal = WelcomeGutter)
                    .padding(top = 36.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(WelcomeIconSize).clip(WelcomeSquircle))
                    Spacer(Modifier.height(20.dp))
                    // Fixed height: two lines fit the longest word, so the list below never moves while the words change.
                    Crossfade(index, Modifier.fillMaxWidth().height(92.dp), animationSpec = tween(500), label = "welcomeGreeting") { shown ->
                        val tag = WelcomeGreetings.ORDER[shown]
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(WelcomeGreetings.WORDS.getValue(tag), fontSize = 36.sp, lineHeight = 42.sp,
                                fontWeight = FontWeight.Bold, color = colors.primary, textAlign = TextAlign.Center, maxLines = 2)
                            Text(C17Language.OPTIONS.first { it.tag == tag }.nativeName ?: "", fontSize = 13.sp, lineHeight = 18.sp,
                                color = colors.onSurfaceVariantSummary, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.welcome_language_title), Modifier.fillMaxWidth().semantics { heading() },
                        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.welcome_language_summary), Modifier.fillMaxWidth(), fontSize = 14.sp,
                        lineHeight = 21.sp, color = colors.onSurfaceVariantSummary)
                    if (saveFailed) WelcomeError(stringResource(R.string.welcome_save_failed), Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(16.dp))
                    WelcomeLanguageList(current) { saveFailed = !onChoose(it) }
                    Spacer(Modifier.height(28.dp))
                    WelcomeUiStyle(uiStyle, onUiStyle)
                }
            }
            WelcomeFooter(stringResource(R.string.welcome_greeting_continue), onClick = onContinue)
        }
    }
}

/** The app's look, chosen beside the language and applied at once. COUI is offered but marked unfinished. */
@Composable
private fun WelcomeUiStyle(current: String, onChoose: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Text(stringResource(R.string.welcome_ui_title), Modifier.fillMaxWidth().semantics { heading() },
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.welcome_ui_summary), Modifier.fillMaxWidth(), fontSize = 14.sp, lineHeight = 21.sp,
        color = colors.onSurfaceVariantSummary)
    Spacer(Modifier.height(16.dp))
    UiGroupCard {
        for (style in C17UiStyle.entries) {
            val summary = stringResource(when (style) {
                C17UiStyle.MIUIX -> R.string.welcome_ui_miuix
                C17UiStyle.COUI -> R.string.welcome_ui_coui
                C17UiStyle.MATERIAL -> R.string.welcome_ui_material
            })
            C17RadioButtonPreference(title = style.title, summary = summary, selected = style.id == current,
                onClick = { onChoose(style.id) })
        }
    }
}

private fun welcomeDisplayName(locale: Locale, shownIn: Locale): String =
    locale.getDisplayName(shownIn).replaceFirstChar { if (it.isLowerCase()) it.titlecase(shownIn) else it.toString() }

@Composable
private fun welcomeLanguageName(tag: String): String = C17Language.OPTIONS.firstOrNull { it.tag == tag }?.nativeName
    ?: stringResource(R.string.welcome_language_follow_system)

/** Coolapk uses "konwo"; the overview explains the attribution beside this name. */
internal const val WELCOME_DEVELOPER = "AiingJie"
