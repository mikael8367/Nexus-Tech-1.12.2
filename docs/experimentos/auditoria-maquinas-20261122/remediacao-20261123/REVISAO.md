# Revisão independente — NÃO INTEGRAR

Candidatos parciais, mantidos isolados. Estes relatórios descrevem o ambiente local da execução, não o estado do GitHub. Nenhuma das máquinas foi alterada no source set ativo. Os scripts dependem de caminhos locais absolutos e de classes, código, Forge e JDK locais; esta pasta por si só não é um ambiente de testes reproduzível.

## Máquina ativa
O candidato mitigou apenas o cenário específico de término reentrante simulado; 79 e 24 asserções passaram, incluindo testes de riscos. Inserção com callback que drena e lança ainda expõe duplicação condicional; falha de spawn ainda perde item. Uma janela de tick não final após cálculo de receita e callback live pode debitar FE e incrementar progresso após drenagem, inferida de leitura e ainda não testada. readFromNBT reentrante é ignorado e não há durabilidade transacional. Não integrar.

## Máquinas experimentais
No cenário simulado, saída congestionada pausa sem desperdiçar o progresso e o acesso físico aos insumos voltou, mas o contrato de automação mudou. 537 e 72 asserções passaram; cinco outras suítes falharam na primeira asserção, não há prova do restante; 8 asserções adicionais documentam riscos. A quarentena pode perder dados na quebra e tem validação incompleta de tipos NBT. Migração de legado não está implantada nem demonstra autenticidade do progresso. Retirar insumo ainda descarta progresso pago. Callbacks e transferências não são atômicos; cabos e hoppers não testados. Não integrar ou distribuir.

Não há testes no Minecraft real, build Gradle, JAR ou garantia de save, crash, servidor e concorrência. A source local de origem pode divergir da main remota. Nada deve ser ativado até correções, revisão e testes com backup.
