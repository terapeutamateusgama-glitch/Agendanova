@echo off
title Gerando AgendaApp.jar (Java 8 Puro)
echo ===================================================
echo  Compilando AgendaApp.java sem dependencias...
echo ===================================================
if exist build rmdir /s /q build
mkdir build

javac -encoding UTF-8 -source 1.8 -target 1.8 -d build AgendaApp.java
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Falha ao compilar. Verifique se o JDK 8 esta instalado.
    pause
    exit /b %ERRORLEVEL%
)

echo Main-Class: AgendaApp> build\MANIFEST.MF
jar cvfm AgendaApp.jar build\MANIFEST.MF -C build .

echo.
echo [SUCESSO] Arquivo AgendaApp.jar criado na pasta atual!
pause
