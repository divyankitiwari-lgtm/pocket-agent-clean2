const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineString } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

const initialAdminEmail = defineString("INITIAL_ADMIN_EMAIL", {
  description: "Verified email allowed to claim the first JARVIS owner/admin role",
});

function requireSignedIn(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in before calling this function.");
  }
  return request.auth;
}

function requireVerifiedOwner(auth) {
  const configuredEmail = initialAdminEmail.value().trim().toLowerCase();
  const callerEmail = String(auth.token.email || "").trim().toLowerCase();

  if (auth.token.email_verified !== true || !configuredEmail || callerEmail !== configuredEmail) {
    throw new HttpsError("permission-denied", "This account is not the verified JARVIS owner.");
  }
}

exports.bootstrapFirstAdmin = onCall(async (request) => {
  const auth = requireSignedIn(request);
  requireVerifiedOwner(auth);

  const db = getFirestore();
  const lockRef = db.doc("system/adminBootstrap");
  const uid = auth.uid;
  const email = String(auth.token.email).trim().toLowerCase();

  // The transaction makes the initial claim one-time even if two devices call
  // the function at the same moment. Only the same owner can safely retry.
  await db.runTransaction(async (tx) => {
    const lock = await tx.get(lockRef);
    if (lock.exists) {
      if (lock.get("uid") !== uid) {
        throw new HttpsError("already-exists", "The initial admin has already been claimed.");
      }
      return;
    }
    tx.create(lockRef, {
      uid,
      email,
      createdAt: FieldValue.serverTimestamp(),
    });
  });

  try {
    const user = await getAuth().getUser(uid);
    await getAuth().setCustomUserClaims(uid, {
      ...(user.customClaims || {}),
      admin: true,
      role: "admin",
    });
    await db.doc("users/" + uid).set({
      email,
      role: "admin",
      adminGrantedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    return { success: true, message: "Initial admin role granted. Refresh your ID token." };
  } catch (error) {
    // Do not leave the one-time lock behind if the claim could not be applied.
    await db.runTransaction(async (tx) => {
      const lock = await tx.get(lockRef);
      if (lock.exists && lock.get("uid") === uid) tx.delete(lockRef);
    });
    console.error("bootstrapFirstAdmin failed", error);
    throw new HttpsError("internal", "Could not activate admin access. Check Cloud Functions logs.");
  }
});

exports.setAdminRole = onCall(async (request) => {
  const auth = requireSignedIn(request);
  requireVerifiedOwner(auth);

  if (auth.token.admin !== true) {
    throw new HttpsError("permission-denied", "The owner must first activate admin access.");
  }

  const email = String(request.data?.email || "").trim().toLowerCase();
  const makeAdmin = request.data?.admin;
  if (!email || email.length > 254 || !email.includes("@") || typeof makeAdmin !== "boolean") {
    throw new HttpsError("invalid-argument", "Provide a valid email and an admin true/false value.");
  }

  let target;
  try {
    target = await getAuth().getUserByEmail(email);
  } catch (_) {
    throw new HttpsError("not-found", "No Firebase Authentication account exists for that email.");
  }

  if (makeAdmin && target.emailVerified !== true) {
    throw new HttpsError("failed-precondition", "The target account must verify its email first.");
  }
  if (target.uid === auth.uid && makeAdmin === false) {
    throw new HttpsError("failed-precondition", "The owner cannot remove their own admin role here.");
  }

  const claims = { ...(target.customClaims || {}) };
  if (makeAdmin) {
    claims.admin = true;
    claims.role = "admin";
  } else {
    delete claims.admin;
    if (claims.role === "admin") delete claims.role;
  }

  await getAuth().setCustomUserClaims(target.uid, claims);
  await getFirestore().doc("users/" + target.uid).set({
    email,
    role: makeAdmin ? "admin" : "user",
    roleUpdatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  return { success: true, email, admin: makeAdmin };
});
