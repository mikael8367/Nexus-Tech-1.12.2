# Experimento isolado: estrutura WorldEdit sem vidro

**Desativado e inacessível ao jogo.** Este diretório contém somente um leitor e um planejador puro experimentais, sem registro Forge, sem implementação de `IWorldGenerator` e sem qualquer método de escrita no mundo. Os arquivos antigos e o reator desativado permanecem intactos na branch base. Não usar este código como gerador nem integrar ao mod jogável até comprovação independente da segurança em Forge. Nada aqui cria ou compila JAR.

## Recurso

`sem_vidro.schematic.b64` é a representação Base64 do anexo WorldEdit, como recurso separado dos esquemas existentes; decodificando obtém-se exatamente o arquivo gzip-NBT com SHA-256 `5333f61ae7f814e0632e03909b219af91e89480b8b78adaabbb9db35b4cdaf22`. O original fornecido tem SHA-256 `46811e619e8155ab14e6450827c9893da57befec59866d2a7726e571e8514367`. Comparação dos dados NBT: a única alteração foi o bloco de vidro (ID 20, metadata 0) substituído por ar no índice 630, em (x=0,y=7,z=7). As demais posições, metadata e tags foram preservadas. Dimensões: largura 10, altura 8, comprimento 8; 640 células; listas de entidades e tile entities vazias. A paleta contém ar, terra, grama, variações de pedra, cascalho e minério de ferro. Nenhum vidro. Coordenadas WEOrigin/WEOffset preservadas no asset, mas ignoradas pelo leitor ao definir a posição no mundo.

## Planejamento, não geração

`NoGlassSchematic` fixa o hash, aceita apenas o NBT e a paleta previstos e indexa `(y*8+z)*10+x`. `NoGlassPlan` escolhe deterministicamente cerca de 1/16 dos chunks candidatos em função da seed e das coordenadas, inclusive negativas, independente do RNG de população. O retângulo usa x=3..12 e z=3..10, 4..11 ou 5..12 do chunk. O Site é uma interface SOMENTE de leitura que deve ser implementada sem carregar chunks, e os resultados são células imutáveis ordenadas de baixo para cima, incluindo ar. Não existe adaptador para o jogo ou rotina que escreva essas células.

A pré-validação conservadora exige superfície de grama plana em todo o retângulo, três camadas inferiores de terra/pedra metadata zero e coluna livre até o limite do mundo, sem tile entities detectáveis. As alturas permitidas da superfície são 3..250. Isso rejeita muitos terrenos, portanto 1/16 é apenas a seleção inicial, NÃO a frequência efetiva nem garantia de que seja comum no jogo.

**Limitações importantes:** somente os dados fornecidos por um Site confiável podem ser analisados; materiais naturais podem pertencer a construção de jogador, ou existir construções próximas ou cavernas não detectadas fora da área verificada. Ainda não há prova de chunk novo, nem proteção transacional contra gravação parcial, nem integração com chunk já carregado, nem comprovação contra cascatas/notificações de vizinhos. Cascalho (12 blocos) é sujeito à gravidade; requer análise de física e suporte antes de qualquer colocação. Nunca tratar um plano aceito como autorização para gerar. O protótipo anterior produziu 15.940 avisos de cascading worldgen e watchdog, e falhou em terreno normal; testes em servidor Forge e proteção robusta contra alterações preexistentes são indispensáveis antes de qualquer ativação.

## Testes sem JAR

Com Python 3 e JDK 8 ou superior, nesta pasta:

```
python3 test_no_glass.py /caminho/opcional/para/o-anexo-original.schematic
mkdir -p classes
javac -d classes NoGlassSchematic.java NoGlassPlan.java NoGlassPlanTest.java
java -cp classes com.mikael8367.nexustech.worldgen.NoGlassPlanTest sem_vidro.schematic.b64 reference.bin
```

O argumento do anexo original é opcional; sem ele não é possível repetir a comparação contra o original. Quando informado, seu SHA-256 e o tamanho exato de 640 bytes do array de blocos são verificados antes da comparação. O teste Python cria `reference.bin` exclusivamente se não existir; se já existir, compara o conteúdo e falha em caso de divergência, sem sobrescrevê-lo. O arquivo serve para comparar os 640 IDs/metadados com o leitor Java. As verificações Python também funcionam com `python3 -O test_no_glass.py /caminho/opcional/para/o-anexo-original.schematic`. Os testes exercitam geometria, dados, frequência amostral, determinismo, coordenadas negativas/extremas e rejeições simuladas, mas NÃO executam Minecraft/Forge e não comprovam segurança no jogo.
