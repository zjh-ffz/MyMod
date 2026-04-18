package com.example.mymod;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.util.Date;
import java.util.Iterator;

public class PgpManager {
    private static PGPSecretKeyRing secretKeyRing;
    private static PGPPublicKeyRing publicKeyRing;
    private static char[] privateKeyPassphrase = new char[0];
    private static Path keysDir;

    public static void initialize() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // 创建 PGP 密钥文件夹
        keysDir = Paths.get("pgp_keys");
        try {
            Files.createDirectories(keysDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建 PGP 密钥目录", e);
        }

        // 尝试加载现有密钥
        loadKeys();
    }

    private static void loadKeys() {
        Path publicKeyFile = keysDir.resolve("public.asc");
        Path privateKeyFile = keysDir.resolve("private.asc");
        Path passphraseFile = keysDir.resolve("passphrase.txt");

        if (Files.exists(publicKeyFile) && Files.exists(privateKeyFile)) {
            try {
                // 加载公钥
                String publicKeyArmored = Files.readString(publicKeyFile, StandardCharsets.UTF_8);
                InputStream publicIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(publicKeyArmored.getBytes(StandardCharsets.UTF_8)));
                PGPObjectFactory pgpFactory = new PGPObjectFactory(publicIn, new JcaKeyFingerprintCalculator());
                Object obj = pgpFactory.nextObject();
                if (obj instanceof PGPPublicKeyRing) {
                    publicKeyRing = (PGPPublicKeyRing) obj;
                }

                // 加载私钥
                String privateKeyArmored = Files.readString(privateKeyFile, StandardCharsets.UTF_8);
                InputStream privateIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(privateKeyArmored.getBytes(StandardCharsets.UTF_8)));
                PGPObjectFactory privateFactory = new PGPObjectFactory(privateIn, new JcaKeyFingerprintCalculator());
                Object privateObj = privateFactory.nextObject();
                if (privateObj instanceof PGPSecretKeyRing) {
                    secretKeyRing = (PGPSecretKeyRing) privateObj;
                }

                // 加载口令
                if (Files.exists(passphraseFile)) {
                    String passphrase = Files.readString(passphraseFile, StandardCharsets.UTF_8).trim();
                    privateKeyPassphrase = passphrase.toCharArray();
                }

            } catch (Exception e) {
                System.err.println("加载 PGP 密钥失败: " + e.getMessage());
                // 如果加载失败，重置为 null
                secretKeyRing = null;
                publicKeyRing = null;
                privateKeyPassphrase = new char[0];
            }
        }
    }

    private static void saveKeys() throws IOException {
        if (publicKeyRing != null) {
            String publicKeyArmored = armoredKeyRing(publicKeyRing);
            Files.write(keysDir.resolve("public.asc"), publicKeyArmored.getBytes(StandardCharsets.UTF_8));
        }

        if (secretKeyRing != null) {
            String privateKeyArmored = armoredKeyRing(secretKeyRing);
            Files.write(keysDir.resolve("private.asc"), privateKeyArmored.getBytes(StandardCharsets.UTF_8));
        }

        if (privateKeyPassphrase.length > 0) {
            Files.write(keysDir.resolve("passphrase.txt"), new String(privateKeyPassphrase).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static String generateKeyPair(String identity, String passphrase) throws Exception {
        initialize();

        if (passphrase == null) {
            passphrase = "";
        }
        privateKeyPassphrase = passphrase.toCharArray();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(2048);
        KeyPair masterKp = kpg.generateKeyPair();

        PGPKeyPair masterKeyPair = new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_SIGN, masterKp, new Date());
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        PGPKeyRingGenerator keyRingGenerator = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                masterKeyPair,
                identity,
                sha1Calc,
                null,
                null,
                new JcaPGPContentSignerBuilder(masterKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256),
                new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha1Calc)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(privateKeyPassphrase)
        );

        secretKeyRing = keyRingGenerator.generateSecretKeyRing();
        publicKeyRing = keyRingGenerator.generatePublicKeyRing();

        saveKeys();

        return exportPublicKey();
    }

    public static String exportPublicKey() throws IOException {
        if (publicKeyRing == null) {
            throw new IllegalStateException("PGP 公钥尚未生成或加载。");
        }
        return armoredKeyRing(publicKeyRing);
    }

    public static String exportPrivateKey() throws IOException {
        if (secretKeyRing == null) {
            throw new IllegalStateException("PGP 私钥尚未生成或加载。");
        }
        return armoredKeyRing(secretKeyRing);
    }

    private static String armoredKeyRing(PGPPublicKeyRing ring) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ArmoredOutputStream aos = new ArmoredOutputStream(baos)) {
            ring.encode(aos);
            aos.close();
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private static String armoredKeyRing(PGPSecretKeyRing ring) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ArmoredOutputStream aos = new ArmoredOutputStream(baos)) {
            ring.encode(aos);
            aos.close();
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    public static String encrypt(String message) throws Exception {
        if (publicKeyRing == null) {
            throw new IllegalStateException("请先生成或导入公钥。");
        }
        PGPPublicKey encryptionKey = findEncryptionKey(publicKeyRing);
        if (encryptionKey == null) {
            throw new IllegalStateException("无法从公钥中找到加密用密钥。");
        }

        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                    new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                            .setWithIntegrityPacket(true)
                            .setSecureRandom(new SecureRandom())
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            );
            encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(encryptionKey)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME));

            try (OutputStream encOut = encGen.open(armoredOut, messageBytes.length)) {
                PGPLiteralDataGenerator literal = new PGPLiteralDataGenerator();
                try (OutputStream pOut = literal.open(encOut, PGPLiteralData.BINARY, "message", messageBytes.length, new Date())) {
                    pOut.write(messageBytes);
                }
                literal.close();
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static String decrypt(String armoredCiphertext) throws Exception {
        if (secretKeyRing == null) {
            throw new IllegalStateException("请先生成或导入私钥。");
        }
        InputStream decoder = PGPUtil.getDecoderStream(new ByteArrayInputStream(armoredCiphertext.getBytes(StandardCharsets.UTF_8)));
        PGPObjectFactory pgpFactory = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
        Object object = pgpFactory.nextObject();
        PGPEncryptedDataList encryptedDataList;
        if (object instanceof PGPEncryptedDataList) {
            encryptedDataList = (PGPEncryptedDataList) object;
        } else {
            encryptedDataList = (PGPEncryptedDataList) pgpFactory.nextObject();
        }

        Iterator<?> it = encryptedDataList.getEncryptedDataObjects();
        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData encryptedData = null;
        while (it.hasNext()) {
            Object obj = it.next();
            if (obj instanceof PGPPublicKeyEncryptedData) {
                PGPPublicKeyEncryptedData pked = (PGPPublicKeyEncryptedData) obj;
                PGPSecretKey secretKey = secretKeyRing.getSecretKey(pked.getKeyID());
                if (secretKey != null) {
                    privateKey = secretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
                            new JcaPGPDigestCalculatorProviderBuilder().build())
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build(privateKeyPassphrase));
                    encryptedData = pked;
                    break;
                }
            }
        }
        if (privateKey == null || encryptedData == null) {
            throw new IllegalStateException("无法找到匹配的私钥来解密。");
        }

        InputStream clear = encryptedData.getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKey));
        PGPObjectFactory plainFactory = new PGPObjectFactory(clear, new JcaKeyFingerprintCalculator());
        Object message = plainFactory.nextObject();
        if (message instanceof PGPLiteralData) {
            try (InputStream literalIn = ((PGPLiteralData) message).getInputStream()) {
                return new String(literalIn.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("解密结果不是文本数据。");
    }

    public static String sign(String message) throws Exception {
        if (secretKeyRing == null) {
            throw new IllegalStateException("请先生成或导入私钥。");
        }
        PGPSecretKey secretKey = findSigningKey(secretKeyRing);
        if (secretKey == null) {
            throw new IllegalStateException("无法从私钥环中找到签名密钥。");
        }
        PGPPrivateKey privateKey = secretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder(
                new JcaPGPDigestCalculatorProviderBuilder().build())
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(privateKeyPassphrase));

        PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME));
        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
            signatureGenerator.update(message.getBytes(StandardCharsets.UTF_8));
            PGPSignature signature = signatureGenerator.generate();
            signature.encode(armoredOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static boolean verify(String message, String armoredSignature) throws Exception {
        if (publicKeyRing == null) {
            throw new IllegalStateException("请先生成或导入公钥。");
        }
        InputStream decoder = PGPUtil.getDecoderStream(new ByteArrayInputStream(armoredSignature.getBytes(StandardCharsets.UTF_8)));
        PGPObjectFactory pgpFactory = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
        Object object = pgpFactory.nextObject();
        PGPSignature signature = null;
        if (object instanceof PGPSignature) {
            signature = (PGPSignature) object;
        } else if (object instanceof PGPSignatureList) {
            signature = ((PGPSignatureList) object).get(0);
        }
        if (signature == null) {
            throw new IllegalArgumentException("签名格式不正确。");
        }
        PGPPublicKey pubKey = publicKeyRing.getPublicKey(signature.getKeyID());
        if (pubKey == null) {
            return false;
        }
        signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(BouncyCastleProvider.PROVIDER_NAME), pubKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.verify();
    }

    private static PGPPublicKey findEncryptionKey(PGPPublicKeyRing keyRing) {
        for (PGPPublicKey key : keyRing) {
            if (key.isEncryptionKey()) {
                return key;
            }
        }
        return null;
    }

    private static PGPSecretKey findSigningKey(PGPSecretKeyRing keyRing) {
        for (PGPSecretKey key : keyRing) {
            if (key.isSigningKey()) {
                return key;
            }
        }
        return null;
    }
}
