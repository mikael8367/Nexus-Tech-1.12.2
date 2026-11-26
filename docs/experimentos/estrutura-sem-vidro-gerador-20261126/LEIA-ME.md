# Experimento transacional sem vidro — NÃO ATIVADO

Este é um núcleo de gerador que executa seleção, validação e entrega de um plano de 640 células a um **backend abstrato**, não um gerador executável em Forge. Não existe implementação Forge do backend, não há registro, configuração ou alteração de versão, e nenhum bloco é gerado no jogo. O reator e os arquivos anteriores não foram alterados. Não houve criação de JAR, workflow ou publicação. Este experimento NÃO prova que o problema anterior de cascata está corrigido.

A base é `isolated/estrutura-sem-vidro-plano-20261126`, commit `93e610be8db0f54a6adfbc07192f91997d5e35c0`. O recurso, o leitor e o plano estão no diretório irmão `estrutura-sem-vidro-20261126`. SHA-256 do recurso gzip decodificado: `5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22`. A comparação com o anexo original confirma apenas vidro para ar no índice 630; os 640 metadados são preservados.

## Arquitetura e limitações

O núcleo usa o seletor determinístico anterior (1/16 candidatos em média, não frequência efetiva), o plano de 10×8×8 e um contrato de backend abstrato que deve fornecer snapshot imutável de um único chunk comprovadamente novo, inalterado e já carregado, sem carregar ou gerar qualquer outro chunk. Rejeita coordenadas extremas, borda incerta, entidades, tile entities, marcadores preexistentes, material ou metadata fora da lista, colunas não planas, cobertura inadequada e cascalho sem suporte no plano final. Examina uma margem e toda a altura, ainda dentro do chunk; evita gravações antes da confirmação. Um estado NBT simplificado registra seed, chunk, versão e hash do asset. O backend deve fazer comparação completa e transação atômica durável de blocos e marcador, sem carregamentos, notificações, efeitos de física, callbacks, iluminação ou gravações parciais. O núcleo chama apenas o contrato; ele NÃO pode garantir que implementações arbitrárias o cumpram. Um backend defeituoso pode produzir efeitos parciais ao lançar exceção.

A API exige uma `Reservation` que fornece o snapshot e é fechada via `try-with-resources` (finally), inclusive em recusa, conflito, commit e exceção. Se aquisição lançar antes de retornar a reserva, a limpeza cabe ao próprio backend; retorno null significa que nenhuma reserva ficou retida. `close` deve liberar, sem confirmar nem desfazer o commit; o núcleo só garante invocação, não a correção de uma implementação defeituosa. Exceções são propagadas, com falha de close suprimida quando existe exceção primária; `Result.ERROR` permanece por compatibilidade, mas não é mais retornado por `attempt`. Falha de close após commit pode significar que os blocos já foram gravados: nunca presumir rollback ou tentar novamente sem verificar o estado. É uma mudança incompatível da interface experimental; não há backend Forge.

**Bloqueio essencial:** o backend Forge 1.12.2 que cumpra tais garantias ainda não existe. Chunk carregado, aparência natural, falta de marcador e inhabited time não provam ausência de construções ou alterações antigas. Mesmo com margem, efeitos, iluminação, cascalho, eventos, persistência, entidades, outros mods e acesso a chunks vizinhos não estão demonstrados como seguros. A solução não está pronta para jogar e não deve ser registrada ou integrada até auditoria de ciclo de vida, atomicidade/recuperação, proteção do mundo e testes reais em Forge. Os filtros rejeitam inclusive terrenos naturais comuns (grama alta, árvores, irregularidades, água); não há demonstração de frequência efetiva aceitável.

## Testes reproduzíveis sem JAR

Com Java 8 e Python 3, a partir da raiz do repositório; os arquivos `.class` são produzidos apenas em um diretório temporário escolhido pelo executor:

```
df -h /
mkdir -p /tmp/nexus-noglass-classes
python3 docs/experimentos/estrutura-sem-vidro-20261126/test_no_glass.py
javac -d /tmp/nexus-noglass-classes docs/experimentos/estrutura-sem-vidro-20261126/{NoGlassSchematic.java,NoGlassPlan.java,NoGlassPlanTest.java} docs/experimentos/estrutura-sem-vidro-gerador-20261126/{NoGlassTransactionalGenerator.java,NoGlassTransactionalGeneratorTest.java,NoGlassReservationTest.java}
java -cp /tmp/nexus-noglass-classes com.mikael8367.nexustech.worldgen.NoGlassPlanTest docs/experimentos/estrutura-sem-vidro-20261126/sem_vidro.schematic.b64 docs/experimentos/estrutura-sem-vidro-20261126/reference.bin
java -cp /tmp/nexus-noglass-classes com.mikael8367.nexustech.worldgen.NoGlassTransactionalGeneratorTest docs/experimentos/estrutura-sem-vidro-20261126/sem_vidro.schematic.b64
```

Teste local Java 8: 1.338 verificações no teste transacional; inclui liberação simulada em sucesso, recusa, conflito, exceções e erros, precedência de exceções e ausência de commit em recusas. Também inclui colocação **em memória**, metadados, marcador NBT lido por parser independente, roundtrip do estado **simulado**, repetição, chunks indisponíveis, entidades/TE, colisões, blocos variantes, água, vegetação, encosta, conflitos, mudança concorrente, falhas em 0/1/320/639/640 células e limites. Teste anterior: 16.352 candidatos em 262.144, geometria e limites. A comparação Python verificou asset e anexo. Não são testes de servidor, callbacks/iluminação, NBT real de chunk Forge nem falha de energia. Não demonstram comportamento de mundo real nem falta de cascata. Nenhum código de teste executa ou cria JAR.
