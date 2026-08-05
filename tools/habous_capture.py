#!/usr/bin/env python3
"""Monthly Habous prayer-times capture tool.

Downloads the official current-month schedule from the Moroccan Ministry of
Habous for every city present in the offline asset and merges it in,
replacing the solar-approximated values for the captured days only.

The live site (https://www.habous.gov.ma/prieres/index.php?ville=<id>) only
exposes the current Hijri month, which usually spans two Gregorian months.
Days outside the captured window keep their existing values.

Usage:
    python tools/habous_capture.py [--asset PATH] [--cities 58,1,...]
                                   [--dry-run] [--delay 0.8] [--out PATH]

The parsing logic mirrors HabousProvider.parseHabousSchedule in the app so
offline values match the online provider exactly.
"""

import argparse
import json
import re
import sys
import time
import urllib.request
from datetime import date
from pathlib import Path

DEFAULT_ASSET = Path("app") / "src" / "main" / "assets" / "prayer_times_offline.json"
BASE_URL = "https://www.habous.gov.ma/prieres/index.php?ville={city}"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)

GREGORIAN_MONTH_NAMES = [
    "يناير", "فبراير", "مارس", "ابريل", "ماي", "يونيو",
    "يوليوز", "يوليو", "غشت", "شتنبر", "اكتوبر", "نونبر", "دجنبر",
]
GREGORIAN_MONTH_NUMBER = {
    "يناير": 1, "فبراير": 2, "مارس": 3, "ابريل": 4, "ماي": 5, "يونيو": 6,
    "يوليوز": 7, "يوليو": 7, "غشت": 8, "شتنبر": 9, "اكتوبر": 10,
    "نونبر": 11, "دجنبر": 12,
}


def to_ascii_digits(value):
    out = []
    for ch in value:
        code = ord(ch)
        if 0x0660 <= code <= 0x0669:
            out.append(chr(code - 0x0660 + ord("0")))
        elif 0x06F0 <= code <= 0x06F9:
            out.append(chr(code - 0x06F0 + ord("0")))
        else:
            out.append(ch)
    return "".join(out)


def normalize(value):
    value = value.lower()
    for fr, to in [
        ("é", "e"), ("è", "e"), ("ê", "e"), ("â", "a"), ("î", "i"),
        ("ô", "o"), ("û", "u"), ("أ", "ا"), ("إ", "ا"), ("آ", "ا"),
        ("ى", "ي"), ("ة", "ه"),
    ]:
        value = value.replace(fr, to)
    return re.sub(r"\s+", "", value)


def extract_gregorian_months(header):
    normalized = normalize(header)
    hits = []
    for name in GREGORIAN_MONTH_NAMES:
        idx = normalized.find(normalize(name))
        if idx >= 0:
            hits.append((idx, GREGORIAN_MONTH_NUMBER[name]))
    hits.sort(key=lambda t: t[0])
    result = []
    for _, month in hits:
        if month not in result:
            result.append(month)
    return result


def fetch_page(city_id, delay):
    url = BASE_URL.format(city=city_id)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in (1, 2):
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                body = resp.read().decode("utf-8", errors="replace")
            time.sleep(delay)
            return body
        except Exception as exc:
            sys.stderr.write(f"[warn] city={city_id} attempt {attempt} failed: {exc}\n")
            time.sleep(2.0)
    return None


def parse_month(html):
    """Returns {date: {field: value}} replicating the app's parser."""
    table_match = re.search(r'<table\s+id="horaire".*?</table>', html, re.S)
    if not table_match:
        return None
    table = table_match.group(0)

    header_p = re.search(
        r'<div class="priere-section-month">\s*<p class="first">(.*?)</p>\s*<p>(.*?)</p>',
        html, re.S,
    )
    header = ""
    if header_p:
        header = re.sub(r"<[^>]+>", " ", header_p.group(1) + " " + header_p.group(2))

    month_numbers = extract_gregorian_months(header)
    if not month_numbers:
        month_numbers = [date.today().month]

    years = [
        int(y)
        for y in re.findall(r"\d{4}", to_ascii_digits(header))
        if 1900 <= int(y) <= 2200
    ]
    current_year = years[0] if years else date.today().year
    if len(years) == 1 and len(month_numbers) > 1 and month_numbers[0] > month_numbers[-1]:
        current_year -= 1

    rows = re.findall(r"<tr\b.*?</tr>", table, re.S)
    result = {}
    month_index = 0
    current_month = month_numbers[month_index]
    previous_day = None

    for row in rows:
        cells = re.findall(r"<td\b.*?</td>", row, re.S)
        if len(cells) < 9:
            continue

        def cell_text(cell):
            return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", cell)).strip()

        def extract_number(value):
            m = re.search(r"\d+", to_ascii_digits(value))
            return int(m.group(0)) if m else None

        def extract_time(value):
            m = re.search(r"\d{1,2}:\d{2}", to_ascii_digits(value))
            if not m:
                return None
            hour, minute = m.group(0).split(":")
            return f"{int(hour):02d}:{minute}"

        gregorian_day = extract_number(cell_text(cells[2]))
        if gregorian_day is None:
            continue
        if previous_day is not None and gregorian_day < previous_day:
            next_month = (
                month_numbers[month_index + 1]
                if month_index + 1 < len(month_numbers)
                else (current_month % 12) + 1
            )
            month_index = min(month_index + 1, len(month_numbers) - 1)
            if next_month < current_month:
                current_year += 1
            current_month = next_month
        previous_day = gregorian_day

        try:
            d = date(current_year, current_month, gregorian_day)
        except ValueError:
            continue

        fajr = extract_time(cell_text(cells[3]))
        shuruq = extract_time(cell_text(cells[4]))
        dhuhr = extract_time(cell_text(cells[5]))
        asr = extract_time(cell_text(cells[6]))
        maghrib = extract_time(cell_text(cells[7]))
        isha = extract_time(cell_text(cells[8]))
        if None in (fajr, shuruq, dhuhr, asr, maghrib, isha):
            continue

        result[d] = {
            "fajr": fajr,
            "sunrise": shuruq,
            "dohr": dhuhr,
            "asr": asr,
            "maghreb": maghrib,
            "ichaa": isha,
        }
    return result or None


def write_asset(data, path):
    lines = ["{"]
    city_keys = list(data.keys())
    for ci, city_key in enumerate(city_keys):
        lines.append(f'  "{city_key}": {{')
        year_keys = list(data[city_key].keys())
        for yi, year_key in enumerate(year_keys):
            lines.append(f'    "{year_key}": {{')
            month_keys = list(data[city_key][year_key].keys())
            for mi, month_key in enumerate(month_keys):
                lines.append(f'      "{month_key}": {{')
                day_keys = list(data[city_key][year_key][month_key].keys())
                for di, day_key in enumerate(day_keys):
                    times = data[city_key][year_key][month_key][day_key]
                    parts = ",".join(
                        f'"{k}":"{times[k]}"'
                        for k in ("fajr", "sunrise", "dohr", "asr", "maghreb", "ichaa")
                    )
                    suffix = "," if di < len(day_keys) - 1 else ""
                    lines.append(f'        "{day_key}": {{{parts}}}{suffix}')
                lines.append("      }")
                if mi < len(month_keys) - 1:
                    lines[-1] += ","
            lines.append("    }")
            if yi < len(year_keys) - 1:
                lines[-1] += ","
        lines.append("  }")
        if ci < len(city_keys) - 1:
            lines[-1] += ","
    lines.append("}")
    text = "\n".join(lines) + "\n"
    with path.open("w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    return len(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset", default=str(DEFAULT_ASSET), help="Path to the offline asset JSON")
    parser.add_argument("--cities", default="", help="Comma-separated habous ids to capture (default: all in asset)")
    parser.add_argument("--dry-run", action="store_true", help="Report what would change without writing")
    parser.add_argument("--delay", type=float, default=0.8, help="Delay between HTTP requests in seconds")
    parser.add_argument("--out", default="", help="Write merged asset to this path instead of in place")
    args = parser.parse_args()

    asset_path = Path(args.asset)
    if not asset_path.exists():
        sys.exit(f"Asset not found: {asset_path}")

    with asset_path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    city_ids = [c for c in data.keys()]
    if args.cities:
        requested = [c.strip() for c in args.cities.split(",") if c.strip()]
        unknown = [c for c in requested if c not in data]
        if unknown:
            sys.exit(f"Unknown city ids in asset: {', '.join(unknown)}")
        city_ids = requested

    total_replaced = 0
    failures = []
    for city_id in city_ids:
        html = fetch_page(city_id, args.delay)
        if html is None:
            failures.append(city_id)
            continue
        captured = parse_month(html)
        if captured is None:
            failures.append(city_id)
            sys.stderr.write(f"[warn] city={city_id} no schedule parsed\n")
            continue
        city_data = data.setdefault(city_id, {})
        replaced = 0
        for d, times in captured.items():
            year_key = str(d.year)
            month_key = str(d.month)
            day_key = str(d.day)
            year_data = city_data.setdefault(year_key, {})
            month_data = year_data.setdefault(month_key, {})
            if month_data.get(day_key) != times:
                replaced += 1
            month_data[day_key] = times
        total_replaced += replaced
        span = f"{min(captured)}..{max(captured)}"
        print(f"city={city_id} days={len(captured)} span={span} replaced={replaced}")

    if failures:
        sys.stderr.write(f"[fail] {len(failures)} cities captured nothing: {', '.join(failures)}\n")

    if total_replaced == 0:
        print("No changes (all captured days already match).")
        return

    if args.dry_run:
        print(f"DRY-RUN: {total_replaced} day entries would be replaced.")
        return

    out_path = Path(args.out) if args.out else asset_path
    size = write_asset(data, out_path)
    print(f"Wrote {out_path} ({size} bytes, {total_replaced} day entries replaced)")


if __name__ == "__main__":
    main()
