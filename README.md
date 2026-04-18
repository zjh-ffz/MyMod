# MyMod

这是一个基于 Forge 1.20.1 的 Java 模组示例，包含 OpenPGP 密钥生成、加密、解密、签名、验证功能，并支持客户端弹窗操作。

## 功能特性

- **自动密钥管理**：模组会在工作目录下自动创建 `pgp_keys` 文件夹，用于存放 OpenPGP 公钥和私钥文件。
- **本地密钥读取**：启动时自动从 `pgp_keys` 文件夹读取现有的密钥对。
- **聊天命令操作**：支持通过聊天命令进行所有 PGP 操作。
- **客户端弹窗界面**：提供直观的 GUI 界面进行密钥生成和加密签名操作。

## 命令说明

- `/pgp gui`：打开 OpenPGP 客户端弹窗界面。
- `/pgp genkey <identity> <passphrase>`：生成一对 PGP 密钥并保存到本地文件夹。
- `/pgp export public`：导出当前公钥。
- `/pgp export private`：导出当前私钥（仅用于测试）。
- `/pgp encrypt <message>`：使用当前公钥加密文本。
- `/pgp decrypt <ciphertext>`：使用当前私钥解密 Armored PGP 文本。
- `/pgp sign <message>`：对文本进行签名。
- `/pgp verify <message>|<signature>`：验证签名。

## 文件结构

```
pgp_keys/
├── public.asc      # 公钥文件
├── private.asc     # 私钥文件
└── passphrase.txt  # 私钥口令（可选）
```

## 使用说明

1. 首次使用时，通过 `/pgp genkey <identity> <passphrase>` 生成密钥对，密钥会自动保存到 `pgp_keys` 文件夹。
2. 后续启动模组时，会自动加载已保存的密钥。
3. 使用 `/pgp gui` 可直接在客户端界面中生成密钥和执行加密签名操作。
4. 如果要验证签名，请使用竖线分隔信息和签名，例如：
   `/pgp verify Hello|-----BEGIN PGP SIGNATURE----- ...`。
