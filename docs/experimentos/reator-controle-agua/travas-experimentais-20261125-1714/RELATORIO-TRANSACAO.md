# Correções experimentais de transações do reator — 25/11/2026

## Escopo e arquivos
Cópia independente de `nexus-reactor-safety-review-fixes-20261125-1709`. Código alterado apenas em `src/com/mikael8367/nexustech/nuclear/NuclearController.java`. Diff relativo à cópia de origem: `transaction-fixes.patch`. Testes novos: `tests/TransactionRegressionTest.java`, gerador `tests/generate_transaction_tests.py` e `run-transaction-tests.sh`. Logs: `transaction-before.log`, `transaction-final.log` e logs individuais `transaction-*.log`. Não foram apagados arquivos anteriores. Reator continua fora do build ativo; nenhum build Forge/JAR, publicação ou GitHub.

## Mudanças
- Se `setEnabled(true)` encontra o reator já ligado e temperatura crítica, trava SCRAM, zera produção, tenta desligar indicador e marcar dirty em vez de apenas desligar silenciosamente. A mesma rotina de trip de proteção usada para refrigerante é reutilizada. O comportamento do reator já desligado não foi alterado.
- `setCoolantGuard` protege a operação completa, incluindo as notificações do SCRAM e a marcação dirty, com a guarda `stopping`; reentradas de partida, tick e mudança da própria proteção são bloqueadas durante a transação. `readFromNBT` também é recusado enquanto `stopping`. O `finally` libera a guarda em caso de exceção. A parada reentrante dentro de uma partida continua interrompendo e travando a partida.
- Após callback da extração de combustível, ambos os modos revalidam o limite e a não negatividade de energia antes de consumir um tick de combustível ou produzir FE, inclusive sem drenagem de água. O item extraído é convertido em `fuelTicks` e fica disponível para geração futura; essa mudança não apaga o combustível. O teste de capacidade válida verifica que a geração ainda ocorre.

## Resultados
Antes: cinco falhas reproduzidas, duas passagens (`transaction-before.log`): desligamento crítico incompleto, reativação em callback do indicador e de dirty, e geração depois de callback de combustível com energia saturada ou negativa. Os testes novos adicionais foram criados depois dessa reprodução inicial.

Depois: `TransactionRegressionTest` 10/10, incluindo reentrada, tick aninhado, reinício manual posterior, exceção no callback e modo automático sem drenagem; `ReviewFixTest` 16/16; `SafetyTest` 24 verificações; `AutoThermalBelowCriticalTest` 45110 verificações. `AutoThermalTest` original **continua falhando** em `bounds`, linha 10, exit 1, pois exige geração partindo de 999 em conflito com o SCRAM antes de atingir 1000. A falha está registrada integralmente; não foi modificada nem escondida. O script só aceita essa falha histórica específica e rejeita outras falhas.

`df -h /` antes de copiar e de compilar stubs: 2,5 GB livres, 87% de uso. Compilação apenas Java 8 dos testes extraídos, não Forge.

## Limitações
O gerador extrai as implementações reais de API, setter/trip, partida e tick, mas tanque, indicador, inventário, mundo, estrutura e NBT são stubs: não é prova de integração Forge, remoção real, persistência, durabilidade, segurança completa ou ausência de outros bugs. O teste de `readFromNBT` não cobre o método real completo. Se um callback de indicador lançar antes de `markDirty`, o estado em memória já está parado e travado, mas a notificação pode não ocorrer; não há garantia de gravação nem de recuperação após crash. Campos públicos diretamente mutáveis por código externo não são protegidos por estas guardas. Não integrar sem revisão e testes reais apropriados.
