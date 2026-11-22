# XP do Triturador de Espinhos — experimento isolado, não implementado

**Não solta XP extra e não há opção de ativação.** `CrusherXpExperiment` é um modelo puro de evidências: não lê entidades/mundos, não registra eventos, não é chamado pelo triturador e não emite XP. Um conjunto vazio de bloqueios significa apenas que todas as informações hipotéticas de entrada foram declaradas `PROVEN`; **não é autorização de gerar XP nem prova de que as informações são verdadeiras**. Informações ausentes, nulas, desconhecidas ou refutadas viram bloqueios. O dano e o loot normais do triturador permanecem inalterados.

## O que a análise estabeleceu

Para `EntityLivingBase` no Forge 1.12.2 14.23.5.2860 (mappings snapshot 20171003), a morte comum é processada no 20º tick. Para não jogadores, a emissão normal requer `recentlyHit > 0`, `canDropLoot()` e `doMobLoot`; usa o cálculo próprio `getExperiencePoints(attackingPlayer)`, depois `ForgeEventFactory.getExperienceDrop`, que publica `LivingExperienceDropEvent` e devolve zero se cancelado, antes de criar orbes. O XP de `EntityLiving` também depende de equipamentos e aleatoriedade; subclasses podem modificar o cálculo ou substituir a rotina. `EntityDragon` tem outro caminho. O patch oficial mostra que o evento de XP vem **depois** das condições vanilla; escutá-lo não ativa XP quando elas falham.

Um `LivingDeathEvent` pode ser cancelado, e um callback ainda em andamento não demonstra o resultado final de outros handlers. Nem dano aceito nem saúde zero asseguram uma morte definitiva. O jogador pode ter acertado o mob antes ou durante a morte, permitindo XP pelo caminho normal; um segundo cálculo/emissão manual pode duplicar XP. Atribuir um falso jogador ou mexer em `recentlyHit` pode alterar loot, atribuição e callbacks. A proteção deve valer para a morte inteira, inclusive reentrada e carregamento, não apenas para um instante. `doMobLoot` e o evento precisam ser tratados no instante e contexto corretos; cancelar, alterar ou chamar o evento duas vezes tem efeitos observáveis.

Referências: [patch Forge 1.12.x de EntityLivingBase](https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/patches/minecraft/net/minecraft/entity/EntityLivingBase.java.patch), [LivingDeathEvent](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraftforge/event/entity/living/LivingDeathEvent.html), [LivingExperienceDropEvent](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.12.2/net/minecraftforge/event/entity/living/LivingExperienceDropEvent.html).

## Modelo novo e limites

Os 14 fatos positivos exigidos cobrem servidor/alvo não jogador; causa e morte definitivas; caminho de morte/XP reconhecido; gamerule e aptidão no momento da emissão; exclusão de XP nativo ou customizado durante toda a morte; cálculo específico e contexto; um único evento Forge não cancelado e resultado positivo; preservação do atacante e loot; e prevenção de duplicação em reentrada/reload. A lista é conservadora e serve para revelar hipóteses não resolvidas, não como implementação ou contrato suficiente de integração. Por exemplo, ainda não existe mecanismo que prove exclusão de XP em mobs arbitrários nem que capture a morte definitiva sem efeitos colaterais. Um evento não pode ser disparado apenas para descobrir se será aceito, pois isso também pode produzir efeitos ou duplicá-lo. O modelo não acessa Forge e não oferece uma ação de emissão.

## Testes reproduzíveis

Com Java 8, a partir da raiz do projeto (diretório de saída novo; não gera JAR):

```sh
df -h /
mkdir -p /tmp/nexus-crusher-xp-evidence-20261122
/home/daytona/workspace/nexus-test/jdk/bin/javac -d /tmp/nexus-crusher-xp-evidence-20261122 \
  src/main/java/com/mikael8367/nexustech/crusher/CrusherXpExperiment.java \
  src/test/java/com/mikael8367/nexustech/crusher/CrusherXpExperimentTest.java
/home/daytona/workspace/nexus-test/jdk/bin/java -cp /tmp/nexus-crusher-xp-evidence-20261122 \
  com.mikael8367.nexustech.crusher.CrusherXpExperimentTest
export JAVAP=/home/daytona/workspace/nexus-test/jdk/bin/javap
export FORGE_CP="$(< /tmp/breaker-test-classpath)"
/home/daytona/workspace/nexus-test/jdk/bin/javac -cp "$FORGE_CP" \
  -d /tmp/nexus-crusher-xp-evidence-20261122 \
  src/test/java/com/mikael8367/nexustech/crusher/CrusherVanillaXpPathTest.java
/home/daytona/workspace/nexus-test/jdk/bin/java -cp "/tmp/nexus-crusher-xp-evidence-20261122:$FORGE_CP" \
  com.mikael8367.nexustech.crusher.CrusherVanillaXpPathTest
PYTHONDONTWRITEBYTECODE=1 python3 src/test/java/com/mikael8367/nexustech/crusher/test_crusher_xp_bytecode.py
```

Resultado local: 245.836 asserções, 16.384 combinações exaustivas do modelo; oito testes estáticos de bytecode/fonte. O modelo exercita cenários hipotéticos de cancelamento, fonte concorrente, jogador anterior/tardio, gamerule, evento de XP, mobs customizados, reentrada/reload e cliente/jogador; ele **não simula Minecraft**. O teste adicional chama o `onDeathUpdate` mapeado real numa vaca e num mundo mínimo artificial: sem golpe recente, com golpe recente, gamerule desligada, cliente e filhote; verifica os ticks 19/20/21. O campo de golpe recente é definido artificialmente; ele não executa o triturador nem o ciclo completo de ataque e morte. O ambiente de teste emite erros de recursos de receitas ausentes no `Bootstrap`, embora as cinco verificações de XP passem. Eventos de morte/XP, ressurreição e mobs customizados não foram testados dinamicamente. Nada demonstra XP funcional, integração, ausência universal de duplicação ou compatibilidade de mods. Os caminhos externos podem não existir em outro ambiente.

Para ativar algum dia, seria necessária uma arquitetura que obtenha evidências confiáveis e preserve o fluxo do mob e eventos sem alterar o atacante ou drops normais; testes dinâmicos com servidor Forge real, mods e casos citados são indispensáveis. Até então, manter o experimento isolado e desativado.
