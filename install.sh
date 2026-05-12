#!/bin/bash
# Collaborative Code Editor - Setup Script for Linux/Mac

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "  Collaborative Code Editor Setup"
echo "========================================"
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}[ERROR] Java not found!${NC}"
    echo "Please install Java 17 from: https://adoptium.net/"
    exit 1
fi

echo -e "${GREEN}[OK] Java found${NC}"
echo ""

# Make scripts executable
chmod +x mvnw

# Download Maven Wrapper
echo -e "${YELLOW}[SETUP] Downloading Maven Wrapper...${NC}"
mkdir -p .mvn/wrapper
if [ ! -f ".mvn/wrapper/maven-wrapper.jar" ]; then
    curl -fsSL -o ".mvn/wrapper/maven-wrapper.jar" \
        "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar" 2>/dev/null || {
        echo -e "${RED}[ERROR] Failed to download Maven Wrapper${NC}"
        exit 1
    }
fi

# Build project
echo ""
echo -e "${YELLOW}[BUILD] Building project...${NC}"
./mvnw clean package -q

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo -e "  ${GREEN}[SUCCESS] Build Complete!${NC}"
    echo "========================================"
    echo ""
    echo "Run server:  java -jar target/CollaborativeEditor.jar server 5000"
    echo "Run client:  java -jar target/CollaborativeEditor.jar client [IP] 5000 [username]"
    echo ""
    echo "Web UI: http://localhost:5100"
    echo ""
else
    echo ""
    echo -e "${RED}[ERROR] Build failed. Check output above.${NC}"
    exit 1
fi