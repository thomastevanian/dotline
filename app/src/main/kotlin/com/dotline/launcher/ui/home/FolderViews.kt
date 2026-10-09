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
import androidx.compose.ui.unit.IntOffset
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
 * Every mini icon is the real processed app icon (tile + glyph) rendered at about a third of the
 * folder size by the normal icon pipeline, so it is cached and drawn once.
 *
 * FolderPopup: the opened folder, a rounded flat card on a scrim with a renamable Doto title and
 * a clean 4 column grid of icons with labels.
 * ------------------------------------------------------------------------------------------ */

/**
 * Folder geometry and name rules. Plain Kotlin (no Compose types) so the numbers can be checked on any JVM.
 */
internal object FolderMath {
    /**
     * Mini icon diameter as a fraction of the folder diameter. Measured on the enlarged reference crop
     * (ref3_icons.png, three folders, ten mini tiles): 0.338 of the folder diameter.
     */
    const val MINI_FRACTION = 0.34f

    /**
     * Distance of every mini icon centre from the folder centre (horizontally and vertically), as a fraction of
     * the folder diameter. Measured on the same crop: 0.19 (left/right 0.187 to 0.196, top/bottom 0.187 to 0.204).
     */
    const val CENTER_FRACTION = 0.19f

    /** Mini icons shown in a folder tile; more apps than this show the first four. */
    const val PREVIEW_COUNT = 4

    /** Longest folder name the editor accepts (the controller trims to the same length). */
    const val NAME_MAX = 24

    /** How many mini icons a folder with [appCount] apps shows. */
    fun previewCount(appCount: Int): Int {
        if (appCount <= 0) return 0
        return if (appCount < PREVIEW_COUNT) appCount else PREVIEW_COUNT
    }

    /**
     * Left (and top) edge in pixels of the first column (row) of mini icons inside a folder that is [folderPx]
     * wide with minis of [miniPx]. The second column (row) is the mirror image, see [farEdgePx], so the minis
     * are exactly symmetric on the pixel grid and never leave the folder.
     */
    fun nearEdgePx(folderPx: Int, miniPx: Int): Int {
        if (folderPx <= 0 || miniPx <= 0) return 0
        val room = folderPx - miniPx
        if (room <= 0) return 0
        val raw = room / 2f - CENTER_FRACTION * folderPx
        val rounded = raw.roundToInt()
        val limit = room / 2
        return if (rounded < 0) 0 else if (rounded > limit) limit else rounded
    }

    /** Left (and top) edge in pixels of the second column (row) of mini icons: the mirror of [nearEdgePx]. */
    fun farEdgePx(folderPx: Int, miniPx: Int): Int {
        val room = folderPx - miniPx
        if (room <= 0) return 0
        return room - nearEdgePx(folderPx, miniPx)
    }

    /**
     * The text a folder name field keeps after an edit from [old] to [new]: one line, at most [NAME_MAX]
     * characters. Typing into a full name changes nothing; a longer paste is cut at the limit.
     */
    fun limitName(old: String, new: String): String {
        val oneLine = if (new.indexOf('\n') >= 0 || new.indexOf('\r') >= 0) {
            new.replace('\n', ' ').replace('\r', ' ')
        } else {
            new
        }
        if (oneLine.length <= NAME_MAX) return oneLine
        if (old.length >= NAME_MAX) return old
        var end = NAME_MAX
        if (oneLine[end - 1].isHighSurrogate()) end -= 1
        return oneLine.substring(0, end)
    }

    /** The name to store after editing: [typed] without surrounding blanks, or null when that is empty or equals [current]. */
    fun renamed(typed: String, current: String): String? {
        val trimmed = typed.trim()
        return if (trimmed.isNotEmpty() && trimmed != current) trimmed else null
    }

    /** [index] limited to the range 0..[length]. */
    fun clampIndex(index: Int, length: Int): Int {
        if (index < 0) return 0
        return if (index > length) length else index
    }
}

/** Columns of the opened folder. */
private const val FOLDER_COLUMNS = 4

/** Share of the available height the icon grid may take before it scrolls. */
private const val FOLDER_GRID_MAX_HEIGHT_FRACTION = 0.6f

/** The popup card starts at this scale and grows to 1 while it fades in. */
private const val FOLDER_ENTER_SCALE_FROM = 0.94f

/** Corner radius of the rounded-square folder (same fraction the icon renderer uses for its tiles). */
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
 * colour holding up to four mini icons in a 2 x 2 arrangement (see [FolderMath] for the measured sizes).
 * With three apps the bottom-right slot stays empty; with more than four the first four are shown.
 * No label: the caller draws it. [size] is the final diameter (the caller applies the icon size setting).
 */
@Composable
fun FolderTile(apps: List<AppInfo>, size: Dp, modifier: Modifier = Modifier) {
    val colors = DotlineTheme.colors
    val shape = folderShapeFor(LocalSettings.current.iconShape)
    val density = LocalDensity.current
    val miniSize = size * FolderMath.MINI_FRACTION
    val folderPx = with(density) { size.roundToPx() }
    val miniPx = with(density) { miniSize.roundToPx() }
    val nearPx = FolderMath.nearEdgePx(folderPx, miniPx)
    val farPx = FolderMath.farEdgePx(folderPx, miniPx)
    val count = FolderMath.previewCount(apps.size)
    Box(
        modifier = modifier
            .size(size)
            .background(colors.folderTile, shape),
    ) {
        for (i in 0 until count) {
            val x = if (i % 2 == 0) nearPx else farPx
            val y = if (i < 2) nearPx else farPx
            FolderMiniIcon(
                app = apps[i],
                miniSize = miniSize,
                modifier = Modifier.offset { IntOffset(x, y) },
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
 * A name that is being edited is kept when the folder is closed, an app is launched or lifted out.
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

/**
 * State of the folder name editor. The fields are read only by the header composable, so typing
 * recomposes the header and nothing else.
 */
@Stable
private class FolderNameEditor {
    var editing: Boolean by mutableStateOf(false)
    var field: TextFieldValue by mutableStateOf(TextFieldValue(""))

    /** Starts editing [name] with the cursor at its end. */
    fun begin(name: String) {
        field = TextFieldValue(text = name, selection = TextRange(name.length))
        editing = true
    }

    /** Applies one edit coming from the text field, keeping the name to one line of at most 24 characters. */
    fun change(value: TextFieldValue) {
        val old = field
        val kept = FolderMath.limitName(old.text, value.text)
        field = when {
            kept == value.text -> value
            kept == old.text -> old
            else -> TextFieldValue(
                text = kept,
                selection = TextRange(
                    FolderMath.clampIndex(value.selection.start, kept.length),
                    FolderMath.clampIndex(value.selection.end, kept.length),
                ),
            )
        }
    }

    /**
     * Ends editing. Returns the trimmed new name, or null when nothing has to be renamed (not editing,
     * blank, or unchanged compared with [currentName]).
     */
    fun finish(currentName: String): String? {
        if (!editing) return null
        editing = false
        return FolderMath.renamed(field.text, currentName)
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

    val editor = remember { FolderNameEditor() }
    val latestName by rememberUpdatedState(name)
    val latestRename by rememberUpdatedState(onRename)
    val latestLaunch by rememberUpdatedState(onLaunch)
    val latestLongPress by rememberUpdatedState(onAppLongPress)
    val latestDismiss by rememberUpdatedState(onDismiss)

    // Every way out of the name editor (keyboard Done, tap on the card, close, launch, lift out) lands here.
    val finishEditing: () -> Unit = remember(editor, focusManager) {
        {
            if (editor.editing) {
                focusManager.clearFocus()
                val renamed = editor.finish(latestName)
                if (renamed != null) latestRename(renamed)
            }
        }
    }
    val dismiss: () -> Unit = remember(finishEditing) {
        {
            finishEditing()
            latestDismiss()
        }
    }
    val launchApp: (AppInfo, AndroidRect?) -> Unit = remember(finishEditing) {
        { app: AppInfo, bounds: AndroidRect? ->
            finishEditing()
            latestLaunch(app, bounds)
        }
    }
    val liftApp: (AppInfo, Offset, Offset) -> Unit = remember(finishEditing) {
        { app: AppInfo, center: Offset, topLeft: Offset ->
            finishEditing()
            latestLongPress(app, center, topLeft)
        }
    }

    // The folder can also vanish without any of the above (home button, last app removed): keep the typed name.
    DisposableEffect(editor) {
        onDispose {
            val renamed = editor.finish(latestName)
            if (renamed != null) latestRename(renamed)
        }
    }

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
            .pointerInput(dismiss) {
                detectTapGestures(onTap = { dismiss() })
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
                    // A tap on the card never reaches the scrim behind it; on the bare card it ends a name edit.
                    .pointerInput(finishEditing) {
                        detectTapGestures(onTap = { finishEditing() })
                    }
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FolderNameHeader(
                    editor = editor,
                    name = name,
                    onDone = finishEditing,
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
                            onLaunch = launchApp,
                            onLongPress = liftApp,
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
 * Done action calls [onDone].
 */
@Composable
private fun FolderNameHeader(
    editor: FolderNameEditor,
    name: String,
    onDone: () -> Unit,
) {
    val colors = DotlineTheme.colors
    val type = DotlineTheme.type
    val primary = colors.primary
    val secondary = colors.secondary
    val titleStyle = remember(type, primary) {
        type.heading.copy(color = primary, textAlign = TextAlign.Center)
    }
    val captionStyle = remember(type, secondary) {
        type.caption.copy(color = secondary, textAlign = TextAlign.Center)
    }
    val selectionColors = remember(primary) {
        TextSelectionColors(handleColor = primary, backgroundColor = primary.copy(alpha = 0.3f))
    }
    val shownName = if (name.isBlank()) "Folder" else name
    val editing = editor.editing

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    BasicTextField(
                        value = editor.field,
                        onValueChange = { value: TextFieldValue -> editor.change(value) },
                        modifier = Modifier
                            .fillMaxWidth()
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
            }
            DottedDivider(Modifier.padding(horizontal = 12.dp))
        } else {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.Button }
                    .flatClickable(shape = DotlineTheme.shapes.chip, onClick = { editor.begin(name) })
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
            // Same height as the dotted underline of the editor, so the grid does not jump when editing starts.
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(2.dp))
        BasicText(
            text = if (editing) "Press Done to save" else "Tap the name to rename",
            style = captionStyle,
        )
    }
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
    val tilePx = with(density) { (FolderPopupIconBase * settings.iconSize).roundToPx() }.toFloat()
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
