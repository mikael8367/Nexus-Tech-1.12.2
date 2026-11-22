# XP do Triturador de Espinhos — experimento não implementado

**Desativado e sem opção de ativação.** `CrusherXpExperiment` é apenas um marcador de projeto, sem execução, registro de evento ou geração de XP. O triturador continua usando dano não associado a jogador. Mobs que já foram atingidos por um jogador podem deixar XP pelo caminho normal; o triturador não acrescenta XP.

## Auditoria: Minecraft/Forge 1.12.2, 14.23.5.2860, mappings snapshot 20171003

Para o caminho comum de `EntityLivingBase`, `onDeathUpdate` aguarda o 20º tick de morte; no servidor, para não jogadores, exige `recentlyHit > 0`, `canDropLoot()` e a gamerule `doMobLoot`. Depois calcula `getExperiencePoints(attackingPlayer)` e passa o resultado ao `ForgeEventFactory.getExperienceDrop`, que publica `LivingExperienceDropEvent` e retorna zero se cancelado, antes de criar orbes. Os métodos de cálculo e decisão, e os campos `recentlyHit`/`attackingPlayer`, são protegidos; não há um setter público de Forge para solicitar somente XP a partir de dano não associado a jogador. O evento de XP é chamado **depois** da condição, de modo que um listener não consegue ativar o cálculo quando ela falha. A morte pode ser cancelada por `LivingDeathEvent`; aceitar o dano ou ver saúde zero não garante morte consumada. `EntityDragon` tem outro processamento de morte.

Marcar `recentlyHit` por reflexão ou simular jogador não seria uma ativação isolada de XP: altera a atribuição da morte e pode mudar loot, callbacks e compatibilidade com outros mods; também não abrange com segurança subclasses que sobrescrevem o processamento. Criar orbes manualmente ou disparar um segundo cálculo pode duplicar o XP de um ataque recente de jogador, ignorar regras/eventos e divergir dos valores de cada mob. Por isso nenhuma dessas soluções está implementada.

## Antes de qualquer implementação futura

Encontrar uma API ou mudança de arquitetura que preserve o fluxo normal sem atribuir falsamente a morte a jogador, sem segundo drop, sem ignorar gamerule ou eventos, e sem presumir que subclasses seguem o fluxo comum. Avaliar explicitamente: morte cancelada ou entidade revivida; dano cancelado; morte com e sem ataque recente de jogador; gamerule ligada/desligada; `LivingExperienceDropEvent` alterado/cancelado; mobs sem XP, jovens e equipados; dragão e outros overrides; clientes/servidor, descarregamento e outros mods. Precisam de testes reais no jogo além dos testes de bytecode: eles apenas comprovam propriedades da versão auditada, não a compatibilidade geral.
