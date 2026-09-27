"""
Quick check that Spotify friend activity works for YOUR account before we build it into PixelPlayer.

1. pip install requests
2. In a desktop browser, log in at https://open.spotify.com, open DevTools > Application > Cookies >
   https://open.spotify.com and copy the value of the `sp_dc` cookie.
3. Run:  python spotify_friends_check.py <sp_dc value>

It prints each friend, what they're playing and whether it's playing right now. Nothing is saved or sent anywhere
except to Spotify. Don't share the sp_dc value, since it works like a password.
"""
import email.utils
import hashlib
import hmac
import re
import struct
import sys
from datetime import datetime, timezone

import requests

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
# Web-player TOTP secret v61 (in use since Jan 2026, per misiektoja/spotify_monitor).
TOTP_VERSION = 61
CIPHER = (44, 55, 47, 42, 70, 40, 34, 114, 76, 74, 50, 111, 120, 97, 75, 76, 94, 102, 43, 69, 49, 120, 118, 80, 64, 78)


def totp_key():
    # XOR each byte, join the numbers as a decimal string; that ASCII string is the HMAC key.
    return "".join(str(v ^ ((i % 33) + 9)) for i, v in enumerate(CIPHER)).encode()


def totp(server_time):
    mac = hmac.new(totp_key(), struct.pack(">Q", server_time // 30), hashlib.sha1).digest()
    o = mac[-1] & 0x0F
    return str((struct.unpack(">I", mac[o:o + 4])[0] & 0x7FFFFFFF) % 1_000_000).zfill(6)


def get_token(s, sp_dc):
    date = s.head("https://open.spotify.com/", headers={"User-Agent": UA}, timeout=15).headers["date"]
    code = totp(int(email.utils.parsedate_to_datetime(date).timestamp()))
    for reason in ("transport", "init"):
        r = s.get("https://open.spotify.com/api/token",
                  params={"reason": reason, "productType": "web-player", "totp": code, "totpServer": code, "totpVer": TOTP_VERSION},
                  headers={"User-Agent": UA, "App-Platform": "WebPlayer", "Referer": "https://open.spotify.com/", "Cookie": f"sp_dc={sp_dc}"},
                  timeout=15)
        print(f"[token reason={reason}] HTTP {r.status_code}" + ("" if r.ok else f": {r.text[:200]}"))
        if r.ok and not r.json().get("isAnonymous"):
            return r.json()
    if r.ok:
        sys.exit("Spotify answered with an anonymous token: the sp_dc value isn't a signed-in cookie. Copy it again while logged in.")
    sys.exit(f"Token request failed ({r.status_code}). See the response above.")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    s = requests.Session()
    tok = get_token(s, sys.argv[1].strip())
    auth = {"Authorization": f"Bearer {tok['accessToken']}", "Client-Id": tok.get("clientId", ""), "User-Agent": UA, "App-Platform": "WebPlayer"}
    print("Token OK\n")

    # Tier 1: the live feed the desktop app uses (has isPlaying)
    feed = s.post("https://spclient.wg.spotify.com/listening-activity/v1/feed", json={"unused": True, "resultLimit": 100}, headers=auth, timeout=15)
    print(f"[listening-activity/v1/feed] HTTP {feed.status_code}")
    if feed.ok:
        for item in feed.json().get("entities", []):
            e = item.get("userEntity") or item.get("followEntity") or {}
            a = e.get("activity") or {}
            uid = e.get("uri", "").split(":")[-1]
            prof = s.get(f"https://spclient.wg.spotify.com/user-profile-view/v3/profile/{uid}",
                         params={"playlist_limit": 10, "artist_limit": 0, "episode_limit": 0, "market": "from_token"}, headers=auth, timeout=15)
            name = prof.json().get("name", uid) if prof.ok else uid
            pls = [p.get("name") for p in (prof.json().get("public_playlists") or [])] if prof.ok else []
            when = a.get("timestamp", "")
            print(f"  {'PLAYING ' if a.get('isPlaying') else 'idle    '} {name:<24} {a.get('entityUri', '-')}  {when}")
            if pls:
                print(f"           public playlists: {', '.join(pls[:5])}{' …' if len(pls) > 5 else ''}")

    # Tier 2: legacy buddy list (has names/titles, but can be incomplete)
    b = s.get("https://guc-spclient.spotify.com/presence-view/v1/buddylist", headers=auth, timeout=15)
    print(f"\n[presence-view/v1/buddylist] HTTP {b.status_code}")
    if b.ok:
        now = datetime.now(timezone.utc).timestamp() * 1000
        for f in b.json().get("friends", []):
            t = f.get("track", {})
            mins = int((now - f.get("timestamp", 0)) / 60000)
            print(f"  {f['user'].get('name', '?'):<24} {t.get('name', '?')} — {t.get('artist', {}).get('name', '?')}  ({mins} min ago)")


if __name__ == "__main__":
    main()
