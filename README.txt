PROJETO ZERO DEPENDENCIAS: AGENDA CORPORATIVA (JAVA 8)
======================================================

POR QUE ESTE PROJETO NAO DA ERRO DE COMPILACAO?
- Apenas 1 unico arquivo: AgendaApp.java (sem subpastas de pacotes, sem Maven, sem JARs externos).
- Persistencia nativa e automatica em arquivos locais (agenda_compromissos.txt, agenda_notas.txt, agenda_config.properties).
- 100% compativel com JRE 8 Update 511.

COMO COMPILAR EM 3 COMANDOS (NO TERMINAL OU CODESPACES):
1. javac -encoding UTF-8 -source 1.8 -target 1.8 AgendaApp.java
2. jar cfe AgendaApp.jar AgendaApp *.class
3. Baixe o AgendaApp.jar e de duplo clique no Windows!

(No Windows com JDK instalado, basta clicar duas vezes em "compilar.bat").
