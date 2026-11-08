# AnimeLHD — análisis (Fase 0) · ❓ DOMINIO POR CONFIRMAR

- **Fecha del análisis:** 2026-11-08
- **Estado:** no sé con certeza cuál es el sitio "AnimeLHD". Los candidatos probados:

| URL | Resultado | Qué es |
|---|---|---|
| `https://animelhd.com/` | **301** → `https://veranime.ninja/` | Redirige a **Anime Ninja** (ya analizado en [`animeninja.md`](animeninja.md)) |
| `https://animelatinohd.com/` | 301 → `https://www.animelatinohd.com/` | — |
| `https://www.animelatinohd.com/` | **403** · `cf-mitigated: challenge` (`cType: 'managed'`) | "Anime Latino HD", protegido por Cloudflare |

- Fixture del desafío: `app/src/test/resources/fixtures/cloudflare/animelatinohd_inicio_managed.html`.

## Posibilidades

1. **AnimeLHD = `animelhd.com`**, que hoy redirige a `veranime.ninja` → sería el mismo sitio que Anime Ninja y bastaría con un solo proveedor.
2. **AnimeLHD = Anime Latino HD (`www.animelatinohd.com`)** → protegido por Cloudflare (desafío *managed*); necesito su HTML guardado.
3. **Otro dominio** → dime la URL que usas.

## Lo que necesito

- Confirmar cuál es la URL de AnimeLHD.
- Si es la opción 2 o 3: el HTML guardado (Ctrl+S → "Página web, solo HTML") de portada, búsqueda de "one piece", directorio con filtros, ficha de anime y página de episodio, en `app/src/test/resources/fixtures/animelhd/`.
