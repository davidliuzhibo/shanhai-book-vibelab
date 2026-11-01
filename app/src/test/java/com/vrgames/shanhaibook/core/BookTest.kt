package com.vrgames.shanhaibook.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTest {
    private fun Book.move(id: String, v: Double): String? = state.set(page.mech(id), v).also { if (it == null) after(page.mech(id)) }

    @Test
    fun theWholeBookCanBeReadWithEverySeal() {
        val b = Book.shanhai()
        for (p in b.pages) {
            assertEquals(p.id, b.page.id)
            for ((id, v) in Pages.solutions.getValue(p.id)) assertNull("${p.id}: $id=$v", b.move(id, v))
            assertTrue("${p.id} not done: ${p.check(b.state)}", b.state.done)
            if (!b.last) assertTrue(b.turn())
        }
        assertEquals(7, b.seals.size)
        assertEquals(6, b.creaturesBack.size)
    }

    @Test
    fun aPageDoesNotTurnUntilDone() {
        val b = Book.shanhai()
        assertFalse(b.turn())
        assertNotNull(b.page.check(b.state))
    }

    @Test
    fun theBirdWaitsForTheTide() {
        val b = Book.shanhai()
        b.goTo(1)
        assertTrue(b.move("bird", 1.0)!!.contains("潮水"))
        assertEquals(0.0, b.state["bird"], 0.0)
    }

    @Test
    fun nineTailsAreHidden() {
        assertEquals(9, Pages.TAILS.values.sum())
        val b = Book.shanhai()
        b.goTo(2)
        b.move("tree", 1.0)
        assertEquals(2, Pages.tails(b.state))
        assertTrue(b.page.say(b.state, b.page.mech("tree"))!!.contains("2/9"))
    }

    @Test
    fun onlyTodaysCrowMayGo() {
        val b = Book.shanhai()
        b.goTo(4)
        b.move("crow", 2.0)
        assertTrue(b.move("go", 1.0)!!.contains("第七只"))
        b.move("crow", 16.0)
        assertEquals(6, b.state.step("crow"))
        assertNull(b.move("go", 1.0))
    }

    @Test
    fun dayAndNightDecideWhoGoesHome() {
        val b = Book.shanhai()
        b.goTo(5)
        assertTrue(b.move("cave", 1.0)!!.contains("黑夜"))
        b.move("eyes", 1.0)
        assertTrue(b.move("nest", 1.0)!!.contains("白天"))
        assertNull(b.move("cave", 1.0))
    }

    @Test
    fun theSongKeepsItsOrder() {
        val b = Book.shanhai()
        b.goTo(6)
        assertTrue(b.move("bell", 1.0)!!.contains("鼓"))
        assertNull(b.move("drum", 1.0))
        assertTrue(b.move("drum", 0.0)!!.contains("奏过"))
    }

    @Test
    fun theSealIsCountedOnce() {
        val b = Book.shanhai()
        b.move("inkstone", 1.0)
        b.move("inkstone", 0.0)
        b.move("inkstone", 1.0)
        assertEquals(setOf("study"), b.seals)
    }

    @Test
    fun progressSurvivesASave() {
        val b = Book.shanhai()
        for ((id, v) in Pages.solutions.getValue("study")) b.move(id, v)
        b.turn()
        val c = Book.shanhai()
        c.load(b.save())
        assertEquals(1, c.index)
        assertEquals(setOf("study"), c.finished)
        assertEquals(setOf("study"), c.seals)
        assertTrue(c.reachable(1))
        assertFalse(c.reachable(2))
    }
}
