# Nexus Tech 1.12.2

Mod tecnológico para Minecraft Java Edition 1.12.2 (Minecraft Forge).

## Stack
- Minecraft 1.12.2
- Minecraft Forge 14.23.5.2860 (verificar a versão recomendada no site do Forge)
- Java 8
- Gradle com ForgeGradle 2.3

## Estrutura
- `src/main/java/com/mikael/nexustech/` — classe principal (`NexusTech`) e `Reference`
- `src/main/java/com/mikael/nexustech/proxy/` — proxies client/server
- `src/main/resources/` — `mcmod.info` e assets

## Build
```
gradlew setupDecompWorkspace
gradlew build
gradlew runClient
```

Status: estrutura base. Ainda não compilada nem testada no Forge.
