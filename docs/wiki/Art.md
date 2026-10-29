# 破门斩美术

## 来源与范围

全部主体由 CPA cliproxy 的 `gpt-image-2.5-flare` 生成，`quality=high`、PNG、
`style=none`，提示词明确指定 Minecraft 像素风。未使用其他后端或 Claude 生图额度。
调用复用 Bong `scripts/images/gen.py` 的 `generate_cliproxy_images` 图片接口；
任务专用参数位于交接目录，未改生成器默认值和全局配置。

先由 flare、sunburst 分别生成木门背景与叩门图标。最终选 flare：中央正文区平均灰度
228.51（sunburst 227.22），两者都接近浅纸；同一最近邻 32×32 采样下，
叩门的颜色数为 375（sunburst 407），flare 略便于收敛为低色数图标。
sunburst 的正文区灰度标准差略低（1.52 对 1.64）；选择依据是亮度和缩小后的颜色收敛，
不代表所有题材上 flare 都优于 sunburst。正文区域按 x=18%–82%、y=12%–88% 测量。

## 成品与资源约定

资源命名空间为 `meridian_door_breaker`。卷轴和背景沿用功法库的完整卷 ID 路径，
战斗图标沿用目录 `presentation.icon_id` 的路径。资源随正式 Fabric JAR 打包。
只提供纹理，不增加卷轴注册、阅读界面、动作栏显示或其他接线代码。

| 书卷 | 卷路径 | 32×32 卷轴图 | 1536×1024 阅读背景 | 提示词 |
|---|---|---|---|---|
| 上卷·木门 | `upper` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/item/upper_scroll.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/scroll/upper.png) | 卷轴 [提示词](#prompt-upper-scroll)；背景 [提示词](#prompt-upper-background) |
| 中卷·铁门 | `middle` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/item/middle_scroll.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/scroll/middle.png) | 卷轴 [提示词](#prompt-middle-scroll)；背景 [提示词](#prompt-middle-background) |
| 下卷·万门 | `lower` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/item/lower_scroll.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/scroll/lower.png) | 卷轴 [提示词](#prompt-lower-scroll)；背景 [提示词](#prompt-lower-background) |

| 招式 | icon_id | 32×32 | 64×64 | 提示词 |
|---|---|---|---|---|
| 叩门 | `meridian_door_breaker:knock` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/knock.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/knock_64.png) | [提示词](#prompt-knock) |
| 破门 | `meridian_door_breaker:cleave` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/cleave.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/cleave_64.png) | [提示词](#prompt-cleave) |
| 撞门 | `meridian_door_breaker:ram` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/ram.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/ram_64.png) | [提示词](#prompt-ram) |
| 破盾 | `meridian_door_breaker:hook` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/hook.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/hook_64.png) | [提示词](#prompt-hook) |
| 横扫 | `meridian_door_breaker:sweep` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/sweep.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/sweep_64.png) | [提示词](#prompt-sweep) |
| 掩门 | `meridian_door_breaker:close` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/close.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/close_64.png) | [提示词](#prompt-close) |
| 连门 | `meridian_door_breaker:chain` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/chain.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/chain_64.png) | [提示词](#prompt-chain) |
| 万门一斩 | `meridian_door_breaker:finale` | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/finale.png) | [PNG](../../shared/src/main/resources/assets/meridian_door_breaker/textures/gui/technique/finale_64.png) | [提示词](#prompt-finale) |

## 后处理

- 阅读背景保留 1536×1024 RGB；主题细节集中在外缘，中央浅纸供运行时正文使用，提示词禁止文字。
- 方形生成请求为 1024×1024，CPA 实际返回 1254×1254 RGB；记录实际尺寸后再处理。
- 卷轴按 R、B≥130、G≤140 且 R−G、B−G≥60 去品红底，裁去透明边，以最近邻缩至 30×30 以内；
  透明像素 RGB 归零后无抖动量化为 32 色，再居中放进 32×32 RGBA，alpha 二值化为 0 或 255。
- 战斗图标先最近邻缩为 64×64，把与主要暗底 RGB 距离≤20 的颜色归为统一炭褐底，
  无抖动量化为 32 色，再统一两圈边框：外圈 `#201813`、内圈 `#846437`。
  64×64 每圈宽 2 像素；最近邻缩为 32×32 后每圈宽 1 像素。加入边框后最多 34 色。
  动作主体保留 CPA 原稿。
- 后处理不绘制或替换动作主体。尺寸、PNG 解码、alpha、透明留边、边框及目录 ID 对照由脚本核验。

## 原稿、比较样张与复现

为遵守仅新增 assets 与本页的协作范围，原稿、日志和独立提示词留在仓外交接目录，
未创建仓内 `.local-art/`、未改 `.gitignore`；逐图提示词全文归档在本页，避免增加其他 Wiki 文件。

- 原稿：`/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/raw/door`。
- 独立提示词：`/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/prompts`；叩门实际使用 `/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/prompts/knock_icon.txt`。
- flare 样张：`/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/samples/flare/upper_background.png`、
  `/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/samples/flare/knock.png`。
- sunburst 样张：`/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/samples/sunburst/upper_background.png`、
  `/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/samples/sunburst/knock.png`。
- 两组对照预览：`/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/samples/comparison.jpg`。
- 生成与后处理：`/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/cpa_generate.py`、`/home/kiz/Code/Kizuna-remote-session/handoff/technique-art-20261028-work/process_art.py`。
- 2.5 模型经通用 Responses 路由曾返回 503；改用生成器已有图片入口后成功，失败日志保留在交接目录。

本页清单记录新增的专用纹理；其他页面中“尚未提供专用美术”的旧说明以本页文件清单为准。

## 逐图提示词

以下保留实际发送内容；代码块仅按词换行。

<a id="prompt-upper-scroll"></a>

### upper_scroll

```text
One single inventory item sprite: 上卷·木门. Subject: a short parchment roll bound by an oak slat; squared
wooden finials, a distinct broad rectangular silhouette, warm oak and amber. Minecraft vanilla-style pixel
art designed on a 32x32 logical pixel grid, shown enlarged in a square image. Shared collection identity:
thick stepped dark umber outline, restrained brass detail, flat clustered shading and limited colors; make
this volume's silhouette and palette clearly distinct from the other two volumes of its book. Center one
fully visible object occupying about 80 percent of canvas. Solid pure magenta #FF00FF background only for
chroma key, no magenta on the object. Crisp block pixels, no antialiasing, gradients, glow, shadows,
photorealism, other objects, UI, frame, writing, letters, symbols or watermark.
```

<a id="prompt-upper-background"></a>

### upper_background

```text
Create a production-ready landscape 1536x1024 Minecraft-compatible pixel-art reading background for 上卷·木门.
Straight-on orthographic view of one antique Chinese martial manual scroll. Shared binding language: a slim
dark umber edge, squared scroll rods at far left and right, restrained aged brass corner fittings, worn
paper edges. Theme: oak grain, mortise-and-tenon joints, a few axe nicks and split fibers; warm oak, umber
and amber. Place all themed detail in the outermost 15 percent margins. Keep the central rectangle from
x=18% to 82% and y=12% to 88% as uninterrupted very light cream parchment, evenly lit and low contrast for
dark live-rendered body text. Crisp clustered square pixels, limited palette, no photorealism, no
perspective or cast shadow. Absolutely no text, Chinese characters, letters, numerals, calligraphy,
writing-like marks, title plates, watermark, logo, people or diagrams. Full-bleed opaque texture.
```

<a id="prompt-middle-scroll"></a>

### middle_scroll

```text
One single inventory item sprite: 中卷·铁门. Subject: a compact tightly rolled parchment secured by two dark
iron bands with rivets; a distinct narrow cylindrical silhouette, steel grey and restrained rust red.
Minecraft vanilla-style pixel art designed on a 32x32 logical pixel grid, shown enlarged in a square image.
Shared collection identity: thick stepped dark umber outline, restrained brass detail, flat clustered
shading and limited colors; make this volume's silhouette and palette clearly distinct from the other two
volumes of its book. Center one fully visible object occupying about 80 percent of canvas. Solid pure
magenta #FF00FF background only for chroma key, no magenta on the object. Crisp block pixels, no
antialiasing, gradients, glow, shadows, photorealism, other objects, UI, frame, writing, letters, symbols or
watermark.
```

<a id="prompt-middle-background"></a>

### middle_background

```text
Create a production-ready landscape 1536x1024 Minecraft-compatible pixel-art reading background for 中卷·铁门.
Straight-on orthographic view of one antique Chinese martial manual scroll. Shared binding language: a slim
dark umber edge, squared scroll rods at far left and right, restrained aged brass corner fittings, worn
paper edges. Theme: forged iron straps, square rivets, restrained rust blooms; charcoal iron, muted
blue-steel and red-brown rust. Place all themed detail in the outermost 15 percent margins. Keep the central
rectangle from x=18% to 82% and y=12% to 88% as uninterrupted very light cream parchment, evenly lit and low
contrast for dark live-rendered body text. Crisp clustered square pixels, limited palette, no photorealism,
no perspective or cast shadow. Absolutely no text, Chinese characters, letters, numerals, calligraphy,
writing-like marks, title plates, watermark, logo, people or diagrams. Full-bleed opaque texture.
```

<a id="prompt-lower-scroll"></a>

### lower_scroll

```text
One single inventory item sprite: 下卷·万门. Subject: a broad partly-unfurled scroll with two layered paper
wings like overlapping doorways and bronze end caps; a distinct fan-like silhouette, charcoal and antique
bronze. Minecraft vanilla-style pixel art designed on a 32x32 logical pixel grid, shown enlarged in a square
image. Shared collection identity: thick stepped dark umber outline, restrained brass detail, flat clustered
shading and limited colors; make this volume's silhouette and palette clearly distinct from the other two
volumes of its book. Center one fully visible object occupying about 80 percent of canvas. Solid pure
magenta #FF00FF background only for chroma key, no magenta on the object. Crisp block pixels, no
antialiasing, gradients, glow, shadows, photorealism, other objects, UI, frame, writing, letters, symbols or
watermark.
```

<a id="prompt-lower-background"></a>

### lower_background

```text
Create a production-ready landscape 1536x1024 Minecraft-compatible pixel-art reading background for 下卷·万门.
Straight-on orthographic view of one antique Chinese martial manual scroll. Shared binding language: a slim
dark umber edge, squared scroll rods at far left and right, restrained aged brass corner fittings, worn
paper edges. Theme: layered receding door silhouettes opening toward a bright central horizon, restrained
monumental scale; charcoal, bronze and cool ivory. Place all themed detail in the outermost 15 percent
margins. Keep the central rectangle from x=18% to 82% and y=12% to 88% as uninterrupted very light cream
parchment, evenly lit and low contrast for dark live-rendered body text. Crisp clustered square pixels,
limited palette, no photorealism, no perspective or cast shadow. Absolutely no text, Chinese characters,
letters, numerals, calligraphy, writing-like marks, title plates, watermark, logo, people or diagrams.
Full-bleed opaque texture.
```

<a id="prompt-knock"></a>

### knock

```text
Use case: stylized-concept. A single square Minecraft martial technique hotbar icon, Door Breaker series,
the KNOCK move. Authentic low resolution pixel art: design as exactly 32 by 32 logical pixels, enlarged to
1024 by 1024 with nearest-neighbor, each square pixel a large flat block, crisp stair-step silhouettes,
limited palette, no antialiasing, no gradients, no photorealism, no painterly marks, no 3D render.
Common series frame: straight square dark umber outer rim exactly one logical pixel thick, thin dull brass
inner rim one logical pixel, flat very dark charcoal-brown interior. No decorative tiny details. The same
frame and dark background will be shared by the whole set. High contrast ivory, oak brown and desaturated
steel foreground, small amber action accents.
In the inner 26 by 26 pixel area show one upright wooden axe shaft, its steel axe head clearly visible near
the upper left, and the blunt lower end of the shaft thumping against a simple oak door upright on the
right. Readable one-hit short angular amber impact mark at the shaft butt, NOT an axe blade cutting. The
silhouette should instantly say a short blunt knocking jab: vertical shaft, right-side door post, one
impact. Keep all action inside the frame with a little breathing room. Front/side emblem view, chunky
compact shapes, no full human figure or hands needed. No text, glyphs, numbers, letters, watermark, logo, UI
labels, or additional icons. Fill the square canvas with the tile, no external padding.
```

<a id="prompt-cleave"></a>

### cleave

```text
One square Minecraft martial-technique hotbar icon for 正劈木门. Action silhouette: heavy axe blade descending
vertically into a split oak door plank; one forceful downward cut with two separated wood chips. Authentic
crisp pixel art designed as exactly 32x32 logical pixels, enlarged in a square 1024x1024 image with large
flat pixel clusters and stepped silhouettes. Shared Door Breaker series frame: exactly one logical pixel
dark umber outer rim, one logical pixel dull brass inner rim, flat charcoal-brown interior. Shared Basic
Techniques icons use this same frame and palette. Foreground uses high-contrast ivory, oak brown and
desaturated steel, with one small muted amber or cinnabar action accent. Keep the action large, instantly
recognizable and inside the frame. No photorealism, 3D, gradients, antialiasing, tiny ornament, full scene,
text, glyphs, numbers, letters, watermark, logo or UI labels. Fill the square tile.
```

<a id="prompt-ram"></a>

### ram

```text
One square Minecraft martial-technique hotbar icon for 冲身撞击. Action silhouette: compact braced
shoulder-and-torso silhouette driving directly into a door; a short straight motion wedge and a square
impact burst, no weapon swing. Authentic crisp pixel art designed as exactly 32x32 logical pixels, enlarged
in a square 1024x1024 image with large flat pixel clusters and stepped silhouettes. Shared Door Breaker
series frame: exactly one logical pixel dark umber outer rim, one logical pixel dull brass inner rim, flat
charcoal-brown interior. Shared Basic Techniques icons use this same frame and palette. Foreground uses
high-contrast ivory, oak brown and desaturated steel, with one small muted amber or cinnabar action accent.
Keep the action large, instantly recognizable and inside the frame. No photorealism, 3D, gradients,
antialiasing, tiny ornament, full scene, text, glyphs, numbers, letters, watermark, logo or UI labels. Fill
the square tile.
```

<a id="prompt-hook"></a>

### hook

```text
One square Minecraft martial-technique hotbar icon for 越盾钩拽. Action silhouette: curved axe hook catching the
upper rim of a small shield and pulling it diagonally aside; clear hooked blade silhouette and one pull
direction. Authentic crisp pixel art designed as exactly 32x32 logical pixels, enlarged in a square
1024x1024 image with large flat pixel clusters and stepped silhouettes. Shared Door Breaker series frame:
exactly one logical pixel dark umber outer rim, one logical pixel dull brass inner rim, flat charcoal-brown
interior. Shared Basic Techniques icons use this same frame and palette. Foreground uses high-contrast
ivory, oak brown and desaturated steel, with one small muted amber or cinnabar action accent. Keep the
action large, instantly recognizable and inside the frame. No photorealism, 3D, gradients, antialiasing,
tiny ornament, full scene, text, glyphs, numbers, letters, watermark, logo or UI labels. Fill the square
tile.
```

<a id="prompt-sweep"></a>

### sweep

```text
One square Minecraft martial-technique hotbar icon for 回旋横扫. Action silhouette: broad horizontal axe sweep
with one thick crescent motion arc crossing left to right; low wide silhouette, single blade. Authentic
crisp pixel art designed as exactly 32x32 logical pixels, enlarged in a square 1024x1024 image with large
flat pixel clusters and stepped silhouettes. Shared Door Breaker series frame: exactly one logical pixel
dark umber outer rim, one logical pixel dull brass inner rim, flat charcoal-brown interior. Shared Basic
Techniques icons use this same frame and palette. Foreground uses high-contrast ivory, oak brown and
desaturated steel, with one small muted amber or cinnabar action accent. Keep the action large, instantly
recognizable and inside the frame. No photorealism, 3D, gradients, antialiasing, tiny ornament, full scene,
text, glyphs, numbers, letters, watermark, logo or UI labels. Fill the square tile.
```

<a id="prompt-close"></a>

### close

```text
One square Minecraft martial-technique hotbar icon for 撤步掩门. Action silhouette: a withdrawing step shown by
one back-pointing boot and a vertical blade closing a narrow door gap; compact retreat-and-close silhouette.
Authentic crisp pixel art designed as exactly 32x32 logical pixels, enlarged in a square 1024x1024 image
with large flat pixel clusters and stepped silhouettes. Shared Door Breaker series frame: exactly one
logical pixel dark umber outer rim, one logical pixel dull brass inner rim, flat charcoal-brown interior.
Shared Basic Techniques icons use this same frame and palette. Foreground uses high-contrast ivory, oak
brown and desaturated steel, with one small muted amber or cinnabar action accent. Keep the action large,
instantly recognizable and inside the frame. No photorealism, 3D, gradients, antialiasing, tiny ornament,
full scene, text, glyphs, numbers, letters, watermark, logo or UI labels. Fill the square tile.
```

<a id="prompt-chain"></a>

### chain

```text
One square Minecraft martial-technique hotbar icon for 三刀连进. Action silhouette: three separate forward
diagonal axe cuts represented by three staggered bright slash marks converging toward one point; triple
rhythm, no circular sweep. Authentic crisp pixel art designed as exactly 32x32 logical pixels, enlarged in a
square 1024x1024 image with large flat pixel clusters and stepped silhouettes. Shared Door Breaker series
frame: exactly one logical pixel dark umber outer rim, one logical pixel dull brass inner rim, flat
charcoal-brown interior. Shared Basic Techniques icons use this same frame and palette. Foreground uses
high-contrast ivory, oak brown and desaturated steel, with one small muted amber or cinnabar action accent.
Keep the action large, instantly recognizable and inside the frame. No photorealism, 3D, gradients,
antialiasing, tiny ornament, full scene, text, glyphs, numbers, letters, watermark, logo or UI labels. Fill
the square tile.
```

<a id="prompt-finale"></a>

### finale

```text
One square Minecraft martial-technique hotbar icon for 万门一斩. Action silhouette: one monumental diagonal axe
slash cleaving through three receding door frames; layered doorway silhouettes collapse around one decisive
cut. Authentic crisp pixel art designed as exactly 32x32 logical pixels, enlarged in a square 1024x1024
image with large flat pixel clusters and stepped silhouettes. Shared Door Breaker series frame: exactly one
logical pixel dark umber outer rim, one logical pixel dull brass inner rim, flat charcoal-brown interior.
Shared Basic Techniques icons use this same frame and palette. Foreground uses high-contrast ivory, oak
brown and desaturated steel, with one small muted amber or cinnabar action accent. Keep the action large,
instantly recognizable and inside the frame. No photorealism, 3D, gradients, antialiasing, tiny ornament,
full scene, text, glyphs, numbers, letters, watermark, logo or UI labels. Fill the square tile.
```
