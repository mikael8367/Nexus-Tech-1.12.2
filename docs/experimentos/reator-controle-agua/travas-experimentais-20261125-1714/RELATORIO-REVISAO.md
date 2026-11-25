# Correções experimentais da revisão — 25/11/2026

Cópia experimental independente de `nexus-reactor-safety-experimental-20261125`; código principal e GitHub não foram tocados, nenhuma versão integrada, nenhuma compilação Forge/JAR. `df -h /` foi executado antes da cópia e das compilações dos stubs: 2,5 GB livres no sistema de arquivos.

## Alterações
- O incremento de temperatura de +3 é verificado imediatamente: a partir de 997 ocorre SCRAM antes do resfriamento por produção, consumo de combustível ou geração de FE. A drenagem de água pode já ter ocorrido antes do incremento; o resfriamento de emergência posterior permanece possível. Em 996 o funcionamento normal continua.
- Verificação de temperatura crítica antes da geração em todos os modos, inclusive depois de callbacks da extração de combustível e no modo legado sem água.
- Tentativa de ligar enquanto reator já está ligado e a proteção de refrigerante exige água ausente aplica trava, indicador desligado e `markDirty`, inclusive quando a água acaba durante o início. Tentativas recusadas com reator já desligado preservam o comportamento anterior.
- Guarda de reentrada durante parada manual bloqueia reinício por callbacks do indicador ou de `markDirty`, mas a parada aninhada durante início permanece interrompendo e travando o início. Um tick aninhado durante a parada é bloqueado.
- O stub do tanque reproduz que `onContentsChanged` captura `RuntimeException` de `markDirty`: um teste verifica que a exceção é engolida e a operação pode continuar. O antigo teste que lança diretamente em `onDrain` testa uma exceção sintética diferente, não prova propagação de falha de `markDirty` do tanque real. Não há garantia de que o chunk foi marcado nem de persistência após crash.

## Testes e limites
`review-before2.log`: antes das correções, 8 falhas e 4 sucessos, inclusive picos, partida a seco já ligada, reentrada e callback de combustível no modo legado. `review-final.log`: 16 casos novos passam; `SafetyTest` original passa 24 verificações. `AutoThermalTest` original é preservado e falha em `bounds`, linha 10, porque exigia continuar gerando desde temperatura inicial 999; essa expectativa é incompatível com a nova trava imediata. `AutoThermalBelowCriticalTest` é um novo teste derivado explícito com 996 em lugar de 999, passa 45110 verificações; novo teste separado cobre 997/998/999 e o valor crítico. Logs anteriores foram mantidos.

Os testes são do código extraído com stubs de tanque, mundo, estrutura e inventário; não são testes em mundo Forge, não demonstram comportamento completo de callbacks reais, nem corrigem ou comprovam durabilidade contra crashes. Outras falhas podem existir. Patch relativo à cópia anterior: `review-fixes-final.patch`.
