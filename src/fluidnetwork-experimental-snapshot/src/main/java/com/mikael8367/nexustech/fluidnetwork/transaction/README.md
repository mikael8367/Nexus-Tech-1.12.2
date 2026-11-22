# Protótipo isolado de propriedade de fluidos

**Não registrar nem ativar.** Este modelo Java 8 não integra Minecraft, Forge, tiles,
chunks, handlers, itens nem rede. Não grava arquivos ou NBT no mundo, não oferece
persistência durável e **não prova segurança contra crash real**. As interrupções
são exceções injetadas ou imagens simuladas; não existe teste de queda de energia,
filesystem, serialização Minecraft ou recuperação em jogo.

## Modelo

Uma instância possui genesis imutável (contas fixas, saldo inicial e identidade de
fluido), saldos e histórico completo, todos incluídos em **uma única imagem**.
Nenhum saldo externo deve ser uma segunda autoridade. A movimentação prepara
cópias privadas e publica um único estado de saldos + histórico. Uma operação
confirmada e repetida com o mesmo ID, contas e valor retorna `false` sem mover;
reutilizar ID com outros parâmetros falha. IDs devem ser gerados de forma única
pelo chamador e preservados entre tentativas. A garantia vale dentro do histórico
da mesma instância recuperada, não entre cópias divergentes, reinicialização
sem a imagem correta ou IDs de diferentes sistemas. O histórico nunca é podado;
quando cheio, novas transferências são recusadas. Não existem criação, remoção
ou alteração de contas após genesis nem emissão/destruição posterior de fluido.

Cada conta está vinculada a um fluido e bytes opacos de NBT. Igualdade exige o
mesmo identificador e bytes exatos; o construtor e o getter copiam os arrays.
A classe **não valida, interpreta nem canonicaliza NBT**. Uma futura integração
precisará criar um formato determinístico, completo e sem perdas, diferenciando
ausência de NBT e NBT vazio, e preservar semântica/compatibilidade da versão.
IDs aceitos são ASCII não vazios de até 128 caracteres: letras, dígitos, `_`,
`-`, `:` ou `.`. Cada conta tem apenas um tipo de fluido. Valores usam `long`;
genesis recusa total por identidade que exceda `Long.MAX_VALUE`.

`recover` recusa formatos/versões desconhecidos, limites, entradas inválidas,
histórico duplicado ou impossível, dados truncados/adicionais e saldos que não
coincidem com o replay a partir de genesis. Após o replay, reserializa e compara
os bytes: a ordem das contas e das operações é preservada; representações UTF
não canônicas (inclusive overlong aceito por `readUTF`) são recusadas. Snapshots
produzidos por `snapshot()` usam a representação esperada. A comparação adiciona
uma serialização e alocação temporária durante a recuperação.

Isso detecta inconsistência lógica e representação não canônica, **não** garante
integridade, autenticidade ou ausência de rollback. Um bit pode mudar o ID `op`
para `oq`, manter o snapshot válido e permitir que uma nova tentativa com `op`
movimente novamente: o teste de limitação demonstra isso, não uma proteção. Um
snapshot antigo válido também passa, mesmo que o mais novo tenha sido perdido ou
substituído. A representação não fornece checksum, MAC, proteção de mundo
corrompido ou bloqueio de múltiplos processos; sem integridade e uma ancoragem
externa confiável de versão/frescor não se detecta a troca por snapshot antigo.
Mesmo checksum isolado não garante autenticidade nem evita rollback.
Limites: 1.024 contas, 10.000 operações, 4.096 bytes de NBT por conta e imagem
até 8 MiB. O histórico é copiado a cada operação e relido integralmente; custo
quadrático em muitos replays não foi avaliado para uso real.

## Fronteira de durabilidade ainda necessária

Uma implementação futura deve definir uma única fonte autoritativa durável de
saldos e histórico, escritor exclusivo, identificadores persistentes, protocolo
real de escrita/substituição e flush, detecção e política de corrupção/rollback,
ciclo de vida dos chunks, remoções, concorrência, compatibilidade com handlers de
terceiros e reconciliação antes de conceder acesso a qualquer representação
externa. A API atual publica em memória antes de qualquer persistência e não pode
sozinha garantir que um sucesso sobreviva a crash. Dois snapshots NBT separados
ou duas chamadas `markDirty` **não** são uma transação durável. Os testes usam a
hipótese explícita de que a imagem antiga OU a nova completa foi escolhida por
um mecanismo durável ainda inexistente; imagens truncadas são recusadas, não
reparadas automaticamente.
