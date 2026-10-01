#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?SDK required for binary verification}"
python3 tools/verify-retained-source.py > artifacts/retained-source-verification.txt
python3 tools/verify-artifacts.py > artifacts/dependency-verification.json
out="$PWD/artifacts/deliverables"
mkdir -p "$out"
cp engine/build/outputs/aar/engine-debug.aar "$out/telegram-photo-engine-proof.aar"
cp smoke-host/build/outputs/apk/debug/smoke-host-debug.apk "$out/photo-engine-proof.apk"
cp smoke-host/build/outputs/apk/androidTest/debug/smoke-host-debug-androidTest.apk "$out/photo-engine-proof-tests.apk"
cp artifacts/independent-consumer/app-debug.apk "$out/independent-aar-consumer.apk"
cp artifacts/independent-consumer/app-debug-androidTest.apk "$out/independent-aar-consumer-tests.apk"
python3 - <<'PY'
from pathlib import Path
import hashlib,json,tarfile
root=Path.cwd();out=root/'artifacts/deliverables'
exclude={'upstream','patches','.private-reference','.runtime','.gradle','.cxx','.git','build','artifacts','__pycache__'}
allowed={'LICENSE','NOTICE','README.md','.gitignore','settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','gradle','engine','smoke-host','standalone-consumer','tools','tests','docs','provenance'}
files=[p for p in root.rglob('*') if p.relative_to(root).parts[0] in allowed and p.is_file() and not any(x in exclude for x in p.relative_to(root).parts) and p.suffix!='.log' and p.name!='local.properties']
manifest=[{'path':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}for p in sorted(files)]
(out/'source-files.json').write_text(json.dumps(manifest,indent=2)+'\n')
with tarfile.open(out/'corresponding-source.tar.gz','w:gz') as tar:
 for p in sorted(files):tar.add(p,arcname='telegram-photo-engine-proof/'+str(p.relative_to(root)))
for p in sorted(out.iterdir()):
 if p.is_file() and p.name!='SHA256SUMS': print(hashlib.sha256(p.read_bytes()).hexdigest(),p.name)
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))
PY
