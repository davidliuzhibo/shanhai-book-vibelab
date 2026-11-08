package com.vrgames.shanhaibook.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.shanhaibook.core.Book
import com.vrgames.shanhaibook.core.Mechanism
import com.vrgames.shanhaibook.render.Music
import com.vrgames.shanhaibook.render.Sfx
import com.vrgames.shanhaibook.render.Sound

/**
 * State the stage, its panels and both windows share (one process). [book] holds the rules (touched
 * under [lock]); the Compose fields mirror what the panels show and change only on events.
 */
private const val GUIDE_AFTER_MS = 4000L

object Game {
    private const val PREFS = "shanhai_book"
    private const val TAG = "BookGame"

    val lock = Any()
    val book = Book.shanhai()
    private var prefs: android.content.SharedPreferences? = null

    /** Bumps whenever a mechanism moves or the page changes. */
    var version by mutableIntStateOf(0); private set
    /** Bumps to make the stage turn the leaf (the book's index has already moved on). */
    var turnVersion by mutableIntStateOf(0); private set
    /** Bumps to make the stage show the book's page at once (a jump from the 书签, a page restarted). */
    var jumpVersion by mutableIntStateOf(0); private set
    /** Bumps when a page is done, for the creature's little celebration. */
    var doneVersion by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var showTitle by mutableStateOf(true)
    /** The narration clip now playing, or null. */
    var speaking by mutableStateOf<String?>(null); private set
    private var narrator: android.media.MediaPlayer? = null
    private var appContext: Context? = null
    /** The scroll beside the book is put away for this page. */
    var scrollDismissed by mutableStateOf(false)
    /** Where the ghost hand should demonstrate next, or null while it should rest. */
    var guideAt by mutableStateOf<String?>(null); private set
    private var idleSince = 0L
    var menuOpen by mutableStateOf(false)
    /** The mechanism the hint points at (the stage sparkles over it), bumping [hintVersion]. */
    var hintAt by mutableStateOf<String?>(null)
    var hintVersion by mutableIntStateOf(0); private set
    var height by mutableFloatStateOf(0f)
    var musicOn by mutableStateOf(true); private set
    var music: Music? = null
    var sound: Sound? = null

    /** The window on screen, or null: one at a time, never twice (a second open starts a second copy). */
    var window by mutableStateOf<String?>(null); private set
    private val windowCopies = HashMap<String, Any>()
    var closeWindow: (String) -> Unit = {}

    fun windowShown(id: String, copy: Any? = null) {
        if (copy != null) windowCopies[id] = copy
        window = id
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && windowCopies[id] !== copy) return
        windowCopies.remove(id)
        if (window == id) window = null
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            synchronized(lock) { book.load(p.getString("book", null)) }
            musicOn = p.getBoolean("music", true)
            height = p.getFloat("height", 0f)
        }
        AutoPilot.register(context)
    }

    private fun save() {
        prefs?.edit()?.putString("book", synchronized(lock) { book.save() })?.apply()
    }

    fun say(text: String?) {
        note = text
        noteVersion++
    }

    /** Sets [m] to [to]; a refusal becomes the note, so the player always learns why. True if it moved. */
    fun move(m: Mechanism, to: Double): Boolean {
        val wasDone = book.state.done
        val sealsBefore = book.seals.size
        val why = synchronized(lock) { book.state.set(m, to) }
        version++
        if (why != null) {
            say(why)
            sound?.play(Sfx.WRONG)
            return false
        }
        synchronized(lock) { book.after(m) }
        val said = book.page.say(book.state, m)
        when {
            book.seals.size > sealsBefore -> {
                say(said ?: "找到一枚红印章！（${book.seals.size}/7）")
                sound?.play(Sfx.SEAL)
            }
            book.state.done && !wasDone -> {
                val c = book.page.creature
                say(if (c != null) "${c.name}回到画里了！听听它的来历。" else "这一页完成了！")
                sound?.play(if (c != null) Sfx.CREATURE else Sfx.DONE)
                doneVersion++
                scrollDismissed = false
                c?.let { speak(it.id) }
            }
            said != null -> say(said)
            else -> say(null)
        }
        hintAt = null
        interacted()
        save()
        return true
    }

    /** The player just did something: the ghost hand rests, and shows the next thing after a pause. */
    fun interacted() {
        idleSince = android.os.SystemClock.uptimeMillis()
        guideAt = null
    }

    /** Called about once a second: after a few quiet seconds, the ghost hand shows the next mechanism. */
    fun tickGuide() {
        // It teaches how paper moves, on the prologue only: the later pages are for the player to work out.
        if (showTitle || book.state.done || book.page.id != "study") { if (guideAt != null) guideAt = null; return }
        if (android.os.SystemClock.uptimeMillis() - idleSince < GUIDE_AFTER_MS) return
        if (guideAt == null) guideAt = com.vrgames.shanhaibook.core.Guide.next(book.page, book.state)?.id
    }

    fun speak(id: String) {
        stopNarration()
        val c = appContext ?: return
        runCatching {
            val fd = c.assets.openFd("narration/$id.ogg")
            narrator = android.media.MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopNarration() }
                prepare()
                start()
            }
            speaking = id
            music?.ducked = true
        }.onFailure { Log.i(TAG, "narration $id: ${it.message}") }
    }

    fun stopNarration() {
        runCatching { narrator?.stop() }
        runCatching { narrator?.release() }
        narrator = null
        speaking = null
        music?.ducked = false
    }

    fun turn() {
        if (synchronized(lock) { book.turn() }) {
            stopNarration()
            scrollDismissed = false
            interacted()
            if (book.last) speak("ending")
            say(null)
            hintAt = null
            version++
            turnVersion++
            sound?.play(Sfx.FLIP)
            save()
            Log.i(TAG, "page ${book.page.id}")
        }
    }

    /** Jumps to page [i] if it has been reached (the 书签 window). */
    fun goTo(i: Int) {
        if (!book.reachable(i)) return
        stopNarration()
        synchronized(lock) { book.goTo(i) }
        say(null)
        version++
        jumpVersion++
        sound?.play(Sfx.FLIP)
        save()
    }

    /** Puts every mechanism of this page back as it started. */
    fun restartPage() {
        synchronized(lock) { book.state.reset() }
        say("这一页从头再来。")
        version++
        jumpVersion++
    }

    /** From the last page: read the book again from the start (what was found stays in the 图录). */
    fun readAgain() {
        synchronized(lock) {
            book.states.forEach { it.reset() }
            book.goTo(0)
        }
        say(null)
        version++
        jumpVersion++
        sound?.play(Sfx.OPEN)
        save()
    }

    /** A hint: what is still missing on this page, and a sparkle over the part to touch. */
    fun hint() {
        val p = book.page
        val s = book.state
        val text = p.hint(s) ?: p.check(s) ?: "这一页已经完成了，翻到下一页吧。"
        say("提示：$text")
        hintAt = p.mechanisms.firstOrNull { m -> text.contains(m.label) } ?.id ?: p.mechanisms.firstOrNull()?.id
        hintVersion++
    }

    fun raise(dy: Float) {
        height = (height + dy).coerceIn(-0.25f, 0.3f)
        prefs?.edit()?.putFloat("height", height)?.apply()
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun resetSave() {
        prefs?.edit()?.clear()?.apply()
        synchronized(lock) {
            book.load("0;;")
            book.states.forEach { it.reset() }
        }
        version++
        jumpVersion++
    }

    // Debug hook (AutoPilot): table pose "x,y,z,yaw,scale,pitch" for the emulator's fixed camera.
    var debugView by mutableStateOf<String?>(null)
    var debugOpen by mutableStateOf<String?>(null)
    var faceRequest by mutableIntStateOf(0)
}
