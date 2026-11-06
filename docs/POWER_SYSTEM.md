# Nexus Tech — máquinas e energia

## Máquinas adicionadas
| Máquina | Função | Especificação inicial |
|---|---|---|
| Gerador a carvão | Queima carvão, carvão vegetal ou bloco de carvão | 10 FE/t; buffer 32.000 FE |
| Painel solar | Gera energia de dia, em céu aberto e sem chuva | 4 FE/t; buffer 2.000 FE |
| Cabo de energia | Armazena e encaminha FE, inclusive para cabos de outros mods | até 512 FE/t; buffer 4.000 FE |
| Forno elétrico | Usa receitas vanilla de fornalha | 10 FE/t; 200 ticks por item; buffer 12.000 FE |
| Bigorna elétrica | Repara itens com durabilidade vanilla, inclusive itens de mods que usem a API padrão | 25 FE por ponto de durabilidade; até 4 pontos/t; buffer 100.000 FE |

## Operação
1. Clique no gerador com carvão, carvão vegetal ou bloco de carvão. O combustível é guardado em ticks pendentes; o gerador pausa a queima se o buffer não tiver espaço.
2. Coloque o painel solar com céu visível, durante o dia e sem chuva.
3. Ligue geradores e painéis aos cabos; conecte os cabos às máquinas. Uma máquina também pode receber FE diretamente de um bloco adjacente.
4. Clique no forno com um item que tenha receita de fornalha para inserir. Clique com a mão vazia ou item não processável para retirar o resultado.
5. Insira uma ferramenta, arma ou armadura danificada na Bigorna Elétrica. Ela repara automaticamente e só permite retirar o item quando estiver totalmente reparado.

## Compatibilidade de energia
O módulo usa a capability Forge Energy (FE / IEnergyStorage). Máquinas consumidoras aceitam energia por conexão direta ou por cabos FE; cabos podem puxar energia de produtores FE adjacentes e expõem capability FE para outros mods. Extreme Reactors 1.12.2 possui Power Taps FE; Mekanism Universal Cables têm integração FE/RF conforme a versão e o bloco. O funcionamento depende da face, versão e configuração do mod instalado.

Sistemas isolados como EU, MJ ou Tesla sem uma capability FE compatível precisam de adaptador próprio. O módulo não converte unidades silenciosamente.

## Bigorna elétrica e itens de outros mods
A bigorna repara qualquer ItemStack danificado que use o sistema padrão de durabilidade do Minecraft. A máquina altera somente o valor de dano do item, mantendo encantamentos, nome personalizado e os demais dados NBT. Itens de mods com sistema de durabilidade completamente customizado e que não usem o campo de dano padrão não são reparáveis por esta versão.

## Segurança e persistência
- Energia, combustível pendente, inventário e progresso são salvos em NBT.
- Transferências e processamento ocorrem no servidor.
- A energia transferida é removida da origem e limitada pela capacidade do destino.
- Simulação FE é consultada antes de transferências.
- Forno só aceita receitas válidas de FurnaceRecipes e bloqueia inserção no slot de saída.
- A bigorna aceita um item danificável por vez, sem sobrescrever o NBT do item.
- Ao quebrar forno ou bigorna, os itens armazenados são derrubados no mundo.

## Checklist de testes em jogo
- Gerador: carvão, carvão vegetal, bloco de carvão, buffer cheio, combustível pendente, salvamento/reload.
- Solar: dia/noite, chuva, teto, dimensão sem céu, salvamento/reload.
- Cabos: gerador/painel → cabo → máquina, cadeias, produtor FE externo, consumidor FE externo, seis faces e ciclos.
- Forno: receitas válidas/inválidas, saída cheia, hoppers, quebra e reload.
- Bigorna: espada, ferramenta, armadura, item de outro mod com durabilidade padrão, encantamentos/NBT, item não danificável, item já reparado, falta de energia, extração automática, quebra e reload.
- Renderização: item na mão, inventário, mundo, modelos, colisões e resource packs.

A compilação e testes unitários não substituem testes dentro do Minecraft com os mods concretos instalados.
