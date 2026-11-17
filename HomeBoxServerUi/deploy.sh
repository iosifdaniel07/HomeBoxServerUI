#!/usr/bin/env bash
set -euo pipefail

SERVER="homebox@homestreambox.go.ro"
SSH_PORT="2222"
JAVA_HOME_PATH="/usr/lib/jvm/java-17-openjdk-amd64"

JAR="server/build/libs/server-all.jar"
WEB_UI="composeApp/build/dist/wasmJs/productionExecutable"

echo "==> Building server JAR..."
JAVA_HOME=$JAVA_HOME_PATH ./gradlew :server:shadowJar

echo "==> Building web UI..."
JAVA_HOME=$JAVA_HOME_PATH ./gradlew :composeApp:wasmJsBrowserDistribution

echo "==> Copying JAR to server..."
scp -P $SSH_PORT "$JAR" "$SERVER:/tmp/"

echo "==> Copying web UI to server..."
ssh -p $SSH_PORT "$SERVER" "mkdir -p /tmp/web-ui"
scp -P $SSH_PORT -r "$WEB_UI/." "$SERVER:/tmp/web-ui/"

echo ""
echo "Done. Now run on the server:"
echo "  sudo systemctl stop homebox"
echo "  sudo cp /tmp/server-all.jar /opt/homebox/server.jar"
echo "  sudo chown root:media /opt/homebox/server.jar"
echo "  sudo cp -r /tmp/web-ui/. /opt/homebox/web/"
echo "  sudo systemctl start homebox"
