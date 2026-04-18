#!/usr/bin/env bash
# create-mod.sh — 离线、可审计、跨平台 Forge 1.20.1 项目骨架生成器 (v2.0)
# 用法: ./create-mod.sh [模组名]   (默认: mymod)

set -euo pipefail

# ----- 用户参数 -----
MOD_NAME="${1:-mymod}"
ROOT_DIR="$(pwd)/${MOD_NAME}"

# ----- 颜色输出（可选）-----
if [ -t 1 ]; then
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    RED='\033[0;31m'
    NC='\033[0m'
else
    GREEN=''; YELLOW=''; RED=''; NC=''
fi

# ----- 安全检查 -----
if [ -d "$ROOT_DIR" ]; then
    echo -e "${YELLOW}⚠️  目录已存在: $ROOT_DIR${NC}"
    read -p "是否覆盖？(y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${RED}❌ 已取消${NC}"
        exit 1
    fi
    rm -rf "$ROOT_DIR"
fi

echo -e "${GREEN}📦 正在创建 Forge 1.20.1 项目骨架: ${MOD_NAME}${NC}"

# ----- 创建根目录 -----
mkdir -p "$ROOT_DIR"

# ===== 1. build.gradle (修复了引号错误) =====
cat > "$ROOT_DIR/build.gradle" << 'EOF'
plugins {
    id 'net.minecraftforge.gradle' version '6.0.18'
    id 'org.jetbrains.kotlin.jvm' version '1.9.22'
    id 'maven-publish'
    id 'signing'
}

version = '1.0.0'
group = 'com.example.${MOD_NAME}'   // 实际会被脚本替换
java.toolchain.languageVersion = JavaLanguageVersion.of(17)

minecraft {
    mappings channel: 'official', version: '1.20.1'
    runs {
        client {
            workingDirectory project.file('run')
            property 'forge.logging.markers', 'SCAN,REGISTRIES,LOADING'
            property 'forge.logging.console.level', 'debug'
            mods { ${MOD_NAME} { source sourceSets.main } }
        }
        server {
            workingDirectory project.file('run')
            property 'forge.logging.markers', 'SCAN,REGISTRIES,LOADING'
            property 'forge.logging.console.level', 'debug'
            mods { ${MOD_NAME} { source sourceSets.main } }
        }
    }
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.20.1-47.3.0'
    implementation 'org.bouncycastle:bcprov-jdk15on:1.73'
    implementation 'org.bouncycastle:bcpg-jdk15on:1.73'
    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}

jar {
    manifest {
        attributes([
            "Specification-Title": "${MOD_NAME}",
            "Specification-Vendor": "example.com",
            "Specification-Version": "1",
            "Implementation-Title": project.name,
            "Implementation-Version": project.version,
            "Implementation-Vendor": "example.com"
        ])
    }
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            artifactId = project.name
        }
    }
}

signing {
    useGpgCmd()
    sign publishing.publications.mavenJava
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
EOF

# 替换 build.gradle 中的占位符 ${MOD_NAME}
sed -i "s/\${MOD_NAME}/${MOD_NAME}/g" "$ROOT_DIR/build.gradle"

# ===== 2. settings.gradle =====
cat > "$ROOT_DIR/settings.gradle" << 'EOF'
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url 'https://maven.minecraftforge.net/' }
        maven { url 'https://repo.spongepowered.org/repository/maven-public/' }
    }
}
rootProject.name = '${MOD_NAME}'
EOF
sed -i "s/\${MOD_NAME}/${MOD_NAME}/g" "$ROOT_DIR/settings.gradle"

# ===== 3. gradle.properties =====
cat > "$ROOT_DIR/gradle.properties" << 'EOF'
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
org.gradle.configuration-cache=true
org.gradle.parallel=true
EOF

# ===== 4. 源码目录结构 =====
PACKAGE_PATH="src/main/java/com/example/${MOD_NAME}"
mkdir -p "$ROOT_DIR/${PACKAGE_PATH}"
mkdir -p "$ROOT_DIR/src/main/resources/META-INF"
mkdir -p "$ROOT_DIR/src/main/resources/assets/${MOD_NAME}/lang"
mkdir -p "$ROOT_DIR/src/test/java/com/example/${MOD_NAME}"
mkdir -p "$ROOT_DIR/.github/workflows"

# ===== 5. 主类 (MyMod.java) =====
cat > "$ROOT_DIR/${PACKAGE_PATH}/MyMod.java" << 'EOF'
package com.example.${MOD_NAME};

import net.minecraftforge.fml.common.Mod;

@Mod("${MOD_NAME}")
public class MyMod {
    public MyMod() {
        // 初始化代码
    }
}
EOF
sed -i "s/\${MOD_NAME}/${MOD_NAME}/g" "$ROOT_DIR/${PACKAGE_PATH}/MyMod.java"

# ===== 6. mods.toml (符合 Forge 规范) =====
cat > "$ROOT_DIR/src/main/resources/META-INF/mods.toml" << 'EOF'
modLoader="javafml"
loaderVersion="[47,)"
license="MIT"

[[mods]]
modId="${MOD_NAME}"
version="1.0.0"
displayName="${MOD_NAME}"
authors="YourName"
description='''
A Forge mod with PGP signing support.
'''

[[dependencies.${MOD_NAME}]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.${MOD_NAME}]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1]"
    ordering="NONE"
    side="BOTH"
EOF
sed -i "s/\${MOD_NAME}/${MOD_NAME}/g" "$ROOT_DIR/src/main/resources/META-INF/mods.toml"

# ===== 7. 语言文件 zh_cn.json =====
cat > "$ROOT_DIR/src/main/resources/assets/${MOD_NAME}/lang/zh_cn.json" << 'EOF'
{
  "item.${MOD_NAME}.pgp_tool": "PGP 工具",
  "command.${MOD_NAME}.pgp.description": "PGP 加密/解密/签名指令",
  "command.${MOD_NAME}.pgp.gen_key.success": "✅ 已生成 PGP 密钥对！"
}
EOF
sed -i "s/\${MOD_NAME}/${MOD_NAME}/g" "$ROOT_DIR/src/main/resources/assets/${MOD_NAME}/lang/zh_cn.json"

# ===== 8. GitHub Actions CI =====
cat > "$ROOT_DIR/.github/workflows/ci-build.yml" << 'EOF'
name: Build & Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 17
          distribution: 'temurin'
      - name: Import GPG key
        run: |
          export GNUPGHOME=$(mktemp -d)
          echo "$GPG_PRIVATE_KEY_BASE64" | base64 --decode | gpg --batch --import
        env:
          GPG_PRIVATE_KEY_BASE64: ${{ secrets.GPG_PRIVATE_KEY_BASE64 }}
      - name: Build with Gradle
        run: ./gradlew build --no-daemon
      - name: Sign JAR with GPG
        run: |
          export GNUPGHOME=$(mktemp -d)
          echo "$GPG_PRIVATE_KEY_BASE64" | base64 --decode | gpg --batch --import
          gpg --batch --yes --pinentry-mode loopback --passphrase "$GPG_PASSPHRASE" \
            --detach-sign --armor build/libs/${MOD_NAME}-1.0.0.jar
        env:
          GPG_PRIVATE_KEY_BASE64: ${{ secrets.GPG_PRIVATE_KEY_BASE64 }}
          GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
      - uses: actions/upload-artifact@v4
        with:
          name: ${MOD_NAME}-1.0.0-signed
          path: |
            build/libs/${MOD_NAME}-1.0.0.jar
            build/libs/${MOD_NAME}-1.0.0.jar.asc
EOF
sed -i "s/\${MOD_NAME}/${MOD_NAME}/g" "$ROOT_DIR/.github/workflows/ci-build.yml"

# ===== 9. Dockerfile =====
cat > "$ROOT_DIR/Dockerfile" << 'EOF'
FROM mcr.microsoft.com/java/jdk:17-jre-ubuntu
WORKDIR /app
RUN apt-get update && apt-get install -y curl unzip && rm -rf /var/lib/apt/lists/*
ENV FORGE_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/forge-1.20.1-47.3.0-installer.jar"
RUN curl -fsSL ${FORGE_URL} -o forge-installer.jar && \
    java -jar forge-installer.jar --installServer && \
    rm forge-installer.jar
COPY build/libs/*.jar mods/
COPY entrypoint.sh /app/
RUN chmod +x /app/entrypoint.sh
EXPOSE 25565
CMD ["/app/entrypoint.sh"]
EOF

# ===== 10. entrypoint.sh =====
cat > "$ROOT_DIR/entrypoint.sh" << 'EOF'
#!/bin/bash
echo "eula=true" > eula.txt
echo "level-type=minecraft:overworld" >> server.properties
java -Xms2G -Xmx4G -jar forge-*.jar nogui
EOF
chmod +x "$ROOT_DIR/entrypoint.sh"

# ===== 11. 占位测试类 =====
cat > "$ROOT_DIR/src/test/java/com/example/${MOD_NAME}/PlaceholderTest.java" << 'EOF'
package com.example.${MOD_NAME};

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlaceholderTest {
    @Test
    void alwaysPasses() {
        assertTrue(true);
    }
}
EOF
sed -i "s/\${MOD_NAME}/${MOD_NAME}/g" "$ROOT_DIR/src/test/java/com/example/${MOD_NAME}/PlaceholderTest.java"

# ===== 验证关键文件是否存在 =====
echo -e "\n${GREEN}✅ 生成完成！${NC}"
echo "项目位置: $ROOT_DIR"
echo -e "\n${YELLOW}📋 生成的文件列表:${NC}"
find "$ROOT_DIR" -type f -not -path "*/\.*" | head -20

echo -e "\n${GREEN}💡 下一步:${NC}"
echo "   cd ${MOD_NAME}"
echo "   ./gradlew build          # 编译模组"
echo "   docker build -t ${MOD_NAME}-test .   # 构建容器镜像"