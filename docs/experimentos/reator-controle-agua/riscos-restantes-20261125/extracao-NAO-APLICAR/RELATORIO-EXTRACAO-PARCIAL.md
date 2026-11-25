# Investigação experimental — extração de combustível e energia

## Resultado: PARCIAL, NÃO INTEGRAR

Esta variante reduz falhas reproduzidas, mas **não cumpre o objetivo de evitar perda/duplicação em todos os callbacks**. Existe um bloqueador explícito: se a notificação da conversão do combustível remover o controlador reentrantemente, o crédito recém-criado em `fuelTicks` não é incluído em `finishRemoval`, que recolhe itens/overflow/água. O controlador removido perde esse crédito. A origem tinha tratamento específico do item extraído quando `removing`; a substituição proposta deixa essa proteção insuficiente. Isso é uma regressão relevante, não apenas uma incerteza de teste. `extraction-known-risks.log` registra o teste negativo por modelo; Forge real não foi executado. **Não aplicar o patch ou incorporar esta variante.**

A recuperação completa requer um protocolo de posse/escrow de combustível que cubra remoção, snapshots reentrantes e cópia/capabilities, sem restaurar cegamente um item depois que um snapshot observou o crédito. Exige também decidir como representar crédito parcialmente queimado na recuperação e analisar o contrato público do inventário. Não há alegação de atomicidade ou durabilidade perante crash, nem entre snapshots/salvamentos independentes.

## Isolamento e arquivos

- Origem, intocada: `/home/daytona/workspace/nexus-reactor-experimental-scram-20261125-1721`.
- Cópia independente: `/home/daytona/workspace/nexus-reactor-experimental-extraction-20261125-2046`.
- Somente código de produção alterado na cópia: `src/com/mikael8367/nexustech/nuclear/NuclearController.java`. Nenhuma edição nos setters, SCRAM, repositório ativo ou no experimento de origem.
- Diff em relação à origem: `extraction-experimental.patch` — **inseguro, não aplicar**.
- Testes/geradores: `tests/ExtractionSafetyTest.java`, `tests/ExtractionKnownRisksTest.java`, `tests/extraction_generate.py`, `tests/extraction_regression_generate.py`; scaffolds e classes em `tests/extraction-generated-*`, `tests/extraction-regression-generated`, `extraction-*-classes`.
- Nenhum JAR, compilação Forge, GitHub, integração, reset ou exclusão. Antes da cópia e das compilações: `df -h /`: 2,5 GB livres, 87% usado.

## O que foi tentado

- Combustível: conversão deixa de chamar `inventory.extractItem` no tick; decrementa a pilha e cria `fuelTicks` em uma sequência sem notificações, depois chama `markDirty`, registra RuntimeException e mantém a conversão. Snapshot feito pelo callback após o commit observa item e crédito coerentes. Verifica se um callback no teste de `usable` já modificou o crédito antes do commit. Depende do comportamento vanilla mapeado de `ItemStack.shrink/setCount` e `NonNullList.set` sem callbacks; não foi validado com Forge real. **A remoção reentrante descrita acima permanece sem solução.**
- Energia: bloqueia extração reentrante durante validação, bind e notificação; o commit debita energia e orçamento juntos antes de chamar `markDirty`. RuntimeException de notificação após commit é registrada e o valor debitado é retornado; nenhum rollback que possa duplicar. Operações ativas adiam finalização da remoção; RuntimeException de finalização após commit não esconde o resultado. Antes do commit a exceção pode propagar; o débito não ocorreu. Simulação não debita/notifica, e fluxo normal continua permitido. O valor é limitado a zero caso energia esteja negativa.
- São testes de lógica síncrona em memória, não de gravação real. `markDirty` pode falhar sem marcar chunk; mesmo sucesso não é transação com disco. Qualquer callback pode guardar/recarregar snapshots independentes; sem protocolo durável não há exatamente-uma-vez após crash.

## Testes e limitações observadas

`extraction-before.log`: teste com métodos/trechos extraídos da origem, 9 passam e 9 falham, incluindo exceção no callback do item ou energia, snapshot parcial de combustível, reentrada e saldo negativo. Alguns testes de pré-condição novos exercitam caminhos diferentes da origem; não são prova de bug exatamente igual nos dois códigos.

`extraction-after.log`: 18/18 nos métodos/trechos extraídos com stubs. Cobrem sem callback, callback que modifica contador, lança antes/depois, reentrada, snapshot-modelo, simulação, troca de lease, orçamento por tick, saldo alterado no bind e exceção na finalização. O snapshot é `int[]` de stub, não `writeToNBT` real nem serialização de capabilities. As suposições sobre ItemStack/Forge e sincronismo não foram validadas no mod.

`extraction-known-risks.log`: **3 falhas intencionais/diagnósticas** no estado após o patch: (1) remoção reentrante pode perder crédito de combustível, por modelo simplificado de `finishRemoval` baseado no código real; (2) `inventory` público ainda expõe diretamente `extractItem` herdado, que pode debitar e lançar no callback sem retorno; o bypass do tick não corrige o contrato público; (3) `Error` não é capturado após o débito de energia, portanto pode propagar sem retorno. Capturar indiscriminadamente erros fatais da VM não seria uma garantia segura; esta lacuna deve permanecer explícita. Campos públicos e pilhas mutáveis permitem que código externo os altere diretamente; nenhuma guarda aplicada aqui controla esse acesso, nem outras threads arbitrárias.

Regressões com trechos reais e mundo/estrutura/tanque/inventário/NBT de teste: `extraction-regression-v2-*.log`: TransactionRegressionTest 10/10, IndependentReviewTest 10/10, ReviewFixTest 16/16, SafetyTest 24 verificações, AutoThermalBelowCriticalTest 45110 verificações. `AutoThermalTest` continua falhando na asserção histórica `bounds` linha 10 (exit 1); não foi ocultada. O scaffold converte o antigo hook sintético `onExtract` em callback de notificação da conversão, pois a implementação deixou de chamar `extractItem`; não é equivalência de integração. A primeira tentativa de executar o laço de testes parou devido à variável somente-leitura `status` do zsh; logs v2 são da execução completa.

`energyExtracting` protege as vias deste experimento, mas não garante atomicidade de callbacks que modificam diretamente campos públicos, alteram estrutura de modo adversarial ou executam código externo que não respeita o contrato. Simulações e callbacks de exceções são localmente exercitados, não são uma prova contra uma implementação Forge ou mod hostil real.
