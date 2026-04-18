# Project Guidelines

## Code Style
- Use Java 17 with Lombok annotations for boilerplate reduction
- Follow Minecraft Forge conventions for mod structure
- Use BouncyCastle for cryptographic operations

## Architecture
- **PgpManager**: Core PGP operations (key generation, encryption/decryption/signing)
- **PgpCommand**: Chat command integration using Brigadier
- **PgpScreen**: Client-side GUI for user interactions
- **MyMod**: Entry point with FML event handling

Key files: [PgpManager.java](src/main/java/com/example/mymod/PgpManager.java), [PgpCommand.java](src/main/java/com/example/mymod/PgpCommand.java)

## Build and Test
```bash
# Build the mod
./gradlew build

# Run client
./gradlew runClient

# Run tests
./gradlew test
```

## Conventions
- PGP keys stored in `pgp_keys/` directory in working directory
- Use armored ASCII format for key exchange
- RSA 2048-bit keys with AES-256 encryption and SHA256 signatures

See [README.md](README.md) for detailed usage and command reference.