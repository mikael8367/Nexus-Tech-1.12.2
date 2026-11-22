# Triturador de espinhos: auditoria de XP (Forge 1.12.2)

## Estado

**Ainda não implementado:** não foi encontrada nesta auditoria uma forma de conceder XP por morte do triturador que garanta os requisitos conservadores para os mobs e eventos de outros mods, sem modificar outros sistemas. O código do triturador permanece inalterado. Não foi adicionado XP fixo, falso jogador nem invocação manual de XP. A morte continua seguindo a lógica anterior do jogo; se um jogador tiver acertado o mob recentemente, a lógica vanilla ainda pode gerar XP normalmente.

## Evidências verificadas

A análise de bytecode foi realizada com Forge 1.12.2 14.23.5.2860 mapeado em MCP (snapshot 20171003-1.12), não em um servidor ativo:

* `CrusherTile.update` ataca com `CrusherDamage`, fonte sem jogador. Retorno verdadeiro de `attackEntityFrom` não é prova de morte definitiva nem de sua causa exclusiva: pode haver ataques reentrantes, cancelamentos e ressurreições.
* `EntityLivingBase.onDeath` chama `ForgeHooks.onLivingDeath` e pode retornar por cancelamento antes de marcar o campo `dead`. Um handler de evento não é, por si só, prova de que a morte foi confirmada depois de todos os outros handlers.
* A via normal de `EntityLivingBase.onDeathUpdate` cria orbes no 20º tick de morte. Para não jogadores, exige `recentlyHit > 0`, `canDropLoot()` e `doMobLoot`; usa o `getExperiencePoints(attackingPlayer)` específico do mob, depois o evento Forge de experiência e só então gera orbes. Assim, XP manual logo após o golpe pode duplicar XP vanilla decorrente de um golpe recente de jogador.
* `getExperiencePoints` e `canDropLoot` são protegidos; a implementação de `EntityLiving` calcula XP também a partir do equipamento e sorteio. Filhotes e subclasses podem ter regras próprias. O dragão substitui a própria rotina de morte e tem um caminho diferente; mods podem fazer o mesmo.
* Fingir que um jogador atingiu o mob ou alterar `recentlyHit` pode mudar drops, atribuição e outros comportamentos; calcular XP fora da via normal pode ignorar cancelamento/alteração do evento, regras próprias e morte adiada. Sem coordenação com a rotina final, consultar um estado no instante do golpe não elimina a possibilidade de XP vanilla posterior.

## Evidência reproduzível

`src/test/java/com/mikael8367/nexustech/crusher/test_crusher_xp_bytecode.py` verifica cinco premissas na classe Forge mapeada e no código atual, incluindo cancelamento, jogador anterior, evento de XP, subclasses especiais e ausência de XP cego. Execução local:

```sh
export JAVAP=/home/daytona/workspace/nexus-test/jdk/bin/javap
export FORGE_CP="$(cat /tmp/breaker-test-classpath)"
PYTHONDONTWRITEBYTECODE=1 python3 src/test/java/com/mikael8367/nexustech/crusher/test_crusher_xp_bytecode.py
```

Passaram 5 verificações estáticas de bytecode/fonte; não são testes dinâmicos de morte, integração com outros mods ou demonstração de segurança no jogo. As dependências são externas e podem não existir em outro ambiente. Nenhum build completo, JAR ou push foi feito.

## Antes de implementar

Definir e testar uma integração que reconheça a causa da morte efetivamente confirmada, invoque as regras de XP do mob e evento Forge no momento apropriado, evite uma segunda emissão pela via normal e mantenha intactos os demais efeitos. Considerar explicitamente cancelamento da morte/XP, ressurreição, golpe prévio do jogador, gamerule desligada, filhotes, mobs sem XP, fontes concorrentes/reentrantes, morte customizada, salvamento/reload e cliente/servidor. A eventual solução pode precisar limitar os tipos suportados ou exigir uma integração específica; não há garantia de compatibilidade universal com subclasses de terceiros.
