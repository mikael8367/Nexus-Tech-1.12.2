# Nexus Tech

Mod de tecnologia para Minecraft Java **1.12.2** com Forge. A implementação atual adiciona seis blocos que geram, armazenam, transportam e consomem **Forge Energy (FE)**.

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

- **Painel solar:** a dimensão precisa ter iluminação de céu, ser dia, não estar chovendo nem trovejando e o bloco acima precisa estar carregado, com visão do céu e luz do céu de pelo menos 15.
- **Gerador:** coloque no espaço de combustível qualquer item com tempo de queima válido para a fornalha. A duração segue o tempo de queima do item; a queima pausa enquanto o armazenamento está cheio. Combustíveis com item recipiente exigem que haja apenas um item no espaço ao iniciar a queima, para que o recipiente possa ser devolvido.
- **Turbina:** basta um dos seis blocos adjacentes carregados ser água vanilla (`water` ou `flowing_water`) com `LEVEL > 0`. Água-fonte (`LEVEL = 0`) sozinha não ativa a turbina; não é necessário consumir a água.
- **Fornalha elétrica:** aceita receitas de fundição registradas na fornalha. Consome **20 FE por tick de trabalho** durante **200 ticks**, ou **4.000 FE por operação**. Sem energia suficiente, o progresso pausa; trocar o tipo ou os dados da entrada, ou ficar sem uma receita/saída válida, zera o progresso. A interface tem espaços de entrada e saída.

Clique com o botão direito nos blocos, exceto o cabo, para abrir suas interfaces e consultar a energia armazenada. A interface do gerador também mostra a queima e a da fornalha, o progresso. Os seis blocos aparecem na aba Redstone do criativo.

## Interligação e limites

Coloque geradores, bateria e fornalha diretamente adjacentes ou conecte-os por cabos, inclusive em ramificações. A conexão considera as seis faces. Por exemplo: painel solar → cabos → bateria → fornalha elétrica. Os blocos usam a capacidade Forge Energy e podem interagir com outros blocos que a exponham em uma face compatível.

Os geradores enviam automaticamente até **100 FE/tick** cada, e a bateria até **128 FE/tick**, quando há energia armazenada e um receptor aceita. A entrada ou extração FE nas máquinas com armazenamento é limitada a **1.000 FE por chamada**, não necessariamente por tick. Os cabos não têm armazenamento próprio. A distribuição não promete divisão igual entre receptores.

**Baterias não alimentam outras baterias na distribuição automática**, nem diretamente nem pela rede de cabos. Elas podem receber energia dos geradores e alimentar a fornalha ou outros receptores compatíveis. A simulação de máquinas e as transferências de energia ocorrem no **servidor**; posições não carregadas não são percorridas pela rede. Para multiplayer, use o mod no servidor Forge e nos clientes.

## Receitas

As receitas são moldadas e podem ser feitas na interface de uma bancada de trabalho 3×3. Não há advancements de desbloqueio de receitas incluídos; portanto, não se pressupõe que apareçam no livro de receitas. `.` indica espaço vazio. Cada receita produz um bloco, salvo indicação contrária.

Legenda: `I` = barra de ferro; `R` = redstone; `G` = vidro; `D` = sensor de luz solar; `C` = carvão (`minecraft:coal`); `F` = fornalha; `P` = pistão; `O` = barra de ouro; `B` = bloco de redstone.

| Resultado | Grade 3×3, de cima para baixo |
| --- | --- |
| `energy_cable` ×8 | `III` / `RRR` / `III` |
| `solar_panel` | `GGG` / `IDI` / `RIR` |
| `coal_generator` | `IRI` / `CFC` / `.I.` |
| `electric_turbine` | `IRI` / `IPI` / `.I.` |
| `battery` | `IRI` / `ROR` / `IRI` |
| `electric_furnace` | `IRI` / `RFR` / `IBI` |

## Requisitos e desenvolvimento

- Minecraft Java **1.12.2** com Forge **14.23.5.2860** para executar o mod.
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
