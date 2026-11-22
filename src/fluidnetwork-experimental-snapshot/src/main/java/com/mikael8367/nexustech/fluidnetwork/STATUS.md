# Rede de líquidos: em desenvolvimento, não habilitar

Tanque: armazenamento de 16.000 mB, IFluidHandler, validação e quarentena de NBT, cópias defensivas e acesso limitado à thread do servidor e tile carregado. Ainda não existe bloco protegido contra todas as formas de remoção, nem proteção contra falhas entre salvamentos. A releitura do tile agora lança exceção e precisa ser revisada contra o ciclo de vida Forge e sincronização de atualização. Não registrar até resolver e testar.

Tubos: inspeção de caminhos com distância limitada a 256 tubos e apenas chunks carregados. Resultado incompleto é recusado. Ainda não há transferência de líquidos.

Bomba: estrutura FE inicial, validação e quarentena de NBT; não tem tick nem transfere líquidos. Não disponibilizar: quebrá-la pode perder energia armazenada.

Recursos: modelos, receitas propostas, texturas e fragmentos pt_br/en_us ficam em src/fluidnetwork-staged-resources, fora do build ativo. Tanque, tubo e bomba usam modelos JSON com relevo 3D; o tubo tem modelos condicionais de conexão. Verificação estática não substitui teste no jogo; integração exige revisão de orientação, iluminação e colisão. Não integrar ou anunciar até implementar e verificar o comportamento.

Conexões visuais de tubos: estados derivados e modelos multipart para seis lados, conectando apenas tubos e bombas próprios. getActualState agora lê o ChunkCache exato do Forge 1.12.2 sem carregar chunks durante a consulta; acessos desconhecidos permanecem desconectados. Teste Java isolado cobriu 64 combinações no World simulado e no ChunkCache real, fronteiras, Y, falhas e ausência de tiles. O tubo não é mais cubo opaco/completo para renderização, mas a colisão ainda é um cubo cheio. Ainda não houve teste no jogo; renderizadores de terceiros, faces coincidentes entre tubos e folgas na bomba precisam de revisão. Não ativar.

Transações: protótipo isolado FluidJournal mantém saldos e histórico em imagem única, com operações idempotentes e rejeição de serialização não canônica. FluidJournalTest passou 11.934 verificações isoladas. Um teste demonstra que alterar o ID da operação pode ser aceito e permitir repetição; o journal não tem proteção de integridade nem ancoragem. Não grava de forma durável, não foi integrado ao jogo, não detecta rollback para imagem antiga e não prova segurança contra crash real. Ver transaction/README.md para limites e pré-requisitos.

Testes isolados em tmpfs com Java 8 e classpath Forge 1.12.2: SafeFluidTankTest, FluidTankTileTest e FluidNetworkSafetyTest compilados em conjunto e aprovados. As exceções de notificação pós-commit são induzidas nos testes. Isso não é compilação completa do mod nem teste dentro do Minecraft. Houve avisos sobre Unsafe, API obsoleta e prefixos Forge no harness. Verificações estáticas de 12 JSON, 4 PNG e 2 arquivos de idioma preparatórios passaram.

Pendências: implementar transferência entre tanques com proteção de identidade, persistência, crash, remoção e reentrância; definir política para handlers de outros mods; criar bloco seguro de tanque e registros; integrar recursos e guia apenas quando seguros; testar chunks, quebra, salvamento e handlers adversariais; compilar mod completo e testar em instância real. Duas chamadas markDirty não são transação durável. Balde tecnológico: caminho para tanques de terceiros ainda exige análise transacional e de permissões.

Disco do workspace ~258 MiB livres em 22/11/2026; /tmp é tmpfs separado e também consome memória. Não criar release/publicar; não excluir versões nem dados. Reavaliar espaço com df antes de build ou cópia.
