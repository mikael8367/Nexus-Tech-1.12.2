# Ambiente Minecraft 1.12.2 — ProjectE Storage Bridge

Este projeto é um **mod para Minecraft Java Edition**, não um launcher e não inclui uma cópia do Minecraft, Forge ou Java.

## Perfil de jogo

| Componente | Versão |
|---|---|
| Minecraft Java Edition | 1.12.2 |
| Forge para executar | 14.23.5.2864 |
| Java Runtime | Java 8, 64 bits |
| Arquitetura recomendada | x86_64 em PC |
| ProjectE | 1.12.2-PE1.4.1 |
| Simple Storage Network | 1.12.2-1.8.3 |

## Runtime Java

O Java Runtime é fornecido pelo launcher, não embutido no JAR do mod. Se o launcher oferecer seleção de runtime, selecione Java 8 (1.8), preferencialmente 64 bits. Não selecione Java 17, 21 ou 25 para este perfil antigo do Forge.

Em launchers Android, use somente um runtime Java 8 compatível com o próprio launcher e com a arquitetura do aparelho. O runtime interno do FCL/Pojav pode ser diferente do runtime de PC; compatibilidade e desempenho precisam ser testados no dispositivo.

## Instalação manual

1. Crie uma instância Minecraft Java 1.12.2.
2. Instale Forge 14.23.5.2864.
3. Configure o launcher para usar Java 8.
4. Inicie uma vez e feche o jogo.
5. Coloque os arquivos JAR de ProjectE e Simple Storage Network na pasta `mods`.
6. Coloque o JAR compilado deste projeto na mesma pasta `mods`.
7. Inicie e confira o log. Não use o JAR enquanto o build do GitHub Actions estiver falhando.

## Manifest de instância

O arquivo `instance/manifest.json` registra Minecraft 1.12.2, Forge e os dois mods dependentes. Ele pode ser usado como base de importação em ferramentas compatíveis com manifest do CurseForge. O mod em desenvolvimento ainda não deve ser considerado pacote pronto para jogar.

## Por que não existe uma pasta runtime no repositório?

Distribuir o Java Runtime junto com o mod não é necessário nem recomendado: o runtime depende do sistema operacional, arquitetura e launcher. No Android, cada launcher gerencia seus próprios runtimes. O repositório guarda apenas a configuração e documentação do ambiente esperado.

## Compilação

O workflow usa Java 8 e Gradle 4.10.3. O ForgeGradle 2.3 é configurado com o Forge 14.23.5.2847 para obter o artefato legado de desenvolvimento; o mod exige Forge 14.23.5.2864 ou superior em execução. Essa combinação deve passar pelo build e por teste real no launcher antes de ser declarada compatível.
