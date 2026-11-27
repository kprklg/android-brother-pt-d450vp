#!/usr/bin/env python3
"""
Non-destructive USB status probe for Brother PT-D450 / PT-D450VP on Linux (/dev/usb/lp0).
Sends ESC @ + ESC i S and prints decoded 32-byte status without feeding or wasting tape.
"""

import os
import select
import sys
import time

DEVICE_PATH = sys.argv[1] if len(sys.argv) > 1 else "/dev/usb/lp0"

MEDIA_TYPES = {
    0x00: "No media",
    0x01: "Laminated TZe tape",
    0x03: "Non-laminated tape",
    0x11: "Heat-shrink tube (HSe)",
    0xFF: "Incompatible tape",
}

TAPE_COLORS = {
    0x01: "White",
    0x03: "Clear",
    0x04: "Red",
    0x05: "Blue",
    0x06: "Yellow",
    0x07: "Green",
    0x08: "Black",
}

TEXT_COLORS = {
    0x01: "White",
    0x04: "Red",
    0x05: "Blue",
    0x08: "Black",
    0x0A: "Gold",
}


def main() -> int:
    if not os.path.exists(DEVICE_PATH):
        print(f"ERROR: {DEVICE_PATH} not found. Connect Brother PT-D450VP via USB and power it on.", file=sys.stderr)
        return 1

    fd = os.open(DEVICE_PATH, os.O_RDWR | os.O_NONBLOCK)
    try:
        # Drain any stale bytes
        while True:
            r, _, _ = select.select([fd], [], [], 0.1)
            if not r:
                break
            try:
                if not os.read(fd, 64):
                    break
            except BlockingIOError:
                break

        cmd = b"\x00" * 100 + b"\x1b\x40" + b"\x1b\x69\x53"
        written = os.write(fd, cmd)
        print(f"Sent {written} bytes to {DEVICE_PATH}")

        buf = bytearray()
        deadline = time.time() + 3.0
        while len(buf) < 32 and time.time() < deadline:
            r, _, _ = select.select([fd], [], [], 0.2)
            if r:
                try:
                    chunk = os.read(fd, 64)
                    if chunk:
                        buf.extend(chunk)
                except BlockingIOError:
                    pass

        if len(buf) < 32:
            print(f"ERROR: Expected 32 status bytes, received {len(buf)}", file=sys.stderr)
            return 2

        print("Raw 32-byte status:")
        print(" ".join(f"{b:02X}" for b in buf[:32]))
        print(f"Model code : 0x{buf[4]:02X} ({'PT-D450 / PT-D450VP' if buf[4] == 0x69 else 'Other Brother'})")
        print(f"Errors     : error1=0x{buf[8]:02X}, error2=0x{buf[9]:02X}")
        print(f"Tape width : {buf[10]} mm")
        print(f"Media type : {MEDIA_TYPES.get(buf[11], f'0x{buf[11]:02X}')}")
        print(f"Tape color : {TAPE_COLORS.get(buf[24], f'0x{buf[24]:02X}')}")
        print(f"Text color : {TEXT_COLORS.get(buf[25], f'0x{buf[25]:02X}')}")
        return 0
    finally:
        os.close(fd)


if __name__ == "__main__":
    raise SystemExit(main())
