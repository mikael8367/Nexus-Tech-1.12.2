# Nexus Tech

Mod de tecnologia para Minecraft Java **1.12.2** com Forge. A implementação inclui máquinas **Forge Energy (FE)**, minérios, mineração digital e um reator nuclear.

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

Clique com o botão direito nos blocos, exceto o cabo, para abrir suas interfaces e consultar a energia armazenada. As interfaces dos geradores mostram a queima; as da fornalha, do triturador e do compressor mostram o progresso. Esses nove blocos aparecem na aba Nexus Tech do inventário criativo, junto com os itens e blocos dos novos módulos.

## Interligação e limites

Coloque geradores, bateria e máquinas consumidoras diretamente adjacentes ou conecte-os por cabos, inclusive em ramificações. A conexão considera as seis faces. Por exemplo: painel solar → cabos → bateria → fornalha elétrica, ou gerador de lava → triturador.

**Integração com outros mods:** as máquinas usam a capacidade **Forge Energy (FE)** e podem receber ou fornecer FE para blocos de outros mods que exponham a capacidade apropriada na face conectada. Por exemplo, uma fonte externa que envie FE pode alimentar a bateria, a fornalha, o triturador ou o compressor; um receptor externo de FE pode receber energia dos geradores ou da bateria. A conexão depende do comportamento do mod conectado, das faces, dos limites e da forma de transferência (enviar ou extrair): não há promessa de compatibilidade universal. Alguns sistemas chamados **RF** também podem funcionar quando seus blocos expõem FE; o nome RF sozinho não garante compatibilidade. Não há suporte ou conversão garantidos para **EU**, **Tesla** ou **RF legado** sem uma ponte compatível.

Os cabos aceitam FE enviada para eles e a distribuem aos receptores. A integração de extração (*pull*) pelos cabos depende da implementação da versão utilizada e do comportamento do mod conectado; não presuma que qualquer fonte ou consumidor funcione com ela.

Os geradores enviam automaticamente até **100 FE/tick** cada, e a bateria até **128 FE/tick**, quando há energia armazenada e um receptor aceita. A entrada ou extração FE nas máquinas com armazenamento é limitada a **1.000 FE por chamada**, não necessariamente por tick. Os cabos não têm armazenamento próprio. A distribuição não promete divisão igual entre receptores.

**Política conservadora da bateria:** no envio automático, a bateria ignora qualquer bloco que exponha extração FE em alguma face, inclusive armazenamentos e consumidores bidirecionais vazios. `IEnergyStorage` não informa se um bloco bidirecional é uma fonte, armazenamento ou consumidor passivo: permitir envio a todos pode devolver a energia que a bateria acabou de extrair, inclusive em ticks diferentes. A bateria ainda puxa energia desses blocos e envia para receptores que não exponham extração; um consumidor bidirecional que não extraia ativamente pode não ser alimentado por ela. Se o consumidor fizer *pull*, pode extrair da bateria diretamente ou pela face conectada de um cabo. Geradores Nexus podem enviar a receptores bidirecionais. A ordem de visita é fixa: não há garantia de prioridade aos geradores nem de distribuição justa entre fontes ou receptores. Essa proteção depende de `canExtract()` refletir corretamente cada face; redes externas com armazenamento compartilhado entre blocos distintos podem criar ciclos que o mod não consegue identificar.

**Baterias não alimentam outras baterias na distribuição automática**, nem diretamente nem pela rede de cabos. Elas podem receber energia dos geradores e alimentar a fornalha, o triturador, o compressor ou outros receptores compatíveis. A simulação de máquinas e as transferências de energia ocorrem no **servidor**; posições não carregadas não são percorridas pela rede. Para multiplayer, use o mod no servidor Forge e nos clientes.

## Receitas

As receitas dos nove blocos acima são moldadas e podem ser feitas na interface de uma bancada de trabalho 3×3. Não há advancements de desbloqueio de receitas incluídos; portanto, não se pressupõe que apareçam no livro de receitas. `.` indica espaço vazio. Cada receita produz um bloco, salvo indicação contrária.

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

## Minérios e aço

No Overworld (dimensão 0), o código prevê dez minérios em chunks novos, substituindo pedra vanilla, sem retrogen ou carregamento intencional de chunks vizinhos. Cada minério fundido deve produzir um lingote e 0,7 XP; essa integração ainda precisa de teste no jogo. Exige picareta de nível adequado e deixa o próprio bloco: nível 1 cobre, estanho, alumínio e zinco; nível 2 níquel, prata, chumbo, urânio e titânio; nível 3 tungstênio. As entradas pretendidas incluem `ore...` e `ingot...`, com aliases `oreAluminium`/`ingotAluminium` e `ingotSteel`.

Os registros no OreDictionary e as receitas de fornalha são realizados na inicialização, após os eventos Forge de registro de blocos e itens. A ordem corrigida ainda precisa ser confirmada numa inicialização completa do jogo.

Geração: tentativas por chunk / passos máximos por tentativa / faixa de Y inclusiva: cobre 12/8/40–96; estanho 9/7/32–80; alumínio 10/8/32–96; zinco 8/7/24–72; níquel 6/6/8–48; prata 5/5/5–40; chumbo 7/6/8–48; urânio 3/4/4–28; titânio 3/5/4–24; tungstênio 2/4/4–20. Passos podem se repetir e blocos não elegíveis não são substituídos.

A receita inicial de aço faz 4 lingotes de aço com quatro `ingotIron` em cruz ao redor de carvão mineral vanilla (não carvão vegetal). Não exige forno ou energia; é um balanceamento inicial.

## Mina digital

Monte uma caixa maciça de **3 blocos de largura × 2 de altura × 3 de profundidade**, com **17 revestimentos** `digital_mine_casing` e um controlador `digital_mine_basic` ou `digital_mine_advanced` no centro inferior da frente, voltado para fora. As quatro direções horizontais são válidas. A estrutura não altera blocos ao montar; ausência ou descarregamento de peça pausa o funcionamento. A verificação também exige chunks de possíveis controladores sobrepostos carregados, mesmo se as 18 peças estiverem carregadas; estruturas válidas sobrepostas são desempatadas pela posição. O jogador que coloca o controlador é o proprietário e somente ele pode abrir/operar a interface; sem proprietário não minera. Ao quebrar o controlador, o inventário é encaminhado para recuperação e geração de itens; quebrar revestimentos não remove os demais.

| Variante | Energia armazenada | Raio | Modo econômico | Modo rápido |
| --- | --- | --- | --- | --- |
| Básica | 40.000 FE | 8 | 1.200 FE/minério, intervalo 41 ticks | 2.400 FE/minério, intervalo 21 ticks |
| Avançada | 120.000 FE | 12 | 1.800 FE/minério, intervalo 21 ticks | 3.600 FE/minério, intervalo 11 ticks |

Recebe FE em qualquer face até 1.000 por chamada e puxa até 1.000 FE/tick de vizinhos/cabos. Tem 27 slots de saída; a capacidade de itens é somente extração. Após ligar e ter estrutura e energia, pesquisa a partir do Y abaixo do controlador até Y=1, por camadas e até 128 posições por tick; o cursor é persistido e retorna ao início após percorrer a área. Posições em chunks descarregados são puladas sem carregá-los: poderão ser verificadas em uma volta posterior, se estiverem carregadas. Só aceita bloco com item associado e entrada OreDictionary cujo nome começa com `ore` e contém mais caracteres; não minera blocos com tile entity, dureza negativa ou maior que 50, ou fora da borda. Se os drops selecionados não couberem, não remove nem cobra energia. Uma lista vazia de drops não impede a remoção e cobrança de energia.

A máquina usa FakePlayer com UUID do dono, verificação `isBlockModifiable` e `BreakEvent` cancelável postado diretamente. Obtém drops com fortuna zero e emite o evento Forge de colheita, que pode alterar a lista e a chance; não aplica toque suave e não chama `harvestBlock`. Após remover o bloco, gera XP apenas se o `BreakEvent` fornecer uma quantidade positiva; isso não garante o XP normal de todo minério. Não há garantia de compatibilidade com todos os mods de proteção ou callbacks arbitrários. Itens excedentes ou do controlador removido são encaminhados a uma fila persistida por dimensão; tentativas de geração rejeitadas são repetidas sem carregar chunks. Isso não garante entrega imediata nem transação atômica entre bloco, inventário e dados salvos após falhas abruptas; efeitos de mods que lançam exceções também não são conhecidos. Teste com os mods e sistema de proteção desejados antes de uso em produção.

## Reator nuclear

Monte uma caixa **maciça 3 × 5 × 3** (largura × altura × profundidade): controlador `nuclear_controller` no centro inferior da frente, voltado para fora; `nuclear_core` no centro (altura 2 a partir da base e profundidade 1); preencha as outras **43 posições** com `nuclear_casing`. As quatro orientações horizontais são aceitas. O entorno não precisa estar vazio. Conecte cabo ou receptor na face externa do controlador; as demais partes não fornecem energia.

O combustível `nuclear_fuel` é fabricado sem forma fixa com um `ingotUranium` do OreDictionary e um lingote de ferro vanilla. Uma unidade dura 2.400 ticks operacionais; produz 200 FE/tick em buffer de 1.000.000 FE, com saída total até 400 FE/tick. Aceita água por balde ou automação de fluidos em tanque de 8.000 mB, devolvendo balde vazio; usa 1 mB/tick quando disponível. Água não é obrigatória: atividade soma 3 de calor/tick, dissipação passiva subtrai 1 e água subtrai 4; sem água há aumento líquido de 2/tick. SCRAM térmico ocorre a 1.000, com rearme manual somente a 400 ou menos. Sem espaço no buffer, o consumo de combustível para. Se qualquer uma das 45 posições não estiver carregada, pausa combustível, água, calor e exportação sem carregar chunks. A perda ou o descarregamento da estrutura desliga o reator; se ele estava ligado, registra SCRAM. Restaurar a estrutura não o religa automaticamente: o acionamento é manual e, após SCRAM, exige calor ≤400. Ao recarregar um controlador que estava ligado, ele também volta desligado e com SCRAM, exigindo rearme manual.

Para desmontar, desligue e espere calor ≤400. A remoção por jogador de peças vinculadas é bloqueada enquanto o controlador estiver ligado ou quente e também quando o chunk dele estiver descarregado. Essa proteção não impede comandos, substituição direta do mundo, explosões arbitrárias/mods e não protege estruturas ainda não validadas; resistência alta não significa invulnerabilidade.

### Receitas dos novos blocos

As linhas são de cima para baixo; `.` é vazio. Nestas receitas, `I` é ferro vanilla e `R` é redstone (poeira), salvo quando indicado. As receitas JSON ficam em `src/main/resources/assets/nexustech/recipes/` e são carregadas pelo Forge; não há garantia de desbloqueio no livro de receitas.

| Resultado | Grade 3×3 e ingredientes adicionais |
| --- | --- |
| Revestimento digital ×4 | `IRI` / `RIR` / `IRI` |
| Mina básica | `IBI` / `DCD` / `IPI`: B bloco de redstone, D picareta de diamante, C revestimento digital, P pistão |
| Mina avançada | `DGD` / `RBR` / `CEC`: D diamante, G bloco de ouro, R bloco de redstone, B mina básica, C revestimento digital, E esmeralda |
| Revestimento nuclear ×8 | `III` / `IGI` / `III`: G vidro |
| Núcleo nuclear | `IRI` / `RDR` / `IRI`: D diamante |
| Controlador nuclear | `IRI` / `RCR` / `I.I`: C núcleo nuclear |

## Integração e testes pendentes

Os pacotes são `com.mikael8367.nexustech.ores`, `.digitalmine` e `.nuclear`. Os assinantes Forge registram automaticamente blocos, itens e modelos; não os registre novamente. `NexusTech.preInit` registra a geração de minérios; `NexusTech.init` registra OreDictionary e fundição após os eventos de blocos e itens. `DigitalMineRegistry.registerTiles()` é chamado uma vez no `preInit`. As tiles nucleares são registradas pelo assinante nuclear. Há um único `MachineGuiHandler`: ID 0 para máquinas existentes, 71 para mina e 8367 para reator. GUI cliente é encaminhada pelo `ClientProxy`, sem importação de telas cliente pelo handler comum de servidor. Idiomas en_us e pt_br incluem os três módulos. `processResources` decodifica texturas PNG dos arquivos `.png.b64` e mantém receitas e modelos JSON.

Compilar e testar isoladamente não comprova funcionamento no Minecraft. Antes de distribuir, testar em cliente e servidor dedicados reais: inicialização/IDs, GUIs, modelos/idiomas, receitas e OreDictionary, geração em mundo novo e níveis de ferramenta; mina com propriedade, chunks, proteção, drops, inventário, recarga e cabos; reator com água, combustível, SCRAM, desmontagem, chunks e FE, inclusive concorrência e mods externos. Verificar também explosões, compatibilidade e balanceamento.

### Verificação sem JAR

Com JDK 8 e dependências disponíveis, execute `sh gradlew compileJava processResources`; com cache previamente preparado pode acrescentar `--offline --no-daemon`. Isso compila código e prepara recursos, mas **não gera JAR**, não executa o jogo e não publica. O comando `build` abaixo é para quando for desejado gerar o JAR.

Para validações estáticas de integração, com Python 3 e Pillow instalados, execute `python3 src/test/java/com/mikael8367/nexustech/test_integration_assets.py`. O teste examina JSON, referências locais de modelos e texturas, arquivos PNG codificados, formatos de receitas, chaves de idioma e encaminhamento das GUIs. O teste também verifica estaticamente a ordem dos hooks de registro dos minérios. A análise não inicia Forge, não valida ingredientes vanilla ou registro efetivo, nem substitui os testes no jogo e servidor.

## Requisitos e desenvolvimento

- Minecraft Java **1.12.2** com Forge **14.23.5.2860** para executar o mod. A compilação não substitui testes no jogo e no servidor.
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
