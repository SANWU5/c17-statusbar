"""Compile production pointer flow against the app's real Compose events; no device needed."""
from pathlib import Path
import argparse
import os
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java', type=Path, default=root / '.local-toolchain/jdk/jdk-17.0.20.1+1/bin/java.exe')
parser.add_argument('--cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
parser.add_argument('--production-classes', type=Path, help='Use compileReleaseKotlin output instead of compiling production source.')
parser.add_argument('--output', type=Path, default=root / 'build/c17-dev/custom-plugin-drag-pointer')
args = parser.parse_args()
out = args.output.resolve()
out.mkdir(parents=True, exist_ok=True)

def artifact(group, name, version, suffix):
    paths = list((args.cache / group / name / version).rglob('*' + suffix))
    assert len(paths) == 1, (name, paths)
    return paths[0]

def run(command):
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True, encoding='utf-8', errors='replace')
    print(result.stdout, end=''); print(result.stderr, end='')
    if result.returncode: raise SystemExit(result.returncode)

stdlib = artifact('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.20', '.jar')
compiler_cp = [artifact('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.20', '.jar'), stdlib,
    artifact('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.4.20', '.jar'),
    artifact('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10', '.jar'),
    artifact('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.9.0', '.jar'),
    artifact('org.jetbrains', 'annotations', '23.0.0', '.jar')]
classes = out / 'classes'
classes.mkdir(exist_ok=True)
run([args.java.with_name('javac.exe' if os.name == 'nt' else 'javac'), '-d', classes,
    root / 'validation/checks-src/android/os/Build.java',
    root / 'validation/checks-src/android/view/MotionEvent.java',
    root / 'app/src/main/java/dev/puitheme/CustomPluginDragGeometry.java'])
classpath = [classes, stdlib, artifact('androidx.collection', 'collection-jvm', '1.5.0', '.jar')]
for group, name in [('androidx.compose.ui', 'ui-android'), ('androidx.compose.ui', 'ui-unit-android'),
                    ('androidx.compose.ui', 'ui-geometry-android'), ('androidx.compose.ui', 'ui-util-android'),
                    ('androidx.compose.foundation', 'foundation-android')]:
    jar = out / (name + '.jar')
    with zipfile.ZipFile(artifact(group, name, '1.12.0', '.aar')) as archive:
        jar.write_bytes(archive.read('classes.jar'))
    classpath.append(jar)
sources = [root / 'validation/CustomPluginDragPointerCheck.kt']
friends = []
if args.production_classes:
    production = args.production_classes.resolve()
    assert (production / 'dev/puitheme/CustomPluginDragPointerKt.class').is_file(), production
    classpath.insert(0, production)
    friends = ['-Xfriend-paths=' + str(production)]
else:
    sources.insert(0, root / 'app/src/main/java/dev/puitheme/CustomPluginDragPointer.kt')
cp = os.pathsep.join(map(str, classpath))
run([args.java, '-Xmx256m', '-cp', os.pathsep.join(map(str, compiler_cp)),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '11',
    '-classpath', cp, '-d', classes, *friends, *sources])
run([args.java, '-Xmx128m', '-cp', cp, 'dev.puitheme.CustomPluginDragPointerCheckKt'])
