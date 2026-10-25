#!/usr/bin/env python3
"""
Personaliza o Chromium NX oficial para o HubNX independente.
O script NAO compila o NRO; executa patches exatos, verificando cada ponto.
Uso: python3 tools/prepare_chromium_nx.py /caminho/chromium-nx
"""
from pathlib import Path
import sys
import shutil

ROOT = Path(__file__).resolve().parent.parent.parent
HOME = 'file:///usr/share/gx/hubnx/index.html'

def change_exact(text, original, new, desc):
    n = text.count(original)
    if n != 1:
        raise RuntimeError(f'{desc}: esperada 1 ocorrência, encontrado {n}. Fonte mudou; revisar manualmente.')
    return text.replace(original,new,1)

def prepare(target: Path):
    if not (target/'gx'/'host_nx.c').is_file():
        raise FileNotFoundError('Pasta inválida: esperado gx/host_nx.c')
    source = target/'media-hub'
    source.mkdir(exist_ok=True)
    html = (ROOT/'index.html').read_text(encoding='utf-8')
    html = html.replace('<meta name="hubnx-platform" content="web">','<meta name="hubnx-platform" content="switch">')
    (source/'index.html').write_text(html, encoding='utf-8')
    shutil.copy2(ROOT/'switch'/'assets'/'icon.jpg',source/'icon.jpg')

    path = target/'gx'/'host_nx.c'
    value=path.read_text(encoding='utf-8')
    value=change_exact(value,'#define GX_DIR "sdmc:/switch/chromium-nx"', '#define GX_DIR "sdmc:/switch/hubnx"','GX_DIR')
    # Usa Chromium completo em vez de chromium-shell para montar profile.ext4 e
    # tentar manter localStorage (cadastro dos favoritos) após reiniciar.
    start=value.find('#define DEFAULT_COMMAND \\')
    end=value.find("/* The kernel\'s memory",start)
    if start<0 or end<0 or 'chromium-shell' not in value[start:end]:
        raise RuntimeError('DEFAULT_COMMAND mudou no upstream; revisar manualmente.')
    command = (
        '#define DEFAULT_COMMAND \\'+chr(10)
        + '\t"-w -b -c3 @chromium-full.ext4 =profile.ext4 -A225,46,1150,80 " \\'+chr(10)
        + '\t"/usr/lib/chromium/chromium --no-sandbox --single-process --no-zygote " \\'+chr(10)
        + '\t"--ozone-platform=wayland --user-data-dir=/root/profile --no-first-run " \\'+chr(10)
        + '\t"--no-default-browser-check --disable-breakpad --disable-crashpad-for-testing " \\'+chr(10)
        + '\t"--test-type --password-store=basic --disable-background-networking " \\'+chr(10)
        + '\t"--disable-component-update --disk-cache-size=33554432 " \\'+chr(10)
        + '\t"--media-cache-size=33554432 --disable-quic --hide-crash-restore-bubble " \\'+chr(10)
        + '\t"--enable-spatial-navigation --remote-debugging-pipe " \\'+chr(10)
        + '\t"--use-gl=angle --use-angle=gles-egl --ignore-gpu-blocklist " \\'+chr(10)
        + '\t"--disable-gpu-sandbox --in-process-gpu ' + HOME + '"'+chr(10)
    )
    value=value[:start]+command+value[end:]
    path.write_text(value,encoding='utf-8')

    path = target/'gx'/'build-chromium.sh'
    value=path.read_text(encoding='utf-8')
    key='mkdir -p "$ROOT"/{proc,sys,dev,tmp,run,root,lib,etc/ssl/certs,usr/lib,usr/share/gx,usr/share/fonts}'
    value=change_exact(value,key,key+'\n\t# Página inicial offline do HubNX, incorporada no ext4 do próprio NRO.\n\tmkdir -p "$ROOT/usr/share/gx/hubnx"\n\tcp "$SRC/../media-hub/index.html" "$ROOT/usr/share/gx/hubnx/index.html" || exit 1','UI embutida no rootfs')
    path.write_text(value,encoding='utf-8')

    path = target/'gx'/'build-nx.sh'
    value=path.read_text(encoding='utf-8')
    value=change_exact(value, 'nacptool --create "Chromium NX" "shodowlo" "0.1"',
                       'nacptool --create "HubNX" "HubNX" "0.1.0"','nome app')
    value=change_exact(value, '--icon="$SRC/nx/icon.jpg"','--icon="$SRC/../media-hub/icon.jpg"','ícone app')
    value=change_exact(value, 'APP=$DIST/switch/chromium-nx', 'APP=$DIST/switch/hubnx','pasta SD')
    value=change_exact(value, 'cp "$OUT/gx.nro" "$APP/chromium-nx.nro"',
                       'cp "$OUT/gx.nro" "$APP/hubnx.nro"','nome NRO')
    # O release original cria programs.txt, que substitui o DEFAULT_COMMAND acima.
    # Por isso, o release do hub não pode entregar esse menu de testes.
    value=change_exact(value, 'ls -la "$APP"',
                       '# Sem menu externo: inicia direto em HOME usando DEFAULT_COMMAND.\nif [ -z "${DEV:-}" ]; then rm -f "$APP/programs.txt"; fi\nls -la "$APP"','sem menu externo')
    path.write_text(value,encoding='utf-8')
    print('Fontes preparadas em',target)
    print('Build requer os Docker containers e devkitPro do Chromium NX oficial.')

if __name__ == '__main__':
    if len(sys.argv)!=2:
        raise SystemExit('Uso: python3 tools/prepare_chromium_nx.py <pasta_do_chromium_nx>')
    prepare(Path(sys.argv[1]).resolve())
