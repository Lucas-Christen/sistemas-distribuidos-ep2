#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

rm -rf -- out-teste
mkdir -p out-teste
SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac
javac --release 17 -encoding UTF-8 -d out-teste --source-path "src${SEP}teste" \
    teste/lucaschristen/sistemasdistribuidos/TodosOsTestes.java
java -Djava.awt.headless=true -cp out-teste lucaschristen.sistemasdistribuidos.TodosOsTestes
