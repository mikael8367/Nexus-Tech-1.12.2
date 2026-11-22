# Nexus Tech: inventário e auditoria inicial

Base consultada: `mikael8367/Nexus-Tech-1.12.2`, branch `main`, commit `815cace413413917ddac45371e0dd0c2c6047f7f`. Auditoria estática inicial abrangente; não representa inspeção exaustiva linha por linha, nem certificação de funcionamento no jogo. Registro significa presença no código, não teste funcional. A revisão deve continuar durante todas as etapas.

## Origens e histórico
A árvore principal remota contém 348 arquivos e 19 classes Java em `src/main/java`. A cópia local de estágio sem metadados Git contém 203 classes Java principais e não tem origem comprovada. Comparação excluindo caches/builds/logs: 92 arquivos iguais, 40 diferentes, 216 apenas remotos e 1.026 apenas locais. Não substituir a árvore remota pela local. O PR #1 industrial já existe com 43 commits à frente e 188 atrás do main no momento consultado. Há snapshots remotos em `docs/experimentos` e um ZIP experimental; preservar o conteúdo e reconciliar por arquivo. O workflow de publicação usa ZIP fixado, não a compilação do main atual.

## Código principal remoto registrado
`ModRegistry.java` registra cabo FE, painel solar, gerador de combustível, turbina, bateria, fornalha elétrica, triturador, compressor e gerador de lava. `MachineTile`, `MachineTileLogic`, `MachineContainer` e `EnergyNetwork` contêm inventário, lógica, GUI e rede básicos. Essas constatações não são validação funcional. A ponte ProjectE/SSN é diagnóstica; não realiza transferências reais nem transações EMC. Seu leitor em produção está deliberadamente desligado. O código remoto principal não contém reator ou rede de fluidos registrados.

## Outras fontes e protótipos
Na cópia local de estágio há registros para mineradores digitais básico e avançado, quebrador de blocos, colheitadeira, triturador de espinhos, ventilador, gerador de criaturas, bigorna elétrica, prensa, fundidor de ligas, serraria, composteira, engarrafadora, recicladora, purificadora, baterias avançadas, solar grande, eólica, hidroelétrica, armazenamento, esteira e cabos de itens. Não se presume que estejam presentes no main ou que funcionem no jogo. Algumas máquinas locais operam por interação manual sem GUI própria.

A mesma cópia registra controlador e peças de reator, mas seu registro não demonstra segurança no jogo. Há recuperação de água experimental em snapshot remoto; salvamentos independentes e casos de interrupção precisam de investigação. O snapshot da rede de fluidos não deve ser registrado: o tubo e a bomba não completam a transferência automática; tanque e persistência precisam de revisão. As dez máquinas salvas em `docs/experimentos/dez-maquinas-20261122` não estão no build principal e apresentam incompatibilidade com os cabos locais. Brocas em área estão desativadas no estágio; não declarar funcionais. Não apagar esses trabalhos.

## Materiais, receitas e recursos
A cópia de estágio define cobre, estanho, alumínio, zinco, níquel, prata, chumbo, urânio, titânio e tungstênio; há registros Ore Dictionary, fundição e ligas bronze e latão. Seu carregamento, utilidade, balanceamento e compatibilidade entre mods não foram demonstrados em jogo. Encontrados no estágio 56 blockstates, 90 modelos de bloco, 143 modelos de item e 130 JSONs de receita com sintaxe válida. Texturas base64 dependem de geração no build. Não foi identificada integração dedicada JEI para receitas próprias. A ponte SSN/ProjectE ainda não reserva EMC nem realiza movimentação: identidade do jogador e persistência entre inventário e EMC exigem desenho e teste antes de ativação.

## Dependências e testes
Na cópia de estágio: Forge 1.12.2-14.23.5.2860, Java 8, Gradle 4.9 e ForgeGradle com versão dinâmica 3.+. O ForgeGradle dinâmico é risco de reprodutibilidade. Testes históricos e mocks não demonstram build atual ou operação in-game. Avaliar dependências opcionais e inicialização com e sem cada mod. Há apenas cerca de 253 MiB livres no ambiente usado; consultar `df -h /` antes de build/cópia/empacotamento. Não apagar dados ou gerar artefatos sem validação.

## Bugs e riscos prioritários
1. `src/main/java/com/mikael8367/nexustech/EnergyNetwork.java`: receptor pode aceitar FE e lançar exceção; o main ignora o valor incerto e o emissor pode reembolsar, criando duplicação condicional. Há mitigação parcial separada nesta branch: contabiliza oferta incerta e interrompe envio, podendo perder FE. Testes isolados não comprovam comportamento real.
2. Na rede FE, callback pode remover cabo já enfileirado e a travessia prosseguir: teste artificial observou entrega de 90 FE após desconexão. Caminhos e chunks precisam de revalidação. Ainda não corrigido.
3. `MachineBlock` da fonte principal cria drops copiando slots antes de esvaziá-los; reentrada pode representar risco de duplicação, ainda sem reprodução em jogo. Capability de item obsoleta também requer análise e teste de remoção e substituição.
4. `MachineTile.update` altera estado antes de `markDirty` fora do `try/finally`; uma exceção pode deixar a guarda travada e afetar FE. Condicional, não reproduzido em jogo.
5. Reator experimental: possível problema de autorização da recuperação de água e ausência de prova de atomicidade entre dados salvos separadamente. Não integrar como seguro.

## Próximas etapas
Auditar exaustivamente diferenças por arquivo e completar inventário; estabilizar infraestrutura e testes; finalizar máquinas já registradas; energia e fluidos; ponte ProjectE/SSN; reator; máquinas novas; materiais e upgrades; interfaces, modelos e receitas; integração JEI e outros mods; testes completos incluindo Forge/Java 8, servidor, mundo real e reload. Documentar para cada alteração arquivos, causa, reprodução, impacto, correção e testes. Não fazer merge, publicar, nem declarar sistemas completos com problemas conhecidos ou sem teste real.
