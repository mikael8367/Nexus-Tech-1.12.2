# Estado da ponte após continuação

A ponte continua INCOMPLETA. Não conecta a tabela à rede SSN e não movimenta itens ou EMC.

Foram adicionados leitores isolados, não conectados à GUI: ProjectEReader para leituras e validações da mesa e jogador, e SsnAdjacentProbe para inspeção de cabo e controlador SSN. Um controlador atribuído pode estar desatualizado e não comprova uma conexão ativa. As novas classes não debitam nem creditam EMC e não acessam estoques.

O check offline com Java 8 e Gradle 4.9 passou, incluindo 10 suítes standalone. As duas novas suítes usam simulações no check principal; a verificação dos JARs reais foi separada e parcial, não um teste em Minecraft. Não houve teste de jogo ou de transferências, persistência transacional, concorrência ou recuperação após falhas. Nenhum JAR foi gerado.

Este documento acompanha a publicação do progresso na main; consulte o histórico Git para os commits. O código inclui uma correção da verificação do tipo da ponte, identificada na revisão.
