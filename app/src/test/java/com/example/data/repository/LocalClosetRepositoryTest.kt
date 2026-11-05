package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ClosetItem
import com.example.data.model.Outfit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalClosetRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: LocalClosetRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "closet_local.json").delete()
        repo = LocalClosetRepository(context)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "closet_local.json").delete()
    }

    private fun item(name: String, category: String = "TOP") = ClosetItem(name = name, category = category)

    private suspend fun items() = repo.observeClosetItems("local").first()
    private suspend fun outfits() = repo.observeOutfits("local").first()
    private suspend fun logs() = repo.observeWearLogs("local").first()

    @Test
    fun addAndReload_keepsItemsOnDisk() = runBlocking {
        val id = repo.addClosetItem(item("Blue Tee")).getOrThrow()
        assertEquals(listOf("Blue Tee"), items().map { it.name })

        // A new repository reading the same file sees the same closet (survives an app restart).
        val reopened = LocalClosetRepository(context)
        val loaded = reopened.observeClosetItems("local").first()
        assertEquals(listOf(id), loaded.map { it.id })
        assertTrue(loaded.single().createdAt != null)
    }

    @Test
    fun logWear_countsUp_andDeletingTheLogRollsBack() = runBlocking {
        val top = repo.addClosetItem(item("Tee")).getOrThrow()
        val bottom = repo.addClosetItem(item("Jeans", "BOTTOM")).getOrThrow()
        val outfit = repo.addOutfit(Outfit(name = "Casual", itemIds = listOf(top, bottom))).getOrThrow()

        val logId = repo.logOutfitWear(
            dateIso = "2026-11-05", outfitId = outfit, outfitName = "Casual", itemIds = listOf(top, bottom),
            occasion = "Casual", weatherTempC = 10.0, weatherCondition = "Overcast"
        ).getOrThrow()
        assertEquals(listOf(1L, 1L), items().map { it.wearCount })
        assertEquals(1L, outfits().single().wearCount)

        repo.deleteWearLog(
            logId = logId,
            itemUpdates = mapOf(top to (0L to 0L), bottom to (0L to 0L)),
            outfitId = outfit,
            outfitWearCount = 0L
        ).getOrThrow()
        assertTrue(logs().isEmpty())
        assertEquals(listOf(0L, 0L), items().map { it.wearCount })
        assertEquals(0L, outfits().single().wearCount)
    }

    @Test
    fun deletingItems_shrinksOutfits_andRemovesEmptyOnes() = runBlocking {
        val a = repo.addClosetItem(item("A")).getOrThrow()
        val b = repo.addClosetItem(item("B")).getOrThrow()
        repo.addOutfit(Outfit(name = "Both", itemIds = listOf(a, b))).getOrThrow()
        repo.addOutfit(Outfit(name = "OnlyA", itemIds = listOf(a))).getOrThrow()

        repo.deleteClosetItems(listOf(a), outfits()).getOrThrow()

        assertEquals(listOf("B"), items().map { it.name })
        val left = outfits()
        assertEquals(listOf("Both"), left.map { it.name })
        assertEquals(listOf(b), left.single().itemIds)
    }

    @Test
    fun deleteEverything_clearsAllAndReportsCount() = runBlocking {
        val a = repo.addClosetItem(item("A")).getOrThrow()
        repo.addOutfit(Outfit(name = "O", itemIds = listOf(a))).getOrThrow()
        repo.logOutfitWear("2026-11-05", "", "Look", listOf(a), "Casual", 12.0, "Clear").getOrThrow()

        assertEquals(3, repo.deleteAllUserData().getOrThrow())
        assertTrue(items().isEmpty() && outfits().isEmpty() && logs().isEmpty())
        assertTrue(LocalClosetRepository(context).observeClosetItems("local").first().isEmpty())
    }

    @Test
    fun sampleItems_addTenPiecesAndAnOutfit() = runBlocking {
        assertEquals(10, repo.seedStarterCapsuleWardrobe().getOrThrow())
        assertEquals(10, items().size)
        assertEquals(1, outfits().size)
    }
}
