#!/usr/bin/env python3
"""Self-contained integrity/scope check; external upstream files are not required."""
from pathlib import Path
import hashlib,json,re
r=Path(__file__).resolve().parents[1]
m=json.loads((r/'docs/extraction-manifest.json').read_text())
for row in m['outputs']:
 p=r/row['output'];assert hashlib.sha256(p.read_bytes()).hexdigest()==row['sha256'],p
for p in (r/'engine/src/main').rglob('*'):
 if p.is_file() and p.suffix in ('.java','.cpp','.h'):
  s=p.read_text()
  assert not re.search(r'org\.telegram|AccountInstance|UserConfig|MessagesController|MessagesStorage|ConnectionsManager|FileLoader|tgnet|java\.net|okhttp',s),p
  assert not re.search(r'ToneCurve|YUCIHighPass|drawSkinSmoothPass|skinPassDrawn|greenAndBlueChannelOverlay|stillImageHighPass|highpassSkinSmoothing',s),p
print('PASS: retained source hashes and excluded account/network/smoothing implementation scope')
