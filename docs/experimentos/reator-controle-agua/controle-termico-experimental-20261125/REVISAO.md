# Revisão independente — não integrar

## Variante de válvula
Partida em automático com guarda legada desligada não bloqueia água vazia em setEnabled: SCRAM ocorre apenas no próximo tick, por leitura do código, não reproduzido. NBT de configuração malformado desativa o automático, mas não bloqueia partida manual no legado sem água. É preciso testar e corrigir os intertravamentos. Um callback de drenagem pode, hipoteticamente, alterar a temperatura antes da redução de resfriamento e ocultar um pico crítico; cenário não foi reproduzido. Outras reentradas, estrutura e ciclo de mundo requerem investigação.

Em simulação com água reposta e alvo 400 em unidades internas, intervalos após estabilização 398–400 e 399–401 conforme ponto inicial; consumo registrado de 4.900 mB em 10.000 ticks, não há garantia de temperatura exata. Controle extraído do fonte, mundo, tanque e NBT são stubs. Persistência completa e Forge reais não testados. Não há interface de configuração no jogo.

## Variante anterior
Não mantém alvo positivo quando há água e proteção; não recomendar operação seca para fazê-la funcionar. O teste de 50 mil ticks foi seco e com proteção desligada. Apenas relatório histórico.

A revisão independente é posterior aos relatórios anteriores que dizem que não havia revisão. Não integrar ou distribuir.
