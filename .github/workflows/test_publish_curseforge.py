"""Offline fail-closed regression tests; no GitHub or CurseForge calls are made."""
import argparse
import contextlib
import hashlib
import io
import json
import socket
import stat
import unittest
import urllib.error
from unittest import mock

import publish_curseforge as pub


CF = 'CF_SECRET_do_not_print'
GH = 'GH_SECRET_do_not_print'
ENV = {'CURSEFORGE_API_KEY': CF, 'GITHUB_TOKEN': GH,
       'GITHUB_REPOSITORY': 'owner/repository', 'GITHUB_SHA': 'A' * 40,
       'CONFIRM_DUPLICATE_CHECK': 'true'}
ARGS = ['--jar', 'artifact.jar', '--version', '1.2.3', '--release-type', 'Beta',
        '--changelog', 'Correções e melhorias']
JAR = b'PK\x03\x04fake-jar-content'


class OfflineTest(unittest.TestCase):
    def setUp(self):
        # Even an accidental real request in a test cannot reach the network.
        for target in ('socket.create_connection', 'socket.socket.connect',
                       'socket.socket.connect_ex'):
            patcher = mock.patch(target, side_effect=AssertionError('network forbidden'))
            patcher.start()
            self.addCleanup(patcher.stop)
        opener = mock.patch.object(pub.OPENER, 'open', side_effect=AssertionError('network forbidden'))
        opener.start()
        self.addCleanup(opener.stop)

    def args(self, **changes):
        values = dict(jar='artifact.jar', version='1.2.3', release_type='Beta',
                      changelog='Correções e melhorias')
        values.update(changes)
        return argparse.Namespace(**values)

    def prepare_with_jar(self, args=None, content=JAR, mode=stat.S_IFREG):
        handle = mock.MagicMock()
        handle.__enter__.return_value = handle
        handle.fileno.return_value = 42
        handle.read.return_value = content
        with mock.patch.object(pub.Path, 'open', return_value=handle), \
             mock.patch.object(pub.os, 'fstat', return_value=mock.Mock(st_mode=mode)):
            return pub.prepare(args or self.args())

    def call_main(self, request=None, *, env=None, args=ARGS, jar=JAR):
        handle = mock.MagicMock()
        handle.__enter__.return_value = handle
        handle.fileno.return_value = 42
        handle.read.return_value = jar
        stdout, stderr = io.StringIO(), io.StringIO()
        with mock.patch.dict(pub.os.environ, ENV if env is None else env, clear=True), \
             mock.patch.object(pub.Path, 'open', return_value=handle), \
             mock.patch.object(pub.os, 'fstat', return_value=mock.Mock(st_mode=stat.S_IFREG)), \
             mock.patch.object(pub, 'request', side_effect=request or AssertionError('unexpected request')), \
             contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            result = pub.main(args)
        return result, stdout.getvalue(), stderr.getvalue()

    def assert_no_secrets(self, *outputs):
        for output in outputs:
            self.assertNotIn(CF, output)
            self.assertNotIn(GH, output)


class ConfigurationTests(OfflineTest):
    def test_valid_and_normalized_sha(self):
        self.assertEqual(pub.configuration(ENV), (CF, GH, 'owner/repository', 'a' * 40))

    def test_bad_tokens(self):
        for key in ('CURSEFORGE_API_KEY', 'GITHUB_TOKEN'):
            for bad in ('', 'bad token', 'bad\nsecret', '\u00e9', 'x' * 4097):
                with self.subTest(key=key, value=repr(bad)):
                    env = dict(ENV, **{key: bad})
                    with self.assertRaises(pub.PublishError) as err:
                        pub.configuration(env)
                    self.assertNotIn(bad, str(err.exception)) if bad else None
                    self.assert_no_secrets(str(err.exception))

    def test_missing_env_and_confirmation_exact(self):
        for key in ENV:
            with self.subTest(key=key):
                env = dict(ENV)
                del env[key]
                with self.assertRaises(pub.PublishError):
                    pub.configuration(env)
        for val in ('True', '1', 'false', '', ' true'):
            with self.subTest(val=val), self.assertRaises(pub.PublishError):
                pub.configuration(dict(ENV, CONFIRM_DUPLICATE_CHECK=val))

    def test_invalid_repositories(self):
        for value in ('', '../repo', 'owner/..', 'owner/.', 'owner/repo/more',
                      'owner/repo?x=y', '/repo', 'owner/', 'a' * 101 + '/repo'):
            with self.subTest(value=value), self.assertRaises(pub.PublishError):
                pub.configuration(dict(ENV, GITHUB_REPOSITORY=value))

    def test_invalid_sha(self):
        for value in ('', 'a' * 39, 'g' * 40, 'a' * 41, 'a' * 39 + '\n'):
            with self.subTest(value=value), self.assertRaises(pub.PublishError):
                pub.configuration(dict(ENV, GITHUB_SHA=value))

    def test_invalid_config_has_zero_requests(self):
        calls = mock.Mock()
        env = dict(ENV, CONFIRM_DUPLICATE_CHECK='false')
        result, out, err = self.call_main(calls, env=env)
        self.assertEqual(result, 1)
        calls.assert_not_called()
        self.assert_no_secrets(out, err)


class PrepareTests(OfflineTest):
    def test_metadata_and_bytes(self):
        jar, meta = self.prepare_with_jar()
        self.assertEqual(jar, JAR)
        self.assertEqual(meta, {'changelog': 'Correções e melhorias', 'changelogType': 'markdown',
                                'displayName': 'Nexus Tech 1.2.3',
                                'gameVersionNames': ['1.12.2', 'Forge', 'Client', 'Server'], 'releaseType': 'beta'})

    def test_release_types(self):
        for label in ('Release', 'Beta', 'Alpha'):
            with self.subTest(label=label):
                self.assertEqual(self.prepare_with_jar(self.args(release_type=label))[1]['releaseType'], label.lower())

    def test_invalid_versions(self):
        for value in ('', '.1', '-1', 'a/b', 'a b', 'v\n1', 'á', 'a' * 129):
            with self.subTest(value=value), self.assertRaises(pub.PublishError):
                self.prepare_with_jar(self.args(version=value))

    def test_valid_version_chars_and_max_length(self):
        for value in ('1.0_RC+build-1', 'x' * 128):
            with self.subTest(value=value):
                self.assertEqual(self.prepare_with_jar(self.args(version=value))[1]['displayName'], 'Nexus Tech ' + value)

    def test_invalid_release_type(self):
        for value in ('', 'beta', 'Snapshot', 'Beta\n'):
            with self.subTest(value=value), self.assertRaises(pub.PublishError):
                self.prepare_with_jar(self.args(release_type=value))

    def test_invalid_changelog(self):
        for value in ('', 'abc\0def', '\ud800', 'á' * (pub.MAX_CHANGELOG // 2 + 1)):
            with self.subTest(value=repr(value[:20])), self.assertRaises(pub.PublishError):
                self.prepare_with_jar(self.args(changelog=value))

    def test_changelog_at_byte_limit(self):
        self.prepare_with_jar(self.args(changelog='á' * (pub.MAX_CHANGELOG // 2)))

    def test_unreadable_nonregular_empty_and_oversized_jar(self):
        with mock.patch.object(pub.Path, 'open', side_effect=OSError('secret path')):
            with self.assertRaises(pub.PublishError) as exc:
                pub.prepare(self.args())
            self.assertNotIn('secret path', str(exc.exception))
        with self.assertRaises(pub.PublishError):
            self.prepare_with_jar(mode=stat.S_IFDIR)
        with self.assertRaises(pub.PublishError):
            self.prepare_with_jar(content=b'')
        with mock.patch.object(pub, 'MAX_JAR', 3):
            with self.assertRaises(pub.PublishError):
                self.prepare_with_jar(content=b'1234')
            self.assertEqual(self.prepare_with_jar(content=b'123')[0], b'123')

    def test_read_is_bounded_and_single(self):
        handle = mock.MagicMock()
        handle.__enter__.return_value = handle
        handle.read.return_value = JAR
        with mock.patch.object(pub.Path, 'open', return_value=handle), \
             mock.patch.object(pub.os, 'fstat', return_value=mock.Mock(st_mode=stat.S_IFREG)):
            pub.prepare(self.args())
        handle.read.assert_called_once_with(pub.MAX_JAR + 1)


class MultipartTests(OfflineTest):
    def test_multipart_preserves_bytes_and_metadata(self):
        metadata = self.prepare_with_jar()[1]
        with mock.patch.object(pub.secrets, 'token_hex', return_value='a' * 48):
            boundary, body = pub.make_multipart(JAR, metadata)
        delim = b'--' + boundary.encode('ascii')
        self.assertTrue(body.startswith(delim + b'\r\n'))
        self.assertTrue(body.endswith(delim + b'--\r\n'))
        self.assertIn(b'name="file"; filename="nexustech.jar"', body)
        self.assertIn(b'\r\n\r\n' + JAR + b'\r\n', body)
        meta_part = body.split(b'Content-Type: application/json\r\n\r\n', 1)[1].split(b'\r\n' + delim, 1)[0]
        self.assertEqual(json.loads(meta_part), metadata)

    def test_boundary_collision_in_jar_and_metadata_regenerates(self):
        values = ['a' * 48, 'b' * 48, 'c' * 48]
        with mock.patch.object(pub.secrets, 'token_hex', side_effect=values) as rand:
            boundary, _ = pub.make_multipart(b'nexus-' + b'a' * 48, {'x': 'nexus-' + 'b' * 48})
        self.assertEqual(boundary, 'nexus-' + 'c' * 48)
        self.assertEqual(rand.call_count, 3)


class ProtocolTests(OfflineTest):
    def test_no_redirect_or_proxy_configuration(self):
        self.assertIsNone(pub.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://evil.example'))
        self.assertFalse(any(isinstance(h, pub.urllib.request.ProxyHandler) for h in pub.OPENER.handlers))

    def test_request_bounded_read_and_timeout(self):
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 201
        response.read.return_value = b'body'
        with mock.patch.object(pub.OPENER, 'open', return_value=response) as opened:
            self.assertEqual(pub.request('fake'), (201, b'body'))
        opened.assert_called_once_with('fake', timeout=pub.TIMEOUT)
        response.read.assert_called_once_with(65537)

    def test_reserve_constructs_exact_ref_and_request(self):
        with mock.patch.object(pub, 'request', return_value=(201, b'')) as request:
            pub.reserve('owner/repository', GH, 'a' * 40, 'sha256', 'b' * 64)
        req = request.call_args.args[0]
        self.assertEqual(req.full_url, 'https://api.github.com/repos/owner/repository/git/refs')
        self.assertEqual(req.method, 'POST')
        self.assertEqual(req.get_header('Authorization'), 'Bearer ' + GH)
        self.assertEqual(json.loads(req.data), {'ref': 'refs/tags/nexus-cf-upload/sha256/' + 'b' * 64, 'sha': 'a' * 40})

    def test_reservation_failures_are_sanitized_and_no_retry(self):
        cases = [(422, b'duplicate ' + GH.encode()), (500, b'error'),
                 urllib.error.HTTPError('https://example.invalid', 422, GH, {}, None),
                 RuntimeError(CF + GH)]
        for case in cases:
            with self.subTest(case=repr(case)):
                with mock.patch.object(pub, 'request', side_effect=case if isinstance(case, Exception) else None,
                                       return_value=case) as req:
                    with self.assertRaises(pub.PublishError) as exc:
                        pub.reserve('owner/repository', GH, 'a' * 40, 'version', 'b' * 64)
                req.assert_called_once()
                self.assert_no_secrets(str(exc.exception))
                self.assertIn('não envie', str(exc.exception))

    def test_upload_request_and_positive_ids(self):
        for status in (200, 201):
            with self.subTest(status=status), mock.patch.object(pub, 'request', return_value=(status, b'{"id":123}')) as request:
                self.assertEqual(pub.upload(CF, 'boundary', b'body'), 123)
                req = request.call_args.args[0]
                self.assertEqual(req.full_url, pub.UPLOAD_URL)
                self.assertEqual(req.method, 'POST')
                self.assertEqual(req.get_header('X-api-token'), CF)
                self.assertEqual(req.get_header('Content-type'), 'multipart/form-data; boundary=boundary')
                self.assertEqual(req.data, b'body')
                request.assert_called_once()

    def test_upload_uncertainty_for_bad_status_body_id_or_exception(self):
        cases = [(202, b'{"id":1}'), (500, CF.encode()), (200, b'x' * 65537),
                 (200, b'not json'), (200, b'[]'), (200, b'{}'), (200, b'{"id":0}'),
                 (200, b'{"id":-1}'), (200, b'{"id":true}'), (200, b'{"id":"1"}'),
                 (200, b'{"id":1.0}'), (200, b'{"id":1,"id":2}'),
                 (200, b'{"id":1,"nested":{"x":0,"x":1}}'), TimeoutError(CF + GH)]
        for case in cases:
            with self.subTest(case=repr(case)[:100]):
                with mock.patch.object(pub, 'request', side_effect=case if isinstance(case, Exception) else None,
                                       return_value=case) as request:
                    with self.assertRaises(pub.PublishError) as exc:
                        pub.upload(CF, 'boundary', b'body')
                request.assert_called_once()
                self.assertIn('NÃO tente novamente', str(exc.exception))
                self.assert_no_secrets(str(exc.exception))


class FlowTests(OfflineTest):
    def fake_server(self, existing=(), upload_result=(200, b'{"id":456}')):
        reservations = set(existing)
        calls = []
        def request(req):
            calls.append(req)
            if req.full_url == pub.UPLOAD_URL:
                if isinstance(upload_result, Exception):
                    raise upload_result
                return upload_result
            self.assertEqual(req.full_url, 'https://api.github.com/repos/owner/repository/git/refs')
            ref = json.loads(req.data)['ref']
            if ref in reservations:
                return 422, b'already exists'
            reservations.add(ref)
            return 201, b'created'
        return request, calls, reservations

    def test_success_reserves_both_before_exact_bytes_upload(self):
        request, calls, reservations = self.fake_server()
        result, out, err = self.call_main(request)
        self.assertEqual(result, 0)
        self.assertEqual(err, '')
        self.assertIn('ID do arquivo: 456', out)
        self.assertIn('SHA-256 do JAR: ' + hashlib.sha256(JAR).hexdigest(), out)
        self.assertIn('aprovação/publicação não foi verificada', out)
        self.assert_no_secrets(out, err)
        jarref = 'refs/tags/nexus-cf-upload/sha256/' + hashlib.sha256(JAR).hexdigest()
        verref = 'refs/tags/nexus-cf-upload/version/' + hashlib.sha256(b'1.2.3').hexdigest()
        self.assertEqual(reservations, {jarref, verref})
        self.assertEqual([json.loads(r.data)['ref'] for r in calls[:2]], [jarref, verref])
        self.assertEqual(calls[2].full_url, pub.UPLOAD_URL)
        self.assertIn(JAR, calls[2].data)
        self.assertEqual(len(calls), 3)

    def test_repeat_same_artifact_or_version_never_uploads(self):
        request, calls, reservations = self.fake_server()
        self.assertEqual(self.call_main(request)[0], 0)
        calls.clear()
        result, out, err = self.call_main(request)
        self.assertEqual(result, 1)
        self.assertEqual(len(calls), 1)
        self.assert_no_secrets(out, err)
        calls.clear()
        result, out, err = self.call_main(request, jar=b'different jar')
        self.assertEqual(result, 1)
        self.assertEqual(len(calls), 2)
        self.assertTrue(all(r.full_url != pub.UPLOAD_URL for r in calls))
        # The new hash remains reserved even though version reservation failed.
        newref = 'refs/tags/nexus-cf-upload/sha256/' + hashlib.sha256(b'different jar').hexdigest()
        self.assertIn(newref, reservations)
        self.assert_no_secrets(out, err)

    def test_same_hash_different_version_never_uploads(self):
        request, calls, _ = self.fake_server()
        self.assertEqual(self.call_main(request)[0], 0)
        calls.clear()
        args = list(ARGS)
        args[args.index('1.2.3')] = '1.2.4'
        self.assertEqual(self.call_main(request, args=args)[0], 1)
        self.assertEqual(len(calls), 1)
        self.assertNotEqual(calls[0].full_url, pub.UPLOAD_URL)

    def test_failure_before_upload_and_no_reservation_cleanup(self):
        for fail_at in (1, 2):
            calls = []
            def request(req):
                calls.append(req)
                if len(calls) == fail_at:
                    raise TimeoutError(GH)
                return 201, b''
            with self.subTest(fail_at=fail_at):
                result, out, err = self.call_main(request)
                self.assertEqual(result, 1)
                self.assertEqual(len(calls), fail_at)
                self.assertTrue(all(r.method == 'POST' and r.full_url != pub.UPLOAD_URL for r in calls))
                self.assertIn('não remova reservas', err)
                self.assert_no_secrets(out, err)

    def test_uncertain_upload_never_retries_and_reservations_persist(self):
        request, calls, reservations = self.fake_server(upload_result=TimeoutError(CF))
        result, out, err = self.call_main(request)
        self.assertEqual(result, 1)
        self.assertEqual(len(calls), 3)
        self.assertEqual(len(reservations), 2)
        self.assertIn('NÃO tente novamente', err)
        self.assert_no_secrets(out, err)
        calls.clear()
        self.assertEqual(self.call_main(request)[0], 1)
        self.assertEqual(len(calls), 1)

    def test_preparation_failure_sends_nothing_and_hides_exception(self):
        calls = mock.Mock()
        with mock.patch.object(pub, 'make_multipart', side_effect=RuntimeError(CF + GH)):
            result, out, err = self.call_main(calls)
        self.assertEqual(result, 1)
        calls.assert_not_called()
        self.assertIn('antes das reservas', err)
        self.assert_no_secrets(out, err)

    def test_unexpected_reservation_and_upload_errors_are_conservative(self):
        for name, expected in [('reserve', 'Reserva pode ter sido criada'),
                               ('upload', 'Resultado do upload incerto')]:
            with self.subTest(stage=name), mock.patch.object(pub, name, side_effect=RuntimeError(CF + GH)):
                request, calls, _ = self.fake_server()
                result, out, err = self.call_main(request)
                self.assertEqual(result, 1)
                self.assertIn(expected, err)
                self.assert_no_secrets(out, err)
                self.assertTrue(all(r.full_url != pub.UPLOAD_URL for r in calls))


class SummaryAndArgumentsTests(OfflineTest):
    def test_summary_only_contains_safe_validated_fields_and_appends(self):
        writer = mock.mock_open()
        request, calls, _ = FlowTests.fake_server(self)
        args = list(ARGS)
        args[args.index('Correções e melhorias')] = 'Example\n## injected\n' + CF
        env = dict(ENV, GITHUB_STEP_SUMMARY='/tmp/step-summary')
        with mock.patch('builtins.open', writer):
            result, out, err = self.call_main(request, env=env, args=args)
        self.assertEqual(result, 0)
        self.assertEqual(err, '')
        writer.assert_called_once_with('/tmp/step-summary', 'a', encoding='utf-8')
        text = writer().write.call_args.args[0]
        self.assertEqual(text, '### Upload aceito pelo CurseForge\n\n'
                         '- ID do arquivo: `456`\n'
                         '- SHA-256 do JAR: `' + hashlib.sha256(JAR).hexdigest() + '`\n\n'
                         'A aprovação/publicação não foi verificada.\n')
        self.assertNotIn('injected', text)
        self.assert_no_secrets(text, out, err)
        self.assertEqual(len(calls), 3)

    def test_bad_summary_fields_cannot_inject_and_are_not_written(self):
        for file_id, digest in [(True, 'a' * 64), (0, 'a' * 64),
                                ('123\n##evil', 'a' * 64),
                                (1, 'a' * 64 + '\n##evil'), (1, 'A' * 64)]:
            with self.subTest(file_id=file_id, digest=digest), mock.patch('builtins.open') as opened:
                with self.assertRaises(ValueError):
                    pub.append_summary('/tmp/summary', file_id, digest)
                opened.assert_not_called()

    def test_summary_write_failure_preserves_accepted_result_and_no_retry(self):
        request, calls, _ = FlowTests.fake_server(self)
        env = dict(ENV, GITHUB_STEP_SUMMARY='/tmp/step-summary')
        with mock.patch('builtins.open', side_effect=OSError(CF + GH)):
            result, out, err = self.call_main(request, env=env)
        self.assertEqual(result, 1)
        self.assertIn('ID do arquivo: 456', out)
        self.assertIn('SHA-256 do JAR: ' + hashlib.sha256(JAR).hexdigest(), out)
        self.assertIn('aprovação/publicação não foi verificada', out)
        self.assertIn('upload já aceito', err)
        self.assertIn('NÃO tente enviar novamente', err)
        self.assertEqual(len(calls), 3)
        self.assert_no_secrets(out, err)

    def test_no_summary_on_uncertain_upload_or_failed_reservation(self):
        env = dict(ENV, GITHUB_STEP_SUMMARY='/tmp/step-summary')
        for request in [FlowTests.fake_server(self, upload_result=TimeoutError())[0],
                        lambda req: (422, b'duplicate')]:
            with self.subTest(request=request), mock.patch('builtins.open') as opened:
                result, out, err = self.call_main(request, env=env)
                self.assertEqual(result, 1)
                opened.assert_not_called()
                self.assert_no_secrets(out, err)

    def test_unrecognized_args_do_not_reveal_secrets_or_inject_logs(self):
        calls = mock.Mock()
        result, out, err = self.call_main(calls, args=ARGS + ['\n::error::' + CF + GH])
        self.assertEqual(result, 1)
        self.assertIn('Argumentos inválidos', err)
        self.assertNotIn('::error::', err)
        self.assert_no_secrets(out, err)
        calls.assert_not_called()


if __name__ == '__main__':
    unittest.main()
