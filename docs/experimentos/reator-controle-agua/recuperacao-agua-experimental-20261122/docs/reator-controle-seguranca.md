# Reator nuclear — controle e diagnóstico experimental

Este trabalho altera apenas o reator existente; não liga a rede experimental de líquidos e não muda as receitas, geometria, capacidade, produção ou proteção anterior. Precisa de auditoria e de testes no Minecraft antes de distribuição.

## Proteção opcional de água

- Na tela do reator, o botão **Água: SIM/NÃO** ativa ou desativa a proteção. A configuração é do reator, é salva no mundo e é desativada por padrão em mundos antigos (e reatores novos), para preservar a geração sem água existente.
- Ativa, impede ligar se o tanque estiver vazio. Se a água terminar durante a operação, ou se a proteção for ativada com o reator ligado e sem água, há parada de segurança travada (SCRAM). O último mB ainda participa do último tick gerador; em seguida o reator para. Nesse caso, a produção exibida passa a 0 mesmo no tick em que os 200 FE já foram gerados com o último mB. Não consome outro tick de combustível nem gera mais energia até ser religado.
- Reponha a água, aguarde o calor chegar a **400 ou menos** e aperte **Ligar**. O reabastecimento e a recarga de chunks não ligam automaticamente. Enquanto estiver ativada, a proteção continua impedindo reinício sem água. Desativá-la é uma escolha manual: não inicia o reator nem apaga a parada, mas permite solicitar o reinício sem água após o resfriamento exigido.
- A água de baldes é processada antes da geração em cada tick normal. O tanque não é alterado por este recurso fora do consumo já existente. Durante SCRAM, se houver água e o calor ainda estiver acima de 400 após o resfriamento passivo, o resfriamento assistido consome 1 mB naquele tick. O processamento ainda pausa se faltar chunk da estrutura, conforme o comportamento anterior.
- Isto é uma proteção contra tanque vazio, não um controle de nível mínimo configurável, previsão de falhas, rede de refrigeração, proteção contra todas as situações ou simulação de derretimento. Uma parada SCRAM pode também ocorrer por calor ou perda de estrutura; a tela não armazena a causa histórica específica.

- A parada por calor é verificada após o resfriamento do tick: ocorre se a temperatura nessa verificação for 1.000 ou mais; uma passagem transitória por esse valor antes de resfriar não garante parada.
- Risco preexistente, não corrigido neste trabalho: se o controlador for removido durante um callback de `fill` de água, pode ocorrer perda da água; a recuperação de inventário na remoção não garante recuperar o conteúdo do tanque. Não há garantia transacional para esse caso.

## Diagnóstico na tela

- Mostra a capacidade máxima de água, estado **Proteção: sem água** (quando aplicável) e **Espera: bateria cheia** quando não cabe o próximo lote de 200 FE. O aviso de SCRAM tem prioridade sobre os demais.
- Posicionar o cursor sobre calor, água, armazenamento FE ou botão de água mostra orientações sobre limites, consumo e funcionamento. Os textos têm português e inglês. A tela ainda usa os botões anteriores de Ligar e Desligar.
- O comando de proteção é tratado no servidor pelo contêiner aberto; usa as mesmas verificações existentes de alcance, dimensão e estrutura. O cliente recebe apenas o estado para exibição.

## Verificação e limites

Compilação isolada Java 8 das três classes alteradas e teste novo contra as dependências Forge 1.12.2-14.23.5.2860 em cache; sem gerar JAR. As classes de outras partes do projeto usadas pelo teste vieram do `build/classes` já existente, portanto isto não substitui um build limpo ou teste de integração completo. Testes isolados cobrem padrão legado, persistência, tanque vazio, último mB, reinício manual e temperatura, baldes, bateria cheia, permissões do botão, sincronização de flag e callback reentrante. Também rodaram regressões nucleares existentes e verificações estáticas de geometria/tradução. Não houve teste de cliente, servidor real, multiplayer ou funcionamento no jogo.
