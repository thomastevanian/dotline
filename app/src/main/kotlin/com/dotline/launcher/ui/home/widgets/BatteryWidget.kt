package com.dotline.launcher.ui.home.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.home.BatteryMath
import com.dotline.launcher.home.WidgetFit
import com.dotline.launcher.home.WidgetShape
import com.dotline.launcher.home.WidgetSizes
import com.dotline.launcher.ui.components.DotProgressBar
import com.dotline.launcher.ui.components.DotRing
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.theme.DotlineTheme

/* ------------------------------------------------------------------------------------------
 * Battery widget. 2x2 card: a dot ring around the percentage in dot-matrix digits and the caption
 * CHARGING / BATTERY. 2x1 capsule: dotted progress bar and the percentage. 1x1 circle: dot ring and
 * the number. The only red is the small charging dot.
 *
 * The level comes from the sticky ACTION_BATTERY_CHANGED broadcast. The receiver is registered ONLY
 * while the lifecycle is STARTED (and unregistered on STOP and dispose), the sticky value is read
 * again on every start. No polling, no timers.
 * ------------------------------------------------------------------------------------------ */

/** Percentage 0..100 ([percent] is -1 while unknown) and whether the phone is charging. */
@Immutable
private data class BatteryReading(val percent: Int, val charging: Boolean)

/** Last value seen in this process, so a widget that re-enters composition does not flash empty. */
private var lastBatteryReading: BatteryReading = BatteryReading(-1, false)

private fun batteryReadingFrom(intent: Intent?): BatteryReading? {
    if (intent == null) return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val percent = BatteryMath.percent(level, scale)
    if (percent < 0) return null
    return BatteryReading(percent, BatteryMath.isCharging(status))
}

@Composable
private fun rememberBatteryReading(): State<BatteryReading> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(lastBatteryReading) }
    DisposableEffect(lifecycleOwner, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val reading = batteryReadingFrom(intent)
                if (reading != null) {
                    lastBatteryReading = reading
                    state.value = reading
                }
            }
        }
        var registered = false

        fun readStickyNow() {
            try {
                val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val reading = batteryReadingFrom(sticky)
                if (reading != null) {
                    lastBatteryReading = reading
                    state.value = reading
                }
            } catch (e: RuntimeException) {
                CrashLog.record("batteryWidget.sticky", e)
            }
        }

        fun registerBatteryReceiver() {
            if (registered) return
            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
            } catch (e: RuntimeException) {
                CrashLog.record("batteryWidget.register", e)
            }
        }

        fun unregisterBatteryReceiver() {
            if (!registered) return
            registered = false
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Already unregistered; nothing to do.
            }
        }

        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        readStickyNow()
                        registerBatteryReceiver()
                    }
                    Lifecycle.Event.ON_STOP -> unregisterBatteryReceiver()
                    else -> Unit
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterBatteryReceiver()
        }
    }
    return state
}

@Composable
fun BatteryWidget(spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    val shape = WidgetSizes.shapeFor(spanX, spanY)
    val reading by rememberBatteryReading()
    val spoken = if (reading.percent < 0) {
        "Battery"
    } else if (reading.charging) {
        "Battery " + reading.percent.toString() + " percent, charging"
    } else {
        "Battery " + reading.percent.toString() + " percent"
    }
    WidgetSurface(shape = shape, modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().widgetDescription(spoken)) {
            when (shape) {
                WidgetShape.CARD -> BatteryCardBody(reading)
                WidgetShape.CAPSULE -> BatteryCapsuleBody(reading)
                WidgetShape.CIRCLE -> BatteryCircleBody(reading)
            }
        }
    }
}

/** The small red charging mark. Nothing else in the widget is red. */
@Composable
private fun BatteryChargingDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(DotlineTheme.colors.accent),
    )
}

/** The percentage in dot-matrix digits; nothing while the level is still unknown. */
@Composable
private fun BatteryDigits(percent: Int, ringDiameter: Float, maxDot: Float) {
    if (percent >= 0) {
        val text = percent.toString()
        val dot = WidgetFit.ringDigitDot(text.length, ringDiameter, maxDot)
        DotText(
            text = text,
            dot = dot.dp,
            gap = (dot * 0.4f).dp,
            color = DotlineTheme.colors.primary,
        )
    }
}

/** 2x2: ring with the percentage inside, caption underneath. */
@Composable
private fun BatteryCardBody(reading: BatteryReading) {
    val colors = DotlineTheme.colors
    val captionStyle = DotlineTheme.type.caption.copy(
        fontSize = widgetFixedSp(9f),
        lineHeight = widgetFixedSp(12f),
        color = colors.secondary,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val captionBlock = 12f + 12f
        val ring = minOf(maxWidth.value, maxHeight.value - captionBlock).coerceAtLeast(40f)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(ring.dp), contentAlignment = Alignment.Center) {
                DotRing(
                    progress = BatteryMath.progress(reading.percent),
                    modifier = Modifier.size(ring.dp),
                    dots = 60,
                )
                BatteryDigits(percent = reading.percent, ringDiameter = ring, maxDot = 4f)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reading.charging) {
                    BatteryChargingDot()
                    Spacer(Modifier.width(6.dp))
                }
                BasicText(
                    text = if (reading.charging) "CHARGING" else "BATTERY",
                    style = captionStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** 2x1: the percentage on top, a 20 dot progress bar underneath. */
@Composable
private fun BatteryCapsuleBody(reading: BatteryReading) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (reading.percent >= 0) {
                DotText(
                    text = reading.percent.toString() + "%",
                    dot = 2.dp,
                    gap = 0.8.dp,
                    color = DotlineTheme.colors.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            if (reading.charging) {
                BatteryChargingDot()
            }
        }
        Spacer(Modifier.height(8.dp))
        DotProgressBar(
            progress = BatteryMath.progress(reading.percent),
            modifier = Modifier.fillMaxWidth(),
            dots = 20,
        )
    }
}

/** 1x1: small ring with the number inside. */
@Composable
private fun BatteryCircleBody(reading: BatteryReading) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ring = (minOf(maxWidth.value, maxHeight.value) - 10f).coerceAtLeast(24f)
        Box(modifier = Modifier.size(ring.dp), contentAlignment = Alignment.Center) {
            DotRing(
                progress = BatteryMath.progress(reading.percent),
                modifier = Modifier.size(ring.dp),
                dots = 36,
            )
            BatteryDigits(percent = reading.percent, ringDiameter = ring, maxDot = 2.4f)
            if (reading.charging) {
                BatteryChargingDot(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (ring * 0.2f).dp),
                )
            }
        }
    }
}
