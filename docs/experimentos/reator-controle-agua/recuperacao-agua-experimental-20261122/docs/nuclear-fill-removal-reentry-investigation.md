# Investigação histórica isolada: remoção durante entrada de água

> **Histórico:** a descrição e tabela abaixo retratam o comportamento anterior à recuperação experimental, não os resultados atuais. O teste foi atualizado e seus resultados atuais estão na seção própria abaixo. Não se trata de prova no jogo nem de atomicidade ou segurança após falha.

Resultado histórico no stage experimental, **não no jogo nem no GitHub principal**: é reproduzível que um controlador destruído de modo reentrante, após a mutação do tanque e antes do retorno de `fill`, retorna 1.000 mB aceitos embora o tanque esteja em uma tile removida. Uma fonte-modelo que debita o retorno perde esses 1.000 mB para um controlador já inexistente. Isso não demonstra que o callback artificial ocorra no Minecraft real nem que o retorno de `fill` seja incorreto: a água foi de fato colocada no tanque antes do rompimento. O comportamento normal de quebrar o controlador também deixa de conservar toda a água armazenada: a recuperação existente é de itens, não do fluido. Assim não atribuir a perda total exclusivamente à reentrada.

## Teste e resultados históricos

Na investigação original, `NuclearFillRemovalReentryTest` usou Java 8, as fontes de então, Forge 1.12.2 mapeado e `FluidTank` verdadeiro, com mundo falso derivado dos testes existentes. A fonte de 5.000 mB é um modelo inteiro: após simulação de `fill(..., false)` de 1.000 mB, chama `fill(..., true)` e debita exatamente o retorno. No cenário reentrante, o mundo chama o `breakBlock` real dentro de `markChunkDirty`, quando o tanque já sofreu mutação. Verifica remoção/invalidação e rejeição do handler antigo. A estrutura de 45 blocos é simulada, não há rede de líquidos habilitada.

| Caso | Tanque inicial | Simulação | Água no callback | Retorno | Fonte final | Tanque vivo final | Saldo inicial → final | Perda |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Normal | 0 | 1.000 | — | 1.000 | 4.000 | 1.000 | 5.000 → 5.000 | 0 |
| Normal | 500 | 1.000 | — | 1.000 | 4.000 | 1.500 | 5.500 → 5.500 | 0 |
| Remoção reentrante | 0 | 1.000 | 1.000 | 1.000 | 4.000 | 0 | 5.000 → 4.000 | 1.000 |
| Remoção reentrante | 500 | 1.000 | 1.500 | 1.000 | 4.000 | 0 | 5.500 → 4.000 | 1.500 |

O objeto Java invalidado ainda exibe 1.000/1.500 mB, mas não é mais tile viva no mundo. Um controle adicional enche normalmente 1.500 mB e quebra depois: tanque vivo 1.500 → 0; objeto invalidado ainda registra 1.500. A simulação não aciona callback nem altera o tanque. Todos os cenários passaram (`NuclearFillRemovalReentryTest: PASS`). Forge emitiu avisos de namespace durante o bootstrap isolado; javac emitiu avisos de `Unsafe` e API obsoleta.

## Resultado do teste atualizado no stage

Com a recuperação experimental, `NuclearFillRemovalReentryTest` foi novamente compilado a partir das fontes atuais e passou em mundo artificial. Abaixo, valores em mB; a reserva ainda não foi devolvida ao novo controlador:

| Caso | Inicial no tanque | Fonte final | Tanque vivo | Reserva na posição | Soma final |
|---|---:|---:|---:|---:|---:|
| Normal | 0 | 4000 | 1000 | 0 | 5000 |
| Normal | 500 | 4000 | 1500 | 0 | 5500 |
| Remoção reentrante | 0 | 4000 | 0 | 1000 | 5000 |
| Remoção reentrante | 500 | 4000 | 0 | 1500 | 5500 |

Na quebra comum, os 1500 mB também foram para a reserva e o tanque removido foi esvaziado. Isso é um saldo de execução em memória, não teste de persistência nem garantia após crash. Ver permissões e demais limites em `nuclear-water-recovery-experiment.md`.

## Limites de aplicação

`FluidTank.onContentsChanged` chama `markDirty` do controlador. No mock, `World.markChunkDirty` foi deliberadamente substituído para quebrar o bloco naquele ponto. O bytecode do `World.markChunkDirty` padrão inspecionado apenas marca o chunk sujo se carregado; portanto o experimento **não prova** que esse disparo ocorra num mundo vanilla/Forge real. A inspeção da chamada de `TileEntity.markDirty` também identificou atualização síncrona de comparadores, que pode chamar `onNeighborChange` de blocos vizinhos: outro mod poderia remover o reator nesse caminho, mas não se testou uma combinação real que faça isso. A fonte é um modelo, não uma bomba ou pipe real. Não se testaram eventos reais, clientes/servidor nem persistência. Antes de afirmar ocorrência no jogo, identificar e reproduzir uma cadeia real de callback/remoção e comportamento da fonte; comparar com o rompimento comum. Não corrigir o retorno ingenuamente: devolver zero depois de uma mutação pode duplicar água se o conteúdo tiver sido recuperado/observado em outra parte.

## Reprodução neste ambiente (sem Gradle/JAR)

```sh
cd /home/daytona/workspace/nexus-release-stage
df -h /
J=/home/daytona/workspace/nexus-test/jdk/bin
OUT=/tmp/nexus-nuclear-fill-removal-reentry-20261122
mkdir -p "$OUT"
CP="build/classes/java/main:$(cat /tmp/nuclear-audit-classpath.txt)"
"$J/javac" -source 8 -target 8 -encoding UTF-8 -cp "$CP" -d "$OUT" src/main/java/com/mikael8367/nexustech/nuclear/*.java src/test/java/com/mikael8367/nexustech/nuclear/NuclearReactorTest.java src/test/java/com/mikael8367/nexustech/nuclear/NuclearFillRemovalReentryTest.java
"$J/java" -cp "$OUT:$CP" com.mikael8367.nexustech.nuclear.NuclearFillRemovalReentryTest
```

O classpath e o JDK são recursos locais externos; `build/classes/java/main` fornece dependências do mod previamente compiladas, mas as fontes nucleares e o teste são recompilados na saída temporária e ficam primeiro no classpath. O teste não equivale a build completo. Na investigação histórica não havia alteração de produção; posteriormente o stage recebeu o experimento descrito acima. Na execução atual o disco tinha 255 MB livres; não houve build completo, JAR ou push.
