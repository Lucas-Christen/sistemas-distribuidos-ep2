#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

rm -rf -- out dist
mkdir -p out dist

javac --release 17 -encoding UTF-8 -d out --source-path src \
    src/lucaschristen/sistemasdistribuidos/servidor/ServidorApp.java \
    src/lucaschristen/sistemasdistribuidos/cliente/ClienteApp.java

jar --create --file dist/servidor.jar --main-class lucaschristen.sistemasdistribuidos.servidor.ServidorApp -C out .
jar --create --file dist/cliente.jar --main-class lucaschristen.sistemasdistribuidos.cliente.ClienteApp -C out .

echo "Pronto:"
echo "  java -jar dist/servidor.jar             (janela)"
echo "  java -jar dist/servidor.jar --console   (terminal)"
echo "  java -jar dist/cliente.jar"
