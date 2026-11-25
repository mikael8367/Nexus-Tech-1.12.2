# Tentativa de remoção reentrante — INSEGURA, NÃO INTEGRAR

## Resultado

A notificação antecipada evita a perda no callback específico testado, mas **não garante a preservação de `fuelTicks`**, introduz risco adicional de persistência e faz duas regressões da suíte falharem. O experimento foi rejeitado. Não aplicar `reentrant-candidate-NAO-APLICAR.patch` nem incorporar a cópia experimental.

A cópia `/home/daytona/workspace/nexus-reactor-reentrant-experimental-20261125-205700` foi criada de `/home/daytona/workspace/nexus-reactor-experimental-extraction-20261125-2046`, após `df -h /` mostrar 2,5 GB livres, 87% em uso. A origem e o histórico foram mantidos. Não houve Forge build, JAR, GitHub, integração ou exclusão. Há classes Java compiladas apenas dos stubs/trechos extraídos.

## Tentativa

Na cópia, `consumeOneFuel` verifica combustível, chama `markDirty` antes de mudar a pilha e o crédito, retorna sem converter se a notificação remover o controlador, e revalida estado e pilha depois do callback. Uma flag evita a reentrada de conversão. Isso não é uma proposta segura: um salvamento síncrono no callback pode observar o estado anterior e limpar o dirty antes da conversão; uma saída antecipada/exceção antes de uma notificação posterior pode deixá-la não persistida. A suíte anterior também esperava que callbacks observassem o estado já convertido. O stub de regressão adaptou seu hook `onExtract` sintético para a notificação pré-conversão, de modo que não equivale a Forge real.

## Diagnósticos reproduzíveis com stubs

`reentrant-diagnostic-before.log` vs `reentrant-diagnostic-after.log`, com o trecho real extraído e modelo simplificado da remoção (que recolhe inventário/overflow mas não `fuelTicks`):

- Remoção na notificação da conversão: antes, 1 item recuperável e 2400 ticks presos no controlador removido; depois, 2 itens e 0 ticks, pois a conversão é cancelada.
- Remoção posterior à conversão sem queima: **antes e depois falham**: 1 item e 2400 ticks presos.
- Snapshot/flush dentro da notificação: antes, modelo salva 1 item e 2400 ticks; depois, salva 2 itens e 0 ticks enquanto o estado vivo posterior contém 1 item e 2400 ticks. É um modelo do helper, não um teste de disco; a execução normal do tick pode chamar `markDirty` novamente, mas isso não garante notificação posterior em todas as saídas e exceções.
- `reentrant-tick-risk.log`: `update` extraído roda com stubs; remoção modelada no `markDirty` seguinte ainda encontra slot vazio, 2399 ticks e 200 FE gerados. Não é execução real de `finishRemoval` no Forge. Restaurar um item inteiro neste cenário geraria crédito extra; a fração de combustível requer representação apropriada.
- `reentrant-after-ExtractionKnownRisksTest.log`: 1 diagnóstico passa, mas ainda falham extração direta pelo `inventory` público que pode debitar e lançar sem retorno, e `Error` após débito de energia. Não foram escondidos nem tratados com captura indiscriminada de erros fatais.
- `reentrant-after-ExtractionSafetyTest.log`: 16/18; falham a expectativa de continuar após `RuntimeException` na notificação e a de snapshot mostrar a conversão. Não foram declaradas sucesso; a exceção agora ocorre antes do débito e pode interromper o tick.

## Outras regressões

Com métodos reais extraídos e mundo, inventário, tanque e NBT de teste: TransactionRegression 10/10; IndependentReview 10/10; ReviewFix 16/16; Safety 24 verificações; AutoThermalBelowCritical 45110 verificações. AutoThermalTest ainda falha no histórico `bounds` (linha 10). Logs `reentrant-regression-*.log`. Resultados em stubs não validam Forge, capabilities, serialização real, concorrência arbitrária ou durabilidade.

## Pendências de segurança

É necessário protocolo explícito de posse e recuperação, inclusive crédito parcialmente queimado, identidades/metadados/capabilities dos itens, snapshots reentrantes e sem restauração cega que possa duplicar crédito. Os campos e o inventário público expõem outros caminhos mutáveis e não estão protegidos aqui. `Error` após débito continua podendo propagar sem retorno; capturar todos os erros da VM não resolve atomicidade com segurança. Crash e salvamentos independentes exigem protocolo durável; os resultados não provam exatamente-uma-vez.
