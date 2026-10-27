import { createHash, createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { FunctionsErrorCode, HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2/options";

initializeApp();

const REGION = "asia-northeast3";
const db = getFirestore();
setGlobalOptions({ region: REGION, maxInstances: 20 });

function stringValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function fail(code: FunctionsErrorCode, message: string): never {
  throw new HttpsError(code, message);
}

export const createGuardianInvite = onCall(async (request) => {
  if (!request.auth) fail("unauthenticated", "로그인이 필요합니다.");
  const guardianEmail = stringValue((request.data as { guardianEmail?: unknown })?.guardianEmail).toLowerCase();
  if (!guardianEmail.includes("@")) fail("invalid-argument", "보호자 이메일 주소를 확인해 주세요.");

  const patientUid = request.auth.uid;
  let guardianUid: string;
  try {
    guardianUid = (await getAuth().getUserByEmail(guardianEmail)).uid;
  } catch {
    fail("not-found", "해당 이메일의 CareGuard 계정을 찾지 못했습니다. 보호자가 먼저 가입해야 합니다.");
  }
  if (guardianUid === patientUid) fail("invalid-argument", "본인을 보호자로 등록할 수 없습니다.");
  const guardianAuth = await getAuth().getUser(guardianUid);
  if (!guardianAuth.emailVerified) fail("failed-precondition", "보호자는 가입 후 이메일 인증을 완료해야 초대를 받을 수 있습니다.");

  const [patientProfile, guardianProfile] = await Promise.all([
    db.collection("users").doc(patientUid).get(),
    db.collection("users").doc(guardianUid).get(),
  ]);
  const patientName = stringValue(patientProfile.get("displayName")) || "사용자";
  const guardianName = stringValue(guardianProfile.get("displayName")) || guardianEmail;
  const inviteRef = db.collection("guardianInvites").doc(`${patientUid}_${guardianUid}`);
  const guardianRef = db.collection("users").doc(patientUid).collection("guardians").doc(guardianUid);

  await db.runTransaction(async (transaction) => {
    const current = await transaction.get(guardianRef);
    if (current.exists && current.get("status") === "ACCEPTED") {
      fail("already-exists", "이미 승인된 보호자입니다.");
    }
    const now = FieldValue.serverTimestamp();
    transaction.set(guardianRef, {
      guardianUid,
      guardianEmail,
      guardianName,
      status: "PENDING",
      requestedAt: now,
    });
    transaction.set(inviteRef, {
      patientUid,
      patientName,
      guardianUid,
      guardianEmail,
      status: "PENDING",
      requestedAt: now,
    });
  });

  return { inviteId: inviteRef.id };
});

export const acceptGuardianInvite = onCall(async (request) => {
  if (!request.auth) fail("unauthenticated", "로그인이 필요합니다.");
  const inviteId = stringValue((request.data as { inviteId?: unknown })?.inviteId);
  if (!inviteId) fail("invalid-argument", "초대 식별자가 필요합니다.");

  const guardianUid = request.auth.uid;
  const inviteRef = db.collection("guardianInvites").doc(inviteId);
  await db.runTransaction(async (transaction) => {
    const invite = await transaction.get(inviteRef);
    if (!invite.exists) fail("not-found", "보호자 요청을 찾지 못했습니다.");
    if (invite.get("guardianUid") !== guardianUid) fail("permission-denied", "다른 사용자의 요청입니다.");
    if (invite.get("status") !== "PENDING") fail("failed-precondition", "이미 처리된 요청입니다.");

    const patientUid = stringValue(invite.get("patientUid"));
    const patientName = stringValue(invite.get("patientName")) || "사용자";
    const now = FieldValue.serverTimestamp();
    transaction.update(inviteRef, { status: "ACCEPTED", acceptedAt: now });
    transaction.update(
      db.collection("users").doc(patientUid).collection("guardians").doc(guardianUid),
      { status: "ACCEPTED", acceptedAt: now }
    );
    transaction.set(
      db.collection("users").doc(guardianUid).collection("patients").doc(patientUid),
      { patientUid, patientName, acceptedAt: now },
      { merge: true }
    );
  });

  return { accepted: true };
});

/**
 * 앱에서 한 번만 보여 줄 Pi 공유 비밀키를 만든다.
 * 비밀키는 앱/클라이언트 규칙으로 읽을 수 없는 piDevices 컬렉션에만 보관한다.
 */
export const createPiPairing = onCall(async (request) => {
  if (!request.auth) fail("unauthenticated", "로그인이 필요합니다.");
  const deviceName = stringValue((request.data as { deviceName?: unknown })?.deviceName);
  if (!deviceName) fail("invalid-argument", "기기 이름을 입력해 주세요.");

  const deviceId = `pi_${randomBytes(12).toString("hex")}`;
  const sharedSecret = randomBytes(32).toString("hex");
  await db.collection("piDevices").doc(deviceId).set({
    patientId: request.auth.uid,
    deviceName: deviceName.slice(0, 60),
    sharedSecret,
    enabled: true,
    createdAt: FieldValue.serverTimestamp(),
  });
  return { deviceId, deviceName, sharedSecret };
});

/** Pi가 HMAC 서명한 낙상 이벤트를 POST한다. */
export const receiveFallEvent = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.set("Allow", "POST").status(405).json({ error: "method_not_allowed" });
    return;
  }

  const body = (req.body ?? {}) as Record<string, unknown>;
  const deviceId = stringValue(body.deviceId);
  const eventId = stringValue(body.eventId);
  const eventType = stringValue(body.eventType);
  const occurredAt = Number(body.occurredAt);
  const signature = stringValue(req.header("x-careguard-signature"));
  if (!/^pi_[a-f0-9]{24}$/.test(deviceId) || !/^[A-Za-z0-9_-]{8,64}$/.test(eventId) || eventType !== "FALL" || !Number.isFinite(occurredAt) || !signature) {
    res.status(400).json({ error: "invalid_payload" });
    return;
  }

  const device = await db.collection("piDevices").doc(deviceId).get();
  if (!device.exists || device.get("enabled") !== true) {
    res.status(403).json({ error: "unknown_or_disabled_device" });
    return;
  }
  const signingText = `${deviceId}.${eventId}.${eventType}.${occurredAt}`;
  const expected = createHmac("sha256", stringValue(device.get("sharedSecret"))).update(signingText).digest("hex");
  const signatureBuffer = Buffer.from(signature, "hex");
  const expectedBuffer = Buffer.from(expected, "hex");
  if (signatureBuffer.length !== expectedBuffer.length || !timingSafeEqual(signatureBuffer, expectedBuffer)) {
    res.status(401).json({ error: "invalid_signature" });
    return;
  }

  const alertRef = db.collection("alerts").doc(`pi_${deviceId}_${eventId}`);
  const exists = await alertRef.get();
  if (exists.exists) {
    res.status(200).json({ ok: true, duplicate: true, alertId: alertRef.id });
    return;
  }

  await alertRef.create({
    patientId: device.get("patientId"),
    createdBy: `pi:${deviceId}`,
    source: "raspberry_pi",
    type: "FALL",
    message: "라즈베리 파이가 낙상 가능 이벤트를 감지했습니다.",
    deviceId,
    occurredAt,
    status: "OPEN",
    createdAt: FieldValue.serverTimestamp(),
  });
  res.status(201).json({ ok: true, alertId: alertRef.id });
});

/** 새 안전 이벤트를 승인된 보호자의 모든 설치 ID에 전송한다. */
export const notifyGuardiansOnAlert = onDocumentCreated({ document: "alerts/{alertId}", retry: true }, async (event) => {
  const alertSnapshot = event.data;
  if (!alertSnapshot) return;
  const alert = alertSnapshot.data();
  const patientId = stringValue(alert.patientId);
  if (!patientId) {
    logger.error("Alert without patientId", { alertId: event.params.alertId });
    return;
  }

  const guardianLinks = await db.collection("users").doc(patientId).collection("guardians")
    .where("status", "==", "ACCEPTED")
    .get();
  const targets: Array<{ guardianUid: string; fid: string; token: string }> = [];
  for (const guardian of guardianLinks.docs) {
    const devices = await db.collection("users").doc(guardian.id).collection("devices").get();
    devices.docs.forEach((device) => {
      const token = stringValue(device.get("token"));
      const fid = stringValue(device.get("fid")) || device.id;
      if (token) targets.push({ guardianUid: guardian.id, fid, token });
    });
  }
  const uniqueTargets = [...new Map(targets.map((target) => [target.fid, target])).values()];
  if (uniqueTargets.length === 0) {
    await alertSnapshot.ref.update({ notificationStatus: "NO_GUARDIAN_DEVICE" });
    return;
  }

  let sent = 0;
  let retryableFailures = 0;
  for (const target of uniqueTargets) {
    const deliveryKey = createHash("sha256").update(target.fid).digest("hex");
    const deliveryRef = alertSnapshot.ref.collection("_system").doc(`delivery_${deliveryKey}`);
    const shouldSend = await db.runTransaction(async (transaction) => {
      const delivery = await transaction.get(deliveryRef);
      if (delivery.exists && delivery.get("status") === "SENT") return false;
      const leaseUntil = Number(delivery.get("leaseUntil") ?? 0);
      if (delivery.exists && delivery.get("status") === "PROCESSING" && leaseUntil > Date.now()) return false;
      const attempts = Number(delivery.get("attempts") ?? 0);
      if (attempts >= 5) return false;
      transaction.set(deliveryRef, {
        guardianUid: target.guardianUid,
        fid: target.fid,
        status: "PROCESSING",
        attempts: attempts + 1,
        leaseUntil: Date.now() + 120_000,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      return true;
    });
    if (!shouldSend) continue;

    try {
      await getMessaging().send({
        token: target.token,
        notification: {
          title: "CareGuard 안전 알림",
          body: "새로운 안전 이벤트가 감지되었습니다. 앱에서 상세 내용을 확인하세요.",
        },
        data: {
          alertId: event.params.alertId,
          patientId,
          type: stringValue(alert.type),
        },
        android: {
          priority: "high",
          notification: { channelId: "guardian_alerts", sound: "default" },
        },
      });
      sent += 1;
      await deliveryRef.set({ status: "SENT", sentAt: FieldValue.serverTimestamp(), leaseUntil: 0 }, { merge: true });
    } catch (error) {
      const code = (error as { code?: string })?.code ?? "unknown";
      const permanent = code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token";
      await deliveryRef.set({
        status: permanent ? "FAILED_PERMANENT" : "RETRYABLE",
        errorCode: code,
        leaseUntil: 0,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
      if (!permanent) retryableFailures += 1;
      logger.warn("Guardian push delivery failed", { alertId: event.params.alertId, fid: target.fid, code });
    }
  }
  const deliverySnapshot = await alertSnapshot.ref.collection("_system").get();
  const successfulDeliveries = deliverySnapshot.docs.filter((doc) => doc.get("status") === "SENT").length;
  await alertSnapshot.ref.update({
    notificationStatus: retryableFailures > 0 ? "RETRYING" : (successfulDeliveries > 0 ? "SENT" : "FAILED"),
    notificationTargets: uniqueTargets.length,
    notificationSuccesses: successfulDeliveries,
    notifiedAt: FieldValue.serverTimestamp(),
  });
  if (retryableFailures > 0) throw new Error(`${retryableFailures} guardian notification(s) failed temporarily.`);
});
