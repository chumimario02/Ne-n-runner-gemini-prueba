const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_runner";
const BOB_UID = "bob_runner";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function makeGridRows(width = 24) {
  const empty = ".".repeat(width);
  const floor = "#".repeat(width);
  const rows = [];
  for (let i = 0; i < 13; i++) {
    rows.push(empty);
  }
  rows.push(floor);
  return rows;
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("community_levels").where("published", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("progress").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("drafts").get());
  await assertFails(
    unauthDb.collection("community_levels").doc("lvl_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      title: "Hack",
      description: "",
      difficulty: "EASY",
      width: 24,
      height: 14,
      gridRows: makeGridRows(24),
      verified: true,
      published: true,
      playsCount: 0,
      likesCount: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Campaign progress: owner can create/update/list, cross-user denied, immutable fields protected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const progressRef = aliceDb.collection("users").doc(ALICE_UID).collection("progress").doc("sector_01");
  await assertSucceeds(
    progressRef.set({
      userId: ALICE_UID,
      levelIndex: 0,
      completed: true,
      stars: 2,
      bestTimeMs: 18500,
      bestScore: 450,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List own progress succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("progress").get());

  // Partial update succeeds
  await assertSucceeds(
    progressRef.update({
      stars: 3,
      bestScore: 700,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    progressRef.update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra id field or shadow field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("progress").doc("sector_02").set({
      id: "sector_02",
      userId: ALICE_UID,
      levelIndex: 1,
      completed: true,
      stars: 1,
      bestTimeMs: 25000,
      bestScore: 200,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("progress").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("progress").doc("sector_01").update({
      stars: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User drafts: owner CRUD works, optional publishedLevelId omitted or valid, cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const draftRef = aliceDb.collection("users").doc(ALICE_UID).collection("drafts").doc("draft_1");
  await assertSucceeds(
    draftRef.set({
      userId: ALICE_UID,
      title: "Mi Pista Cyber",
      description: "Saltos sobre laser",
      difficulty: "MEDIUM",
      width: 32,
      height: 14,
      gridRows: makeGridRows(32),
      verified: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    draftRef.update({
      verified: true,
      publishedLevelId: "comm_1",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("drafts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("drafts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("drafts").doc("draft_1").delete());
});

test("Community levels: publish, filtered query, play increment, atomic like/unlike, author protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const levelRef = aliceDb.collection("community_levels").doc("comm_1");
  await assertSucceeds(
    levelRef.set({
      authorId: ALICE_UID,
      authorName: "Alice",
      title: "Torre Electrica",
      description: "Nivel de prueba",
      difficulty: "HARD",
      width: 32,
      height: 14,
      gridRows: makeGridRows(32),
      verified: true,
      published: true,
      playsCount: 0,
      likesCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Unfiltered query on top-level community_levels must fail
  await assertFails(bobDb.collection("community_levels").get());

  // Filtered query matching rule (.where("published", "==", true)) succeeds
  await assertSucceeds(bobDb.collection("community_levels").where("published", "==", true).get());

  // Author filtered query succeeds
  await assertSucceeds(aliceDb.collection("community_levels").where("authorId", "==", ALICE_UID).get());

  // Bob cannot edit Alice's level title or delete it
  await assertFails(
    bobDb.collection("community_levels").doc("comm_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("community_levels").doc("comm_1").delete());

  // Bob can increment playsCount by +1
  await assertSucceeds(
    bobDb.collection("community_levels").doc("comm_1").update({
      playsCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot jump playsCount by +10
  await assertFails(
    bobDb.collection("community_levels").doc("comm_1").update({
      playsCount: 11,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot increment likesCount without atomic like doc
  await assertFails(
    bobDb.collection("community_levels").doc("comm_1").update({
      likesCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob atomic batch like succeeds
  const bobBatchLike = bobDb.batch();
  const bobLevelRef = bobDb.collection("community_levels").doc("comm_1");
  const bobLikeRef = bobLevelRef.collection("likes").doc(BOB_UID);
  bobBatchLike.set(bobLikeRef, {
    userId: BOB_UID,
    createdAt: new Date(Date.now() - 1000),
  });
  bobBatchLike.update(bobLevelRef, {
    likesCount: 1,
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertSucceeds(bobBatchLike.commit());

  // Bob atomic batch unlike succeeds
  const bobBatchUnlike = bobDb.batch();
  bobBatchUnlike.delete(bobLikeRef);
  bobBatchUnlike.update(bobLevelRef, {
    likesCount: 0,
    updatedAt: new Date(Date.now() - 500),
  });
  await assertSucceeds(bobBatchUnlike.commit());

  // Alice (author) can delete her own level
  await assertSucceeds(levelRef.delete());
});
