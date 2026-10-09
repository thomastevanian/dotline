package com.dotline.launcher.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.core.DefaultHome
import com.dotline.launcher.ui.components.DotClock
import com.dotline.launcher.ui.components.DotPageIndicator
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.components.PillButton
import com.dotline.launcher.ui.components.rememberNow
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.LocalReduceMotion
import com.dotline.launcher.ui.theme.flatClickable
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/** DOTLINE at dot 5dp / gap 2dp is 273dp wide; below this width the wordmark wraps onto two lines. */
private val WordmarkOneLineWidth: Dp = 276.dp

private val GridPitch: Dp = 14.dp
private const val GRID_ROWS = 5

/**
 * First-run flow: three full-screen pages (welcome, design, set as default home).
 * [onFinished] is called when the user skips, or after the home-role request returns.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    // One-shot, under 200 ms, ease-out only. snap() when animations are removed system-wide.
    val scrollSpec = remember<AnimationSpec<Float>>(reduceMotion) {
        if (reduceMotion) snap<Float>() else tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        onFinished()
    }

    fun goToPage(target: Int) {
        scope.launch {
            pagerState.animateScrollToPage(page = target, animationSpec = scrollSpec)
        }
    }

    fun launchIntent(intent: Intent): Boolean {
        return try {
            roleLauncher.launch(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            CrashLog.record("Onboarding: launch ${intent.action}", e)
            false
        }
    }

    fun requestDefaultHome() {
        if (DefaultHome.isDefault(context)) {
            onFinished()
            return
        }
        val roleIntent = DefaultHome.roleRequestIntent(context)
        val launched = roleIntent != null && launchIntent(roleIntent)
        if (!launched) {
            launchIntent(DefaultHome.homeSettingsIntent())
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        goToPage(pagerState.currentPage - 1)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(DotlineTheme.colors.background)
            .systemBarsPadding(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> DesignPage()
                else -> HomePage(onSetDefault = { requestDefaultHome() })
            }
        }
        BottomBar(
            currentPage = pagerState.currentPage,
            onSkip = onFinished,
            onNext = { goToPage(pagerState.currentPage + 1) },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

@Composable
private fun WelcomePage() {
    PageFrame(eyebrow = "WELCOME") {
        DotGrid(Modifier.fillMaxWidth())
        Spacer(Modifier.height(40.dp))
        Wordmark()
        Spacer(Modifier.height(24.dp))
        BasicText(
            text = "A flat, dotted home screen for your Samsung.",
            style = DotlineTheme.type.body.copy(color = DotlineTheme.colors.primary),
        )
    }
}

@Composable
private fun DesignPage() {
    val now by rememberNow()
    PageFrame(eyebrow = "DESIGN") {
        PageTitle("Made of dots")
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .cardSurface()
                .padding(24.dp),
        ) {
            DotClock(now = now, stacked = true, dot = 5.dp, gap = 2.dp)
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .cardSurface(),
        ) {
            BulletRow(title = "Monochrome icons", subtitle = "Every app as a white glyph on a dark tile.")
            DottedDivider(Modifier.padding(horizontal = 20.dp))
            BulletRow(title = "Dot-matrix widgets", subtitle = "Clock, date and weather drawn in dots.")
            DottedDivider(Modifier.padding(horizontal = 20.dp))
            BulletRow(title = "No ads, no tracking", subtitle = "Nothing leaves your phone.")
        }
    }
}

@Composable
private fun HomePage(onSetDefault: () -> Unit) {
    PageFrame(eyebrow = "SETUP") {
        PageTitle("Make it your home")
        Spacer(Modifier.height(20.dp))
        BasicText(
            text = "Android will ask you to set Dotline as your home app. Confirm in the system dialog and you are done.",
            style = DotlineTheme.type.body.copy(color = DotlineTheme.colors.primary),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = "Every permission is optional. Dotline works without any of them, and you can change everything later in Settings.",
            style = DotlineTheme.type.body.copy(color = DotlineTheme.colors.secondary),
        )
        Spacer(Modifier.height(32.dp))
        PillButton(
            text = "Set as default",
            onClick = onSetDefault,
            modifier = Modifier.fillMaxWidth(),
            filled = true,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

/** Scrollable page body with the dot-matrix eyebrow label on top. */
@Composable
private fun PageFrame(eyebrow: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        DotText(
            text = eyebrow,
            dot = 2.5.dp,
            gap = 1.2.dp,
            color = DotlineTheme.colors.secondary,
        )
        Spacer(Modifier.height(32.dp))
        content()
    }
}

@Composable
private fun PageTitle(text: String) {
    BasicText(
        text = text,
        style = DotlineTheme.type.title.copy(color = DotlineTheme.colors.primary),
    )
}

@Composable
private fun Modifier.cardSurface(): Modifier =
    this.background(DotlineTheme.colors.card, DotlineTheme.shapes.card)

/** The DOTLINE wordmark in dot-matrix, wrapped onto two lines on very narrow screens. */
@Composable
private fun Wordmark() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= WordmarkOneLineWidth) {
            DotText(text = "DOTLINE", dot = 5.dp, gap = 2.dp)
        } else {
            Column {
                DotText(text = "DOT", dot = 5.dp, gap = 2.dp)
                Spacer(Modifier.height(12.dp))
                DotText(text = "LINE", dot = 5.dp, gap = 2.dp)
            }
        }
    }
}

/**
 * Decorative dot grid: the dots shrink row by row (flat circles, no gradient) and a single
 * Nothing red dot marks one cell. Drawn once; static.
 */
@Composable
private fun DotGrid(modifier: Modifier = Modifier) {
    val dim = DotlineTheme.colors.tertiary
    val accent = DotlineTheme.colors.accent
    val gridHeight = GridPitch * (GRID_ROWS - 1) + 6.dp
    Canvas(modifier.height(gridHeight)) {
        val pitch = GridPitch.toPx()
        val maxRadius = 3.dp.toPx()
        var cols = (size.width / pitch).toInt()
        if (cols < 1) cols = 1
        for (row in 0 until GRID_ROWS) {
            val radius = maxRadius * (GRID_ROWS - row).toFloat() / GRID_ROWS.toFloat()
            val cy = maxRadius + row * pitch
            for (col in 0 until cols) {
                val cx = maxRadius + col * pitch
                val isMark = row == 1 && col == 2
                drawCircle(
                    color = if (isMark) accent else dim,
                    radius = if (isMark) maxRadius * 0.8f else radius,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

@Composable
private fun BulletRow(title: String, subtitle: String) {
    val colors = DotlineTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Canvas(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp),
        ) {
            drawCircle(color = colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText(
                text = title,
                style = DotlineTheme.type.bodyMedium.copy(color = colors.primary),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = DotlineTheme.type.small.copy(color = colors.secondary),
            )
        }
    }
}

@Composable
private fun BottomBar(currentPage: Int, onSkip: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            SkipButton(onClick = onSkip)
        }
        DotPageIndicator(count = PAGE_COUNT, current = currentPage)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (currentPage < PAGE_COUNT - 1) {
                PillButton(text = "Next", onClick = onNext)
            }
        }
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button }
            .flatClickable(shape = DotlineTheme.shapes.pill, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Skip",
            style = DotlineTheme.type.bodyMedium.copy(color = DotlineTheme.colors.secondary),
        )
    }
}
