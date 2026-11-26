# Novo anexo experimental — geração NÃO ativada

A nova seleção é `nova_anexo_11x14x11_sem_vidro.schematic` neste diretório, e não o arquivo anterior `nova_structure_sem_vidro.schematic.b64`. Ambos permanecem preservados. A nova seleção ainda NÃO é lida pelo gerador antigo: ele foi feito para outro schematic de 6×5×7, portanto NÃO deve ser apontado para este arquivo sem adaptação e validação real de segurança no Forge. A geração permanece desativada; nada aqui é uma liberação para jogar.

## Conteúdo e integridade

O anexo recebido é um schematic clássico Alpha/NBT compactado em gzip, dimensões X=11, Y=14, Z=11, volume 1694. O original permanece intacto fora do repositório. SHA-256 do anexo: `89f384a40f8c62632beaa02dec61685784f9940105c4e13e488b86b31068b335`. SHA-256 do arquivo novo: `2dc25713290206fc8adb090492ccaf13701d7af859f53a3c5af9ee1931b17d8d`.

Um único bloco ID 20 meta 0 (vidro transparente vanilla) na posição local x=0, y=13, z=0, foi substituído por ar; seus metadados já eram 0. Foram verificados também os IDs de vidro tingido 95, painel de vidro 102 e painel tingido 160: não ocorrem neste arquivo. Não há AddBlocks nem mapeamento de blocos mod no anexo; não se extrapola a outros schematics. Existem zero TileEntities e zero Entities, logo não havia TileEntity ligada ao bloco removido. Lava e demais blocos foram mantidos.

IDs e metadados no original, contagem: 0:0=1305; 4:0=285; 11:0=3; 11:8=15; 14:0=5; 15:0=10; 20:0=1; 44:3=43; 44:11=3; 67:0=6; 67:1=6; 67:2=6; 67:3=6. Offsets WorldEdit (3,-12,4), origem (-1013,57,674) preservados. O NBT descompactado inteiro é idêntico byte a byte, exceto o byte 1598 (20→0), e a comparação por parser NBT independente nbtlib 2.0.4 confirmou tags, tipos, arrays, metadados e entidades.

`nova-anexo-check.py` permite repetir a conferência contra o arquivo original recebido: `python -B nova-anexo-check.py check CAMINHO_DO_ORIGINAL nova_anexo_11x14x11_sem_vidro.schematic`. O script também permite `make`, mas exige o hash exato do original e é destinado apenas à auditoria deste caso. Ele impõe limites de tamanho e não substitui testes no Forge.
