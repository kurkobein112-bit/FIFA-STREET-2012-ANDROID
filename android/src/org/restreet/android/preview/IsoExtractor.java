package org.restreet.android.preview;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** XDVDFS reader. Input is read-only; only a new staging directory is written. */
public final class IsoExtractor {
    private static final int SECTOR = 2048;
    private static final byte[] MAGIC = "MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII);
    public interface Progress { void update(long done, long total) throws IOException; }
    public static final class Entry {
        public final String path;
        public final long offset, size;
        public final boolean directory;
        Entry(String path, long offset, long size, boolean directory) {
            this.path = path; this.offset = offset; this.size = size; this.directory = directory;
        }
    }
    private final FileChannel input;
    private final long length;
    private long base;
    private long rootSector, rootSize;

    public IsoExtractor(FileChannel input) throws IOException {
        this.input = input; length = input.size();
        for (long candidate : new long[] { 0, 0xFD90000L, 0x2080000L, 0x18300000L }) {
            if (candidate + 0x10800 > length) continue;
            byte[] header = read(candidate + 0x10000, SECTOR);
            if (Arrays.equals(MAGIC, Arrays.copyOfRange(header, 0, 20))
                    && Arrays.equals(MAGIC, Arrays.copyOfRange(header, SECTOR - 20, SECTOR))) {
                base = candidate;
                ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                rootSector = uint(b.getInt(20)); rootSize = uint(b.getInt(24));
                return;
            }
        }
        throw new IOException("La ISO no contiene un volumen Xbox 360 XDVDFS compatible.");
    }
    private static long uint(int value) { return value & 0xFFFFFFFFL; }
    private byte[] read(long offset, int count) throws IOException {
        if (offset < 0 || count < 0 || offset > length - count) throw new IOException("ISO truncada: datos fuera del archivo.");
        byte[] data = new byte[count]; ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            if (input.read(buffer, offset + buffer.position()) <= 0) throw new EOFException("Lectura incompleta de la ISO.");
        }
        return data;
    }
    private void walk(String parent, long sector, long size, int depth, Set<String> extents,
                      Set<String> paths, List<Entry> result) throws IOException {
        if (size == 0) return;
        if (depth > 64 || size > 64 * 1024 * 1024 || !extents.add(sector + ":" + size))
            throw new IOException("Directorio ISO inválido o cíclico.");
        byte[] table = read(base + sector * SECTOR, (int)size);
        ByteBuffer b = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN);
        ArrayDeque<Integer> pending = new ArrayDeque<>(); pending.push(0);
        Set<Integer> nodes = new HashSet<>();
        while (!pending.isEmpty()) {
            int at = pending.pop();
            if (!nodes.add(at) || at > table.length - 14) throw new IOException("Árbol ISO inválido o cíclico.");
            int left = b.getShort(at) & 65535, right = b.getShort(at + 2) & 65535;
            long start = uint(b.getInt(at + 4)), bytes = uint(b.getInt(at + 8));
            int flags = table[at + 12] & 255, count = table[at + 13] & 255;
            if (count == 0 || at + 14 + count > table.length) throw new IOException("Nombre ISO inválido.");
            for (int i = 0; i < count; i++) {
                int c = table[at + 14 + i] & 255;
                if (c < 32 || c > 126 || "\\/:<>\"|?*".indexOf(c) >= 0) throw new IOException("Ruta ISO insegura.");
            }
            String name = new String(table, at + 14, count, StandardCharsets.US_ASCII);
            if (name.equals(".") || name.equals("..") || name.endsWith(".") || name.endsWith(" "))
                throw new IOException("Ruta ISO insegura.");
            String path = parent.isEmpty() ? name : parent + "/" + name;
            if (paths.size() >= 100000 || !paths.add(path.toLowerCase(Locale.ROOT))) throw new IOException("Demasiadas entradas o rutas duplicadas.");
            long offset = base + start * SECTOR;
            if (offset > length - bytes) throw new IOException("Archivo fuera de la ISO.");
            boolean directory = (flags & 0x10) != 0;
            result.add(new Entry(path, offset, bytes, directory));
            if (directory) walk(path, start, bytes, depth + 1, extents, paths, result);
            if (left != 0) pending.push(left * 4);
            if (right != 0) pending.push(right * 4);
        }
    }
    public List<Entry> inventory() throws IOException {
        List<Entry> entries = new ArrayList<>();
        walk("", rootSector, rootSize, 0, new HashSet<>(), new HashSet<>(), entries);
        return entries;
    }
    public void validateGame(List<Entry> entries) throws IOException {
        // These are the exact executables used by the fixed ARM64 recompilation.
        String[][] expected = {
            {"default.xex", "f4902ab0a2515ca64675544852a0263ba429863dad13dc19b7c2d989c97aa2b1"},
            {"fifadllzf.xex.dll", "9d2fb47c3caf279d55962f671afc5b8eb7a597717d44ccb5995dd002a3c26201"}
        };
        for (String[] item : expected) {
            Entry found = null;
            for (Entry entry : entries) if (!entry.directory && entry.path.equalsIgnoreCase(item[0])) found = entry;
            if (found == null) throw new IOException("Falta " + item[0] + ". Selecciona FIFA Street (2012) para Xbox 360.");
            try {
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                for (long at = 0; at < found.size;) {
                    byte[] chunk = read(found.offset + at, (int)Math.min(1024 * 1024, found.size - at));
                    hash.update(chunk); at += chunk.length;
                }
                StringBuilder hex = new StringBuilder();
                for (byte value : hash.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
                if (!hex.toString().equals(item[1])) throw new IOException("Edición de juego incompatible. Necesitas Title ID 4541097B · Media ID 78AA9072 sin modificar.");
            } catch (java.security.NoSuchAlgorithmException error) { throw new IOException(error); }
        }
        for (String name : new String[] {"data0.big", "data0.bh"}) {
            boolean found = false;
            for (Entry entry : entries) if (!entry.directory && entry.size > 0 && entry.path.equalsIgnoreCase(name)) found = true;
            if (!found) throw new IOException("ISO incompleta: falta " + name + ".");
        }
    }
    public static long totalBytes(List<Entry> entries) throws IOException {
        long total = 0;
        for (Entry entry : entries) if (!entry.directory) {
            if (entry.size > Long.MAX_VALUE - total) throw new IOException("Tamaño ISO inválido.");
            total += entry.size;
        }
        return total;
    }
    public void extract(List<Entry> entries, File destination, Progress progress) throws IOException {
        if (destination.exists() || !destination.mkdirs()) throw new IOException("La carpeta de extracción ya existe o no se puede crear.");
        long total = totalBytes(entries), done = 0;
        if (total > length * 2) throw new IOException("Tamaño extraído inválido.");
        String prefix = destination.getCanonicalPath() + File.separator;
        for (Entry entry : entries) {
            progress.update(done, total);
            File target = new File(destination, entry.path);
            if (!target.getCanonicalPath().startsWith(prefix)) throw new IOException("Ruta fuera de GameData.");
            if (entry.directory) {
                if (!target.isDirectory() && !target.mkdirs()) throw new IOException("No se puede crear " + entry.path);
                continue;
            }
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("No se puede crear la carpeta de destino.");
            if (!target.createNewFile()) throw new IOException("Archivo duplicado en la extracción.");
            try (FileOutputStream out = new FileOutputStream(target)) {
                for (long at = 0; at < entry.size;) {
                    byte[] chunk = read(entry.offset + at, (int)Math.min(1024 * 1024, entry.size - at));
                    out.write(chunk); at += chunk.length; done += chunk.length;
                    progress.update(done, total);
                }
            }
        }
    }
}
