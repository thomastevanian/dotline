package com.dotline.launcher.ui.home

import android.graphics.Rect as AndroidRect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dotline.launcher.data.IconShape
import com.dotline.launcher.data.icons.AppIconView
import com.dotline.launcher.data.icons.rememberAppIcon
import com.dotline.launcher.data.icons.rememberIconRequest
import com.dotline.launcher.data.model.AppInfo
import com.dotline.launcher.ui.LocalSettings
import com.dotline.launcher.ui.components.DottedDivider
import com.dotline.launcher.ui.theme.DotlineTheme
import com.dotline.launcher.ui.theme.LocalReduceMotion
import com.dotline.launcher.ui.theme.flatClickable
import kotlin.math.roundToInt

/* ------------------------------------------------------------------------------------------
 * Folders, Nothing OS style.
 *
 * FolderTile: the lighter circle on the home screen with up to four mini icons in a 2 x 2 grid.
 * Every mini icon is the real processed app icon (tile + glyph) rendered at 31 percent of the
 * folder size by the normal icon pipeline, so it is cached and drawn once.
 *
 * FolderPopup: the opened folder, a rounded flat card on a scrim with a renamable Doto title and
 * a clean 4 column grid of icons with labels.
 * ------------------------------------------------------------------------------------------ */

/** Mini icon diameter as a fraction of the folder diameter (spec: 31 percent). */
private const val FOLDER_MINI_FRACTION = 0.31f

/** Distance of every mini icon centre from the folder centre, as a fraction of the folder diameter. */
private const val FOLDER_MINI_CENTER_FRACTION = 0.18f

/** Mini icons shown in a folder tile. */
private const val FOLDER_PREVIEW_COUNT = 4

/** Longest folder name the editor accepts. */
private const val FOLDER_NAME_MAX = 24

/** Columns of the opened folder. */
private const val FOLDER_COLUMNS = 4

/** Share of the available height the icon grid may take before it scrolls. */
private const val FOLDER_GRID_MAX_HEIGHT_FRACTION = 0.6f

/** The popup card starts at this scale and grows to 1 while it fades in. */
private const val FOLDER_ENTER_SCALE_FROM = 0.94f

/** Corner radius of the rounded-square folder (same fraction the icon renderer uses for tiles). */
private const val FOLDER_ROUNDED_PERCENT = 28

/** Base icon size inside the opened folder; AppIconView multiplies it by the user's icon size setting. */
private val FolderPopupIconBase: Dp = 52.dp

private val FolderPopupMaxWidth: Dp = 400.dp
private val FolderPopupShape = RoundedCornerShape(28.dp)
private val FolderRoundedShape = RoundedCornerShape(percent = FOLDER_ROUNDED_PERCENT)
private val FolderCellShape = RoundedCornerShape(16.dp)

private fun folderShapeFor(iconShape: IconShape): Shape {
    return if (iconShape == IconShape.CIRCLE) CircleShape else FolderRoundedShape
}

// ---------------------------------------------------------------------------------------------
// Home screen tile
// ---------------------------------------------------------------------------------------------

/**
 * The folder as it appears on the home screen: a circle of [size] filled with the (lighter) folder
 * colour holding up to four mini icons. Mini centres sit at plus or minus 18 percent of [size] from the
 * centre; with three apps the bottom-right slot stays empty; more than four show the first four.
 * No label: the caller draws it. [size] is the final diameter (the caller applies the icon size setting).
 */
@Composable
fun FolderTile(apps: List<AppInfo>, size: Dp, modifier: Modifier = Modifier) {
    val colors = DotlineTheme.colors
    val shape = folderShapeFor(LocalSettings.current.iconShape)
    val miniSize = size * FOLDER_MINI_FRACTION
    val centerShift = size * FOLDER_MINI_CENTER_FRACTION
    val count = if (apps.size < FOLDER_PREVIEW_COUNT) apps.size else FOLDER_PREVIEW_COUNT
    Box(
        modifier = modifier
            .size(size)
            .background(colors.folderTile, shape),
    ) {
        for (i in 0 until count) {
            val dx = if (i % 2 == 0) -centerShift else centerShift
            val dy = if (i < 2) -centerShift else centerShift
            FolderMiniIcon(
                app = apps[i],
                miniSize = miniSize,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = dx, y = dy),
            )
        }
    }
}

/**
 * One mini icon. The processed bitmap already contains the dark tile, so it is drawn as is; until it
 * has loaded a flat tile-coloured placeholder keeps the slot from looking empty.
 */
@Composable
private fun FolderMiniIcon(app: AppInfo, miniSize: Dp, modifier: Modifier = Modifier) {
    val shape = folderShapeFor(LocalSettings.current.iconShape)
    val tileColor = DotlineTheme.colors.iconTile
    val request = rememberIconRequest(sizeDp = miniSize)
    val icon = rememberAppIcon(app, request).value
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.size(miniSize),
        )
    } else {
        Box(
            modifier = modifier
                .size(miniSize)
                .background(tileColor, shape),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Opened folder
// ---------------------------------------------------------------------------------------------

/**
 * The opened folder: a dimmed scrim over the whole screen (tap outside or back closes it) and a
 * centred rounded card with the renamable name and a 4 column grid of the folder's apps.
 *
 * [onLaunch] gets the window bounds of the tapped icon (launch animation source). [onAppLongPress] gets
 * the icon's centre and top-left in root coordinates so the caller can lift the icon out of the folder.
 * An empty folder shows nothing. Fades and scales in over 150 ms (instantly when animations are reduced).
 */
@Composable
fun FolderPopup(
    name: String,
    apps: List<AppInfo>,
    onRename: (String) -> Unit,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onAppLongPress: (app: AppInfo, rootCenter: Offset, rootTopLeft: Offset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isNotEmpty()) {
        FolderPopupContent(
            name = name,
            apps = apps,
            onRename = onRename,
            onLaunch = onLaunch,
            onAppLongPress = onAppLongPress,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

@Composable
private fun FolderPopupContent(
    name: String,
    apps: List<AppInfo>,
    onRename: (String) -> Unit,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onAppLongPress: (app: AppInfo, rootCenter: Offset, rootTopLeft: Offset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val colors = DotlineTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val focusManager = LocalFocusManager.current

    // A duplicate key would crash the lazy grid, so every app is shown once.
    val shownApps = remember(apps) { apps.distinctBy { app -> app.key.flat } }

    var editing by remember { mutableStateOf(false) }
    var nameField by remember { mutableStateOf(TextFieldValue(name)) }

    fun commitName() {
        val trimmed = nameField.text.trim()
        editing = false
        focusManager.clearFocus()
        if (trimmed.isNotEmpty() && trimmed != name) {
            onRename(trimmed)
        }
    }

    fun startEditing() {
        nameField = TextFieldValue(text = name, selection = TextRange(name.length))
        editing = true
    }

    // Closing while the name is being edited keeps what was typed.
    val dismiss: () -> Unit = {
        if (editing) {
            commitName()
        }
        onDismiss()
    }
    val latestDismiss by rememberUpdatedState(dismiss)

    BackHandler {
        dismiss()
    }

    // One-shot 150 ms ease-out. The value is read in draw / graphicsLayer lambdas only.
    val enter = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            enter.animateTo(1f, tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing))
        }
    }

    val scrimColor = colors.scrim
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = scrimColor, alpha = enter.value)
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { latestDismiss() })
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val gridMaxHeight = maxHeight * FOLDER_GRID_MAX_HEIGHT_FRACTION
            Column(
                modifier = Modifier
                    .widthIn(max = FolderPopupMaxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = enter.value
                        alpha = p
                        val s = FOLDER_ENTER_SCALE_FROM + (1f - FOLDER_ENTER_SCALE_FROM) * p
                        scaleX = s
                        scaleY = s
                    }
                    .background(colors.widget, FolderPopupShape)
                    // Swallow taps on the card so they never reach the scrim behind it.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { })
                    }
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FolderNameHeader(
                    name = name,
                    editing = editing,
                    nameField = nameField,
                    onStartEditing = { startEditing() },
                    onFieldChange = { value: TextFieldValue -> nameField = clampFolderName(value) },
                    onDone = { commitName() },
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(FOLDER_COLUMNS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = gridMaxHeight),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = shownApps, key = { app -> app.key.flat }) { app ->
                        FolderAppCell(
                            app = app,
                            onLaunch = onLaunch,
                            onLongPress = onAppLongPress,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The folder name in the Doto heading style with a tiny caption. Tapping the name turns it into a
 * single-line text field (same dot-matrix look, a dotted underline marks it as editable); the IME
 * Done action commits.
 */
@Composable
private fun FolderNameHeader(
    name: String,
    editing: Boolean,
    nameField: TextFieldValue,
    onStartEditing: () -> Unit,
    onFieldChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val primary = colors.primary
    val titleStyle = remember(type, primary) {
        type.heading.copy(color = primary, textAlign = TextAlign.Center)
    }
    val secondary = colors.secondary
    val captionStyle = remember(type, secondary) {
        type.caption.copy(color = secondary, textAlign = TextAlign.Center)
    }
    val selectionColors = remember(primary) {
        TextSelectionColors(handleColor = primary, backgroundColor = primary.copy(alpha = 0.3f))
    }
    val shownName = if (name.isBlank()) "Folder" else name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (editing) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    // The field is not attached yet; the user can tap it to focus.
                }
            }
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                BasicTextField(
                    value = nameField,
                    onValueChange = onFieldChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .focusRequester(focusRequester),
                    textStyle = titleStyle,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    singleLine = true,
                    cursorBrush = SolidColor(primary),
                )
            }
            DottedDivider(Modifier.padding(horizontal = 12.dp))
        } else {
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .semantics { role = Role.Button }
                    .flatClickable(shape = DotlineTheme.shapes.chip, onClick = onStartEditing)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = shownName,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = if (editing) "Press Done to save" else "Tap the name to rename",
            style = captionStyle,
        )
    }
}

/** Keeps a folder name to [FOLDER_NAME_MAX] characters on one line. */
private fun clampFolderName(value: TextFieldValue): TextFieldValue {
    val cleaned = value.text.replace('\n', ' ')
    if (cleaned.length <= FOLDER_NAME_MAX) {
        if (cleaned == value.text) return value
        return TextFieldValue(text = cleaned, selection = TextRange(cleaned.length))
    }
    var end = FOLDER_NAME_MAX
    if (cleaned[end - 1].isHighSurrogate()) {
        end -= 1
    }
    val cut = cleaned.substring(0, end)
    return TextFieldValue(text = cut, selection = TextRange(cut.length))
}

/**
 * Where an icon cell is on screen. Holds the coordinates only (a plain object, not state) and works
 * out the bounds of the ICON TILE (without the label) when a tap or long press needs them.
 */
private class FolderCellBounds {
    var coordinates: LayoutCoordinates? = null

    /** The icon tile in window pixels, or null while the cell is not laid out. */
    fun tileInWindow(tilePx: Float): AndroidRect? {
        val c = coordinates ?: return null
        if (!c.isAttached) return null
        val r = c.boundsInWindow()
        val left = r.left + (r.width - tilePx) / 2f
        return AndroidRect(
            left.roundToInt(),
            r.top.roundToInt(),
            (left + tilePx).roundToInt(),
            (r.top + tilePx).roundToInt(),
        )
    }

    /** Top-left of the icon tile in root pixels, or null while the cell is not laid out. */
    fun tileTopLeftInRoot(tilePx: Float): Offset? {
        val c = coordinates ?: return null
        if (!c.isAttached) return null
        val r = c.boundsInRoot()
        return Offset(r.left + (r.width - tilePx) / 2f, r.top)
    }
}

/** One app in the opened folder: the icon with its label, a flat press overlay, tap and long press. */
@Composable
private fun FolderAppCell(
    app: AppInfo,
    onLaunch: (AppInfo, AndroidRect?) -> Unit,
    onLongPress: (app: AppInfo, rootCenter: Offset, rootTopLeft: Offset) -> Unit,
) {
    val settings = LocalSettings.current
    val density = LocalDensity.current
    val holder = remember { FolderCellBounds() }
    val tileDp = FolderPopupIconBase * settings.iconSize
    val tilePx = with(density) { tileDp.toPx() }
    val describe: Modifier = if (settings.showLabels) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = app.label }
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AppIconView(
            app = app,
            modifier = Modifier
                .then(describe)
                .semantics { role = Role.Button }
                .flatClickable(
                    shape = FolderCellShape,
                    onLongClick = {
                        val topLeft = holder.tileTopLeftInRoot(tilePx)
                        if (topLeft != null) {
                            val center = Offset(topLeft.x + tilePx / 2f, topLeft.y + tilePx / 2f)
                            onLongPress(app, center, topLeft)
                        }
                    },
                    onClick = { onLaunch(app, holder.tileInWindow(tilePx)) },
                )
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .onGloballyPositioned { coordinates -> holder.coordinates = coordinates },
            iconSize = FolderPopupIconBase,
        )
    }
}
