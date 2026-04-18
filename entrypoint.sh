#!/bin/bash
echo "eula=true" > eula.txt
echo "level-type=minecraft:overworld" >> server.properties
java -Xms2G -Xmx4G -jar forge-*.jar nogui
