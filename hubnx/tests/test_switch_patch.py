"""Teste do patch de integração do Chromium NX em uma árvore mínima simulada.
Não substitui compilação do Chromium nem teste no hardware.
"""
import importlib.util
from pathlib import Path
import tempfile

root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('patch',root/'switch/tools/prepare_chromium_nx.py')
patch=importlib.util.module_from_spec(spec);spec.loader.exec_module(patch)
with tempfile.TemporaryDirectory() as directory:
    target=Path(directory)
    gx=target/'gx';gx.mkdir()
    (gx/'host_nx.c').write_text('#define GX_DIR "sdmc:/switch/chromium-nx"\n#define DEFAULT_COMMAND \\\n  "chromium-shell https://google.com"\n/* The kernel\'s memory is useful. */\n')
    (gx/'build-chromium.sh').write_text('mkdir -p "$ROOT"/{proc,sys,dev,tmp,run,root,lib,etc/ssl/certs,usr/lib,usr/share/gx,usr/share/fonts}\n')
    (gx/'build-nx.sh').write_text('nacptool --create "Chromium NX" "shodowlo" "0.1"\nelf2nro x --icon="$SRC/nx/icon.jpg"\nAPP=$DIST/switch/chromium-nx\ncp "$OUT/gx.nro" "$APP/chromium-nx.nro"\nls -la "$APP"\n')
    patch.prepare(target)
    assert 'GX_DIR "sdmc:/switch/hubnx"' in (gx/'host_nx.c').read_text()
    assert 'file:///usr/share/gx/hubnx/index.html' in (gx/'host_nx.c').read_text()
    assert 'APP=$DIST/switch/hubnx' in (gx/'build-nx.sh').read_text()
    assert 'hubnx.nro' in (gx/'build-nx.sh').read_text()
    assert 'meta name="hubnx-platform" content="switch"' in (target/'media-hub/index.html').read_text()
    assert (target/'media-hub/icon.jpg').is_file()
print('PASSOU: adaptação de pasta, nome NRO, ícone, HTML offline e plataforma Switch (simulada)')
