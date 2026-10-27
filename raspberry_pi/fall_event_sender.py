#!/usr/bin/env python3
"""기존 낙상 탐지 코드에서 send_fall_event()만 호출해 Firebase Functions로 전송합니다."""
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.request
import uuid

FALL_INGEST_URL = os.environ["CARE_GUARD_FALL_INGEST_URL"]
DEVICE_ID = os.environ["CARE_GUARD_DEVICE_ID"]
SHARED_SECRET = os.environ["CARE_GUARD_SHARED_SECRET"]


def send_fall_event(event_id: str | None = None, occurred_at: int | None = None) -> dict:
    """낙상 확정 직후 호출합니다. event_id를 재사용하면 네트워크 재시도도 중복 전송되지 않습니다."""
    event_id = event_id or uuid.uuid4().hex
    occurred_at = occurred_at or int(time.time() * 1000)
    event_type = "FALL"
    signing_text = f"{DEVICE_ID}.{event_id}.{event_type}.{occurred_at}"
    signature = hmac.new(
        SHARED_SECRET.encode("utf-8"), signing_text.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    payload = {
        "deviceId": DEVICE_ID,
        "eventId": event_id,
        "eventType": event_type,
        "occurredAt": occurred_at,
    }
    request = urllib.request.Request(
        FALL_INGEST_URL,
        data=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-CareGuard-Signature": signature,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Fall event rejected: HTTP {exc.code} {detail}") from exc


if __name__ == "__main__":
    # 기존 모델이 낙상을 확정한 자리에서 이 함수를 호출하세요.
    print(send_fall_event())
