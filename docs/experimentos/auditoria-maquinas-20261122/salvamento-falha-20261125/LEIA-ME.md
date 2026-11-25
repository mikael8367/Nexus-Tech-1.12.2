# Experimentos de falha no salvamento — NÃO INTEGRAR

Duas variantes isoladas, sem validação Forge. A quarentena recusa todas as inserções; a variante funcional ainda reproduz perda em modelo. Nenhuma é segura para integrar.

Arquivos grandes foram divididos em partes contíguas de bytes UTF-8. Reunir na ordem numérica; os hashes abaixo são dos bytes do arquivo original. Alguns runners referenciam caminhos absolutos do sandbox e requerem adaptação para reprodução.

- funcional/RELATORIO.md: SHA-256 `f6c99d958da730f5d954a33c42128e2fc60eac291dcb2ed0f2105bd4cc3a2134`; arquivos: RELATORIO.md.parte01.txt, RELATORIO.md.parte02.txt
- funcional/candidato.patch: SHA-256 `0a0cd5d8a321a0543f6d26a4f93a13667dce0812fc151daf160537a1eb77ed0e`; arquivos: candidato.patch
- funcional/run_tests.py: SHA-256 `924aa088928d69d147e3ce9c7dd933b7218fa50f55792f84bbc9de98f0df1c7a`; arquivos: run_tests.py.parte01.txt, run_tests.py.parte02.txt, run_tests.py.parte03.txt
- funcional/teste-modelo.log: SHA-256 `6b81b90915034ffa5ceb55cb83cb48587f162a7262476edfd65d991b7f129bd5`; arquivos: teste-modelo.log
- funcional/teste-modelo-revisado.log: SHA-256 `1ba1221b4a19caab5851f0d95d6179af40b719f8d349a0ffd5da4c85fd939bc3`; arquivos: teste-modelo-revisado.log
- funcional/teste-modelo-final.log: SHA-256 `16e05eca49595827f8974c91fc9a30ef188ddb65307d0730d9bc13c256b45e14`; arquivos: teste-modelo-final.log
- funcional/teste-modelo-final-v2.log: SHA-256 `75697245370f19a3646a5af9b9a1a8247c9f992a07d92aef6afe42de8908f48f`; arquivos: teste-modelo-final-v2.log
- funcional/TechMachineTile.java: SHA-256 `8f17e608226e7bb9b82310e2799b33dd8b1b34ecdfb552252272d1b63c50bf5c`; arquivos: TechMachineTile.java.parte01.txt, TechMachineTile.java.parte02.txt, TechMachineTile.java.parte03.txt
- quarentena/RELATORIO.md: SHA-256 `ce5c9087483be5f402ddaaeff9c109e3f016a4ee56905694f07013819d525afc`; arquivos: RELATORIO.md.parte01.txt, RELATORIO.md.parte02.txt
- quarentena/quarentena.patch: SHA-256 `d55450d7e9c399f6501a79ff1dfdcbb65eb36a3af85f2b065a1a72256e418283`; arquivos: quarentena.patch
- quarentena/run_quarantine_tests.py: SHA-256 `79f6165bf2b037e860716c1d7e6427923bd7e9baa277d6e8d2e8df5088891208`; arquivos: run_quarantine_tests.py
- quarentena/teste-quarentena.log: SHA-256 `c9942ee409df372ab141b22864d03659e8c23f2f8d1914d8fd13c8dd75e0c955`; arquivos: teste-quarentena.log
- quarentena/testes-anteriores.log: SHA-256 `543dbfbb0a7b6735be6612f6357465fbac6f522d32915724ee4cd4cf31d34139`; arquivos: testes-anteriores.log
- quarentena/TechMachineTile.java: SHA-256 `2fa05c6bbafd47951479a05768a541159faed500576c8fb3da8435f339c89d95`; arquivos: TechMachineTile.java.parte01.txt, TechMachineTile.java.parte02.txt, TechMachineTile.java.parte03.txt
