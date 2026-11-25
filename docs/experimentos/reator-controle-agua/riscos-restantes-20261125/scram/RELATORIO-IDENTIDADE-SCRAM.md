# Experimento independente: identidade e notificações SCRAM

## NÃO INTEGRAR
Cópia de `nexus-reactor-experimental-reentrancy-20261125-1746`. Antes de copiar e antes de compilar os testes isolados, df -h mostrou 2,5 GB livres, 87% ocupado. Não houve build Forge/JAR, alteração do projeto principal, GitHub, publicação ou exclusão. `identity-controller.patch` contém o diff somente do controlador, não inclui os testes. SHA-256 do controlador: `2c5b244af5c18ebb9c9d638495dc9fd5043f71ae4d33f3d56917678f721078b2`.

## Alterações
- `setIndicator` só opera em tile carregado e ainda atual (usable inclui identidade, invalidação e thread); verifica novamente depois da leitura e da escrita. Retorna falso quando a ação não é confirmada, inclusive chunk ausente, tile substituído ou setBlockState retorna falso. O retorno é usado no retry SCRAM para manter pendência. Isso não estabelece atomicidade contra qualquer implementação adversarial de World.
- Retry SCRAM verifica identidade/carga antes e depois de markDirty; uma notificação não realizada ou cuja identidade foi perdida continua pendente. O estado antigo permanece parado e travado, sem atuar no substituto após a perda de identidade. Pendências anteriores reentrantes são preservadas.
- `abortUnsafeStart(true)` passa pelo trip também se já desabilitado. `stopForStructureLoss` agenda a notificação antes de visuais quando estava ativo; falha de visual chama caminho SCRAM e preserva a exceção original; teste cobre visual que falha e visual que substitui e falha.

## Testes
`tests/generate_identity_tests.py` extrai indicador, callbacks SCRAM, partida, parada por perda de estrutura e tick reais; constrói stubs de mundo, carregamento, identidade, bloco e falhas, e compila com Java 8 sem Forge. Mesmo teste em ambas as fontes: `identity-before-v3.log` 1/8 (7 falhas), `identity-after-v3.log` 8/8, incluindo substituição em callbacks, chunk descarregado, retorno falso da escrita, retry, abort travado e erro visual. Os casos usam reflexão para dois métodos privados. Os stubs não reproduzem todo comportamento Forge.

Regressões com gerador reentrancy adaptado (indicador, estrutura e restante do mundo como stubs, portanto não são teste de integração): ReentrancyScramTest 23/23; IndependentReviewTest 10/10; TransactionRegressionTest 10/10; ReviewFixTest 16/16; SafetyTest 24 checks; AutoThermalBelowCriticalTest 45110 checks. `AutoThermalTest` continua falhando em bounds, linha 10, como já conhecido; log de falha preservado. Logs `identity-regression-*.log`.

## Limitações e riscos
`markDirty` não prova gravação em disco, nem pendências em memória sobrevivem a crash/descarregamento/restart; retentativas exigem tick e tile atual. Estado anterior em disco pode ressurgir sem SCRAM. Não é garantia de durabilidade. Se tile é removido/substituído definitivamente, pendências do objeto antigo não serão necessariamente processadas. `setVisualFormed` e `syncPartVisuals` da estrutura real, remoção, interações reentrantes dentro das próprias consultas World/setBlockState e múltiplos caminhos de tick/ownership não foram exercitados nem integralmente auditados. O stub de regressão usa stop simplificado; só o harness de identidade extrai a parada real. Não houve teste de jogo, chunk real ou gravação real. Preservar cópias e revisar antes de eventual combinação com outras variantes.
