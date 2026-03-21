#!/bin/bash
# AI Model Proxy Forward - Startup Script

echo "========================================"
echo "  AI Model Proxy Forward"
echo "========================================"
echo ""

if ! command -v java &> /dev/null; then
    echo "Error: Java not found. Please install JDK 17+"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="${SCRIPT_DIR}/config"
LIBS_DIR="${SCRIPT_DIR}/libs"

if [ $# -eq 0 ]; then
    echo "Usage: $0 server|agent [config]"
    echo "Example: $0 server config/server.yaml"
    exit 1
fi

COMMAND=$1
CONFIG_FILE=$2

if [ "$COMMAND" == "server" ]; then
    echo "Starting server..."
    CONFIG_FILE=${CONFIG_FILE:=${CONFIG_DIR}/server.yaml}
    java -jar "${LIBS_DIR}"/forward-server-*.jar --config "$CONFIG_FILE"
elif [ "$COMMAND" == "agent" ]; then
    echo "Starting agent..."
    CONFIG_FILE=${CONFIG_FILE:=${CONFIG_DIR}/agent.yaml}
    java -jar "${LIBS_DIR}"/forward-agent-*.jar --config "$CONFIG_FILE"
else
    echo "Unknown command: $COMMAND"
    exit 1
fi