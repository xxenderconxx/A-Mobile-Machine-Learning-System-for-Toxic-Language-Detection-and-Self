"""Read aggregate research counters from Firestore without exporting identifiers."""
from __future__ import annotations

import csv
import json
import math
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from statistics import mean

COLLECTION = "research_reports"
COUNTER_FIELDS = (
    "total_detections",
    "user_corrections",
    "ignored_alerts",
    "self_correction_rate",
)


def fail(message: str) -> "NoReturn":
    print(message, file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    script_path = Path(__file__).resolve()
    config_candidates = [
        parent / "ToxicLanguageKeyboard" / "app" / "google-services.json"
        for parent in script_path.parents
    ]
    project_config = next((candidate for candidate in config_candidates if candidate.is_file()), None)
    if project_config is None:
        fail("Could not find ToxicLanguageKeyboard/app/google-services.json beside this script or in a parent folder.")
    try:
        project_id = json.loads(project_config.read_text(encoding="utf-8"))["project_info"]["project_id"]
    except (OSError, KeyError, json.JSONDecodeError):
        fail("Could not read the Firebase project ID from the app config.")

    try:
        from google.cloud import firestore
    except ImportError:
        fail("Firestore library missing. Install it with: py -m pip install -r requirements-counter-export.txt")

    try:
        # Application Default Credentials and Cloud IAM are required; this server client
        # does not use or bypass Firestore client security rules.
        db = firestore.Client(project=project_id)
        query = db.collection(COLLECTION).select(["userId", "timestamp", *COUNTER_FIELDS])
        documents = list(query.stream())
    except Exception as error:
        error_name = type(error).__name__
        if error_name in {"DefaultCredentialsError", "RefreshError"}:
            fail("Google Cloud credentials are missing or expired. Run: gcloud auth application-default login")
        if error_name in {"PermissionDenied", "Forbidden"}:
            fail("This Google account needs read-only IAM access to the project's Firestore data (for example, Cloud Datastore Viewer).")
        fail(f"Firestore read failed ({error_name}). Check project access, network, and collection name.")

    # Uploads append snapshots. Keep only the most recent snapshot per authenticated UID,
    # but never write the UID or document ID to an output file or terminal.
    latest: dict[str, tuple[datetime, dict]] = {}
    missing_user_ids = 0
    for document in documents:
        record = document.to_dict() or {}
        uid = record.get("userId")
        if isinstance(uid, bool) or not isinstance(uid, (str, int)) or not str(uid):
            missing_user_ids += 1
            continue
        uid_key = str(uid)
        timestamp = record.get("timestamp") or document.create_time
        if not isinstance(timestamp, datetime):
            timestamp = datetime.min.replace(tzinfo=timezone.utc)
        previous = latest.get(uid_key)
        if previous is None or timestamp > previous[0]:
            latest[uid_key] = (timestamp, record)

    selected: list[dict] = []
    incomplete = 0
    invalid = 0
    empty = 0
    for _, record in latest.values():
        if all(record.get(field) in (None, 0, 0.0) for field in COUNTER_FIELDS):
            empty += 1
            continue
        if any(field not in record or record[field] is None for field in COUNTER_FIELDS):
            incomplete += 1
            continue
        detections, edits, ignored, stored_rate = (record[field] for field in COUNTER_FIELDS)
        if (type(detections) is not int or type(edits) is not int or type(ignored) is not int
                or not isinstance(stored_rate, (int, float)) or isinstance(stored_rate, bool)
                or not math.isfinite(stored_rate) or min(detections, edits, ignored) < 0
                or edits + ignored > detections):
            invalid += 1
            continue
        selected.append({
            "total_detections": detections,
            "user_corrections": edits,
            "ignored_alerts": ignored,
            "self_correction_rate": float(stored_rate),
        })

    output_dir = script_path.parent
    csv_path = output_dir / "research-counters.csv"
    summary_path = output_dir / "research-counters-summary.json"
    fields = ["record_number", *COUNTER_FIELDS]
    with csv_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for number, record in enumerate(selected, start=1):
            writer.writerow({"record_number": number, **record})

    detections = sum(record["total_detections"] for record in selected)
    edits = sum(record["user_corrections"] for record in selected)
    ignored = sum(record["ignored_alerts"] for record in selected)
    summary = {
        "project_id": project_id,
        "collection": COLLECTION,
        "documents_read": len(documents),
        "unique_accounts_with_report": len(latest),
        "empty_latest_reports_excluded": empty,
        "incomplete_reports_excluded": incomplete,
        "invalid_reports_excluded": invalid,
        "reports_missing_account_id_excluded": missing_user_ids,
        "usable_latest_reports": len(selected),
        "total_detections": detections,
        "total_corrections": edits,
        "total_ignored": ignored,
        "pooled_correction_rate_percent": 100 * edits / detections if detections else None,
        "pooled_ignored_rate_percent": 100 * ignored / detections if detections else None,
        "mean_stored_self_correction_rate_percent": mean(r["self_correction_rate"] for r in selected) if selected else None,
        "weighted_stored_self_correction_rate_percent": (
            sum(r["total_detections"] * r["self_correction_rate"] for r in selected) / detections
            if detections else None
        ),
        "outputs_contain_user_ids_or_timestamps": False,
    }
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    print(f"Saved counters only to {csv_path.name} and {summary_path.name}")


if __name__ == "__main__":
    main()
