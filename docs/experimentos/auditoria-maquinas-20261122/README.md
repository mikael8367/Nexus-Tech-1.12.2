# Experimentos de máquinas: NÃO INTEGRAR

Este diretório preserva apenas código Java, testes, baselines e relatórios para revisão. O snapshot de origem era local e difere da main remota. Nenhuma máquina foi registrada no mod ativo.

Os arquivos de recursos e imagens do experimento local não estão nesta cópia; não é um mod executável. Os relatórios descrevem as condições locais quando foram escritos. O script run-all.sh mantém caminhos absolutos do sandbox original e depende de JDK 8 e dependências Forge em cache; para reproduzir em outro ambiente, seus caminhos devem ser ajustados, incluindo o teste original agora preservado em baseline/. Essas adaptações não foram testadas. Os logs e o patch combinado também permaneceram locais, e nem todos os hashes em diff/sha256.txt correspondem a arquivos publicados aqui.

A suíte original falhou após a alteração de extração; riscos de perda e duplicação persistem. Ver RELATORIO.md antes de qualquer integração. Não distribuir nem fazer merge como versão funcional.
