# MyMod

Forge 1.20.1 的 OpenPGP 模组：密钥生成、文本/文件加解密、签名验签、口令管理，以及从密钥服务器拉取公钥。

## 功能

- **密钥管理**：生成 RSA 2048 密钥对，公钥/私钥以 ASCII Armor 保存；首次运行自动创建数据目录。
- **口令管理**：口令用 AES-256-GCM 加密后存入 `passwords.json`（密钥为本机 `.secret`），不再是明文。
- **文本操作**：加密、解密、签名、验签；也支持文件。
- **文件加密/解密**：`/pgp encryptfile`、`/pgp decryptfile`；解密成功后可自动删除 `.gpg` 源文件（可配置）。
- **进度反馈**：命令走聊天栏、GUI 走底部状态条，形如 `[██████░░░░░░░░░░░░░░] 30% 加密中`。
- **密钥服务器**：按邮箱 / Key ID / 指纹从 HKP 服务器下载公钥，服务器地址可配置、也可在游戏内临时覆盖。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/pgp gui` | 打开操作面板（客户端） |
| `/pgp genkey <identity> <passphrase>` | 生成密钥对并加密保存口令 |
| `/pgp keys` | 列出当前公钥及指纹 |
| `/pgp export public` / `private` | 导出公钥 / 私钥 |
| `/pgp encrypt <message>` | 用公钥加密文本 |
| `/pgp decrypt <ciphertext>` | 用私钥解密文本 |
| `/pgp sign <message>` | 对文本签名 |
| `/pgp verify <message>\|<signature>` | 验证签名 |
| `/pgp encryptfile <path>` | 加密文件，输出 `<文件>.gpg` |
| `/pgp decryptfile <path>` | 解密文件，默认删除 `.gpg` 源文件 |
| `/pgp fetch <query>` | 从密钥服务器下载公钥（邮箱 / KeyID / 指纹） |
| `/pgp keyserver` | 查看当前服务器 |
| `/pgp keyserver set <url>` | 本次会话临时切换服务器 |
| `/pgp keyserver reset` | 恢复配置文件里的服务器 |
| `/pgp forget <fingerprint>` | 删除记住的口令 |

## 配置（`config/mymod-common.toml`）

```toml
[pgp]
    dataDirectory = "mods/mymoddata"     # keys/、passwords.json、.secret 所在目录
    keyserver = "https://keys.openpgp.org"
    allowInsecureHttp = false            # 允许 http:// 密钥服务器（不推荐）
    deleteSourceAfterDecrypt = true      # 解密成功后删除 .gpg 源文件
    rememberPassphrase = true            # 记住口令（AES-GCM 加密存储）
```

## 文件结构

```
mods/mymoddata/
├── keys/
│   ├── public.asc
│   └── private.asc
├── passwords.json    # AES-256-GCM 加密的口令条目
└── .secret           # 本机随机密钥（权限 600）
```

旧版的 `pgp_keys/` 目录会在首次启动时自动迁移过来；旧的明文 `passphrase.txt` 会在迁移进加密口令库后被删除。

## 安全说明

- `passwords.json` 采用 AES-256-GCM 加密，密钥来自本机 `.secret`：换机器或删除 `.secret` 后无法解出，能避免口令以明文落盘。
- 这**不等于**硬件级保护：拿到整台机器读写权限的人仍然可以解出。
- 密钥服务器下载只做公钥导入，不会上传你的私钥。

## 构建

```bash
./gradlew build
```

产物在 `build/libs/mymod-1.1.0.jar`。
