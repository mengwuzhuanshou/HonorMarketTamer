# -*- coding: utf-8 -*-
"""对照真机 aapt2 arsc 的结构细节。"""
import struct
import sys

data = open(sys.argv[1], "rb").read()
n = len(data)

def u16(o): return struct.unpack_from("<H", data, o)[0]
def u32(o): return struct.unpack_from("<I", data, o)[0]

print("file:", n)
t, hs, size, npkg = u16(0), u16(2), u32(4), u32(8)
print("table hdr: hsize=%d size=%d pkgs=%d" % (hs, size, npkg))
off = hs
gt, ghs, gsize = u16(off), u16(off + 2), u32(off + 4)
print("gpool: flags=0x%X count=%d strStart=%d" % (u32(off + 16), u32(off + 8), u32(off + 20)))
off += gsize
pkg = off
phs = u16(pkg + 2)
psize = u32(pkg + 4)
tstr = u32(pkg + 268)
last_tid = u32(pkg + 272)
kstr = u32(pkg + 276)
last_key = u32(pkg + 280)
print("package: hsize=%d size=%d typeStr@%d lastTypeId=%d keyStr@%d lastKey=%d"
      % (phs, psize, tstr, last_tid, kstr, last_key))

# 遍历 package 内 chunk，统计并打印前几个 type/typeSpec 与全部类型布局摘要
p = pkg + phs
end = pkg + psize
order = []
samples = {}
while p + 8 <= end:
    ct, chs, csz = u16(p), u16(p + 2), u32(p + 4)
    if csz < 8 or p + csz > end:
        print("!! bad chunk @%d type=0x%X hs=%d size=%d" % (p - pkg, ct, chs, csz))
        break
    if ct == 0x0202:
        order.append("spec%d" % data[p + 8])
    elif ct == 0x0201:
        tid = data[p + 8]
        ec = u32(p + 12)
        es = u32(p + 16)
        cs = u32(p + 20)
        dens = struct.unpack_from("<H", data, p + 20 + 14)[0]
        sdkv = struct.unpack_from("<H", data, p + 20 + 24)[0]
        order.append("type%d(%d)" % (tid, ec))
        if tid not in samples:
            samples[tid] = (chs, csz, es, cs, dens, sdkv,
                            struct.unpack_from("<HHI", data, p + es),
                            struct.unpack_from("<HBBI", data, p + es + 8))
    p += csz

print("chunk 序列前 12:", order[:12])
for tid in sorted(samples):
    chs, csz, es, cs, dens, sdkv, e0, v0 = samples[tid]
    print("type%d: hsize=%d size=%d entriesStart=%d cfgSize=%d density=%d sdk=%d"
          % (tid, chs, csz, es, cs, dens, sdkv))
    print("   entry[0]={size=%d flags=%d key=%d} value={sz=%d r0=%d dt=0x%02X dat=%d}"
          % (e0[0], e0[1], e0[2], v0[0], v0[1], v0[2], v0[3]))
