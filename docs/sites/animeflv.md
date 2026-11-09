# AnimeFLV — ⛔ CERRADO

- **Última comprobación:** 2026-11-09.
- **Estado:** el sitio cerró. No hay proveedor ni lo habrá mientras no vuelva.

## Qué se comprobó

| URL | Resultado (2026-11-08 y 2026-11-09) |
|---|---|
| `https://animeflv.net/` | **522** |
| `https://www3.animeflv.net/` | **522** |
| `https://www4.animeflv.net/` | **522** |

El **522** lo da Cloudflare cuando no logra conectar con el servidor de origen: el servidor de AnimeFLV ya no responde. No es un bloqueo hacia nosotros ni un desafío anti-bots.

## Qué pasó (investigación del 2026-11-09)

- En abril de 2026 empezaron las caídas. A finales de junio el reproductor dejó de devolver videos: la portada y el directorio seguían, pero sin nada que ver. ([TorrentFreak, 14-07-2026](https://torrentfreak.com/anime-piracy-giant-animeflv-mysteriously-stopped-serving-video/))
- Después la MPA informó a la Oficina del Representante Comercial de EE. UU. que la familia de sitios de AnimeFLV cerró tras sus acciones. ([TorrentFreak](https://torrentfreak.com/mpa-takes-credit-for-shutting-down-piracy-giants-animeflv-and-cineby/))
- No hay un dominio nuevo oficial. Los sitios que usan el nombre (animeflv.ar, .si, .ac…) son de otros dueños y algunos están marcados como sospechosos de malware: **no se agregan**.

## En la app

`config/sites.json` lo marca con `"status": "cerrado"` y desactivado; en Ajustes aparece como "cerró". Si algún día vuelve, basta con analizarlo, cambiar el dominio y el estado en la configuración remota.
