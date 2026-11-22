# Novidades experimentais do reator

Este é um conjunto de alterações sobre o snapshot de recuperação de água em `docs/experimentos/reator-controle-agua/recuperacao-agua-experimental-20261122`. Não substitui o código ativo do projeto e não é uma distribuição pronta.

## Painel
- Barras para energia, calor e água; calor muda de cor conforme o nível.
- As dicas exibem percentuais. Qualquer valor positivo abaixo de 1% aparece como `<1%` e tem pelo menos um pixel na barra. Valores numéricos continuam disponíveis no painel.
- O botão de água atualmente controla a proteção, não a presença da água; sua legenda é ambígua e precisa ser melhorada. O motivo de SCRAM não é mantido; o painel não deve inferir a causa. Ainda não foi verificada visualmente no Minecraft a compatibilidade de textos, resolução e sincronização das metades dos números.

## Reserva
- Consultas internas geram retrato imutável por posição com quantidade em long e número de entradas, e simulam espaço disponível, transferência e saldo; não recuperam água e não estão conectadas à GUI. O resultado pode ficar desatualizado e não verifica permissões, chunks, thread nem persistência. A obtenção do armazenamento pode alterar seu estado; só os métodos de consulta são não mutantes.
- NBT inválido pode fazer coordenadas ausentes virarem zero ou descartar quantidades inválidas. Leitura reentrante durante entrega de itens é um risco condicional ainda não reproduzido. Listas muito grandes podem degradar o desempenho. Nenhum desses riscos foi corrigido aqui.
- Crash, falha de markDirty e gravação separada ainda podem perder ou duplicar água ou itens. A recuperação não verifica propriedade específica.

## Verificação
- Java 8: compilaram-se e testaram-se juntas as fontes nucleares atuais em ambiente isolado, usando dependências e classes de uma build anterior: 17 suítes passaram. Depois de uma revisão, foram recompiladas a GUI e as alterações de teste e passaram os testes NuclearGuiMeterTest e NuclearWaterDiagnosticsTest. O teste de medições percorreu limites e 100 mil pares aleatórios.
- Testes Python de layout: 4 passaram após a revisão. Na rodada integrada anterior, 16 testes passaram e 1 teste da textura vanilla foi ignorado porque o cliente não estava configurado.
- Não houve build completo, JAR, teste visual ou teste real em Minecraft. Rede de líquidos não foi ativada. Não aplicar o snapshot diretamente ao mod ativo sem integração e validação cuidadosas.
