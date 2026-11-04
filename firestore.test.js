const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validClosetItemPayload(userId, now) {
  return {
    userId,
    name: "Camel Trench Coat",
    category: "OUTERWEAR",
    subCategory: "Trench Coat",
    colorName: "Warm Camel",
    colorHex: "#C19A6B",
    warmthLevel: 4,
    isRainResistant: true,
    isWindResistant: true,
    occasions: ["Work", "Casual"],
    seasons: ["Autumn", "Spring"],
    notes: "Water-repellent cotton gabardine",
    imageBase64: "",
    wearCount: 2,
    lastWornEpochDay: 19900,
    isFavorite: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validOutfitPayload(userId, now) {
  return {
    userId,
    name: "Rainy Commute Look",
    itemIds: ["item_1", "item_2"],
    occasion: "Work",
    targetWeatherSummary: "12C Rainy & Breezy",
    weatherComfortScore: 95,
    stylistNotes: "Layer the trench over fine merino knitwear.",
    isAiGenerated: true,
    isFavorite: false,
    wearCount: 1,
    createdAt: now,
    updatedAt: now,
  };
}

function validWearLogPayload(userId, now) {
  return {
    userId,
    dateIso: "2026-10-30",
    outfitId: "outfit_1",
    outfitName: "Rainy Commute Look",
    itemIds: ["item_1", "item_2"],
    occasion: "Work",
    weatherTempC: 12.5,
    weatherCondition: "Light Rain",
    notes: "Kept dry on morning walk",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write closetItems", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("closetItems").get()
  );
});

test("Authenticated user can create, read, update, and query their own closetItems", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const itemRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("closetItems")
    .doc("item_1");

  await assertSucceeds(itemRef.set(validClosetItemPayload(ALICE_UID, now)));
  await assertSucceeds(itemRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("closetItems")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  // Valid update (increment wearCount andisFavorite toggle)
  await assertSucceeds(
    itemRef.update({
      wearCount: 3,
      isFavorite: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user access to another user's closetItems is rejected", async () => {
  const now = new Date(Date.now() - 2000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("closetItems")
      .doc("item_1")
      .set(validClosetItemPayload(ALICE_UID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("closetItems")
      .doc("item_1")
      .get()
  );
});

test("Shadow update with ghost field or invalid category is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const itemRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("closetItems")
    .doc("item_1");

  await assertSucceeds(itemRef.set(validClosetItemPayload(ALICE_UID, now)));

  // Ghost field injection
  await assertFails(
    itemRef.update({
      isVerified: true,
    })
  );

  // Invalid category enum
  await assertFails(
    itemRef.update({
      category: "SPACE_SUIT",
    })
  );
});

test("Authenticated user can create and query outfits and wearLogs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  const outfitRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("outfits")
    .doc("outfit_1");
  await assertSucceeds(outfitRef.set(validOutfitPayload(ALICE_UID, now)));
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("outfits")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  const logRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("wearLogs")
    .doc("log_1");
  await assertSucceeds(logRef.set(validWearLogPayload(ALICE_UID, now)));
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("wearLogs")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});
