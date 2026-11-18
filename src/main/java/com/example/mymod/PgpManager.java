package com.example.mymod;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PgpManager {

    private static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
    private static final int BUFFER_SIZE = 64 * 1024;

    private static PGPSecretKeyRing secretKeyRing;
    private static final List<PGPPublicKeyRing> publicKeyRings = new ArrayList<>();
    private static char[] privateKeyPassphrase = new char[0];
    private static Path dataDir;
    private static Path keysDir;
    private static PgpPasswordStore passwordStore;
    private static boolean initialized;

    private PgpManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        dataDir = Paths.get(PgpConfig.dataDirectory());
        keysDir = dataDir.resolve("keys");
        try {
            Files.createDirectories(keysDir);
            migrateLegacyKeys();
            passwordStore = new PgpPasswordStore(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("初始化 PGP 数据目录失败: " + e.getMessage(), e);
        }

        loadKeys();
        initialized = true;
    }

    /** 旧版本把密钥放在工作目录的 pgp_keys/，这里做一次搬迁。 */
    private static void migrateLegacyKeys() throws IOException {
        Path legacy = Paths.get("pgp_keys");
        if (!Files.isDirectory(legacy)) {
            return;
        }
        String[] names = {"public.asc", "private.asc", "passphrase.txt"};
        for (String name : names) {
            Path from = legacy.resolve(name);
            Path to = keysDir.resolve(name);
            if (Files.exists(from) && !Files.exists(to)) {
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                MyMod.LOGGER.info("已迁移旧密钥文件 {} -> {}", from, to);
            }
        }
    }

    private static void loadKeys() {
        publicKeyRings.clear();
        Path publicKeyFile = keysDir.resolve("public.asc");
        Path privateKeyFile = keysDir.resolve("private.asc");
        Path legacyPassphrase = keysDir.resolve("passphrase.txt");

        if (Files.exists(publicKeyFile)) {
            try {
                publicKeyRings.addAll(parsePublicKeyRings(
                        Files.readString(publicKeyFile, StandardCharsets.UTF_8)));
            } catch (Exception e) {
                MyMod.LOGGER.error("加载公钥失败: {}", e.getMessage());
            }
        }
        if (publicKeyRings.isEmpty()) {
            loadEmbeddedPublicKey();
        }

        if (Files.exists(privateKeyFile)) {
            try {
                secretKeyRing = parseSecretKeyRing(
                        Files.readString(privateKeyFile, StandardCharsets.UTF_8));
            } catch (Exception e) {
                MyMod.LOGGER.error("加载私钥失败: {}", e.getMessage());
                secretKeyRing = null;
            }
        }

        // 旧版明文口令：迁移进加密口令库后删除明文文件
        if (Files.exists(legacyPassphrase)) {
            try {
                String pass = Files.readString(legacyPassphrase, StandardCharsets.UTF_8).trim();
                privateKeyPassphrase = pass.toCharArray();
                if (secretKeyRing != null && passwordStore != null && PgpConfig.rememberPassphrase()) {
                    passwordStore.put(fingerprintOf(secretKeyRing), pass);
                    Files.deleteIfExists(legacyPassphrase);
                    MyMod.LOGGER.info("已将明文口令迁移到加密口令库，并删除 passphrase.txt");
                }
            } catch (Exception e) {
                MyMod.LOGGER.warn("读取旧口令失败: {}", e.getMessage());
            }
        }
    }

    private static void loadEmbeddedPublicKey() {
        try (InputStream in = PgpManager.class.getResourceAsStream("/assets/mymod/pgp/public_key.asc")) {
            if (in == null) {
                return;
            }
            publicKeyRings.addAll(parsePublicKeyRings(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            MyMod.LOGGER.info("已加载内置公钥，共 {} 个密钥环", publicKeyRings.size());
        } catch (Exception e) {
            MyMod.LOGGER.warn("加载内置公钥失败: {}", e.getMessage());
        }
    }

    private static List<PGPPublicKeyRing> parsePublicKeyRings(String armored) throws IOException, PGPException {
        List<PGPPublicKeyRing> rings = new ArrayList<>();
        try (InputStream decoder = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {
            PGPObjectFactory factory = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
            Object object;
            while ((object = factory.nextObject()) != null) {
                if (object instanceof PGPPublicKeyRing) {
                    rings.add((PGPPublicKeyRing) object);
                } else if (object instanceof PGPPublicKeyRingCollection) {
                    Iterator<PGPPublicKeyRing> it = ((PGPPublicKeyRingCollection) object).getKeyRings();
                    while (it.hasNext()) {
                        rings.add(it.next());
                    }
                }
            }
        }
        return rings;
    }

    private static PGPSecretKeyRing parseSecretKeyRing(String armored) throws IOException, PGPException {
        try (InputStream decoder = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {
            PGPObjectFactory factory = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
            Object object;
            while ((object = factory.nextObject()) != null) {
                if (object instanceof PGPSecretKeyRing) {
                    return (PGPSecretKeyRing) object;
                }
            }
        }
        return null;
    }

    private static void saveKeys() throws IOException {
        Files.createDirectories(keysDir);
        if (!publicKeyRings.isEmpty()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (PGPPublicKeyRing ring : publicKeyRings) {
                try (ArmoredOutputStream aos = new ArmoredOutputStream(baos)) {
                    ring.encode(aos);
                }
            }
            Files.write(keysDir.resolve("public.asc"), baos.toByteArray());
        }
        if (secretKeyRing != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ArmoredOutputStream aos = new ArmoredOutputStream(baos)) {
                secretKeyRing.encode(aos);
            }
            Files.write(keysDir.resolve("private.asc"), baos.toByteArray());
        }
    }

    // ---------------------------------------------------------------- 密钥生成/导出

    public static String generateKeyPair(String identity, String passphrase, boolean remember,
                                         PgpProgress progress) throws Exception {
        initialize();
        progress.update(5, "生成主密钥");
        if (passphrase == null) {
            passphrase = "";
        }
        privateKeyPassphrase = passphrase.toCharArray();

        progress.update(20, "计算 RSA 2048 密钥对");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", PROVIDER);
        kpg.initialize(2048);
        KeyPair masterKp = kpg.generateKeyPair();
        KeyPair encryptionKp = kpg.generateKeyPair();

        PGPKeyPair masterKeyPair = new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_SIGN, masterKp, new Date());
        PGPKeyPair encryptionKeyPair = new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_ENCRYPT, encryptionKp, new Date());
        PGPDigestCalculator sha1Calc =
                new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        progress.update(45, "构建密钥环");
        PGPKeyRingGenerator keyRingGenerator = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                masterKeyPair,
                identity,
                sha1Calc,
                null,
                null,
                new JcaPGPContentSignerBuilder(masterKeyPair.getPublicKey().getAlgorithm(),
                        HashAlgorithmTags.SHA256),
                new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc)
                        .setProvider(PROVIDER)
                        .build(privateKeyPassphrase)
        );

        keyRingGenerator.addSubKey(encryptionKeyPair);

        progress.update(70, "导出密钥环");
        secretKeyRing = keyRingGenerator.generateSecretKeyRing();
        publicKeyRings.clear();
        publicKeyRings.add(keyRingGenerator.generatePublicKeyRing());

        progress.update(85, "写入磁盘");
        saveKeys();

        if (remember && passwordStore != null && PgpConfig.rememberPassphrase()) {
            passwordStore.put(fingerprintOf(secretKeyRing), passphrase);
        }

        progress.update(100, "密钥对已生成");
        return exportPublicKey();
    }

    public static String exportPublicKey() throws IOException {
        if (publicKeyRings.isEmpty()) {
            throw new IllegalStateException("PGP 公钥尚未生成或加载。");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (PGPPublicKeyRing ring : publicKeyRings) {
            try (ArmoredOutputStream aos = new ArmoredOutputStream(baos)) {
                ring.encode(aos);
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    public static String exportPrivateKey() throws IOException {
        if (secretKeyRing == null) {
            throw new IllegalStateException("PGP 私钥尚未生成或导入。");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ArmoredOutputStream aos = new ArmoredOutputStream(baos)) {
            secretKeyRing.encode(aos);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- 文本操作

    public static String encrypt(String message) throws Exception {
        return encrypt(message, PgpProgress.NOOP);
    }

    public static String encrypt(String message, PgpProgress progress) throws Exception {
        initialize();
        PGPPublicKey encryptionKey = findEncryptionKey();
        if (encryptionKey == null) {
            throw new IllegalStateException("当前公钥没有可用于加密的子密钥。");
        }

        progress.update(10, "准备加密");
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
            PGPEncryptedDataGenerator encGen = newEncryptor();
            encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(encryptionKey)
                    .setProvider(PROVIDER));
            try (OutputStream encOut = encGen.open(armoredOut, new byte[BUFFER_SIZE])) {
                progress.update(40, "写入加密数据");
                PGPLiteralDataGenerator literal = new PGPLiteralDataGenerator();
                try (OutputStream literalOut = literal.open(encOut, PGPLiteralData.BINARY, "message",
                        messageBytes.length, new Date())) {
                    int offset = 0;
                    while (offset < messageBytes.length) {
                        int len = Math.min(BUFFER_SIZE, messageBytes.length - offset);
                        literalOut.write(messageBytes, offset, len);
                        offset += len;
                        progress.update(40 + (int) (55.0 * offset / messageBytes.length), "加密中");
                    }
                }
            }
        }
        progress.update(100, "加密完成");
        return out.toString(StandardCharsets.UTF_8);
    }

    public static String decrypt(String armoredCiphertext) throws Exception {
        return decrypt(armoredCiphertext, PgpProgress.NOOP);
    }

    public static String decrypt(String armoredCiphertext, PgpProgress progress) throws Exception {
        initialize();
        if (secretKeyRing == null) {
            throw new IllegalStateException("请先生成或导入私钥。");
        }
        progress.update(10, "解析密文");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new ByteArrayInputStream(armoredCiphertext.getBytes(StandardCharsets.UTF_8))) {
            decryptStream(in, out, progress, -1);
        }
        progress.update(100, "解密完成");
        return out.toString(StandardCharsets.UTF_8);
    }

    public static String sign(String message) throws Exception {
        return sign(message, PgpProgress.NOOP);
    }

    public static String sign(String message, PgpProgress progress) throws Exception {
        initialize();
        if (secretKeyRing == null) {
            throw new IllegalStateException("请先生成或导入私钥。");
        }
        PGPSecretKey secretKey = findSigningKey(secretKeyRing);
        if (secretKey == null) {
            throw new IllegalStateException("私钥环中没有可用的签名密钥。");
        }
        progress.update(20, "解锁私钥");
        PGPPrivateKey privateKey = secretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
                new JcaPGPDigestCalculatorProviderBuilder().build())
                .setProvider(PROVIDER)
                .build(passphraseFor(secretKey)));

        PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
                        .setProvider(PROVIDER));
        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);

        progress.update(60, "计算签名");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
            signatureGenerator.update(message.getBytes(StandardCharsets.UTF_8));
            signatureGenerator.generate().encode(armoredOut);
        }
        progress.update(100, "签名完成");
        return out.toString(StandardCharsets.UTF_8);
    }

    public static boolean verify(String message, String armoredSignature) throws Exception {
        return verify(message, armoredSignature, PgpProgress.NOOP);
    }

    public static boolean verify(String message, String armoredSignature, PgpProgress progress) throws Exception {
        initialize();
        progress.update(30, "解析签名");
        PGPSignature signature = null;
        try (InputStream decoder = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armoredSignature.getBytes(StandardCharsets.UTF_8)))) {
            PGPObjectFactory factory = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
            Object object = factory.nextObject();
            if (object instanceof PGPSignature) {
                signature = (PGPSignature) object;
            } else if (object instanceof PGPSignatureList) {
                signature = ((PGPSignatureList) object).get(0);
            }
        }
        if (signature == null) {
            throw new IllegalArgumentException("签名格式不正确。");
        }
        PGPPublicKey pubKey = findPublicKey(signature.getKeyID());
        if (pubKey == null) {
            progress.update(100, "找不到对应公钥");
            return false;
        }
        progress.update(70, "校验签名");
        signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(PROVIDER), pubKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        boolean valid = signature.verify();
        progress.update(100, valid ? "签名有效" : "签名无效");
        return valid;
    }

    // ---------------------------------------------------------------- 文件操作

    /** 加密文件，输出 <源文件>.gpg。 */
    public static Path encryptFile(Path source, PgpProgress progress) throws Exception {
        initialize();
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("文件不存在: " + source);
        }
        PGPPublicKey encryptionKey = findEncryptionKey();
        if (encryptionKey == null) {
            throw new IllegalStateException("请先生成或导入公钥。");
        }
        long total = Files.size(source);
        Path target = source.resolveSibling(source.getFileName() + ".gpg");
        progress.update(0, "加密 " + source.getFileName());

        try (InputStream in = Files.newInputStream(source);
             OutputStream fileOut = Files.newOutputStream(target);
             ArmoredOutputStream armored = new ArmoredOutputStream(fileOut)) {
            PGPEncryptedDataGenerator encGen = newEncryptor();
            encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(encryptionKey)
                    .setProvider(PROVIDER));
            try (OutputStream encOut = encGen.open(armored, new byte[BUFFER_SIZE])) {
                PGPLiteralDataGenerator literal = new PGPLiteralDataGenerator();
                try (OutputStream literalOut = literal.open(encOut, PGPLiteralData.BINARY,
                        target.getFileName().toString(), total, new Date())) {
                    copyWithProgress(in, literalOut, total, progress, "加密中");
                }
            }
        }
        progress.update(100, "已生成 " + target.getFileName());
        return target;
    }

    /** 解密文件；按配置删除 .gpg 源文件，返回明文路径。 */
    public static Path decryptFile(Path source, PgpProgress progress) throws Exception {
        initialize();
        if (secretKeyRing == null) {
            throw new IllegalStateException("请先生成或导入私钥。");
        }
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("文件不存在: " + source);
        }
        long total = Files.size(source);
        String name = source.getFileName().toString();
        String outName = name.toLowerCase(Locale.ROOT).endsWith(".gpg")
                ? name.substring(0, name.length() - 4)
                : name + ".dec";
        Path target = source.resolveSibling(outName);
        progress.update(0, "解密 " + name);

        try (InputStream raw = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {
            decryptStream(raw, out, progress, total);
        }

        if (PgpConfig.deleteSourceAfterDecrypt()) {
            Files.delete(source);
            progress.update(100, "已解密并删除源文件 " + name);
        } else {
            progress.update(100, "已解密到 " + outName);
        }
        return target;
    }

    private static void decryptStream(InputStream rawInput, OutputStream out,
                                      PgpProgress progress, long totalBytes) throws Exception {
        try (InputStream decoder = PGPUtil.getDecoderStream(rawInput)) {
            PGPObjectFactory factory = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());

            PGPEncryptedDataList encryptedDataList = null;
            Object object;
            while ((object = factory.nextObject()) != null) {
                if (object instanceof PGPEncryptedDataList) {
                    encryptedDataList = (PGPEncryptedDataList) object;
                    break;
                }
            }
            if (encryptedDataList == null) {
                throw new IllegalArgumentException("不是有效的 PGP 加密数据。");
            }

            PGPPublicKeyEncryptedData encryptedData = null;
            PGPPrivateKey privateKey = null;
            for (Iterator<?> it = encryptedDataList.getEncryptedDataObjects(); it.hasNext(); ) {
                Object candidate = it.next();
                if (!(candidate instanceof PGPPublicKeyEncryptedData)) {
                    continue;
                }
                PGPPublicKeyEncryptedData data = (PGPPublicKeyEncryptedData) candidate;
                PGPSecretKey secretKey = secretKeyRing.getSecretKey(data.getKeyID());
                if (secretKey != null) {
                    privateKey = secretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
                            new JcaPGPDigestCalculatorProviderBuilder().build())
                            .setProvider(PROVIDER)
                            .build(passphraseFor(secretKey)));
                    encryptedData = data;
                    break;
                }
            }
            if (privateKey == null || encryptedData == null) {
                throw new IllegalStateException("无法找到匹配的私钥来解密。");
            }

            progress.update(15, "解密数据");
            try (InputStream clear = encryptedData.getDataStream(
                    new JcePublicKeyDataDecryptorFactoryBuilder().setProvider(PROVIDER).build(privateKey))) {
                PGPObjectFactory plainFactory =
                        new PGPObjectFactory(clear, new JcaKeyFingerprintCalculator());
                Object message = plainFactory.nextObject();
                while (message instanceof PGPOnePassSignatureList) {
                    message = plainFactory.nextObject();
                }
                if (message instanceof PGPCompressedData) {
                    message = new PGPObjectFactory(((PGPCompressedData) message).getDataStream(),
                            new JcaKeyFingerprintCalculator()).nextObject();
                }
                if (!(message instanceof PGPLiteralData)) {
                    throw new IllegalArgumentException("解密结果不是有效的数据块。");
                }
                try (InputStream literalIn = ((PGPLiteralData) message).getInputStream()) {
                    copyWithProgress(literalIn, out, totalBytes, progress, "解密中");
                }
            }

            if (encryptedData.isIntegrityProtected() && !encryptedData.verify()) {
                throw new PGPException("完整性校验失败，数据可能被篡改。");
            }
        }
    }

    // ---------------------------------------------------------------- 密钥服务器

    public static String currentKeyserver() {
        String override = PgpCommand.keyserverOverride();
        return (override == null || override.isBlank()) ? PgpConfig.keyserver() : override;
    }

    public static PgpKeyserver.Result fetchKeys(String query, PgpProgress progress) throws Exception {
        initialize();
        String server = currentKeyserver();
        progress.update(10, "连接 " + server);
        PgpKeyserver.Result result = PgpKeyserver.fetch(
                server, query, PgpConfig.allowInsecureHttp());
        progress.update(60, "导入公钥");
        int added = importPublicKeys(result.rings());
        if (added > 0) {
            progress.update(85, "写入磁盘");
            saveKeys();
        }
        progress.update(100, added > 0 ? "已导入 " + added + " 个公钥" : "公钥已存在，未改动");
        return result;
    }

    public static int importPublicKeys(List<PGPPublicKeyRing> rings) {
        int added = 0;
        for (PGPPublicKeyRing ring : rings) {
            Iterator<PGPPublicKey> keys = ring.getPublicKeys();
            long primaryId = keys.hasNext() ? keys.next().getKeyID() : -1L;
            if (primaryId == -1L || findPublicKey(primaryId) == null) {
                publicKeyRings.add(ring);
                added++;
            }
        }
        return added;
    }

    public static List<String> publicKeySummaries() {
        List<String> list = new ArrayList<>();
        for (PGPPublicKeyRing ring : publicKeyRings) {
            for (Iterator<PGPPublicKey> it = ring.getPublicKeys(); it.hasNext(); ) {
                PGPPublicKey key = it.next();
                Iterator<String> ids = key.getUserIDs();
                list.add(PgpKeyserver.fingerprintOf(key) + "  " + (ids.hasNext() ? ids.next() : "(无 UID)"));
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- 内部工具

    private static PGPEncryptedDataGenerator newEncryptor() {
        return new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
                        .setProvider(PROVIDER));
    }

    private static void copyWithProgress(InputStream in, OutputStream out, long total,
                                         PgpProgress progress, String stage) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long done = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            done += read;
            if (total > 0) {
                progress.update((int) Math.min(99, done * 100 / total), stage);
            }
        }
        out.flush();
    }

    private static PGPPublicKey findPublicKey(long keyId) {
        for (PGPPublicKeyRing ring : publicKeyRings) {
            PGPPublicKey key = ring.getPublicKey(keyId);
            if (key != null) {
                return key;
            }
        }
        return null;
    }

    private static PGPPublicKey findEncryptionKey() {
        for (PGPPublicKeyRing ring : publicKeyRings) {
            for (Iterator<PGPPublicKey> it = ring.getPublicKeys(); it.hasNext(); ) {
                PGPPublicKey key = it.next();
                if (key.isEncryptionKey()) {
                    return key;
                }
            }
        }
        return null;
    }

    private static PGPSecretKey findSigningKey(PGPSecretKeyRing keyRing) {
        for (Iterator<PGPSecretKey> it = keyRing.getSecretKeys(); it.hasNext(); ) {
            PGPSecretKey key = it.next();
            if (key.isSigningKey()) {
                return key;
            }
        }
        return null;
    }

    private static char[] passphraseFor(PGPSecretKey secretKey) {
        if (passwordStore != null) {
            Optional<String> stored = passwordStore.get(PgpKeyserver.fingerprintOf(secretKey.getPublicKey()));
            if (stored.isPresent()) {
                return stored.get().toCharArray();
            }
        }
        return privateKeyPassphrase.clone();
    }

    public static String fingerprintOf(PGPSecretKeyRing ring) {
        for (Iterator<PGPSecretKey> it = ring.getSecretKeys(); it.hasNext(); ) {
            return PgpKeyserver.fingerprintOf(it.next().getPublicKey());
        }
        return "";
    }

    public static Path dataDirectory() {
        initialize();
        return dataDir;
    }

    public static Path keysDirectory() {
        initialize();
        return keysDir;
    }

    public static boolean hasSecretKey() {
        return secretKeyRing != null;
    }

    public static List<String> storedPassphraseFingerprints() {
        initialize();
        return passwordStore == null ? new ArrayList<>() : passwordStore.fingerprints();
    }

    public static boolean forgetPassphrase(String fingerprint) {
        initialize();
        return passwordStore != null && passwordStore.remove(fingerprint);
    }
}
