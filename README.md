# My Friend — Virtual Pet & Watch Face (Wear OS / Galaxy Watch 6)

Bichinho virtual retrô renderizado em uma grade LCD `52x52` para **Wear OS (Galaxy Watch 6)**, funcionando tanto como aplicativo standalone quanto como **Watch Face** interativa, escrito 100% em Java puro (zero dependências externas).

## Arquitetura do Projeto (`v1.6.0`)

- **`PetConfig.java`** — Constantes de balanceamento, decaimento por hora, preços da loja, IDs de itens, humores e partículas.
- **`PetState.java`** — Persistência em `SharedPreferences`, cálculo exato de decaimento em segundo plano e regras de evolução/status.
- **`PixelGrid.java`** — Matemática da grade `52x52` adaptável à resolução do relógio (`432x432` / `480x480`), paleta de `Paint` pré-alocada, fontes `3x5`, sprites `5x5` e primitivas otimizadas (`rectSolid`, `hline`, `rectPx`).
- **`PetAccessoryRenderer.java`** — Renderização modular dos 17 acessórios da loja, objetos de humor (maçã, copo d'água, cocô) e indicadores do HUD/relógio.
- **`PetEngine.java`** — Motor de simulação, física (olhar, piscar, pulo/squash, partículas), feedback tátil e entrada (toque e coroa/bezel giratório), desacoplado de `View`.
- **`FriendView.java`** — `View` enxuta usada pela `MainActivity`.
- **`FriendWatchFaceService.java`** — `WallpaperService` que renderiza o `PetEngine` direto no `SurfaceHolder`.
- **`ReminderReceiver.java`** — Verificação periódica em background (bateria, sede e fome), respeitando o modo de sono (`isSleeping`) e *cooldown* entre alertas.
- **`SetWatchFaceTool.java`** — Utilitário de linha de comando (`app_process`) para ativar a Watch Face diretamente via ADB no Wear OS.

## Controles no Galaxy Watch 6

- **Toque no centro:** Cutuca o bichinho (5 cutucadas rápidas deixam ele bravo).
- **Arrastar o dedo ou girar a coroa/bezel:** Faz carinho e ganha moedas.
- **Toque no topo (com HUD visível):** Alterna entre relógio (`HH:MM`) e porcentagem da bateria.
- **Toque no cocô (canto inferior esquerdo):** Dá banho e limpa o ambiente.
- **Barra inferior (5 ícones):** Comer | Beber água | Carinho | Dormir/Acordar | Loja de acessórios.

## Compilação e Instalação via ADB (Wi-Fi)

```bash
# 1. Compilar o APK de release (otimizado com R8 e assinado para instalação direta)
./gradlew assembleRelease

# 2. Conectar ao Galaxy Watch 6 via Depuração Wi-Fi e instalar
adb install -r app/build/outputs/apk/release/app-release.apk

# 3. (Opcional) Ativar a Watch Face diretamente via ADB
adb shell 'CLASSPATH=$(pm path com.myfriend | cut -d: -f2) app_process / com.myfriend.SetWatchFaceTool'
```
