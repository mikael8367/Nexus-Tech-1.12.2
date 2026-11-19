#!/usr/bin/env python3
"""Upload único ao CurseForge com reservas permanentes de SHA-256 e versão.

O operador deve verificar manualmente todos os arquivos, inclusive pendentes,
no painel CurseForge ANTES de confirmar. As reservas protegem apenas execuções
que usam o mesmo repositório e namespace; não cobrem uploads externos.
Nunca apague reservas automaticamente, mesmo após erros ou timeout.
"""
import argparse
import hashlib
import json
import os
import re
import secrets
import stat
import sys
import urllib.error
import urllib.request
from pathlib import Path

PROJECT_ID = '1803861'
UPLOAD_URL = 'https://minecraft.curseforge.com/api/projects/' + PROJECT_ID + '/upload-file'
MAX_JAR = 128 * 1024 * 1024
MAX_CHANGELOG = 64 * 1024
TIMEOUT = 120


class PublishError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


# Do not follow redirects or environment proxy settings with privileged tokens.
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())


def safe_token(value):
    return bool(value) and len(value) <= 4096 and all(33 <= ord(c) <= 126 for c in value)


def configuration(env):
    cf = env.get('CURSEFORGE_API_KEY', '')
    gh = env.get('GITHUB_TOKEN', '')
    if not safe_token(cf):
        raise PublishError('CURSEFORGE_API_KEY ausente ou inválida; nenhuma reserva foi feita.')
    if not safe_token(gh):
        raise PublishError('GITHUB_TOKEN ausente ou inválido; nenhuma reserva foi feita.')
    if env.get('CONFIRM_DUPLICATE_CHECK') != 'true':
        raise PublishError('Confirme a verificação manual do painel com CONFIRM_DUPLICATE_CHECK=true; nenhuma reserva foi feita.')
    repo = env.get('GITHUB_REPOSITORY', '')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9_.-]{1,100}', repo) or repo.split('/')[1] in ('.', '..'):
        raise PublishError('GITHUB_REPOSITORY inválido; nenhuma reserva foi feita.')
    sha = env.get('GITHUB_SHA', '')
    if not re.fullmatch(r'[0-9a-fA-F]{40}', sha):
        raise PublishError('GITHUB_SHA inválido; nenhuma reserva foi feita.')
    return cf, gh, repo, sha.lower()


def prepare(args):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+\-]{0,127}', args.version):
        raise PublishError('Versão inválida; use até 128 caracteres ASCII (letras, números, ponto, hífen, + e _).')
    if args.release_type not in ('Release', 'Beta', 'Alpha'):
        raise PublishError('Tipo de publicação inválido.')
    try:
        change_bytes = args.changelog.encode('utf-8')
    except UnicodeError:
        raise PublishError('Changelog inválido.') from None
    if not change_bytes or len(change_bytes) > MAX_CHANGELOG or b'\x00' in change_bytes:
        raise PublishError('Changelog vazio ou inválido (limite 64 KiB).')
    try:
        # Read once: the exact same immutable bytes are hashed and uploaded.
        with Path(args.jar).open('rb') as f:
            if not stat.S_ISREG(os.fstat(f.fileno()).st_mode):
                raise PublishError('O JAR deve ser um arquivo regular.')
            jar = f.read(MAX_JAR + 1)
    except (OSError, ValueError):
        raise PublishError('Não foi possível ler o JAR.') from None
    if not jar or len(jar) > MAX_JAR:
        raise PublishError('JAR vazio ou maior que 128 MiB.')
    metadata = {'changelog': args.changelog, 'changelogType': 'markdown',
                'displayName': 'Nexus Tech ' + args.version,
                'gameVersionNames': ['1.12.2', 'Forge', 'Client', 'Server'],
                'releaseType': args.release_type.lower()}
    return jar, metadata


def request(req):
    # All responses, including errors, are intentionally bounded and never logged.
    with OPENER.open(req, timeout=TIMEOUT) as response:
        return response.status, response.read(65537)


def reserve(repo, token, sha, kind, digest):
    ref = 'refs/tags/nexus-cf-upload/' + kind + '/' + digest
    body = json.dumps({'ref': ref, 'sha': sha}).encode('utf-8')
    req = urllib.request.Request('https://api.github.com/repos/' + repo + '/git/refs',
        data=body, method='POST', headers={'Authorization': 'Bearer ' + token,
        'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28',
        'Content-Type': 'application/json', 'User-Agent': 'nexus-tech-publisher'})
    try:
        status, _ = request(req)
    except Exception:
        # Even unusual protocol/response errors must not leak server text.
        raise PublishError('Reserva GitHub falhou ou ficou incerta. Pode já existir; não remova reservas e não envie. Verifique o GitHub e o painel CurseForge.') from None
    if status != 201:
        raise PublishError('Reserva GitHub não confirmada. Pode já existir; não remova reservas e não envie. Verifique o GitHub e o painel CurseForge.')


def make_multipart(jar, metadata):
    meta = json.dumps(metadata, ensure_ascii=True, separators=(',', ':')).encode('ascii')
    # Avoid even the exceedingly unlikely boundary collision with the payload.
    while True:
        boundary = ('nexus-' + secrets.token_hex(24)).encode('ascii')
        if boundary not in jar and boundary not in meta:
            break
    body = (b'--' + boundary + b'\r\nContent-Disposition: form-data; name="metadata"\r\n'
            b'Content-Type: application/json\r\n\r\n' + meta + b'\r\n--' + boundary +
            b'\r\nContent-Disposition: form-data; name="file"; filename="nexustech.jar"\r\n'
            b'Content-Type: application/java-archive\r\n\r\n' + jar + b'\r\n--' + boundary + b'--\r\n')
    return boundary.decode('ascii'), body


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key')
        result[key] = value
    return result


def upload(token, boundary, body):
    req = urllib.request.Request(UPLOAD_URL, data=body, method='POST', headers={
        'X-Api-Token': token, 'Content-Type': 'multipart/form-data; boundary=' + boundary,
        'Accept': 'application/json', 'User-Agent': 'nexus-tech-publisher'})
    try:
        status, raw = request(req)
        if status not in (200, 201) or len(raw) > 65536:
            raise ValueError('bad response')
        response = json.loads(raw, object_pairs_hook=unique_object)
        file_id = response.get('id') if isinstance(response, dict) else None
        if type(file_id) is not int or file_id <= 0:
            raise ValueError('bad id')
        return file_id
    except Exception:
        # Do not reveal response text or retry a potentially accepted upload.
        raise PublishError('Resultado do upload incerto: pode ter sido recebido pelo CurseForge. NÃO tente novamente; confira o painel e as reservas permanentes.') from None


def append_summary(path, file_id, jar_digest):
    # Only validated digits and lowercase hex enter Markdown; no user input,
    # server text, release notes or tokens can become summary markup.
    if type(file_id) is not int or file_id <= 0 or not re.fullmatch(r'[0-9a-f]{64}', jar_digest):
        raise ValueError('invalid summary fields')
    text = ('### Upload aceito pelo CurseForge\n\n'
            '- ID do arquivo: `' + str(file_id) + '`\n'
            '- SHA-256 do JAR: `' + jar_digest + '`\n\n'
            'A aprovação/publicação não foi verificada.\n')
    with open(path, 'a', encoding='utf-8') as summary:
        summary.write(text)


class SafeArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        # argparse errors can contain secrets or workflow command injection.
        raise PublishError('Argumentos inválidos; confira os parâmetros obrigatórios.')


def main(argv=None):
    parser = SafeArgumentParser(description='Publicação controlada do Nexus Tech')
    parser.add_argument('--jar', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--release-type', required=True)
    parser.add_argument('--changelog', required=True)
    phase = 'preparation'
    try:
        args = parser.parse_args(argv)
        cf, gh, repo, sha = configuration(os.environ)
        jar, metadata = prepare(args)
        jar_digest = hashlib.sha256(jar).hexdigest()
        version_digest = hashlib.sha256(args.version.encode('ascii')).hexdigest()
        # Build everything possible before the first irreversible reservation.
        boundary, body = make_multipart(jar, metadata)
        phase = 'reservation'
        reserve(repo, gh, sha, 'sha256', jar_digest)
        reserve(repo, gh, sha, 'version', version_digest)
        phase = 'upload'
        file_id = upload(cf, boundary, body)
    except PublishError as error:
        print('ERRO: ' + str(error), file=sys.stderr)
        return 1
    except Exception:
        # Never echo unexpected exception text: it may include credentials or
        # server responses. Preserve conservative recovery instructions.
        if phase == 'preparation':
            message = 'Falha inesperada antes das reservas; nenhum upload foi iniciado.'
        elif phase == 'reservation':
            message = 'Reserva pode ter sido criada; não remova reservas e não envie. Verifique o GitHub e o painel CurseForge.'
        else:
            message = 'Resultado do upload incerto; NÃO tente novamente. Confira o painel e as reservas permanentes.'
        print('ERRO: ' + message, file=sys.stderr)
        return 1
    print('Upload aceito pelo CurseForge; ID do arquivo: ' + str(file_id) + '. A aprovação/publicação não foi verificada.')
    print('SHA-256 do JAR: ' + jar_digest)
    summary_path = os.environ.get('GITHUB_STEP_SUMMARY', '')
    if summary_path:
        try:
            append_summary(summary_path, file_id, jar_digest)
        except Exception:
            print('ERRO: upload já aceito, mas não foi possível registrar ID e SHA-256 no resumo. NÃO tente enviar novamente; confira o painel e as reservas permanentes.', file=sys.stderr)
            return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
