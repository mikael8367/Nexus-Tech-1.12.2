# WIP — não validado, não usar em mundo

Base 3e690bae7c42b6471387b05bfe23cfcf02096b1d. Somente fontes de laboratório nesta pasta: não são integradas ao build, nem registradas. Sem geração, sem JAR. Os recursos anteriores permanecem.

A proposta usa cópia privada ChunkPrimer, snapshot do terreno e exige ar no destino e pedra abaixo. IDs 0,2,3,9,59,60,85 são apenas hipótese vanilla, não existe proveniência do registro original. Exige nome, ID e round-trip de metadata; 50 células, bounds do chunk, verificação de estados e rejeição de TE. Não há World, acesso de vizinho, retrogen ou callbacks de geração. Falha descarta a cópia privada, não significa rollback de mundo.

Compilação Java 8 offline contra Forge 1.12.2-14.23.5.2860 mapeado: exit 0, aviso de API deprecated. Testes NÃO concluídos: primeira tentativa falhou com NoClassDefFoundError net/minecraft/launchwrapper/LaunchClassLoader; adicionada dependência do cache, nova execução não terminou em 12 segundos e foi interrompida. Portanto nenhum teste de comportamento pode ser afirmado como aprovado. Investigar inicialização Bootstrap/Forge e executar testes antes de sequer considerar integração.

Não comprova segurança de geração real, cascata, física, fluxo de água, ticks, luz, hidratação, suporte das culturas, callbacks ou mapeamento original. Jamais usar em mundo persistente; não ligar o gerador.
