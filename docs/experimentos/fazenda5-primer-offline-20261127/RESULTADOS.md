# Fazenda 5x2x5: ensaio offline com ChunkPrimer real

Base: 026964241457ff0be5340c5ba163718a2904d0c0. Os arquivos do adaptador e do teste-base não foram alterados. Java 8 Temurin 1.8.0_512, biblioteca Forge 1.12.2-14.23.5.2860 mapeada snapshot_20171003-1.12, todas as dependências já em cache. Nenhum JAR foi baixado ou criado; nenhum World foi criado, lido ou salvo; geração não foi ativada.

## O que foi executado

`PartialBootstrap` inicializa sons e blocos, sinaliza artificialmente que `Bootstrap` já foi registrado e evita o restante do ciclo normal do Forge. Isso é um ambiente parcialmente inicializado, não uma sessão Forge completa. O teste opera realmente sobre `net.minecraft.world.chunk.ChunkPrimer` da biblioteca mapeada, porém não prova worldgen, mod carregado, comportamento de vizinhos, persistência ou equivalência a um servidor.

Saída base, código de processo 0: `PASS 65625 checks; private ChunkPrimer; 50 cells {0:0=13, 2:0=10, 3:0=6, 3:1=4, 59:0=2, 60:0=4, 85:0=10, 9:0=1}; failures before/after; no World or JAR`.

Saída ampliada, código de processo 0: `DEEP PASS: 306 scenarios; 459173 assertions; seed=0x5A17C0DE; 7 full snapshots x 65536; all 50 collisions, 25 support, 100 injection failures, reapply rejected; no World or JAR`. As 459173 asserções não são 459173 casos independentes: 458752 são comparações de célula dos 7 snapshots completos. Semente Java Random fixa 0x5A17C0DE com 200 tentativas de inserção de estados fora da área reservada por cenário; pode haver repetições de posição.

O teste abrange também coordenadas negativas, limites do chunk e alturas, extremos inteiros válidos, overflow e rejeições, todos os metadados 0–15 para os sete IDs aceitos, IDs não suportados, rejeição de 50 colisões individuais e 25 suportes removidos. Verifica o array de entrada em 100 falhas injetadas (antes/depois de cada uma das 50 escritas) e isolamento do resultado contra mudanças posteriores no array original. Nisso, isolamento e descarte da cópia privada não são rollback em um mundo. Replicar o resultado como entrada leva à rejeição de destino protegido: não há sucesso idempotente. A nova classe não implementa reservas/margens externas nem coordenação de acesso concorrente. A cópia do array não garante snapshot atômico se um produtor concorrente o alterar enquanto é copiado.

O recurso Base64 testado tem SHA-256 `e4249b9aa18cac96309254d2156bd1b6c09bc16350caeac824b642f88bb7eea5`. O ficheiro da estrutura não contém mapeamento dos IDs de origem; os IDs vanilla são hipótese explícita, não prova da proveniência dos blocos. Água/trigo/farmland, ar e cercas foram confrontados como estados gravados, não submetidos a ticks ou testes de fluido/crescimento/conectividade.

O Bootstrap completo direto excedeu 15 s e foi encerrado, com thread principal durante a inicialização `EntityList` -> `ForgeRegistries` -> `Entity` e carregamento de classe; não foi estabelecido se era deadlock. Uma primeira execução ampliada excedeu 90 s sem diagnóstico; repetida com logs de progresso, concluiu em aproximadamente 11 s. Espaço livre final ~766 MB.

## Como reproduzir em ambiente técnico equivalente

O classpath precisa conter a biblioteca Forge mapeada, dependências locais e o diretório de recursos com `assets/nexustech/structures/nova_anexo_5x2x5_3c17a590.schematic.b64`. Não é necessário criar JAR. Compile em diretório temporário, com Java 8, os fontes `NovaPlacement.java`, `NovaSchematic.java`, `Fazenda5ForgePrimer.java`, `Fazenda5ForgePrimerTest.java`, `Fazenda5DeepPrimerTest.java` e `PartialBootstrap.java` (o classpath deve apontar para as dependências locais); em seguida execute `timeout 90 java -Xmx192m -cp "$CLASSPATH:$CLASSES:$RESOURCES" com.mikael8367.nexustech.worldgen.PartialBootstrap` e depois acrescente o argumento `deep`. As cópias usadas de NovaPlacement e NovaSchematic possuem git blob IDs respectivamente `8ea03e42b90ce29f8fdd693a1d8fd106a6f47e4a` e `112903e8d5b774c4d94e8506589e36de1cfd26e4`. O ensaio não substitui o bootstrap real.
