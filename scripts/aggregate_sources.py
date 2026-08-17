#!/usr/bin/env python3
"""Aggregate already-public M3U/M3U8 playlists into sources/.

Fetches well-known open-source GitHub / GitHub Pages playlists, merges and
deduplicates them, and writes:

  sources/cn.m3u      merged China list
  sources/cctv.m3u    CCTV / 央视 only
  sources/status.json fetch metadata

This script does not scrape 央视网 / 央视频 / yangshipin APIs, and does not
call login-gated, DRM, or official player endpoints. It only downloads
playlists that are already published as ordinary M3U/M3U8 files.

A failed or 404 upstream is skipped; the job continues with the rest.
Channels are not dropped because a GitHub-hosted runner cannot play them.
"""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from urllib.parse import urlsplit, urlunsplit


ROOT = Path(__file__).resolve().parent.parent
SOURCES_DIR = ROOT / "sources"

USER_AGENT = (
    "miaoTv-source-aggregator/1.0 (+https://github.com/feverdestiny/miaoTv)"
)
FETCH_TIMEOUT_SEC = 45

# Edit this list to add or remove upstream playlists.
UPSTREAMS: list[dict[str, str]] = [
    {
        "id": "iptv-org-cn",
        "name": "iptv-org/iptv",
        "url": "https://iptv-org.github.io/iptv/countries/cn.m3u",
    },
    {
        "id": "best-fan-cn-cctv",
        "name": "best-fan/iptv-sources",
        "url": "https://raw.githubusercontent.com/best-fan/iptv-sources/master/cn_cctv.m3u8",
    },
    {
        "id": "best-fan-cn-all",
        "name": "best-fan/iptv-sources",
        "url": "https://raw.githubusercontent.com/best-fan/iptv-sources/master/cn_all.m3u8",
    },
    {
        "id": "ccsh-live-lite",
        "name": "CCSH/IPTV",
        "url": "https://raw.githubusercontent.com/CCSH/IPTV/refs/heads/main/live_lite.m3u",
    },
]

STREAM_URL_RE = re.compile(
    r"^(rtsp|rtmp|rtp|https?|udp|file)://",
    re.IGNORECASE,
)
ATTR_RE = re.compile(r'([\w-]+)="([^"]*)"')
QUALITY_SUFFIX_RE = re.compile(
    r"\((?:sd|hd|fhd|uhd|4k|8k|\d{3,4}\s*p)\)|\[(?:not\s*24/7|geo-?blocked)\]",
    re.IGNORECASE,
)
NSFW_RE = re.compile(
    r"(?:\bxxx\b|\bporn|\bnsfw\b|\badult\b|\berotic\b|18\+|成人|色情|情色|黄片|"
    r"成人频道|成人电影|av频道)",
    re.IGNORECASE,
)
CCTV_NUM_RE = re.compile(r"cctv\s*-?\s*(\d+)", re.IGNORECASE)
CCTV_NAME_RE = re.compile(
    r"(?:cctv\s*-?\s*(?:\d+|4k|8k|\+|plus)|cgtn|央视|中央电视台)",
    re.IGNORECASE,
)
META_GROUP_RE = re.compile(r"^(更新时间|update)$", re.IGNORECASE)
META_NAME_RE = re.compile(r"^\d{8}(?:\s+\d{1,2}:\d{2})?$")
WEISHI_RE = re.compile(r"卫视")

GENERIC_GROUPS = {
    "",
    "undefined",
    "general",
    "other",
    "其他",
    "其他频道",
}


@dataclass
class Channel:
    name: str
    url: str
    group: str = ""
    tvg_id: str = ""
    tvg_name: str = ""
    tvg_logo: str = ""
    source_id: str = ""
    raw_extinf: str = ""

    @property
    def display_name(self) -> str:
        return self.name or self.tvg_name or "Unknown"

    @property
    def identity_name(self) -> str:
        return self.tvg_name or self.name


@dataclass
class UpstreamResult:
    id: str
    name: str
    url: str
    http_status: int | None = None
    ok: bool = False
    error: str | None = None
    channels_parsed: int = 0
    channels_kept: int = 0
    channels: list[Channel] = field(default_factory=list)


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def fetch_text(url: str) -> tuple[int | None, str | None, str | None]:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/vnd.apple.mpegurl, audio/mpegurl, text/plain, */*",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=FETCH_TIMEOUT_SEC) as resp:
            status = int(resp.getcode() or 0)
            raw = resp.read()
            text = raw.decode("utf-8-sig", errors="replace")
            return status, text, None
    except urllib.error.HTTPError as exc:
        return int(exc.code), None, f"HTTP {exc.code} {exc.reason}"
    except urllib.error.URLError as exc:
        return None, None, f"URL error: {exc.reason}"
    except Exception as exc:  # noqa: BLE001 — keep the job going
        return None, None, f"{type(exc).__name__}: {exc}"


def parse_extinf(line: str) -> tuple[dict[str, str], str]:
    body = re.sub(r"^#EXTINF:[^,\s]*\s*", "", line.strip())
    if "," in body:
        attr_part, name = body.rsplit(",", 1)
    else:
        attr_part, name = body, ""
    attrs = {key: value.strip() for key, value in ATTR_RE.findall(attr_part)}
    return attrs, name.strip()


def parse_m3u(text: str, source_id: str) -> list[Channel]:
    lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    channels: list[Channel] = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line.startswith("#EXTINF"):
            i += 1
            continue
        attrs, name = parse_extinf(line)
        url = ""
        j = i + 1
        while j < len(lines):
            nxt = lines[j].strip()
            if not nxt:
                j += 1
                continue
            if nxt.startswith("#EXTINF"):
                break
            if nxt.startswith("#"):
                j += 1
                continue
            url = nxt
            break
        if url and STREAM_URL_RE.search(url.split("|", 1)[0].strip()):
            tvg_name = attrs.get("tvg-name", "")
            channels.append(
                Channel(
                    name=name or tvg_name,
                    url=url,
                    group=attrs.get("group-title", "").strip(),
                    tvg_id=attrs.get("tvg-id", "").strip(),
                    tvg_name=tvg_name,
                    tvg_logo=attrs.get("tvg-logo", "").strip(),
                    source_id=source_id,
                    raw_extinf=line,
                )
            )
        i = j if url else i + 1
    return channels


def normalize_name(name: str) -> str:
    text = QUALITY_SUFFIX_RE.sub("", name or "")
    text = text.replace("综合", "").replace("高清", "").replace("超高清", "")
    text = text.replace("标清", "").replace("超清", "")
    text = text.casefold()
    text = re.sub(r"[\s\-_.·•]+", "", text)
    return text


def normalize_url(url: str) -> str:
    stream = (url or "").strip().split("|", 1)[0].strip()
    stream = stream.split("#", 1)[0].rstrip()
    parts = urlsplit(stream)
    if not parts.scheme:
        return stream.rstrip("/")
    netloc = parts.netloc.lower()
    path = parts.path.rstrip("/") or parts.path
    return urlunsplit((parts.scheme.lower(), netloc, path, parts.query, ""))


def haystack(channel: Channel) -> str:
    return " ".join(
        part
        for part in (channel.name, channel.tvg_name, channel.group, channel.tvg_id)
        if part
    )


def is_nsfw(channel: Channel) -> bool:
    return bool(NSFW_RE.search(haystack(channel)))


def is_meta_entry(channel: Channel) -> bool:
    if META_GROUP_RE.match(channel.group.strip()):
        return True
    return bool(META_NAME_RE.match(channel.display_name.strip()))


def is_cctv(channel: Channel) -> bool:
    if channel.group.strip() in {"央视频道", "央视台", "央视"}:
        return True
    return bool(CCTV_NAME_RE.search(haystack(channel)))


def is_weishi(channel: Channel) -> bool:
    return bool(WEISHI_RE.search(haystack(channel)))


def preference_score(channel: Channel) -> tuple[int, int, int, int]:
    """Higher is better. Prefer CCTV / 卫视 and richer metadata."""
    cctv = 1 if is_cctv(channel) else 0
    weishi = 1 if is_weishi(channel) else 0
    logo = 1 if channel.tvg_logo else 0
    named_group = 0 if channel.group.casefold() in GENERIC_GROUPS else 1
    return (cctv, weishi, logo, named_group)


def merge_channels(channels: Iterable[Channel]) -> tuple[list[Channel], int]:
    """Deduplicate by normalized name + URL; keep the preferred entry."""
    chosen: dict[tuple[str, str], Channel] = {}
    duplicates = 0
    for channel in channels:
        key = (normalize_name(channel.identity_name), normalize_url(channel.url))
        if not key[0] or not key[1]:
            continue
        existing = chosen.get(key)
        if existing is None:
            chosen[key] = channel
            continue
        duplicates += 1
        if preference_score(channel) > preference_score(existing):
            chosen[key] = channel
    return list(chosen.values()), duplicates


def cctv_sort_key(channel: Channel) -> tuple[int, int, str]:
    text = haystack(channel)
    numbered = CCTV_NUM_RE.search(text)
    if numbered:
        return (0, int(numbered.group(1)), channel.display_name)
    if re.search(r"cctv\s*-?\s*4k", text, re.IGNORECASE):
        return (1, 0, channel.display_name)
    if re.search(r"cctv\s*-?\s*8k", text, re.IGNORECASE):
        return (1, 1, channel.display_name)
    if re.search(r"cgtn", text, re.IGNORECASE):
        return (2, 0, channel.display_name)
    return (3, 0, channel.display_name)


def sort_cn(channels: list[Channel]) -> list[Channel]:
    def key(channel: Channel) -> tuple[int, int, int, str, str]:
        if is_cctv(channel):
            bucket, num, name = cctv_sort_key(channel)
            return (0, bucket, num, name, channel.url)
        if is_weishi(channel):
            return (1, 0, 0, channel.display_name, channel.url)
        return (2, 0, 0, f"{channel.group}|{channel.display_name}", channel.url)

    return sorted(channels, key=key)


def format_extinf(channel: Channel) -> str:
    attrs: list[str] = []
    if channel.tvg_id:
        attrs.append(f'tvg-id="{channel.tvg_id}"')
    tvg_name = channel.tvg_name or channel.display_name
    if tvg_name:
        attrs.append(f'tvg-name="{tvg_name}"')
    if channel.tvg_logo:
        attrs.append(f'tvg-logo="{channel.tvg_logo}"')
    group = channel.group
    if is_cctv(channel) and group.casefold() in GENERIC_GROUPS:
        group = "央视"
    elif is_weishi(channel) and group.casefold() in GENERIC_GROUPS:
        group = "卫视"
    if group:
        attrs.append(f'group-title="{group}"')
    attr_str = (" " + " ".join(attrs)) if attrs else ""
    return f"#EXTINF:-1{attr_str},{channel.display_name}"


def playlist_header(title: str) -> str:
    upstream_names = []
    seen: set[str] = set()
    for item in UPSTREAMS:
        name = item["name"]
        if name not in seen:
            seen.add(name)
            upstream_names.append(name)
    listed = "\n".join(f"#   - {name}" for name in upstream_names)
    return "\n".join(
        [
            "#EXTM3U",
            f"# {title}",
            "# Generated by miaoTv (https://github.com/feverdestiny/miaoTv)",
            "# Aggregated from public open-source playlists:",
            listed,
            "# Streams are third-party public URLs, not official, and may die at any time.",
            "# Not affiliated with CCTV / 央视网 / 央视频 or any broadcaster.",
            "",
        ]
    )


def write_m3u(path: Path, title: str, channels: list[Channel]) -> None:
    parts = [playlist_header(title)]
    for channel in channels:
        parts.append(format_extinf(channel))
        parts.append(channel.url)
    path.write_text("\n".join(parts) + "\n", encoding="utf-8")


def collect_upstreams() -> list[UpstreamResult]:
    results: list[UpstreamResult] = []
    for item in UPSTREAMS:
        result = UpstreamResult(id=item["id"], name=item["name"], url=item["url"])
        print(f"Fetching {result.name}: {result.url}", flush=True)
        status, text, error = fetch_text(result.url)
        result.http_status = status
        result.error = error
        if status == 404:
            print(f"  skip (404)", flush=True)
            results.append(result)
            continue
        if text is None or not (status and 200 <= status < 300):
            print(f"  skip ({error or status})", flush=True)
            results.append(result)
            continue
        parsed = parse_m3u(text, result.id)
        result.channels_parsed = len(parsed)
        kept: list[Channel] = []
        for channel in parsed:
            if is_nsfw(channel) or is_meta_entry(channel):
                continue
            kept.append(channel)
        result.channels = kept
        result.channels_kept = len(kept)
        result.ok = True
        print(
            f"  HTTP {status}: parsed {result.channels_parsed}, kept {result.channels_kept}",
            flush=True,
        )
        results.append(result)
    return results


def main() -> int:
    SOURCES_DIR.mkdir(parents=True, exist_ok=True)
    fetched_at = utc_now_iso()
    results = collect_upstreams()

    incoming: list[Channel] = []
    for result in results:
        incoming.extend(result.channels)

    merged, duplicates = merge_channels(incoming)
    cn_channels = sort_cn(merged)
    cctv_channels = sort_cn([ch for ch in merged if is_cctv(ch)])

    write_m3u(SOURCES_DIR / "cn.m3u", "miaoTv aggregated China playlist", cn_channels)
    write_m3u(SOURCES_DIR / "cctv.m3u", "miaoTv aggregated CCTV / 央视 playlist", cctv_channels)

    status = {
        "fetched_at": fetched_at,
        "generator": "miaoTv/scripts/aggregate_sources.py",
        "upstreams": [
            {
                "id": item.id,
                "name": item.name,
                "url": item.url,
                "http_status": item.http_status,
                "ok": item.ok,
                "error": item.error,
                "channels_parsed": item.channels_parsed,
                "channels_kept": item.channels_kept,
            }
            for item in results
        ],
        "outputs": {
            "cn.m3u": {"channels": len(cn_channels)},
            "cctv.m3u": {"channels": len(cctv_channels)},
        },
        "dropped": {
            "duplicates": duplicates,
            "nsfw_or_meta": sum(
                item.channels_parsed - item.channels_kept for item in results if item.ok
            ),
        },
    }
    (SOURCES_DIR / "status.json").write_text(
        json.dumps(status, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    ok_count = sum(1 for item in results if item.ok)
    print(
        f"Wrote sources/cn.m3u ({len(cn_channels)} channels), "
        f"sources/cctv.m3u ({len(cctv_channels)} channels) "
        f"from {ok_count}/{len(results)} upstreams.",
        flush=True,
    )
    if ok_count == 0:
        print("All upstreams failed; wrote empty playlists and status.json.", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
