# Changelog

## 0.6.0 — varredura de bugs

### Crítico
- **Máquinas perdiam energia e itens ao recarregar o mundo.** As classes `Tile*` não eram públicas, e o Minecraft não conseguia recriá-las (`IllegalAccessException` em todo carregamento de chunk). Itens dentro do forno e da bigorna eram apagados. Agora cada tile é uma classe pública com construtor público, e há um teste de regressão.
- **Forno elétrico e bigorna elétrica nunca funcionavam.** Eles consumiam a própria energia via `extractEnergy`, mas têm extração externa 0 FE/t. Foi criado `EnergyBuffer.consumeInternal`.
- **Receita da bigorna elétrica não carregava.** `minecraft:anvil` precisa de `"data": 0`; o Forge rejeitava a receita.
- **Três texturas PNG corrompidas** (`electric_furnace_front`, `electric_anvil_front`, `solar_panel_side`) apareciam como textura roxa/preta. Foram refeitas com a mesma paleta.
- **ProjectE Storage Bridge — duplicação via NBT.** O cabo aprendia o item com NBT e dano (mochilas cheias, encantamentos). Retirar depois criava cópias com o conteúdo pelo valor base. Agora o item é normalizado como na Mesa de Transmutação, `PlayerAttemptLearnEvent` é respeitado e entradas antigas contaminadas não são listadas nem entregues.

### Energia
- Cabos não devolvem energia pela face de onde ela acabou de chegar (evita loop com baterias/cabos de outros mods).
- Entre cabos Nexus a energia só desce para o cabo com menos FE e converge (sem vai-e-volta); a soma é conservada.
- `pull` ignora qualquer bloco Nexus (eles já empurram sozinhos) e não lê chunks não carregados.
- Proteção contra provedores externos que retornam valores inválidos no commit.

### Rede e desempenho
- Sincronização só quando o estado exibido muda, via pacote do tile (sem `notifyBlockUpdate`, que forçava o cliente a refazer o chunk).

### Blocos, modelos e interface
- Gerador, forno e bigorna agora viram a frente para o jogador ao colocar (mundos antigos continuam virados para o norte).
- Cabo de energia com conexões reais (multipart): braços só aparecem onde há bloco FE; colisão e seleção seguem o formato.
- Caixa da bigorna gira junto com a direção.
- Abrir a tela de status não cria mais um `Container` no servidor — antes o inventário do jogador ficava dessincronizado.
- Tela fecha quando o bloco é quebrado ou o jogador se afasta.
- Clique com a mão secundária não abre mais a tela nem bloqueia o uso do item.
- Inserir item pelo cliente não abre a tela por engano.
- Painel solar usa `isRainingAt` (deserto/savana continuam gerando) e mostra se está gerando.
- Forno elétrico concede XP de fundição ao retirar a saída.
- Retirada parcial (inventário quase cheio) agora derruba o resto em vez de perder itens.
- Funis e automação não podem colocar itens inválidos (terra no forno, pilhas na bigorna).
- Todos os textos da tela traduzidos (pt_BR e en_US); títulos usam o nome do bloco.

### Projeto
- Versão única em `gradle.properties` (0.6.0) aplicada aos dois mods, `mcmod.info` e manifest.
- `settings.gradle` renomeado para `Nexus-Tech-1.12.2`.
- Novos testes: consumo interno, divisão entre cabos, reflexão dos tiles, integridade de PNG/modelos/blockstates/traduções/receitas.
