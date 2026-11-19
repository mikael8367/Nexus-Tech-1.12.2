> Nota: este relatório documenta a análise inicial, anterior à elaboração do protótipo e à publicação; as afirmações de que nenhum arquivo foi criado se referem àquele momento. Para o estado mais recente, veja `projecte-storage-bridge-status.md`.

# Relatório preliminar: ProjectE Storage Bridge

## Estado

**Projeto não implementado e não concluído.** Esta é uma análise inicial, não uma entrega funcional. Nenhuma alteração foi feita no repositório durante esta análise; nenhum commit foi criado. Não foi gerado JAR. A análise de código não comprova o comportamento em execução.

## 1. Repositório e arquitetura examinados

- Repositório: https://github.com/mikael8367/Nexus-Tech-1.12.2
- Main observada: `618c67afb6bd226fa95314b0f4434822adc9f2cd`.
- `industrial-expansion-wip` observada: `7dbc9e7d65a3e16d0e6d8c60c47d1fcd216c8996`, descendente da main observada. Essa branch inclui trabalho industrial separado e não foi mesclada.
- `build.gradle`: Java 8, Forge 1.12.2-14.23.5.2860, mappings snapshot e ForgeGradle `3.+`. A versão exata resolvida de ForgeGradle não foi determinada.
- Registro de blocos e itens: `ModRegistry.java`; registro de tiles e GUI: `NexusTech.java`; modelos de itens: `ClientProxy.java`.
- Blocos, inventários, energia e persistência: `MachineBlock.java`, `MachineTile.java`, `MachineTileLogic.java`, `EnergyNetwork.java`. GUI/container: `MachineGuiHandler.java`, `MachineContainer.java`, `MachineGui.java`.
- Recursos: `src/main/resources/assets/nexustech/`; fontes de texturas em `src/main/texture_sources/`. Foram inspecionados os 36 JSONs e as 26 fontes PNG da main quanto a sintaxe e decodificação, não quanto a renderização em jogo.
- A main não possui as alterações industriais da WIP. Integrações futuras terão de coordenar registros, `NexusTech.java`, `ClientProxy.java`, GUI, idiomas e README sem substituir o trabalho existente.

## 2. Versões das integrações

Não há manifesto do modpack nem identificação dos JARs reais do usuário no repositório. Para investigação estática foram usadas referências específicas, **não confirmadas como as instaladas**:

- ProjectE 1.12.2 PE1.4.1: [código-fonte](https://github.com/sinkillerj/ProjectE/tree/7436b7f6c082bfd455dcafabaada0b6b0a471757), [arquivo distribuído](https://www.curseforge.com/minecraft/mc-mods/projecte/files/2702991). As assinaturas da API foram conferidas no JAR distribuído; não foi comprovado que todo o bytecode corresponde ao commit citado.
- Simple Storage Network 1.12.2 2.0.1: [código-fonte da tag](https://github.com/Lothrazar/Storage-Network/tree/261189c40a7ae88b540dac8c34919de37cfd55b8), com comparação dos pontos relevantes com a 2.0.0. Não foi executado o JAR.

## 3. APIs e limitações identificadas

### ProjectE

A Transmutation Table abre uma interface vinculada ao jogador; o conhecimento, EMC e slots são de uma capability do jogador, não um inventário compartilhado da mesa. Em `IKnowledgeProvider`, as assinaturas analisadas incluem `hasKnowledge(ItemStack)`, `getKnowledge()`, `getEmc()`, `setEmc(long)` e `sync(EntityPlayerMP)`. O `IEMCProxy` fornece `hasValue(ItemStack)`, `getValue(ItemStack)` e `getSellValue(ItemStack)`. O valor de venda pode ser diferente do de compra. A interface original também considera itens armazenadores de EMC nos slots, NBT, regras de aprendizado e eventos canceláveis. `setEmc` não é, por si só, uma operação de transmutação completa ou commit durável. Providers de jogadores offline são imutáveis na implementação analisada.

Fontes: [IKnowledgeProvider](https://github.com/sinkillerj/ProjectE/blob/7436b7f6c082bfd455dcafabaada0b6b0a471757/src/main/java/moze_intel/projecte/api/capabilities/IKnowledgeProvider.java), [IEMCProxy](https://github.com/sinkillerj/ProjectE/blob/7436b7f6c082bfd455dcafabaada0b6b0a471757/src/main/java/moze_intel/projecte/api/proxy/IEMCProxy.java), [TransmutationInventory](https://github.com/sinkillerj/ProjectE/blob/7436b7f6c082bfd455dcafabaada0b6b0a471757/src/main/java/moze_intel/projecte/gameObjs/container/inventory/TransmutationInventory.java).

### SSN

`INetworkMaster` expõe `getStacks()`, `insertStack(ItemStack, boolean)` e `request(IItemStackMatcher, int, boolean)`. O resultado de `insertStack` é a **quantidade restante**, não a aceita. Um armazenamento pode integrar-se por `IConnectableLink`, com métodos como `getStoredStacks`, `insertStack` e `extractStack`. Esses métodos de armazenamento não recebem a identidade do jogador. A lista agregada é compartilhada, e a rede também atende automação. Uma simulação é previsão, não reserva ou transação.

Fontes: [INetworkMaster](https://github.com/Lothrazar/Storage-Network/blob/261189c40a7ae88b540dac8c34919de37cfd55b8/src/main/java/mrriegel/storagenetwork/api/network/INetworkMaster.java), [IConnectableLink](https://github.com/Lothrazar/Storage-Network/blob/261189c40a7ae88b540dac8c34919de37cfd55b8/src/main/java/mrriegel/storagenetwork/api/capability/IConnectableLink.java), [RequestMessage](https://github.com/Lothrazar/Storage-Network/blob/261189c40a7ae88b540dac8c34919de37cfd55b8/src/main/java/mrriegel/storagenetwork/network/RequestMessage.java).

## 4. Bloqueios de segurança

1. **Identidade:** a API genérica de estoque SSN não repassa o jogador ao link. Expor conhecimento e EMC de uma pessoa como estoque comum poderia permitir uso por outras pessoas ou máquinas. Uma interface própria validada no servidor é uma alternativa de pesquisa, mas não cumpre automaticamente a exigência de mostrar itens virtuais na interface original da SSN. Intervenções na SSN seriam invasivas e não foram implementadas.
2. **Durabilidade:** dados do jogador ProjectE e inventários de terceiros podem ser salvos em momentos diferentes. Uma queda entre remoção de item e crédito de EMC, ou entre débito e entrega, pode persistir apenas um lado. `markDirty`, sincronização, um lock em memória ou um diário de intenções isolado não garantem commit atômico entre participantes. Não foi demonstrado um protocolo que garanta recuperação sem perda nem duplicação para inventários arbitrários. Uma integração teria de restringir participantes ou bloquear e sinalizar operações de estado incerto, sem prometer recuperação automática não demonstrada.
3. **Semântica de transmutação:** uma alteração simples de `setEmc` não reproduz necessariamente os mecanismos e regras da mesa. A API pública analisada não oferece um método que efetue atomicamente toda a transação de entrada ou saída da mesa.
4. **Versões desconhecidas:** não é possível confirmar compatibilidade binária com os arquivos realmente usados sem identificá-los.

Por essas razões, **nenhuma operação de EMC ou transferência foi habilitada**; não existe cabo ou GUI nesta entrega. Isso evita colocar implementação incompleta ou insegura na main.

## 5. Riscos levantados na leitura

### SSN: possível perda com matcher permissivo

- Arquivo/classe/método: `CapabilityConnectableLink.java`, `CapabilityConnectableLink.extractStack` na versão analisada.
- Causa observada na leitura: a extração real acontece antes da comparação com a primeira pilha; se uma pilha passar no filtro, mas não empilhar com a primeira, o fluxo pode continuar sem incluí-la no retorno.
- Hipótese para reproduzir: configurar um filtro permissivo que aceite duas variantes com NBT diferente, solicitar quantidade que atravesse ambos os slots e comparar os inventários e a pilha retornada.
- Status: **não reproduzido em jogo**; não foi aplicada correção ao mod externo nem testada correção.
- Fonte: [implementação](https://github.com/Lothrazar/Storage-Network/blob/261189c40a7ae88b540dac8c34919de37cfd55b8/src/main/java/mrriegel/storagenetwork/capabilities/CapabilityConnectableLink.java#L151-L182).

### ProjectE: relato externo de EMC debitado sem entrega

- Arquivo/classe/método relevante: `TransmutationContainer.java`, fluxo de transferência por shift-click. Há um relato público específico à versão PE1.4.1.
- Causa potencial relatada/observada na leitura: o fluxo pode debitar e não tratar todo o restante de uma inserção.
- Hipótese para reproduzir: testar shift-click de saída com inventário sem espaço ou inserção parcial e comparar saldo e itens.
- Status: **não reproduzido nesta análise**; nenhuma correção aplicada nem reteste.
- Fonte: [issue do ProjectE](https://github.com/sinkillerj/ProjectE/issues/2043).

## 6. Relatório de integridade

| Teste | Resultado honesto |
|---|---|
| Perda de itens | Não executado |
| Duplicação de itens | Não executado |
| EMC duplicado | Não executado |
| EMC perdido | Não executado |
| Transações simultâneas | Não executado |
| Reinicialização durante operações | Não executado |
| Recuperação de transações | Não executado |
| Integridade do NBT | Não executado |

Também não foram executados colocação/quebra de cabo, reconexão, consulta e transferência no jogo, falta de EMC, conhecimento ausente, multiplayer, remoção de dependências, testes de estresse, compatibilidade com Mekanism/Extreme Reactors ou servidores reais. Não existe implementação para submetê-la a esses testes. Análise estática não significa aprovação.

## 7. Compilação, implementação e commits

- Classes da ponte criadas: nenhuma.
- Arquivos criados no repositório: nenhum. Arquivos modificados no repositório: nenhum.
- GUI, adaptadores, cabo, configuração e operações: não implementados.
- APIs citadas acima foram estudadas, **não utilizadas em implementação**.
- Compilação: uma tentativa de build da main em ambiente isolado falhou por falta de espaço temporário; não há compilação aprovada. Nenhum JAR foi produzido para esta entrega.
- Testes no Minecraft e auditoria dinâmica: não executados.
- Bugs corrigidos: nenhum; hipóteses externas documentadas acima.
- Commits nesta tarefa: nenhum.

## 8. Caminho de investigação pendente

1. Identificar os nomes e versões dos JARs ProjectE e SSN realmente instalados, ou obter o manifesto do modpack.
2. Projetar um fluxo que autentique o jogador sem expor seu saldo à rede compartilhada, decidindo se uma interface própria é aceitável.
3. Especificar persistência, recuperação e o comportamento conservador quando não for possível provar o estado da transação. Provar as garantias com injeção de falhas, sem supor atomicidade entre mods.
4. Estabelecer dependências opcionais, build Java 8, testes de inicialização com e sem mods, e testes reais no Forge com inventários parciais, múltiplos jogadores e NBT.
5. Somente depois implementar e integrar sem remover recursos existentes, auditar, testar e registrar os commits autorizados.
