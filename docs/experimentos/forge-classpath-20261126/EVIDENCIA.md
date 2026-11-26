# Investigação sem criar JAR — 26/11/2026

## Resultado e limites

**Servidor Forge NÃO iniciado nesta investigação; mod NÃO carregado; geração NÃO validada.** O núcleo transacional segue sem backend Forge e sem registro no jogo. Reator permanece desativado; nenhuma alteração foi feita ao código de produção ou à main. Nenhum JAR foi criado, baixado, copiado, publicado ou apagado nesta investigação.

`df -h /`: 96 MB livres em `/`; `free -m`: aproximadamente 55 MiB disponíveis, sem swap (valores no momento do diagnóstico). Não é seguro iniciar a geração real de mundo nessas condições. Não se invocou Gradle nem `--dry-run`: ForgeGradle pode resolver/preparar artefatos durante configuração, inclusive gerar artefatos mapeados, e dry-run não é garantia de zero arquivos JAR. Os caches foram apenas lidos. A experiência também não executou runner do GitHub; não há log de boot a apresentar.

## Verificações executadas

- Java 8 `1.8.0_512`. Fontes atuais do núcleo e testes compiladas diretamente com `javac` em diretório de classes, sem JAR; execução `NoGlassTransactionalGeneratorTest` exibiu `PASS 1338 assertions; fake atomic backend only; candidate=0,0`. Teste exclusivamente de backend simulado; não prova atomicidade, persistência, callback, ausência de cascata ou segurança em Forge.
- `check_forge_2860_bytecode.py` inspecionou com `javap -c -p` um JAR mapeado Forge **já existente** de versão 1.12.2-14.23.5.2860 snapshot_20171003-1.12, SHA-256 `a2121e061c9162992a206d6e3192bf33691263c1083d6942c1bfd89d43e1f7e4`:
  - PASS: `World.markAndNotifyBlock` tem verificação dos bits 1 e 16 antes da via `updateObservingBlocksAt`; a rotina de observadores percorre seis vizinhos.
  - PASS: `World.setBlockState` obtém chunk, chama `Chunk.setBlockState` e pode chamar `checkLight`.
  - PASS: `Chunk.setBlockState` contém chamadas `Block.breakBlock` e `Block.onBlockAdded`.
  - PASS: `Chunk.populate` verifica `populating` e pode registrar geração em cascata aninhada.
- Interpretação: `2 | 16` suprime o caminho específico de observadores quando não há bit 1, mas **não** é uma garantia de ausência de callbacks, luz, carregamento de chunks ou cascatas por outras vias. A observação é estática e não identifica a causa das 15.940 advertências anteriores, nem valida a alteração em mundo real.

## Caminho concreto para próximo teste sem construir JAR

1. Em runner isolado com espaço/RAM suficientes e limites de tempo, usar somente dependências JAR **preexistentes e verificadas por hash**, incluindo Forge userdev mapeado 2860, client-extra, legacydev, launchwrapper, bibliotecas da versão e mapeamentos adequados. JAR mapeado existe no cache local acima; em runner externo é necessário disponibilizar uma cópia preexistente permitida, pois ForgeGradle pode gerá-lo e isso violaria a restrição. Se indisponível, abortar sem executar tarefas de preparação.
2. Compilar fontes pretendidas apenas para diretório de `.class` com `javac`, com recursos necessários em diretório separado; inspecionar explicitamente que **não** inclua classes stub `net.minecraft.*` ou `net.minecraftforge.*`. O diretório `baseline-classes` antigo contém stubs e não pode integrar o classpath de um servidor real.
3. O `config.json` dentro do userdev Forge preexistente indica `net.minecraftforge.legacydev.MainServer`, `mainClass=net.minecraft.launchwrapper.Launch`, `tweakClass=net.minecraftforge.fml.common.launcher.FMLServerTweaker`, `FORGE_VERSION=14.23.5.2860`, `FORGE_GROUP=net.minecraftforge`, `MCP_TO_SRG` e `MCP_MAPPINGS`. Identificar os arquivos/formatos e ordenação exata do classpath em ambiente seguro; não assumir que presença dos arquivos por si só constitui configuração válida.
4. Lançar o servidor com stdin controlada, diretório novo, sem exposição pública e com EULA tratada apropriadamente. Confirmar log de inicialização, identificação e estado ativo de Nexus Tech, versão Forge e reator desligado antes de reivindicar sucesso. Se o mod em diretório não for descoberto, abortar e investigar descoberta de mods; não substituir pela execução de um JAR de mod construído.
5. Usar instrumentação exclusivamente de teste que conte entradas/recursões por gerador e coordenadas; repetir seeds e controles `baseline`, `2|16`, sem gerador de minério e sem harness. Capturar warnings e stack/associação efetiva, distinguir carregamento aninhado de callback e registrar resultados sem supor causalidade. O gerador transacional sem vidro NÃO pode ser testado in-game antes de existir implementação Forge real do backend; nunca habilitá-lo sobre mundo de usuário.
6. Registrar logs, versões e hashes, tamanhos e contagem de avisos; falhar se não houver servidor pronto, mod efetivamente carregado e verificação dos controles. Evitar `assemble`, `build`, `jar`, `reobfJar`, Forge installer e quaisquer tarefas/rotinas que criem JARs.

Este procedimento é uma hipótese operacional não testada e não garante que o ForgeGradle/LegacyDev descubra classes como mod ou que mapeamentos sejam compatíveis.
