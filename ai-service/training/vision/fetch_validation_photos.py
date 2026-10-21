"""Fetch a small attributed Commons review set, never automatically training labels."""

import argparse
import html
import json
import re
import urllib.parse
import urllib.request
import urllib.error
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

CATEGORIES = ["Automobile doors", "Automobile seats", "Steering wheels",
              "Automobile bumpers", "Rear-view mirrors", "Catalytic converters", "Coffee cups"]


def request(url):
    query = urllib.request.Request(url, headers={"User-Agent": "EbiessaAcademicVision/1.0 (local academic evaluation)"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(query, timeout=30) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 2:
                raise
            time.sleep(15)


def api(**parameters):
    return json.loads(request("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {"action": "query", "format": "json", **parameters})))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    photos = []
    for category in CATEGORIES:
        members = api(list="categorymembers", cmtitle="Category:" + category, cmtype="file", cmlimit=20)["query"]["categorymembers"]
        members = [member for member in members if member["title"].lower().endswith((".jpg", ".jpeg", ".png"))][:2]
        if not members:
            print(f"No photos in {category}", flush=True)
            continue
        pages = api(pageids="|".join(str(member["pageid"]) for member in members), prop="imageinfo",
                    iiprop="url|extmetadata", iiurlwidth=640)["query"]["pages"]
        for page in pages.values():
            info = page["imageinfo"][0]
            metadata = info.get("extmetadata", {})
            license_name = metadata.get("LicenseShortName", {}).get("value", "")
            if not license_name:
                print(f"Skipped unlicensed metadata: {page['title']}", flush=True)
                continue
            url = info.get("thumburl", info["url"])
            suffix = ".png" if urllib.parse.urlparse(url).path.lower().endswith(".png") else ".jpg"
            path = args.output / f"additional-{len(photos)+1:02d}{suffix}"
            if not path.exists():
                path.write_bytes(request(url))
            photos.append({"id": f"additional-{len(photos)+1:02d}", "path": str(path.resolve()),
                "category_hint": category, "expected": "Requires visual review", "title": page["title"],
                "source": info["descriptionurl"], "download": url, "license": license_name,
                "license_url": metadata.get("LicenseUrl", {}).get("value", ""),
                "creator": html.unescape(re.sub("<[^>]+>", "", metadata.get("Artist", {}).get("value", "")))})
            (args.output / "sources.json").write_text(json.dumps(photos, indent=2), encoding="utf-8")
            print(f"Downloaded {path.name}: {page['title']} ({license_name})", flush=True)
        time.sleep(1)
    (args.output / "sources.json").write_text(json.dumps(photos, indent=2), encoding="utf-8")
    sheet = Image.new("RGB", (1000, ((len(photos)+3)//4)*210), "white")
    draw = ImageDraw.Draw(sheet)
    for index, photo in enumerate(photos):
        x, y = (index % 4)*250, (index // 4)*210
        with Image.open(photo["path"]) as image:
            thumbnail = ImageOps.contain(image.convert("RGB"), (240, 165))
        sheet.paste(thumbnail, (x, y))
        draw.text((x+5, y+170), photo["id"], fill="black")
        draw.text((x+5, y+185), photo["category_hint"], fill="black")
    sheet.save(args.output / "review-sheet.jpg")


if __name__ == "__main__":
    main()
