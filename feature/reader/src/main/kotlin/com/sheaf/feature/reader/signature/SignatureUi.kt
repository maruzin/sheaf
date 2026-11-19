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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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

private val INK_COLOURS = listOf(0xFF15171C.toInt() to "Black", 0xFF1F3FA8.toInt() to "Blue")

/**
 * Create or pick a signature: draw it (any number of strokes), photograph it on paper, choose a
 * picture of it, or reuse a saved one.
 */
@Composable
internal fun SignatureDialog(
    saved: List<String>,
    images: Map<String, Bitmap>,
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
    var tab by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 600.dp),
        ) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Your signature",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                    tabs.forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    when (tabs[tab]) {
                        "Saved" -> SavedTab(saved, images, onChoose = { onChoose(it); onDismiss() }, onForget = onForget)
                        "Draw" -> DrawTab(onCancel = onDismiss, onSave = { s, w, h, lw, c -> onSaveDrawn(s, w, h, lw, c); onDismiss() })
                        else -> PhotoTab(onImport = { onImport(it); onDismiss() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedTab(saved: List<String>, images: Map<String, Bitmap>, onChoose: (String) -> Unit, onForget: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tap a signature to use it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        saved.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignatureThumb(
                    image = images[name],
                    label = "Saved signature ${i + 1}",
                    active = false,
                    modifier = Modifier.weight(1f).height(72.dp),
                    onClick = { onChoose(name) },
                )
                IconButton(onClick = { onForget(name) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove saved signature ${i + 1}")
                }
            }
        }
        Text(
            "Removing a signature here doesn't change documents you already signed.",
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
                .semantics { contentDescription = "Signature pad. Draw your signature with your finger." }
                .padStrokes(strokes, current),
            contentAlignment = Alignment.Center,
        ) {
            if (strokes.isEmpty() && current.isEmpty()) {
                Text("Sign here", color = Color(0xFF9AA0A6))
            }
            Canvas(Modifier.matchParentSize()) {
                // Baseline to sign on.
                val y = size.height * 0.74f
                drawLine(Color(0xFFD0D4DA), Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), 2f)
                val c = Color(colour)
                (strokes + listOf(current.toList())).forEach { s -> drawPadStroke(s, c, lineWidthPx) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            INK_COLOURS.forEach { (argb, name) ->
                Box(
                    Modifier
                        .semantics {
                            contentDescription = "$name ink"
                            role = Role.RadioButton
                            selected = argb == colour
                        }
                        .size(36.dp)
                        .padding(4.dp)
                        .background(Color(argb), CircleShape)
                        .border(if (argb == colour) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { colour = argb },
                )
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
private fun PhotoTab(onImport: (Uri) -> Unit) {
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
            Text("Take a photo")
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
internal fun SignatureThumb(image: Bitmap?, label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .background(Color.White, shape)
            .border(if (active) 3.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .semantics {
                contentDescription = label
                role = Role.RadioButton
                selected = active
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
                        modifier = Modifier.size(width = 88.dp, height = 44.dp),
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
        Text(
            if (active == null) "Create your signature to start."
            else "Tap the page to place your signature. Drag it to move; use the handles to resize, turn or remove it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** A drag in progress: the placement shown for annotation [id] until the database catches up. */
internal data class LiveSignature(val id: Long, val placement: SignaturePlacement)

/** Draws placed signatures, and the selected one's frame and handles. */
internal fun DrawScope.drawSignatures(
    placed: List<Pair<Annotation, SignaturePlacement>>,
    images: Map<String, ImageBitmap>,
    selectedId: Long?,
    live: LiveSignature?,
    handleRadius: Float,
    accent: Color,
) {
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
                )
            }
            if (ann.id == selectedId) {
                val left = f.cx - f.hw
                val top = f.cy - f.hh
                drawRect(
                    color = accent,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(f.hw * 2, f.hh * 2),
                    style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
                val knobY = top - SignatureGestures.rotateKnobOffset(handleRadius)
                drawLine(accent, Offset(f.cx, top), Offset(f.cx, knobY), 2.5f)
                // Rotate knob: a ring.
                drawCircle(Color.White, handleRadius, Offset(f.cx, knobY))
                drawCircle(accent, handleRadius, Offset(f.cx, knobY), style = Stroke(width = 4f))
                drawArcHint(Offset(f.cx, knobY), handleRadius * 0.5f, accent)
                // Resize handle (bottom-right): filled with a diagonal arrow.
                val br = Offset(f.cx + f.hw, f.cy + f.hh)
                drawCircle(accent, handleRadius, br)
                val a = handleRadius * 0.45f
                drawLine(Color.White, br - Offset(a, a), br + Offset(a, a), 4f, cap = StrokeCap.Round)
                // Delete handle (top-left): red with an X.
                val tr = Offset(f.cx - f.hw, top)
                drawCircle(Color(0xFFD93025), handleRadius, tr)
                drawLine(Color.White, tr - Offset(a, a), tr + Offset(a, a), 4f, cap = StrokeCap.Round)
                drawLine(Color.White, tr + Offset(-a, a), tr + Offset(a, -a), 4f, cap = StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawArcHint(centre: Offset, r: Float, colour: Color) {
    drawArc(
        color = colour,
        startAngle = -60f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = centre - Offset(r, r),
        size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
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
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val items = placements()
        val hit = SignatureGestures.hitTest(
            items.map { it.first.id to it.second }, selectedId(), down.position.x, down.position.y, w, h, handleRadius,
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
                null -> if (selectedId() != null) onSelect(null)
                else onPlace(NormPoint(down.position.x / w, down.position.y / h), h / w)
            }
        }
    }
}
