# Auditoria de desempenho FE — protótipo não aprovado

## Conclusão

A implementação experimental original não foi alterada nem publicada. Sua cópia em `src/` e o arquivo da origem `nexus-fe-topology-20261122` têm SHA-256 `1f5833057947be2065e304e6c2aa5c0ace5fc3608326a6be3236089829d50380`. Um protótipo isolado em `prototype/` reduziu muito as consultas em linhas sem mutação, mas NÃO é correção validada nem deve ser integrado: deixou de validar rotas antes de consultar vizinhos de nós já desconectados. Um teste reproduz a diferença. Não foi provado que a otimização preserva todas as propriedades, principalmente com consultas ao mundo com efeitos colaterais. Não foi demonstrado que consultas Forge reais causem efeitos prejudiciais neste cenário.

Disco: 247 MB antes da cópia, 245 MB antes do primeiro build, 244 MB depois. Nenhum arquivo apagado. Sem build Forge, JAR, Minecraft, medição de tempo ou publicação.

## Medições

Cadeia com N cabos, origem excluída, um endpoint, sem callbacks de mutação. A tabela apresenta a soma de chamadas `isBlockLoaded` e `getTileEntity`; os valores foram iguais para send e pull e, em cada linha, metade é de cada método. Isto não é medição de latência. O limite de 4096 inclui a origem como nó visitado nesta implementação.

| Cabos | Original: consultas / FE | Protótipo: consultas / FE |
|---:|---:|---:|
| 32 | 8.184 / 100 | 726 / 100 |
| 64 | 30.680 / 100 | 1.430 / 100 |
| 128 | 118.680 / 100 | 2.838 / 100 |
| 192 | 264.024 / 100 | 4.246 / 100 |
| 193 | 264.460 / 0 | 4.268 / 100 |
| 256 | 264.460 / 0 | 5.654 / 100 |
| 512 | 264.460 / 0 | 11.286 / 100 |
| 1.024 | 264.460 / 0 | 22.550 / 100 |
| 4.094 | 264.460 / 0 | 90.090 / 100 |
| 4.095 | 264.460 / 0 | 90.112 / 100 |
| 4.096 | 264.460 / 0 | 49.144 / 0 |

O endpoint foi chamado uma vez para FE=100 e zero vezes para FE=0. No protótipo a varredura sem callbacks usa menos consultas nesta geometria, mas ainda pode custar proporcionalmente ao comprimento por callback, e as limitações de número de cabos, validação, reinícios, justiça e resultados desconhecidos permanecem. Não se generalizam os resultados para todas as redes ou para o desempenho real.

## Risco observado no protótipo

O protótipo removeu a validação integral ao processar nó e antes de cada vizinho; manteve revalidação ao redor de callbacks e adicionou uma barreira de identidade sobre os nós descobertos ao esgotar a fila. Isso não é equivalente à sequência anterior.

`LazyTraversalAdversarialTest` enfileira A→A2 e, por um outro caminho B, executa um endpoint que remove A ou torna A descarregado. Existe outro endpoint após A2. Em send e pull, simulado e real, com remoção e descarregamento (8 cenários): o código original não consulta o tile após A2 pela rota desconectada; o protótipo consulta esse tile uma vez antes de detectar a rota inválida. Ambos retornam 10 FE e não chamam capability desse endpoint desconectado. O teste usa um World de stub cujo `getTileEntity` contabiliza a consulta; não demonstra dano real por si só. Demonstra, porém, mudança observável relevante quando leituras de mundo podem ter efeitos, e é suficiente para não promover este protótipo sob as garantias exigidas. A barreira também não foi demonstrada suficiente para todos os caminhos de saída antecipada e situações de mutação arbitrária.

## Regressões e reprodução

`sh performance-repro.sh` executa ambos os builds Java 8 usando stubs e 4 suites, varredura com todos os comprimentos da tabela e 8 testes adversariais por implementação. O original passou 29+9+72+116 checks; o protótipo passou 29+9+72+140. No teste de topologia do protótipo apenas a expectativa da cadeia de 1.200 cabos foi atualizada (antes falhava fechada, agora move 100); os demais testes permanecem os mesmos. A auditoria de caminhos inclui rotas alternativas em send/pull, simulação, mutação em predicado e transferência; a suíte de topologia inclui chunk descarregado e reentrada. Os arquivos `performance-*-results.txt`, `performance-*-build-sweep.txt` e `performance-*-adversarial.txt` contêm os resultados. O primeiro teste sem ajuste de expectativa falhou na cadeia 1.200 como esperado pela mudança deliberada.

Os testes são isolados e não provam segurança para todas as topologias, callbacks, consultas de mundo, concorrência, exceções ou alterações que preservem identidade. Uma solução futura precisará de prova/instrumentação de invalidação confiável ou de um contrato explícito, confirmado no Forge, para leituras de mundo, além de preservar revalidação após cada callback e reinícios para rotas alternativas. Até lá o gargalo e o corte de redes válidas continuam bloqueando integração como solução final.
