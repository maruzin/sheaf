package com.sheaf.feature.reader.signature

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.NormPoint
import kotlin.math.roundToInt
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField

private val INK_COLOURS = listOf(0xFF15171C.toInt() to "Black", 0xFF1F3FA8.toInt() to "Blue")

/**
 * Create or pick a signature: draw it (any number of strokes), photograph it on paper, choose a
 * picture of it, or reuse a saved one.
 */
@Composable
internal fun SignatureDialog(
    saved: List<String>,
    images: Map<String, Bitmap>,
    busy: Boolean = false,
    importError: String? = null,
    onDismiss: () -> Unit,
    onSaveDrawn: (strokes: List<List<NormPoint>>, padW: Float, padH: Float, lineWidthPx: Float, colorArgb: Int) -> Unit,
    onImport: (Uri) -> Unit,
    onChoose: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    val tabs = buildList {
        if (saved.isNotEmpty()) add("Saved")
        add("Draw")
        add("Photo")
    }
    // Saveable: after a rotation in the camera, the Photo tab (and its launchers) must come back.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // A photo import keeps the dialog open until it's done: on success it closes, on failure the
    // reason and a retry stay right here instead of a toast that vanishes.
    var importing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(busy, importError) {
        if (importing && !busy) {
            importing = false
            if (importError == null) onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 600.dp).semantics { paneTitle = "Your signature" },
        ) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Your signature",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
                )
                TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                    tabs.forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
                // Scrolls at large font sizes and in landscape, so the buttons are always reachable.
                // (The drawing pad consumes its own drags, so signing never scrolls the dialog.)
                Box(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    when (tabs[tab.coerceIn(0, tabs.lastIndex)]) {
                        "Saved" -> SavedTab(saved, images, onChoose = { onChoose(it); onDismiss() }, onForget = onForget)
                        "Draw" -> DrawTab(onCancel = onDismiss, onSave = { s, w, h, lw, c -> onSaveDrawn(s, w, h, lw, c); onDismiss() })
                        else -> PhotoTab(
                            busy = busy,
                            error = importError,
                            onImport = { importing = true; onImport(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedTab(saved: List<String>, images: Map<String, Bitmap>, onChoose: (String) -> Unit, onForget: (String) -> Unit) {
    var confirmForget by remember { mutableStateOf<String?>(null) }
    confirmForget?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmForget = null },
            title = { Text("Remove this signature?") },
            text = { Text("It's deleted from your phone. Documents you already signed keep it.") },
            confirmButton = { TextButton(onClick = { onForget(name); confirmForget = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmForget = null }) { Text("Keep") } },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tap a signature to use it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        saved.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignatureThumb(
                    image = images[name],
                    label = "Use saved signature ${i + 1}",
                    active = false,
                    role = Role.Button,
                    modifier = Modifier.weight(1f).height(72.dp),
                    onClick = { onChoose(name) },
                )
                IconButton(onClick = { confirmForget = name }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove saved signature ${i + 1}")
                }
            }
        }
        Text(
            "Removing a signature deletes it from your phone. Documents you already signed keep it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawTab(
    onCancel: () -> Unit,
    onSave: (List<List<NormPoint>>, Float, Float, Float, Int) -> Unit,
) {
    val strokes = remember { mutableStateListOf<List<NormPoint>>() }
    val current = remember { mutableStateListOf<NormPoint>() }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var colour by remember { mutableIntStateOf(INK_COLOURS[0].first) }
    val lineWidthPx = with(LocalDensity.current) { 3.dp.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2.4f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .onSizeChanged { padSize = it }
                .semantics {
                    contentDescription = "Signature pad. Draw your signature with your finger, or use the Photo tab instead."
                    stateDescription = if (strokes.isEmpty()) "Empty" else "Signature drawn"
                }
                .padStrokes(strokes, current),
            contentAlignment = Alignment.Center,
        ) {
            if (strokes.isEmpty() && current.isEmpty()) {
                Text("Sign here", color = Color(0xFF5F6368))
            }
            Canvas(Modifier.matchParentSize()) {
                // Baseline to sign on.
                val y = size.height * 0.74f
                drawLine(Color(0xFF8A9099), Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), 1.dp.toPx())
                val c = Color(colour)
                (strokes + listOf(current.toList())).forEach { s -> drawPadStroke(s, c, lineWidthPx) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.selectableGroup()) {
                INK_COLOURS.forEach { (argb, name) ->
                    // 48dp touch target around a 28dp swatch.
                    Box(
                        Modifier
                            .size(48.dp)
                            .selectable(selected = argb == colour, role = Role.RadioButton, onClick = { colour = argb })
                            .semantics { contentDescription = "$name ink" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(argb), CircleShape)
                                .border(if (argb == colour) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { strokes.removeLastOrNull() }, enabled = strokes.isNotEmpty()) { Text("Undo") }
            TextButton(onClick = { strokes.clear(); current.clear() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = { onSave(strokes.toList(), padSize.width.toFloat(), padSize.height.toFloat(), lineWidthPx, colour) },
                enabled = strokes.isNotEmpty() && padSize.width > 0,
            ) { Text("Save signature") }
        }
    }
}

/** Every finger stroke is kept (a signature is usually several), each point normalized over the pad. */
private fun Modifier.padStrokes(strokes: MutableList<List<NormPoint>>, current: MutableList<NormPoint>): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            fun norm(o: Offset) = NormPoint((o.x / w).coerceIn(0f, 1f), (o.y / h).coerceIn(0f, 1f))
            current.clear()
            current.add(norm(down.position))
            down.consume()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                current.add(norm(change.position))
                change.consume()
            }
            if (current.isNotEmpty()) strokes.add(current.toList())
            current.clear()
        }
    }

private fun DrawScope.drawPadStroke(points: List<NormPoint>, colour: Color, lineWidth: Float) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(colour, lineWidth / 2f, Offset(points[0].x * size.width, points[0].y * size.height))
        return
    }
    val path = Path().apply {
        moveTo(points[0].x * size.width, points[0].y * size.height)
        for (i in 1 until points.size) lineTo(points[i].x * size.width, points[i].y * size.height)
    }
    drawPath(path, colour, style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun PhotoTab(busy: Boolean, error: String?, onImport: (Uri) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImport(uri)
    }
    // The document scanner gives a cropped, flattened, upright photo: ideal for cutting out ink.
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .build(),
        )
    }
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri?.let(onImport)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Finding your signature…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (error != null && !busy) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        Text("Sign on plain white paper with a dark pen, then photograph it close up in good light.")
        Text(
            "Sheaf removes the paper so only your ink is placed on documents.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(
            onClick = {
                (context as? Activity)?.let { activity ->
                    scanner.getStartScanIntent(activity)
                        .addOnSuccessListener { scan.launch(IntentSenderRequest.Builder(it).build()) }
                        .addOnFailureListener {
                            Toast.makeText(context, "Camera isn't available. Choose a picture instead.", Toast.LENGTH_LONG).show()
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (error != null) "Try another photo" else "Take a photo")
        }
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Choose a picture")
        }
    }
}

@Composable
internal fun SignatureThumb(
    image: Bitmap?,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .background(Color.White, shape)
            .border(if (active) 3.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .semantics {
                contentDescription = label
                this.role = role
                if (role == Role.RadioButton) selected = active
            }
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription = null, filterQuality = FilterQuality.High)
        }
    }
}

/** Shown under the reader's top bar while signing. */
@Composable
internal fun SignatureBar(
    saved: List<String>,
    images: Map<String, Bitmap>,
    active: String?,
    busy: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onChoose: (String) -> Unit,
    onNew: () -> Unit,
    onDone: () -> Unit,
    hasSelection: Boolean = false,
    onPlaceHere: () -> Unit = {},
    onNudge: (SignatureGestures.Nudge) -> Unit = {},
    onRemove: () -> Unit = {},
    activeStamp: String? = null,
    onStamp: (SignatureStore.Stamp) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                saved.forEachIndexed { i, name ->
                    SignatureThumb(
                        image = images[name],
                        label = "Use signature ${i + 1}",
                        active = name == active,
                        modifier = Modifier.size(width = 96.dp, height = 48.dp),
                        onClick = { onChoose(name) },
                    )
                }
                OutlinedButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New")
                }
            }
            Button(onClick = onDone) { Text("Done") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        // Stamps: always on their own row, so they're never pushed off screen by the adjust buttons.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val marks = setOf("Date", "Tick", "Cross")
            StampChip("Date", activeStamp == "Date", { Icon(Icons.Filled.CalendarToday, null, Modifier.size(18.dp)) }) {
                onStamp(SignatureStore.Stamp.Date)
            }
            StampChip("Tick", activeStamp == "Tick", { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) {
                onStamp(SignatureStore.Stamp.Tick)
            }
            StampChip("Cross", activeStamp == "Cross", { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)) }) {
                onStamp(SignatureStore.Stamp.Cross)
            }
            StampChip("Text", activeStamp != null && activeStamp !in marks, { Icon(Icons.Filled.TextFields, null, Modifier.size(18.dp)) }) {
                onStamp(SignatureStore.Stamp.Text)
            }
            if (active != null && !hasSelection) TextButton(onClick = onPlaceHere) { Text("Place on this page") }
        }
        // Every drag on the page has a button here too: for TalkBack and Switch Access users, and for
        // anyone who finds small, precise drags hard. Always shown (disabled until something is
        // selected) so the bar never changes height and the page never jumps under the finger.
        run {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val what = if (activeStamp != null) "stamp" else "signature"
                NudgeButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move $what left", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.Left) }
                NudgeButton(Icons.Filled.KeyboardArrowUp, "Move $what up", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.Up) }
                NudgeButton(Icons.Filled.KeyboardArrowDown, "Move $what down", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.Down) }
                NudgeButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move $what right", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.Right) }
                NudgeButton(Icons.Filled.ZoomOut, "Make $what smaller", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.Smaller) }
                NudgeButton(Icons.Filled.ZoomIn, "Make $what bigger", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.Bigger) }
                NudgeButton(Icons.AutoMirrored.Filled.RotateLeft, "Turn $what left", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.TurnLeft) }
                NudgeButton(Icons.AutoMirrored.Filled.RotateRight, "Turn $what right", enabled = hasSelection) { onNudge(SignatureGestures.Nudge.TurnRight) }
                NudgeButton(Icons.Filled.Delete, "Remove $what", enabled = hasSelection, onClick = onRemove)
            }
        }
        Text(
            when {
                hasSelection && activeStamp != null -> "Stamp selected. Adjust it, or tap the page to add another."
                hasSelection -> "Signature selected. Drag it or use the buttons to move, resize or turn it."
                activeStamp != null -> "Tap each place the ${if (activeStamp in setOf("Date", "Tick", "Cross")) activeStamp.lowercase() else "text"} goes, or use Place on this page."
                active == null -> "Create your signature, or add a date, tick, cross or text."
                else -> "Tap the page where the signature goes, or use Place on this page."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Read out when it changes, so a TalkBack user hears that a signature was placed or selected.
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Text for a stamp: a name, "N/A", a reference number, a job title. */
@Composable
internal fun StampTextDialog(initial: String, onDismiss: () -> Unit, onStamp: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text stamp") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(SignatureStore.MAX_STAMP_CHARS) },
                singleLine = true,
                label = { Text("Text to place") },
                placeholder = { Text("Your name, N/A, a reference…") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onStamp(text) }, enabled = text.isNotBlank()) { Text("Place text") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StampChip(label: String, selected: Boolean, icon: @Composable () -> Unit, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon,
        modifier = Modifier.padding(horizontal = 3.dp).semantics { contentDescription = "$label stamp" },
    )
}

@Composable
private fun NudgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
}

/** A drag in progress: the placement shown for annotation [id] until the database catches up. */
internal data class LiveSignature(val id: Long, val placement: SignaturePlacement)

/**
 * Draws placed signatures, and the selected one's frame and handles. [pageFilter] is the reading
 * theme's page filter: signatures get it too, so black ink stays visible on a darkened page.
 * Handles use a fixed high-contrast blue with a white rim (readable on white and dark pages alike),
 * with sizes in dp so they look the same on every screen.
 */
internal fun DrawScope.drawSignatures(
    placed: List<Pair<Annotation, SignaturePlacement>>,
    images: Map<String, ImageBitmap>,
    selectedId: Long?,
    live: LiveSignature?,
    handleRadius: Float,
    pageFilter: ColorFilter? = null,
) {
    val line = 2.dp.toPx()
    val rim = 2.dp.toPx()
    placed.forEach { (ann, saved) ->
        val p = if (live?.id == ann.id) live.placement else saved
        val f = SignatureGestures.frame(p, size.width, size.height)
        withTransform({ rotate(p.rotation, Offset(f.cx, f.cy)) }) {
            images[p.image]?.let { img ->
                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset((f.cx - f.hw).roundToInt(), (f.cy - f.hh).roundToInt()),
                    dstSize = IntSize((f.hw * 2).roundToInt().coerceAtLeast(1), (f.hh * 2).roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High,
                    colorFilter = pageFilter,
                )
            }
            if (ann.id == selectedId) {
                val left = f.cx - f.hw
                val top = f.cy - f.hh
                val frameSize = androidx.compose.ui.geometry.Size(f.hw * 2, f.hh * 2)
                // White under blue: the frame shows on white paper and on a dark page.
                drawRect(Color.White, Offset(left, top), frameSize, style = Stroke(width = line + rim))
                drawRect(
                    color = HANDLE_BLUE,
                    topLeft = Offset(left, top),
                    size = frameSize,
                    style = Stroke(width = line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
                val knobY = top - SignatureGestures.rotateKnobOffset(handleRadius)
                drawLine(Color.White, Offset(f.cx, top), Offset(f.cx, knobY), line + rim)
                drawLine(HANDLE_BLUE, Offset(f.cx, top), Offset(f.cx, knobY), line)
                // Rotate knob: a white disc with a blue ring and a turning arrow.
                handleDisc(Offset(f.cx, knobY), handleRadius, Color.White, rim)
                drawCircle(HANDLE_BLUE, handleRadius - rim / 2, Offset(f.cx, knobY), style = Stroke(width = 1.5f.dp.toPx()))
                drawArcHint(Offset(f.cx, knobY), handleRadius * 0.5f, HANDLE_BLUE)
                // Resize handle (bottom-right): blue with a diagonal arrow.
                val a = handleRadius * 0.45f
                val br = Offset(f.cx + f.hw, f.cy + f.hh)
                handleDisc(br, handleRadius, HANDLE_BLUE, rim)
                drawLine(Color.White, br - Offset(a, a), br + Offset(a, a), 1.5f.dp.toPx(), cap = StrokeCap.Round)
                // Delete handle (top-left): red with an X.
                val tl = Offset(left, top)
                handleDisc(tl, handleRadius, HANDLE_RED, rim)
                drawLine(Color.White, tl - Offset(a, a), tl + Offset(a, a), 1.5f.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, tl + Offset(-a, a), tl + Offset(a, -a), 1.5f.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** A handle disc with a white rim and a thin dark outline, visible on any page colour. */
private fun DrawScope.handleDisc(centre: Offset, r: Float, fill: Color, rim: Float) {
    drawCircle(Color.Black.copy(alpha = 0.55f), r + rim + 0.75f.dp.toPx(), centre)
    drawCircle(Color.White, r + rim, centre)
    drawCircle(fill, r, centre)
}

private val HANDLE_BLUE = Color(0xFF1A56DB)
private val HANDLE_RED = Color(0xFFC5221F)

private fun DrawScope.drawArcHint(centre: Offset, r: Float, colour: Color) {
    drawArc(
        color = colour,
        startAngle = -60f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = centre - Offset(r, r),
        size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round),
    )
}

/**
 * The signing gesture loop on a page: tap to place (or select / deselect), drag a signature to move
 * it, drag its handles to resize or turn it, tap its red handle to remove it. A finger that starts on
 * empty page and moves is left alone, so one-finger scrolling still works; two fingers always scroll.
 */
internal suspend fun PointerInputScope.signatureGestures(
    placements: () -> List<Pair<Annotation, SignaturePlacement>>,
    selectedId: () -> Long?,
    handleRadius: Float,
    onLive: (LiveSignature?) -> Unit,
    onPlace: (at: NormPoint, pageAspect: Float) -> Unit,
    onSelect: (Long?) -> Unit,
    onDelete: (Long) -> Unit,
    onCommit: (Annotation, SignaturePlacement) -> Unit,
    /** Stamping: a tap on empty page always places another stamp, even while one is selected. */
    placeOnEveryTap: () -> Boolean = { false },
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val items = placements()
        // A selection on another page is no selection here.
        val selectedHere = selectedId()?.takeIf { id -> items.any { it.first.id == id } }
        val hit = SignatureGestures.hitTest(
            items.map { it.first.id to it.second }, selectedHere, down.position.x, down.position.y, w, h, handleRadius,
            // Handles respond across Android's 48dp minimum touch target, though drawn smaller.
            minTouchRadius = viewConfiguration.minimumTouchTargetSize.width.toPx() / 2f,
        )
        val target = hit?.let { found -> items.firstOrNull { it.first.id == found.id } }
        if (hit != null) down.consume()
        val slop = viewConfiguration.touchSlop
        var dragging = false
        var abandoned = false
        var last: SignaturePlacement? = null
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) { abandoned = true; break }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.position - down.position
            if (hit == null) {
                // Empty page: a moving finger is a scroll, not a tap.
                if (change.isConsumed || delta.getDistance() > slop) { abandoned = true; break }
                continue
            }
            if (!dragging && delta.getDistance() > slop) {
                dragging = true
                if (hit.part == SignatureGestures.Part.Body) onSelect(hit.id)
            }
            if (dragging && target != null) {
                val start = target.second
                last = when (hit.part) {
                    SignatureGestures.Part.Body -> SignatureGestures.move(start, delta.x, delta.y, w, h)
                    SignatureGestures.Part.Resize -> SignatureGestures.resize(start, change.position.x, change.position.y, w, h)
                    SignatureGestures.Part.Rotate -> SignatureGestures.rotate(start, change.position.x, change.position.y, w, h)
                    SignatureGestures.Part.Delete -> null
                }
                last?.let { onLive(LiveSignature(hit.id, it)) }
            }
            change.consume()
        }
        if (abandoned) {
            onLive(null)
            return@awaitEachGesture
        }
        val moved = last
        if (dragging && target != null && moved != null) {
            onCommit(target.first, moved)
            return@awaitEachGesture
        }
        if (!dragging) {
            when (hit?.part) {
                SignatureGestures.Part.Delete -> onDelete(hit.id)
                SignatureGestures.Part.Body -> onSelect(hit.id)
                SignatureGestures.Part.Resize, SignatureGestures.Part.Rotate -> Unit
                null -> if (selectedHere != null && !placeOnEveryTap()) onSelect(null)
                else onPlace(NormPoint(down.position.x / w, down.position.y / h), h / w)
            }
        }
    }
}
