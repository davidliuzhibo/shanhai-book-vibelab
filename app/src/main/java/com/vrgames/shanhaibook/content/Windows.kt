package com.vrgames.shanhaibook.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.shanhaibook.ATLAS_ID
import com.vrgames.shanhaibook.MARKS_ID
import com.vrgames.shanhaibook.core.Pages
import com.vrgames.shanhaibook.game.Game

private val CardBg = Color(0x26F4EEE2)

/** 异兽图录: every creature back in its picture, with the classic's words, plain words and a fact. */
@Composable
fun AtlasWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val b = Game.book
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WindowHeader(ATLAS_ID, "异兽图录　${b.creaturesBack.size}/6", 32.sp)
        Text("每帮一只异兽回到画里，这里就多一页。原文出自《山海经》，白话是我们的解说。", fontSize = 18.sp, color = Dim)
        for (p in b.pages) {
            val c = p.creature ?: continue
            val back = p.id in b.finished
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!back) {
                    Text("？？？　（${p.title.substringBefore(" · ")}）", fontSize = 24.sp, color = Dim)
                    Text("完成这一页后，它会回到这里。", fontSize = 18.sp, color = Dim)
                } else {
                    Text("${c.name}　${c.source}", fontSize = 26.sp, color = Brass)
                    Text("原文：${c.original}", fontSize = 18.sp, color = LabelInk)
                    Text("白话：${c.plain}", fontSize = 18.sp, color = LabelInk)
                    Text("小知识：${c.fact}", fontSize = 18.sp, color = Jade)
                }
            }
        }
        Text("红印章 ${b.seals.size}/7", fontSize = 24.sp, color = Cinnabar)
        if (b.seals.size >= 7) {
            Text("墨生的题跋：七枚印章都找到了！《山海图》本是我画给自己看的，现在有你陪它们一起回家，这本书就是我们两个人的了。", fontSize = 19.sp, color = Brass)
        } else {
            Text("每一页都藏着一枚红印章：在能掀开的纸片下面找找。找齐七枚，墨生有话对你说。", fontSize = 18.sp, color = Dim)
        }
        Text("故事、人物和画面为本作原创；异兽的形象和文字出自《山海经》（公有领域）。", fontSize = 15.sp, color = Dim)
    }
}

/** 书签: the pages (open one already reached), help for the page at hand, and the settings. */
@Composable
fun MarksWindow() {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val b = Game.book
    Column(
        Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WindowHeader(MARKS_ID, "书签", 32.sp)
        for ((i, p) in b.pages.withIndex()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { Game.goTo(i); Game.closeWindow(MARKS_ID) }, enabled = b.reachable(i) && i != b.index) {
                    Text(p.title, fontSize = 21.sp)
                }
                Text(
                    when {
                        i == b.index -> "正在看"
                        p.id in b.finished -> "读过" + if (p.id in b.seals) " · 印章 ✓" else ""
                        b.reachable(i) -> "可以读"
                        else -> "读完前一页才能翻到"
                    },
                    fontSize = 18.sp,
                    color = if (p.id in b.finished) Jade else Dim,
                )
            }
        }
        Text("这一页", fontSize = 24.sp, color = Jade)
        Text(b.page.check(b.state)?.let { "还差：$it" } ?: "这一页完成了。", fontSize = 19.sp, color = LabelInk)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { Game.hint(); Game.closeWindow(MARKS_ID) }) { Text("给点提示", fontSize = 21.sp) }
            Button(onClick = { Game.restartPage() }) { Text("这一页从头再来", fontSize = 21.sp) }
        }
        Text("书的高度", fontSize = 24.sp, color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { Game.raise(-0.05f) }) { Text("降低", fontSize = 21.sp) }
            Button(onClick = { Game.raise(0.05f) }) { Text("升高", fontSize = 21.sp) }
            Text("比桌面高 ${(Game.height * 100).toInt()} 厘米", fontSize = 18.sp, color = Dim)
        }
        Button(onClick = { Game.toggleMusic() }) { Text(if (Game.musicOn) "音乐：开" else "音乐：关", fontSize = 21.sp) }
        Text("共 ${Pages.all.size} 页。进度自动保存。", fontSize = 16.sp, color = Dim)
    }
}
