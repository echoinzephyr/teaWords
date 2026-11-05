"""Generate the public, token-free fallback feed from GitHub releases."""
import argparse
import json
import os
from pathlib import Path
import re
from urllib.request import Request, urlopen

REPOSITORY = "echoinzephyr/teaWords"


def public_releases(releases):
    result = []
    for release in releases:
        if release.get("draft"):
            continue
        tag = release["tag_name"]
        if not re.fullmatch(r"[vV]?\d+(?:\.\d+)*(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?", tag):
            continue
        item = {key: release.get(key) for key in
                ("tag_name", "name", "body", "html_url", "published_at", "prerelease")}
        item["draft"] = False
        item["assets"] = []
        for asset in release.get("assets", []):
            if not asset["name"].lower().endswith(".apk"):
                continue
            assert asset["browser_download_url"].startswith(f"https://github.com/{REPOSITORY}/releases/download/")
            assert 0 < asset["size"] <= 536_870_912
            assert re.fullmatch(r"sha256:[a-fA-F0-9]{64}", asset.get("digest", "")), "APK digest missing"
            item["assets"].append({key: asset[key] for key in ("name", "browser_download_url", "size", "digest")})
        result.append(item)
    return {"schema": 1, "repository": REPOSITORY, "releases": result}


def fetch_releases():
    releases = []
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "teaWords-update-manifest"}
    if os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
    for page in range(1, 101):
        request = Request(f"https://api.github.com/repos/{REPOSITORY}/releases?per_page=100&page={page}", headers=headers)
        with urlopen(request, timeout=30) as response:
            batch = json.load(response)
        if not isinstance(batch, list):
            raise ValueError("Expected a release list")
        releases.extend(batch)
        if len(batch) < 100:
            return releases
    raise ValueError("Release pagination limit reached; refusing to publish an incomplete feed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="Previously fetched GitHub REST release list")
    parser.add_argument("--output", type=Path, default=Path("updates/releases.json"))
    args = parser.parse_args()
    releases = json.loads(args.input.read_text(encoding="utf-8")) if args.input else fetch_releases()
    manifest = public_releases(releases)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(manifest['releases'])} public releases to {args.output}")
