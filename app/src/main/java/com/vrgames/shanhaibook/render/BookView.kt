package com.vrgames.shanhaibook.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.shanhaibook.core.Book
import com.vrgames.shanhaibook.core.Mechanism
import com.vrgames.shanhaibook.core.PageState
import com.vrgames.shanhaibook.core.Pages
import com.vrgames.shanhaibook.core.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The open pop-up book on the table. Frame: the root sits on the table top under the spine, +z
 * toward the player, +x to the right. Each page is a spread of paper cards (the illustrations in
 * [cards], cut out with a white paper rim by tools/cutout.py) hinged at their foot: they stand while
 * the spread lies open and fold flat as a leaf turns over them. The mechanisms are pinchable: a tab
 * pulls out toward the player, a wheel turns with a sideways pull, a flap lifts or a door swings open.
 */
class BookView(private val book: Book, private val cards: Map<String, TextureResource>, private val aspects: Map<String, Float>) {
    val root = Entity()
    private val spreads = HashMap<Int, Spread>()
    private val leafPivot = Entity().also { root.addChild(it) }
    private var leaf: Entity? = null
    private var turn: Double? = null
    private var turnFrom = 0
    private var opening = 0.0
    private val sphere = MeshResource.createSphere(1f).shared()
    private val glitter = Sparks("glitter", 160, sphere, glow(color(1f, 0.85f, 0.45f, 1f)).shared()).also { root.addChild(it.entity) }
    private val materials = HashMap<String, Material>()
    var clock = 0.0; private set

    init {
        val board = pbr(color(0.32f, 0.12f, 0.1f), roughness = 0.7f)
        for (sx in listOf(-1f, 1f)) {
            root.addChild(model(MeshResource.createBox(Vector3(PAGE_W + 0.012f, 0.012f, PAGE_D + 0.02f), 0.003f), board).apply {
                setPos(Vector3(sx * (PAGE_W / 2 + 0.006f), 0.006f, 0f))
            })
            root.addChild(model(MeshResource.createBox(Vector3(PAGE_W, 0.01f, PAGE_D), 0f), pbr(color(0.92f, 0.88f, 0.78f), roughness = 0.95f)).apply {
                setPos(Vector3(sx * PAGE_W / 2, 0.017f, 0f))
            })
        }
        leafPivot.setPos(Vector3(0f, PAPER_Y + 0.002f, 0f))
        leafPivot.enabled = false
        show()
    }

    private fun spread(i: Int) = spreads.getOrPut(i) { Spread(i).also { root.addChild(it.root) } }

    val turning get() = turn != null

    /** Starts the leaf over to the next page (the book has already moved its index on). */
    fun startTurn() {
        turnFrom = book.index - 1
        turn = 0.0
        leaf?.let { leafPivot.removeChild(it) }
        leaf = model(MeshResource.createBox(Vector3(PAGE_W, 0.002f, PAGE_D), 0f), paper(turnFrom)).apply { setPos(Vector3(PAGE_W / 2, 0f, 0f)) }
            .also { leafPivot.addChild(it) }
        leafPivot.transform().eulerAngles = EulerAngles(0f, 0f, 0f)
        leafPivot.enabled = true
        spread(book.index).apply(book.state, instant = true)
    }

    /** Jumps to the book's page with no turning (restart, page select). */
    fun show() {
        turn = null
        leafPivot.enabled = false
        for ((i, s) in spreads) if (i != book.index) s.set(0.0, left = false, right = false)
        spread(book.index).let {
            it.set(1.0, left = true, right = true)
            it.apply(book.state, instant = true)
        }
    }

    /** Per frame; true when a turn has just finished. */
    fun frame(dt: Double): Boolean {
        clock += dt
        glitter.update(dt, drag = 0.8)
        spreads[book.index]?.frame(dt)
        if (opening < 1) {
            opening = (opening + dt / OPEN_S).coerceAtMost(1.0)
            if (turn == null) spread(book.index).set(ease(opening), left = true, right = true)
        }
        val t = turn ?: return false
        val n = (t + dt / TURN_S).coerceAtMost(1.0)
        turn = n
        val e = ease(n)
        leafPivot.transform().eulerAngles = EulerAngles(0f, 0f, (180 * e).toFloat())
        // The old spread folds as the leaf lifts off it; the new one rises as the leaf lays it bare.
        spread(turnFrom).set((1 - e * 1.6).coerceAtLeast(0.0), left = true, right = false)
        spread(turnFrom + 1).set(((e - 0.35) / 0.65).coerceIn(0.0, 1.0), left = false, right = true)
        if (n >= 1) {
            turn = null
            leafPivot.enabled = false
            show()
            return true
        }
        return false
    }

    fun mechanismOf(e: Entity?): Mechanism? {
        var cur = e
        val s = spread(book.index)
        while (cur != null) {
            s.hits[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    /** Shows [m] at [value] while the hand moves it (a tab between stops, a wheel between steps). */
    fun preview(m: Mechanism, value: Double) = spread(book.index).preview(m, value)

    /** Shows the page as its state now stands (after a move is accepted or refused). */
    fun apply(state: PageState) = spread(book.index).apply(state, instant = false)

    fun shake(m: Mechanism) = spread(book.index).shake(m)

    /** The page is done: its creature comes back with a little hop and a shower of gold. */
    fun celebrate() = spread(book.index).celebrate()

    /** Where a mechanism stands (book frame), for the sound and the hint sparkle. */
    fun spotOf(id: String): Vec3? = spread(book.index).parts[id]?.let { e ->
        val p = e.convertPositionTo(Vector3(0f, 0f, 0f), root)
        Vec3(p.x.toDouble(), p.y.toDouble() + 0.02, p.z.toDouble())
    }

    /** A little sparkle over [id], the hint. */
    fun point(id: String) {
        spotOf(id)?.let { glitter.emit(it, 18, 0.12, 0.9, 0.004f) }
    }

    /** The card's size: the illustration fitted inside [w] x [h] at its own proportions (as given while the art is missing). */
    private fun fit(name: String, w: Float, h0: Float): Pair<Float, Float> {
        val a = aspects[name] ?: return w to h0
        // Pictures may stand taller than the flat placeholders did: a pop-up card is meant to rise.
        val h = h0 * TALLER
        return if (a > h / w) (h / a) to h else w to (w * a)
    }

    /** A card's paper: the cut-out illustration (alpha-masked), or a flat tint while the art is missing. */
    private fun art(name: String, fallback: Color4): Material = materials.getOrPut("$name/$fallback") {
        val tex = cards[name]
        if (tex == null) pbr(fallback, roughness = 0.95f) else PhysicallyBasedMaterial.create(BlendingMode.MASKED).apply {
            setBaseColorTexture(tex)
            setBaseColor(color(1f, 1f, 1f))
            // Without a threshold the masked mode drew the cut-away ground as a grey card.
            setOpacityThreshold(0.5f)
            setRoughness(0.9f)
            setMetallic(0f)
        }.shared()
    }

    // ---- One spread ------------------------------------------------------------------------------

    private inner class Spread(val index: Int) {
        val root = Entity()
        val leftPaper = model(MeshResource.createBox(Vector3(PAGE_W, 0.002f, PAGE_D), 0f), paper(index)).apply { setPos(Vector3(-PAGE_W / 2, PAPER_Y, 0f)) }
        val rightPaper = model(MeshResource.createBox(Vector3(PAGE_W, 0.002f, PAGE_D), 0f), paper(index)).apply { setPos(Vector3(PAGE_W / 2, PAPER_Y, 0f)) }
        /** Hinged things and how far each stands up when the spread is open (1 = upright). */
        val cards = ArrayList<Pair<Entity, Double>>()
        /** Loose cut-outs (they move on their own): shown only with the spread wide open. */
        val loose = ArrayList<Entity>()
        val hits = HashMap<Entity, Mechanism>()
        val parts = HashMap<String, Entity>()
        val shows = HashMap<String, (Double) -> Unit>()
        /** Values on show per mechanism, gliding to [target] so accepted moves do not jump. */
        private val shown = HashMap<String, Double>()
        private val target = HashMap<String, Double>()
        private var shaking: Pair<String, Double>? = null
        private val baseX = HashMap<String, Float>()
        /** Per-frame animations of this page (a dancing 帝江, tails flying home, the finale). */
        val anims = ArrayList<(Double) -> Unit>()
        private var hop = -1.0
        private var creature: Entity? = null
        private var creatureY = 0f
        private val page = book.pages[index]

        init {
            root.addChild(leftPaper)
            root.addChild(rightPaper)
            when (page.id) {
                "study" -> buildStudy()
                "sea" -> buildSea()
                "fox" -> buildFox()
                "gate" -> buildGate()
                "sun" -> buildSun()
                "dragon" -> buildDragon()
                "song" -> buildSong()
                "end" -> buildEnd()
            }
        }

        fun set(open: Double, left: Boolean, right: Boolean) {
            root.enabled = open > 0.001 || left || right
            leftPaper.enabled = left || open > 0.001
            rightPaper.enabled = right || open > 0.001
            for ((pivot, stand) in cards) {
                pivot.enabled = open > 0.02
                // Folded flat lies toward the back of the page (-z); upright is 0.
                pivot.transform().eulerAngles = EulerAngles((-90 * (1 - open * stand)).toFloat(), 0f, 0f)
            }
            for (e in loose) e.enabled = open > 0.85
            for (e in hits.keys) e.enabled = open > 0.9
        }

        fun preview(m: Mechanism, v: Double) {
            shown[m.id] = v
            target[m.id] = v
            shows[m.id]?.invoke(v)
        }

        fun apply(state: PageState, instant: Boolean) {
            for (m in page.mechanisms) {
                target[m.id] = state[m.id]
                if (instant || m.id !in shown) {
                    shown[m.id] = state[m.id]
                    shows[m.id]?.invoke(state[m.id])
                }
            }
        }

        fun frame(dt: Double) {
            for ((id, t) in target) {
                val s = shown[id] ?: t
                if (s == t) continue
                val n = if (abs(s - t) < 1e-3) t else s + (t - s) * (dt / 0.12).coerceAtMost(1.0)
                shown[id] = n
                shows[id]?.invoke(n)
            }
            shaking?.let { (id, t) ->
                val n = t + dt
                parts[id]?.let { e ->
                    val p = e.transform().position
                    val off = if (n < SHAKE_S) (sin(n * 60) * 0.004 * (1 - n / SHAKE_S)).toFloat() else 0f
                    e.setPos(Vector3(baseX[id]!! + off, p.y, p.z))
                }
                shaking = if (n < SHAKE_S) id to n else null
            }
            if (hop >= 0) {
                hop += dt
                creature?.let { c ->
                    val y = if (hop < 0.6) (sin(hop / 0.6 * PI) * 0.03).toFloat() else 0f
                    val p = c.transform().position
                    c.setPos(Vector3(p.x, creatureY + y, p.z))
                }
                if (hop > 0.6) hop = -1.0
            }
            for (a in anims) a(dt)
        }

        fun shake(m: Mechanism) {
            shaking = m.id to 0.0
        }

        fun celebrate() {
            val c = creature ?: return
            if (hop < 0) creatureY = c.transform().position.y
            hop = 0.0
            val p = c.convertPositionTo(Vector3(0f, 0.06f, 0f), this@BookView.root)
            glitter.emit(Vec3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()), 50, 0.25, 1.4, 0.005f)
        }

        // ---- Building blocks ----

        /** A hinge on the page at ([x], [z]) that stands its children up when the spread opens. */
        fun stand(x: Float, z: Float, y: Float = PAPER_Y, standUp: Double = 1.0): Entity {
            val pivot = Entity().apply { setPos(Vector3(x, y, z)) }
            root.addChild(pivot)
            cards += pivot to standUp
            return pivot
        }

        /** A card standing on the page, foot at ([x], [z]), [w] wide and [h] tall, showing illustration [name]. */
        fun card(name: String, x: Float, z: Float, w0: Float, h0: Float, tint: Color4, y: Float = PAPER_Y): Entity {
            val (w, h) = fit(name, w0, h0)
            return stand(x, z, y).also { it.addChild(model(MeshResource.createBox(Vector3(w, h, CARD_T), 0f), art(name, tint)).apply { setPos(Vector3(0f, h / 2, 0f)) }) }
        }

        /** A loose cut-out that is not hinged to the page (it moves: a bird, a tail, a crow). */
        fun cutout(name: String, w0: Float, h0: Float, tint: Color4): Entity = Entity().apply {
            val (w, h) = fit(name, w0, h0)
            addChild(model(MeshResource.createBox(Vector3(w, h, CARD_T), 0f), art(name, tint)).apply { setPos(Vector3(0f, h / 2, 0f)) })
            root.addChild(this)
            loose += this
        }

        fun pinchable(e: Entity, m: Mechanism, size: Vector3, center: Vector3 = Vector3(0f, 0f, 0f)) {
            // Thin paper is hard to pinch: the collider is never smaller than MIN_HIT on any side.
            val s = Vector3(maxOf(size.x, MIN_HIT), maxOf(size.y, MIN_HIT), maxOf(size.z, MIN_HIT))
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(s).offsetByTranslation(center)), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            e.components.set(HoverEffectComponent())
            hits[e] = m
            parts[m.id] = e
            baseX[m.id] = e.transform().position.x
        }

        /** A pull tab from the page's front edge at [x]; [onShow] gets 0 (in) .. 1 (out). */
        fun tab(id: String, x: Float, onShow: (Double) -> Unit) {
            val e = Entity().apply { setPos(Vector3(x, PAPER_Y + 0.003f, PAGE_D / 2 - 0.01f)) }
            e.addChild(model(MeshResource.createBox(Vector3(0.026f, 0.003f, 0.06f), 0.002f), pbr(color(0.82f, 0.22f, 0.16f))))
            // A paper pull-ring at its end, so it reads as something to pull.
            e.addChild(model(MeshResource.createTorus(0.012f, 0.008f), pbr(color(0.9f, 0.75f, 0.35f))).apply { setPos(Vector3(0f, 0f, 0.035f)) })
            root.addChild(e)
            pinchable(e, page.mech(id), Vector3(0.045f, 0.03f, 0.09f), Vector3(0f, 0f, 0.015f))
            shows[id] = { v ->
                e.setPos(Vector3(baseX[id] ?: x, PAPER_Y + 0.003f, (PAGE_D / 2 - 0.01f + TAB_TRAVEL * v).toFloat()))
                onShow(v)
            }
        }

        /** A paper wheel lying in the page at ([x], [z]); [onShow] gets the step (fractional while turned). */
        fun wheel(id: String, x: Float, z: Float, onShow: (Double) -> Unit) {
            val m = page.mech(id) as Mechanism.Wheel
            val e = Entity().apply { setPos(Vector3(x, PAPER_Y + 0.003f, z)) }
            e.addChild(model(MeshResource.createCylinder(0.004f, WHEEL_R), pbr(color(0.78f, 0.62f, 0.32f), roughness = 0.8f)))
            for (k in 0 until m.steps) {
                val a = 2 * PI * k / m.steps
                e.addChild(model(MeshResource.createBox(Vector3(0.005f, 0.003f, 0.01f), 0f), pbr(if (k == 0) color(0.75f, 0.15f, 0.1f) else color(0.3f, 0.18f, 0.1f))).apply {
                    setPos(Vector3((sin(a) * WHEEL_R * 0.8).toFloat(), 0.003f, (cos(a) * WHEEL_R * 0.8).toFloat()))
                    transform().eulerAngles = EulerAngles(0f, Math.toDegrees(a).toFloat(), 0f)
                })
            }
            root.addChild(e)
            // The pointer the marks turn past.
            root.addChild(model(MeshResource.createCone(0.014f, 0.006f), pbr(color(0.85f, 0.2f, 0.15f))).apply {
                setPos(Vector3(x, PAPER_Y + 0.006f, z + WHEEL_R + 0.01f))
                transform().eulerAngles = EulerAngles(-90f, 0f, 0f)
            })
            pinchable(e, m, Vector3(WHEEL_R * 2, 0.03f, WHEEL_R * 2))
            shows[id] = { v ->
                e.transform().eulerAngles = EulerAngles(0f, (-360.0 * v / m.steps).toFloat(), 0f)
                onShow(v)
            }
        }

        /** A flap lying on the page, hinged at its back edge, lifting up; [onShow] gets 0 (shut) .. 1 (open). */
        fun flap(id: String, art: String, x: Float, z: Float, w0: Float, d0: Float, tint: Color4, onShow: (Double) -> Unit = {}) {
            val (w, d) = fit(art, w0, d0)
            val hinge = Entity().apply { setPos(Vector3(x, PAPER_Y + 0.006f, z - d / 2)) }
            val e = Entity()
            e.addChild(model(MeshResource.createBox(Vector3(w, d, CARD_T), 0f), art(art, tint)).apply {
                // The illustration faces up while the flap lies shut.
                transform().eulerAngles = EulerAngles(-90f, 0f, 0f)
                setPos(Vector3(0f, 0f, d / 2))
            })
            hinge.addChild(e)
            root.addChild(hinge)
            pinchable(e, page.mech(id), Vector3(w, 0.02f, d), Vector3(0f, 0f, d / 2))
            shows[id] = { v ->
                hinge.transform().eulerAngles = EulerAngles((-110 * v).toFloat(), 0f, 0f)
                onShow(v)
            }
        }

        /** A standing card that swings open like a door about its left edge; behind it something waits. */
        fun door(id: String, art: String, x: Float, z: Float, w0: Float, h0: Float, tint: Color4, onShow: (Double) -> Unit = {}) {
            val (w, h) = fit(art, w0, h0)
            val pivot = stand(x - w / 2, z)
            val e = Entity()
            e.addChild(model(MeshResource.createBox(Vector3(w, h, CARD_T), 0f), art(art, tint)).apply { setPos(Vector3(w / 2, h / 2, 0f)) })
            pivot.addChild(e)
            pinchable(e, page.mech(id), Vector3(w, h, 0.02f), Vector3(w / 2, h / 2, 0f))
            shows[id] = { v ->
                e.transform().eulerAngles = EulerAngles(0f, (100 * v).toFloat(), 0f)
                onShow(v)
            }
        }

        /** A small red seal lying under a flap. */
        fun seal(x: Float, z: Float) {
            stand(x, z, PAPER_Y + 0.001f, standUp = 0.0).addChild(
                // Centered on its hinge, so lying flat it stays under the flap that hides it.
                model(MeshResource.createBox(Vector3(0.024f, 0.024f, CARD_T), 0f), art("seal", color(0.8f, 0.15f, 0.12f))),
            )
        }

        private fun creatureIs(e: Entity) {
            creature = e
            creatureY = e.transform().position.y
        }

        // ---- The pages ----

        private fun buildStudy() {
            card("study_shelf", -0.14f, -0.14f, 0.14f, 0.2f, color(0.6f, 0.42f, 0.28f))
            card("study_window", 0.11f, -0.15f, 0.14f, 0.14f, color(0.75f, 0.85f, 0.95f), y = PAPER_Y + 0.03f)
            val curtain = card("study_curtain", 0.11f, -0.13f, 0.13f, 0.15f, color(0.7f, 0.2f, 0.18f), y = PAPER_Y + 0.025f)
            card("study_desk", 0.02f, -0.03f, 0.2f, 0.09f, color(0.42f, 0.22f, 0.15f))
            creatureIs(card("study_painter", -0.13f, 0.02f, 0.07f, 0.12f, color(0.4f, 0.6f, 0.55f)))
            val lamp = card("study_lamp", -0.04f, 0.0f, 0.04f, 0.07f, color(0.55f, 0.45f, 0.2f))
            val flame = model(sphere, glow(color(1f, 0.8f, 0.4f, 1f))).apply { setScale(0.001f); setPos(Vector3(0f, 0.075f, 0f)) }
            lamp.addChild(flame)
            seal(0.08f, 0.1f)
            tab("curtain", 0.2f) { v -> curtain.setPos(Vector3((0.11f + 0.12f * v).toFloat(), PAPER_Y + 0.025f, -0.13f)) }
            wheel("lamp", -0.2f, 0.12f) { v -> flame.setScale((0.003 + 0.007 * v).toFloat()) }
            flap("inkstone", "study_inkstone", 0.08f, 0.1f, 0.07f, 0.06f, color(0.2f, 0.2f, 0.22f))
        }

        private fun buildSea() {
            card("sea_back", 0f, -0.17f, 0.52f, 0.16f, color(0.55f, 0.66f, 0.75f))
            card("sea_reef", 0.14f, -0.07f, 0.09f, 0.06f, color(0.4f, 0.38f, 0.35f))
            val stone = model(sphere, pbr(color(0.55f, 0.52f, 0.48f))).apply { setScale(0.007f); setPos(Vector3(-0.12f, PAPER_Y + 0.006f, 0.03f)) }
            root.addChild(stone)
            val waves = card("sea_waves", 0f, 0.05f, 0.5f, 0.08f, color(0.25f, 0.5f, 0.75f))
            val bird = cutout("sea_bird", 0.07f, 0.05f, color(0.95f, 0.95f, 0.95f))
            creature = bird
            wheel("tide", -0.22f, 0.13f) { v ->
                // The tide goes out: the near waves sink toward the page.
                waves.transform().setScaleVector(Vector3(1f, (1 - 0.32 * v).toFloat(), 1f))
            }
            tab("bird", 0.21f) { v ->
                val p = Vector3((-0.18 + 0.32 * v).toFloat(), (PAPER_Y + 0.08 - 0.02 * v).toFloat(), (0.02 - 0.09 * v).toFloat())
                bird.setPos(p)
                creatureY = p.y
                stone.setPos(if (v > 0.95) Vector3(0.14f, PAPER_Y + 0.062f, -0.07f) else Vector3(-0.12f, PAPER_Y + 0.006f, 0.03f))
            }
            seal(0.05f, 0.0f)
            flap("rock", "sea_rock", 0.05f, 0.0f, 0.06f, 0.05f, color(0.45f, 0.42f, 0.38f))
        }

        private fun buildFox() {
            card("fox_back", 0f, -0.17f, 0.52f, 0.15f, color(0.45f, 0.6f, 0.45f))
            creatureIs(card("fox_body", 0.0f, -0.08f, 0.09f, 0.1f, color(0.95f, 0.95f, 0.92f)))
            // Nine tails, each behind its hiding place; found, they fly to the fox and fan out behind it.
            val homes = listOf(
                "tree" to Vector3(-0.19f, PAPER_Y, -0.02f), "tree" to Vector3(-0.17f, PAPER_Y, -0.02f),
                "rock" to Vector3(-0.07f, PAPER_Y, 0.05f),
                "bush" to Vector3(0.1f, PAPER_Y, 0.02f), "bush" to Vector3(0.12f, PAPER_Y, 0.02f),
                "grass" to Vector3(0.18f, PAPER_Y, 0.1f), "grass" to Vector3(0.2f, PAPER_Y, 0.1f),
                "cloud" to Vector3(0.18f, PAPER_Y + 0.1f, -0.12f), "cloud" to Vector3(0.21f, PAPER_Y + 0.1f, -0.12f),
            )
            val tails = homes.map { (_, at) -> cutout("fox_tail", 0.035f, 0.05f, color(0.97f, 0.97f, 0.95f)).apply { setPos(at) } }
            val flying = HashMap<Int, Double>()
            fun fly(key: String, on: Boolean) {
                if (on) for (i in homes.indices) if (homes[i].first == key && i !in flying) flying[i] = 0.0
            }
            anims += { dt ->
                for ((i, t0) in flying.toMap()) {
                    if (t0 >= 1) continue
                    val t = (t0 + dt / 1.2).coerceAtMost(1.0)
                    flying[i] = t
                    val from = homes[i].second
                    // A fan behind the fox: nine tails spread from left to right.
                    val a = (i - 4) * 0.35
                    val to = Vector3((sin(a) * 0.05).toFloat(), (PAPER_Y + 0.03 + cos(a) * 0.03).toFloat(), -0.09f)
                    val e = ease(t).toFloat()
                    val arc = (sin(t * PI) * 0.06).toFloat()
                    tails[i].setPos(Vector3(from.x + (to.x - from.x) * e, from.y + (to.y - from.y) * e + arc, from.z + (to.z - from.z) * e))
                    tails[i].transform().eulerAngles = EulerAngles(0f, 0f, (Math.toDegrees(-a) * e).toFloat())
                }
            }
            door("tree", "fox_tree", -0.18f, 0.0f, 0.08f, 0.13f, color(0.3f, 0.45f, 0.3f)) { v -> fly("tree", v > 0.5) }
            door("rock", "fox_rock", -0.07f, 0.065f, 0.07f, 0.06f, color(0.5f, 0.5f, 0.48f)) { v -> fly("rock", v > 0.5) }
            door("bush", "fox_bush", 0.11f, 0.035f, 0.08f, 0.07f, color(0.35f, 0.55f, 0.3f)) { v -> fly("bush", v > 0.5) }
            val grass = card("fox_grass", 0.19f, 0.115f, 0.08f, 0.07f, color(0.4f, 0.65f, 0.35f))
            tab("grass", 0.24f) { v ->
                grass.setPos(Vector3((0.19 + 0.07 * v).toFloat(), PAPER_Y, 0.115f))
                fly("grass", v > 0.9)
            }
            val cloud = card("fox_cloud", 0.195f, -0.115f, 0.1f, 0.06f, color(0.95f, 0.95f, 0.97f), y = PAPER_Y + 0.09f)
            wheel("cloud", -0.22f, 0.13f) { v ->
                // The cloud drifts sideways step by step; at the third step what was behind it shows.
                cloud.setPos(Vector3((0.195 - 0.05 * v).toFloat(), PAPER_Y + 0.09f, -0.115f))
                fly("cloud", Math.round(v).toInt() == 2)
            }
            seal(0.0f, 0.12f)
            flap("moss", "fox_rock", 0.0f, 0.12f, 0.05f, 0.04f, color(0.4f, 0.5f, 0.38f))
        }

        private fun buildGate() {
            card("gate_back", 0f, -0.17f, 0.52f, 0.16f, color(0.8f, 0.85f, 0.88f))
            val doorL = card("gate_door", -0.03f, -0.12f, 0.06f, 0.12f, color(0.75f, 0.15f, 0.12f))
            val doorR = card("gate_door", 0.03f, -0.12f, 0.06f, 0.12f, color(0.75f, 0.15f, 0.12f))
            creatureIs(card("gate_beast", -0.15f, -0.04f, 0.12f, 0.1f, color(0.9f, 0.7f, 0.3f)))
            card("gate_chart", 0.18f, -0.08f, 0.1f, 0.1f, color(0.85f, 0.8f, 0.65f))
            // The chart's arrows on the page before it, one per head group (north points away from the player).
            for ((k, dir) in Pages.HEADS.values.withIndex()) {
                root.addChild(model(MeshResource.createCone(0.02f, 0.006f), pbr(color(0.8f, 0.2f, 0.15f))).apply {
                    setPos(Vector3(0.155f + k * 0.025f, PAPER_Y + 0.004f, -0.01f))
                    transform().eulerAngles = EulerAngles(-90f, -90f * dir, 0f)
                })
            }
            val heads = (1..3).map { k -> card("gate_heads", -0.2f + k * 0.06f, 0.06f, 0.05f, 0.035f, color(0.9f, 0.75f, 0.4f)) }
            fun open() {
                val s = book.states[index]
                val a = if (Pages.HEADS.all { (k, v) -> s.step(k) == v }) 80f else 0f
                doorL.transform().eulerAngles = EulerAngles(0f, a, 0f)
                doorR.transform().eulerAngles = EulerAngles(0f, -a, 0f)
            }
            for ((i, h) in heads.withIndex()) {
                wheel("heads${i + 1}", -0.14f + i * 0.06f, 0.13f) { v ->
                    // Facing north (0) the heads look away from the player; each step turns them a quarter right.
                    h.transform().eulerAngles = EulerAngles(0f, (180 - 90 * v).toFloat(), 0f)
                    open()
                }
            }
            seal(0.2f, 0.1f)
            flap("stone", "gate_stone", 0.2f, 0.1f, 0.05f, 0.06f, color(0.6f, 0.6f, 0.58f))
        }

        private fun buildSun() {
            card("sun_back", 0f, -0.17f, 0.52f, 0.15f, color(0.95f, 0.75f, 0.55f))
            card("sun_tree", -0.06f, -0.1f, 0.2f, 0.18f, color(0.5f, 0.4f, 0.25f))
            card("sun_calendar", 0.17f, -0.05f, 0.08f, 0.08f, color(0.9f, 0.85f, 0.7f))
            // Ten crows on the branches; the chosen one grows, today's flies up as the sun.
            fun perch(i: Int): Vector3 {
                val a = i / 9.0
                return Vector3((-0.15 + 0.18 * a).toFloat(), (PAPER_Y + 0.08 + 0.05 * sin(a * PI)).toFloat(), -0.085f)
            }
            val crows = (0 until 10).map { i -> cutout("sun_crow", 0.03f, 0.03f, color(0.95f, 0.75f, 0.2f)).apply { setPos(perch(i)) } }
            creature = crows[Pages.TODAY]
            val sun = model(sphere, glow(color(1f, 0.7f, 0.3f, 1f))).apply { setScale(0.001f) }
            root.addChild(sun)
            var chosen = 0
            wheel("crow", -0.22f, 0.13f) { v ->
                chosen = ((Math.round(v).toInt() % 10) + 10) % 10
                for ((i, c) in crows.withIndex()) c.transform().setScaleVector(Vector3(if (i == chosen) 1.6f else 1f))
            }
            tab("go", 0.21f) { v ->
                val from = perch(chosen)
                val p = Vector3(from.x + (0.1f - from.x) * v.toFloat(), from.y + 0.12f * v.toFloat(), from.z)
                crows[chosen].setPos(p)
                creatureY = p.y
                sun.setPos(Vector3(p.x, p.y + 0.04f, p.z))
                sun.setScale((0.018 * v).toFloat().coerceAtLeast(0.001f))
            }
            seal(0.06f, 0.06f)
            flap("hollow", "sun_hollow", 0.06f, 0.06f, 0.05f, 0.05f, color(0.35f, 0.25f, 0.15f))
        }

        private fun buildDragon() {
            card("dragon_back", 0f, -0.17f, 0.52f, 0.15f, color(0.4f, 0.45f, 0.55f))
            val night = card("night", 0f, -0.165f, 0.52f, 0.15f, color(0.05f, 0.06f, 0.15f))
            creatureIs(card("dragon_body", -0.02f, -0.1f, 0.16f, 0.12f, color(0.8f, 0.2f, 0.15f)))
            val eyes = card("dragon_eyes", -0.02f, -0.095f, 0.05f, 0.02f, color(1f, 0.85f, 0.3f), y = PAPER_Y + 0.07f)
            card("dragon_deer", -0.17f, 0.03f, 0.05f, 0.06f, color(0.75f, 0.55f, 0.35f))
            card("dragon_owl", 0.16f, 0.03f, 0.04f, 0.05f, color(0.55f, 0.45f, 0.35f))
            tab("eyes", 0.22f) { v ->
                eyes.transform().setScaleVector(Vector3(1f, v.toFloat().coerceAtLeast(0.05f), 1f))
                // Eyes shut, night falls over the back of the page.
                night.transform().setScaleVector(Vector3(1f, (1 - v).toFloat().coerceAtLeast(0.01f), 1f))
            }
            door("cave", "dragon_cave", -0.12f, -0.03f, 0.07f, 0.07f, color(0.4f, 0.35f, 0.3f))
            door("nest", "dragon_nest", 0.13f, -0.03f, 0.06f, 0.05f, color(0.5f, 0.38f, 0.25f))
            seal(0.05f, 0.11f)
            flap("boulder", "fox_rock", 0.05f, 0.11f, 0.05f, 0.04f, color(0.5f, 0.48f, 0.45f))
        }

        private fun buildSong() {
            card("song_back", 0f, -0.17f, 0.52f, 0.15f, color(0.6f, 0.75f, 0.85f))
            card("song_score", 0.18f, -0.08f, 0.08f, 0.1f, color(0.9f, 0.85f, 0.7f))
            val dj = card("song_dijiang", -0.02f, -0.07f, 0.11f, 0.1f, color(0.9f, 0.25f, 0.15f))
            creatureIs(dj)
            for ((id, x) in listOf("drum" to -0.18f, "bell" to -0.07f, "flute" to 0.04f, "gong" to 0.15f)) {
                val e = card("song_$id", x, 0.07f, 0.06f, 0.06f, color(0.8f, 0.5f, 0.2f))
                pinchable(e, page.mech(id), Vector3(0.06f, 0.06f, 0.02f), Vector3(0f, 0.03f, 0f))
                // Played, an instrument tips back a little and stays so: one can see how far the tune has got.
                shows[id] = { v -> e.getChildren().firstOrNull()?.transform()?.eulerAngles = EulerAngles((-25 * v).toFloat(), 0f, 0f) }
            }
            // Once the four notes are played, 帝江 dances: a bounce and a sway.
            anims += { _ ->
                val s = book.states[index]
                if (Pages.SCORE.all { s.open(it) }) {
                    val t = clock
                    dj.getChildren().firstOrNull()?.let { c ->
                        c.transform().eulerAngles = EulerAngles(0f, (sin(t * 3) * 25).toFloat(), (sin(t * 6) * 8).toFloat())
                        c.setPos(Vector3(0f, (0.05 + abs(sin(t * 6)) * 0.015).toFloat(), 0f))
                    }
                }
            }
            seal(0.05f, 0.14f)
            flap("mat", "song_score", 0.05f, 0.14f, 0.05f, 0.04f, color(0.8f, 0.3f, 0.25f))
        }

        /** The last page: every creature hops out of the book onto the table, circles it, and hops back. */
        private fun buildEnd() {
            val who = listOf("sea_bird", "fox_body", "gate_beast", "sun_crow", "dragon_body", "song_dijiang")
            val figures = who.mapIndexed { i, n ->
                val home = Vector3(-0.18f + i * 0.072f, PAPER_Y, -0.02f)
                cutout(n, 0.065f, 0.065f, color(0.9f, 0.6f, 0.3f)).apply { setPos(home) } to home
            }
            anims += { _ ->
                val t = clock % 14.0
                for ((i, pair) in figures.withIndex()) {
                    val (e, home) = pair
                    // Out at 1-3 s, around the book 3-10 s, back home 10-12 s.
                    val a = 2 * PI * i / figures.size + (t - 3).coerceIn(0.0, 7.0) * 0.45
                    val ring = Vector3((sin(a) * 0.42).toFloat(), 0f, (cos(a) * 0.3).toFloat())
                    val k = when {
                        t < 1 -> 0.0
                        t < 3 -> (t - 1) / 2
                        t < 10 -> 1.0
                        t < 12 -> 1 - (t - 10) / 2
                        else -> 0.0
                    }
                    val jump = if (t in 1.0..3.0 || t in 10.0..12.0) sin(((t - 1) % 2) / 2 * PI) * 0.12 else 0.0
                    val hopY = (jump + abs(sin(t * 5 + i)) * 0.012 * k).toFloat()
                    val e2 = ease(k).toFloat()
                    e.setPos(Vector3(home.x + (ring.x - home.x) * e2, home.y + (ring.y - home.y) * e2 + hopY, home.z + (ring.z - home.z) * e2))
                    e.transform().eulerAngles = EulerAngles(0f, if (k > 0) Math.toDegrees(a).toFloat() + 90f else 0f, 0f)
                }
            }
        }
    }

    companion object {
        /** Book middle, this far in front of the player: nearer felt in the lap on the device (see the lessons list). */
        const val TABLE_Z = 0.72f
        /** Table height when no real table is found (meters above the floor). */
        const val TABLE_H = 0.75f
        const val PAGE_W = 0.28f
        const val PAGE_D = 0.38f
        const val PAPER_Y = 0.023f
        const val CARD_T = 0.0015f
        const val TAB_TRAVEL = 0.05
        const val WHEEL_R = 0.04f
        const val MIN_HIT = 0.03f
        const val TURN_S = 1.2
        const val OPEN_S = 1.6
        const val SHAKE_S = 0.4
        /** How much taller than its placeholder box a picture card may stand. */
        const val TALLER = 1.6f

        private val paperMats = HashMap<Int, Material>()

        /** Each page's paper a slightly different old cream, so a turn reads. */
        fun paper(i: Int): Material = paperMats.getOrPut(i) {
            val k = (i % 3) * 0.025f
            pbr(color(0.95f - k, 0.91f - k, 0.8f - k), roughness = 0.95f).shared()
        }

        fun ease(t: Double) = t * t * (3 - 2 * t)
    }
}
