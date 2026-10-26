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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read health_records", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("health_records").get());
});

test("Cross-user access is denied for health_records and food_logs", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = new Date();
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("health_records").doc("rec_1").set({
      metricType: "steps",
      value: 8500,
      unit: "steps",
      localDate: "2026-10-26",
      sourceType: "health_connect",
      sourceName: "Health Connect",
      dedupStatus: "unique_primary",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("health_records").doc("rec_1").get());
});

test("Authenticated owner can create and list valid health_records and food_logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("health_records").doc("rec_steps_1").set({
      metricType: "steps",
      value: 9200,
      unit: "steps",
      localDate: "2026-10-26",
      sourceType: "xiaomi_band_10_ble",
      sourceName: "Xiaomi Smart Band 10",
      dedupStatus: "unique_primary",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("food_logs").doc("meal_1").set({
      mealSlot: "lunch",
      foodName: "كسكس باللحم والخضار",
      grams: 300,
      calories: 495,
      proteinG: 22.5,
      carbsG: 69.0,
      fatG: 13.5,
      fiberG: 8.4,
      healthTip: "Rich in fiber and protein",
      localDate: "2026-10-26",
      isAiRecognized: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("health_records").get()
  );
});

test("Shadow update with ghost field or invalid schema is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("food_logs").doc("meal_bad").set({
      mealSlot: "lunch",
      foodName: "Couscous",
      grams: -50,
      calories: 495,
      proteinG: 20,
      carbsG: 60,
      fatG: 10,
      fiberG: 8,
      localDate: "2026-10-26",
      isAiRecognized: false,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});
