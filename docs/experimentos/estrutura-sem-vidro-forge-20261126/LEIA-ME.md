# Adaptador Forge experimental: somente diagnóstico, bloqueado

Base: `isolated/estrutura-sem-vidro-transacional-20261126`, commit `5a424d3b686ca2dc4538e9dfeeb0638ca555d29d`. Estes arquivos vivem apenas em `docs/experimentos/`; não registram `IWorldGenerator`, não alteram o mod, reator, build, versão ou configuração. Nenhum JAR foi gerado. **Não é uma correção jogável e não prova ausência de cascata.**

## O que existe

`NoGlassForgeProbe` usa tipos concretos do Forge/Minecraft 1.12.2 e implementa o `Backend` do núcleo. Recebe do chamador um objeto `Chunk` existente; não pede chunk a `World`/provider, não verifica chunks vizinhos e não contém API de escrita. O diagnóstico de um candidato selecionado lê somente os limites locais x/z calculados (14×12, dentro de 0..15) por toda a altura, aceita apenas tipos de bloco listados e metadados permitidos, observa listas atualmente materializadas de entidades/TEs, cria um snapshot local e chama o planejador de 640 células. Exceção de leitura produz `READ_FAILED`; blocos desconhecidos, entidades/TEs ou terreno inadequado são rejeitados. `DIAGNOSTIC_PLAN_ONLY` significa apenas que o planejador calculou 640 células: não é autorização para gerar; sequer executa a checagem de cascalho do núcleo. Sem candidato não lê blocos.

A implementação do backend **sempre** retorna `null` ao adquirir reserva; a chamada direta a `compareAndCommit` lança `UnsupportedOperationException`. Mesmo que alguém conecte inadvertidamente este backend ao núcleo, nenhuma colocação acontece. Não há opção para retirar o bloqueio. A inspeção só é para testes em mundo descartável, pelo thread do servidor; nunca é chamada automaticamente. O código rejeita subclasses de `Chunk` e classes de bloco fora do namespace vanilla, mas nomes e classes não impedem bytecode transformado; um mod/coremod ainda pode alterar o comportamento de qualquer método, inclusive leitura. Também existem variantes de `getBlockState` para mundo de depuração; inspect não garante compatibilidade com todos os mundos/mods. Não foram auditados todos os callbacks indiretos do ecossistema Forge.

## Por que não gera

* `isLoaded` não prova que o chunk é novo, não modificado, não reclamado ou que o objeto ainda é a instância atual no provider. Blocos naturais, ausência de marcador ou inhabited time também não provam isso. A inspeção não verifica proteção, borda dinâmica, marcador, entidades/TEs pendentes ou persistidas. Nada disso pode ser interpretado como ausente. Não acessamos outro chunk para tentar sanar a dúvida.
* `Chunk.setBlockState`/`World.setBlockState`, flag de notificação, snapshot ou comparação em sequência NÃO implementam uma transação durável e indivisível das 640 células e marcador. Outros mods e callbacks podem mudar o estado durante a operação ou observar estado parcial. Um suposto CAS precisa validar **todos** os dados, identidade, exclusão e proteções, sem corrida até confirmação; o backend não possui essa garantia.
* Queda de energia ou reinício entre alterações e persistência pode deixar estados parciais ou marcador fora de sincronia. Um journal isolado sem ordenação de flush, barreiras, recuperação e política de conflitos não é suficiente. Rollback pode apagar edições legítimas. Nenhuma recuperação é implementada ou testada.
* Mesmo sem notificação explícita, callbacks, iluminação, gravidade, fluidos, blocos de outros mods e carregamento/serialização podem causar efeitos fora do chunk. Uma margem não prova isolamento. A inspeção não testa gravação, física, iluminação ou callbacks.
* O seletor de 1/16 apenas seleciona candidatos: rejeições de terreno, limites e condições reais podem tornar a frequência efetiva muito menor; não foi medida.

Para continuar de forma responsável seria necessário primeiro demonstrar uma arquitetura de ciclo de vida exclusivamente para chunk inequivocamente novo e sem proteção, transação durável com recuperação e isolamento, auditoria de callbacks/física/iluminação e testes instrumentados de Forge com falhas, reentrância e reinícios. Se não for possível demonstrar tudo isso sem consultar vizinhos, o bloqueio deve permanecer.

## Testes reproduzíveis, sem JAR

Na raiz do repositório, com Java 8, Python 3 e Forge 1.12.2-14.23.5.2860 com mapeamento `snapshot_20171003` e Guava 18 já em cache. Verifique espaço antes de compilar ou gravar qualquer arquivo:

```sh
df -h /
P=docs/experimentos/estrutura-sem-vidro-20261126
T=docs/experimentos/estrutura-sem-vidro-gerador-20261126
A=docs/experimentos/estrutura-sem-vidro-forge-20261126
# FAKE classes Forge em diretório temporário; não é teste do servidor:
python3 "$A/test_probe.py" "$JAVA_HOME/bin" "$A" "$P" "$T" "$P/sem_vidro.schematic.b64"
# Compilação contra as APIs reais previamente disponíveis (não empacota JAR):
mkdir -p /tmp/nexus-forge-probe-classes
"$JAVA_HOME/bin/javac" -source 8 -target 8 -cp "$FORGE_MAPPED_JAR:$GUAVA_JAR" -d /tmp/nexus-forge-probe-classes "$P/NoGlassSchematic.java" "$P/NoGlassPlan.java" "$T/NoGlassTransactionalGenerator.java" "$A/NoGlassForgeProbe.java"
```

Evidências locais: compilação Java 8 concluída com Forge real em cache; testes do adaptador com classes Forge **simuladas** cobrem candidatura, limites, 75 chunks selecionados, 43.008 leituras locais por candidato completo, bloqueio explícito da reserva/commit, chunks não carregados, descarregamento, entidade/TE, bloco/metadata desconhecidos, exceção e terreno irregular. Testes anteriores rodados novamente: 640 células, metadados e 16.352 candidatos em 262.144; 1.338 verificações do backend transacional **simulado**. Inspeção local do bytecode do adaptador mostrou acesso direto a `Chunk.getBlockState(int,int,int)` e nenhuma chamada de escrita; bytecode Forge em cache dessa leitura usa o storage do próprio chunk no caminho normal e consulta o tipo de mundo no caminho especial. Nada disso é teste de integração no servidor, validação de callback, recuperação em disco ou prova de segurança; não execute em seu mundo.
