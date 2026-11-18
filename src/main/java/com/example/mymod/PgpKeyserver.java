package com.example.mymod;

import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 从 OpenPGP 公钥服务器（HKP 协议）拉取公钥。
 * 例：https://keys.openpgp.org/pks/lookup?op=get&search=0xABCDEF...
 *
 * 只用 JDK 自带的 HttpURLConnection，不引入额外依赖。
 */
public final class PgpKeyserver {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final String USER_AGENT = "MyMod-OpenPGP/1.1 (Forge 1.20.1)";

    private PgpKeyserver() {
    }

    /** 结果：公钥环 + 便于展示的指纹/身份摘要。 */
    public static final class Result {
        private final List<PGPPublicKeyRing> rings;
        private final List<String> summaries;

        Result(List<PGPPublicKeyRing> rings, List<String> summaries) {
            this.rings = rings;
            this.summaries = summaries;
        }

        public List<PGPPublicKeyRing> rings() {
            return rings;
        }

        public List<String> summaries() {
            return summaries;
        }
    }

    public static Result fetch(String server, String query, boolean allowInsecureHttp) throws Exception {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询内容不能为空（可用邮箱、Key ID 或指纹）");
        }
        URL url = buildLookupUrl(server, query, allowInsecureHttp);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/pgp-keys, text/plain");

            int code = conn.getResponseCode();
            if (code == 404) {
                throw new IOException("密钥服务器上没有找到匹配的公钥（HTTP 404）");
            }
            if (code != 200) {
                throw new IOException("密钥服务器返回 HTTP " + code);
            }

            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                body = readLimited(in, MAX_RESPONSE_BYTES);
            }
            if (body.length == 0) {
                throw new IOException("密钥服务器返回了空响应");
            }
            return parse(body);
        } finally {
            conn.disconnect();
        }
    }

    static URL buildLookupUrl(String server, String query, boolean allowInsecureHttp) throws Exception {
        String base = (server == null || server.isBlank()) ? PgpConfig.keyserver() : server.trim();
        if (!base.contains("://")) {
            base = "https://" + base;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String scheme = base.substring(0, base.indexOf("://")).toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("只支持 http/https 密钥服务器地址");
        }
        if ("http".equals(scheme) && !allowInsecureHttp) {
            throw new IllegalArgumentException("已禁用明文 http，请在配置中开启 allowInsecureHttp 或改用 https");
        }
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        // HKP 规定 search 参数对指纹/KeyID 需要 0x 前缀；对邮箱地址则不需要
        boolean looksLikeHex = query.replace("0x", "").replace(" ", "").matches("(?i)[0-9a-f]{8,40}");
        String search = looksLikeHex
                ? "0x" + query.replace(" ", "").replace("0X", "").replace("0x", "")
                : encoded;
        String url = base + "/pks/lookup?op=get&search=" + search;
        return URI.create(url).toURL();
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("密钥响应超过 " + (limit / 1024) + " KB 上限，已中止");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Result parse(byte[] body) throws Exception {
        List<PGPPublicKeyRing> rings = new ArrayList<>();
        List<String> summaries = new ArrayList<>();

        try (InputStream decoder = PGPUtil.getDecoderStream(new ByteArrayInputStream(body))) {
            PGPObjectFactory factory = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
            Object object;
            while ((object = factory.nextObject()) != null) {
                if (object instanceof PGPPublicKeyRing) {
                    collect((PGPPublicKeyRing) object, rings, summaries);
                } else if (object instanceof PGPPublicKeyRingCollection) {
                    Iterator<PGPPublicKeyRing> it = ((PGPPublicKeyRingCollection) object).getKeyRings();
                    while (it.hasNext()) {
                        collect(it.next(), rings, summaries);
                    }
                }
            }
        }

        if (rings.isEmpty()) {
            throw new IOException("响应里没有可用的公钥（可能查询无结果，或服务器返回了错误页）");
        }
        return new Result(rings, summaries);
    }

    private static void collect(PGPPublicKeyRing ring, List<PGPPublicKeyRing> rings, List<String> summaries) {
        rings.add(ring);
        for (Iterator<PGPPublicKey> it = ring.getPublicKeys(); it.hasNext(); ) {
            PGPPublicKey key = it.next();
            summaries.add(fingerprintOf(key) + "  " + firstUserId(key));
        }
    }

    public static String fingerprintOf(PGPPublicKey key) {
        StringBuilder sb = new StringBuilder();
        for (byte b : key.getFingerprint()) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String firstUserId(PGPPublicKey key) {
        Iterator<String> ids = key.getUserIDs();
        return ids.hasNext() ? ids.next() : "(无 UID)";
    }
}
