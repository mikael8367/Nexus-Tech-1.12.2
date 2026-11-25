# Correções experimentais da revisão independente — itens 1, 2 e 3

## Escopo
Cópia experimental de `nexus-reactor-experimental-fixes-20261125-1714`, sem alterar a origem nem remover arquivos. Alteração de código somente em `src/com/mikael8367/nexustech/nuclear/NuclearController.java`; diff em `independent-review-fixes.patch`. Nenhum Forge, JAR, integração, publicação ou GitHub. Espaço verificado com `df -h /` antes de copiar e de compilar: 2,5 GB livres, 87% de uso.

## Alterações
1. Ao abortar uma partida insegura enquanto já ligado, fora dos casos de SCRAM existentes, zera produção, desliga o indicador e chama `markDirty`. Assim a falta de combustível é notificada; reator já desligado não recebe notificação adicional.
2. Ao ativar o controle automático com reator ligado e água zerada, aplica imediatamente o trip existente: `enabled=false`, `scrammed=true`, produção zero, indicador desligado e `markDirty`. Protege toda a transação com `stopping`, inclusive seus callbacks, e libera a guarda em `finally`. Com água presente não desliga; com reator já desligado não força SCRAM.
3. `repairAutoTemperatureConfig`, `setAutoTemperatureTarget` e `setAutoTemperatureEnabled` recusam chamadas durante `stopping`. Callback não pode reconfigurar ou reiniciar no meio da parada. Reinício manual com água reposta permanece possível sujeito às demais condições de segurança.

## Testes isolados
`tests/IndependentReviewTest.java` exercita implementações extraídas do código real por `tests/generate_transaction_tests.py`, com stubs para mundo, inventário, tanque, indicador, estrutura e NBT. Antes: `independent-before.log` registra 3/10 aprovados e 7 falhas, incluindo falta de combustível sem notificação, automático sem SCRAM imediato, setters reentrantes e callback recursivo com StackOverflow. Depois: `independent-after-IndependentReviewTest.log` registra 10/10: os três itens, callbacks do indicador e dirty, reparo, bloqueio de reinício e reconfiguração durante trip, ativação segura, reator desligado, reinício manual e exceção de indicador mantendo o estado parado em memória.

Regressões anteriores recompiladas e executadas com o mesmo gerador: `TransactionRegressionTest` 10/10; `ReviewFixTest` 16/16; `SafetyTest` 24 verificações; `AutoThermalBelowCriticalTest` 45.110 verificações. `AutoThermalTest` original continua **falhando**, exit 1, em `bounds` linha 10, conflito previamente documentado para temperatura inicial 999; não foi tratado como sucesso. Logs individuais `independent-after-*.log` preservam o resultado completo.

## Limites e riscos em aberto
NÃO INTEGRAR com base nesses testes isolados. Stubs não demonstram execução, persistência ou durabilidade em Forge/Minecraft nem segurança contra modificação direta de campos públicos. A hipótese de perda de item quando `extractItem` remove antes de callback lançar continua não resolvida. Se `setIndicator` lançar antes de `markDirty`, este pode não ser chamado; não há prova de gravação do SCRAM após crash. O teste de exceção demonstra somente estado parado em memória e liberação da guarda, não persistência. O método real completo `readFromNBT` não foi exercitado por estes testes.
