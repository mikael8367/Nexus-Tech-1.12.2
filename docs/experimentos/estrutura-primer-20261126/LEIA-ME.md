# Experimento ChunkPrimer — não registrado, não jogável

Base `isolated/estrutura-sem-vidro-forge-bloqueado-20261126`, commit `58ece6b356c44f35440e30d3023636637e6a2897`. Apenas arquivos em `docs/experimentos/`; nenhum listener, gerador, mod, build, versão ou workflow é alterado. Não foi criado JAR. **Correção em andamento: não há teste real de servidor, nem prova de segurança; não usar em mundo persistente.**

## API e comportamento

`NoGlassPrimerExperiment.apply(event, seed, schematic)` recebe manualmente `ReplaceBiomeBlocks` e só acessa `event.getX/getZ/getPrimer` e `ChunkPrimer.getBlockState/setBlockState`. Não chama `World.getBlockState`, `getChunk`, escrita global nem registra qualquer evento. É um ensaio hipotético para thread do servidor e mundo descartável; ele não verifica o lado, a dimensão, a identidade ou a novidade do chunk, nem evita que um chamador o use indevidamente. Não alterar `Event.Result`. A seleção usa o planejador anterior (aprox. 1/16 de chunks antes dos filtros, não é taxa efetiva).

O evento auditado ocorre antes da superfície, cavernas e estruturas: por isso este experimento NÃO usa a exigência anterior de superfície de grama (ainda não existe nesse instante). Examina 14×12 colunas locais em toda altura y=0..255; aceita exclusivamente estados default de ar, pedra e bedrock, sem bloco declarando tile entity; qualquer líquido, árvore, bloco de outro mod, variante de pedra, construção ou desconhecido rejeita. As 80 colunas do retângulo 10×8 devem ter topo de pedra em altura uniforme 4..250, com quatro pedras contínuas até topo-3. Limites e margem são verificados antes das leituras. A condição é deliberadamente muito restritiva. O primer não tem mapa de tile entities: rejeitar tipos de bloco com TE não detecta TEs persistidas/pendentes ou outras estruturas.

A schematic é o recurso anterior com hash SHA-256 `5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22`, dimensão 10×8×8, 640 estados, sem vidro, 325 ar e 12 cascalhos. Todos os estados, inclusive ar, são preparados antes de escrever. A base é topo-2. Cada cascalho exige no esquema um bloco de suporte inteiro de pedra, grama, terra ou minério de ferro logo abaixo (ou pedra natural no solo para y=0); os 12 têm suporte interno na schematic exata. Nenhuma física é executada nem simulada. Não há giro ou alteração de offsets originais.

Retornos: `INVALID` é entrada/limite inválido; `NOT_SELECTED` não lê/escreve primer; `BAD_PALETTE` é estado, metadado ou suporte inválido; `UNSAFE_TERRAIN` é terreno não aceito; `READ_FAILED` representa exceção `RuntimeException` capturada durante leitura **ou preparação da paleta**; todos esses têm `completedWrites=0` e ocorrem antes do laço de escrita. Exceções em operações anteriores ao bloco de leitura/preparação não são convertidas em `READ_FAILED`. `WRITTEN_TO_PRIMER` significa apenas 640 chamadas retornadas, NÃO preservação posterior.

**Falhas de escrita não retornam relatório**: a exceção original propaga para fora de `apply`, inclusive se a própria chamada que lançou já tiver alterado uma célula. Escritas anteriores também podem ter ocorrido. Não há rollback, transação, atomicidade ou recuperação. Isso só interrompe a criação do chunk se o chamador e a cadeia acima deixarem a exceção escapar e não continuarem com o primer afetado; este experimento sozinho não controla esse comportamento nem prova como o Forge reagirá. `Error` também não é capturado. A classe continua sem registro nem integração. A validação só reflete o momento da leitura; transformações, reentrância ou modificações concorrentes não são controladas.

## Limites essenciais

* Antes da superfície a geração subsequente pode substituir os blocos, inclusive os 325 ares; cavernas, estruturas, biomas, outros mods, gravidade, iluminação e callbacks futuros podem destruir ou alterar a estrutura e o terreno. A comparação de estados imediatamente após a escrita não comprova forma final, física ou ausência de cascatas. Rejeitar água/árvore existentes não impede sua geração posterior ou em chunks vizinhos.
* Superfície plana e naturais não provam ausência de construção, proteção, chunk antigo/corrompido, TEs persistidas, estruturas externas ou alterações de outros mods. Não há reserva, proteção de região ou marcador durável. O evento não oferece prova geral de que um chunk é novo. O resultado depende do gerador que o emite, da ordem de eventos e de coremods: testes simulados não comprovam isso.
* A região lida é local, mas nada garante que a geração posterior ou callbacks não carreguem vizinhos. Nenhum teste de reinício, iluminação, efetividade, servidor Forge ou interoperabilidade foi feito. Não ligar em servidor de produção nem prometer segurança de mundo existente.

## Ensaios sem JAR

Nesta revisão: Java Temurin 8u512, apenas classes Forge **simuladas**, sem servidor e sem JAR: 65.637 asserções, incluindo todas as 65.536 células após sucesso, 50 candidatos, limites, rejeições e falhas de leitura no começo e depois de 10.000 leituras; em rejeições testadas, nenhuma chamada de escrita e conteúdo integral inalterado. Testados também erro antes da primeira escrita, erro após 37 chamadas retornadas e erro depois de a própria chamada modificar uma célula, confirmando que a exceção original escapa, não há chamadas posteriores e não existe rollback. A suíte rejeitou um mutante que converte a exceção de escrita em retorno normal. Esses testes não provam segurança do mundo nem o comportamento do Forge real.

Na revisão anterior, antes desta alteração, houve compilação contra Forge `1.12.2-14.23.5.2860` mapeado `snapshot_20171003` e Guava 18 em cache, e teste independente do leitor; esses resultados anteriores não validam esta revisão. Não foi compilado o mod nem gerado JAR nesta revisão.

Reprodução na raiz da branch, com caches e variáveis `JAVA_HOME`, `FORGE_MAPPED_JAR`, `GUAVA_JAR` disponíveis:

```sh
df -h /
P=docs/experimentos/estrutura-sem-vidro-20261126
E=docs/experimentos/estrutura-primer-20261126
mkdir -p /tmp/nexus-primer-classes
"$JAVA_HOME/bin/javac" -source 8 -target 8 -cp "$FORGE_MAPPED_JAR:$GUAVA_JAR" -d /tmp/nexus-primer-classes "$P/NoGlassSchematic.java" "$P/NoGlassPlan.java" "$E/NoGlassPrimerExperiment.java"
df -h /
python3 "$E/test_primer.py" "$JAVA_HOME/bin" "$E" "$P" "$P/sem_vidro.schematic.b64"
```
