package com.vrgames.shanhaibook.core

import com.vrgames.shanhaibook.core.Mechanism.Flap
import com.vrgames.shanhaibook.core.Mechanism.Tab
import com.vrgames.shanhaibook.core.Mechanism.Wheel

/**
 * The eight pages of 《山海图》. The creatures and their words come from the 山海经 (public domain);
 * the story of the ink drop and the little painter 墨生 is ours. No page uses a violent passage.
 */
object Pages {
    val jingwei = Creature(
        "jingwei", "精卫", "《山海经·北山经》",
        "有鸟焉，其状如乌，文首，白喙，赤足，名曰精卫，其鸣自詨。是炎帝之少女，名曰女娃。女娃游于东海，溺而不返，故为精卫，常衔西山之木石，以堙于东海。",
        "发鸠山上有一种鸟，样子像乌鸦，头上有花纹，白嘴红脚，叫声就像在喊自己的名字“精卫”。传说她是炎帝的小女儿女娃，在东海游玩时没能回来，化作精卫鸟，天天衔来西山的树枝和石子，想把东海填平。",
        "成语“精卫填海”就出自这里，用来比喻意志坚定、不怕艰难。",
    )
    val fox = Creature(
        "fox", "九尾狐", "《山海经·南山经》",
        "又东三百里，曰青丘之山，其阳多玉，其阴多青雘。有兽焉，其状如狐而九尾，其音如婴儿。",
        "再往东三百里是青丘山，山的南面多玉石，北面多青色的矿石。山里有一种野兽，样子像狐狸，却长着九条尾巴，叫声像婴儿。",
        "在汉代的画像石上，九尾狐常和白兔、三足乌一起出现，被看作吉祥的象征。",
    )
    val kaiming = Creature(
        "kaiming", "开明兽", "《山海经·海内西经》",
        "昆仑南渊深三百仞。开明兽身大类虎而九首，皆人面，东向立昆仑上。",
        "昆仑山南边的深渊有三百仞深。守在那里的开明兽，身子大得像老虎，有九个头，每个头都是人脸，面朝东方站在昆仑山上。",
        "开明兽守着昆仑山的九道门，是神话里的守门神兽。（书里把它画成了可爱的样子。）",
    )
    val jinwu = Creature(
        "jinwu", "金乌", "《山海经·海外东经》《大荒东经》",
        "汤谷上有扶桑，十日所浴……九日居下枝，一日居上枝。……一日方至，一日方出，皆载于乌。",
        "汤谷上有一棵扶桑树，是十个太阳洗澡的地方。九个太阳住在下面的树枝上，一个住在最上面的树枝上。它们轮流出门：一个刚回来，另一个才出去，每个太阳都由一只乌鸟驮着。",
        "后来人们把驮太阳的鸟叫作“金乌”，金乌也就成了太阳的代称。",
    )
    val zhulong = Creature(
        "zhulong", "烛龙", "《山海经·海外北经》",
        "钟山之神，名曰烛阴，视为昼，瞑为夜，吹为冬，呼为夏，不饮，不食，不息，息为风。……其为物，人面，蛇身，赤色，居钟山下。",
        "钟山的山神名叫烛阴：他睁开眼睛就是白天，闭上眼睛就是黑夜；吹一口气是冬天，呼一口气是夏天。他不喝水、不吃东西、也不呼吸，一呼吸就起风。他长着人的脸、蛇的身子，全身通红，住在钟山下。",
        "烛阴也叫烛龙，是神话里掌管昼夜和四季的神。",
    )
    val dijiang = Creature(
        "dijiang", "帝江", "《山海经·西山经》",
        "有神焉，其状如黄囊，赤如丹火，六足四翼，浑敦无面目，是识歌舞，实为帝江也。",
        "天山上有一位神，样子像一个黄色的口袋，却红得像一团火；有六只脚、四只翅膀，浑浑沌沌，没有脸，却很懂唱歌跳舞，他就是帝江。",
        "帝江常被看作“混沌”的化身。没有五官，却最会跳舞。",
    )

    /** The 帝江 page: the score, in the order the instruments must sound. */
    val SCORE = listOf("drum", "bell", "flute", "gong")
    private val NOTE_NAMES = mapOf("drum" to "鼓", "bell" to "铃", "flute" to "笛", "gong" to "锣")

    /** The 九尾狐 page: tails behind each hiding place. */
    val TAILS = mapOf("tree" to 2, "rock" to 1, "bush" to 2, "grass" to 2, "cloud" to 2)
    fun tails(s: PageState): Int {
        var n = 0
        for (k in listOf("tree", "rock", "bush")) if (s.open(k)) n += TAILS[k]!!
        if (s.open("grass")) n += TAILS["grass"]!!
        if (s.step("cloud") == 2) n += TAILS["cloud"]!!
        return n
    }

    /** The 开明兽 page: where each wheel's three heads must face (0 north .. 3 west), as the star chart shows. */
    val HEADS = mapOf("heads1" to 1, "heads2" to 3, "heads3" to 2)
    private val DIRS = listOf("北", "东", "南", "西")

    /** The 金乌 page: the crow whose day it is (the calendar says 第七日). */
    const val TODAY = 6

    val all: List<Page> = listOf(
        Page(
            "study", "序 · 书斋",
            "小画师墨生的书斋。学会三种纸机关：拉出书帘，把灯盏转到第三格，掀开砚台盖。",
            listOf(Tab("curtain", "书帘"), Wheel("lamp", "灯盏", 4), Flap("inkstone", "砚台盖")),
            check = { s ->
                when {
                    !s.open("curtain") -> "把书帘拉出来：捏住书页下边的红纸条，往自己这边拉。"
                    s.step("lamp") != 2 -> "把灯盏转到第三格（现在第 ${s.step("lamp") + 1} 格）：捏住纸轮的边，往旁边拉。"
                    !s.open("inkstone") -> "掀开砚台盖：捏一下就行。"
                    else -> null
                }
            },
            say = { s, m -> if (m.id == "inkstone" && s.open("inkstone")) "砚台里藏着一枚红印章。每一页都藏着一枚，找齐有惊喜。" else null },
            seal = "inkstone",
        ),
        Page(
            "sea", "第一页 · 东海 · 精卫",
            "一滴墨落下，书里的异兽都走散了。先帮精卫：转动潮水纸轮让海水退下，再拉纸条，让她把石子衔到礁石上。",
            listOf(Wheel("tide", "潮水", 3), Tab("bird", "精卫"), Flap("rock", "海边岩石")),
            check = { s ->
                when {
                    s.step("tide") != 2 -> "潮水还太高，石子在水下：转动潮水纸轮，让海水退下（转到第三格）。"
                    !s.open("bird") -> "拉动纸条，让精卫衔起石子飞到礁石上。"
                    else -> null
                }
            },
            allow = { s, m, to ->
                if (m.id == "bird" && to > 0 && s.step("tide") != 2) "潮水太高，精卫衔不到石子：先让海水退下。" else null
            },
            creature = jingwei,
            seal = "rock",
        ),
        Page(
            "fox", "第二页 · 青丘 · 九尾狐",
            "九尾狐的九条尾巴躲起来了：藏在树后、石后、灌木后、草丛里和云里。把它们一条条找回来。",
            listOf(Flap("tree", "松树"), Flap("rock", "山石"), Flap("bush", "灌木"), Tab("grass", "草丛"), Wheel("cloud", "祥云", 4), Flap("moss", "苔石")),
            check = { s -> val n = tails(s); if (n < 9) "还差 ${9 - n} 条尾巴（找到 $n/9）。树、石、灌木都能掀开，草丛能拉开，祥云能转动。" else null },
            say = { s, m ->
                when (m.id) {
                    "moss" -> if (s.open("moss")) "苔石下面没有尾巴，倒有一枚红印章。" else null
                    "cloud" -> if (s.step("cloud") == 2) "云转开了，后面有 2 条尾巴！（${tails(s)}/9）" else "云后面还空着，再转转。"
                    else -> if (s[m.id] > 0.5) "找到 ${TAILS[m.id]} 条尾巴！（${tails(s)}/9）" else null
                }
            },
            creature = fox,
            seal = "moss",
            hints = listOf(
                { s: PageState -> s.open("tree") && s.open("rock") && s.open("bush") } to "先掀开松树、山石和灌木。",
                { s: PageState -> s.open("grass") } to "草丛可以往外拉。",
                { s: PageState -> s.step("cloud") == 2 } to "祥云要转到第三格才露出后面。",
            ),
        ),
        Page(
            "gate", "第三页 · 昆仑 · 开明兽",
            "开明兽守着昆仑的宫门。它的九个头分成三组，照着墙上星图的箭头，转动三个纸轮，让每组头朝对方向，宫门就开了。",
            listOf(Wheel("heads1", "左边三个头", 4), Wheel("heads2", "中间三个头", 4), Wheel("heads3", "右边三个头", 4), Flap("stone", "石碑")),
            check = { s ->
                val wrong = HEADS.entries.firstOrNull { (k, v) -> s.step(k) != v }
                if (wrong == null) null else {
                    val name = (s.page.mech(wrong.key) as Wheel).label
                    "${name}要朝${DIRS[wrong.value]}（现在朝${DIRS[s.step(wrong.key)]}）。看星图上的箭头。"
                }
            },
            creature = kaiming,
            seal = "stone",
        ),
        Page(
            "sun", "第四页 · 扶桑 · 金乌",
            "扶桑树上住着十只金乌，每天只能一只驮着太阳出门。看日历：今天是第七日。转动纸轮选中第七只金乌，再拉纸条放它出门。",
            listOf(Wheel("crow", "选金乌", 10), Tab("go", "出门"), Flap("hollow", "树洞")),
            check = { s -> if (!s.open("go")) "转动纸轮选中第七只金乌（现在是第 ${s.step("crow") + 1} 只），再拉纸条放它出门。" else null },
            allow = { s, m, to ->
                when {
                    m.id == "go" && to > 0 && s.step("crow") != TODAY -> "今天轮到第七只金乌，不是第 ${s.step("crow") + 1} 只。天上不能有两个太阳。"
                    m.id == "crow" && s.open("go") -> "金乌已经出门了，今天就不换了。"
                    else -> null
                }
            },
            creature = jinwu,
            seal = "hollow",
        ),
        Page(
            "dragon", "第五页 · 钟山 · 烛龙",
            "烛龙睁眼是白天，闭眼是黑夜。小鹿白天回家，猫头鹰夜里回窝：拉纸条让烛龙睁眼或闭眼，在对的时候打开它们的家门。",
            listOf(Tab("eyes", "烛龙的眼睛"), Flap("cave", "小鹿的山洞"), Flap("nest", "猫头鹰的窝"), Flap("boulder", "山脚的大石")),
            check = { s ->
                when {
                    !s.open("cave") -> "小鹿要在白天回家：让烛龙睁开眼睛，再打开山洞。"
                    !s.open("nest") -> "猫头鹰要在夜里回窝：让烛龙闭上眼睛，再打开鸟窝。"
                    else -> null
                }
            },
            allow = { s, m, to ->
                when {
                    m.id == "cave" && to > 0.5 && !s.open("eyes") -> "现在是黑夜，小鹿不敢出来：先让烛龙睁开眼睛。"
                    m.id == "nest" && to > 0.5 && s.open("eyes") -> "现在是白天，猫头鹰还在睡觉：先让烛龙闭上眼睛。"
                    else -> null
                }
            },
            say = { s, m -> if (m.id == "eyes") (if (s.open("eyes")) "烛龙睁开了眼睛：天亮了。" else "烛龙闭上了眼睛：天黑了。") else null },
            creature = zhulong,
            seal = "boulder",
        ),
        Page(
            "song", "第六页 · 天山 · 帝江",
            "帝江没有脸，却最爱跳舞。照着乐谱的顺序掀开乐器：鼓、铃、笛、锣，帝江就会跳起来。",
            listOf(Flap("drum", "鼓"), Flap("bell", "铃"), Flap("flute", "笛"), Flap("gong", "锣"), Flap("mat", "舞毯一角")),
            check = { s -> val n = SCORE.count { s.open(it) }; if (n < SCORE.size) "照乐谱奏下一个：${NOTE_NAMES[SCORE[n]]}（${n}/4）。" else null },
            allow = { s, m, to ->
                if (m.id !in SCORE) null else {
                    val played = SCORE.count { s.open(it) }
                    when {
                        to < 0.5 && s.open(m.id) -> "已经奏过了，接着奏下一个。"
                        to > 0.5 && SCORE.indexOf(m.id) != played -> "节拍不对：乐谱上下一个是${NOTE_NAMES[SCORE[played]]}。"
                        else -> null
                    }
                }
            },
            creature = dijiang,
            seal = "mat",
        ),
        Page(
            "end", "尾页 · 山海图",
            "所有异兽都回来了。看，它们从书里跳了出来！",
            emptyList(),
            check = { null },
        ),
    )

    /** A way through each page, move by move (the test hook plays these; the tests check them), seal included. */
    val solutions: Map<String, List<Pair<String, Double>>> = mapOf(
        "study" to listOf("curtain" to 1.0, "lamp" to 2.0, "inkstone" to 1.0),
        "sea" to listOf("rock" to 1.0, "tide" to 2.0, "bird" to 1.0),
        "fox" to listOf("tree" to 1.0, "rock" to 1.0, "bush" to 1.0, "moss" to 1.0, "grass" to 1.0, "cloud" to 2.0),
        "gate" to listOf("stone" to 1.0, "heads1" to 1.0, "heads2" to 3.0, "heads3" to 2.0),
        "sun" to listOf("hollow" to 1.0, "crow" to TODAY.toDouble(), "go" to 1.0),
        "dragon" to listOf("boulder" to 1.0, "eyes" to 1.0, "cave" to 1.0, "eyes" to 0.0, "nest" to 1.0),
        "song" to listOf("mat" to 1.0, "drum" to 1.0, "bell" to 1.0, "flute" to 1.0, "gong" to 1.0),
        "end" to emptyList(),
    )
}
