# Auditoria independente de máquinas e receitas — NÃO INTEGRAR

Apenas cópia experimental, fora do source set e sem registro no mod ativo. A rede FE e os cabos de itens não foram alterados. Nenhum JAR, publicação ou GitHub. Origem: `nexus-release-stage/experiments/ten_machines`; cópia local em `src/`. A base ativa `TechMachineTile.java` foi examinada e copiada em `audit/` exclusivamente para reprodução.

## Alterações experimentais
- `MachineKind.accepts` rejeita null sem exceção.
- `ProcessingTile` salva identidade da máquina e dos ingredientes do processamento. Ao carregar, progresso é zerado quando faltam identificadores, máquina ou ingredientes diferem, a receita está incompleta ou contém NBT não permitido. O inventário e energia continuam preservados. Identificadores não autenticam NBT adulterado; uma alteração coordenada ainda pode forjá-los. Instâncias antigas sem os novos campos perdem o progresso parcial/FE já gasto (mas não os itens); não migrar silenciosamente para produção.
- Pilhas já sobrelotadas que conseguem ser desserializadas deixam de ser truncadas no carregamento. Enquanto acima do limite, novas inserções são recusadas; extração simulada/real limita cada retorno ao máximo do item, preservando o restante. Não resolve contagem inválida ou impossível de representar no formato vanilla, slots duplicados, slots inválidos ou corrupção geral do NBT.

## Risco crítico na base ativa, não corrigido nesta cópia
Em `techmachines/TechMachineTile.java:143-148`, a produção escreve primeiro a saída. `ItemStackHandler.onContentsChanged` chama `markDirty` entre as mutações. Se uma chamada reentrante remover a máquina nesse intervalo, `TechMachineBlock.breakBlock` chama `drain` sem observar `busy`: tanto insumo como produto podem sair antes de o método consumir o insumo. Um teste isolado de reentrada simula a drenagem da quebra nesse callback: 1 tronco e 6 tábuas recuperados simultaneamente. É uma reprodução condicional, não prova que o callback aconteça normalmente no Minecraft. Não aplicar reparo apressado: é preciso tratar também remoção durante inserção, falha de spawn e durabilidade; FE e integração Forge têm que ser testados. O código ativo NÃO foi modificado.

## Evidências
Java Temurin 1.8.0_512, compilação `-source 8 -target 8 -proc:none` com Forge 1.12.2-14.23.5.2860 mapeado e dependências existentes em cache, sem JAR. `df -h /` antes de copiar/compilar: 243–244 MB livres, 99% utilizado. Execuções em diretório próprio; logs em arquivos próprios.
- `TenMachinesTest.log`: PASS 537 verificações, inclusive 10 tipos, receitas, metadados, simulação, saída cheia/incompatível, reentrada, persistência e remoção simulada.
- `ProcessingSafetyTest.log`: PASS 22 verificações, progresso persistido e rejeição de progresso com receita/máquina/identidade trocada, falta de campo, item com tag, legado, null, energia/progresso extremos, 100 itens mantidos, limite 64, simulação e recarga.
- `ActiveMachineReentryProbe.log`: `REPRODUCED active sawmill: 1 log and 6 planks drained on reentrant callback` com cópia da máquina ativa e mundo falso. Não houve alteração da máquina ativa.
- `compile.log`, `compile-tests.log`, `compile-active.log` contêm as mensagens da compilação. Os logs indicam `Couldn't find .mcassetsroot`, `Errors with built-in recipes!` e avisos de namespace no bootstrap isolado. Os testes exercitam receitas Java das máquinas, não o carregamento real de todas as receitas.

Não foram executados cliente Minecraft, dedicated server, inventário/automação Forge em ambiente de jogo, quebra real, cabeamento, carregamento de recursos, save/reload do mundo ou interrupção/crash. A API mapeada e mundo simulado não equivalem a uma integração Forge real. Permanecem limitações de transações externas, reentrada durante transferência, perda se spawn falhar, persistência, NBT duplicado e integração; ver também `BASE-README.md`. Não distribuir ou integrar como pronto.
