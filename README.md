# ProjectE Storage Bridge

Mod de integração entre ProjectE e Simple Storage Network para Minecraft Java 1.12.2.

## Recursos no código

- Cabo de rede próprio derivado de `TileCable` da SSN; não precisa de Storage Link.
- Capability de armazenamento da SSN para expor o inventário virtual.
- Lista de itens aprendidos no ProjectE, limitada a variantes com EMC positivo.
- Retirada virtual com débito de EMC no servidor.
- Simulação sem cobrança e bloqueio de saldo insuficiente.
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

Ainda precisam ser testados: retirada pela Request Table e Remote, NBT/metadata, saldo insuficiente, recarga de chunk, salvar/reabrir mundo e servidor dedicado.
