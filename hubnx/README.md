# HubNX — central de sites para Android e Nintendo Switch 1

Projeto de código-fonte. **Nenhum APK/NRO final foi compilado ou validado em hardware neste pacote.**

## Entregue nesta revisão (v0.1.0)
- Home responsiva e menu lateral com Início, Streamings, Favoritos, Todos os sites, Adicionar/Gerenciar, Backup, Atualizações e Configurações.
- Adicionar, editar, excluir, favoritar, reordenar sites; pesquisa e armazenamento local com migração da chave `switch-media-hub.bookmarks.v1`.
- Importação/exportação JSON com validação; perfis independentes por dispositivo.
- Detecção de versão nova via `releases.json` publicado no GitHub, com checagem automática e manual, comparação de SemVer e notificação discreta.
- Android: projeto Kotlin + dois WebViews separados; o navegador externo continua dentro do aplicativo sem expor a ponte JavaScript para sites visitados. Menu nativo sobre sites, suporte a tela cheia, backup via seletor de arquivos e download de APK com validação SHA-256 antes de abrir o instalador do sistema.
- Switch: script de preparação de uma versão independente baseada no código-fonte do Chromium NX (sem depender de instalação do aplicativo Chromium NX). A central HTML será incorporada ao rootfs na compilação.
- Automação GitHub Actions: ao publicar Release estável com tag `hubnx-vX.Y.Z` e binários com nomes convencionados, gera hash e atualiza o manifesto sem precisar editar manualmente.

## Limitações reais
- Navegador HTML simples: usa `<iframe>` para abrir sites; **sites que bloqueiam incorporação não abrirão na prévia HTML**, incluindo muitos streamings. No Android, a navegação dos sites usa outro WebView nativo na mesma Activity. No Switch, é necessário compilar e testar Chromium NX adaptado; não há garantia de suporte do CineVS ou de navegação permanente pelo menu em todos os sites.
- Netflix: DRM e certificação continuam sendo limitações. Atalho não equivale a reprodução de vídeos.
- Não existe backend de Conta/Sincronização neste pacote. Esses itens não aparecem no menu enquanto não forem funcionais.
- Android: um APK ainda precisará ser compilado com Android SDK e assinado; aqui não há Android SDK. Instalação via update somente com APK HTTPS, SHA-256 conhecido, permissão do usuário e assinatura compatível.
- Switch: o ambiente requer LKL, Chromium arm64, Docker, devkitA64 e teste no console; aqui não há tais toolchains. A atualização automática do arquivo `.nro` ainda não foi implementada. Há consulta/aviso de atualização e link para o pacote, quando publicado.
- A versão do código-fonte v0.1.0 não será indicada como atualização instalável enquanto não existirem artefatos testados.

## Android (como compilar)
Abrir a pasta `hubnx/android/` no Android Studio, com Java 17 e Android SDK Platform 35. Sincronizar Gradle (AGP 8.7.3, Kotlin 2.0.21), gerar wrapper Gradle pelo Android Studio/Gradle local se necessário e compilar. O arquivo `hubnx/index.html` precisa ser copiado também para `android/app/src/main/assets/index.html` nas alterações; neste commit os dois são idênticos. Testar no Android antes de assinar Release.

## Switch (como preparar)
```sh
git clone https://github.com/shodowlo/chromium-nx.git /tmp/chromium-nx
python3 hubnx/switch/tools/prepare_chromium_nx.py /tmp/chromium-nx
# seguir ambiente de compilação do projeto upstream (dois containers Docker)
```
A saída esperada é `/tmp/chromium-nx/gx/dist-nx/switch/hubnx/` após compilar, incluindo `.nro` e imagens de sistema. O atalho no HOME (forwarder) pode ser criado pelo Sphaira após a compilação. **Ainda não instalar no Switch uma pasta só de fontes como se fosse `.nro` pronto.**

## Processo de publicação GitHub (atualizações automáticas)
1. Compilar, assinar e testar o APK ou compilar/testar o `.nro` e empacotar dependências no `.zip`.
2. Criar uma **GitHub Release** estável com tag `hubnx-v1.0.0` (exemplo), anexando `hubnx-android-v1.0.0.apk` e/ou `hubnx-switch-v1.0.0.zip`.
3. O workflow `.github/workflows/hubnx-release.yml` calcula SHA-256 do(s) pacote(s) e atualiza `hubnx/releases.json` do branch `main`. Apenas plataformas com artefato são marcadas como publicadas.
4. Na inicialização, o aplicativo consulta esse manifesto (a cada ~6 horas no Android) e avisa quando uma versão de sua plataforma superar a instalada. Não depende de commits comuns, apenas de Releases estáveis com binários.
5. Se a assinatura do novo APK não corresponder à versão instalada, o sistema Android não fará instalação como atualização. Atualizações Switch permanecem manuais até implementação segura e testada do instalador integrado.

## Validação local
```sh
node hubnx/tests/test_core.js
python3 hubnx/tests/smoke.py
python3 -m py_compile hubnx/switch/tools/prepare_chromium_nx.py hubnx/scripts/update_release.py
```

## Segurança
- O WebView com `JavascriptInterface` só abre a página `file:///android_asset/index.html`, o WebView de sites externos não possui ponte nativa.
- Não permite esquemas `javascript:`, `file:`, `data:`, nem URLs com usuário/senha cadastrado.
- Backups exportados não incluem senhas de sites. URLs e cookies de serviços externos continuam sujeitos às políticas dos respectivos sites.
- O APK é baixado somente por HTTPS, limitado a 150 MB, com SHA-256 obrigatório, e repassado ao instalador do Android mediante autorização explícita.

**Referência de código Switch:** https://github.com/shodowlo/chromium-nx — respeitar licenças e atribuições do upstream, inclusive Chromium e LKL.
