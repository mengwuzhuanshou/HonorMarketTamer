# -*- coding: utf-8 -*-
"""Walk resources.arsc chunk chain and sanity-check structure."""
import struct
import sys

data = open(sys.argv[1], "rb").read()
print("total bytes:", len(data))

def u16(o): return struct.unpack_from("<H", data, o)[0]
def u32(o): return struct.unpack_from("<I", data, o)[0]

t, hs, size, pkgs = u16(0), u16(2), u32(4), u32(8)
print("table: type=0x%04X hsize=%d size=%d packages=%d" % (t, hs, size, pkgs))
assert t == 0x0002 and size == len(data), "table header bad"

off = hs
# global string pool
gt, ghs, gsize = u16(off), u16(off + 2), u32(off + 4)
gcount = u32(off + 8)
gstart = u32(off + 24)
print("global pool: type=0x%04X hsize=%d size=%d count=%d stringsStart=%d"
      % (gt, ghs, gsize, gcount, gstart))
assert off + gsize <= size
off += gsize

pt, phs, psize = u16(off), u16(off + 2), u32(off + 4)
pid = u32(off + 8)
type_strings_off = u32(off + 268)
key_strings_off = u32(off + 276)
print("package: type=0x%04X hsize=%d size=%d id=0x%X typeStr@+%d keyStr@+%d"
      % (pt, phs, psize, pid, type_strings_off, key_strings_off))
pkg_start = off
end = off + psize
p = off + phs

while p < end:
    ct, chs, csz = u16(p), u16(p + 2), u32(p + 4)
    name = {0x0202: "typeSpec", 0x0201: "type", 0x0001: "strPool"}.get(ct, "?")
    print("  chunk @%-6d type=0x%04X(%s) hsize=%d size=%d" % (p - pkg_start, ct, name, chs, csz))
    if ct == 0x0201:
        tid = data[p + 8]
        ec = u32(p + 12)
        es = u32(p + 16)
        cfg_size = u32(p + 20)
        dens = struct.unpack_from("<H", data, p + 20 + 14)[0]
        sdkv = struct.unpack_from("<H", data, p + 20 + 24)[0]
        print("    typeId=%d entryCount=%d entriesStart=%d cfg.size=%d density=%d sdk=%d"
              % (tid, ec, es, cfg_size, dens, sdkv))
        # entry offsets
        for i in range(ec):
            eo = u32(p + 20 + 64 + 4 * i)
            print("    entry[%d] offset=%d -> abs %d" % (i, eo, p + es + eo))
            ea = p + es + eo
            esz, efl, ekey = struct.unpack_from("<HHI", data, ea)
            vsz = struct.unpack_from("<H", data, ea + 8)[0]
            vdt = data[ea + 11]
            vdat = struct.unpack_from("<I", data, ea + 12)[0]
            print("      entrySize=%d flags=%d keyIdx=%d val(size=%d dt=0x%02X data=%d)"
                  % (esz, efl, ekey, vsz, vdt, vdat))
    p += csz

print("package walked: end=%d == pkg_end=%d ? %s" % (p, end, p == end))
