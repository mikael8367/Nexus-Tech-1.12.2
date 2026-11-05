# ProjectE Storage Bridge

Integra ProjectE e Simple Storage Network no Minecraft Java 1.12.2 / Forge.

## O que já foi implementado

- Bloco-cabo próprio que herda a estrutura de cabo do Simple Storage Network.
- Capability `IConnectable` herdada de `TileCable`, permitindo que o grafo da SSN atravesse o bloco.
- Capability `IConnectableLink` para apresentar itens conhecidos no ProjectE como armazenamento virtual.
- Retirada virtual com cobrança de EMC no servidor.
- Simulação sem cobrança; retirada real debita EMC antes de devolver o item.
- Limite de retirada calculado sem permitir saldo negativo.
- Itens sem valor EMC ou sem conhecimento não são disponibilizados.
- Testes unitários para os limites de retirada.

## Limitação importante da API da SSN

A interface pública `IConnectableLink.extractStack(...)` não recebe o jogador que solicitou o item. Por segurança, esta versão só expõe o inventário virtual quando existe exatamente um jogador conectado ao servidor. Em servidor com dois ou mais jogadores, a ponte fica indisponível em vez de cobrar EMC da pessoa errada. O suporte multiplayer com contas independentes exige uma integração adicional no fluxo de requisições da SSN.

## Requisitos fixados

- Minecraft 1.12.2
- Forge 14.23.5.2864
- Java 8
- ProjectE 1.12.2-PE1.4.1 (CurseForge file 2702991)
- Simple Storage Network 1.12.2-1.8.3 (CurseForge file 3655827)

## Compilar

O workflow do GitHub Actions compila com Java 8 e Gradle 4.10.3. O JAR só deve ser usado depois de o workflow concluir com sucesso e de testes em jogo.

## Auditoria de segurança

- Todas as operações de EMC são executadas no servidor.
- A simulação não altera o saldo.
- A retirada real verifica saldo e conhecimento novamente.
- A quantidade é limitada pelo saldo e pelo limite de stack do item.
- A ponte não aceita inserções de itens.
- Não há fallback para jogador aleatório em ambiente multiplayer.
- Ainda é necessário testar em cliente real 1.12.2, inclusive reconexão, recarga de chunks e interação com remotes da SSN.
