# Candidatos experimentais: NÃO DISTRIBUIR

Base: execução 45711716383, variante com reator desativado. Estes patches são candidatos independentes, não aplicados automaticamente.

- testes.patch: separa suítes de reator ativo da variante desativada e ajusta o teste de guia; dry-run de configuração e verificações estáticas passaram, mas suíte Java completa não executada. Integração Mystical exige dependências externas.
- quarentena.patch.gz.b64: tentativa de quarentena de NBT e desmontagem condicionada; testes Java focados e estáticos passaram, mas salvamentos não são atômicos, remoções podem contornar callback, e falta restauração segura. Não integrar ou tratar como correção concluída.
- Reator permanece desativado. Persistem riscos de manipulação do handler público e restauração de ForgeCaps/ForgeData. Requer revisão adicional, build completo e testes reais.
- Nenhuma mudança na main ou release.
