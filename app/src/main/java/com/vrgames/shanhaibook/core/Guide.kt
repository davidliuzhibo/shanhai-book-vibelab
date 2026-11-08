package com.vrgames.shanhaibook.core

/** Which mechanism the ghost hand should demonstrate: the one the page's own check is asking about. */
object Guide {
    fun next(page: Page, state: PageState): Mechanism? {
        val missing = page.check(state) ?: return null
        return page.mechanisms.firstOrNull { missing.contains(it.label) }
            ?: page.mechanisms.firstOrNull { state[it.id] < 0.5 && it.id != page.seal }
            ?: page.mechanisms.firstOrNull()
    }
}
