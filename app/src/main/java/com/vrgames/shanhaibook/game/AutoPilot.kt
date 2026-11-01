package com.vrgames.shanhaibook.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.shanhaibook.core.Pages

/**
 * Test hook, debug builds only: works the page's mechanisms and turns pages without hands, so the
 * whole book can be checked on the emulator. Results go to logcat (tag BookTest).
 *
 *   adb shell am broadcast -a com.vrgames.shanhaibook.TEST -p com.vrgames.shanhaibook --es cmd all
 *
 * cmd: all (every page, seals included, turning on) · solve (this page) · set --es id "<mechanism>=<value>" ·
 * turn · page --es id <index> · hint · start · view --es id "x,y,z,yaw,scale,pitch"|reset ·
 * open --es id atlas|marks|menu|close · face · reset · stop
 */
object AutoPilot {
    const val ACTION = "com.vrgames.shanhaibook.TEST"
    const val TAG = "BookTest"
    private const val MOVE_MS = 2500L
    private const val TURN_MS = 6000L
    private val main = Handler(Looper.getMainLooper())
    private var all = false

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "all" -> {
                stop()
                all = true
                Game.showTitle = false
                solve()
            }
            "solve" -> {
                stop()
                Game.showTitle = false
                solve()
            }
            "set" -> {
                val (k, v) = id!!.split("=")
                set(k, v.toDouble())
            }
            "turn" -> Game.turn()
            "page" -> Game.goTo(id?.toIntOrNull() ?: 0)
            "hint" -> Game.hint()
            "start" -> Game.showTitle = false
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "open" -> Game.debugOpen = id
            "face" -> Game.faceRequest++
            "reset" -> {
                stop()
                Game.resetSave()
            }
            "stop" -> stop()
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    /** The stage has built the book: tests wait for this line. */
    fun ready() = Log.i(TAG, "book ready")

    private fun stop() {
        all = false
        main.removeCallbacksAndMessages(null)
    }

    /** Plays this page's solution one move at a time, then (in an [all] run) turns and goes on. */
    private fun solve() {
        val page = Game.book.page
        val moves = Pages.solutions[page.id].orEmpty()
        moves.forEachIndexed { i, (k, v) -> main.postDelayed({ set(k, v) }, MOVE_MS * (i + 1)) }
        main.postDelayed({
            Log.i(TAG, "page ${page.id} done=${Game.book.state.done} seals=${Game.book.seals.size} creatures=${Game.book.creaturesBack.size}")
            if (all) {
                if (Game.book.last) {
                    Log.i(TAG, "book done seals=${Game.book.seals.size}/7 creatures=${Game.book.creaturesBack.size}/6")
                    all = false
                } else {
                    Game.turn()
                    main.postDelayed(::solve, TURN_MS)
                }
            }
        }, MOVE_MS * (moves.size + 1))
    }

    private fun set(id: String, v: Double) {
        val m = Game.book.page.mechanisms.firstOrNull { it.id == id } ?: return Log.w(TAG, "no $id on ${Game.book.page.id}").let {}
        val ok = Game.move(m, v)
        Log.i(TAG, "set $id=$v ok=$ok note=${Game.note} done=${Game.book.state.done}")
    }
}
