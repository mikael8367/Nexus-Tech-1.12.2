# Revisão independente da variante transacional

Resultado: NÃO INTEGRAR. Código experimental isolado; sem build ou teste Forge/Minecraft reais. Leitura independente apontou os seguintes pontos ainda em aberto, não reproduzidos em Forge:

1. Se o reator já está ligado e deixa de ter combustível, `setEnabled(true)` pode desligá-lo sem atualizar o indicador ou chamar `markDirty` (NuclearController.java:397–404, 420). A revisão anterior notificava o desligamento.
2. Ativar o controle automático com o reator ligado e sem água não o desliga imediatamente; o SCRAM ocorre no próximo update (linhas 54–57, 462–466).
3. Setters do controle automático não verificam `stopping`; callbacks durante parada podem alterar a configuração automática (linhas 45–57).
4. Possível perda de combustível se `inventory.extractItem` remover o item antes de `onContentsChanged`/`markDirty` lançar uma exceção: o retorno não é recebido e `fuelTicks` não é atribuído. Hipótese a confirmar no Forge real; stubs não demonstram durabilidade nem semântica real do Forge.
5. Uma exceção no indicador pode impedir a notificação de gravação. Não há prova de persistência do SCRAM após crash ou de segurança perante acesso direto a campos públicos.

As correções parciais e testes estão descritos em RELATORIO-TRANSACAO.md e RELATORIO-REVISAO.md. A falha do teste original AutoThermalTest em 999 permanece registrada, não deve ser apagada nem tratada como sucesso. Testes com trechos extraídos e stubs não provam segurança no jogo.
