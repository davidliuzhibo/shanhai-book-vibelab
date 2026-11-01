package com.vrgames.shanhaibook.core

/**
 * The rules of the pop-up book, free of rendering: pages, the paper mechanisms on them (pull tabs,
 * wheels, flaps) and when a page is done. A page's [Page.check] says what is still wrong, in words
 * the player sees, so a refused move is never silent.
 */
sealed interface Mechanism {
    val id: String
    val label: String

    /** A strip pulled out of a slot; it rests at one of [stops] (0 in .. 1 fully out). */
    data class Tab(override val id: String, override val label: String, val stops: List<Double> = listOf(0.0, 1.0)) : Mechanism

    /** A paper wheel with [steps] marked positions. */
    data class Wheel(override val id: String, override val label: String, val steps: Int) : Mechanism

    /** A paper flap that opens and closes. */
    data class Flap(override val id: String, override val label: String) : Mechanism
}

/** A creature of the 山海经 that comes back into its picture: the classic's words, plain words, one fact. */
data class Creature(val id: String, val name: String, val source: String, val original: String, val plain: String, val fact: String)

class Page(
    val id: String,
    val title: String,
    /** What the player is asked to do, shown with the page. */
    val task: String,
    val mechanisms: List<Mechanism>,
    /** Null when the page is done; otherwise what is still missing. */
    val check: (PageState) -> String?,
    /** May [m] move to [to] now? Null if so; otherwise why not. */
    val allow: (PageState, Mechanism, Double) -> String? = { _, _, _ -> null },
    /** What a move just accepted shows up (a tail behind a cloud, an empty hiding place). */
    val say: (PageState, Mechanism) -> String? = { _, _ -> null },
    /** The creature this page brings back, if any. */
    val creature: Creature? = null,
    /** The flap that hides this page's red seal, if any. */
    val seal: String? = null,
    /** Step-by-step hints, first one not yet done shown first (see [hint]). */
    val hints: List<Pair<(PageState) -> Boolean, String>> = emptyList(),
) {
    fun mech(id: String) = mechanisms.first { it.id == id }

    /** The first hint whose step is not yet done, or null. */
    fun hint(s: PageState) = hints.firstOrNull { !it.first(s) }?.second
}

/** Where every mechanism of a page stands: tab stop (0..1), wheel step, flap open (1) or shut (0). */
class PageState(val page: Page) {
    private val values = HashMap<String, Double>()

    operator fun get(id: String) = values[id] ?: 0.0
    fun open(id: String) = get(id) >= 0.5
    fun step(id: String) = get(id).toInt()

    /** Sets [m] to [to] if the page allows it; returns the reason when it does not. */
    fun set(m: Mechanism, to: Double): String? {
        val v = when (m) {
            is Mechanism.Tab -> m.stops.minBy { kotlin.math.abs(it - to) }
            is Mechanism.Wheel -> (((Math.round(to).toInt() % m.steps) + m.steps) % m.steps).toDouble()
            is Mechanism.Flap -> if (to >= 0.5) 1.0 else 0.0
        }
        page.allow(this, m, v)?.let { return it }
        values[m.id] = v
        return null
    }

    fun reset() = values.clear()

    val done get() = page.check(this) == null
}

class Book(val pages: List<Page>) {
    var index = 0; private set
    val states = pages.map { PageState(it) }
    val page get() = pages[index]
    val state get() = states[index]
    val last get() = index == pages.lastIndex
    /** Pages finished at least once (their creature is back in the 图录). */
    val finished = LinkedHashSet<String>()
    val seals = LinkedHashSet<String>()

    /** Records what the move on [m] just did: a page done, a seal found. */
    fun after(m: Mechanism) {
        if (state.done) finished += page.id
        if (page.seal == m.id && state.open(m.id)) seals += page.id
    }

    /** Turns to the next page once this one is done. */
    fun turn(): Boolean {
        if (!state.done || last) return false
        finished += page.id
        index++
        return true
    }

    /** Jumps to a page already reached (the 书签 window). */
    fun goTo(i: Int) {
        index = i.coerceIn(0, pages.lastIndex)
    }

    /** Pages one may open from the 书签: every finished one and the first unfinished. */
    fun reachable(i: Int) = i == 0 || pages[i - 1].id in finished

    val creaturesBack get() = pages.filter { it.id in finished }.mapNotNull { it.creature }

    fun save() = "${index};${finished.joinToString(",")};${seals.joinToString(",")}"

    fun load(s: String?) {
        val parts = s?.split(";") ?: return
        if (parts.size != 3) return
        finished.clear(); seals.clear()
        finished += parts[1].split(",").filter { id -> pages.any { it.id == id } }
        seals += parts[2].split(",").filter { id -> pages.any { it.id == id } }
        index = (parts[0].toIntOrNull() ?: 0).coerceIn(0, pages.lastIndex)
    }

    companion object {
        fun shanhai() = Book(Pages.all)
    }
}
