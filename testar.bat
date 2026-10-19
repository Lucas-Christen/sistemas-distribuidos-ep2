@echo off
cd /d "%~dp0"

if exist out-teste rmdir /s /q out-teste
mkdir out-teste
javac --release 17 -encoding UTF-8 -d out-teste --source-path "src;teste" ^
    teste\lucaschristen\sistemasdistribuidos\TodosOsTestes.java
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp out-teste lucaschristen.sistemasdistribuidos.TodosOsTestes
