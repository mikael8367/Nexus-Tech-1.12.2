# ProjectE Storage Bridge

Mod para Minecraft Java 1.12.2 / Forge 14.23.5.2864.

## Objetivo

Adicionar um cabo compatível com a rede do Simple Storage Network que exponha os itens aprendidos na Tabela de Transmutação do ProjectE como armazenamento virtual. Ao retirar itens, o mod deverá debitar EMC no servidor, validar conhecimento e saldo antes de entregar qualquer item.

## Estado atual

**Bootstrap inicial — ainda não é uma integração funcional.** Este commit contém a estrutura ForgeGradle e a classe de entrada do mod. O cabo, a listagem virtual, a cobrança de EMC, sincronização e persistência ainda não foram implementados.

A implementação só avançará após conferir as APIs reais das versões 1.12.2 dos dois mods. Não serão usadas APIs de versões modernas como se fossem compatíveis com 1.12.2.

## Requisitos-alvo

- Minecraft 1.12.2
- Forge 14.23.5.2864 (ou build 1.12.2 compatível a confirmar)
- Java 8
- ProjectE para 1.12.2
- Simple Storage Network para 1.12.2

## Compilar

O GitHub Actions usa Java 8 e Gradle 4.10.3 para executar a tarefa de build. O JAR compilado será publicado como artefato da execução do workflow.

## Regras de segurança da integração

- Operações de EMC exclusivamente no servidor.
- Nunca entregar itens antes de confirmar e debitar o EMC de forma atômica.
- Nunca permitir saldo negativo.
- Respeitar o conhecimento do jogador e o NBT/metadados do item.
- Evitar duplicação em falhas, desconexões e operações simultâneas.
- Não inventar compatibilidade: validar os hooks do cabo e do grafo da SSN nas fontes 1.12.2.
