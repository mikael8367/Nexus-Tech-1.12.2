# Mitigação experimental: energia FE em resposta incerta

Base: `815cace413413917ddac45371e0dd0c2c6047f7f`. Esta alteração é para revisão, não para publicação ou merge sem testes reais.

## Falha, reprodução e impacto
`EnergyNetwork.send` no main invoca um receptor `IEnergyStorage`. Se ele aceita parte ou toda a oferta e depois lança uma `RuntimeException`, a rede antiga não contabiliza o valor recebido. Um gerador pode reembolsar energia já entregue, duplicando FE. O cenário foi reproduzido com receptor simulado; ainda não foi reproduzido no jogo.

## Alteração e limitações
Em envio real, se o método de recepção lançar ou retornar negativo, a oferta restante é considerada incerta, totalmente contabilizada e o envio para. Isso evita o reembolso da oferta possivelmente entregue, mas **pode perder FE**. Se uma consulta posterior do mundo lançar, as transferências anteriores são mantidas na contabilidade. Simulação não debita oferta. A correção não garante segurança contra receptores que violem o contrato e mutem durante a simulação ou mintam um retorno não negativo. Não corrige `pull`, callbacks diretos, erros fatais, falhas do logger, persistência ou `MachineTile.update`.

Em outro teste artificial, a rede ainda entregou 90 FE através de cabo removido depois de enfileirado. A topologia não é revalidada; isso é uma limitação conhecida, não uma verificação aprovada. A guarda é ThreadLocal, não circuito persistente nem proteção entre threads.

## Testes isolados executados
Temurin 1.8.0_512, com o `EnergyNetwork.java` real e dependências mínimas simuladas: 29 + 9 verificações passaram. Abrangem envios parciais, receptor que altera estado e lança, negativos, retorno acima da oferta, simulação, exceções, reentrada, chunk descarregado, baterias e valores extremos. Os testes não executam o `MachineTile.update` real. O comportamento da baseline falhou em oito verificações antes de uma exceção de mundo encerrar o processo. Testes unitários com stubs não são Minecraft ou Forge real.

## Reprodução
Antes de compilar, confira o espaço em disco. A partir da raiz do repositório, com JDK 8 e sem sobrescrever arquivos existentes:

```sh
D=docs/experimentos/auditoria-industrial-fe-20261122
mkdir /tmp/nexus-fe-repro-UNICO
javac -source 8 -target 8 -d /tmp/nexus-fe-repro-UNICO $(find "$D/stubs" -name '*.java') src/main/java/com/mikael8367/nexustech/EnergyNetwork.java $(find "$D/tests" -name '*.java')
java -cp /tmp/nexus-fe-repro-UNICO com.mikael8367.nexustech.EnergyNetworkTest
java -cp /tmp/nexus-fe-repro-UNICO com.mikael8367.nexustech.EnergyNetworkExtraTest
```

Substitua `UNICO` por um nome que não exista. Os stubs ficam em `docs/experimentos`, fora do source set ativo. Não houve build completo, JAR, teste de Minecraft/Forge, multiplayer, modpack ou verificação de salvamento após queda. Avaliar a perda de FE como trade-off antes de integrar.
