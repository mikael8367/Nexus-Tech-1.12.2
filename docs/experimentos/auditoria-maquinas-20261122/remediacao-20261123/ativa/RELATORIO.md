# Candidato isolado — não integrar/distribuir

## Resultado e escopo
Há uma mitigação candidata para a duplicação reproduzida na conclusão, não uma correção demonstrada segura em todos os cenários. Não foi integrada, nem houve alteração de originais/GitHub. Em callback após término no FakeWorld, uma entrada consumida não é mais drenada junto com seis tábuas. Outra duplicação condicional ainda foi reproduzida na inserção com exceção; falhas de spawn e adulteração NBT também têm problemas. Revisão independente é obrigatória antes de qualquer integração.

- `active-machine-candidate.patch` é patch unificado somente da classe ativa; fonte candidata em `src/com/mikael8367/nexustech/techmachines/TechMachineTile.java`.
- O bloco em `src/.../TechMachineBlock.java` é cópia idêntica do bloco ativo para os testes. `src/.../NexusTech.java` é **stub somente de teste**, não deve ser integrado.
- Conclusão: prepara cópias, revalida resultado, identidade, energia, progresso e inventário e substitui os slots diretamente (lista `ItemStackHandler.stacks`) sem callbacks entre substituições e débito; chama `markDirty()` depois. `busy` envolve operações externas e processamento; NBT reentrante é ignorado enquanto ocupado. A extração de `drain` continua disponível durante busy para a quebra real do bloco. Pressupõe thread principal e ausência de callbacks nas atribuições de `NonNullList`; não é transação durável.
- Mudança comportamental a revisar: notificações de inserção/extração/energia agora ocorrem com `busy=true`; acessos e `readFromNBT` reentrantes são recusados, inclusive leituras NBT legítimas reentrantes. Handler ainda reseta progresso após alteração da entrada.

## Evidências reproduzíveis
- `sh run-tests.sh`: JDK `1.8.0_512`, Forge 1.12.2-14.23.5.2860 snapshot 20171003 mapeado local; nenhum Gradle, JAR ou download. `/home/daytona/workspace/nexus-machine-combined-0O0A3WNE/run-l4GcBMzG/classes` fornece `TenMachinesTest.FakeWorld` e caps; segundo FakeWorld próprio usa Unsafe. O bloco testado é o Java ativo inalterado com stub NexusTech, não o mod carregado em Minecraft. Forge registra erro `.mcassetsroot`/receitas vanilla no bootstrap dos logs, mas suites executam.
- Resultado `run-ayKkddgK/`: `PASS ActiveReentryTests assertions=79`; `PASS RealBlockFakeWorldTests assertions=24`, incluindo testes que confirmam riscos, não ausência deles. `compile.log`, `test.log`, `block-test.log` e classes estão nesse diretório; fontes dos testes na raiz.
- `sh run-baseline.sh` compilou original isolado e falhou como esperado: `baseline-TlEm9PyQ/test.log`, `callback progress committed got=80 expected=0`; o probe preexistente reporta 1 tronco e 6 tábuas drenados no mesmo callback. No candidato, com 1 tronco, callback drena 0 troncos e 6 tábuas; com 2 troncos, drena 1 tronco restante e 6 tábuas. Quebra de bloco repetida e aninhada com FakeWorld; saída pré-existente cresce para 12 tábuas e é drenada uma vez.
- Outras verificações: simulação de inserção, energia e progresso em 79/80 ticks, overflow de saída, compostor 8 sementes, engarrafador com fonte simulada, callbacks na inserção/extração/energia/tick, acesso reentrante bloqueado, drenos repetidos, exceção após commit, serialização e clamps NBT, tile substituída/descarregada. Não são testes exaustivos: não há instrumentação de todas as mutações ou interleavings possíveis; nem testes de vizinhos de energia adversários completos, nem jogo real, save/reload real, concorrência, queda do processo ou multiplayer.

## Riscos confirmados que impedem aprovação geral
1. Inserção + callback que drena e lança: o `ItemStack` oferecido permanece com 1 tronco nas mãos do chamador que recebeu exceção, e outro tronco foi retirado da máquina. A ocorrência/duplicação depende do chamador e do destino da cópia, mas é estado duplicável reproduzido (`RealBlockFakeWorldTests`). Não há rollback seguro genérico depois que callback externo já expôs o item.
2. `spawnEntity` lança na quebra: entrada já foi removida do handler sem item spawnado; foi observado item perdido. Falha de spawn `false`, unload/crash e quebras de entidade não estão cobertos. Bloco ativo não foi alterado.
3. NBT não autenticado: progresso 79 forjado com energia 1600 completa com apenas 20 FE consumidos, deixando 1580 FE. Replays NBT, saves adulterados e migração de progresso não estão protegidos; guard de reentrada não os valida. Sem garantia de crash/durabilidade.
4. `pullEnergy` envolve capability externa: extração que faz mudanças/lance exceção/remova máquina pode perder energia. Verificação não cria transação entre máquinas.
5. `markDirty` que lança após commit deixa estado já alterado e exceção para o chamador; teste de conclusão confirma que busy é liberado e produção é consistente, mas o resultado externo pode ser ambíguo. Threads simultâneas/itens ou worlds hostis não são suportados ou provados seguros.

## Identidade dos artefatos
- Base original SHA-256 `1d34bfd50e2ad3818c445110153debc84c58d454803606e8b2f2645f38b6688e` (inalterado).
- Classe candidata fonte SHA-256 `1061d743d5a5981c7ebceb3ceab06151d50f3e2aa4ddf235ff0f2beedb13f88d`; patch `6957cf64b87f0da91bfbcc4b6366aaeb7aa2f800cc9d1bdcb59a218810f19c01`.
- Espaço inicial 241 MB; após testes 239 MB. Workspace ~812 KB. Nada foi apagado.
