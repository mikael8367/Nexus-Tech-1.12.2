# Terreno experimental da estrutura 5×2×5

Seletor offline para `nova_anexo_5x2x5_3c17a590.schematic.b64`, não a 11×14×11. Não lê o recurso nem registra geração, não grava mundo e não cria JAR. A água da schematic não é água pré-existente no terreno.

O snapshot completo é copiado, com 16×16 colunas, alturas explícitas e máscara de reservas completa: índice `((y-minY)*16+z)*16+x`. Rejeita fonte não confirmada, alturas ilegais, blocos de chunk que transbordem inteiro, água, obstáculo, desconhecido ou reserva nos locais examinados. A confirmação da origem é uma declaração do chamador, não verificável pela classe. A futura integração deve confirmar que obteve uma cópia atômica de chunk já carregado e que os estados e a máscara são completos e verdadeiros; caso contrário usar UNCONFIRMED. O seletor não consulta chunks.

Uma única tentativa por chunk, chance 1/divisor, posição local 1 a 10 (margem 1), Y fornecido, não procurado. Exige 25 colunas com duas camadas sólidas naturais abaixo do piso, inclusive sob as partes vazias da schematic, e 50 blocos de ar no volume, sem reserva: conservador, pode rejeitar terreno válido. Não garante ausência de cavernas profundas, colisões, estruturas, entidades ou reservas fora do volume examinado. A classificação deve ser conservadora e compatível com os dados conhecidos. Não existe detecção universal nem sincronização com o mundo. O snapshot pode se tornar obsoleto: integração futura deve revalidar atomicamente antes de colocar, confirmar offsets/IDs Alpha, dados e proteções, e testar no Forge real.

Teste independente Java 8, sem Forge: compilar as duas classes e executar `com.mikael8367.nexustech.worldgen.Fazenda5CandidateTest`. Exercita 10.000 seeds, limites, água, obstáculos, desconhecidos, reservas, suporte e cópia defensiva.
