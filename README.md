# Nexus Tech

Mod de tecnologia para Minecraft Java **1.12.2** com Forge. A implementação atual inclui nove blocos que geram, armazenam, transportam e consomem **Forge Energy (FE)** e um décimo bloco independente para diagnóstico da ponte, descrito ao final.

## Blocos e funcionamento

As taxas abaixo são por tick de jogo; 20 ticks correspondem a aproximadamente um segundo em velocidade normal. Todos os IDs usam o prefixo `nexustech:`.

| Bloco | Função e capacidade |
| --- | --- |
| `energy_cable` | Transporta FE entre blocos adjacentes; não armazena energia e não tem interface. |
| `solar_panel` | Gera **20 FE/tick** sob as condições abaixo; armazena até **10.000 FE**. |
| `coal_generator` | Gera **40 FE/tick** enquanto queima combustível; armazena até **20.000 FE**. |
| `electric_turbine` | Gera **30 FE/tick** com água corrente adjacente; armazena até **10.000 FE**. |
| `battery` | Armazena até **100.000 FE** e fornece energia a consumidores. |
| `electric_furnace` | Fundição elétrica; armazena até **20.000 FE**. |
| `electric_crusher` | Triturador elétrico; armazena até **20.000 FE**. |
| `electric_compressor` | Compressor elétrico; armazena até **20.000 FE**. |
| `lava_generator` | Gera **40 FE/tick** com baldes de lava; armazena até **20.000 FE**. |

- **Painel solar:** a dimensão precisa ter iluminação de céu, ser dia, não estar chovendo nem trovejando e o bloco acima precisa estar carregado, com visão do céu e luz do céu de pelo menos 15. O cabo não bloqueia a luz; painel solar, gerador de carvão, turbina e bateria bloqueiam a luz de céu como um bloco inteiro, apesar dos modelos parciais. Assim, podem impedir a geração de um painel abaixo deles.

- **Gerador:** coloque no espaço de combustível qualquer item com tempo de queima válido para a fornalha. A duração segue o tempo de queima do item; a queima pausa quando não há espaço para os **40 FE** de um tick inteiro. Combustíveis com item recipiente exigem que haja apenas um item no espaço ao iniciar a queima, para que o recipiente possa ser devolvido. O funil pode retirar o balde vazio deixado por um balde de lava; não retira combustível não queimado. Outros recipientes devem ser retirados pela interface.
- **Turbina:** basta um dos seis blocos adjacentes carregados ser água vanilla (`water` ou `flowing_water`) com `LEVEL > 0`. Água-fonte (`LEVEL = 0`) sozinha não ativa a turbina; não é necessário consumir a água.
- **Fornalha elétrica:** aceita receitas de fundição registradas na fornalha. Consome **20 FE por tick de trabalho** durante **200 ticks**, ou **4.000 FE por operação**. Sem energia suficiente, o progresso pausa; trocar o tipo, metadados ou NBT da entrada, ou ficar sem uma receita/saída válida, zera o progresso. A interface tem espaços de entrada e saída.

- **Triturador e compressor elétricos:** cada operação consome **20 FE/tick de trabalho** por **200 ticks** (total de **4.000 FE**). Ambos têm entrada e saída. Sem energia suficiente, o progresso pausa; ao mudar o tipo, metadados ou NBT da entrada, ou faltar entrada suficiente ou espaço para a saída, o progresso é zerado. As receitas são fixas para os itens vanilla abaixo, não receitas genéricas de outros mods:
  - Triturador: **1 pedregulho → 1 cascalho**, **1 cascalho → 1 areia**, **1 minério de ferro → 2 barras de ferro** e **1 minério de ouro → 2 barras de ouro**.
  - Compressor: **9 carvões → 1 bloco de carvão**, **9 barras de ferro → 1 bloco de ferro**, **9 barras de ouro → 1 bloco de ouro** e **4 bolas de argila → 1 bloco de argila**. O carvão aceito é `minecraft:coal` com metadado 0, não carvão vegetal.
- **Gerador de lava:** coloque um **balde de lava vanilla** na entrada. Um balde produz **40 FE/tick por 1.000 ticks** (**40.000 FE** no total), devolvendo um balde vazio na saída ao iniciar. Precisa haver espaço na saída para o balde vazio; a queima pausa quando não há espaço para os **40 FE** de um tick inteiro. Não há entrada de fluido documentada: a alimentação implementada usa baldes de lava.

Os modelos parciais do painel, gerador de carvão, turbina, bateria e cabo não ocultam as faces dos blocos vizinhos e são tratados como não completos para renderização. As quatro máquinas têm opacidade de luz 15 (o cabo tem 0): o bloqueio de luz usa o bloco inteiro, inclusive os espaços vazios do modelo. As caixas de colisão permanecem de bloco inteiro; não há colisão ajustada ao desenho. A iluminação das máquinas usa brilho das posições vizinhas, o que pode produzir diferenças visuais; o cabo mantém o comportamento anterior de brilho.

Clique com o botão direito nos blocos, exceto o cabo, para abrir suas interfaces e consultar a energia armazenada. As interfaces dos geradores mostram a queima; as da fornalha, do triturador e do compressor mostram o progresso. Os nove blocos aparecem na aba Nexus Tech do inventário criativo.

## Interligação e limites

Coloque geradores, bateria e máquinas consumidoras diretamente adjacentes ou conecte-os por cabos, inclusive em ramificações. A conexão considera as seis faces. Por exemplo: painel solar → cabos → bateria → fornalha elétrica, ou gerador de lava → triturador.

**Integração com outros mods:** as máquinas usam a capacidade **Forge Energy (FE)** e podem receber ou fornecer FE para blocos de outros mods que exponham a capacidade apropriada na face conectada. Por exemplo, uma fonte externa que envie FE pode alimentar a bateria, a fornalha, o triturador ou o compressor; um receptor externo de FE pode receber energia dos geradores ou da bateria. A conexão depende do comportamento do mod conectado, das faces, dos limites e da forma de transferência (enviar ou extrair): não há promessa de compatibilidade universal. Alguns sistemas chamados **RF** também podem funcionar quando seus blocos expõem FE; o nome RF sozinho não garante compatibilidade. Não há suporte ou conversão garantidos para **EU**, **Tesla** ou **RF legado** sem uma ponte compatível.

Os cabos aceitam FE enviada para eles e a distribuem aos receptores. A integração de extração (*pull*) pelos cabos depende da implementação da versão utilizada e do comportamento do mod conectado; não presuma que qualquer fonte ou consumidor funcione com ela.

Os geradores enviam automaticamente até **100 FE/tick** cada, e a bateria até **128 FE/tick**, quando há energia armazenada e um receptor aceita. A entrada ou extração FE nas máquinas com armazenamento é limitada a **1.000 FE por chamada**, não necessariamente por tick. Os cabos não têm armazenamento próprio. A distribuição não promete divisão igual entre receptores.

**Política conservadora da bateria:** no envio automático, a bateria ignora qualquer bloco que exponha extração FE em alguma face, inclusive armazenamentos e consumidores bidirecionais vazios. `IEnergyStorage` não informa se um bloco bidirecional é uma fonte, armazenamento ou consumidor passivo: permitir envio a todos pode devolver a energia que a bateria acabou de extrair, inclusive em ticks diferentes. A bateria ainda puxa energia desses blocos e envia para receptores que não exponham extração; um consumidor bidirecional que não extraia ativamente pode não ser alimentado por ela. Se o consumidor fizer *pull*, pode extrair da bateria diretamente ou pela face conectada de um cabo. Geradores Nexus podem enviar a receptores bidirecionais. A ordem de visita é fixa: não há garantia de prioridade aos geradores nem de distribuição justa entre fontes ou receptores. Essa proteção depende de `canExtract()` refletir corretamente cada face; redes externas com armazenamento compartilhado entre blocos distintos podem criar ciclos que o mod não consegue identificar.

**Baterias não alimentam outras baterias na distribuição automática**, nem diretamente nem pela rede de cabos. Elas podem receber energia dos geradores e alimentar a fornalha, o triturador, o compressor ou outros receptores compatíveis. A simulação de máquinas e as transferências de energia ocorrem no **servidor**; posições não carregadas não são percorridas pela rede. Para multiplayer, use o mod no servidor Forge e nos clientes.

## Receitas

As receitas são moldadas e podem ser feitas na interface de uma bancada de trabalho 3×3. Não há advancements de desbloqueio de receitas incluídos; portanto, não se pressupõe que apareçam no livro de receitas. `.` indica espaço vazio. Cada receita produz um bloco, salvo indicação contrária.

Legenda: `I` = barra de ferro; `R` = redstone; `G` = vidro; `D` = sensor de luz solar; `C` = carvão (`minecraft:coal`); `F` = fornalha; `P` = pistão; `O` = barra de ouro; `B` = bloco de redstone; `X` = bloco de ferro; `Y` = ejetor (dispenser); `H` = funil; `U` = balde de lava; `M` = bloco de magma.

| Resultado | Grade 3×3, de cima para baixo |
| --- | --- |
| `energy_cable` ×8 | `III` / `RRR` / `III` |
| `solar_panel` | `GGG` / `IDI` / `RIR` |
| `coal_generator` | `IRI` / `CFC` / `.I.` |
| `electric_turbine` | `IRI` / `IPI` / `.I.` |
| `battery` | `IRI` / `ROR` / `IRI` |
| `electric_furnace` | `IRI` / `RFR` / `IBI` |
| `electric_crusher` | `IPI` / `RYR` / `IHI` |
| `electric_compressor` | `IPI` / `IXI` / `IRI` |
| `lava_generator` | `IUI` / `RFR` / `IMI` |

## Requisitos e desenvolvimento

- Minecraft Java **1.12.2** com Forge **14.23.5.2860** para executar o mod. **Um build completo com a versão 2860 ainda não foi verificado.**
- **JDK 8** (não apenas JRE) para o desenvolvimento; configure `JAVA_HOME` para a pasta do JDK e inclua `JAVA_HOME/bin` no `PATH`. Confira com `java -version` e `javac -version`.
- Internet na primeira execução para baixar Gradle, Forge e dependências; alguns gigabytes de espaço livre.

Para compilar, na raiz do projeto:

```sh
sh gradlew build
```

No Windows (Prompt de Comando):

```bat
gradlew.bat build
```

O JAR reobfuscado é gerado em `build/libs/` (por exemplo, `nexustech-1.0.jar`); coloque-o na pasta `mods` de uma instalação Forge compatível. Para iniciar o cliente de desenvolvimento, use `sh gradlew runClient` (Windows: `gradlew.bat runClient`); para o servidor de desenvolvimento, use `sh gradlew runServer` ou `gradlew.bat runServer`.

O wrapper oficial do MDK é armazenado como texto em `gradle/wrapper/gradle-wrapper.jar.base64.part*`. Os scripts o decodificam automaticamente na primeira execução; o JAR gerado é ignorado pelo Git. No Unix é necessário `base64` (GNU ou BSD) ou `openssl`; no Windows, PowerShell. Não é necessário marcar `gradlew` como executável quando iniciado com `sh`. Se o JAR local estiver danificado, exclua `gradle/wrapper/gradle-wrapper.jar` e execute o comando novamente.

A versão do mod em `build.gradle` e `NexusTech.VERSION` deve permanecer sincronizada; `mcmod.info` é preenchido durante `processResources`. Os avisos e créditos do MDK estão em `MDK-LICENSE.part*.txt` (em ordem) e `MDK-CREDITS.txt`.


## Ponte ProjectE / Simple Storage Network — etapa de diagnóstico

O bloco `nexustech:storage_bridge` aparece na aba criativa e abre uma GUI própria com clique direito. Não há receita nesta etapa. A GUI mostra o nome do jogador que a abriu e resultados enviados pelo servidor, atualizados enquanto aberta: presença dos mods no servidor e os seis blocos imediatamente adjacentes, sem carregar chunks. Chunks não carregados são indicados separadamente. Reconhece `projecte:transmutation_table`, outros blocos `projecte`, e os blocos `storagenetwork:master`, `request`, `inventory`, `simple_kabel`, `kabel`, `storage_kabel`, `ex_kabel`, `im_kabel` e `process_kabel`; outros blocos do namespace SSN são classificados genericamente. Identificação por ID não comprova conexão, acesso, funcionamento da rede nem compatibilidade de versões. IDs conferidos no código do ProjectE ramo mc1.12.x e SSN tag v1.12.2-2.0.1.

**Leitura bloqueada:** existe código experimental para consultar por reflexão a capacidade pública de conhecimento do ProjectE **1.12.2-PE1.4.1** apenas no servidor e para o jogador online que abriu a GUI. Entretanto, a consulta está deliberadamente desativada e a GUI mostra dados indisponíveis: os testes usaram a interface oficial e provedores simulados, mas não validaram o caminho completo com ProjectE real e a injeção de capacidade do Forge. Não se substitui falha por zero. A estrutura de sincronização usa partes de 16 bits e trata espera, indisponibilidade e erro. Quando o tomo está ativo, a API retorna a lista do tomo, não a lista individual. O projeto não tem dependência estática do ProjectE; não é necessário instalá-lo. Não houve teste em jogo.

**Limitação:** não conecta redes, não lista ou movimenta itens, não converte nem altera EMC. A presença do bloco ao lado não significa integração funcional. É um bloco independente do cabo de energia existente, sem capacidades de energia ou inventário; não participa da rede FE. A textura representa um cabo estático, não conexões detectadas. As caixas de colisão permanecem de bloco inteiro. Antes de qualquer futura transferência são necessários testes reais em cliente e servidor com as versões alvo, controle de identidade/permissões e desenho transacional contra duplicação e perdas.

Os testes `main` são executados por `sh gradlew check` ou `sh gradlew allStandaloneSuites` (essas tarefas não geram JAR). A tarefa padrão `test` sozinha não executa os testes independentes.
