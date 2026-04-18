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
