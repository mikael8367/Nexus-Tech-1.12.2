#!/usr/bin/env python3
"""Bloqueio preventivo: não enviar até haver verificação confiável de duplicatas.

A API oficial de autores documenta upload-file, mas não oferece método
comprovadamente completo de listar pendentes/rejeitados ou upload idempotente.
"""
import argparse
import os
import sys


def main():
    p = argparse.ArgumentParser(description='Publicação do Nexus Tech: proteção contra duplicatas')
    p.add_argument('--jar', required=True)
    p.add_argument('--version', required=True)
    p.add_argument('--release-type', required=True, choices=['Release', 'Beta', 'Alpha'])
    p.add_argument('--changelog', required=True)
    p.parse_args()
    if not os.environ.get('CURSEFORGE_API_KEY'):
        print('ERRO: Configure CURSEFORGE_API_KEY como secret do GitHub Actions.', file=sys.stderr)
    else:
        print('ERRO: Publicação bloqueada. A API de autores não documenta verificação completa de duplicatas e o envio não é idempotente. Nenhum arquivo foi enviado. Confira com o suporte CurseForge antes de liberar a publicação.', file=sys.stderr)
    return 1

if __name__ == '__main__':
    sys.exit(main())
