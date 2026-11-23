# Revisao independente

NAO INTEGRAR NEM DISTRIBUIR. Correcao parcial testada em FakeWorld, nao no Minecraft real.

Na maquina ativa, insercao e tick foram mitigados nos casos simulados, mas save sincronizado com a notificacao pode perder uma insercao aceita. A suite original RealBlockFakeWorldTests falhou por esperar o comportamento antigo; a copia ajustada nao a substitui. Outros riscos incluem spawn, callbacks e NBT.

Nas maquinas experimentais, NBT invalido foi posto em quarentena nos casos testados. A protecao de quebra bloqueia as maquinas saudaveis tambem, nao e adequada para integracao e nao cobre remocao direta. Spawn false ou excecao esvaziou o inventario serializado nos testes. Ha suites anteriores falhando.

Sem Minecraft real, Gradle, JAR ou alteracao da main. A revisao identificou hipotese nao reproduzida de mudanca da agua durante live apos result.
