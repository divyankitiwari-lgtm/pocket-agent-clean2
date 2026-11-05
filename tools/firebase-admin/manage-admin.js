#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { initializeApp, cert } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");

const [action, rawEmail] = process.argv.slice(2);
const email = String(rawEmail || "").trim().toLowerCase();
const allowedActions = new Set(["bootstrap", "grant", "revoke"]);

function fail(message) {
  console.error("\nERROR: " + message + "\n");
  process.exit(1);
}

async function main() {
  if (!allowedActions.has(action) || !email || !email.includes("@")) {
    fail("Usage: node tools/firebase-admin/manage-admin.js <bootstrap|grant|revoke> <account-email>");
  }

  const credentialPath = process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    path.resolve(__dirname, "../../serviceAccountKey.json");

  if (!fs.existsSync(credentialPath)) {
    fail("Service account file not found. Put serviceAccountKey.json in the repository root, or set GOOGLE_APPLICATION_CREDENTIALS.");
  }

  const serviceAccount = JSON.parse(fs.readFileSync(credentialPath, "utf8"));
  if (!serviceAccount.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
    fail("The credential file does not look like a Firebase service-account JSON file.");
  }

  initializeApp({
    credential: cert(serviceAccount),
    projectId: serviceAccount.project_id,
  });

  const auth = getAuth();
  const configuredOwner = String(process.env.INITIAL_ADMIN_EMAIL || "").trim().toLowerCase();

  if (action === "bootstrap") {
    if (!configuredOwner || email !== configuredOwner) {
      fail("Bootstrap is restricted to INITIAL_ADMIN_EMAIL. Set that environment variable to the intended owner email and retry.");
    }
  }

  if (action === "revoke" && configuredOwner && email === configuredOwner) {
    fail("The configured initial owner cannot be revoked by this utility.");
  }

  const user = await auth.getUserByEmail(email);
  if (!user.emailVerified) {
    fail("The target Firebase Authentication account must verify its email first.");
  }

  const claims = { ...(user.customClaims || {}) };

  if (action === "revoke") {
    delete claims.admin;
    if (claims.role === "admin") delete claims.role;
  } else {
    claims.admin = true;
    claims.role = "admin";
  }

  await auth.setCustomUserClaims(user.uid, claims);

  console.log(action === "revoke"
    ? "Admin access revoked for " + email
    : "Admin access granted to " + email);
  console.log("Project: " + serviceAccount.project_id);
  console.log("User must sign out and sign in again, or force-refresh their Firebase ID token, before the app sees the new role.");
}

main().catch((error) => {
  console.error("Firebase admin operation failed:", error.message || error);
  process.exitCode = 1;
});
