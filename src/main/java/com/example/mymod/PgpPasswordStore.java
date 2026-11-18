package com.example.mymod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 口令保管：把各密钥的口令用 AES-256-GCM 加密后存到 <dataDir>/passwords.json。
 * 密钥来自同目录下的 .secret（32 字节随机数，权限 600）。
 *
 * 说明：这是「本机绑定」的保护 —— 换了机器或删了 .secret 就解不开，
 * 能防止口令以明文躺在磁盘上；但挡不住已经拿到你整台机器读写权限的人。
 */
public final class PgpPasswordStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int SECRET_BYTES = 32;

    private final Path storeFile;
    private final Path secretFile;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private SecretKeySpec key;

    private static final class Entry {
        String nonce;
        String data;
    }

    public PgpPasswordStore(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        this.storeFile = dataDir.resolve("passwords.json");
        this.secretFile = dataDir.resolve(".secret");
        this.key = loadOrCreateKey();
        load();
    }

    private SecretKeySpec loadOrCreateKey() throws IOException {
        byte[] secret;
        if (Files.exists(secretFile)) {
            secret = Files.readAllBytes(secretFile);
            if (secret.length != SECRET_BYTES) {
                throw new IOException("口令密钥文件长度异常（期望 " + SECRET_BYTES + " 字节）");
            }
        } else {
            secret = new byte[SECRET_BYTES];
            RANDOM.nextBytes(secret);
            Files.write(secretFile, secret);
            restrictPermissions(secretFile);
        }
        byte[] digest = sha256(secret);
        return new SecretKeySpec(digest, "AES");
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static void restrictPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {
            // Windows 等不支持 POSIX 权限的平台直接跳过
        }
    }

    private void load() throws IOException {
        entries.clear();
        if (!Files.exists(storeFile)) {
            return;
        }
        String json = Files.readString(storeFile, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject map = root.has("entries") ? root.getAsJsonObject("entries") : new JsonObject();
            for (String fingerprint : map.keySet()) {
                JsonObject item = map.getAsJsonObject(fingerprint);
                Entry entry = new Entry();
                entry.nonce = item.get("nonce").getAsString();
                entry.data = item.get("data").getAsString();
                entries.put(fingerprint, entry);
            }
        } catch (Exception e) {
            throw new IOException("passwords.json 解析失败: " + e.getMessage(), e);
        }
    }

    private void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject map = new JsonObject();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("nonce", e.getValue().nonce);
            item.addProperty("data", e.getValue().data);
            map.add(e.getKey(), item);
        }
        root.add("entries", map);

        Path tmp = storeFile.resolveSibling(storeFile.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
        restrictPermissions(tmp);
        Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
        restrictPermissions(storeFile);
    }

    public synchronized void put(String fingerprint, String password) throws IOException {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint 不能为空");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] encrypted;
        try {
            encrypted = crypt(Cipher.ENCRYPT_MODE, nonce, password.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IOException("加密口令失败: " + e.getMessage(), e);
        }

        Entry entry = new Entry();
        entry.nonce = Base64.getEncoder().encodeToString(nonce);
        entry.data = Base64.getEncoder().encodeToString(encrypted);
        entries.put(normalize(fingerprint), entry);
        save();
    }

    public synchronized Optional<String> get(String fingerprint) {
        Entry entry = entries.get(normalize(fingerprint));
        if (entry == null) {
            return Optional.empty();
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(entry.nonce);
            byte[] data = Base64.getDecoder().decode(entry.data);
            byte[] plain = crypt(Cipher.DECRYPT_MODE, nonce, data);
            return Optional.of(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            MyMod.LOGGER.warn("读取口令失败（{}）: {}", fingerprint, e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized boolean remove(String fingerprint) {
        boolean removed = entries.remove(normalize(fingerprint)) != null;
        if (removed) {
            try {
                save();
            } catch (IOException e) {
                MyMod.LOGGER.warn("写回 passwords.json 失败: {}", e.getMessage());
            }
        }
        return removed;
    }

    public synchronized void clear() throws IOException {
        entries.clear();
        save();
    }

    public synchronized List<String> fingerprints() {
        return new ArrayList<>(entries.keySet());
    }

    private byte[] crypt(int mode, byte[] nonce, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        return cipher.doFinal(input);
    }

    private static String normalize(String fingerprint) {
        return fingerprint.replace(" ", "").toUpperCase();
    }
}
