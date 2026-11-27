#!/usr/bin/env python3
"""
Calibration test print for Brother PT-D450 / PT-D450VP over Linux usblp (/dev/usb/lp0).

Replicates the app's Brother P-touch Raster encoder (RasterProtocol.kt):
100 x 0x00, ESC @, ESC i a 0x01, ESC i z, ESC i M, ESC i K, ESC i d, M 0x02,
'Z'/'G' raster lines (TIFF PackBits), 0x1A.

Usage:
  python3 scripts/test-print.py --dry-run          # build packet, do not print
  python3 scripts/test-print.py                    # print calibration label
  python3 scripts/test-print.py --text "HELLO"     # custom text
  python3 scripts/test-print.py --no-mirror        # disable vertical mirroring
"""

import argparse
import os
import select
import sys
import time

from PIL import Image, ImageDraw, ImageFont

DEVICE = "/dev/usb/lp0"
DPI = 180
PRINTHEAD_PINS = 128
BYTES_PER_LINE = PRINTHEAD_PINS // 8
DOTS_PER_MM = DPI / 25.4

TAPE_HEIGHTS = {4: 24, 6: 32, 9: 50, 12: 70, 18: 112}
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def packbits_encode(data: bytes) -> bytes:
    out = bytearray()
    i, n = 0, len(data)
    while i < n:
        run = 1
        while i + run < n and run < 128 and data[i + run] == data[i]:
            run += 1
        if run >= 2:
            out.append((257 - run) & 0xFF)
            out.append(data[i])
            i += run
        else:
            start = i
            lit = 0
            while i < n and lit < 128:
                if i + 1 < n and data[i] == data[i + 1]:
                    break
                i += 1
                lit += 1
            out.append((lit - 1) & 0xFF)
            out.extend(data[start:start + lit])
    return bytes(out)


def render_label(text: str, height_px: int, mirror: bool) -> tuple[int, int, list[bytes]]:
    # Width in pixels along the feed direction; keep it short for calibration (~59 mm)
    width_px = 420
    left_panel_px = 58          # arrow area
    right_pad_px = 10           # keep text away from the frame
    available_w = width_px - left_panel_px - right_pad_px
    img = Image.new("1", (width_px, height_px), 1)  # 1 = white
    draw = ImageDraw.Draw(img)

    # Frame: checks printable-area alignment and centering
    draw.rectangle([0, 0, width_px - 1, height_px - 1], outline=0, width=1)

    # Up arrow: orientation indicator (must point up on the physical label)
    ax, ay = 26, height_px // 2
    draw.polygon([(ax, ay - 18), (ax - 14, ay + 6), (ax + 14, ay + 6)], fill=0)
    draw.rectangle([ax - 4, ay + 6, ax + 4, ay + 24], fill=0)

    # Main text: auto-fit to both printable height and available width
    size = height_px - 18
    font = ImageFont.truetype(FONT_PATH, size)
    while size > 8:
        bbox = draw.textbbox((0, 0), text, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        if th <= height_px - 14 and tw <= available_w:
            break
        size -= 1
        font = ImageFont.truetype(FONT_PATH, size)

    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = left_panel_px + (available_w - tw) // 2 - bbox[0]
    ty = (height_px - th) // 2 - bbox[1]
    draw.text((tx, ty), text, font=font, fill=0)

    # Convert to 16-byte raster lines, centered on the 128-pin head
    margin = (PRINTHEAD_PINS - height_px) // 2
    lines = []
    for x in range(width_px):
        line = bytearray(BYTES_PER_LINE)
        for y in range(height_px):
            if img.getpixel((x, y)) == 0:
                raw_pin = margin + y
                pin = (PRINTHEAD_PINS - 1) - raw_pin if mirror else raw_pin
                if 0 <= pin < PRINTHEAD_PINS:
                    line[pin // 8] |= 1 << (7 - (pin % 8))
        lines.append(bytes(line))
    return width_px, height_px, lines


def build_job(lines: list[bytes], width_mm: int, margin_dots: int) -> bytes:
    out = bytearray()
    out += b"\x00" * 100
    out += b"\x1b\x40"                     # ESC @
    out += b"\x1b\x69\x61\x01"             # ESC i a 1 (raster mode)
    count = len(lines)
    out += b"\x1b\x69\x7a"                 # ESC i z
    out += bytes([0x86, 0x01, width_mm, 0x00])
    out += count.to_bytes(4, "little")
    out += bytes([0x00, 0x00])
    out += b"\x1b\x69\x4d\x00"             # ESC i M (no auto-cut)
    out += b"\x1b\x69\x4b\x08"             # ESC i K (feed at end)
    out += b"\x1b\x69\x64" + margin_dots.to_bytes(2, "little")
    out += b"\x4d\x02"                     # M 2 (PackBits)
    for line in lines:
        if not any(line):
            out += b"\x5a"                 # Z
        else:
            payload = packbits_encode(line)
            out += b"\x47" + len(payload).to_bytes(2, "little") + payload
    out += b"\x1a"
    return bytes(out)


def send(job: bytes, dry_run: bool) -> int:
    if dry_run:
        path = "/tmp/opencode/test-label.bin"
        with open(path, "wb") as f:
            f.write(job)
        print(f"Dry run: {len(job)} bytes -> {path}")
        print("Header:", " ".join(f"{b:02X}" for b in job[:160]))
        return 0

    if not os.path.exists(DEVICE):
        print(f"ERROR: {DEVICE} not found", file=sys.stderr)
        return 1

    fd = os.open(DEVICE, os.O_RDWR | os.O_NONBLOCK)
    try:
        offset = 0
        while offset < len(job):
            _, w, _ = select.select([], [fd], [], 5.0)
            if not w:
                print(f"ERROR: write timeout at {offset}/{len(job)}", file=sys.stderr)
                return 1
            chunk = job[offset:offset + 4096]
            written = os.write(fd, chunk)
            offset += written
        print(f"Sent {offset} bytes to {DEVICE}")

        # Give the printer time to finish and read post-print status (optional)
        time.sleep(3)
        buf = bytearray()
        deadline = time.time() + 2.0
        while len(buf) < 32 and time.time() < deadline:
            r, _, _ = select.select([fd], [], [], 0.3)
            if not r:
                break
            try:
                chunk = os.read(fd, 64)
            except BlockingIOError:
                continue
            if not chunk:
                break
            buf.extend(chunk)
        if len(buf) >= 32:
            print("Post-print status:", " ".join(f"{b:02X}" for b in buf[:32]))
            print(f"Errors: error1=0x{buf[8]:02X}, error2=0x{buf[9]:02X}")
        else:
            print("Post-print status: not received (normal for some firmware revisions)")
        return 0
    finally:
        os.close(fd)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--text", default="PT-D450VP")
    ap.add_argument("--width-mm", type=int, default=12, choices=sorted(TAPE_HEIGHTS))
    ap.add_argument("--margin-dots", type=int, default=14)
    ap.add_argument("--no-mirror", action="store_true", default=True,
                    help="kept for backward compatibility; no mirroring is the default")
    ap.add_argument("--mirror", dest="no_mirror", action="store_false",
                    help="enable vertical mirroring (legacy behaviour)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    height_px = TAPE_HEIGHTS[args.width_mm]
    width_px, height_px, lines = render_label(args.text, height_px, not args.no_mirror)
    job = build_job(lines, args.width_mm, args.margin_dots)

    empty = sum(1 for line in lines if not any(line))
    print(f"Label: {width_px} x {height_px} px (~{width_px / DOTS_PER_MM:.1f} mm), "
          f"raster lines: {len(lines)}, empty: {empty}, "
          f"mirror: {not args.no_mirror}, job: {len(job)} bytes")
    return send(job, args.dry_run)


if __name__ == "__main__":
    raise SystemExit(main())
