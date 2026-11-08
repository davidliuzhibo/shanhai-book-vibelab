package com.vrgames.shanhaibook.content

import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.shanhaibook.ATLAS_ID
import com.vrgames.shanhaibook.MARKS_ID
import com.vrgames.shanhaibook.game.Game

const val PANEL_TITLE = "title"
const val PANEL_PAGE = "page"
const val PANEL_NEXT = "next"
const val PANEL_MENU = "menu"
const val PANEL_SCROLL = "scroll"

// Ink for text on the scrolls' paper.
private val Ink = Color(0xFF241A10)
private val InkSoft = Color(0xFF5E4C39)
private val SealRed = Color(0xFFB3271C)

val LabelInk = Color(0xFFF4EEE2)
val Brass = Color(0xFFE2B75A)
// Secondary text, light enough to read against the dark panel recess.
val Dim = Color(0xFFE2DACB)
val Jade = Color(0xFF8FD3A8)
val Cinnabar = Color(0xFFE0604A)
private val PanelBg = Color(0xE6151A22)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF2151A22)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0012f), Color(0x55E2B75A), shape).padding(c.m(0.016f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.007f)),
    ) { content() }
}

/**
 * The one step that moves the story on, filled in brass with dark text: the design system's dark
 * buttons read as plain text, and players on the device never thought to pinch them.
 */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).background(Brass).border(c.m(0.002f), Color(0xFFFFF1C9), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.018f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.022f),
        color = Color(0xFF1B1408),
    )
}

/** A quieter pinchable line: outlined, still clearly a button. */
@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.011f), vertical = c.m(0.007f)),
        fontSize = c.sz(0.015f),
        color = color,
    )
}

/** Text on the paper of a thick scroll frame (the model is added by the stage); no flat box of its own. */
@Composable
private fun Paper(c: PhysicalLengthConverter, width: Float, height: Float, content: @Composable () -> Unit) {
    // Fixed by its frame; content taller than the panel is scaled down to fit, so a button never slips under the edge.
    Layout(
        content = { Column(verticalArrangement = Arrangement.spacedBy(c.m(0.006f))) { content() } },
        modifier = Modifier.width(c.m(width)).height(c.m(height)).padding(horizontal = c.m(0.018f), vertical = c.m(0.006f)),
    ) { measurables, constraints ->
        val p = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        val s = if (p.height > constraints.maxHeight) constraints.maxHeight.toFloat() / p.height else 1f
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = ((constraints.maxWidth - p.width * s) / 2f).roundToInt().coerceAtLeast(0)
            val y = ((constraints.maxHeight - p.height * s) / 2f).roundToInt().coerceAtLeast(0)
            p.placeWithLayer(x, y) {
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** A carved red seal to pinch: it reads the creature's story aloud. */
@Composable
private fun SealButton(c: PhysicalLengthConverter, playing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Box(
        Modifier.size(c.m(0.062f)).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFD23A2A), Color(0xFF8E1D14))))
            .border(c.m(0.002f), Color(0xFF5E1009), shape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(if (playing) "停" else "听", fontSize = c.sz(0.032f), color = Color(0xFFFFE9D6)) }
}

/** Once a creature is back: its name, source and story on a scroll, with the seal that reads it. */
@Composable
fun ScrollPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val cr = Game.book.page.creature ?: return
    Paper(c, 0.46f, 0.32f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.012f))) {
            SealButton(c, Game.speaking == cr.id) { if (Game.speaking == cr.id) Game.stopNarration() else Game.speak(cr.id) }
            Column {
                Text(cr.name, fontSize = c.sz(0.032f), color = SealRed)
                Text(cr.source, fontSize = c.sz(0.014f), color = InkSoft)
            }
        }
        Text(com.vrgames.shanhaibook.core.Pages.VOICE[cr.id] ?: "", fontSize = c.sz(0.0165f), color = Ink)
        Pill(c, "收起卷轴", InkSoft) { Game.scrollDismissed = true }
    }
}

/** Before the book opens: what it is, how the paper works, and the brass button to begin. */
@Composable
fun TitlePanel(c: PhysicalLengthConverter) {
    Paper(c, 0.52f, 0.25f) {
        Text("纸上山海", fontSize = c.sz(0.036f), color = SealRed)
        Text("小画师墨生的立体书《山海图》被一滴墨弄乱了，书里的异兽都走散了。拉纸条、转纸轮、掀纸片，帮它们一页页回到画里。", fontSize = c.sz(0.017f), color = LabelInk)
        Text("怎么玩：看着书上的纸机关，用手指捏住它：红纸条往自己这边拉，纸轮往旁边拉着转，纸片捏一下就掀开。每一页还藏着一枚红印章。", fontSize = c.sz(0.015f), color = LabelInk)
        CallToAction(c, "打开书 →") { Game.showTitle = false }
    }
}

/** Behind the book: the page's title, what to do, and the latest word (a refusal says why). */
@Composable
fun PagePanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val b = Game.book
    val missing = b.page.check(b.state)
    Paper(c, 0.48f, 0.2f) {
        Text(b.page.title, fontSize = c.sz(0.024f), color = SealRed)
        Text(b.page.task, fontSize = c.sz(0.015f), color = Ink)
        Game.note?.let { Text(it, fontSize = c.sz(0.016f), color = Ink) }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
            if (missing != null) Pill(c, "给点提示", SealRed) { Game.hint() }
            Text("红印章 ${b.seals.size}/7　异兽 ${b.creaturesBack.size}/6", fontSize = c.sz(0.014f), color = InkSoft)
        }
    }
}

/** In front of the book once the page is done: the way on, in brass. */
@Composable
fun NextPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val b = Game.book
    if (!b.state.done) return
    Column(verticalArrangement = Arrangement.spacedBy(c.m(0.006f)), horizontalAlignment = Alignment.CenterHorizontally) {
        if (b.last) {
            Text("全书读完！异兽 ${b.creaturesBack.size}/6，红印章 ${b.seals.size}/7", fontSize = c.sz(0.018f), color = Brass, modifier = Modifier.clip(RoundedCornerShape(c.m(0.01f))).background(PanelBg).padding(c.m(0.008f)))
            CallToAction(c, "从头再读一遍") { Game.readAgain() }
        } else {
            CallToAction(c, "翻到下一页 →") { Game.turn() }
        }
    }
}

/** The menu tab at the book's front left; unfolded it opens the windows and the settings. */
@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Brass) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.26f) {
        Pill(c, if (Game.window == ATLAS_ID) "收起异兽图录" else "异兽图录", Brass) { toggle(ATLAS_ID) }
        Pill(c, if (Game.window == MARKS_ID) "收起书签" else "书签（目录、设置）") { toggle(MARKS_ID) }
        Pill(c, "给点提示", Jade) { Game.menuOpen = false; Game.hint() }
        Pill(c, "这一页从头再来") { Game.menuOpen = false; Game.restartPage() }
        Pill(c, "把书摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.012f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

/**
 * A window's title row with its 关闭 button. It also tells [Game] while this copy of window [id] is
 * on screen, so a window the system closes does not count as open.
 */
@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Brass)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
