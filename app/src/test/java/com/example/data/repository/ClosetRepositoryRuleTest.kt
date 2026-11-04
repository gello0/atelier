package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ClosetItem
import com.example.data.model.GarmentCategory
import com.example.data.model.Outfit
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ClosetRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveClosetItem_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ClosetRepository(firestore)
        val uniqueId = "item_${UUID.randomUUID().toString().replace("-", "")}"

        val item = ClosetItem(
            id = uniqueId,
            userId = uid,
            name = "Camel Cashmere Coat",
            category = GarmentCategory.OUTERWEAR.name,
            subCategory = "Overcoat",
            colorName = "Camel",
            colorHex = "#C19A6B",
            warmthLevel = 5,
            isRainResistant = true,
            isWindResistant = true,
            occasions = listOf("Work", "Evening"),
            seasons = listOf("Winter"),
            notes = "Double-faced wool cashmere"
        )

        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.addClosetItem(item) }
        assertTrue("Expected addClosetItem to succeed: ${addResult.exceptionOrNull()}", addResult.isSuccess)

        val observedItems = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeClosetItems(uid).first { list -> list.any { it.id == uniqueId } }
        }
        assertTrue(observedItems.any { it.id == uniqueId && it.name == "Camel Cashmere Coat" })
    }

    @Test
    fun addOutfitAndLogWear_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ClosetRepository(firestore)
        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addClosetItem(
                ClosetItem(
                    userId = uid,
                    name = "Merino Sweater",
                    category = GarmentCategory.TOP.name,
                    colorName = "Espresso",
                    colorHex = "#3D2B1F",
                    warmthLevel = 4
                )
            ).getOrThrow()
        }

        val outfitId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addOutfit(
                Outfit(
                    userId = uid,
                    name = "Crisp Morning Office",
                    itemIds = listOf(itemId),
                    occasion = "Work",
                    targetWeatherSummary = "11°C · Overcast",
                    weatherComfortScore = 92,
                    stylistNotes = "Warm merino base layer.",
                    isAiGenerated = true
                )
            ).getOrThrow()
        }
        assertTrue(outfitId.isNotEmpty())

        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logOutfitWear(
                dateIso = "2026-10-30",
                outfitId = outfitId,
                outfitName = "Crisp Morning Office",
                itemIds = listOf(itemId),
                occasion = "Work",
                weatherTempC = 11.0,
                weatherCondition = "Overcast",
                notes = "Very comfortable"
            ).getOrThrow()
        }
        assertTrue(logId.isNotEmpty())
    }

    @Test
    fun getClosetItem_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ClosetRepository(firestore)
        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addClosetItem(
                ClosetItem(
                    userId = aliceUid,
                    name = "Alice's Silk Blouse",
                    category = GarmentCategory.TOP.name,
                    colorName = "Ivory",
                    colorHex = "#FAF7F2",
                    warmthLevel = 2
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ClosetRepository(firestore)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getClosetItemById(aliceUid, itemId)
        }
        assertTrue(crossResult.isFailure)
        val firestoreEx = crossResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeClosetItems_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ClosetRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeClosetItems("unauth_user_123").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull("Expected exception for unauthenticated query", caught)
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@atelier.test"
        const val BOB_EMAIL = "bob@atelier.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
