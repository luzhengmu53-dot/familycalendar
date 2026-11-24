const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-5cd9dabb-0941-4554-826f-6ffa1297900d";
const ALICE_UID = "alice_owner_1";
const BOB_UID = "bob_member_2";
const EVE_UID = "eve_stranger_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTs() {
  return new Date(Date.now() - 2000);
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

async function seedFamilyWithAliceAndBob() {
  const ts = pastTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("families").doc("fam_1").set({
      name: "佐藤家",
      ownerUid: ALICE_UID,
      ownerEmail: "alice@example.com",
      inviteCode: "FAM12345",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("families").doc("fam_1").collection("members").doc(ALICE_UID).set({
      uid: ALICE_UID,
      familyId: "fam_1",
      displayName: "ママ",
      email: "alice@example.com",
      colorHex: "#E07A5F",
      avatarSymbol: "マ",
      role: "owner",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("families").doc("fam_1").collection("members").doc(BOB_UID).set({
      uid: BOB_UID,
      familyId: "fam_1",
      displayName: "パパ",
      email: "bob@example.com",
      colorHex: "#4F86C6",
      avatarSymbol: "パ",
      role: "member",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    });
  });
}

test("Unauthenticated user cannot read users, families, or events", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("families").doc("fam_1").get());
  await assertFails(unauthDb.collection("families").doc("fam_1").collection("events").get());
});

test("User can create and read own profile, but cannot read another user's profile", async () => {
  const ts = pastTs();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      colorHex: "#4285F4",
      avatarSymbol: "🏠",
      avatarImageBase64: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      colorHex: "#EA4335",
      avatarSymbol: "🌸",
      avatarImageBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
      updatedAt: pastTs(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Family creation batch creates family and owner member atomically", async () => {
  const ts = pastTs();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("families").doc("fam_new"), {
    name: "田中家",
    iconSymbol: "⚽",
    colorHex: "#34A853",
    iconImageBase64: "",
    ownerUid: ALICE_UID,
    ownerEmail: "alice@example.com",
    inviteCode: "ABCD1234",
    memberUids: [ALICE_UID],
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(aliceDb.collection("families").doc("fam_new").collection("members").doc(ALICE_UID), {
    uid: ALICE_UID,
    familyId: "fam_new",
    displayName: "ママ",
    email: "alice@example.com",
    colorHex: "#E07A5F",
    avatarSymbol: "マ",
    role: "owner",
    memberUids: [ALICE_UID],
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(batch.commit());

  // Owner can customize calendar icon and name
  await assertSucceeds(
    aliceDb.collection("families").doc("fam_new").update({
      name: "田中家サッカー",
      iconSymbol: "🎵",
      colorHex: "#4285F4",
      updatedAt: pastTs(),
    })
  );
});

test("Stranger cannot read family or events, and cannot submit joinRequest with wrong inviteCode", async () => {
  await seedFamilyWithAliceAndBob();
  const ts = pastTs();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("families").doc("fam_1").collection("events").doc("evt_secret").set({
      familyId: "fam_1",
      title: "家族旅行",
      description: "",
      location: "",
      dateKey: "2026-11-25",
      startMinutes: 540,
      endMinutes: 1080,
      isAllDay: true,
      assignedMemberUid: "ALL",
      createdByUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    });
  });

  await assertFails(eveDb.collection("families").doc("fam_1").get());
  await assertFails(eveDb.collection("families").doc("fam_1").collection("events").doc("evt_secret").get());
  await assertFails(eveDb.collection("families").doc("fam_1").collection("events").get());
  await assertFails(
    eveDb.collection("families").doc("fam_1").collection("events").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Wrong inviteCode fails
  await assertFails(
    eveDb.collection("families").doc("fam_1").collection("joinRequests").doc(EVE_UID).set({
      familyId: "fam_1",
      familyOwnerUid: ALICE_UID,
      requesterUid: EVE_UID,
      requesterName: "Eve",
      requesterEmail: "eve@example.com",
      inviteCode: "WRONGCODE",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Correct inviteCode succeeds
  await assertSucceeds(
    eveDb.collection("families").doc("fam_1").collection("joinRequests").doc(EVE_UID).set({
      familyId: "fam_1",
      familyOwnerUid: ALICE_UID,
      requesterUid: EVE_UID,
      requesterName: "Eve",
      requesterEmail: "eve@example.com",
      inviteCode: "FAM12345",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Stranger cannot self-approve
  await assertFails(
    eveDb.collection("families").doc("fam_1").collection("joinRequests").doc(EVE_UID).update({
      status: "approved",
      updatedAt: pastTs(),
    })
  );
});

test("Owner can approve joinRequest and add member; member can customize own color", async () => {
  await seedFamilyWithAliceAndBob();
  const ts = pastTs();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    eveDb.collection("families").doc("fam_1").collection("joinRequests").doc(EVE_UID).set({
      familyId: "fam_1",
      familyOwnerUid: ALICE_UID,
      requesterUid: EVE_UID,
      requesterName: "ゆうた",
      requesterEmail: "eve@example.com",
      inviteCode: "FAM12345",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const ts2 = pastTs();
  const batch = aliceDb.batch();
  batch.update(aliceDb.collection("families").doc("fam_1").collection("joinRequests").doc(EVE_UID), {
    status: "approved",
    updatedAt: ts2,
  });
  batch.update(aliceDb.collection("families").doc("fam_1"), {
    memberUids: [ALICE_UID, BOB_UID, EVE_UID],
    updatedAt: ts2,
  });
  batch.set(aliceDb.collection("families").doc("fam_1").collection("members").doc(EVE_UID), {
    uid: EVE_UID,
    familyId: "fam_1",
    displayName: "ゆうた",
    email: "eve@example.com",
    colorHex: "#6B9080",
    avatarSymbol: "ゆ",
    role: "member",
    memberUids: [ALICE_UID, BOB_UID, EVE_UID],
    createdAt: ts2,
    updatedAt: ts2,
  });
  await assertSucceeds(batch.commit());

  // Bob can customize his own colorHex, displayName, and avatarImageBase64, but cannot change Alice's color or escalate role
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    bobDb.collection("families").doc("fam_1").collection("members").doc(BOB_UID).update({
      colorHex: "#9D8DF1",
      displayName: "パパ(変更)",
      avatarImageBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("families").doc("fam_1").collection("members").doc(BOB_UID).update({
      avatarImageBase64: "A".repeat(70000),
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("families").doc("fam_1").collection("members").doc(BOB_UID).update({
      role: "owner",
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("families").doc("fam_1").collection("members").doc(ALICE_UID).update({
      colorHex: "#000000",
      updatedAt: pastTs(),
    })
  );
});

test("Family members can create, list (with memberUids filter), edit, and delete events", async () => {
  await seedFamilyWithAliceAndBob();
  const ts = pastTs();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const eventRef = bobDb.collection("families").doc("fam_1").collection("events").doc("evt_1");

  await assertSucceeds(
    eventRef.set({
      familyId: "fam_1",
      title: "参観日",
      description: "体育館",
      location: "小学校",
      dateKey: "2026-11-20",
      startMinutes: 600,
      endMinutes: 720,
      isAllDay: false,
      assignedMemberUid: "ALL",
      createdByUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unfiltered list query fails (rules are not filters)
  await assertFails(bobDb.collection("families").doc("fam_1").collection("events").get());

  // Filtered list query succeeds for Alice
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    aliceDb.collection("families").doc("fam_1").collection("events")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Alice can edit the shared family event
  await assertSucceeds(
    aliceDb.collection("families").doc("fam_1").collection("events").doc("evt_1").update({
      title: "授業参観",
      updatedAt: pastTs(),
    })
  );

  // Shadow update with ghost field fails
  await assertFails(
    aliceDb.collection("families").doc("fam_1").collection("events").doc("evt_1").update({
      title: "Hack",
      isAdmin: true,
      updatedAt: pastTs(),
    })
  );

  // Valid reminderMinutesBefore update succeeds, invalid out-of-range fails
  await assertSucceeds(
    aliceDb.collection("families").doc("fam_1").collection("events").doc("evt_1").update({
      reminderMinutesBefore: 15,
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    aliceDb.collection("families").doc("fam_1").collection("events").doc("evt_1").update({
      reminderMinutesBefore: 9999,
      updatedAt: pastTs(),
    })
  );
});

test("Personal calendar blocks joinRequests and single-member calendar can be deleted", async () => {
  const ts = pastTs();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore(DATABASE_ID);

  await assertSucceeds(
    aliceDb.collection("families").doc("fam_personal").set({
      name: "私用カレンダー",
      iconSymbol: "📅",
      colorHex: "#D97757",
      iconImageBase64: "",
      calendarType: "personal",
      ownerUid: ALICE_UID,
      ownerEmail: "alice@example.com",
      inviteCode: "PERS1234",
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("families").doc("fam_personal").collection("members").doc(ALICE_UID).set({
      uid: ALICE_UID,
      familyId: "fam_personal",
      displayName: "Alice",
      email: "alice@example.com",
      colorHex: "#4285F4",
      avatarSymbol: "🏠",
      role: "owner",
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Outsider cannot submit joinRequest to a personal calendar even with inviteCode
  await assertFails(
    eveDb.collection("families").doc("fam_personal").collection("joinRequests").doc(EVE_UID).set({
      familyId: "fam_personal",
      familyOwnerUid: ALICE_UID,
      requesterUid: EVE_UID,
      requesterName: "Eve",
      requesterEmail: "eve@example.com",
      inviteCode: "PERS1234",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Single-member owner can delete member doc and family doc
  const delBatch = aliceDb.batch();
  delBatch.delete(aliceDb.collection("families").doc("fam_personal").collection("members").doc(ALICE_UID));
  delBatch.delete(aliceDb.collection("families").doc("fam_personal"));
  await assertSucceeds(delBatch.commit());
});

test("Multi-member shared calendar cannot be deleted directly, but member or owner can leave (退会) with handoff", async () => {
  await seedFamilyWithAliceAndBob();
  const ts = pastTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore(DATABASE_ID);
    await db.collection("families").doc("fam_1").collection("events").doc("evt_shared").set({
      familyId: "fam_1",
      title: "共有イベント",
      description: "",
      location: "",
      dateKey: "2026-11-28",
      startMinutes: 600,
      endMinutes: 660,
      isAllDay: false,
      assignedMemberUid: "ALL",
      reminderMinutesBefore: 10,
      createdByUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);

  // Owner cannot delete family while another member is still present
  await assertFails(aliceDb.collection("families").doc("fam_1").delete());

  // Bob cannot kick Alice out
  await assertFails(
    bobDb.collection("families").doc("fam_1").update({
      memberUids: [BOB_UID],
      updatedAt: pastTs(),
    })
  );

  // Owner Alice leaves shared calendar and transfers ownership to Bob atomically
  const tsLeave = pastTs();
  const ownerLeaveBatch = aliceDb.batch();
  ownerLeaveBatch.update(aliceDb.collection("families").doc("fam_1"), {
    ownerUid: BOB_UID,
    ownerEmail: "bob@example.com",
    memberUids: [BOB_UID],
    updatedAt: tsLeave,
  });
  ownerLeaveBatch.update(aliceDb.collection("families").doc("fam_1").collection("members").doc(BOB_UID), {
    role: "owner",
    memberUids: [BOB_UID],
    updatedAt: tsLeave,
  });
  ownerLeaveBatch.update(aliceDb.collection("families").doc("fam_1").collection("events").doc("evt_shared"), {
    memberUids: [BOB_UID],
    updatedAt: tsLeave,
  });
  ownerLeaveBatch.delete(aliceDb.collection("families").doc("fam_1").collection("members").doc(ALICE_UID));
  await assertSucceeds(ownerLeaveBatch.commit());

  // Now Bob is the sole remaining member & owner, so Bob can delete the calendar
  const bobDeleteBatch = bobDb.batch();
  bobDeleteBatch.delete(bobDb.collection("families").doc("fam_1").collection("events").doc("evt_shared"));
  bobDeleteBatch.delete(bobDb.collection("families").doc("fam_1").collection("members").doc(BOB_UID));
  bobDeleteBatch.delete(bobDb.collection("families").doc("fam_1"));
  await assertSucceeds(bobDeleteBatch.commit());
});

