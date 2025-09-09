/* 
 * Copyright 2016-2025, Michael Büchner <m.buechner@dnb.de>
 * Deutsche Digitale Bibliothek
 * c/o Deutsche Nationalbibliothek
 * Informationsinfrastruktur
 * Adickesallee 1, D-60322 Frankfurt am Main 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ddb.beacons;

// File: CommonsLicensesMVOnly.java
// MVStore-only Pipeline mit Lombok-Logging (@Slf4j)
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.h2.mvstore.*;
import org.h2.mvstore.type.StringDataType;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class CommonsLicensesMVOnly {

    static final String MAP_GND2FILE = "gnd2file";
    static final String MAP_FILE2GNDS = "file2gnds";
    static final String MAP_GND2LIC = "gnd2license";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            log.error(
                    "Usage:\n  build-rev   <store.mvstore>\n  join        <store.mvstore> <commons-mediainfo.json[.gz|.bz2]>\n  export-json <store.mvstore> <out.ndjson>\n  fetch       <url> <destPath> [sha256hex]");
            System.exit(1);
        }
        switch (args[0]) {
            case "build-rev" ->
                buildRev(args[1]);
            case "join" -> {
                req(args.length == 3, "join braucht Dump-Pfad");
                join(args[1], args[2]);
            }
            case "export-json" -> {
                req(args.length == 3, "export-json <store> <out.ndjson>");
                exportNdjson(args[1], args[2]);
            }
            case "fetch" -> {
                req(args.length >= 3, "fetch <url> <dest> [sha256]");
                downloadWithResume(args[1], Paths.get(args[2]), args.length >= 4 ? args[3] : null);
            }
            default ->
                die("Unbekanntes Kommando: " + args[0]);
        }
    }

    // Reverse-Index: gnd2file -> file2gnds
    static void buildRev(String storePath) {
        try (MVStore store = new MVStore.Builder().fileName(storePath).open()) {
            MVMap<String, String> gnd2file = openStrMap(store, MAP_GND2FILE);
            MVMap<String, String> file2gnds = openStrMap(store, MAP_FILE2GNDS);
            file2gnds.clear();

            long n = 0;
            for (var e : gnd2file.entrySet()) {
                String gnd = e.getKey();
                String fn = e.getValue();
                if (gnd == null || fn == null || fn.isBlank()) {
                    continue;
                }

                String key = canon(stripNamespace(fn));
                if (key == null) {
                    continue;
                }

                String prev = file2gnds.get(key);
                if (prev == null || prev.isEmpty()) {
                    file2gnds.put(key, gnd);
                } else if (!containsId(prev, gnd)) {
                    file2gnds.put(key, prev + ";" + gnd);
                }

                if (++n % 100_000 == 0) {
                    store.commit();
                }
            }
            store.commit();
            log.info("Build-Rev fertig. file2gnds keys={}", file2gnds.sizeAsLong());
        }
    }

    // Dump joinen -> gnd2license schreiben
    static void join(String storePath, String dumpPath) throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonFactory jf = om.getFactory();

        try (MVStore store = new MVStore.Builder().fileName(storePath).open()) {
            MVMap<String, String> file2gnds = openStrMap(store, MAP_FILE2GNDS);
            if (file2gnds.isEmpty()) {
                die("file2gnds ist leer. Erst build-rev ausführen.");
            }
            MVMap<String, String> gnd2lic = openStrMap(store, MAP_GND2LIC);

            long seen = 0, writes = 0;
            try (InputStream raw = openMaybeCompressed(dumpPath);
                    InputStream in = new BufferedInputStream(raw, 1 << 20);
                    JsonParser p = jf.createParser(in)) {

                JsonToken t = p.nextToken();
                if (t == JsonToken.START_ARRAY) {
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        writes += processOne(om.readTree(p), file2gnds, gnd2lic);
                        if (++seen % 50_000 == 0) {
                            store.commit();
                            log.info("seen={} writes={}", seen, writes);
                        }
                    }
                } else {
                    while (t != null) {
                        if (t == JsonToken.START_OBJECT) {
                            writes += processOne(om.readTree(p), file2gnds, gnd2lic);
                            if (++seen % 50_000 == 0) {
                                store.commit();
                                log.info("seen={} writes={}", seen, writes);
                            }
                        }
                        t = p.nextToken();
                    }
                }
            }
            store.commit();
            log.info("Join fertig. seen={} writes={} gnd2license={}", seen, writes, gnd2lic.sizeAsLong());
        }
    }

    // Ein Mediainfo-Objekt verarbeiten
    static long processOne(JsonNode node, MVMap<String, String> file2gnds, MVMap<String, String> gnd2lic) {
        JsonNode titleN = node.get("title");
        if (titleN == null || titleN.isNull()) {
            return 0;
        }
        String title = titleN.asText();
        String key = canon(stripNamespace(title));
        if (key == null) {
            return 0;
        }

        String gnds = file2gnds.get(key);
        if (gnds == null) {
            return 0;
        }

        JsonNode stmts = node.get("statements");
        String lic = dedupJoin(extractItemIds(stmts, "P275"));
        String st = dedupJoin(extractItemIds(stmts, "P6216"));
        String valueJson = toJsonValue(stripNamespace(title), lic, st);

        long writes = 0;
        for (String gnd : gnds.split(";")) {
            String k = gnd.trim();
            if (k.isEmpty()) {
                continue;
            }
            gnd2lic.put(k, valueJson);
            writes++;
        }
        return writes;
    }

    // NDJSON exportieren
    static void exportNdjson(String storePath, String outPath) throws Exception {
        ObjectMapper om = new ObjectMapper();
        try (MVStore store = new MVStore.Builder().fileName(storePath).open();
                BufferedWriter bw = Files.newBufferedWriter(Paths.get(outPath))) {

            MVMap<String, String> gnd2file = openStrMap(store, MAP_GND2FILE);
            MVMap<String, String> gnd2lic = openStrMap(store, MAP_GND2LIC);
            long n = 0;

            for (var e : gnd2file.entrySet()) {
                String gnd = e.getKey();
                String file = stripNamespace(e.getValue());
                String licJ = gnd2lic.get(gnd);

                ObjectNode obj = om.createObjectNode();
                obj.put("gnd_uri", gnd);
                obj.put("file", file);

                String lic = "", st = "";
                if (licJ != null) {
                    int i = licJ.indexOf("\"license\":\"");
                    if (i >= 0) {
                        int j = licJ.indexOf('"', i + 11);
                        lic = licJ.substring(i + 11, j);
                    }
                    i = licJ.indexOf("\"status\":\"");
                    if (i >= 0) {
                        int j = licJ.indexOf('"', i + 10);
                        st = licJ.substring(i + 10, j);
                    }
                }
                obj.putPOJO("license_qids", lic.isEmpty() ? List.of() : Arrays.asList(lic.split(";")));
                obj.putPOJO("status_qids", st.isEmpty() ? List.of() : Arrays.asList(st.split(";")));

                bw.write(om.writeValueAsString(obj));
                bw.write('\n');
                if (++n % 200_000 == 0) {
                    bw.flush();
                    log.info("export rows={}", n);
                }
            }
        }
        log.info("NDJSON exportiert: {}", outPath);
    }

    // OkHttp Downloader mit Resume + optionaler SHA-256
    public static void downloadWithResume(String url, Path dest, String expectedSha256Hex) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .readTimeout(java.time.Duration.ofMinutes(5))
                .callTimeout(java.time.Duration.ofMinutes(10))
                .build();

        Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".part");
        long existing = Files.exists(tmp) ? Files.size(tmp) : 0L;

        Request.Builder rb = new Request.Builder().url(url);
        if (existing > 0) {
            rb.header("Range", "bytes=" + existing + "-");
        }

        try (Response resp = client.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                if (resp.code() == 200 && existing > 0) {
                    Files.deleteIfExists(tmp);
                    existing = 0;
                } else {
                    throw new IOException("HTTP " + resp.code() + " for " + url);
                }
            }
            boolean append = existing > 0 && resp.code() == 206;
            Files.createDirectories(dest.getParent() == null ? Paths.get(".") : dest.getParent());
            try (InputStream in = resp.body().byteStream();
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp.toFile(), append), 1 << 20)) {
                byte[] buf = new byte[1 << 20];
                int r;
                long total = existing;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    total += r;
                    if ((total & ((1 << 22) - 1)) == 0) {
                        log.debug("downloaded ~{} MiB", total >> 20); // alle ~4 MiB
                    }
                }
            }
        }

        if (expectedSha256Hex != null && !expectedSha256Hex.isBlank()) {
            String got = sha256Hex(tmp);
            if (!expectedSha256Hex.equalsIgnoreCase(got)) {
                Files.deleteIfExists(tmp);
                throw new IOException("SHA-256 mismatch. expected=" + expectedSha256Hex + " got=" + got);
            }
        }

        try {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Downloaded: {}", dest);
    }

    static String sha256Hex(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p), 1 << 20)) {
            byte[] buf = new byte[1 << 20];
            int r;
            while ((r = in.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Helpers
    static MVMap<String, String> openStrMap(MVStore store, String name) {
        return store.openMap(name, new MVMap.Builder<String, String>()
                .keyType(StringDataType.INSTANCE).valueType(StringDataType.INSTANCE));
    }

    static List<String> extractItemIds(JsonNode stmts, String pid) {
        List<String> out = new ArrayList<>(2);
        if (stmts == null || !stmts.has(pid)) {
            return out;
        }
        for (JsonNode s : stmts.get(pid)) {
            JsonNode id = s.path("mainsnak").path("datavalue").path("value").get("id");
            if (id != null && !id.isNull()) {
                out.add(id.asText());
            }
        }
        return out;
    }

    static String toJsonValue(String file, String lic, String st) {
        return "{\"file\":\"" + jsonEsc(file) + "\",\"license\":\"" + jsonEsc(lic) + "\",\"status\":\"" + jsonEsc(st)
                + "\"}";
    }

    static String canon(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().replace('\u00A0', ' ').replace('_', ' ');
        n = stripNamespace(n);
        if (n.isEmpty()) {
            return null;
        }
        return n.toLowerCase(Locale.ROOT);
    }

    static String stripNamespace(String title) {
        int idx = title.indexOf(':');
        if (idx > 0 && idx < 20) {
            return title.substring(idx + 1).trim();
        }
        return title.trim();
    }

    static boolean containsId(String semicol, String id) {
        int from = 0;
        while (true) {
            int i = semicol.indexOf(id, from);
            if (i < 0) {
                return false;
            }
            boolean leftOk = (i == 0) || semicol.charAt(i - 1) == ';';
            int j = i + id.length();
            boolean rightOk = (j == semicol.length()) || semicol.charAt(j) == ';';
            if (leftOk && rightOk) {
                return true;
            }
            from = i + 1;
        }
    }

    static String dedupJoin(List<String> in) {
        return in == null || in.isEmpty() ? "" : String.join(";", new LinkedHashSet<>(in));
    }

    static InputStream openMaybeCompressed(String path) throws IOException {
        InputStream in = Files.newInputStream(Paths.get(path));
        if (path.endsWith(".gz")) {
            return new GZIPInputStream(in, 1 << 20);
        }
        if (path.endsWith(".bz2")) {
            return new BZip2CompressorInputStream(in, true);
        }
        return in;
    }

    static void die(String m) {
        log.error(m);
        System.exit(2);
    }

    static void req(boolean b, String m) {
        if (!b) {
            die(m);
        }
    }

    static String jsonEsc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
