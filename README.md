# Nexus Tech

Minecraft Java 1.12.2 Forge mod focused on technology, energy and automation.

This repository contains the initial Forge MDK-based project structure.

## Requisitos

- Minecraft Java **1.12.2**, com Forge **14.23.5.2859** para executar o mod.
- **JDK 8** (não apenas JRE); configure `JAVA_HOME` para a pasta do JDK e inclua `JAVA_HOME/bin` no `PATH`. Verifique com `java -version` e `javac -version`.
- Internet na primeira execução para baixar Gradle, Forge e dependências; alguns gigabytes de espaço livre.

## Compilar e executar

Na raiz do projeto, no Linux/macOS ou outro ambiente POSIX:

```sh
sh gradlew build
```

No Windows (Prompt de Comando):

```bat
gradlew.bat build
```

O JAR reobfuscado do mod será gerado em `build/libs/` (por exemplo, `nexustech-1.0.jar`). Para jogar, coloque-o na pasta `mods` de uma instalação Forge compatível. Para iniciar o cliente de desenvolvimento, use `sh gradlew runClient` (Windows: `gradlew.bat runClient`); pode ser necessário configurar uma conta/ambiente de desenvolvimento. Para servidor de desenvolvimento, use `sh gradlew runServer` ou `gradlew.bat runServer`.

O wrapper oficial do MDK é armazenado como texto em `gradle/wrapper/gradle-wrapper.jar.base64.part*`. Os scripts o decodificam automaticamente na primeira execução; o JAR gerado é ignorado pelo Git. No Unix é necessário `base64` (GNU ou BSD) ou `openssl`; no Windows, PowerShell. Não é necessário marcar `gradlew` como executável quando iniciado com `sh`. Se o JAR local estiver danificado, exclua `gradle/wrapper/gradle-wrapper.jar` e execute o comando novamente.

A versão do mod em `build.gradle` e `NexusTech.VERSION` deve permanecer sincronizada; `mcmod.info` é preenchido durante `processResources`.

Os avisos e créditos do MDK estão em `MDK-LICENSE.part*.txt` (em ordem) e `MDK-CREDITS.txt`.
