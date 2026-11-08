package com.vrgames.shanhaibook.content

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.shanhaibook.ATLAS_ID
import com.vrgames.shanhaibook.MARKS_ID
import com.vrgames.shanhaibook.core.Mechanism
import com.vrgames.shanhaibook.game.AutoPilot
import com.vrgames.shanhaibook.game.Game
import com.vrgames.shanhaibook.render.BookView
import com.vrgames.shanhaibook.render.Music
import com.vrgames.shanhaibook.render.Sfx
import com.vrgames.shanhaibook.render.Sound
import com.vrgames.shanhaibook.render.TableFinder
import com.vrgames.shanhaibook.render.setPos
import com.vrgames.shanhaibook.render.shared
import com.vrgames.shanhaibook.render.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The book on the table (Stage, mixed reality): it lies open in front of the player on their real
 * table when plane detection finds one. Pinch a tab and pull it toward you, pinch a wheel and pull
 * sideways, pinch a flap or a door to open it. The page panel stands behind the book, the brass
 * "next page" button in front of it once the page is done, the menu tab at its front left.
 */
@Composable
fun BookStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val hands = remember { HandTrackingProvider() }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<BookView?>(null) }
    val place = remember { Place() }
    val drag = remember { DragState() }
    val panels = remember { HashMap<String, Entity>() }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    // One window at a time, each a toggle: opening a window that is already open starts a second copy.
    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val h = runCatching { hands.start() }
        Log.i(INPUT_TAG, "hand tracking ${h.getOrNull() ?: h.exceptionOrNull()?.message}")
        val hd = runCatching { head.start() }
        Log.i(TAG, "head tracking ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { hands.stop() }
            runCatching { head.stop() }
            TableFinder.stop()
            subscription[0]?.cancel()
            Game.closeWindow(ATLAS_ID)
            Game.closeWindow(MARKS_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    LaunchedEffect(Game.turnVersion, view) {
        if (Game.turnVersion > 0) view?.let { v -> synchronized(Game.lock) { v.startTurn() } }
    }
    LaunchedEffect(Game.jumpVersion, view) {
        if (Game.jumpVersion > 0) view?.let { v -> synchronized(Game.lock) { v.show() } }
    }
    LaunchedEffect(Game.version, view) {
        val v = view ?: return@LaunchedEffect
        if (!v.turning && drag.mech == null) synchronized(Game.lock) { v.apply(Game.book.state) }
    }
    LaunchedEffect(Game.doneVersion, view) { if (Game.doneVersion > 0) view?.celebrate() }
    LaunchedEffect(Game.guideAt, view) { view?.guide(Game.guideAt) }
    LaunchedEffect(Unit) {
        Game.interacted()
        while (true) {
            kotlinx.coroutines.delay(1000)
            Game.tickGuide()
        }
    }
    LaunchedEffect(Game.hintVersion, view) {
        val id = Game.hintAt ?: return@LaunchedEffect
        val v = view ?: return@LaunchedEffect
        // A sparkle over the part to touch, a few times.
        repeat(4) {
            v.point(id)
            kotlinx.coroutines.delay(700)
        }
    }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) place.again() }
    LaunchedEffect(Game.height, view) { place.lift(Game.height) }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(8000)
        if (Game.note == n) Game.note = null
    }
    LaunchedEffect(Game.debugView, view) {
        val v = view ?: return@LaunchedEffect
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            place.debug = true
            v.root.setPos(Vector3(d[0], d[1], d[2]))
            v.root.transform().eulerAngles = EulerAngles(d[5], d[3], 0f)
            v.root.transform().setScaleVector(Vector3(d[4]))
        } else {
            place.debug = false
            v.root.transform().setScaleVector(Vector3(1f))
            place.again()
        }
    }
    // A real table to lay the book on, once the head pose (and so the heading) is in.
    LaunchedEffect(view) {
        if (view == null) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        val (x, z) = place.lastPose ?: return@LaunchedEffect
        TableFinder.find(x, z, place.heading)?.let { found ->
            Log.i(TAG, "real table at ${found.height} m, ${found.distance} m ahead")
            place.table(found.height, found.distance)
        }
    }
    // Panels: the title card first, then the page panel and the way on.
    LaunchedEffect(Game.showTitle, Game.menuOpen, view) {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        show(PANEL_TITLE, Game.showTitle)
        show(PANEL_PAGE, !Game.showTitle)
        show(PANEL_NEXT, !Game.showTitle)
        show(PANEL_SCROLL, !Game.showTitle && Game.book.page.creature != null && Game.book.state.done && !Game.scrollDismissed)
        show(PANEL_MENU, !Game.showTitle)
        panels[PANEL_MENU]?.setPos(if (Game.menuOpen) MENU_OPEN_POS else MENU_POS)
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "atlas" -> if (Game.window != ATLAS_ID) toggleWindow(ATLAS_ID)
            "marks" -> if (Game.window != MARKS_ID) toggleWindow(MARKS_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val v = view ?: return@detectSpatialDragGesture
                    drag.last = SystemClock.uptimeMillis()
                    if (drag.mech != null) release(v, drag, "restart")
                    if (v.turning || Game.showTitle) return@detectSpatialDragGesture
                    val m = v.mechanismOf(value.targetEntity)
                    val (l, r) = pinchValues(latest(hands))
                    drag.hand = when {
                        (r ?: 0f) >= (l ?: 0f) -> if ((r ?: 0f) > PINCH_HELD) 1 else -1
                        else -> if ((l ?: 0f) > PINCH_HELD) 0 else -1
                    }
                    drag.open = 0
                    Log.i(INPUT_TAG, "start ${m?.id} target=${value.targetEntity?.getName()} pinchL=$l pinchR=$r")
                    when (m) {
                        null -> Unit
                        // A flap answers the pinch itself: open, or shut again.
                        is Mechanism.Flap -> {
                            val now = Game.book.state[m.id]
                            Game.sound?.play(Sfx.FLAP)
                            if (!Game.move(m, 1 - now)) v.shake(m)
                        }
                        else -> {
                            drag.mech = m
                            drag.value = Game.book.state[m.id]
                            drag.lastStep = Math.round(drag.value).toInt()
                            Game.sound?.play(if (m is Mechanism.Tab) Sfx.TAB else Sfx.WHEEL)
                        }
                    }
                },
                onDragEnd = { view?.let { release(it, drag, "end") } },
                onDragCancel = { view?.let { release(it, drag, "cancel") } },
            ) { value ->
                val v = view ?: return@detectSpatialDragGesture
                val m = drag.mech ?: return@detectSpatialDragGesture
                drag.last = SystemClock.uptimeMillis()
                // Compose y points down; turn the room's motion into the book's frame.
                val (dx, dz) = place.toBook(meters(value.dragAmount.x), meters(value.dragAmount.z))
                drag.value = when (m) {
                    // Toward the player pulls a tab out.
                    is Mechanism.Tab -> (drag.value + dz / BookView.TAB_TRAVEL).coerceIn(0.0, 1.0)
                    // A sideways pull turns the wheel: one wheel width of travel is about one step.
                    is Mechanism.Wheel -> drag.value - dx / (BookView.WHEEL_R * 2)
                    is Mechanism.Flap -> drag.value
                }
                // A click for each step a wheel turns past.
                if (m is Mechanism.Wheel && Math.round(drag.value).toInt() != drag.lastStep) {
                    drag.lastStep = Math.round(drag.value).toInt()
                    Game.sound?.play(Sfx.WHEEL)
                }
                v.preview(m, drag.value)
            }
        },
        attachments = {
            AttachmentPanel(PANEL_TITLE) { TitlePanel(converter) }
            AttachmentPanel(PANEL_PAGE) { PagePanel(converter) }
            AttachmentPanel(PANEL_NEXT) { NextPanel(converter) }
            AttachmentPanel(PANEL_SCROLL) { ScrollPanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments -> view?.let { attach(it, attachments, panels) } },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE8C8).toColor4(), 900f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-55f, 30f, 0f))
            })
            // The card illustrations (tools/cutout.py), with their proportions for the card sizes.
            val (cards, aspects) = withContext(Dispatchers.IO) { loadCards(context) }
            Log.i(TAG, "cards ${cards.size}")
            for (name in PANEL_FRAMES.values) runCatching { Entity.loadSuspend("asset://panels/$name.glb") }.onSuccess { frames[name] = it }
            val v = BookView(Game.book, cards, aspects)
            place.view = v
            place.put()
            content.addEntity(v.root)
            attach(v, attachments, panels)
            Game.sound = Sound(v.root).also { it.load() }
            view = v
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                if (pose != null) place.frame(pose.position, pose.rotation.rotateVector(Vector3(0f, 0f, -1f)), dt, main)
                synchronized(Game.lock) {
                    if (v.frame(dt)) main.post { Log.i(AutoPilot.TAG, "turned to ${Game.book.page.id}") }
                }
                // The fingers have opened, or the drag has gone silent: that is the release.
                if (drag.mech != null) {
                    if (SystemClock.uptimeMillis() - drag.last > STUCK_DRAG_MS) main.post { release(v, drag, "watchdog") }
                    else if (drag.hand >= 0) {
                        val p = pinchValues(latest(hands)).toList()[drag.hand]
                        drag.open = if (p != null && p < PINCH_OPEN) drag.open + 1 else 0
                        if (drag.open >= PINCH_OPEN_FRAMES) main.post { release(v, drag, "fingers-open") }
                    }
                }
            }
        },
    )
}

/** Loads every card PNG under assets/textures/cards, and each one's height over width. */
private fun loadCards(context: android.content.Context): Pair<Map<String, TextureResource>, Map<String, Float>> {
    val tex = HashMap<String, TextureResource>()
    val aspect = HashMap<String, Float>()
    val names = runCatching { context.assets.list("textures/cards") }.getOrNull().orEmpty()
    for (f in names) {
        if (!f.endsWith(".png")) continue
        val name = f.removeSuffix(".png")
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("textures/cards/$f").use { BitmapFactory.decodeStream(it, null, o) }
            if (o.outWidth > 0) aspect[name] = o.outHeight.toFloat() / o.outWidth
            tex[name] = TextureResource.load("textures/cards/$f", LoadType.FROM_ASSETS, TextureCreateOption()).shared()
        }.onFailure { Log.w(TAG, "card $f: ${it.message}") }
    }
    return tex to aspect
}

private class DragState {
    var mech: Mechanism? = null
    var value = 0.0
    var hand = -1
    var open = 0
    var last = 0L
    var lastStep = 0
}

/** Lets go of the mechanism in hand: the book takes the move or says why not, and the part settles. */
private fun release(v: BookView, d: DragState, why: String) {
    val m = d.mech ?: return
    d.mech = null
    val target = when (m) {
        is Mechanism.Wheel -> Math.round(d.value).toDouble()
        else -> d.value
    }
    val ok = Game.move(m, target)
    Log.i(INPUT_TAG, "$why ${m.id} -> $target ok=$ok")
    if (!ok) v.shake(m)
    synchronized(Game.lock) { v.apply(Game.book.state) }
}

/**
 * Where the book lies: in front of the player's heading, averaged over about a second once tracking
 * is in (the first pose alone left earlier games askew), at table height and [BookView.TABLE_Z] ahead.
 */
private class Place {
    var view: BookView? = null
    var debug = false
    private var x = 0f
    private var z = 0f
    private var facing = 0f
    private var height = BookView.TABLE_H
    private var lift = 0f
    private var distance = BookView.TABLE_Z
    private var placed = false
    private var collecting = 0.0
    private val headings = ArrayList<Float>()
    var lastPose: Pair<Float, Float>? = null

    fun again() {
        placed = false
        headings.clear()
        collecting = 0.0
    }

    fun frame(eye: Vector3, forward: Vector3, dt: Double, main: Handler) {
        lastPose = eye.x to eye.z
        if (placed || debug || hypot(forward.x, forward.z) < 0.2f) return
        headings += Math.toDegrees(atan2(-forward.x, -forward.z).toDouble()).toFloat()
        collecting += dt
        if (collecting < 1.0) return
        val s = headings.sumOf { sin(Math.toRadians(it.toDouble())) }
        val c = headings.sumOf { cos(Math.toRadians(it.toDouble())) }
        facing = Math.toDegrees(atan2(s, c)).toFloat()
        x = eye.x
        z = eye.z
        placed = true
        Log.i(TAG, "facing: ${headings.size} samples, first ${headings.first()}, mean $facing, range ${headings.min()}..${headings.max()}, eye $eye")
        main.post { put() }
    }

    fun table(h: Float, d: Float) {
        height = h
        distance = d
        put()
    }

    fun lift(dy: Float) {
        lift = dy
        put()
    }

    fun put() {
        if (debug) return
        val v = view ?: return
        val a = Math.toRadians(facing.toDouble())
        v.root.setPos(Vector3(x - distance * sin(a).toFloat(), height + lift, z - distance * cos(a).toFloat()))
        v.root.transform().eulerAngles = EulerAngles(0f, facing, 0f)
    }

    /** A hand movement in the room as (x, z) in the book's frame. */
    fun toBook(dx: Double, dz: Double): Pair<Double, Double> {
        val a = Math.toRadians(-facing.toDouble())
        return (dx * cos(a) + dz * sin(a)) to (-dx * sin(a) + dz * cos(a))
    }

    val heading get() = facing
}

/** The menu tab at the book's front left, and where the unfolded menu stands (book frame). */
private val MENU_POS = Vector3(-0.3f, 0.03f, 0.24f)
private val MENU_OPEN_POS = Vector3(-0.42f, 0.2f, 0.12f)

/** The thick scroll frames behind the panels, loaded by the stage (tools/panel3d). */
private val PANEL_FRAMES = mapOf(PANEL_TITLE to "panel_title", PANEL_PAGE to "panel_page", PANEL_SCROLL to "panel_scroll")
private val frames = HashMap<String, Entity>()

private fun attach(v: BookView, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        v.root.addChild(e)
        e.setPos(pos)
        PANEL_FRAMES[id]?.let { name -> frames[name]?.let { f -> f.setPos(Vector3(0f, 0f, -0.006f)); e.addChild(f) } }
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // The title over the closed book; the page panel behind the book, raised and leaning back a little.
    put(PANEL_TITLE, Vector3(0f, 0.28f, -0.1f), -10f)
    put(PANEL_PAGE, Vector3(0f, 0.33f, -0.27f), -12f)
    // The way on over the near edge of the right page, tipped up to face the player.
    put(PANEL_NEXT, Vector3(0.14f, 0.09f, 0.24f), -35f)
    // The scroll stands to the right of the book, turned towards the player.
    put(PANEL_SCROLL, Vector3(0.46f, 0.24f, -0.02f), -8f, -32f)
    put(PANEL_MENU, MENU_POS, -35f, 15f)
}

private fun latest(hands: HandTrackingProvider): HandTrackingData? =
    runCatching { if (hands.state == DataProvider.State.STARTED) hands.latestData else null }.getOrNull()

private fun pinchValues(h: HandTrackingData?): Pair<Float?, Float?> {
    fun v(p: com.pico.spatial.tracking.hand.HandPinch?) = p?.takeIf { it.isReady }?.pinchValue
    return v(h?.leftPinch) to v(h?.rightPinch)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private const val TAG = "BookStage"
/** Every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "BookInput"
private const val STUCK_DRAG_MS = 2000L
private const val PINCH_HELD = 0.5f
private const val PINCH_OPEN = 0.3f
private const val PINCH_OPEN_FRAMES = 3
