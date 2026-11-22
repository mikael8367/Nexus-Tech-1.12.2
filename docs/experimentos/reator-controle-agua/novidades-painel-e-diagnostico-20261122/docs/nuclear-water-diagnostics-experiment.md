# Diagnóstico experimental da reserva nuclear — não publicar

## Escopo

Somente na cópia de estágio: `NuclearRecovery.waterSnapshot(pos)` cria um retrato imutável de quantidade pendente em mB (`long`, sem a saturação em `Integer.MAX_VALUE` do método antigo) e número de registros de água naquela posição, usando apenas a reserva já carregada daquela instância. A dimensão é implicitamente a da instância: o chamador deve assegurar a dimensão e posição corretas. Não há histórico de jogadores, nomes, logs, cópias de itens, posição ou referências de mundo no resultado. Ele não modifica, compacta, faz salvamento, lê chunks nem transfere água. A consulta percorre toda a lista; listas muito grandes aumentam custo no servidor e não existe limite novo.

`WaterSnapshot.plan(tankMb)` calcula espaço até 8000 mB, quantidade teoricamente transferível e restante, sem tocar no tanque ou reserva. Valores do tanque fora de 0–8000 e posição nula são recusados; os resultados são imutáveis. O cálculo é **somente aritmético**: não confirma existência ou identidade do controlador, dimensão, thread, permissões, chunk, armazenamento, estado de remoção ou operação em curso. Uma cópia pode ficar desatualizada imediatamente, inclusive numa reentrada em que uma operação ainda está em andamento. O chamador futuro precisa fazer as verificações e ler novamente no momento correto; o resultado não autoriza uma transferência nem substitui `reclaimWater()`.

Ambas são APIs internas ao pacote e não estão ligadas a uma interface, comando, pacote de rede, capacidade ou mecanismo acessível ao jogador. Portanto, **não são ainda uma funcionalidade visível no jogo**. Não enviam informação a terceiros; isso não é um sistema de privacidade ou de permissões: quantidade e posição podem ser sensíveis e eventual exposição deve ser controlada separadamente. Devem ser usadas apenas na thread proprietária do servidor; não há sincronização para leitura concorrente. Não alteram a recuperação real, a GUI, os testes de GUI ou a rede de fluidos.

## Auditoria: limites e falhas remanescentes

- Ao ler NBT existente, a recuperação anterior ignora entradas de água com `Amount` zero, negativo ou tipo não numérico. Coordenadas ausentes viram zero via `getInteger`, potencialmente atribuindo água à posição errada. O teste reproduziu Y=64, `Amount=42`, X/Z ausentes como reserva em (0,64,0). Esta alteração **não corrige nem recupera** os dados inválidos: descartar, mover ou adivinhar coordenadas automaticamente pode perder ou duplicar. Backup e inspeção especializada antes de intervenção. Tipos numéricos diferentes, listas malformadas, limites de alocação e todos os formatos corrompidos não foram exaustivamente testados.
- O diagnóstico só vê registros que o leitor já aceitou; não detecta registros descartados durante o carregamento. Não prova que o NBT original ou o snapshot reflita os arquivos realmente persistidos. `markDirty` não é commit; tanque e reserva são salvos separadamente. Crash ou rollback parcial ainda pode perder ou duplicar água. Remoções fora do hook, erros de callbacks/slots e operações concorrentes externas mantêm os riscos descritos em `nuclear-water-recovery-experiment.md`.
- A recuperação por clique não implementa propriedade ou proteção de terrenos. A estimativa não verifica se ela poderá ocorrer. Não foi criado comando de administração nem reset, descarte, extração manual, remapeamento ou reparo de dados, pois seriam ações destrutivas ou exigiriam autorização e garantias adicionais.

## Provas isoladas

Compilação com Java 8 e Forge mapeado 1.12.2/14.23.5.2860, em diretório temporário separado, sem JAR e sem Gradle. Fontes nucleares não-GUI atuais e testes não-GUI foram compilados; classes anteriores de GUI/teste de GUI já existentes em `build/classes` foram usadas quando dependências exigiam. Isso não valida alterações concorrentes da GUI nem build completo. Foi verificado `df -h /` antes de cada compilação: 255 MB livres, 99% em uso; nenhum arquivo foi apagado.

`NuclearWaterDiagnosticsTest: PASS`: vazio e posições separadas; dois registros somando 8500 mB; dois `Integer.MAX_VALUE` + 200 sem overflow no retrato; tanques 0, 7500, 8000; planos e limites inválidos, posição nula, snapshot antigo após débito, NBT e `isDirty` inalterados; NBT com valores inválidos e coordenadas ausentes; 20 combinações de tanque/reserva comparando estimativa com recuperação real no mock sem interferência; consulta reentrante na notificação do fill sem outra notificação nem mutação da reserva, seguida de recuperação real única. A injeção é síncrona e artificial; não prova ausência de bugs em todos os callbacks ou corrida entre threads.

Também passaram, antes da última edição exclusivamente do novo teste: `NuclearWaterRecoveryTest`, `NuclearFillRemovalReentryTest`, `NuclearAutomationExtractionReentryTest`, `NuclearRecoveryTest`, `NuclearWaterSafetyTest`, `NuclearStartSafetyTest`, `NuclearCoolantGuardTest`, `NuclearUpdateReentryTest`, `NuclearPartIntegrationTest`, `NuclearStructureGeometryTest`, `NuclearRegressionTest`, `NuclearReactorTest`, `NuclearSafetyTest` e `NuclearUraniumTest`. Alguns testes existentes chamam outros, incluindo a classe GUI já compilada. Exceções deliberadas são registradas nos logs; avisos de `Unsafe`, namespace e API obsoleta são esperados no harness. Os logs estão em `/tmp/nexus-nuclear-water-diagnostics-20261122/`, incluindo `NuclearWaterDiagnosticsTest-final.log`.

Reprodução (ambiente e dependências locais necessários; executar `df -h /` antes da compilação):

```bash
cd /home/daytona/workspace/nexus-release-stage
df -h /
J=/home/daytona/workspace/nexus-test/jdk/bin
OUT=/tmp/nexus-nuclear-water-diagnostics-20261122
mkdir -p "$OUT"
CP="build/classes/java/main:build/classes/java/test:$(cat /tmp/nuclear-audit-classpath.txt)"
MAIN=(); for f in src/main/java/com/mikael8367/nexustech/nuclear/*.java; do case "$f" in *NuclearGui.java|*NuclearGuiLayout.java|*NuclearGuiHooks.java) ;; *) MAIN+=("$f");; esac; done
TEST=(); for f in src/test/java/com/mikael8367/nexustech/nuclear/*.java; do case "$f" in *Gui*) ;; *) TEST+=("$f");; esac; done
"$J/javac" -source 8 -target 8 -encoding UTF-8 -sourcepath '' -cp "$CP" -d "$OUT" "${MAIN[@]}" "${TEST[@]}"
"$J/java" -cp "$OUT:$CP" com.mikael8367.nexustech.nuclear.NuclearWaterDiagnosticsTest
```

Não foram realizados testes em Minecraft/Forge inicializado, multiplayer, modpack, disco, crash ou mundo real; não usar em produção nem publicar sem revisão e testes independentes.
