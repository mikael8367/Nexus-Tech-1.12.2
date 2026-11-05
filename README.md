# ProjectE Storage Bridge

Mod de integração entre ProjectE e Simple Storage Network para Minecraft Java 1.12.2.

## Recursos no código

- Cabo de rede próprio derivado de `BlockCable` e `TileCable` da SSN; não precisa de Storage Link.
- Mantém o comportamento herdado de renderização/conexão do cabo da SSN, sem forçar um modelo de bloco genérico.
- O cabo entra na rede SSN; a Storage Request Table e os Remotes acessam o inventário virtual como qualquer outro nó de armazenamento.
- Capability de armazenamento da SSN para expor o inventário virtual.
- Lista de itens aprendidos no ProjectE, limitada a variantes com EMC positivo.
- Retirada virtual com débito de EMC no servidor.
- O saldo é consultado no jogador conectado e a ponte fecha com segurança quando não consegue identificar um único jogador.
- O bloco não tenta conectar fisicamente à Transmutation Table: a tabela é uma interface do ProjectE; o vínculo usa o conhecimento e o saldo EMC do jogador.
- Simulação sem cobrança e bloqueio de saldo insuficiente.
- Proteção contra extrações concorrentes: a retirada é sincronizada, só executa na thread principal do servidor e confirma o débito antes de devolver itens à SSN.
- Eleição determinística de um único cabo principal por rede SSN: colocar vários cabos da ponte na mesma rede não multiplica a quantidade virtual exibida nem cria várias cópias da mesma fonte EMC.
- Revalidação do saldo imediatamente antes do débito; falha fechada se o saldo mudar ou não puder ser confirmado.
- Testes adicionais para saldo exato, limites de pilha e valores extremos de EMC.
- Cabo disponível na aba Redstone e receita de fabricação.
- Configuração local para ativar/desativar a ponte e limitar quantas variantes são listadas.
- Traduções em português do Brasil e inglês.
- Testes unitários do cálculo de retirada.

## Limitação multiplayer

A API pública `IConnectableLink.extractStack(...)` da SSN 1.8.3 não informa quem solicitou o item. Por segurança, a ponte só funciona quando existe exatamente um jogador conectado. Com dois ou mais jogadores, ela não disponibiliza o inventário. Não existe opção para desativar essa proteção.

## Versões

- Minecraft 1.12.2
- Forge de execução mínimo: 14.23.5.2864
- Java 8
- ProjectE 1.12.2-PE1.4.1 (CurseForge file 2702991)
- Simple Storage Network 1.12.2-1.8.3 (CurseForge file 3655827)

O ambiente de compilação usa Forge 14.23.5.2847 porque o ForgeGradle 2.3 espera o artefato antigo `userdev`; versões posteriores publicam `userdev3`, incompatível com ForgeGradle 2.3. O mod mantém Forge 14.23.5.2864 como requisito mínimo de execução. Essa compatibilidade binária ainda precisa ser validada em jogo.

## Configuração

O arquivo `config/projectestoragebridge.cfg` é criado automaticamente.

- `enableVirtualStorage=true`: ativa ou desativa o armazenamento virtual.
- `maxListedItemTypes=256`: limita a quantidade de variantes exibidas (1–4096).

## Compilação e testes

O GitHub Actions compila com Java 8 e Gradle 4.10.3. O JAR só deve ser considerado pronto após o workflow passar e os testes de integração no Minecraft real serem concluídos.

Ainda precisam ser testados dentro do jogo: retirada pela Request Table e Remote, NBT/metadata, saldo insuficiente, rede com vários cabos da ponte, recarga de chunk do cabo principal, salvar/reabrir mundo, servidor dedicado e renderização/conexão em diferentes configurações gráficas. O sucesso da compilação não substitui esses testes. Nenhum mod consegue prometer ausência absoluta de duplicações sem testes no jogo e auditoria do fluxo completo da SSN; estas proteções evitam os principais caminhos de extração grátis/concorrente no código da ponte.


## Ambiente do Minecraft e runtime

- Perfil do jogo: Minecraft Java Edition 1.12.2 + Forge 14.23.5.2864.
- Runtime de execução esperado: Java 8 (1.8), preferencialmente 64 bits em PC.
- ProjectE e Simple Storage Network são dependências obrigatórias e devem estar na pasta `mods`.
- O runtime Java não é incluído no JAR: ele é fornecido e selecionado pelo launcher. Em Android, a compatibilidade depende do runtime e da arquitetura suportados pelo launcher.
- `instance/manifest.json` registra a versão do Minecraft, Forge e os dois mods necessários.
- Consulte `docs/MINECRAFT_1.12.2_RUNTIME.md` para instalação e configuração do runtime.
