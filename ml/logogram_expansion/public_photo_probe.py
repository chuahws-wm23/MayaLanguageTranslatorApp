#!/usr/bin/env python3
"""Reproduce a public-photo DEVELOPMENT probe; never invent expert sign labels."""
import argparse
import concurrent.futures
import datetime
import hashlib
import json
import subprocess
from pathlib import Path
import time
import urllib.parse
import urllib.request

PINNED_PHOTO_SHA256 = {
    "carlos-seven-ajaw": "353e476b612dee99be429521f99de6c18f9c2f25987fca9f25c200ab353013a3",
    "inah-ceramic": "45d808207ac93552dde9017fe49e3b4769e9e2fe90ad4f74465d1e75a22ddeeb",
    "palenque-head": "6e60ac210fad17fa80c10437918797d317f4ad26411de087a7ee648a8547b7d2",
    "princeton-ajaw": "99a0ae2a1c43c9c431703d593f1d2cbdbaa6ebffe29b5c6d5c9bf62330ee5e09",
    "palenque-stone": "bf861cb11810004d0372e8b51f00268a83cb692a6be8825ecd25a1f1a52c9819"
}

def samples():
    rows = [
        dict(id="princeton-ajaw", source_group="princeton-y1983-51",
             url="https://media.artmuseum.princeton.edu/iiif/3/collection/INV014967/full/!1024,1024/0/default.jpg",
             source_url="https://artmuseum.princeton.edu/art/collections/objects/32863",
             license="Public Domain (museum download permission)", creator="Princeton University Art Museum",
             expected_readings=["AJAW"], label_status="museum title; reading only, exact sign unverified",
             content="single sculptural glyph"),
        dict(id="carlos-seven-ajaw", filename='Maya Ceramic, Late Classic, "7 Ahau" Glyph, 711-889 AD (10452410093).jpg',
             source_group="carlos-seven-ahau-vessel", expected_readings=["AJAW"],
             label_status="photographer title; reading only, exact sign unverified",
             content="whole vessel containing a numeral and glyph"),
        dict(id="inah-ceramic", filename="Classic Maya Ceramic Glyph (9756961171).jpg",
             source_group="inah-ceramic-9756961171", expected_readings=[],
             label_status="no verified sign label", content="ceramic glyph"),
        dict(id="palenque-head", filename='Classic Maya Stucco Glyph "Head Variant," Forgotten Temple, Palenque (9756977652).jpg',
             source_group="palenque-forgotten-temple-stucco", expected_readings=[],
             label_status="no verified sign label", content="stucco head-variant glyph"),
        dict(id="palenque-stone", filename="Language Glyph, Palenque, Late Classic, 600-900 AD 01.jpg",
             source_group="palenque-language-glyph-15647167217", expected_readings=[],
             label_status="no verified sign label", content="stone inscription fragment"),
    ]
    for row in rows:
        if "filename" in row:
            name = row["filename"].replace(" ", "_")
            digest = hashlib.md5(name.encode()).hexdigest()
            row["url"] = "https://upload.wikimedia.org/wikipedia/commons/" + digest[0] + "/" + digest[:2] + "/" + urllib.parse.quote(name)
            row["source_url"] = "https://commons.wikimedia.org/wiki/File:" + urllib.parse.quote(name)
            row["creator"] = "Gary Todd"
            row["license"] = "CC0 1.0"
            row["license_url"] = "https://creativecommons.org/publicdomain/zero/1.0/"
        row["file"] = row["id"] + ".jpg"
        row["label_is_expert_verified_exact_sign"] = False
    return rows

def prepare(root):
    from PIL import Image
    root.mkdir(parents=True, exist_ok=False)
    rows = samples()
    def download(row):
        req = urllib.request.Request(row["url"], headers={
            "User-Agent": "MayaGlyphPhotoProbe/1.0 (https://github.com/chuahws-wm23/MayaLanguageTranslatorApp)"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(req, timeout=45) as response:
                    data = response.read(20 * 1024 * 1024 + 1)
                if len(data) > 20 * 1024 * 1024:
                    raise ValueError("Image too large")
                break
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(2 ** attempt)
        if hashlib.sha256(data).hexdigest() != PINNED_PHOTO_SHA256[row["id"]]:
            raise ValueError("Source photo bytes changed; review provenance before updating the pinned hash: " + row["id"])
        path = root / row["file"]
        path.write_bytes(data)
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            row["width"], row["height"] = image.size
        row["sha256"] = hashlib.sha256(data).hexdigest()
        print(row["id"], len(data), row["sha256"])
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(download, rows))
    # Selected before inference from the photographer-identified AJAW motif.
    # Both views share ONE artifact/source group; neither is a new independent example.
    closeup = dict(rows[1])
    closeup.update(id="carlos-ajaw-closeup", file="carlos-ajaw-closeup.png",
                   content="operator-cropped AJAW close-up; diagnostic only",
                   derived_from=rows[1]["file"], crop_pixels=[840, 780, 1860, 1900])
    with Image.open(root / rows[1]["file"]) as image:
        if image.size != (3888, 2592):
            raise ValueError("Source dimensions changed; review the documented crop")
        image.crop(tuple(closeup["crop_pixels"])).save(root / closeup["file"])
    closeup.update(width=1020, height=1120,
                   sha256=hashlib.sha256((root / closeup["file"]).read_bytes()).hexdigest())
    rows.append(closeup)
    manifest = dict(format_version=1,
                    study="Public-photograph development probe; not an expert-verified accuracy benchmark",
                    retrieved_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    inputs=len(rows), source_groups=len({r["source_group"] for r in rows}),
                    independent_expert_verified_test=False, release_approved=False, samples=rows)
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest

def summarize(path):
    report = json.loads(path.read_text())
    summary = dict(study=report["study"], inputs=report["inputs"],
                   source_groups=report["source_groups"], runtime_errors=report["runtime_errors"],
                   release_approved=False, cases=[])
    for row in report["results"]:
        case = dict(id=row["id"], source_group=row["source_group"],
                    expected_readings=row["expected_readings"], label_status=row["label_status"],
                    production_top5=[c["reading"] for c in row.get("production_candidates", [])],
                    research_illumination_top5=[c["reading"] for c in row.get("research_illumination_candidates", [])],
                    region_proposal_count=row.get("region_proposal_count"),
                    production_message=row.get("production_message"), runtime_error=row.get("runtime_error"))
        for prefix in ["production", "research_illumination"]:
            for k in [1, 5]:
                name = prefix + "_top" + str(k) + "_contains_source_reading"
                if name in row:
                    case[name] = row[name]
        summary["cases"].append(case)
    print("PHOTO_PROBE_SUMMARY " + json.dumps(summary, separators=(",", ":")))
    path.with_name("public-photo-summary.json").write_text(json.dumps(summary, indent=2) + "\n")

def run_android():
    result = subprocess.run([
        "gradle", "connectedDebugAndroidTest",
        "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true",
        "-Pandroid.testInstrumentationRunnerArguments.class=com.chuahws.mayalanguageapp.presentation.translation.PublicGlyphPhotoProbeTest",
        "--stacktrace",
    ], check=False)
    report = Path("public-photo-report.json")
    pull = subprocess.run([
        "adb", "pull",
        "/sdcard/Android/data/com.chuahws.mayalanguageapp/files/screenshots/public-photo-development-report.json",
        str(report),
    ], check=False, timeout=60)
    if report.exists():
        summarize(report)
    raise SystemExit(result.returncode or pull.returncode)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("app/src/androidTest/assets/public_photo_probe"))
    parser.add_argument("--summarize", type=Path)
    parser.add_argument("--run-android", action="store_true")
    args = parser.parse_args()
    if args.run_android:
        run_android()
    elif args.summarize:
        summarize(args.summarize)
    else:
        prepare(args.root)
