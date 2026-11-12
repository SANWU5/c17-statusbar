"""Source backup of one round: .local-private/source-backup-code<N>.zip (python tools/dev/source_backup.py 102).

The private folder is git-ignored; the backup is a local safety copy next to the git history, not a replacement for it.
"""
import os
import sys
import zipfile

root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
code = sys.argv[1] if len(sys.argv) > 1 else None
if not code or not code.isdigit():
    sys.exit('usage: source_backup.py <versionCode>')
os.makedirs(os.path.join(root, '.local-private'), exist_ok=True)
out = os.path.join(root, '.local-private', 'source-backup-code%s.zip' % code)
count = 0
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
    for top in ('app/src', 'app/build.gradle', 'docs', 'README.md', 'THIRD_PARTY_NOTICES.md', 'VALIDATION.md', 'DEV-STATUS.zh-CN.md', 'HANDOVER.zh-CN.md', 'AGENTS.md',
                'build.gradle', 'gradle.properties', 'settings.gradle', 'validation/checks-src', 'validation/test-libs',
                'validation/native-canvas', 'validation/README.md', 'validation/run-checks.ps1', 'validation/check-runtime-dependencies.ps1', 'validation/SettingsCheck.java',
                'validation/verify_font_reference.py', 'validation/performance-module-check.py', 'validation/performance-webui-check.cjs',
                 'validation/CouiSurfaceMathCheck.kt', 'validation/coui-surface-check.py', 'tools', 'plugins'):
        path = os.path.join(root, top)
        if os.path.isfile(path):
            archive.write(path, top)
            count += 1
        elif os.path.isdir(path):
            for folder, dirs, files in os.walk(path):
                dirs[:] = [name for name in dirs if name not in ('build', '__pycache__', '.git', '.gradle', '.local-private', '.local-toolchain')]
                for name in files:
                    if name.endswith(('.pyc', '.pyo')):
                        continue
                    full = os.path.join(folder, name)
                    archive.write(full, os.path.relpath(full, root).replace('\\', '/'))
                    count += 1
print(count, 'files,', os.path.getsize(out), 'bytes ->', out)
