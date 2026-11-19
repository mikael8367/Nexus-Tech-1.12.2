# ProjectE Storage Bridge — estado do protótipo

**Incompleto. Não usar como integração funcional.** A implementação não conecta a bancada à rede SSN e não movimenta itens nem EMC.

## Versões identificadas
A captura enviada pelo usuário mostra ProjectE `1.12.2-PE1.4.1` e Simple Storage Network `1.12.2-2.0.1`. A análise do código utilizou essas versões. Não foi verificada a identidade do conteúdo dos JARs do usuário por hash.

## Progresso
Foi criado um bloco experimental que abre GUI própria de diagnóstico e identifica IDs de blocos adjacentes e mods carregados. Proximidade não comprova conexão real. As versões, blocos e estados são apresentados em modo diagnóstico. Foi preparado código experimental para leitura do ProjectE, mas está deliberadamente bloqueado por `INTEGRATION_VERIFIED = false`, pois a injeção de capability e a implementação real ainda não foram testadas. Não lê EMC nem conhecimento em execução normal.

O código não conecta rede, não fornece inventário, não transfere, converte, credita ou debita recursos. Não tem receita, e o modelo não apresenta conexões dinâmicas. É separado da rede Forge Energy existente.

## Alterações locais
Base: main `618c67afb6bd226fa95314b0f4434822adc9f2cd`. Adições: classes `BridgeBlock`, `BridgeContainer`, `BridgeDiagnostics`, `BridgeGui`, `BridgeTile`, `ProjectEReadOnly`; modelos, blockstate e texturas da ponte; testes `BridgeDiagnosticsTest`, `ProjectEReadOnlyTest`. Modificados: `README.md`, `build.gradle`, `ClientProxy`, `MachineGuiHandler`, `ModRegistry`, idiomas `en_us` e `pt_br`. Veja o pacote de código para as alterações completas. O histórico Git documenta a publicação deste protótipo. Nenhum JAR foi gerado.

## Verificação feita
`gradle --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m check -x jar -x reobfJar` com Java 8 e cache de ForgeGradle disponível: `BUILD SUCCESSFUL`; oito suítes standalone passaram, incluindo diagnóstico, testes com provedor ProjectE simulado e seis suítes preexistentes. Isso não é teste com ProjectE ou SSN reais. Uma tentativa anterior com cache errado falhou por ausência do ForgeGradle no cache; com o cache apropriado passou. Nenhum JAR foi construído.

## Auditoria e riscos
- O protótipo NÃO implementa o requisito principal. Não deve ser publicado como ponte pronta.
- A tabela ProjectE não é baú: converte itens em EMC e conhecimento pertencentes a um jogador. A rede SSN tem estoque físico e sua API genérica não fornece a identidade do jogador a cada operação.
- Conversão de itens recebidos por qualquer parte da SSN e cobrança por retiradas externas não foram implementadas; podem ocorrer por automação e sem identidade segura.
- Dados do jogador e inventários podem ser salvos em tempos diferentes. Diário, lock e simulação, isoladamente, não provam recuperação atômica após crash. A conversão precisa permanecer bloqueada enquanto o requisito de integridade não for tratado e testado.
- Não foram executados testes em cliente ou servidor Minecraft, testes com os JARs reais, transferência, falhas, simultaneidade, multiplayer, interrupção ou reinicialização. Nada disso foi aprovado.
- Na auditoria foram corrigidos um centro visual potencialmente vazado no modelo e envio duplicado de propriedades ao abrir a GUI; a aparência e sincronização ainda não foram testadas no jogo.

## Próximos passos
Investigar e implementar conexão real com ProjectE e SSN, autenticação de jogador e semântica explícita de estoque físico versus transmutável, conversão e criação legítimas, e recuperação para operações que cruzem persistências. Assegurar que limites impossíveis de garantir sejam claros e bloquear operações em estado incerto. Testar cliente e servidor com os mods exatos, e executar testes de integridade e estresse reais antes de liberar.
