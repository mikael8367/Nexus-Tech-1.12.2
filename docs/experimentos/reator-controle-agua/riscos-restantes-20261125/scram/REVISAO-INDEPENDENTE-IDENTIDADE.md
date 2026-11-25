# Revisão independente do experimento de identidade

NÃO INTEGRAR. Achados por leitura; não foram reproduzidos em Forge:

- O desligamento manual e abortos podem ignorar retorno falso de `setIndicator(false)` e invocar `markDirty()` sem revalidar a identidade do tile; o indicador pode continuar ativo e a notificação pode agir sobre objeto antigo.
- Alteração/substituição do controlador durante `setVisualFormed(false)`, `syncPartVisuals` ou `releaseStructure` pode afetar peças do controlador substituto, já que as associações são por posição. Os testes usam stubs de estrutura.
- No retry, pendência dirty é zerada antes da chamada e não há guarda própria de retry. Em reentrada adversarial é possível tentar reiniciar quando ambas pendências estão temporariamente limpas; o finally para novamente, sem necessariamente agendar notificação do estado final. Necessita reprodução.
- `setIndicator` verifica identidade depois de setBlockState, mas não relê o estado. Callback pode reverter ACTIVE mantendo tile, enquanto retry acredita que foi confirmado.
- Pendências existem apenas na memória. `markDirty` não confirma gravação; após crash, descarregamento ou substituição definitiva não há garantia de persistência do SCRAM.

Os testes isolados mostram melhoria específica da identidade e reentrada, não prova de segurança no Forge. As variantes de extração são independentes e NÃO devem ser combinadas sem corrigir as regressões documentadas em seus relatórios. O teste térmico original em 999 continua falhando e está preservado.
