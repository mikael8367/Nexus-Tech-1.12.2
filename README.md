# ProjectE Storage Bridge

![Capa do ProjectE Storage Bridge](docs/projecte-storage-bridge-cover.svg)

Mod de integração entre ProjectE e Simple Storage Network para Minecraft Java 1.12.2.

## Recursos no código

- Cabo próprio derivado de `BlockCable` e `TileCable` da SSN, conectado diretamente à rede.
- Renderização do cabo registrada no TESR oficial da SSN, com textura própria 32x32 e conexões visuais dinâmicas.
- O cabo precisa ficar encostado em um bloco ProjectE com registro `projecte:transmutation_table`; a ponta especial do cabo é renderizada na direção da mesa.
- A Storage Request Table e os Remotes acessam o inventário virtual pela rede SSN.
- A mesa do ProjectE 1.12.2 é um bloco de interface sem TileEntity/inventário próprio. O EMC e o conhecimento pertencem ao jogador; o cabo exige a mesa adjacente e consulta a capability ProjectE desse jogador.
- Lista de itens aprendidos no ProjectE, limitada a variantes com EMC positivo.
- Retirada virtual com débito de EMC no servidor.
- O saldo é consultado no jogador conectado e a ponte fecha com segurança quando não consegue identificar um único jogador.
- Simulação sem cobrança e bloqueio de saldo insuficiente.
- Proteção contra extrações concorrentes: a retirada é sincronizada, só executa na thread principal do servidor e confirma o débito antes de devolver itens à SSN.
- Eleição determinística de um único cabo principal por rede SSN: colocar vários cabos da ponte na mesma rede não multiplica a quantidade virtual exibida nem cria várias cópias da mesma fonte EMC.
- Revalidação do saldo imediatamente antes do débito; falha fechada se o saldo mudar ou não puder ser confirmado.
- Cabo disponível na aba Redstone e receita de fabricação.
- Traduções em português do Brasil e inglês.
- Testes unitários do cálculo de retirada.

## Como conectar

1. Coloque a Transmutation Table do ProjectE.
2. Coloque o Cabo de Integração EMC encostado em qualquer uma das seis faces da mesa.
3. Conecte o outro lado do cabo à rede do Simple Storage Network usando os cabos normais da SSN.
4. Abra a Storage Request Table ou um Remote da SSN. Os itens aprendidos serão listados e as retiradas cobrarão EMC do jogador.

A mesa não é transformada em inventário nem recebe EMC próprio: no ProjectE 1.12.2 ela abre a interface de transmutação do jogador. A ligação física é detectada pelo bloco real adjacente e o cabo usa a capability de conhecimento/EMC do jogador.

## Limitação multiplayer

A API pública `IConnectableLink.extractStack(...)` da SSN 1.8.3 não informa quem solicitou o item. Por segurança, a ponte só funciona quando existe exatamente um jogador conectado. Com dois ou mais jogadores, ela não disponibiliza o inventário. Não existe opção para desativar essa proteção.

## Versões

- Minecraft 1.12.2
- Forge de execução mínimo: 14.23.5.2860
- Java 8
- ProjectE 1.12.2-PE1.4.1 (CurseForge file 2702991)
- Simple Storage Network 1.12.2-1.8.3 (CurseForge file 3655827)

O ambiente de compilação usa Forge 14.23.5.2847 apenas para obter o artefato legado `userdev` exigido pelo ForgeGradle 2.3. O mod declara Forge 14.23.5.2860 como versão mínima de execução. Como a compilação usa uma versão anterior do Forge, a compatibilidade em 2860 ainda precisa ser confirmada em teste real.

## Configuração

O arquivo `config/projectestoragebridge.cfg` é criado automaticamente.

- `enableVirtualStorage=true`: ativa ou desativa a ponte.
- `maxListedItemTypes=256`: limita quantas variantes são listadas (1–4096).

## Compilação e testes

O GitHub Actions compila com Java 8 e Gradle 4.10.3. O JAR só deve ser considerado pronto após o workflow passar e os testes de integração no Minecraft real serem concluídos.

Ainda precisam ser testados dentro do jogo: conexão visual com a mesa e com os cabos SSN, retirada pela Request Table e Remote, saldo insuficiente, NBT/metadata, rede com vários cabos da ponte, recarga de chunk do cabo principal, salvar/reabrir mundo, servidor dedicado e renderização em diferentes configurações gráficas. O sucesso da compilação não substitui esses testes. Nenhum mod consegue prometer ausência absoluta de duplicações sem testes no jogo e auditoria do fluxo completo da SSN.

## Ambiente do Minecraft e runtime

- Perfil do jogo: Minecraft Java Edition 1.12.2 + Forge 14.23.5.2860.
- Runtime de execução esperado: Java 8.
- ProjectE e Simple Storage Network são dependências obrigatórias e devem estar na pasta `mods`.
- O runtime Java é fornecido pelo launcher. Em Android, a compatibilidade depende do runtime e da arquitetura suportados pelo launcher.
- `instance/manifest.json` registra a versão do Minecraft, Forge e os dois mods necessários.
- Consulte `docs/MINECRAFT_1.12.2_RUNTIME.md` para instalação e configuração do runtime.
