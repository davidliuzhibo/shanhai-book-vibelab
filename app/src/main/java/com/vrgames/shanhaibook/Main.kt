package com.vrgames.shanhaibook

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.shanhaibook.content.AtlasWindow
import com.vrgames.shanhaibook.content.BookStage
import com.vrgames.shanhaibook.content.MarksWindow

const val ATLAS_ID = "Atlas"
const val MARKS_ID = "Marks"

/**
 * Three linked spaces: the pop-up book on the player's table (Stage), the 异兽图录 (the creatures
 * brought back, with the 山海经's words) and the 书签 (pages, help, settings). They share
 * [com.vrgames.shanhaibook.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { BookStage() }
        }
        WindowContainer(ATLAS_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { AtlasWindow() }
        }
        WindowContainer(MARKS_ID, Form.Planar, { defaultSize = WindowContainerSize(720.dp, 1000.dp) }) {
            PicoTheme { MarksWindow() }
        }
    }
