# Auditoria adicional de caminhos e custo FE

## Resultado

- Trabalho isolado em `nexus-fe-path-audit-20261122`, copiado de `nexus-fe-topology-20261122`; não foi alterado outro workspace, repositório GitHub ou gerado JAR. O `EnergyNetwork.java` real permaneceu inalterado (SHA-256 `1f5833057947be2065e304e6c2aa5c0ace5fc3608326a6be3236089829d50380`). Não há correção de produção nova nesta auditoria: os cenários testados de rota alternativa já passam na implementação recebida, e não há otimização validada que preserve as garantias neste modelo.
- `df -h /` antes da cópia: 251 MB disponíveis, 99% de uso; antes dos builds: entre 249 e 248 MB. Risco operacional: disco quase cheio. Nada foi apagado para liberar espaço.
- Os 110 checks anteriores (29 + 9 + 72) e 116 novos passaram, total 226; `audit-results.txt` contém toda a saída. A varredura separada 184–195 acrescenta 48 checks, em `audit-sweep.txt`. Compilação Java 8, código real com stubs mínimos, não Forge/Minecraft real.

## Rota alternativa após nó compartilhado já descoberto

- A geometria é O=(0,0,0), A=(0,-1,0), B=(0,0,-1), compartilhado=(0,-1,-1). A BFS primeiro descobre o compartilhado via A e depois o lê novamente via B antes do callback no endpoint de B=(1,0,-1). O teste exige ao menos 2 leituras da posição compartilhada ANTES da mutação; o alvo está em (0,-2,-1).
- O callback remove o ancestral A quando a rota do compartilhado ainda aponta para ele; a rota do próprio callback via B permanece válida. Mais tarde, a fila encontra a rota antiga inválida: reiniciar a busca permite descobrir B → compartilhado. Para ambos `send` e `pull`, em modo real e simulado, e mutação tanto no predicado quanto na transferência, o alvo é chamado exatamente uma vez e o total é 100 (10 do mutador + 90 do alvo). Em modo simulado, os saldos permanecem zero.
- Se o callback remove também B, o alvo nunca é chamado, inclusive na simulação. Quando a remoção de B acontece no predicado, nem a transferência do próprio mutador é chamada; resultado 0. Quando a remoção acontece dentro da transferência já iniciada, preserva-se o retorno conhecido de 10, sem chamar o alvo.
- Controle negativo isolado em `mutation-no-restart/`: uma variante artificial troca apenas o reinício no processamento de rota inválida por `return moved`; os testes de send e pull falham como esperado (obtido 10, esperado 100). Não é código a integrar. Saídas `mutation-no-restart/results-send.txt` e `results-pull.txt`. Isso confirma que o teste alcança o caso de rota já descoberta, e não apenas um corte anterior à descoberta. Não demonstra correção de todas as topologias possíveis.

## Chamadas de mundo: cadeia reta, endpoint único

Cada cenário cria N cabos em linha, origem `MachineTile` e um endpoint no final; sem fontes externas ou mutações. O `CountWorld` contabiliza cada chamada `isBlockLoaded` e cada chamada `getTileEntity`, incluindo todas feitas pelo próprio código de produção. Números idênticos para send e pull; são contagens, não milissegundos nem custos reais medidos no Forge.

| Cabos | isBlockLoaded | getTileEntity | Total | FE movida | Chamadas ao endpoint |
|---:|---:|---:|---:|---:|---:|
| 32 | 4.092 | 4.092 | 8.184 | 100 | 1 |
| 64 | 15.340 | 15.340 | 30.680 | 100 | 1 |
| 128 | 59.340 | 59.340 | 118.680 | 100 | 1 |
| 256 | 132.230 | 132.230 | 264.460 | 0 | 0 |
| 512 | 132.230 | 132.230 | 264.460 | 0 | 0 |

- Crescimento 32→64 de 3,75× e 64→128 de 3,87× comprova comportamento aproximadamente quadrático antes do corte. A implementação atual revalida a rota inteira repetidamente para cada nó e direção, até mesmo nessa cadeia sem callbacks intermediários. Não foi demonstrado que todo esse custo seja teoricamente inerente: há revalidações repetidas, mas eliminar ou memorizar verificações exige garantias robustas sobre mutações e invalidação que não foram provadas aqui. Após callbacks arbitrários que podem mudar ancestrais, a validação cuidadosa continua necessária. Não se sacrificou segurança para melhorar os números.
- Varredura específica: 192 cabos movem 100 em ambos os sentidos, com 132.012 chamadas a cada método (264.024 no total); 193 cabos movem 0, sem chamar endpoint, com 132.230 chamadas a cada método (264.460 no total). Isso vale apenas para esta geometria, origem=excluído e demais opções do cenário; não é um limite universal para qualquer rede.
- O limite de 131.072 é de **passos de validação `same()`**, não o número total de chamadas ao mundo; consultas de vizinhos também contribuem às contagens. Outros parâmetros, endpoints, callbacks, reinícios e topologias podem esgotar o orçamento mais cedo. O teto de 4.096 cabos é distinto. Limites anteriores e falta de justiça entre ticks continuam.

## Reproduzir e ressalvas

- `sh repro.sh`: compila em diretório novo `repro.*` e executa as 4 suites (JDK padrão `../nexus-test/jdk/bin`; `JDK` pode substituí-lo). `tests/com/mikael8367/nexustech/EnergyNetworkPathAuditTest.java` contém cenários, contadores e benchmarks; o argumento `sweep` roda a varredura. As saídas foram salvas nos arquivos citados.
- Falhas de endpoint com resultado incerto ainda podem causar perda de FE; validação não torna callbacks atômicos. Consultas de mundo com efeitos colaterais, mudanças simultâneas ou internas que não alterem a identidade e os demais riscos da auditoria anterior permanecem. Não houve teste no jogo, medição de latência/tick, build Forge, integração nem publicação. O gargalo e a interrupção de redes válidas devem bloquear a integração como solução final até existir estratégia segura, reproduzível e testada no ambiente real.
