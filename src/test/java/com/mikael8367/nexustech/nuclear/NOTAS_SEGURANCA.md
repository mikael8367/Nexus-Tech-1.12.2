# Regressões de segurança nuclear

Com Java 8 e dependências ForgeGradle já em cache, executar na raiz do projeto:

```sh
bash ./gradlew --offline --no-daemon -I src/test/java/com/mikael8367/nexustech/nuclear/nuclear-safety-check.init.gradle nuclearSafetyCheck
```

O teste principal executa também `NuclearReactorTest` e `NuclearRegressionTest`. Abrange estrutura inválida ou indisponível, interrupção e persistência, ausência de religamento automático, reinício manual após resfriamento, retenção de vínculos e proteção de mineração com controlador quente ou inacessível, disputa por peças, comportamento do tanque e simulação, além das regressões prévias de energia, itens e estruturas.

Os testes usam o controlador e os handlers reais em um mundo isolado de teste. O callback de proteção é chamado com um evento de teste cuja capacidade de cancelamento simula a transformação de eventos do Forge, ausente no teste isolado; não substitui teste em jogo com carregamento real de chunks, barramento Forge, jogadores ou outros mods. A proteção por `BreakEvent` destina-se à mineração por jogador; não garante proteção contra remoções feitas por explosões ou outros mods. Perdas que ocorram e sejam revertidas antes de uma verificação do controlador não são detectadas por estes testes.
